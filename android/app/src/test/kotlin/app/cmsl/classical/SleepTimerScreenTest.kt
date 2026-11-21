package app.cmsl.classical

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.cmsl.classical.core.playback.PlayQueue
import app.cmsl.classical.core.playback.SleepMode
import app.cmsl.classical.ui.CmslRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** フルプレイヤーのスリープタイマー（PLAN 1.13、SPEC §5.2）: 時間・この曲の終わりまで・オフ。 */
@RunWith(RobolectricTestRunner::class)
class SleepTimerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun choose(text: String) {
        compose.onNodeWithTag("sleepTimer").performScrollTo().performClick()
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `時間・この曲の終わりまで・オフを選ぶ`() {
        val player = FakePlayerController()
        val container = testContainer(context, tmp.root, player)
        player.play(PlayQueue(listOf("474143449")))
        compose.setContent { CmslRoot(container) }
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.onNodeWithText(context.getString(R.string.sleep_title)).assertExists()

        choose(context.resources.getQuantityString(R.plurals.sleep_minutes, 15, 15))
        assertTrue(container.sleepTimer.mode.value is SleepMode.Until)
        compose.onNodeWithText(context.getString(R.string.sleep_remaining, "15:00")).assertExists()

        choose(context.getString(R.string.sleep_end_of_track))
        assertEquals(SleepMode.EndOfTrack, container.sleepTimer.mode.value)
        assertTrue(player.stopAtEndOfTrack)
        compose.onNodeWithText(context.getString(R.string.sleep_at_end_of_track)).assertExists()

        choose(context.getString(R.string.sleep_off))
        assertEquals(SleepMode.Off, container.sleepTimer.mode.value)
        assertFalse(player.stopAtEndOfTrack)
        compose.onNodeWithText(context.getString(R.string.sleep_title)).assertExists()
        container.sleepTimer.cancel()
    }
}
