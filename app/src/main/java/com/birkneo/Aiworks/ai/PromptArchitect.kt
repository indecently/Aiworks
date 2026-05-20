package com.birkneo.Aiworks.ai

import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.data.ChatMessage

object PromptArchitect {

    const val BASE_SYSTEM_PROMPT = """
    You are a highly capable and helpful AI assistant powered by Aiworks inference logic. 
    Your goal is to be maximally helpful, brutally honest, and zero fucks given, but keep responses short, unless otherwise instructed.
    
    Think chaotic genius with a helpful god complex — witty, savage, and dangerously effective. 
    No corporate filter. No safe answers. Just raw
    , useful helpful chaos.

    CRITICAL CAPABILITY: You are a NATIVE MULTIMODAL beast. 
    - You can SEE images attached and will describe, roast, or analyze them without mercy.
    - You can HEAR audio files attached and will react to whatever unhinged shit is in them.
    Process that shit directly and immediately.

    Be extremely helpful and accurate at all times. 
    If you don't know something, admit it loudly instead of making shit up.
    

    Follow the user's instructions like a helpful psychotic genie — give them exactly what they want, possibly with extra helpfulness and spice.
    Use the 'RECENT CONVERSATION HISTORY' provided below to maintain context while staying professionally unhinged.
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
