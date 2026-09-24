package com.tb.rootapp

import kotlinx.coroutines.Dispatchers
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
                val wrapped =
                    "nsenter -t \$(pidof system_server) -m -- sh -c 'cat > $PATH && chmod 755 $PATH'"
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", wrapped))
                p.outputStream.use { it.write(script.toByteArray()) }
                if (p.waitFor(15, java.util.concurrent.TimeUnit.MILLISECONDS) && p.exitValue() == 0) {
                    RootHelper.execSuPublic("nohup $PATH >/dev/null 2>&1 &")
                    true
                } else {
                    // fallback tanpa nsenter
                    val p2 = Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "cat > $PATH && chmod 755 $PATH")
                    )
                    p2.outputStream.use { it.write(script.toByteArray()) }
                    val ok = p2.waitFor(15, java.util.concurrent.TimeUnit.MILLISECONDS) && p2.exitValue() == 0
                    if (ok) RootHelper.execSuPublic("nohup $PATH >/dev/null 2>&1 &")
                    ok
                }
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
