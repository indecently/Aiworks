package com.birkneo.Aiworks.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ai.ModelStatus

@Composable
fun ModelStatusIndicator(status: ModelStatus) {
    val text = when (status) {
        ModelStatus.NotLoaded -> "Model Not Loaded"
        is ModelStatus.Loading -> {
            if (status.progress >= 1.0f) {
                "Initializing AI..."
            } else {
                "Loading... ${(status.progress * 100).toInt()}%"
            }
        }
        is ModelStatus.Ready -> "Gemma Ready"
        is ModelStatus.Error -> "Model Error"
    }
    val color = when (status) {
        ModelStatus.NotLoaded -> MaterialTheme.colorScheme.outline
        is ModelStatus.Loading -> MaterialTheme.colorScheme.primary
        is ModelStatus.Ready -> MaterialTheme.colorScheme.secondary
        is ModelStatus.Error -> MaterialTheme.colorScheme.error
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
