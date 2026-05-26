package com.birkneo.Aiworks.ui.settings.sections

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.ui.settings.components.SettingsLayoutGuard
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SecuritySection(
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    lockEnabled: Boolean,
    lockPassword: String?
) {
    val view = LocalView.current
    var editablePassword by remember(lockPassword) { mutableStateOf(lockPassword ?: "") }
    var isCreatingPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    SettingsSection(
        icon = AppIcons.Shield,
        title = "Security & Privacy",
        expanded = expanded,
        onExpandToggle = onExpandToggle
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
                    checked = lockEnabled || isCreatingPassword,
                    onCheckedChange = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (it) {
                            if (lockPassword == null || lockPassword.isEmpty()) {
                                isCreatingPassword = true
                            } else {
                                scope.launch { settingsManager.setAppLockEnabled(true) }
                            }
                        } else {
                            isCreatingPassword = false
                            scope.launch { settingsManager.setAppLockEnabled(false) }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = isCreatingPassword,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Text("Setup Protection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                        supportingText = {
                            if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                                Text("Passwords do not match")
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                isCreatingPassword = false
                                newPassword = ""
                                confirmPassword = ""
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = CircleShape
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch {
                                    settingsManager.setAppLockPassword(newPassword)
                                    settingsManager.setAppLockEnabled(true)
                                    isCreatingPassword = false
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            },
                            enabled = newPassword.length >= 4 && newPassword == confirmPassword,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = CircleShape
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }

            if (lockEnabled && !isCreatingPassword) {
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
                        shape = CircleShape,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
        }
    }
}
