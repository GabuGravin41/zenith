package com.zenith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.ui.MainViewModel

@Composable
fun SocialPostScreen(platform: String, viewModel: MainViewModel) {
    var caption by remember { mutableStateOf("") }
    val platformColor = getPlatformColor(platform)
    val platformIcon = getPlatformIcon(platform)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(platformColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(platformIcon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(platform, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Automation Studio", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Caption Input
        Text("Compose Caption", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            placeholder = { Text("What's on your mind?") },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(12.dp))

        // AI Tools
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { /* AI Suggestion logic */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI Refine", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Automation Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("UI Automation Active", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "This will open $platform and use Accessibility Services to post your content automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action Button
        Button(
            onClick = { /* Trigger Accessibility Automation */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = platformColor),
            enabled = caption.isNotBlank()
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Automate Post", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(20.dp))
    }
}

fun getPlatformColor(platform: String): Color {
    return when (platform.lowercase()) {
        "instagram" -> Color(0xFFE4405F)
        "linkedin" -> Color(0xFF0A66C2)
        "snapchat" -> Color(0xFFFFFC00) // Note: Needs dark icon
        else -> Color.DarkGray
    }
}

fun getPlatformIcon(platform: String): ImageVector {
    return when (platform.lowercase()) {
        "instagram" -> Icons.Default.CameraAlt
        "linkedin" -> Icons.Default.Work
        "snapchat" -> Icons.Default.ChatBubble
        else -> Icons.Default.Share
    }
}
