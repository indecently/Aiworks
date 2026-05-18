package com.birkneo.Aiworks.ui.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout

/**
 * SettingsLayoutGuard: Implements layout boundaries around internal settings components.
 * 
 * Enforces Intrinsic Independent Sizing by allowing content to measure itself at its natural height,
 * while the parent clips the view frame during animations. This prevents squashing or morphing.
 */
@Composable
fun SettingsLayoutGuard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SettingsGuardAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds() // Strict clipping property for the "view window" effect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // EXPLICIT GUARDRAIL: Force internal content to size strictly based on requirements.
                // We use a custom layout modifier to ensure the height is not compressed by parent constraints
                // during the animation transit.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer { 
                    this.alpha = alpha
                    // Prevent hardware layer overhead if not needed
                    clip = false 
                }
        ) {
            content()
        }
    }
}
