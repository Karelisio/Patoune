package com.karelisio.patoune

import android.content.Context
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
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

    private fun onLocalChange() {}
}
