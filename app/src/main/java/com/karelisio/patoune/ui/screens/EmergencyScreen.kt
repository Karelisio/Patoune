package com.karelisio.patoune.ui.screens

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
        BackTopBar("Fiche urgence") { a?.let { IconButton(onClick = { nav.navigate(Routes.AnimalEdit(it.id)) }) { Icon(Icons.Rounded.Edit, "Modifier") } } }
    }) { pad ->
        if (a == null) return@Scaffold
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimalAvatar(a, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.headlineMedium)
                        Text(listOf(a.species, a.breed, a.sex).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                        weights.lastOrNull()?.let { Text("Poids : ${fmtQty(it.kg)} kg (${Dates.fmtDay(it.date)})", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            item {
                if (a.emergencyVetPhone.isNotBlank()) Button(
                    onClick = { call(a.emergencyVetPhone) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Icon(Icons.Rounded.LocalHospital, null); Spacer(Modifier.width(8.dp))
                    Text("Véto de garde · ${a.emergencyVetName.ifBlank { a.emergencyVetPhone }}", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                if (a.vetPhone.isNotBlank()) FilledTonalButton(onClick = { call(a.vetPhone) }, Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Rounded.Call, null); Spacer(Modifier.width(8.dp))
                    Text("Véto attitré · ${a.vetName.ifBlank { a.vetPhone }}", style = MaterialTheme.typography.titleMedium)
                }
                if (a.vetPhone.isBlank() && a.emergencyVetPhone.isBlank()) Text("Ajoutez les numéros de vos vétos dans le profil.", color = MaterialTheme.colorScheme.error)
            }
            item {
                SectionCard {
                    Text("Traitement en cours", style = MaterialTheme.typography.titleLarge)
                    if (meds.isEmpty()) Text("Aucun")
                    meds.forEach { m ->
                        Text("• ${m.name} — ${m.dose.ifBlank { m.type.label }}" + if (m.asNeeded) " (si besoin)" else " à ${m.times.replace(",", ", ")}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                SectionCard {
                    Text("Infos clés", style = MaterialTheme.typography.titleLarge)
                    Text("Allergies : ${a.allergies.ifBlank { "aucune connue" }}", style = MaterialTheme.typography.bodyLarge)
                    Text("Antécédents : ${a.history.ifBlank { "—" }}", style = MaterialTheme.typography.bodyLarge)
                    a.birthDate?.let { Text("Né(e) le ${Dates.fmtDay(it)}", style = MaterialTheme.typography.bodyLarge) }
                    if (a.chipNumber.isNotBlank()) Text("Puce : ${a.chipNumber}", style = MaterialTheme.typography.bodyLarge)
                    Text("Stérilisé(e) : ${if (a.sterilized) "oui" else "non"}", style = MaterialTheme.typography.bodyLarge)
                    a.breathThreshold?.let { Text("Seuil respiratoire fixé avec le véto : $it resp/min", style = MaterialTheme.typography.bodyLarge) }
                }
            }
            item { VetNote("Patoune aide au suivi et ne remplace pas l'avis de votre vétérinaire.") }
        }
    }
}
