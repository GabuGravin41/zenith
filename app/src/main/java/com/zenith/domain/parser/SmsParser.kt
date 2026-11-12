package com.zenith.domain.parser

import com.zenith.domain.model.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object SmsParser {
    private val dateFormatter = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US)
    private val altDateFormatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    private val standardDateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val isoDateShortFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthNameFormatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthNameWithTime = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US)

    private fun generateId(reference: String?): String {
        if (!reference.isNullOrBlank()) return reference
        return "${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    private fun parseAmount(raw: String): Double {
        return raw.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private fun parseDate(dateStr: String, timeStr: String, fallbackTimestamp: Long? = null): Date {
        val cleanTime = timeStr.trim().replace("\\s+".toRegex(), " ")
        return try {
            val parsed = dateFormatter.parse("$dateStr $cleanTime")
                ?: altDateFormatter.parse("$dateStr $cleanTime")
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                val year = cal.get(Calendar.YEAR)
                if (year in 2015..2035) parsed
                else fallbackTimestamp?.let { Date(it) } ?: parsed
            } else {
                fallbackTimestamp?.let { Date(it) } ?: Date()
            }
        } catch (e: Exception) {
            fallbackTimestamp?.let { Date(it) } ?: Date()
        }
    }

    private fun extractFee(text: String): Double {
        val feeMatch = Regex("Transaction\\s+cost,?\\s*(?:Ksh|KES)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:Fee|Charge):?\\s*(?:Ksh|KES)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        return feeMatch?.groupValues?.get(1)?.let { parseAmount(it) } ?: 0.0
    }

    fun parseSingleSms(text: String, senderAddress: String? = null, smsTimestamp: Long? = null): Transaction? {
        val upperText = text.uppercase()
        val upperSender = (senderAddress ?: "").uppercase()

        return when {
            // M-Pesa patterns or MPESA sender
            upperSender.contains("MPESA") || upperText.contains("M-PESA") || upperText.contains("MPESA") ||
                    Pattern.compile("^[A-Z0-9]{10}\\s+CONFIRMED", Pattern.CASE_INSENSITIVE).matcher(text.trim()).find() -> {
                parseMpesa(text, smsTimestamp)
            }
            // Equity Bank
            upperSender.contains("EQUITY") || upperSender.contains("EQUITEL") || upperText.contains("EQUITY") || upperText.contains("EQUITEL") -> {
                parseEquity(text, smsTimestamp)
            }
            // KCB Bank / Vooma
            upperSender.contains("KCB") || upperSender.contains("VOOMA") || upperText.contains("KCB") || upperText.contains("VOOMA") -> {
                parseKcb(text, smsTimestamp)
            }
            // Co-operative Bank / MCo-op Cash
            upperSender.contains("CO-OP") || upperSender.contains("COOP") || upperText.contains("CO-OP BANK") || upperText.contains("MCO-OP") -> {
                parseCoop(text, smsTimestamp)
            }
            // NCBA / LOOP
            upperSender.contains("NCBA") || upperSender.contains("LOOP") || upperText.contains("NCBA") -> {
                parseNcba(text, smsTimestamp)
            }
            // ABSA
            upperSender.contains("ABSA") || upperText.contains("ABSA") -> {
                parseAbsa(text, smsTimestamp)
            }
            // Stanbic
            upperSender.contains("STANBIC") || upperText.contains("STANBIC") -> {
                parseStanbic(text, smsTimestamp)
            }
            // Generic Bank / Financial SMS pattern fallback
            upperText.contains("DEBIT") || upperText.contains("CREDIT") || upperText.contains("TRANSFERRED") -> {
                parseGenericFinancialSms(text, senderAddress, smsTimestamp)
            }
            else -> null
        }
    }

    private fun parseMpesa(text: String, smsTimestamp: Long? = null): Transaction? {
        val confirmRef = Regex("^([A-Z0-9]{10,12})\\s+Confirmed", RegexOption.IGNORE_CASE)
            .find(text.trim())?.groupValues?.get(1)
            ?: Regex("Ref:\\s*([A-Z0-9]{10,12})", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)

        val dateMatch = Regex("on\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+at\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)", RegexOption.IGNORE_CASE)
            .find(text)
        val date = if (dateMatch != null) {
            parseDate(dateMatch.groupValues[1], dateMatch.groupValues[2], smsTimestamp)
        } else {
            smsTimestamp?.let { Date(it) } ?: Date()
        }

        val balanceMatch = Regex("balance[^\\d]*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val fee = extractFee(text)

        // 1. Received
        val receivedMatch = Regex("received\\s+Ksh\\s*([\\d,]+\\.?\\d*)\\s+from\\s+(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)
        if (receivedMatch != null) {
            val amount = parseAmount(receivedMatch.groupValues[1])
            val rawParty = receivedMatch.groupValues[2].trim()
            val description = "Received from $rawParty"
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
                reference = confirmRef,
                fee = fee,
                partyName = rawParty
            )
        }

        // 2. Sent
        val sentMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+sent\\s+to\\s+(.+?)\\s+for\\s+account", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+sent\\s+to\\s+(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)

        if (sentMatch != null) {
            val amount = parseAmount(sentMatch.groupValues[1])
            val rawParty = sentMatch.groupValues[2].trim()
            val description = "Sent to $rawParty"
            val initialCategory = FinanceCategories.categorize(description, TransactionType.DEBIT)
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
                reference = confirmRef,
                fee = fee,
                partyName = rawParty
            )
        }

        // 3. Paid (Till/Paybill)
        val paidMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+paid\\s+to\\s+(.+?)\\.", RegexOption.IGNORE_CASE).find(text)
        if (paidMatch != null) {
            val amount = parseAmount(paidMatch.groupValues[1])
            val rawParty = paidMatch.groupValues[2].trim()
            val description = "Paid to $rawParty"
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
                reference = confirmRef,
                fee = fee,
                partyName = rawParty
            )
        }

        // 4. Withdrawal
        val withdrawalMatch = Regex("Ksh\\s*([\\d,]+\\.?\\d*)\\s+withdrawn\\s+from\\s+(?:agent\\s+)?(.+?)(?:\\s+\\d{10}|\\s+on)", RegexOption.IGNORE_CASE).find(text)
        if (withdrawalMatch != null) {
            val amount = parseAmount(withdrawalMatch.groupValues[1])
            val rawParty = withdrawalMatch.groupValues[2].trim()
            val description = "Withdrawal from $rawParty"
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
                reference = confirmRef,
                fee = fee,
                partyName = rawParty
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
                reference = confirmRef,
                fee = fee,
                partyName = "Safaricom"
            )
        }

        return null
    }

    private fun parseEquity(text: String, smsTimestamp: Long? = null): Transaction? {
        val debitMatch = Regex("(?:debited\\s+(?:with|by)?|debit\\s+of)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+debited", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+(?:with|by)?|credit\\s+of)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+credited", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+balance|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text, smsTimestamp)
        val fee = extractFee(text)

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
                reference = reference,
                fee = fee,
                partyName = recipient
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
                reference = reference,
                fee = fee,
                partyName = sender
            )
        }

        return null
    }

    private fun parseKcb(text: String, smsTimestamp: Long? = null): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit\\s+of|withdrawn|transferred)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+(?:debited|spent)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit\\s+of|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+credited", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+bal(?:ance)?|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("(?:Ref(?:erence)?|Txn\\s*Id):?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text, smsTimestamp)
        val fee = extractFee(text)

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
                reference = reference,
                fee = fee,
                partyName = party
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
                reference = reference,
                fee = fee,
                partyName = party
            )
        }

        return null
    }

    private fun parseCoop(text: String, smsTimestamp: Long? = null): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit\\s+of|withdrawn|paid)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)\\s+(?:debited|paid)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit\\s+of|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:available\\s+balance|bal(?:ance)?)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text, smsTimestamp)
        val fee = extractFee(text)

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
                reference = reference,
                fee = fee,
                partyName = party
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
                reference = reference,
                fee = fee,
                partyName = null
            )
        }

        return null
    }

    private fun parseNcba(text: String, smsTimestamp: Long? = null): Transaction? {
        val debitMatch = Regex("(?:debited\\s+with|debit|sent|paid)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val creditMatch = Regex("(?:credited\\s+with|credit|received)\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val balanceMatch = Regex("(?:balance|bal)\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)

        val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
        val reference = refMatch?.groupValues?.get(1)
        val date = extractGenericDate(text, smsTimestamp)
        val fee = extractFee(text)

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
                reference = reference,
                fee = fee,
                partyName = null
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
                reference = reference,
                fee = fee,
                partyName = null
            )
        }

        return null
    }

    private fun parseAbsa(text: String, smsTimestamp: Long? = null): Transaction? {
        val amountMatch = Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val isDebit = text.contains("debited", ignoreCase = true) || text.contains("purchase", ignoreCase = true) || text.contains("paid", ignoreCase = true)
        val isCredit = text.contains("credited", ignoreCase = true) || text.contains("received", ignoreCase = true)
        val balanceMatch = Regex("bal(?:ance)?\\s*(?:is)?\\s*(?:KES|Ksh)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val refMatch = Regex("Ref(?:erence)?:?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
        val fee = extractFee(text)

        if (amountMatch != null && (isDebit || isCredit)) {
            val amount = parseAmount(amountMatch.groupValues[1])
            val balance = balanceMatch?.groupValues?.get(1)?.let { parseAmount(it) }
            val reference = refMatch?.groupValues?.get(1)
            val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
            val description = if (isDebit) "Absa: Debit" else "Absa: Credit"
            return Transaction(
                id = generateId(reference),
                date = extractGenericDate(text, smsTimestamp),
                amount = amount,
                type = type,
                description = description,
                category = FinanceCategories.categorize(description, type),
                source = TransactionSource.ABSA,
                rawText = text,
                balance = balance,
                reference = reference,
                fee = fee,
                partyName = null
            )
        }
        return null
    }

    private fun parseStanbic(text: String, smsTimestamp: Long? = null): Transaction? {
        val amountMatch = Regex("(?:KES|Ksh)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(text)
        val isDebit = text.contains("debit", ignoreCase = true) || text.contains("paid", ignoreCase = true)
        val isCredit = text.contains("credit", ignoreCase = true) || text.contains("received", ignoreCase = true)
        val fee = extractFee(text)

        if (amountMatch != null && (isDebit || isCredit)) {
            val amount = parseAmount(amountMatch.groupValues[1])
            val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
            val description = if (isDebit) "Stanbic: Payment" else "Stanbic: Deposit"
            return Transaction(
                id = generateId(null),
                date = extractGenericDate(text, smsTimestamp),
                amount = amount,
                type = type,
                description = description,
                category = FinanceCategories.categorize(description, type),
                source = TransactionSource.STANBIC,
                rawText = text,
                balance = null,
                reference = null,
                fee = fee,
                partyName = null
            )
        }
        return null
    }

    private fun parseGenericFinancialSms(text: String, sender: String?, smsTimestamp: Long? = null): Transaction? {
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
        val fee = extractFee(text)

        return Transaction(
            id = generateId(null),
            date = extractGenericDate(text, smsTimestamp),
            amount = amount,
            type = type,
            description = description,
            category = FinanceCategories.categorize(description, type),
            source = TransactionSource.OTHER,
            rawText = text,
            balance = balance,
            reference = null,
            fee = fee,
            partyName = null
        )
    }

    private fun extractGenericDate(text: String, fallbackTimestamp: Long? = null): Date {
        // 1. dd/MM/yy or dd/MM/yyyy or dd-MM-yyyy
        val dmMatch = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[AP]M)?", RegexOption.IGNORE_CASE).find(text)
        if (dmMatch != null) {
            val dStr = dmMatch.groupValues[1].replace("-", "/")
            val tStr = dmMatch.groupValues[2].ifEmpty { "12:00 PM" }
            val parsed = try {
                dateFormatter.parse("$dStr $tStr") ?: altDateFormatter.parse("$dStr $tStr")
            } catch (e: Exception) { null }
            if (parsed != null) return parsed
        }

        // 2. yyyy-MM-dd or yyyy/MM/dd (ISO)
        val isoMatch = Regex("(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})(?:\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?))?", RegexOption.IGNORE_CASE).find(text)
        if (isoMatch != null) {
            val dStr = isoMatch.groupValues[1].replace("/", "-")
            val tStr = isoMatch.groupValues[2].ifEmpty { "12:00:00" }
            val parsed = try {
                standardDateFormatter.parse("$dStr $tStr") ?: isoDateShortFormatter.parse(dStr)
            } catch (e: Exception) { null }
            if (parsed != null) return parsed
        }

        // 3. 12-May-2025, 12 May 2025, 12-May-25
        val wordMonthMatch = Regex("(\\d{1,2})[- ]([A-Za-z]{3,9})[- ](\\d{2,4})\\s*(?:at)?\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)?", RegexOption.IGNORE_CASE).find(text)
        if (wordMonthMatch != null) {
            val dStr = "${wordMonthMatch.groupValues[1]} ${wordMonthMatch.groupValues[2].take(3)} ${wordMonthMatch.groupValues[3]}"
            val tStr = wordMonthMatch.groupValues[4].ifEmpty { "12:00 PM" }
            val parsed = try {
                monthNameWithTime.parse("$dStr $tStr") ?: monthNameFormatter.parse(dStr)
            } catch (e: Exception) { null }
            if (parsed != null) return parsed
        }

        // 4. Default to SMS network arrival timestamp (carrier ground-truth)
        return fallbackTimestamp?.let { Date(it) } ?: Date()
    }
}
