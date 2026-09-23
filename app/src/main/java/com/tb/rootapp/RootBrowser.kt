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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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
    var browserQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RootEntry>>(emptyList()) }

    // cari folder + isi subfolder (debounce, depth ≤4)
    LaunchedEffect(browserQuery, path) {
        if (browserQuery.isBlank()) { searchResults = emptyList(); return@LaunchedEffect }
        delay(400)
        searching = true
        try {
            searchResults = RootHelper.search(path, browserQuery)
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            searching = false
        }
    }

    val searchingNow = browserQuery.isNotBlank()
    val shown = remember(entries, browserQuery) {
        if (browserQuery.isBlank()) entries
        else entries.filter { it.name.contains(browserQuery, ignoreCase = true) }
    }

    fun load(p: String) {
        loading = true
        error = null
        browserQuery = ""
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Header rapi: back = naik 1 level, X = tutup
                TopAppBar(
                    navigationIcon = {
                        TextButton(
                            onClick = { if (path != "/") load(parentOf(path)) },
                            enabled = path != "/"
                        ) { Text("←") }
                    },
                    title = {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        TextButton(onClick = onDismiss) { Text("✕") }
                    }
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        OutlinedButton(onClick = { load("/data/data") }) { Text("data") }
                        OutlinedButton(onClick = { load("/data/app") }) { Text("app") }
                        OutlinedButton(onClick = { load("/sdcard") }) { Text("sdcard") }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    OutlinedTextField(
                        value = browserQuery,
                        onValueChange = { browserQuery = it },
                        label = { Text("Cari folder + isi subfolder…") },
                        leadingIcon = { Text("🔍") },
                        trailingIcon = {
                            if (browserQuery.isNotEmpty()) {
                                TextButton(onClick = { browserQuery = "" }) { Text("✕") }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (loading) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (searchingNow) {
                        if (searching) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Text(
                                "Hasil di folder + subfolder (${searchResults.size}):",
                                style = MaterialTheme.typography.bodySmall
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(searchResults, key = { it.path }) { e ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = e.isDirectory) { load(e.path) }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(if (e.isDirectory) "📁" else "📄")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = e.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val rel = e.path.removePrefix(path).trimStart('/')
                                            if (rel != e.name && rel.isNotEmpty()) {
                                                Text(
                                                    text = rel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "${shown.size}/${entries.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(shown, key = { it.path }) { e ->
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (path != "/") load(parentOf(path)) else onDismiss() },
                            modifier = Modifier.weight(1f)
                        ) { Text("← Kembali") }
                        Button(
                            onClick = { onPick(path) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Pakai folder ini") }
                    }
                }
            }
        }
    }
}

fun parentOf(p: String): String {
    if (p == "/") return "/"
    val t = p.trimEnd('/')
    val i = t.lastIndexOf('/')
    return if (i <= 0) "/" else t.substring(0, i)
}
