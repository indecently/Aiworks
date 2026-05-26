package com.birkneo.Aiworks.ui.settings

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    val verboseLogging by viewModel.settingsManager.verboseLogging.collectAsState(initial = false)
    val temperature by viewModel.settingsManager.temperature.collectAsState(initial = 0.8)
    val topK by viewModel.settingsManager.topK.collectAsState(initial = 40)
    val topP by viewModel.settingsManager.topP.collectAsState(initial = 0.95)

    val livePromptLogging by viewModel.settingsManager.livePromptLogging.collectAsState(initial = false)
    val lastRawPrompt by viewModel.lastRawPrompt.collectAsState()
    val lastContextSummary by viewModel.lastContextSummary.collectAsState()
    val ttftMs by viewModel.ttftMs.collectAsState()
    val generationSpeed by viewModel.generationSpeed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Options", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            LoggingAndSystemControlCard(
                verboseLogging = verboseLogging,
                onVerboseLoggingChange = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.setVerboseLogging(it) 
                },
                onNukeClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.nukeDatabase()
                    onBack()
                }
            )

            ModelHyperparametersCard(
                temperature = temperature,
                onTemperatureChange = { 
                    scope.launch { viewModel.settingsManager.setTemperature(it) }
                },
                topK = topK,
                onTopKChange = { 
                    scope.launch { viewModel.settingsManager.setTopK(it) }
                },
                topP = topP,
                onTopPChange = { 
                    scope.launch { viewModel.settingsManager.setTopP(it) }
                },
                onConfigUpdate = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.updateInferenceConfig()
                }
            )

            AIInferenceInspectorCard(
                livePromptLogging = livePromptLogging,
                onLivePromptLoggingChange = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.setLivePromptLogging(it) 
                },
                lastRawPrompt = lastRawPrompt,
                lastContextSummary = lastContextSummary
            )

            InferenceVitalsCard(
                ttftMs = ttftMs,
                generationSpeed = generationSpeed
            )

            DeveloperInfoCard(
                title = "Device Information",
                items = listOf(
                    "Model" to Build.MODEL,
                    "Manufacturer" to Build.MANUFACTURER,
                    "SDK Version" to Build.VERSION.SDK_INT.toString(),
                    "Android Version" to Build.VERSION.RELEASE
                )
            )

            DeveloperInfoCard(
                title = "Runtime Information",
                items = listOf(
                    "Architecture" to System.getProperty("os.arch").orEmpty(),
                    "Processors" to Runtime.getRuntime().availableProcessors().toString()
                )
            )

            Text(
                text = "Advanced developer tools will appear here in future updates.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )
        }
    }
}

@Composable
fun LoggingAndSystemControlCard(
    verboseLogging: Boolean,
    onVerboseLoggingChange: (Boolean) -> Unit,
    onNukeClick: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Nuke Database?") },
            text = { Text("This will permanently delete ALL chat sessions and messages. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNukeClick()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("NUKE EVERYTHING")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Logging & System Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Verbose System Logs", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = verboseLogging, onCheckedChange = onVerboseLoggingChange)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(AppIcons.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Local Chat Cache / DB")
            }
        }
    }
}

@Composable
fun ModelHyperparametersCard(
    temperature: Double,
    onTemperatureChange: (Double) -> Unit,
    topK: Int,
    onTopKChange: (Int) -> Unit,
    topP: Double,
    onTopPChange: (Double) -> Unit,
    onConfigUpdate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Model Hyperparameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Temperature
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Temperature", style = MaterialTheme.typography.bodyMedium)
                    Text(String.format(Locale.US, "%.2f", temperature), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = temperature.toFloat(),
                    onValueChange = { onTemperatureChange(it.toDouble()) },
                    onValueChangeFinished = onConfigUpdate,
                    valueRange = 0f..2f,
                    steps = 19
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Top-K
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Top-K", style = MaterialTheme.typography.bodyMedium)
                    Text(topK.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = topK.toFloat(),
                    onValueChange = { onTopKChange(it.toInt()) },
                    onValueChangeFinished = onConfigUpdate,
                    valueRange = 1f..100f
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Top-P
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Top-P", style = MaterialTheme.typography.bodyMedium)
                    Text(String.format(Locale.US, "%.2f", topP), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = topP.toFloat(),
                    onValueChange = { onTopPChange(it.toDouble()) },
                    onValueChangeFinished = onConfigUpdate,
                    valueRange = 0f..1f
                )
            }
        }
    }
}

@Composable
fun AIInferenceInspectorCard(
    livePromptLogging: Boolean,
    onLivePromptLoggingChange: (Boolean) -> Unit,
    lastRawPrompt: String,
    lastContextSummary: String
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "AI Inference Inspector",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Live Prompt Logging", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = livePromptLogging, onCheckedChange = onLivePromptLoggingChange)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            val placeholder = "Prompt logging disabled for privacy. Turn on switch to inspect active session data."
            
            InspectionSection(
                label = "Last Sent Raw Prompt", 
                content = if (livePromptLogging) lastRawPrompt else placeholder,
                enabled = livePromptLogging
            )
            Spacer(modifier = Modifier.height(12.dp))
            InspectionSection(
                label = "Active Context Summary", 
                content = if (livePromptLogging) lastContextSummary else placeholder,
                enabled = livePromptLogging
            )
        }
    }
}

@Composable
private fun InspectionSection(label: String, content: String, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (expanded) 300.dp else 80.dp)
                .then(if (enabled) Modifier.clickable { expanded = !expanded } else Modifier)
        ) {
            Box(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = if (content.isEmpty() && enabled) "(No data from last run)" else content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun InferenceVitalsCard(
    ttftMs: Long,
    generationSpeed: Float
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Inference Vitals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VitalItem(label = "TTFT", value = "$ttftMs ms")
                VerticalDivider(modifier = Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant)
                VitalItem(label = "Generation Speed", value = String.format(Locale.US, "%.1f t/s", generationSpeed))
            }
        }
    }
}

@Composable
private fun VitalItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DeveloperInfoCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = key, style = MaterialTheme.typography.bodyMedium)
                    Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
