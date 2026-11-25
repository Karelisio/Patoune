package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.ui.fmtQty
import com.karelisio.patoune.ui.Labels
import com.karelisio.patoune.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalAvatar
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.VetNote
import kotlinx.coroutines.flow.flowOf

/** Fiche urgence : appel en un tap, traitement en cours, infos clés. Grande taille, lisible dans l'urgence. */
@Composable
fun EmergencyScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val c = ctx.container
    val nav = LocalNav.current
    val animal by vm.selected.collectAsStateWithLifecycle()
    val meds by remember(animal?.id) { animal?.let { c.db.medications().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val weights by remember(animal?.id) { animal?.let { c.db.weights().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val a = animal
    fun call(phone: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, "tel:${phone.filter { it.isDigit() || it == '+' }}".toUri()))

    Scaffold(topBar = {
        BackTopBar(stringResource(R.string.emergency_title)) { a?.let { IconButton(onClick = { nav.navigate(Routes.AnimalEdit(it.id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) } } }
    }) { pad ->
        if (a == null) return@Scaffold
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimalAvatar(a, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.headlineMedium)
                        Text(Labels.animalTraits(ctx, a.species, a.breed, a.sex), style = MaterialTheme.typography.bodyLarge)
                        weights.lastOrNull()?.let { Text(stringResource(R.string.emergency_weight, fmtQty(it.kg), Dates.fmtDay(it.date)), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            item {
                val emergencyLabel = stringResource(R.string.a11y_call_emergency_vet, a.emergencyVetName.ifBlank { a.emergencyVetPhone })
                if (a.emergencyVetPhone.isNotBlank()) Button(
                    onClick = { call(a.emergencyVetPhone) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Icon(Icons.Rounded.LocalHospital, null); Spacer(Modifier.width(8.dp))
                    // Libellé explicite pour TalkBack (le bouton fusionne ses enfants et porte le rôle bouton).
                    Text(stringResource(R.string.emergency_vet_button, a.emergencyVetName.ifBlank { a.emergencyVetPhone }), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { contentDescription = emergencyLabel })
                }
            }
            item {
                val vetLabel = stringResource(R.string.a11y_call_vet, a.vetName.ifBlank { a.vetPhone })
                if (a.vetPhone.isNotBlank()) FilledTonalButton(onClick = { call(a.vetPhone) }, Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vet_button, a.vetName.ifBlank { a.vetPhone }), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { contentDescription = vetLabel })
                }
                if (a.vetPhone.isBlank() && a.emergencyVetPhone.isBlank()) Text(stringResource(R.string.emergency_no_numbers), color = MaterialTheme.colorScheme.error)
            }
            item {
                SectionCard {
                    Text(stringResource(R.string.emergency_current_treatment), style = MaterialTheme.typography.titleLarge)
                    if (meds.isEmpty()) Text(stringResource(R.string.emergency_none))
                    meds.forEach { m ->
                        val dose = m.dose.ifBlank { stringResource(m.type.labelRes) }
                        Text(
                            if (m.asNeeded) stringResource(R.string.emergency_med_as_needed, m.name, dose)
                            else stringResource(R.string.emergency_med_times, m.name, dose, m.times.replace(",", ", ")),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                SectionCard {
                    Text(stringResource(R.string.emergency_key_info), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.emergency_allergies, a.allergies.ifBlank { stringResource(R.string.emergency_no_allergy) }), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.emergency_history, a.history.ifBlank { stringResource(R.string.dash) }), style = MaterialTheme.typography.bodyLarge)
                    a.birthDate?.let { Text(stringResource(R.string.emergency_born, Dates.fmtDay(it)), style = MaterialTheme.typography.bodyLarge) }
                    if (a.chipNumber.isNotBlank()) Text(stringResource(R.string.emergency_chip, a.chipNumber), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(if (a.sterilized) R.string.emergency_sterilized_yes else R.string.emergency_sterilized_no), style = MaterialTheme.typography.bodyLarge)
                    a.breathThreshold?.let { Text(stringResource(R.string.emergency_threshold, it), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            item { VetNote(stringResource(R.string.emergency_disclaimer)) }

        }
    }
}
