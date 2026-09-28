package com.tb.rootapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simpan manual satu file via root ke folder simpan.
 * Return path tujuan bila sukses, null bila gagal.
 */
object ManualSave {
    suspend fun saveFile(context: Context, srcPath: String): String? =
        withContext(Dispatchers.IO) {
            val cfg = WatchStore.load(context)
            if (cfg.dst.isBlank()) return@withContext null
            RootHelper.suMkdir(cfg.dst)
            val name = srcPath.substringAfterLast('/').ifEmpty { "file" }
            val dst = RootHelper.uniquePath(cfg.dst, name)
            if (RootHelper.suCopy(srcPath, dst)) {
                WatchStore.addSaved(context)
                WatchLog.push(
                    context,
                    context.getString(
                        R.string.watch_manual_saved,
                        dst.substringAfterLast('/')
                    )
                )
                dst
            } else {
                WatchLog.push(
                    context,
                    context.getString(R.string.watch_manual_fail, name)
                )
                null
            }
        }
}
