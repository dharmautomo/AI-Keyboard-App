package org.dslul.openboard.inputmethod.latin.autotext

import android.content.Context
import android.content.SharedPreferences

data class AutoTextEntry(val shortcut: String, val message: String)

class AutoTextRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auto_text_store", Context.MODE_PRIVATE)

    fun getAll(): List<AutoTextEntry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        return raw.split("\u0001").mapNotNull {
            val parts = it.split("\u0002")
            if (parts.size == 2) AutoTextEntry(parts[0], parts[1]) else null
        }
    }

    fun add(entry: AutoTextEntry) {
        val list = getAll().toMutableList()
        // Replace if same shortcut exists
        val idx = list.indexOfFirst { it.shortcut.equals(entry.shortcut, ignoreCase = true) }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(list)
    }

    private fun save(list: List<AutoTextEntry>) {
        val raw = list.joinToString("\u0001") { it.shortcut + "\u0002" + it.message }
        prefs.edit().putString("entries", raw).apply()
    }
}


