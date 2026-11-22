package jp.vntech.classic.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import jp.vntech.classic.core.model.QueueSnapshot
import jp.vntech.classic.core.model.RepeatMode

/** サービス側で、track.id だけの MediaItem を再生できる MediaItem にする。 */
internal fun PlayableTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(audioUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(composer)
            .setAlbumTitle(workTitle)
            .setGenre(genre)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { artworkData?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .build(),
    )
    .build()

/** コントローラ側から送る MediaItem（ID だけ。URI はサービスが onAddMediaItems で付ける）。 */
internal fun mediaItemOf(trackId: String): MediaItem = MediaItem.Builder().setMediaId(trackId).build()

internal fun Player.queueIds(): List<String> = List(mediaItemCount) { getMediaItemAt(it).mediaId }

internal fun Player.snapshot(): QueueSnapshot = QueueSnapshot(
    trackIds = queueIds(),
    index = currentMediaItemIndex,
    positionMs = currentPosition.coerceAtLeast(0),
    shuffle = shuffleModeEnabled,
    repeatMode = repeatModeOf(repeatMode),
)

internal fun repeatModeOf(playerRepeatMode: Int): RepeatMode = when (playerRepeatMode) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}

internal fun RepeatMode.toPlayer(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.One -> Player.REPEAT_MODE_ONE
}
