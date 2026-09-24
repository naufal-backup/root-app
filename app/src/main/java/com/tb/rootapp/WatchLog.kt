package com.tb.rootapp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log ringkas aktivitas penjaga (memory + 30 terakhir di prefs). */
object WatchLog {
    private const val PREFS = "watch_log"
    private const val KEY = "lines"
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun load(context: Context) {
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toList()?.sorted() ?: emptyList()
        _lines.value = set.takeLast(30).map { it.substringAfter('|', it) }
    }

    fun push(context: Context, msg: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$stamp] $msg"
        _lines.value = (_lines.value + line).takeLast(30)
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cur = p.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            cur.add(System.currentTimeMillis().toString() + "|" + line)
            val trimmed = if (cur.size > 60) cur.toList().sorted().takeLast(60).toSet() else cur
            p.edit().putStringSet(KEY, trimmed).apply()
        } catch (_: Exception) { }
    }

    fun clear(context: Context) {
        _lines.value = emptyList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
