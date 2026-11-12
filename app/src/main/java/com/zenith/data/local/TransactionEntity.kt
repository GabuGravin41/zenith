package com.zenith.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zenith.domain.model.Category
import com.zenith.domain.model.TransactionSource
import com.zenith.domain.model.TransactionType

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val amount: Double,
    val type: String, // CREDIT, DEBIT
    val description: String,
    val category: String, // Category name
    val source: String, // MPESA, EQUITY, etc.
    val rawText: String,
    val balance: Double? = null,
    val reference: String? = null,
    val isSuspicious: Boolean = false
)
