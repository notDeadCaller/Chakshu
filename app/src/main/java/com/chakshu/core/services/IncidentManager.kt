package com.chakshu.core.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.entities.IncidentEntity
import com.chakshu.core.utils.BatteryUtils
import com.chakshu.core.utils.DeviceUtils
import com.chakshu.core.utils.NtpUtils
import com.chakshu.core.workers.SmsAlertWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val incidentDao: IncidentDao,
    private val recordingManager: RecordingManager
) {
    private var activeIncidentId: String? = null

    suspend fun startIncident() = withContext(Dispatchers.IO) {
        try {
            if (activeIncidentId != null) {
                Log.d(TAG, "Incident already active: $activeIncidentId")
                return@withContext
            }

            val id = UUID.randomUUID().toString()
            val triggeredAt = NtpUtils.getCurrentTimeMs()
            val now = System.currentTimeMillis()

            val incident = IncidentEntity(
                id = id,
                triggeredAt = triggeredAt,
                deviceHash = DeviceUtils.getDeviceHash(context),
                batteryPct = BatteryUtils.getBatteryPct(context),
                networkType = getNetworkType(),
                cellTowerId = null,
                lat = null,
                lon = null,
                status = "ACTIVE",
                createdAt = now
            )
            incidentDao.insert(incident)
            activeIncidentId = id
            Log.d(TAG, "Incident started: $id")

            val smsRequest = OneTimeWorkRequestBuilder<SmsAlertWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(SmsAlertWorker.KEY_INCIDENT_ID to id))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("sms_$id", ExistingWorkPolicy.KEEP, smsRequest)

            recordingManager.startRecording(id)
            ChakshuForegroundService.instance?.onIncidentStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Crash in startIncident", e)
        }
    }

    suspend fun endIncident(incidentId: String) = withContext(Dispatchers.IO) {
        incidentDao.updateStatus(incidentId, "ENDED")
        activeIncidentId = null
        recordingManager.stopRecording()
        ChakshuForegroundService.instance?.onIncidentEnded()
        Log.d(TAG, "Incident ended: $incidentId")
    }

    fun getActiveIncidentId(): String? = activeIncidentId

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "LTE"
            else -> "NONE"
        }
    }

    companion object {
        private const val TAG = "IncidentManager"
    }
}
