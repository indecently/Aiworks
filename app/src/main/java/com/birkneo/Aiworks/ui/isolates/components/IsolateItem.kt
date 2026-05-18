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

    // ELIMINATING TRANSPARENCY: Use solid colors to prevent shadow/bleeding artifacts
    val backgroundColorState by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val itemShape = RoundedCornerShape(40.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(itemShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { 
                    if (!isSelected) showMenu = true 
                }
            ),
        shape = itemShape,
        color = backgroundColorState,
        shadowElevation = 2.dp, // Reduced for cleaner contour on solid background
        tonalElevation = 0.dp
    ) {
        // UNIFIED ROW: Simplified hierarchy to remove sub-pixel layout artifacts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            
            // Text Area: Removed internal weights to prevent vertical splitting artifacts
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
            
            // Menu Anchor: Isolated from the text column
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
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .width(220.dp)
                        .background(Color.Transparent), 
                    shape = RoundedCornerShape(40.dp),
                    containerColor = Color.Transparent, 
                    tonalElevation = 0.dp,               
                    shadowElevation = 0.dp,              
                    properties = androidx.compose.ui.window.PopupProperties(
                        focusable = true,
                        clippingEnabled = true
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(40.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            val menuItems = listOf(
                                Triple(if (session.isFavorite) "Unfavorite" else "Favorite", if (session.isFavorite) AppIcons.StarOutline else AppIcons.Star, onFavoriteToggle),
                                Triple("Duplicate", AppIcons.Copy, onDuplicate),
                                Triple("Rename", AppIcons.Edit, onRename),
                                Triple("Copy All Text", AppIcons.CopyAll, onCopyAll),
                                Triple("Select Multiple", AppIcons.Check, onEnterSelectionMode)
                            )

                            menuItems.forEach { (text, icon, action) ->
                                Surface(
                                    onClick = { 
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        action(); showMenu = false 
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Surface(
                                onClick = { 
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onDelete(); showMenu = false 
                                },
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
