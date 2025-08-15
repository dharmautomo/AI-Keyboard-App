package org.dslul.openboard.inputmethod.latin.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AiActionController(
    private val provider: AiProvider,
    private val commitCallback: (String) -> Unit,
    private val progressCallback: (String) -> Unit = {}
) {
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
    }

    private fun execute(req: AiProvider.Request) {
        job?.cancel()
        job = scope.launch {
            val builder = StringBuilder()
            provider.stream(req).collect { chunk ->
                if (chunk.content.isNotEmpty()) {
                    builder.append(parseDelta(chunk.content))
                    progressCallback(builder.toString())
                }
                if (chunk.isDone) {
                    commitCallback(builder.toString())
                }
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
            ""
        }
    }
}


