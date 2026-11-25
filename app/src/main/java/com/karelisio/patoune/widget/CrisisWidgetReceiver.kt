package com.karelisio.patoune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.karelisio.patoune.MainActivity
import com.karelisio.patoune.R

/**
 * Widget « Crise en cours » en RemoteViews classiques (sans Glance) : la vue de départ est déjà le
 * vrai bouton et le lanceur conserve son action, même si le système tue l'appli ou après une mise à jour.
 * Couleurs dynamiques Material You via les couleurs système (Android 12+), palette de l'appli sinon.
 */
class CrisisWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(context))
    }

    companion object {
        private fun open(context: Context): PendingIntent {
            val i = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_CRISIS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 4242, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun layout(context: Context, res: Int): RemoteViews = RemoteViews(context.packageName, res).apply {
            setOnClickPendingIntent(R.id.crisis_root, open(context))
        }

        /** Vue adaptée à la taille : icône seule en 1×1, icône + texte quand il y a la place. */
        fun views(context: Context): RemoteViews =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RemoteViews(mapOf(
                SizeF(40f, 40f) to layout(context, R.layout.widget_crisis_small),
                SizeF(130f, 40f) to layout(context, R.layout.widget_crisis),
            ))
            else layout(context, R.layout.widget_crisis)

        /** Redessine toutes les instances (démarrage de l'appli, redémarrage, mise à jour, tâche quotidienne). */
        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, CrisisWidgetReceiver::class.java))
            if (ids.isNotEmpty()) m.updateAppWidget(ids, views(context))
        }
    }
}
