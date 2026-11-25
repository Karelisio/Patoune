package com.karelisio.patoune.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.ShapeBadge
import kotlinx.coroutines.launch

@Composable
fun UpdateSheet(release: Release, updater: Updater, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val animated by animateFloatAsState(progress ?: 0f, spring(stiffness = 200f), label = "dl")
    val vitrine = remember { updater.vitrineInstalled() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShapeBadge(Icons.Rounded.SystemUpdate, PawShapes.sunny, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 64.dp)
            Text("Patoune ${release.version} est disponible", style = MaterialTheme.typography.headlineSmall)
            Text(
                release.changelog.ifBlank { "Améliorations et corrections." },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            )
            if (progress != null) {
                LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                Text("${(animated * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (vitrine) {
                Button(onClick = { updater.openInVitrine(); onDismiss() }, Modifier.fillMaxWidth()) { Text("Ouvrir dans Vitrine") }
            }
            val download: () -> Unit = {
                progress = 0f; error = null
                scope.launch {
                    runCatching {
                        updater.download(release).collect { s ->
                            when (s) {
                                is DownloadState.Progress -> progress = s.fraction
                                is DownloadState.Done -> updater.install(s.file)
                            }
                        }
                    }.onFailure { error = "Téléchargement impossible : ${it.message}"; progress = null }
                }
            }
            if (vitrine) OutlinedButton(onClick = download, enabled = progress == null, modifier = Modifier.fillMaxWidth()) { Text("Télécharger ici") }
            else Button(onClick = download, enabled = progress == null, modifier = Modifier.fillMaxWidth()) { Text("Télécharger et installer") }
            OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Plus tard") }
        }
    }
}
