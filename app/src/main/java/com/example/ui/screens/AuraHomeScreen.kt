package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.VoiceListeningMode
import com.example.data.model.AssistantMessage
import com.example.data.model.ContextualSuggestion
import com.example.data.model.UiState
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.viewmodel.AuraViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AuraHomeScreen(
    viewModel: AuraViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.localPrefs.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val lockServiceRunning by viewModel.lockScreenServiceRunning.collectAsStateWithLifecycle()
    val lockServiceStatus by viewModel.lockScreenServiceStatus.collectAsStateWithLifecycle()
    val lockServiceMode by viewModel.lockScreenServiceMode.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val busy by viewModel.assistantBusy.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction?.invoke()
        }
        pendingMicAction = null
    }

    val lockWakePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.toggleLockScreenWake(true)
        }
    }

    fun requireMicThen(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestLockScreenWakeEnable() {
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
            viewModel.toggleLockScreenWake(true)
        } else {
            lockWakePermissionLauncher.launch(needed.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusMiniTag(
                        label = if (prefs.offlineVoicePreferred) "OFFLINE-FIRST" else "CLOUD VOICE",
                        accent = if (prefs.offlineVoicePreferred) AuraMint else AuraCyan
                    )
                    StatusMiniTag(
                        label = "${telemetry.appHeapMb}MB RAM • ${telemetry.batteryPercent}%",
                        accent = AuraCobalt
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (voiceState.speaking) {
                    IconButton(
                        onClick = { viewModel.voiceEngine.stopSpeaking() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .testTag("stop_tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = stringResource(R.string.cd_stop_speaking),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Scrollable Assistant Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            // Ambient Voice Orb + Wake Word Card
            item(key = "orb_hero") {
                val effectiveMode = when {
                    voiceState.mode != VoiceListeningMode.IDLE -> voiceState.mode
                    lockServiceRunning -> lockServiceMode
                    else -> VoiceListeningMode.IDLE
                }
                AmbientVoiceOrbCard(
                    wakeWord = prefs.wakeWord,
                    standbyActive = prefs.wakeWordStandbyEnabled || lockServiceRunning,
                    lockScreenWakeActive = lockServiceRunning,
                    lockServiceStatus = lockServiceStatus,
                    voiceMode = effectiveMode,
                    rmsLevel = voiceState.rmsLevel,
                    partialTranscript = voiceState.partialTranscript,
                    statusNotice = voiceState.statusNotice,
                    locationLabel = telemetry.locationLabel,
                    onOrbTap = {
                        if (voiceState.mode == VoiceListeningMode.ACTIVE_COMMAND) {
                            viewModel.voiceEngine.stopListening()
                        } else {
                            requireMicThen { viewModel.voiceEngine.startActiveListening() }
                        }
                    },
                    onToggleWakeStandby = {
                        val next = !prefs.wakeWordStandbyEnabled
                        if (next) {
                            requireMicThen { viewModel.toggleWakeWordStandby(true) }
                        } else {
                            viewModel.toggleWakeWordStandby(false)
                        }
                    },
                    onToggleLockScreenWake = {
                        if (lockServiceRunning) {
                            viewModel.toggleLockScreenWake(false)
                        } else {
                            requestLockScreenWakeEnable()
                        }
                    },
                    onEditWakeWord = onOpenSettings
                )
            }

            // Quick Answers & Real-Time Info Bar
            item(key = "quick_answers_bar") {
                QuickAnswersChipRow(
                    locationLabel = telemetry.locationLabel,
                    enabled = !busy,
                    onQuickPrompt = { prompt -> viewModel.submitPrompt(prompt) }
                )
            }

            // Contextual Suggestions Row
            item(key = "suggestions_row") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contextual Suggestions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !suggestionsLoading) {
                                    viewModel.refreshContextualSuggestions()
                                }
                                .testTag("refresh_suggestions_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (suggestionsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "AI Refresh",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(suggestions, key = { it.title }) { suggestion ->
                            SuggestionPillCard(
                                suggestion = suggestion,
                                onClick = { viewModel.submitPrompt(suggestion.promptOrCommand) }
                            )
                        }
                    }
                }
            }

            // Live Streaming Gemini Reply
            if (!streamingReply.isNullOrBlank() || busy) {
                item(key = "streaming_reply") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "GEMINI 3.8 FLASH • LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = streamingReply ?: "Thinking...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Synced Conversation History
            when (val state = conversationsState) {
                is UiState.Loading -> {
                    item(key = "conv_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "conv_error") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item(key = "conv_empty") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Say \"${prefs.wakeWord}\" or tap the Aura Orb",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Try \"Set a timer for 5 minutes\" (offline), \"WhatsApp Maya saying I'm on my way\", or ask Gemini anything.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.data, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                            ConversationBubbleCard(
                                message = msg,
                                onSpeak = { viewModel.voiceEngine.speak(msg.content) },
                                onDelete = { viewModel.deleteMessage(msg.id) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Prompt Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        requireMicThen { viewModel.voiceEngine.startActiveListening() }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("composer_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.cd_voice_orb),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Ask Gemini or issue an offline command...",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !busy) {
                                viewModel.submitPrompt(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_prompt_input")
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !busy) {
                            viewModel.submitPrompt(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !busy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("assistant_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send_message),
                        tint = if (inputText.isNotBlank() && !busy) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientVoiceOrbCard(
    wakeWord: String,
    standbyActive: Boolean,
    lockScreenWakeActive: Boolean,
    lockServiceStatus: String,
    voiceMode: VoiceListeningMode,
    rmsLevel: Float,
    partialTranscript: String,
    statusNotice: String?,
    locationLabel: String,
    onOrbTap: () -> Unit,
    onToggleWakeStandby: () -> Unit,
    onToggleLockScreenWake: () -> Unit,
    onEditWakeWord: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "orbPulse")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    val orbDescription = stringResource(R.string.cd_voice_orb)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (lockScreenWakeActive) AuraMint.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(26.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (standbyActive) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleWakeStandby() }
                        .testTag("wake_word_standby_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (standbyActive) Icons.Rounded.GraphicEq else Icons.Rounded.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (standbyActive) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (standbyActive) "WAKE ON: \"$wakeWord\"" else "WAKE STANDBY OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (standbyActive) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp)
                    )
                }
            }

            // Luminous Animated Orb
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = orbDescription }
                    .clickable { onOrbTap() }
                    .testTag("aura_voice_orb"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseR = size.minDimension * 0.30f
                    val boost = if (voiceMode == VoiceListeningMode.ACTIVE_COMMAND) {
                        rmsLevel * size.minDimension * 0.16f
                    } else if (voiceMode == VoiceListeningMode.WAKE_STANDBY) {
                        phase * size.minDimension * 0.08f
                    } else {
                        phase * size.minDimension * 0.04f
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AuraCyan.copy(alpha = 0.32f),
                                AuraCobalt.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseR + boost + 26.dp.toPx()
                        ),
                        radius = baseR + boost + 26.dp.toPx(),
                        center = center
                    )

                    drawCircle(
                        color = if (lockScreenWakeActive) AuraMint.copy(alpha = 0.7f) else AuraCyan.copy(alpha = 0.55f),
                        radius = baseR + boost + 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = if (voiceMode == VoiceListeningMode.ACTIVE_COMMAND) {
                                listOf(AuraMint, AuraCyan, AuraCobalt)
                            } else {
                                listOf(AuraCyan, AuraCobalt)
                            }
                        ),
                        radius = baseR + (boost * 0.4f),
                        center = center
                    )
                }

                Icon(
                    imageVector = if (voiceMode == VoiceListeningMode.ACTIVE_COMMAND) {
                        Icons.Rounded.GraphicEq
                    } else {
                        Icons.Rounded.Mic
                    },
                    contentDescription = null,
                    tint = Color(0xFF04141F),
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = when {
                    voiceMode == VoiceListeningMode.ACTIVE_COMMAND ->
                        partialTranscript.ifBlank { "Listening... speak your command" }
                    lockScreenWakeActive ->
                        lockServiceStatus
                    voiceMode == VoiceListeningMode.WAKE_STANDBY ->
                        "Listening passively for \"$wakeWord\"..."
                    else ->
                        "Tap orb to speak or say \"$wakeWord\""
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Lock-Screen Always-On Wake Word Toggle Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (lockScreenWakeActive) {
                    AuraMint.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (lockScreenWakeActive) AuraMint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onToggleLockScreenWake() }
                    .testTag("lock_screen_wake_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (lockScreenWakeActive) AuraMint else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (lockScreenWakeActive) {
                                    "Lock-Screen Wake Active"
                                } else {
                                    "Enable Lock-Screen Wake Word"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (lockScreenWakeActive) AuraMint else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lockScreenWakeActive) {
                                    "Say \"$wakeWord\" + any command even when locked"
                                } else {
                                    "Keep \"$wakeWord\" listening on lock screen & background"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = if (lockScreenWakeActive) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (lockScreenWakeActive) AuraMint else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            AnimatedVisibility(visible = !statusNotice.isNullOrBlank()) {
                Text(
                    text = statusNotice.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SuggestionPillCard(
    suggestion: ContextualSuggestion,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("suggestion_card_${suggestion.title.lowercase().replace(' ', '_')}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = suggestion.category.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConversationBubbleCard(
    message: AssistantMessage,
    onSpeak: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == "USER"
    val timeText = remember(message.createdAt) {
        val date = message.createdAt?.toDate()
        if (date != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(date) else "Just now"
    }

    Surface(
        color = if (isUser) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else "AURA GEMINI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    StatusMiniTag(
                        label = message.mode,
                        accent = if (message.mode == "OFFLINE") AuraMint else AuraCyan
                    )
                    if (!message.visualTag.isNullOrBlank()) {
                        StatusMiniTag(label = message.visualTag, accent = AuraCobalt)
                    }
                }
                Text(
                    text = "$timeText • ${message.deviceName.take(14)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isUser) {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = stringResource(R.string.cd_speak_text),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMiniTag(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (label.contains("OFFLINE")) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class QuickAnswerAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val prompt: String
)

@Composable
private fun QuickAnswersChipRow(
    locationLabel: String,
    enabled: Boolean,
    onQuickPrompt: (String) -> Unit
) {
    val area = locationLabel.takeIf { it.isNotBlank() && it != "Location standby" } ?: "my current area"
    val actions = remember(area) {
        listOf(
            QuickAnswerAction("weather", "Weather", Icons.Rounded.WbSunny, "What is the current weather outlook and what should I wear in $area today?"),
            QuickAnswerAction("traffic", "Traffic", Icons.Rounded.Traffic, "Give me a quick commute and traffic advisory tip for $area right now."),
            QuickAnswerAction("sports", "Sports", Icons.Rounded.SportsScore, "Summarize today's biggest sports headlines and top matchups."),
            QuickAnswerAction("stocks", "Stocks", Icons.Rounded.ShowChart, "Give a concise market overview covering major stock indices and tech leaders."),
            QuickAnswerAction("flights", "Flights", Icons.Rounded.Flight, "Help me check flight travel tips and best booking window for an upcoming trip from $area."),
            QuickAnswerAction("hotels", "Hotels", Icons.Rounded.Hotel, "Recommend top-rated boutique hotel types and areas to stay for a weekend getaway."),
            QuickAnswerAction("hours", "Hours", Icons.Rounded.Schedule, "What are typical business hours for pharmacies, banks, and coffee shops nearby, and how should I verify holiday hours?"),
            QuickAnswerAction("translate", "Translate", Icons.Rounded.Translate, "Translate 'Excuse me, could you help me find the nearest station?' into Spanish, French, and Japanese with pronunciation.")
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Quick Answers & Real-Time Info",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(actions, key = { it.id }) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(enabled = enabled) { onQuickPrompt(item.prompt) }
                        .testTag("quick_answer_${item.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
