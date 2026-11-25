package com.karelisio.patoune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.AnimalOps
import com.karelisio.patoune.deleteAnimal
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalAvatar
import com.karelisio.patoune.ui.components.EmptyState
import kotlinx.coroutines.launch

/** Gestion des animaux : ordre d'affichage, modification, suppression. */
@Composable
fun AnimalsScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val animals by vm.animals.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Animal?>(null) }

    fun move(from: Int, to: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        scope.launch { AnimalOps.move(c.store, animals, from, to) }
    }

    Scaffold(
        topBar = { BackTopBar("Mes animaux") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.AnimalEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Animal") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (animals.isEmpty()) item { EmptyState(Icons.Rounded.Pets, "Aucun animal", "Ajoutez votre premier compagnon.") }
            itemsIndexed(animals, key = { _, a -> a.id }) { i, a ->
                ListItem(
                    modifier = Modifier.animateItem().clip(MaterialTheme.shapes.large).clickable { nav.navigate(Routes.AnimalEdit(a.id)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = { AnimalAvatar(a, 44.dp) },
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text(listOf(a.species, a.breed).filter { it.isNotBlank() }.joinToString(" · ")) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Monter") }
                            IconButton(onClick = { move(i, i + 1) }, enabled = i < animals.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Descendre") }
                            IconButton(onClick = { toDelete = a }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) }
                        }
                    },
                )
            }
        }
    }
    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ${a.name} ?") },
            text = { Text("Le profil, ses traitements et leurs rappels seront supprimés" + if (c.sharedFlag) ", aussi chez les autres membres du foyer." else ".") },
            confirmButton = { TextButton(onClick = { toDelete = null; scope.launch { c.deleteAnimal(a) } }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}
