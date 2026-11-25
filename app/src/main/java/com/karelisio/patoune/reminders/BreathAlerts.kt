package com.karelisio.patoune.reminders

import com.karelisio.patoune.R
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Breath

/** Alerte quand une mesure dépasse le seuil fixé avec le véto. */
object BreathAlerts {
    suspend fun check(c: AppContainer, animal: Animal, count: BreathCount): Boolean {
        if (!Breath.exceeds(count.bpm, animal.breathThreshold)) return false
        val title = c.context.getString(R.string.notif_breath_title, animal.name)
        val body = c.context.getString(R.string.notif_breath_body, Breath.format(count.bpm), animal.breathThreshold ?: 0)

        Notif.simple(c.context, Notif.CH_ALERTS, ("breath" + animal.id).hashCode(), title, body)
        c.hooks.onPush(PushType.BREATH, title, body, count.id)
        return true
    }
}
