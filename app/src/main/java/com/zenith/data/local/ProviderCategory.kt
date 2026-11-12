package com.zenith.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_categories")
data class ProviderCategory(
    @PrimaryKey val providerName: String,
    val category: String
)
