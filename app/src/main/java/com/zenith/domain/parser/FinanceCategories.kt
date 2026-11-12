package com.zenith.domain.parser

import com.zenith.domain.model.Category
import com.zenith.domain.model.TransactionType

object FinanceCategories {
    private val KEYWORDS = mapOf(
        Category.FOOD_DINING to listOf(
            "java", "kfc", "pizza", "food", "restaurant", "cafe", "hotel",
            "quickmart", "naivas", "carrefour", "eat", "lunch", "dinner",
            "breakfast", "chicken", "burger", "kitchen", "bakery", "supermarket",
            "grocery", "cleanshelf", "market", "jumia food", "glovo"
        ),
        Category.TRANSPORT to listOf(
            "uber", "bolt", "little", "matatu", "stage", "bus", "fuel",
            "petrol", "fare", "parking", "ntsa", "safeboda", "taxi",
            "vehicle", "transport", "travel", "ride"
        ),
        Category.UTILITIES to listOf(
            "kplc", "kenya power", "water", "safaricom", "airtel", "faiba",
            "telkom", "data", "airtime", "electricity", "wifi", "internet",
            "stima", "nairobi water", "wwda"
        ),
        Category.SHOPPING to listOf(
            "game", "jumia", "kilimall", "amazon", "clothes", "clothing",
            "fashion", "shoes", "shirt", "trouser", "dress", "mall",
            "nakumatt", "tuskys", "shop", "store", "buy", "purchase"
        ),
        Category.HEALTH to listOf(
            "pharmacy", "dawa", "hospital", "clinic", "doctor", "medicine",
            "chemist", "health", "medical", "nhif", "insurance", "dental",
            "lab", "test", "prescription"
        ),
        Category.ENTERTAINMENT to listOf(
            "movie", "cinema", "netflix", "spotify", "dstv", "showmax",
            "club", "bar", "pub", "lounge", "concert", "event", "ticket",
            "game", "sport", "gym", "fitness"
        ),
        Category.SAVINGS to listOf(
            "save", "savings", "sacco", "investment", "mshwari", "kcbmpesa",
            "kcb mpesa", "mali", "pesa link", "deposit", "fixed", "money market"
        ),
        Category.TRANSFER to listOf(
            "sent to", "paid to", "received from", "transfer", "bank",
            "equity", "kcb", "coop", "absa", "standard chartered",
            "family bank", "ncba", "i&m"
        ),
        Category.EDUCATION to listOf(
            "school", "university", "college", "tuition", "fees", "exam",
            "knec", "kcse", "library", "education", "study", "course"
        ),
        Category.CASH_WITHDRAWAL to listOf(
            "withdrawal", "atm", "agent", "withdraw", "cash out"
        )
    )

    fun categorize(description: String, type: TransactionType): Category {
        val lower = description.lowercase()
        
        if (type == TransactionType.CREDIT) {
            if (KEYWORDS[Category.SAVINGS]?.any { lower.contains(it) } == true) return Category.SAVINGS
            return Category.TRANSFER
        }

        val order = listOf(
            Category.UTILITIES,
            Category.HEALTH,
            Category.EDUCATION,
            Category.SAVINGS,
            Category.FOOD_DINING,
            Category.TRANSPORT,
            Category.ENTERTAINMENT,
            Category.SHOPPING,
            Category.CASH_WITHDRAWAL,
            Category.TRANSFER
        )

        for (cat in order) {
            if (KEYWORDS[cat]?.any { lower.contains(it) } == true) {
                return cat
            }
        }

        return Category.OTHER
    }
}
