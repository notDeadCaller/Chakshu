package com.chakshu.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val triggeredAt: Long,
    val deviceHash: String,
    val batteryPct: Int,
    val networkType: String,
    val cellTowerId: String?,
    val lat: Double?,
    val lon: Double?,
    val status: String,
    val createdAt: Long
)
