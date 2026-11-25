package com.karelisio.patoune.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.patoune.container
import java.util.concurrent.TimeUnit

/** File d'envoi : la sync se fait dès que le réseau est disponible, avec reprise exponentielle. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        applicationContext.container.sync.sync()
        Result.success()
    } catch (e: ApiException) {
        if (e.status == 401) {
            // Retiré du foyer (ou foyer supprimé) : retour en solo, la copie locale est conservée.
            val c = applicationContext.container
            c.settings.leaveHousehold()
            com.karelisio.patoune.reminders.Notif.simple(applicationContext, com.karelisio.patoune.reminders.Notif.CH_HOUSEHOLD, 401,
                "Foyer quitté", "Vous ne faites plus partie du foyer. Patoune repasse en solo avec toutes vos données.")
            Result.failure()
        } else Result.retry()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun now(context: Context, delaySec: Long = 2) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).setInitialDelay(delaySec, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS).build(),
            )
        }

        fun periodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("sync-now")
            WorkManager.getInstance(context).cancelUniqueWork("sync-periodic")
        }
    }
}
