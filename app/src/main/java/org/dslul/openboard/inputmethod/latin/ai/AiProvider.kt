package org.dslul.openboard.inputmethod.latin.ai

import kotlinx.coroutines.flow.Flow

interface AiProvider {
    data class Request(
        val action: Action,
        val textBeforeCursor: String,
        val selectedText: String?,
        val textAfterCursor: String,
        val targetLanguage: String? = null,
        val customPrompt: String? = null,
        val truncateAtChars: Int = 3000
    )

    enum class Action { REPLY, IMPROVE, GRAMMAR, TRANSLATE, CUSTOM }

    /** Returns a flow of streamed chunks; terminal chunk has isDone=true. */
    fun stream(request: Request): Flow<StreamChunk>

    data class StreamChunk(val content: String, val isDone: Boolean = false)
}


