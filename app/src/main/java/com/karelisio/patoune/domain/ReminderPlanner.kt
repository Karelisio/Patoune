package com.karelisio.patoune.domain

import com.karelisio.patoune.data.Medication
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ReminderPlanner {
    fun parseTimes(csv: String): List<LocalTime> = csv.split(",").mapNotNull {
        runCatching { LocalTime.parse(it.trim()) }.getOrNull()
    }.distinct().sorted()

    /**
     * Prochain rappel quotidien strictement après [after] dans le fuseau [zone], entre [start] et [end] inclus.
     * L'heure murale est conservée lors des changements d'heure (voir [Schedule]).
     */
    fun next(times: List<LocalTime>, after: Long, zone: ZoneId, start: LocalDate?, end: LocalDate?): Long? =
        Schedule.daily(times, zone, start, end).next(after)

    /** Prochain rappel d'un traitement selon sa fréquence (tous les N jours, certains jours, mensuel…). */
    fun next(med: Medication, after: Long, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Long? =
        Schedule.of(med, zone, today).next(after)

    /** Idem, le cycle des fréquences espacées repartant de la dernière prise donnée [lastGiven] (voir [Schedule.of]). */
    fun next(med: Medication, after: Long, zone: ZoneId, lastGiven: Long?, today: LocalDate = LocalDate.now(zone)): Long? =
        Schedule.of(med, zone, lastGiven, today).next(after)

    fun snooze(now: Long, minutes: Int = 15): Long = now + minutes * 60_000L
}
