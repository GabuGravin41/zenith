package com.zenith.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFilterDao {
    @Query("SELECT * FROM media_filters ORDER BY createdAt DESC")
    fun getAllFilters(): Flow<List<MediaFilterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: MediaFilterEntity)

    @Delete
    suspend fun deleteFilter(filter: MediaFilterEntity)

    @Query("UPDATE media_filters SET isEnabled = :enabled WHERE id = :id")
    suspend fun setFilterEnabled(id: Int, enabled: Boolean)
}
