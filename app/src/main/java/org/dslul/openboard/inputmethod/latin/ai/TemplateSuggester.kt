package org.dslul.openboard.inputmethod.latin.ai

import org.dslul.openboard.inputmethod.latin.SuggestedWords
import android.util.Log

/**
 * Lightweight templates provider. Later we can back this by Room/DataStore.
 * For now, keep a small in-memory list of keyword→template pairs.
 */
object TemplateSuggester {
    private const val TAG = "TemplateSuggester"

    data class Template(val trigger: String, val content: String)

    // Demo templates plus user Auto Text entries merged at query time
    private val templates: List<Template> = listOf(
        Template("brb", "Be right back."),
        Template("omw", "On my way!"),
        Template("ty", "Thank you!"),
        Template("tysm", "Thank you so much!"),
        Template("intro", "Hi, I'm using the new AI Keyboard app.")
    )

    /**
     * Returns template suggestions matching the tail token of the given textBefore.
     */
    fun suggestionsFor(textBeforeCursor: CharSequence?, max: Int = 3): SuggestedWords? {
        return try {
            val tail = lastToken(textBeforeCursor?.toString().orEmpty())
            // Do not show any auto text suggestions until user starts typing something.
        if (tail.isEmpty()) return null
        val ctx = org.dslul.openboard.inputmethod.latin.LatinIME.sInstance
        val repoEntries = try {
            if (ctx != null) org.dslul.openboard.inputmethod.latin.autotext.AutoTextRepository(ctx).getAll()
            else emptyList()
        } catch (_: Throwable) { emptyList() }
        val repoTemplates = repoEntries.map { Template(it.shortcut, it.message) }
        val all = templates + repoTemplates
        val matches = all.filter { it.trigger.startsWith(tail, ignoreCase = true) }
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
            SuggestedWords(
                ArrayList(infos),
                null,
                null,
                false,
                false,
                false,
                SuggestedWords.INPUT_STYLE_PREDICTION,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating template suggestions", e)
            null
        }
    }

    private fun lastToken(text: String): String {
        return try {
            if (text.isBlank()) ""
            else {
                val parts = text.split('\n', ' ', '\t')
                parts.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing last token", e)
            ""
        }
    }
}


