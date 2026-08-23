package com.example.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MainViewModel
import com.example.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTranslatorScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text("LISAN".tr(uiAmharic), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Translator".tr(uiAmharic), fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1).sp)
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ACTIVE".tr(uiAmharic), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                        reverseLayout = true
                    ) {
                        items(messages.reversed()) { msg ->
                            ChatBubble(
                                text = msg.text,
                                isFromGlove = msg.isFromGlove,
                                uiAmharic = uiAmharic
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    // Bottom Controls inside the chat surface
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                if (isListening) viewModel.speechManager.stopListening()
                                else {
                                    viewModel.speechManager.startListening()
                                    if (isDemoMode) {
                                        viewModel.simulateGloveMessage()
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(if (isListening) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isListening) "STT LISTENING...".tr(uiAmharic) else "TAP TO LISTEN".tr(uiAmharic),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isListening) 0.7f else 0.3f),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.width(1.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "ENG",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun ChatBubble(text: String, isFromGlove: Boolean, uiAmharic: Boolean = false) {
    val alignment = if (isFromGlove) Alignment.CenterStart else Alignment.CenterEnd
    val bgColor = if (isFromGlove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isFromGlove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (isFromGlove) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
    }
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isFromGlove) Alignment.Start else Alignment.End) {
            Text(
                text = if (isFromGlove) "GLOVE INPUT (AMHARIC)".tr(uiAmharic) else "VOICE OUTPUT (ENGLISH)".tr(uiAmharic),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Surface(
                color = bgColor,
                shape = shape,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = text,
                    color = textColor,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        // Conversation Detail View
        var isSelectionMode by remember { mutableStateOf(false) }
        var selectedMessageIds by remember { mutableStateOf(setOf<Int>()) }
        val dayMessages = groupedMessages[selectedDate] ?: emptyList()
        
        // Auto exit if deleted everything
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
                            Text("${selectedMessageIds.size} Selected")
                        } else {
                            Text(selectedDate ?: "", fontWeight = FontWeight.Bold) 
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { 
                                isSelectionMode = false 
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel")
                            }
                        } else {
                            IconButton(onClick = { selectedDate = null }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { 
                                if (selectedMessageIds.size == dayMessages.size) {
                                    selectedMessageIds = emptySet()
                                } else {
                                    selectedMessageIds = dayMessages.map { it.id }.toSet()
                                }
                            }) {
                                Icon(Icons.Outlined.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(onClick = { 
                                if (selectedMessageIds.isNotEmpty()) {
                                    viewModel.deleteMessages(selectedMessageIds.toList())
                                    selectedMessageIds = emptySet()
                                    isSelectionMode = false
                                }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Outlined.Checklist, contentDescription = "Select")
                            }
                            IconButton(onClick = { 
                                viewModel.deleteMessages(dayMessages.map { it.id })
                                selectedDate = null
                            }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true
            ) {
                items(dayMessages.reversed()) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = isSelectionMode) {
                            if (isSelectionMode) {
                                selectedMessageIds = if (selectedMessageIds.contains(msg.id)) {
                                    selectedMessageIds - msg.id
                                } else {
                                    selectedMessageIds + msg.id
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(visible = isSelectionMode) {
                            Checkbox(
                                checked = selectedMessageIds.contains(msg.id),
                                onCheckedChange = { checked ->
                                    selectedMessageIds = if (checked) {
                                        selectedMessageIds + msg.id
                                    } else {
                                        selectedMessageIds - msg.id
                                    }
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ChatBubble(
                                text = msg.text,
                                isFromGlove = msg.isFromGlove,
                                uiAmharic = uiAmharic
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    } else {
        // List of Dates
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("History".tr(uiAmharic), fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (groupedMessages.isEmpty()) {
                    item {
                        Text("No conversations yet.".tr(uiAmharic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                groupedMessages.forEach { (dateStr, msgs) ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { selectedDate = dateStr },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(text = dateStr, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${msgs.size} " + "messages".tr(uiAmharic), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GloveStatusScreen(viewModel: MainViewModel) {
    val isConnected by viewModel.bluetoothManager.isConnected.collectAsStateWithLifecycle()
    val latency by viewModel.bluetoothManager.latencyMs.collectAsStateWithLifecycle()
    val battery by viewModel.bluetoothManager.batteryLevel.collectAsStateWithLifecycle()
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Status".tr(uiAmharic), fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 100.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Big Battery Circular Indicator
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(
                    progress = battery / 100f,
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 12.dp,
                    color = Color(0xFF22C55E),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$battery%", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("Battery".tr(uiAmharic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Stats Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Latency".tr(uiAmharic),
                    value = "${latency}ms",
                    icon = Icons.Outlined.Speed
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Signal".tr(uiAmharic),
                    value = if (isConnected) "Strong".tr(uiAmharic) else "None".tr(uiAmharic),
                    icon = Icons.Outlined.Bluetooth
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Firmware Version".tr(uiAmharic),
                value = "v2.1.4 (Up to date)".tr(uiAmharic),
                icon = Icons.Outlined.Memory
            )
        }
    }
}

@Composable
fun StatusCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiAmharic by viewModel.uiLanguageAmharic.collectAsStateWithLifecycle()
    val ttsAmharic by viewModel.translationLanguageAmharic.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isConnected by viewModel.bluetoothManager.isConnected.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings".tr(uiAmharic), fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) },
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 100.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingToggleCard(
                title = "Dark Theme".tr(uiAmharic),
                subtitle = "Use high-contrast dark mode".tr(uiAmharic),
                checked = isDarkTheme,
                onCheckedChange = { viewModel.setTheme(it) },
                icon = Icons.Outlined.DarkMode
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            SettingToggleCard(
                title = "UI Language".tr(uiAmharic),
                subtitle = "Switch app interface to Amharic".tr(uiAmharic),
                checked = uiAmharic,
                onCheckedChange = { viewModel.setUiLanguage(it) },
                icon = Icons.Outlined.Translate
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Translation STT/TTS".tr(uiAmharic), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Select glove output language".tr(uiAmharic), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setTranslationLanguage(false) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !ttsAmharic, onClick = { viewModel.setTranslationLanguage(false) })
                        Text("English".tr(uiAmharic))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setTranslationLanguage(true) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = ttsAmharic, onClick = { viewModel.setTranslationLanguage(true) })
                        Text("Amharic".tr(uiAmharic))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingToggleCard(
                title = "Demo Mode".tr(uiAmharic),
                subtitle = "Simulate glove when listening".tr(uiAmharic),
                checked = isDemoMode,
                onCheckedChange = { viewModel.setDemoMode(it) },
                icon = Icons.Outlined.BugReport
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (isConnected) viewModel.bluetoothManager.disconnect()
                    else viewModel.connectBluetooth()
                },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(if (isConnected) "Disconnect Glove".tr(uiAmharic) else "Connect Glove".tr(uiAmharic), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isConnected) "Connected to HC-05".tr(uiAmharic) else "Tap to connect".tr(uiAmharic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}
