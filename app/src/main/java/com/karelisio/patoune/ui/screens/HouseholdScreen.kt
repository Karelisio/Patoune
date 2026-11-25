package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.platform.LocalResources
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
    val res = LocalResources.current
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
            runCatching { block() }.onFailure { error = res.getString(R.string.error_failed, it.message ?: it::class.simpleName.orEmpty()) }
            busy = false
        }
    }
    LaunchedEffect(s.shared, s.lastSyncAt) { if (s.shared) runCatching { members = c.household.members() } }

    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scanning = true }
    fun joinWith(text: String) {
        val inv = Invitation.parse(text, server)
        if (inv == null) { error = res.getString(R.string.household_bad_code); return }
        run { c.household.join(inv, name.trim()) }
    }

    Scaffold(topBar = { BackTopBar(stringResource(if (s.shared) R.string.settings_shared else R.string.household_share_title)) }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!s.shared) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeBadge(Icons.Rounded.Group, PawShapes.clover, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(stringResource(R.string.household_solo_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                        Text(stringResource(R.string.household_solo_body), style = MaterialTheme.typography.bodyMedium)
                        Field(name, { name = it }, stringResource(R.string.household_your_name))
                    }
                }
                item {
                    SectionCard {
                        Text(stringResource(R.string.household_create_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.household_create_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { run { c.household.create(name.trim(), server.trim()) } }, enabled = !busy && name.isNotBlank() && server.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.household_create)) }
                    }
                }
                item {
                    SectionCard {
                        Text(stringResource(R.string.household_join_title), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { cameraPerm.launch(Manifest.permission.CAMERA) }, enabled = !busy && name.isNotBlank(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.QrCodeScanner, null); Text(stringResource(R.string.household_scan), Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = { codeEntry = true }, enabled = !busy && name.isNotBlank(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.household_enter_code)) }
                        }
                    }
                }
                item { Field(server, { server = it }, stringResource(R.string.household_server)) }
            } else {
                item {
                    SectionCard {
                        Text(stringResource(if (s.isCreator) R.string.household_you_creator else R.string.household_you, s.memberName), style = MaterialTheme.typography.titleMedium)
                        Text(if (s.lastSyncAt > 0) stringResource(R.string.household_last_sync, Dates.fmtDayTime(s.lastSyncAt)) else stringResource(R.string.household_never_synced), style = MaterialTheme.typography.bodyMedium)
                        if (pending > 0) Text(pluralStringResource(R.plurals.household_pending, pending, pending), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { run { c.sync.sync() } }, enabled = !busy) { Icon(Icons.Rounded.Sync, null); Text(stringResource(R.string.household_sync), Modifier.padding(start = 8.dp)) }
                            if (members.size < 6) Button(onClick = { run { invitation = c.household.invite() } }, enabled = !busy) { Icon(Icons.Rounded.QrCode2, null); Text(stringResource(R.string.household_invite), Modifier.padding(start = 8.dp)) }
                        }
                    }
                }
                item { Text(stringResource(R.string.household_members, members.size, 6), style = MaterialTheme.typography.titleMedium) }
                items(members, key = { it.first.id }) { (m, n) ->
                    ListItem(
                        headlineContent = { Text(if (m.id == s.memberId) stringResource(R.string.household_member_you, n) else n) },
                        supportingContent = { Text(stringResource(R.string.household_member_since, Dates.fmtDay(m.joinedAt))) },
                        leadingContent = { if (m.isCreator) Icon(Icons.Rounded.Star, stringResource(R.string.household_creator)) },
                        trailingContent = {
                            if (s.isCreator && m.id != s.memberId) IconButton(onClick = { confirmRemove = m to n }) { Icon(Icons.Rounded.PersonRemove, stringResource(R.string.household_remove_named, n)) }
                        },
                    )
                }
                item {
                    SectionCard {
                        Text(stringResource(R.string.household_notifications), style = MaterialTheme.typography.titleMedium)
                        PushType.entries.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(t.labelRes), style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(t.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(t in s.pushEnabled, { on -> scope.launch { c.settings.setPush(if (on) s.pushEnabled + t else s.pushEnabled - t) } })
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        Text(stringResource(R.string.household_storage), style = MaterialTheme.typography.titleMedium)
                        val max = s.quotaMax.takeIf { it > 0 }
                        Text(if (max != null) stringResource(R.string.household_quota, mb(ctx, s.quotaUsed), mb(ctx, max)) else stringResource(R.string.household_quota_unknown))
                        if (max != null) LinearProgressIndicator(progress = { (s.quotaUsed.toFloat() / max).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.household_leave_button), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (busy) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }

    if (scanning) ModalBottomSheet(onDismissRequest = { scanning = false }) {
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.household_scan_title), style = MaterialTheme.typography.titleMedium)
            QrScanner(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)) { text -> scanning = false; joinWith(text) }
        }
    }
    if (codeEntry) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { codeEntry = false },
            title = { Text(stringResource(R.string.household_code_title)) },
            text = { Field(code, { code = it }, stringResource(R.string.household_code_hint), minLines = 3) },
            confirmButton = { TextButton(onClick = { codeEntry = false; joinWith(code) }) { Text(stringResource(R.string.household_join)) } },
            dismissButton = { TextButton(onClick = { codeEntry = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    invitation?.let { inv ->
        ModalBottomSheet(onDismissRequest = { invitation = null; SyncWorker.now(ctx) }) {
            Column(Modifier.padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.household_invitation_title), style = MaterialTheme.typography.titleMedium)
                val bmp = remember(inv) { Qr.encode(inv.toQr()) }
                Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(PawShapes.cookie).background(Color.White).padding(28.dp)) {
                    Image(bmp.asImageBitmap(), stringResource(R.string.household_qr), Modifier.fillMaxWidth().aspectRatio(1f))
                }
                Text(stringResource(R.string.household_backup_code), style = MaterialTheme.typography.labelLarge)
                Text(inv.shortCode(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.household_backup_code_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    confirmRemove?.let { (m, n) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.household_remove_q, n)) },
            text = { Text(stringResource(R.string.household_remove_body, n)) },
            confirmButton = { TextButton(onClick = { confirmRemove = null; run { c.household.remove(m.id); members = c.household.members() } }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.household_leave_q)) },
        text = { Text(stringResource(R.string.household_leave_body)) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; run { c.household.leave() } }) { Text(stringResource(R.string.household_leave)) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Taille lisible (unités binaires, comme les limites du Worker) avec les unités traduites (Mo / MB…). */
private fun mb(ctx: android.content.Context, b: Long): String =
    if (b >= 1 shl 30) ctx.getString(R.string.size_gb, "%.1f".format(b / 1073741824.0))
    else ctx.getString(R.string.size_mb, "%.0f".format(b / 1048576.0))


