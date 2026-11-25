package com.karelisio.patoune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.air.City
import com.karelisio.patoune.container
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.components.DateField
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.VetNote
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReportScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var from by remember { mutableStateOf(Dates.today().minusDays(30).toString()) }
    var to by remember { mutableStateOf(Dates.today().toString()) }
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar("Rapport pour le véto") { file?.let { f -> IconButton(onClick = { shareFile(ctx, f, "application/pdf") }) { Icon(Icons.Rounded.Share, "Partager") } } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L to "7 jours", 30L to "30 jours", 90L to "3 mois").forEach { (d, l) ->
                        val f = Dates.today().minusDays(d).toString()
                        FilterChip(from == f && to == Dates.today().toString(), { from = f; to = Dates.today().toString() }, { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(from, { from = it ?: from }, "Du", Modifier.weight(1f), clearable = false)
                    DateField(to, { to = it ?: to }, "Au", Modifier.weight(1f), clearable = false)
                }
                Button(
                    onClick = {
                        val a = animal ?: return@Button
                        busy = true; error = null
                        scope.launch {
                            runCatching { file = c.report.generate(a, java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)) }
                                .onFailure { error = "Échec : ${it.message}" }
                            busy = false
                        }
                    },
                    enabled = !busy && animal != null, modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.PictureAsPdf, null); Text("  Générer le PDF") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                VetNote("Profil, traitements et observance, courbe respiratoire, crises avec vignettes, poids, observations.")
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    busy -> CircularWavyProgressIndicator()
                    file != null -> PdfPages(file!!)
                }
            }
        }
    }
}

/** Choix manuel de la ville (pas d'autorisation de localisation) et données du jour. */
@Composable
fun AirScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val days by remember { c.db.air().observeAll() }.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar("Pollens et qualité de l'air") }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    Text("Ville : ${settings?.city ?: "aucune"}", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(query, { query = it }, label = { Text("Rechercher une ville") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { scope.launch { results = runCatching { c.air.search(query) }.getOrElse { msg = "Recherche impossible (hors ligne ?)"; emptyList() } } }) {
                                Icon(Icons.Rounded.Search, "Rechercher")
                            }
                        })
                    Text("Les données sont récupérées une fois par jour et superposées à la frise des crises et de la respiration.", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(results) { city ->
                ListItem(
                    headlineContent = { Text(city.name) }, supportingContent = { Text(city.detail) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            c.settings.setCity(city.name, city.lat, city.lon)
                            results = emptyList()
                            msg = runCatching { "${c.air.fetch(city.name, city.lat, city.lon, pastDays = 30)} jour(s) récupéré(s)." }.getOrElse { "Récupération impossible : ${it.message}" }
                        }
                    },
                )
            }
            msg?.let { item { Text(it) } }
            items(days.reversed().take(14)) { d ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Grass, null) },
                    headlineContent = { Text(Dates.fmtDay(d.date)) },
                    supportingContent = {
                        Text(listOfNotNull(
                            d.aqi?.let { "AQI ${it.toInt()}" },
                            d.grass?.let { "graminées ${it.toInt()}" }, d.birch?.let { "bouleau ${it.toInt()}" }, d.alder?.let { "aulne ${it.toInt()}" },
                            d.mugwort?.let { "armoise ${it.toInt()}" }, d.ragweed?.let { "ambroisie ${it.toInt()}" }, d.olive?.let { "olivier ${it.toInt()}" },
                        ).joinToString(" · ") + " (grains/m³)")
                    },
                )
            }
            item { Text("Source : Open-Meteo.com, données CAMS (Copernicus Atmosphere Monitoring Service), licence CC BY 4.0.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
