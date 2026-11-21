package app.cmsl.classical.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * プレイヤー標準の HTTP キャッシュ（最大 200MB、SPEC §5）。
 * ダウンロード済みの曲（file:// の URI。SPEC §5.1）はキャッシュを通さずにファイルから読む。
 * SimpleCache は同じディレクトリに 1 つしか作れないので、プロセスで共有する。
 */
@OptIn(UnstableApi::class)
internal object MediaCache {
    private const val MAX_BYTES = 200L * 1024 * 1024
    private const val USER_AGENT = "CMSLClassical-Android"

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = CacheDataSource.Factory()
            .setCache(cache(context.applicationContext))
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent(USER_AGENT)
                    .setAllowCrossProtocolRedirects(false),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // file:// は FileDataSource、http(s) は上のキャッシュ付きの HTTP で読む
        return DefaultDataSource.Factory(context.applicationContext, http)
    }
}
