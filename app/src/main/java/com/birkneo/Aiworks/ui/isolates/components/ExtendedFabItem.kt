package com.birkneo.Aiworks.ui.isolates.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExtendedFabItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Row(
        // OPTIMIZATION: Ensure shadow/depth effects are not clipped during animation settle frames
        modifier = Modifier.graphicsLayer { clip = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // ELIMINATE SHADOW POP: Use Surface with tonalElevation instead of Card with shadowElevation
        Surface(
            shape = CircleShape, // 100% Full-Capsule Geometry
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 0.dp, 
            tonalElevation = 4.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = Color.White,
            // STABILIZE ELEVATION: Disable dynamic shadow calculations to prevent terminal frame artifacts
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                focusedElevation = 0.dp
            ),
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}
