package com.karelisio.patoune.reminders

import com.karelisio.patoune.R
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.karelisio.patoune.container
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.LateCheck
import com.karelisio.patoune.domain.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Planifie les rappels exacts pour chaque traitement actif : setExactAndAllowWhileIdle,
 * ou setAlarmClock en mode « réveil ». Chaque rappel déclenché arme aussi une vérification de retard.
 */
class ReminderScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    suspend fun rescheduleAll() {
        val db = context.container.db
        val meds = db.medications().active()
        val now = System.currentTimeMillis()
        meds.forEach { med ->
            schedule(med)
            // Après un redémarrage : ré-armer la vérif retard de l'horaire qui vient de passer.
            if (!med.asNeeded && med.lateMinutes > 0) {
                db.scheduleOf(med).next(now - med.lateMinutes * 60_000L)
                    ?.takeIf { it <= now }?.let { scheduleLate(med, it, now) }
            }
        }
    }

    /** Prochain rappel du traitement ; pour une fréquence espacée, compté depuis la dernière prise donnée. */
    suspend fun schedule(med: Medication, after: Long = System.currentTimeMillis()) {
        cancelReminder(med.id)
        if (med.deleted || med.asNeeded) { cancelLate(med.id); return }
        val at = context.container.db.scheduleOf(med).next(after) ?: return
        setAlarm(pending(med.id, at), at, med.alarmStyle)
    }

    fun snooze(med: Medication, scheduledAt: Long) {
        setAlarm(pending(med.id, scheduledAt), ReminderPlanner.snooze(System.currentTimeMillis()), med.alarmStyle)
    }

    /** Arme la vérification « prise en retard » pour l'horaire [scheduledAt] (rien si délai 0 ou déjà passé). */
    fun scheduleLate(med: Medication, scheduledAt: Long, now: Long = System.currentTimeMillis()) {
        val at = LateCheck.checkAt(scheduledAt, med.lateMinutes, now) ?: return cancelLate(med.id)
        setAlarm(latePending(med.id, scheduledAt), at, alarmStyle = false)
    }

    private fun setAlarm(pi: PendingIntent, triggerAt: Long, alarmStyle: Boolean) {
        when {
            !canExact() -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            // Alarme « réveil » : visible dans l'horloge système et jamais différée par le mode Doze.
            alarmStyle -> am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, Notif.openApp(context)), pi)
            else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** Annule le rappel et la vérification de retard (traitement supprimé). */
    fun cancel(medId: String) { cancelReminder(medId); cancelLate(medId) }

    private fun cancelReminder(medId: String) = am.cancel(pending(medId, 0))

    fun cancelLate(medId: String) = am.cancel(latePending(medId, 0))

    private fun pending(medId: String, scheduledAt: Long): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_MED, medId).putExtra(EXTRA_SCHEDULED, scheduledAt)
            .setData(android.net.Uri.parse("patoune://reminder/$medId"))
        return PendingIntent.getBroadcast(context, medId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** PendingIntent distinct du rappel (uri et requestCode propres) : l'un n'écrase pas l'autre. */
    private fun latePending(medId: String, scheduledAt: Long): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_LATE)
            .putExtra(EXTRA_MED, medId).putExtra(EXTRA_SCHEDULED, scheduledAt)
            .setData(android.net.Uri.parse("patoune://late/$medId"))
        return PendingIntent.getBroadcast(context, ("late$medId").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_FIRE = "com.karelisio.patoune.REMINDER"
        const val ACTION_LATE = "com.karelisio.patoune.LATE"
        const val ACTION_GIVEN = "com.karelisio.patoune.GIVEN"
        const val ACTION_GIVEN_CONFIRMED = "com.karelisio.patoune.GIVEN_CONFIRMED"
        const val ACTION_SNOOZE = "com.karelisio.patoune.SNOOZE"
        const val ACTION_SKIP = "com.karelisio.patoune.SKIP"
        const val ACTION_DISMISS = "com.karelisio.patoune.DISMISS"
        const val EXTRA_MED = "med"
        const val EXTRA_SCHEDULED = "scheduled"
        const val EXTRA_LATE = "late"
        fun notifId(medId: String) = medId.hashCode()
        /** Notification « En retard » reçue d'un autre membre (même identifiant que dans PushService). */
        fun latePushNotifId(medId: String) = (PushType.LATE.name + medId).hashCode()
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Déclenchement d'un rappel, vérification de retard et actions de la notification (Donné / Reporter / Sauté). */
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
                // Déjà donné dans la fenêtre (par moi ou un autre membre) : pas de rappel ni de relance.
                if (doses.alreadyGiven(med) == null) {
                    ReminderNotifications.show(context, med, scheduled, late = false)
                    c.reminders.scheduleLate(med, scheduled)
                }
                c.reminders.schedule(med, after = maxOf(scheduled, System.currentTimeMillis()))
            }
            ReminderScheduler.ACTION_LATE -> {
                if (doses.alreadyGiven(med) == null && doses.isLate(med, scheduled)) {
                    ReminderNotifications.show(context, med, scheduled, late = true)
                    val animal = c.db.animals().byId(med.animalId)?.name.orEmpty()
                    c.hooks.onPush(PushType.LATE, context.getString(R.string.push_late_title, med.name),
                        context.getString(R.string.push_late_body, animal, Dates.fmtTime(scheduled)), med.id)
                }
            }
            ReminderScheduler.ACTION_GIVEN, ReminderScheduler.ACTION_GIVEN_CONFIRMED -> {
                val res = doses.give(med, DoseStatus.GIVEN, scheduled, force = action == ReminderScheduler.ACTION_GIVEN_CONFIRMED)
                if (res is DoseResult.AlreadyGiven) showDoubleDose(context, med, scheduled, res)
                else Notif.cancel(context, ReminderScheduler.notifId(medId))
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                Notif.cancel(context, ReminderScheduler.notifId(medId))
                c.reminders.snooze(med, scheduled)
            }
            ReminderScheduler.ACTION_SKIP -> {
                doses.give(med, DoseStatus.SKIPPED, scheduled, force = true)
                Notif.cancel(context, ReminderScheduler.notifId(medId))
            }
            else -> Notif.cancel(context, ReminderScheduler.notifId(medId))
        }
    }

    private fun showDoubleDose(context: Context, med: Medication, scheduled: Long, r: DoseResult.AlreadyGiven) {
        val text = if (r.by.isNotBlank()) context.getString(R.string.notif_double_body_by, med.name, Dates.fmtTime(r.at), r.by)
        else context.getString(R.string.notif_double_body, med.name, Dates.fmtTime(r.at))
        val n = Notif.builder(context, Notif.CH_REMINDERS, context.getString(R.string.med_double_title), text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.notif_confirm_anyway), ReminderNotifications.action(context, ReminderScheduler.ACTION_GIVEN_CONFIRMED, med.id, scheduled))
            .addAction(0, context.getString(R.string.action_cancel), ReminderNotifications.action(context, ReminderScheduler.ACTION_DISMISS, med.id, scheduled))
        Notif.post(context, ReminderScheduler.notifId(med.id), n)
    }
}

