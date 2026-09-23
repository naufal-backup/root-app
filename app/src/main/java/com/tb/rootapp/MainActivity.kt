package com.tb.rootapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tb.rootapp.ui.theme.RootAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RootAppTheme {
                RootMediaScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootMediaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootUri by remember { mutableStateOf<Uri?>(loadSavedRoot(context)) }
    var suRoot by remember { mutableStateOf(loadSavedSuRoot(context)) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var showSuBrowser by remember { mutableStateOf(false) }
    var rooted by remember { mutableStateOf<Boolean?>(null) }

    fun load(uri: Uri) {
        loading = true
        error = null
        scope.launch {
            try {
                media = MediaRepository.fetchFromRoot(context, uri)
                if (media.isEmpty()) error = "Tidak ada media di folder ini."
            } catch (e: Exception) {
                error = "Gagal baca folder: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun loadSu(path: String) {
        loading = true
        error = null
        scope.launch {
            try {
                val paths = RootHelper.findMedia(path)
                media = paths.map { p ->
                    val f = File(p)
                    MediaItem(
                        uri = Uri.fromFile(f),
                        name = f.name.ifEmpty { p.substringAfterLast('/') },
                        mimeType = null,
                        size = 0L,
                        filePath = p
                    )
                }
                if (media.isEmpty()) error = "Tidak ada media di $path (atau akses ditolak)."
            } catch (e: Exception) {
                error = "Gagal baca via su: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            saveRoot(context, uri)
            saveSuRoot(context, null) // ganti sumber ke SAF
            rootUri = uri
            suRoot = null
            load(uri)
        }
    }

    LaunchedEffect(Unit) {
        rooted = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
        // auto-load sumber terakhir
        suRoot?.let { loadSu(it) } ?: rootUri?.let { load(it) }
    }

    val filtered = remember(media, filter, query) {
        media.filter {
            val matchFilter = when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.IMAGE -> it.isImage
                MediaFilter.VIDEO -> it.isVideo
                MediaFilter.AUDIO -> it.isAudio
            }
            val matchQuery = query.isBlank() ||
                it.name.contains(query, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val countText = remember(media, filtered) {
        val img = media.count { it.isImage }
        val vid = media.count { it.isVideo }
        val aud = media.count { it.isAudio }
        "${filtered.size}/${media.size} • 🖼 $img • 🎬 $vid • 🎵 $aud"
    }

    val sourceText = when {
        suRoot != null -> "SU Root: $suRoot"
        rootUri != null -> "Root: ${rootUri?.path?.takeLast(60)}"
        else -> "Belum ada folder dipilih"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Root App — Media") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { picker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pilih Folder Root")
            }

            OutlinedButton(
                onClick = { showSuBrowser = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Browser Superuser (/data/data)")
            }

            Text(
                text = "Akses root: " + when (rooted) {
                    null -> "mengecek…"
                    true -> "OK (uid=0)"
                    false -> "tidak ada — HP belum root"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = sourceText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari nama file…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == MediaFilter.ALL,
                    onClick = { filter = MediaFilter.ALL },
                    label = { Text("Semua") }
                )
                FilterChip(
                    selected = filter == MediaFilter.IMAGE,
                    onClick = { filter = MediaFilter.IMAGE },
                    label = { Text("Gambar") }
                )
                FilterChip(
                    selected = filter == MediaFilter.VIDEO,
                    onClick = { filter = MediaFilter.VIDEO },
                    label = { Text("Video") }
                )
                FilterChip(
                    selected = filter == MediaFilter.AUDIO,
                    onClick = { filter = MediaFilter.AUDIO },
                    label = { Text("Audio") }
                )
            }

            Text(text = if (loading) "Memuat media…" else countText,
                style = MaterialTheme.typography.bodyMedium)

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (media.isEmpty()) "Belum ada media. Pilih folder root / browser superuser."
                        else "Tidak cocok dengan filter/pencarian.")
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { (it.filePath ?: it.uri.toString()) }) { item ->
                        MediaCard(item, onClick = { selected = item })
                    }
                }
            }
        }
    }

    if (showSuBrowser) {
        RootBrowserDialog(
            initialPath = suRoot ?: "/data/data",
            onPick = { path ->
                saveSuRoot(context, path)
                saveRootUriClear(context)
                suRoot = path
                rootUri = null
                showSuBrowser = false
                loadSu(path)
            },
            onDismiss = { showSuBrowser = false }
        )
    }

    selected?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { selected = null })
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            // Item superuser belum tentu bisa dibaca langsung → tampilkan ikon, copy saat preview
            if (item.filePath != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            item.isImage -> "🖼"
                            item.isVideo -> "🎬"
                            item.isAudio -> "🎵"
                            else -> "📄"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            } else if (item.isImage) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            } else if (item.isVideo) {
                Box {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                        ) {
                            Text("▶", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (item.isAudio) "♪" else "📄")
                }
            }
            Text(
                text = item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Composable
fun MediaPreviewDialog(item: MediaItem, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
                Text(
                    text = if (item.filePath != null) "${item.filePath} • via su"
                        else "${item.mimeType ?: "-"} • ${formatSize(item.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.filePath != null) {
                        SuPreviewContent(item = item)
                    } else {
                        when {
                            item.isImage -> AsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            item.isVideo || item.isAudio -> VideoPlayer(uri = item.uri)
                            else -> Text("Preview tidak didukung.")
                        }
                    }
                }
            }
        }
    }
}

