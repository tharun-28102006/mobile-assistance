package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ContextualSuggestion
import com.example.data.model.DraftTone
import com.example.data.model.StudioWriteMode
import com.example.data.model.VisualIdentificationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiRepo"
private const val AI_TIMEOUT_MS = 55_000L

class GeminiAiRepository {

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Aura Assist, a fast, versatile mobile AI assistant built on Gemini. " +
                        "You help with quick answers (weather guidance, traffic, sports, stocks, flights, hotels, business hours, translations), " +
                        "research & learning (explaining complex topics, coding, trip planning, multi-step reasoning), and everyday tasks. " +
                        "Keep responses crisp, well-structured, and easy to read aloud unless the user asks for deep detail."
                )
            }
        )
    }

    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val visionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "objectName" to Schema.string(description = "Exact name of the circled item"),
                "category" to Schema.string(description = "Short category label"),
                "summary" to Schema.string(description = "Clear 1-2 sentence explanation of what it is"),
                "keyDetails" to Schema.array(
                    items = Schema.string(),
                    description = "3 concise facts, specs, or notable traits"
                ),
                "suggestedActions" to Schema.array(
                    items = Schema.string(),
                    description = "2 short follow-up actions the user might want"
                )
            )
        )
    }

    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = visionSchema
            }
        )
    }

    private val suggestionsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "suggestions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Short 2-4 word title"),
                            "subtitle" to Schema.string(description = "One-line contextual reason"),
                            "promptOrCommand" to Schema.string(description = "Exact prompt or command to run"),
                            "category" to Schema.string(description = "One of: Routine, Local, Message, Insight")
                        )
                    ),
                    description = "3 tailored contextual suggestions"
                )
            )
        )
    }

    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = suggestionsSchema
            }
        )
    }

    private val messageReadoutSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "spokenSummary" to Schema.string(description = "Natural 1-2 sentence hands-free readout summary"),
                "urgency" to Schema.string(description = "Low, Normal, or Urgent"),
                "suggestedReply" to Schema.string(description = "Short natural WhatsApp reply draft")
            )
        )
    }

    private val messageTriageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = messageReadoutSchema
            }
        )
    }

    fun streamAssistantReply(
        prompt: String,
        locationLabel: String,
        recentTopics: List<String>,
        recentContextHistory: List<Pair<String, String>>
    ): Flow<String> = flow {
        val contextHeader = buildString {
            append("[Context] Location: ").append(locationLabel)
            if (recentTopics.isNotEmpty()) {
                append(" | Recent topics: ").append(recentTopics.take(5).joinToString(", "))
            }
            if (recentContextHistory.isNotEmpty()) {
                append("\n[Recent Conversation]\n")
                recentContextHistory.takeLast(4).forEach { (role, msg) ->
                    append(role).append(": ").append(msg.take(220)).append('\n')
                }
            }
            append("\n[User Request]\n").append(prompt)
        }

        val buffer = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                assistantModel.generateContentStream(contextHeader).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        buffer.append(piece)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Assistant stream timed out", e)
            throw IllegalStateException("Response timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Assistant stream failed", e)
            throw e
        }
    }

    suspend fun identifyCircledObject(
        croppedRegion: Bitmap,
        fullSceneContext: Bitmap?
    ): VisualIdentificationResult {
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                visionModel.generateContent(
                    content {
                        text(
                            "The user circled an object in the image. Image 1 is the exact zoomed crop of the circled object. " +
                                if (fullSceneContext != null) "Image 2 is the broader scene for context. " else "" +
                                "Identify precisely what the circled object is in real-time with speed and accuracy."
                        )
                        image(croppedRegion)
                        if (fullSceneContext != null) {
                            image(fullSceneContext)
                        }
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("Vision model returned empty JSON.")
            val json = JSONObject(raw)
            val detailsArr = json.optJSONArray("keyDetails")
            val details = buildList {
                if (detailsArr != null) {
                    for (i in 0 until detailsArr.length()) {
                        val item = detailsArr.optString(i)
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            val actionsArr = json.optJSONArray("suggestedActions")
            val actions = buildList {
                if (actionsArr != null) {
                    for (i in 0 until actionsArr.length()) {
                        val item = actionsArr.optString(i)
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            return VisualIdentificationResult(
                objectName = json.optString("objectName", "Identified Object"),
                category = json.optString("category", "Visual Match"),
                summary = json.optString("summary", "Object analyzed via Gemini Vision."),
                keyDetails = details,
                suggestedActions = actions
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Circle vision timed out", e)
            throw IllegalStateException("Visual analysis timed out. Try circling a clearer area.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Circle vision failed", e)
            throw e
        }
    }

    suspend fun draftWhatsAppMessage(
        recipientName: String,
        intentNotes: String,
        tone: DraftTone
    ): String {
        try {
            val prompt = "Draft a ready-to-send WhatsApp message to $recipientName. " +
                "Tone: ${tone.label}. Key points: $intentNotes. " +
                "Return ONLY the final message text ready to paste into WhatsApp, no quotes or commentary."
            val response = withTimeout(AI_TIMEOUT_MS) {
                assistantModel.generateContent(prompt)
            }
            return response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Could not draft message.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "WhatsApp draft timed out", e)
            throw IllegalStateException("Message drafting timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp draft failed", e)
            throw e
        }
    }

    suspend fun summarizeIncomingMessage(
        senderName: String,
        messageText: String
    ): Triple<String, String, String> {
        try {
            val prompt = "Incoming message from $senderName:\n\"$messageText\"\n" +
                "Provide a natural hands-free spoken summary, urgency level, and a short reply."
            val response = withTimeout(AI_TIMEOUT_MS) {
                messageTriageModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Empty triage response.")
            val json = JSONObject(raw)
            return Triple(
                json.optString("spokenSummary", messageText),
                json.optString("urgency", "Normal"),
                json.optString("suggestedReply", "Got it, thanks!")
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Message summary timed out", e)
            throw IllegalStateException("Message summarization timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Message summary failed", e)
            throw e
        }
    }

    suspend fun generateContextualSuggestions(
        locationLabel: String,
        timeOfDay: String,
        recentTopics: List<String>
    ): List<ContextualSuggestion> {
        try {
            val prompt = "Generate 3 practical, concise mobile assistant suggestions for a user right now.\n" +
                "Time of day: $timeOfDay\n" +
                "Location context: $locationLabel\n" +
                "Recent user behavior topics: ${recentTopics.ifEmpty { listOf("productivity", "messaging") }.joinToString(", ")}"
            val response = withTimeout(AI_TIMEOUT_MS) {
                fastLiteModel.generateContent(prompt)
            }
            val raw = response.text ?: return emptyList()
            val arr = JSONObject(raw).optJSONArray("suggestions") ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        ContextualSuggestion(
                            title = obj.optString("title", "Quick Action"),
                            subtitle = obj.optString("subtitle", ""),
                            promptOrCommand = obj.optString("promptOrCommand", ""),
                            category = obj.optString("category", "Insight")
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Contextual suggestions failed", e)
            throw e
        }
    }

    suspend fun generateStudioWriting(
        mode: StudioWriteMode,
        userInput: String,
        locationLabel: String
    ): String {
        val instruction = when (mode) {
            StudioWriteMode.EMAIL -> "Draft a polished, ready-to-send email with a clear Subject line and body."
            StudioWriteMode.ARTICLE -> "Write a clear, well-structured short article with headings and key takeaways."
            StudioWriteMode.BIO -> "Write a compelling, authentic personal or professional bio in 2 concise options."
            StudioWriteMode.SUMMARIZE -> "Summarize the following text, article, or notes into a 1-sentence TL;DR plus 4 crisp bullet points."
            StudioWriteMode.RECIPE -> "Create a practical step-by-step recipe with prep time, ingredients list, and clear instructions."
            StudioWriteMode.TRIP -> "Create an actionable day-by-day travel itinerary with timing, highlights, dining ideas, and logistics."
            StudioWriteMode.STUDY -> "Explain this topic clearly step-by-step with a concrete example or clean code snippet."
        }
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                assistantModel.generateContent(
                    "$instruction\nUser context location: $locationLabel\n\nPrompt:\n$userInput"
                )
            }
            return response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini returned an empty result.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Studio writing timed out", e)
            throw IllegalStateException("Request timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Studio writing failed", e)
            throw e
        }
    }

    suspend fun analyzeDocumentOrFile(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        question: String
    ): String {
        require(bytes.size <= 15 * 1024 * 1024) {
            "File exceeds 15 MB inline limit. Choose a smaller PDF, image, or text document."
        }
        val prompt = question.trim().ifBlank {
            "Analyze this file ($fileName). Provide a clear summary, key findings or action items, and notable details."
        }
        try {
            val response = withTimeout(60_000L) {
                assistantModel.generateContent(
                    content {
                        inlineData(bytes, mimeType)
                        text(prompt)
                    }
                )
            }
            return response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No analysis was returned for $fileName.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Document analysis timed out", e)
            throw IllegalStateException("Document analysis timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document analysis failed", e)
            throw e
        }
    }

    suspend fun generateImageFromPrompt(promptText: String): Bitmap {
        try {
            val response = withTimeout(60_000L) {
                imageGenModel.generateContent(promptText)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            return imagePart?.image
                ?: throw IllegalStateException("No image was returned by Gemini Nano Banana 2.1.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            throw IllegalStateException("Image generation timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            if (e.localizedMessage?.contains("App Check", ignoreCase = true) == true ||
                e.localizedMessage?.contains("token is invalid", ignoreCase = true) == true
            ) {
                throw IllegalStateException(
                    "Firebase App Check rejected this debug app. Find the SDK-generated debug token " +
                        "in Android Studio Logcat and register it in Firebase project " +
                        "gen-lang-client-0441900106 under the Android app " +
                        "com.aistudio.auraassist.qvmxkr."
                )
            }
            throw e
        }
    }
}
