package com.zenith.worker

import android.content.Context
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zenith.data.local.AppDatabase
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.model.Category
import com.zenith.domain.parser.SmsParser
import com.zenith.util.NotificationHelper

class SmsScanWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val isFullScan = inputData.getBoolean("full_scan", false)
        val maxMessages = if (isFullScan) 20000 else 200

        val cursor = try {
            applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.transactionDao()
        val providerDao = db.providerCategoryDao()

        // 1. Pre-load all learned provider categories into memory ONCE
        val savedCategories = try {
            providerDao.getAllCategoriesList().associate { it.providerName to it.category }
        } catch (e: Exception) {
            emptyMap()
        }

        val pendingEntities = mutableListOf<TransactionEntity>()

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            
            var count = 0

            while (it.moveToNext() && count < maxMessages) {
                val body = it.getString(bodyIndex) ?: ""
                val address = if (addressIndex >= 0) it.getString(addressIndex) else null
                val smsDate = if (dateIndex >= 0 && !it.isNull(dateIndex)) it.getLong(dateIndex) else null
                
                val transaction = SmsParser.parseSingleSms(body, address, smsDate)
                
                if (transaction != null) {
                    val savedCategory = savedCategories[transaction.description]
                    val finalCategory = savedCategory ?: transaction.category.name

                    // Ambiguous transfer check: if categorized as TRANSFER and not explicitly saved
                    val needsReview = (finalCategory == Category.TRANSFER.name && 
                            savedCategory == null && 
                            !transaction.description.contains("bank", ignoreCase = true))

                    pendingEntities.add(
                        TransactionEntity(
                            id = transaction.id,
                            date = transaction.date.time,
                            amount = transaction.amount,
                            type = transaction.type.name,
                            description = transaction.description,
                            category = finalCategory,
                            source = transaction.source.name,
                            rawText = transaction.rawText,
                            balance = transaction.balance,
                            reference = transaction.reference,
                            needsReview = needsReview,
                            fee = transaction.fee,
                            partyName = transaction.partyName
                        )
                    )
                }
                count++
            }
        }

        // 2. High-performance batch insert in 500-item chunks (takes ~0.2s vs minutes)
        var newTransactionsCount = 0
        if (pendingEntities.isNotEmpty()) {
            pendingEntities.chunked(500).forEach { chunk ->
                val insertedIds = dao.insertTransactions(chunk)
                newTransactionsCount += insertedIds.count { it != -1L }
            }
        }

        if (newTransactionsCount > 0) {
            NotificationHelper.showNotification(
                applicationContext,
                "Sync Complete",
                "Found $newTransactionsCount new transactions across your SMS history."
            )
        }

        return Result.success()
    }
}
