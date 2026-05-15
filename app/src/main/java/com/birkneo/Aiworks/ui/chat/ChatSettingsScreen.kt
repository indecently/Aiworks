package com.birkneo.Aiworks.ui.chat

import android.view.HapticFeedbackConstants
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.birkneo.Aiworks.data.database.ChatDatabase
import com.birkneo.Aiworks.ui.settings.components.SettingsSection
import com.birkneo.Aiworks.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    sessionId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val chatDao = remember { ChatDatabase.getDatabase(context).chatDao() }
    val scope = rememberCoroutineScope()

    val session by viewModel.getSessionFlow(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val isIncognito = session?.isIncognito ?: false

    var personaOverride by remember { mutableStateOf<String?>(null) }
    
    val persona = personaOverride ?: session?.persona ?: ""
    val sessionMemory = session?.longTermMemory
    val chatPictureUri = session?.imageUri
    val backgroundUri = session?.backgroundUri

    var messageCount by remember { mutableIntStateOf(0) }
    var tokenEstimate by remember { mutableIntStateOf(0) }

    var memoryToEdit by remember { mutableStateOf<String?>(null) }
    var memoryToEditIndex by remember { mutableIntStateOf(-1) }

    val isCompressing by viewModel.isCompressingContext.collectAsState()
    
    val memoryItems = remember(sessionMemory) {
        sessionMemory?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    LaunchedEffect(sessionId, session, isCompressing) {
        if (!isCompressing) {
            val texts = if (isIncognito) {
                viewModel.messages.value.map { it.text }
            } else {
                chatDao.getRecentMessagesForSession(sessionId, 1000).map { it.text }
            }
            messageCount = texts.size
            tokenEstimate = texts.sumOf { viewModel.estimateTokens(it) }
        }
    }

    val pictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}
            viewModel.updateSessionImage(sessionId, it.toString())
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}
            viewModel.updateSessionBackground(sessionId, it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Privacy Section
            item {
                SettingsSection(icon = AppIcons.Palette, title = "Privacy & Visibility") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Incognito Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (isIncognito) "Chat history is volatile and will be wiped on exit." 
                                       else "Chat history is persisted in secure storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = !isIncognito,
                            onCheckedChange = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.toggleIncognito(sessionId, !it) 
                            }
                        )
                    }
                }
            }

            // Reasoning Section
            item {
                SettingsSection(icon = AppIcons.Psychology, title = "Reasoning Engine") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deep Reasoning", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Enable chain-of-thought for complex problem solving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        val isReasoning by remember(session) { derivedStateOf { session?.isReasoningMode == true } }
                        Switch(
                            checked = isReasoning,
                            onCheckedChange = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.updateSessionReasoning(sessionId, it) 
                            }
                        )
                    }
                }
            }

            // Appearance Section
            item {
                SettingsSection(icon = AppIcons.Edit, title = "Appearance") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (chatPictureUri != null) {
                                    AsyncImage(model = chatPictureUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(AppIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chat Picture", style = MaterialTheme.typography.titleSmall)
                                Text("Visible on the home screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Button(onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                pictureLauncher.launch("image/*") 
                            }) { Text("Change") }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (backgroundUri != null) {
                                    AsyncImage(model = backgroundUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(AppIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chat Background", style = MaterialTheme.typography.titleSmall)
                                Text("Personalize the chat window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Button(onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                backgroundLauncher.launch("image/*") 
                            }) { Text("Set") }
                        }
                    }
                }
            }

            // Persona Section
            item {
                SettingsSection(icon = AppIcons.Psychology, title = "Persona & Instructions") {
                    OutlinedTextField(
                        value = persona,
                        onValueChange = { 
                            personaOverride = it
                            viewModel.updateSessionPersona(sessionId, it)
                        },
                        label = { Text("Custom Instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Memory Section Title & Stats
            item {
                SettingsSection(icon = AppIcons.Storage, title = "Context & Stats") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Messages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("$messageCount", style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Raw History", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("$tokenEstimate", style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LTM Anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                val anchorTokens = viewModel.estimateTokens(sessionMemory ?: "")
                                Text("$anchorTokens", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(text = "Long-Term Memory", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            // Flattened Memory Items
            if (memoryItems.isEmpty()) {
                item {
                    Text("No long-term context stored yet.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                itemsIndexed(memoryItems) { index, item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        tonalElevation = 1.dp
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                memoryToEdit = item; memoryToEditIndex = index 
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(AppIcons.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                val newList = memoryItems.toMutableList().apply { removeAt(index) }
                                viewModel.updateSessionMemory(sessionId, if (newList.isEmpty()) null else newList.joinToString("\n"))
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(AppIcons.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Memory Actions
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (isCompressing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.generateLongTermMemory(sessionId) 
                        }) {
                            Icon(AppIcons.Welcome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Memory")
                        }
                    }
                    if (sessionMemory != null && !isCompressing) {
                        TextButton(onClick = { 
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.clearChatMemory(sessionId) 
                        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        memoryToEdit?.let { initialText ->
            EditMemoryDialog(
                initialText = initialText,
                onDismiss = { memoryToEdit = null; memoryToEditIndex = -1 },
                onConfirm = { updatedText ->
                    val newList = memoryItems.toMutableList().apply { 
                        if (memoryToEditIndex in indices) set(memoryToEditIndex, updatedText)
                    }
                    viewModel.updateSessionMemory(sessionId, newList.joinToString("\n"))
                    memoryToEdit = null; memoryToEditIndex = -1
                }
            )
        }
    }
}

@Composable
fun EditMemoryDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Memory Snippet") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
