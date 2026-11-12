package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenith.data.local.TransactionEntity
import com.zenith.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private data class PartyEntry(
    val name: String,
    val sent: Double,
    val received: Double,
    val net: Double          // positive = they owe you, negative = you owe them
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyLedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedParty by remember { mutableStateOf<String?>(null) }

    // In-session overrides (persist across recompositions but reset on app restart)
    val dismissedParties = remember { mutableStateListOf<String>() }
    val flippedParties   = remember { mutableStateListOf<String>() }

    // Build one PartyEntry per person (names normalized to Title Case for deduplication)
    val partyLedgers: List<PartyEntry> = remember(transactions) {
        val map = mutableMapOf<String, MutableList<TransactionEntity>>()
        for (tx in transactions) {
            val rawName: String? = tx.partyName?.takeIf { it.isNotBlank() }
                ?: run {
                    val desc = tx.description
                    when {
                        desc.startsWith("Sent to ", ignoreCase = true)      -> desc.removePrefix("Sent to ").trim()
                        desc.startsWith("Received from ", ignoreCase = true) -> desc.removePrefix("Received from ").trim()
                        desc.startsWith("Paid to ", ignoreCase = true)       -> desc.removePrefix("Paid to ").trim()
                        else -> null
                    }
                }
            if (rawName != null) {
                val key = rawName.trim().lowercase()
                    .split(" ")
                    .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                map.getOrPut(key) { mutableListOf() }.add(tx)
            }
        }
        map.map { (name, list) ->
            val sent     = list.filter { it.type == "DEBIT"  }.sumOf { it.amount }
            val received = list.filter { it.type == "CREDIT" }.sumOf { it.amount }
            PartyEntry(name = name, sent = sent, received = received, net = sent - received)
        }.sortedByDescending { abs(it.net) }
    }

    // Apply search + dismissed filter + flip overrides
    val displayList: List<PartyEntry> = remember(partyLedgers, searchQuery, dismissedParties, flippedParties) {
        partyLedgers
            .filter { it.name !in dismissedParties }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .map { entry ->
                if (entry.name in flippedParties) entry.copy(net = -entry.net) else entry
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party Ledger & Debts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Privacy"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search person or business...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            if (displayList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found for individuals.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayList, key = { it.name }) { entry ->
                        val isExpanded = selectedParty == entry.name
                        val netAbs = abs(entry.net)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParty = if (isExpanded) null else entry.name },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                // ── Header row ──
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val iconColor = when {
                                            entry.net > 0 -> Color(0xFF10B981)
                                            entry.net < 0 -> Color(0xFFEF4444)
                                            else -> Color.Gray
                                        }
                                        Surface(
                                            modifier = Modifier.size(42.dp),
                                            shape = CircleShape,
                                            color = iconColor.copy(alpha = 0.15f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when {
                                                        entry.net > 0 -> Icons.Default.CallMade
                                                        entry.net < 0 -> Icons.Default.CallReceived
                                                        else -> Icons.Default.SyncAlt
                                                    },
                                                    contentDescription = null,
                                                    tint = iconColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = entry.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val label = when {
                                                entry.net > 0 -> "Owes you money"
                                                entry.net < 0 -> "You owe them"
                                                else -> "Settled (0 KES)"
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when {
                                                    entry.net > 0 -> Color(0xFF10B981)
                                                    entry.net < 0 -> Color(0xFFEF4444)
                                                    else -> Color.Gray
                                                },
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        val netStr = if (isPrivacyMode) "••••"
                                            else "KES ${String.format("%,.0f", netAbs)}"
                                        Text(
                                            text = netStr,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = when {
                                                entry.net > 0 -> Color(0xFF10B981)
                                                entry.net < 0 -> Color(0xFFEF4444)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = "Net Balance",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                // ── Expanded detail ──
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                        // Sent / Received breakdown
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Sent To", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(
                                                    text = if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", entry.sent)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEF4444)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Total Received From", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(
                                                    text = if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", entry.received)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        // Recent transactions
                                        Text(
                                            text = "Recent Transactions with ${entry.name}:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(6.dp))

                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                        val partyTxs = transactions.filter { tx ->
                                            val nameMatch = tx.partyName?.equals(entry.name, ignoreCase = true) == true
                                            val descMatch = tx.description.contains(entry.name, ignoreCase = true)
                                            nameMatch || descMatch
                                        }.take(5)

                                        partyTxs.forEach { tx ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable { onNavigateToDetail(tx.id) },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(sdf.format(Date(tx.date)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text(tx.category, style = MaterialTheme.typography.bodySmall)
                                                }
                                                val sign = if (tx.type == "CREDIT") "+" else "-"
                                                val amtStr = if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", tx.amount)}"
                                                Text(
                                                    text = "$sign$amtStr",
                                                    color = if (tx.type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }

                                        // ── Action buttons ──
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (flippedParties.contains(entry.name)) flippedParties.remove(entry.name)
                                                    else flippedParties.add(entry.name)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Flip Direction", style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    dismissedParties.add(entry.name)
                                                    selectedParty = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Remove", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}
