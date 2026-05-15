package com.birkneo.Aiworks.ai

import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.data.ChatMessage

object PromptArchitect {
    
    const val BASE_SYSTEM_PROMPT = """
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
    """

    fun constructSystemPrompt(
        session: ChatSession?,
        longTermMemory: String,
        persona: String,
        recentHistoryText: String
    ): String = buildString {
        // 1. System Prompt: Core Instructions
        append(BASE_SYSTEM_PROMPT.trimIndent())
        
        if (persona.isNotEmpty()) {
            append("\n\nIDENTITY & INSTRUCTIONS:\n")
            append(persona)
        }
        
        // 2. LTM: Historical Context/Facts (Prioritized)
        if (longTermMemory.isNotEmpty()) {
            append("\n\nLONG-TERM MEMORY (HISTORICAL CONTEXT & FACTS):\n")
            append(longTermMemory)
        }

        // 3. Chat History: Recent Tokens (Subject to truncation)
        if (recentHistoryText.isNotEmpty()) {
            append("\n\nRECENT CONVERSATION HISTORY:\n")
            append(recentHistoryText)
        }
        
        if (session?.isReasoningMode == true) {
            append("\n\n[REASONING MODE ENABLED: Think deeply before responding]")
        }
    }
}
