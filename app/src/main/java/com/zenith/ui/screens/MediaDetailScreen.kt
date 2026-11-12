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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
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
    var previewFile by remember { mutableStateOf<MediaFile?>(null) }

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

            // Folder Filter Pills
            if (folders.size > 2) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        val count = if (folder == "All") files.size else files.count { it.folder == folder }
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            label = { Text("$folder ($count)") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            }

            if (filteredFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No $typeLabel found", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    }
                }
            } else if (useListMode) {
                // List layout for audio / documents
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles) { file ->
                        MediaListRow(
                            file = file,
                            isSelected = selectedFiles.contains(file.path),
                            onSelect = {
                                selectedFiles = if (selectedFiles.contains(file.path))
                                    selectedFiles - file.path else selectedFiles + file.path
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                // Grid layout with dates
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByDate.forEach { (date, filesForDate) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    date,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${filesForDate.size} items • ${MediaScanner.formatBytes(filesForDate.sumOf { it.size })}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        items(filesForDate) { file ->
                            MediaThumbnail(
                                file = file,
                                isSelected = selectedFiles.contains(file.path),
                                onSelect = {
                                    selectedFiles = if (selectedFiles.contains(file.path))
                                        selectedFiles - file.path else selectedFiles + file.path
                                },
                                onPreview = {
                                    previewFile = file
                                }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Full-screen Image / Video In-App Lightbox
    previewFile?.let { file ->
        MediaLightboxDialog(
            file = file,
            onDismiss = { previewFile = null },
            onDelete = {
                viewModel.deleteMedia(listOf(file.path))
                previewFile = null
            }
        )
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

// ── In-App Media Lightbox Dialog ─────────────────────────────────────────────

@Composable
fun MediaLightboxDialog(
    file: MediaFile,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Media Display
            if (file.type == MediaType.VIDEOS) {
                // Video preview with frame extraction + quick launch player
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val request = ImageRequest.Builder(context)
                        .data(File(file.path))
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .build()

                    AsyncImage(
                        model = request,
                        contentDescription = "Video preview",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Fit
                    )

                    FloatingActionButton(
                        onClick = {
                            try {
                                val f = File(file.path)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) { /* ignore */ }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            } else {
                // Image Display
                AsyncImage(
                    model = File(file.path),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() },
                    contentScale = ContentScale.Fit
                )
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Open in external app
                    IconButton(
                        onClick = {
                            try {
                                val f = File(file.path)
                                val ext = file.name.substringAfterLast(".", "")
                                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open with"))
                            } catch (e: Exception) { /* ignore */ }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open In", tint = Color.White)
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.background(Color.Red.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                }
            }

            // Bottom File Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MediaScanner.formatBytes(file.size)} • ${file.folder} • Tap anywhere to dismiss",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
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
        else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = when (file.type) {
                    MediaType.DOCUMENTS -> Color(0xFFEF4444).copy(alpha = 0.15f)
                    MediaType.AUDIO -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                    MediaType.VOICE_NOTES -> Color(0xFF10B981).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (file.type) {
                            MediaType.DOCUMENTS -> Icons.Default.Description
                            MediaType.AUDIO -> Icons.Default.Audiotrack
                            MediaType.VOICE_NOTES -> Icons.Default.Mic
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when (file.type) {
                            MediaType.DOCUMENTS -> Color(0xFFEF4444)
                            MediaType.AUDIO -> Color(0xFFF59E0B)
                            MediaType.VOICE_NOTES -> Color(0xFF10B981)
                            else -> MaterialTheme.colorScheme.primary
                        },
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
fun MediaThumbnail(
    file: MediaFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
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
            .clickable { onPreview() }
    ) {
        // Thumbnail using Coil with VideoFrameDecoder support for videos
        val imageRequest = remember(file.path) {
            ImageRequest.Builder(context)
                .data(File(file.path))
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Video play overlay
        if (file.type == MediaType.VIDEOS) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
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

        // Select checkbox tap area (top-right)
        IconButton(
            onClick = onSelect,
            modifier = Modifier.size(36.dp).align(Alignment.TopEnd)
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
