package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.EcosystemApp
import com.example.data.model.DraftTone
import com.example.data.model.MessageDraft
import com.example.data.model.UiState
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.viewmodel.AuraViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommsScreen(viewModel: AuraViewModel) {
    val commsState by viewModel.commsState.collectAsStateWithLifecycle()
    val draftsState by viewModel.draftsState.collectAsStateWithLifecycle()
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var activeQuickDialog by remember { mutableStateOf<String?>(null) } // "CALL", "SMS", "EMAIL"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Hands-Free Comms",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Calls, SMS, Email, Gemini WhatsApp drafts & hands-free message reader",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Quick Native Actions: Call, SMS, Email
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { activeQuickDialog = "CALL" },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("comms_quick_call")
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Call")
            }
            FilledTonalButton(
                onClick = { activeQuickDialog = "SMS" },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("comms_quick_sms")
            ) {
                Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("SMS")
            }
            FilledTonalButton(
                onClick = { activeQuickDialog = "EMAIL" },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("comms_quick_email")
            ) {
                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Email")
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("comms_tab_whatsapp")
            ) {
                Text("WhatsApp Drafter")
            }
            SegmentedButton(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("comms_tab_reader")
            ) {
                Text("Message Reader")
            }
        }

        if (commsState.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = commsState.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (subTab == 0) {
            // WhatsApp Message Drafter
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = commsState.recipientName,
                            onValueChange = { viewModel.updateCommsFields(recipientName = it) },
                            label = { Text("Recipient") },
                            placeholder = { Text("e.g., Mom, Alex") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wa_recipient_input")
                        )
                        OutlinedTextField(
                            value = commsState.recipientPhone,
                            onValueChange = { viewModel.updateCommsFields(recipientPhone = it) },
                            label = { Text("Phone (optional)") },
                            placeholder = { Text("+15551234567") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wa_phone_input")
                        )
                    }

                    OutlinedTextField(
                        value = commsState.intentNotes,
                        onValueChange = { viewModel.updateCommsFields(intentNotes = it) },
                        label = { Text("What should the message say?") },
                        placeholder = { Text("Running 10 minutes late because of traffic, grabbing coffee") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wa_notes_input")
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DraftTone.entries.forEach { tone ->
                            FilterChip(
                                selected = commsState.selectedTone == tone,
                                onClick = { viewModel.updateCommsFields(tone = tone) },
                                label = { Text(tone.label) }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.draftWhatsAppWithGemini() },
                        enabled = !commsState.drafting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("draft_whatsapp_button")
                    ) {
                        if (commsState.drafting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Draft WhatsApp with Gemini")
                        }
                    }

                    if (commsState.generatedDraft.isNotBlank()) {
                        OutlinedTextField(
                            value = commsState.generatedDraft,
                            onValueChange = { viewModel.updateCommsFields(generatedDraft = it) },
                            label = { Text("Ready-to-Send Draft") },
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wa_generated_draft_field")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.voiceEngine.speak(commsState.generatedDraft) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("speak_draft_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Hear It")
                            }

                            Button(
                                onClick = {
                                    viewModel.sendDraftOnWhatsApp(
                                        draftId = null,
                                        phone = commsState.recipientPhone,
                                        message = commsState.generatedDraft
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("launch_whatsapp_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Open WhatsApp")
                            }
                        }
                    }
                }
            }
        } else {
            // Hands-Free Message Reader & AI Triage
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tip: Share any WhatsApp or SMS text directly to Aura Assist from Android Share to read it aloud hands-free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = commsState.incomingSender,
                        onValueChange = { viewModel.updateCommsFields(incomingSender = it) },
                        label = { Text("Sender Name") },
                        placeholder = { Text("e.g., Priya, Project Group") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_sender_input")
                    )

                    OutlinedTextField(
                        value = commsState.incomingText,
                        onValueChange = { viewModel.updateCommsFields(incomingText = it) },
                        label = { Text("Paste or Share Any Message") },
                        placeholder = { Text("Paste a long chat or email here for Aura to summarize and read aloud...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_body_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.summarizeAndReadIncomingMessage() },
                            enabled = !commsState.summarizing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("read_and_summarize_button")
                        ) {
                            if (commsState.summarizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Summarize & Read Aloud")
                            }
                        }
                    }

                    if (!commsState.incomingSummary.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "AI SPOKEN SUMMARY • ${commsState.incomingUrgency ?: "Normal"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraMint
                                )
                                Text(
                                    text = commsState.incomingSummary.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!commsState.incomingSuggestedReply.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.updateCommsFields(
                                                recipientName = commsState.incomingSender.ifBlank { "Contact" },
                                                generatedDraft = commsState.incomingSuggestedReply.orEmpty(),
                                                intentNotes = "Reply to ${commsState.incomingSender}"
                                            )
                                            subTab = 0
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("use_suggested_reply_button")
                                    ) {
                                        Icon(Icons.Rounded.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Edit Reply in WhatsApp Drafter")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Synced Cross-Device Drafts & Reader History
        Text(
            text = "Synced Across Phone & Desktop",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val state = draftsState) {
            is UiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            is UiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Your WhatsApp drafts and read-aloud summaries will automatically sync here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    state.data.forEach { item ->
                        SyncedDraftCard(
                            draft = item,
                            onSpeak = { viewModel.voiceEngine.speak(item.summary ?: item.body) },
                            onSendWhatsApp = {
                                viewModel.sendDraftOnWhatsApp(item.id, item.recipientPhone, item.body)
                            },
                            onDelete = { viewModel.deleteDraft(item.id) }
                        )
                    }
                }
            }
        }
    }

    activeQuickDialog?.let { mode ->
        QuickCommsActionDialog(
            mode = mode,
            onDismiss = { activeQuickDialog = null },
            onDial = { phone ->
                viewModel.actionExecutor.launchDialer(phone)
                activeQuickDialog = null
            },
            onSendSms = { phone, body ->
                viewModel.actionExecutor.launchSms(phone, body)
                activeQuickDialog = null
            },
            onSendEmail = { to, subject, body ->
                viewModel.actionExecutor.launchEmail(to, subject, body)
                activeQuickDialog = null
            },
            onOpenGmail = {
                viewModel.openEcosystemApp(EcosystemApp.GMAIL)
                activeQuickDialog = null
            }
        )
    }
}

@Composable
private fun QuickCommsActionDialog(
    mode: String,
    onDismiss: () -> Unit,
    onDial: (String) -> Unit,
    onSendSms: (String, String) -> Unit,
    onSendEmail: (String, String, String) -> Unit,
    onOpenGmail: () -> Unit
) {
    var target by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    "CALL" -> "Make a Phone Call"
                    "SMS" -> "Send Text Message (SMS)"
                    else -> "Draft or Check Email"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = {
                        Text(if (mode == "EMAIL") "Recipient Email" else "Phone Number")
                    },
                    placeholder = {
                        Text(if (mode == "EMAIL") "name@example.com" else "+15551234567")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode == "EMAIL") {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (mode != "CALL") {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message Body") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (mode == "EMAIL") {
                    TextButton(onClick = onOpenGmail) {
                        Text("Check Inbox in Gmail →")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        "CALL" -> onDial(target)
                        "SMS" -> onSendSms(target, body)
                        else -> onSendEmail(target, subject.ifBlank { "Message via Aura Assist" }, body)
                    }
                },
                enabled = target.isNotBlank() || mode == "EMAIL"
            ) {
                Text(
                    when (mode) {
                        "CALL" -> "Open Dialer"
                        "SMS" -> "Open SMS"
                        else -> "Compose Email"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SyncedDraftCard(
    draft: MessageDraft,
    onSpeak: () -> Unit,
    onSendWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncoming = draft.direction == "INCOMING_READER"
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isIncoming) Icons.Rounded.MarkChatRead else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        tint = if (isIncoming) AuraMint else AuraCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = draft.recipientName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${draft.status} • ${draft.tone}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = draft.summary ?: draft.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSpeak, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.cd_speak_text),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!isIncoming) {
                    IconButton(onClick = onSendWhatsApp, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send on WhatsApp",
                            tint = AuraMint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
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
