package com.karelisio.patoune

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.sync.HouseholdManager
import com.karelisio.patoune.sync.MemoryBox
import com.karelisio.patoune.sync.SessionStore
import com.karelisio.patoune.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/** Un « téléphone » de test : base Room en mémoire, réglages, store, sync et foyer. */
class TestDevice(val deviceId: String, var author: String = "", server: FakeServer? = null) {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    val db: PatouneDb = Room.inMemoryDatabaseBuilder(ctx, PatouneDb::class.java).allowMainThreadQueries().build()
    val settings = Settings(ctx, "test-$deviceId-${UUID.randomUUID()}")
    val store = Store(db, deviceId, author = { settings.current().memberName.ifEmpty { author } })
    val mediaDir: File = File(ctx.cacheDir, "media-$deviceId-${UUID.randomUUID()}").apply { mkdirs() }
    val sessions = SessionStore(settings, MemoryBox())
    private val api = server?.client { runBlocking { sessions.load()?.token } }
    val sync: SyncEngine? = api?.let { SyncEngine(it, store, db, sessions, settings, { mediaDir }) }
    val household: HouseholdManager? = api?.let { HouseholdManager(it, store, db, sessions, settings, sync!!) }
}
