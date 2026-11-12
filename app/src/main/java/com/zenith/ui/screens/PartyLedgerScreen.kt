package com.zenith.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.data.local.TransactionEntity
import com.zenith.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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

    // Group transactions by individual / party
    val partyLedgers = remember(transactions) {
        val map = mutableMapOf<String, MutableList<TransactionEntity>>()
        for (tx in transactions) {
            val name = tx.partyName?.takeIf { it.isNotBlank() }
                ?: run {
                    val desc = tx.description
                    when {
                        desc.startsWith("Sent to ", ignoreCase = true) -> desc.removePrefix("Sent to ").trim()
                        desc.startsWith("Received from ", ignoreCase = true) -> desc.removePrefix("Received from ").trim()
                        desc.startsWith("Paid to ", ignoreCase = true) -> desc.removePrefix("Paid to ").trim()
                        else -> null
                    }
                }
            if (name != null) {
                map.getOrPut(name) { mutableListOf() }.add(tx)
            }
        }
        map.mapValues { (_, list) ->
            val sent = list.filter { it.type == "DEBIT" }.sumOf { it.amount }
            val received = list.filter { it.type == "CREDIT" }.sumOf { it.amount }
            // net: positive = he owes you (you sent more), negative = you owe him (he sent more)
            val net = sent - received
            Triple(sent, received, net)
        }.toList().sortedByDescending { abs(it.second.third) }
    }

    val filteredParties = remember(partyLedgers, searchQuery) {
        if (searchQuery.isBlank()) partyLedgers
        else partyLedgers.filter { it.first.contains(searchQuery, ignoreCase = true) }
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
            // Search Input
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

            if (filteredParties.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found for individuals.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredParties) { (party, triple) ->
                        val (sent, received, net) = triple
                        val isExpanded = selectedParty == party

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParty = if (isExpanded) null else party },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            modifier = Modifier.size(42.dp),
                                            shape = CircleShape,
                                            color = if (net > 0) Color(0xFF10B981).copy(alpha = 0.15f)
                                            else if (net < 0) Color(0xFFEF4444).copy(alpha = 0.15f)
                                            else Color.Gray.copy(alpha = 0.15f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (net > 0) Icons.Default.CallMade
                                                    else if (net < 0) Icons.Default.CallReceived
                                                    else Icons.Default.SyncAlt,
                                                    contentDescription = null,
                                                    tint = if (net > 0) Color(0xFF10B981)
                                                    else if (net < 0) Color(0xFFEF4444)
                                                    else Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                party,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                when {
                                                    net > 0 -> "Owes you money"
                                                    net < 0 -> "You owe them"
                                                    else -> "Settled (0 KES)"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when {
                                                    net > 0 -> Color(0xFF10B981)
                                                    net < 0 -> Color(0xFFEF4444)
                                                    else -> Color.Gray
                                                },
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", abs(net))}",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = when {
                                                net > 0 -> Color(0xFF10B981)
                                                net < 0 -> Color(0xFFEF4444)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            "Net Balance",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                // Expanded view: Full breakdown & transactions
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Sent To", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(
                                                    if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", sent)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEF4444)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Total Received From", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(
                                                    if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", received)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))
                                        Text("Recent Transactions with $party:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))

                                        val partyTxs = transactions.filter {
                                            it.partyName.equals(party, ignoreCase = true) ||
                                                    it.description.contains(party, ignoreCase = true)
                                        }.take(5)

                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
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
                                                Text(
                                                    (if (tx.type == "CREDIT") "+" else "-") +
                                                            (if (isPrivacyMode) "••••" else "KES ${String.format("%,.0f", tx.amount)}"),
                                                    color = if (tx.type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
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
