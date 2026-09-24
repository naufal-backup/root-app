package com.tb.rootapp

/** Satu-satunya tempat konstanta app — tanpa hardcode tersebar. */
object AppConfig {
    // Browser superuser
    const val FS_ROOT = "/"
    const val DEFAULT_SU_PATH = "/data/data"
    val BROWSER_PRESETS = listOf("/", "/data/data", "/data/app", "/sdcard")

    // Penjaga anti-hapus
    const val DEFAULT_WATCH_SRC = "/data/data/com.whatsapp/files/ViewOnce"
    const val DEFAULT_WATCH_DST = "/sdcard/ViewOnceSaved"
    val WATCH_INTERVALS = listOf(2, 3, 5, 10)
    const val DEFAULT_WATCH_INTERVAL = 3
    const val MIN_WATCH_INTERVAL = 2
    const val MAX_WATCH_INTERVAL = 60

    // Media scan & grid
    const val MAX_SCAN_ITEMS = 1200
    const val PAGE_SIZE = 200
    const val PAGE_STEP = 300
    const val SEARCH_DEBOUNCE_MS = 300L
    const val BROWSER_SEARCH_DEBOUNCE_MS = 400L
    const val THUMB_SIZE_PX = 300
    const val THUMB_DIR = "su_thumbs"
    const val PREVIEW_CACHE_DIR = "su_preview"

    // Root shell
    const val SU_CMD_TIMEOUT_MS = 15_000L
    const val SU_COPY_TIMEOUT_MS = 60_000L
    const val MAX_SEARCH_RESULTS = 50
    const val SEARCH_MAX_DEPTH = 4
    const val SEARCH_QUERY_MAX = 40

    // Riwayat & cache
    const val MAX_RECENT = 6
    const val MAX_KNOWN_WATCH = 2000
    const val MAX_HISTORY = 20
    const val MAX_LOG_LINES = 30

    // Notifikasi & service
    const val WATCH_CHANNEL_ID = "watch_channel"
    const val WATCH_NOTIF_ID = 2001

    // Script root permanen
    const val SCRIPT_NAME = "zzz_rootapp_watch.sh"
    const val SCRIPT_PATH = "/data/adb/service.d/" + SCRIPT_NAME
    const val SCRIPT_WRITE_TIMEOUT_SEC = 30L

    // Tampilan
    const val RECENT_LABEL_CHARS = 28
    const val SUGGEST_COUNT = 5
}
