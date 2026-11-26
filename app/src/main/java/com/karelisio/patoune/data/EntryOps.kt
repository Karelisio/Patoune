package com.karelisio.patoune.data

import com.karelisio.patoune.domain.Breath
import kotlin.math.roundToInt

/** Saisie de la fréquence : directement en resp/min, ou nombre de respirations comptées sur une durée. */
sealed interface BreathInput {
    data class Rate(val perMinute: Double) : BreathInput
    data class Taps(val taps: Int, val durationSec: Int) : BreathInput
}

/** Création et correction après coup des mesures et observations (toujours via le Store). */
object EntryOps {
    /** Plage plausible d'une fréquence saisie à la main. */
    private val plausible = 1.0..300.0

    /** Mesure modifiée ou ajoutée ; null si la saisie est incomplète ou invraisemblable. La fréquence est recalculée. */
    fun breath(base: BreathCount, at: Long, input: BreathInput, context: BreathContext, note: String): BreathCount? {
        val (taps, duration, bpm) = when (input) {
            is BreathInput.Rate -> {
                if (input.perMinute !in plausible) return null
                // Valeur inchangée : on garde le comptage d'origine (taps et durée).
                if (input.perMinute == base.bpm && base.durationSec > 0) Triple(base.taps, base.durationSec, base.bpm)
                else Triple(input.perMinute.roundToInt(), 60, input.perMinute)
            }
            is BreathInput.Taps -> {
                if (input.taps <= 0 || input.durationSec <= 0) return null
                val bpm = Breath.perMinute(input.taps, input.durationSec)
                if (bpm !in plausible) return null
                Triple(input.taps, input.durationSec, bpm)
            }
        }
        // Une estimation du comptage auto reste marquée comme telle tant que sa valeur n'est pas corrigée.
        return base.copy(at = at, taps = taps, durationSec = duration, bpm = bpm, context = context, note = note.trim(),
            estimated = base.estimated && bpm == base.bpm)
    }

    suspend fun saveBreath(store: Store, base: BreathCount, at: Long, input: BreathInput, context: BreathContext, note: String): BreathCount? =
        breath(base, at, input, context, note)?.let { store.save(it) }

    /** Observation modifiée ou ajoutée ; la valeur doit être une des options (clés françaises) de la catégorie. */
    fun observation(base: Observation, at: Long, category: ObsCategory, value: String, note: String): Observation? {
        if (value !in category.options) return null
        return base.copy(at = at, category = category, value = value, note = note.trim())
    }

    suspend fun saveObservation(store: Store, base: Observation, at: Long, category: ObsCategory, value: String, note: String): Observation? =
        observation(base, at, category, value, note)?.let { store.save(it) }
}
