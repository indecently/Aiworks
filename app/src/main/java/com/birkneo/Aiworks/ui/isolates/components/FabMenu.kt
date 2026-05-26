package com.birkneo.Aiworks.ui.isolates.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.delay

@Composable
fun FabMenu(
    isGenerating: Boolean,
    bottomSearchBar: Boolean,
    onNewSession: (Boolean) -> Unit
) {
    val view = LocalView.current
    var isFabExpanded by remember { mutableStateOf(false) }
    
    val rotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "FabRotation"
    )

    val alignment = if (bottomSearchBar) Alignment.BottomCenter else Alignment.BottomEnd
    val baseBottomPadding = if (bottomSearchBar) 92.dp else 16.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            horizontalAlignment = if (bottomSearchBar) Alignment.CenterHorizontally else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(alignment)
                .padding(bottom = baseBottomPadding + 72.dp)
        ) {
            var showSecond by remember { mutableStateOf(false) }
            LaunchedEffect(isFabExpanded) {
                if (isFabExpanded) {
                    delay(100)
                    showSecond = true
                } else {
                    showSecond = false
                }
            }

            AnimatedVisibility(
                visible = showSecond,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                ExtendedFabItem(
                    label = "Incognito Chat",
                    icon = AppIcons.VisibilityOff,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        isFabExpanded = false
                        onNewSession(true)
                    }
                )
            }

            AnimatedVisibility(
                visible = isFabExpanded,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                ExtendedFabItem(
                    label = "Regular Chat",
                    icon = AppIcons.ChatOutline,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        isFabExpanded = false
                        onNewSession(false)
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { 
                if (!isGenerating) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    isFabExpanded = !isFabExpanded 
                }
            },
            modifier = Modifier
                .align(alignment)
                .padding(bottom = baseBottomPadding),
            containerColor = if (isGenerating) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else if (isFabExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
            contentColor = if (isGenerating) MaterialTheme.colorScheme.outline
            else if (isFabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                AppIcons.Add,
                contentDescription = "New Chat Options",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
