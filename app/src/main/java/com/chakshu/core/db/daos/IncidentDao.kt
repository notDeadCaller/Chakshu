package com.chakshu.core.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chakshu.core.db.entities.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)

    @Update
    suspend fun update(incident: IncidentEntity)

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun getById(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE status = :status")
    suspend fun getByStatus(status: String): List<IncidentEntity>

    @Query("UPDATE incidents SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM incidents WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveFlow(): Flow<IncidentEntity?>
}
