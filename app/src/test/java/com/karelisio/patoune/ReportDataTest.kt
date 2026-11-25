package com.karelisio.patoune

import com.karelisio.patoune.air.AirRepo
import com.karelisio.patoune.domain.Adherence
import com.karelisio.patoune.domain.ReminderPlanner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReportDataTest {
    @Test fun dailyMaxFromHourlyAirQuality() {
        val r = Json.parseToJsonElement("""{"hourly":{"time":["2026-04-01T00:00","2026-04-01T12:00","2026-04-02T00:00"],
            "grass_pollen":[1.0,40.0,null],"birch_pollen":[0.0,120.5,3.0],"european_aqi":[20,35,18]}}""").jsonObject
        val days = AirRepo.parseDaily(r, "Lyon")
        assertEquals(listOf("2026-04-01", "2026-04-02"), days.map { it.date })
        assertEquals(40.0, days[0].grass!!, 0.0)
        assertEquals(120.5, days[0].pollenMax!!, 0.0)
        assertEquals(35.0, days[0].aqi!!, 0.0)
        assertEquals(3.0, days[1].pollenMax!!, 0.0)
    }

    @Test fun expectedDosesOverPeriod() {
        val z = ZoneId.of("Europe/Paris")
        val from = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, z).toInstant().toEpochMilli()
        val to = ZonedDateTime.of(2026, 4, 7, 23, 59, 0, 0, z).toInstant().toEpochMilli()
        val times = ReminderPlanner.parseTimes("08:00,20:00")
        assertEquals(14, Adherence.expected(times, null, null, from, to, z))
        assertEquals(6, Adherence.expected(times, LocalDate.of(2026, 4, 5), null, from, to, z))
        assertEquals(85, Adherence.percent(12, 14))
    }
}
