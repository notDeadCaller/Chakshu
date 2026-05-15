package com.chakshu.core.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.entities.ContactEntity
import com.chakshu.core.services.SmsDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SmsAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val incidentDao: IncidentDao,
    private val smsDispatcher: SmsDispatcher
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val incidentId = inputData.getString(KEY_INCIDENT_ID) ?: return Result.failure()
        val incident = incidentDao.getById(incidentId) ?: run {
            Log.e(TAG, "Incident $incidentId not found")
            return Result.failure()
        }

        // TODO Phase 2: load SMS-enabled contacts from ContactDao and pass them here
        val contacts = listOf(
            ContactEntity(
                id = "test-contact",
                name = "Test",
                phone = "+91XXXXXXXXXX", // TODO Phase 2: replace with DB contacts
                notifySms = true,
                notifyPush = false,
                addedAt = System.currentTimeMillis()
            )
        )

        smsDispatcher.sendAlert(incident, contacts)
        return Result.success()
    }

    companion object {
        const val KEY_INCIDENT_ID = "incident_id"
        private const val TAG = "SmsAlertWorker"
    }
}
