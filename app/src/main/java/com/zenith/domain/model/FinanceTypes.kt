package com.zenith.domain.model

import java.util.Date

enum class TransactionType {
    CREDIT, DEBIT
}

enum class TransactionSource {
    MPESA, EQUITY, OTHER, MANUAL
}

enum class Category(val label: String, val icon: String, val color: String) {
    FOOD_DINING("Food & Dining", "coffee", "#F59E0B"),
    TRANSPORT("Transport", "navigation", "#3B82F6"),
    UTILITIES("Utilities", "zap", "#8B5CF6"),
    SHOPPING("Shopping", "shopping-bag", "#EC4899"),
    HEALTH("Health", "heart", "#EF4444"),
    ENTERTAINMENT("Entertainment", "music", "#F97316"),
    SAVINGS("Savings", "trending-up", "#10B981"),
    TRANSFER("Transfer", "repeat", "#06B6D4"),
    EDUCATION("Education", "book", "#6366F1"),
    RENT("Rent", "home", "#0F172A"),
    CASH_WITHDRAWAL("Cash Withdrawal", "dollar-sign", "#64748B"),
    OTHER("Other", "more-horizontal", "#94A3B8")
}

data class Transaction(
    val id: String,
    val date: Date,
    val amount: Double,
    val type: TransactionType,
    val description: String,
    val category: Category,
    val source: TransactionSource,
    val rawText: String,
    val balance: Double? = null,
    val reference: String? = null
)
