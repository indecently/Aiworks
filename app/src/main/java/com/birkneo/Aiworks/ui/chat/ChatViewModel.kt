package com.birkneo.Aiworks.ui.chat

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.HFModel
import com.birkneo.Aiworks.ai.HFModelUIState
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.isolates.SessionSortOrder
import com.birkneo.Aiworks.util.HuggingFaceClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    internal val gemmaInference = GemmaContainer.getGemmaInference(application)
    internal val embeddingInference = GemmaContainer.getEmbeddingInference(application)
    internal val settingsManager = GemmaContainer.getSettingsManager(application)
    internal val repository = GemmaContainer.getChatRepository(application)
    internal val audioRecorder = GemmaContainer.getAudioRecorder(application)
    internal val ttsManager = GemmaContainer.getTtsManager(application)

    internal val _currentSessionIdFlow = MutableStateFlow<String?>(null)
    internal val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

    // OPTIMIZATION: Separate DB messages from the transient streaming message.
    // This prevents the entire message list from re-calculating on every incoming token.
    // We also reverse the list here once to avoid .asReversed() churn in the UI.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dbMessages: StateFlow<List<ChatMessage>> = _currentSessionIdFlow
        .flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList())
            else repository.getMessagesForSession(sessionId)
        }
        .map { it.asReversed() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    internal val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    internal val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    internal val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    internal val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val isNpuSupported: Boolean = gemmaInference.isNpuSupported()

    internal val _isTransientUnlock = MutableStateFlow(false)
    val isTransientUnlock: StateFlow<Boolean> = _isTransientUnlock.asStateFlow()

    internal val _pendingIncognitoChat = MutableStateFlow(false)
    val pendingIncognitoChat: StateFlow<Boolean> = _pendingIncognitoChat.asStateFlow()

    val recordingAmplitude: StateFlow<Float> = audioRecorder.amplitude
        .stateIn(viewModelScope, SharingStarted.Lazily, 0f)

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SessionSortOrder.DATE_NEWEST)
    val sortOrder: StateFlow<SessionSortOrder> = _sortOrder.asStateFlow()

    /**
     * OPTIMIZATION: Filtering and Sorting moved to ViewModel to ensure the UI 
     * receives a pre-processed list, preventing jank on the main thread during 
     * rapid search or scroll.
     */
    val filteredSessions: StateFlow<List<ChatSession>> = combine(
        sessions, _searchQuery, _sortOrder
    ) { sessionsList, query, order ->
        if (query.isEmpty()) {
            sortSessions(sessionsList, order)
        } else {
            val filtered = sessionsList.filter { it.title.contains(query, ignoreCase = true) }
            sortSessions(filtered, order)
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sortSessions(list: List<ChatSession>, order: SessionSortOrder): List<ChatSession> {
        return when (order) {
            SessionSortOrder.DATE_NEWEST -> list.sortedByDescending { it.timestamp }
            SessionSortOrder.DATE_OLDEST -> list.sortedBy { it.timestamp }
            SessionSortOrder.NAME -> list.sortedBy { it.title }
            SessionSortOrder.FAVORITES -> list.sortedByDescending { it.isFavorite }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SessionSortOrder) {
        _sortOrder.value = order
    }

    internal var currentSessionId: String? = null
    internal var inferenceSessionId: String? = null
    internal var inferenceJob: Job? = null
    internal var messageCollectionJob: Job? = null
    internal var currentAudioFile: File? = null
    
    internal val engineMutex = Mutex()

    internal var cachedSystemPrompt: String? = null
    internal var cachedTemperature: Double? = null
    internal var cachedTopK: Int? = null
    internal var cachedTopP: Double? = null
    internal var cachedMaxTokens: Int? = null
    internal val sessionMessageCounters = mutableMapOf<String, Int>()

    internal val _pendingImageUri = MutableStateFlow<String?>(null)
    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    internal val _pendingAudioUri = MutableStateFlow<String?>(null)
    val pendingAudioUri: StateFlow<String?> = _pendingAudioUri.asStateFlow()

    internal val _isCompressingContext = MutableStateFlow(false)
    val isCompressingContext: StateFlow<Boolean> = _isCompressingContext.asStateFlow()

    // Developer Inspection States
    private val _lastRawPrompt = MutableStateFlow("")
    val lastRawPrompt: StateFlow<String> = _lastRawPrompt.asStateFlow()

    private val _lastContextSummary = MutableStateFlow("")
    val lastContextSummary: StateFlow<String> = _lastContextSummary.asStateFlow()

    private val _ttftMs = MutableStateFlow(0L)
    val ttftMs: StateFlow<Long> = _ttftMs.asStateFlow()

    private val _generationSpeed = MutableStateFlow(0f)
    val generationSpeed: StateFlow<Float> = _generationSpeed.asStateFlow()

    // Hugging Face Integration
    private val hfClient = HuggingFaceClient()
    private val _hfModels = MutableStateFlow<List<HFModel>>(emptyList())
    private val _hfSearchQuery = MutableStateFlow("litert")
    val hfSearchQuery: StateFlow<String> = _hfSearchQuery.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val _isFetchingHF = MutableStateFlow(false)
    val isFetchingHF: StateFlow<Boolean> = _isFetchingHF.asStateFlow()

    private val _downloadedModels = MutableStateFlow<List<File>>(emptyList())
    val downloadedModels: StateFlow<List<File>> = _downloadedModels.asStateFlow()

    val hfAccessEnabled: StateFlow<Boolean> = settingsManager.hfAccessEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hfModelsUIState: StateFlow<List<HFModelUIState>> = combine(
        _hfModels, _downloadProgress
    ) { models, progressMap ->
        val totalRam = getTotalRam()
        models.map { model ->
            val localFile = File(getApplication<Application>().filesDir, "models/${model.id.replace("/", "_")}.litertlm")
            val targetSibling = model.siblings?.find { it.rfilename.endsWith(".litertlm") || it.rfilename.endsWith(".bin") || it.rfilename.endsWith(".tflite") }
            
            // Priority: sibling.size -> sibling.lfs.size -> 0L
            var fileSize = targetSibling?.size ?: targetSibling?.lfs?.size ?: 0L
            
            // FALLBACK: If size is still 0, try a quick tree fetch if we're in IO context
            // Note: In a StateFlow combine, we should avoid blocking. 
            // We'll rely on the enhanced search query for now.

            // Extract quantization from tags or name
            val qTags = mutableListOf<String>()
            val searchPool = (model.tags ?: emptyList()) + model.id
            
            val is4bit = searchPool.any { it.contains("int4", true) || it.contains("4bit", true) || it.contains("q4", true) }
            val is8bit = searchPool.any { it.contains("int8", true) || it.contains("8bit", true) || it.contains("q8", true) }
            val isFp16 = searchPool.any { it.contains("fp16", true) || it.contains("f16", true) }
            
            if (is4bit) qTags.add("INT4")
            if (is8bit) qTags.add("INT8")
            if (isFp16) qTags.add("FP16")
            
            // RAM Warning Logic
            val hasWarning = when {
                fileSize > (totalRam * 0.35) && totalRam > 0 -> true
                model.id.contains("7b", true) && totalRam < 8L * 1024 * 1024 * 1024 -> true
                model.id.contains("4b", true) && totalRam < 6L * 1024 * 1024 * 1024 -> true
                else -> false
            }

            HFModelUIState(
                model = model,
                isDownloaded = localFile.exists(),
                localPath = if (localFile.exists()) localFile.absolutePath else null,
                downloadProgress = progressMap[model.id],
                isDownloading = progressMap.containsKey(model.id),
                formattedSize = if (fileSize > 0) formatFileSize(fileSize) else null,
                hasRamWarning = hasWarning,
                quantizationTags = qTags
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getTotalRam(): Long {
        val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }

    fun fetchHuggingFaceModels(query: String? = null) {
        if (_isFetchingHF.value || !hfAccessEnabled.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isFetchingHF.value = true
            val searchQuery = query ?: _hfSearchQuery.value
            val models = hfClient.fetchModels(searchQuery)
            _hfModels.value = models
            _isFetchingHF.value = false
        }
    }

    fun setHfAccessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setHfAccessEnabled(enabled)
            if (enabled) {
                fetchHuggingFaceModels()
            } else {
                _hfModels.value = emptyList()
            }
        }
    }

    fun onHFSearchQueryChanged(query: String) {
        _hfSearchQuery.value = query
    }

    fun refreshDownloadedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelsDir = File(getApplication<Application>().filesDir, "models")
            if (modelsDir.exists()) {
                val files = modelsDir.listFiles { file -> 
                    file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin") || file.name.endsWith(".tflite"))
                }?.toList() ?: emptyList()
                _downloadedModels.value = files
            } else {
                _downloadedModels.value = emptyList()
            }
        }
    }

    fun downloadHFModel(model: HFModel) {
        val fileName = model.siblings?.find { it.rfilename.endsWith(".litertlm") || it.rfilename.endsWith(".bin") || it.rfilename.endsWith(".tflite") }?.rfilename ?: return
        val destFile = File(getApplication<Application>().filesDir, "models/${model.id.replace("/", "_")}.litertlm")
        
        viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.update { it + (model.id to 0f) }
            val success = hfClient.downloadModel(model.id, fileName, destFile) { progress ->
                _downloadProgress.update { it + (model.id to progress) }
            }
            if (success) {
                // Refresh state
                _downloadProgress.update { it - model.id }
                refreshDownloadedModels()
            } else {
                _downloadProgress.update { it - model.id }
                // Handle error
            }
        }
    }

    fun deleteHFModel(modelId: String) {
        val destFile = File(getApplication<Application>().filesDir, "models/${modelId.replace("/", "_")}.litertlm")
        if (destFile.exists()) {
            destFile.delete()
            // Trigger UI refresh by slightly updating a flow if needed, 
            // but hfModelsUIState re-calculates on its own if _hfModels or _downloadProgress change.
            // Since we're not changing those, we might need a dummy trigger or rely on next poll.
            // Let's force an update to _downloadProgress to trigger recomposition.
            _downloadProgress.update { it } 
            refreshDownloadedModels()
        }
    }

    fun updateInspectionData(prompt: String, summary: String) {
        viewModelScope.launch {
            if (settingsManager.livePromptLogging.first()) {
                _lastRawPrompt.value = prompt
                _lastContextSummary.value = summary
            } else {
                _lastRawPrompt.value = ""
                _lastContextSummary.value = ""
            }
        }
    }

    fun updateVitals(ttft: Long, speed: Float) {
        _ttftMs.value = ttft
        _generationSpeed.value = speed
    }

    init {
        gemmaInference.cleanupMediaCache()
        refreshDownloadedModels()
        val internalModelFile = File(getApplication<Application>().filesDir, "model.litertlm")
        val hasLocalModel = internalModelFile.exists()

        viewModelScope.launch {
            // OPTIMIZATION: Move blocking DB/IO operations to background dispatcher
            withContext(Dispatchers.IO) {
                repository.deleteIncognitoSessions()
            }

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

            // AUTO-RELOAD on hardware settings change
            settingsManager.computeAccelerator
                .drop(1) // Skip initial value
                .collect { 
                    val currentPath = settingsManager.modelPath.first()
                    if (!currentPath.isNullOrEmpty()) {
                        loadModel(currentPath)
                    }
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

            val status = gemmaInference.loadModel(
                modelPathOrUri = path,
                temperature = temperature,
                maxTokens = maxTokens,
                topK = topK,
                topP = topP,
                accelerator = accelerator
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

    fun stopGeneration() {
        inferenceJob?.cancel()
        _isGenerating.value = false
        _streamingMessage.value = null
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

    fun triggerIncognitoChat() {
        _pendingIncognitoChat.value = true
        _isTransientUnlock.value = true
    }

    fun consumeIncognitoChat() {
        _pendingIncognitoChat.value = false
    }

    fun nukeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            com.birkneo.Aiworks.data.database.ChatDatabase.getDatabase(getApplication()).clearAllTables()
            repository.nukeVolatileMessages()
            _currentSessionIdFlow.value = null
        }
    }

    fun setVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setVerboseLogging(enabled)
        }
    }

    fun setLivePromptLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLivePromptLogging(enabled)
            if (!enabled) {
                _lastRawPrompt.value = ""
                _lastContextSummary.value = ""
            }
        }
    }

    fun updateInferenceConfig() {
        viewModelScope.launch {
            val temperature = settingsManager.temperature.first()
            val maxTokens = settingsManager.maxTokens.first()
            val topK = settingsManager.topK.first()
            val topP = settingsManager.topP.first()
            
            gemmaInference.resetConversation(
                temperature = temperature,
                maxTokens = maxTokens,
                topK = topK,
                topP = topP
            )
        }
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

    fun estimateTokens(text: String): Int = gemmaInference.estimateTokens(text)

    override fun onCleared() {
        viewModelScope.launch {
            repository.deleteIncognitoSessions()
        }
    }
}
