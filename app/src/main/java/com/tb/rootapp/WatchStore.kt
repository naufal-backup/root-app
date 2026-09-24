package com.tb.rootapp

import android.content.Context

/** Konfigurasi watcher anti-hapus, tersimpan permanen. */
object WatchStore {
    private const val PREFS = "watch_cfg"
    private const val K_ENABLED = "enabled"
    private const val K_SRC = "src"
    private const val K_DST = "dst"
    private const val K_INTERVAL = "interval"
    private const val K_CHATTR = "chattr"
    private const val K_SAVED = "saved_count"
    private const val K_KNOWN = "known"

    data class Config(
        val enabled: Boolean = false,
        val src: String = "/data/data/com.whatsapp/files/ViewOnce",
        val dst: String = "/sdcard/ViewOnceSaved",
        val intervalSec: Int = 3,
        val chattr: Boolean = true
    )

    fun load(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            enabled = p.getBoolean(K_ENABLED, false),
            src = p.getString(K_SRC, "/data/data/com.whatsapp/files/ViewOnce")
                ?: "/data/data/com.whatsapp/files/ViewOnce",
            dst = p.getString(K_DST, "/sdcard/ViewOnceSaved") ?: "/sdcard/ViewOnceSaved",
            intervalSec = p.getInt(K_INTERVAL, 3).coerceIn(2, 60),
            chattr = p.getBoolean(K_CHATTR, true)
        )
    }

    fun save(context: Context, cfg: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ENABLED, cfg.enabled)
            .putString(K_SRC, cfg.src)
            .putString(K_DST, cfg.dst)
            .putInt(K_INTERVAL, cfg.intervalSec)
            .putBoolean(K_CHATTR, cfg.chattr)
            .apply()
    }

    fun savedCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_SAVED, 0)

    fun addSaved(context: Context, n: Int = 1) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt(K_SAVED, p.getInt(K_SAVED, 0) + n).apply()
    }

    fun known(context: Context): MutableSet<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(K_KNOWN, emptySet())?.toMutableSet() ?: mutableSetOf()

    fun remember(context: Context, names: Collection<String>) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = p.getStringSet(K_KNOWN, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.addAll(names)
        // batasi agar prefs tidak membengkak
        val trimmed = if (cur.size > 2000) cur.toList().takeLast(2000).toSet() else cur
        p.edit().putStringSet(K_KNOWN, trimmed).apply()
    }

    fun resetKnown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(K_KNOWN).apply()
    }
}
