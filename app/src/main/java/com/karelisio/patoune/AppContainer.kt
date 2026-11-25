package com.karelisio.patoune

import android.content.Context
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.reminders.Doses
import com.karelisio.patoune.reminders.ReminderScheduler
import com.karelisio.patoune.backup.Backup
import com.karelisio.patoune.media.MediaRepo
import com.karelisio.patoune.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** Injection de dépendances manuelle : un seul conteneur pour toute l'appli. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: PatouneDb = PatouneDb.build(context)
    val settings = Settings(context)
    val deviceId: String = runBlocking { settings.deviceId() }
    val store = Store(db, deviceId, author = { settings.current().memberName }, onLocalChange = { onLocalChange() })
    val updater = Updater(context, settings)
    val reminders = ReminderScheduler(context)
    val doses = Doses(this)
    val hooks = Hooks(this)
    val media = MediaRepo(context, store)
    val backup = Backup(db, store) { media.dir }

    private fun onLocalChange() {}
}
