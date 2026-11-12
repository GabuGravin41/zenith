package com.zenith.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "party_overrides")
data class PartyOverrideEntity(
    @PrimaryKey val partyName: String,
    val isDismissed: Boolean = false,
    val isFlipped: Boolean = false
)

@Dao
interface PartyOverrideDao {
    @Query("SELECT * FROM party_overrides")
    fun getAllOverrides(): Flow<List<PartyOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: PartyOverrideEntity)

    @Query("DELETE FROM party_overrides WHERE partyName = :name")
    suspend fun delete(name: String)
}
