package com.chakshu.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chakshu.core.db.daos.ChunkDao
import com.chakshu.core.db.daos.ContactDao
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.daos.InterferenceEventDao
import com.chakshu.core.db.entities.ChunkEntity
import com.chakshu.core.db.entities.ContactEntity
import com.chakshu.core.db.entities.IncidentEntity
import com.chakshu.core.db.entities.InterferenceEventEntity

@Database(
    entities = [
        IncidentEntity::class,
        ChunkEntity::class,
        ContactEntity::class,
        InterferenceEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChakshuDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun contactDao(): ContactDao
    abstract fun interferenceEventDao(): InterferenceEventDao
}
