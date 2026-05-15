package com.chakshu.core.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chakshu.core.db.entities.InterferenceEventEntity

@Dao
interface InterferenceEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: InterferenceEventEntity)

    @Update
    suspend fun update(event: InterferenceEventEntity)

    @Query("SELECT * FROM interference_events WHERE id = :id")
    suspend fun getById(id: String): InterferenceEventEntity?

    @Query("SELECT * FROM interference_events WHERE incidentId = :incidentId ORDER BY detectedAt ASC")
    suspend fun getByIncidentId(incidentId: String): List<InterferenceEventEntity>

    @Query("SELECT * FROM interference_events WHERE synced = 0")
    suspend fun getUnsynced(): List<InterferenceEventEntity>
}
