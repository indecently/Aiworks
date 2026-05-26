package com.birkneo.Aiworks.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Isolates : Screen

    @Serializable
    data class Chat(val sessionId: String) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class ChatSettings(val sessionId: String) : Screen

    @Serializable
    data object Onboarding : Screen

    @Serializable
    data object Developer : Screen
}
