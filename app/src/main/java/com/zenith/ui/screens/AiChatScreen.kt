package com.zenith.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.ui.MainViewModel

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = java.util.UUID.randomUUID().toString()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(viewModel: MainViewModel) {
    val latestBalance by viewModel.latestBalance.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var messageText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hello! I'm Zenith AI. I've loaded your financial data and I'm ready to help. Ask me about your balance, where you're spending too much, or how to save more. 💡",
                false
            )
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, isAiLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Zenith AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isAiLoading) "thinking..." else "DeepSeek V3 • OpenRouter",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAiLoading) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* Back */ }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = {
            Column {
                SuggestionChips(onChipClick = { messageText = it })
                ChatInputArea(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank() && !isAiLoading) {
                            val userMsg = messageText.trim()
                            messages.add(ChatMessage(userMsg, true))
                            messageText = ""

                            viewModel.sendMessage(userMsg) { response ->
                                messages.add(ChatMessage(response, false))
                            }
                        }
                    }
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg -> ChatBubble(msg) }
                if (isAiLoading) { item { TypingIndicator() } }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = alignment) {
        Surface(color = bgColor, shape = shape, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = index * 200), repeatMode = RepeatMode.Reverse),
                label = "dot"
            )
            Box(modifier = Modifier.padding(horizontal = 2.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        }
    }
}

@Composable
fun SuggestionChips(onChipClick: (String) -> Unit) {
    val suggestions = listOf("What's my balance?", "Advice on spending", "Saving strategy", "Where to cut?")
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions) { suggestion ->
            SuggestionChip(onClick = { onChipClick(suggestion) }, label = { Text(suggestion) })
        }
    }
}

@Composable
fun ChatInputArea(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 8.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = value, onValueChange = onValueChange, placeholder = { Text("Ask Zenith...") },
                modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        }
    }
}
