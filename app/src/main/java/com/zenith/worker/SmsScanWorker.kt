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
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fullScan = inputData.getBoolean("full_scan", false)
        // If fullScan is true, scan all SMS (up to 20,000). Otherwise, scan recent 200.
        val maxMessages = if (fullScan) 20000 else 200

        val cursor = try {
            applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
                null,
                null,
                Telephony.Sms.DATE + " DESC"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.transactionDao()
        val providerDao = db.providerCategoryDao()

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            
            var count = 0
            var newTransactionsCount = 0

            while (it.moveToNext() && count < maxMessages) {
                val body = it.getString(bodyIndex) ?: ""
                val address = if (addressIndex >= 0) it.getString(addressIndex) else null
                
                val transaction = SmsParser.parseSingleSms(body, address)
                
                if (transaction != null) {
                    val savedCategory = providerDao.getCategoryForProvider(transaction.description)
                    val finalCategory = savedCategory ?: transaction.category.name

                    // Ambiguous transfer check: if categorized as TRANSFER and not explicitly saved
                    val needsReview = (finalCategory == Category.TRANSFER.name && 
                            savedCategory == null && 
                            !transaction.description.contains("bank", ignoreCase = true))

                    val rowId = dao.insertTransaction(
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
                    if (rowId != -1L) {
                        newTransactionsCount++
                    }
                }
                count++
            }

            if (newTransactionsCount > 0) {
                NotificationHelper.showNotification(
                    applicationContext,
                    "Sync Complete",
                    "Found $newTransactionsCount new transactions across your SMS history."
                )
            }
        }

        return Result.success()
    }
}
