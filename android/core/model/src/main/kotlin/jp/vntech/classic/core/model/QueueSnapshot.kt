package jp.vntech.classic.core.model

import kotlinx.serialization.Serializable

/** 再生キューと位置（SPEC §5「状態の復元」）。永続化するのは track.id だけ。 */
@Serializable
data class QueueSnapshot(
    val trackIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
)

@Serializable
enum class RepeatMode { Off, All, One }
