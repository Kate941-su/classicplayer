package app.cmsl.classical.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cmsl.classical.core.data.DownloadState
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.components.TrackRowActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer が未設定") }

/** 画面（NavBackStackEntry）ごとの ViewModel を、AppContainer から作る。 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

/** 曲の行の「…」メニューの操作（どの画面でも同じ）。 */
class TrackActions(private val container: AppContainer, private val scope: CoroutineScope) {
    val favoriteIds: StateFlow<Set<String>> = container.favoriteIds

    fun toggleFavorite(track: Track) {
        scope.launch { container.database.favorites().toggle(track.id, container.nowMillis()) }
    }

    fun playNext(track: Track) = container.player.playNext(track.id)

    fun addToQueue(track: Track) = container.player.addToQueue(track.id)

    /** ダウンロードの状態（SPEC §5.1）。 */
    val downloads: StateFlow<DownloadState> = container.downloads.state

    fun download(tracks: List<Track>) = container.downloads.enqueue(tracks)

    /** 済みならファイルを消し、待ち・取得中なら取り消す。 */
    fun removeDownload(track: Track) = container.downloads.remove(track.id)

    /** 曲の行に渡す操作。 */
    fun rowActions(downloads: DownloadState, onPlay: (Track) -> Unit, onAbout: (Track) -> Unit) = TrackRowActions(
        onPlay = onPlay,
        onToggleFavorite = ::toggleFavorite,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        onAbout = onAbout,
        downloads = downloads,
        onDownload = { download(listOf(it)) },
        onRemoveDownload = ::removeDownload,
    )
}

internal fun <T> Flow<T>.stateInWhileSubscribed(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

private const val STOP_TIMEOUT_MS = 5_000L
