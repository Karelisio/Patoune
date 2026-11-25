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
