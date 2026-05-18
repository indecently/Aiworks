package com.birkneo.Aiworks.ui.chat.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.birkneo.Aiworks.ui.theme.AppIcons

@Composable
fun MediaPreviewItem(
    uri: String,
    type: String,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (type == "image") {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f), CircleShape)
        ) {
            Icon(AppIcons.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val barCount = 40
    // Use a Ref to hold the array to avoid any state-read overhead inside the calculation
    val amplitudes = remember { FloatArray(barCount) }
    // Single state trigger for redraws
    var redrawTrigger by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(amplitude) {
        // Shift values in place (No allocation)
        for (i in amplitudes.indices.reversed()) {
            if (i > 0) amplitudes[i] = amplitudes[i - 1]
        }
        amplitudes[0] = amplitude.coerceIn(0.05f, 1.0f)
        redrawTrigger++
    }

    Canvas(
        modifier = modifier.graphicsLayer { 
            // Hardware accelerate the waveform to ensure 60FPS during UI heavy tasks
            renderEffect = null
        }
    ) {
        // Access redrawTrigger to ensure composition is aware of the change
        val _trigger = redrawTrigger
        val barWidth = size.width / barCount
        val centerY = size.height / 2f

        for (index in amplitudes.indices) {
            val amp = amplitudes[index]
            val h = (amp * size.height).coerceAtLeast(4f)
            val x = index * barWidth
            drawRoundRect(
                color = color.copy(alpha = (1f - index.toFloat() / barCount).coerceIn(0.3f, 1f)),
                topLeft = Offset(x + 2f, centerY - h / 2),
                size = Size(barWidth - 4f, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRecordToggle: (Boolean) -> Unit,
    isGenerating: Boolean,
    isRecording: Boolean,
    amplitude: Float,
    enabled: Boolean
) {
    val recordingScale by animateFloatAsState(if (isRecording) 1.2f else 1f, label = "recording_scale")
    val recordingColor by animateColorAsState(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, label = "recording_color")
    val view = LocalView.current

    Column(modifier = Modifier.background(Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp)) {
        // The "Big Pill" Shell
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Far Left: Plus (+) Button
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onGalleryClick()
                    }, 
                    enabled = enabled && !isGenerating,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(AppIcons.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Middle: The Original Text Pill (weighted to fill space)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (isRecording) {
                            WaveformVisualizer(
                                amplitude = amplitude,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            TextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Ask anything...", color = MaterialTheme.colorScheme.outline) },
                                enabled = enabled && !isGenerating,
                                shape = RoundedCornerShape(28.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onCameraClick()
                                        }, 
                                        enabled = enabled && !isGenerating,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(AppIcons.Camera, contentDescription = "Camera", tint = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                maxLines = 4
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Far Right: Microphone / Send / Stop Icon
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (isGenerating) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onStop()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        ) {
                            Icon(AppIcons.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } else if (text.isBlank()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(recordingScale)
                                .background(recordingColor.copy(alpha = 0.1f), CircleShape)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        if (enabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onRecordToggle(true)
                                        }
                                        waitForUpOrCancellation()
                                        if (enabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onRecordToggle(false)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.Mic, 
                                contentDescription = "Record", 
                                tint = recordingColor
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { 
                                if (enabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSend() 
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                AppIcons.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
