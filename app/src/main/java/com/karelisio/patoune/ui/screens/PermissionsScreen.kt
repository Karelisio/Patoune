package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.ui.components.AppPermission
import com.karelisio.patoune.ui.components.Perms
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.rememberPermissionRequester
import com.karelisio.patoune.ui.components.rememberResumeTick

/** État des autorisations, revérifié à chaque retour des réglages système. */
@Composable
fun PermissionsScreen() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val request = rememberPermissionRequester { refresh++ }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.permissions_title)) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AppPermission.entries) { p ->
                val ok = remember(tick, refresh) { Perms.granted(ctx, p) }
                SectionCard {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(p.icon, null)
                        Text(stringResource(p.titleRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        if (ok) Icon(Icons.Rounded.CheckCircle, stringResource(R.string.perm_granted), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(stringResource(p.whyRes), style = MaterialTheme.typography.bodyMedium)
                    if (!ok) FilledTonalButton(onClick = { request(p) }) { Text(stringResource(R.string.perm_allow)) }
                    else androidx.compose.material3.TextButton(onClick = { Perms.settingsIntent(ctx, p)?.let { runCatching { ctx.startActivity(it) } } }) { Text(stringResource(R.string.perm_open_settings)) }

                }
            }
        }
    }
}
