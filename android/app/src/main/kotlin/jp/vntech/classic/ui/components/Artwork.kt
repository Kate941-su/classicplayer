package jp.vntech.classic.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import jp.vntech.classic.artwork.Portraits
import jp.vntech.classic.core.artwork.ArtworkProvider

val LocalArtwork = staticCompositionLocalOf { ArtworkProvider() }

/** null なら肖像画を使わない（プレビューなど）。 */
val LocalPortraits = staticCompositionLocalOf<Portraits?> { null }

/**
 * 曲・作曲家のアートワーク（SPEC §4.10）: 作曲家の肖像画があればそれ、なければ（取得中・失敗も）生成アートワーク。
 * 装飾なので読み上げない。
 */
@Composable
fun Artwork(composerId: String, genreId: String, modifier: Modifier = Modifier, cornerRadius: Int = 8) {
    val portraits = LocalPortraits.current
    val portrait by produceState<Bitmap?>(portraits?.cached(composerId), portraits, composerId) {
        value = portraits?.bitmap(composerId)
    }
    val bitmap = portrait
    if (bitmap == null) {
        GeneratedArtwork(composerId, genreId, modifier, cornerRadius)
    } else {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)),
        )
    }
}

/** 生成アートワークだけ（ジャンルのカードなど、作曲家の絵でないところ）。 */
@Composable
fun GeneratedArtwork(composerId: String, genreId: String, modifier: Modifier = Modifier, cornerRadius: Int = 8) {
    val provider = LocalArtwork.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius.dp))) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val h = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        // 小さい正方形はサイズをそろえてキャッシュを効かせる。横長・縦長の枠はその比率で描く
        // （イラストが切れないように。模様とイラストの置き方は ArtworkRenderer）
        val (width, height) = when {
            w == h && w <= SMALL -> SMALL to SMALL
            else -> w to h
        }
        val bitmap = remember(composerId, genreId, width, height) {
            provider.bitmap(composerId, genreId, width, height).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val SMALL = 160
