package com.birkneo.Aiworks.ui.settings

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.birkneo.Aiworks.ui.settings.components.*
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsManager = remember { GemmaContainer.getSettingsManager(context) }
    val scope = rememberCoroutineScope()

    val modelPath by settingsManager.modelPath.collectAsState(initial = "")
    val temperature by settingsManager.temperature.collectAsState(initial = 0.8)
    val maxTokens by settingsManager.maxTokens.collectAsState(initial = 4096)
    val topK by settingsManager.topK.collectAsState(initial = 40)
    val topP by settingsManager.topP.collectAsState(initial = 0.95)
    val computeAccelerator by settingsManager.computeAccelerator.collectAsState(initial = "GPU")
    val homeWallpaperPath by settingsManager.homeWallpaperPath.collectAsState(initial = null)
    val bottomSearchBar by settingsManager.bottomSearchBar.collectAsState(initial = false)
    val modelStatus by viewModel.modelStatus.collectAsState()
    
    val lockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)
    val lockPassword by settingsManager.appLockPassword.collectAsState(initial = "")

    var editablePassword by remember(lockPassword) { mutableStateOf(lockPassword ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Isolate Localized Expansion States
    var securityExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var inferenceExpanded by remember { mutableStateOf(false) }
    var hardwareExpanded by remember { mutableStateOf(false) }
    var wallpaperExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    // License Dialog States
    var showLicenseDialog by remember { mutableStateOf(false) }
    var licenseDialogTitle by remember { mutableStateOf("") }
    var licenseDialogText by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    settingsManager.setModelPath(it.toString())
                    viewModel.loadModel(it.toString())
                }
            }
        }
    )

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val file = java.io.File(context.filesDir, "home_wallpaper.jpg")
                        inputStream?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        settingsManager.setHomeWallpaperPath(file.absolutePath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Full bleed for scroll
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main scrollable content extends to top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Space for the floating header (Status Bar + Header Height + Gap)
                Spacer(modifier = Modifier.statusBarsPadding().height(84.dp))

                // Section: Security
            SettingsSection(
                icon = AppIcons.Shield,
                title = "Security & Privacy",
                expanded = securityExpanded,
                onExpandToggle = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    securityExpanded = !securityExpanded 
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Password Lock", style = MaterialTheme.typography.bodyMedium)
                            Text("Require a password to access the app and load AI.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = lockEnabled,
                            onCheckedChange = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                if (it && (lockPassword == null || lockPassword!!.isEmpty())) {
                                    showPasswordDialog = true
                                } else {
                                    scope.launch { settingsManager.setAppLockEnabled(it) }
                                }
                            }
                        )
                    }

                    if (lockEnabled) {
                        SettingsLayoutGuard(visible = lockEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editablePassword,
                                onValueChange = { 
                                    editablePassword = it
                                    scope.launch { settingsManager.setAppLockPassword(it) }
                                },
                                label = { Text("App Password") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp), // Pill Shape Input
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                            )
                        }
                    }
                }
            }

            // Section: Model Management
            SettingsSection(
                icon = AppIcons.Model,
                title = "AI Engine",
                expanded = modelExpanded,
                onExpandToggle = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    modelExpanded = !modelExpanded 
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(28.dp) // Redesigned pill row
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (modelStatus is ModelStatus.Ready) AppIcons.Check else AppIcons.Error,
                                contentDescription = null,
                                tint = if (modelStatus is ModelStatus.Ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (modelStatus is ModelStatus.Ready) "Model Loaded" else "No Model Active",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (modelPath?.isNotEmpty() == true) {
                                    Text(
                                        text = modelPath!!.split("/").last(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = modelStatus is ModelStatus.Loading,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val status = modelStatus as? ModelStatus.Loading
                        SettingsLayoutGuard(visible = modelStatus is ModelStatus.Loading) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { status?.progress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Loading... ${((status?.progress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                launcher.launch(arrayOf("*/*")) 
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp), // Redesigned pill button
                            enabled = modelStatus !is ModelStatus.Loading
                        ) {
                            Icon(AppIcons.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Load File")
                        }
                        
                        if (modelStatus is ModelStatus.Ready) {
                            OutlinedButton(
                                onClick = { 
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.unloadModel() 
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(28.dp), // Redesigned pill button
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(AppIcons.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unload")
                            }
                        }
                    }
                }
            }

            // Section: Inference
            SettingsSection(
                icon = AppIcons.Tune,
                title = "Sampling Parameters",
                expanded = inferenceExpanded,
                onExpandToggle = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    inferenceExpanded = !inferenceExpanded 
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InferenceParameterRow(
                        title = "Temperature",
                        description = "Controls randomness: higher values increase diversity.",
                        value = temperature.toFloat(),
                        valueRange = 0f..2f,
                        onValueChange = { scope.launch { settingsManager.setTemperature(it.toDouble()) } }
                    )

                    InferenceParameterRow(
                        title = "Max Tokens",
                        description = "Maximum length of the generated response.",
                        value = maxTokens.toFloat(),
                        valueRange = 1024f..16384f,
                        isInteger = true,
                        onValueChange = { scope.launch { settingsManager.setMaxTokens(it.toInt()) } }
                    )

                    InferenceParameterRow(
                        title = "Top-K",
                        description = "Filters the top K most likely tokens.",
                        value = topK.toFloat(),
                        valueRange = 1f..128f,
                        isInteger = true,
                        onValueChange = { scope.launch { settingsManager.setTopK(it.toInt()) } }
                    )

                    InferenceParameterRow(
                        title = "Top-P",
                        description = "Cumulative probability cutoff for token selection.",
                        value = topP.toFloat(),
                        valueRange = 0f..1f,
                        onValueChange = { scope.launch { settingsManager.setTopP(it.toDouble()) } }
                    )
                }
            }

            // Section: Hardware
            SettingsSection(
                icon = AppIcons.Model,
                title = "Hardware & Performance",
                expanded = hardwareExpanded,
                onExpandToggle = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    hardwareExpanded = !hardwareExpanded 
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Compute Accelerator", style = MaterialTheme.typography.bodyMedium)
                    Text("Target specific hardware for inference. Changes take effect on next model load.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = computeAccelerator == "CPU",
                            onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch { settingsManager.setComputeAccelerator("CPU") } 
                            },
                            label = { Text("CPU") },
                            leadingIcon = if (computeAccelerator == "CPU") {
                                { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(24.dp) // Pill Shape Chip
                        )
                        FilterChip(
                            selected = computeAccelerator == "GPU",
                            onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch { settingsManager.setComputeAccelerator("GPU") } 
                            },
                            label = { Text("GPU") },
                            leadingIcon = if (computeAccelerator == "GPU") {
                                { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(24.dp) // Pill Shape Chip
                        )
                    }
                }
            }

            // Section: Wallpaper
            SettingsSection(
                icon = AppIcons.Palette,
                title = "Appearance & Personalization",
                expanded = wallpaperExpanded,
                onExpandToggle = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    wallpaperExpanded = !wallpaperExpanded
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Home Wallpaper Sub-section
                    Text(
                        text = "Home Wallpaper",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize the background of your Home Screen with a blurred image.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                wallpaperLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(AppIcons.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Image")
                        }

                        if (homeWallpaperPath != null) {
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    scope.launch { settingsManager.setHomeWallpaperPath(null) }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(AppIcons.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Layout Sub-section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bottom Search Bar", style = MaterialTheme.typography.bodyMedium)
                            Text("Relocate the search pill to the bottom of the screen.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = bottomSearchBar,
                            onCheckedChange = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch { settingsManager.setBottomSearchBar(it) }
                            }
                        )
                    }
                }
            }

            // Section: About
            SettingsSection(
                icon = AppIcons.Info,
                title = "About",
                expanded = aboutExpanded,
                onExpandToggle = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    aboutExpanded = !aboutExpanded 
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Aiworks is a privacy-first AI assistant. Process images and audio entirely on-device with custom personas. Powered by the Google LiteRT Runtime (formerly TensorFlow Lite), licensed under Apache 2.0.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "GNU GPLv3 License",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                licenseDialogTitle = "GNU GPLv3 License"
                                licenseDialogText = context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
                                showLicenseDialog = true
                            }
                        )
                        Text(
                            text = "Third-Party Notices",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                licenseDialogTitle = "Third-Party Notices"
                                licenseDialogText = context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
                                showLicenseDialog = true
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Version", style = MaterialTheme.typography.bodySmall)
                        Text(text = "0.7.0.5 (OpenPillyPilly)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch { 
                                settingsManager.setOnboardingCompleted(false)
                                // Clear model path to ensure it goes back to onboarding
                                settingsManager.setModelPath("")
                                // Optionally clear lock settings as well for a clean start
                                settingsManager.setAppLockEnabled(false)
                            } 
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp), // Redesigned pill button
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(AppIcons.Reset, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Onboarding")
                    }
                }
            }
        }

        if (showPasswordDialog) {
            CreatePasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onConfirm = { newPassword ->
                    scope.launch {
                        settingsManager.setAppLockPassword(newPassword)
                        settingsManager.setAppLockEnabled(true)
                        showPasswordDialog = false
                    }
                }
            )
        }

        if (showLicenseDialog) {
            AlertDialog(
                onDismissRequest = { showLicenseDialog = false },
                title = { Text(licenseDialogTitle) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 400.dp)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = licenseDialogText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showLicenseDialog = false 
                        },
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Dismiss")
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            )
        }

        // 2. Floating Header Capsule
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
