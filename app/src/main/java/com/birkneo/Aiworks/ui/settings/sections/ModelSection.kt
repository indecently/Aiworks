package com.birkneo.Aiworks.ui.settings.sections

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.settings.components.SettingsLayoutGuard
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ModelSection(
    viewModel: ChatViewModel,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    modelPath: String?,
    modelStatus: ModelStatus
) {
    val view = LocalView.current
    
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

    SettingsSection(
        icon = AppIcons.Model,
        title = "AI Engine",
        expanded = expanded,
        onExpandToggle = onExpandToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp)
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
                                text = modelPath.split("/").last(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    shape = RoundedCornerShape(28.dp),
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
                        shape = RoundedCornerShape(28.dp),
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
}
