package com.karelisio.patoune.reminders

import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Breath

/** Alerte quand une mesure dépasse le seuil fixé avec le véto. */
object BreathAlerts {
    suspend fun check(c: AppContainer, animal: Animal, count: BreathCount): Boolean {
        if (!Breath.exceeds(count.bpm, animal.breathThreshold)) return false
        val title = "Respiration élevée : ${animal.name}"
        val body = "${Breath.format(count.bpm)} resp/min (seuil fixé avec le véto : ${animal.breathThreshold}). Recomptez au calme et contactez votre véto si besoin."
        Notif.simple(c.context, Notif.CH_ALERTS, ("breath" + animal.id).hashCode(), title, body)
        c.hooks.onPush(PushType.BREATH, title, body, count.id)
        return true
    }
}
