package com.zenith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()

    // Real dynamic stats derived from local SQLite Room DB
    val txCount = transactions.size
    val txCountStr = if (txCount >= 1000) String.format("%.1fk", txCount / 1000f) else "$txCount"

    val totalMediaCount = mediaFiles.values.sumOf { it.size }
    val mediaStr = if (totalMediaCount >= 1000) String.format("%.1fk", totalMediaCount / 1000f) else "$totalMediaCount"

    val linkedSources = remember(transactions) {
        transactions.map { it.source }.distinct().filter { it.isNotBlank() }
    }
    val accountsCount = linkedSources.size

    val oldestTx = remember(transactions) { transactions.minByOrNull { it.date } }
    val activeSince = remember(oldestTx) {
        oldestTx?.let {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(it.date))
        } ?: "Recent"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header / Cover Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
            )

            // Profile Info Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(110.dp)
                        .border(4.dp, Color.White, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Dalton", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Active since $activeSince", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(80.dp))

        // Real Stats Row from Local Phone DB
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStat("Transactions", txCountStr)
            ProfileStat("Accounts", "$accountsCount")
            ProfileStat("Media Items", mediaStr)
        }

        Spacer(Modifier.height(32.dp))

        // Settings / Data Sections
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Data Sovereignty & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            ProfileMenuItem(
                title = "Privacy Mode (Blur Numbers)",
                subtitle = if (isPrivacyMode) "Currently BLURRED (Protected)" else "Currently VISIBLE",
                icon = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                onClick = { viewModel.togglePrivacyMode() }
            )

            ProfileMenuItem(
                title = "Offline Intelligence",
                subtitle = "Pure rule-based parsing • No cloud AI API keys required",
                icon = Icons.Default.Shield
            )

            ProfileMenuItem(
                title = "Local Storage Only",
                subtitle = "All financial records stored encrypted in SQLite on-device",
                icon = Icons.Default.Storage
            )

            Spacer(Modifier.height(28.dp))

            Text("Detected Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (linkedSources.isEmpty()) {
                Text("No accounts detected yet. Sync SMS to discover accounts.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                linkedSources.forEach { source ->
                    val txForSource = transactions.count { it.source == source }
                    ProfileMenuItem(
                        title = source,
                        subtitle = "$txForSource transactions recorded",
                        icon = if (source == "MPESA") Icons.Default.Smartphone else Icons.Default.AccountBalance
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text("System & Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            ProfileMenuItem(
                title = "SMS Parser",
                subtitle = "Multi-Bank Parser (M-Pesa, KCB, Equity, Co-op, NCBA, Stanbic, Absa)",
                icon = Icons.Default.Receipt
            )

            ProfileMenuItem(
                title = "Deduplication Engine",
                subtitle = "NameMatcher (Levenshtein + Token Overlap clustering)",
                icon = Icons.Default.CompareArrows
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun ProfileMenuItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}
