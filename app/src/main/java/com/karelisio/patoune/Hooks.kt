package com.karelisio.patoune

import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.sync.SyncWorker

/**
 * Réactions transverses aux événements métier (widget, sync, pushs).
 * En solo, rien ne part sur le réseau : seules les mises à jour locales (widget) ont lieu.
 */
open class Hooks(private val c: AppContainer) {
    private suspend fun shared() = c.settings.current().shared

    open suspend fun onDose(med: Medication, log: DoseLog) {
        if (shared() && log.status == com.karelisio.patoune.data.DoseStatus.GIVEN) {
            val animal = c.db.animals().byId(med.animalId)?.name.orEmpty()
            onPush(PushType.DOSE, c.context.getString(R.string.push_dose_title, med.name),
                c.context.getString(R.string.push_dose_body, animal, com.karelisio.patoune.domain.Dates.fmtTime(log.takenAt)), med.id)

        }
        onDataChanged()
    }

    open suspend fun onPush(type: PushType, title: String, body: String, refId: String?) {
        if (!shared()) return
        runCatching { c.household.push(type, title, body, refId) }
    }

    open suspend fun onDataChanged() {
        c.widgets.refresh()
    }

    /** Appelé à chaque écriture locale (Store). */
    fun onLocalWrite() {
        if (c.sharedFlag) SyncWorker.now(c.context)
    }

    open suspend fun onDaily() {
        c.widgets.refresh()
        // Pollens du jour (Open-Meteo, pas le backend) : silencieux hors ligne.
        val s = c.settings.current()
        if (s.city != null && s.cityLat != null && s.cityLon != null) runCatching { c.air.fetch(s.city, s.cityLat, s.cityLon) }
        if (shared()) SyncWorker.now(c.context)
    }

    /** Après un import de sauvegarde : en partagé, renvoyer les données au foyer. */
    open suspend fun onImported() {
        if (shared()) { c.store.queueSnapshot(); SyncWorker.now(c.context) }
        onDataChanged()
    }

    /** Données reçues d'un autre membre : rappels et widget à jour, rappels annulés si la prise est faite. */
    open suspend fun onRemote(entities: Set<String>, medIds: Set<String>) {
        if ("medication" in entities) c.reminders.rescheduleAll()
        for (id in medIds) {
            val med = c.db.medications().byId(id) ?: continue
            if (c.doses.alreadyGiven(med) != null) {
                com.karelisio.patoune.reminders.Notif.cancel(c.context, com.karelisio.patoune.reminders.ReminderScheduler.notifId(id))
                com.karelisio.patoune.reminders.Notif.cancel(c.context, com.karelisio.patoune.reminders.ReminderScheduler.latePushNotifId(id))
            }
        }
        c.widgets.refresh()
    }
}
