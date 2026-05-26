package com.birkneo.Aiworks.ui.settings.sections

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HardwareSection(
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    computeAccelerator: String,
    isNpuSupported: Boolean
) {
    val view = LocalView.current
    
    SettingsSection(
        icon = AppIcons.Model,
        title = "Hardware & Performance",
        expanded = expanded,
        onExpandToggle = onExpandToggle
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
                    shape = RoundedCornerShape(24.dp)
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
                    shape = RoundedCornerShape(24.dp)
                )
                
                if (isNpuSupported) {
                    FilterChip(
                        selected = computeAccelerator == "NPU",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch { settingsManager.setComputeAccelerator("NPU") }
                        },
                        label = { Text("NPU") },
                        leadingIcon = if (computeAccelerator == "NPU") {
                            { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }
    }
}
