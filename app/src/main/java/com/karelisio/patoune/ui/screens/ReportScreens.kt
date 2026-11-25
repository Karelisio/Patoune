package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalResources
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
    val res = LocalResources.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var from by remember { mutableStateOf(Dates.today().minusDays(30).toString()) }
    var to by remember { mutableStateOf(Dates.today().toString()) }
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.report_title)) { file?.let { f -> IconButton(onClick = { shareFile(ctx, f, "application/pdf") }) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) } } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L to R.string.range_week, 30L to R.string.range_month, 90L to R.string.range_3_months).forEach { (d, l) ->
                        val f = Dates.today().minusDays(d).toString()
                        FilterChip(from == f && to == Dates.today().toString(), { from = f; to = Dates.today().toString() }, { Text(stringResource(l)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(from, { from = it ?: from }, stringResource(R.string.report_from), Modifier.weight(1f), clearable = false)
                    DateField(to, { to = it ?: to }, stringResource(R.string.report_to), Modifier.weight(1f), clearable = false)
                }
                Button(
                    onClick = {
                        val a = animal ?: return@Button
                        busy = true; error = null
                        scope.launch {
                            runCatching { file = c.report.generate(a, java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)) }
                                .onFailure { error = res.getString(R.string.error_failed, it.message ?: "") }
                            busy = false
                        }
                    },
                    enabled = !busy && animal != null, modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Rounded.PictureAsPdf, null); Text(stringResource(R.string.report_generate), Modifier.padding(start = 8.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                VetNote(stringResource(R.string.report_contents))
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
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val days by remember { c.db.air().observeAll() }.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.air_title)) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard {
                    Text(stringResource(R.string.air_city, settings?.city ?: stringResource(R.string.air_no_city)), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.air_search_city)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { scope.launch { results = runCatching { c.air.search(query) }.getOrElse { msg = res.getString(R.string.air_search_failed); emptyList() } } }) {
                                Icon(Icons.Rounded.Search, stringResource(R.string.air_search))
                            }
                        })
                    Text(stringResource(R.string.air_explain), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(results) { city ->
                ListItem(
                    headlineContent = { Text(city.name) }, supportingContent = { Text(city.detail) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            c.settings.setCity(city.name, city.lat, city.lon)
                            results = emptyList()
                            msg = runCatching {
                                val n = c.air.fetch(city.name, city.lat, city.lon, pastDays = 30)
                                res.getQuantityString(R.plurals.air_days_fetched, n, n)
                            }.getOrElse { res.getString(R.string.air_fetch_failed, it.message ?: "") }
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
                        Text(stringResource(R.string.air_day_values, listOfNotNull(
                            d.aqi?.let { stringResource(R.string.air_aqi, it.toInt()) },
                            d.grass?.let { stringResource(R.string.air_grass, it.toInt()) }, d.birch?.let { stringResource(R.string.air_birch, it.toInt()) },
                            d.alder?.let { stringResource(R.string.air_alder, it.toInt()) }, d.mugwort?.let { stringResource(R.string.air_mugwort, it.toInt()) },
                            d.ragweed?.let { stringResource(R.string.air_ragweed, it.toInt()) }, d.olive?.let { stringResource(R.string.air_olive, it.toInt()) },
                        ).joinToString(" · ")))
                    },
                )
            }
            item { Text(stringResource(R.string.air_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        }
    }
}
