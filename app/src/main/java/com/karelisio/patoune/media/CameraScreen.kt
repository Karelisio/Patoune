package com.karelisio.patoune.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.patoune.container
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.MediaKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Capture intégrée CameraX : photo, ou vidéo avec le son (la toux s'entend).
 * Les vidéos sont ensuite compressées en 720p.
 */
@android.annotation.SuppressLint("MissingPermission")
@Composable
fun CameraScreen(video: Boolean, animalId: String, attach: AttachType, attachId: String?, autoStart: Boolean, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val perms = if (video) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.CAMERA)
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasCamera = granted(Manifest.permission.CAMERA) }
    LaunchedEffect(Unit) { if (perms.any { !granted(it) }) launcher.launch(perms) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(if (video) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(hasCamera) {
        if (hasCamera) controller.bindToLifecycle(lifecycle)
        onDispose { controller.unbind() }
    }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var processing by remember { mutableStateOf(false) }
    val (id, file) = remember { c.media.newFile() }

    fun finish(kind: MediaKind, mime: String) {
        processing = true
        scope.launch {
            if (kind == MediaKind.VIDEO) {
                VideoCompressor.compress(context, file)?.let { out -> file.delete(); out.renameTo(file) }
            }
            c.media.register(id, animalId, kind, mime, attach, attachId, durationMs = if (kind == MediaKind.VIDEO) VideoCompressor.durationMs(file) else 0)
            onDone(id)
        }
    }

    fun startRecording() {
        val audio = AudioConfig.create(granted(Manifest.permission.RECORD_AUDIO))
        recStart = System.currentTimeMillis()
        recording = controller.startRecording(FileOutputOptions.Builder(file).build(), audio, ContextCompat.getMainExecutor(context)) { e ->
            if (e is VideoRecordEvent.Finalize) {
                recording = null
                if (e.hasError() && file.length() == 0L) onDone(null) else finish(MediaKind.VIDEO, "video/mp4")
            }
        }
    }

    fun shutter() {
        if (video) {
            val r = recording
            if (r == null) startRecording() else r.stop()
        } else {
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = finish(MediaKind.PHOTO, "image/jpeg")
                    override fun onError(exception: ImageCaptureException) { onDone(null) }
                })
        }
    }

    // « Crise en cours » : l'enregistrement démarre dès que la caméra est prête.
    LaunchedEffect(hasCamera) {
        if (autoStart && video && hasCamera) {
            delay(700)
            if (recording == null) startRecording()
        }
    }
    LaunchedEffect(recording) { while (recording != null) { elapsed = System.currentTimeMillis() - recStart; delay(250) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) AndroidView({ PreviewView(it).apply { this.controller = controller } }, Modifier.fillMaxSize())
        else Text("Autorisez la caméra pour filmer ou photographier.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        Row(Modifier.systemBarsPadding().padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { recording?.stop(); onDone(null) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
            Box(Modifier.weight(1f))
            if (recording != null) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xCCB3261E)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("● %d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (processing) {
                CircularWavyProgressIndicator()
                Text(if (video) "Compression en 720p…" else "Enregistrement…", color = Color.White)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.size(48.dp))
                val inner by animateDpAsState(if (recording != null) 28.dp else 64.dp, label = "shutter")
                Box(Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).clickable(enabled = hasCamera) { shutter() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(inner).clip(if (recording != null) RoundedCornerShape(8.dp) else CircleShape).background(if (video) Color(0xFFE53935) else Color.White))
                }
                IconButton(onClick = {
                    controller.cameraSelector = if (controller.cameraSelector == androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA)
                        androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA else androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                }, enabled = recording == null) { Icon(Icons.Rounded.Cameraswitch, "Changer de caméra", tint = Color.White) }
            }
        }
    }
}
