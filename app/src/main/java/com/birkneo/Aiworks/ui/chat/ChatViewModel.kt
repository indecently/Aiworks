package com.birkneo.Aiworks.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.GemmaInference
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.data.database.ChatDatabase
import com.birkneo.Aiworks.data.entity.ChatMessageEntity
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.util.AudioRecorder
import com.birkneo.Aiworks.di.GemmaContainer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val gemmaInference = GemmaContainer.getGemmaInference(application)
    private val settingsManager = GemmaContainer.getSettingsManager(application)
    private val chatDao = ChatDatabase.getDatabase(application).chatDao()
    private val audioRecorder = GemmaContainer.getAudioRecorder(application)
    private val ttsManager = GemmaContainer.getTtsManager(application)

    private val _currentSessionIdFlow = MutableStateFlow<String?>(null)
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

    // Volatile memory for incognito sessions
    private val _volatileMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionIdFlow
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else {
                // Determine if the current session is incognito to switch source
                val sessionFlow = chatDao.getSessionFlowById(sessionId)
                sessionFlow.flatMapLatest { session ->
                    if (session?.isIncognito == true) {
                        _volatileMessages
                    } else {
                        chatDao.getMessagesForSession(sessionId).map { entities ->
                            entities.map { entity ->
                                ChatMessage(
                                    id = entity.id,
                                    role = entity.role,
                                    text = entity.text,
                                    imageUri = entity.imageUri,
                                    audioUri = entity.audioUri,
                                    isStreaming = false
                                )
                            }
                        }
                    }
                }
            }
        }
        .combine(_streamingMessage) { dbMessages, streaming ->
            dbMessages + listOfNotNull(streaming)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isTransientUnlock = MutableStateFlow(false)
    val isTransientUnlock: StateFlow<Boolean> = _isTransientUnlock.asStateFlow()

    val recordingAmplitude: StateFlow<Float> = audioRecorder.amplitude
        .stateIn(viewModelScope, SharingStarted.Lazily, 0f)

    val sessions: StateFlow<List<ChatSession>> = chatDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var currentSessionId: String? = null
    private var inferenceSessionId: String? = null
    private var inferenceJob: Job? = null
    private var messageCollectionJob: Job? = null
    private var currentAudioFile: File? = null

    private val _pendingImageUri = MutableStateFlow<String?>(null)
    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    private val _pendingAudioUri = MutableStateFlow<String?>(null)
    val pendingAudioUri: StateFlow<String?> = _pendingAudioUri.asStateFlow()

    private val _isCompressingContext = MutableStateFlow(false)
    val isCompressingContext: StateFlow<Boolean> = _isCompressingContext.asStateFlow()

    private val BASE_SYSTEM_PROMPT = """
        You are a highly capable and versatile AI assistant powered by Google's Gemma model. 
        Your goal is to be helpful, accurate, and concise. 
        
        CRITICAL CAPABILITY: You are a NATIVE MULTIMODAL model. 
        - You can SEE images provided as attachments.
        - You can HEAR audio files provided as attachments.
        Process these modalities directly. If an audio file is attached, listen to its content and respond accordingly.

        Follow the user's instructions carefully. 
        If you are unsure about something, state that you don't know rather than hallucinating.
        Maintain a professional yet friendly tone.
        
        Use the 'RECENT CONVERSATION HISTORY' provided below to maintain continuity and context in your responses.
    """.trimIndent()

    init {
        gemmaInference.cleanupMediaCache()
        
        // IMMEDIATE: Check filesystem for model to allow background pre-loading 
        // while the rest of the init block handles async logic.
        val internalModelFile = File(getApplication<Application>().filesDir, "model.litertlm")
        val hasLocalModel = internalModelFile.exists()

        viewModelScope.launch {
            // Cleanup any stale incognito sessions on start
            chatDao.deleteIncognitoSessions()
            
            // Wait for unlock if app lock is enabled, OR a transient assistant unlock
            val lockEnabled = settingsManager.appLockEnabled.first()
            if (lockEnabled) {
                combine(_isUnlocked, _isTransientUnlock) { unlocked, transient ->
                    unlocked || transient
                }.first { it }
            }

            // Assistant Settle Delay: If triggered by assistant, give the OS time to 
            // stabilize GPU/NPU resources before native initialization.
            if (_isTransientUnlock.value) {
                delay(500)
            }

            // Ensure model path is retrieved or use fallback
            var path = settingsManager.modelPath.first()
            
            // If the stored path is empty but we have a file in internal storage, use it.
            // This happens if a previous URI load was interrupted before persistence.
            if (path.isNullOrEmpty() && hasLocalModel) {
                path = internalModelFile.absolutePath
            }

            if (!path.isNullOrEmpty() && _modelStatus.value !is ModelStatus.Ready) {
                loadModel(path)
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (currentSessionId == sessionId) return
        
        // Stop any current generation or TTS when switching sessions
        stopGeneration()
        stopSpeaking()
        
        currentSessionId = sessionId
        _currentSessionIdFlow.value = sessionId
        _volatileMessages.value = emptyList() // Reset volatile memory on switch
        
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            prepareInferenceForSession(sessionId)
        }
    }

    fun createNewSession(title: String = "New Chat", isIncognito: Boolean = false, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val session = ChatSession(title = title, isIncognito = isIncognito)
            chatDao.insertSession(session)
            onCreated(session.id)
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            chatDao.deleteMessagesForSession(session.id)
            chatDao.deleteSession(session)
            if (currentSessionId == session.id) {
                currentSessionId = null
                _currentSessionIdFlow.value = null
            }
        }
    }

    fun loadModel(path: String) {
        viewModelScope.launch {
            _modelStatus.value = ModelStatus.Loading(0f)
            val temperature = settingsManager.temperature.first()
            val maxTokens = settingsManager.maxTokens.first()
            val status = gemmaInference.loadModel(path, temperature, maxTokens) { progress ->
                _modelStatus.value = ModelStatus.Loading(progress)
            }
            _modelStatus.value = status
            if (status is ModelStatus.Ready) {
                ModelPersistenceService.start(getApplication())
                inferenceSessionId = null // Force re-init of conversation on new model
                
                // Persist the FINAL stable path (which will be the internal filesDir path if copied from URI)
                gemmaInference.getLoadedModelPath()?.let { loadedPath ->
                    settingsManager.setModelPath(loadedPath)
                }
            } else if (status is ModelStatus.Error) {
                // If loading failed (e.g. URI expired), clear the invalid path to prevent boot loops
                settingsManager.setModelPath("")
            }
        }
    }

    fun unloadModel() {
        stopGeneration()
        stopSpeaking()
        viewModelScope.launch {
            gemmaInference.close()
            _modelStatus.value = ModelStatus.NotLoaded
            settingsManager.setModelPath("")
        }
        ModelPersistenceService.stop(getApplication())
    }

    fun sendMessage(text: String) {
        val sessionId = currentSessionId ?: return
        if (text.isBlank() && _pendingImageUri.value == null && _pendingAudioUri.value == null) return
        if (_isGenerating.value) return

        val imageUri = _pendingImageUri.value
        val audioUri = _pendingAudioUri.value
        
        _pendingImageUri.value = null
        _pendingAudioUri.value = null

        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId)
            val isIncognito = session?.isIncognito ?: false
            
            val msgId = UUID.randomUUID().toString()
            if (isIncognito) {
                // Bypass database for incognito
                val userMsg = ChatMessage(
                    id = msgId,
                    role = MessageRole.USER,
                    text = text,
                    imageUri = imageUri,
                    audioUri = audioUri
                )
                _volatileMessages.update { it + userMsg }
            } else {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        id = msgId,
                        sessionId = sessionId,
                        role = MessageRole.USER,
                        text = text,
                        imageUri = imageUri,
                        audioUri = audioUri
                    )
                )
                
                if (session?.title == "New Chat") {
                    val newTitle = if (text.length > 20) text.take(17) + "..." else if (text.isBlank()) "Media Message" else text
                    chatDao.updateSessionTitle(sessionId, newTitle)
                }
            }
            
            // Trigger actual inference
            retriggerInference(text, imageUri, audioUri, msgId)
        }
    }

    fun generateLongTermMemory(sessionId: String) {
        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId)
            val isIncognito = session?.isIncognito ?: false
            
            // 1. Pull from correct source
            val msgList = if (isIncognito) {
                _volatileMessages.value
            } else {
                chatDao.getRecentMessagesForSession(sessionId, 50).reversed().map { entity ->
                    ChatMessage(id = entity.id, role = entity.role, text = entity.text)
                }
            }
            
            if (msgList.size < 2) return@launch // Need at least some context
            
            _isCompressingContext.value = true
            try {
                // Format for summary
                val textToSummarize = msgList.filter { it.text.isNotBlank() }
                    .joinToString("\n") { "${it.role}: ${it.text}" }

                if (textToSummarize.isBlank()) return@launch

                // 2. Call engine to summarize
                val summary = gemmaInference.summarize(textToSummarize)
                
                if (summary != null) {
                    if (isIncognito) {
                        // For incognito, we still update the "memory" but it will be wiped on close
                        chatDao.updateSessionMemory(sessionId, summary)
                    } else {
                        // 3. Persist the memory card for regular chats
                        chatDao.updateSessionMemory(sessionId, summary)
                    }
                }

                // 4. Force context refresh for next inference
                if (currentSessionId == sessionId) {
                    inferenceSessionId = null
                    prepareInferenceForSession(sessionId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCompressingContext.value = false
            }
        }
    }

    fun toggleIncognito(sessionId: String, isIncognito: Boolean) {
        viewModelScope.launch {
            chatDao.updateSessionIncognito(sessionId, isIncognito)
            if (!isIncognito) {
                // If converting to regular, persist current volatile messages
                val currentVolatile = _volatileMessages.value
                currentVolatile.forEach { msg ->
                    chatDao.insertMessage(
                        ChatMessageEntity(
                            id = msg.id,
                            sessionId = sessionId,
                            role = msg.role,
                            text = msg.text,
                            imageUri = msg.imageUri,
                            audioUri = msg.audioUri
                        )
                    )
                }
                _volatileMessages.value = emptyList()
            }
        }
    }

    fun getSessionFlow(sessionId: String): Flow<ChatSession?> {
        return chatDao.getSessionFlowById(sessionId)
    }

    private suspend fun prepareInferenceForSession(sessionId: String, excludeMsgId: String? = null) {
        if (inferenceSessionId == sessionId) return
        
        val session = chatDao.getSessionById(sessionId)
        val isIncognito = session?.isIncognito ?: false
        val longTermMemory = session?.longTermMemory ?: ""
        val persona = session?.persona ?: ""
        
        val maxTokensTotal = settingsManager.maxTokens.first()
        // Reserve 30% for the new prompt and response
        // APPLY 10% SAFETY BUFFER on the remaining 70% to prevent overflow crashes
        val contextBudget = (maxTokensTotal * 0.7 * 0.9).toInt()
        
        val basePromptTokens = gemmaInference.estimateTokens(BASE_SYSTEM_PROMPT)
        val instructionsTokens = gemmaInference.estimateTokens(persona)
        val memoryTokens = gemmaInference.estimateTokens(longTermMemory)
        
        var remainingBudget = contextBudget - basePromptTokens - instructionsTokens - memoryTokens
        
        // Safety check
        if (remainingBudget < 0) remainingBudget = (maxTokensTotal * 0.2).toInt()

        // Fetch recent history from correct source
        val historyToInclude = mutableListOf<String>()
        var historyTokens = 0
        
        if (isIncognito) {
            val volatileList = _volatileMessages.value.reversed()
            for (msg in volatileList) {
                if (msg.id == excludeMsgId) continue
                val messageText = "${msg.role}: ${msg.text}"
                val tokens = gemmaInference.estimateTokens(messageText)
                if (historyTokens + tokens < remainingBudget) {
                    historyToInclude.add(0, messageText)
                    historyTokens += tokens
                } else break
            }
        } else {
            val recentEntities = chatDao.getRecentMessagesForSession(sessionId, 20)
            for (entity in recentEntities) {
                if (entity.id == excludeMsgId) continue
                val messageText = "${entity.role}: ${entity.text}"
                val tokens = gemmaInference.estimateTokens(messageText)
                if (historyTokens + tokens < remainingBudget) {
                    historyToInclude.add(0, messageText) // Add to start to maintain order (since recentEntities is DESC)
                    historyTokens += tokens
                } else {
                    break // Budget exceeded
                }
            }
        }
        
        val recentHistoryText = historyToInclude.joinToString("\n")
        
        val systemPrompt = buildString {
            append(BASE_SYSTEM_PROMPT)
            if (persona.isNotEmpty()) {
                append("\n\nUSER-DEFINED PERSONA/INSTRUCTIONS:\n")
                append(persona)
            }
            if (longTermMemory.isNotEmpty()) {
                append("\n\nLONG-TERM MEMORY (PAST FACTS):\n")
                append(longTermMemory)
            }
            if (recentHistoryText.isNotEmpty()) {
                append("\n\nRECENT CONVERSATION HISTORY:\n")
                append(recentHistoryText)
            }
        }
        
        val temp = settingsManager.temperature.first()
        gemmaInference.resetConversation(temp, maxTokensTotal, systemPrompt)
        
        inferenceSessionId = sessionId
    }

    fun stopGeneration() {
        inferenceJob?.cancel()
        _isGenerating.value = false
        _streamingMessage.value = null
    }

    fun startRecording() {
        val file = File(getApplication<Application>().cacheDir, "audio_${System.currentTimeMillis()}.wav")
        currentAudioFile = file
        audioRecorder.start(file)
        _isRecording.value = true
    }

    fun stopRecording() {
        audioRecorder.stop()
        _isRecording.value = false
        _pendingAudioUri.value = currentAudioFile?.toURI().toString()
    }

    fun unlock(password: String): Boolean {
        var success = false
        viewModelScope.launch {
            val storedPassword = settingsManager.appLockPassword.first()
            if (password == storedPassword) {
                _isUnlocked.value = true
                success = true
            }
        }
        return true
    }

    suspend fun verifyAndUnlock(password: String): Boolean {
        val storedPassword = settingsManager.appLockPassword.first()
        return if (password == storedPassword) {
            _isUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun setPendingImage(uri: String?) {
        _pendingImageUri.value = uri
    }

    fun setPendingAudio(uri: String?) {
        _pendingAudioUri.value = uri
    }

    fun toggleFavorite(sessionId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            chatDao.updateSessionFavorite(sessionId, isFavorite)
        }
    }

    fun updateSessionImage(sessionId: String, imageUri: String?) {
        viewModelScope.launch {
            chatDao.updateSessionImage(sessionId, imageUri)
        }
    }

    fun updateSessionBackground(sessionId: String, backgroundUri: String?) {
        viewModelScope.launch {
            chatDao.updateSessionBackground(sessionId, backgroundUri)
        }
    }

    fun duplicateSession(sessionId: String) {
        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId) ?: return@launch
            val newSession = session.copy(
                id = UUID.randomUUID().toString(),
                title = "${session.title} (Copy)",
                timestamp = System.currentTimeMillis(),
                isFavorite = false
            )
            chatDao.insertSession(newSession)
            
            // Copy messages
            val messagesList = chatDao.getMessagesForSession(sessionId).first()
            messagesList.forEach { entity ->
                chatDao.insertMessage(entity.copy(id = UUID.randomUUID().toString(), sessionId = newSession.id))
            }
        }
    }

    suspend fun getFullChatText(sessionId: String): String {
        val messagesList = chatDao.getMessagesForSession(sessionId).first()
        return messagesList.joinToString("\n\n") { "${it.role}: ${it.text}" }
    }

    fun updateSessionPersona(sessionId: String, persona: String?) {
        viewModelScope.launch {
            chatDao.updateSessionPersona(sessionId, persona)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null // Force re-init on next message
            }
        }
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        viewModelScope.launch {
            chatDao.updateSessionTitle(sessionId, title)
        }
    }

    fun deleteMessageById(messageId: String) {
        viewModelScope.launch {
            val session = currentSessionId?.let { chatDao.getSessionById(it) }
            if (session?.isIncognito == true) {
                _volatileMessages.update { list -> list.filter { it.id != messageId } }
            } else {
                chatDao.deleteMessageById(messageId)
            }
        }
    }

    fun updateSessionMemory(sessionId: String, memory: String?) {
        viewModelScope.launch {
            chatDao.updateSessionMemory(sessionId, memory)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null
            }
        }
    }

    fun undoLastExchange() {
        val sessionId = currentSessionId ?: return
        if (_isGenerating.value) return
        
        viewModelScope.launch {
            // Ensure any stale context is cleared
            stopGeneration()
            
            // Delete last two messages (Assistant then User)
            val lastMsg = chatDao.getLastMessageForSession(sessionId)
            if (lastMsg != null) {
                chatDao.deleteMessageById(lastMsg.id)
                val precedingMsg = chatDao.getLastMessageForSession(sessionId)
                if (precedingMsg?.role == MessageRole.USER) {
                    chatDao.deleteMessageById(precedingMsg.id)
                }
            }
            // Reset inference to clear context of deleted messages
            inferenceSessionId = null
        }
    }

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun setUnlocked(unlocked: Boolean) {
        _isUnlocked.value = unlocked
        if (unlocked) _isTransientUnlock.value = false
    }

    fun setTransientUnlock(unlocked: Boolean) {
        _isTransientUnlock.value = unlocked
    }

    fun regenerateResponse() {
        val sessionId = currentSessionId ?: return
        // Force stop any hanging generation
        stopGeneration()
        
        viewModelScope.launch {
            try {
                val lastMsg = chatDao.getLastMessageForSession(sessionId)
                if (lastMsg?.role == MessageRole.ASSISTANT) {
                    // 1. Remove the old response
                    chatDao.deleteMessageById(lastMsg.id)
                    
                    // 2. Find the prompt that triggered it
                    val userMsg = chatDao.getLastMessageForSession(sessionId)
                    if (userMsg?.role == MessageRole.USER) {
                        // 3. Re-trigger inference
                        retriggerInference(userMsg.text, userMsg.imageUri, userMsg.audioUri)
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                ModelPersistenceService.updateStatus(getApplication(), false)
            }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val sessionId = currentSessionId ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            try {
                val session = chatDao.getSessionById(sessionId)
                if (session?.isIncognito == true) {
                    val messagesList = _volatileMessages.value
                    val targetMsg = messagesList.find { it.id == messageId }
                    if (targetMsg != null) {
                        // In incognito, we just re-trigger from this point
                        // Wiping subsequent messages in RAM
                        val index = messagesList.indexOf(targetMsg)
                        _volatileMessages.value = messagesList.take(index + 1).map {
                            if (it.id == messageId) it.copy(text = newText) else it
                        }
                        retriggerInference(newText, targetMsg.imageUri, targetMsg.audioUri, messageId)
                    }
                } else {
                    val messagesList = chatDao.getMessagesForSession(sessionId).first()
                    val targetEntity = messagesList.find { it.id == messageId }
                    if (targetEntity != null) {
                        // 1. Update the text in DB
                        chatDao.updateMessageText(messageId, newText)
                        // 2. Delete everything AFTER this message to prevent context mismatch
                        chatDao.deleteMessagesAfter(sessionId, targetEntity.timestamp)
                        
                        // 3. Re-trigger inference
                        retriggerInference(newText, targetEntity.imageUri, targetEntity.audioUri, messageId)
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                ModelPersistenceService.updateStatus(getApplication(), false)
            }
        }
    }

    private fun retriggerInference(text: String, imageUri: String?, audioUri: String?, currentMsgId: String? = null) {
        val sessionId = currentSessionId ?: return
        
        _isGenerating.value = true
        inferenceJob?.cancel()

        inferenceJob = viewModelScope.launch {
            val assistantMessageId = UUID.randomUUID().toString()
            var retryCount = 0
            val maxRetries = 2
            
            try {
                ModelPersistenceService.updateStatus(getApplication(), true)
                
                var success = false
                while (!success && retryCount <= maxRetries) {
                    try {
                        // FORCE context refresh BUT without the turn we just added
                        // We set inferenceSessionId to a dummy value so prepareInferenceForSession runs
                        // but it might see the latest message.
                        // Actually, prepareInferenceForSession should probably be called BEFORE inserting the msg
                        // or we should filter the history.
                        inferenceSessionId = null 
                        prepareInferenceForSession(sessionId, currentMsgId)

                        val session = chatDao.getSessionById(sessionId)
                        val persona = session?.persona ?: ""
                        val longTermMemory = session?.longTermMemory ?: ""
                        val isIncognito = session?.isIncognito ?: false
                        val systemDirective = "IMPORTANT: Use raw text only. Do NOT use asterisks or symbols."

                        val responseAccumulator = StringBuilder()
                        
                        if (modelStatus.value is ModelStatus.Ready) {
                            // Start streaming UI feedback
                            _streamingMessage.value = ChatMessage(
                                id = assistantMessageId,
                                role = MessageRole.ASSISTANT,
                                text = "",
                                isStreaming = true
                            )

                            // Concurrent Pipe: Send raw turn directly to engine
                            gemmaInference.sendMessage(text, imageUri, audioUri, persona, systemDirective, longTermMemory)?.collect { token ->
                                responseAccumulator.append(token)
                                _streamingMessage.update { it?.copy(text = responseAccumulator.toString()) }
                            }
                            
                            val finalResponse = responseAccumulator.toString()
                            if (finalResponse.isNotBlank()) {
                                if (isIncognito) {
                                    val aiMsg = ChatMessage(
                                        id = assistantMessageId,
                                        role = MessageRole.ASSISTANT,
                                        text = finalResponse
                                    )
                                    _volatileMessages.update { it + aiMsg }
                                } else {
                                    chatDao.insertMessage(
                                        ChatMessageEntity(
                                            id = assistantMessageId,
                                            sessionId = sessionId,
                                            role = MessageRole.ASSISTANT,
                                            text = finalResponse
                                        )
                                    )
                                }
                                success = true
                            } else {
                                throw IllegalStateException("AI generated an empty response.")
                            }
                            
                            generateLongTermMemory(sessionId)
                        } else {
                            throw IllegalStateException("AI Engine is not ready.")
                        }
                    } catch (e: Exception) {
                        val isNotAlive = e.message?.contains("not alive", ignoreCase = true) == true
                        if (isNotAlive && retryCount < maxRetries) {
                            retryCount++
                            delay(500) // Brief pause before retry
                        } else {
                            throw e // Bubble up for final error message
                        }
                    }
                }
                
            } catch (e: Exception) {
                if (retryCount >= maxRetries) {
                    val errorMsg = "\n[Inference Error: ${e.message}. Please try again.]"
                    val session = chatDao.getSessionById(sessionId)
                    if (session?.isIncognito == true) {
                        _volatileMessages.update { it + ChatMessage(id = assistantMessageId, role = MessageRole.ASSISTANT, text = errorMsg) }
                    } else {
                        chatDao.insertMessage(
                            ChatMessageEntity(
                                id = assistantMessageId,
                                sessionId = sessionId,
                                role = MessageRole.ASSISTANT,
                                text = errorMsg
                            )
                        )
                    }
                }
            } finally {
                _streamingMessage.value = null
                _isGenerating.value = false
                ModelPersistenceService.updateStatus(getApplication(), false)
            }
        }
    }

    fun clearChatMemory(sessionId: String) {
        viewModelScope.launch {
            chatDao.updateSessionMemory(sessionId, null)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null
            }
        }
    }

    fun estimateTokens(text: String): Int {
        return gemmaInference.estimateTokens(text)
    }

    fun closeSession() {
        val sessionId = currentSessionId ?: return
        
        stopGeneration()
        stopSpeaking()
        gemmaInference.cleanupMediaCache()

        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId)
            
            // TRIPLE WIPE: RAM, Disk, and Engine
            if (session?.isIncognito == true) {
                // 1. Wipe Disk (Room)
                chatDao.deleteMessagesForSession(sessionId)
                chatDao.deleteSession(session)
            }
            
            // 2. Wipe RAM
            _volatileMessages.value = emptyList()
            currentSessionId = null
            _currentSessionIdFlow.value = null
            inferenceSessionId = null
            
            // 3. Wipe Engine KV Cache
            gemmaInference.resetConversation()
        }
    }

    fun clearChat() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            stopGeneration()
            chatDao.deleteMessagesForSession(sessionId)
            chatDao.updateSessionMemory(sessionId, null)
        }
    }

    override fun onCleared() {
        // Cleanup incognito sessions on clear
        viewModelScope.launch {
            chatDao.deleteIncognitoSessions()
        }
    }
}
