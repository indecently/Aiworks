package com.birkneo.Aiworks.ui.settings.sections

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
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
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AboutSection(
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onNavigateToDeveloper: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    var showLicenseDialog by remember { mutableStateOf(false) }
    var licenseDialogTitle by remember { mutableStateOf("") }
    var licenseDialogText by remember { mutableStateOf("") }

    SettingsSection(
        icon = AppIcons.Info,
        title = "About",
        expanded = expanded,
        onExpandToggle = onExpandToggle,
        onIconLongClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onNavigateToDeveloper()
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
                Text(
                    text = "Gemma Terms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        licenseDialogTitle = "Gemma Terms of Use"
                        licenseDialogText = context.assets.open("GEMMA_TERMS.txt").bufferedReader().use { it.readText() }
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
                Text(text = "0.8.0.0 (Samantabrain)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    scope.launch { 
                        settingsManager.setOnboardingCompleted(false)
                        settingsManager.setModelPath("")
                        settingsManager.setAppLockEnabled(false)
                    } 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(AppIcons.Reset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Onboarding")
            }
        }
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
}
