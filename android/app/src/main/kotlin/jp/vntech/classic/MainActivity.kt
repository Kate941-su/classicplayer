package jp.vntech.classic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.vntech.classic.core.playback.MediaControllerPlayer
import jp.vntech.classic.ui.CmslRoot

class MainActivity : ComponentActivity() {
    private val container get() = (application as CmslApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CmslRoot(container) }
    }

    override fun onStart() {
        super.onStart()
        (container.player as? MediaControllerPlayer)?.connect()
    }

    override fun onStop() {
        (container.player as? MediaControllerPlayer)?.disconnect()
        super.onStop()
    }
}
