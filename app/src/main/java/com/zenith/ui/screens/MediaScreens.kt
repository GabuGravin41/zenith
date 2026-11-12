package com.zenith.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.domain.MediaScanner
import com.zenith.domain.model.MediaFile
import com.zenith.domain.model.MediaType
import com.zenith.ui.MainViewModel
import com.zenith.ui.navigation.Screen

@Composable
fun WhatsAppCleanerScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Storage Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Centralized media and file management",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        item {
            var totalSize = 0L
            for (files in mediaFiles.values) { for (f in files) { totalSize += f.size } }
            StorageHeaderCard(totalSize, isScanning, onScan = { viewModel.scanMedia() })
        }

        if (mediaFiles.isNotEmpty()) {
            item {
                Text(
                    "Media Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(mediaFiles.toList()) { (type, files) ->
                if (files.isNotEmpty()) {
                    MediaTypeCard(
                        type = type,
                        files = files,
                        onClick = { onNavigate(Screen.MediaDetail.route.replace("{label}", type.label)) },
                        onDelete = { viewModel.deleteMedia(files.map { it.path }) }
                    )
                }
            }
        } else if (!isScanning) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AutoDelete,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No media scanned yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StorageHeaderCard(totalSize: Long, isScanning: Boolean, onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Unified Storage",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        MediaScanner.formatBytes(totalSize),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Button(
                    onClick = onScan,
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan")
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            val progress = (totalSize / (1024.0 * 1024.0 * 1024.0 * 10.0)).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You are using ${MediaScanner.formatBytes(totalSize)} of WhatsApp media.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MediaTypeCard(type: MediaType, files: List<MediaFile>, onClick: () -> Unit, onDelete: () -> Unit) {
    val totalSize = files.sumOf { it.size }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(getMediaColor(type).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getMediaIcon(type),
                    contentDescription = null,
                    tint = getMediaColor(type),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${files.size} items • ${MediaScanner.formatBytes(totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

fun getMediaIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.IMAGES -> Icons.Default.Image
        MediaType.VIDEOS -> Icons.Default.VideoLibrary
        MediaType.AUDIO -> Icons.Default.Audiotrack
        MediaType.DOCUMENTS -> Icons.Default.Description
        MediaType.VOICE_NOTES -> Icons.Default.Mic
        MediaType.GIFS -> Icons.Default.Gif
        MediaType.STICKERS -> Icons.Default.StickyNote2
    }
}

fun getMediaColor(type: MediaType): Color {
    return when (type) {
        MediaType.IMAGES -> Color(0xFF3B82F6)
        MediaType.VIDEOS -> Color(0xFFEF4444)
        MediaType.AUDIO -> Color(0xFFF59E0B)
        MediaType.DOCUMENTS -> Color(0xFF10B981)
        MediaType.VOICE_NOTES -> Color(0xFF8B5CF6)
        MediaType.GIFS -> Color(0xFFEC4899)
        MediaType.STICKERS -> Color(0xFFF97316)
    }
}
