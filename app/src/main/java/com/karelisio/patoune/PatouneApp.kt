package com.karelisio.patoune

import android.app.Application
import com.karelisio.patoune.reminders.DailyWorker
import com.karelisio.patoune.reminders.Notif
import kotlinx.coroutines.launch

class PatouneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notif.createChannels(this)
        DailyWorker.schedule(this)
        container.scope.launch { container.reminders.rescheduleAll() }
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PatouneApp).container
