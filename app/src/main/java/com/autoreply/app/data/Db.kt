package com.autoreply.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** लॉग में दर्ज होने वाली घटनाओं के प्रकार */
object LogType {
    const val REPLY = "REPLY"
    const val GROUP_SKIPPED = "GROUP_SKIPPED"
    const val MISSED_VOICE = "MISSED_VOICE"
    const val MISSED_VIDEO = "MISSED_VIDEO"
    const val BLOCKED_CALL = "BLOCKED_CALL"
    const val SKIPPED_SCHEDULE = "SKIPPED_SCHEDULE"
    const val SKIPPED_RATE = "SKIPPED_RATE"
    const val SKIPPED_CAP = "SKIPPED_CAP"
    const val SKIPPED_NO_ACTION = "SKIPPED_NO_ACTION"
    const val SKIPPED_EMPTY = "SKIPPED_EMPTY"
    const val FAILED = "FAILED"
}

@Entity(tableName = "logs", indices = [Index("time")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val contact: String,
    val message: String = "",
    val detail: String = "",
)

@Entity(tableName = "blocked")
data class BlockedNumber(
    @PrimaryKey val numberKey: String,
    val display: String,
    val addedAt: Long,
    val expiresAt: Long,
)

@Entity(tableName = "time_rules")
data class TimeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMin: Int,
    val endMin: Int,
    val daysMask: Int,
    val message: String,
    val enabled: Boolean = true,
)

@Entity(tableName = "contact_state")
data class ContactState(
    @PrimaryKey val contactKey: String,
    val lastReplyAt: Long = 0,
    val lastCallReplyAt: Long = 0,
    val lastHandledTs: Long = 0,
)

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("SELECT * FROM logs ORDER BY time DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs ORDER BY time DESC")
    suspend fun all(): List<LogEntry>

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("DELETE FROM logs WHERE time < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface BlockedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BlockedNumber)

    @Query("SELECT * FROM blocked ORDER BY addedAt DESC")
    fun observe(): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blocked WHERE numberKey = :key AND expiresAt > :now LIMIT 1")
    suspend fun findActive(key: String, now: Long): BlockedNumber?

    @Query("DELETE FROM blocked WHERE numberKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM blocked WHERE expiresAt < :before")
    suspend fun deleteExpiredBefore(before: Long)
}

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: TimeRule)

    @Query("SELECT * FROM time_rules ORDER BY id")
    fun observe(): Flow<List<TimeRule>>

    @Query("SELECT * FROM time_rules WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<TimeRule>

    @Query("DELETE FROM time_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact_state WHERE contactKey = :key")
    suspend fun get(key: String): ContactState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ContactState)
}

@Database(
    entities = [LogEntry::class, BlockedNumber::class, TimeRule::class, ContactState::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun logs(): LogDao
    abstract fun blocked(): BlockedDao
    abstract fun rules(): RuleDao
    abstract fun contacts(): ContactDao

    companion object {
        fun build(context: Context): AppDb =
            Room.databaseBuilder(context.applicationContext, AppDb::class.java, "autoreply.db").build()
    }
}
