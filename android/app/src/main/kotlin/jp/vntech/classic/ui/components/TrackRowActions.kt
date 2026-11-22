package jp.vntech.classic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.vntech.classic.R
import jp.vntech.classic.core.data.DownloadState
import jp.vntech.classic.core.model.Track

/** 曲の行の操作（SPEC §4.5 の「…」メニュー）。 */
class TrackRowActions(
    val onPlay: (Track) -> Unit,
    val onToggleFavorite: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onAbout: (Track) -> Unit,
    /** ダウンロードの状態と操作（SPEC §5.1）。 */
    val downloads: DownloadState = DownloadState(),
    val onDownload: (Track) -> Unit = {},
    val onRemoveDownload: (Track) -> Unit = {},
)

@Composable
fun TrackRow(
    track: Track,
    title: String,
    supporting: String?,
    isFavorite: Boolean,
    actions: TrackRowActions,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = false,
    showTags: Boolean = false,
    isCurrent: Boolean = false,
) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button) { actions.onPlay(track) },
        colors = if (isCurrent) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = if (showArtwork) {
            { Artwork(track.composerId, track.genreId, Modifier.size(48.dp)) }
        } else {
            null
        },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = when {
            showTags && track.tags.isNotEmpty() -> {
                { Tags(track.tags) }
            }
            supporting != null -> {
                { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            else -> null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadBadge(track.id, actions.downloads)
                FavoriteButton(isFavorite) { actions.onToggleFavorite(track) }
                TrackMenu(track, isFavorite, actions)
            }
        },
    )
}

@Composable
private fun Tags(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = stringResource(
                if (isFavorite) R.string.action_unfavorite else R.string.action_favorite
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ダウンロード済み・取得中・待ちの印（SPEC §4.5・§5.1）。 */
@Composable
private fun DownloadBadge(trackId: String, downloads: DownloadState) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        downloads.isDownloaded(trackId) -> Icon(
            Icons.Filled.DownloadDone,
            contentDescription = stringResource(R.string.a11y_downloaded),
            tint = tint,
            modifier = Modifier.size(BADGE_SIZE),
        )
        downloads.active == trackId -> {
            val label = stringResource(R.string.a11y_downloading)
            CircularProgressIndicator(
                progress = { downloads.progress },
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(BADGE_SIZE)
                    .semantics { contentDescription = label },
            )
        }
        downloads.isPending(trackId) -> Icon(
            Icons.Outlined.Schedule,
            contentDescription = stringResource(R.string.a11y_downloading),
            tint = tint,
            modifier = Modifier.size(BADGE_SIZE),
        )
    }
}

private val BADGE_SIZE = 20.dp

/** 曲の「…」メニュー（曲の行とフルプレイヤー。SPEC §4.5・§4.8）。 */
@Composable
fun TrackMenu(track: Track, isFavorite: Boolean, actions: TrackRowActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite)
                    )
                },
                onClick = {
                    open = false
                    actions.onToggleFavorite(track)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_play_next)) },
                onClick = {
                    open = false
                    actions.onPlayNext(track)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_to_queue)) },
                onClick = {
                    open = false
                    actions.onAddToQueue(track)
                },
            )
            DownloadMenuItem(track, actions) { open = false }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_about_track)) },
                onClick = {
                    open = false
                    actions.onAbout(track)
                },
            )
        }
    }
}

@Composable
private fun DownloadMenuItem(track: Track, actions: TrackRowActions, close: () -> Unit) {
    val downloads = actions.downloads
    val (label, action) = when {
        downloads.isDownloaded(track.id) -> R.string.action_remove_download to actions.onRemoveDownload
        downloads.isPending(track.id) -> R.string.action_cancel_download to actions.onRemoveDownload
        else -> R.string.action_download to actions.onDownload
    }
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            action(track)
        },
    )
}
