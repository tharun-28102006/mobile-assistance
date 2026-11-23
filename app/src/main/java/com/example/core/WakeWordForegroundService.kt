package com.example.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.BatteryProfile
import com.example.data.repository.GeminiAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "WakeWordService"

/**
 * Shared matcher supporting exact phrases, multi-word token matches, and
 * 1-edit fuzzy matching for custom wake words (e.g. "Hey Aura", "Jarvis", "Nova").
 */
object VoiceWakeMatcher {

    fun matches(transcript: String, wakeWord: String): Boolean {
        val spoken = normalize(transcript)
        val target = normalize(wakeWord)
        if (spoken.isEmpty() || target.isEmpty()) return false
        if (spoken.contains(target)) return true

        val targetTokens = target.split(" ").filter { it.isNotBlank() }
        val spokenTokens = spoken.split(" ").filter { it.isNotBlank() }
        if (targetTokens.isEmpty() || spokenTokens.isEmpty()) return false

        return targetTokens.all { t ->
            spokenTokens.any { s ->
                s == t || (t.length >= 4 && abs(s.length - t.length) <= 1 && levenshtein(s, t) <= 1)
            }
        }
    }

    fun extractCommandAfterWake(transcript: String, wakeWord: String): String {
        val raw = transcript.trim()
        val lower = raw.lowercase(Locale.US)
        val target = normalize(wakeWord)
        if (target.isEmpty()) return ""

        val directIdx = lower.indexOf(target)
        if (directIdx >= 0) {
            return raw.substring((directIdx + target.length).coerceAtMost(raw.length))
                .trimStart(',', '.', ':', '-', ' ')
                .trim()
        }

        val targetTokens = target.split(" ").filter { it.isNotBlank() }
        val lastToken = targetTokens.lastOrNull() ?: return ""
        val rawWords = raw.split(Regex("\\s+"))
        val matchIdx = rawWords.indexOfLast { w ->
            val nw = normalize(w)
            nw == lastToken || (lastToken.length >= 4 && levenshtein(nw, lastToken) <= 1)
        }
        return if (matchIdx >= 0 && matchIdx + 1 < rawWords.size) {
            rawWords.subList(matchIdx + 1, rawWords.size).joinToString(" ").trim()
        } else {
            ""
        }
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        return prev[b.length]
    }
}

/**
 * In-process coordination bus between WakeWordForegroundService and AuraViewModel / MainActivity.
 */
object VoiceCommandBus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _serviceMode = MutableStateFlow(VoiceListeningMode.IDLE)
    val serviceMode: StateFlow<VoiceListeningMode> = _serviceMode.asStateFlow()

    private val _serviceStatus = MutableStateFlow("Lock-screen wake standby off")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    private val _wakePulse = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val wakePulse: SharedFlow<Long> = _wakePulse.asSharedFlow()

    private val _recognizedCommands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val recognizedCommands: SharedFlow<String> = _recognizedCommands.asSharedFlow()

    val hasActiveUiCollector = AtomicBoolean(false)

    internal fun setRunning(running: Boolean, status: String) {
        _serviceRunning.value = running
        _serviceStatus.value = status
        if (!running) _serviceMode.value = VoiceListeningMode.IDLE
    }

    internal fun setMode(mode: VoiceListeningMode, status: String) {
        _serviceMode.value = mode
        _serviceStatus.value = status
    }

    internal fun emitWakePulse() {
        _wakePulse.tryEmit(System.currentTimeMillis())
    }

    internal fun emitCommand(command: String) {
        _recognizedCommands.tryEmit(command)
    }
}

/**
 * Microphone Foreground Service that listens continuously for the user's custom wake word
 * even when the device is locked or the app is in the background, then captures and executes
 * the spoken command automatically.
 */
class WakeWordForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefsStore: LocalPreferencesStore
    private lateinit var actionExecutor: AndroidActionExecutor
    private lateinit var telemetryHelper: DeviceTelemetryHelper
    private val aiRepository = GeminiAiRepository()

    @Volatile private var wakePhrase: String = "Hey Aura"
    @Volatile private var preferOffline: Boolean = true
    @Volatile private var autoReadReplies: Boolean = true
    @Volatile private var batteryProfile: BatteryProfile = BatteryProfile.ECO
    @Volatile private var ttsRate: Float = 1.05f
    @Volatile private var micYieldedToForeground: Boolean = false
    @Volatile private var currentMode: VoiceListeningMode = VoiceListeningMode.IDLE

    override fun onCreate() {
        super.onCreate()
        prefsStore = LocalPreferencesStore(applicationContext)
        actionExecutor = AndroidActionExecutor(applicationContext)
        telemetryHelper = DeviceTelemetryHelper(applicationContext)
        createNotificationChannel()
        initFallbackTts()

        serviceScope.launch {
            prefsStore.prefsFlow.collect { prefs ->
                wakePhrase = prefs.wakeWord.trim().ifBlank { "Hey Aura" }
                preferOffline = prefs.offlineVoicePreferred
                autoReadReplies = prefs.autoReadReplies
                batteryProfile = BatteryProfile.fromRaw(prefs.batteryMode)
                ttsRate = prefs.ttsSpeechRate
                if (VoiceCommandBus.serviceRunning.value) {
                    updateNotification("Listening on lock screen for \"$wakePhrase\"")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch { prefsStore.setLockScreenWake(false) }
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_MIC -> {
                micYieldedToForeground = true
                stopRecognizerOnMain()
                return START_STICKY
            }
            ACTION_RESUME_MIC -> {
                micYieldedToForeground = false
                scheduleStandbyCycle(delayMs = 500L)
                return START_STICKY
            }
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            VoiceCommandBus.setRunning(false, "Microphone permission required for lock-screen wake")
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_WAKE_WORD)?.takeIf { it.isNotBlank() }?.let {
            wakePhrase = it.trim()
        }

        val notification = buildNotification("Listening on lock screen for \"$wakePhrase\"")
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground microphone service", e)
            VoiceCommandBus.setRunning(false, "Could not start lock-screen mic service")
            stopSelf()
            return START_NOT_STICKY
        }

        acquirePartialWakeLock()
        micYieldedToForeground = false
        VoiceCommandBus.setRunning(true, "Listening on lock screen for \"$wakePhrase\"")
        startListeningOnMain(VoiceListeningMode.WAKE_STANDBY)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecognizerOnMain()
        releasePartialWakeLock()
        try {
            fallbackTts?.stop()
            fallbackTts?.shutdown()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        VoiceCommandBus.setRunning(false, "Lock-screen wake standby off")
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        stopRecognizerOnMain()
        releasePartialWakeLock()
        VoiceCommandBus.setRunning(false, "Lock-screen wake standby off")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startListeningOnMain(mode: VoiceListeningMode) {
        if (micYieldedToForeground) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            VoiceCommandBus.setMode(VoiceListeningMode.IDLE, "On-device speech service unavailable")
            return
        }
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            if (micYieldedToForeground) return@post
            try {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                }
                recognizer?.cancel()
                recognizer?.setRecognitionListener(createRecognitionListener(mode))

                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                    if (mode == VoiceListeningMode.ACTIVE_COMMAND) {
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                    }
                }
                currentMode = mode
                val statusText = if (mode == VoiceListeningMode.ACTIVE_COMMAND) {
                    "Wake word heard! Listening for your command..."
                } else {
                    "Listening on lock screen for \"$wakePhrase\""
                }
                VoiceCommandBus.setMode(mode, statusText)
                updateNotification(statusText)
                recognizer?.startListening(speechIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Recognizer start error: ${e.message}")
                scheduleStandbyCycle(delayMs = batteryProfile.pollDelayMs + 800L)
            }
        }
    }

    private fun stopRecognizerOnMain() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
            } catch (_: Exception) {
            }
            currentMode = VoiceListeningMode.IDLE
        }
    }

    private fun scheduleStandbyCycle(delayMs: Long) {
        if (micYieldedToForeground) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!micYieldedToForeground && VoiceCommandBus.serviceRunning.value) {
                startListeningOnMain(VoiceListeningMode.WAKE_STANDBY)
            }
        }, delayMs)
    }

    private fun createRecognitionListener(mode: VoiceListeningMode): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if (mode != VoiceListeningMode.WAKE_STANDBY) return
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) return

                if (VoiceWakeMatcher.matches(text, wakePhrase)) {
                    val trailing = VoiceWakeMatcher.extractCommandAfterWake(text, wakePhrase)
                    onWakeTriggered(trailingCommand = trailing.takeIf { it.length >= 6 })
                }
            }

            override fun onResults(results: Bundle?) {
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (mode == VoiceListeningMode.WAKE_STANDBY) {
                    if (best.isNotEmpty() && VoiceWakeMatcher.matches(best, wakePhrase)) {
                        val trailing = VoiceWakeMatcher.extractCommandAfterWake(best, wakePhrase)
                        onWakeTriggered(trailingCommand = trailing.ifBlank { null })
                    } else {
                        scheduleStandbyCycle(batteryProfile.pollDelayMs)
                    }
                } else {
                    if (best.isNotBlank()) {
                        dispatchCapturedCommand(best)
                    } else {
                        scheduleStandbyCycle(batteryProfile.pollDelayMs)
                    }
                }
            }

            override fun onError(error: Int) {
                val backoff = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_AUDIO -> batteryProfile.pollDelayMs + 1200L
                    else -> batteryProfile.pollDelayMs + 400L
                }
                if (mode == VoiceListeningMode.ACTIVE_COMMAND) {
                    updateNotification("Listening on lock screen for \"$wakePhrase\"")
                }
                scheduleStandbyCycle(backoff)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    private fun onWakeTriggered(trailingCommand: String?) {
        pulseVibration()
        wakeScreenBriefly()
        VoiceCommandBus.emitWakePulse()

        if (!trailingCommand.isNullOrBlank()) {
            dispatchCapturedCommand(trailingCommand)
        } else {
            // Two-step wake: user said custom wake word alone, now capture their command
            speakBriefly("Listening")
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                startListeningOnMain(VoiceListeningMode.ACTIVE_COMMAND)
            }, 650L)
        }
    }

    private fun dispatchCapturedCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) {
            scheduleStandbyCycle(batteryProfile.pollDelayMs)
            return
        }
        Log.i(TAG, "Lock-screen command captured: $clean")
        updateNotification("Following command: \"$clean\"")
        VoiceCommandBus.setMode(VoiceListeningMode.IDLE, "Following: \"$clean\"")

        if (VoiceCommandBus.hasActiveUiCollector.get()) {
            // AuraViewModel is alive — dispatch directly into full offline + Gemini + Firestore pipeline
            VoiceCommandBus.emitCommand(clean)
            // Resume passive wake standby after giving TTS/action a short breathing window
            scheduleStandbyCycle(delayMs = 4500L)
        } else {
            // Headless background / lock-screen execution fallback so commands work even if UI was killed
            executeHeadlessLockScreenCommand(clean)
        }
    }

    private fun executeHeadlessLockScreenCommand(command: String) {
        serviceScope.launch {
            try {
                val local = actionExecutor.tryHandleLocally(
                    input = command,
                    savedAutomations = emptyList(),
                    smartDevices = emptyList(),
                    locationLabel = "Lock Screen"
                )
                val spokenReply = when (local) {
                    is OfflineCommandOutcome.Handled -> local.replyText
                    OfflineCommandOutcome.RouteToCloudGemini -> {
                        if (!telemetryHelper.isNetworkAvailable()) {
                            "You are offline. I can still run timers, alarms, calls, and device controls locally."
                        } else {
                            var reply = ""
                            aiRepository.streamAssistantReply(
                                prompt = command,
                                locationLabel = "Lock Screen",
                                recentTopics = emptyList(),
                                recentContextHistory = emptyList()
                            ).collect { reply = it }
                            reply.ifBlank { "Done." }
                        }
                    }
                }
                updateNotification(spokenReply.take(120))
                if (autoReadReplies) {
                    speakBriefly(spokenReply)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Headless lock-screen command error", e)
                updateNotification("Could not complete command: ${e.localizedMessage ?: "error"}")
            } finally {
                scheduleStandbyCycle(delayMs = 4500L)
            }
        }
    }

    private fun initFallbackTts() {
        fallbackTts = TextToSpeech(applicationContext) { status ->
            fallbackTtsReady = status == TextToSpeech.SUCCESS
            if (fallbackTtsReady) {
                fallbackTts?.language = Locale.getDefault()
                fallbackTts?.setSpeechRate(ttsRate)
            }
        }
    }

    private fun speakBriefly(text: String) {
        if (!fallbackTtsReady || text.isBlank()) return
        fallbackTts?.setSpeechRate(ttsRate)
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    private fun pulseVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(90L)
                }
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isInteractive) {
                val screenLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AuraAssist::ScreenWakePulse"
                )
                screenLock.acquire(5_000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun acquirePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuraAssist::LockScreenWakeLock").apply {
                setReferenceCounted(false)
                acquire(60L * 60L * 1000L) // max 1h safety cap per acquire, refreshed if restarted
            }
        } catch (e: Exception) {
            Log.w(TAG, "Partial wake lock unavailable: ${e.message}")
        }
    }

    private fun releasePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Lock-Screen Wake Word",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps custom wake-word recognition active on lock screen and in background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Aura Assist • Lock-Screen Wake")
            .setContentText(statusText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateNotification(statusText: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "aura_lock_wake_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.aura.ACTION_START_WAKE_SERVICE"
        const val ACTION_STOP = "com.example.aura.ACTION_STOP_WAKE_SERVICE"
        const val ACTION_PAUSE_MIC = "com.example.aura.ACTION_PAUSE_WAKE_MIC"
        const val ACTION_RESUME_MIC = "com.example.aura.ACTION_RESUME_WAKE_MIC"
        const val EXTRA_WAKE_WORD = "extra_wake_word"

        fun start(context: Context, wakeWord: String) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WAKE_WORD, wakeWord)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pauseMicForForeground(context: Context) {
            if (!VoiceCommandBus.serviceRunning.value) return
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_PAUSE_MIC
            }
            context.startService(intent)
        }

        fun resumeMicAfterForeground(context: Context) {
            if (!VoiceCommandBus.serviceRunning.value) return
            val intent = Intent(context, WakeWordForegroundService::class.java).apply {
                action = ACTION_RESUME_MIC
            }
            context.startService(intent)
        }
    }
}
