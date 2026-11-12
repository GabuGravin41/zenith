package com.zenith.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import coil.compose.AsyncImage
import com.zenith.ui.MainViewModel
import com.zenith.worker.ScheduledPostWorker
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialStudioScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var sourceText by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf("Instagram", "LinkedIn")) }
    var platformDrafts by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var isGenerating by remember { mutableStateOf(false) }
    var currentDraftPlatform by remember { mutableStateOf<String?>(null) }
    var attachedImages by remember { mutableStateOf(listOf<Uri>()) }

    // Scheduling state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var scheduledDateMillis by remember { mutableStateOf<Long?>(null) }
    var scheduledHour by remember { mutableStateOf(9) }
    var scheduledMinute by remember { mutableStateOf(0) }
    var postScheduled by remember { mutableStateOf(false) }

    // Image picker launcher (multi-select)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> attachedImages = (attachedImages + uris).distinct() }

    val platforms = listOf(
        SocialPlatform("Instagram", Icons.Default.CameraAlt, Color(0xFFE4405F)),
        SocialPlatform("LinkedIn", Icons.Default.Work, Color(0xFF0A66C2)),
        SocialPlatform("X / Twitter", Icons.Default.Public, Color(0xFF000000)),
        SocialPlatform("Snapchat", Icons.Default.ChatBubble, Color(0xFFFFFC00), darkIcon = true),
        SocialPlatform("Facebook", Icons.Default.Groups, Color(0xFF1877F2))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Social Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("AI-crafted content • Smart scheduling", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        // ── Platform Selector ────────────────────────────────────────────────
        Text("Target Channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(platforms) { platform ->
                PlatformChip(
                    platform = platform,
                    isSelected = selectedPlatforms.contains(platform.name),
                    onClick = {
                        selectedPlatforms = if (selectedPlatforms.contains(platform.name))
                            selectedPlatforms - platform.name else selectedPlatforms + platform.name
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Content Input ────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Your Idea / Base Content", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("What do you want to share today?") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(12.dp))

                // ── Image Attachments ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attachments", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    TextButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Images")
                    }
                }

                if (attachedImages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachedImages) { uri ->
                            Box(modifier = Modifier.size(72.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                // Remove button
                                IconButton(
                                    onClick = { attachedImages = attachedImages - uri },
                                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove",
                                        tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        isGenerating = true
                        val newDrafts = mutableMapOf<String, String>()
                        selectedPlatforms.forEach { platform ->
                            newDrafts[platform] = generateMockDraft(platform, sourceText)
                        }
                        platformDrafts = newDrafts
                        isGenerating = false
                        currentDraftPlatform = selectedPlatforms.firstOrNull()
                        postScheduled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = sourceText.isNotBlank() && selectedPlatforms.isNotEmpty()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate AI Drafts")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Drafts + Scheduling ──────────────────────────────────────────────
        AnimatedVisibility(visible = platformDrafts.isNotEmpty()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Refined Drafts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                // ── Schedule Card ─────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (scheduledDateMillis != null)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Publish Schedule", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Date selector
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (scheduledDateMillis != null)
                                        java.text.SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(scheduledDateMillis!!))
                                    else "Pick Date",
                                    fontSize = 13.sp
                                )
                            }
                            // Time selector
                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                enabled = scheduledDateMillis != null
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(String.format("%02d:%02d", scheduledHour, scheduledMinute), fontSize = 13.sp)
                            }
                            // Clear schedule
                            if (scheduledDateMillis != null) {
                                IconButton(onClick = { scheduledDateMillis = null; postScheduled = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }
                        if (scheduledDateMillis != null) {
                            Spacer(Modifier.height(8.dp))
                            val scheduledCal = Calendar.getInstance().apply {
                                timeInMillis = scheduledDateMillis!!
                                set(Calendar.HOUR_OF_DAY, scheduledHour)
                                set(Calendar.MINUTE, scheduledMinute)
                            }
                            Text(
                                "📅 Scheduled for ${java.text.SimpleDateFormat("EEE, MMM dd 'at' HH:mm", Locale.getDefault()).format(scheduledCal.time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Platform tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedPlatforms.toList().indexOf(currentDraftPlatform ?: "").coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (currentDraftPlatform != null) {
                            val index = selectedPlatforms.toList().indexOf(currentDraftPlatform)
                            if (index >= 0) {
                                TabRowDefaults.Indicator(
                                    Modifier.tabIndicatorOffset(tabPositions[index]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                ) {
                    selectedPlatforms.forEach { platform ->
                        Tab(
                            selected = currentDraftPlatform == platform,
                            onClick = { currentDraftPlatform = platform },
                            text = { Text(platform) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                currentDraftPlatform?.let { platform ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            val draftText = platformDrafts[platform] ?: ""
                            TextField(
                                value = draftText,
                                onValueChange = { platformDrafts = platformDrafts.toMutableMap().also { m -> m[platform] = it } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                placeholder = { Text("Edit your draft...") }
                            )

                            // Attached image previews in draft
                            if (attachedImages.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("${attachedImages.size} image(s) attached",
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(attachedImages) { uri ->
                                        AsyncImage(
                                            model = uri, contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Action buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Publish Now
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent: Intent = when {
                                            attachedImages.size == 1 -> {
                                                val uri = attachedImages.first()
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/*"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_TEXT, draftText)
                                                    // ClipData is REQUIRED for URI permission grants on API 21+
                                                    clipData = ClipData.newRawUri("", uri)
                                                    addFlags(
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                                    )
                                                }
                                            }
                                            attachedImages.size > 1 -> {
                                                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                    type = "image/*"
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachedImages))
                                                    putExtra(Intent.EXTRA_TEXT, draftText)
                                                    // Build ClipData with all URIs so permission grants work
                                                    val clip = ClipData.newRawUri("", attachedImages.first())
                                                    attachedImages.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                                    clipData = clip
                                                    addFlags(
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                                    )
                                                }
                                            }
                                            else -> {
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, draftText)
                                                }
                                            }
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Post to $platform"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Post Now", fontSize = 13.sp)
                                }

                                // Schedule or Confirm Schedule
                                Button(
                                    onClick = {
                                        if (scheduledDateMillis != null) {
                                            // Enqueue WorkManager job
                                            val scheduledCal = Calendar.getInstance().apply {
                                                timeInMillis = scheduledDateMillis!!
                                                set(Calendar.HOUR_OF_DAY, scheduledHour)
                                                set(Calendar.MINUTE, scheduledMinute)
                                                set(Calendar.SECOND, 0)
                                            }
                                            val delayMs = scheduledCal.timeInMillis - System.currentTimeMillis()
                                            if (delayMs > 0) {
                                                val data = workDataOf(
                                                    "platform" to platform,
                                                    "text" to draftText
                                                )
                                                val work = OneTimeWorkRequestBuilder<ScheduledPostWorker>()
                                                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                                                    .setInputData(data)
                                                    .addTag("scheduled_post_$platform")
                                                    .build()
                                                WorkManager.getInstance(context)
                                                    .enqueueUniqueWork(
                                                        "post_${platform}_${scheduledCal.timeInMillis}",
                                                        ExistingWorkPolicy.REPLACE,
                                                        work
                                                    )
                                                postScheduled = true
                                            }
                                        } else {
                                            showDatePicker = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = if (postScheduled)
                                        ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    else ButtonDefaults.buttonColors()
                                ) {
                                    Icon(
                                        if (postScheduled) Icons.Default.EventAvailable else Icons.Default.EventNote,
                                        contentDescription = null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (postScheduled) "Scheduled ✓"
                                        else if (scheduledDateMillis != null) "Confirm Schedule"
                                        else "Schedule",
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Date Picker Dialog ───────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    scheduledDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next: Pick Time") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // ── Time Picker Dialog ───────────────────────────────────────────────────
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledHour, initialMinute = scheduledMinute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick a time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scheduledHour = timePickerState.hour
                    scheduledMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

private fun generateMockDraft(platform: String, source: String): String {
    return when (platform) {
        "Instagram" -> "✨ $source\n\n#lifestyle #zenith #automation #ai"
        "LinkedIn" -> "I'm excited to share insights on: $source\n\nHow are you approaching this in your workflow? #Professional #AI #Strategy"
        "X / Twitter" -> "Just thinking about $source. 🧵 #ZenithAI"
        "Snapchat" -> "Live: $source 🔥"
        else -> source
    }
}

data class SocialPlatform(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val darkIcon: Boolean = false
)

@Composable
fun PlatformChip(platform: SocialPlatform, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        color = if (isSelected) platform.color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(platform.icon, contentDescription = null,
                tint = if (isSelected) (if (platform.darkIcon) Color.Black else Color.White) else Color.Gray,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(platform.name,
                color = if (isSelected) (if (platform.darkIcon) Color.Black else Color.White) else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp)
        }
    }
}
