package com.birkneo.Aiworks.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.birkneo.Aiworks.di.GemmaContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelPersistenceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val CHANNEL_ID = "gemma_model_persistence"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.birkneo.Aiworks.STOP_SERVICE"
        private const val EXTRA_IS_GENERATING = "is_generating"

        fun start(context: Context) {
            val intent = Intent(context, ModelPersistenceService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ModelPersistenceService::class.java)
            context.stopService(intent)
        }

        fun updateStatus(context: Context, isGenerating: Boolean) {
            val intent = Intent(context, ModelPersistenceService::class.java).apply {
                putExtra(EXTRA_IS_GENERATING, isGenerating)
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanupAndStop()
            return START_NOT_STICKY
        }

        val isGenerating = intent?.getBooleanExtra(EXTRA_IS_GENERATING, false) ?: false

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(isGenerating))

        return START_STICKY
    }

    override fun onDestroy() {
        cleanupAndStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cleanupAndStop() {
        serviceScope.launch {
            // PERF: Ensure native memory release doesn't block the service's final destruction
            GemmaContainer.getGemmaInference(applicationContext).close()
            withContext(Dispatchers.Main) {
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val name = "Gemma AI Status"
        val descriptionText = "Ensures the AI model remains active in the background"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(isGenerating: Boolean = false): Notification {
        val mainActivityClass = Class.forName("com.birkneo.Aiworks.MainActivity")
        val pendingIntent: PendingIntent =
            Intent(this, mainActivityClass).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val title = if (isGenerating) "AI is Generating..." else "Gemma AI Active"
        val text = if (isGenerating) "Please wait while a response is being prepared." else "Local AI is resident in memory and ready."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
