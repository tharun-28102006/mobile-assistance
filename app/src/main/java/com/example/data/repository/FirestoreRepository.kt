package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AssistantMessage
import com.example.data.model.AutomationActionType
import com.example.data.model.BatteryProfile
import com.example.data.model.DraftTone
import com.example.data.model.MessageDraft
import com.example.data.model.MessageMode
import com.example.data.model.SmartDeviceType
import com.example.data.model.SmartHomeDevice
import com.example.data.model.TaskAutomation
import com.example.data.model.TodoCategory
import com.example.data.model.TodoReminder
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeConversations(userId: String): Flow<List<AssistantMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid).collection("conversations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AssistantMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAutomations(userId: String): Flow<List<TaskAutomation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid/automations"
        emitAll(
            db.collection("users").document(uid).collection("automations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        TaskAutomation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMessageDrafts(userId: String): Flow<List<MessageDraft>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid/message_drafts"
        emitAll(
            db.collection("users").document(uid).collection("message_drafts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        MessageDraft::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfileAndStarterData(
        displayName: String,
        deviceName: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snap = ref.get().await()
            val safeName = displayName.trim().take(80).ifBlank { "Aura User" }
            val safeDevice = deviceName.trim().take(80).ifBlank { "Android Phone" }

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "wakeWord" to "Hey Aura",
                    "offlineVoicePreferred" to true,
                    "autoReadReplies" to true,
                    "batteryMode" to BatteryProfile.ECO.name,
                    "ttsSpeechRate" to 1.05,
                    "lastDeviceName" to safeDevice,
                    "recentTopics" to listOf("voice", "automations"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
                seedStarterAutomations(uid)
                seedStarterSmartDevices(uid)
            } else {
                ref.update(
                    mapOf(
                        "lastDeviceName" to safeDevice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                seedStarterSmartDevices(uid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterAutomations(uid: String) {
        val col = db.collection("users").document(uid).collection("automations")
        val existing = col.limit(1).get().await()
        if (!existing.isEmpty) return

        val starters = listOf(
            mapOf(
                "userId" to uid,
                "title" to "Morning Commute Briefing",
                "triggerPhrase" to "start my morning",
                "actionType" to AutomationActionType.BRIEFING.name,
                "actionPayload" to "Give me a crisp 3-bullet morning briefing for my current location.",
                "contextHint" to "Morning • Daily",
                "enabled" to true,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "title" to "Focus Sprint Timer",
                "triggerPhrase" to "start focus sprint",
                "actionType" to AutomationActionType.TIMER.name,
                "actionPayload" to "25",
                "contextHint" to "Deep Work • Offline",
                "enabled" to true,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "title" to "Heading Home WhatsApp",
                "triggerPhrase" to "heading home",
                "actionType" to AutomationActionType.WHATSAPP.name,
                "actionPayload" to "Hey! Just wrapping up and heading home now. See you soon!",
                "contextHint" to "Evening • Commute",
                "enabled" to true,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        starters.forEach { payload ->
            col.document().set(payload).await()
        }
    }

    suspend fun updatePreferences(
        wakeWord: String,
        offlineVoicePreferred: Boolean,
        autoReadReplies: Boolean,
        batteryMode: BatteryProfile,
        ttsSpeechRate: Double,
        deviceName: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val safeWake = wakeWord.trim().take(40).ifBlank { "Hey Aura" }
            require(safeWake.length >= 2) { "Wake word must be at least 2 characters." }
            val safeRate = ttsSpeechRate.coerceIn(0.5, 2.0)
            ref.update(
                mapOf(
                    "wakeWord" to safeWake,
                    "offlineVoicePreferred" to offlineVoicePreferred,
                    "autoReadReplies" to autoReadReplies,
                    "batteryMode" to batteryMode.name,
                    "ttsSpeechRate" to safeRate,
                    "lastDeviceName" to deviceName.trim().take(80).ifBlank { "Android Phone" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateContextualTelemetry(
        locationLabel: String?,
        recentTopics: List<String>,
        deviceName: String
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val cleanTopics = recentTopics.map { it.trim().take(32) }.filter { it.isNotBlank() }.distinct().take(10)
            val updates = buildMap<String, Any> {
                put("lastDeviceName", deviceName.trim().take(80).ifBlank { "Android Phone" })
                if (!locationLabel.isNullOrBlank()) {
                    put("lastLocationLabel", locationLabel.trim().take(120))
                }
                put("recentTopics", cleanTopics)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addConversationMessage(
        role: String,
        content: String,
        mode: MessageMode,
        deviceName: String,
        locationContext: String? = null,
        visualTag: String? = null
    ): Result<Unit> {
        var docPath = "users/unknown/conversations"
        return try {
            val uid = requireUserId()
            val safeContent = content.trim().take(7900)
            require(safeContent.isNotEmpty()) { "Message cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("conversations").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "role" to role,
                "content" to safeContent,
                "mode" to mode.name,
                "deviceName" to deviceName.trim().take(80).ifBlank { "Android Phone" },
                "locationContext" to locationContext?.trim()?.take(120)?.takeIf { it.isNotBlank() },
                "visualTag" to visualTag?.trim()?.take(120)?.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversationMessage(messageId: String): Result<Unit> {
        var docPath = "users/unknown/conversations/$messageId"
        return try {
            val uid = requireUserId()
            require(messageId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("conversations").document(messageId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createAutomation(
        title: String,
        triggerPhrase: String,
        actionType: AutomationActionType,
        actionPayload: String,
        contextHint: String?
    ): Result<Unit> {
        var docPath = "users/unknown/automations"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(80)
            val safeTrigger = triggerPhrase.trim().take(80)
            val safePayload = actionPayload.trim().take(500)
            require(safeTitle.isNotEmpty() && safeTrigger.isNotEmpty() && safePayload.isNotEmpty()) {
                "Title, trigger phrase, and action details are required."
            }
            val docRef = db.collection("users").document(uid).collection("automations").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "triggerPhrase" to safeTrigger,
                "actionType" to actionType.name,
                "actionPayload" to safePayload,
                "contextHint" to contextHint?.trim()?.take(120)?.takeIf { it.isNotBlank() },
                "enabled" to true,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleAutomation(automationId: String, enabled: Boolean): Result<Unit> {
        var docPath = "users/unknown/automations/$automationId"
        return try {
            val uid = requireUserId()
            require(automationId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("automations").document(automationId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "enabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordAutomationRun(automationId: String): Result<Unit> {
        var docPath = "users/unknown/automations/$automationId"
        return try {
            val uid = requireUserId()
            require(automationId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("automations").document(automationId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "runCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAutomation(automationId: String): Result<Unit> {
        var docPath = "users/unknown/automations/$automationId"
        return try {
            val uid = requireUserId()
            require(automationId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("automations").document(automationId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveMessageDraft(
        recipientName: String,
        recipientPhone: String?,
        body: String,
        tone: DraftTone,
        direction: String,
        status: String,
        summary: String? = null
    ): Result<String> {
        var docPath = "users/unknown/message_drafts"
        return try {
            val uid = requireUserId()
            val safeRecipient = recipientName.trim().take(80).ifBlank { "Contact" }
            val safeBody = body.trim().take(3900)
            require(safeBody.isNotEmpty()) { "Message body cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("message_drafts").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "recipientName" to safeRecipient,
                "recipientPhone" to recipientPhone?.trim()?.take(32)?.takeIf { it.isNotBlank() },
                "body" to safeBody,
                "tone" to tone.name,
                "direction" to direction,
                "status" to status,
                "summary" to summary?.trim()?.take(950)?.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markDraftStatus(draftId: String, status: String): Result<Unit> {
        var docPath = "users/unknown/message_drafts/$draftId"
        return try {
            val uid = requireUserId()
            require(draftId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("message_drafts").document(draftId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageDraft(draftId: String): Result<Unit> {
        var docPath = "users/unknown/message_drafts/$draftId"
        return try {
            val uid = requireUserId()
            require(draftId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("message_drafts").document(draftId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeTodos(userId: String): Flow<List<TodoReminder>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid/todos"
        emitAll(
            db.collection("users").document(uid).collection("todos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        TodoReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun addTodo(
        title: String,
        dueLabel: String?,
        category: TodoCategory
    ): Result<Unit> {
        var docPath = "users/unknown/todos"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(160)
            require(safeTitle.isNotEmpty()) { "Task title cannot be empty." }
            val ref = db.collection("users").document(uid).collection("todos").document()
            docPath = ref.path
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "dueLabel" to dueLabel?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                "category" to category.name,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTodoCompleted(todoId: String, completed: Boolean): Result<Unit> {
        var docPath = "users/unknown/todos/$todoId"
        return try {
            val uid = requireUserId()
            require(todoId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("todos").document(todoId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTodo(todoId: String): Result<Unit> {
        var docPath = "users/unknown/todos/$todoId"
        return try {
            val uid = requireUserId()
            require(todoId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("todos").document(todoId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeSmartDevices(userId: String): Flow<List<SmartHomeDevice>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched user session." }
        val path = "users/$uid/smart_devices"
        emitAll(
            db.collection("users").document(uid).collection("smart_devices")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(20)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SmartHomeDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private suspend fun seedStarterSmartDevices(uid: String) {
        val col = db.collection("users").document(uid).collection("smart_devices")
        val existing = col.limit(1).get().await()
        if (!existing.isEmpty) return

        val starters = listOf(
            mapOf(
                "userId" to uid,
                "name" to "Living Room Lights",
                "room" to "Living Room",
                "deviceType" to SmartDeviceType.LIGHT.name,
                "poweredOn" to true,
                "level" to 75L,
                "statusText" to "Warm Glow • 75%",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "name" to "Front Door Lock",
                "room" to "Entryway",
                "deviceType" to SmartDeviceType.LOCK.name,
                "poweredOn" to true,
                "level" to 100L,
                "statusText" to "Locked & Secure",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "name" to "Hallway Thermostat",
                "room" to "Home Climate",
                "deviceType" to SmartDeviceType.THERMOSTAT.name,
                "poweredOn" to true,
                "level" to 22L,
                "statusText" to "Eco Heat • 22°C",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "name" to "RoboVac Pro",
                "room" to "Main Floor",
                "deviceType" to SmartDeviceType.VACUUM.name,
                "poweredOn" to false,
                "level" to 96L,
                "statusText" to "Docked • 96% Battery",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "name" to "Living Room TV",
                "room" to "Living Room",
                "deviceType" to SmartDeviceType.TV.name,
                "poweredOn" to false,
                "level" to 30L,
                "statusText" to "Standby",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        starters.forEach { payload ->
            col.document().set(payload).await()
        }
    }

    suspend fun updateSmartDevice(
        deviceId: String,
        poweredOn: Boolean,
        level: Long,
        statusText: String
    ): Result<Unit> {
        var docPath = "users/unknown/smart_devices/$deviceId"
        return try {
            val uid = requireUserId()
            require(deviceId.isNotBlank())
            val ref = db.collection("users").document(uid).collection("smart_devices").document(deviceId)
            docPath = ref.path
            ref.update(
                mapOf(
                    "poweredOn" to poweredOn,
                    "level" to level.coerceIn(0L, 100L),
                    "statusText" to statusText.trim().take(80).ifBlank { if (poweredOn) "On" else "Off" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val toLog = firestoreError ?: error
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(toLog, op, path)
        }
    }
}
