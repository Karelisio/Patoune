package com.karelisio.patoune

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.karelisio.patoune.air.AirRepo
import com.karelisio.patoune.backup.Backup
import com.karelisio.patoune.report.ReportGenerator
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.media.MediaRepo
import com.karelisio.patoune.reminders.Doses
import com.karelisio.patoune.reminders.ReminderScheduler
import com.karelisio.patoune.sync.HouseholdManager
import com.karelisio.patoune.sync.HttpApi
import com.karelisio.patoune.sync.KeystoreBox
import com.karelisio.patoune.sync.SessionStore
import com.karelisio.patoune.sync.SyncEngine
import com.karelisio.patoune.sync.SyncWorker
import com.karelisio.patoune.update.Updater
import com.karelisio.patoune.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Injection de dépendances manuelle : un seul conteneur pour toute l'appli. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: PatouneDb = PatouneDb.build(context)
    val settings = Settings(context)
    val deviceId: String = runBlocking { settings.deviceId() }

    /** Mode partagé actif (mis à jour depuis les réglages) : évite de lire DataStore à chaque écriture. */
    @Volatile var sharedFlag: Boolean = false

    val store = Store(db, deviceId, author = { settings.current().memberName }, onLocalChange = { hooks.onLocalWrite() })
    val updater = Updater(context, settings)
    val reminders = ReminderScheduler(context)
    val doses = Doses(this)
    val hooks = Hooks(this)
    val media = MediaRepo(context, store)
    val backup = Backup(db, store) { media.dir }
    val widgets = Widgets(context)
    val air = AirRepo(db.air())
    val report = ReportGenerator(context, this)

    // ---- Mode partagé (aucun appel réseau tant que le foyer n'existe pas) ----
    val sessions = SessionStore(settings, KeystoreBox())
    val api = HttpApi { sessions.load()?.let { it.server to it.token } }
    val sync = SyncEngine(api, store, db, sessions, settings, { media.dir }) { events ->
        hooks.onRemote(events.map { it.entity }.toSet(), events.filter { it.entity == "dose" && it.field == "medicationId" }.map { it.value.toString().trim('"') }.toSet())
    }
    val household = HouseholdManager(api, store, db, sessions, settings, sync, fcmToken = { fcmToken() })

    init {
        scope.launch {
            settings.flow.collect { s ->
                val was = sharedFlag
                sharedFlag = s.shared
                if (s.shared && !was) { SyncWorker.periodic(context); SyncWorker.now(context) }
                if (!s.shared && was) SyncWorker.cancel(context)
            }
        }
    }

    /** Jeton FCM, seulement si Firebase est configuré (google-services.json présent au build). */
    suspend fun fcmToken(): String? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().token.await()
    }.getOrNull()
}
