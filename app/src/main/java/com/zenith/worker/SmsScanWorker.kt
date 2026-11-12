package com.zenith.worker

import android.content.Context
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zenith.data.local.AppDatabase
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.parser.SmsParser
import com.zenith.util.NotificationHelper

class SmsScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
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

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            
            // We only scan the last 50 messages to keep it efficient
            var count = 0
            var newTransactionsCount = 0
            while (it.moveToNext() && count < 50) {
                val body = it.getString(bodyIndex)
                val transaction = SmsParser.parseSingleSms(body)
                
                if (transaction != null) {
                    val rowId = dao.insertTransaction(
                        TransactionEntity(
                            id = transaction.id,
                            date = transaction.date.time,
                            amount = transaction.amount,
                            type = transaction.type.name,
                            description = transaction.description,
                            category = db.providerCategoryDao().getCategoryForProvider(transaction.description) 
                                ?: transaction.category.name,
                            source = transaction.source.name,
                            rawText = transaction.rawText,
                            balance = transaction.balance,
                            reference = transaction.reference
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
                    "Scan Complete",
                    "Found $newTransactionsCount new transactions from your SMS history."
                )
            }
        }

        return Result.success()
    }
}
