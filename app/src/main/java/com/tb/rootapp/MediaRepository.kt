package com.tb.rootapp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaRepository {

    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic",
        "mp4", "mkv", "webm", "3gp", "avi", "mov",
        "mp3", "wav", "ogg", "m4a", "flac", "aac"
    )

    suspend fun fetchFromRoot(
        context: Context,
        rootUri: Uri,
        maxItems: Int = AppConfig.MAX_SCAN_ITEMS
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val out = ArrayList<MediaItem>(512)
        collectRecursive(root, out, maxItems)
        out.sortBy { it.name.lowercase() }
        out
    }

    private fun collectRecursive(
        dir: DocumentFile,
        out: MutableList<MediaItem>,
        maxItems: Int
    ) {
        if (out.size >= maxItems) return
        val files = try {
            dir.listFiles()
        } catch (_: Exception) {
            return
        }
        for (f in files) {
            if (out.size >= maxItems) break
            try {
                if (f.isDirectory) {
                    collectRecursive(f, out, maxItems)
                } else if (f.isFile && isMedia(f)) {
                    out.add(
                        MediaItem(
                            uri = f.uri,
                            name = f.name ?: "unknown",
                            mimeType = f.type,
                            size = f.length()
                        )
                    )
                }
            } catch (_: Exception) {
                // skip file rusak / tanpa akses
            }
        }
    }

    private fun isMedia(file: DocumentFile): Boolean {
        val type = file.type ?: ""
        if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
            return true
        }
        val ext = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""
        return ext in mediaExtensions
    }
}
