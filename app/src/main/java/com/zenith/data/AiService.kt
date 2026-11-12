package com.zenith.data

import com.zenith.data.local.TransactionEntity
import com.zenith.domain.ZenithInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiService {

    // Primary: DeepSeek V3 — fast, very cheap on OpenRouter
    private const val PRIMARY_MODEL = "deepseek/deepseek-chat"

    // Fallback: Gemini 2.5 Flash — Google's efficient model, also low cost
    private const val FALLBACK_MODEL = "google/gemini-2.5-flash-preview-05-20"

    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a chat message to OpenRouter, trying DeepSeek V3 first, then falling back
     * to Gemini 2.5 Flash. Returns the AI response text, or an error message.
     */
    suspend fun chat(
        userMessage: String,
        apiKey: String,
        transactions: List<TransactionEntity>,
        insights: List<ZenithInsight>,
        latestBalance: Double?
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = buildSystemPrompt(transactions, insights, latestBalance)

        // Try primary model first
        val primaryResult = runCatching {
            callOpenRouter(apiKey, PRIMARY_MODEL, systemPrompt, userMessage)
        }

        if (primaryResult.isSuccess) {
            return@withContext primaryResult.getOrThrow()
        }

        // Fallback to Gemini 2.5 Flash
        val fallbackResult = runCatching {
            callOpenRouter(apiKey, FALLBACK_MODEL, systemPrompt, userMessage)
        }

        fallbackResult.getOrElse { error ->
            when {
                error.message?.contains("401") == true ->
                    "⚠️ API key is invalid. Please check your key in Settings."
                error.message?.contains("429") == true ->
                    "⚠️ Rate limit reached. Please wait a moment and try again."
                error.message?.contains("timeout") == true || error.message?.contains("connect") == true ->
                    "⚠️ Network error. Check your internet connection."
                else ->
                    "⚠️ AI service is temporarily unavailable. Try again shortly."
            }
        }
    }

    private fun callOpenRouter(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 512)
            put("temperature", 0.7)
        }.toString()

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://zenith.finance.app")
            .addHeader("X-Title", "Zenith Finance")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response from AI")
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $responseBody")
            }
            val json = JSONObject(responseBody)
            return json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    private fun buildSystemPrompt(
        transactions: List<TransactionEntity>,
        insights: List<ZenithInsight>,
        latestBalance: Double?
    ): String {
        val totalIn = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val totalOut = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val topCategories = transactions
            .filter { it.type == "DEBIT" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key}: Ksh ${String.format("%,.0f", it.value)}" }

        val insightsSummary = insights.take(3).joinToString("; ") { it.title }

        return """
            You are Zenith, an expert personal finance assistant embedded in a Kenyan finance app.
            You analyze M-Pesa and Equity Bank transactions and give actionable, concise advice.
            Always respond in 2-4 sentences maximum. Be friendly, direct, and specific.
            Currency is Kenyan Shillings (Ksh).

            USER'S CURRENT FINANCIAL SNAPSHOT:
            - Total Income: Ksh ${String.format("%,.0f", totalIn)}
            - Total Expenses: Ksh ${String.format("%,.0f", totalOut)}
            - Net Flow: Ksh ${String.format("%,.0f", totalIn - totalOut)}
            - Latest Reported Balance: ${if (latestBalance != null) "Ksh ${String.format("%,.2f", latestBalance)}" else "N/A"}
            - Top Spending Categories: ${topCategories.ifEmpty { "No data yet" }}
            - Active Insights: ${insightsSummary.ifEmpty { "No insights yet" }}
            - Total Transactions Analyzed: ${transactions.size}

            Respond only to the user's question using this context. Do not repeat the entire summary.
        """.trimIndent()
    }
}
