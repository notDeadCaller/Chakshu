package com.chakshu.core.di

import android.content.Context
import androidx.room.Room
import com.chakshu.core.db.ChakshuDatabase
import com.chakshu.core.db.daos.ChunkDao
import com.chakshu.core.db.daos.ContactDao
import com.chakshu.core.db.daos.IncidentDao
import com.chakshu.core.db.daos.InterferenceEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChakshuDatabase =
        Room.databaseBuilder(context, ChakshuDatabase::class.java, "chakshu.db")
            .build()

    @Provides
    fun provideIncidentDao(db: ChakshuDatabase): IncidentDao = db.incidentDao()

    @Provides
    fun provideChunkDao(db: ChakshuDatabase): ChunkDao = db.chunkDao()

    @Provides
    fun provideContactDao(db: ChakshuDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideInterferenceEventDao(db: ChakshuDatabase): InterferenceEventDao =
        db.interferenceEventDao()
}
