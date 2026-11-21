package app.cmsl.classical.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** お気に入りと再生履歴（SPEC §6）。保存するのは track.id と日時だけ。 */
@Database(entities = [FavoriteEntity::class, HistoryEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao

    abstract fun history(): HistoryDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val trackId: String,
    val playedAt: Long,
)

@Dao
interface FavoriteDao {
    /** 追加日の新しい順。カタログから消えた曲も残す（画面でグレー表示する）。 */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC, trackId")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT trackId FROM favorites")
    fun observeIds(): Flow<List<String>>

    /** すでにあれば追加日を変えない。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun remove(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun contains(trackId: String): Boolean

    @Transaction
    suspend fun toggle(trackId: String, now: Long): Boolean {
        val added = !contains(trackId)
        if (added) add(FavoriteEntity(trackId, now)) else remove(trackId)
        return added
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC, trackId LIMIT :limit")
    fun observeRecent(limit: Int = MAX_HISTORY): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query(
        "DELETE FROM history WHERE trackId NOT IN " +
            "(SELECT trackId FROM history ORDER BY playedAt DESC, trackId LIMIT :keep)"
    )
    suspend fun trim(keep: Int)

    /** 同じ曲は 1 件にまとめて日時を更新し、最大 100 件に保つ。 */
    @Transaction
    suspend fun record(trackId: String, playedAt: Long) {
        upsert(HistoryEntity(trackId, playedAt))
        trim(MAX_HISTORY)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
