package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.BatteryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "AuraVoiceEngine"

enum class VoiceListeningMode {
    IDLE,
    WAKE_STANDBY,
    ACTIVE_COMMAND
}

data class VoiceEngineState(
    val mode: VoiceListeningMode = VoiceListeningMode.IDLE,
    val partialTranscript: String = "",
    val rmsLevel: Float = 0f,
    val speaking: Boolean = false,
    val speechAvailable: Boolean = true,
    val statusNotice: String? = null
)

class VoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRate = 1.05f

    private var wakePhrase: String = "hey aura"
    private var preferOffline: Boolean = true
    private var batteryProfile: BatteryProfile = BatteryProfile.ECO
    private var standbyRequested: Boolean = false
    private var backgroundServiceOwnsStandby: Boolean = false

    private var onCommandRecognized: ((String) -> Unit)? = null
    private var onWakeDetected: (() -> Unit)? = null

    private val _state = MutableStateFlow(
        VoiceEngineState(speechAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(speechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }
                })
            }
        }
    }

    fun configure(
        wakeWord: String,
        offlinePreferred: Boolean,
        profile: BatteryProfile,
        rate: Float,
        onCommand: (String) -> Unit,
        onWake: () -> Unit
    ) {
        wakePhrase = wakeWord.trim().lowercase(Locale.US).ifBlank { "hey aura" }
        preferOffline = offlinePreferred
        batteryProfile = profile
        speechRate = rate.coerceIn(0.5f, 2.0f)
        if (ttsReady) {
            tts?.setSpeechRate(speechRate)
        }
        onCommandRecognized = onCommand
        onWakeDetected = onWake
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !ttsReady) return
        val id = UUID.randomUUID().toString()
        tts?.setSpeechRate(speechRate)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(speaking = false)
    }

    fun setBackgroundServiceOwnsStandby(owns: Boolean) {
        backgroundServiceOwnsStandby = owns
        if (owns && _state.value.mode == VoiceListeningMode.WAKE_STANDBY) {
            stopListening()
        } else if (!owns && standbyRequested && _state.value.mode == VoiceListeningMode.IDLE) {
            startListeningInternal(VoiceListeningMode.WAKE_STANDBY)
        }
    }

    fun setWakeStandby(enabled: Boolean) {
        standbyRequested = enabled
        mainHandler.removeCallbacksAndMessages(null)
        if (backgroundServiceOwnsStandby) {
            if (_state.value.mode == VoiceListeningMode.WAKE_STANDBY) {
                stopListening()
            }
            return
        }
        if (enabled) {
            if (_state.value.mode == VoiceListeningMode.IDLE) {
                startListeningInternal(VoiceListeningMode.WAKE_STANDBY)
            }
        } else {
            if (_state.value.mode == VoiceListeningMode.WAKE_STANDBY) {
                stopListening()
            }
        }
    }

    fun startActiveListening() {
        mainHandler.removeCallbacksAndMessages(null)
        stopSpeaking()
        if (backgroundServiceOwnsStandby) {
            WakeWordForegroundService.pauseMicForForeground(context.applicationContext)
        }
        startListeningInternal(VoiceListeningMode.ACTIVE_COMMAND)
    }

    fun stopListening() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Stop listening warning: ${e.message}")
            }
            _state.value = _state.value.copy(
                mode = VoiceListeningMode.IDLE,
                partialTranscript = "",
                rmsLevel = 0f
            )
            if (backgroundServiceOwnsStandby) {
                WakeWordForegroundService.resumeMicAfterForeground(context.applicationContext)
            }
        }
    }

    private fun startListeningInternal(targetMode: VoiceListeningMode) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                mode = VoiceListeningMode.IDLE,
                speechAvailable = false,
                statusNotice = "On-device speech service is unavailable on this device."
            )
            return
        }

        mainHandler.post {
            try {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                }
                recognizer?.cancel()
                recognizer?.setRecognitionListener(createListener(targetMode))

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                }

                _state.value = _state.value.copy(
                    mode = targetMode,
                    partialTranscript = "",
                    rmsLevel = 0f,
                    statusNotice = null
                )
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _state.value = _state.value.copy(
                    mode = VoiceListeningMode.IDLE,
                    statusNotice = "Microphone listener could not start. Check permissions."
                )
            }
        }
    }

    private fun createListener(activeMode: VoiceListeningMode): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.value = _state.value.copy(rmsLevel = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) return
                _state.value = _state.value.copy(partialTranscript = text)

                if (activeMode == VoiceListeningMode.WAKE_STANDBY && matchesWakeWord(text)) {
                    val trailing = extractAfterWakeWord(text)
                    onWakeDetected?.invoke()
                    if (trailing.length >= 4) {
                        stopListening()
                        onCommandRecognized?.invoke(trailing)
                        scheduleStandbyResumeIfNeeded()
                    } else {
                        startActiveListening()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (activeMode == VoiceListeningMode.WAKE_STANDBY) {
                    if (best.isNotEmpty() && matchesWakeWord(best)) {
                        val trailing = extractAfterWakeWord(best)
                        onWakeDetected?.invoke()
                        if (trailing.isNotBlank()) {
                            onCommandRecognized?.invoke(trailing)
                            scheduleStandbyResumeIfNeeded()
                        } else {
                            startActiveListening()
                        }
                    } else {
                        scheduleStandbyResumeIfNeeded()
                    }
                } else {
                    _state.value = _state.value.copy(
                        mode = VoiceListeningMode.IDLE,
                        partialTranscript = "",
                        rmsLevel = 0f
                    )
                    if (best.isNotBlank()) {
                        onCommandRecognized?.invoke(best)
                    }
                    scheduleStandbyResumeIfNeeded()
                }
            }

            override fun onError(error: Int) {
                if (activeMode == VoiceListeningMode.WAKE_STANDBY && standbyRequested) {
                    scheduleStandbyResumeIfNeeded(extraBackoffMs = 600L)
                } else {
                    val hint = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio capture error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                        else -> "Voice input paused (code $error)."
                    }
                    _state.value = _state.value.copy(
                        mode = VoiceListeningMode.IDLE,
                        partialTranscript = "",
                        rmsLevel = 0f,
                        statusNotice = hint
                    )
                    scheduleStandbyResumeIfNeeded()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    private fun scheduleStandbyResumeIfNeeded(extraBackoffMs: Long = 0L) {
        if (backgroundServiceOwnsStandby) {
            WakeWordForegroundService.resumeMicAfterForeground(context.applicationContext)
            return
        }
        if (!standbyRequested) return
        val delay = batteryProfile.pollDelayMs + extraBackoffMs
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (standbyRequested && !backgroundServiceOwnsStandby && _state.value.mode != VoiceListeningMode.ACTIVE_COMMAND) {
                startListeningInternal(VoiceListeningMode.WAKE_STANDBY)
            }
        }, delay)
    }

    private fun matchesWakeWord(transcript: String): Boolean =
        VoiceWakeMatcher.matches(transcript, wakePhrase)

    private fun extractAfterWakeWord(transcript: String): String =
        VoiceWakeMatcher.extractCommandAfterWake(transcript, wakePhrase)

    fun release() {
        standbyRequested = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
