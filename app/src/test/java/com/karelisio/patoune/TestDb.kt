package com.karelisio.patoune

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Store

/** Base Room en mémoire + Store pour un « appareil » de test. */
class TestDevice(val deviceId: String, var author: String = "") {
    val db: PatouneDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatouneDb::class.java)
        .allowMainThreadQueries().build()
    val store = Store(db, deviceId, author = { author })
}
