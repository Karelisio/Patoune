package com.karelisio.patoune

import android.app.Application

class PatouneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PatouneApp).container
