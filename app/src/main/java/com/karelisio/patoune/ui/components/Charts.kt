package com.karelisio.patoune.ui.components

import com.karelisio.patoune.ui.fmtQty
import com.karelisio.patoune.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartPoint(val x: Long, val y: Double)

/** Barre de fond (pollens / AQI) normalisée 0..1 sur une journée. */
data class ChartBand(val x: Long, val widthMs: Long, val level: Float)

/**
 * Courbe qui se dessine à l'apparition : points mesurés, moyenne mobile, seuil en pointillé,
 * repères de crises et bandes de pollens en fond. TalkBack lit un résumé ([description] ou résumé par défaut).
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    average: List<ChartPoint> = emptyList(),
    threshold: Double? = null,
    markers: List<Long> = emptyList(),
    bands: List<ChartBand> = emptyList(),
    height: Dp = 200.dp,
    unit: String = "",
    color: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
) {
    val summary = description ?: if (points.isEmpty()) stringResource(R.string.a11y_chart_empty) else {
        val over = threshold?.let { t -> points.count { it.y > t } } ?: 0
        pluralStringResource(
            R.plurals.a11y_chart_summary, points.size, points.size,
            fmtQty(points.minOf { it.y }), fmtQty(points.maxOf { it.y }), fmtQty(points.last().y), unit,
        ) + if (threshold != null) " " + pluralStringResource(R.plurals.a11y_chart_over, over, over, fmtQty(threshold)) else ""

    }
    val reduce = rememberReduceMotion()
    val progress = remember(points.size, points.firstOrNull()?.x) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, tween(900)) }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = summary }) {

        if (points.isEmpty()) return@Canvas
        val minX = minOf(points.minOf { it.x }, bands.minOfOrNull { it.x } ?: Long.MAX_VALUE)
        val maxX = maxOf(points.maxOf { it.x }, (bands.maxOfOrNull { it.x + it.widthMs } ?: 0L)).let { if (it == minX) minX + 1 else it }
        val ys = points.map { it.y } + listOfNotNull(threshold)
        val minY = (ys.min() - 4).coerceAtLeast(0.0)
        val maxY = ys.max() + 4
        val left = 28.dp.toPx(); val bottom = size.height - 4.dp.toPx(); val top = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        fun px(x: Long) = left + (x - minX).toFloat() / (maxX - minX) * (right - left)
        fun py(y: Double) = bottom - ((y - minY) / (maxY - minY)).toFloat() * (bottom - top)

        bands.forEach { b ->
            val x0 = px(b.x); val x1 = px(b.x + b.widthMs)
            drawRect(colors.tertiary.copy(alpha = 0.10f + 0.35f * b.level), Offset(x0, bottom - (bottom - top) * b.level), Size((x1 - x0).coerceAtLeast(1f), (bottom - top) * b.level))
        }
        // Axe Y : trois graduations.
        listOf(minY, (minY + maxY) / 2, maxY).forEach { v ->
            drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(left, py(v)), Offset(right, py(v)), 1f)
            drawText(measurer, "%.0f".format(v), Offset(0f, py(v) - 7.sp.toPx()), labelStyle)
        }
        threshold?.let { t ->
            drawLine(colors.error, Offset(left, py(t)), Offset(right, py(t)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        markers.forEach { m -> if (m in minX..maxX) drawCircle(colors.error, 4.dp.toPx(), Offset(px(m), bottom - 2.dp.toPx())) }

        fun linePath(list: List<ChartPoint>): Path = Path().apply {
            list.forEachIndexed { i, p -> if (i == 0) moveTo(px(p.x), py(p.y)) else lineTo(px(p.x), py(p.y)) }
        }
        fun drawAnimated(path: Path, c: Color, width: Float) {
            val pm = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            pm.getSegment(0f, pm.length * progress.value, partial, true)
            drawPath(partial, c, style = Stroke(width, cap = StrokeCap.Round))
        }
        if (points.size > 1) drawAnimated(linePath(points), color.copy(alpha = 0.45f), 2.dp.toPx())
        if (average.size > 1) drawAnimated(linePath(average), color, 4.dp.toPx())
        val visible = (points.size * progress.value).toInt()
        points.take(maxOf(1, visible)).forEach { p ->
            val over = threshold != null && p.y > threshold
            drawCircle(if (over) colors.error else color, if (over) 5.dp.toPx() else 3.5.dp.toPx(), Offset(px(p.x), py(p.y)))
        }
    }
}
