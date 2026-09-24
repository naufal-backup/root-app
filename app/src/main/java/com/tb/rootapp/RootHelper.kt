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

    /**
     * Bungkus command agar jalan di mount namespace system_server.
     * su (Magisk) memakai mnt namespace berbeda → /data/data tak terlihat.
     * system_server melihat view yang benar.
     */
    private fun wrapNs(cmd: String): String {
        val safe = cmd.replace("'", "'\\''")
        return "nsenter -t \$(pidof system_server) -m -- sh -c '$safe'"
    }

    suspend fun ls(path: String): List<RootEntry> = withContext(Dispatchers.IO) {
        // pakai ls -a -p, parse trailing '/' sebagai direktori
        val out = execSu("ls -a -p \"$path\"") ?: return@withContext emptyList()
        out.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "./" && it != "../" }
            .mapNotNull { raw ->
                // ls -p menambah '/' di akhir direktori
                val isDir = raw.endsWith("/")
                val name = raw.trimEnd('/')
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                val full = if (path == "/") "/$name" else "$path/$name"
                RootEntry(name, full, isDir)
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun findMedia(root: String, maxItems: Int = 1200): List<String> =
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

    /** Cari rekursif nama file/folder di bawah root (depth dibatasi agar cepat). */
    suspend fun search(root: String, query: String, maxDepth: Int = 4, maxItems: Int = 50): List<RootEntry> =
        withContext(Dispatchers.IO) {
            val q = query.replace(Regex("[\"\\\\`$]"), "").take(40)
            if (q.isBlank()) return@withContext emptyList()
            val pattern = "*$q*"
            val dirs = execSu(
                "find \"$root\" -maxdepth $maxDepth -type d -iname \"$pattern\" 2>/dev/null | head -n $maxItems"
            )?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val files = execSu(
                "find \"$root\" -maxdepth $maxDepth -type f -iname \"$pattern\" 2>/dev/null | head -n $maxItems"
            )?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val out = ArrayList<RootEntry>(dirs.size + files.size)
            dirs.take(maxItems).forEach { p ->
                out.add(RootEntry(p.substringAfterLast('/').ifEmpty { p }, p, true))
            }
            files.take(maxItems).forEach { p ->
                out.add(RootEntry(p.substringAfterLast('/').ifEmpty { p }, p, false))
            }
            out
        }

    /**
     * Copy file root-only ke cache app.
     * `su -c cat` jalan di namespace system_server; redirection di sisi app.
     */
    suspend fun copyToCache(srcPath: String, dst: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                dst.parentFile?.mkdirs()
                val esc = srcPath
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                    .replace("`", "\\`")
                // Namespace system_server dulu, fallback su biasa
                val cmds = listOf(
                    "nsenter -t \$(pidof system_server) -m -- cat \"$esc\"",
                    "cat \"$esc\""
                )
                for (c in cmds) {
                    try {
                        dst.delete()
                        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", c))
                        p.inputStream.use { ins ->
                            dst.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                        withTimeoutOrNull(60_000) { p.waitFor() } ?: continue
                        if (p.exitValue() == 0 && dst.exists() && dst.length() > 0) {
                            return@withContext true
                        }
                    } catch (_: Exception) { }
                }
                false
            } catch (_: Exception) {
                false
            }
        }

    /** Namespace system_server dulu, fallback su biasa. */
    private fun execSu(cmd: String, timeoutMs: Long = 15_000): String? {
        return runSu(wrapNs(cmd), timeoutMs) ?: runSu(cmd, timeoutMs)
    }

    private fun runSu(shellCmd: String, timeoutMs: Long): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", shellCmd))
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
