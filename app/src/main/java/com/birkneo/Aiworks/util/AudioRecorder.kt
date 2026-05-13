package com.birkneo.Aiworks.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    // TIE TO LIFECYCLE: Using a managed SupervisorJob to allow clean cleanup
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    fun start(outputFile: File) {
        stop()

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) return
        
        audioRecord = record
        record.startRecording()

        recordingJob = scope.launch {
            FileOutputStream(outputFile).use { fos ->
                // Write a placeholder header (44 bytes)
                writeWavHeader(fos, channelConfig, sampleRate, audioFormat, 0)
                
                val buffer = ByteArray(bufferSize)
                val shortBuffer = ShortArray(bufferSize / 2)
                var totalAudioLen = 0L
                
                while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = record.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        // Update amplitude
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += shortBuffer[i] * shortBuffer[i]
                        }
                        val rms = Math.sqrt(sum / read)
                        _amplitude.value = (rms.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f) * 2f

                        // Convert shorts back to bytes for writing
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val s = shortBuffer[i].toInt()
                            byteBuffer[i * 2] = (s and 0xff).toByte()
                            byteBuffer[i * 2 + 1] = (s shr 8 and 0xff).toByte()
                        }
                        fos.write(byteBuffer, 0, byteBuffer.size)
                        totalAudioLen += byteBuffer.size
                    }
                }
                
                // Seek back to start and update header with correct lengths
                fos.channel.position(0)
                writeWavHeader(fos, channelConfig, sampleRate, audioFormat, totalAudioLen)
            }
        }
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
        _amplitude.value = 0f
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        channelConfig: Int,
        sampleRate: Int,
        audioFormat: Int,
        totalAudioLen: Long
    ) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val bitDepth = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitDepth / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // fmt 
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // length of fmt chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte() // block align
        header[33] = 0
        header[34] = bitDepth.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte() // data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
}
