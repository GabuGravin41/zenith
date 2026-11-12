package com.zenith.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.zenith.data.local.AppDatabase
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room.Room

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.displayMessageBody
                val address = message.displayOriginatingAddress
                
                Log.d("SmsReceiver", "Received SMS from $address: $body")
                
                // Parse the SMS with sender address
                val transaction = SmsParser.parseSingleSms(body, address)
                if (transaction != null) {
                    saveTransaction(context, transaction)
                }
            }
        }
    }

    private fun saveTransaction(context: Context, transaction: com.zenith.domain.model.Transaction) {
        val db = AppDatabase.getDatabase(context)

        val entity = TransactionEntity(
            id = transaction.id,
            date = transaction.date.time,
            amount = transaction.amount,
            type = transaction.type.name,
            description = transaction.description,
            category = transaction.category.name,
            source = transaction.source.name,
            rawText = transaction.rawText,
            balance = transaction.balance,
            reference = transaction.reference,
            fee = transaction.fee,
            partyName = transaction.partyName
        )

        CoroutineScope(Dispatchers.IO).launch {
            db.transactionDao().insertTransaction(entity)
            Log.d("SmsReceiver", "Saved transaction: ${transaction.description}")
        }
    }
}
