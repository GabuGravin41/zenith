package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.domain.model.Category
import com.zenith.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(transactionId: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
    val transaction = transactions.find { it.id == transactionId }
    var showCategoryDialog by remember { mutableStateOf(false) }

    if (transaction == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Transaction not found")
        }
        return
    }

    val category = try { Category.valueOf(transaction.category) } catch (e: Exception) { Category.OTHER }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Privacy")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Amount Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(category.color)).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForCategory(category),
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(category.color)),
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                if (isPrivacyMode) "****" else "Ksh ${String.format("%,.2f", transaction.amount)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (transaction.type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444)
            )
            
            Text(
                transaction.description,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showCategoryDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Default.Category, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recategorize")
                }
                
                OutlinedButton(
                    onClick = { viewModel.toggleSuspicious(transaction.id, transaction.isSuspicious) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (transaction.isSuspicious) Color.Red else Color.LightGray),
                    colors = if (transaction.isSuspicious) ButtonDefaults.outlinedButtonColors(contentColor = Color.Red) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(if (transaction.isSuspicious) Icons.Default.GppBad else Icons.Default.GppMaybe, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (transaction.isSuspicious) "Flagged" else "Suspicious")
                }
            }

            Spacer(Modifier.height(32.dp))

            // Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailRow("Date", java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm").format(java.util.Date(transaction.date)))
                    DetailRow("Source", transaction.source)
                    DetailRow("Category", category.label)
                    DetailRow("Reference", transaction.reference ?: "N/A")
                    DetailRow("Type", transaction.type)
                    
                    if (transaction.balance != null) {
                        DetailRow("Account Balance", if (isPrivacyMode) "****" else "Ksh ${String.format("%,.2f", transaction.balance)}")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Raw Text Section
            Text("Raw Message", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Text(
                    transaction.rawText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }

    if (showCategoryDialog) {
        CategoryDialog(
            currentCategory = category,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { newCat ->
                viewModel.updateProviderCategory(transaction.description, newCat.name)
                showCategoryDialog = false
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryDialog(currentCategory: Category, onDismiss: () -> Unit, onConfirm: (Category) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Category") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Category.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(category) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(category.color))))
                        Spacer(Modifier.width(12.dp))
                        Text(category.label, fontWeight = if (category == currentCategory) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {}
    )
}
