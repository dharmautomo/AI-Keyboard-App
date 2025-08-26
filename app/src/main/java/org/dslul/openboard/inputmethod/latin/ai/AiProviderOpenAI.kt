package org.dslul.openboard.inputmethod.latin.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkRequest
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.json.JSONObject
import android.util.Log

/**
 * Provider that talks to a secure relay exposing OpenAI-compatible SSE streaming.
 * Never embeds provider API keys in app. Uses BuildConfig.KB_RELAY_BASE_URL.
 */
class AiProviderOpenAI(
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    companion object {
        private const val TAG = "AiProviderOpenAI"
    }

    private fun actionToInstruction(action: AiProvider.Action, targetLanguage: String?, custom: String?): String {
        return when (action) {
            AiProvider.Action.GRAMMAR -> "Fix grammar, spelling, and clarity. Preserve meaning. Return only the corrected text."
            AiProvider.Action.IMPROVE -> "Improve tone and clarity while preserving meaning. Return only the improved text."
            AiProvider.Action.REPLY -> "Draft a concise, polite reply. Return only the reply text."
            AiProvider.Action.TRANSLATE -> "Translate to ${targetLanguage ?: "English"}. Return only the translation."
            AiProvider.Action.CUSTOM -> custom ?: ""
        }
    }

    override fun stream(request: AiProvider.Request): Flow<AiProvider.StreamChunk> = callbackFlow {
        val relay = BuildConfig.KB_RELAY_BASE_URL
        
        // Check if relay URL is available
        if (relay.isBlank()) {
            Log.w(TAG, "KB_RELAY_BASE_URL is empty, AI features are disabled")
            // Return empty result instead of crashing
            trySend(AiProvider.StreamChunk(content = "AI features are currently disabled. Please configure KB_RELAY_BASE_URL.", isDone = true))
            close()
            return@callbackFlow
        }

        // Check if this is a dummy URL (for development/testing)
        if (relay.contains("httpbin.org") || relay.contains("example.com") || relay.contains("dummy")) {
            Log.i(TAG, "Using dummy URL for development: $relay")
            // Return a mock response for development
            val mockResponse = generateMockResponse(request.action, request.selectedText ?: request.textBeforeCursor)
            trySend(AiProvider.StreamChunk(content = mockResponse, isDone = true))
            close()
            return@callbackFlow
        }

        val before = request.textBeforeCursor.takeLast(request.truncateAtChars)
        val after = request.textAfterCursor.take(request.truncateAtChars)

        val prompt = buildString {
            append(actionToInstruction(request.action, request.targetLanguage, request.customPrompt))
            append("\n\n")
            if (request.selectedText?.isNotBlank() == true) {
                append("Selected:\n")
                append(request.selectedText)
            } else {
                append("Context before:\n")
                append(before)
                append("\n\nContext after:\n")
                append(after)
            }
        }

        val json = JSONObject()
            .put("model", "gpt-5")
            .put("stream", true)
            .put("messages", listOf(
                mapOf("role" to "system", "content" to "You are a helpful keyboard assistant."),
                mapOf("role" to "user", "content" to prompt)
            ))

        val req = OkRequest.Builder()
            .url(relay.trimEnd('/') + "/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(AiProvider.StreamChunk(content = "", isDone = true))
                    close()
                    return
                }
                trySend(AiProvider.StreamChunk(content = data, isDone = false))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "AI request failed", t)
                // Send error message instead of crashing
                trySend(AiProvider.StreamChunk(content = "Sorry, AI service is currently unavailable. Please try again later.", isDone = true))
                close(t)
            }
        }

        val es = EventSources.createFactory(httpClient).newEventSource(req, listener)
        awaitClose { es.cancel() }
    }

    private fun generateMockResponse(action: AiProvider.Action, text: String): String {
        return when (action) {
            AiProvider.Action.GRAMMAR -> {
                if (text.isNotBlank()) {
                    "This is a mock grammar correction for: \"$text\". In a real implementation, this would be the corrected text."
                } else {
                    "Mock grammar correction: Please provide some text to correct."
                }
            }
            AiProvider.Action.IMPROVE -> {
                if (text.isNotBlank()) {
                    "This is a mock improvement for: \"$text\". In a real implementation, this would be the improved version."
                } else {
                    "Mock improvement: Please provide some text to improve."
                }
            }
            AiProvider.Action.REPLY -> {
                "This is a mock reply. In a real implementation, this would be an AI-generated response based on the context."
            }
            AiProvider.Action.TRANSLATE -> {
                "This is a mock translation. In a real implementation, this would be the translated text."
            }
            AiProvider.Action.CUSTOM -> {
                "This is a mock custom response. In a real implementation, this would be based on your custom prompt."
            }
        }
    }
}


