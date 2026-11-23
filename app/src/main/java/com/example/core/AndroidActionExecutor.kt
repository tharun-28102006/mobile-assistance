package com.example.core

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.model.AutomationActionType
import com.example.data.model.SmartDeviceType
import com.example.data.model.SmartHomeDevice
import com.example.data.model.TaskAutomation
import com.example.data.model.TodoCategory
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface OfflineCommandOutcome {
    data class Handled(
        val replyText: String,
        val openWhatsAppDraftFor: Pair<String, String>? = null,
        val addTodoRequest: Pair<String, TodoCategory>? = null,
        val smartDeviceToggle: Triple<String, Boolean, Int?>? = null,
        val smartSceneName: String? = null,
        val openTabIndex: Int? = null
    ) : OfflineCommandOutcome

    data object RouteToCloudGemini : OfflineCommandOutcome
}

enum class EcosystemApp(
    val displayName: String,
    val packageName: String,
    val fallbackUrl: String
) {
    GMAIL("Gmail", "com.google.android.gm", "https://mail.google.com"),
    DRIVE("Google Drive", "com.google.android.apps.docs", "https://drive.google.com"),
    CALENDAR("Calendar", "com.google.android.calendar", "https://calendar.google.com"),
    YOUTUBE("YouTube", "com.google.android.youtube", "https://www.youtube.com"),
    MAPS("Google Maps", "com.google.android.apps.maps", "https://maps.google.com"),
    WHATSAPP("WhatsApp", "com.whatsapp", "https://web.whatsapp.com"),
    SPOTIFY("Spotify", "com.spotify.music", "https://open.spotify.com"),
    GOOGLE_HOME("Google Home", "com.google.android.apps.chromecast.app", "https://home.google.com")
}

class AndroidActionExecutor(private val context: Context) {

