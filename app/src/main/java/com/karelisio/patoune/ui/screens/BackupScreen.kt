package com.karelisio.patoune.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.backup.WrongPassword
import com.karelisio.patoune.container
import com.karelisio.patoune.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var importPwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { c.backup.export(it, pwd.toCharArray()) }
                "Sauvegarde créée. Gardez le mot de passe : sans lui, elle est illisible."
            }.getOrElse { "Échec : ${it.message}" }
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = runCatching {
                val r = ctx.contentResolver.openInputStream(uri)!!.use { c.backup.import(it, importPwd.toCharArray()) }
                c.reminders.rescheduleAll()
                c.hooks.onImported()
                "Import terminé : ${r.rows} champ(s) fusionné(s), ${r.media} média(s)."
            }.getOrElse { if (it is WrongPassword) "Mot de passe incorrect." else "Échec : ${it.message}" }
            busy = false
        }
    }

    Scaffold(topBar = { BackTopBar("Sauvegarde") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Text("Exporter", style = MaterialTheme.typography.titleLarge)
                Text("Toutes les données et les médias, chiffrés avec votre mot de passe (.zip).", style = MaterialTheme.typography.bodyMedium)
                PasswordField(pwd, { pwd = it }, "Mot de passe (8 caractères min.)")
                PasswordField(pwd2, { pwd2 = it }, "Confirmer")
                Button(onClick = { exporter.launch("patoune-${LocalDate.now()}.zip") }, enabled = !busy && pwd.length >= 8 && pwd == pwd2, modifier = Modifier.fillMaxWidth()) { Text("Exporter") }
            }
            SectionCard {
                Text("Importer", style = MaterialTheme.typography.titleLarge)
                Text("Fusionne une sauvegarde avec les données actuelles (rien n'est effacé).", style = MaterialTheme.typography.bodyMedium)
                PasswordField(importPwd, { importPwd = it }, "Mot de passe de la sauvegarde")
                OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy && importPwd.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Choisir le fichier") }
            }
            if (busy) CircularWavyProgressIndicator()
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
