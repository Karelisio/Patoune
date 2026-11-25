package com.karelisio.patoune.reminders

import com.karelisio.patoune.R
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
                val ctx = applicationContext
                Notif.simple(ctx, Notif.CH_STOCK, ("renew" + med.id).hashCode(), ctx.getString(R.string.notif_renew_title, med.name),
                    days?.let { ctx.resources.getQuantityString(R.plurals.notif_renew_days, it, it) } ?: ctx.getString(R.string.notif_renew_soon))
            }
        }
        for (ci in c.db.care().withDue()) {
            val due = Dates.parse(ci.nextDue) ?: continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            if (days in 0..7 && (days == 7L || days <= 1L)) {
                val animal = c.db.animals().byId(ci.animalId)?.name ?: ""
                val ctx = applicationContext
                Notif.simple(ctx, Notif.CH_STOCK, ("care" + ci.id).hashCode(), ctx.getString(R.string.notif_care_title, ctx.getString(ci.kind.labelRes), animal),
                    ctx.getString(R.string.notif_care_body, ci.name, Dates.fmtDay(ci.nextDue)))
            }
        }
        for (v in c.db.visits().allRows().filter { !it.deleted && it.nextVisit != null }) {
            val due = Dates.parse(v.nextVisit) ?: continue
            if (java.time.temporal.ChronoUnit.DAYS.between(today, due) == 1L) {
                Notif.simple(applicationContext, Notif.CH_STOCK, ("visit" + v.id).hashCode(), applicationContext.getString(R.string.notif_visit_tomorrow),
                    v.reason.ifBlank { applicationContext.getString(R.string.health_next_visit) })

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
