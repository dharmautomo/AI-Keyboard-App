package org.dslul.openboard.inputmethod.latin.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Log

class AiActionController(
    private val provider: AiProvider,
    private val commitCallback: (String) -> Unit,
    private val progressCallback: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "AiActionController"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    fun runGrammar(textBefore: String, selected: String?, textAfter: String) {
        run(
            action = AiProvider.Action.GRAMMAR,
            textBefore = textBefore,
            selected = selected,
            textAfter = textAfter,
            targetLang = null,
            customPrompt = null
        )
    }

    fun run(
        action: AiProvider.Action,
        textBefore: String,
        selected: String?,
        textAfter: String,
        targetLang: String? = null,
        customPrompt: String? = null
    ) {
        Log.d(TAG, "Starting AI action: $action")
        execute(
            AiProvider.Request(
                action = action,
                textBeforeCursor = textBefore,
                selectedText = selected,
                textAfterCursor = textAfter,
                targetLanguage = targetLang,
                customPrompt = customPrompt
            )
        )
    }

    fun cancel() {
        job?.cancel()
        Log.d(TAG, "AI action cancelled")
    }

    private fun execute(req: AiProvider.Request) {
        job?.cancel()
        job = scope.launch {
            try {
                val builder = StringBuilder()
                provider.stream(req).collect { chunk ->
                    if (chunk.content.isNotEmpty()) {
                        val parsedContent = parseDelta(chunk.content)
                        if (parsedContent.isNotEmpty()) {
                            builder.append(parsedContent)
                            progressCallback(builder.toString())
                        }
                    }
                    if (chunk.isDone) {
                        val finalResult = builder.toString()
                        Log.d(TAG, "AI action completed, result length: ${finalResult.length}")
                        commitCallback(finalResult)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing AI action", e)
                // Send error message to user
                commitCallback("Sorry, an error occurred while processing your request. Please try again.")
            }
        }
    }

    // The relay is expected to forward SSE JSON lines similar to OpenAI; we extract delta content.
    private fun parseDelta(raw: String): String {
        return try {
            val obj = org.json.JSONObject(raw)
            val choices = obj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val delta = choices.getJSONObject(0).optJSONObject("delta")
                delta?.optString("content").orEmpty()
            } else obj.optString("content")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse delta content: $raw", t)
            ""
        }
    }
}


