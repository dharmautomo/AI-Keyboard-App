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

/**
 * Provider that talks to a secure relay exposing OpenAI-compatible SSE streaming.
 * Never embeds provider API keys in app. Uses BuildConfig.KB_RELAY_BASE_URL.
 */
class AiProviderOpenAI(
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

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
        require(relay.isNotBlank()) { "KB_RELAY_BASE_URL is empty" }

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
                close(t)
            }
        }

        val es = EventSources.createFactory(httpClient).newEventSource(req, listener)
        awaitClose { es.cancel() }
    }
}


