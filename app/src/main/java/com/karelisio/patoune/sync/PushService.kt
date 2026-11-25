package com.karelisio.patoune.sync

import com.karelisio.patoune.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.karelisio.patoune.container
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.reminders.Notif
import com.karelisio.patoune.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking

/** Réception des pushs chiffrés des autres membres. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val hid = message.data["h"] ?: return
        val data = message.data["d"] ?: return
        val c = applicationContext.container
        runBlocking {
            val p = c.household.openPush(hid, data) ?: return@runBlocking
            val type = runCatching { PushType.valueOf(p.t) }.getOrNull() ?: return@runBlocking
            // « Prise donnée » : le rappel s'annule ici aussi.
            if (type == PushType.DOSE && p.ref != null) {
                Notif.cancel(applicationContext, ReminderScheduler.notifId(p.ref))
                Notif.cancel(applicationContext, ReminderScheduler.latePushNotifId(p.ref))
            }
            if (type in c.settings.current().pushEnabled) {
                val channel = when (type) {
                    PushType.BREATH -> Notif.CH_ALERTS
                    PushType.LATE -> Notif.CH_REMINDERS
                    else -> Notif.CH_HOUSEHOLD
                }
                // Titre et texte rédigés par l'émetteur, dans sa langue ; seul l'ajout de l'auteur est local.
                val body = p.from.takeIf { it.isNotBlank() }?.let { applicationContext.getString(R.string.pair_dot, p.body, it) } ?: p.body
                Notif.simple(applicationContext, channel, (p.t + (p.ref ?: "")).hashCode(), p.title, body)

            }
        }
        SyncWorker.now(applicationContext, 0)
    }

    override fun onNewToken(token: String) {
        val c = applicationContext.container
        runBlocking { if (c.settings.current().shared) runCatching { c.api.setFcmToken(token) } }
    }
}
