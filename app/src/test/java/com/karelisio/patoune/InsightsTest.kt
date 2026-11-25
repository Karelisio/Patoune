package com.karelisio.patoune

import com.karelisio.patoune.data.AirDay
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.domain.Insights.DayPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 25)

    private fun ms(d: LocalDate, hour: Int = 10) = LocalDateTime.of(d, LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()
    private fun crisis(d: LocalDate, hour: Int = 10, triggers: String = "", types: String = "", deleted: Boolean = false) =
        Crisis(id = "$d-$hour-$triggers", startAt = ms(d, hour), triggers = triggers, types = types, deleted = deleted)
    private fun air(d: LocalDate, pollen: Double?, aqi: Double? = null) = AirDay(d.toString(), "Lyon", aqi, null, pollen, null, null, null, null)
    private fun breath(d: LocalDate, bpm: Double) = BreathCount(id = "$d-$bpm", at = ms(d, 8), bpm = bpm)

    @Test fun perMonthCountsTwelveMonthsWithZeros() {
        val crises = listOf(
            crisis(LocalDate.of(2026, 11, 2)), crisis(LocalDate.of(2026, 11, 20)),
            crisis(LocalDate.of(2026, 3, 15)),
            crisis(LocalDate.of(2025, 10, 31)), // hors fenêtre (avant décembre 2025)
            crisis(LocalDate.of(2026, 6, 1), deleted = true),
        )
        val s = Insights.lastYear(crises, emptyList(), emptyList(), zone, today)
        assertEquals(12, s.months.size)
        assertEquals(YearMonth.of(2025, 12), s.months.first().month)
        assertEquals(YearMonth.of(2026, 11), s.months.last().month)
        assertEquals(2, s.months.last().count)
        assertEquals(1, s.months.first { it.month == YearMonth.of(2026, 3) }.count)
        assertEquals(0, s.months.first { it.month == YearMonth.of(2026, 6) }.count)
        assertEquals(3, s.total)
    }

    @Test fun monthUsesLocalZone() {
        // 31 octobre 23 h 30 à Paris = 22 h 30 UTC : reste en octobre.
        val late = LocalDateTime.of(2026, 10, 31, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val months = Insights.perMonth(listOf(Crisis(id = "x", startAt = late)), zone, YearMonth.of(2026, 10), YearMonth.of(2026, 11))
        assertEquals(listOf(1, 0), months.map { it.count })
    }

    @Test fun triggerRanking() {
        val d = LocalDate.of(2026, 11, 1)
        val crises = listOf(
            crisis(d, 1, "Litière, Pollens", "Toux"),
            crisis(d, 2, "Pollens", "Toux,Sifflement"),
            crisis(d, 3, "Pollens,Pollens,Stress", "Toux"),
            crisis(d, 4, "", ""),
        )
        val r = Insights.ranking(crises) { it.triggers }
        assertEquals(listOf("Pollens", "Litière", "Stress"), r.map { it.label })
        assertEquals(3, r[0].count)
        assertEquals(75, r[0].percent)
        assertEquals(25, r[1].percent)
        val types = Insights.ranking(crises) { it.types }
        assertEquals(Insights.Share("Toux", 3, 75), types[0])
        assertEquals(Insights.Share("Sifflement", 1, 25), types[1])
        assertTrue(Insights.ranking(emptyList()) { it.triggers }.isEmpty())
    }

    @Test fun pollenComparisonEnoughData() {
        val base = LocalDate.of(2026, 5, 1)
        val crisisDays = (0L..2L).map { base.plusDays(it) }
        val airDays = crisisDays.map { air(it, 90.0, aqi = 60.0) } + (3L..6L).map { air(base.plusDays(it), 30.0, aqi = 20.0) }
        val s = Insights.summarize(crisisDays.map { crisis(it) }, emptyList(), airDays, zone, base, base.plusDays(10))
        assertNotNull(s.pollen)
        val p = s.pollen!!
        assertEquals(90.0, p.with, 1e-9)
        assertEquals(30.0, p.without, 1e-9)
        assertEquals(3, p.daysWith)
        assertEquals(4, p.daysWithout)
        assertEquals(60.0, s.aqi!!.with, 1e-9)
        assertEquals(
            "Les jours de crise, le pollen moyen était de 90 grains/m³ contre 30 les autres jours.",
            Insights.pollenSentence(p),
        )
    }

    @Test fun pollenComparisonNotEnoughData() {
        val base = LocalDate.of(2026, 5, 1)
        // Deux jours de crise seulement (< 3).
        val airDays = (0L..6L).map { air(base.plusDays(it), 50.0) }
        val s = Insights.summarize(listOf(crisis(base), crisis(base.plusDays(1))), emptyList(), airDays, zone, base, base.plusDays(10))
        assertNull(s.pollen)
        assertNull(s.aqi) // aucun AQI renseigné
        assertTrue(Insights.pollenSentence(s.pollen).contains("pas encore assez"))
        // Jours sans pollen (null) ignorés : 3 jours de crise mais seulement 2 autres jours connus.
        val sparse = (0L..2L).map { air(base.plusDays(it), 80.0) } + listOf(air(base.plusDays(3), 10.0), air(base.plusDays(4), 10.0), air(base.plusDays(5), null))
        assertNull(Insights.compare(sparse.associate { LocalDate.parse(it.date) to it.pollenMax }, (0L..2L).map { base.plusDays(it) }.toSet()))
    }

    @Test fun breathByPollenUsesMedian() {
        val base = LocalDate.of(2026, 4, 1)
        val pollen = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0)
        val airDays = pollen.mapIndexed { i, p -> air(base.plusDays(i.toLong()), p) }
        // Médiane 40 : jours 50/60/70 « forts », 10/20/30/40 « faibles ».
        val breaths = pollen.indices.flatMap { i ->
            val d = base.plusDays(i.toLong())
            if (pollen[i] > 40) listOf(breath(d, 34.0), breath(d, 36.0)) else listOf(breath(d, 24.0))
        }
        val c = Insights.summarize(emptyList(), breaths, airDays, zone, base, base.plusDays(6)).breathByPollen!!
        assertEquals(35.0, c.with, 1e-9)
        assertEquals(24.0, c.without, 1e-9)
        assertEquals(3, c.daysWith)
        assertEquals(4, c.daysWithout)
        // Trop peu de jours mesurés côté fort.
        assertNull(Insights.breathByPollen(breaths.filter { it.bpm < 30 }, airDays.associate { LocalDate.parse(it.date) to it.pollenMax }, zone))
    }

    @Test fun dayPartDistribution() {
        val d = LocalDate.of(2026, 11, 1)
        val crises = listOf(crisis(d, 7), crisis(d, 19), crisis(d, 21), crisis(d, 23), crisis(d, 3), crisis(d, 14))
        val s = Insights.summarize(crises, emptyList(), emptyList(), zone, d, d)
        assertEquals(mapOf(DayPart.MORNING to 1, DayPart.AFTERNOON to 1, DayPart.EVENING to 3, DayPart.NIGHT to 1), s.dayParts)
        assertEquals(DayPart.EVENING, s.topDayPart)
        assertEquals("Les crises surviennent le plus souvent le soir (3 sur 6).", Insights.dayPartSentence(s))
        assertEquals(DayPart.NIGHT, DayPart.of(0))
        assertEquals(DayPart.MORNING, DayPart.of(6))
        assertEquals(DayPart.AFTERNOON, DayPart.of(12))
        assertEquals(DayPart.EVENING, DayPart.of(18))
        // Égalité en tête : pas de moment dominant.
        val tie = Insights.summarize(listOf(crisis(d, 8), crisis(d, 20)), emptyList(), emptyList(), zone, d, d)
        assertNull(tie.topDayPart)
        assertNull(Insights.summarize(emptyList(), emptyList(), emptyList(), zone, d, d).topDayPart)
    }
}
