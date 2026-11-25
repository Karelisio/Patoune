package com.karelisio.patoune.ui.components

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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


/**
 * Animation signature « Prise donnée » : la pilule « Donné » se resserre en cercle, se transforme en coussinet
 * puis en coche, avec un ressort et un retour haptique.
 */
@Composable
fun DoneButton(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.dose_given)) {
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (done) 2f else 0f) }
    // La pilule se resserre d'abord en cercle (forme carrée, donc sans déformation), puis morphe en coussinet et revient en cercle.
    val circleToPad = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }
    LaunchedEffect(done) {
        val target = if (done) 2f else 0f
        if (reduce) progress.snapTo(target)
        else progress.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val p = progress.value
    val shape: androidx.compose.ui.graphics.Shape = when {
        p <= 1f -> RoundedCornerShape(percent = 50)
        p <= 1.5f -> MorphShape(circleToPad, ((p - 1f) * 2f).coerceIn(0f, 1f))
        else -> MorphShape(circleToPad, ((2f - p) * 2f).coerceIn(0f, 1f))
    }
    val t = p.coerceIn(0f, 1f)
    val colors = MaterialTheme.colorScheme
    val description = stringResource(if (done) R.string.a11y_already_given else R.string.a11y_mark_given)
    val give = {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { onClick() }
        Unit
    }
    Box(
        modifier
            .height(56.dp)
            .width(lerp(128.dp, 56.dp, t))
            .clip(shape)
            .background(if (p > 0.5f) colors.tertiaryContainer else colors.primary)
            .clickable(enabled = !done, role = Role.Button, onClick = give)
            // Une seule annonce claire, quelle que soit l'étape du morphing.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                if (done) disabled() else onClick(label = description) { give(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (p < 1.2f) Text(label, color = colors.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.graphicsLayer { alpha = (1f - p / 0.6f).coerceIn(0f, 1f) })
        else Icon(Icons.Rounded.Check, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(28.dp).graphicsLayer {

            val a = ((p - 1.2f) / 0.8f).coerceIn(0f, 1f); alpha = a; scaleX = 0.6f + 0.4f * a; scaleY = scaleX
        })
    }
}
