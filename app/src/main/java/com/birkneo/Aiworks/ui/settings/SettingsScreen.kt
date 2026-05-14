package com.birkneo.Aiworks.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.BuildConfig
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { GemmaContainer.getSettingsManager(context) }
    val scope = rememberCoroutineScope()

    val modelPath by settingsManager.modelPath.collectAsState(initial = "")
    val temperature by settingsManager.temperature.collectAsState(initial = 0.8)
    val maxTokens by settingsManager.maxTokens.collectAsState(initial = 4096)
    val modelStatus by viewModel.modelStatus.collectAsState()
    
    val lockEnabled by settingsManager.appLockEnabled.collectAsState(initial = false)
    val lockPassword by settingsManager.appLockPassword.collectAsState(initial = "")

    var editablePassword by remember(lockPassword) { mutableStateOf(lockPassword ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Welcome, contentDescription = "Back") // Using Welcome as back for now or similar
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Security
            SettingsSection(
                icon = AppIcons.Shield,
                title = "Security & Privacy"
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
                                if (it && (lockPassword == null || lockPassword!!.isEmpty())) {
                                    showPasswordDialog = true
                                } else {
                                    scope.launch { settingsManager.setAppLockEnabled(it) }
                                }
                            }
                        )
                    }

                    if (lockEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editablePassword,
                            onValueChange = { 
                                editablePassword = it
                                scope.launch { settingsManager.setAppLockPassword(it) }
                            },
                            label = { Text("App Password") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                        )
                    }
                }
            }

            // Section: Model Management
            SettingsSection(
                icon = AppIcons.Model,
                title = "AI Engine"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
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
                        Column {
                            LinearProgressIndicator(
                                progress = { status?.progress ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Loading... ${((status?.progress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { launcher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = modelStatus !is ModelStatus.Loading
                        ) {
                            Icon(AppIcons.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Load File")
                        }
                        
                        if (modelStatus is ModelStatus.Ready) {
                            OutlinedButton(
                                onClick = { viewModel.unloadModel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
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
                title = "Inference Parameters"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Temperature", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format(Locale.US, "%.2f", temperature),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = temperature.toFloat(),
                            onValueChange = { scope.launch { settingsManager.setTemperature(it.toDouble()) } },
                            valueRange = 0f..2f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Max Tokens", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "$maxTokens",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = maxTokens.toFloat(),
                            onValueChange = { scope.launch { settingsManager.setMaxTokens(it.toInt()) } },
                            valueRange = 1024f..16384f,
                            steps = 15
                        )
                    }
                }
            }

            // Section: About
            SettingsSection(
                icon = AppIcons.Info,
                title = "About"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Aiworks is a personalized, privacy-first AI assistant. Process images and audio entirely on-device with custom personas.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Version", style = MaterialTheme.typography.bodySmall)
                        Text(text = "${BuildConfig.VERSION_NAME} (Tinbucktwo)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            scope.launch { 
                                settingsManager.setOnboardingCompleted(false)
                                // Clear model path to ensure it goes back to onboarding
                                settingsManager.setModelPath("")
                                // Optionally clear lock settings as well for a clean start
                                settingsManager.setAppLockEnabled(false)
                            } 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(AppIcons.Reset, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App & Onboarding")
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
    }
}

@Composable
fun CreatePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create App Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    isError = confirm.isNotEmpty() && pass != confirm,
                    supportingText = {
                        if (confirm.isNotEmpty() && pass != confirm) {
                            Text("Passwords do not match")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pass) },
                enabled = pass.length >= 4 && pass == confirm
            ) {
                Text("Enable Protection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}
