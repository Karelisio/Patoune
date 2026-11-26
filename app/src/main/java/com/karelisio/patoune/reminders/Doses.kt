package com.karelisio.patoune.reminders

import com.karelisio.patoune.ui.unitQty
import com.karelisio.patoune.R
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.Backdate
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.HandledDose
import com.karelisio.patoune.domain.LateCheck
import com.karelisio.patoune.domain.Schedule
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
        val window = DoseGuard.currentWindow(Schedule.of(med, Dates.zone()), now, med.asNeeded)
        val logs = c.db.doses().since(med.id, window.first).filter { it.status == DoseStatus.GIVEN }
        return DoseGuard.alreadyGiven(logs.map { TakenDose(it.takenAt, it.createdBy) }, window)
    }

    /** Vérification de retard : aucune prise donnée ou sautée ne couvre l'horaire [scheduledAt]. */
    suspend fun isLate(med: Medication, scheduledAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val schedule = Schedule.of(med, Dates.zone())
        val from = DoseGuard.currentWindow(schedule, scheduledAt).first
        val handled = c.db.doses().since(med.id, minOf(from, scheduledAt)).map { HandledDose(it.takenAt, it.scheduledAt) }
        return LateCheck.shouldAlert(schedule, med.lateMinutes, active = !med.deleted && !med.asNeeded, scheduledAt, handled, now)
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

    private val ledger by lazy { DoseLedger(c.store, c.db) }

    /**
     * Prise saisie après coup (date choisie) : garde-fou double dose sur la fenêtre de [takenAt],
     * stock déduit si [adjustStock]. Push « Prise donnée » et annulation des rappels seulement si la prise date de moins d'1 h.
     */
    suspend fun record(med: Medication, takenAt: Long, status: DoseStatus, adjustStock: Boolean, force: Boolean = false, now: Long = System.currentTimeMillis()): DoseResult {
        val r = ledger.record(med, takenAt, status, adjustStock, force)
        if (r !is DoseResult.Saved) return r
        if (status == DoseStatus.GIVEN && adjustStock) c.db.medications().byId(med.id)?.let { checkStock(it) }
        if (Backdate.isRecent(takenAt, now)) {
            Notif.cancel(c.context, ReminderScheduler.notifId(med.id))
            c.reminders.cancelLate(med.id)
            Notif.cancel(c.context, ReminderScheduler.latePushNotifId(med.id))
            c.hooks.onDose(med, r.log)
        } else c.hooks.onDataChanged()
        return r
    }

    /** Correction d'une prise (date, statut) ; le stock suit un changement de statut si [adjustStock]. Jamais de push. */
    suspend fun edit(old: DoseLog, newTakenAt: Long, newStatus: DoseStatus, adjustStock: Boolean = true, force: Boolean = false): DoseResult {
        val r = ledger.edit(old, newTakenAt, newStatus, adjustStock, force)
        if (r !is DoseResult.Saved) return r
        if (old.status != newStatus && newStatus == DoseStatus.GIVEN && adjustStock) c.db.medications().byId(old.medicationId)?.let { checkStock(it) }
        c.hooks.onDataChanged()
        return r
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

/**
 * Saisie et correction des prises, sans effet de bord Android (testable avec une base en mémoire).
 * Toutes les écritures passent par le Store.
 */
class DoseLedger(private val store: Store, private val db: PatouneDb) {

    /** Fenêtre du garde-fou contenant l'instant [at] : seul appel à DoseGuard, à adapter si sa signature change. */
    private fun window(med: Medication, at: Long): LongRange =
        DoseGuard.currentWindow(Schedule.of(med, Dates.zone()), at, med.asNeeded)

    /**
     * Prise déjà donnée dans la même fenêtre que [at] : avant [at] (dans sa fenêtre) ou après [at]
     * (quand [at] tombe dans la fenêtre de cette prise plus récente). [exceptId] : la prise en cours de correction.
     */
    suspend fun conflict(med: Medication, at: Long, exceptId: String? = null): TakenDose? {
        val w = window(med, at)
        val logs = db.doses().since(med.id, minOf(w.first, at - SCAN_MS))
            .filter { it.status == DoseStatus.GIVEN && it.id != exceptId && it.takenAt <= at + SCAN_MS }
        return logs.filter { it.takenAt in w || at in window(med, it.takenAt) }
            .maxByOrNull { it.takenAt }?.let { TakenDose(it.takenAt, it.createdBy) }
    }

    suspend fun record(med: Medication, takenAt: Long, status: DoseStatus, adjustStock: Boolean, force: Boolean = false): DoseResult {
        if (status == DoseStatus.GIVEN && !force) conflict(med, takenAt)?.let { return DoseResult.AlreadyGiven(it.takenAt, it.by) }
        val log = store.save(DoseLog(medicationId = med.id, animalId = med.animalId, takenAt = takenAt, status = status))
        if (status == DoseStatus.GIVEN && adjustStock) {
            // Relu en base : le stock a pu bouger depuis l'ouverture de l'écran.
            val cur = db.medications().byId(med.id) ?: med
            if (cur.stockRemaining != null) store.save(cur.copy(stockRemaining = Stock.decrement(cur.stockRemaining, cur.unitsPerDose)))
        }
        return DoseResult.Saved(log)
    }

    /** Donné → sauté restitue une unité de prise, sauté → donné en déduit une (si [adjustStock] et stock suivi). */
    suspend fun edit(old: DoseLog, newTakenAt: Long, newStatus: DoseStatus, adjustStock: Boolean = true, force: Boolean = false): DoseResult {
        val med = db.medications().byId(old.medicationId)
        val recheck = old.status != DoseStatus.GIVEN || newTakenAt != old.takenAt
        if (med != null && newStatus == DoseStatus.GIVEN && recheck && !force) {
            conflict(med, newTakenAt, exceptId = old.id)?.let { return DoseResult.AlreadyGiven(it.takenAt, it.by) }
        }
        // L'auteur (createdBy) reste celui de la saisie d'origine : le Store le conserve.
        val log = store.save(old.copy(takenAt = newTakenAt, status = newStatus))
        if (med != null && med.stockRemaining != null && adjustStock && old.status != newStatus) {
            val stock = if (newStatus == DoseStatus.GIVEN) Stock.decrement(med.stockRemaining, med.unitsPerDose) else Stock.increment(med.stockRemaining, med.unitsPerDose)
            store.save(med.copy(stockRemaining = stock))
        }
        return DoseResult.Saved(log)
    }

    private companion object {
        /** Marge de recherche autour de la date choisie (les fenêtres durent au plus une journée). */
        const val SCAN_MS = 2 * 86_400_000L
    }
}
