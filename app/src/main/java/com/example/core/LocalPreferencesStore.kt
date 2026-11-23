package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.auraDataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_local_prefs")

data class LocalAssistantPrefs(
    val wakeWord: String = "Hey Aura",
    val offlineVoicePreferred: Boolean = true,
    val autoReadReplies: Boolean = true,
    val batteryMode: String = "ECO",
    val ttsSpeechRate: Float = 1.05f,
    val wakeWordStandbyEnabled: Boolean = false,
    val lockScreenWakeEnabled: Boolean = false
)

class LocalPreferencesStore(private val context: Context) {
    private object Keys {
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val OFFLINE_PREFERRED = booleanPreferencesKey("offline_preferred")
        val AUTO_READ = booleanPreferencesKey("auto_read_replies")
        val BATTERY_MODE = stringPreferencesKey("battery_mode")
        val TTS_RATE = floatPreferencesKey("tts_speech_rate")
        val WAKE_STANDBY = booleanPreferencesKey("wake_word_standby")
        val LOCK_SCREEN_WAKE = booleanPreferencesKey("lock_screen_wake")
    }

    val prefsFlow: Flow<LocalAssistantPrefs> = context.auraDataStore.data.map { prefs ->
        LocalAssistantPrefs(
            wakeWord = prefs[Keys.WAKE_WORD] ?: "Hey Aura",
            offlineVoicePreferred = prefs[Keys.OFFLINE_PREFERRED] ?: true,
            autoReadReplies = prefs[Keys.AUTO_READ] ?: true,
            batteryMode = prefs[Keys.BATTERY_MODE] ?: "ECO",
            ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.05f,
            wakeWordStandbyEnabled = prefs[Keys.WAKE_STANDBY] ?: false,
            lockScreenWakeEnabled = prefs[Keys.LOCK_SCREEN_WAKE] ?: false
        )
    }

    suspend fun save(
        wakeWord: String,
        offlineVoicePreferred: Boolean,
        autoReadReplies: Boolean,
        batteryMode: String,
        ttsSpeechRate: Float,
        wakeWordStandbyEnabled: Boolean? = null,
        lockScreenWakeEnabled: Boolean? = null
    ) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD] = wakeWord
            prefs[Keys.OFFLINE_PREFERRED] = offlineVoicePreferred
            prefs[Keys.AUTO_READ] = autoReadReplies
            prefs[Keys.BATTERY_MODE] = batteryMode
            prefs[Keys.TTS_RATE] = ttsSpeechRate
            if (wakeWordStandbyEnabled != null) {
                prefs[Keys.WAKE_STANDBY] = wakeWordStandbyEnabled
            }
            if (lockScreenWakeEnabled != null) {
                prefs[Keys.LOCK_SCREEN_WAKE] = lockScreenWakeEnabled
            }
        }
    }

    suspend fun setWakeStandby(enabled: Boolean) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.WAKE_STANDBY] = enabled
        }
    }

    suspend fun setLockScreenWake(enabled: Boolean) {
        context.auraDataStore.edit { prefs ->
            prefs[Keys.LOCK_SCREEN_WAKE] = enabled
        }
    }
}
