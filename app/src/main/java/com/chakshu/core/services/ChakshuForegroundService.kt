package com.chakshu.core.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent

import androidx.core.app.NotificationCompat
import com.chakshu.R
import com.chakshu.core.platform.TriggerAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import javax.inject.Inject

@AndroidEntryPoint
class ChakshuForegroundService : Service() {

    @Inject lateinit var incidentManager: IncidentManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorsRegistered = false

    // --- Shake detection state (sensor callback thread only) ---
    private val shakeTimestamps = LongArray(3) { 0L }
    private var shakeIndex = 0
    private var lastShakeAxis: Float = 0f
    private var lastShakeTime: Long = 0L

    @Volatile private var lastTriggerTime = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            if (magnitude < SHAKE_THRESHOLD) return

            val now = System.currentTimeMillis()

            // Reset direction tracking after a long pause (gesture start over)
            if (lastShakeTime > 0 && now - lastShakeTime > SHAKE_WINDOW_MS) {
                lastShakeAxis = 0f
            }

            // Minimum gap between consecutive recorded shakes — filters violent single jerk
            if (now - lastShakeTime < MIN_SHAKE_INTERVAL_MS) return

            // Require direction reversal on the dominant axis
            val dominantAxis = when {
                abs(x) > abs(y) && abs(x) > abs(z) -> x
                abs(y) > abs(z) -> y
                else -> z
            }
            val isReversal = lastShakeAxis == 0f ||
                (dominantAxis > 0 && lastShakeAxis < 0) ||
                (dominantAxis < 0 && lastShakeAxis > 0)

            if (!isReversal) return

            lastShakeAxis = dominantAxis
            lastShakeTime = now

            shakeTimestamps[shakeIndex % 3] = now
            shakeIndex++

            if (shakeIndex >= 3) {
                val newest = shakeTimestamps.max()
                val oldest = shakeTimestamps.min()
                if (newest - oldest < SHAKE_WINDOW_MS) {
                    val inCooldown = now - lastTriggerTime < TRIGGER_COOLDOWN_MS
                    val incidentActive = incidentManager.getActiveIncidentId() != null
                    if (!inCooldown && !incidentActive) {
                        lastTriggerTime = now
                        lastShakeAxis = 0f
                        Log.d(TAG, "Shake trigger detected")
                        TriggerAccessibilityService.instance?.triggerFromShake()
                    }
                } else {
                    lastShakeAxis = 0f  // window expired, reset direction context
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIF_ID, buildIdleNotification())

        if (!sensorsRegistered) {
            acquireWakeLock()
            setupMediaSession()
            registerShakeSensor()
            startAccessibilityMonitor()
            sensorsRegistered = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        serviceRunning.value = false
        serviceScope.cancel()
        getSystemService(SensorManager::class.java).unregisterListener(shakeListener)
        mediaSession?.release()
        mediaSession = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Accessibility monitor ---

    private fun startAccessibilityMonitor() {
        serviceScope.launch {
            while (isActive) {
                checkAccessibilityService()
                delay(ACCESSIBILITY_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkAccessibilityService() {
        if (TriggerAccessibilityService.instance == null) {
            accessibilityEnabled.value = false
            Log.w(TAG, "Accessibility service is OFF")
            showAccessibilityDisabledNotification()
        } else {
            accessibilityEnabled.value = true
            getSystemService(NotificationManager::class.java).cancel(NOTIF_ID_ACCESSIBILITY)
        }
    }

    private fun showAccessibilityDisabledNotification() {
        val serviceId = "$packageName/${TriggerAccessibilityService::class.java.name}"
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ACTION_ACCESSIBILITY_DETAILS_SETTINGS
        else
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        val settingsIntent = Intent(action)
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settingsIntent.putExtra(EXTRA_ACCESSIBILITY_SHORTCUT_TARGET, serviceId)
        }
        val pi = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channel = NotificationChannel(
            CHANNEL_ID_ALERT,
            getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle(getString(R.string.notification_trigger_inactive_title))
            .setContentText(getString(R.string.notification_trigger_inactive_text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_ACCESSIBILITY, notification)
    }

    // --- Sensor + hardware setup ---

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chakshu:trigger")
            .also { it.acquire() }
    }

    private fun registerShakeSensor() {
        val sm = getSystemService(SensorManager::class.java)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Log.w(TAG, "Accelerometer unavailable — shake trigger disabled")
            return
        }
        sm.registerListener(shakeListener, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    @Suppress("DEPRECATION")
    private fun setupMediaSession() {
        val session = MediaSession(this, "chakshu_trigger")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .build()
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                } ?: return false
                if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
                if (keyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    TriggerAccessibilityService.instance?.onVolumeDownFromMedia()
                }
                return false
            }
        })
        session.isActive = true
        mediaSession = session
    }

    // --- Incident notification state ---

    fun onIncidentStarted() {
        getSystemService(NotificationManager::class.java)
            .notify(FOREGROUND_NOTIF_ID, buildActiveNotification())
    }

    fun onIncidentEnded() {
        getSystemService(NotificationManager::class.java)
            .notify(FOREGROUND_NOTIF_ID, buildIdleNotification())
    }

    // --- Notification helpers ---

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_MIN: no sound, no status bar icon, appears only
        // under "Silent" section in notification shade — intentional
        // for stealth operation
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_IDLE, getString(R.string.notification_idle_channel_name),
                NotificationManager.IMPORTANCE_MIN).also { it.setShowBadge(false) }
        )
        // IMPORTANCE_MIN: no sound, no status bar icon, appears only
        // under "Silent" section in notification shade — intentional
        // for stealth operation
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_ACTIVE, getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN).also {
                it.setShowBadge(false)
                it.enableVibration(false)
                it.enableLights(false)
            }
        )
    }

    private fun buildIdleNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID_IDLE)
            .setContentTitle("System sync")
            .setContentText("Keeping services up to date")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setColor(0)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun buildActiveNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setContentTitle("System sync")
            .setContentText("Sync in progress")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setColor(0)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        private const val CHANNEL_ID_IDLE = "chakshu_idle"
        private const val CHANNEL_ID_ACTIVE = "chakshu_active"
        private const val CHANNEL_ID_ALERT = "chakshu_alert"
        private const val FOREGROUND_NOTIF_ID = 1001
        private const val NOTIF_ID_ACCESSIBILITY = 1002
        private const val TAG = "ChakshuForeground"

        @Volatile var instance: ChakshuForegroundService? = null

        const val SHAKE_THRESHOLD = 27.0f          // m/s²; ~2.75g
        private const val SHAKE_WINDOW_MS = 2_000L
        private const val MIN_SHAKE_INTERVAL_MS = 150L
        private const val TRIGGER_COOLDOWN_MS = 10_000L
        private const val ACCESSIBILITY_CHECK_INTERVAL_MS = 30_000L

        val serviceRunning = MutableStateFlow(false)
        val accessibilityEnabled = MutableStateFlow(true)

        // String literals: the API-33 Settings constants are unavailable on older SDKs
        // at compile time. Using the raw action/extra strings is equivalent.
        const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
        const val EXTRA_ACCESSIBILITY_SHORTCUT_TARGET = "accessibility_shortcut_target"
    }
}
