package jp.vntech.classic.core.playback

import jp.vntech.classic.core.model.QueueSnapshot
import jp.vntech.classic.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPolicyTest {
    @Test
    fun `3曲続けて失敗したら止める`() {
        val p = ErrorSkipPolicy()
        assertEquals(ErrorSkipPolicy.Decision.Skip, p.onError(hasNext = true))
        assertEquals(ErrorSkipPolicy.Decision.Skip, p.onError(hasNext = true))
        assertEquals(ErrorSkipPolicy.Decision.Stop, p.onError(hasNext = true))
    }

    @Test
    fun `再生できたら数え直す`() {
        val p = ErrorSkipPolicy()
        p.onError(hasNext = true)
        p.onError(hasNext = true)
        p.onReady()
        assertEquals(ErrorSkipPolicy.Decision.Skip, p.onError(hasNext = true))
    }

    @Test
    fun `次の曲がなければ止める`() {
        assertEquals(ErrorSkipPolicy.Decision.Stop, ErrorSkipPolicy().onError(hasNext = false))
    }

    @Test
    fun `リピートはオフ・全体・1曲の順`() {
        assertEquals(RepeatMode.All, RepeatMode.Off.next())
        assertEquals(RepeatMode.One, RepeatMode.All.next())
        assertEquals(RepeatMode.Off, RepeatMode.One.next())
    }

    @Test
    fun `復元はカタログから消えた曲を外し、再生中の曲と位置を保つ`() {
        val s = QueueSnapshot(listOf("a", "gone", "b", "c"), index = 2, positionMs = 30_000, shuffle = true)
        val r = s.restorable { it != "gone" }!!
        assertEquals(listOf("a", "b", "c"), r.trackIds)
        assertEquals(1, r.index)
        assertEquals(30_000, r.positionMs)
        assertEquals(true, r.shuffle)
    }

    @Test
    fun `再生中の曲が消えていたら次に残っている曲の先頭から`() {
        val s = QueueSnapshot(listOf("a", "gone", "b"), index = 1, positionMs = 30_000)
        val r = s.restorable { it != "gone" }!!
        assertEquals(listOf("a", "b"), r.trackIds)
        assertEquals(1, r.index)
        assertEquals(0, r.positionMs)
    }

    @Test
    fun `全部消えていたら復元しない`() {
        assertNull(QueueSnapshot(listOf("x"), 0, 0).restorable { false })
    }
}
