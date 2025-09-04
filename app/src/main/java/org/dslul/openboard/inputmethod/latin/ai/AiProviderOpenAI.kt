package org.dslul.openboard.inputmethod.latin.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
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
class AiProviderOpenAI @JvmOverloads constructor(
    private val appContext: android.content.Context,
    private val httpClient: OkHttpClient = newHttpClient(appContext)
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
        try {
            val apiKey = ApiKeyProvider.getOpenAiKey(appContext)
            val base = "https://api.openai.com"
            if (apiKey.isNullOrBlank()) {
                trySend(AiProvider.StreamChunk(content = "", isDone = true))
                close(IllegalStateException("Missing OPENAI_API_KEY"))
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

        // Try Responses API first
        val responsesJson = JSONObject()
            .put("model", "gpt-5-fast")
            .put("input", prompt)

            var req = OkRequest.Builder()
                .url(base + "/v1/responses")
                .post(responsesJson.toString().toRequestBody("application/json".toMediaType()))
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

            var es = EventSources.createFactory(httpClient).newEventSource(req, listener)
            awaitClose { es.cancel() }
        } catch (t: Throwable) {
            trySend(AiProvider.StreamChunk(content = "", isDone = true))
            close(t)
        }
    }

    companion object {
        private fun newHttpClient(context: android.content.Context): OkHttpClient {
            val apiKey = ApiKeyProvider.getOpenAiKey(context)
            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("Content-Type", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
                chain.proceed(builder.build())
            }

            val retry429Interceptor = Interceptor { chain ->
                var attempt = 0
                var resp: Response = chain.proceed(chain.request())
                while (resp.code == 429 && attempt < 1) {
                    resp.close()
                    attempt++
                    try { Thread.sleep(800L * (1 shl attempt)) } catch (_: InterruptedException) { }
                    resp = chain.proceed(chain.request())
                }
                resp
            }

            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(retry429Interceptor)
                .build()
        }
    }
}


