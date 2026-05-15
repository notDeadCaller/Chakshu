package com.chakshu.core.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chakshu.core.db.entities.ChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    @Update
    suspend fun update(chunk: ChunkEntity)

    @Query("SELECT * FROM chunks WHERE id = :id")
    suspend fun getById(id: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE incidentId = :incidentId ORDER BY chunkIndex ASC")
    suspend fun getByIncidentId(incidentId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE status = :status")
    suspend fun getByStatus(status: String): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks WHERE incidentId = :incidentId")
    fun getCountFlowForIncident(incidentId: String): Flow<Int>

    @Query("SELECT * FROM chunks WHERE incidentId = :incidentId ORDER BY chunkIndex DESC LIMIT 1")
    fun getLastChunkFlow(incidentId: String): Flow<ChunkEntity?>
}
