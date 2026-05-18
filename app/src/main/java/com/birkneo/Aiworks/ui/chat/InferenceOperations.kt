package com.birkneo.Aiworks.ui.chat

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

internal suspend fun ChatViewModel.prepareInferenceForSession(sessionId: String, excludeMsgId: String? = null) {
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

internal fun ChatViewModel.retriggerInference(text: String, imageUri: String?, audioUri: String?, currentMsgId: String? = null) {
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
                            var lastUiUpdateTimestamp = 0L
                            val UI_THROTTLE_MS = 32L // ~30 FPS throttle

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

                                // 2. UI Updates (Throttled)
                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdateTimestamp >= UI_THROTTLE_MS || endTagDetected) {
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
                                    lastUiUpdateTimestamp = now
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
