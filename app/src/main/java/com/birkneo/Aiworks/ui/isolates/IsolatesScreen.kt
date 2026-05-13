package com.birkneo.Aiworks.ui.isolates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.entity.ChatSession
import com.birkneo.Aiworks.ui.chat.ChatViewModel
import com.birkneo.Aiworks.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsolatesScreen(
    viewModel: ChatViewModel,
    onSessionSelected: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val favoriteSessions = remember(sessions) {
        sessions.filter { it.isFavorite }.sortedByDescending { it.timestamp }.take(4)
    }

    var sortOrder by remember { mutableStateOf(SessionSortOrder.DATE_NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }

    // Filtering & Sorting Logic
    val filteredSessions = remember(sessions, searchQuery, sortOrder) {
        sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
            .let { list ->
                when (sortOrder) {
                    SessionSortOrder.DATE_NEWEST -> list.sortedByDescending { it.timestamp }
                    SessionSortOrder.DATE_OLDEST -> list.sortedBy { it.timestamp }
                    SessionSortOrder.NAME -> list.sortedBy { it.title }
                    SessionSortOrder.FAVORITES -> list.sortedByDescending { it.isFavorite }
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Home", fontWeight = FontWeight.ExtraBold) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(AppIcons.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                
                // Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search...") },
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(AppIcons.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showSortMenu = true },
                            label = { 
                                Text(
                                    when(sortOrder) {
                                        SessionSortOrder.DATE_NEWEST -> "Newest"
                                        SessionSortOrder.DATE_OLDEST -> "Oldest"
                                        SessionSortOrder.NAME -> "A-Z"
                                        SessionSortOrder.FAVORITES -> "Favorites"
                                    }
                                )
                            },
                            trailingIcon = { Icon(AppIcons.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(24.dp)
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SessionSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayName) },
                                    onClick = { 
                                        sortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        ExtendedFabItem(
                            label = "Incognito Chat",
                            icon = AppIcons.VisibilityOff,
                            containerColor = MaterialTheme.colorScheme.secondary,
                            onClick = {
                                isFabExpanded = false
                                viewModel.createNewSession(isIncognito = true) { id ->
                                    onSessionSelected(id)
                                }
                            }
                        )
                        ExtendedFabItem(
                            label = "Regular Chat",
                            icon = AppIcons.ChatOutline,
                            containerColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                isFabExpanded = false
                                viewModel.createNewSession(isIncognito = false) { id ->
                                    onSessionSelected(id)
                                }
                            }
                        )
                    }
                }
                
                FloatingActionButton(
                    onClick = { if (!isGenerating) isFabExpanded = !isFabExpanded },
                    containerColor = if (isGenerating) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                                    else if (isFabExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    contentColor = if (isGenerating) MaterialTheme.colorScheme.outline
                                  else if (isFabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        if (isFabExpanded) AppIcons.Close else AppIcons.Add,
                        contentDescription = "New Chat Options"
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
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
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
            }

            if (filteredSessions.isEmpty()) {
                item { EmptyIsolatesView() }
            } else {
                items(filteredSessions, key = { it.id }) { session ->
                    IsolateItem(
                        session = session,
                        onClick = { onSessionSelected(session.id) },
                        onDelete = { if (!isGenerating) sessionToDelete = session },
                        onFavoriteToggle = { viewModel.toggleFavorite(session.id, !session.isFavorite) },
                        onDuplicate = { viewModel.duplicateSession(session.id) },
                        onRename = { sessionToRename = session },
                        onCopyAll = {
                            scope.launch {
                                val text = viewModel.getFullChatText(session.id)
                                clipboardManager.setText(AnnotatedString(text))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
}

enum class SessionSortOrder(val displayName: String) {
    DATE_NEWEST("Newest First"),
    DATE_OLDEST("Oldest First"),
    NAME("Name (A-Z)"),
    FAVORITES("Favorites First")
}

@Composable
fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(title) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FavoriteItem(session: ChatSession, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (session.imageUri != null) {
                AsyncImage(
                    model = session.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    AppIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Icon(
                AppIcons.Star,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(2.dp),
                tint = Color(0xFFFFD700)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = session.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ExtendedFabItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IsolateItem(
    session: ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onCopyAll: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(session.timestamp))
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
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
                        if (session.isIncognito) MaterialTheme.colorScheme.secondaryContainer 
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (session.imageUri != null) {
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
                IconButton(onClick = { showMenu = true }) {
                    Icon(AppIcons.More, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (session.isFavorite) "Unfavorite" else "Favorite") },
                        onClick = { onFavoriteToggle(); showMenu = false },
                        leadingIcon = { Icon(if (session.isFavorite) AppIcons.StarOutline else AppIcons.Star, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { onDuplicate(); showMenu = false },
                        leadingIcon = { Icon(AppIcons.Copy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { onRename(); showMenu = false },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy All Text") },
                        onClick = { onCopyAll(); showMenu = false },
                        leadingIcon = { Icon(AppIcons.CopyAll, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyIsolatesView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            AppIcons.ChatOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No matches found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
