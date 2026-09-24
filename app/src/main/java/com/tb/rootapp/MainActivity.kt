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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.tb.rootapp.ui.theme.RootAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var searchActive by remember { mutableStateOf(false) }
    var debouncedQuery by remember { mutableStateOf("") }
    var visibleCount by remember { mutableStateOf(AppConfig.PAGE_SIZE) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var rooted by remember { mutableStateOf<Boolean?>(null) }
    var recentSaf by remember { mutableStateOf(getRecentSaf(context)) }
    var recentSu by remember { mutableStateOf(getRecentSu(context)) }
    var tab by remember { mutableIntStateOf(0) }

    // Template teks (stringResource tak bisa dipanggil dari dalam launch)
    val tNoMedia = stringResource(R.string.load_no_media)
    val tFailSaf = stringResource(R.string.load_fail_saf)
    val tFailSu = stringResource(R.string.load_fail_su)
    val tEmptySu = stringResource(R.string.load_empty_su)

    // debounce ketikan agar grid tidak recompute tiap huruf
    LaunchedEffect(query) {
        delay(AppConfig.SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
        visibleCount = AppConfig.PAGE_SIZE
    }
    LaunchedEffect(filter) { visibleCount = AppConfig.PAGE_SIZE }

    fun load(uri: Uri) {
        if (loading) return
        loading = true
        error = null
        visibleCount = AppConfig.PAGE_SIZE
        scope.launch {
            try {
                media = MediaRepository.fetchFromRoot(context, uri)
                if (media.isEmpty()) error = tNoMedia
            } catch (e: Exception) {
                error = tFailSaf.format(e.message)
            } finally {
                loading = false
            }
        }
    }

    fun loadSu(path: String) {
        if (loading) return
        loading = true
        error = null
        visibleCount = AppConfig.PAGE_SIZE
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
                if (media.isEmpty()) error = tEmptySu.format(path)
            } catch (e: Exception) {
                error = tFailSu.format(e.message)
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
            addRecentSaf(context, uri.toString())
            recentSaf = getRecentSaf(context)
            rootUri = uri
            suRoot = null
            load(uri)
        }
    }

    LaunchedEffect(Unit) {
        rooted = withContext(Dispatchers.IO) { RootHelper.isRootAvailable() }
        // auto-load sumber terakhir — sekali saja (hindari double scan)
        suRoot?.let { loadSu(it) } ?: rootUri?.let { load(it) }
    }

    val filtered = remember(media, filter, debouncedQuery) {
        media.filter {
            val matchFilter = when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.IMAGE -> it.isImage
                MediaFilter.VIDEO -> it.isVideo
                MediaFilter.AUDIO -> it.isAudio
            }
            val matchQuery = debouncedQuery.isBlank() ||
                it.name.contains(debouncedQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }
    // pagination: render bertahap agar grid tidak macet di ribuan item
    val visible = remember(filtered, visibleCount) { filtered.take(visibleCount) }

    val countFmt = stringResource(R.string.count_format)
    val countText = remember(media, filtered, countFmt) {
        val img = media.count { it.isImage }
        val vid = media.count { it.isVideo }
        val aud = media.count { it.isAudio }
        countFmt.format(filtered.size, media.size, img, vid, aud)
    }

    val sourceText = when {
        suRoot != null -> stringResource(R.string.source_su, suRoot ?: "")
        rootUri != null -> stringResource(
            R.string.source_saf,
            rootUri?.path?.takeLast(AppConfig.RECENT_LABEL_CHARS) ?: ""
        )
        else -> stringResource(R.string.source_none)
    }
    val rootLabel = when (rooted) {
        null -> stringResource(R.string.root_checking)
        true -> stringResource(R.string.root_ok)
        false -> stringResource(R.string.root_none)
    }

    // Loader Coil + decoder frame video (untuk thumbnail superuser)
    val videoLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    fun pickSuFolder(path: String) {
        saveSuRoot(context, path)
        saveRootUriClear(context)
        addRecentSu(context, path)
        recentSu = getRecentSu(context)
        suRoot = path
        rootUri = null
        loadSu(path)
        tab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            1 -> stringResource(R.string.tab_browser)
                            2 -> stringResource(R.string.tab_watch)
                            else -> stringResource(R.string.title_media)
                        }
                    )
                },
                actions = {
                    Text(
                        if (rooted == true) "🟢" else if (rooted == false) "🔴" else "⚪",
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🖼") },
                    label = { Text(stringResource(R.string.tab_media)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📁") },
                    label = { Text(stringResource(R.string.tab_browser)) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("🛡") },
                    label = { Text(stringResource(R.string.tab_watch)) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            1 -> Column(Modifier.fillMaxSize().padding(padding)) {
                RootBrowserContent(
                    initialPath = suRoot ?: AppConfig.DEFAULT_SU_PATH,
                    onPick = { pickSuFolder(it) },
                    onClose = null
                )
            }
            2 -> Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WatchCard()
            }
            else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tombol rapi: 2 sejajar, sama lebar
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { picker.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_folder)) }
                    OutlinedButton(
                        onClick = { tab = 1 },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_superuser)) }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.root_status, rootLabel, sourceText),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Riwayat folder — tap untuk auto-fetch tanpa pilih ulang
            if (recentSaf.isNotEmpty() || recentSu.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                        stringResource(R.string.recent_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lazyItems(recentSu, key = { "su:$it" }) { p ->
                        SuggestionChip(
                            onClick = {
                                saveSuRoot(context, p)
                                saveRootUriClear(context)
                                suRoot = p
                                rootUri = null
                                loadSu(p)
                            },
                            label = { Text("🔑 ${p.takeLast(AppConfig.RECENT_LABEL_CHARS)}") }
                        )
                    }
                    lazyItems(recentSaf, key = { "saf:$it" }) { u ->
                        SuggestionChip(
                            onClick = {
                                try {
                                    val uri = Uri.parse(u)
                                    saveRoot(context, uri)
                                    saveSuRoot(context, null)
                                    rootUri = uri
                                    suRoot = null
                                    load(uri)
                                } catch (_: Exception) { }
                            },
                            label = { Text("📂 ${u.takeLast(AppConfig.RECENT_LABEL_CHARS)}") }
                        )
                    }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = { searchActive = false },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = { Text(stringResource(R.string.search_media_hint)) },
                leadingIcon = {
                    if (searchActive) {
                        TextButton(onClick = { searchActive = false }) { Text(stringResource(R.string.back_arrow)) }
                    } else {
                        Text("🔍")
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = "" }) { Text("✕") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val suggestions = remember(media, query) {
                    if (query.isBlank()) media.take(AppConfig.SUGGEST_COUNT)
                    else media.filter { it.name.contains(query, ignoreCase = true) } .take(AppConfig.SUGGEST_COUNT)
                }
                // Column biasa (bukan Lazy) agar aman di dalam item grid
                Column {
                    suggestions.forEach { s ->
                        ListItem(
                            headlineContent = {
                                Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                Text(if (s.isImage) "🖼" else if (s.isVideo) "🎬" else "🎵")
                            },
                            modifier = Modifier.clickable {
                                query = s.name
                                searchActive = false
                            }
                        )
                    }
                }
            }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == MediaFilter.ALL,
                        onClick = { filter = MediaFilter.ALL },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                    FilterChip(
                        selected = filter == MediaFilter.IMAGE,
                        onClick = { filter = MediaFilter.IMAGE },
                        label = { Text(stringResource(R.string.filter_image)) }
                    )
                    FilterChip(
                        selected = filter == MediaFilter.VIDEO,
                        onClick = { filter = MediaFilter.VIDEO },
                        label = { Text(stringResource(R.string.filter_video)) }
                    )
                    FilterChip(
                        selected = filter == MediaFilter.AUDIO,
                        onClick = { filter = MediaFilter.AUDIO },
                        label = { Text(stringResource(R.string.filter_audio)) }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = if (loading) stringResource(R.string.loading_media) else countText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }

            when {
                loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                filtered.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (media.isEmpty()) stringResource(R.string.empty_media_start)
                            else stringResource(R.string.no_match)
                        )
                    }
                }
                else -> {
                    items(
                        visible,
                        key = { (it.filePath ?: it.uri.toString()) },
                        contentType = { if (it.isVideo) "v" else if (it.isAudio) "a" else "i" }
                    ) { item ->
                        MediaCard(item, videoLoader, onClick = { selected = item })
                    }
                    if (visibleCount < filtered.size) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = { visibleCount += AppConfig.PAGE_STEP },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.load_more,
                                        AppConfig.PAGE_STEP,
                                        filtered.size - visibleCount
                                    )
                                )
                            }
                        }
                    }
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
fun MediaCard(item: MediaItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    val context = LocalContext.current
    // thumbnail kecil (300px) agar scroll grid tidak lag
    val thumb = remember(item) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(AppConfig.THUMB_SIZE_PX)
            .crossfade(false)
            .build()
    }
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            // Item superuser: thumbnail asli via cache (bukan ikon)
            if (item.filePath != null && (item.isImage || item.isVideo)) {
                SuThumb(item = item, imageLoader = imageLoader)
            } else if (item.filePath != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎵", style = MaterialTheme.typography.headlineMedium)
                }
            } else if (item.isImage) {
                AsyncImage(
                    model = thumb,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            } else if (item.isVideo) {
                Box {
                    AsyncImage(
                        model = thumb,
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

/** Thumbnail asli untuk file superuser: copy sekali ke cache lalu tampilkan. */
@Composable
fun SuThumb(item: MediaItem, imageLoader: ImageLoader) {
    val context = LocalContext.current
    var file by remember(item) { mutableStateOf<java.io.File?>(null) }
    var done by remember(item) { mutableStateOf(false) }

    LaunchedEffect(item) {
        done = false
        file = withContext(Dispatchers.IO) {
            item.filePath?.let { RootHelper.thumbFor(it, context.cacheDir) }
        }
        done = true
    }

    when {
        !done -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        file != null && item.isImage -> {
            val req = remember(file) {
                ImageRequest.Builder(context)
                    .data(file)
                    .size(AppConfig.THUMB_SIZE_PX)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        }
        file != null && item.isVideo -> Box {
            AsyncImage(
                model = file,
                imageLoader = imageLoader,
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
        else -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (item.isAudio) "🎵" else "📄",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewDialog(item: MediaItem, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.back_arrow)) }
                    },
                    title = {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                )
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = if (item.filePath != null) stringResource(R.string.preview_via_su, item.filePath ?: "")
                        else stringResource(R.string.preview_meta, item.mimeType ?: "-", formatSize(item.size)),
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
                            else -> Text(stringResource(R.string.preview_unsupported))
                        }
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
            else suError = context.getString(R.string.preview_su_fail)
        } catch (e: Exception) {
            suError = context.getString(R.string.preview_fail, e.message)
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
        else -> Text(stringResource(R.string.preview_unsupported))
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
private const val KEY_RECENT_SAF = "recent_saf"
private const val KEY_RECENT_SU = "recent_su"
private const val MAX_RECENT = 6

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

private fun getRecentSaf(context: android.content.Context): List<String> {
    return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getStringSet(KEY_RECENT_SAF, emptySet())?.toList() ?: emptyList()
}

private fun addRecentSaf(context: android.content.Context, uri: String) {
    val cur = getRecentSaf(context).toMutableList()
    cur.remove(uri)
    cur.add(0, uri)
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_RECENT_SAF, cur.take(MAX_RECENT).toSet()).apply()
}

private fun getRecentSu(context: android.content.Context): List<String> {
    return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .getStringSet(KEY_RECENT_SU, emptySet())?.toList()?.sorted() ?: emptyList()
}

private fun addRecentSu(context: android.content.Context, path: String) {
    val cur = getRecentSu(context).toMutableList()
    cur.remove(path)
    cur.add(0, path)
    context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_RECENT_SU, cur.take(MAX_RECENT).toSet()).apply()
}
