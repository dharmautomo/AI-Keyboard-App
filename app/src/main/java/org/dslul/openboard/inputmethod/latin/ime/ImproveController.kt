package org.dslul.openboard.inputmethod.latin.ime

import android.content.Context
import android.view.View
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.data.ai.OpenAIClient
import org.dslul.openboard.inputmethod.latin.data.ai.OpenAIRepository

class ImproveController(private val context: Context, private val root: View) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null
    private var overlay: ImproveOverlayView? = null

    private var selStart: Int = -1
    private var selEnd: Int = -1

    fun open(ic: InputConnection?) {
        if (ic == null) {
            toast("Unable to open Improve")
            return
        }
        val selected = ic.getSelectedText(0)?.toString() ?: getAllText(ic)
        ensureOverlay()
        overlay?.apply {
            setText(selected)
            showLoading(true)
            setOnBack { close() }
            setOnReplace {
                val improved = getText()
                replaceSelection(ic, improved)
                close()
            }
        }
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            try {
                val repo = OpenAIRepository(OpenAIClient(context))
                val out = repo.improve(selected)
                launch(Dispatchers.Main) {
                    overlay?.showLoading(false)
                    overlay?.setText(out)
                }
            } catch (_: Throwable) {
                launch(Dispatchers.Main) {
                    overlay?.showLoading(false)
                    toast(context.getString(R.string.improve_failed))
                }
            }
        }
    }

    private fun ensureOverlay() {
        if (overlay != null) return
        val container = root as? android.view.ViewGroup ?: return
        overlay = ImproveOverlayView(context)
        // height will be managed by parent (LatinIME sizes) - add at bottom
        container.addView(overlay)
    }

    fun close() {
        val container = root as? android.view.ViewGroup ?: return
        overlay?.let { container.removeView(it) }
        overlay = null
        job?.cancel()
    }

    private fun replaceSelection(ic: InputConnection, text: String) {
        ic.beginBatchEdit()
        try {
            if (selStart >= 0 && selEnd >= selStart) {
                ic.setSelection(selStart, selEnd)
            }
            ic.deleteSurroundingText(0, 0) // no-op but keeps symmetry
            ic.commitText(text, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun getAllText(ic: InputConnection): String {
        val r = ExtractedTextRequest()
        r.hintMaxChars = 0; r.hintMaxLines = 0; r.token = 1; r.flags = 0
        val et: ExtractedText? = ic.getExtractedText(r, 0)
        selStart = et?.selectionStart ?: -1
        selEnd = et?.selectionEnd ?: -1
        return et?.text?.toString().orEmpty()
    }

    private fun toast(msg: String) {
        try { android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }
}


