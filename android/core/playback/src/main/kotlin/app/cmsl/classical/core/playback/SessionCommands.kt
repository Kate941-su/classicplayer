package app.cmsl.classical.core.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** アプリの MediaController からサービスへ送るコマンド。 */
internal object SessionCommands {
    /** 今の曲が終わったら一時停止する（スリープタイマー。`KEY_ENABLED` で切り替える）。 */
    val STOP_AT_END_OF_TRACK = SessionCommand("app.cmsl.classical.STOP_AT_END_OF_TRACK", Bundle.EMPTY)
    const val KEY_ENABLED = "enabled"
}
