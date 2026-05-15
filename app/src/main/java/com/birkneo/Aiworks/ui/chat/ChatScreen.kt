package com.birkneo.Aiworks.ui.chat

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.ui.chat.components.*
import com.birkneo.Aiworks.ui.theme.AppIcons
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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
    
    val view = LocalView.current
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
            viewModel.stopSpeaking()
        }
    }

    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showUndoConfirmation by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

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
            val isGeneratingOrStreaming = isGenerating || streamingMessage != null
            if (isGeneratingOrStreaming) {
                // Determine if we should snap to bottom
                val layoutInfo = listState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                val totalItemsCount = layoutInfo.totalItemsCount
                
                // If we are near the bottom (within 2 items), snap instantly
                // This is much more efficient than animateScrollToItem during high-speed streaming
                if (lastVisibleItem != null && lastVisibleItem.index >= totalItemsCount - 3) {
                    listState.scrollToItem(messages.size - 1)
                }
            } else {
                // For a standard new message (initial send), use a smooth animation
                listState.animateScrollToItem(messages.size - 1)
            }
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
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            )
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    AnimatedVisibility(visible = isCompressing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 32.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Left Bubble (Status Island)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            tonalElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                ModelStatusIndicator(status = modelStatus)
                            }
                        }

                        // Middle Gap (Wallpaper Void)
                        Spacer(modifier = Modifier.weight(1f))

                        // 2. Right Bubble (Control Island)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                IconButton(onClick = { 
                                    if (!isGenerating) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        showUndoConfirmation = true 
                                    }
                                }) {
                                    Icon(
                                        imageVector = AppIcons.Reset, 
                                        contentDescription = "Undo Last",
                                        tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { 
                                    if (!isGenerating) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        showClearConfirmation = true 
                                    }
                                }) {
                                    Icon(
                                        imageVector = AppIcons.Delete, 
                                        contentDescription = "Clear Chat",
                                        tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { 
                                    if (!isGenerating) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onNavigateToSettings() 
                                    }
                                }) {
                                    Icon(
                                        imageVector = AppIcons.Settings, 
                                        contentDescription = "Settings",
                                        tint = if (isGenerating) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.Transparent)
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
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    viewModel.startRecording()
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                clipboardManager.setText(AnnotatedString(message.text))
                            },
                            onSpeak = { viewModel.speak(it) },
                            onEdit = { viewModel.editMessage(message.id, it) },
                            onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                            onDelete = { messageToDelete = message },
                            onRegenerate = { viewModel.regenerateResponse() }
                        )
                    }
                    
                    if (isGenerating && messages.none { it.role == MessageRole.ASSISTANT && it.isStreaming }) {
                        item {
                            StreamingPlaceholder()
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
}

fun createTempImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
