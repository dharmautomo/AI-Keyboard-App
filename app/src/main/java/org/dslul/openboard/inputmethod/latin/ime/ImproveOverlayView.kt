package org.dslul.openboard.inputmethod.latin.ime

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.dslul.openboard.inputmethod.latin.R

class ImproveOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val tvImproved: TextView
    private val btnBack: ImageButton
    private val btnReplace: MaterialButton
    private val progress: View

    init {
        LayoutInflater.from(context).inflate(R.layout.view_improve_overlay, this, true)
        tvImproved = findViewById(R.id.tvImproved)
        btnBack = findViewById(R.id.btnBack)
        btnReplace = findViewById(R.id.btnReplace)
        progress = findViewById(R.id.progress)
    }

    fun setOnBack(action: () -> Unit) { btnBack.setOnClickListener { action() } }
    fun setOnReplace(action: () -> Unit) { btnReplace.setOnClickListener { action() } }

    fun showLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnReplace.isEnabled = !loading && tvImproved.text.isNotBlank()
    }

    fun setText(text: String?) {
        tvImproved.text = text ?: ""
        btnReplace.isEnabled = text?.isNotBlank() == true
    }

    fun getText(): String = tvImproved.text?.toString() ?: ""
}


