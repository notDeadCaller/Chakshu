package com.chakshu.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunks",
    foreignKeys = [ForeignKey(
        entity = IncidentEntity::class,
        parentColumns = ["id"],
        childColumns = ["incidentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("incidentId")]
)
data class ChunkEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val chunkIndex: Int,
    val filePath: String,
    val durationMs: Long,
    val sha256: String?,
    val ipfsCid: String?,
    val otsProof: String?,
    val lat: Double?,
    val lon: Double?,
    val recordedAt: Long,
    val uploadedAt: Long?,
    val status: String
)
