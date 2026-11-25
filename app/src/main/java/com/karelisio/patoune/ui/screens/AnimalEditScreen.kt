package com.karelisio.patoune.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.karelisio.patoune.container
import com.karelisio.patoune.deleteAnimal
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.domain.newId
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.components.AnimalAvatar
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.ui.components.DateField
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.VetNote
import kotlinx.coroutines.launch

@Composable
fun AnimalEditScreen(vm: MainViewModel, id: String?, onSaved: (() -> Unit)? = null, embedded: Boolean = false) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf(Animal(id = id ?: newId())) }
    var loaded by remember { mutableStateOf(id == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { if (id != null) c.db.animals().byId(id)?.let { a = it }; loaded = true }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { c.media.import(uri, a.id, AttachType.ANIMAL, a.id)?.let { a = a.copy(photoMediaId = it.id) } }
    }
    val save: () -> Unit = {
        scope.launch {
            val order = if (id == null) (c.db.animals().allRows().filter { !it.deleted }.maxOfOrNull { it.sortOrder } ?: -1) + 1 else a.sortOrder
            val saved = c.store.save(a.copy(name = a.name.trim().ifEmpty { "Mon animal" }, sortOrder = order))
            vm.select(saved.id)
            if (onSaved != null) onSaved() else nav.popBackStack()
        }
    }
    val content = @Composable {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { AnimalAvatar(a, 120.dp) }
            }
            Text("Touchez pour choisir une photo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Field(a.name, { a = a.copy(name = it) }, "Nom")
            ChoiceChips(listOf("Chat", "Chien", "Lapin", "Autre"), a.species, { it }) { a = a.copy(species = it) }
            Field(a.breed, { a = a.copy(breed = it) }, "Race")
            ChoiceChips(listOf("Femelle", "Mâle"), a.sex, { it }) { a = a.copy(sex = it) }
            DateField(a.birthDate, { a = a.copy(birthDate = it) }, "Date de naissance")
            Field(a.chipNumber, { a = a.copy(chipNumber = it) }, "Numéro de puce", keyboard = KeyboardType.Number)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stérilisé(e)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(a.sterilized, { a = a.copy(sterilized = it) })
            }
            Text("Vétérinaires", style = MaterialTheme.typography.titleMedium)
            Field(a.vetName, { a = a.copy(vetName = it) }, "Véto attitré")
            Field(a.vetPhone, { a = a.copy(vetPhone = it) }, "Téléphone du véto", keyboard = KeyboardType.Phone)
            Field(a.emergencyVetName, { a = a.copy(emergencyVetName = it) }, "Véto de garde")
            Field(a.emergencyVetPhone, { a = a.copy(emergencyVetPhone = it) }, "Téléphone de garde", keyboard = KeyboardType.Phone)
            Text("Santé", style = MaterialTheme.typography.titleMedium)
            Field(a.breathThreshold?.toString() ?: "", { a = a.copy(breathThreshold = it.toIntOrNull()) }, "Seuil respiratoire (resp/min)", keyboard = KeyboardType.Number)
            VetNote("Seuil à fixer avec votre véto (souvent autour de 30 au repos chez le chat).")
            Field(a.allergies, { a = a.copy(allergies = it) }, "Allergies", minLines = 2)
            Field(a.history, { a = a.copy(history = it) }, "Antécédents", minLines = 2)
            Button(onClick = save, enabled = loaded, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
            if (id != null && !embedded) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Supprimer ce profil", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (embedded) content()
    else Scaffold(topBar = { BackTopBar(if (id == null) "Nouvel animal" else "Profil") }) { pad -> Box(Modifier.padding(pad)) { content() } }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer ${a.name} ?") },
        text = { Text("Le profil et son suivi ne seront plus affichés.") },
        confirmButton = { TextButton(onClick = { scope.launch { c.deleteAnimal(a); nav.popBackStack() } }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )
}
