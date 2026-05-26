package com.birkneo.Aiworks.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
    val pagerState = rememberPagerState(pageCount = { 7 })
    
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
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pager Indicator - Optimized Pill Design
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val width by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isSelected) 32.dp else 8.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }

                    // Buttons - Redesigned as modern pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage < 6) {
                            TextButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onComplete()
                                },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("Skip", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(56.dp))
                        }

                        val nextEnabled = when (pagerState.currentPage) {
                            3 -> modelStatus is ModelStatus.Ready
                            5 -> !isLockEnabled || (setupPassword.length >= 4 && setupPassword == confirmPassword)
                            else -> true
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                if (pagerState.currentPage < 6) {
                                    if (pagerState.currentPage == 5) {
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
                            shape = RoundedCornerShape(28.dp), // Max Pill Shape
                            modifier = Modifier.height(56.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                if (pagerState.currentPage == 6) "Let's go" else "Next",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (pagerState.currentPage < 6) {
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
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = AppIcons.Welcome,
                    title = "Yo! Welcome.",
                    description = "We're about to set up your personal AI. It's fast, private, and lives entirely on your phone.",
                    warning = "No clouds, no spying. Just pure intelligence."
                )
                1 -> FeaturesPage()
                2 -> PermissionsPage()
                3 -> ModelSetupPage(
                    modelStatus = modelStatus,
                    onLoadClick = { launcher.launch(arrayOf("*/*")) }
                )
                4 -> LicensesPage()
                5 -> SecuritySetupPage(
                    isEnabled = isLockEnabled,
                    onEnabledChange = { isLockEnabled = it },
                    password = setupPassword,
                    onPasswordChange = { setupPassword = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it }
                )
                6 -> OnboardingPage(
                    icon = AppIcons.Chat,
                    title = "You're all set!",
                    description = "Time to experience AI the way it was meant to be. Fast, private, and yours. Let's get to work."
                )
            }
        }
    }
}
