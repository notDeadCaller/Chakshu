package com.chakshu.core.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {

    fun getDeviceHash(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
        return CryptoUtils.computeSha256((androidId + Build.FINGERPRINT).toByteArray())
    }
}
