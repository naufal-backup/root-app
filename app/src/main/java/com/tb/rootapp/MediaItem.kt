package com.tb.rootapp

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val isDirectory: Boolean = false,
    /** Path filesystem absolut untuk item via superuser (su). Null untuk item SAF. */
    val filePath: String? = null
) {
    private val ext: String get() = name.substringAfterLast('.', "").lowercase()

    val isImage: Boolean get() =
        mimeType?.startsWith("image/") == true ||
            ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    val isVideo: Boolean get() =
        mimeType?.startsWith("video/") == true ||
            ext in setOf("mp4", "mkv", "webm", "3gp", "avi", "mov")

    val isAudio: Boolean get() =
        mimeType?.startsWith("audio/") == true ||
            ext in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
}

enum class MediaFilter { ALL, IMAGE, VIDEO, AUDIO }
