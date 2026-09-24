package com.tb.rootapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Kartu penjaga anti-hapus: pilih sumber + tujuan, interval,
 * kunci +i, start/stop, status & log.
 */
@Composable
fun WatchCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf(WatchStore.load(context)) }
    var saved by remember { mutableStateOf(WatchStore.savedCount(context)) }
    var pickingSrc by remember { mutableStateOf(false) }
    var pickingDst by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val logs by WatchLog.lines.collectAsState()

    LaunchedEffect(Unit) {
        WatchLog.load(context)
        saved = WatchStore.savedCount(context)
    }

    fun persist(next: WatchStore.Config) {
        cfg = next
        WatchStore.save(context, next)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🛡 Penjaga anti-hapus", style = MaterialTheme.typography.titleSmall)
            Text(
                "File baru di folder sumber langsung disalin ke folder simpan + dikunci (chattr +i) sebelum sempat dihapus.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = cfg.src,
                onValueChange = { persist(cfg.copy(src = it)) },
                label = { Text("Folder sumber (diawasi)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickingSrc = true },
                    modifier = Modifier.weight(1f)
                ) { Text("📁 Pilih sumber") }
                OutlinedButton(
                    onClick = { pickingDst = true },
                    modifier = Modifier.weight(1f)
                ) { Text("💾 Pilih simpan") }
            }
            OutlinedTextField(
                value = cfg.dst,
                onValueChange = { persist(cfg.copy(dst = it)) },
                label = { Text("Folder simpan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cek tiap:", style = MaterialTheme.typography.bodySmall)
                listOf(2, 3, 5, 10).forEach { s ->
                    FilterChip(
                        selected = cfg.intervalSec == s,
                        onClick = { persist(cfg.copy(intervalSec = s)) },
                        label = { Text("${s}s") }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = cfg.chattr,
                    onCheckedChange = { persist(cfg.copy(chattr = it)) }
                )
                Text("Kunci file asli (+i) setelah disalin", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    busy = true
                    msg = null
                    scope.launch {
                        try {
                            if (cfg.enabled) {
                                persist(cfg.copy(enabled = false))
                                WatchService.stop(context)
                                msg = "Penjaga dihentikan."
                            } else {
                                if (cfg.src.isBlank() || cfg.dst.isBlank()) {
                                    msg = "Isi folder sumber & simpan dulu."
                                } else if (!RootHelper.isRootAvailable()) {
                                    msg = "Butuh akses root (su)."
                                } else {
                                    WatchStore.resetKnown(context)
                                    persist(cfg.copy(enabled = true))
                                    WatchService.start(context)
                                    saved = WatchStore.savedCount(context)
                                    msg = "Penjaga jalan — pantau ${cfg.src}"
                                }
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (cfg.enabled) "⏹ Hentikan penjaga" else "▶ Jalankan penjaga")
            }

            Text(
                "Status: ${if (cfg.enabled) "JALAN" else "berhenti"} • $saved file diamankan",
                style = MaterialTheme.typography.bodySmall
            )
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            ScriptSection(cfg)

            if (logs.isNotEmpty()) {
                Text("Log:", style = MaterialTheme.typography.labelMedium)
                logs.takeLast(5).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (pickingSrc) {
        RootBrowserDialog(
            initialPath = cfg.src.ifBlank { "/data/data" },
            onPick = { p ->
                persist(cfg.copy(src = p))
                pickingSrc = false
            },
            onDismiss = { pickingSrc = false }
        )
    }
    if (pickingDst) {
        RootBrowserDialog(
            initialPath = cfg.dst.ifBlank { "/sdcard" },
            onPick = { p ->
                persist(cfg.copy(dst = p))
                pickingDst = false
            },
            onDismiss = { pickingDst = false }
        )
    }
}

/**
 * Script root permanen di /data/adb/service.d:
 * jalan saat boot & tetap hidup walau app ditutup total.
 */
@Composable
fun ScriptSection(cfg: WatchStore.Config) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<Boolean?>(null) }
    var running by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            installed = WatchScript.isInstalled()
            running = if (installed == true) WatchScript.isRunning() else false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Mode permanen (script root):", style = MaterialTheme.typography.labelMedium)
        Text(
            "Script: " + when (installed) {
                null -> "mengecek…"
                true -> "terpasang"
                false -> "belum dipasang"
            } + " • " + when (running) {
                null -> ""
                true -> "JALAN walau app ditutup"
                false -> "berhenti"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    busy = true
                    msg = null
                    scope.launch {
                        val ok = WatchScript.installAndStart(
                            cfg.src, cfg.dst, cfg.intervalSec, cfg.chattr
                        )
                        msg = if (ok) "Script dipasang + jalan." else "Gagal — butuh root + Magisk."
                        refresh()
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("⚙ Pasang + jalan") }
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        WatchScript.stop()
                        refresh()
                        msg = "Script dihentikan."
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("⏹ Stop") }
        }
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    val ok = WatchScript.uninstall()
                    msg = if (ok) "Script dihapus." else "Gagal hapus."
                    refresh()
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗑 Hapus script permanen") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
