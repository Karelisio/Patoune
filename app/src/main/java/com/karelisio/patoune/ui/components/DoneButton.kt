package com.karelisio.patoune.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import kotlinx.coroutines.launch

/** Forme intermédiaire entre deux polygones normalisés (0..1). */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cubics = morph.asCubics(progress)
        val w = size.width; val h = size.height
        cubics.firstOrNull()?.let { path.moveTo(it.anchor0X * w, it.anchor0Y * h) }
        cubics.forEach { c -> path.cubicTo(c.control0X * w, c.control0Y * h, c.control1X * w, c.control1Y * h, c.anchor1X * w, c.anchor1Y * h) }
        path.close()
        return Outline.Generic(path)
    }
}

private fun RoundedPolygon.normalizedCircle() = RoundedPolygon.circle(numVertices = 8, radius = 0.5f, centerX = 0.5f, centerY = 0.5f)

/**
 * Animation signature « Prise donnée » : la pilule « Donné » se transforme en coussinet
 * puis en coche, avec un ressort et un retour haptique.
 */
@Composable
fun DoneButton(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Donné") {
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (done) 2f else 0f) }
    val pillToPad = remember { Morph(MaterialShapes.Pill, MaterialShapes.Cookie4Sided) }
    val padToCircle = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie4Sided.normalizedCircle()) }
    LaunchedEffect(done) {
        val target = if (done) 2f else 0f
        if (reduce) progress.snapTo(target)
        else progress.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val p = progress.value
    val shape = if (p <= 1f) MorphShape(pillToPad, p.coerceIn(0f, 1f)) else MorphShape(padToCircle, (p - 1f).coerceIn(0f, 1f))
    val t = (p / 2f).coerceIn(0f, 1f)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(56.dp)
            .width(lerp(128.dp, 56.dp, t))
            .clip(shape)
            .background(if (p > 0.5f) colors.tertiaryContainer else colors.primary)
            .clickable(enabled = !done, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (p < 0.6f) Text(label, color = colors.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.graphicsLayer { alpha = 1f - p / 0.6f })
        else Icon(Icons.Rounded.Check, "Donné", tint = colors.onTertiaryContainer, modifier = Modifier.size(28.dp).graphicsLayer {
            val a = ((p - 0.6f) / 1.4f).coerceIn(0f, 1f); alpha = a; scaleX = 0.6f + 0.4f * a; scaleY = scaleX
        })
    }
}
