package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()

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

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Intelligence Hub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${transactions.size} transactions analyzed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                    Icon(
                        if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle privacy",
                        tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ── Net Summary Card ────────────────────────────────────────────────
        item { NetSummaryCard(totalIncome, totalExpense, netFlow, savingsRate, isPrivacyMode) }

        // ── Cash Flow Bar ───────────────────────────────────────────────────
        item { CashFlowCard(totalIncome, totalExpense) }

        // ── Spending Donut + Legend ─────────────────────────────────────────
        if (categorySpending.isNotEmpty()) {
            item { SpendingBreakdownCard(categorySpending, totalExpense, isPrivacyMode) }
        }

        // ── 30-Day Trend Line ───────────────────────────────────────────────
        item { TrendLineCard(transactions) }

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
                    Column {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(income.masked(isPrivacyMode), fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80), fontSize = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Expenses", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(expense.masked(isPrivacyMode), fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5), fontSize = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Savings Rate", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(if (isPrivacyMode) "--%" else "$animatedRate%", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// ── Cash Flow Bar ─────────────────────────────────────────────────────────────

@Composable
fun CashFlowCard(income: Double, expense: Double) {
    val total = income + expense
    val incomeRatio by animateFloatAsState(
        targetValue = if (total > 0) (income / total).toFloat() else 0.5f,
        animationSpec = tween(1000), label = "ratio"
    )
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Cash Flow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(20.dp).clip(CircleShape)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(incomeRatio)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF34D399))))
                    )
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF87171))))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(6.dp))
                    Text("Income ${(incomeRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(Modifier.width(6.dp))
                    Text("Expenses ${((1f - incomeRatio) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── Spending Breakdown ────────────────────────────────────────────────────────

@Composable
fun SpendingBreakdownCard(data: List<Pair<String, Double>>, total: Double, isPrivacyMode: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Donut
                Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    AnimatedDonut(data, total)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Out", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            total.masked(isPrivacyMode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                // Legend
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.take(6).forEach { (catName, amount) ->
                        val category = try { Category.valueOf(catName) } catch (e: Exception) { Category.OTHER }
                        val pct = if (total > 0) ((amount / total) * 100).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(category.color))))
                            Spacer(Modifier.width(6.dp))
                            Text(category.label, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f), maxLines = 1)
                            Text("$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Progress bars for each category
            data.take(5).forEach { (catName, amount) ->
                val category = try { Category.valueOf(catName) } catch (e: Exception) { Category.OTHER }
                val pct = if (total > 0) (amount / total).toFloat() else 0f
                val animPct by animateFloatAsState(targetValue = pct, animationSpec = tween(900), label = "bar")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(amount.masked(isPrivacyMode), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { animPct },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Color(android.graphics.Color.parseColor(category.color)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun AnimatedDonut(data: List<Pair<String, Double>>, total: Double) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        var startAngle = -90f
        data.forEach { (catName, amount) ->
            val category = try { Category.valueOf(catName) } catch (e: Exception) { Category.OTHER }
            val sweep = (amount / total).toFloat() * 360f * animatedProgress.value
            drawArc(
                color = Color(android.graphics.Color.parseColor(category.color)),
                startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt),
                size = Size(size.width * 0.85f, size.height * 0.85f),
                topLeft = Offset(size.width * 0.075f, size.height * 0.075f)
            )
            startAngle += sweep
        }
    }
}

// ── 30-Day Trend Line ─────────────────────────────────────────────────────────

@Composable
fun TrendLineCard(transactions: List<TransactionEntity>) {
    val last30 = remember(transactions) {
        val sdf = SimpleDateFormat("dd", Locale.getDefault())
        (29 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val dayStart = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val dayEnd = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            val daySpend = transactions.filter { it.type == "DEBIT" && it.date in dayStart..dayEnd }.sumOf { it.amount }
            sdf.format(cal.time) to daySpend
        }
    }

    val animProg = remember { Animatable(0f) }
    LaunchedEffect(last30) {
        animProg.snapTo(0f)
        animProg.animateTo(1f, animationSpec = tween(1400, easing = FastOutSlowInEasing))
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("30-Day Spending Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Daily expenses over the last month", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            val primaryColor = MaterialTheme.colorScheme.primary
            val maxVal = last30.maxOf { it.second }.coerceAtLeast(1.0)
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val w = size.width
                val h = size.height
                val step = w / (last30.size - 1).toFloat()
                val points = last30.mapIndexed { i, (_, v) ->
                    Offset(i * step, h - (v / maxVal * h * animProg.value).toFloat())
                }
                // Fill under line
                val path = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent)))
                // Draw line
                for (i in 0 until points.size - 1) {
                    drawLine(primaryColor, points[i], points[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                // Dots on peaks
                val maxIdx = last30.indexOfFirst { it.second == last30.maxOf { p -> p.second } }
                if (maxIdx >= 0) {
                    drawCircle(Color(0xFFEF4444), radius = 6.dp.toPx(), center = points[maxIdx])
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(last30.firstOrNull()?.first ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("Peak: Ksh ${String.format("%,.0f", last30.maxOf { it.second })}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                Text(last30.lastOrNull()?.first ?: "Today", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

// ── Top Merchants ─────────────────────────────────────────────────────────────

@Composable
fun TopMerchantsCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    val merchants = remember(transactions) {
        transactions.filter { it.type == "DEBIT" }
            .groupBy { it.description }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList().sortedByDescending { it.second }.take(5)
    }
    val maxAmount = merchants.firstOrNull()?.second?.coerceAtLeast(1.0) ?: 1.0

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Top Merchants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Where your money goes most", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            merchants.forEachIndexed { idx, (name, amount) ->
                val ratio by animateFloatAsState(
                    targetValue = (amount / maxAmount).toFloat(),
                    animationSpec = tween(800, delayMillis = idx * 100), label = "m$idx"
                )
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (idx == 0) Color(0xFFF59E0B) else Color.Gray,
                        modifier = Modifier.width(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                modifier = Modifier.weight(1f))
                            Text(amount.masked(isPrivacyMode), style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = when (idx) {
                                0 -> Color(0xFFF59E0B)
                                1 -> Color(0xFF6366F1)
                                2 -> Color(0xFF10B981)
                                else -> Color.Gray
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
            if (merchants.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No expense data yet", color = Color.Gray)
                }
            }
        }
    }
}

// ── Weekday vs Weekend ────────────────────────────────────────────────────────

@Composable
fun WeekdayWeekendCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    val (weekday, weekend) = remember(transactions) {
        var wd = 0.0; var we = 0.0
        val cal = Calendar.getInstance()
        transactions.filter { it.type == "DEBIT" }.forEach {
            cal.timeInMillis = it.date
            if (cal.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)) we += it.amount
            else wd += it.amount
        }
        wd to we
    }
    val total = (weekday + weekend).coerceAtLeast(1.0)
    val wdRatio by animateFloatAsState(
        targetValue = (weekday / total).toFloat(), animationSpec = tween(900), label = "wdr"
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(Color(0xFF6366F1), -90f, wdRatio * 360f, false,
                        style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Color(0xFFF43F5E), -90f + wdRatio * 360f, (1f - wdRatio) * 360f, false,
                        style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(wdRatio * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Weekday", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Weekday vs Weekend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF6366F1)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Weekdays", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(weekday.masked(isPrivacyMode), fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF43F5E)))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Weekends", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(weekend.masked(isPrivacyMode), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Suspicious Transactions ───────────────────────────────────────────────────

@Composable
fun SuspiciousCard(transactions: List<TransactionEntity>, isPrivacyMode: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Suspicious Activity (${transactions.size})", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            }
            Spacer(Modifier.height(12.dp))
            transactions.take(3).forEach { tx ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(tx.amount.masked(isPrivacyMode), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            }
        }
    }
}
