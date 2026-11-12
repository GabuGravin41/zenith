package com.zenith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zenith.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val aiProviderUrl by viewModel.aiProviderUrl.collectAsState()
    
    var apiKeyText by remember { mutableStateOf("") }
    var providerUrlText by remember { mutableStateOf("") }
    
    LaunchedEffect(aiApiKey, aiProviderUrl) {
        apiKeyText = aiApiKey ?: ""
        providerUrlText = aiProviderUrl ?: "https://openrouter.ai/api/v1"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & AI Config") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("AI Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Configure your AI provider (OpenRouter, OpenAI, etc.) to enable advanced financial insights and social content generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("AI API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (apiKeyText.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                            }
                        }
                    )
                    
                    OutlinedTextField(
                        value = providerUrlText,
                        onValueChange = { providerUrlText = it },
                        label = { Text("Provider Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("https://openrouter.ai/api/v1") }
                    )
                    
                    Button(
                        onClick = {
                            viewModel.saveAiApiKey(apiKeyText)
                            viewModel.saveAiProviderUrl(providerUrlText)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Config")
                    }
                }
            }

            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            ListItem(
                headlineContent = { Text("Privacy Mode") },
                supportingContent = { Text("Mask balances and transaction amounts globally") },
                trailingContent = {
                    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
                    Switch(checked = isPrivacyMode, onCheckedChange = { viewModel.togglePrivacyMode() })
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.height(40.dp))
            
            Text(
                "Zenith Platform v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
