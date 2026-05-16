package com.chakshu.core.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.chakshu.core.services.IncidentManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TriggerAccessibilityService : AccessibilityService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TriggerEntryPoint {
        fun incidentManager(): IncidentManager
    }

    private lateinit var incidentManager: IncidentManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val volDownTimestamps = LongArray(3) { 0L }
    private var tsIndex = 0
    private var inCancellationWindow = false
    private var cancellationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        incidentManager = EntryPointAccessors
            .fromApplication(applicationContext, TriggerEntryPoint::class.java)
            .incidentManager()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Programmatically ensure FLAG_REQUEST_FILTER_KEY_EVENTS is set, in case
        // the XML attribute is not honoured on a particular device/ROM.
        serviceInfo = serviceInfo?.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        Log.d(TAG, "ACTION_DOWN: keyCode=${event.keyCode}")
        when {
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> recordVolDown()
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && inCancellationWindow -> {
                cancellationJob?.cancel()
                inCancellationWindow = false
                Log.d(TAG, "Trigger cancelled by user")
            }
        }
        return false
    }

    // Secondary input path: called by ChakshuForegroundService MediaSession when screen is off.
    fun onVolumeDownFromMedia() = recordVolDown()

    // Screen-off trigger path: called from sensor callback thread — post to main thread.
    fun triggerFromShake() {
        Log.d(TAG, "Trigger source: shake (screen-off)")
        Handler(Looper.getMainLooper()).post { onTriggerDetected() }
    }

    private fun recordVolDown() {
        volDownTimestamps[tsIndex % 3] = System.currentTimeMillis()
        tsIndex++
        if (tsIndex >= 3) {
            val newest = volDownTimestamps.max()
            val oldest = volDownTimestamps.min()
            if (newest - oldest < TRIGGER_WINDOW_MS) {
                onTriggerDetected()
            }
        }
    }

    private fun onTriggerDetected() {
        if (inCancellationWindow) return
        try {
            vibrateConfirmation()
        } catch (e: Exception) {
            Log.e(TAG, "Crash in vibrateConfirmation", e)
        }
        inCancellationWindow = true
        Log.d(TAG, "Cancellation window opened")
        cancellationJob = serviceScope.launch {
            try {
                delay(CANCEL_WINDOW_MS)
                inCancellationWindow = false
                Log.d(TAG, "Window expired — starting incident")
                incidentManager.startIncident()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Crash in trigger handler coroutine", e)
            }
        }
    }

    private fun vibrateConfirmation() {
        val vibrator = getVibrator()
        val pattern = VibrationEffect.createWaveform(
            longArrayOf(0, 80, 100, 80, 100, 80),
            intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
            -1
        )
        vibrator.vibrate(pattern)
    }

    private fun getVibrator(): Vibrator =
        checkNotNull(getSystemService(Vibrator::class.java)) { "Vibrator unavailable" }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "TriggerService"
        private const val TRIGGER_WINDOW_MS = 1_500L
        private const val CANCEL_WINDOW_MS = 4_000L

        @Volatile
        var instance: TriggerAccessibilityService? = null
    }
}
