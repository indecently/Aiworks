package com.birkneo.Aiworks.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class GemmaAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return GemmaAssistantSession(this)
    }
}

class GemmaAssistantSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Launch main activity to handle the assistant request
        val intent = android.content.Intent(context, com.birkneo.Aiworks.MainActivity::class.java).apply {
            action = "android.intent.action.ASSISTANT"
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        finish()
    }
}
