package com.karelisio.patoune.reminders

import com.karelisio.patoune.ui.unitQty
import com.karelisio.patoune.R
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.HandledDose
import com.karelisio.patoune.domain.LateCheck
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.Stock
import com.karelisio.patoune.domain.TakenDose

sealed interface DoseResult {
    data class Saved(val log: DoseLog) : DoseResult
    /** Une prise existe déjà dans la fenêtre en cours : demander confirmation. */
    data class AlreadyGiven(val at: Long, val by: String) : DoseResult
}

/** Enregistrement des prises, garde-fou double dose et suivi du stock. */
class Doses(private val c: AppContainer) {

    suspend fun alreadyGiven(med: Medication, now: Long = System.currentTimeMillis()): TakenDose? {
        val window = DoseGuard.currentWindow(ReminderPlanner.parseTimes(med.times), now, Dates.zone(), med.asNeeded)
        val logs = c.db.doses().since(med.id, window.first).filter { it.status == DoseStatus.GIVEN }
        return DoseGuard.alreadyGiven(logs.map { TakenDose(it.takenAt, it.createdBy) }, window)
    }

    /** Vérification de retard : aucune prise donnée ou sautée ne couvre l'horaire [scheduledAt]. */
    suspend fun isLate(med: Medication, scheduledAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val times = ReminderPlanner.parseTimes(med.times)
        val from = DoseGuard.currentWindow(times, scheduledAt, Dates.zone()).first
        val handled = c.db.doses().since(med.id, minOf(from, scheduledAt)).map { HandledDose(it.takenAt, it.scheduledAt) }
        return LateCheck.shouldAlert(times, Dates.zone(), med.lateMinutes, active = !med.deleted && !med.asNeeded, scheduledAt, handled, now)
    }

    suspend fun give(med: Medication, status: DoseStatus, scheduledAt: Long? = null, force: Boolean = false): DoseResult {
        if (status == DoseStatus.GIVEN && !force) alreadyGiven(med)?.let { return DoseResult.AlreadyGiven(it.takenAt, it.by) }
        val log = c.store.save(DoseLog(medicationId = med.id, animalId = med.animalId, scheduledAt = scheduledAt, takenAt = System.currentTimeMillis(), status = status))
        if (status == DoseStatus.GIVEN && med.stockRemaining != null) {
            val updated = c.store.save(med.copy(stockRemaining = Stock.decrement(med.stockRemaining, med.unitsPerDose)))
            checkStock(updated)
        }
        Notif.cancel(c.context, ReminderScheduler.notifId(med.id))
        // Prise donnée ou sautée : plus de relance « En retard » pour cet horaire.
        c.reminders.cancelLate(med.id)
        Notif.cancel(c.context, ReminderScheduler.latePushNotifId(med.id))
        c.hooks.onDose(med, log)
        return DoseResult.Saved(log)
    }

    /** Annule une prise (erreur de saisie) et restitue le stock. */
    suspend fun undo(log: DoseLog) {
        c.store.delete(log)
        val med = c.db.medications().byId(log.medicationId) ?: return
        if (log.status == DoseStatus.GIVEN && med.stockRemaining != null) c.store.save(med.copy(stockRemaining = Stock.increment(med.stockRemaining, med.unitsPerDose)))
        c.hooks.onDataChanged()
    }

    suspend fun checkStock(med: Medication) {
        if (!Stock.isLow(med.stockRemaining, med.stockAlertAt)) return
        val ctx = c.context
        val left = unitQty(ctx, med.type, med.stockRemaining)
        val title = ctx.getString(R.string.notif_stock_title, med.name)
        Notif.simple(ctx, Notif.CH_STOCK, ("stock" + med.id).hashCode(), title, ctx.getString(R.string.notif_stock_body, left))
        c.hooks.onPush(PushType.STOCK, title, ctx.getString(R.string.push_stock_body, left), med.id)

    }
}
