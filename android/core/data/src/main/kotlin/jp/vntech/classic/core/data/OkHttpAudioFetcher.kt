package jp.vntech.classic.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 音源の mp3 を OkHttp で取る（SPEC §5.1）。取り消されたら通信も止める。
 * 大きいファイルなので全体のタイムアウトはなくし、読み込みが止まったときだけ失敗にする。https から http へのリダイレクトは追わない。
 */
class OkHttpAudioFetcher(
    client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AudioFetcher {
    private val client = client.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()

    override suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit) = coroutineScope {
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
        // 取り消されたら、読み込み中の通信を切る（ブロックしている読み込みが例外で抜ける）
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(io) {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw DownloadException("HTTP ${response.code}")
                    val body = response.body
                    val total = body.contentLength()
                    var written = 0L
                    target.sink().buffer().use { sink ->
                        val source = body.source()
                        while (true) {
                            ensureActive()
                            val read = source.read(sink.buffer, CHUNK)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            written += read
                            if (total > 0) onProgress(written.toFloat() / total)
                        }
                    }
                    if (total > 0 && written != total) throw DownloadException("途中で切れた（$written / $total）")
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    private companion object {
        const val READ_TIMEOUT_SECONDS = 30L
        const val CHUNK = 64L * 1024
        const val USER_AGENT = "CMSLClassical-Android"
    }
}
