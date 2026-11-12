package com.zenith.domain.parser

import com.zenith.domain.model.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object SmsParser {
    private val dateFormatter = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US)

    private fun generateId(reference: String?): String {
        if (!reference.isNullOrBlank()) return reference
        return "${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    private fun parseAmount(raw: String): Double {
        return raw.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private fun parseDate(dateStr: String, timeStr: String): Date {
        return try {
            dateFormatter.parse("$dateStr $timeStr") ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    fun parseSingleSms(text: String): Transaction? {
        val upper = text.uppercase()

        return when {
            upper.contains("M-PESA") || upper.contains("MPESA") || Pattern.compile("[A-Z]{2,4}\\d{6,10}").matcher(text).find() -> {
                parseMpesa(text)
            }
            upper.contains("EQUITY") || upper.contains("EQUITEL") -> {
                parseEquity(text)
            }
            else -> null
        }
    }

    private fun parseMpesa(text: String): Transaction? {
        // Extract reference code (usually at the start or end, 10 characters uppercase alphanumeric)
        val confirmRef = Regex("^([A-Z0-9]{10,12})\\s+Confirmed", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1) 
            ?: Regex("Ref:\\s*([A-Z0-9]{10,12})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

        val dateMatch = Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)", RegexOption.IGNORE_CASE)
            .find(text)
        val date = if (dateMatch != null) parseDate(dateMatch.groupValues[1], dateMatch.groupValues[2]) else Date()

        val balanceMatch = Regex("balance[^\\d]*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }

        // 1. Received
        val receivedMatch = Regex("received\\s+Ksh\\s*([\\d,]+\\.?\\d*)\\s+from\\s+(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)
        if (receivedMatch != null) {
            val amount = parseAmount(receivedMatch.groupValues[1])
            val description = "Received from ${receivedMatch.groupValues[2].trim()}"
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.CREDIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.CREDIT),
                source = TransactionSource.MPESA,
                rawText = text,
                balance = balance,
                reference = confirmRef
            )
        }

        // 2. Sent
        val sentMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+sent\\s+to\\s+(.+?)\\s+for\\s+account", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+sent\\s+to\\s+(.+?)\\s+\\d{10}", RegexOption.IGNORE_CASE).find(text)
        
        if (sentMatch != null) {
            val amount = parseAmount(sentMatch.groupValues[1])
            val description = "Sent to ${sentMatch.groupValues[2].trim()}"
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.MPESA,
                rawText = text,
                balance = balance,
                reference = confirmRef
            )
        }

        // 3. Paid (Till/Paybill)
        val paidMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+paid\\s+to\\s+(.+?)\\.", RegexOption.IGNORE_CASE).find(text)
        if (paidMatch != null) {
            val amount = parseAmount(paidMatch.groupValues[1])
            val description = "Paid to ${paidMatch.groupValues[2].trim()}"
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.MPESA,
                rawText = text,
                balance = balance,
                reference = confirmRef
            )
        }

        // 4. Withdrawal
        val withdrawalMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+withdrawn\\s+from\\s+(?:agent\\s+)?(.+?)\\s+\\d{10}", RegexOption.IGNORE_CASE).find(text)
        if (withdrawalMatch != null) {
            val amount = parseAmount(withdrawalMatch.groupValues[1])
            val description = "Withdrawal from agent ${withdrawalMatch.groupValues[2].trim()}"
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = Category.CASH_WITHDRAWAL,
                source = TransactionSource.MPESA,
                rawText = text,
                balance = balance,
                reference = confirmRef
            )
        }

        // 5. Airtime
        val airtimeMatch = Regex("You\\s+bought\\s+Ksh\\s*([\\d,]+\\.?\\d*)\\s+of\\s+airtime", RegexOption.IGNORE_CASE).find(text)
        if (airtimeMatch != null) {
            val amount = parseAmount(airtimeMatch.groupValues[1])
            val description = "Bought Airtime"
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = Category.UTILITIES,
                source = TransactionSource.MPESA,
                rawText = text,
                balance = balance,
                reference = confirmRef
            )
        }

        return null
    }

    private fun parseEquity(text: String): Transaction? {
        val debitMatch = Regex("(?:debited|debit)\\s+(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited|credit)\\s+(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available|balance)\\s+(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref:\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
        
        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        
        if (debitMatch != null) {
            val amount = parseAmount(debitMatch.groupValues[1])
            val description = "Equity Bank Debit"
            return Transaction(
                id = generateId(reference),
                date = Date(),
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.EQUITY,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        if (creditMatch != null) {
            val amount = parseAmount(creditMatch.groupValues[1])
            val description = "Equity Bank Credit"
            return Transaction(
                id = generateId(reference),
                date = Date(),
                amount = amount,
                type = TransactionType.CREDIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.CREDIT),
                source = TransactionSource.EQUITY,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        return null
    }
}
