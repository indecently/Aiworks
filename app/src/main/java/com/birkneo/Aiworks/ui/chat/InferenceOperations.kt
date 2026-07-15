package com.birkneo.Aiworks.ui.chat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.ai.PromptArchitect
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import java.util.UUID

internal suspend fun ChatViewModel.prepareInferenceForSession(
    sessionId: String, 
    userInput: String = "",
    excludeMsgId: String? = null,
    precalculatedRagFragments: List<String>? = null
) {
    val temp = settingsManager.temperature.first()
    val topK = settingsManager.topK.first()
    val topP = settingsManager.topP.first()
    val maxTokensTotal = gemmaInference.currentMaxTokens

    val session = repository.getSessionById(sessionId)
    val fullLtm = session?.longTermMemory ?: ""
    val persona = session?.persona ?: ""

    // 1. Semantic RAG Retrieval
    val ragStartTime = System.currentTimeMillis()
    val relevantLtmFragments = precalculatedRagFragments ?: if (fullLtm.isNotEmpty() && userInput.isNotBlank()) {
        // Split LTM into sentences/chunks for granular retrieval
        val chunks = fullLtm.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.length > 10 }
        
        if (chunks.isNotEmpty()) {
            embeddingInference.rankFragments(userInput, chunks, topK = 5)
        } else emptyList()
    } else emptyList()
    val ragDuration = System.currentTimeMillis() - ragStartTime
    
    // 2. Budget Calculation
    // OPTIMIZATION: Context budget is dynamic based on RAM. 
    // We leave 15% head room for generation.
    val contextBudget = (maxTokensTotal * 0.85).toInt()
    
    // We construct a temporary prompt without history to estimate base overhead (system + persona + RAG)
    val systemPromptWithoutHistory = PromptArchitect.constructSystemPrompt(
        session = session,
        relevantLtmFragments = relevantLtmFragments,
        persona = persona,
        recentHistoryText = ""
    )
    
    val baseTokens = gemmaInference.estimateTokens(systemPromptWithoutHistory)
    var remainingBudgetForWindow = contextBudget - baseTokens
    
    // Guarantee at least 400 tokens for recent chat history to prevent context loss
    if (remainingBudgetForWindow < 400) remainingBudgetForWindow = 400

    val historyToInclude = mutableListOf<String>()
    // Fetch 60 messages to ensure we fill the budget if possible
    val recentMsgs = repository.getRecentMessages(sessionId, 60).reversed()
    
    // Use a more conservative char-to-token ratio (3.0 instead of 2.8) to avoid budget overflow
    val charBudget = (remainingBudgetForWindow * 3.0).toInt() 
    var currentChars = 0

    for (msg in recentMsgs) {
        if (msg.id == excludeMsgId) continue
        val messageText = "${msg.role}: ${msg.text}"
        val msgLength = messageText.length
        
        if (currentChars + msgLength < charBudget) {
            historyToInclude.add(0, messageText)
            currentChars += msgLength
        } else break
    }
    
    val recentHistoryText = historyToInclude.joinToString("\n")
    
    val finalSystemPrompt = PromptArchitect.constructSystemPrompt(
        session = session,
        relevantLtmFragments = relevantLtmFragments,
        persona = persona,
        recentHistoryText = recentHistoryText
    )
    
    val totalEstimatedTokens = gemmaInference.estimateTokens(finalSystemPrompt)

    // Capture inspection data for the Developer Screen
    updateInspectionData(
        prompt = finalSystemPrompt,
        summary = """
            RAG Pre-pass: ${ragDuration}ms
            Relevant LTM Fragments: ${relevantLtmFragments.size}
            Total Estimated Tokens: $totalEstimatedTokens / $maxTokensTotal
            Recent History Messages: ${historyToInclude.size}
            Memory Anchor Size: ${fullLtm.length} chars
        """.trimIndent()
    )
    
    // OPTIMIZATION: Precise KV Cache management. 
    // Only reset the conversation if the actual instructions or hardware params changed.
    if (inferenceSessionId == sessionId &&
        cachedSystemPrompt == finalSystemPrompt &&
        cachedTemperature == temp &&
        cachedTopK == topK &&
        cachedTopP == topP &&
        cachedMaxTokens == maxTokensTotal &&
        gemmaInference.isConversationActive()) return

    cachedSystemPrompt = finalSystemPrompt
    cachedTemperature = temp
    cachedTopK = topK
    cachedTopP = topP
    cachedMaxTokens = maxTokensTotal

    gemmaInference.resetConversation(
        temperature = temp,
        maxTokens = maxTokensTotal,
        topK = topK,
        topP = topP,
        systemInstruction = finalSystemPrompt
    )
    inferenceSessionId = sessionId
}

