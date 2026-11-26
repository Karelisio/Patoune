package com.karelisio.patoune.report

import com.karelisio.patoune.ui.screens.fmtDuration
import com.karelisio.patoune.data.CrisisTypes
import com.karelisio.patoune.ui.Labels
import com.karelisio.patoune.ui.FreqText
import com.karelisio.patoune.ui.InsightsText
import com.karelisio.patoune.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.domain.Adherence
import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.domain.Schedule
import com.karelisio.patoune.media.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Rapport PDF pour le véto sur une période : profil, observance, respiration, crises, poids, observations. */
class ReportGenerator(private val context: Context, private val c: AppContainer) {
    private val W = 595f; private val H = 842f; private val M = 40f
    private val accent = Color.rgb(154, 74, 43)
    private val sage = Color.rgb(85, 99, 79)

    private inner class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var y = 0f
        var n = 0
        val canvas: Canvas get() = page!!.canvas
        val text = TextPaint().apply { color = Color.rgb(35, 26, 22); textSize = 10f; isAntiAlias = true }
        val muted = TextPaint(text).apply { color = Color.rgb(110, 98, 92); textSize = 9f }
        val h1 = TextPaint(text).apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = accent }
        val h2 = TextPaint(text).apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = accent }

        fun newPage() {
            page?.let { finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            y = M
        }

        private fun finishPage(p: PdfDocument.Page) {
            p.canvas.drawText(str(R.string.pdf_footer, n), M, H - 20f, muted)
            doc.finishPage(p)
        }

        fun finish() { page?.let { finishPage(it) }; page = null }

        fun ensure(h: Float) { if (y + h > H - 40f) newPage() }

        fun para(s: String, paint: TextPaint = text, gap: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, (W - 2 * M).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            ensure(layout.height.toFloat())
            canvas.save(); canvas.translate(M, y); layout.draw(canvas); canvas.restore()
            y += layout.height + gap
        }

        fun title(s: String) { ensure(40f); y += 10f; para(s, h2, 6f) }
    }

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun plural(id: Int, n: Int, vararg args: Any): String = context.resources.getQuantityString(id, n, *args)

    suspend fun generate(animal: Animal, from: LocalDate, to: LocalDate): File = withContext(Dispatchers.IO) {
        val zone = Dates.zone()
        val t0 = Dates.startOfDay(from); val t1 = Dates.startOfDay(to.plusDays(1)) - 1
        val doc = PdfDocument()
        val w = Writer(doc)
        w.newPage()

        // En-tête et profil.
        w.para(str(R.string.pdf_title, animal.name), w.h1, 2f)
        w.para(str(R.string.pdf_period, from.format(Dates.day), to.format(Dates.day), Dates.today().format(Dates.day)), w.muted, 10f)
        val weights = c.db.weights().forAnimal(animal.id)
        w.title(str(R.string.animal_profile))
        w.para(listOfNotNull(
            Labels.animalTraits(context, animal.species, animal.breed, animal.sex).takeIf { it.isNotBlank() },
            animal.birthDate?.let { str(R.string.emergency_born, Dates.fmtDay(it)) },
            if (animal.chipNumber.isNotBlank()) str(R.string.emergency_chip, animal.chipNumber) else null,
            str(if (animal.sterilized) R.string.emergency_sterilized_yes else R.string.emergency_sterilized_no),
            weights.lastOrNull()?.let { str(R.string.emergency_weight, "%.2f".format(it.kg), Dates.fmtDay(it.date)) },
            animal.breathThreshold?.let { str(R.string.emergency_threshold, it) },
        ).joinToString("\n"))
        if (animal.allergies.isNotBlank()) w.para(str(R.string.emergency_allergies, animal.allergies))
        if (animal.history.isNotBlank()) w.para(str(R.string.emergency_history, animal.history))

        // Traitements et observance.
        w.title(str(R.string.pdf_treatments))
        val meds = c.db.medications().allRows().filter { it.animalId == animal.id && !it.deleted }
        val doses = c.db.doses().between(animal.id, t0, t1)
        if (meds.isEmpty()) w.para(str(R.string.pdf_no_treatment))
        for (m in meds) {
            val given = doses.count { it.medicationId == m.id && it.status == DoseStatus.GIVEN }
            val skipped = doses.count { it.medicationId == m.id && it.status == DoseStatus.SKIPPED }
            val dose = m.dose.ifBlank { str(m.type.labelRes) }
            val line = if (m.asNeeded) plural(R.plurals.pdf_med_as_needed, given, m.name, dose, given)
            else {
                val exp = Adherence.expected(Schedule.of(m, zone), t0, minOf(t1, System.currentTimeMillis()))
                val pct = Adherence.percent(given, exp)
                listOfNotNull(
                    plural(R.plurals.pdf_med_fixed, exp, m.name, dose, FreqText.summary(context, m), given, exp),
                    pct?.let { str(R.string.pdf_adherence, it) },
                    if (skipped > 0) plural(R.plurals.pdf_skipped, skipped, skipped) else null,
                ).joinToString(" · ")
            }
            w.para(str(R.string.pdf_bullet, line))
        }
        // Changements de traitement (dose, horaires, dates) sur la période.
        val changes = c.db.medicationChanges().between(animal.id, t0, t1)
        if (changes.isNotEmpty()) {
            val names = c.db.medications().allRows().associate { it.id to it.name }
            w.para(str(R.string.pdf_changes), w.muted, 2f)
            for (ch in changes) {
                // Résumé enregistré tel quel (dans la langue de la personne qui a modifié).
                val what = ch.summary.lines().filter { it.isNotBlank() }.joinToString(" ; ")
                w.para(str(R.string.pdf_change_line, Dates.fmtDay(ch.at), names[ch.medicationId] ?: str(R.string.pdf_deleted_treatment), what))
            }
        }

        // Respiration.
        val breaths = c.db.breaths().between(animal.id, t0, t1)
        w.title(str(R.string.breath_rate))
        if (breaths.isEmpty()) w.para(str(R.string.pdf_no_breath))
        else {
            val stats = plural(R.plurals.pdf_breath_stats, breaths.size, breaths.size, Breath.format(breaths.map { it.bpm }.average()),
                Breath.format(breaths.minOf { it.bpm }), Breath.format(breaths.maxOf { it.bpm }))
            w.para(animal.breathThreshold?.let { t -> breaths.count { it.bpm > t }.let { n -> str(R.string.pair_dot, stats, plural(R.plurals.pdf_over_threshold, n, n)) } } ?: stats)
            val crises = c.db.crises().between(animal.id, t0, t1)
            chart(w, breaths.map { it.at to it.bpm }, t0, t1, animal.breathThreshold?.toDouble(), crises.map { it.startAt }, airLevels(from, to))
            w.para(str(R.string.pdf_breath_legend), w.muted)
        }

        // Crises.
        val crises = c.db.crises().between(animal.id, t0, t1)
        w.title(str(R.string.pdf_crises, crises.size))
        if (crises.isEmpty()) w.para(str(R.string.journal_empty))
        for (cr in crises) {
            val thumbs = c.db.media().attached(cr.id).take(3).mapNotNull { thumbnail(it.id, it.kind) }
            w.ensure(if (thumbs.isNotEmpty()) 110f else 40f)
            val head = listOfNotNull(
                Dates.fmtDayTime(cr.startAt), str(R.string.pdf_intensity, cr.intensity), fmtDuration(context, cr.durationSec),
                cr.types.takeIf { it.isNotBlank() }?.let { Labels.csv(context, CrisisTypes.labels, it) },
            ).joinToString(" · ")
            w.para(listOfNotNull(
                head,
                cr.emergencyTreatment.takeIf { it.isNotBlank() }?.let { str(R.string.pdf_rescue, it) },
                cr.triggers.takeIf { it.isNotBlank() }?.let { str(R.string.pdf_triggers, Labels.csv(context, CrisisTypes.labels, it)) },
                cr.notes.takeIf { it.isNotBlank() },
            ).joinToString("\n"), gap = 2f)
            if (thumbs.isNotEmpty()) {
                var x = M
                thumbs.forEach { b ->
                    val h = 80f; val wd = h * b.width / b.height
                    w.canvas.drawBitmap(b, null, RectF(x, w.y, x + wd, w.y + h), null)
                    x += wd + 8f
                }
                w.y += 88f
            }
        }

        // Poids.
        val wPeriod = weights.filter { val d = Dates.parse(it.date); d != null && !d.isBefore(from.minusMonths(6)) && !d.isAfter(to) }
        if (wPeriod.size > 1) {
            w.title(str(R.string.pdf_weight))
            chart(w, wPeriod.map { Dates.startOfDay(LocalDate.parse(it.date)) to it.kg }, Dates.startOfDay(LocalDate.parse(wPeriod.first().date)), Dates.startOfDay(LocalDate.parse(wPeriod.last().date)), null, emptyList(), emptyList(), sage)
        }

        // Analyse des déclencheurs sur la période (mêmes chiffres que la carte « Analyse » du journal).
        val ins = Insights.summarize(crises, breaths, c.db.air().between(from.toString(), to.toString()), zone, from, to)
        w.title(str(R.string.insights_title))
        w.para(str(R.string.pdf_per_month, InsightsText.months(context, ins.months)))
        if (ins.triggers.isNotEmpty()) w.para(str(R.string.pdf_noted_triggers, InsightsText.shares(context, ins.triggers)))
        if (ins.types.isNotEmpty()) w.para(str(R.string.pdf_crisis_types, InsightsText.shares(context, ins.types)))
        InsightsText.sentences(context, ins).forEach { w.para(str(R.string.pdf_bullet, it)) }
        w.para(str(R.string.pdf_insights_note), w.muted)

        // Observations.
        val obs = c.db.observations().between(animal.id, t0, t1)
        w.title(str(R.string.journal_tab_observations))
        if (obs.isEmpty()) w.para(str(R.string.pdf_no_observation))
        obs.groupBy { it.category }.forEach { (cat, list) ->
            val counts = list.groupingBy { it.value }.eachCount().entries.joinToString(", ") { str(R.string.pdf_obs_count, Labels.obs(context, it.key), it.value) }
            w.para(str(R.string.pdf_bullet, str(R.string.obs_line, str(cat.labelRes), counts)))
        }

        c.settings.current().city?.let { w.para(str(R.string.pdf_air_source, it), w.muted) }
        w.para(str(R.string.pdf_disclaimer), w.muted)

        w.finish()

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "Patoune-${animal.name.replace(Regex("[^A-Za-z0-9]"), "_")}-$from-$to.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        out
    }

    private suspend fun airLevels(from: LocalDate, to: LocalDate): List<Pair<Long, Float>> =
        c.db.air().between(from.toString(), to.toString()).mapNotNull { d -> d.pollenMax?.let { Dates.startOfDay(LocalDate.parse(d.date)) to (it / 200.0).toFloat().coerceIn(0.03f, 1f) } }

    private fun thumbnail(id: String, kind: MediaKind): Bitmap? {
        val f = c.media.file(id).takeIf { it.exists() } ?: return null
        return when (kind) {
            MediaKind.VIDEO -> VideoCompressor.thumbnail(f)
            MediaKind.PHOTO -> BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 })
            MediaKind.PDF -> null
        }
    }

    private fun chart(w: Writer, pts: List<Pair<Long, Double>>, x0: Long, x1: Long, threshold: Double?, markers: List<Long>, bands: List<Pair<Long, Float>>, color: Int = accent) {
        val h = 150f
        w.ensure(h + 10f)
        val cv = w.canvas
        val left = M + 24f; val right = W - M; val top = w.y; val bottom = w.y + h
        val ys = pts.map { it.second } + listOfNotNull(threshold)
        val minY = (ys.min() - 3).coerceAtLeast(0.0); val maxY = ys.max() + 3
        val span = (x1 - x0).coerceAtLeast(1)
        fun px(x: Long) = left + (x - x0).toFloat() / span * (right - left)
        fun py(y: Double) = bottom - ((y - minY) / (maxY - minY)).toFloat() * h
        val grid = Paint().apply { this.color = Color.rgb(230, 220, 215); strokeWidth = 0.5f }
        bands.forEach { (x, lvl) ->
            cv.drawRect(px(x), bottom - h * lvl, px(x + 86_400_000L), bottom, Paint().apply { this.color = Color.argb((30 + 90 * lvl).toInt(), 85, 99, 79) })
        }
        listOf(minY, (minY + maxY) / 2, maxY).forEach { v ->
            cv.drawLine(left, py(v), right, py(v), grid)
            cv.drawText("%.0f".format(v), M, py(v) + 3f, w.muted)
        }
        threshold?.let { t -> cv.drawLine(left, py(t), right, py(t), Paint().apply { this.color = Color.rgb(186, 26, 26); strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) }) }
        val avg = Breath.movingAverage(pts.map { it.second })
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.first), py(avg[i])) else path.lineTo(px(p.first), py(avg[i])) }
        cv.drawPath(path, Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
        val dot = Paint().apply { this.color = color; isAntiAlias = true; alpha = 160 }
        pts.forEach { cv.drawCircle(px(it.first), py(it.second), 2.2f, dot) }
        val red = Paint().apply { this.color = Color.rgb(186, 26, 26); isAntiAlias = true }
        markers.forEach { cv.drawCircle(px(it), bottom - 3f, 3f, red) }
        w.y = bottom + 10f
    }
}
