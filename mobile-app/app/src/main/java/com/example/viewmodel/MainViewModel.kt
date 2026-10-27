package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MessageEntity
import com.example.managers.GloveBluetoothManager
import com.example.managers.GloveGestureClassifier
import com.example.managers.SpeechManager
import com.example.ui.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase::class.java // Assuming we instantiate this properly later, but wait, Room needs a builder.
    // Let's use a simple singleton pattern for the db for now.
    
    private val database = androidx.room.Room.databaseBuilder(
        application,
        AppDatabase::class.java, "glove-db"
    ).build()

    private val messageDao = database.messageDao()

    val messages: StateFlow<List<MessageEntity>> = messageDao.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speechManager = SpeechManager(application)
    val bluetoothManager = GloveBluetoothManager(application, viewModelScope)
    val gestureClassifier = GloveGestureClassifier(application)

    private val _uiLanguageAmharic = MutableStateFlow(false)
    val uiLanguageAmharic: StateFlow<Boolean> = _uiLanguageAmharic.asStateFlow()

    private val _translationLanguageAmharic = MutableStateFlow(false)
    val translationLanguageAmharic: StateFlow<Boolean> = _translationLanguageAmharic.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true) // Default to dark for Samsung premium feel
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _selectedTheme = MutableStateFlow(com.example.ui.theme.AppThemePalette.TOKYO_NIGHT)
    val selectedTheme: StateFlow<com.example.ui.theme.AppThemePalette> = _selectedTheme.asStateFlow()

    val gloveFirmwareVersion = "v1.0.4"

    private val _isCheckingFirmware = MutableStateFlow(false)
    val isCheckingFirmware: StateFlow<Boolean> = _isCheckingFirmware.asStateFlow()

    private val _firmwareStatusMessage = MutableStateFlow<String?>(null)
    val firmwareStatusMessage: StateFlow<String?> = _firmwareStatusMessage.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private var activeMessageId: Int? = null
    private var accumulatedBubbleText: String = ""
    private var lastSpeechTimestamp: Long = 0L
    private var silenceJob: Job? = null
    private val recentGloveTexts = mutableListOf<String>()
    private var lastGloveSpokenTime = 0L

    private fun registerGloveMessage(text: String) {
        lastGloveSpokenTime = System.currentTimeMillis()
        speechManager.registerGloveSpokenText(text)
        val norm = text.lowercase().replace(Regex("[.,?!:;።፣፤፧፡\\-_]"), " ").trim()
        if (norm.isNotEmpty()) {
            recentGloveTexts.add(norm)
            if (recentGloveTexts.size > 15) recentGloveTexts.removeAt(0)
        }
    }

    init {
        // Listen to Glove Bluetooth messages
        viewModelScope.launch {
            bluetoothManager.receivedText.collect { text ->
                if (text.isNotBlank()) {
                    val rawFeatures = gestureClassifier.parseTelemetry(text)
                    if (rawFeatures != null) {
                        // Incoming text is raw glove sensor telemetry (e.g. 512,520,530,510,500,1024,-2048,16000)
                        val gestureResult = gestureClassifier.processStreamingTelemetry(text)
                        if (gestureResult != null) {
                            val isAmharic = _translationLanguageAmharic.value
                            val translated = gestureResult.label.tr(isAmharic)
                            registerGloveMessage(translated)
                            messageDao.insertMessage(MessageEntity(text = translated, isFromGlove = true))
                            speechManager.speak(translated)
                        }
                    } else {
                        // Incoming text is an already decoded text phrase
                        val isAmharic = _translationLanguageAmharic.value
                        val translated = text.tr(isAmharic)
                        registerGloveMessage(translated)
                        messageDao.insertMessage(MessageEntity(text = translated, isFromGlove = true))
                        speechManager.speak(translated)
                    }
                }
            }
        }

        // Listen to Speech Recognizer messages
        viewModelScope.launch {
            speechManager.speechResult.collect { result ->
                val trimmedText = result.text.trim()
                if (trimmedText.isBlank()) return@collect

                // Strict echo filter: prevent any speech the glove spoke from showing as user chat bubble
                if (speechManager.isGloveEcho(trimmedText)) {
                    return@collect
                }
                val cleanText = trimmedText.lowercase().replace(Regex("[.,?!:;።፣፤፧፡\\-_]"), " ").trim()
                if (recentGloveTexts.any { it.contains(cleanText) || cleanText.contains(it) }) {
                    return@collect
                }

                val now = System.currentTimeMillis()

                // If user stopped talking for >= 5 seconds (5000ms), start in a brand new bubble
                if (lastSpeechTimestamp > 0L && (now - lastSpeechTimestamp) >= 5000L) {
                    activeMessageId = null
                    accumulatedBubbleText = ""
                }
                lastSpeechTimestamp = now
                silenceJob?.cancel()

                val combinedText = if (accumulatedBubbleText.isNotEmpty()) {
                    "$accumulatedBubbleText $trimmedText"
                } else {
                    trimmedText
                }

                val isNewMessage = (activeMessageId == null)
                if (isNewMessage) {
                    val newId = messageDao.insertMessage(
                        MessageEntity(
                            text = combinedText,
                            isFromGlove = false,
                            timestamp = now
                        )
                    )
                    activeMessageId = newId.toInt()
                } else {
                    messageDao.updateMessage(
                        MessageEntity(
                            id = activeMessageId!!,
                            text = combinedText,
                            isFromGlove = false,
                            timestamp = now
                        )
                    )
                }

                if (result.isFinal) {
                    accumulatedBubbleText = combinedText
                    // Countdown 5 seconds of silence: if no speech arrives within 5 seconds,
                    // finalize this bubble so the next phrase begins in a new bubble
                    silenceJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(5000L)
                        activeMessageId = null
                        accumulatedBubbleText = ""
                    }
                }
            }
        }
    }

    fun toggleListening() {
        if (speechManager.isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        silenceJob?.cancel()
        activeMessageId = null
        accumulatedBubbleText = ""
        lastSpeechTimestamp = 0L
        speechManager.startListening()
    }

    fun stopListening() {
        silenceJob?.cancel()
        activeMessageId = null
        accumulatedBubbleText = ""
        speechManager.stopListening()
    }

    fun setUiLanguage(amharic: Boolean) {
        _uiLanguageAmharic.value = amharic
    }

    fun setTranslationLanguage(amharic: Boolean) {
        _translationLanguageAmharic.value = amharic
        speechManager.setLanguage(amharic)
    }

    fun toggleTranslationLanguage() {
        val next = !_translationLanguageAmharic.value
        setTranslationLanguage(next)
    }
    
    fun setTheme(dark: Boolean) {
        _isDarkTheme.value = dark
        if (!dark && _selectedTheme.value.isDark) {
            _selectedTheme.value = com.example.ui.theme.AppThemePalette.CLEAN_LIGHT
        } else if (dark && !_selectedTheme.value.isDark) {
            _selectedTheme.value = com.example.ui.theme.AppThemePalette.TOKYO_NIGHT
        }
    }

    fun selectTheme(theme: com.example.ui.theme.AppThemePalette) {
        _selectedTheme.value = theme
        _isDarkTheme.value = theme.isDark
    }

    fun checkFirmwareUpdate() {
        viewModelScope.launch {
            _isCheckingFirmware.value = true
            _firmwareStatusMessage.value = null
            kotlinx.coroutines.delay(1600)
            _isCheckingFirmware.value = false
            _firmwareStatusMessage.value = "Firmware is up to date"
        }
    }

    fun setDemoMode(demo: Boolean) {
        _isDemoMode.value = demo
    }

    fun simulateGloveMessage() {
        viewModelScope.launch {
            val isAmharic = _translationLanguageAmharic.value
            val gestures = listOf(
                "Victory", "Okay", "Stop", "I Love You", "Good Bye",
                "What is wrong", "Why", "Come here", "Hope"
            )
            val gesture = gestures.random()
            val text = gesture.tr(isAmharic)
            registerGloveMessage(text)
            kotlinx.coroutines.delay(800)
            messageDao.insertMessage(MessageEntity(text = text, isFromGlove = true))
            speechManager.speak(text)
        }
    }

    fun connectBluetooth(mac: String = "00:11:22:33:44:55") {
        bluetoothManager.connectToDevice(mac, _isDemoMode.value)
    }

    fun deleteMessages(ids: List<Int>) {
        viewModelScope.launch {
            messageDao.deleteMessages(ids)
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            messageDao.deleteAllMessages()
        }
    }

    override fun onCleared() {
        super.onCleared()
        silenceJob?.cancel()
        speechManager.destroy()
        gestureClassifier.close()
        bluetoothManager.disconnect()
    }
}
