package com.birkneo.Aiworks.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.ai.PromptArchitect
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.util.MediaPersistenceManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val gemmaInference = GemmaContainer.getGemmaInference(application)
    private val settingsManager = GemmaContainer.getSettingsManager(application)
    private val repository = GemmaContainer.getChatRepository(application)
    private val audioRecorder = GemmaContainer.getAudioRecorder(application)
    private val ttsManager = GemmaContainer.getTtsManager(application)

    private val _currentSessionIdFlow = MutableStateFlow<String?>(null)
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionIdFlow
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessagesForSession(sessionId)
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

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var currentSessionId: String? = null
    private var inferenceSessionId: String? = null
    private var inferenceJob: Job? = null
    private var messageCollectionJob: Job? = null
    private var currentAudioFile: File? = null
    
    // SERIALIZATION: Protect LiteRT engine from concurrent Summary vs Chat tasks
    private val engineMutex = Mutex()

    private var cachedSystemPrompt: String? = null
    private var cachedTemperature: Double? = null
    private var cachedTopK: Int? = null
    private var cachedTopP: Double? = null
    private var cachedMaxTokens: Int? = null
    private val sessionMessageCounters = mutableMapOf<String, Int>()

    private val _pendingImageUri = MutableStateFlow<String?>(null)
    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    private val _pendingAudioUri = MutableStateFlow<String?>(null)
    val pendingAudioUri: StateFlow<String?> = _pendingAudioUri.asStateFlow()

    private val _isCompressingContext = MutableStateFlow(false)
    val isCompressingContext: StateFlow<Boolean> = _isCompressingContext.asStateFlow()

    init {
        gemmaInference.cleanupMediaCache()
        val internalModelFile = File(getApplication<Application>().filesDir, "model.litertlm")
        val hasLocalModel = internalModelFile.exists()

        viewModelScope.launch {
            repository.deleteIncognitoSessions()
            val lockEnabled = settingsManager.appLockEnabled.first()
            if (lockEnabled) {
                combine(_isUnlocked, _isTransientUnlock) { unlocked, transient ->
                    unlocked || transient
                }.first { it }
            }

            if (_isTransientUnlock.value) {
                delay(500)
            }

            var path = settingsManager.modelPath.first()
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
        stopGeneration()
        stopSpeaking()
        currentSessionId = sessionId
        _currentSessionIdFlow.value = sessionId
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            prepareInferenceForSession(sessionId)
        }
    }

    fun createNewSession(title: String = "New Chat", isIncognito: Boolean = false, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val session = ChatSession(title = title, isIncognito = isIncognito)
            repository.insertSession(session)
            onCreated(session.id)
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            repository.clearMessagesForSession(session.id)
            repository.deleteSession(session)
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
            val topK = settingsManager.topK.first()
            val topP = settingsManager.topP.first()
            val accelerator = settingsManager.computeAccelerator.first()
            val useGpu = accelerator == "GPU"

            val status = gemmaInference.loadModel(
                modelPathOrUri = path,
                temperature = temperature,
                maxTokens = maxTokens,
                topK = topK,
                topP = topP,
                useGpu = useGpu
            ) { progress ->
                _modelStatus.value = ModelStatus.Loading(progress)
            }
            _modelStatus.value = status
            if (status is ModelStatus.Ready) {
                ModelPersistenceService.start(getApplication())
                inferenceSessionId = null
                gemmaInference.getLoadedModelPath()?.let { loadedPath ->
                    settingsManager.setModelPath(loadedPath)
                }
            } else if (status is ModelStatus.Error) {
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
            val session = repository.getSessionById(sessionId)
            val msgId = UUID.randomUUID().toString()
            
            // PERSIST MEDIA: Move files from temp cache/content URIs to permanent internal storage
            val persistedImage = MediaPersistenceManager.persistMedia(getApplication(), imageUri)
            val persistedAudio = MediaPersistenceManager.persistMedia(getApplication(), audioUri)

            repository.insertMessage(sessionId, ChatMessage(
                id = msgId,
                role = MessageRole.USER,
                text = text,
                imageUri = persistedImage,
                audioUri = persistedAudio
            ))
            
            if (session?.title == "New Chat") {
                val newTitle = if (text.length > 20) text.take(17) + "..." else if (text.isBlank()) "Media Message" else text
                repository.updateSessionTitle(sessionId, newTitle)
            }
            
            // Use the persisted URIs for inference as well for consistency
            retriggerInference(text, persistedImage, persistedAudio, msgId)

            val count = (sessionMessageCounters[sessionId] ?: 0) + 1
            sessionMessageCounters[sessionId] = count
            if (count >= 5) {
                sessionMessageCounters[sessionId] = 0
                generateLongTermMemory(sessionId)
            }
        }
    }

    fun generateLongTermMemory(sessionId: String) {
        viewModelScope.launch {
            engineMutex.withLock {
                val session = repository.getSessionById(sessionId)
                val msgList = repository.getRecentMessages(sessionId, 10)
                
                if (msgList.size < 2) return@withLock 
                
                _isCompressingContext.value = true
                try {
                    val textToSummarize = msgList.filter { it.text.isNotBlank() }
                        .joinToString("\n") { "${it.role}: ${it.text}" }

                    if (textToSummarize.isBlank()) return@withLock

                    val currentMemory = session?.longTermMemory ?: ""
                    val newSummary = if (currentMemory.length > 1500) {
                        gemmaInference.distillMemory(currentMemory, textToSummarize)
                    } else {
                        val summary = gemmaInference.summarize(textToSummarize)
                        if (summary != null) {
                            if (currentMemory.isEmpty()) summary else "$currentMemory\n$summary"
                        } else null
                    }
                    
                    if (newSummary != null) {
                        repository.updateSessionMemory(sessionId, newSummary)
                    }

                    if (currentSessionId == sessionId) {
                        cachedSystemPrompt = null
                        // No need to call prepareInference here as it will be called by next message
                        // and we already nulled inferenceSessionId or cachedSystemPrompt
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isCompressingContext.value = false
                }
            }
        }
    }

    fun toggleIncognito(sessionId: String, isIncognito: Boolean) {
        viewModelScope.launch {
            repository.updateSessionIncognito(sessionId, isIncognito)
            if (!isIncognito) {
                repository.convertIncognitoToRegular(sessionId)
            }
        }
    }

    fun updateSessionReasoning(sessionId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSessionReasoning(sessionId, enabled)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null
            }
        }
    }

    fun getSessionFlow(sessionId: String): Flow<ChatSession?> {
        return repository.getSessionFlowById(sessionId)
    }

    private suspend fun prepareInferenceForSession(sessionId: String, excludeMsgId: String? = null) {
        val temp = settingsManager.temperature.first()
        val topK = settingsManager.topK.first()
        val topP = settingsManager.topP.first()
        val maxTokensTotal = settingsManager.maxTokens.first()

        // FIXED: Now checking if parameters changed to ensure Settings are applied mid-session
        if (inferenceSessionId == sessionId && 
            cachedSystemPrompt != null && 
            cachedTemperature == temp &&
            cachedTopK == topK &&
            cachedTopP == topP &&
            cachedMaxTokens == maxTokensTotal &&
            gemmaInference.isConversationActive()) return
        
        val session = repository.getSessionById(sessionId)
        val longTermMemory = session?.longTermMemory ?: ""
        val persona = session?.persona ?: ""
        
        val contextBudget = (maxTokensTotal * 0.9).toInt()
        
        val basePromptTokens = gemmaInference.estimateTokens(PromptArchitect.BASE_SYSTEM_PROMPT)
        val instructionsTokens = gemmaInference.estimateTokens(persona)
        val memoryTokens = gemmaInference.estimateTokens(longTermMemory)
        
        var remainingBudgetForWindow = contextBudget - basePromptTokens - instructionsTokens - memoryTokens
        if (remainingBudgetForWindow < 100) remainingBudgetForWindow = 100

        val historyToInclude = mutableListOf<String>()
        var historyTokens = 0
        
        val recentMsgs = repository.getRecentMessages(sessionId, 50).reversed()
        for (msg in recentMsgs) {
            if (msg.id == excludeMsgId) continue
            val messageText = "${msg.role}: ${msg.text}"
            val tokens = gemmaInference.estimateTokens(messageText)
            if (historyTokens + tokens < remainingBudgetForWindow) {
                historyToInclude.add(0, messageText)
                historyTokens += tokens
            } else break
        }
        
        val recentHistoryText = historyToInclude.joinToString("\n")
        
        val systemPrompt = PromptArchitect.constructSystemPrompt(
            session = session,
            longTermMemory = longTermMemory,
            persona = persona,
            recentHistoryText = recentHistoryText
        )
        
        cachedSystemPrompt = systemPrompt
        cachedTemperature = temp
        cachedTopK = topK
        cachedTopP = topP
        cachedMaxTokens = maxTokensTotal

        gemmaInference.resetConversation(
            temperature = temp, 
            maxTokens = maxTokensTotal, 
            topK = topK, 
            topP = topP, 
            systemInstruction = systemPrompt
        )
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
        val uri = currentAudioFile?.toURI().toString()
        _pendingAudioUri.value = uri
        // PRACTICAL FIX: Clear pending image when audio recording is captured
        _pendingImageUri.value = null
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
        // PRACTICAL FIX: Prevent OpenCL crash by enforcing one media type per prompt
        if (uri != null) _pendingAudioUri.value = null
    }

    fun setPendingAudio(uri: String?) {
        _pendingAudioUri.value = uri
        // PRACTICAL FIX: Prevent OpenCL crash by enforcing one media type per prompt
        if (uri != null) _pendingImageUri.value = null
    }

    fun toggleFavorite(sessionId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updateSessionFavorite(sessionId, isFavorite)
        }
    }

    fun updateSessionImage(sessionId: String, imageUri: String?) {
        viewModelScope.launch {
            repository.updateSessionImage(sessionId, imageUri)
        }
    }

    fun updateSessionBackground(sessionId: String, backgroundUri: String?) {
        viewModelScope.launch {
            repository.updateSessionBackground(sessionId, backgroundUri)
        }
    }

    fun duplicateSession(sessionId: String) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId) ?: return@launch
            val newSession = session.copy(
                id = UUID.randomUUID().toString(),
                title = "${session.title} (Copy)",
                timestamp = System.currentTimeMillis(),
                isFavorite = false
            )
            repository.insertSession(newSession)
            
            val messagesList = repository.getRecentMessages(sessionId, 1000)
            messagesList.forEach { msg ->
                repository.insertMessage(newSession.id, msg.copy(id = UUID.randomUUID().toString()))
            }
        }
    }

    suspend fun getFullChatText(sessionId: String): String {
        val messagesList = repository.getRecentMessages(sessionId, 1000)
        return messagesList.joinToString("\n\n") { "${it.role}: ${it.text}" }
    }

    fun updateSessionPersona(sessionId: String, persona: String?) {
        viewModelScope.launch {
            repository.updateSessionPersona(sessionId, persona)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null
            }
        }
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, title)
        }
    }

    fun deleteMessageById(messageId: String) {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            // Optional: Cleanup disk space on message deletion
            val recent = repository.getRecentMessages(sessionId, 1000)
            val target = recent.find { it.id == messageId }
            MediaPersistenceManager.deleteMedia(target?.imageUri)
            MediaPersistenceManager.deleteMedia(target?.audioUri)
            
            repository.deleteMessageById(sessionId, messageId)
        }
    }

    fun updateSessionMemory(sessionId: String, memory: String?) {
        viewModelScope.launch {
            repository.updateSessionMemory(sessionId, memory)
            if (currentSessionId == sessionId) {
                inferenceSessionId = null
            }
        }
    }

    fun undoLastExchange() {
        val sessionId = currentSessionId ?: return
        if (_isGenerating.value) return
        
        viewModelScope.launch {
            stopGeneration()
            val lastMsg = repository.getLastMessage(sessionId)
            if (lastMsg != null) {
                repository.deleteMessageById(sessionId, lastMsg.id)
                val precedingMsg = repository.getLastMessage(sessionId)
                if (precedingMsg?.role == MessageRole.USER) {
                    repository.deleteMessageById(sessionId, precedingMsg.id)
                }
            }
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
        stopGeneration()
        
        viewModelScope.launch {
            try {
                val lastMsg = repository.getLastMessage(sessionId)
                if (lastMsg?.role == MessageRole.ASSISTANT) {
                    repository.deleteMessageById(sessionId, lastMsg.id)
                    val userMsg = repository.getLastMessage(sessionId)
                    if (userMsg?.role == MessageRole.USER) {
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
                // FIXED: Now only updates the text in the database.
                // Does NOT trigger retriggerInference or delete subsequent messages.
                repository.updateMessageText(messageId, newText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun retriggerInference(text: String, imageUri: String?, audioUri: String?, currentMsgId: String? = null) {
        val sessionId = currentSessionId ?: return
        _isGenerating.value = true
        inferenceJob?.cancel()

        inferenceJob = viewModelScope.launch {
            engineMutex.withLock {
                val assistantMessageId = UUID.randomUUID().toString()
                var retryCount = 0
                val maxRetries = 2
                
                try {
                    ModelPersistenceService.updateStatus(getApplication(), true)
                    var success = false
                    while (!success && retryCount <= maxRetries) {
                        try {
                            prepareInferenceForSession(sessionId, currentMsgId)
                            val session = repository.getSessionById(sessionId)
                            
                            if (modelStatus.value is ModelStatus.Ready) {
                                val isDeepReasoning = session?.isReasoningMode == true
                                _streamingMessage.value = ChatMessage(
                                    id = assistantMessageId,
                                    role = MessageRole.ASSISTANT,
                                    text = "",
                                    isStreaming = true,
                                    isThinking = isDeepReasoning // PROACTIVE: Start in thinking state
                                )

                                val fullResponseAccumulator = StringBuilder()
                                var thoughtTagFound = false
                                var endTagDetected = false

                                gemmaInference.sendMessage(text, imageUri, audioUri)?.collect { token ->
                                    fullResponseAccumulator.append(token)
                                    val currentFullText = fullResponseAccumulator.toString()
                                    
                                    // 1. Tag Detection
                                    if (!thoughtTagFound && currentFullText.contains("<|channel>thought", ignoreCase = true)) {
                                        thoughtTagFound = true
                                    }
                                    
                                    if (!endTagDetected && currentFullText.contains("<channel|>", ignoreCase = true)) {
                                        endTagDetected = true
                                        _streamingMessage.update { it?.copy(isThinking = false) }
                                    }

                                    // 2. UI Updates
                                    if (isDeepReasoning) {
                                        if (endTagDetected) {
                                            // Extract final answer after the LAST instance of the end tag
                                            val parts = currentFullText.split(Regex("<channel|>", RegexOption.IGNORE_CASE), limit = 2)
                                            if (parts.size > 1) {
                                                _streamingMessage.update { it?.copy(text = parts[1].trimStart()) }
                                            }
                                        } else {
                                            // MASK ALL OUTPUT while reasoning is active
                                            _streamingMessage.update { it?.copy(text = "", isThinking = true) }
                                        }
                                    } else {
                                        // Standard mode: Stream normally
                                        _streamingMessage.update { it?.copy(text = currentFullText.trimStart()) }
                                    }
                                }
                                
                                val finalFullText = fullResponseAccumulator.toString()
                                val finalResponse = if (isDeepReasoning && endTagDetected) {
                                    finalFullText.split(Regex("<channel|>", RegexOption.IGNORE_CASE), limit = 2).lastOrNull()?.trim() ?: ""
                                } else if (isDeepReasoning && !thoughtTagFound && finalFullText.length > 5 && !finalFullText.contains("<")) {
                                    // RECOVERY: If mode is on but model failed to use tags, show content anyway
                                    finalFullText.trim()
                                } else {
                                    finalFullText.trim()
                                }
                                if (finalResponse.isNotBlank()) {
                                    repository.insertMessage(sessionId, ChatMessage(
                                        id = assistantMessageId,
                                        role = MessageRole.ASSISTANT,
                                        text = finalResponse
                                    ))
                                    success = true
                                } else {
                                    throw IllegalStateException("AI generated an empty response.")
                                }
                                // Trigger LTM generation outside the lock to avoid deadlock if called again,
                                // but the trigger in sendMessage already does this.
                            } else {
                                throw IllegalStateException("AI Engine is not ready.")
                            }
                        } catch (e: Exception) {
                            val isNotAlive = e.message?.contains("not alive", ignoreCase = true) == true
                            if (isNotAlive && retryCount < maxRetries) {
                                retryCount++
                                delay(500)
                            } else throw e
                        }
                    }
                } catch (e: Exception) {
                    if (retryCount >= maxRetries) {
                        repository.insertMessage(sessionId, ChatMessage(id = assistantMessageId, role = MessageRole.ASSISTANT, text = "\n[Inference Error: ${e.message}]"))
                    }
                } finally {
                    _streamingMessage.value = null
                    _isGenerating.value = false
                    ModelPersistenceService.updateStatus(getApplication(), false)
                }
            }
        }
    }

    fun clearChatMemory(sessionId: String) {
        viewModelScope.launch {
            repository.updateSessionMemory(sessionId, null)
            if (currentSessionId == sessionId) inferenceSessionId = null
        }
    }

    fun estimateTokens(text: String): Int = gemmaInference.estimateTokens(text)

    fun closeSession() {
        val sessionId = currentSessionId ?: return
        stopGeneration()
        stopSpeaking()
        gemmaInference.cleanupMediaCache()

        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session?.isIncognito == true) {
                repository.clearMessagesForSession(sessionId)
                repository.deleteSession(session)
            }
            currentSessionId = null
            _currentSessionIdFlow.value = null
            inferenceSessionId = null
            gemmaInference.resetConversation()
        }
    }

    fun clearChat() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            stopGeneration()
            repository.clearMessagesForSession(sessionId)
            repository.updateSessionMemory(sessionId, null)
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            repository.deleteIncognitoSessions()
        }
    }
}
