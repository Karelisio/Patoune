package com.karelisio.patoune.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.layout.Box
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.DpSize
import com.karelisio.patoune.MainActivity
import com.karelisio.patoune.R

/** Widget « Crise en cours » : un tap ouvre directement la caméra en vidéo et horodate la crise. */
class CrisisWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(57.dp, 57.dp), DpSize(130.dp, 57.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { PatouneGlanceTheme { Content() } }
    }

    @Composable
    private fun Content() {
        val wide = LocalSize.current.width >= 130.dp
        val colors = GlanceTheme.colors
        val open = actionStartActivity(
            Intent().setClassName("com.karelisio.patoune", MainActivity::class.java.name)
                .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_CRISIS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        Box(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(colors.widgetBackground)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (wide) FilledButton("Crise en cours", open, GlanceModifier.fillMaxSize(), icon = ImageProvider(R.drawable.ic_videocam),
                colors = ButtonDefaults.buttonColors(colors.errorContainer, colors.onErrorContainer))
            else CircleIconButton(ImageProvider(R.drawable.ic_videocam), "Crise en cours : filmer", open, GlanceModifier.fillMaxSize(),
                backgroundColor = colors.errorContainer, contentColor = colors.onErrorContainer)
        }
    }
}

class CrisisWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CrisisWidget()
}