/** Preview file root-only: copy via `su -c cat` ke cache lalu tampilkan. */
@Composable
fun SuPreviewContent(item: MediaItem) {
    val context = LocalContext.current
    var cachedUri by remember(item) { mutableStateOf<Uri?>(null) }
    var suError by remember(item) { mutableStateOf<String?>(null) }

    LaunchedEffect(item) {
        cachedUri = null
        suError = null
        try {
            val src = item.filePath ?: return@LaunchedEffect
            val safeName = File(src).name.ifEmpty { "preview" }.takeLast(60)
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dst = File(context.cacheDir, "su_preview/$safeName")
            val ok = withContext(Dispatchers.IO) { RootHelper.copyToCache(src, dst) }
            if (ok) cachedUri = Uri.fromFile(dst)
            else suError = "Gagal baca via su — pastikan HP rooted & akses root diizinkan."
        } catch (e: Exception) {
            suError = "Gagal: ${e.message}"
        }
    }

    when {
        suError != null -> Text(
            suError!!,
            color = MaterialTheme.colorScheme.error
        )
        cachedUri == null -> CircularProgressIndicator()
        item.isImage -> AsyncImage(
            model = cachedUri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        item.isVideo || item.isAudio -> VideoPlayer(uri = cachedUri!!)
        else -> Text("Preview tidak didukung.")
    }
}

@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

private const val PREFS = "root_app"
private const val KEY_ROOT = "root_uri"
private const val KEY_SU_ROOT = "su_root_path"

private fun saveRoot(context: android.content.Context, uri: Uri) {
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putString(KEY_ROOT, uri.toString()).apply()
}

private fun saveRootUriClear(context: android.content.Context) {
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().remove(KEY_ROOT).apply()
}

private fun loadSavedRoot(context: android.content.Context): Uri? {
    val s = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getString(KEY_ROOT, null) ?: return null
    return try { Uri.parse(s) } catch (_: Exception) { null }
}

private fun saveSuRoot(context: android.content.Context, path: String?) {
    val e = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
    if (path == null) e.remove(KEY_SU_ROOT) else e.putString(KEY_SU_ROOT, path)
    e.apply()
}

private fun loadSavedSuRoot(context: android.content.Context): String? {
    return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getString(KEY_SU_ROOT, null)
}
