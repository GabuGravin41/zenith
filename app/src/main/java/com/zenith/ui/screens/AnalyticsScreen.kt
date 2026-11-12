package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.model.Category
import com.zenith.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val allTransactions by viewModel.transactions.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
    val timeHorizonDays by viewModel.timeHorizonDays.collectAsState()
    val pendingReview by viewModel.pendingReviewTransactions.collectAsState()

    // Dynamic timeline filtering
    val transactions = remember(allTransactions, timeHorizonDays) {
        if (timeHorizonDays == null) {
            allTransactions
        } else {
            val cutoff = System.currentTimeMillis() - (timeHorizonDays!!.toLong() * 24 * 60 * 60 * 1000)
            allTransactions.filter { it.date >= cutoff }
        }
    }

    val categorySpending = remember(transactions) {
        transactions
            .filter { it.type == "DEBIT" }
            .groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }
    }
    val totalExpense = categorySpending.sumOf { it.second }
    val totalIncome = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val netFlow = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) ((netFlow / totalIncome) * 100).toInt() else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Intelligence Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "${transactions.size} transactions analyzed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle privacy",
                            tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Timeline Horizon Selector ───────────────────────────────────────
            item {
                TimelineSelector(
                    selectedDays = timeHorizonDays,
                    onSelect = { viewModel.setTimeHorizon(it) }
                )
            }

            // ── Pending Clarifications Loop (Hardcoded manual intelligence) ───────
            if (pendingReview.isNotEmpty()) {
                item {
                    ClarifyTransactionsCard(
                        pending = pendingReview,
                        isPrivacyMode = isPrivacyMode,
                        onClassify = { tx, cat ->
                            viewModel.reclassifyTransaction(tx.id, tx.description, cat)
                        }
                    )
                }
            }

            // ── Net Summary Card ────────────────────────────────────────────────
            item { NetSummaryCard(totalIncome, totalExpense, netFlow, savingsRate, isPrivacyMode) }

            // ── Cash Flow Bar ───────────────────────────────────────────────────
            item { CashFlowCard(totalIncome, totalExpense, isPrivacyMode) }

            // ── Spending Donut + Legend ─────────────────────────────────────────
            if (categorySpending.isNotEmpty()) {
                item { SpendingBreakdownCard(categorySpending, totalExpense, isPrivacyMode) }
            }

            // ── 30-Day Trend Line ───────────────────────────────────────────────
            item { TrendLineCard(transactions, isPrivacyMode) }

            // ── Top Merchants ────────────────────────────────────────────────────
            item { TopMerchantsCard(transactions, isPrivacyMode) }

            // ── Weekday vs Weekend ───────────────────────────────────────────────
            item { WeekdayWeekendCard(transactions, isPrivacyMode) }

            // ── Suspicious Transactions ──────────────────────────────────────────
            val suspicious = transactions.filter { it.isSuspicious }
            if (suspicious.isNotEmpty()) {
                item { SuspiciousCard(suspicious, isPrivacyMode) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Timeline Selector ────────────────────────────────────────────────────────

@Composable
fun TimelineSelector(selectedDays: Int?, onSelect: (Int?) -> Unit) {
    val horizons = listOf(
        "7 Days" to 7,
        "30 Days" to 30,
        "90 Days" to 90,
        "All Time" to null
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(horizons) { (label, days) ->
            FilterChip(
                selected = selectedDays == days,
                onClick = { onSelect(days) },
                label = { Text(label, fontWeight = if (selectedDays == days) FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

// ── Clarify Transactions Interactive Card ────────────────────────────────────

@Composable
fun ClarifyTransactionsCard(
    pending: List<TransactionEntity>,
    isPrivacyMode: Boolean,
    onClassify: (TransactionEntity, String) -> Unit
) {
    var expandedTx by remember { mutableStateOf<TransactionEntity?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Clarify Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Help Zenith learn where your money went",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            pending.take(3).forEach { tx ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tx.description,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", tx.amount)}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = { expandedTx = if (expandedTx?.id == tx.id) null else tx },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Classify", fontSize = 12.sp)
                        }
                    }

                    if (expandedTx?.id == tx.id) {
                        Spacer(Modifier.height(10.dp))
                        Text("What was this for?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        val options = listOf(
                            Category.FOOD_DINING,
                            Category.SHOPPING,
                            Category.TRANSPORT,
                            Category.UTILITIES,
                            Category.ENTERTAINMENT,
                            Category.TRANSFER
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            options.take(3).forEach { cat ->
                                OutlinedButton(
                                    onClick = {
                                        onClassify(tx, cat.name)
                                        expandedTx = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(cat.label.take(8), fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            options.drop(3).forEach { cat ->
                                OutlinedButton(
                                    onClick = {
                                        onClassify(tx, cat.name)
                                        expandedTx = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(cat.label.take(8), fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Net Summary ──────────────────────────────────────────────────────────────

private fun Double.masked(privacy: Boolean) = if (privacy) "••••" else "Ksh ${String.format("%,.0f", this)}"
private fun Double.maskedPrecise(privacy: Boolean) = if (privacy) "••••" else "Ksh ${String.format("%,.2f", this)}"

@Composable
fun NetSummaryCard(income: Double, expense: Double, net: Double, savingsRate: Int, isPrivacyMode: Boolean = false) {
    val animatedRate by animateIntAsState(
        targetValue = savingsRate.coerceIn(0, 100),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "savings"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    if (net >= 0)
                        listOf(Color(0xFF0F4C81), Color(0xFF1A7FCC))
                    else
                        listOf(Color(0xFF7F0F0F), Color(0xFFCC3333))
                )
            ).padding(24.dp)
        ) {
            Column {
                Text(
                    if (net >= 0) "You're saving money 🎉" else "Spending exceeds income ⚠️",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isPrivacyMode) "••••" else "Ksh ${String.format("%,.0f", kotlin.math.abs(net))}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    if (net >= 0) "net savings this period" else "net deficit this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatPill("Inflow", income.masked(isPrivacyMode), Color(0xFF4ADE80))
                    StatPill("Outflow", expense.masked(isPrivacyMode), Color(0xFFF87171))
                    StatPill("Rate", if (isPrivacyMode) "••%" else "$animatedRate%", Color(0xFF67E8F9))
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ── Cash Flow Bar ────────────────────────────────────────────────────────────

@Composable
fun CashFlowCard(income: Double, expense: Double, isPrivacyMode: Boolean = false) {
    val total = (income + expense).coerceAtLeast(1.0)
    val inRatio = (income / total).toFloat()
    val animatedInRatio by animateFloatAsState(
        targetValue = inRatio,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "cashflow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Cash In vs. Out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedInRatio)
                        .background(Color(0xFF10B981))
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPrivacyMode) "Income (••%)" else "Income (${(animatedInRatio * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPrivacyMode) "Expenses (••%)" else "Expenses (${((1f - animatedInRatio) * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ── Spending Donut Breakdown ─────────────────────────────────────────────────

private val PALETTE = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899),
    Color(0xFF14B8A6), Color(0xFFF97316), Color(0xFF6366F1),
    Color(0xFF84CC16)
)

@Composable
fun SpendingBreakdownCard(
    categorySpending: List<Pair<String, Double>>,
    totalExpense: Double,
    isPrivacyMode: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Category Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Donut Chart
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val stroke = 32.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arcSize = Size(diameter, diameter)
                    var startAngle = -90f

                    categorySpending.forEachIndexed { idx, pair ->
                        val sweep = if (totalExpense > 0) ((pair.second / totalExpense) * 360f).toFloat() else 0f
                        drawArc(
                            color = PALETTE[idx % PALETTE.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Spent", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        totalExpense.masked(isPrivacyMode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Legend rows
            categorySpending.forEachIndexed { idx, (catName, amount) ->
                val pct = if (totalExpense > 0) ((amount / totalExpense) * 100).toInt() else 0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(PALETTE[idx % PALETTE.size]))
                        Spacer(Modifier.width(10.dp))
                        Text(catName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(amount.masked(isPrivacyMode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("$pct%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ── 30-Day Trend Line ────────────────────────────────────────────────────────

@Composable
fun TrendLineCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    val dailySpend = remember(transactions) {
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        (0..29).map { d ->
            val dayStart = now - (29 - d) * dayMillis
            val dayEnd = dayStart + dayMillis
            val spent = transactions
                .filter { it.type == "DEBIT" && it.date in dayStart until dayEnd }
                .sumOf { it.amount }
            spent.toFloat()
        }
    }
    val maxSpend = (dailySpend.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("30-Day Spending Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isPrivacyMode) "Peak: ••••" else "Peak: Ksh ${String.format("%,.0f", maxSpend)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val stepX = size.width / (dailySpend.size - 1)
                val path = Path()
                val fillPath = Path()

                dailySpend.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v / maxSpend) * size.height
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        listOf(Color(0xFF3B82F6).copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                drawPath(
                    path,
                    Color(0xFF3B82F6),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("30 days ago", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("Today", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

// ── Top Merchants ────────────────────────────────────────────────────────────

@Composable
fun TopMerchantsCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    val topMerchants = remember(transactions) {
        transactions
            .filter { it.type == "DEBIT" }
            .groupBy { it.description }
            .mapValues { e -> e.value.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }
            .take(5)
    }

    if (topMerchants.isEmpty()) return

    val maxAmount = (topMerchants.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Top Outlets & Recipients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            topMerchants.forEach { (merchant, amount) ->
                val ratio = (amount / maxAmount).toFloat()
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            merchant.take(28),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(amount.masked(isPrivacyMode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF3B82F6))
                        )
                    }
                }
            }
        }
    }
}

// ── Weekday vs Weekend ───────────────────────────────────────────────────────

@Composable
fun WeekdayWeekendCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    val (weekdaySpend, weekendSpend) = remember(transactions) {
        val cal = Calendar.getInstance()
        var wd = 0.0
        var we = 0.0
        transactions.filter { it.type == "DEBIT" }.forEach {
            cal.timeInMillis = it.date
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) we += it.amount else wd += it.amount
        }
        Pair(wd, we)
    }

    val total = (weekdaySpend + weekendSpend).coerceAtLeast(1.0)
    val weekendPct = ((weekendSpend / total) * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Spending Rhythm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (weekendPct > 40) "🔥 Weekend spending is unusually high ($weekendPct% of total)"
                else "✅ Healthy spending distribution through the week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp)
                ) {
                    Text("Mon – Fri", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(weekdaySpend.masked(isPrivacyMode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${100 - weekendPct}% of total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(
                    modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp)
                ) {
                    Text("Sat – Sun", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(weekendSpend.masked(isPrivacyMode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                    Text("$weekendPct% of total", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

// ── Suspicious Transactions ──────────────────────────────────────────────────

@Composable
fun SuspiciousCard(suspicious: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Flagged Transactions (${suspicious.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444)
                )
            }
            Spacer(Modifier.height(12.dp))
            suspicious.forEach { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tx.description.take(25), style = MaterialTheme.typography.bodyMedium)
                    Text(tx.amount.masked(isPrivacyMode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            }
        }
    }
}
