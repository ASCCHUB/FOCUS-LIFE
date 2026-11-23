package com.focuslife.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppUsageEntity::class, DailySummaryEntity::class, AppLimitEntity::class, FocusSessionEntity::class, GoalEntity::class, GoalCompletionEntity::class, SleepScheduleEntity::class, UserSettingsEntity::class, ExtensionUsageEntity::class, ShortVideoStatEntity::class, BlockScheduleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun dao(): FocusDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `short_video_stats` (`date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `videos` INTEGER NOT NULL, `blocked` INTEGER NOT NULL, `feedMs` INTEGER NOT NULL, PRIMARY KEY(`date`, `packageName`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `block_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
            }
        }

        @Volatile private var instance: FocusDatabase? = null

        /** One shared instance for the app UI, worker and accessibility service, so changes show up everywhere. */
        fun get(context: Context): FocusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FocusDatabase::class.java, "focuslife.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
