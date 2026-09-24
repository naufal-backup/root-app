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
        val src: String = AppConfig.DEFAULT_WATCH_SRC,
        val dst: String = AppConfig.DEFAULT_WATCH_DST,
        val intervalSec: Int = 3,
        val chattr: Boolean = true
    )

    fun load(context: Context): Config {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            enabled = p.getBoolean(K_ENABLED, false),
            src = p.getString(K_SRC, AppConfig.DEFAULT_WATCH_SRC)
                ?: AppConfig.DEFAULT_WATCH_SRC,
            dst = p.getString(K_DST, AppConfig.DEFAULT_WATCH_DST) ?: AppConfig.DEFAULT_WATCH_DST,
            intervalSec = p.getInt(K_INTERVAL, AppConfig.DEFAULT_WATCH_INTERVAL).coerceIn(AppConfig.MIN_WATCH_INTERVAL, AppConfig.MAX_WATCH_INTERVAL),
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
        val trimmed = if (cur.size > AppConfig.MAX_KNOWN_WATCH) cur.toList().takeLast(AppConfig.MAX_KNOWN_WATCH).toSet() else cur
        p.edit().putStringSet(K_KNOWN, trimmed).apply()
    }

    fun resetKnown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(K_KNOWN).apply()
    }
}
