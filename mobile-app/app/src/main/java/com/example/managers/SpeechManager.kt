package com.example.managers

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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

private const val TAG = "SpeechManager"

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private data class GloveSpokenRecord(
    val text: String,
    val normalized: String,
    val words: Set<String>,
    val timestamp: Long
)

class SpeechManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _speechResult = MutableStateFlow(SpeechResult("", false))
    val speechResult: StateFlow<SpeechResult> = _speechResult.asStateFlow()

    // Public state reflecting user intent: true when user pressed record, false only when stopped or sustained failure
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Internal session-level state driven by recognizer callbacks (onReadyForSpeech / onEndOfSpeech)
    private val _hasActiveSession = MutableStateFlow(false)
    val hasActiveSession: StateFlow<Boolean> = _hasActiveSession.asStateFlow()

    // Public error state: true only after sustained consecutive errors, resets on successful onReadyForSpeech
    private val _hasRecognitionError = MutableStateFlow(false)
    val hasRecognitionError: StateFlow<Boolean> = _hasRecognitionError.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val streamVolumeBackup = mutableMapOf<Int, Int>()

    // WARNING: Keep ONLY AudioManager.STREAM_SYSTEM here.
    // DO NOT add STREAM_RING or STREAM_NOTIFICATION: muting them switches Android into
    // silent/vibrate mode as a side effect, which triggers a haptic vibration buzz on every mute/restore cycle.
    private val STREAMS_TO_MUTE = intArrayOf(
        AudioManager.STREAM_SYSTEM
    )

    // Playback guard: drops recognizer results arriving during speaker output + reverberation
    @Volatile private var isSpeakingActive = false
    @Volatile private var lastSpeechOutputEndTime = 0L

    private var currentLanguage = "en-US" // or "am-ET"
    private var isExplicitlyRecording = false
    private var busyRetryCount = 0
    private var consecutiveErrorCount = 0
    private val CONSECUTIVE_ERROR_THRESHOLD = 5

    // Filter buffer: tracks recent glove texts spoken aloud
    private val gloveSpokenHistory = mutableListOf<GloveSpokenRecord>()

    private val handler = Handler(Looper.getMainLooper())

    private val restartRunnable = Runnable {
        if (!isExplicitlyRecording) return@Runnable
        startListeningInternal()
    }

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize SpeechRecognizer", e)
        }
    }

    private fun recreateRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer during recreation", e)
        }
        initSpeechRecognizer()
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context, this, "com.google.android.tts")
        } catch (e: Exception) {
            Log.w(TAG, "Google TTS engine unavailable, trying default engine", e)
            textToSpeech = TextToSpeech(context, this)
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeakingActive = true
            }
            override fun onDone(utteranceId: String?) {
                isSpeakingActive = false
                lastSpeechOutputEndTime = System.currentTimeMillis()
            }
            @Deprecated("Deprecated in Java", ReplaceWith("onDone(utteranceId)"))
            override fun onError(utteranceId: String?) {
                isSpeakingActive = false
                lastSpeechOutputEndTime = System.currentTimeMillis()
            }
        })
    }

    fun setLanguage(isAmharic: Boolean) {
        currentLanguage = if (isAmharic) "am-ET" else "en-US"
        configureEngineForLanguage(isAmharic)
        if (isExplicitlyRecording) {
            safeRestartListening(100)
        }
    }

    private fun configureEngineForLanguage(isAmharic: Boolean) {
        val tts = textToSpeech ?: return
        val locale = if (isAmharic) Locale("am", "ET") else Locale.US
        try {
            tts.language = locale
            if (isAmharic) {
                try {
                    val amharicVoices = tts.voices?.filter {
                        it.locale.language.equals("am", ignoreCase = true)
                    }
                    val bestVoice = amharicVoices?.maxByOrNull { it.quality }
                        ?: tts.voices?.firstOrNull { it.locale.language.equals("am", ignoreCase = true) }
                    if (bestVoice != null) {
                        tts.voice = bestVoice
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set custom Amharic voice", e)
                }

                tts.setSpeechRate(1.10f)
                tts.setPitch(1.0f)
            } else {
                tts.setSpeechRate(1.0f)
                tts.setPitch(1.0f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring TTS for locale $locale", e)
            tts.language = locale
        }
    }

    /**
     * Architectural note:
     * Amharic recognition sessions may still restart more frequently than English even after these
     * fixes, because Google's speech recognizer has weaker endpointing/language-model support for
     * Amharic than English, and Amharic almost certainly always requires network (no on-device offline
     * pack exists for it on most phones) while English may run on-device. These fixes make restarts
     * invisible/inaudible to the user (no flicker, no beep) rather than eliminating the restarts
     * themselves — truly eliminating restarts for any language would require replacing SpeechRecognizer
     * with a streaming AudioRecord-based architecture (out of scope here, but flagged for future reference).
     */
    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Silence tolerance extras to prevent early session terminations during normal thinking pauses
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 3000L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 3000L)
        }
    }

    /**
     * Suppress start/stop recognizer chimes across STREAM_SYSTEM.
     */
    private fun muteSystemBeep() {
        val am = audioManager ?: return
        for (stream in STREAMS_TO_MUTE) {
            try {
                if (!streamVolumeBackup.containsKey(stream)) {
                    streamVolumeBackup[stream] = am.getStreamVolume(stream)
                }
                am.setStreamVolume(stream, 0, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mute audio stream $stream", e)
            }
        }
    }

    private fun restoreSystemBeep() {
        handler.postDelayed({
            val am = audioManager
            if (am != null) {
                for ((stream, volume) in streamVolumeBackup) {
                    try {
                        am.setStreamVolume(stream, volume, 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore audio stream $stream to volume $volume", e)
                    }
                }
            }
            streamVolumeBackup.clear()
        }, 200L)
    }

    /**
     * Starts continuous listening like a voice recorder.
     * Stays active until explicitly stopped by stopListening() or sustained errors.
     */
    fun startListening() {
        if (isExplicitlyRecording) return
        isExplicitlyRecording = true
        _isListening.value = true
        _hasRecognitionError.value = false
        busyRetryCount = 0
        consecutiveErrorCount = 0
        safeRestartListening(150)
    }

    private fun startListeningInternal() {
        if (!isExplicitlyRecording) return

        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer cancel error", e)
        }

        handler.postDelayed({
            if (!isExplicitlyRecording) return@postDelayed
            muteSystemBeep()
            try {
                speechRecognizer?.startListening(buildRecognizerIntent())
            } catch (e: Exception) {
                Log.w(TAG, "startListening call failed directly", e)
                handleRecognitionError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            } finally {
                restoreSystemBeep()
            }
        }, 150)
    }

    /**
     * Explicitly stops listening when the user clicks the microphone button again.
     */
    fun stopListening() {
        isExplicitlyRecording = false
        _isListening.value = false
        _hasActiveSession.value = false
        busyRetryCount = 0
        consecutiveErrorCount = 0
        _hasRecognitionError.value = false
        handler.removeCallbacks(restartRunnable)

        // Restore any pending stream volumes immediately
        audioManager?.let { am ->
            for ((stream, volume) in streamVolumeBackup) {
                try {
                    am.setStreamVolume(stream, volume, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore stream $stream on stop", e)
                }
            }
        }
        streamVolumeBackup.clear()

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
    }

    private fun safeRestartListening(delayMs: Long = 150) {
        if (!isExplicitlyRecording) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun handleRecognitionError(error: Int) {
        _hasActiveSession.value = false
        if (!isExplicitlyRecording) {
            _isListening.value = false
            return
        }

        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel recognizer on error failed", e)
        }

        // If errors sustain continuously past threshold, update public error flag and halt
        if (consecutiveErrorCount >= CONSECUTIVE_ERROR_THRESHOLD) {
            Log.w(TAG, "Sustained recognition errors reached threshold ($consecutiveErrorCount), notifying UI")
            _hasRecognitionError.value = true
            // If completely unrecoverable, user intent can be disengaged
            if (consecutiveErrorCount >= 8) {
                isExplicitlyRecording = false
                _isListening.value = false
                return
            }
        }

        val delayMs = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                val backoff = (450L + (busyRetryCount * 350L)).coerceAtMost(2500L)
                busyRetryCount++
                backoff
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                val backoff = (500L + (busyRetryCount * 300L)).coerceAtMost(2500L)
                busyRetryCount++
                if (busyRetryCount > 3) {
                    recreateRecognizer()
                }
                backoff
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // User pause or natural silence; restart calmly without increasing busy retry backoff
                busyRetryCount = 0
                350L
            }
            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                busyRetryCount = 0
                1000L
            }
            else -> {
                busyRetryCount = 0
                450L
            }
        }

        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    /**
     * Clean Ethiopic punctuation markers so the TTS engine produces natural pauses.
     */
    private fun cleanAmharicForTTS(raw: String): String {
        return raw
            .replace("፡", " ")
            .replace("።", ". ")
            .replace("፣", ", ")
            .replace("፤", "; ")
            .replace("፧", "? ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isAmharicContent(text: String): Boolean {
        return text.any { it.code in 0x1200..0x137F } || currentLanguage.startsWith("am")
    }

    private fun normalizeForEchoCheck(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[.,?!:;።፣፤፧፡\\-_'\"`~@]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Register text that the glove is speaking aloud to filter it out from microphone input.
     */
    fun registerGloveSpokenText(text: String) {
        val norm = normalizeForEchoCheck(text)
        if (norm.isBlank()) return
        val words = norm.split(" ").filter { it.length > 1 }.toSet()
        val record = GloveSpokenRecord(text, norm, words, System.currentTimeMillis())
        synchronized(gloveSpokenHistory) {
            gloveSpokenHistory.add(record)
            val cutoff = System.currentTimeMillis() - 30000L
            gloveSpokenHistory.removeAll { it.timestamp < cutoff }
            if (gloveSpokenHistory.size > 20) {
                gloveSpokenHistory.removeAt(0)
            }
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.contains(shorter)) return shorter.length.toFloat() / longer.length.toFloat()

        val pairs1 = s1.windowed(2, 1, false).toSet()
        val pairs2 = s2.windowed(2, 1, false).toSet()
        if (pairs1.isEmpty() || pairs2.isEmpty()) return 0.0f
        val intersection = pairs1.intersect(pairs2).size
        return (2f * intersection) / (pairs1.size + pairs2.size)
    }

    /**
     * Checks if recognized speech matches any text the glove recently spoke aloud,
     * or if speech arrived while the speaker was actively producing audio.
     */
    fun isGloveEcho(text: String): Boolean {
        // Lightweight guard: drop any speech arriving while TTS or MediaPlayer is actively playing audio,
        // or within 600ms of audio finishing (room reverberation / tail echo).
        if (isSpeakingActive || (System.currentTimeMillis() - lastSpeechOutputEndTime < 600L)) {
            return true
        }

        val clean = normalizeForEchoCheck(text)
        if (clean.isBlank()) return true
        val cleanWords = clean.split(" ").filter { it.length > 1 }.toSet()
        val now = System.currentTimeMillis()

        synchronized(gloveSpokenHistory) {
            gloveSpokenHistory.removeAll { now - it.timestamp > 25000L }

            for (record in gloveSpokenHistory) {
                // 1. Exact or substring containment
                if (record.normalized.contains(clean) || clean.contains(record.normalized)) {
                    return true
                }

                // 2. High word overlap
                if (cleanWords.isNotEmpty() && record.words.isNotEmpty()) {
                    val intersection = cleanWords.intersect(record.words)
                    val overlapRatio = intersection.size.toFloat() / cleanWords.size.toFloat()
                    if (overlapRatio >= 0.5f) {
                        return true
                    }
                }

                // 3. String similarity
                if (calculateSimilarity(clean, record.normalized) >= 0.65f) {
                    return true
                }
            }
        }
        return false
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        // Register spoken text for echo filtering and mark active playback
        isSpeakingActive = true
        registerGloveSpokenText(text)

        val isAmharic = isAmharicContent(text)
        val processedText = if (isAmharic) cleanAmharicForTTS(text) else text

        if (isAmharic) {
            scope.launch {
                val audioFile = fetchNeuralAudio(processedText, "am")
                if (audioFile != null) {
                    playAudioFile(audioFile, isAmharic = true)
                } else {
                    speakLocal(processedText, isAmharic = true)
                }
            }
        } else {
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
            Log.w(TAG, "Error fetching neural audio", e)
            null
        }
    }

    private fun playAudioFile(file: File, isAmharic: Boolean = false) {
        try {
            stopCurrentAudio()

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

            if (isAmharic) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(1.15f)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set playback speed", e)
                }
            }

            mp.setOnCompletionListener {
                stopCurrentAudio()
            }
            mp.setOnErrorListener { _, _, _ ->
                stopCurrentAudio()
                true
            }
            isSpeakingActive = true
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed, falling back to local TTS", e)
            speakLocal(file.nameWithoutExtension, isAmharic)
        }
    }

    private fun stopCurrentAudio() {
        activeMediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaPlayer", e)
            }
            activeMediaPlayer = null
        }
        isSpeakingActive = false
        lastSpeechOutputEndTime = System.currentTimeMillis()
    }

    fun destroy() {
        isExplicitlyRecording = false
        _isListening.value = false
        _hasActiveSession.value = false
        handler.removeCallbacks(restartRunnable)

        audioManager?.let { am ->
            for ((stream, volume) in streamVolumeBackup) {
                try {
                    am.setStreamVolume(stream, volume, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore stream $stream on destroy", e)
                }
            }
        }
        streamVolumeBackup.clear()

        scope.cancel()
        stopCurrentAudio()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        busyRetryCount = 0
        consecutiveErrorCount = 0
        _hasRecognitionError.value = false
        _hasActiveSession.value = true
    }

    override fun onBeginningOfSpeech() {
        _hasActiveSession.value = true
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _hasActiveSession.value = false
    }

    override fun onError(error: Int) {
        _hasActiveSession.value = false
        consecutiveErrorCount++
        handleRecognitionError(error)
    }

    override fun onResults(results: Bundle?) {
        busyRetryCount = 0
        consecutiveErrorCount = 0
        _hasRecognitionError.value = false
        _hasActiveSession.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].trim()
            if (text.isNotEmpty() && !isGloveEcho(text)) {
                _speechResult.value = SpeechResult(text, true)
            }
        }
        if (isExplicitlyRecording) {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Cancel recognizer on results failed", e)
            }
            safeRestartListening(150)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].trim()
            if (text.isNotEmpty() && !isGloveEcho(text)) {
                _speechResult.value = SpeechResult(text, false)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupUtteranceListener()
            configureEngineForLanguage(currentLanguage.startsWith("am"))
        } else {
            try {
                textToSpeech = TextToSpeech(context) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        setupUtteranceListener()
                        configureEngineForLanguage(currentLanguage.startsWith("am"))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback TextToSpeech initialization failed", e)
            }
        }
    }
}
