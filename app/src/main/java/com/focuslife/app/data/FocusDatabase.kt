package com.focuslife.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AppUsageEntity::class, DailySummaryEntity::class, AppLimitEntity::class, FocusSessionEntity::class, GoalEntity::class, GoalCompletionEntity::class, SleepScheduleEntity::class, UserSettingsEntity::class, ExtensionUsageEntity::class], version = 1, exportSchema = true)
abstract class FocusDatabase : RoomDatabase() { abstract fun dao(): FocusDao }
