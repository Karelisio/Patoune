package com.karelisio.patoune.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.BuildConfig
import com.karelisio.patoune.container
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.sync.Invitation
import com.karelisio.patoune.sync.Qr
import com.karelisio.patoune.sync.QrScanner
import com.karelisio.patoune.sync.RemoteMember
import com.karelisio.patoune.sync.SyncWorker
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import kotlinx.coroutines.launch

/** Mode partagé : créer/rejoindre un foyer, inviter, membres, notifications, quota, quitter. */
@Composable
fun HouseholdScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember(s.memberName) { mutableStateOf(s.memberName) }
    var server by remember(s.serverUrl) { mutableStateOf(s.serverUrl.ifBlank { BuildConfig.WORKER_URL }) }
    var scanning by remember { mutableStateOf(false) }
    var codeEntry by remember { mutableStateOf(false) }
    var invitation by remember { mutableStateOf<Invitation?>(null) }
    var members by remember { mutableStateOf<List<Pair<RemoteMember, String>>>(emptyList()) }
    var confirmRemove by remember { mutableStateOf<Pair<RemoteMember, String>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val pending by remember { c.db.sync().observePendingCount() }.collectAsState(0)

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            runCatching { block() }.onFailure { error = "Échec : ${it.message ?: it::class.simpleName}" }
            busy = false
        }
    }
    LaunchedEffect(s.shared, s.lastSyncAt) { if (s.shared) runCatching { members = c.household.members() } }

    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scanning = true }
    fun joinWith(text: String) {
        val inv = Invitation.parse(text, server)
        if (inv == null) { error = "Code d'invitation illisible."; return }
        run { c.household.join(inv, name.trim()) }
    }

    Scaffold(topBar = { BackTopBar(if (s.shared) "Foyer partagé" else "Partager avec mes proches") }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!s.shared) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeBadge(Icons.Rounded.Group, PawShapes.clover, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("  Vous êtes en mode solo", style = MaterialTheme.typography.titleLarge)
                        }
                        Text("Un foyer réunit 2 à 6 personnes (conjoint, famille, pet-sitter) autour des mêmes animaux : on voit qui a donné quoi, les doubles doses sont évitées. " +
                            "Tout est chiffré sur le téléphone ; la clé du foyer passe uniquement par le QR code.", style = MaterialTheme.typography.bodyMedium)
                        Field(name, { name = it }, "Votre prénom (visible des membres)")
                    }
                }
                item {
                    SectionCard {
                        Text("Créer un foyer", style = MaterialTheme.typography.titleMedium)
                        Text("Vos données actuelles seront chiffrées et envoyées dans le nouveau foyer.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { run { c.household.create(name.trim(), server.trim()) } }, enabled = !busy && name.isNotBlank() && server.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Créer le foyer") }
                    }
                }
                item {
                    SectionCard {
                        Text("Rejoindre un foyer", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { cameraPerm.launch(Manifest.permission.CAMERA) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.QrCodeScanner, null); Text("  Scanner")
                            }
                            OutlinedButton(onClick = { codeEntry = true }, enabled = !busy && name.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Saisir le code") }
                        }
                    }
                }
                item { Field(server, { server = it }, "Serveur (avancé)") }
            } else {
                item {
                    SectionCard {
                        Text("Vous : ${s.memberName}" + if (s.isCreator) " · créateur du foyer" else "", style = MaterialTheme.typography.titleMedium)
                        Text(if (s.lastSyncAt > 0) "Dernière synchronisation : ${Dates.fmtDayTime(s.lastSyncAt)}" else "Pas encore synchronisé", style = MaterialTheme.typography.bodyMedium)
                        if (pending > 0) Text("$pending modification(s) en attente d'envoi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { run { c.sync.sync() } }, enabled = !busy) { Icon(Icons.Rounded.Sync, null); Text("  Synchroniser") }
                            if (members.size < 6) Button(onClick = { run { invitation = c.household.invite() } }, enabled = !busy) { Icon(Icons.Rounded.QrCode2, null); Text("  Inviter") }
                        }
                    }
                }
                item { Text("Membres (${members.size}/6)", style = MaterialTheme.typography.titleMedium) }
                items(members, key = { it.first.id }) { (m, n) ->
                    ListItem(
                        headlineContent = { Text(n + if (m.id == s.memberId) " (vous)" else "") },
                        supportingContent = { Text("Depuis le ${Dates.fmtDay(m.joinedAt)}") },
                        leadingContent = { if (m.isCreator) Icon(Icons.Rounded.Star, "Créateur") },
                        trailingContent = {
                            if (s.isCreator && m.id != s.memberId) IconButton(onClick = { confirmRemove = m to n }) { Icon(Icons.Rounded.PersonRemove, "Retirer") }
                        },
                    )
                }
                item {
                    SectionCard {
                        Text("Notifications du foyer", style = MaterialTheme.typography.titleMedium)
                        PushType.entries.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(t in s.pushEnabled, { on -> scope.launch { c.settings.setPush(if (on) s.pushEnabled + t else s.pushEnabled - t) } })
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        Text("Stockage des médias", style = MaterialTheme.typography.titleMedium)
                        val max = s.quotaMax.takeIf { it > 0 }
                        Text(if (max != null) "${mb(s.quotaUsed)} utilisés sur ${mb(max)} (50 Mo max par média)" else "Quota inconnu (synchronisez)")
                        if (max != null) LinearProgressIndicator(progress = { (s.quotaUsed.toFloat() / max).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Quitter le foyer (garder une copie locale)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (busy) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }

    if (scanning) ModalBottomSheet(onDismissRequest = { scanning = false }) {
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Scannez le QR code affiché par un membre", style = MaterialTheme.typography.titleMedium)
            QrScanner(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)) { text -> scanning = false; joinWith(text) }
        }
    }
    if (codeEntry) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { codeEntry = false },
            title = { Text("Code d'invitation") },
            text = { Field(code, { code = it }, "XXXX-XXXX-…", minLines = 3) },
            confirmButton = { TextButton(onClick = { codeEntry = false; joinWith(code) }) { Text("Rejoindre") } },
            dismissButton = { TextButton(onClick = { codeEntry = false }) { Text("Annuler") } },
        )
    }
    invitation?.let { inv ->
        ModalBottomSheet(onDismissRequest = { invitation = null; SyncWorker.now(ctx) }) {
            Column(Modifier.padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invitation (valable 15 min, usage unique)", style = MaterialTheme.typography.titleMedium)
                val bmp = remember(inv) { Qr.encode(inv.toQr()) }
                Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(PawShapes.cookie).background(Color.White).padding(28.dp)) {
                    Image(bmp.asImageBitmap(), "QR code d'invitation", Modifier.fillMaxWidth().aspectRatio(1f))
                }
                Text("Code de secours", style = MaterialTheme.typography.labelLarge)
                Text(inv.shortCode(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Text("Ce code contient la clé du foyer : ne le partagez qu'avec la personne invitée.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    confirmRemove?.let { (m, n) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Retirer $n ?") },
            text = { Text("$n n'aura plus accès au foyer. Une nouvelle clé est générée et transmise aux autres membres.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; run { c.household.remove(m.id); members = c.household.members() } }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Annuler") } },
        )
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Quitter le foyer ?") },
        text = { Text("Vous repassez en mode solo avec une copie complète des données et des médias sur ce téléphone.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; run { c.household.leave() } }) { Text("Quitter") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Annuler") } },
    )
}

private fun mb(b: Long) = if (b >= 1 shl 30) "%.1f Go".format(b / 1073741824.0) else "%.0f Mo".format(b / 1048576.0)
