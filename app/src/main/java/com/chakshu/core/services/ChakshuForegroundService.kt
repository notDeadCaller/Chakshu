package com.chakshu.core.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.chakshu.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChakshuForegroundService : Service() {

    @Inject
    lateinit var recordingManager: RecordingManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incidentId = intent?.getStringExtra(EXTRA_INCIDENT_ID) ?: return START_NOT_STICKY

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        recordingManager.startRecording(incidentId)
        return START_STICKY
    }

    override fun onDestroy() {
        recordingManager.stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    companion object {
        const val EXTRA_INCIDENT_ID = "incident_id"
        private const val CHANNEL_ID = "chakshu_bg"
        private const val NOTIFICATION_ID = 1001
    }
}