    fun tryHandleLocally(
        input: String,
        savedAutomations: List<TaskAutomation>,
        smartDevices: List<SmartHomeDevice>,
        locationLabel: String
    ): OfflineCommandOutcome {
        val raw = input.trim()
        val normalized = raw.lowercase(Locale.US)
        if (normalized.isEmpty()) return OfflineCommandOutcome.RouteToCloudGemini

        // 1. Saved voice automation trigger
        val matchedRoutine = savedAutomations.firstOrNull { auto ->
            auto.enabled && auto.triggerPhrase.isNotBlank() &&
                normalized.contains(auto.triggerPhrase.trim().lowercase(Locale.US))
        }
        if (matchedRoutine != null &&
            AutomationActionType.fromRaw(matchedRoutine.actionType) != AutomationActionType.BRIEFING
        ) {
            val status = executeAutomation(matchedRoutine)
            return OfflineCommandOutcome.Handled("Executed routine \"${matchedRoutine.title}\". $status")
        }

        // 2. Smart Home Multi-Device Scenes
        when {
            normalized.contains("movie night") ->
                return OfflineCommandOutcome.Handled("Running Movie Night scene across your Smart Home.", smartSceneName = "MOVIE_NIGHT")
            normalized.contains("good morning scene") || normalized.contains("morning home") ->
                return OfflineCommandOutcome.Handled("Running Good Morning scene across your Smart Home.", smartSceneName = "GOOD_MORNING")
            normalized.contains("leaving home") || normalized.contains("away mode") ->
                return OfflineCommandOutcome.Handled("Activating Away Mode: locking doors, turning off lights & TV, starting vacuum.", smartSceneName = "AWAY_MODE")
        }

        // 3. Smart Home Single Device Controls (lights, lock, thermostat, vacuum, TV)
        val tempMatch = Regex("""(?:set\s+)?thermostat\s+(?:to\s+)?(\d{2})""").find(normalized)
        if (tempMatch != null) {
            val targetTemp = tempMatch.groupValues[1].toIntOrNull()?.coerceIn(16, 30) ?: 22
            val thermo = smartDevices.firstOrNull {
                SmartDeviceType.fromRaw(it.deviceType) == SmartDeviceType.THERMOSTAT
            }
            if (thermo != null) {
                return OfflineCommandOutcome.Handled(
                    replyText = "Set ${thermo.name} to $targetTemp°C.",
                    smartDeviceToggle = Triple(thermo.id, true, targetTemp)
                )
            }
        }
        val smartVerb = Regex("""^(turn\s+on|turn\s+off|switch\s+on|switch\s+off|lock|unlock|start|stop)\s+(.+)""")
            .find(normalized)
        if (smartVerb != null) {
            val verb = smartVerb.groupValues[1]
            val target = smartVerb.groupValues[2].trim()
            val matchedDev = smartDevices.firstOrNull { dev ->
                val n = dev.name.lowercase(Locale.US)
                val t = dev.deviceType.lowercase(Locale.US)
                n.contains(target) || target.contains(n) || target.contains(t)
            }
            if (matchedDev != null) {
                val powerOn = verb.contains("on") || verb == "lock" || verb == "start"
                val verbLabel = when (SmartDeviceType.fromRaw(matchedDev.deviceType)) {
                    SmartDeviceType.LOCK -> if (powerOn) "Locked" else "Unlocked"
                    SmartDeviceType.VACUUM -> if (powerOn) "Started" else "Docked"
                    else -> if (powerOn) "Turned on" else "Turned off"
                }
                return OfflineCommandOutcome.Handled(
                    replyText = "$verbLabel ${matchedDev.name}.",
                    smartDeviceToggle = Triple(matchedDev.id, powerOn, null)
                )
            }
        }

        // 4. Time & Date offline query
        if (normalized.contains("what time") || normalized == "time" || normalized.contains("current time")) {
            val formatted = SimpleDateFormat("h:mm a, EEEE MMM d", Locale.getDefault()).format(Date())
            return OfflineCommandOutcome.Handled("It is currently $formatted (processed locally on-device).")
        }

        // 5. Timers & Alarms
        val timerMatch = Regex("""(?:set\s+)?(?:a\s+)?timer\s+(?:for\s+)?(\d+)\s*(min|minute|minutes|sec|second|seconds)""")
            .find(normalized)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
            val unit = timerMatch.groupValues[2]
            val seconds = if (unit.startsWith("min")) amount * 60 else amount
            val ok = launchTimer(seconds, "Aura Timer")
            return OfflineCommandOutcome.Handled(
                if (ok) "Started an offline timer for $amount $unit."
                else "Parsed timer for $amount $unit (Clock app unavailable on emulator)."
            )
        }

