package com.birkneo.Aiworks.ui.settings.sections

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.ui.settings.components.InferenceParameterRow
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun InferenceSection(
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    maxTokens: Int
) {
    val view = LocalView.current
    
    SettingsSection(
        icon = AppIcons.Tune,
        title = "Tokens",
        expanded = expanded,
        onExpandToggle = onExpandToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InferenceParameterRow(
                title = "Max Tokens",
                description = "Maximum context window (history + response). Capped by device RAM.",
                value = maxTokens.toFloat(),
                valueRange = 1024f..16384f,
                isInteger = true,
                onValueChange = { scope.launch { settingsManager.setMaxTokens(it.toInt()) } }
            )
        }
    }
}
