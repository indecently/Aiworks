package com.birkneo.Aiworks.ui.chat.components

import android.view.HapticFeedbackConstants
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.ui.theme.AppIcons

@Composable
fun StreamingPlaceholder() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dotColor = MaterialTheme.colorScheme.primary
    
    val animValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot_animation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { 
                    clip = true
                    shape = CircleShape
                }
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppIcons.Audio,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        
        Canvas(modifier = Modifier.size(width = 32.dp, height = 8.dp)) {
            val dotRadius = 4.dp.toPx()
            val dotSpacing = 4.dp.toPx()
            
            for (i in 0..2) {
                val phase = (animValue - i * 0.2f).let { if (it < 0) it + 1f else it }
                val alphaValue = if (phase < 0.5f) {
                    0.3f + (phase / 0.5f) * 0.7f
                } else {
                    1f - ((phase - 0.5f) / 0.5f) * 0.7f
                }.coerceIn(0.3f, 1f)
                
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = androidx.compose.ui.geometry.Offset(
                        x = dotRadius + i * (dotRadius * 2 + dotSpacing),
                        y = size.height / 2f
                    ),
                    alpha = alphaValue
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onLongClick: () -> Unit,
    onSpeak: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    val view = LocalView.current
    val isUser = message.role == MessageRole.USER
    var showEditDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val audioContainerColor = if (isUser) {
        containerColor.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { 
                            clip = true
                            shape = CircleShape
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Audio,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp, min = if (message.isThinking) 130.dp else 40.dp)
                    .defaultMinSize(minHeight = if (message.isThinking) 56.dp else 0.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                ),
                color = containerColor,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick
                        )
                        .padding(12.dp)
                ) {
                    if (message.isThinking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Thinking...",
                                color = contentColor.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        message.imageUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Attached Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(10.dp))
                        }
                        
                        message.audioUri?.let { uri ->
                            AudioPlayer(uri = uri, contentColor = contentColor, containerColor = audioContainerColor)
                            if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                color = contentColor,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }

        if (message.text.isNotBlank() && !message.isStreaming) {
            Row(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isUser) {
                    IconButton(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showEditDialog = true 
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit Response",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSpeak(message.text) 
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showRegenerateDialog = true 
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Reset,
                            contentDescription = "Regenerate Response",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDelete()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showEditDialog = true 
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            showRegenerateDialog = true 
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Reset,
                            contentDescription = "Regenerate Response",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDelete()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Regenerate Response") },
            text = { Text("Regenerate this response? This will discard the current output.") },
            confirmButton = {
                Button(onClick = {
                    onRegenerate()
                    showRegenerateDialog = false
                }) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        EditMessageDialog(
            initialText = message.text,
            onDismiss = { showEditDialog = false },
            onConfirm = { 
                onEdit(it)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Prompt") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Save Changes")
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
fun AudioPlayer(uri: String, contentColor: Color, containerColor: Color) {
    val context = LocalContext.current
    val view = LocalView.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    if (player == null) {
                        player = MediaPlayer.create(context, Uri.parse(uri))?.apply {
                            setOnCompletionListener { 
                                isPlaying = false 
                            }
                        }
                    }
                    player?.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = contentColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        val transition = rememberInfiniteTransition(label = "player_bars")
        val barHeight by transition.animateFloat(
            initialValue = 4f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_height"
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(15) { index ->
                val h = if (isPlaying) (barHeight * (1 + index % 3 * 0.5f)) else 4f
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(h.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.6f))
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Voice", color = contentColor, style = MaterialTheme.typography.labelSmall)
    }
}
