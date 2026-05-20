package com.birkneo.Aiworks.ui.isolates

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.di.GemmaContainer
import com.birkneo.Aiworks.ui.chat.*
import com.birkneo.Aiworks.ui.isolates.components.*
import com.birkneo.Aiworks.ui.theme.AppIcons
import kotlinx.coroutines.delay
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
    var isFabExpanded by remember { mutableStateOf(false) }
    
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
            // Tonal overlay for readability
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
                    CenterAlignedTopAppBar(
                        title = { 
                            Text(
                                "${selectedSessionIds.size} Selected", 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                isSelectionMode = false
                                selectedSessionIds.clear()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            if (selectedSessionIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    scope.launch {
                                        val toDelete = sessions.filter { it.id in selectedSessionIds }
                                        toDelete.forEach { viewModel.deleteSession(it) }
                                        isSelectionMode = false
                                        selectedSessionIds.clear()
                                    }
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
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
                    bottom = 92.dp // Consistent bottom padding for Control Island / FAB
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Favorites Section
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
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp) // Reduced bottom padding
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

        // --- Furniture Overlay ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 1. FAB (Fixed Bottom Layer)
            if (!isSelectionMode) {
                val rotation by animateFloatAsState(
                    targetValue = if (isFabExpanded) 45f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "FabRotation"
                )

                val alignment = if (bottomSearchBar) Alignment.BottomCenter else Alignment.BottomEnd
                val baseBottomPadding = if (bottomSearchBar) 92.dp else 16.dp

                // Stable Container for FAB and Menu
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Expanded Menu Container (Independent Anchor)
                    Column(
                        horizontalAlignment = if (bottomSearchBar) Alignment.CenterHorizontally else Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .align(alignment)
                            .padding(bottom = baseBottomPadding + 72.dp) // Anchored above FAB
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
                                    viewModel.createNewSession(isIncognito = true) { id ->
                                        onSessionSelected(id)
                                    }
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
                                    viewModel.createNewSession(isIncognito = false) { id ->
                                        onSessionSelected(id)
                                    }
                                }
                            )
                        }
                    }

                    // Floating Action Button (Static Anchor)
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

            // 2. Search Pill (IME-Sensitive Top Layer)
            if (!isSelectionMode && bottomSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .imePadding() // Glides with keyboard
                        .zIndex(1f)   // Renders above FAB
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
                        useNavigationPadding = false // Handled by overlay wrapper
                    )
                }
            }
        }
    }
}

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
        shape = CircleShape, // 100% Full-Capsule Geometry
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
            // 1. Left: Home Title
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Center-Left: Consolidated Search & Filter Capsule (Nested Bubble)
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
                        
                        // Icon-Only Filter Capsule
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

                            // Explicitly Anchored Sorting Menu
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier
                                    .width(200.dp)
                                    .background(Color.Transparent),
                                offset = androidx.compose.ui.unit.DpOffset(x = (-16).dp, y = 8.dp),
                                properties = androidx.compose.ui.window.PopupProperties(
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
                shape = CircleShape, // 100% Full-Capsule Geometry
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

            // 3. Far-Right: Spinning Settings Anchor
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
