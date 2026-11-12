package com.zenith.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderCategoryDao {
    @Query("SELECT * FROM provider_categories")
    fun getAllProviderCategories(): Flow<List<ProviderCategory>>

    @Query("SELECT category FROM provider_categories WHERE providerName = :name LIMIT 1")
    suspend fun getCategoryForProvider(name: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderCategory(providerCategory: ProviderCategory)
}
