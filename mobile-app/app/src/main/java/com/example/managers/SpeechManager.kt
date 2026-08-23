package com.example.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class SpeechManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val _speechResult = MutableStateFlow(SpeechResult("", false))
    val speechResult: StateFlow<SpeechResult> = _speechResult.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var currentLanguage = "en-US" // or "am-ET"

    private var isExplicitlyStopped = true
    private var isMutedForTTS = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(this)
        textToSpeech = TextToSpeech(context, this)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!isExplicitlyStopped) {
                    isMutedForTTS = true
                    handler.post { speechRecognizer?.stopListening() }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (isMutedForTTS) {
                    isMutedForTTS = false
                    if (!isExplicitlyStopped) {
                        handler.post { startListeningInternal() }
                    }
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })
    }

    fun setLanguage(isAmharic: Boolean) {
        currentLanguage = if (isAmharic) "am-ET" else "en-US"
        textToSpeech?.language = Locale.forLanguageTag(currentLanguage)
    }

    fun startListening() {
        if (!isExplicitlyStopped && _isListening.value) return // Already running securely
        isExplicitlyStopped = false
        _isListening.value = true
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (isMutedForTTS) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isExplicitlyStopped = true
        isMutedForTTS = false
        _isListening.value = false
        speechRecognizer?.stopListening()
    }

    fun speak(text: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "glove_output")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "glove_output")
    }

    fun destroy() {
        isExplicitlyStopped = true
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    
    override fun onEndOfSpeech() { 
        // We let onResults or onError handle the restart to avoid premature stopping
    }
    
    override fun onError(error: Int) {
        if (isExplicitlyStopped || isMutedForTTS) {
            if (isExplicitlyStopped) _isListening.value = false
            return
        }
        
        // Minor delay to prevent aggressive looping on continuous errors like NO_MATCH
        if (error != SpeechRecognizer.ERROR_CLIENT) {
            handler.postDelayed({
                if (!isExplicitlyStopped && !isMutedForTTS) startListeningInternal()
            }, 50)
        } else {
            _isListening.value = false
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _speechResult.value = SpeechResult(matches[0], true)
        }
        // Continuous dictation: restart with minor delay
        if (!isExplicitlyStopped && !isMutedForTTS) {
            handler.postDelayed({
                if (!isExplicitlyStopped && !isMutedForTTS) startListeningInternal()
            }, 50)
        } else if (isExplicitlyStopped) {
            _isListening.value = false
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _speechResult.value = SpeechResult(matches[0], false)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.forLanguageTag(currentLanguage)
        }
    }
}
