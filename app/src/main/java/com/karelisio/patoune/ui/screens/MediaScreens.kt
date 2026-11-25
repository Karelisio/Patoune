package com.karelisio.patoune.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.karelisio.patoune.container
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.EmptyState
import com.karelisio.patoune.ui.components.cascade
import com.karelisio.patoune.ui.sharedKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Vignette d'un média (photo, image de la vidéo, première page du PDF). */
@Composable
fun MediaThumb(item: MediaItem, modifier: Modifier = Modifier, size: Dp? = null, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val file = remember(item.id, item.localPath) { item.localPath?.let(::File)?.takeIf { it.exists() } ?: ctx.container.media.file(item.id).takeIf { it.exists() } }
    val base = (if (size != null) modifier.size(size) else modifier.aspectRatio(1f))
        .sharedKey("media-${item.id}")
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Box(base, contentAlignment = Alignment.Center) {
        when {
            file == null -> Text("En attente…", style = MaterialTheme.typography.labelSmall)
            item.kind == MediaKind.PDF -> {
                val bmp by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { renderPdfPage(file, 0, 400) } }
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Icon(Icons.Rounded.Description, null)
            }
            else -> AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item.kind == MediaKind.VIDEO) Icon(Icons.Rounded.PlayCircle, "Vidéo", tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

fun renderPdfPage(file: File, page: Int, width: Int): Bitmap? = runCatching {
    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
        if (page >= r.pageCount) return null
        r.openPage(page).use { p ->
            val h = (width.toFloat() / p.width * p.height).toInt()
            Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also { bmp ->
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
}.getOrNull()

fun pdfPageCount(file: File): Int = runCatching {
    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
}.getOrDefault(0)

@Composable
fun MediaStrip(items: List<MediaItem>) {
    val nav = LocalNav.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { m -> MediaThumb(m, size = 96.dp) { nav.navigate(Routes.MediaViewer(m.id)) } }
    }
}

@Composable
fun GalleryContent(animalId: String) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val all by remember(animalId) { c.db.media().observeFor(animalId) }.collectAsState(emptyList())
    var type by remember { mutableStateOf<AttachType?>(null) }
    var kind by remember { mutableStateOf<MediaKind?>(null) }
    val shown = all.filter { (type == null || it.attachedType == type) && (kind == null || it.kind == kind) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(kind == MediaKind.PHOTO, { kind = if (kind == MediaKind.PHOTO) null else MediaKind.PHOTO }, { Text("Photos") }) }
                    item { FilterChip(kind == MediaKind.VIDEO, { kind = if (kind == MediaKind.VIDEO) null else MediaKind.VIDEO }, { Text("Vidéos") }) }
                    items(listOf(AttachType.CRISIS, AttachType.BREATH, AttachType.VISIT, AttachType.OBSERVATION, AttachType.DOCUMENT, AttachType.NONE)) { t ->
                        FilterChip(type == t, { type = if (type == t) null else t }, { Text(t.label) })
                    }
                }
            }
            if (shown.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.PlayCircle, "Pas encore de média", "Photos et vidéos de crises, visites et observations apparaîtront ici.")
            }
            items(shown, key = { it.id }) { m -> MediaThumb(m, Modifier.cascade(shown.indexOf(m))) { nav.navigate(Routes.MediaViewer(m.id)) } }
        }
    }
}

/** Plein écran : photo zoomable, lecteur vidéo, ou visionneuse PDF. */
@OptIn(UnstableApi::class)
@Composable
fun MediaViewerScreen(id: String) {
    val ctx = LocalContext.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val item by remember { c.db.media().observe(id) }.collectAsState(null)
    val m = item ?: return
    val file = c.media.file(m.id)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().sharedKey("media-${m.id}"), contentAlignment = Alignment.Center) {
            when {
                !file.exists() -> Text("Média en cours de synchronisation…", color = Color.White)
                m.kind == MediaKind.VIDEO -> {
                    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(androidx.media3.common.MediaItem.fromUri(file.toURI().toString())); prepare(); playWhenReady = true } }
                    DisposableEffect(Unit) { onDispose { player.release() } }
                    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize())
                }
                m.kind == MediaKind.PDF -> PdfPages(file)
                else -> ZoomableImage(file)
            }
        }
        Row(Modifier.systemBarsPadding().padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
            Text(Dates.fmtDayTime(m.takenAt) + " · " + m.attachedType.label, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { shareFile(ctx, file, m.mime) }) { Icon(Icons.Rounded.Share, "Partager", tint = Color.White) }
            IconButton(onClick = { scope.launch { c.media.delete(m); nav.popBackStack() } }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = Color.White) }
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f); ox += pan.x; oy += pan.y
        if (scale == 1f) { ox = 0f; oy = 0f }
    }
    AsyncImage(file, null, Modifier.fillMaxSize().transformable(state).graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy }, contentScale = ContentScale.Fit)
}

@Composable
fun PdfPages(file: File) {
    val count = remember(file) { pdfPageCount(file) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer), contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp, start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(count) { i ->
            val bmp by produceState<Bitmap?>(null, file, i) { value = withContext(Dispatchers.IO) { renderPdfPage(file, i, 1400) } }
            bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small), contentScale = ContentScale.FillWidth) }
                ?: Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(Color.White))
        }
    }
}

fun shareFile(context: android.content.Context, file: File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime).putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, "Partager").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

