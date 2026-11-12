package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.MediaScanner
import com.zenith.domain.ZenithInsight
import com.zenith.ui.MainViewModel
import com.zenith.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val latestBalance by viewModel.latestBalance.collectAsState()
    val sourceBalances by viewModel.sourceBalances.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    // Timeline filter
    val timelineOptions = listOf("3M", "6M", "1Y", "2Y", "All")
    var selectedTimeline by remember { mutableStateOf("6M") }
    val filteredTransactions = remember(transactions, selectedTimeline) {
        val now = System.currentTimeMillis()
        val cutoff = when (selectedTimeline) {
            "3M" -> now - 90L * 24 * 60 * 60 * 1000
            "6M" -> now - 180L * 24 * 60 * 60 * 1000
            "1Y" -> now - 365L * 24 * 60 * 60 * 1000
            "2Y" -> now - 730L * 24 * 60 * 60 * 1000
            else -> 0L
        }
        if (selectedTimeline == "All") transactions else transactions.filter { it.date >= cutoff }
    }

    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = { viewModel.manualScanSms(fullScan = true) },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        // Top Greeting Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Welcome back,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "Dalton",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                    Icon(
                        if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Privacy",
                        tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onNavigate(Screen.Profile.route) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Timeline filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(timelineOptions) { option ->
                FilterChip(
                    selected = selectedTimeline == option,
                    onClick = { selectedTimeline = option },
                    label = {
                        Text(
                            when (option) {
                                "3M" -> "3 Months"
                                "6M" -> "6 Months"
                                "1Y" -> "1 Year"
                                "2Y" -> "2 Years"
                                else -> "All Time"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Premium Main Dashboard Card
        MainSummaryCard(filteredTransactions, latestBalance, sourceBalances, isPrivacyMode, onNavigate)

        Spacer(Modifier.height(32.dp))

        // Zenith Intelligence Section
        if (insights.isNotEmpty()) {
            Text("Zenith Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(insights) { insight ->
                    InsightCard(insight)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Quick Tools Row
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionCard(
                "Party Ledger",
                Icons.Default.AccountBalanceWallet,
                Color(0xFF10B981),
                Modifier.weight(1f),
                onClick = { onNavigate(Screen.PartyLedger.route) }
            )
            ActionCard(
                "Fees & Costs",
                Icons.Default.ReceiptLong,
                Color(0xFFEF4444),
                Modifier.weight(1f),
                onClick = { onNavigate(Screen.TransactionFees.route) }
            )
            ActionCard(
                "Cleaner",
                Icons.Default.CleaningServices,
                Color(0xFF3B82F6),
                Modifier.weight(1f),
                onClick = { onNavigate(Screen.WhatsAppCleaner.route) }
            )
        }

        Spacer(Modifier.height(32.dp))

        // Recent Activity Preview
        if (filteredTransactions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onNavigate(Screen.Transactions.route) }) {
                    Text("View All")
                }
            }
            Spacer(Modifier.height(8.dp))
            filteredTransactions.take(5).forEach { tx ->
                TransactionRow(
                    tx, 
                    isPrivacyMode = isPrivacyMode,
                    onClick = { onNavigate(Screen.TransactionDetail.route.replace("{id}", tx.id)) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
    } // end PullToRefreshBox
}

@Composable
private fun InsightCard(insight: ZenithInsight) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(120.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(insight.icon) {
                        "Warning" -> Icons.Default.Warning
                        "Savings" -> Icons.Default.Savings
                        "Delete" -> Icons.Default.Delete
                        else -> Icons.Default.Lightbulb
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(insight.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
private fun MainSummaryCard(
    transactions: List<TransactionEntity>, 
    latestBalance: Double?, 
    sourceBalances: Map<String, Double>,
    isPrivacyMode: Boolean,
    onNavigate: (String) -> Unit
) {
    val totalIn = remember(transactions) { transactions.filter { it.type == "CREDIT" }.sumOf { it.amount } }
    val totalOut = remember(transactions) { transactions.filter { it.type == "DEBIT" }.sumOf { it.amount } }
    val netCalculated = remember(totalIn, totalOut) { totalIn - totalOut }
    
    fun formatPrivacy(value: Double, pattern: String): String {
        return if (isPrivacyMode) "****" else String.format(pattern, value)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (sourceBalances.size > 1) 240.dp else 210.dp)
            .clickable { onNavigate(Screen.FinanceDashboard.route) },
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Total Net (In - Out)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    "Ksh ${formatPrivacy(netCalculated.toDouble(), "%,.0f")}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (netCalculated >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                )
                
                Spacer(Modifier.height(16.dp))

                if (sourceBalances.isEmpty()) {
                    Text(
                        "Latest Reported Balance",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        "Ksh ${formatPrivacy(latestBalance ?: 0.0, "%,.2f")}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    sourceBalances.forEach { (source, balance) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(source, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                            Text("Ksh ${formatPrivacy(balance, "%,.2f")}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                
                Spacer(Modifier.weight(1f))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryItem("Income", "Ksh ${if (isPrivacyMode) "***" else String.format("%,.0f", totalIn)}", Color(0xFF10B981))
                    SummaryItem("Expenses", "Ksh ${if (isPrivacyMode) "***" else String.format("%,.0f", totalOut)}", Color(0xFFEF4444))
                }
            }
            
            Icon(
                imageVector = Icons.Default.AutoGraph,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer(alpha = 0.1f),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ActionCard(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
