package com.birkneo.Aiworks.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.di.GemmaContainer
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    internal val gemmaInference = GemmaContainer.getGemmaInference(application)
    internal val settingsManager = GemmaContainer.getSettingsManager(application)
    internal val repository = GemmaContainer.getChatRepository(application)
    internal val audioRecorder = GemmaContainer.getAudioRecorder(application)
    internal val ttsManager = GemmaContainer.getTtsManager(application)

    internal val _currentSessionIdFlow = MutableStateFlow<String?>(null)
    internal val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
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

    internal val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    internal val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    internal val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    internal val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    internal val _isTransientUnlock = MutableStateFlow(false)
    val isTransientUnlock: StateFlow<Boolean> = _isTransientUnlock.asStateFlow()

    val recordingAmplitude: StateFlow<Float> = audioRecorder.amplitude
        .stateIn(viewModelScope, SharingStarted.Lazily, 0f)

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
