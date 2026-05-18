package com.birkneo.Aiworks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import com.birkneo.Aiworks.ui.chat.ChatScreen
import com.birkneo.Aiworks.ui.chat.ChatSettingsScreen
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.chat.closeSession
import com.birkneo.Aiworks.ui.chat.createNewSession
import com.birkneo.Aiworks.ui.components.GlobalLoadingOverlay
import com.birkneo.Aiworks.ui.components.LockScreen
import com.birkneo.Aiworks.ui.isolates.IsolatesScreen
import com.birkneo.Aiworks.ui.navigation.Screen
import com.birkneo.Aiworks.ui.onboarding.OnboardingScreen
import com.birkneo.Aiworks.ui.settings.SettingsScreen
import com.birkneo.Aiworks.ui.theme.LocalGemmaChatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val intentFlow = MutableStateFlow<Intent?>(null)
    private val _assistantIntentConsumed = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "android.intent.action.ASSISTANT") {
            _assistantIntentConsumed.value = false // Reset consumption on new invocation
        }
        intentFlow.value = intent
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == "android.intent.action.ASSISTANT") {
            _assistantIntentConsumed.value = false
        }
        intentFlow.value = intent

        setContent {
            LocalGemmaChatTheme {
                val viewModel: ChatViewModel = viewModel()
                val modelStatus by viewModel.modelStatus.collectAsState()
                val isUnlocked by viewModel.isUnlocked.collectAsState()
                val isTransientUnlock by viewModel.isTransientUnlock.collectAsState()
                
                val context = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { com.birkneo.Aiworks.di.GemmaContainer.getSettingsManager(context) }
                val lockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)

                val currentIntent by intentFlow.collectAsState()
                val isIntentConsumed by _assistantIntentConsumed.collectAsState()

                // Digital Assistant Trigger Logic - MOVE UP to ensure state is set before UI evaluation
                LaunchedEffect(currentIntent) {
                    if (currentIntent?.action == "android.intent.action.ASSISTANT") {
                        viewModel.setTransientUnlock(true)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (lockEnabled && !isUnlocked && !isTransientUnlock) {
                        LockScreen(onUnlock = { viewModel.verifyAndUnlock(it) })
                    } else {
                        MainApp(
                            viewModel = viewModel, 
                            intent = currentIntent, 
                            isIntentConsumed = isIntentConsumed,
                            onIntentConsumed = { _assistantIntentConsumed.value = true }
                        )
                    }
                    GlobalLoadingOverlay(status = modelStatus)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainApp(
    viewModel: ChatViewModel, 
    intent: Intent? = null, 
    isIntentConsumed: Boolean = false,
    onIntentConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val settingsManager = remember { com.birkneo.Aiworks.di.GemmaContainer.getSettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val onboardingCompleted by settingsManager.onboardingCompleted.collectAsState(initial = null)
    val modelPathFlow = settingsManager.modelPath.collectAsState(initial = "")
    val modelPath = modelPathFlow.value

    val isGenerating by viewModel.isGenerating.collectAsState()
    val isTransientUnlock by viewModel.isTransientUnlock.collectAsState()
    
    // Determine starting screen based on persistent state
    val startScreen = remember(onboardingCompleted, modelPath) {
        if (onboardingCompleted == false && (modelPath == null || modelPath.isEmpty())) {
            Screen.Onboarding
        } else {
            Screen.Isolates
        }
    }

    if (onboardingCompleted == null) {
        return
    }

    val backStack = rememberNavBackStack(startScreen)
    
    // Digital Assistant Trigger Logic
    LaunchedEffect(intent, isIntentConsumed) {
        if (intent?.action == "android.intent.action.ASSISTANT" && !isIntentConsumed) {
            // Check if we are already in a chat to avoid double-stacking
            if (backStack.last() !is Screen.Chat) {
                // Trigger a new chat session immediately
                viewModel.createNewSession(isIncognito = false) { sessionId ->
                    onIntentConsumed()
                    backStack.add(Screen.Chat(sessionId = sessionId))
                }
            }
        }
    }
    val strategy = rememberListDetailSceneStrategy<NavKey>()

    NavDisplay(
        backStack = backStack,
        sceneStrategies = listOf(strategy),
        onBack = {
            if (!isGenerating && backStack.size > 1) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                // Digital Assistant transient state handling
                if (isTransientUnlock && backStack.size == 2 && backStack.last() is Screen.Chat) {
                    viewModel.closeSession() // HARD WIPE ephemeral assistant session
                    viewModel.setTransientUnlock(false)
                    backStack.removeAt(backStack.size - 1) // FIX: Remove screen to avoid Ghost Chat
                } else {
                    // Regular back navigation - if leaving chat, close session to ensure cleanup
                    val currentScreen = backStack.last()
                    if (currentScreen is Screen.Chat) {
                        viewModel.closeSession()
                    }
                    backStack.removeAt(backStack.size - 1)
                }
            }
        }
    ) { key ->
        when (key) {
            is Screen.Onboarding -> NavEntry(key) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onComplete = {
                        scope.launch {
                            settingsManager.setOnboardingCompleted(true)
                            viewModel.setUnlocked(true) // Auto-unlock on first setup
                            // Replace current screen with Isolates
                            backStack.removeAt(backStack.size - 1)
                            backStack.add(Screen.Isolates)
                        }
                    }
                )
            }
            is Screen.Isolates -> NavEntry(
                key = key,
                metadata = ListDetailSceneStrategy.listPane()
            ) {
                IsolatesScreen(
                    viewModel = viewModel,
                    onSessionSelected = { id ->
                        if (!isGenerating && (backStack.last() as? Screen.Chat)?.sessionId != id) {
                            backStack.add(Screen.Chat(sessionId = id))
                        }
                    },
                    onNavigateToSettings = {
                        if (!isGenerating && backStack.last() !is Screen.Settings) {
                            backStack.add(Screen.Settings)
                        }
                    }
                )
            }
            is Screen.Chat -> NavEntry(
                key = key,
                metadata = ListDetailSceneStrategy.detailPane()
            ) {
                ChatScreen(
                    viewModel = viewModel,
                    sessionId = key.sessionId,
                    onNavigateToSettings = {
                        if (backStack.last() !is Screen.ChatSettings) {
                            backStack.add(Screen.ChatSettings(sessionId = key.sessionId))
                        }
                    }
                )
            }
            is Screen.ChatSettings -> NavEntry(
                key = key,
                metadata = ListDetailSceneStrategy.detailPane()
            ) {
                ChatSettingsScreen(
                    sessionId = key.sessionId,
                    viewModel = viewModel,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                )
            }
            is Screen.Settings -> NavEntry(
                key = key,
                metadata = ListDetailSceneStrategy.detailPane()
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                )
            }
            else -> NavEntry(key) { }
        }
    }
}
