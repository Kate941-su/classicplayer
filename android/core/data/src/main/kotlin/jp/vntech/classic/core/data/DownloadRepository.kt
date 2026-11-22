package jp.vntech.classic.core.data

import jp.vntech.classic.core.model.CatalogValidator
import jp.vntech.classic.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 1 曲分の取得（テストで差し替える）。[target]（一時ファイル）に書く。失敗は例外（IOException など）。 */
fun interface AudioFetcher {
    suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit)
}

data class DownloadState(
    /** ダウンロード済みの曲とファイルの大きさ（バイト）。 */
    val downloaded: Map<String, Long> = emptyMap(),
    /** 取得を待っている曲（順番）。 */
    val queued: List<String> = emptyList(),
    /** 取得中の曲と進み具合（0〜1）。 */
    val active: String? = null,
    val progress: Float = 0f,
    /** 「Wi-Fi のときだけ」で、モバイル通信なので待っている。 */
    val waitingForNetwork: Boolean = false,
    /** 直前に失敗した曲（画面に知らせる）。 */
    val lastFailure: String? = null,
) {
    val totalBytes: Long get() = downloaded.values.sum()

    fun isDownloaded(trackId: String): Boolean = trackId in downloaded

    fun isPending(trackId: String): Boolean = active == trackId || trackId in queued
}

/**
 * ダウンロード（オフライン再生。SPEC §5.1）。ファイルは `dir/{track.id}.mp3`、取得中は `dir/.{track.id}.part`。
 * - 1 曲ずつ順番に取得する（音源のサーバーに負担をかけない。SPEC §1.2）。取得先はカタログの `audioUrl` だけ
 * - [networkAllowed] が false の間は待つ（「Wi-Fi のときだけ」でモバイル通信のとき）。取得中に false になったら、
 *   その曲を中断して、許されたら最初から取り直す
 * - 失敗した曲は未ダウンロードに戻す（自動では再試行しない）
 */
