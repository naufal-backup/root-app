package com.tb.rootapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Script penjaga di /data/adb/service.d → jalan saat boot,
 * tetap hidup walau app ditutup / force-stop.
 */
object WatchScript {
    const val NAME = "zzz_rootapp_watch.sh"
    const val PATH = "/data/adb/service.d/$NAME"

    fun build(src: String, dst: String, intervalSec: Int, chattr: Boolean): String {
        // Kutip ganda di-escape agar aman disuntik ke shell
        val q = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"") }
        // Semua $ shell ditulis ${'$'} agar tak ditelan template Kotlin
        val D = "${'$'}"
        return """
        |#!/system/bin/sh
        |# RootApp penjaga anti-hapus (otomatis, jangan edit manual)
        |SRC="${q(src)}"
        |DST="${q(dst)}"
        |INTERVAL=${intervalSec.coerceIn(2, 60)}
        |LOCKCHATTR=${if (chattr) 1 else 0}
        |(
        |mkdir -p "${D}DST"
        |while true; do
        |  for f in "${D}SRC"/*; do
        |    [ -e "${D}f" ] || continue
        |    [ -d "${D}f" ] && continue
        |    base="${D}{f##*/}"
        |    if [ ! -e "${D}DST/${D}base" ]; then
        |      cp -n "${D}f" "${D}DST/${D}base" 2>/dev/null
        |      if [ "${D}LOCKCHATTR" = 1 ]; then chattr +i "${D}f" 2>/dev/null; fi
        |    fi
        |  done
        |  sleep "${D}INTERVAL"
        |done
        |) &
        """.trimMargin()
    }

    suspend fun isInstalled(): Boolean =
        withContext(Dispatchers.IO) { RootHelper.suExists(PATH) }

    suspend fun isRunning(): Boolean =
        withContext(Dispatchers.IO) {
            RootHelper.execSuPublic("pgrep -f $NAME")?.isNotBlank() == true
        }

    /** Tulis script via stdin su, chmod 755, langsung jalankan. */
    suspend fun installAndStart(src: String, dst: String, intervalSec: Int, chattr: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val script = build(src, dst, intervalSec, chattr)
                val writers = listOf(
                    "nsenter -t \$(pidof system_server) -m -- sh -c 'cat > $PATH && chmod 755 $PATH'",
                    "cat > $PATH && chmod 755 $PATH"
                )
                var written = false
                for (w in writers) {
                    try {
                        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", w))
                        p.outputStream.use { it.write(script.toByteArray()) }
                        val done = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                        if (done && p.exitValue() == 0 && RootHelper.suExists(PATH)) {
                            written = true
                            break
                        }
                    } catch (_: Exception) { }
                }
                if (!written) return@withContext false
                RootHelper.execSuPublic("nohup $PATH >/dev/null 2>&1 &")
                delay(1500)
                isRunning()
            } catch (_: Exception) {
                false
            }
        }

    suspend fun stop(): Boolean =
        withContext(Dispatchers.IO) {
            RootHelper.execSuPublic("pkill -f $NAME") != null
        }

    suspend fun uninstall(): Boolean =
        withContext(Dispatchers.IO) {
            stop()
            RootHelper.execSuPublic("rm -f $PATH") != null && !isInstalled()
        }
}
