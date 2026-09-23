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
import kotlinx.coroutines.launch

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
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MediaItem?>(null) }

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
            rootUri = uri
            load(uri)
        }
    }

    LaunchedEffect(rootUri) {
        rootUri?.let { load(it) }
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

            Text(
                text = rootUri?.let { "Root: ${it.path?.takeLast(60)}" } ?: "Belum ada folder dipilih",
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
                    Text(if (media.isEmpty()) "Belum ada media. Pilih folder root dulu."
                        else "Tidak cocok dengan filter/pencarian.")
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.uri.toString() }) { item ->
                        MediaCard(item, onClick = { selected = item })
                    }
                }
            }
        }
    }

    selected?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { selected = null })
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box {
                if (item.isImage) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                } else if (item.isVideo) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
                        ) {
                            Text("▶", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (item.isAudio) "♪" else "📄")
                    }
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
                    text = "${item.mimeType ?: "-"} • ${formatSize(item.size)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
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

private fun saveRoot(context: android.content.Context, uri: Uri) {
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putString(KEY_ROOT, uri.toString()).apply()
}

private fun loadSavedRoot(context: android.content.Context): Uri? {
    val s = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getString(KEY_ROOT, null) ?: return null
    return try { Uri.parse(s) } catch (_: Exception) { null }
}
