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

    /**
     * Update an existing entry identified by [oldShortcut]. If the [updated.shortcut]
     * belongs to another entry (case-insensitive), no change is made and false is returned.
     */
    fun update(oldShortcut: String, updated: AutoTextEntry): Boolean {
        val list = getAll().toMutableList()
        val oldIndex = list.indexOfFirst { it.shortcut.equals(oldShortcut, ignoreCase = true) }
        if (oldIndex < 0) return false
        // If shortcut changed, ensure uniqueness against all other entries
        val isSameShortcut = updated.shortcut.equals(oldShortcut, ignoreCase = true)
        if (!isSameShortcut) {
            val clash = list.anyIndexed { index, it ->
                index != oldIndex && it.shortcut.equals(updated.shortcut, ignoreCase = true)
            }
            if (clash) return false
        }
        list[oldIndex] = updated
        save(list)
        return true
    }

    private inline fun <T> List<T>.anyIndexed(predicate: (index: Int, T) -> Boolean): Boolean {
        for (i in indices) if (predicate(i, this[i])) return true
        return false
    }

    private fun save(list: List<AutoTextEntry>) {
        val raw = list.joinToString("\u0001") { it.shortcut + "\u0002" + it.message }
        prefs.edit().putString("entries", raw).apply()
    }

    fun removeMany(shortcuts: Collection<String>) {
        if (shortcuts.isEmpty()) return
        val list = getAll().filterNot { e ->
            shortcuts.any { it.equals(e.shortcut, ignoreCase = true) }
        }
        save(list)
    }
}


