package com.karelisio.patoune.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karelisio.patoune.MainActivity
import com.karelisio.patoune.R

object Notif {
    const val CH_REMINDERS = "reminders"
    const val CH_ALERTS = "alerts"
    const val CH_STOCK = "stock"
    const val CH_HOUSEHOLD = "household"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_REMINDERS, "Rappels de traitement", NotificationManager.IMPORTANCE_HIGH).apply { description = "Médicaments à donner" },
            NotificationChannel(CH_ALERTS, "Alertes respiratoires", NotificationManager.IMPORTANCE_HIGH).apply { description = "Fréquence au-dessus du seuil fixé avec le véto" },
            NotificationChannel(CH_STOCK, "Stock et rappels de soins", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_HOUSEHOLD, "Foyer partagé", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Prises et crises enregistrées par les autres membres" },
        ))
    }

    fun canPost(context: Context): Boolean =
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openApp(context: Context, action: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        action?.let { i.putExtra(MainActivity.EXTRA_ACTION, it) }
        return PendingIntent.getActivity(context, action.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun builder(context: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_paw)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)

    fun post(context: Context, id: Int, n: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try { NotificationManagerCompat.from(context).notify(id, n.build()) } catch (_: SecurityException) {}
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun simple(context: Context, channel: String, id: Int, title: String, text: String) = post(context, id, builder(context, channel, title, text))
}
