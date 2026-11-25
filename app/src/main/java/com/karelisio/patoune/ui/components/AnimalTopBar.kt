package com.karelisio.patoune.ui.components

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import androidx.compose.ui.platform.LocalContext
import com.karelisio.patoune.container
import java.io.File

@Composable
fun AnimalAvatar(animal: Animal?, size: Dp = 32.dp) {
    val ctx = LocalContext.current
    val path = remember(animal?.photoMediaId) {
        animal?.photoMediaId?.let { id -> File(ctx.filesDir, "media/$id").takeIf { it.exists() } }
    }
    Box(Modifier.size(size).clip(PawShapes.cookie).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (path != null) AsyncImage(path, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else PawIcon(MaterialTheme.colorScheme.onPrimaryContainer, size = size * 0.6f)
    }
}

/** Barre du haut avec le sélecteur d'animal (présent sur tous les écrans principaux). */
@Composable
fun AnimalTopBar(vm: MainViewModel, title: String? = null, scroll: TopAppBarScrollBehavior? = null, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    val animals by vm.animals.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        scrollBehavior = scroll,
        title = {
            Box {
                FilledTonalButton(onClick = { open = true }) {
                    AnimalAvatar(selected, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(selected?.name ?: stringResource(R.string.animal_add), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Rounded.ExpandMore, stringResource(R.string.a11y_choose_animal))
                }
                DropdownMenu(open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
                    animals.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.name) },
                            leadingIcon = { AnimalAvatar(a, 28.dp) },
                            onClick = { open = false; vm.select(a.id) },
                        )
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.animal_new)) }, leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { open = false; nav.navigate(Routes.AnimalEdit()) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.animal_manage)) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { open = false; nav.navigate(Routes.Animals) })
                    selected?.let { a ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.animal_edit_named, a.name)) }, onClick = { open = false; nav.navigate(Routes.AnimalEdit(a.id)) })
                    }
                }
            }
            title?.let { Row { } }
        },
        actions = {
            actions()
            IconButton(onClick = { nav.navigate(Routes.Settings) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title)) }

        },
    )
}
