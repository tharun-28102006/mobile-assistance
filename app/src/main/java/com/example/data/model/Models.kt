package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BatteryProfile(val label: String, val pollDelayMs: Long, val detail: String) {
    ECO("Eco", 1400L, "Minimal wake-word polling & ultra-low memory"),
    BALANCED("Balanced", 750L, "Smart adaptive wake detection"),
    RESPONSIVE("Responsive", 300L, "Instant wake response when active");

    companion object {
        fun fromRaw(raw: String?): BatteryProfile =
            entries.firstOrNull { it.name == raw } ?: ECO
    }
}

enum class MessageMode {
    VOICE, TEXT, VISION, OFFLINE, AUTOMATION;

    companion object {
        fun fromRaw(raw: String?): MessageMode =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class AutomationActionType(val label: String) {
    ALARM("Set Alarm"),
    TIMER("Start Timer"),
    WHATSAPP("WhatsApp Message"),
    MAPS("Navigate Maps"),
    BRIEFING("AI Briefing"),
    WEB_SEARCH("Web Search");

    companion object {
        fun fromRaw(raw: String?): AutomationActionType =
            entries.firstOrNull { it.name == raw } ?: BRIEFING
    }
}

enum class DraftTone(val label: String) {
    CASUAL("Casual"),
    PROFESSIONAL("Professional"),
    WARM("Warm"),
    CONCISE("Concise");

    companion object {
        fun fromRaw(raw: String?): DraftTone =
            entries.firstOrNull { it.name == raw } ?: CASUAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Aura User",
    val wakeWord: String = "Hey Aura",
    val offlineVoicePreferred: Boolean = true,
    val autoReadReplies: Boolean = true,
    val batteryMode: String = BatteryProfile.ECO.name,
    val ttsSpeechRate: Double = 1.05,
    val lastDeviceName: String = "Android Phone",
    val lastLocationLabel: String? = null,
    val recentTopics: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AssistantMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val content: String = "",
    val mode: String = MessageMode.TEXT.name,
    val deviceName: String = "Android Phone",
    val locationContext: String? = null,
    val visualTag: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TaskAutomation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val triggerPhrase: String = "",
    val actionType: String = AutomationActionType.BRIEFING.name,
    val actionPayload: String = "",
    val contextHint: String? = null,
    val enabled: Boolean = true,
    val runCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MessageDraft(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recipientName: String = "",
    val recipientPhone: String? = null,
    val body: String = "",
    val tone: String = DraftTone.CASUAL.name,
    val direction: String = "OUTGOING_DRAFT",
    val status: String = "DRAFT",
    val summary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class VisualIdentificationResult(
    val objectName: String,
    val category: String,
    val summary: String,
    val keyDetails: List<String>,
    val suggestedActions: List<String>
)

data class ContextualSuggestion(
    val title: String,
    val subtitle: String,
    val promptOrCommand: String,
    val category: String
)

data class DeviceTelemetry(
    val appHeapMb: Int = 0,
    val maxHeapMb: Int = 256,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val locationLabel: String = "Location standby",
    val deviceLabel: String = "Android Phone",
    val networkOnline: Boolean = true
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class TodoCategory(val label: String) {
    TASK("To-Do"),
    REMINDER("Reminder"),
    CALENDAR("Calendar");

    companion object {
        fun fromRaw(raw: String?): TodoCategory =
            entries.firstOrNull { it.name == raw } ?: TASK
    }
}

enum class SmartDeviceType(val label: String) {
    LIGHT("Light"),
    LOCK("Smart Lock"),
    THERMOSTAT("Thermostat"),
    VACUUM("Vacuum"),
    TV("Smart TV");

    companion object {
        fun fromRaw(raw: String?): SmartDeviceType =
            entries.firstOrNull { it.name == raw } ?: LIGHT
    }
}

@Keep
@IgnoreExtraProperties
data class TodoReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val dueLabel: String? = null,
    val category: String = TodoCategory.TASK.name,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SmartHomeDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val room: String = "",
    val deviceType: String = SmartDeviceType.LIGHT.name,
    val poweredOn: Boolean = false,
    val level: Long = 50L,
    val statusText: String = "Off",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class StudioWriteMode(val label: String, val placeholder: String) {
    EMAIL("Draft Email", "Ask Alex to reschedule Thursday's design review to 3 PM"),
    ARTICLE("Article", "Write a concise explainer on solid-state EV batteries"),
    BIO("Bio", "Professional Android & AI developer bio for LinkedIn"),
    SUMMARIZE("Summarize", "Paste long text, web article content, or notes to summarize"),
    RECIPE("Recipe", "Quick 20-minute dinner with chicken, spinach, garlic, and lemon"),
    TRIP("Trip Plan", "3-day Tokyo itinerary focusing on food, tech, and architecture"),
    STUDY("Explain & Code", "Explain Kotlin Coroutines Flow vs StateFlow with a crisp example")
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: android.graphics.Bitmap, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

sealed interface DocAnalysisUiState {
    data object Idle : DocAnalysisUiState
    data object Loading : DocAnalysisUiState
    data class Success(val fileName: String, val analysis: String) : DocAnalysisUiState
    data class Error(val message: String) : DocAnalysisUiState
}

