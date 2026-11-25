package com.karelisio.patoune.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.container
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.theme.PatouneTheme
import kotlinx.coroutines.launch

/**
 * Rappel « réveil » en plein écran, affiché même téléphone verrouillé.
 * Les boutons reprennent la logique des actions de notification (garde-fou double dose compris).
 */
class ReminderAlarmActivity : ComponentActivity() {
    private val request = mutableStateOf<Request?>(null)

    private data class Request(val medId: String, val scheduled: Long, val late: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        if (!read(intent)) { finish(); return }
        setContent {
            PatouneTheme {
                request.value?.let { r -> AlarmContent(r.medId, r.scheduled, r.late, onDone = { finish() }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent?): Boolean {
        val medId = intent?.getStringExtra(ReminderScheduler.EXTRA_MED) ?: return false
        request.value = Request(
            medId,
            intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULED, System.currentTimeMillis()),
            intent.getBooleanExtra(ReminderScheduler.EXTRA_LATE, false),
        )
        return true
    }

    companion object {
        fun pendingIntent(context: Context, medId: String, scheduled: Long, late: Boolean): PendingIntent {
            val i = Intent(context, ReminderAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(ReminderScheduler.EXTRA_MED, medId)
                .putExtra(ReminderScheduler.EXTRA_SCHEDULED, scheduled)
                .putExtra(ReminderScheduler.EXTRA_LATE, late)
            return PendingIntent.getActivity(context, ("alarm$medId").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

@Composable
private fun AlarmContent(medId: String, scheduled: Long, late: Boolean, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var med by remember(medId) { mutableStateOf<Medication?>(null) }
    var animal by remember(medId) { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var conflict by remember(medId) { mutableStateOf<DoseResult.AlreadyGiven?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(medId) {
        val m = c.db.medications().byId(medId)
        if (m == null || m.deleted) { Notif.cancel(context, ReminderScheduler.notifId(medId)); onDone(); return@LaunchedEffect }
        med = m
        animal = c.db.animals().byId(m.animalId)?.name.orEmpty()
        shared = c.settings.current().shared
    }
    val m = med

    fun act(block: suspend (Medication) -> Unit) {
        val target = med ?: return
        if (busy) return
        busy = true
        scope.launch { try { block(target) } finally { busy = false } }
    }

    fun give(force: Boolean) = act { target ->
        when (val r = c.doses.give(target, DoseStatus.GIVEN, scheduled, force = force)) {
            is DoseResult.AlreadyGiven -> conflict = r
            is DoseResult.Saved -> onDone()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            if (m == null) return@Box
            Card(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = if (late) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShapeBadge(Icons.Rounded.AlarmOn, PawShapes.cookie, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 88.dp)
                    Text(if (late) "En retard" else "C'est l'heure", style = MaterialTheme.typography.titleLarge)
                    Text(m.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    if (animal.isNotBlank()) Text("pour $animal", style = MaterialTheme.typography.titleMedium)
                    Text("${m.dose.ifBlank { m.type.label }} · prévu à ${Dates.fmtTime(scheduled)}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

                    val r = conflict
                    if (r != null) {
                        val who = if (shared && r.by.isNotBlank()) " par ${r.by}" else ""
                        Text(
                            "Déjà donné à ${Dates.fmtTime(r.at)}$who (${Dates.fmtDayTime(r.at)}). Enregistrer une nouvelle prise ?",
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { conflict = null; give(force = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Oui, nouvelle prise") }
                        OutlinedButton(onClick = { Notif.cancel(context, ReminderScheduler.notifId(m.id)); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Non") }
                    } else {
                        Button(onClick = { give(force = false) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Icon(Icons.Rounded.Check, null, Modifier.padding(end = 8.dp))
                            Text("Donné", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = { act { target -> Notif.cancel(context, ReminderScheduler.notifId(target.id)); c.reminders.snooze(target, scheduled); onDone() } },
                            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Rounded.Snooze, null, Modifier.padding(end = 8.dp))
                            Text("Reporter 15 min")
                        }
                        TextButton(
                            onClick = { act { target -> c.doses.give(target, DoseStatus.SKIPPED, scheduled, force = true); onDone() } },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sauté") }
                    }
                }
            }
        }
    }
}
