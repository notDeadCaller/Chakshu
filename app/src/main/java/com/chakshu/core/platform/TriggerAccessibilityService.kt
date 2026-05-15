package com.chakshu.core.platform

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.chakshu.core.services.IncidentManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
    private var cancelWindowJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        incidentManager = EntryPointAccessors
            .fromApplication(applicationContext, TriggerEntryPoint::class.java)
            .incidentManager()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when {
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> {
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
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP &&
            cancelWindowJob?.isActive == true -> {
                cancelWindowJob?.cancel()
                cancelWindowJob = null
                Log.d(TAG, "Trigger cancelled by user")
            }
        }
        return false
    }

    private fun onTriggerDetected() {
        if (cancelWindowJob?.isActive == true) return
        vibrateConfirmation()
        cancelWindowJob = serviceScope.launch {
            delay(CANCEL_WINDOW_MS)
            incidentManager.startIncident()
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

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "TriggerService"
        private const val TRIGGER_WINDOW_MS = 1_500L
        private const val CANCEL_WINDOW_MS = 4_000L
    }
}
