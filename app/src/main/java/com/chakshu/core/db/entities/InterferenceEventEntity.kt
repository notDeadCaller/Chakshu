package com.chakshu.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interference_events")
data class InterferenceEventEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val eventType: String,
    val detectedAt: Long,
    val synced: Boolean
)
