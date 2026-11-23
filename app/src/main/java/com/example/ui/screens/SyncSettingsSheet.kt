package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BatteryProfile
import com.example.data.model.UiState
import com.example.ui.theme.AuraMint
import com.example.ui.viewmodel.AuraViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncSettingsSheet(
    viewModel: AuraViewModel,
    userEmail: String,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.localPrefs.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val lockServiceRunning by viewModel.lockScreenServiceRunning.collectAsStateWithLifecycle()

    var wakeWord by remember(prefs.wakeWord) { mutableStateOf(prefs.wakeWord) }
    var lockScreenWake by remember(prefs.lockScreenWakeEnabled, lockServiceRunning) {
        mutableStateOf(prefs.lockScreenWakeEnabled || lockServiceRunning)
    }
    var offlinePreferred by remember(prefs.offlineVoicePreferred) { mutableStateOf(prefs.offlineVoicePreferred) }
    var autoRead by remember(prefs.autoReadReplies) { mutableStateOf(prefs.autoReadReplies) }
    var batteryProfile by remember(prefs.batteryMode) {
        mutableStateOf(BatteryProfile.fromRaw(prefs.batteryMode))
    }
    var speechRate by remember(prefs.ttsSpeechRate) { mutableFloatStateOf(prefs.ttsSpeechRate) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            lockScreenWake = true
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Wake Word, Efficiency & Sync",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Cross-Device Cloud Sync Status
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = AuraMint,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Phone & Desktop Cloud Sync Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$userEmail • ${telemetry.deviceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val syncedDevice = (profileState as? UiState.Success)?.data?.lastDeviceName
                        if (!syncedDevice.isNullOrBlank()) {
                            Text(
                                text = "Last cloud heartbeat: $syncedDevice",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Custom Wake Word Input
            OutlinedTextField(
                value = wakeWord,
                onValueChange = { wakeWord = it.take(40) },
                label = { Text("Custom Wake Word") },
                supportingText = { Text("Examples: \"Hey Aura\", \"Wake Nova\", \"Hello Pulse\"") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_wake_word_input")
            )

            // Lock-Screen & Background Custom Wake Word Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recognize Wake Word on Lock Screen",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Runs a low-power microphone service so saying \"${wakeWord.ifBlank { "Hey Aura" }}\" wakes Aura and follows your command even when locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = lockScreenWake,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            lockScreenWake = false
                        } else {
                            val needed = buildList {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    add(Manifest.permission.RECORD_AUDIO)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            if (needed.isEmpty()) {
                                lockScreenWake = true
                            } else {
                                permissionLauncher.launch(needed.toTypedArray())
                            }
                        }
                    },
                    modifier = Modifier.testTag("lock_screen_wake_switch")
                )
            }

            // Battery & Memory Efficiency Mode
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Background Power & Memory Profile",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatteryProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = batteryProfile == profile,
                            onClick = { batteryProfile = profile },
                            label = { Text(profile.label) }
                        )
                    }
                }
                Text(
                    text = batteryProfile.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Offline Privacy Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline-First Voice & Commands",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Prioritize on-device speech recognition & local command routing for privacy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = offlinePreferred,
                    onCheckedChange = { offlinePreferred = it },
                    modifier = Modifier.testTag("offline_voice_switch")
                )
            }

            // Hands-Free Auto-Read Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hands-Free Spoken Readout",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically speak Gemini replies, Circle results, and message summaries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoRead,
                    onCheckedChange = { autoRead = it },
                    modifier = Modifier.testTag("auto_read_switch")
                )
            }

            // Speech Speed Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Voice Readout Speed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "%.2fx".format(speechRate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.6f..1.8f
                )
            }

            Button(
                onClick = {
                    viewModel.saveAssistantPreferences(
                        wakeWord = wakeWord,
                        offlineVoicePreferred = offlinePreferred,
                        autoReadReplies = autoRead,
                        batteryProfile = batteryProfile,
                        ttsSpeechRate = speechRate,
                        lockScreenWakeEnabled = lockScreenWake
                    )
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_preferences_button")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Save & Sync Across Devices")
            }

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Sign Out")
            }
        }
    }
}
