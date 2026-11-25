package com.karelisio.patoune.ui

import android.app.Application
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.ContainerOwner
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import java.util.UUID

/**
 * Application des tests d'interface : le vrai conteneur, mais avec une base Room en mémoire
 * et des réglages isolés (un fichier DataStore par test). Pas de WorkManager ni de rappels au démarrage.
 */
class UiTestApp : Application(), ContainerOwner {
    override val container: AppContainer by lazy {
        AppContainer(this, db = PatouneDb.build(this, name = null), settings = Settings(this, "ui-${UUID.randomUUID()}"))
    }
}
