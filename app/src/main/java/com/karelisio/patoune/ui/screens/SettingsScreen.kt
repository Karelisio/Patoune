package com.karelisio.patoune.ui.screens

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
fun BackTopBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
        actions = { actions() },
    )
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val nav = LocalNav.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val st = settings ?: return
    Scaffold(topBar = { BackTopBar("Réglages") }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SettingsHeader("Mode") }
            item {
                SettingsItem(
                    if (st.shared) "Foyer partagé" else "Solo",
                    if (st.shared) "Membres, invitations, notifications, quota" else "Tout reste sur ce téléphone. Touchez pour partager avec vos proches.",
                    if (st.shared) Icons.Rounded.Group else Icons.Rounded.Person,
                ) { nav.navigate(Routes.Household) }
            }
            item { SettingsHeader("Suivi") }
            item { SettingsItem("Rapport pour le véto", "PDF sur une période choisie", Icons.Rounded.PictureAsPdf) { nav.navigate(Routes.Report) } }
            item { SettingsItem("Pollens et qualité de l'air", st.city ?: "Choisir une ville", Icons.Rounded.Grass) { nav.navigate(Routes.Report) } }
            item { SettingsHeader("Données") }
            item { SettingsItem("Sauvegarde chiffrée", "Exporter ou importer (.zip avec les médias)", Icons.Rounded.Backup) { nav.navigate(Routes.Backup) } }
            item { SettingsItem("Autorisations", "Notifications, alarmes, caméra, batterie…", Icons.Rounded.Security) { nav.navigate(Routes.Permissions) } }
            item { SettingsHeader("À propos") }
            item {
                SettingsItem("Rechercher une mise à jour", "Version ${BuildConfig.VERSION_NAME}", Icons.Rounded.SystemUpdate) { vm.checkUpdate(force = true) }
            }
            item { VetNote("Patoune aide au suivi et n'est pas un avis médical. Les seuils d'alerte sont ceux fixés avec votre véto.") }
        }
    }
}