        val alarmMatch = Regex("""(?:set\s+)?(?:an?\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
            .find(normalized)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = alarmMatch.groupValues[3]
            if (amPm == "pm" && hour in 1..11) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            val ok = launchAlarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59), "Aura Alarm")
            return OfflineCommandOutcome.Handled(
                if (ok) "Set an alarm for %02d:%02d.".format(hour, minute)
                else "Parsed alarm for %02d:%02d (Clock app unavailable).".format(hour, minute)
            )
        }

        // 6. Reminders, To-Do Lists & Calendar Events
        val reminderMatch = Regex("""^(?:remind me to|add reminder|add to[- ]?do|add task)\s+(.+)""").find(raw)
        if (reminderMatch != null) {
            val item = reminderMatch.groupValues[1].trim()
            val cat = if (normalized.startsWith("remind")) TodoCategory.REMINDER else TodoCategory.TASK
            return OfflineCommandOutcome.Handled(
                replyText = "Added \"${item}\" to your synced ${cat.label} list.",
                addTodoRequest = item to cat
            )
        }

        val calMatch = Regex("""^(?:schedule|add calendar event|create event)\s+(.+)""").find(raw)
        if (calMatch != null) {
            val title = calMatch.groupValues[1].trim()
            launchCalendarEvent(title, "Created via Aura Assist")
            return OfflineCommandOutcome.Handled(
                replyText = "Opened Calendar to schedule \"$title\" and saved it to your list.",
                addTodoRequest = title to TodoCategory.CALENDAR
            )
        }

        // 7. Communication: Call, SMS, Email, WhatsApp
        val callMatch = Regex("""^(?:call|dial)\s+(.+)""").find(raw)
        if (callMatch != null) {
            val target = callMatch.groupValues[1].trim()
            launchDialer(target)
            return OfflineCommandOutcome.Handled("Opened phone dialer for $target.")
        }

        val smsMatch = Regex("""^(?:text|sms)\s+([+\d\s-]+)\s+(?:saying\s+)?(.+)""").find(raw)
        if (smsMatch != null) {
            val number = smsMatch.groupValues[1].trim()
            val body = smsMatch.groupValues[2].trim()
            launchSms(number, body)
            return OfflineCommandOutcome.Handled("Opened SMS to $number with your message.")
        }

        val emailMatch = Regex("""^(?:email|send email to)\s+([^\s]+@[^\s]+)\s*(.*)""").find(raw)
        if (emailMatch != null) {
            val addr = emailMatch.groupValues[1].trim()
            val body = emailMatch.groupValues[2].trim()
            launchEmail(addr, "Message via Aura Assist", body)
            return OfflineCommandOutcome.Handled("Opened your Email app addressed to $addr.")
        }

        val waMatch = Regex("""(?:whatsapp|message)\s+([a-zA-Z0-9\s]+?)\s+(?:saying|that|:)\s+(.+)""")
            .find(raw)
        if (waMatch != null) {
            val recipient = waMatch.groupValues[1].trim()
            val note = waMatch.groupValues[2].trim()
            return OfflineCommandOutcome.Handled(
                replyText = "Drafting a WhatsApp message to $recipient...",
                openWhatsAppDraftFor = recipient to note
            )
        }

        // 8. Device Controls: Volume, Settings, Camera, Music/Podcast, Open Apps
        when {
            normalized.contains("volume up") || normalized.contains("louder") -> {
                adjustMediaVolume(AudioManager.ADJUST_RAISE)
                return OfflineCommandOutcome.Handled("Increased media volume.")
            }
            normalized.contains("volume down") || normalized.contains("quieter") -> {
                adjustMediaVolume(AudioManager.ADJUST_LOWER)
                return OfflineCommandOutcome.Handled("Lowered media volume.")
            }
            normalized.contains("mute volume") || normalized == "mute" -> {
                adjustMediaVolume(AudioManager.ADJUST_MUTE)
                return OfflineCommandOutcome.Handled("Muted media volume.")
            }
            normalized.contains("wifi") || normalized.contains("wi-fi") -> {
                openWifiSettings()
                return OfflineCommandOutcome.Handled("Opened Wi-Fi settings panel.")
            }
            normalized.contains("bluetooth") -> {
                openBluetoothSettings()
                return OfflineCommandOutcome.Handled("Opened Bluetooth settings.")
            }
            normalized.contains("brightness") || normalized.contains("display settings") -> {
                openDisplaySettings()
                return OfflineCommandOutcome.Handled("Opened Display & Brightness settings.")
            }
            normalized.contains("take a photo") || normalized.contains("take photo") || normalized.contains("open camera") -> {
                launchSystemCamera()
                return OfflineCommandOutcome.Handled("Launched Camera to capture a photo.")
            }
        }

        val playMatch = Regex("""^play\s+(?:music|podcast|song|video)?\s*(.+)""").find(raw)
        if (playMatch != null) {
            val query = playMatch.groupValues[1].trim()
            playMusicOrPodcast(query)
            return OfflineCommandOutcome.Handled("Playing \"$query\" via media apps.")
        }

        val openMatch = Regex("""^(?:open|launch)\s+(.+)""").find(normalized)
        if (openMatch != null) {
            val appQuery = openMatch.groupValues[1].trim()
            val launchedName = openAppByName(appQuery)
            if (launchedName != null) {
                return OfflineCommandOutcome.Handled("Opened $launchedName.")
            }
        }

        // 9. Navigation / Maps
        val navMatch = Regex("""^(?:navigate|directions|take me)\s+to\s+(.+)""").find(normalized)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim()
            val ok = launchMaps(dest)
            return OfflineCommandOutcome.Handled(
                if (ok) "Opening Google Maps directions to \"$dest\"."
                else "Could not open Maps for \"$dest\"."
            )
        }

        // 10. Current location readout
        if (normalized.contains("where am i") || normalized.contains("my location")) {
            return OfflineCommandOutcome.Handled("Your current contextual region is: $locationLabel.")
        }

        return OfflineCommandOutcome.RouteToCloudGemini
    }

    fun executeAutomation(automation: TaskAutomation): String {
        val type = AutomationActionType.fromRaw(automation.actionType)
        val payload = automation.actionPayload.trim()
        return when (type) {
            AutomationActionType.ALARM -> {
                val parts = payload.split(":")
                val hour = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val min = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                if (launchAlarm(hour, min, automation.title)) "Alarm set for %02d:%02d.".format(hour, min)
                else "No compatible Clock app found."
            }
            AutomationActionType.TIMER -> {
                val mins = payload.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 180) ?: 5
                if (launchTimer(mins * 60, automation.title)) "Timer set for $mins min."
                else "No compatible Clock app found."
            }
            AutomationActionType.WHATSAPP -> {
                val launched = launchWhatsAppMessage(phone = null, message = payload)
                if (launched) "Opened WhatsApp with your prepared message."
                else "Opened system share sheet for your message."
            }
            AutomationActionType.MAPS -> {
                if (launchMaps(payload)) "Opened Maps for \"$payload\"."
                else "Could not find a Maps app."
            }
            AutomationActionType.WEB_SEARCH -> {
                val encoded = URLEncoder.encode(payload, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (startSafely(intent)) "Opened search for \"$payload\"."
                else "No browser available."
            }
            AutomationActionType.BRIEFING -> "Running real-time Gemini briefing..."
        }
    }

    fun launchWhatsAppMessage(phone: String?, message: String): Boolean {
        val encoded = URLEncoder.encode(message, "UTF-8")
        val cleanPhone = phone.orEmpty().filter { it.isDigit() }
        if (cleanPhone.isNotEmpty()) {
            val waIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$cleanPhone?text=$encoded")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startSafely(waIntent)) return true
        }
        val directWa = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(directWa)) return true

        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            "Send via WhatsApp or Messaging"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(chooser)
        return false
    }

    fun launchDialer(phoneOrQuery: String): Boolean {
        val digits = phoneOrQuery.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse("tel:$digits")
        return startSafely(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchSms(phone: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchEmail(recipient: String, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.trim()))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchCalendarEvent(title: String, description: String): Boolean {
        val startMillis = System.currentTimeMillis() + 3_600_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3_600_000L)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchAlarm(hour: Int, minute: Int, message: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchTimer(seconds: Int, label: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(intent)
    }

    fun launchSystemCamera(): Boolean {
        return startSafely(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun playMusicOrPodcast(query: String): Boolean {
        val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startSafely(mediaIntent)) return true

        val encoded = URLEncoder.encode(query, "UTF-8")
        return startSafely(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun adjustMediaVolume(direction: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun openWifiSettings(): Boolean {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return startSafely(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openBluetoothSettings(): Boolean =
        startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openDisplaySettings(): Boolean =
        startSafely(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openSoundSettings(): Boolean =
        startSafely(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun launchEcosystemApp(app: EcosystemApp): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startSafely(launchIntent)) return true
        }
        return startSafely(
            Intent(Intent.ACTION_VIEW, Uri.parse(app.fallbackUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAppByName(query: String): String? {
        val q = query.lowercase(Locale.US)
        val eco = EcosystemApp.entries.firstOrNull {
            q.contains(it.displayName.lowercase(Locale.US)) ||
                q.contains(it.name.lowercase(Locale.US).replace('_', ' '))
        }
        if (eco != null) {
            launchEcosystemApp(eco)
            return eco.displayName
        }
        if (q.contains("setting")) {
            startSafely(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Settings"
        }
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val match = apps.firstOrNull { info ->
            info.loadLabel(pm).toString().lowercase(Locale.US).contains(q)
        } ?: return null
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (startSafely(launch)) match.loadLabel(pm).toString() else null
    }

    fun launchMaps(query: String): Boolean {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startSafely(intent)
    }

    private fun startSafely(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
