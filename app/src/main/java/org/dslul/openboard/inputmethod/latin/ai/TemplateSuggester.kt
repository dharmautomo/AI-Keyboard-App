package org.dslul.openboard.inputmethod.latin.ai

import org.dslul.openboard.inputmethod.latin.SuggestedWords

/**
 * Lightweight templates provider. Later we can back this by Room/DataStore.
 * For now, keep a small in-memory list of keyword→template pairs.
 */
object TemplateSuggester {
    data class Template(val trigger: String, val content: String)

    // Demo templates plus user Auto Text entries merged at query time
    private val templates: List<Template> = listOf(
        Template("brb", "Be right back."),
        Template("omw", "On my way!"),
        Template("ty", "Thank you!"),
        Template("tysm", "Thank you so much!"),
        Template("intro", "Hi, I’m using the new AI Keyboard app.")
    )

    /**
     * Returns template suggestions matching the tail token of the given textBefore.
     */
    fun suggestionsFor(textBeforeCursor: CharSequence?, max: Int = 3): SuggestedWords? {
        val tail = lastToken(textBeforeCursor?.toString().orEmpty())
        val ctx = org.dslul.openboard.inputmethod.latin.LatinIME.sInstance
        val repoEntries = try {
            if (ctx != null) org.dslul.openboard.inputmethod.latin.autotext.AutoTextRepository(ctx).getAll()
            else emptyList()
        } catch (_: Throwable) { emptyList() }
        val repoTemplates = repoEntries.map { Template(it.shortcut, it.message) }
        val all = templates + repoTemplates
        val matches = if (tail.isEmpty()) repoTemplates else all.filter { it.trigger.startsWith(tail, ignoreCase = true) }
        if (matches.isEmpty()) return null

        val infos = matches.take(max).map {
            SuggestedWords.SuggestedWordInfo(
                it.content,
                "",
                SuggestedWords.SuggestedWordInfo.MAX_SCORE,
                SuggestedWords.SuggestedWordInfo.KIND_PREDICTION,
                null,
                SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        }
        return SuggestedWords(
            ArrayList(infos),
            null,
            null,
            false,
            false,
            false,
            SuggestedWords.INPUT_STYLE_PREDICTION,
            SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
    }

    private fun lastToken(text: String): String {
        if (text.isBlank()) return ""
        val parts = text.split('\n', ' ', '\t')
        return parts.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
}


