package com.tb.rootapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RootBrowserDialog(
    initialPath: String = "/data/data",
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(initialPath) }
    var input by remember { mutableStateOf(initialPath) }
    var entries by remember { mutableStateOf<List<RootEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rooted by remember { mutableStateOf<Boolean?>(null) }

    fun load(p: String) {
        loading = true
        error = null
        scope.launch {
            try {
                val list = RootHelper.ls(p)
                if (list.isEmpty()) error = "Kosong / tidak bisa baca: $p (perlu root?)"
                entries = list
                path = p
                input = p
            } catch (e: Exception) {
                error = "Gagal: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        rooted = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
    }
    LaunchedEffect(initialPath) { load(initialPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browser Superuser", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Text(
                    text = when (rooted) {
                        null -> "Root: mengecek…"
                        true -> "Root: OK (uid=0)"
                        false -> "Root: TIDAK ADA — HP belum root / su ditolak"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rooted == false) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Path") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { load(input) }) { Text("Go") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { load("/") }) { Text("/") }
                    OutlinedButton(onClick = { load("/data/data") }) { Text("/data/data") }
                    OutlinedButton(onClick = { load("/data/app") }) { Text("/data/app") }
                    OutlinedButton(onClick = { load("/sdcard") }) { Text("/sdcard") }
                    OutlinedButton(onClick = { load(parentOf(path)) }) { Text("↑") }
                }

                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (loading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(entries, key = { it.path }) { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = e.isDirectory) { load(e.path) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(if (e.isDirectory) "📁" else "📄")
                                Text(
                                    text = e.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onPick(path) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pakai folder ini")
                }
            }
        }
    }
}

private fun parentOf(p: String): String {
    if (p == "/") return "/"
    val t = p.trimEnd('/')
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}
