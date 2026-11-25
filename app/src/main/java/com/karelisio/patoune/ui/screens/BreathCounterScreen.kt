package com.karelisio.patoune.ui.screens

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.BreathContext
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.reminders.BreathAlerts
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.components.AnimatedNumber
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.rememberReduceMotion
import com.karelisio.patoune.ui.theme.BigNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { READY, COUNTING, DONE }

/**
 * Compteur de fréquence respiratoire : un tap par respiration.
 * Animation signature « Souffle » : la forme gonfle et se dégonfle à chaque tap, avec des ondes concentriques.
 */
@Composable
fun BreathCounterScreen(vm: MainViewModel, nightStart: Boolean) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduce = rememberReduceMotion()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var night by remember { mutableStateOf(nightStart) }
    var duration by remember(settings?.breathDuration) { mutableStateOf(settings?.breathDuration ?: 30) }
    var phase by remember { mutableStateOf(Phase.READY) }
    val taps = remember { mutableStateListOf<Long>() }
    var startAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val ripples = remember { mutableStateListOf<Long>() }
    val scale = remember { Animatable(1f) }
    var ctx by remember { mutableStateOf(BreathContext.SLEEP) }
    var note by remember { mutableStateOf("") }
    var alert by remember { mutableStateOf(false) }

    // Écran allumé ; en mode nuit, luminosité au minimum.
    val activity = LocalContext.current as? Activity
    DisposableEffect(night) {
        val w = activity?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs = w?.attributes
        val old = attrs?.screenBrightness
        if (night && attrs != null) { attrs.screenBrightness = 0.02f; w.attributes = attrs }
        onDispose {
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (attrs != null && old != null) { attrs.screenBrightness = old; w.attributes = attrs }
        }
    }

    LaunchedEffect(phase, ripples.size) {
        while (phase == Phase.COUNTING || ripples.isNotEmpty()) {
            withFrameMillis { now = System.currentTimeMillis() }
            ripples.removeAll { now - it > 1600 }
            if (phase == Phase.COUNTING && now - startAt >= duration * 1000L) phase = Phase.DONE
        }
    }

    val bg = if (night) Color(0xFF040303) else MaterialTheme.colorScheme.surface
    val fg = if (night) Color(0xFF7A3A2C) else MaterialTheme.colorScheme.primary
    val onBg = if (night) Color(0xFF6B4A42) else MaterialTheme.colorScheme.onSurface
    val blob = if (night) Color(0xFF1A0D0A) else MaterialTheme.colorScheme.primaryContainer
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "r")

    fun onTap() {
        val t = System.currentTimeMillis()
        if (phase == Phase.DONE) return
        if (phase == Phase.READY) { phase = Phase.COUNTING; startAt = t; taps.clear() }
        taps += t
        if (!reduce) ripples += t
        if (night) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        else view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!reduce) scope.launch {
            scale.animateTo(1.16f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow))
        }
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        if (phase != Phase.DONE) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { onTap() }) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    ripples.forEach { start ->
                        val f = ((now - start) / 1600f).coerceIn(0f, 1f)
                        drawCircle(fg.copy(alpha = (1f - f) * if (night) 0.35f else 0.5f), radius = size.minDimension * (0.28f + 0.42f * f), center = center, style = Stroke(width = (1f - f) * 10f + 1f))
                    }
                }
                Box(
                    Modifier.size(260.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = if (reduce) 0f else spin }
                        .clip(shape).background(blob),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val live = if (phase == Phase.COUNTING) Breath.liveRate(taps) else null
                    AnimatedNumber(live?.let { Breath.format(it) } ?: taps.size.toString(), BigNumber, color = onBg)
                    Text(if (live != null) "resp/min (estimation)" else if (phase == Phase.READY) "Touchez à chaque inspiration" else "respiration(s)",
                        color = onBg, style = MaterialTheme.typography.titleMedium)
                    if (phase == Phase.COUNTING) {
                        val left = ((duration * 1000L - (now - startAt)) / 1000L).coerceAtLeast(0)
                        Text("$left s", color = fg, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        // Barre du haut.
        Row(Modifier.systemBarsPadding().padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = onBg) }
            Text(animal?.name ?: "", color = onBg, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { night = !night }) { Icon(if (night) Icons.Rounded.LightMode else Icons.Rounded.Bedtime, "Mode nuit", tint = onBg) }
        }
        if (phase == Phase.READY) {
            Column(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SingleChoiceSegmentedButtonRow {
                    Breath.durations.forEachIndexed { i, d ->
                        SegmentedButton(selected = d == duration, onClick = { duration = d; scope.launch { c.settings.setBreathDuration(d) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Breath.durations.size)) { Text("$d s") }
                    }
                }
                if (!night) VetNote("Comptez au repos ou pendant le sommeil, une respiration = un soulèvement du flanc.")
            }
        }
        if (phase == Phase.DONE) {
            val bpm = Breath.perMinute(taps.size, duration)
            val over = Breath.exceeds(bpm, animal?.breathThreshold)
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.padding(top = 48.dp))
                AnimatedContent(bpm, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "res") { v ->
                    AnimatedNumber(Breath.format(v), BigNumber, color = if (over) MaterialTheme.colorScheme.error else onBg)
                }
                Text("respirations / min · ${taps.size} en $duration s", color = onBg)
                if (over) Text("Au-dessus du seuil fixé avec le véto (${animal?.breathThreshold}).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                ChoiceChips(BreathContext.entries, ctx, { it.label }) { ctx = it }
                Field(note, { note = it }, "Note")
                Button(
                    onClick = {
                        val a = animal ?: return@Button
                        scope.launch {
                            val saved = c.store.save(BreathCount(animalId = a.id, at = startAt, taps = taps.size, durationSec = duration, bpm = bpm, context = ctx, note = note))
                            alert = BreathAlerts.check(c, a, saved)
                            c.hooks.onDataChanged()
                            nav.popBackStack()
                        }
                    },
                    colors = if (night) ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1410), contentColor = Color(0xFF9A6A60)) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enregistrer") }
                OutlinedButton(onClick = { phase = Phase.READY; taps.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Recommencer", color = onBg) }
            }
        }
    }
}
