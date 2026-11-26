package com.karelisio.patoune.domain

import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import java.time.LocalDate
import java.time.LocalTime

/**
 * Changements importants d'un traitement (pour l'historique et le rapport véto), sous forme structurée :
 * la mise en forme textuelle (traduite) est faite côté UI par `MedDiffText`.
 * Le stock, les notes et le type ne comptent pas : seuls la posologie, le calendrier (horaires, fréquence, dates)
 * et le mode de rappel.
 */
object MedDiff {
    sealed interface Change {
        /** [freq] : fréquence si autre que « tous les jours ». */
        data class Created(
            val dose: String, val asNeeded: Boolean, val times: List<LocalTime>, val start: LocalDate?, val end: LocalDate?,
            val freq: Frequency? = null,
        ) : Change
        data class Name(val from: String, val to: String) : Change
        data class Dose(val from: String, val to: String) : Change
        data class Units(val from: Double, val to: Double) : Change
        data class Times(val from: List<LocalTime>, val to: List<LocalTime>) : Change
        /** Fréquence (tous les N jours, certains jours, mensuel…), ancrée sur la date de début de chaque version. */
        data class Recurrence(val from: Frequency, val to: Frequency) : Change
        data object ToAsNeeded : Change
        data class ToFixed(val times: List<LocalTime>) : Change
        data class Start(val from: LocalDate?, val to: LocalDate?) : Change
        data class End(val from: LocalDate?, val to: LocalDate?) : Change
        data class Alarm(val on: Boolean) : Change
    }

    fun diff(old: Medication?, new: Medication): List<Change> {
        if (old == null) return listOf(
            Change.Created(
                new.dose.trim(), new.asNeeded, times(new.times), Dates.parse(new.startDate), Dates.parse(new.endDate),
                Frequency.of(new).takeIf { !new.asNeeded && it.type != FreqType.DAILY },
            ),
        )
        val out = ArrayList<Change>()
        if (old.name.trim() != new.name.trim()) out += Change.Name(old.name.trim(), new.name.trim())
        if (old.dose.trim() != new.dose.trim()) out += Change.Dose(old.dose.trim(), new.dose.trim())
        if (old.unitsPerDose != new.unitsPerDose) out += Change.Units(old.unitsPerDose, new.unitsPerDose)
        when {
            !old.asNeeded && new.asNeeded -> out += Change.ToAsNeeded
            old.asNeeded && !new.asNeeded -> out += Change.ToFixed(times(new.times))
            !new.asNeeded -> if (times(old.times) != times(new.times)) out += Change.Times(times(old.times), times(new.times))
        }
        if (!new.asNeeded) {
            val a = Frequency.of(old)
            val b = Frequency.of(new)
            if (a.key != b.key) out += Change.Recurrence(a, b)
        }
        Dates.parse(old.startDate).let { a -> Dates.parse(new.startDate).let { b -> if (a != b) out += Change.Start(a, b) } }
        Dates.parse(old.endDate).let { a -> Dates.parse(new.endDate).let { b -> if (a != b) out += Change.End(a, b) } }
        if (old.alarmStyle != new.alarmStyle) out += Change.Alarm(new.alarmStyle)
        return out
    }

    private fun times(csv: String): List<LocalTime> = ReminderPlanner.parseTimes(csv)
}
