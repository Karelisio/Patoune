package com.karelisio.patoune.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.karelisio.patoune.container
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Planifie les rappels exacts (setExactAndAllowWhileIdle) pour chaque traitement actif. */
class ReminderScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        val meds = context.container.db.medications().active()
        meds.forEach { schedule(it) }
    }

    fun schedule(med: Medication, after: Long = System.currentTimeMillis()) {
        cancel(med.id)
        if (med.deleted || med.asNeeded) return
        val at = ReminderPlanner.next(ReminderPlanner.parseTimes(med.times), after, Dates.zone(), Dates.parse(med.startDate), Dates.parse(med.endDate)) ?: return
        setAlarm(med.id, at, at)
    }

    fun snooze(medId: String, scheduledAt: Long) {
        setAlarm(medId, ReminderPlanner.snooze(System.currentTimeMillis()), scheduledAt)
    }

    private fun setAlarm(medId: String, triggerAt: Long, scheduledAt: Long) {
        val pi = pending(medId, scheduledAt)
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(medId: String) = am.cancel(pending(medId, 0))

    private fun pending(medId: String, scheduledAt: Long): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_MED, medId).putExtra(EXTRA_SCHEDULED, scheduledAt)
            .setData(android.net.Uri.parse("patoune://reminder/$medId"))
        return PendingIntent.getBroadcast(context, medId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_FIRE = "com.karelisio.patoune.REMINDER"
        const val ACTION_GIVEN = "com.karelisio.patoune.GIVEN"
        const val ACTION_GIVEN_CONFIRMED = "com.karelisio.patoune.GIVEN_CONFIRMED"
        const val ACTION_SNOOZE = "com.karelisio.patoune.SNOOZE"
        const val ACTION_SKIP = "com.karelisio.patoune.SKIP"
        const val ACTION_DISMISS = "com.karelisio.patoune.DISMISS"
        const val EXTRA_MED = "med"
        const val EXTRA_SCHEDULED = "scheduled"
        fun notifId(medId: String) = medId.hashCode()
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Déclenchement d'un rappel et actions de la notification (Donné / Reporter / Sauté). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(ReminderScheduler.EXTRA_MED) ?: return
        val scheduled = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED, System.currentTimeMillis())
        val pending = goAsync()
        receiverScope.launch {
            try { handle(context, intent.action, medId, scheduled) } finally { pending.finish() }
        }
    }

    private suspend fun handle(context: Context, action: String?, medId: String, scheduled: Long) {
        val c = context.container
        val doses = c.doses
        val med = c.db.medications().byId(medId) ?: return
        when (action) {
            ReminderScheduler.ACTION_FIRE -> {
                // Déjà donné dans la fenêtre (par moi ou un autre membre) : pas de rappel.
                if (doses.alreadyGiven(med) == null) showReminder(context, med, scheduled)
                c.reminders.schedule(med, after = maxOf(scheduled, System.currentTimeMillis()))
            }
            ReminderScheduler.ACTION_GIVEN, ReminderScheduler.ACTION_GIVEN_CONFIRMED -> {
                val res = doses.give(med, DoseStatus.GIVEN, scheduled, force = action == ReminderScheduler.ACTION_GIVEN_CONFIRMED)
                if (res is DoseResult.AlreadyGiven) showDoubleDose(context, med, scheduled, res)
                else Notif.cancel(context, ReminderScheduler.notifId(medId))
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                Notif.cancel(context, ReminderScheduler.notifId(medId))
                c.reminders.snooze(medId, scheduled)
            }
            ReminderScheduler.ACTION_SKIP -> {
                doses.give(med, DoseStatus.SKIPPED, scheduled, force = true)
                Notif.cancel(context, ReminderScheduler.notifId(medId))
            }
            else -> Notif.cancel(context, ReminderScheduler.notifId(medId))
        }
    }

    private suspend fun showReminder(context: Context, med: Medication, scheduled: Long) {
        val animal = context.container.db.animals().byId(med.animalId)?.name ?: ""
        val n = Notif.builder(context, Notif.CH_REMINDERS, "${med.name} pour $animal", "${med.dose.ifBlank { med.type.label }} · prévu à ${Dates.fmtTime(scheduled)}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Donné", action(context, ReminderScheduler.ACTION_GIVEN, med.id, scheduled))
            .addAction(0, "Reporter 15 min", action(context, ReminderScheduler.ACTION_SNOOZE, med.id, scheduled))
            .addAction(0, "Sauté", action(context, ReminderScheduler.ACTION_SKIP, med.id, scheduled))
        Notif.post(context, ReminderScheduler.notifId(med.id), n)
    }

    private fun showDoubleDose(context: Context, med: Medication, scheduled: Long, r: DoseResult.AlreadyGiven) {
        val who = r.by.takeIf { it.isNotBlank() }?.let { " par $it" } ?: ""
        val n = Notif.builder(context, Notif.CH_REMINDERS, "Déjà donné ?", "${med.name} a déjà été marqué à ${Dates.fmtTime(r.at)}$who. Confirmer une nouvelle prise ?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Confirmer quand même", action(context, ReminderScheduler.ACTION_GIVEN_CONFIRMED, med.id, scheduled))
            .addAction(0, "Annuler", action(context, ReminderScheduler.ACTION_DISMISS, med.id, scheduled))
        Notif.post(context, ReminderScheduler.notifId(med.id), n)
    }

    private fun action(context: Context, action: String, medId: String, scheduled: Long): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(action)
            .putExtra(ReminderScheduler.EXTRA_MED, medId).putExtra(ReminderScheduler.EXTRA_SCHEDULED, scheduled)
        return PendingIntent.getBroadcast(context, (action + medId).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Replanifie après un redémarrage, un changement d'heure ou de fuseau, une mise à jour. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                context.container.reminders.rescheduleAll()
                DailyWorker.schedule(context)
            } finally { pending.finish() }
        }
    }
}
