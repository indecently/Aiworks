package com.birkneo.Aiworks.ui.onboarding

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.onboarding.components.*
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: ChatViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsManager = remember { GemmaContainer.getSettingsManager(context) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 6 })
    
    val modelStatus by viewModel.modelStatus.collectAsState()

    // Local state for security setup to prevent early persistence
    var isLockEnabled by remember { mutableStateOf(false) }
    var setupPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    // CRITICAL: Do NOT persist the raw content URI to SettingsManager here.
                    // Instead, let viewModel.loadModel handle the internal copy and then 
                    // persist the final absolute file path.
                    viewModel.loadModel(it.toString())
                }
            }
        }
    )

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 1.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pager Indicator
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                            )
                        }
                    }

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left placeholder or Skip
                        if (pagerState.currentPage < 5) {
                            TextButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onComplete()
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Skip")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        val nextEnabled = when (pagerState.currentPage) {
                            3 -> modelStatus is ModelStatus.Ready
                            4 -> !isLockEnabled || (setupPassword.length >= 4 && setupPassword == confirmPassword)
                            else -> true
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                if (pagerState.currentPage < 5) {
                                    // If leaving security page, persist settings
                                    if (pagerState.currentPage == 4) {
                                        scope.launch {
                                            settingsManager.setAppLockEnabled(isLockEnabled)
                                            if (isLockEnabled) {
                                                settingsManager.setAppLockPassword(setupPassword)
                                            }
                                        }
                                    }
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    onComplete()
                                }
                            },
                            enabled = nextEnabled,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(56.dp).padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp)
                        ) {
                            Text(
                                if (pagerState.currentPage == 5) "Lets go" else "Next",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (pagerState.currentPage < 5) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(AppIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false // Manual navigation for validation safety
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = AppIcons.Welcome,
                    title = "Setup",
                    description = "Fully offline, private, powerful assistant thats actually YOURS.",
                    warning = "Optimized for a multimodel and agentic native user experience."
                )
                1 -> OnboardingPage(
                    icon = AppIcons.Private,
                    title = "100% Private & Offline",
                    description = "No data ever leaves your device. Your conversations, images, and audio are yours alone, always."
                )
                2 -> PermissionsPage()
                3 -> ModelSetupPage(
                    modelStatus = modelStatus,
                    onLoadClick = { launcher.launch(arrayOf("*/*")) }
                )
                4 -> SecuritySetupPage(
                    isEnabled = isLockEnabled,
                    onEnabledChange = { isLockEnabled = it },
                    password = setupPassword,
                    onPasswordChange = { setupPassword = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it }
                )
                5 -> OnboardingPage(
                    icon = AppIcons.Chat,
                    title = "Ready to Begin",
                    description = "Create regular or incognito chats, customize your AI's persona, and explore on-device intelligence."
                )
            }
        }
    }
}
