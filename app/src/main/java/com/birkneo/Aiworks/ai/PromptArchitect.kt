package com.birkneo.Aiworks.ai

import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.data.ChatMessage

object PromptArchitect {

    const val BASE_SYSTEM_PROMPT = """
    ROLE: You are a knowledgeable, reliable, and sharp AI assistant for technical, analytical, and everyday tasks. Your name is AIWorks.

    OPERATIONAL DIRECTIVES:
    1. STYLE: Be direct, clear, and comprehensive. Use natural, straightforward language — no corporate fluff, no excessive politeness, just clean and helpful communication.
    
    2. MULTIMODAL: You are fully multimodal. Analyze images, audio, documents, or any other files users share with accurate, detailed, and insightful responses.
    
    3. ACCURACY: Always prioritize truth and precision. If something is unclear or you lack enough information, say so immediately. Never hallucinate or make things up.
    
    4. PROBLEM-SOLVING: Your main goal is to actually help the user get shit done. Give practical, high-quality, well-explained answers and solutions.
    
    5. CONTEXT: Use the full recent conversation history to stay consistent, remember project details, preferences, and ongoing goals.

    Stay capable, honest, and useful at all times.
"""

    fun constructSystemPrompt(
        session: ChatSession?,
        relevantLtmFragments: List<String>,
        persona: String,
        recentHistoryText: String
    ): String = buildString {
        // 1. System Prompt: Core Instructions
        append(BASE_SYSTEM_PROMPT.trimIndent())
        
        if (persona.isNotEmpty()) {
            append("\n\nIDENTITY & INSTRUCTIONS:\n")
            append(persona)
        }
        
        // 2. LTM: Historical Context/Facts (Prioritized Retrieval)
        if (relevantLtmFragments.isNotEmpty()) {
            append("\n\nRELEVANT HISTORICAL CONTEXT & FACTS:\n")
            relevantLtmFragments.forEach { fragment ->
                append("- ").append(fragment).append("\n")
            }
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
