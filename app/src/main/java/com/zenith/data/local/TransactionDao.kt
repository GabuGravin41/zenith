package com.zenith.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class SourceBalance(
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "balance") val balance: Double
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET category = :newCategory WHERE description = :description")
    suspend fun updateCategoryByDescription(description: String, newCategory: String)

    @Query("SELECT * FROM transactions WHERE description = :description")
    suspend fun getTransactionsByDescription(description: String): List<TransactionEntity>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT'")
    fun getTotalExpense(): Flow<Double?>

    @Query("SELECT balance FROM transactions WHERE balance IS NOT NULL ORDER BY date DESC LIMIT 1")
    fun getLatestBalance(): Flow<Double?>

    @Query("UPDATE transactions SET isSuspicious = :isSuspicious WHERE id = :id")
    suspend fun updateSuspiciousStatus(id: String, isSuspicious: Boolean)

    @Query("UPDATE transactions SET category = :category, needsReview = 0 WHERE id = :id")
    suspend fun updateTransactionCategory(id: String, category: String)

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY date DESC LIMIT 10")
    fun getTransactionsNeedingReview(): Flow<List<TransactionEntity>>

    @Query("SELECT source, balance FROM transactions t1 WHERE balance IS NOT NULL AND date = (SELECT MAX(date) FROM transactions t2 WHERE t2.source = t1.source AND t2.balance IS NOT NULL)")
    fun getLatestBalancesBySource(): Flow<List<SourceBalance>>

    @Query("SELECT * FROM transactions WHERE partyName = :party OR description LIKE '%' || :party || '%' ORDER BY date DESC")
    fun getTransactionsForParty(party: String): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(fee) FROM transactions WHERE fee > 0")
    fun getTotalAccruedFees(): Flow<Double?>

    @Query("SELECT description, SUM(fee) as totalFee, COUNT(*) as count FROM transactions WHERE fee > 0 GROUP BY description ORDER BY totalFee DESC")
    fun getFeesByMerchant(): Flow<List<MerchantFeeSummary>>
}

data class MerchantFeeSummary(
    val description: String,
    val totalFee: Double,
    val count: Int
)
