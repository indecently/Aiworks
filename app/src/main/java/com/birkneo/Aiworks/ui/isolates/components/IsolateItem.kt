package com.birkneo.Aiworks.ui.isolates.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IsolateItem(
    session: ChatSession,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onCopyAll: () -> Unit,
    onEnterSelectionMode: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(session.timestamp))
    var showMenu by remember { mutableStateOf(false) }
    val view = LocalView.current

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { 
                    if (!isSelected) showMenu = true 
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Manual elevation to reduce shadow calc
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else if (session.isIncognito) MaterialTheme.colorScheme.secondaryContainer 
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                } else if (session.imageUri != null) {
                    AsyncImage(
                        model = session.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (session.isIncognito) AppIcons.VisibilityOff else AppIcons.ChatOutline,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (session.isIncognito) MaterialTheme.colorScheme.onSecondaryContainer 
                               else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (session.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(AppIcons.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFD700))
                    }
                    if (session.isIncognito) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Box {
                if (!isSelected) {
                    IconButton(onClick = { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showMenu = true 
                    }) {
                        Icon(AppIcons.More, contentDescription = "More")
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (session.isFavorite) "Unfavorite" else "Favorite") },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onFavoriteToggle(); showMenu = false 
                        },
                        leadingIcon = { Icon(if (session.isFavorite) AppIcons.StarOutline else AppIcons.Star, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDuplicate(); showMenu = false 
                        },
                        leadingIcon = { Icon(AppIcons.Copy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onRename(); showMenu = false 
                        },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy All Text") },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onCopyAll(); showMenu = false 
                        },
                        leadingIcon = { Icon(AppIcons.CopyAll, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Select Multiple") },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onEnterSelectionMode(); showMenu = false 
                        },
                        leadingIcon = { Icon(AppIcons.Check, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDelete(); showMenu = false 
                        },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
