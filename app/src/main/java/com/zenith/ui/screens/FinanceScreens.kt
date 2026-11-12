package com.zenith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.zenith.data.local.TransactionEntity
import com.zenith.domain.model.Category
import com.zenith.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDashboardScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val sourceBalances by viewModel.sourceBalances.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = { viewModel.manualScanSms(fullScan = true) },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Accounts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Privacy",
                            tint = if (isPrivacyMode) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.manualScanSms(fullScan = true) }) {
                        Icon(Icons.Default.Sync, contentDescription = "Full Sync SMS")
                    }
                }
            }
        }

        item {
            if (sourceBalances.isEmpty()) {
                val balance = transactions.sumOf { if (it.type == "CREDIT") it.amount else -it.amount }
                BalanceCard("Estimated Net", balance, isPrivacyMode)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sourceBalances.toList()) { (source, balance) ->
                        BalanceCard(source, balance, isPrivacyMode, modifier = Modifier.width(280.dp))
                    }
                }
            }
        }

        item {
            val totalIn = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
            val totalOut = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Income", totalIn, isPrivacyMode, Icons.Default.TrendingUp, Color(0xFF10B981), Modifier.weight(1f))
                SummaryCard("Expenses", totalOut, isPrivacyMode, Icons.Default.TrendingDown, Color(0xFFEF4444), Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onNavigate("transactions") }) {
                    Text("See All")
                }
            }
        }

        items(transactions.take(15)) { tx ->
            TransactionRow(tx, isPrivacyMode, onClick = { onNavigate("transaction_detail/${tx.id}") })
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
    } // end PullToRefreshBox
}

@Composable
private fun BalanceCard(label: String, balance: Double, isPrivacyMode: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = Brush.linearGradient(
                    colors = if (label == "MPESA") listOf(Color(0xFF10B981), Color(0xFF059669)) else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                )
            ).padding(20.dp)
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPrivacyMode) "****" else "Ksh ${String.format("%,.2f", balance)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Icon(imageVector = if (label == "MPESA") Icons.Default.Smartphone else Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(48.dp).align(Alignment.BottomEnd).graphicsLayer(alpha = 0.2f), tint = Color.White)
        }
    }
}

@Composable
private fun SummaryCard(label: String, amount: Double, isPrivacyMode: Boolean, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (isPrivacyMode) "***" else "Ksh ${String.format("%,.0f", amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransactionRow(tx: TransactionEntity, isPrivacyMode: Boolean, onClick: () -> Unit) {
    val category = try { Category.valueOf(tx.category) } catch (e: Exception) { Category.OTHER }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (tx.isSuspicious) androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(android.graphics.Color.parseColor(category.color)).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = getIconForCategory(category), contentDescription = null, tint = Color(android.graphics.Color.parseColor(category.color)), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (tx.isSuspicious) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Warning, contentDescription = "Suspicious", tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(" • ", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(tx.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            val isCredit = tx.type == "CREDIT"
            Text(
                if (isPrivacyMode) "***" else "${if (isCredit) "+" else "-"} Ksh ${String.format("%,.0f", tx.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
    var selectedSource by remember { mutableStateOf("All") }
    
    val sources = remember(transactions) { listOf("All") + transactions.map { it.source }.distinct() }
    val filteredTransactions = remember(transactions, selectedSource) { if (selectedSource == "All") transactions else transactions.filter { it.source == selectedSource } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Transactions") },
            actions = {
                IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                    Icon(if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Privacy")
                }
            }
        )
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources) { source ->
                FilterChip(selected = selectedSource == source, onClick = { selectedSource = source }, label = { Text(source) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredTransactions) { tx ->
                TransactionRow(tx, isPrivacyMode, onClick = { onNavigate("transaction_detail/${tx.id}") })
            }
        }
    }
}

fun getIconForCategory(category: Category): ImageVector {
    return when (category) {
        Category.FOOD_DINING -> Icons.Default.Restaurant
        Category.TRANSPORT -> Icons.Default.DirectionsCar
        Category.UTILITIES -> Icons.Default.Bolt
        Category.SHOPPING -> Icons.Default.ShoppingCart
        Category.HEALTH -> Icons.Default.MedicalServices
        Category.ENTERTAINMENT -> Icons.Default.Movie
        Category.SAVINGS -> Icons.Default.Savings
        Category.TRANSFER -> Icons.Default.SyncAlt
        Category.EDUCATION -> Icons.Default.School
        Category.RENT -> Icons.Default.Home
        Category.CASH_WITHDRAWAL -> Icons.Default.Payments
        Category.OTHER -> Icons.Default.Category
    }
}
