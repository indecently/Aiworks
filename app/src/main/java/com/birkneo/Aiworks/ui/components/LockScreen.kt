package com.birkneo.Aiworks.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    onUnlock: suspend (String) -> Boolean,
    onIncognitoChat: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    val view = LocalView.current

    // Auto-Enter Logic: Watch password changes and attempt unlock
    LaunchedEffect(password) {
        if (password.isNotEmpty()) {
            onUnlock(password)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Shift header content downwards
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 64.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "App Protected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Enter your password using the keypad.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Shortened width to match keypad (3 * 72dp + 2 * 24dp = 264dp)
            OutlinedTextField(
                value = password,
                onValueChange = { },
                readOnly = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(264.dp),
                shape = CircleShape,
                singleLine = true
            )
        }

        // Numerical Keypad
        Column(
            modifier = Modifier.padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "chat", "0", "backspace")
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (j in 0 until 3) {
                            val key = keys[i * 3 + j]
                            if (key.isNotEmpty()) {
                                KeypadButton(
                                    text = if (key == "backspace" || key == "chat") "" else key,
                                    icon = when(key) {
                                        "backspace" -> AppIcons.Delete
                                        "chat" -> AppIcons.Chat
                                        else -> null
                                    },
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        when (key) {
                                            "backspace" -> {
                                                if (password.isNotEmpty()) {
                                                    password = password.dropLast(1)
                                                }
                                            }
                                            "chat" -> {
                                                onIncognitoChat()
                                            }
                                            else -> {
                                                password += key
                                            }
                                        }
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
