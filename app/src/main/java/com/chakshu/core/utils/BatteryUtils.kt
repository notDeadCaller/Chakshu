package com.chakshu.core.utils

import android.content.Context
import android.os.BatteryManager

object BatteryUtils {

    fun getBatteryPct(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
