package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.VetNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.karelisio.patoune.BuildConfig
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel

@Composable
fun SettingsItem(title: String, subtitle: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.large).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
fun SettingsHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, start = 8.dp, bottom = 4.dp))
}

@Composable
fun BackTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title) },
        // [onBack] : écran qui doit agir avant de se fermer (ex. brouillon de crise abandonné).
        navigationIcon = { IconButton(onClick = { onBack?.invoke() ?: nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
        actions = { actions() },
    )
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val nav = LocalNav.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val st = settings ?: return
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title)) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SettingsHeader(stringResource(R.string.settings_mode)) }
            item {
                SettingsItem(
                    stringResource(if (st.shared) R.string.settings_shared else R.string.settings_solo),
                    stringResource(if (st.shared) R.string.settings_shared_sub else R.string.settings_solo_sub),
                    if (st.shared) Icons.Rounded.Group else Icons.Rounded.Person,
                ) { nav.navigate(Routes.Household) }
            }
            item { SettingsHeader(stringResource(R.string.settings_tracking)) }
            item { SettingsItem(stringResource(R.string.settings_animals), stringResource(R.string.settings_animals_sub), Icons.Rounded.Pets) { nav.navigate(Routes.Animals) } }
            item { SettingsItem(stringResource(R.string.report_title), stringResource(R.string.settings_report_sub), Icons.Rounded.PictureAsPdf) { nav.navigate(Routes.Report) } }
            item { SettingsItem(stringResource(R.string.air_title), st.city ?: stringResource(R.string.settings_air_sub), Icons.Rounded.Grass) { nav.navigate(Routes.Air) } }
            item { SettingsHeader(stringResource(R.string.settings_data)) }
            item { SettingsItem(stringResource(R.string.backup_title), stringResource(R.string.settings_backup_sub), Icons.Rounded.Backup) { nav.navigate(Routes.Backup) } }
            item { SettingsItem(stringResource(R.string.permissions_title), stringResource(R.string.settings_permissions_sub), Icons.Rounded.Security) { nav.navigate(Routes.Permissions) } }
            item { SettingsHeader(stringResource(R.string.settings_about)) }
            item {
                SettingsItem(stringResource(R.string.settings_update), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), Icons.Rounded.SystemUpdate) { vm.checkUpdate(force = true) }
            }
            item { VetNote(stringResource(R.string.settings_disclaimer)) }

        }
    }
}
