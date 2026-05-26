package com.birkneo.Aiworks.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.settings.sections.*
import com.birkneo.Aiworks.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsManager = remember { GemmaContainer.getSettingsManager(context) }
    val scope = rememberCoroutineScope()

    val modelPath by settingsManager.modelPath.collectAsState(initial = "")
    val maxTokens by settingsManager.maxTokens.collectAsState(initial = 4096)
    val computeAccelerator by settingsManager.computeAccelerator.collectAsState(initial = "GPU")
    val homeWallpaperPath by settingsManager.homeWallpaperPath.collectAsState(initial = null)
    val bottomSearchBar by settingsManager.bottomSearchBar.collectAsState(initial = false)
    val modelStatus by viewModel.modelStatus.collectAsState()
    
    val lockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)
    val lockPassword by settingsManager.appLockPassword.collectAsState(initial = "")

    // Isolate Localized Expansion States
    var securityExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var inferenceExpanded by remember { mutableStateOf(false) }
    var hardwareExpanded by remember { mutableStateOf(false) }
    var wallpaperExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.statusBarsPadding().height(84.dp))

                SecuritySection(
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = securityExpanded,
                    onExpandToggle = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        securityExpanded = !securityExpanded 
                    },
                    lockEnabled = lockEnabled,
                    lockPassword = lockPassword
                )

                ModelSection(
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = modelExpanded,
                    onExpandToggle = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        modelExpanded = !modelExpanded 
                    },
                    modelPath = modelPath,
                    modelStatus = modelStatus
                )

                InferenceSection(
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = inferenceExpanded,
                    onExpandToggle = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        inferenceExpanded = !inferenceExpanded 
                    },
                    maxTokens = maxTokens
                )

                HardwareSection(
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = hardwareExpanded,
                    onExpandToggle = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        hardwareExpanded = !hardwareExpanded 
                    },
                    computeAccelerator = computeAccelerator,
                    isNpuSupported = viewModel.isNpuSupported
                )

                AppearanceSection(
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = wallpaperExpanded,
                    onExpandToggle = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        wallpaperExpanded = !wallpaperExpanded
                    },
                    homeWallpaperPath = homeWallpaperPath,
                    bottomSearchBar = bottomSearchBar
                )

                AboutSection(
                    settingsManager = settingsManager,
                    scope = scope,
                    expanded = aboutExpanded,
                    onExpandToggle = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        aboutExpanded = !aboutExpanded 
                    },
                    onNavigateToDeveloper = onNavigateToDeveloper
                )
            }

            // Floating Header Capsule
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                tonalElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onBack()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.Welcome, 
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Global Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
