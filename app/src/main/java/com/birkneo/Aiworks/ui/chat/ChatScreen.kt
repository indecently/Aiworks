package com.birkneo.Aiworks.ui.chat

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.ui.theme.AppIcons
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val streamingMessage by viewModel.streamingMessage.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    val isCompressing by viewModel.isCompressingContext.collectAsState()
    val pendingImage by viewModel.pendingImageUri.collectAsState()
    val pendingAudio by viewModel.pendingAudioUri.collectAsState()
    
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    
    var backgroundUri by remember { mutableStateOf<String?>(null) }
    val chatDao = remember { com.birkneo.Aiworks.data.database.ChatDatabase.getDatabase(context).chatDao() }

    LaunchedEffect(sessionId) {
        viewModel.selectSession(sessionId)
        val session = chatDao.getSessionById(sessionId)
        backgroundUri = session?.backgroundUri
    }

    DisposableEffect(sessionId) {
        onDispose {
            // Only stop speaking, don't close session yet to allow settings/memory generation to persist
            viewModel.stopSpeaking()
        }
    }

    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showUndoConfirmation by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    
    // Permission States
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setPendingImage(it.toString()) }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.setPendingImage(it.toString()) }
        }
    }

    LaunchedEffect(messages.size, streamingMessage?.text?.length) {
        if (messages.isNotEmpty()) {
            // Anchor to absolute bottom during generation
            listState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        ModelStatusIndicator(status = modelStatus)
                    }
                },
                actions = {
                    IconButton(onClick = { if (!isGenerating) showUndoConfirmation = true }) {
                        Icon(
                            imageVector = AppIcons.Reset, 
                            contentDescription = "Undo Last",
                            tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { if (!isGenerating) showClearConfirmation = true }) {
                        Icon(
                            AppIcons.Delete, 
                            contentDescription = "Clear Chat",
                            tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { if (!isGenerating) onNavigateToSettings() }) {
                        Icon(
                            AppIcons.Settings, 
                            contentDescription = "Settings",
                            tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
            AnimatedVisibility(visible = isCompressing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                AnimatedVisibility(
                    visible = pendingImage != null || pendingAudio != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pendingImage?.let { uri ->
                                MediaPreviewItem(
                                    uri = uri,
                                    type = "image",
                                    onRemove = { viewModel.setPendingImage(null) }
                                )
                            }
                            pendingAudio?.let { uri ->
                                MediaPreviewItem(
                                    uri = uri,
                                    type = "audio",
                                    onRemove = { viewModel.setPendingAudio(null) }
                                )
                            }
                        }
                    }
                }

                ChatInput(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    onStop = { viewModel.stopGeneration() },
                    onCameraClick = {
                        if (cameraPermission.status.isGranted) {
                            val uri = createTempImageUri(context)
                            cameraImageUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermission.launchPermissionRequest()
                        }
                    },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onRecordToggle = { recording ->
                        if (micPermission.status.isGranted) {
                            if (recording) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.startRecording()
                            } else {
                                viewModel.stopRecording()
                            }
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    isGenerating = isGenerating,
                    isRecording = isRecording,
                    amplitude = recordingAmplitude,
                    enabled = modelStatus is ModelStatus.Ready
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Background Image
            if (backgroundUri != null) {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.4f
                )
                // Scrim for readability
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            clipboardManager.setText(AnnotatedString(message.text))
                        },
                        onSpeak = { viewModel.speak(it) },
                        onEdit = { viewModel.editMessage(message.id, it) },
                        onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                        onDelete = { messageToDelete = message }
                    )
                }
                
                if (isGenerating && messages.none { it.role == MessageRole.ASSISTANT && it.isStreaming }) {
                    item {
                        StreamingPlaceholder()
                    }
                }

                if (!isGenerating && messages.isNotEmpty() && messages.last().role == MessageRole.ASSISTANT) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = { if (!isGenerating) viewModel.regenerateResponse() },
                                enabled = !isGenerating,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    disabledContentColor = MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Regenerate Response", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        
        if (showClearConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearConfirmation = false },
                icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Clear Chat History?") },
                text = { Text("This will permanently delete all messages in this conversation.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearChat()
                            showClearConfirmation = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showUndoConfirmation) {
            AlertDialog(
                onDismissRequest = { showUndoConfirmation = false },
                icon = { Icon(AppIcons.Reset, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Undo Last Exchange?") },
                text = { Text("This will remove the last AI response and your last prompt. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.undoLastExchange()
                            showUndoConfirmation = false
                        }
                    ) {
                        Text("Undo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUndoConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        messageToDelete?.let { message ->
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Delete Message?") },
                text = { Text("This will permanently remove this message from your history.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessageById(message.id)
                            messageToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun StreamingPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppIcons.Audio,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val transition = rememberInfiniteTransition(label = "dots")
            repeat(3) { index ->
                val opacity by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "opacity"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = opacity))
                )
            }
        }
    }
}

@Composable
fun MediaPreviewItem(
    uri: String,
    type: String,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (type == "image") {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f), CircleShape)
        ) {
            Icon(AppIcons.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    onLongClick: () -> Unit,
    onSpeak: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    var showEditDialog by remember { mutableStateOf(false) }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // High-contrast background for audio in non-user bubbles
    val audioContainerColor = if (isUser) {
        containerColor.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Audio,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Message Body Container
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (isUser) 20.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 20.dp
                        )
                    )
                    .background(containerColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(12.dp)
            ) {
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Attached Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(10.dp))
                }
                
                message.audioUri?.let { uri ->
                    AudioPlayer(uri = uri, contentColor = contentColor, containerColor = audioContainerColor)
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Action Buttons - Moved outside the main bubble container for better touch delegation on large outputs
        if (message.text.isNotBlank() && !message.isStreaming) {
            Row(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isUser) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit Response",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSpeak(message.text) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditMessageDialog(
            initialText = message.text,
            onDismiss = { showEditDialog = false },
            onConfirm = { 
                onEdit(it)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Prompt") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("Update & Resend")
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
fun AudioPlayer(uri: String, contentColor: Color, containerColor: Color) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    if (player == null) {
                        player = MediaPlayer.create(context, Uri.parse(uri))?.apply {
                            setOnCompletionListener { 
                                isPlaying = false 
                            }
                        }
                    }
                    player?.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = contentColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        // Simple visualization for player
        val transition = rememberInfiniteTransition(label = "player_bars")
        val barHeight by transition.animateFloat(
            initialValue = 4f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_height"
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(15) { index ->
                val h = if (isPlaying) (barHeight * (1 + index % 3 * 0.5f)) else 4f
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(h.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.6f))
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Voice", color = contentColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val barCount = 40
    // Use a persistent state for the waveform that isn't wiped on every recompose
    val amplitudes = remember { mutableStateOf(FloatArray(barCount)) }
    
    // Explicitly update state inside LaunchedEffect for stability
    LaunchedEffect(amplitude) {
        val current = amplitudes.value
        for (i in current.indices.reversed()) {
            if (i > 0) current[i] = current[i - 1]
        }
        current[0] = amplitude.coerceIn(0.05f, 1.0f)
        // Force a recomposition by re-assigning the reference
        amplitudes.value = current.copyOf()
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / barCount
        val centerY = size.height / 2f
        val current = amplitudes.value

        current.forEachIndexed { index, amp ->
            val h = (amp * size.height).coerceAtLeast(4f) // Ensure minimum visibility
            val x = index * barWidth
            drawRoundRect(
                color = color.copy(alpha = (1f - index.toFloat() / barCount).coerceIn(0.3f, 1f)),
                topLeft = Offset(x + 2f, centerY - h / 2),
                size = Size(barWidth - 4f, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRecordToggle: (Boolean) -> Unit,
    isGenerating: Boolean,
    isRecording: Boolean,
    amplitude: Float,
    enabled: Boolean
) {
    val recordingScale by animateFloatAsState(if (isRecording) 1.2f else 1f, label = "recording_scale")
    val recordingColor by animateColorAsState(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, label = "recording_color")

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onGalleryClick, enabled = enabled && !isGenerating) {
                    Icon(AppIcons.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Box(modifier = Modifier.weight(1f)) {
                if (isRecording) {
                    WaveformVisualizer(
                        amplitude = amplitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask anything...", color = MaterialTheme.colorScheme.outline) },
                        enabled = enabled && !isGenerating,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = onCameraClick, 
                                enabled = enabled && !isGenerating,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(AppIcons.Camera, contentDescription = "Camera", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(AppIcons.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            } else if (text.isBlank()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(recordingScale)
                        .background(recordingColor.copy(alpha = 0.1f), CircleShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                if (enabled) onRecordToggle(true)
                                waitForUpOrCancellation()
                                onRecordToggle(false)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Mic, 
                        contentDescription = "Record", 
                        tint = recordingColor
                    )
                }
            } else {
                IconButton(
                    onClick = { if (enabled) onSend() },
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        AppIcons.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

fun createTempImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
