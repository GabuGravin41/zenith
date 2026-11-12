package com.zenith.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.data.local.MediaFilterEntity
import com.zenith.ui.MainViewModel

@Composable
fun MediaFilterScreen(viewModel: MainViewModel) {
    val filters by viewModel.mediaFilters.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Cleanup Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Flag groups or keywords to target for deletion", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filters.isEmpty()) {
                item {
                    EmptyFiltersState()
                }
            }

            items(filters.size) { index ->
                val item = filters[index]
                FilterCard(
                    filter = item,
                    onToggle = { viewModel.toggleFilter(item) },
                    onDelete = { viewModel.deleteFilter(item) }
                )
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddFilterDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { pattern, type ->
                viewModel.addFilter(pattern, type)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyFiltersState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No cleanup rules yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                "Tap + to flag a group or keyword",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun FilterCard(
    filter: MediaFilterEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (filter.isEnabled) MaterialTheme.colorScheme.surface 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (filter.type == "GROUP") Color(0xFF10B981).copy(alpha = 0.1f)
                        else Color(0xFF3B82F6).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (filter.type == "GROUP") Icons.Default.Groups else Icons.Default.TextFields,
                    contentDescription = null,
                    tint = if (filter.type == "GROUP") Color(0xFF10B981) else Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    filter.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (filter.isEnabled) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    if (filter.type == "GROUP") "WhatsApp Group" else "Filename Keyword",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            Switch(
                checked = filter.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary
                )
            )
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddFilterDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("GROUP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cleanup Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Group Name or Keyword") },
                    placeholder = { Text("e.g. Hiking Group 2024") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Text("Target Type:", style = MaterialTheme.typography.labelLarge)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeChip(
                        label = "WhatsApp Group",
                        isSelected = selectedType == "GROUP",
                        onClick = { selectedType = "GROUP" },
                        modifier = Modifier.weight(1f)
                    )
                    TypeChip(
                        label = "Keyword",
                        isSelected = selectedType == "KEYWORD",
                        onClick = { selectedType = "KEYWORD" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pattern.isNotBlank()) onConfirm(pattern, selectedType) },
                enabled = pattern.isNotBlank()
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun TypeChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        modifier = modifier
    )
}
