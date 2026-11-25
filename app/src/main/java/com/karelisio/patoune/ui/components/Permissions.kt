package com.karelisio.patoune.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.patoune.container

enum class AppPermission(val title: String, val why: String, val icon: ImageVector) {
    NOTIFICATIONS("Notifications", "Rappels de traitement, alertes respiratoires et prises données par les autres membres.", Icons.Rounded.Notifications),
    EXACT_ALARMS("Alarmes exactes", "Pour que les rappels de médicaments sonnent à l'heure pile, même en veille.", Icons.Rounded.Alarm),
    FULL_SCREEN("Rappels plein écran", "Pour les traitements en mode réveil : afficher le rappel en grand, même écran verrouillé.", Icons.Rounded.AlarmOn),
    CAMERA_MIC("Caméra et micro", "Photos, et vidéos de crise avec le son : la toux s'entend, c'est précieux pour le véto.", Icons.Rounded.Videocam),
    BATTERY("Batterie sans restriction", "Certains téléphones (Honor/MagicOS, Huawei, Xiaomi…) coupent les applis en arrière-plan : rappels et sync seraient perdus. Pensez aussi à autoriser le lancement automatique dans les réglages du téléphone.", Icons.Rounded.BatteryChargingFull),
    INSTALL("Installation de mises à jour", "Pour installer les nouvelles versions de Patoune directement depuis l'appli.", Icons.Rounded.InstallMobile),
}

object Perms {
    fun granted(context: Context, p: AppPermission): Boolean = when (p) {
        AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        AppPermission.EXACT_ALARMS -> context.container.reminders.canExact()
        AppPermission.FULL_SCREEN -> Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        AppPermission.CAMERA_MIC -> listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        AppPermission.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        AppPermission.INSTALL -> context.packageManager.canRequestPackageInstalls()
    }

    fun settingsIntent(context: Context, p: AppPermission): Intent? {
        val pkg = "package:${context.packageName}".toUri()
        return when (p) {
            AppPermission.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            AppPermission.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            @Suppress("BatteryLife")
            AppPermission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            AppPermission.INSTALL -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
            AppPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            AppPermission.CAMERA_MIC -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }
}

/** Compteur qui s'incrémente à chaque retour au premier plan (retour des réglages système). */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** Renvoie une fonction qui demande l'autorisation (dialogue système ou écran de réglages). */
@Composable
fun rememberPermissionRequester(onResult: () -> Unit): (AppPermission) -> Unit {
    val context = LocalContext.current
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return remember {
        { p: AppPermission ->
            when {
                p == AppPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 && !Perms.granted(context, p) ->
                    runtime.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                p == AppPermission.CAMERA_MIC -> runtime.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                else -> Perms.settingsIntent(context, p)?.let { runCatching { context.startActivity(it) } }
            }
        }
    }
}
