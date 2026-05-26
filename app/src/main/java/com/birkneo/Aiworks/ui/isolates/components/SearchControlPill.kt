package com.birkneo.Aiworks.ui.isolates.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties
import com.birkneo.Aiworks.ui.isolates.SessionSortOrder
import com.birkneo.Aiworks.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchControlPill(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SessionSortOrder,
    onSortOrderChange: (SessionSortOrder) -> Unit,
    onSettingsClick: () -> Unit,
    settingsRotation: Float,
    modifier: Modifier = Modifier,
    useNavigationPadding: Boolean = false
) {
    val view = LocalView.current
    var showSortMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .then(if (useNavigationPadding) Modifier.navigationBarsPadding() else Modifier)
            .fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSearchQueryChange("")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(AppIcons.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    showSortMenu = true
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        AppIcons.Tune,
                                        contentDescription = "Filter",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier
                                    .width(200.dp)
                                    .background(Color.Transparent),
                                offset = DpOffset(x = (-16).dp, y = 8.dp),
                                properties = PopupProperties(
                                    focusable = true,
                                    clippingEnabled = true
                                ),
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(40.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    shadowElevation = 4.dp,
                                    tonalElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        SessionSortOrder.entries.forEach { order ->
                                            Surface(
                                                onClick = { 
                                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                    onSortOrderChange(order)
                                                    showSortMenu = false
                                                },
                                                shape = RoundedCornerShape(24.dp),
                                                color = if (sortOrder == order) 
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = order.displayName, 
                                                        style = MaterialTheme.typography.bodyMedium, 
                                                        fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (sortOrder == order) 
                                                            MaterialTheme.colorScheme.primary 
                                                        else 
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                shape = CircleShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    AppIcons.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(settingsRotation),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
