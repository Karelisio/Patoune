package com.karelisio.patoune.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Respecte « Supprimer les animations » (échelle d'animation système à 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Dessine une empreinte de patte aux coussinets arrondis, centrée dans [size]. */
fun DrawScope.drawPaw(center: Offset, size: Float, color: Color, alpha: Float = 1f) {
    val s = size
    // Coussinet principal : trois ovales fondus.
    drawOval(color, Offset(center.x - s * 0.30f, center.y - s * 0.02f), Size(s * 0.60f, s * 0.44f), alpha)
    drawOval(color, Offset(center.x - s * 0.36f, center.y + s * 0.10f), Size(s * 0.34f, s * 0.30f), alpha)
    drawOval(color, Offset(center.x + s * 0.02f, center.y + s * 0.10f), Size(s * 0.34f, s * 0.30f), alpha)
    // Quatre doigts.
    val toes = listOf(-0.40f to -0.22f, -0.16f to -0.42f, 0.12f to -0.42f, 0.34f to -0.22f)
    toes.forEach { (dx, dy) ->
        drawOval(color, Offset(center.x + dx * s - s * 0.10f, center.y + dy * s - s * 0.12f), Size(s * 0.21f, s * 0.26f), alpha)
    }
}

@Composable
fun PawIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawPaw(this.center, this.size.minDimension, color) }
}

/**
 * Animation signature « Empreintes » : des pattes apparaissent une à une
 * et traversent l'écran en diagonale, puis s'estompent.
 */
@Composable
fun PawTrail(color: Color, key: Any?, modifier: Modifier = Modifier, steps: Int = 8, onDone: () -> Unit = {}) {
    val reduce = rememberReduceMotion()
    val alphas = remember(key) { List(steps) { Animatable(0f) } }
    LaunchedEffect(key) {
        if (reduce) { onDone(); return@LaunchedEffect }
        alphas.forEachIndexed { i, a ->
            launch {
                delay(i * 90L)
                a.animateTo(1f, tween(160))
                delay(260)
                a.animateTo(0f, tween(420))
            }
        }
        delay(steps * 90L + 900)
        onDone()
    }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val pawSize = size.minDimension / 9f
            for (i in 0 until steps) {
                val t = (i + 0.5f) / steps
                val side = if (i % 2 == 0) -1 else 1
                val x = size.width * (0.12f + 0.76f * t) + side * pawSize * 0.45f
                val y = size.height * (0.78f - 0.56f * t) + side * pawSize * 0.25f
                rotate(degrees = 52f, pivot = Offset(x, y)) {
                    drawPaw(Offset(x, y), pawSize, color, alphas[i].value)
                }
            }
        }
    }
}
