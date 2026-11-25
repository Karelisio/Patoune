package com.karelisio.patoune

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.karelisio.patoune.reminders.DailyWorker
import com.karelisio.patoune.reminders.Notif
import kotlinx.coroutines.launch

class PatouneApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notif.createChannels(this)
        DailyWorker.schedule(this)
        container.scope.launch { container.reminders.rescheduleAll(); container.widgets.refresh() }
    }
}

val android.content.Context.container: AppContainer get() = (applicationContext as PatouneApp).container
