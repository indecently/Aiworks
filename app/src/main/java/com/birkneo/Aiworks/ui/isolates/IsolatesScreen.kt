package com.birkneo.Aiworks.ui.isolates

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.chat.*
import com.birkneo.Aiworks.ui.isolates.components.*
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsolatesScreen(
    viewModel: ChatViewModel,
    onSessionSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsManager = remember { GemmaContainer.getSettingsManager(context) }
    val homeWallpaperPath by settingsManager.homeWallpaperPath.collectAsState(initial = null)
    val bottomSearchBar by settingsManager.bottomSearchBar.collectAsState(initial = false)
    val sessions by viewModel.sessions.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    
    // Selection Mode State
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedSessionIds = remember { mutableStateListOf<String>() }

    BackHandler(enabled = isSelectionMode) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        isSelectionMode = false
        selectedSessionIds.clear()
    }

    val favoriteSessions = remember(sessions) {
        sessions.filter { it.isFavorite }.sortedByDescending { it.timestamp }.take(4)
    }

    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }

    // Settings Spin State
    var isSettingsSpinning by remember { mutableStateOf(false) }
    val settingsRotation by animateFloatAsState(
        targetValue = if (isSettingsSpinning) 360f else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "SettingsSpin",
        finishedListener = {
            if (isSettingsSpinning) {
                onNavigateToSettings()
                isSettingsSpinning = false
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (homeWallpaperPath != null) {
            AsyncImage(
                model = homeWallpaperPath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(6.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
            )
        }

        Scaffold(
            containerColor = if (homeWallpaperPath != null) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                if (isSelectionMode) {
                    SelectionToolbar(
                        selectedCount = selectedSessionIds.size,
                        onCancel = {
                            isSelectionMode = false
                            selectedSessionIds.clear()
                        },
                        onDelete = {
                            scope.launch {
                                val toDelete = sessions.filter { it.id in selectedSessionIds }
                                toDelete.forEach { viewModel.deleteSession(it) }
                                isSelectionMode = false
                                selectedSessionIds.clear()
                            }
                        }
                    )
                } else if (!bottomSearchBar) {
                    SearchControlPill(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        sortOrder = sortOrder,
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        onSettingsClick = {
                            if (!isSettingsSpinning) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                isSettingsSpinning = true
                            }
                        },
                        settingsRotation = settingsRotation,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    end = 16.dp, 
                    top = 16.dp, 
                    bottom = 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (favoriteSessions.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Text(
                            "Favorites",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            items(favoriteSessions) { session ->
                                FavoriteItem(
                                    session = session,
                                    onClick = { onSessionSelected(session.id) }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        if (searchQuery.isEmpty()) "Recent Chats" else "Search Results",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }

                if (filteredSessions.isEmpty()) {
                    item { EmptyIsolatesView() }
                } else {
                    items(filteredSessions, key = { it.id }) { session ->
                        val isSelected = remember(selectedSessionIds) {
                            derivedStateOf { session.id in selectedSessionIds } 
                        }
                        
                        IsolateItem(
                            session = session,
                            isSelected = isSelected.value,
                            onClick = { 
                                if (isSelectionMode) {
                                    if (session.id in selectedSessionIds) {
                                        selectedSessionIds.remove(session.id)
                                        if (selectedSessionIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        selectedSessionIds.add(session.id)
                                    }
                                } else {
                                    onSessionSelected(session.id) 
                                }
                            },
                            onDelete = { if (!isGenerating) sessionToDelete = session },
                            onFavoriteToggle = { viewModel.toggleFavorite(session.id, !session.isFavorite) },
                            onDuplicate = { viewModel.duplicateSession(session.id) },
                            onRename = { sessionToRename = session },
                            onCopyAll = {
                                scope.launch {
                                    val text = viewModel.getFullChatText(session.id)
                                    clipboardManager.setText(AnnotatedString(text))
                                }
                            },
                            onEnterSelectionMode = {
                                isSelectionMode = true
                                selectedSessionIds.add(session.id)
                            }
                        )
                    }
                }
            }
            
            sessionToDelete?.let { session ->
                AlertDialog(
                    onDismissRequest = { sessionToDelete = null },
                    icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete Chat?") },
                    text = { Text("This will permanently remove '${session.title}'.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteSession(session)
                                sessionToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { sessionToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            sessionToRename?.let { session ->
                RenameSessionDialog(
                    initialTitle = session.title,
                    onDismiss = { sessionToRename = null },
                    onConfirm = { newTitle ->
                        viewModel.updateSessionTitle(session.id, newTitle)
                        sessionToRename = null
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            if (!isSelectionMode) {
                FabMenu(
                    isGenerating = isGenerating,
                    bottomSearchBar = bottomSearchBar,
                    onNewSession = { isIncognito ->
                        viewModel.createNewSession(isIncognito = isIncognito) { id ->
                            onSessionSelected(id)
                        }
                    }
                )
            }

            if (!isSelectionMode && bottomSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .zIndex(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SearchControlPill(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        sortOrder = sortOrder,
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        onSettingsClick = {
                            if (!isSettingsSpinning) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                isSettingsSpinning = true
                            }
                        },
                        settingsRotation = settingsRotation,
                        modifier = Modifier.fillMaxWidth(),
                        useNavigationPadding = false
                    )
                }
            }
        }
    }
}
