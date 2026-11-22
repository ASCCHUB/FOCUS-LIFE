package com.focuslife.app.data

import androidx.room.*

@Entity(tableName = "app_usage", primaryKeys = ["date", "packageName"], indices = [Index("date")])
data class AppUsageEntity(val date: String, val packageName: String, val durationMs: Long, val updatedAt: Long)
@Entity(tableName = "daily_summary", indices = [Index("date")])
data class DailySummaryEntity(@PrimaryKey val date: String, val screenMs: Long, val distractingMs: Long, val baselineMs: Long?, val updatedAt: Long)
@Entity(tableName = "app_limits")
data class AppLimitEntity(@PrimaryKey val packageName: String, val enabled: Boolean = false, val dailyLimitMinutes: Int? = null)
@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val startedAt: Long, val plannedMs: Long, val elapsedBeforeRunMs: Long = 0, val resumedAtElapsedRealtime: Long? = null, val state: String = "PAUSED", val endedAt: Long? = null, val distractions: Int = 0)
@Entity(tableName = "goals")
data class GoalEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val targetMinutes: Int, val reminderEnabled: Boolean = false, val archived: Boolean = false)
@Entity(tableName = "goal_completions", primaryKeys = ["goalId", "date"], indices = [Index("date")])
data class GoalCompletionEntity(val goalId: Long, val date: String, val completedMinutes: Int)
@Entity(tableName = "sleep_schedule")
data class SleepScheduleEntity(@PrimaryKey val id: Int = 1, val bedtime: String = "22:30", val wakeTime: String = "06:30", val windDownMinutes: Int = 30)
@Entity(tableName = "user_settings")
data class UserSettingsEntity(@PrimaryKey val id: Int = 1, val dailyLimitMinutes: Int = 60, val enforcement: String = "MODERATE", val extensionEnabled: Boolean = true, val monitoringEnabled: Boolean = true)
@Entity(tableName = "extensions")
data class ExtensionUsageEntity(@PrimaryKey val date: String, val grantedMinutes: Int = 5)
