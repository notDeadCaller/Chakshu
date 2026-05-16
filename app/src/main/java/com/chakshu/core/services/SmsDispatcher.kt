package com.chakshu.core.services

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.chakshu.BuildConfig
import com.chakshu.core.db.entities.ContactEntity
import com.chakshu.core.db.entities.IncidentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun sendAlert(incident: IncidentEntity, contacts: List<ContactEntity>) {
        val body = buildSmsBody(incident)
        val smsManager = getSmsManager()

        val phones = contacts.filter { it.notifySms }.map { it.phone }.toMutableList()
        if (!BuildConfig.DEBUG) {
            phones.add("XXX")   //TODO: CHANGE BEFORE PROD
        }

        for (phone in phones) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG build — SMS suppressed. Would send to $phone: $body")
            } else {
                try {
                    smsManager.sendTextMessage(phone, null, body, null, null)
                    Log.d(TAG, "SMS sent to $phone")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to $phone", e)
                }
            }
        }
    }

    private fun buildSmsBody(incident: IncidentEntity): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val dateStr = fmt.format(Date(incident.triggeredAt)) + " IST"
        val shortId = incident.id.take(8)

        return buildString {
            // TODO Phase 2: replace "User" with actual user name from profile
            append("[CHAKSHU] User needs help.\n")
            append("$dateStr\n")
            if (incident.lat != null && incident.lon != null) {
                append("https://www.openstreetmap.org/?mlat=${incident.lat}&mlon=${incident.lon}&zoom=16\n")
            }
            append("ID: $shortId")
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)!!
        } else {
            SmsManager.getDefault()
        }
    }

    companion object {
        private const val TAG = "SmsDispatcher"
    }
}
