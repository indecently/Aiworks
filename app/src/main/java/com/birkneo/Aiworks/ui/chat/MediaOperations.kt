package com.birkneo.Aiworks.ui.chat

import androidx.compose.ui.platform.LocalView
import java.io.File

fun ChatViewModel.startRecording() {
    val file = File(getApplication<android.app.Application>().cacheDir, "audio_${System.currentTimeMillis()}.wav")
    currentAudioFile = file
    audioRecorder.start(file)
    _isRecording.value = true
}

fun ChatViewModel.stopRecording() {
    audioRecorder.stop()
    _isRecording.value = false
    val uri = currentAudioFile?.toURI().toString()
    _pendingAudioUri.value = uri
    // PRACTICAL FIX: Clear pending image when audio recording is captured
    _pendingImageUri.value = null
}

fun ChatViewModel.setPendingImage(uri: String?) {
    _pendingImageUri.value = uri
    // PRACTICAL FIX: Prevent OpenCL crash by enforcing one media type per prompt
    if (uri != null) _pendingAudioUri.value = null
}

fun ChatViewModel.setPendingAudio(uri: String?) {
    _pendingAudioUri.value = uri
    // PRACTICAL FIX: Prevent OpenCL crash by enforcing one media type per prompt
    if (uri != null) _pendingImageUri.value = null
}
