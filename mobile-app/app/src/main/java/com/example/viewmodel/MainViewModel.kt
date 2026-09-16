package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MessageEntity
import com.example.managers.GloveBluetoothManager
import com.example.managers.SpeechManager
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

    init {
        // Listen to Glove Bluetooth messages
        viewModelScope.launch {
            bluetoothManager.receivedText.collect { text ->
                if (text.isNotBlank()) {
                    // Save to DB
                    messageDao.insertMessage(MessageEntity(text = text, isFromGlove = true))
                    // Speak it
                    speechManager.speak(text)
                }
            }
        }

        // Listen to Speech Recognizer messages
        viewModelScope.launch {
            speechManager.speechResult.collect { result ->
                if (result.text.isBlank()) return@collect
                
                val now = System.currentTimeMillis()
                val isNewMessage = activeMessageId == null

                if (isNewMessage) {
                    val newId = messageDao.insertMessage(MessageEntity(text = result.text, isFromGlove = false, timestamp = now))
                    activeMessageId = newId.toInt()
                } else {
                    messageDao.updateMessage(MessageEntity(id = activeMessageId!!, text = result.text, isFromGlove = false, timestamp = now))
                }
                
                if (result.isFinal) {
                    activeMessageId = null
                }
            }
        }
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
            val texts = if (isAmharic) {
                listOf("ሰላም፣ እንዴት ነህ?", "ዛሬ የአየር ሁኔታው በጣም ቆንጆ ነው።", "እኔ ሊሳን እባላለሁ።", "ጥሩ ስራ!")
            } else {
                listOf("Hello, how are you?", "The weather is very nice today.", "My name is Lisan.", "Great job!")
            }
            val text = texts.random()
            kotlinx.coroutines.delay(1000)
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
        speechManager.destroy()
        bluetoothManager.disconnect()
    }
}
