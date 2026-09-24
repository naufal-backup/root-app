package com.tb.rootapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service: pantau folder sumber tiap interval,
 * copy file baru ke folder simpan + opsional chattr +i.
 */
class WatchService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cfg = WatchStore.load(this)
        startForegroundCompat()
        job?.cancel()
        job = scope.launch { loop(cfg) }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, CH)
            .setContentTitle(getString(R.string.watch_notif_title))
            .setContentText(getString(R.string.watch_notif_monitoring))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, notif)
        }
    }

    private suspend fun loop(cfg: WatchStore.Config) {
        RootHelper.suMkdir(cfg.dst)
        var known = WatchStore.known(this).toMutableSet()
        // baseline: anggap isi saat ini sudah dikenal (jangan copy ulang semua)
        try {
            val first = RootHelper.ls(cfg.src).filter { !it.isDirectory }.map { it.name }
            if (known.isEmpty() && first.isNotEmpty()) {
                known.addAll(first)
                WatchStore.remember(this, first)
            }
        } catch (_: Exception) { }
        updateNotif(getString(R.string.watch_notif_watching, cfg.src))

        while (scope.isActive) {
            try {
                val cur = WatchStore.load(this)
                if (!cur.enabled) break
                val entries = RootHelper.ls(cur.src).filter { !it.isDirectory }
                val fresh = entries.filter { it.name !in known }
                if (fresh.isNotEmpty()) {
                    RootHelper.suMkdir(cur.dst)
                    var saved = 0
                    for (e in fresh) {
                        val dst = uniqueDst(cur.dst, e.name)
                        if (RootHelper.suCopy(e.path, dst)) {
                            saved++
                            if (cur.chattr) {
                                RootHelper.suChattrImmutable(e.path)
                            }
                        }
                        known.add(e.name)
                    }
                    WatchStore.remember(this, fresh.map { it.name })
                    if (saved > 0) {
                        WatchStore.addSaved(this, saved)
                        WatchLog.push(this, getString(R.string.watch_log_saved, saved, WatchStore.savedCount(this)))
                    } else {
                        WatchLog.push(this, getString(R.string.watch_log_copy_fail, fresh.size))
                    }
                    updateNotif(getString(R.string.watch_notif_saved, saved, WatchStore.savedCount(this)))
                }
                delay((cur.intervalSec.coerceIn(AppConfig.MIN_WATCH_INTERVAL, AppConfig.MAX_WATCH_INTERVAL) * 1000L))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                WatchLog.push(this, getString(R.string.watch_log_error, e.message?.take(100) ?: ""))
                delay(5000)
            }
        }
        stopSelf()
    }

    private suspend fun uniqueDst(dstDir: String, name: String): String {
        val clean = name.ifEmpty { "file" }
        var candidate = "$dstDir/$clean"
        var i = 1
        while (RootHelper.suExists(candidate) && i < 100) {
            val dot = clean.lastIndexOf('.')
            candidate = if (dot > 0) {
                "$dstDir/${clean.substring(0, dot)}_$i${clean.substring(dot)}"
            } else {
                "$dstDir/${clean}_$i"
            }
            i++
        }
        return candidate
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH)
            .setContentTitle(getString(R.string.watch_notif_title))
            .setContentText(text.take(120))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        try { nm.notify(ID, n) } catch (_: Exception) { }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH, getString(R.string.watch_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CH = AppConfig.WATCH_CHANNEL_ID
        const val ID = AppConfig.WATCH_NOTIF_ID

        fun start(context: Context) {
            val i = Intent(context, WatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}

/** Lanjutkan penjagaan setelah reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (WatchStore.load(context).enabled) {
                WatchService.start(context)
            }
        }
    }
}
