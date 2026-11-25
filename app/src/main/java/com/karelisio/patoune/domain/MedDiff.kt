package com.karelisio.patoune.domain

import com.karelisio.patoune.data.Medication

/**
 * Résumé lisible des changements importants d'un traitement (pour l'historique et le rapport véto).
 * Le stock, les notes et le type ne comptent pas : seuls la posologie, le calendrier et le mode de rappel.
 */
object MedDiff {
    fun diff(old: Medication?, new: Medication): List<String> {
        if (old == null) return listOf(created(new))
        val out = ArrayList<String>()
        fun line(label: String, a: String, b: String) { if (a != b) out += "$label : $a → $b" }
        line("Nom", old.name.trim().ifBlank { "—" }, new.name.trim().ifBlank { "—" })
        line("Dose", old.dose.trim().ifBlank { "—" }, new.dose.trim().ifBlank { "—" })
        line("${new.type.unit.replaceFirstChar { it.uppercase() }}(s) par prise", qty(old.unitsPerDose), qty(new.unitsPerDose))
        when {
            !old.asNeeded && new.asNeeded -> out += "Mode : horaires fixes → si besoin"
            old.asNeeded && !new.asNeeded -> out += "Mode : si besoin → horaires ${times(new.times)}"
            !new.asNeeded -> line("Horaires", times(old.times), times(new.times))
        }
        line("Début", Dates.fmtDay(old.startDate), Dates.fmtDay(new.startDate))
        line("Fin", Dates.fmtDay(old.endDate), Dates.fmtDay(new.endDate))
        if (old.alarmStyle != new.alarmStyle) out += "Rappel type réveil : ${if (new.alarmStyle) "activé" else "désactivé"}"
        return out
    }

    private fun created(m: Medication): String {
        val parts = listOfNotNull(
            m.dose.trim().takeIf { it.isNotEmpty() },
            if (m.asNeeded) "si besoin" else times(m.times),
            Dates.parse(m.startDate)?.let { "dès le ${it.format(Dates.day)}" },
            Dates.parse(m.endDate)?.let { "jusqu'au ${it.format(Dates.day)}" },
        )
        return "Nouveau traitement" + if (parts.isEmpty()) "" else " : " + parts.joinToString(", ")
    }

    private fun times(csv: String): String =
        ReminderPlanner.parseTimes(csv).joinToString(", ") { it.format(Dates.time) }.ifEmpty { "—" }

    private fun qty(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString().replace('.', ',')
}
