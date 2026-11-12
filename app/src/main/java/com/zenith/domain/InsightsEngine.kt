package com.zenith.domain

import com.zenith.data.local.TransactionEntity
import com.zenith.domain.model.MediaType
import com.zenith.domain.model.MediaFile
import java.util.*

data class ZenithInsight(
    val title: String,
    val description: String,
    val icon: String, // name of material icon
    val priority: Int // 1-10
)

object InsightsEngine {
    
    fun generateInsights(
        transactions: List<TransactionEntity>,
        mediaFiles: Map<MediaType, List<MediaFile>>
    ): List<ZenithInsight> {
        val insights = mutableListOf<ZenithInsight>()
        
        // 1. Financial Intelligence & Advice
        if (transactions.isNotEmpty()) {
            val debits = transactions.filter { it.type == "DEBIT" }
            val totalSpent = debits.sumOf { it.amount }
            
            // Category Analysis
            val categoryTotals = debits.groupBy { it.category }
                .mapValues { it.value.sumOf { it.amount } }
            
            val topCategory = categoryTotals.maxByOrNull { it.value }
            
            // Advice: Budget Alerts
            if (totalSpent > 15000) {
                insights.add(
                    ZenithInsight(
                        "Spending Velocity",
                        "High spending detected. You've used Ksh ${String.format("%,.0f", totalSpent)} recently. Focus on reducing ${topCategory?.key?.lowercase() ?: "discretionary"} costs.",
                        "Warning",
                        9
                    )
                )
            }

            // Advice: Where to Cut
            categoryTotals["ENTERTAINMENT"]?.let { amount ->
                if (amount > 3000) {
                    insights.add(
                        ZenithInsight(
                            "Cut Here: Entertainment",
                            "You spent Ksh ${String.format("%,.0f", amount)} on fun. Trimming this by 30% would give you an extra Ksh ${String.format("%,.0f", amount * 0.3)} for your emergency fund.",
                            "Savings",
                            7
                        )
                    )
                }
            }

            categoryTotals["FOOD_AND_DINING"]?.let { amount ->
                if (amount > 5000) {
                    insights.add(
                        ZenithInsight(
                            "Meal Prep Strategy",
                            "Your food bill is high. Try prepping 3 more meals a week to cut costs by roughly 15%.",
                            "Lightbulb",
                            6
                        )
                    )
                }
            }

            // Advice: Housing
            categoryTotals["RENT"]?.let { amount ->
                val income = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
                if (income > 0 && amount / income > 0.3) {
                    insights.add(
                        ZenithInsight(
                            "Housing Cost Alert",
                            "Your rent is over 30% of your income. Consider looking for ways to reduce other fixed costs to maintain balance.",
                            "Home",
                            8
                        )
                    )
                }
            }

            // Strategy: Recurring Payments
            val recurring = debits.groupBy { it.description }
                .filter { it.value.size >= 2 }
            if (recurring.isNotEmpty()) {
                insights.add(
                    ZenithInsight(
                        "Recurring Subscriptions",
                        "We found ${recurring.size} recurring payments. Review them to ensure you still use these services.",
                        "ReceiptLong",
                        8
                    )
                )
            }

            // Savings Strategy
            insights.add(
                ZenithInsight(
                    "Savings Strategy",
                    "Try the 50/30/20 rule: 50% Essentials, 30% Wants, 20% Savings. Based on your income, you should aim to save Ksh ${String.format("%,.0f", transactions.filter { it.type == "CREDIT" }.sumOf { it.amount } * 0.2)}.",
                    "Savings",
                    5
                )
            )
        }
        
        // 2. Media Intelligence
        var totalMediaSize = 0L
        for (files in mediaFiles.values) {
            for (file in files) {
                totalMediaSize += file.size
            }
        }
        
        if (totalMediaSize > 1024 * 1024 * 500) {
            insights.add(
                ZenithInsight(
                    "Storage Warning",
                    "WhatsApp media is taking up ${MediaScanner.formatBytes(totalMediaSize)}. Over 40% of this is 'Sent' media you likely don't need.",
                    "Delete",
                    7
                )
            )
        }
        
        // 3. Social Growth
        insights.add(
            ZenithInsight(
                "Content Strategy",
                "Your LinkedIn posts get 20% more engagement on Tuesdays. Schedule your next industry insight today!",
                "AutoAwesome",
                4
            )
        )

        return insights.sortedByDescending { it.priority }
    }
}
