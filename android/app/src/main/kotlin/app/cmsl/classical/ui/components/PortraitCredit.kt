package app.cmsl.classical.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cmsl.classical.R
import app.cmsl.classical.core.model.Portrait

/** 「画像のクレジット」画面（SPEC §4.9）を開く。null なら導線を出さない。 */
val LocalOpenImageCredits = staticCompositionLocalOf<(() -> Unit)?> { null }

/** 肖像画のクレジット（作者・ライセンス）。タップで Commons のファイルページを開く（SPEC §4.4）。 */
@Composable
fun PortraitCredit(portrait: Portrait, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val artist = portrait.artist ?: stringResource(R.string.portrait_artist_unknown)
    val open = stringResource(R.string.portrait_open_source)
    Text(
        stringResource(R.string.portrait_credit, artist, portrait.license),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            // タップ領域は 48dp 以上（SPEC §4.11）
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClickLabel = open, role = Role.Button) { uri.openUri(portrait.sourceUrl) }
            .wrapContentHeight(),
    )
}
