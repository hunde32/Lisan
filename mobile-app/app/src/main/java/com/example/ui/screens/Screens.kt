package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MessageEntity
import com.example.ui.theme.*
import com.example.ui.tr
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------------------------------------
// HELPER FORMATTER
// ------------------------------------------------------------------------------------------------
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ------------------------------------------------------------------------------------------------
// 1. LIVE TRANSLATOR SCREEN (Completely Redesigned)
// ------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LiveTranslatorScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    val ttsAmharic by viewModel.translationLanguageAmharic.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val isConnected by viewModel.bluetoothManager.isConnected.collectAsStateWithLifecycle()
    val latency by viewModel.bluetoothManager.latencyMs.collectAsStateWithLifecycle()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf(setOf<Int>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-exit selection mode if no messages remain or all deselected
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            isSelectionMode = false
            selectedMessageIds = emptySet()
        } else {
            // Automatically scroll down to the newest incoming message
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedMessageIds.size} " + "Selected".tr(uiAmharic),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel".tr(uiAmharic))
                        }
                    },
                    actions = {
                        // Select All / Deselect All
                        IconButton(onClick = {
                            if (selectedMessageIds.size == messages.size) {
                                selectedMessageIds = emptySet()
                            } else {
                                selectedMessageIds = messages.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedMessageIds.size == messages.size && messages.isNotEmpty()) {
                                    Icons.Outlined.Deselect
                                } else {
                                    Icons.Outlined.SelectAll
                                },
                                contentDescription = "Select All".tr(uiAmharic),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Delete Selected
                        IconButton(
                            onClick = {
                                if (selectedMessageIds.isNotEmpty()) {
                                    viewModel.deleteMessages(selectedMessageIds.toList())
                                    selectedMessageIds = emptySet()
                                    isSelectionMode = false
                                }
                            },
                            enabled = selectedMessageIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete Selected".tr(uiAmharic),
                                tint = if (selectedMessageIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Lisan".tr(uiAmharic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        // Hardware status chip
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    BorderStroke(1.dp, if (isConnected || isDemoMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pulseAnim = rememberInfiniteTransition(label = "pulse")
                            val alpha by pulseAnim.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when {
                                            isConnected || isDemoMode -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isConnected -> "${"GLOVE".tr(uiAmharic)} ${latency}ms"
                                    isDemoMode -> "DEMO".tr(uiAmharic)
                                    else -> "OFFLINE".tr(uiAmharic)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isConnected || isDemoMode -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Trigger Selection Mode
                        IconButton(
                            onClick = { isSelectionMode = true },
                            enabled = messages.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Outlined.Checklist,
                                contentDescription = "Select".tr(uiAmharic),
                                tint = if (messages.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                // Futuristic empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Emerald.copy(alpha = 0.2f),
                                        CyanAccent.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(BorderStroke(1.dp, Emerald.copy(alpha = 0.3f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Ready to Translate".tr(uiAmharic),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Wear glove or tap mic to start".tr(uiAmharic),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hold message to select & delete".tr(uiAmharic),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isSelected = selectedMessageIds.contains(msg.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = isSelectionMode,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                IconButton(
                                    onClick = {
                                        selectedMessageIds = if (isSelected) {
                                            selectedMessageIds - msg.id
                                        } else {
                                            selectedMessageIds + msg.id
                                        }
                                        if (selectedMessageIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedMessageIds = if (isSelected) {
                                                    selectedMessageIds - msg.id
                                                } else {
                                                    selectedMessageIds + msg.id
                                                }
                                                if (selectedMessageIds.isEmpty()) {
                                                    isSelectionMode = false
                                                }
                                            } else {
                                                // Quick speech playback on tap
                                                viewModel.speechManager.speak(msg.text)
                                            }
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedMessageIds = if (isSelected) {
                                                selectedMessageIds - msg.id
                                            } else {
                                                selectedMessageIds + msg.id
                                            }
                                        }
                                    )
                            ) {
                                ModernChatBubble(
                                    message = msg,
                                    uiAmharic = uiAmharic,
                                    isSelected = isSelected,
                                    onPlayAudio = { viewModel.speechManager.speak(msg.text) }
                                )
                            }
                        }
                    }
                }
            }

            // Floating Bottom Audio & Control Deck
            FloatingControlDeck(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                isListening = isListening,
                uiAmharic = uiAmharic,
                isDemoMode = isDemoMode,
                ttsAmharic = ttsAmharic,
                onToggleLanguage = { viewModel.toggleTranslationLanguage() },
                onMicToggle = {
                    if (isListening) {
                        viewModel.speechManager.stopListening()
                    } else {
                        viewModel.speechManager.startListening()
                        if (isDemoMode) {
                            viewModel.simulateGloveMessage()
                        }
                    }
                },
                onSimulateGlove = {
                    viewModel.simulateGloveMessage()
                }
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// MODERN CHAT BUBBLE
// ------------------------------------------------------------------------------------------------
@Composable
fun ModernChatBubble(
    message: MessageEntity,
    uiAmharic: Boolean,
    isSelected: Boolean,
    onPlayAudio: () -> Unit
) {
    val isGlove = message.isFromGlove
    val alignment = if (isGlove) Alignment.CenterStart else Alignment.CenterEnd

    val bubbleShape = if (isGlove) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    }

    val bubbleBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        isGlove -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isGlove -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isGlove) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            // Source metadata chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isGlove) Icons.Outlined.TouchApp else Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = if (isGlove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isGlove) {
                        "GLOVE INPUT (AMHARIC)".tr(uiAmharic)
                    } else {
                        "VOICE OUTPUT (ENGLISH)".tr(uiAmharic)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGlove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
            }

            Surface(
                color = bubbleBg,
                shape = bubbleShape,
                border = borderStroke,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 23.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick replay speaker button
                        IconButton(
                            onClick = onPlayAudio,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = "Play".tr(uiAmharic),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// FLOATING CONTROL DECK (Microphone & Mode Controls)
// ------------------------------------------------------------------------------------------------
@Composable
fun FloatingControlDeck(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    uiAmharic: Boolean,
    isDemoMode: Boolean,
    ttsAmharic: Boolean,
    onToggleLanguage: () -> Unit,
    onMicToggle: () -> Unit,
    onSimulateGlove: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Interactive Language Pair Pill (Tapping toggles translation direction)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleLanguage() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!ttsAmharic) {
                    Text("EN", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = "Switch translation direction".tr(uiAmharic),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(16.dp)
                    )
                    Text("አማ", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("አማ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        contentDescription = "Switch translation direction".tr(uiAmharic),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(16.dp)
                    )
                    Text("EN", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Primary Interactive Mic Button - uses weight(1f) to flexibly fill center space without overlap
            val pulseTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isListening) 1.15f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "micPulse"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isListening) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onMicToggle() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .scale(pulseScale)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f), CircleShape)
                        )
                    }
                    Icon(
                        imageVector = if (isListening) Icons.Filled.GraphicEq else Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (isListening) "Listening...".tr(uiAmharic) else "Listen".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Demo trigger button (robot icon) with protected touch target
            if (isDemoMode) {
                IconButton(
                    onClick = onSimulateGlove,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = "Simulate Glove".tr(uiAmharic),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 2. CONVERSATIONS SCREEN (History with Long-Press Selection & Select All)
// ------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // Group messages by Date string
    val groupedMessages = remember(messages) {
        messages.groupBy {
            android.text.format.DateUtils.getRelativeTimeSpanString(
                it.timestamp,
                System.currentTimeMillis(),
                android.text.format.DateUtils.DAY_IN_MILLIS
            ).toString()
        }
    }

    if (selectedDate != null) {
        // Day Detail View with full multi-selection & long-press
        var isSelectionMode by remember { mutableStateOf(false) }
        var selectedMessageIds by remember { mutableStateOf(setOf<Int>()) }
        val dayMessages = groupedMessages[selectedDate] ?: emptyList()

        LaunchedEffect(dayMessages.size) {
            if (dayMessages.isEmpty()) {
                selectedDate = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text(
                                text = "${selectedMessageIds.size} " + "Selected".tr(uiAmharic),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        } else {
                            Column {
                                Text(
                                    text = (selectedDate ?: "").tr(uiAmharic),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "${dayMessages.size} " + "messages".tr(uiAmharic),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel".tr(uiAmharic))
                            }
                        } else {
                            IconButton(onClick = { selectedDate = null }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back".tr(uiAmharic))
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            // Select All / Deselect All
                            IconButton(onClick = {
                                if (selectedMessageIds.size == dayMessages.size) {
                                    selectedMessageIds = emptySet()
                                } else {
                                    selectedMessageIds = dayMessages.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    imageVector = if (selectedMessageIds.size == dayMessages.size) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                                    contentDescription = "Select All".tr(uiAmharic),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Delete Selected
                            IconButton(
                                onClick = {
                                    if (selectedMessageIds.isNotEmpty()) {
                                        viewModel.deleteMessages(selectedMessageIds.toList())
                                        selectedMessageIds = emptySet()
                                        isSelectionMode = false
                                    }
                                },
                                enabled = selectedMessageIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete Selected".tr(uiAmharic),
                                    tint = if (selectedMessageIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        } else {
                            // Enter Select Mode
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Outlined.Checklist, contentDescription = "Select".tr(uiAmharic))
                            }

                            // Delete Entire Day
                            IconButton(onClick = {
                                viewModel.deleteMessages(dayMessages.map { it.id })
                                selectedDate = null
                            }) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = "Delete All".tr(uiAmharic),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                reverseLayout = true
            ) {
                items(dayMessages.reversed(), key = { it.id }) { msg ->
                    val isSelected = selectedMessageIds.contains(msg.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = {
                                    selectedMessageIds = if (isSelected) {
                                        selectedMessageIds - msg.id
                                    } else {
                                        selectedMessageIds + msg.id
                                    }
                                    if (selectedMessageIds.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedMessageIds = if (isSelected) {
                                                selectedMessageIds - msg.id
                                            } else {
                                                selectedMessageIds + msg.id
                                            }
                                            if (selectedMessageIds.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            viewModel.speechManager.speak(msg.text)
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedMessageIds = if (isSelected) {
                                            selectedMessageIds - msg.id
                                        } else {
                                            selectedMessageIds + msg.id
                                        }
                                    }
                                )
                        ) {
                            ModernChatBubble(
                                message = msg,
                                uiAmharic = uiAmharic,
                                isSelected = isSelected,
                                onPlayAudio = { viewModel.speechManager.speak(msg.text) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Date Group Overview Screen
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ARCHIVE".tr(uiAmharic),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "History".tr(uiAmharic),
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    },
                    actions = {
                        if (messages.isNotEmpty()) {
                            var showConfirmDialog by remember { mutableStateOf(false) }

                            IconButton(onClick = { showConfirmDialog = true }) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    contentDescription = "Clear All".tr(uiAmharic),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            if (showConfirmDialog) {
                                AlertDialog(
                                    onDismissRequest = { showConfirmDialog = false },
                                    title = { Text("Clear All".tr(uiAmharic)) },
                                    text = { Text("Are you sure you want to delete all saved conversations?".tr(uiAmharic)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            viewModel.deleteAllMessages()
                                            showConfirmDialog = false
                                        }) {
                                            Text("Delete".tr(uiAmharic), color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showConfirmDialog = false }) {
                                            Text("Cancel".tr(uiAmharic))
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
            ) {
                if (groupedMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.HistoryEdu,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No conversations yet.".tr(uiAmharic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                groupedMessages.forEach { (dateStr, msgs) ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedDate = dateStr },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dateStr.tr(uiAmharic),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val latestSnippet = msgs.lastOrNull()?.text ?: ""
                                        Text(
                                            text = latestSnippet,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${msgs.size}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 3. GLOVE STATUS SCREEN (Centered Battery & Latency, Firmware Updates)
// ------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GloveStatusScreen(viewModel: MainViewModel) {
    val isConnected by viewModel.bluetoothManager.isConnected.collectAsStateWithLifecycle()
    val latency by viewModel.bluetoothManager.latencyMs.collectAsStateWithLifecycle()
    val battery by viewModel.bluetoothManager.batteryLevel.collectAsStateWithLifecycle()
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val isCheckingFirmware by viewModel.isCheckingFirmware.collectAsStateWithLifecycle()
    val firmwareStatusMessage by viewModel.firmwareStatusMessage.collectAsStateWithLifecycle()
    val gloveFirmwareVersion = viewModel.gloveFirmwareVersion

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "LISAN".tr(uiAmharic),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Status".tr(uiAmharic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 60.dp)
        ) {
            // Hero Hardware Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Outlined.BluetoothConnected else Icons.Outlined.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "EthioGlove SPP".tr(uiAmharic),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isConnected) "Connected to HC-05".tr(uiAmharic) else if (isDemoMode) "Demo Simulation Mode".tr(uiAmharic) else "Disconnected".tr(uiAmharic),
                                    fontSize = 13.sp,
                                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isConnected) viewModel.bluetoothManager.disconnect()
                                else viewModel.connectBluetooth()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary,
                                contentColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Disconnect".tr(uiAmharic) else "Connect".tr(uiAmharic),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Centered Battery & Latency Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Battery Card (Centered)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { battery / 100f },
                                modifier = Modifier.size(92.dp),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$battery%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "BATT".tr(uiAmharic),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Battery".tr(uiAmharic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isConnected) "Active (~4.5h)".tr(uiAmharic) else "Standby".tr(uiAmharic),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Latency Card (Centered)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isConnected) "${latency}ms" else "--",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "PING".tr(uiAmharic),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Latency".tr(uiAmharic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isConnected) "Stream Ready".tr(uiAmharic) else "Inactive".tr(uiAmharic),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Glove Firmware & Check for Updates Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Glove Firmware".tr(uiAmharic),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${"Version".tr(uiAmharic)}: $gloveFirmwareVersion",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.checkFirmwareUpdate() },
                            enabled = !isCheckingFirmware,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isCheckingFirmware) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking...".tr(uiAmharic), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    text = "Check Updates".tr(uiAmharic),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    firmwareStatusMessage?.let { status ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${status.tr(uiAmharic)} ($gloveFirmwareVersion)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// 4. SETTINGS SCREEN (Modern Card Groups & Themes Tab)
// ------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    val ttsAmharic by viewModel.translationLanguageAmharic.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isConnected by viewModel.bluetoothManager.isConnected.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val currentTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()

    var selectedSettingsTab by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PREFERENCES".tr(uiAmharic),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Settings".tr(uiAmharic),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 60.dp)
        ) {
            // Settings Tab Header (General vs Themes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedSettingsTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedSettingsTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "General".tr(uiAmharic),
                        fontSize = 14.sp,
                        fontWeight = if (selectedSettingsTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSettingsTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedSettingsTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedSettingsTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Themes".tr(uiAmharic),
                        fontSize = 14.sp,
                        fontWeight = if (selectedSettingsTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSettingsTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedSettingsTab == 1) {
                // THEMES TAB (Palettes without names)
                Text(
                    text = "THEMES & PALETTES".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                Text(
                    text = "Choose your preferred color palette:".tr(uiAmharic),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppThemePalette.values().forEach { palette ->
                        val isSelected = currentTheme == palette
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { viewModel.selectTheme(palette) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Color Palettes (Canvas, Surface, Primary) - NO text name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Swatch 1: Background
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(palette.bg)
                                            .border(1.5.dp, palette.border, CircleShape)
                                    )

                                    // Swatch 2: Surface
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(palette.surface)
                                            .border(1.5.dp, palette.border, CircleShape)
                                    )

                                    // Swatch 3: Primary Accent
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(palette.primary)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Mini UI Accent Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(palette.surfaceVariant)
                                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(palette.primary, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp, 4.dp)
                                                    .background(
                                                        if (palette.isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }

                                // Selection Indicator
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected".tr(uiAmharic),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // GENERAL SETTINGS TAB
                // Group 1: Appearance & UI
                Text(
                    text = "LANGUAGE".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            title = "UI Language".tr(uiAmharic),
                            subtitle = "Switch app interface to Amharic".tr(uiAmharic),
                            checked = uiAmharic,
                            onCheckedChange = { viewModel.setUiLanguage(it) },
                            icon = Icons.Outlined.Translate
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Group 2: Speech & Translation
                Text(
                    text = "TRANSLATION & VOICE".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Translation STT/TTS".tr(uiAmharic), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Select glove output language".tr(uiAmharic), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LanguageSelectorPill(
                                modifier = Modifier.weight(1f),
                                title = "English".tr(uiAmharic),
                                selected = !ttsAmharic,
                                onClick = { viewModel.setTranslationLanguage(false) }
                            )

                            LanguageSelectorPill(
                                modifier = Modifier.weight(1f),
                                title = "Amharic".tr(uiAmharic),
                                selected = ttsAmharic,
                                onClick = { viewModel.setTranslationLanguage(true) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Group 3: Hardware & Demo
                Text(
                    text = "HARDWARE & CONNECTION".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            title = "Demo Mode".tr(uiAmharic),
                            subtitle = "Simulate glove without physical board".tr(uiAmharic),
                            checked = isDemoMode,
                            onCheckedChange = { viewModel.setDemoMode(it) },
                            icon = Icons.Outlined.BugReport
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isConnected) viewModel.bluetoothManager.disconnect()
                                    else viewModel.connectBluetooth()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (isConnected) "Disconnect Glove".tr(uiAmharic) else "Connect Glove".tr(uiAmharic),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isConnected) "HC-05 Paired & Streaming".tr(uiAmharic) else "Tap to scan & connect SPP".tr(uiAmharic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isConnected) Icons.Outlined.Close else Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Group 4: Data Management
                Text(
                    text = "DATA MANAGEMENT".tr(uiAmharic),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showClearConfirm = true }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Clear All".tr(uiAmharic), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text("${messages.size} " + "messages".tr(uiAmharic), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (showClearConfirm) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirm = false },
                        title = { Text("Clear All Conversations".tr(uiAmharic)) },
                        text = { Text("This will permanently delete all conversation records from your local database.".tr(uiAmharic)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.deleteAllMessages()
                                showClearConfirm = false
                            }) {
                                Text("Delete".tr(uiAmharic), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirm = false }) {
                                Text("Cancel".tr(uiAmharic))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun LanguageSelectorPill(
    modifier: Modifier = Modifier,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
