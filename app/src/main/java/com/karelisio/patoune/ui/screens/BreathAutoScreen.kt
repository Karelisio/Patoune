package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.BreathContext
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.media.AutoCount
import com.karelisio.patoune.media.MotionAnalyzer
import com.karelisio.patoune.reminders.BreathAlerts
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimatedNumber
import com.karelisio.patoune.ui.components.ChartPoint
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.TrendChart
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.theme.BigNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Comptage auto expérimental : vidéo de l'animal endormi, zone du flanc, estimation à valider. */
@Composable
fun BreathAutoScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var file by remember { mutableStateOf<File?>(null) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(Size.Zero) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var result by remember { mutableStateOf<AutoCount?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val f = File(ctx.cacheDir, "auto-count.mp4")
            withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } }
            file = f; frame = withContext(Dispatchers.IO) { MotionAnalyzer.firstFrame(f) }
            start = null; end = null; result = null
        }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.auto_title)) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                ShapeBadge(Icons.Rounded.AutoAwesome, PawShapes.sunny, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                Text(stringResource(R.string.auto_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.auto_pick)) }
            }
            frame?.let { bmp ->
                Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).clip(MaterialTheme.shapes.large)) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    val accent = MaterialTheme.colorScheme.primary
                    val zoneLabel = stringResource(R.string.a11y_auto_zone)
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = zoneLabel }.pointerInput(Unit) {
                        detectDragGestures(onDragStart = { start = it; end = it }, onDrag = { ch, _ -> end = ch.position })
                    }) {
                        box = size
                        val s = start; val e = end
                        if (s != null && e != null) {
                            val tl = Offset(minOf(s.x, e.x), minOf(s.y, e.y))
                            drawRect(accent.copy(alpha = 0.25f), tl, Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)))
                            drawRect(accent, tl, Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)), style = Stroke(4f))
                        }
                    }
                }
                Button(
                    onClick = {
                        val f = file ?: return@Button
                        val s = start ?: return@Button; val e = end ?: return@Button
                        val roi = RectF(minOf(s.x, e.x) / box.width, minOf(s.y, e.y) / box.height, maxOf(s.x, e.x) / box.width, maxOf(s.y, e.y) / box.height)
                        progress = 0f; result = null
                        scope.launch { result = MotionAnalyzer.analyze(f, roi) { p -> progress = p }; progress = -1f }
                    },
                    enabled = start != null && end != null && progress < 0, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.auto_analyze)) }
            }
            if (progress >= 0) LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            result?.let { r ->
                SectionCard {
                    if (r.bpm == null) Text(stringResource(R.string.auto_not_detected))
                    else {
                        Text(stringResource(R.string.auto_estimate), style = MaterialTheme.typography.titleMedium)
                        AnimatedNumber("≈ ${Breath.format(r.bpm)}", BigNumber, description = stringResource(R.string.a11y_auto_estimate, Breath.format(r.bpm)))
                        Text(stringResource(R.string.auto_estimate_detail, r.seconds.roundToInt()), style = MaterialTheme.typography.bodyMedium)
                        TrendChart(r.signal.mapIndexed { i, v -> ChartPoint(i.toLong(), v) }, height = 100.dp, description = stringResource(R.string.a11y_auto_signal))
                        Button(onClick = {
                            val a = animal ?: return@Button
                            scope.launch {
                                val saved = c.store.save(BreathCount(animalId = a.id, at = System.currentTimeMillis(), taps = (r.bpm * r.seconds / 60).roundToInt(),
                                    durationSec = r.seconds.roundToInt().coerceAtLeast(1), bpm = r.bpm, context = BreathContext.SLEEP, note = res.getString(R.string.breath_auto), estimated = true))
                                BreathAlerts.check(c, a, saved)
                                nav.popBackStack()
                            }
                        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.auto_confirm)) }
                        OutlinedButton(onClick = { nav.navigate(Routes.BreathCounter(night = true)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.auto_recount)) }
                    }
                }
            }
            VetNote(stringResource(R.string.auto_vet_note))

        }
    }
}
