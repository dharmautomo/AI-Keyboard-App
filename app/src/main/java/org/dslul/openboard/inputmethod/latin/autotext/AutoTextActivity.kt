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

        // State for multi-select mode
        var selectionMode = false
        val selected = linkedSetOf<String>()

        fun refresh() {
            list.removeAllViews()
            repo.getAll().forEach { entry ->
                val row = LayoutInflater.from(this).inflate(R.layout.item_auto_text, list, false)
                val txtShortcut = row.findViewById<TextView>(R.id.txt_shortcut)
                val txtMessage = row.findViewById<TextView>(R.id.txt_message)
                val checkbox = row.findViewById<android.widget.CheckBox>(R.id.checkbox)
                txtShortcut.text = entry.shortcut
                txtMessage.text = entry.message

                // Configure selection visuals
                fun syncSelectionViews() {
                    checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
                    val isChecked = selected.contains(entry.shortcut)
                    checkbox.isChecked = isChecked
                    row.setBackgroundColor(if (isChecked) 0x2233B5E5 else 0x00000000)
                }
                syncSelectionViews()

                row.setOnLongClickListener {
                    selectionMode = true
                    if (!selected.contains(entry.shortcut)) selected.add(entry.shortcut)
                    syncSelectionViews()
                    showDeleteBarIfNeeded(repo, selected) { // onDeleted
                        selectionMode = false
                        selected.clear()
                        refresh()
                    }
                    true
                }

                row.setOnClickListener {
                    if (selectionMode) {
                        if (!selected.add(entry.shortcut)) selected.remove(entry.shortcut)
                        syncSelectionViews()
                        showDeleteBarIfNeeded(repo, selected) {
                            selectionMode = false
                            selected.clear()
                            refresh()
                        }
                    } else {
                        showEditDialog(repo, entry) { refresh() }
                    }
                }

                checkbox.setOnClickListener {
                    if (!selected.add(entry.shortcut)) selected.remove(entry.shortcut)
                    syncSelectionViews()
                    showDeleteBarIfNeeded(repo, selected) {
                        selectionMode = false
                        selected.clear()
                        refresh()
                    }
                }

                list.addView(row)
            }
        }

        add.setOnClickListener {
            showAddDialog(repo) { refresh() }
        }

        // Add small bottom padding
        findViewById<View>(R.id.root).setPadding(16, 16, 16, 16)

        refresh()
    }

    private fun showDeleteBarIfNeeded(repo: AutoTextRepository, selected: LinkedHashSet<String>, onDeleted: () -> Unit) {
        // Reuse the existing content root to attach a simple floating bar
        val container = findViewById<ViewGroup>(android.R.id.content)
        // Remove any existing bar first
        val existing = container.findViewWithTag<View>("delete_bar")
        if (selected.isEmpty()) {
            if (existing != null) container.removeView(existing)
            return
        }
        val bar = existing ?: layoutInflater.inflate(R.layout.simple_delete_bar, container, false).apply {
            tag = "delete_bar"
        }
        // Update count label
        val label = bar.findViewById<TextView>(R.id.txt_count)
        label.text = selected.size.toString()
        // Wire delete action
        bar.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            // Confirmation prompt
            val confirm = layoutInflater.inflate(R.layout.simple_confirm_dialog, container, false)
            confirm.findViewById<TextView>(R.id.txt_message).text = getString(R.string.confirm_delete_auto_texts)
            confirm.findViewById<Button>(R.id.btn_cancel).setOnClickListener { container.removeView(confirm) }
            confirm.findViewById<Button>(R.id.btn_ok).setOnClickListener {
                repo.removeMany(selected)
                container.removeView(confirm)
                container.removeView(bar)
                onDeleted()
            }
            container.addView(confirm)
        }
        if (existing == null) container.addView(bar)
    }

    private fun showAddDialog(repo: AutoTextRepository, onSaved: () -> Unit) {
        val dialog = layoutInflater.inflate(R.layout.dialog_add_auto_text, null)
        val etShortcut = dialog.findViewById<EditText>(R.id.et_shortcut)
        val etMessage = dialog.findViewById<EditText>(R.id.et_message)
        val btnPrimary = dialog.findViewById<Button>(R.id.btn_add)
        btnPrimary.text = getString(R.string.add)
        val container = findViewById<ViewGroup>(android.R.id.content)
        container.addView(dialog)
        dialog.findViewById<Button>(R.id.btn_cancel).setOnClickListener { container.removeView(dialog) }
        btnPrimary.setOnClickListener {
            val s = etShortcut.text.toString().trim()
            val m = etMessage.text.toString().trim()
            if (s.isNotEmpty() && m.isNotEmpty()) {
                repo.add(AutoTextEntry(s, m))
                container.removeView(dialog)
                onSaved()
            }
        }
    }

    private fun showEditDialog(repo: AutoTextRepository, original: AutoTextEntry, onSaved: () -> Unit) {
        val dialog = layoutInflater.inflate(R.layout.dialog_add_auto_text, null)
        val etShortcut = dialog.findViewById<EditText>(R.id.et_shortcut)
        val etMessage = dialog.findViewById<EditText>(R.id.et_message)
        val title = dialog.findViewById<TextView>(R.id.title)
        val btnPrimary = dialog.findViewById<Button>(R.id.btn_add)
        // Pre-fill
        etShortcut.setText(original.shortcut)
        etMessage.setText(original.message)
        // Update title and button
        title?.text = getString(R.string.edit)
        btnPrimary.text = getString(R.string.save)
        val container = findViewById<ViewGroup>(android.R.id.content)
        container.addView(dialog)
        dialog.findViewById<Button>(R.id.btn_cancel).setOnClickListener { container.removeView(dialog) }
        btnPrimary.setOnClickListener {
            val newShortcut = etShortcut.text.toString().trim()
            val newMessage = etMessage.text.toString().trim()
            if (newShortcut.isNotEmpty() && newMessage.isNotEmpty()) {
                val ok = repo.update(original.shortcut, AutoTextEntry(newShortcut, newMessage))
                if (ok) {
                    container.removeView(dialog)
                    onSaved()
                } else {
                    // Duplicate shortcut; keep dialog open and show a minimal inline error
                    etShortcut.error = getString(R.string.custom_input_style_already_exists, newShortcut)
                }
            }
        }
    }
}


