package com.karelisio.patoune.ui.components

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Formes M3 Expressive utilisées comme pastilles (coussinet, cookie, trèfle…). */
object PawShapes {
    val pad: Shape @Composable get() = MaterialShapes.Cookie4Sided.toShape()
    val cookie: Shape @Composable get() = MaterialShapes.Cookie9Sided.toShape()
    val clover: Shape @Composable get() = MaterialShapes.Clover4Leaf.toShape()
    val sunny: Shape @Composable get() = MaterialShapes.Sunny.toShape()
    val soft: Shape @Composable get() = MaterialShapes.SoftBurst.toShape()
    val pill: Shape @Composable get() = MaterialShapes.Pill.toShape()
    val flower: Shape @Composable get() = MaterialShapes.Flower.toShape()
    val gem: Shape @Composable get() = MaterialShapes.Gem.toShape()
}

@Composable
fun ShapeBadge(icon: ImageVector, shape: Shape, container: Color, content: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

/** Rappel discret : l'appli aide au suivi, les seuils sont ceux fixés avec le véto. */
@Composable
fun VetNote(text: String = stringResource(R.string.vet_note_default), modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShapeBadge(icon, PawShapes.cookie, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 88.dp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Compteur animé : chaque chiffre glisse verticalement quand il change.
 * L'accessibilité lit la valeur entière ([description] si fournie), pas chiffre par chiffre.
 */
@Composable
fun AnimatedNumber(value: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, description: String = value) {
    Row(modifier.clearAndSetSemantics { contentDescription = description }) {

        value.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Apparition en cascade d'un élément de liste (décalage selon l'index). */
@Composable
fun Modifier.cascade(index: Int): Modifier {
    val reduce = rememberReduceMotion()
    val anim = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(minOf(index, 10) * 45L)
        anim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 48f
    }
}

@Composable
fun FadeInOnce(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(300)) }
    Box(Modifier.graphicsLayer { alpha = a.value }) { content() }
}