/** Notification de rappel (normale ou « réveil » plein écran), aussi utilisée pour la relance « En retard ». */
object ReminderNotifications {
    suspend fun show(context: Context, med: Medication, scheduled: Long, late: Boolean) {
        val animal = context.container.db.animals().byId(med.animalId)?.name ?: ""
        val title = context.getString(if (late) R.string.reminder_title_late else R.string.reminder_title, med.name, animal)
        val dose = med.dose.ifBlank { context.getString(med.type.labelRes) }
        val text = context.getString(if (late) R.string.reminder_text_late else R.string.reminder_text, dose, Dates.fmtTime(scheduled))
        val n = Notif.builder(context, if (med.alarmStyle) Notif.CH_ALARM else Notif.CH_REMINDERS, title, text)
            .setCategory(if (med.alarmStyle) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (med.alarmStyle) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.dose_given), action(context, ReminderScheduler.ACTION_GIVEN, med.id, scheduled))
            .addAction(0, context.getString(R.string.reminder_snooze), action(context, ReminderScheduler.ACTION_SNOOZE, med.id, scheduled))
            .addAction(0, context.getString(R.string.dose_skipped), action(context, ReminderScheduler.ACTION_SKIP, med.id, scheduled))

        if (med.alarmStyle) {
            val full = ReminderAlarmActivity.pendingIntent(context, med.id, scheduled, late)
            n.setFullScreenIntent(full, true).setContentIntent(full).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
        Notif.post(context, ReminderScheduler.notifId(med.id), n, insistent = med.alarmStyle)
    }

    fun action(context: Context, action: String, medId: String, scheduled: Long): PendingIntent {
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
                context.container.widgets.refresh()
            } finally { pending.finish() }
        }
    }
}
