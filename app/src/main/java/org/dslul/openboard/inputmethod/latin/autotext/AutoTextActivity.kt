package org.dslul.openboard.inputmethod.latin.autotext

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.dslul.openboard.inputmethod.latin.R

class AutoTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_text)

        val repo = AutoTextRepository(this)
        val list = findViewById<LinearLayout>(R.id.auto_text_list)
        val add = findViewById<Button>(R.id.btn_add_auto_text)

        fun refresh() {
            list.removeAllViews()
            repo.getAll().forEach { entry ->
                val row = LayoutInflater.from(this).inflate(R.layout.item_auto_text, list, false)
                row.findViewById<TextView>(R.id.txt_shortcut).text = entry.shortcut
                row.findViewById<TextView>(R.id.txt_message).text = entry.message
                list.addView(row)
            }
        }

        add.setOnClickListener {
            val dialog = layoutInflater.inflate(R.layout.dialog_add_auto_text, null)
            val etShortcut = dialog.findViewById<EditText>(R.id.et_shortcut)
            val etMessage = dialog.findViewById<EditText>(R.id.et_message)
            val container = findViewById<ViewGroup>(android.R.id.content)
            // simple inline modal
            container.addView(dialog)
            dialog.findViewById<Button>(R.id.btn_cancel).setOnClickListener { container.removeView(dialog) }
            dialog.findViewById<Button>(R.id.btn_add).setOnClickListener {
                val s = etShortcut.text.toString().trim()
                val m = etMessage.text.toString().trim()
                if (s.isNotEmpty() && m.isNotEmpty()) {
                    repo.add(AutoTextEntry(s, m))
                    container.removeView(dialog)
                    refresh()
                }
            }
        }

        // Add small bottom padding
        findViewById<View>(R.id.root).setPadding(16, 16, 16, 16)

        refresh()
    }
}


