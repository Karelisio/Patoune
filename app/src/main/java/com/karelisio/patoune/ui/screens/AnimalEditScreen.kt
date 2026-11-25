package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.ui.stableLabel
import com.karelisio.patoune.ui.Labels
import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalResources
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
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
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
            val saved = c.store.save(a.copy(name = a.name.trim().ifEmpty { res.getString(R.string.animal_default_name) }, sortOrder = order))
            vm.select(saved.id)
            if (onSaved != null) onSaved() else nav.popBackStack()
        }
    }
    val content = @Composable {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val photoLabel = stringResource(R.string.animal_pick_photo)
                Box(Modifier.clickable(onClickLabel = photoLabel) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { AnimalAvatar(a, 120.dp) }
            }
            Text(stringResource(R.string.animal_photo_hint), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Field(a.name, { a = a.copy(name = it) }, stringResource(R.string.animal_name))
            // Espèce et sexe : valeurs stockées en français (clés stables), libellés traduits.
            val species = Labels.species.keys.toList()
            ChoiceChips(if (a.species in species || a.species.isBlank()) species else species + a.species, a.species, { stableLabel(Labels.species, it) }) { a = a.copy(species = it) }
            Field(a.breed, { a = a.copy(breed = it) }, stringResource(R.string.animal_breed))
            ChoiceChips(Labels.sexes.keys.toList(), a.sex, { stableLabel(Labels.sexes, it) }) { a = a.copy(sex = it) }
            DateField(a.birthDate, { a = a.copy(birthDate = it) }, stringResource(R.string.animal_birth))
            Field(a.chipNumber, { a = a.copy(chipNumber = it) }, stringResource(R.string.animal_chip), keyboard = KeyboardType.Number)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.animal_sterilized), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(a.sterilized, { a = a.copy(sterilized = it) })
            }
            Text(stringResource(R.string.animal_vets), style = MaterialTheme.typography.titleMedium)
            Field(a.vetName, { a = a.copy(vetName = it) }, stringResource(R.string.animal_vet))
            Field(a.vetPhone, { a = a.copy(vetPhone = it) }, stringResource(R.string.animal_vet_phone), keyboard = KeyboardType.Phone)
            Field(a.emergencyVetName, { a = a.copy(emergencyVetName = it) }, stringResource(R.string.animal_emergency_vet))
            Field(a.emergencyVetPhone, { a = a.copy(emergencyVetPhone = it) }, stringResource(R.string.animal_emergency_vet_phone), keyboard = KeyboardType.Phone)
            Text(stringResource(R.string.animal_health), style = MaterialTheme.typography.titleMedium)
            Field(a.breathThreshold?.toString() ?: "", { a = a.copy(breathThreshold = it.toIntOrNull()) }, stringResource(R.string.animal_threshold), keyboard = KeyboardType.Number)
            VetNote(stringResource(R.string.animal_threshold_note))
            Field(a.allergies, { a = a.copy(allergies = it) }, stringResource(R.string.animal_allergies), minLines = 2)
            Field(a.history, { a = a.copy(history = it) }, stringResource(R.string.animal_history), minLines = 2)
            Button(onClick = save, enabled = loaded, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
            if (id != null && !embedded) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.animal_delete_profile), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (embedded) content()
    else Scaffold(topBar = { BackTopBar(stringResource(if (id == null) R.string.animal_new else R.string.animal_profile)) }) { pad -> Box(Modifier.padding(pad)) { content() } }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_named_q, a.name)) },
        text = { Text(stringResource(R.string.animal_delete_body)) },
        confirmButton = { TextButton(onClick = { scope.launch { c.deleteAnimal(a); nav.popBackStack() } }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },

    )
}
