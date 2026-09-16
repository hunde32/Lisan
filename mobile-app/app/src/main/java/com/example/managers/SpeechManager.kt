package com.example.managers

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class SpeechManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _speechResult = MutableStateFlow(SpeechResult("", false))
    val speechResult: StateFlow<SpeechResult> = _speechResult.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var currentLanguage = "en-US" // or "am-ET"
    private var isExplicitlyStopped = true
    private var isMutedForTTS = false
    private val handler = Handler(Looper.getMainLooper())

    private var useNeuralVoice: Boolean = true
    private var speechRate: Float = 0.88f
    private var speechPitch: Float = 1.0f

    fun setUseNeuralVoice(enabled: Boolean) {
        useNeuralVoice = enabled
    }

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(this)
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        // Try Google TTS engine first for superior Amharic support
        try {
            textToSpeech = TextToSpeech(context, this, "com.google.android.tts")
        } catch (e: Exception) {
            textToSpeech = TextToSpeech(context, this)
        }
    }

    private fun setupUtteranceListener() {
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

            @Deprecated("Deprecated in Java", ReplaceWith("onDone(utteranceId)"))
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })
    }

    fun setLanguage(isAmharic: Boolean) {
        currentLanguage = if (isAmharic) "am-ET" else "en-US"
        configureEngineForLanguage(isAmharic)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        textToSpeech?.setSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch
        textToSpeech?.setPitch(pitch)
    }

    private fun configureEngineForLanguage(isAmharic: Boolean) {
        val tts = textToSpeech ?: return
        val locale = if (isAmharic) Locale("am", "ET") else Locale.US
        try {
            tts.language = locale
            if (isAmharic) {
                // Find highest quality Amharic voice in engine
                try {
                    val amharicVoices = tts.voices?.filter {
                        it.locale.language.equals("am", ignoreCase = true)
                    }
                    val bestVoice = amharicVoices?.maxByOrNull { it.quality }
                        ?: tts.voices?.firstOrNull { it.locale.language.equals("am", ignoreCase = true) }
                    if (bestVoice != null) {
                        tts.voice = bestVoice
                    }
                } catch (ignored: Exception) {}

                // Brisk, natural pace for Amharic
                tts.setSpeechRate(1.10f)
                tts.setPitch(1.0f)
            } else {
                // English version remains exactly standard (untouched)
                tts.setSpeechRate(1.0f)
                tts.setPitch(1.0f)
            }
        } catch (e: Exception) {
            tts.language = locale
        }
    }

    fun startListening() {
        if (!isExplicitlyStopped && _isListening.value) return
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

    /**
     * Clean Ethiopic punctuation markers so the TTS engine produces natural pauses
     * rather than awkward stutters or trying to pronounce Ethiopic separator symbols.
     */
    private fun cleanAmharicForTTS(raw: String): String {
        return raw
            .replace("፡", " ")        // Ethiopic wordspace -> space
            .replace("።", ". ")       // Ethiopic full stop -> pause
            .replace("፣", ", ")       // Ethiopic comma -> pause
            .replace("፤", "; ")       // Ethiopic semicolon -> pause
            .replace("፧", "? ")       // Ethiopic question -> question
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isAmharicContent(text: String): Boolean {
        return text.any { it.code in 0x1200..0x137F } || currentLanguage.startsWith("am")
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val isAmharic = isAmharicContent(text)
        val processedText = if (isAmharic) cleanAmharicForTTS(text) else text

        if (isAmharic) {
            scope.launch {
                val audioFile = fetchNeuralAudio(processedText, "am")
                if (audioFile != null) {
                    playAudioFile(audioFile, isAmharic = true)
                } else {
                    // Seamless fallback to local optimized engine
                    speakLocal(processedText, isAmharic = true)
                }
            }
        } else {
            // English version remains exactly untouched as requested
            speakLocal(processedText, isAmharic = false)
        }
    }

    private fun speakLocal(text: String, isAmharic: Boolean) {
        val tts = textToSpeech ?: return
        configureEngineForLanguage(isAmharic)
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "glove_output")
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "glove_output")
    }

    private suspend fun fetchNeuralAudio(text: String, lang: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "tts_cache").apply { mkdirs() }
            val hash = (text + lang).hashCode().toString()
            val cachedFile = File(cacheDir, "tts_$hash.mp3")
            if (cachedFile.exists() && cachedFile.length() > 500) {
                return@withContext cachedFile
            }

            val encoded = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$lang&client=tw-ob&q=$encoded"
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
                )
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    cachedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (cachedFile.length() > 500) {
                    return@withContext cachedFile
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun playAudioFile(file: File, isAmharic: Boolean = false) {
        try {
            stopCurrentAudio()
            isMutedForTTS = true
            if (!isExplicitlyStopped) {
                speechRecognizer?.stopListening()
            }

            val mp = MediaPlayer()
            activeMediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()

            // For Amharic, adjust playback pace to 1.15x for brisk, natural conversation
            if (isAmharic) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(1.15f)
                } catch (ignored: Exception) {}
            }

            mp.setOnCompletionListener {
                it.release()
                if (activeMediaPlayer == it) activeMediaPlayer = null
                isMutedForTTS = false
                if (!isExplicitlyStopped) {
                    handler.post { startListeningInternal() }
                }
            }
            mp.setOnErrorListener { it, _, _ ->
                it.release()
                if (activeMediaPlayer == it) activeMediaPlayer = null
                isMutedForTTS = false
                if (!isExplicitlyStopped) {
                    handler.post { startListeningInternal() }
                }
                true
            }
            mp.start()
        } catch (e: Exception) {
            isMutedForTTS = false
            // Fall back to local TTS if player error occurs
            speakLocal(file.nameWithoutExtension, isAmharic)
        }
    }

    private fun stopCurrentAudio() {
        activeMediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (ignored: Exception) {}
            activeMediaPlayer = null
        }
    }

    fun destroy() {
        isExplicitlyStopped = true
        scope.cancel()
        stopCurrentAudio()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        if (isExplicitlyStopped || isMutedForTTS) {
            if (isExplicitlyStopped) _isListening.value = false
            return
        }

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
            setupUtteranceListener()
            configureEngineForLanguage(currentLanguage.startsWith("am"))
        } else {
            // Re-attempt fallback to default engine
            try {
                textToSpeech = TextToSpeech(context) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        setupUtteranceListener()
                        configureEngineForLanguage(currentLanguage.startsWith("am"))
                    }
                }
            } catch (ignored: Exception) {}
        }
    }
}
