package jp.vntech.classic.ui.player

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.vntech.classic.R
import jp.vntech.classic.core.model.RepeatMode
import jp.vntech.classic.core.model.displayName
import jp.vntech.classic.core.model.shortTitle
import jp.vntech.classic.core.playback.SleepMode
import jp.vntech.classic.core.playback.SleepTimer
import jp.vntech.classic.ui.components.AboutTrackDialog
import jp.vntech.classic.ui.components.Artwork
import jp.vntech.classic.ui.components.CmslTopBar
import jp.vntech.classic.ui.components.FavoriteButton
import jp.vntech.classic.ui.components.TrackMenu
import jp.vntech.classic.ui.containerViewModel
import jp.vntech.classic.ui.theme.LocalEnglish

/** フルプレイヤー（SPEC §4.8）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val vm = containerViewModel { PlayerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.action_share)
    Scaffold(
        topBar = {
            CmslTopBar(stringResource(R.string.player_title)) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_close_player)
                    )
                }
            }
        },
    ) { padding ->
        val now = state.now
        if (now == null) {
            Text(
                stringResource(R.string.player_empty),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            return@Scaffold
        }
        val english = LocalEnglish.current
        val downloads by vm.actions.downloads.collectAsStateWithLifecycle()
        val sleep by vm.sleep.collectAsStateWithLifecycle()
        val p = state.player
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                now.track.composerId,
                now.track.genreId,
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadius = 16,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                now.work?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                now.track.shortTitle(english),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                listOfNotNull(now.composer?.displayName(english), now.genre?.displayName(english)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SeekBar(p.positionMs, p.durationMs, vm.player::seekTo)
            Controls(p.isPlaying, vm.player::skipPrevious, vm.player::togglePlayPause, vm.player::skipNext)
            // 狭い画面ではボタンを縮めずに折り返す（タップ領域 48dp を保つ）
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ShuffleButton(p.shuffle) { vm.player.setShuffle(!p.shuffle) }
                RepeatButton(p.repeatMode, vm.player::cycleRepeatMode)
                FavoriteButton(state.isFavorite, vm::toggleFavorite)
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.action_queue)
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, now.track.articleUrl)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = shareTitle)
                }
                // 曲の行と同じ「…」メニュー（ダウンロード・この曲について など。SPEC §4.8）
                TrackMenu(
                    now.track,
                    state.isFavorite,
                    vm.actions.rowActions(downloads, onPlay = {}, onAbout = { showAbout = true }),
                )
            }
            SleepTimerButton(sleep, vm::setSleep)
            Spacer(Modifier.height(24.dp))
        }
        if (showQueue) QueueSheet(state, vm) { showQueue = false }
        if (showAbout) state.license?.let { AboutTrackDialog(now.track, it) { showAbout = false } }
    }
}

/** スリープタイマー（SPEC §5.2）。設定中は残り時間（か「曲の終わりで停止」）を出す。 */
@Composable
private fun SleepTimerButton(state: SleepUiState, onSelect: (SleepChoice) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val active = state.mode != SleepMode.Off
    val title = stringResource(R.string.sleep_title)
    val label = when (state.mode) {
        SleepMode.Off -> title
        SleepMode.EndOfTrack -> stringResource(R.string.sleep_at_end_of_track)
        is SleepMode.Until -> stringResource(
            R.string.sleep_remaining,
            formatTime(roundUpToSecond(state.remainingMs ?: 0))
        )
    }
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("sleepTimer")
                .semantics { contentDescription = if (active) "$title, $label" else title },
        ) {
            val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(if (active) Icons.Filled.Bedtime else Icons.Outlined.Bedtime, contentDescription = null, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label, color = color)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choices = listOf(SleepChoice.Off) + SleepTimer.CHOICES.map { SleepChoice.Minutes(it) } +
                SleepChoice.EndOfTrack
            choices.forEach { choice ->
                val selected = when (choice) {
                    SleepChoice.Off -> state.mode == SleepMode.Off
                    SleepChoice.EndOfTrack -> state.mode == SleepMode.EndOfTrack
                    // 時間は残りが変わるので、選んだ分を印にしない
                    is SleepChoice.Minutes -> false
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            when (choice) {
                                SleepChoice.Off -> stringResource(R.string.sleep_off)
                                SleepChoice.EndOfTrack -> stringResource(R.string.sleep_end_of_track)
                                is SleepChoice.Minutes ->
                                    pluralStringResource(R.plurals.sleep_minutes, choice.minutes, choice.minutes)
                            }
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

/** 残り 0.4 秒を「0:00」にしない（設定した直後に 15:00 と出す）。 */
private fun roundUpToSecond(ms: Long): Long = (ms + SECOND_MS - 1) / SECOND_MS * SECOND_MS

private const val SECOND_MS = 1000L

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    // ドラッグ中だけ手元の値を使う（離したらシークする）
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val value = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val elapsed = formatTime((value * duration).toLong())
    val remaining = formatTime(durationMs - (value * duration).toLong())
    val seekLabel = stringResource(R.string.player_seek)
    val seekState = stringResource(R.string.player_seek_state, elapsed, formatTime(durationMs))
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            modifier = Modifier.semantics {
                contentDescription = seekLabel
                stateDescription = seekState
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(elapsed, style = MaterialTheme.typography.bodySmall)
            Text(if (durationMs > 0) "-$remaining" else "--:--", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Controls(isPlaying: Boolean, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                Modifier.size(36.dp)
            )
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.action_next), Modifier.size(36.dp))
        }
    }
}

@Composable
private fun ShuffleButton(enabled: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (enabled) R.string.toggle_on else R.string.toggle_off)
    IconButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = state }) {
        Icon(
            if (enabled) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
            contentDescription = stringResource(R.string.action_shuffle),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RepeatButton(mode: RepeatMode, onClick: () -> Unit) {
    val state = stringResource(
        when (mode) {
            RepeatMode.Off -> R.string.repeat_off
            RepeatMode.All -> R.string.repeat_all
            RepeatMode.One -> R.string.repeat_one
        },
    )
    IconButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = state }) {
        Icon(
            when (mode) {
                RepeatMode.Off -> Icons.Filled.Repeat
                RepeatMode.All -> Icons.Filled.RepeatOn
                RepeatMode.One -> Icons.Filled.RepeatOneOn
            },
            contentDescription = stringResource(R.string.action_repeat),
            tint = if (mode == RepeatMode.Off) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** キュー: 並べ替え（上下）と削除、タップでその曲へ（SPEC §5）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val english = LocalEnglish.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.action_queue),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            itemsIndexed(state.queue) { i, t ->
                val current = i == state.player.currentIndex
                ListItem(
                    colors = if (current) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    headlineContent = {
                        Text(
                            t?.shortTitle(english) ?: stringResource(R.string.track_unavailable),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = t?.let { { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    modifier = Modifier.semantics(mergeDescendants = false) {},
                    leadingContent = {
                        IconButton(onClick = { vm.player.skipToQueueItem(i) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play_this))
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.player.moveQueueItem(i, i - 1) }, enabled = i > 0) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.action_move_up)
                                )
                            }
                            IconButton(
                                onClick = { vm.player.moveQueueItem(i, i + 1) },
                                enabled = i < state.queue.lastIndex
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.action_move_down)
                                )
                            }
                            IconButton(onClick = { vm.player.removeQueueItem(i) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_remove_from_queue)
                                )
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