class DownloadRepository(
    private val dir: File,
    private val fetcher: AudioFetcher,
    private val scope: CoroutineScope,
    private val networkAllowed: Flow<Boolean> = flowOf(true),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private val urls = ConcurrentHashMap<String, String>()
    private var worker: Job? = null
    private var activeJob: Job? = null

    private val mutableState = MutableStateFlow(DownloadState(downloaded = scan()))
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    private val mutableChanges = MutableSharedFlow<String>(extraBufferCapacity = CHANGE_BUFFER)

    /** ファイルができた・消えた曲（再生側が URL を選び直すため）。 */
    val changes: SharedFlow<String> = mutableChanges.asSharedFlow()

    /** ダウンロード済みならファイル。 */
    fun file(trackId: String): File? = fileOf(trackId).takeIf { state.value.isDownloaded(trackId) && it.isFile }

    /** 取得を待つ列に入れる（済み・待ち・取得中の曲と、許していない URL の曲は飛ばす）。 */
    fun enqueue(tracks: List<Track>) = synchronized(lock) {
        val s = mutableState.value
        val add = tracks
            .filter { !s.isDownloaded(it.id) && !s.isPending(it.id) && CatalogValidator.isAllowedAudioUrl(it.audioUrl) }
            .distinctBy { it.id }
        add.forEach { urls[it.id] = it.audioUrl }
        mutableState.value = s.copy(queued = s.queued + add.map { it.id }, lastFailure = null)
        startWorker()
    }

    /** 待ち・取得中なら取り消し、済みならファイルを消す。 */
    fun remove(trackId: String) {
        synchronized(lock) {
            val s = mutableState.value
            if (s.active == trackId) activeJob?.cancel()
            mutableState.value = s.copy(queued = s.queued - trackId)
        }
        deleteFile(trackId)
    }

    fun removeAll() {
        val ids = synchronized(lock) {
            activeJob?.cancel()
            mutableState.value = mutableState.value.copy(queued = emptyList())
            mutableState.value.downloaded.keys.toList()
        }
        ids.forEach(::deleteFile)
    }

    /** カタログを更新したとき: 新しい録音に置き換わった曲とカタログから消えた曲のファイルを消す（SPEC §5.1）。 */
    fun prune(isInCatalog: (String) -> Boolean) {
        val ids = synchronized(lock) {
            val s = mutableState.value
            if (s.active != null && !isInCatalog(s.active)) activeJob?.cancel()
            mutableState.value = s.copy(queued = s.queued.filter(isInCatalog))
            s.downloaded.keys.filterNot(isInCatalog)
        }
        ids.forEach(::deleteFile)
    }

    /** 取得し終わるのを待つ（テスト用）。 */
    suspend fun awaitIdle() {
        state.first { it.active == null && it.queued.isEmpty() }
        synchronized(lock) { worker }?.join()
    }

    // ---- 内部

    private fun fileOf(trackId: String) = File(dir, "$trackId.mp3")

    private fun tempOf(trackId: String) = File(dir, ".$trackId.part")

    /** 起動時: 置いてあるファイルを数える。取得途中の一時ファイルは消す。 */
    private fun scan(): Map<String, Long> {
        val files = dir.listFiles().orEmpty()
        files.filter { it.name.endsWith(PART) }.forEach { it.delete() }
        return files.filter { it.isFile && it.name.endsWith(MP3) && !it.name.startsWith(".") }
            .associate { it.name.removeSuffix(MP3) to it.length() }
    }

    private fun deleteFile(trackId: String) {
        val removed = synchronized(lock) {
            val s = mutableState.value
            if (!s.isDownloaded(trackId)) return@synchronized false
            mutableState.value = s.copy(downloaded = s.downloaded - trackId)
            true
        }
        if (!removed) return
        fileOf(trackId).delete()
        mutableChanges.tryEmit(trackId)
    }

    /** lock の中で呼ぶ。 */
    private fun startWorker() {
        if (worker != null || mutableState.value.queued.isEmpty()) return
        // 先に worker に入れてから始める（すぐに実行される Dispatcher でも、終わりの worker = null を上書きしない）
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while (true) {
                val job = synchronized(lock) {
                    val s = mutableState.value
                    val id = s.queued.firstOrNull()
                    if (id == null) {
                        worker = null
                        null
                    } else {
                        mutableState.value = s.copy(queued = s.queued.drop(1), active = id, progress = 0f)
                        launch(start = CoroutineStart.LAZY) { fetch(id) }.also { activeJob = it }
                    }
                } ?: break
                job.join()
                synchronized(lock) {
                    activeJob = null
                    mutableState.value = mutableState.value.copy(
                        active = null,
                        progress = 0f,
                        waitingForNetwork = false,
                    )
                }
            }
        }
        worker = job
        job.start()
    }

    private suspend fun fetch(trackId: String) {
        val url = urls[trackId] ?: return
        val temp = tempOf(trackId)
        try {
            while (true) {
                if (!networkAllowed.first()) {
                    setWaiting(true)
                    networkAllowed.first { it }
                    setWaiting(false)
                }
                val outcome = fetchOnce(trackId, url, temp)
                if (outcome == Outcome.Lost) continue
                // 取り消し（削除）なら、ここで止める
                currentCoroutineContext().ensureActive()
                if (outcome != Outcome.Done || !place(trackId, temp)) fail(trackId)
                return
            }
        } finally {
            temp.delete()
        }
    }

    private suspend fun fetchOnce(trackId: String, url: String, temp: File): Outcome = coroutineScope {
        val download = async(io) {
            runCatching {
                dir.mkdirs()
                temp.delete()
                fetcher.fetch(url, temp) { setProgress(trackId, it) }
            }
        }
        // 通信が許されなくなったら中断する（Flow が終わったら、もう変わらないとみなす）
        val lost = async { networkAllowed.firstOrNull { !it } ?: awaitCancellation() }
        select {
            download.onAwait { result ->
                lost.cancel()
                result.fold({ Outcome.Done }, { Outcome.Failed(it) })
            }
            lost.onAwait {
                download.cancel()
                Outcome.Lost
            }
        }
    }

    /** 一時ファイルを置き場所へ移す（中身が空なら失敗）。 */
    private fun place(trackId: String, temp: File): Boolean {
        val size = temp.length()
        if (size <= 0) return false
        val target = fileOf(trackId)
        target.delete()
        if (!temp.renameTo(target)) return false
        synchronized(lock) {
            val s = mutableState.value
            mutableState.value = s.copy(downloaded = s.downloaded + (trackId to size))
        }
        mutableChanges.tryEmit(trackId)
        return true
    }

    private fun fail(trackId: String) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(lastFailure = trackId)
    }

    private fun setWaiting(value: Boolean) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(waitingForNetwork = value)
    }

    /** 1% 刻みで知らせる（画面の更新を減らす）。 */
    private fun setProgress(trackId: String, value: Float) = synchronized(lock) {
        val s = mutableState.value
        if (s.active == trackId && (value - s.progress >= PROGRESS_STEP || value >= 1f)) {
            mutableState.value = s.copy(progress = value.coerceIn(0f, 1f))
        }
    }

    private sealed interface Outcome {
        data object Done : Outcome

        data object Lost : Outcome

        data class Failed(val error: Throwable) : Outcome
    }

    private companion object {
        const val MP3 = ".mp3"
        const val PART = ".part"
        const val PROGRESS_STEP = 0.01f
        const val CHANGE_BUFFER = 64
    }
}

/** 取得に失敗した（HTTP のエラーなど）。 */
class DownloadException(message: String) : IOException(message)
