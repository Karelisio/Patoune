package com.karelisio.patoune.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.patoune.container
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.Stock
import java.util.concurrent.TimeUnit

/** Tâche quotidienne : renouvellement d'ordonnance, rappels de soins, pollens. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val today = Dates.today()
        for (med in c.db.medications().active()) {
            val days = Stock.daysLeft(med.stockRemaining, med.unitsPerDose, ReminderPlanner.parseTimes(med.times).size)
            if (Stock.renewalDue(Dates.parse(med.renewalDate), today, days)) {
                Notif.simple(applicationContext, Notif.CH_STOCK, ("renew" + med.id).hashCode(), "Ordonnance : ${med.name}",
                    days?.let { "Environ $it jour(s) de traitement restant(s). Pensez à renouveler l'ordonnance." } ?: "Date de renouvellement proche.")
            }
        }
        for (ci in c.db.care().withDue()) {
            val due = Dates.parse(ci.nextDue) ?: continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            if (days in 0..7 && (days == 7L || days <= 1L)) {
                val animal = c.db.animals().byId(ci.animalId)?.name ?: ""
                Notif.simple(applicationContext, Notif.CH_STOCK, ("care" + ci.id).hashCode(), "Rappel ${ci.kind.label.lowercase()} : $animal",
                    "${ci.name} prévu le ${Dates.fmtDay(ci.nextDue)}.")
            }
        }
        for (v in c.db.visits().allRows().filter { !it.deleted && it.nextVisit != null }) {
            val due = Dates.parse(v.nextVisit) ?: continue
            if (java.time.temporal.ChronoUnit.DAYS.between(today, due) == 1L) {
                Notif.simple(applicationContext, Notif.CH_STOCK, ("visit" + v.id).hashCode(), "Visite véto demain", v.reason.ifBlank { "Prochaine visite" })
            }
        }
        c.hooks.onDaily()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
