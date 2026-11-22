package jp.vntech.classic

import android.app.Application
import jp.vntech.classic.core.playback.PlaybackEnvironment
import jp.vntech.classic.di.AppContainer

class CmslApplication : Application(), PlaybackEnvironment.Provider {
    lateinit var container: AppContainer
        private set

    override val playbackEnvironment: PlaybackEnvironment get() = container.playbackEnvironment

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        container.start()
    }
}
