package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AndroidActionExecutor
import com.example.core.DeviceTelemetryHelper
import com.example.core.EcosystemApp
import com.example.core.LocalAssistantPrefs
import com.example.core.LocalPreferencesStore
import com.example.core.OfflineCommandOutcome
import com.example.core.VoiceCommandBus
import com.example.core.VoiceEngine
import com.example.core.VoiceEngineState
import com.example.core.VoiceListeningMode
import com.example.core.WakeWordForegroundService
import com.example.data.model.AssistantMessage
import com.example.data.model.AutomationActionType
import com.example.data.model.BatteryProfile
import com.example.data.model.ContextualSuggestion
import com.example.data.model.DeviceTelemetry
import com.example.data.model.DocAnalysisUiState
import com.example.data.model.DraftTone
import com.example.data.model.ImageGenUiState
import com.example.data.model.MessageDraft
import com.example.data.model.MessageMode
import com.example.data.model.SmartDeviceType
import com.example.data.model.SmartHomeDevice
import com.example.data.model.StudioWriteMode
import com.example.data.model.TaskAutomation
import com.example.data.model.TodoCategory
import com.example.data.model.TodoReminder
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VisualIdentificationResult
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "AuraViewModel"

sealed interface VisionUiState {
    data object Ready : VisionUiState
    data object Analyzing : VisionUiState
    data class Identified(val result: VisualIdentificationResult) : VisionUiState
    data class Error(val message: String) : VisionUiState
}

data class CommsDraftState(
    val recipientName: String = "",
    val recipientPhone: String = "",
    val intentNotes: String = "",
    val selectedTone: DraftTone = DraftTone.CASUAL,
    val generatedDraft: String = "",
    val drafting: Boolean = false,
    val incomingSender: String = "",
    val incomingText: String = "",
    val incomingSummary: String? = null,
    val incomingUrgency: String? = null,
    val incomingSuggestedReply: String? = null,
    val summarizing: Boolean = false,
    val error: String? = null
)

class AuraViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val displayName: String,
    private val firestoreRepository: FirestoreRepository,
    private val aiRepository: GeminiAiRepository,
    private val localPrefsStore: LocalPreferencesStore,
    private val telemetryHelper: DeviceTelemetryHelper,
    val actionExecutor: AndroidActionExecutor,
    val voiceEngine: VoiceEngine
) : ViewModel() {

    val localPrefs: StateFlow<LocalAssistantPrefs> = localPrefsStore.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAssistantPrefs())

    val voiceState: StateFlow<VoiceEngineState> = voiceEngine.state
    val lockScreenServiceRunning: StateFlow<Boolean> = VoiceCommandBus.serviceRunning
    val lockScreenServiceStatus: StateFlow<String> = VoiceCommandBus.serviceStatus
    val lockScreenServiceMode: StateFlow<VoiceListeningMode> = VoiceCommandBus.serviceMode

    val profileState: StateFlow<UiState<UserProfile?>> =
        firestoreRepository.observeProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("profile", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conversationsState: StateFlow<UiState<List<AssistantMessage>>> =
        firestoreRepository.observeConversations(currentUserId)
            .map<List<AssistantMessage>, UiState<List<AssistantMessage>>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("conversations", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val automationsState: StateFlow<UiState<List<TaskAutomation>>> =
        firestoreRepository.observeAutomations(currentUserId)
            .map<List<TaskAutomation>, UiState<List<TaskAutomation>>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("automations", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val draftsState: StateFlow<UiState<List<MessageDraft>>> =
        firestoreRepository.observeMessageDrafts(currentUserId)
            .map<List<MessageDraft>, UiState<List<MessageDraft>>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("drafts", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val todosState: StateFlow<UiState<List<TodoReminder>>> =
        firestoreRepository.observeTodos(currentUserId)
            .map<List<TodoReminder>, UiState<List<TodoReminder>>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("todos", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val smartDevicesState: StateFlow<UiState<List<SmartHomeDevice>>> =
        firestoreRepository.observeSmartDevices(currentUserId)
            .map<List<SmartHomeDevice>, UiState<List<SmartHomeDevice>>> { UiState.Success(it) }
            .catch { error -> emitFirestoreUiError("smart_devices", error) { emit(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _studioOutput = MutableStateFlow<UiState<String>?>(null)
    val studioOutput: StateFlow<UiState<String>?> = _studioOutput.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _docAnalysisState = MutableStateFlow<DocAnalysisUiState>(DocAnalysisUiState.Idle)
    val docAnalysisState: StateFlow<DocAnalysisUiState> = _docAnalysisState.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _assistantBusy = MutableStateFlow(false)
    val assistantBusy: StateFlow<Boolean> = _assistantBusy.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _telemetry = MutableStateFlow(
        telemetryHelper.snapshotMemoryAndBattery("Location standby")
    )
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ContextualSuggestion>>(defaultContextualSuggestions())
    val suggestions: StateFlow<List<ContextualSuggestion>> = _suggestions.asStateFlow()

    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading.asStateFlow()

    private val _visionState = MutableStateFlow<VisionUiState>(VisionUiState.Ready)
    val visionState: StateFlow<VisionUiState> = _visionState.asStateFlow()

    private val _commsState = MutableStateFlow(CommsDraftState())
    val commsState: StateFlow<CommsDraftState> = _commsState.asStateFlow()

    private val _requestedTab = MutableStateFlow<Int?>(null)
    val requestedTab: StateFlow<Int?> = _requestedTab.asStateFlow()

    private var cachedLocationLabel: String = "Location standby"

    init {
        VoiceCommandBus.hasActiveUiCollector.set(true)
        bootstrapSession()
        observePreferencesForVoice()
        observeLockScreenCommandBus()
        startLightweightTelemetryTicker()
    }

    private fun observeLockScreenCommandBus() {
        viewModelScope.launch {
            VoiceCommandBus.serviceRunning.collect { running ->
                voiceEngine.setBackgroundServiceOwnsStandby(running)
            }
        }
        viewModelScope.launch {
            VoiceCommandBus.recognizedCommands.collect { spokenCommand ->
                _requestedTab.value = 0
                submitPrompt(spokenCommand, fromVoice = true)
            }
        }
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            firestoreRepository.ensureUserProfileAndStarterData(
                displayName = displayName,
                deviceName = telemetryHelper.currentDeviceLabel()
            )
            refreshLocationContext()
        }
    }

    private fun observePreferencesForVoice() {
        viewModelScope.launch {
            localPrefs.collect { prefs ->
                voiceEngine.configure(
                    wakeWord = prefs.wakeWord,
                    offlinePreferred = prefs.offlineVoicePreferred,
                    profile = BatteryProfile.fromRaw(prefs.batteryMode),
                    rate = prefs.ttsSpeechRate,
                    onCommand = { spoken -> submitPrompt(spoken, fromVoice = true) },
                    onWake = {
                        _bannerMessage.value = "Wake word \"${prefs.wakeWord}\" detected — listening..."
                    }
                )
            }
        }
        viewModelScope.launch {
            profileState.collect { state ->
                val profile = (state as? UiState.Success)?.data ?: return@collect
                localPrefsStore.save(
                    wakeWord = profile.wakeWord,
                    offlineVoicePreferred = profile.offlineVoicePreferred,
                    autoReadReplies = profile.autoReadReplies,
                    batteryMode = profile.batteryMode,
                    ttsSpeechRate = profile.ttsSpeechRate.toFloat()
                )
            }
        }
    }

    private fun startLightweightTelemetryTicker() {
        viewModelScope.launch {
            while (isActive) {
                _telemetry.value = telemetryHelper.snapshotMemoryAndBattery(cachedLocationLabel)
                val intervalMs = when (BatteryProfile.fromRaw(localPrefs.value.batteryMode)) {
                    BatteryProfile.ECO -> 12_000L
                    BatteryProfile.BALANCED -> 8_000L
                    BatteryProfile.RESPONSIVE -> 5_000L
                }
                delay(intervalMs)
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun consumeRequestedTab() {
        _requestedTab.value = null
    }

    fun ingestSharedText(sharedText: String) {
        val clean = sharedText.trim()
        if (clean.isEmpty()) return
        _commsState.value = _commsState.value.copy(
            incomingSender = "Shared Message",
            incomingText = clean,
            incomingSummary = null,
            incomingUrgency = null,
            incomingSuggestedReply = null
        )
        _requestedTab.value = 3
        summarizeAndReadIncomingMessage()
    }

    fun toggleWakeWordStandby(enabled: Boolean) {
        viewModelScope.launch {
            localPrefsStore.setWakeStandby(enabled)
            voiceEngine.setWakeStandby(enabled)
            _bannerMessage.value = if (enabled) {
                "Wake standby active for \"${localPrefs.value.wakeWord}\" (${localPrefs.value.batteryMode} mode)"
            } else {
                "Wake word standby paused for zero mic drain."
            }
        }
    }

    fun toggleLockScreenWake(enabled: Boolean) {
        viewModelScope.launch {
            localPrefsStore.setLockScreenWake(enabled)
            val wake = localPrefs.value.wakeWord.trim().ifBlank { "Hey Aura" }
            if (enabled) {
                voiceEngine.setBackgroundServiceOwnsStandby(true)
                WakeWordForegroundService.start(appContext, wake)
                _bannerMessage.value = "Lock-screen wake active — say \"$wake\" even while locked."
            } else {
                WakeWordForegroundService.stop(appContext)
                voiceEngine.setBackgroundServiceOwnsStandby(false)
                _bannerMessage.value = "Lock-screen background wake turned off."
            }
        }
    }

    fun refreshLocationContext() {
        viewModelScope.launch {
            val resolved = telemetryHelper.resolveCoarseLocationLabel()
            if (!resolved.isNullOrBlank()) {
                cachedLocationLabel = resolved
                _telemetry.value = telemetryHelper.snapshotMemoryAndBattery(resolved)
                val topics = (profileState.value as? UiState.Success)?.data?.recentTopics.orEmpty()
                firestoreRepository.updateContextualTelemetry(
                    locationLabel = resolved,
                    recentTopics = topics,
                    deviceName = telemetryHelper.currentDeviceLabel()
                )
            }
        }
    }

    fun refreshContextualSuggestions() {
        viewModelScope.launch {
            _suggestionsLoading.value = true
            try {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val timeOfDay = when (hour) {
                    in 5..11 -> "Morning"
                    in 12..16 -> "Afternoon"
                    in 17..20 -> "Evening"
                    else -> "Night"
                }
                val topics = (profileState.value as? UiState.Success)?.data?.recentTopics.orEmpty()
                val generated = aiRepository.generateContextualSuggestions(
                    locationLabel = cachedLocationLabel,
                    timeOfDay = timeOfDay,
                    recentTopics = topics
                )
                if (generated.isNotEmpty()) {
                    _suggestions.value = generated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Contextual suggestions fallback: ${e.message}")
                _bannerMessage.value = e.localizedMessage ?: "Could not refresh AI suggestions."
            } finally {
                _suggestionsLoading.value = false
            }
        }
    }

    fun submitPrompt(rawInput: String, fromVoice: Boolean = false) {
        val text = rawInput.trim()
        if (text.isEmpty() || _assistantBusy.value) return
        val device = telemetryHelper.currentDeviceLabel()
        val prefs = localPrefs.value
        val automations = (automationsState.value as? UiState.Success)?.data.orEmpty()
        val smartDevices = (smartDevicesState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _assistantBusy.value = true
            _streamingReply.value = null
            try {
                val userMode = if (fromVoice) MessageMode.VOICE else MessageMode.TEXT
                firestoreRepository.addConversationMessage(
                    role = "USER",
                    content = text,
                    mode = userMode,
                    deviceName = device,
                    locationContext = cachedLocationLabel.takeIf { it != "Location standby" }
                )

                // Check offline/local command router first for speed & privacy
                val localOutcome = actionExecutor.tryHandleLocally(
                    input = text,
                    savedAutomations = automations,
                    smartDevices = smartDevices,
                    locationLabel = cachedLocationLabel
                )
                if (localOutcome is OfflineCommandOutcome.Handled) {
                    firestoreRepository.addConversationMessage(
                        role = "ASSISTANT",
                        content = localOutcome.replyText,
                        mode = MessageMode.OFFLINE,
                        deviceName = device,
                        locationContext = cachedLocationLabel.takeIf { it != "Location standby" }
                    )
                    if (prefs.autoReadReplies) {
                        voiceEngine.speak(localOutcome.replyText)
                    }
                    localOutcome.addTodoRequest?.let { (todoTitle, cat) ->
                        firestoreRepository.addTodo(todoTitle, null, cat)
                    }
                    localOutcome.smartDeviceToggle?.let { (devId, powerOn, levelOpt) ->
                        val targetDev = smartDevices.firstOrNull { it.id == devId }
                        if (targetDev != null) {
                            val newLevel = (levelOpt?.toLong() ?: targetDev.level).coerceIn(0L, 100L)
                            updateSmartDeviceState(targetDev, powerOn, newLevel)
                        }
                    }
                    localOutcome.smartSceneName?.let { scene ->
                        runSmartScene(scene)
                    }
                    localOutcome.openWhatsAppDraftFor?.let { (recipient, notes) ->
                        _commsState.value = _commsState.value.copy(
                            recipientName = recipient,
                            intentNotes = notes
                        )
                        _requestedTab.value = 3
                        draftWhatsAppWithGemini()
                    }
                    return@launch
                }

                if (!telemetryHelper.isNetworkAvailable()) {
                    val offlineReply =
                        "You are currently offline. I can still set timers, alarms, open Maps, or run offline routines locally."
                    firestoreRepository.addConversationMessage(
                        role = "ASSISTANT",
                        content = offlineReply,
                        mode = MessageMode.OFFLINE,
                        deviceName = device
                    )
                    if (prefs.autoReadReplies) voiceEngine.speak(offlineReply)
                    return@launch
                }

                val recentHistory = (conversationsState.value as? UiState.Success)?.data
                    .orEmpty()
                    .take(6)
                    .reversed()
                    .map { it.role to it.content }
                val topics = (profileState.value as? UiState.Success)?.data?.recentTopics.orEmpty()

                var finalReply = ""
                aiRepository.streamAssistantReply(
                    prompt = text,
                    locationLabel = cachedLocationLabel,
                    recentTopics = topics,
                    recentContextHistory = recentHistory
                ).collect { partial ->
                    finalReply = partial
                    _streamingReply.value = partial
                }

                _streamingReply.value = null
                if (finalReply.isNotBlank()) {
                    firestoreRepository.addConversationMessage(
                        role = "ASSISTANT",
                        content = finalReply,
                        mode = userMode,
                        deviceName = device,
                        locationContext = cachedLocationLabel.takeIf { it != "Location standby" }
                    )
                    if (prefs.autoReadReplies) {
                        voiceEngine.speak(finalReply)
                    }
                    trackBehavioralTopic(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _streamingReply.value = null
                Log.e(TAG, "Prompt handling failed", e)
                _bannerMessage.value = e.localizedMessage ?: "Assistant request failed."
            } finally {
                _assistantBusy.value = false
            }
        }
    }

    private fun trackBehavioralTopic(query: String) {
        viewModelScope.launch {
            val keyword = query.split(Regex("\\s+"))
                .firstOrNull { it.length >= 5 && it.all { ch -> ch.isLetter() } }
                ?.lowercase() ?: return@launch
            val currentTopics = (profileState.value as? UiState.Success)?.data?.recentTopics.orEmpty()
            val updated = (listOf(keyword) + currentTopics).distinct().take(10)
            firestoreRepository.updateContextualTelemetry(
                locationLabel = cachedLocationLabel.takeIf { it != "Location standby" },
                recentTopics = updated,
                deviceName = telemetryHelper.currentDeviceLabel()
            )
        }
    }

    fun analyzeCircledRegion(cropped: Bitmap, fullScene: Bitmap?) {
        viewModelScope.launch {
            _visionState.value = VisionUiState.Analyzing
            try {
                val result = aiRepository.identifyCircledObject(cropped, fullScene)
                _visionState.value = VisionUiState.Identified(result)
                val summaryMsg = "Circle-to-Identify: ${result.objectName} (${result.category}) — ${result.summary}"
                firestoreRepository.addConversationMessage(
                    role = "ASSISTANT",
                    content = summaryMsg,
                    mode = MessageMode.VISION,
                    deviceName = telemetryHelper.currentDeviceLabel(),
                    locationContext = cachedLocationLabel.takeIf { it != "Location standby" },
                    visualTag = result.objectName
                )
                if (localPrefs.value.autoReadReplies) {
                    voiceEngine.speak("${result.objectName}. ${result.summary}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Circle vision error", e)
                _visionState.value = VisionUiState.Error(
                    e.localizedMessage ?: "Could not identify the circled area."
                )
            }
        }
    }

    fun resetVisionResult() {
        _visionState.value = VisionUiState.Ready
    }

    fun prefillWhatsAppFromVision(result: VisualIdentificationResult) {
        _commsState.value = _commsState.value.copy(
            intentNotes = "Share details about ${result.objectName}: ${result.summary}",
            generatedDraft = ""
        )
        _requestedTab.value = 3
    }

    fun updateCommsFields(
        recipientName: String? = null,
        recipientPhone: String? = null,
        intentNotes: String? = null,
        tone: DraftTone? = null,
        generatedDraft: String? = null,
        incomingSender: String? = null,
        incomingText: String? = null
    ) {
        val current = _commsState.value
        _commsState.value = current.copy(
            recipientName = recipientName ?: current.recipientName,
            recipientPhone = recipientPhone ?: current.recipientPhone,
            intentNotes = intentNotes ?: current.intentNotes,
            selectedTone = tone ?: current.selectedTone,
            generatedDraft = generatedDraft ?: current.generatedDraft,
            incomingSender = incomingSender ?: current.incomingSender,
            incomingText = incomingText ?: current.incomingText,
            error = null
        )
    }

    fun draftWhatsAppWithGemini() {
        val state = _commsState.value
        val recipient = state.recipientName.trim().ifBlank { "Friend" }
        val notes = state.intentNotes.trim()
        if (notes.isEmpty()) {
            _commsState.value = state.copy(error = "Tell Aura what you want to say first.")
            return
        }
        viewModelScope.launch {
            _commsState.value = state.copy(drafting = true, error = null)
            try {
                val draft = aiRepository.draftWhatsAppMessage(recipient, notes, state.selectedTone)
                _commsState.value = _commsState.value.copy(
                    generatedDraft = draft,
                    drafting = false
                )
                firestoreRepository.saveMessageDraft(
                    recipientName = recipient,
                    recipientPhone = state.recipientPhone.takeIf { it.isNotBlank() },
                    body = draft,
                    tone = state.selectedTone,
                    direction = "OUTGOING_DRAFT",
                    status = "DRAFT"
                )
                if (localPrefs.value.autoReadReplies) {
                    voiceEngine.speak("Draft for $recipient: $draft")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commsState.value = _commsState.value.copy(
                    drafting = false,
                    error = e.localizedMessage ?: "Failed to draft WhatsApp message."
                )
            }
        }
    }

    fun sendDraftOnWhatsApp(draftId: String?, phone: String?, message: String) {
        if (message.isBlank()) return
        actionExecutor.launchWhatsAppMessage(phone, message)
        if (!draftId.isNullOrBlank()) {
            viewModelScope.launch {
                firestoreRepository.markDraftStatus(draftId, "SENT")
            }
        }
    }

    fun summarizeAndReadIncomingMessage() {
        val state = _commsState.value
        val text = state.incomingText.trim()
        val sender = state.incomingSender.trim().ifBlank { "Contact" }
        if (text.isEmpty()) {
            _commsState.value = state.copy(error = "Paste or share a message to read aloud.")
            return
        }
        viewModelScope.launch {
            _commsState.value = state.copy(summarizing = true, error = null)
            try {
                val (summary, urgency, suggestedReply) = aiRepository.summarizeIncomingMessage(sender, text)
                _commsState.value = _commsState.value.copy(
                    incomingSummary = summary,
                    incomingUrgency = urgency,
                    incomingSuggestedReply = suggestedReply,
                    summarizing = false
                )
                firestoreRepository.saveMessageDraft(
                    recipientName = sender,
                    recipientPhone = null,
                    body = text,
                    tone = DraftTone.CONCISE,
                    direction = "INCOMING_READER",
                    status = "READ",
                    summary = summary
                )
                voiceEngine.speak("Message from $sender. $summary")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _commsState.value = _commsState.value.copy(
                    summarizing = false,
                    error = e.localizedMessage ?: "Could not summarize message."
                )
                // Still read the raw message aloud offline for accessibility
                voiceEngine.speak("Message from $sender: $text")
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteMessageDraft(draftId)
        }
    }

    fun createAutomation(
        title: String,
        triggerPhrase: String,
        actionType: AutomationActionType,
        actionPayload: String,
        contextHint: String?
    ) {
        viewModelScope.launch {
            val res = firestoreRepository.createAutomation(
                title = title,
                triggerPhrase = triggerPhrase,
                actionType = actionType,
                actionPayload = actionPayload,
                contextHint = contextHint
            )
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Could not save routine."
            } else {
                _bannerMessage.value = "Routine \"$title\" synced across your devices."
            }
        }
    }

    fun toggleAutomation(automationId: String, enabled: Boolean) {
        viewModelScope.launch {
            firestoreRepository.toggleAutomation(automationId, enabled)
        }
    }

    fun runAutomation(automation: TaskAutomation) {
        viewModelScope.launch {
            firestoreRepository.recordAutomationRun(automation.id)
            val type = AutomationActionType.fromRaw(automation.actionType)
            if (type == AutomationActionType.BRIEFING) {
                _requestedTab.value = 0
                submitPrompt(automation.actionPayload, fromVoice = false)
            } else {
                val resultMsg = actionExecutor.executeAutomation(automation)
                _bannerMessage.value = resultMsg
                if (localPrefs.value.autoReadReplies) {
                    voiceEngine.speak(resultMsg)
                }
            }
        }
    }

    fun deleteAutomation(automationId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteAutomation(automationId)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteConversationMessage(messageId)
        }
    }

    fun saveAssistantPreferences(
        wakeWord: String,
        offlineVoicePreferred: Boolean,
        autoReadReplies: Boolean,
        batteryProfile: BatteryProfile,
        ttsSpeechRate: Float,
        lockScreenWakeEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            val safeWake = wakeWord.trim().take(40).ifBlank { "Hey Aura" }
            localPrefsStore.save(
                wakeWord = safeWake,
                offlineVoicePreferred = offlineVoicePreferred,
                autoReadReplies = autoReadReplies,
                batteryMode = batteryProfile.name,
                ttsSpeechRate = ttsSpeechRate,
                lockScreenWakeEnabled = lockScreenWakeEnabled
            )
            if (lockScreenWakeEnabled == true) {
                voiceEngine.setBackgroundServiceOwnsStandby(true)
                WakeWordForegroundService.start(appContext, safeWake)
            } else if (lockScreenWakeEnabled == false && VoiceCommandBus.serviceRunning.value) {
                WakeWordForegroundService.stop(appContext)
                voiceEngine.setBackgroundServiceOwnsStandby(false)
            }
            val res = firestoreRepository.updatePreferences(
                wakeWord = safeWake,
                offlineVoicePreferred = offlineVoicePreferred,
                autoReadReplies = autoReadReplies,
                batteryMode = batteryProfile,
                ttsSpeechRate = ttsSpeechRate.toDouble(),
                deviceName = telemetryHelper.currentDeviceLabel()
            )
            _bannerMessage.value = if (res.isSuccess) {
                "Preferences & wake word \"$safeWake\" synced to cloud."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Saved locally; cloud sync pending."
            }
        }
    }

    fun prepareForSignOut() {
        voiceEngine.setWakeStandby(false)
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        if (VoiceCommandBus.serviceRunning.value) {
            WakeWordForegroundService.stop(appContext)
        }
    }

    // --- Productivity: To-Do, Reminders & Calendar ---
    fun addTodoItem(title: String, dueLabel: String?, category: TodoCategory) {
        viewModelScope.launch {
            val res = firestoreRepository.addTodo(title, dueLabel, category)
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Could not save task."
            }
        }
    }

    fun toggleTodoItem(todoId: String, completed: Boolean) {
        viewModelScope.launch {
            firestoreRepository.toggleTodoCompleted(todoId, completed)
        }
    }

    fun deleteTodoItem(todoId: String) {
        viewModelScope.launch {
            firestoreRepository.deleteTodo(todoId)
        }
    }

    // --- Smart Home Control & Multi-Device Routines ---
    fun updateSmartDeviceState(device: SmartHomeDevice, poweredOn: Boolean, level: Long) {
        val type = SmartDeviceType.fromRaw(device.deviceType)
        val statusText = when (type) {
            SmartDeviceType.LIGHT -> if (poweredOn) "On • ${level}%" else "Off"
            SmartDeviceType.LOCK -> if (poweredOn) "Locked & Secure" else "Unlocked"
            SmartDeviceType.THERMOSTAT -> if (poweredOn) "Active • ${level}°C" else "Eco Standby"
            SmartDeviceType.VACUUM -> if (poweredOn) "Cleaning Floor" else "Docked • ${level}% Battery"
            SmartDeviceType.TV -> if (poweredOn) "Playing • Vol ${level}%" else "Standby"
        }
        viewModelScope.launch {
            firestoreRepository.updateSmartDevice(device.id, poweredOn, level, statusText)
        }
    }

    fun runSmartScene(sceneKey: String) {
        val devices = (smartDevicesState.value as? UiState.Success)?.data.orEmpty()
        if (devices.isEmpty()) return
        viewModelScope.launch {
            devices.forEach { dev ->
                val type = SmartDeviceType.fromRaw(dev.deviceType)
                when (sceneKey) {
                    "MOVIE_NIGHT" -> when (type) {
                        SmartDeviceType.LIGHT -> updateSmartDeviceState(dev, true, 20L)
                        SmartDeviceType.TV -> updateSmartDeviceState(dev, true, 45L)
                        SmartDeviceType.LOCK -> updateSmartDeviceState(dev, true, 100L)
                        SmartDeviceType.VACUUM -> updateSmartDeviceState(dev, false, dev.level)
                        SmartDeviceType.THERMOSTAT -> updateSmartDeviceState(dev, true, 21L)
                    }
                    "GOOD_MORNING" -> when (type) {
                        SmartDeviceType.LIGHT -> updateSmartDeviceState(dev, true, 90L)
                        SmartDeviceType.THERMOSTAT -> updateSmartDeviceState(dev, true, 22L)
                        SmartDeviceType.TV -> updateSmartDeviceState(dev, false, dev.level)
                        else -> Unit
                    }
                    "AWAY_MODE" -> when (type) {
                        SmartDeviceType.LIGHT -> updateSmartDeviceState(dev, false, 0L)
                        SmartDeviceType.LOCK -> updateSmartDeviceState(dev, true, 100L)
                        SmartDeviceType.TV -> updateSmartDeviceState(dev, false, dev.level)
                        SmartDeviceType.VACUUM -> updateSmartDeviceState(dev, true, dev.level)
                        SmartDeviceType.THERMOSTAT -> updateSmartDeviceState(dev, false, 18L)
                    }
                }
            }
            val niceName = sceneKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            _bannerMessage.value = "Smart Home scene \"$niceName\" applied across ${devices.size} devices."
        }
    }

    // --- AI Studio: Writing, Summarization, Recipes, Trips, Study & Image Generation ---
    fun runStudioWriting(mode: StudioWriteMode, input: String) {
        val clean = input.trim()
        if (clean.isEmpty()) {
            _studioOutput.value = UiState.Error("Enter a topic, prompt, or text first.")
            return
        }
        viewModelScope.launch {
            _studioOutput.value = UiState.Loading
            try {
                val result = aiRepository.generateStudioWriting(mode, clean, cachedLocationLabel)
                _studioOutput.value = UiState.Success(result)
                firestoreRepository.addConversationMessage(
                    role = "ASSISTANT",
                    content = "[${mode.label}] ${result.take(1200)}",
                    mode = MessageMode.TEXT,
                    deviceName = telemetryHelper.currentDeviceLabel(),
                    locationContext = cachedLocationLabel.takeIf { it != "Location standby" }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studioOutput.value = UiState.Error(e.localizedMessage ?: "Studio generation failed.")
            }
        }
    }

    fun generateCreativeImage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Describe the image you want Gemini to create.")
            return
        }
        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiRepository.generateImageFromPrompt(clean)
                _imageGenState.value = ImageGenUiState.Success(bitmap, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageGenState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    // --- Multimodal Document & PDF Analysis ---
    fun analyzeDocument(bytes: ByteArray, mimeType: String, fileName: String, question: String) {
        viewModelScope.launch {
            _docAnalysisState.value = DocAnalysisUiState.Loading
            try {
                val analysis = aiRepository.analyzeDocumentOrFile(bytes, mimeType, fileName, question)
                _docAnalysisState.value = DocAnalysisUiState.Success(fileName, analysis)
                firestoreRepository.addConversationMessage(
                    role = "ASSISTANT",
                    content = "Document ($fileName): ${analysis.take(1500)}",
                    mode = MessageMode.VISION,
                    deviceName = telemetryHelper.currentDeviceLabel(),
                    visualTag = fileName.take(40)
                )
                if (localPrefs.value.autoReadReplies) {
                    voiceEngine.speak(analysis.take(450))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _docAnalysisState.value = DocAnalysisUiState.Error(
                    e.localizedMessage ?: "Could not analyze $fileName."
                )
            }
        }
    }

    fun openEcosystemApp(app: EcosystemApp) {
        actionExecutor.launchEcosystemApp(app)
        _bannerMessage.value = "Opening ${app.displayName}..."
    }

    override fun onCleared() {
        VoiceCommandBus.hasActiveUiCollector.set(false)
        super.onCleared()
        voiceEngine.release()
    }

    private suspend fun <T> emitFirestoreUiError(
        label: String,
        error: Throwable,
        emitFn: suspend (UiState<T>) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing $label", root)
        emitFn(UiState.Error(root.message ?: "Failed to sync $label"))
    }

    private fun defaultContextualSuggestions(): List<ContextualSuggestion> = listOf(
        ContextualSuggestion(
            title = "Daily Voice Briefing",
            subtitle = "Quick spoken overview for your current time & area",
            promptOrCommand = "Give me a concise 3-bullet briefing for today with one productivity tip.",
            category = "Routine"
        ),
        ContextualSuggestion(
            title = "Offline 5-Min Timer",
            subtitle = "Zero-network local intent execution",
            promptOrCommand = "Set a timer for 5 minutes",
            category = "Offline"
        ),
        ContextualSuggestion(
            title = "Quick WhatsApp ETA",
            subtitle = "Draft a warm on-my-way update in seconds",
            promptOrCommand = "WhatsApp Alex saying I will be there in 10 minutes",
            category = "Message"
        )
    )
}
