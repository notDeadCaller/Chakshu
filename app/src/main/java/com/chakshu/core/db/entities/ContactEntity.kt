package com.chakshu.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val notifySms: Boolean,
    val notifyPush: Boolean,
    val addedAt: Long
)
