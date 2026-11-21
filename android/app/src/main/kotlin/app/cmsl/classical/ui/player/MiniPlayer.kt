package app.cmsl.classical.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cmsl.classical.R
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.core.model.shortTitle
import app.cmsl.classical.ui.components.Artwork
import app.cmsl.classical.ui.theme.LocalEnglish

/** タブバーの上に出すミニプレイヤー（再生中のキューがあるときだけ。SPEC §4.1）。 */
@Composable
fun MiniPlayer(state: PlayerUiState, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val now = state.now ?: return
    val english = LocalEnglish.current
    val openLabel = stringResource(R.string.action_open_player)
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("miniPlayer")
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { onClick(label = openLabel) { onOpen().let { true } } },
    ) {
        Column {
            val p = state.player
            if (p.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(now.track.composerId, now.track.genreId, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(now.track.shortTitle(english), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(now.composer?.displayName(english), now.work?.title).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (p.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (p.isPlaying) R.string.action_pause else R.string.action_play
                        ),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.action_next))
                }
            }
        }
    }
}
