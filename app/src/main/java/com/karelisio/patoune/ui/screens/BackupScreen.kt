package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalResources
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
    val res = LocalResources.current
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
                res.getString(R.string.backup_done)
            }.getOrElse { res.getString(R.string.error_failed, it.message ?: "") }
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
                res.getString(
                    R.string.backup_imported,
                    res.getQuantityString(R.plurals.backup_fields, r.rows, r.rows),
                    res.getQuantityString(R.plurals.backup_media, r.media, r.media),
                )
            }.getOrElse { if (it is WrongPassword) res.getString(R.string.backup_wrong_password) else res.getString(R.string.error_failed, it.message ?: "") }
            busy = false
        }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.backup_short)) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Text(stringResource(R.string.backup_export), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.backup_export_body), style = MaterialTheme.typography.bodyMedium)
                PasswordField(pwd, { pwd = it }, stringResource(R.string.backup_password))
                PasswordField(pwd2, { pwd2 = it }, stringResource(R.string.backup_confirm))
                Button(onClick = { exporter.launch("patoune-${LocalDate.now()}.zip") }, enabled = !busy && pwd.length >= 8 && pwd == pwd2, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
            }
            SectionCard {
                Text(stringResource(R.string.backup_import), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.backup_import_body), style = MaterialTheme.typography.bodyMedium)
                PasswordField(importPwd, { importPwd = it }, stringResource(R.string.backup_import_password))
                OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy && importPwd.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_pick_file)) }

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