internal fun ChatViewModel.retriggerInference(text: String, imageUri: String?, audioUri: String?, currentMsgId: String? = null) {
    val sessionId = currentSessionId ?: return
    _isGenerating.value = true
    inferenceJob?.cancel()

    inferenceJob = viewModelScope.launch {
        // PERF: Move heavy processing to a background thread to keep UI thread idle for drawing
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            
            // 1. Semantic RAG Retrieval (Pre-calculate OUTSIDE the engine lock)
            // This allows the AI engine to remain free while we do embedding math.
            val session = repository.getSessionById(sessionId)
            val fullLtm = session?.longTermMemory ?: ""
            val relevantLtmFragments = if (fullLtm.isNotEmpty() && text.isNotBlank()) {
                val chunks = fullLtm.split(Regex("(?<=[.!?])\\s+"))
                    .filter { it.length > 10 }
                if (chunks.isNotEmpty()) {
                    embeddingInference.rankFragments(text, chunks, topK = 5)
                } else emptyList()
            } else emptyList()

            // CRITICAL: Synchronize with background LTM tasks using the engine-level loadMutex.
            // This prevents background summarization from closing the session mid-inference.
            gemmaInference.loadMutex.withLock {
                engineMutex.withLock {
                val assistantMessageId = UUID.randomUUID().toString()
                var retryCount = 0
                val maxRetries = 2
                
                try {
                    ModelPersistenceService.updateStatus(getApplication(), true)
                    var success = false
                    while (!success && retryCount <= maxRetries) {
                        try {
                            // Pass the pre-calculated RAG fragments to avoid redundant work inside the lock
                            prepareInferenceForSession(sessionId, text, currentMsgId, relevantLtmFragments)
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
                                var lastUiUpdateTimestamp = 0L
                                val UI_THROTTLE_MS = 32L // ~30 FPS throttle

                                val startTime = System.currentTimeMillis()
                                var firstTokenTime = 0L
                                var tokenCount = 0

                                gemmaInference.sendMessage(text, imageUri, audioUri)?.collect { token ->
                                    if (firstTokenTime == 0L) {
                                        firstTokenTime = System.currentTimeMillis()
                                        updateVitals(firstTokenTime - startTime, 0f)
                                    }
                                    tokenCount++

                                    fullResponseAccumulator.append(token)
                                    val currentFullText = fullResponseAccumulator.toString()
                                    
                                    // Reasoning Detection (Legacy support for reasoning tags)
                                    if (!thoughtTagFound && currentFullText.contains("<|channel>thought", ignoreCase = true)) {
                                        thoughtTagFound = true
                                    }
                                    
                                    if (!endTagDetected && currentFullText.contains("<channel|>", ignoreCase = true)) {
                                        endTagDetected = true
                                        _streamingMessage.update { it?.copy(isThinking = false) }
                                    }

                                    // UI Update Dispatcher
                                    val now = System.currentTimeMillis()
                                    if (now - lastUiUpdateTimestamp >= UI_THROTTLE_MS || endTagDetected) {
                                        if (isDeepReasoning) {
                                            val parts = currentFullText.split(Regex("<channel|>", RegexOption.IGNORE_CASE), limit = 2)
                                            if (parts.size > 1) {
                                                _streamingMessage.update { it?.copy(text = parts[1].trimStart(), isThinking = false) }
                                            } else {
                                                _streamingMessage.update { it?.copy(text = "", isThinking = true) }
                                            }
                                        } else {
                                            _streamingMessage.update { it?.copy(text = currentFullText.trimStart()) }
                                        }
                                        lastUiUpdateTimestamp = now
                                    }
                                }
                                
                                val finalFullText = fullResponseAccumulator.toString()
                                val finalResponse = if (isDeepReasoning && endTagDetected) {
                                    finalFullText.split(Regex("<channel|>", RegexOption.IGNORE_CASE), limit = 2).lastOrNull()?.trim() ?: ""
                                } else if (isDeepReasoning && !thoughtTagFound && finalFullText.length > 5 && !finalFullText.contains("<")) {
                                    finalFullText.trim()
                                } else {
                                    finalFullText.trim()
                                }
                                
                                if (finalResponse.isNotBlank()) {
                                    val endTime = System.currentTimeMillis()
                                    val totalDuration = (endTime - firstTokenTime) / 1000f
                                    if (totalDuration > 0) {
                                        updateVitals(firstTokenTime - startTime, tokenCount / totalDuration)
                                    }

                                    repository.insertMessage(sessionId, ChatMessage(
                                        id = assistantMessageId,
                                        role = MessageRole.ASSISTANT,
                                        text = finalResponse
                                    ))
                                    success = true
                                }
                                return@withLock
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
}
}
