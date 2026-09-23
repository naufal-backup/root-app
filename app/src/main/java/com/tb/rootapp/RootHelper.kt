package com.tb.rootapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class RootEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

object RootHelper {

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ls(path: String): List<RootEntry> = withContext(Dispatchers.IO) {
        // pakai ls -a -p, parse trailing '/' sebagai direktori
        val out = execSu("ls -a -p \"$path\"") ?: return@withContext emptyList()
        out.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "./" && it != "../" }
            .mapNotNull { raw ->
                // ls -p menambah '/' di akhir direktori, tapi juga untuk symlink aneh — cukup pakai itu
                val isDir = raw.endsWith("/")
                val name = raw.trimEnd('/')
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                val full = if (path == "/") "/$name" else "$path/$name"
                RootEntry(name, full, isDir)
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun findMedia(root: String, maxItems: Int = 2000): List<String> =
        withContext(Dispatchers.IO) {
            val cmd = "find \"$root\" -type f \\( " +
                "-iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o " +
                "-iname '*.webp' -o -iname '*.gif' -o -iname '*.bmp' -o " +
                "-iname '*.heic' -o -iname '*.mp4' -o -iname '*.mkv' -o " +
                "-iname '*.webm' -o -iname '*.3gp' -o -iname '*.avi' -o " +
                "-iname '*.mov' -o -iname '*.mp3' -o -iname '*.wav' -o " +
                "-iname '*.ogg' -o -iname '*.m4a' -o -iname '*.flac' -o " +
                "-iname '*.aac' \\) 2>/dev/null | head -n $maxItems"
            val out = withTimeoutOrNull(60_000) { execSu(cmd) } ?: return@withContext emptyList()
            out.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(maxItems)
        }

    /** Copy file root-only ke cache app via `su -c cat`. Return true jika sukses. */
    suspend fun copyToCache(srcPath: String, dst: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                dst.parentFile?.mkdirs()
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat \"$srcPath\""))
                p.inputStream.use { ins ->
                    dst.outputStream().use { outs -> ins.copyTo(outs) }
                }
                // drain stderr agar tidak block, tunggu exit
                withTimeoutOrNull(60_000) { p.waitFor() } ?: return@withContext false
                p.exitValue() == 0 && dst.exists() && dst.length() > 0
            } catch (_: Exception) {
                false
            }
        }

    private fun execSu(cmd: String, timeoutMs: Long = 15_000): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread { try { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } catch (_: Exception) {} }
            val tErr = Thread { try { p.errorStream.bufferedReader().forEachLine { err.appendLine(it) } } catch (_: Exception) {} }
            tOut.start(); tErr.start()
            val finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            tOut.join(2000); tErr.join(2000)
            if (!finished) { p.destroyForcibly(); return null }
            if (p.exitValue() != 0 && out.isBlank()) return null
            out.toString()
        } catch (_: Exception) {
            null
        }
    }
}
