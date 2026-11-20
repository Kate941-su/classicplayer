package app.cmsl.classical.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cmsl.classical.R
import app.cmsl.classical.core.model.Track

/** 曲の行の操作（SPEC §4.5 の「…」メニュー）。 */
class TrackRowActions(
    val onPlay: (Track) -> Unit,
    val onToggleFavorite: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onAbout: (Track) -> Unit,
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
            Row {
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

@Composable
private fun TrackMenu(track: Track, isFavorite: Boolean, actions: TrackRowActions) {
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
