package dev.indla.island.data.rules

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.indla.island.model.AppRule
import dev.indla.island.model.Priority
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val show: Boolean,
    @ColumnInfo(defaultValue = "0") val muted: Boolean = false,
    val priority: String,
    val autoExpand: Boolean,
    val hideContentOnLock: Boolean,
    val accentArgb: Long?,
) {
    fun toModel() = AppRule(
        packageName = packageName,
        show = show,
        muted = muted,
        priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.NORMAL),
        autoExpand = autoExpand,
        hideContentOnLock = hideContentOnLock,
        accentArgb = accentArgb,
    )

    companion object {
        fun from(rule: AppRule) = AppRuleEntity(
            packageName = rule.packageName,
            show = rule.show,
            muted = rule.muted,
            priority = rule.priority.name,
            autoExpand = rule.autoExpand,
            hideContentOnLock = rule.hideContentOnLock,
            accentArgb = rule.accentArgb,
        )
    }
}

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppRuleEntity?

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Upsert
    suspend fun upsertAll(rules: List<AppRuleEntity>)
}

@Database(entities = [AppRuleEntity::class], version = 2, exportSchema = false)
abstract class AppRuleDatabase : RoomDatabase() {
    abstract fun rules(): AppRuleDao

    companion object {
        /** v2: "muted" (silences live activities too), separate from the notification switch. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_rules ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
