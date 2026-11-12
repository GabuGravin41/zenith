package com.zenith.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_filters")
data class MediaFilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val pattern: String,
    val type: String, // "GROUP" or "KEYWORD"
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
