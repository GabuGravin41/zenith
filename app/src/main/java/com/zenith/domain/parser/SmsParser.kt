package com.zenith.domain.parser

import com.zenith.domain.model.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object SmsParser {
    private val dateFormatter = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US)
    private val altDateFormatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    private val standardDateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun generateId(reference: String?): String {
        if (!reference.isNullOrBlank()) return reference
        return "${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    private fun parseAmount(raw: String): Double {
        return raw.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private fun parseDate(dateStr: String, timeStr: String): Date {
        val cleanTime = timeStr.trim().replace("\\s+".toRegex(), " ")
        return try {
            dateFormatter.parse("$dateStr $cleanTime")
                ?: altDateFormatter.parse("$dateStr $cleanTime")
                ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    fun parseSingleSms(text: String, senderAddress: String? = null): Transaction? {
        val upperText = text.uppercase()
        val upperSender = (senderAddress ?: "").uppercase()

        return when {
            // M-Pesa patterns or MPESA sender
            upperSender.contains("MPESA") || upperText.contains("M-PESA") || upperText.contains("MPESA") ||
                    Pattern.compile("^[A-Z0-9]{10}\\s+CONFIRMED", Pattern.CASE_INSENSITIVE).matcher(text.trim()).find() -> {
                parseMpesa(text)
            }
            // Equity Bank
            upperSender.contains("EQUITY") || upperSender.contains("EQUITEL") || upperText.contains("EQUITY") || upperText.contains("EQUITEL") -> {
                parseEquity(text)
            }
            // KCB Bank / Vooma
            upperSender.contains("KCB") || upperSender.contains("VOOMA") || upperText.contains("KCB") || upperText.contains("VOOMA") -> {
                parseKcb(text)
            }
            // Co-operative Bank / MCo-op Cash
            upperSender.contains("CO-OP") || upperSender.contains("COOP") || upperText.contains("CO-OP BANK") || upperText.contains("MCO-OP") -> {
                parseCoop(text)
            }
            // NCBA / LOOP
            upperSender.contains("NCBA") || upperSender.contains("LOOP") || upperText.contains("NCBA") -> {
                parseNcba(text)
            }
            // ABSA
            upperSender.contains("ABSA") || upperText.contains("ABSA") -> {
                parseAbsa(text)
            }
            // Stanbic
            upperSender.contains("STANBIC") || upperText.contains("STANBIC") -> {
                parseStanbic(text)
            }
            // Generic Bank / Financial SMS pattern fallback
            upperText.contains("DEBIT") || upperText.contains("CREDIT") || upperText.contains("TRANSFERRED") -> {
                parseGenericFinancialSms(text, senderAddress)
            }
            else -> null
        }
    }

    private fun parseMpesa(text: String): Transaction? {
        val confirmRef = Regex("^([A-Z0-9]{10,12})\\s+Confirmed", RegexOption.IGNORE_CASE)
            .find(text.trim())?.groupValues?.get(1)
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
            ?: Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+sent\\s+to\\s+(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)

        if (sentMatch != null) {
            val amount = parseAmount(sentMatch.groupValues[1])
            val rawRecipient = sentMatch.groupValues[2].trim()
            val description = "Sent to $rawRecipient"
            val initialCategory = FinanceCategories.categorize(description, TransactionType.DEBIT)
            // Flag ambiguous person-to-person transfers for user review
            val isAmbiguous = initialCategory == Category.TRANSFER && !description.contains("bank", ignoreCase = true)
            return Transaction(
                id = generateId(confirmRef),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = initialCategory,
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
        val withdrawalMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+withdrawn\\s+from\\s+(?:agent\\s+)?(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)
        if (withdrawalMatch != null) {
            val amount = parseAmount(withdrawalMatch.groupValues[1])
            val description = "Withdrawal from ${withdrawalMatch.groupValues[2].trim()}"
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
        val airtimeMatch = Regex("bought\\s+Ksh\\s*([\\d,]+\\.?\\d*)\\s+of\\s+airtime", RegexOption.IGNORE_CASE).find(text)
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
        val debitMatch = Regex("(?:debited\\s+(?:with|by)?|debit\\s+of)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+debited", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+(?:with|by)?|credit\\s+of)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+credited", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+balance|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)

        val date = extractGenericDate(text) ?: Date()

        if (debitMatch != null) {
            val amount = parseAmount(debitMatch.groupValues[1])
            val recipientMatch = Regex("(?:to|at)\\s+([A-Za-z0-9\\s,.-]+?)(?:\\s+on|\\.|\\s+Bal)", RegexOption.IGNORE_CASE).find(text)
            val recipient = recipientMatch?.groupValues?.get(1)?.trim() ?: "Equity Payment"
            val description = "Equity: $recipient"
            return Transaction(
                id = generateId(reference),
                date = date,
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
            val senderMatch = Regex("(?:from|by)\\s+([A-Za-z0-9\\s,.-]+?)(?:\\s+on|\\.|\\s+Bal)", RegexOption.IGNORE_CASE).find(text)
            val sender = senderMatch?.groupValues?.get(1)?.trim() ?: "Deposit"
            val description = "Equity: Received from $sender"
            return Transaction(
                id = generateId(reference),
                date = date,
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

    private fun parseKcb(text: String): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit\\s+of|withdrawn|transferred)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+(?:debited|spent)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit\\s+of|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+credited", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+bal(?:ance)?|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("(?:Ref(?:erence)?|Txn\\s*Id):?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text) ?: Date()

        if (debitMatch != null) {
            val amount = parseAmount(debitMatch.groupValues[1])
            val partyMatch = Regex("(?:to|at)\\s+([A-Za-z0-9\\s,.-]+?)(?:\\s+on|\\.|\\s+Bal)", RegexOption.IGNORE_CASE).find(text)
            val party = partyMatch?.groupValues?.get(1)?.trim() ?: "KCB Outgoing"
            val description = "KCB: $party"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.KCB,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        if (creditMatch != null) {
            val amount = parseAmount(creditMatch.groupValues[1])
            val partyMatch = Regex("(?:from|by)\\s+([A-Za-z0-9\\s,.-]+?)(?:\\s+on|\\.|\\s+Bal)", RegexOption.IGNORE_CASE).find(text)
            val party = partyMatch?.groupValues?.get(1)?.trim() ?: "Deposit"
            val description = "KCB: Received from $party"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.CREDIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.CREDIT),
                source = TransactionSource.KCB,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        return null
    }

    private fun parseCoop(text: String): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit\\s+of|withdrawn|paid)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+(?:debited|paid)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit\\s+of|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+balance|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text) ?: Date()

        if (debitMatch != null) {
            val amount = parseAmount(debitMatch.groupValues[1])
            val partyMatch = Regex("(?:to|at)\\s+([A-Za-z0-9\\s,.-]+?)(?:\\s+on|\\.|\\s+Bal)", RegexOption.IGNORE_CASE).find(text)
            val party = partyMatch?.groupValues?.get(1)?.trim() ?: "Co-op Payment"
            val description = "Co-op: $party"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.COOP,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        if (creditMatch != null) {
            val amount = parseAmount(creditMatch.groupValues[1])
            val description = "Co-op: Received funds"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.CREDIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.CREDIT),
                source = TransactionSource.COOP,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        return null
    }

    private fun parseNcba(text: String): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit|sent|paid)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:balance|bal)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text) ?: Date()

        if (debitMatch != null) {
            val amount = parseAmount(debitMatch.groupValues[1])
            val description = "NCBA: Outgoing Transaction"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.DEBIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.DEBIT),
                source = TransactionSource.NCBA,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        if (creditMatch != null) {
            val amount = parseAmount(creditMatch.groupValues[1])
            val description = "NCBA: Received funds"
            return Transaction(
                id = generateId(reference),
                date = date,
                amount = amount,
                type = TransactionType.CREDIT,
                description = description,
                category = FinanceCategories.categorize(description, TransactionType.CREDIT),
                source = TransactionSource.NCBA,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }

        return null
    }

    private fun parseAbsa(text: String): Transaction? {
        val amountMatch = Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val isDebit = text.contains("debited", ignoreCase = true) || text.contains("purchase", ignoreCase = true) || text.contains("paid", ignoreCase = true)
        val isCredit = text.contains("credited", ignoreCase = true) || text.contains("received", ignoreCase = true)
        val balanceMatch = Regex("bal(?:ance)?\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        if (amountMatch != null && (isDebit || isCredit)) {
            val amount = parseAmount(amountMatch.groupValues[1])
            val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
            val reference = refMatch?.groupValues?.get(1)
            val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
            val description = if (isDebit) "Absa: Debit" else "Absa: Credit"
            return Transaction(
                id = generateId(reference),
                date = extractGenericDate(text) ?: Date(),
                amount = amount,
                type = type,
                description = description,
                category = FinanceCategories.categorize(description, type),
                source = TransactionSource.ABSA,
                rawText = text,
                balance = balance,
                reference = reference
            )
        }
        return null
    }

    private fun parseStanbic(text: String): Transaction? {
        val amountMatch = Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val isDebit = text.contains("debit", ignoreCase = true) || text.contains("paid", ignoreCase = true)
        val isCredit = text.contains("credit", ignoreCase = true) || text.contains("received", ignoreCase = true)
        if (amountMatch != null && (isDebit || isCredit)) {
            val amount = parseAmount(amountMatch.groupValues[1])
            val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
            val description = if (isDebit) "Stanbic: Payment" else "Stanbic: Deposit"
            return Transaction(
                id = generateId(null),
                date = extractGenericDate(text) ?: Date(),
                amount = amount,
                type = type,
                description = description,
                category = FinanceCategories.categorize(description, type),
                source = TransactionSource.STANBIC,
                rawText = text,
                balance = null,
                reference = null
            )
        }
        return null
    }

    private fun parseGenericFinancialSms(text: String, sender: String?): Transaction? {
        val amountMatch = Regex("(?:KES|Ksh|USD|EUR)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val amount = parseAmount(amountMatch.groupValues[1])
        if (amount <= 0.0) return null

        val isDebit = text.contains("debit", ignoreCase = true) || text.contains("paid", ignoreCase = true) || text.contains("sent", ignoreCase = true) || text.contains("withdrawn", ignoreCase = true)
        val isCredit = text.contains("credit", ignoreCase = true) || text.contains("received", ignoreCase = true) || text.contains("deposited", ignoreCase = true)

        if (!isDebit && !isCredit) return null

        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val sourceLabel = sender?.uppercase()?.take(10) ?: "OTHER"
        val description = "$sourceLabel: Transaction"

        val balanceMatch = Regex("(?:available|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }

        return Transaction(
            id = generateId(null),
            date = extractGenericDate(text) ?: Date(),
            amount = amount,
            type = type,
            description = description,
            category = FinanceCategories.categorize(description, type),
            source = TransactionSource.OTHER,
            rawText = text,
            balance = balance,
            reference = null
        )
    }

    private fun extractGenericDate(text: String): Date? {
        val dateMatch = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]M)?", RegexOption.IGNORE_CASE).find(text)
        if (dateMatch != null) {
            val dStr = dateMatch.groupValues[1].replace("-", "/")
            val tStr = dateMatch.groupValues[2].ifEmpty { "12:00 PM" }
            return try {
                dateFormatter.parse("$dStr $tStr") ?: altDateFormatter.parse("$dStr $tStr")
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
