package app.cmsl.classical.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cmsl.classical.core.artwork.ArtworkProvider

val LocalArtwork = staticCompositionLocalOf { ArtworkProvider() }

/** 生成アートワーク（SPEC §4.10）。装飾なので読み上げない。 */
@Composable
fun Artwork(composerId: String, genreId: String, modifier: Modifier = Modifier, cornerRadius: Int = 8) {
    val provider = LocalArtwork.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius.dp))) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceAtLeast(1)
        // 小さい画像はサイズをそろえてキャッシュを効かせる
        val size = if (px <= SMALL) SMALL else px
        val bitmap = remember(composerId, genreId, size) { provider.bitmap(composerId, genreId, size).asImageBitmap() }
        // 正方形の絵を、横長・縦長の枠には切り抜いて敷き詰める
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val SMALL = 160
