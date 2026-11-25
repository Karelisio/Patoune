package com.karelisio.patoune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.EmptyState

@Composable
fun HomeScreen(vm: MainViewModel) {
    val nav = LocalNav.current
    val animal by vm.selected.collectAsStateWithLifecycle()
    Scaffold(topBar = { AnimalTopBar(vm) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (animal == null) item {
                EmptyState(Icons.Rounded.Pets, "Bienvenue sur Patoune", "Ajoutez votre premier compagnon pour commencer le suivi.")
                Button(onClick = { nav.navigate(Routes.AnimalEdit()) }) { Text("Ajouter un animal") }
            }
        }
    }
}
