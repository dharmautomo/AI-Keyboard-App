package org.dslul.openboard.inputmethod.latin.data.ai

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkRequest
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.dslul.openboard.inputmethod.latin.ai.ApiKeyProvider

class OpenAIClient(private val context: android.content.Context) {
    companion object {
        const val MODEL = "gpt-5-fast"
        private const val BASE = "https://api.openai.com"
    }

    private fun httpClient(): OkHttpClient {
        val apiKey = ApiKeyProvider.getOpenAiKey(context)
        val auth = Interceptor { chain ->
            val b = chain.request().newBuilder()
                .header("Content-Type", "application/json")
            if (!apiKey.isNullOrBlank()) b.header("Authorization", "Bearer $apiKey")
            chain.proceed(b.build())
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .build()
    }

    fun improveBlocking(text: String): String {
        val safe = text.take(4000)
        val prompt = "You are a writing assistant. Improve clarity, grammar, and tone.\n" +
                "Keep meaning. Keep language the same. Do not add emojis.\n" +
                "Text: \"\"\"$safe\"\"\""
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", listOf(
                mapOf("role" to "system", "content" to "You are a concise writing improver."),
                mapOf("role" to "user", "content" to prompt)
            ))
            .put("temperature", 0.2)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = OkRequest.Builder()
            .url("$BASE/v1/chat/completions")
            .post(body)
            .build()
        httpClient().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${'$'}{resp.code}")
            val s = resp.body?.string().orEmpty()
            val jo = JSONObject(s)
            val choices = jo.optJSONArray("choices")
            if (choices == null || choices.length() == 0) return ""
            val msg = choices.getJSONObject(0)?.optJSONObject("message")
            return msg?.optString("content")?.trim().orEmpty()
        }
    }
}


