package com.zenith.ui.screens

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.zenith.domain.MediaScanner
import com.zenith.domain.model.MediaFile
import com.zenith.domain.model.MediaType
import com.zenith.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(typeLabel: String, viewModel: MainViewModel) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val mediaType = MediaType.values().find { it.label == typeLabel } ?: MediaType.IMAGES
    val files = mediaFiles[mediaType] ?: emptyList()

    var selectedFolder by remember { mutableStateOf("All") }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // List mode for audio/docs, grid mode for images/videos
    val useListMode = mediaType in listOf(MediaType.VOICE_NOTES, MediaType.AUDIO, MediaType.DOCUMENTS)

    val filteredFiles = remember(files, selectedFolder) {
        if (selectedFolder == "All") files else files.filter { it.folder == selectedFolder }
    }
    val groupedByDate = remember(filteredFiles) {
        filteredFiles.groupBy {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.modificationTime))
        }.toList().sortedByDescending { it.first }
    }

    val folders = remember(files) {
        listOf("All") + files.map { it.folder }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(typeLabel, fontWeight = FontWeight.Bold)
                        Text(
                            "${filteredFiles.size} files • ${MediaScanner.formatBytes(filteredFiles.sumOf { it.size })}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    if (selectedFiles.isNotEmpty()) {
                        TextButton(onClick = { selectedFiles = filteredFiles.map { it.path }.toSet() }) {
                            Text("All")
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete ${selectedFiles.size}")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Folder filter tabs
            if (folders.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = folders.indexOf(selectedFolder).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    folders.forEach { folder ->
                        val count = if (folder == "All") files.size else files.count { it.folder == folder }
                        Tab(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder; selectedFiles = emptySet() },
                            text = { Text("$folder ($count)") }
                        )
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            getMediaIcon(mediaType), contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No $typeLabel found", color = Color.Gray)
                        Text(
                            "Tap 'Scan' on the Storage Studio page to find your media",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else if (useListMode) {
                // List mode for audio / voice notes / documents
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByDate.forEach { (date, dayFiles) ->
                        item {
                            Text(
                                date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(dayFiles) { file ->
                            MediaListRow(
                                file = file,
                                isSelected = selectedFiles.contains(file.path),
                                onSelect = {
                                    selectedFiles = if (selectedFiles.contains(file.path))
                                        selectedFiles - file.path else selectedFiles + file.path
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                // Grid mode for images / videos
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedByDate.forEach { (date, dayFiles) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(dayFiles) { file ->
                            MediaThumbnail(
                                file = file,
                                isSelected = selectedFiles.contains(file.path),
                                onSelect = {
                                    selectedFiles = if (selectedFiles.contains(file.path))
                                        selectedFiles - file.path else selectedFiles + file.path
                                }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedFiles.size} files?") },
            text = {
                Text(
                    "This will permanently delete ${selectedFiles.size} file(s) from your device. This cannot be undone.\n\n" +
                    "Total size: ${MediaScanner.formatBytes(filteredFiles.filter { it.path in selectedFiles }.sumOf { it.size })}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMedia(selectedFiles.toList())
                        selectedFiles = emptySet()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── List row for audio / voice notes / documents ─────────────────────────────

@Composable
fun MediaListRow(file: MediaFile, isSelected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null,
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / type indicator
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(getMediaColor(file.type).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = when (file.type) {
                            MediaType.VOICE_NOTES -> Icons.Default.Mic
                            MediaType.AUDIO -> Icons.Default.MusicNote
                            MediaType.DOCUMENTS -> Icons.Default.InsertDriveFile
                            else -> Icons.Default.AttachFile
                        },
                        contentDescription = null,
                        tint = getMediaColor(file.type),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        MediaScanner.formatBytes(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        sdf.format(Date(file.modificationTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (file.folder) {
                            "Sent" -> Color(0xFF10B981).copy(alpha = 0.15f)
                            "Received" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                            else -> Color.Gray.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            file.folder,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (file.folder) {
                                "Sent" -> Color(0xFF10B981)
                                "Received" -> Color(0xFF3B82F6)
                                else -> Color.Gray
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Open button
            IconButton(onClick = {
                try {
                    val f = File(file.path)
                    val ext = file.name.substringAfterLast(".", "")
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) { /* no app to handle */ }
            }) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Grid thumbnail for images / videos ───────────────────────────────────────

@Composable
fun MediaThumbnail(file: MediaFile, isSelected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
    ) {
        // Thumbnail
        AsyncImage(
            model = File(file.path),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Video play overlay
        if (file.type == MediaType.VIDEOS) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            Icon(
                Icons.Default.PlayCircle, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(32.dp).align(Alignment.Center)
            )
        }

        // Selection overlay
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            Icon(
                Icons.Default.CheckCircle, contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp).align(Alignment.TopEnd).padding(6.dp)
            )
        }

        // Bottom metadata bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Column {
                Text(
                    MediaScanner.formatBytes(file.size),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    file.folder,
                    color = when (file.folder) {
                        "Sent" -> Color(0xFF4ADE80)
                        "Received" -> Color(0xFF60A5FA)
                        else -> Color.White.copy(alpha = 0.7f)
                    },
                    fontSize = 8.sp
                )
            }
        }

        // Tap-to-open icon (top-left when not selected)
        if (!isSelected) {
            IconButton(
                onClick = {
                    try {
                        val f = File(file.path)
                        val ext = file.name.substringAfterLast(".", "")
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                            ?: if (file.type == MediaType.IMAGES) "image/*" else "video/*"
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open"))
                    } catch (e: Exception) { /* no app */ }
                },
                modifier = Modifier.size(32.dp).align(Alignment.TopStart)
            ) {
                Icon(
                    Icons.Default.OpenInNew, contentDescription = "Preview",
                    tint = Color.White, modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
