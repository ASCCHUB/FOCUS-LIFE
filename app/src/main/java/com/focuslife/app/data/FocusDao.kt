package com.focuslife.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface FocusDao {
    @Query("SELECT * FROM app_usage WHERE date = :date") fun usage(date: String): Flow<List<AppUsageEntity>>
    @Query("SELECT * FROM app_usage WHERE date BETWEEN :start AND :end ORDER BY date") fun usageRange(start: String, end: String): Flow<List<AppUsageEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putUsage(rows: List<AppUsageEntity>)
    @Query("DELETE FROM app_usage WHERE date = :date") suspend fun deleteUsageDate(date: String)
    @Query("SELECT * FROM daily_summary WHERE date = :date LIMIT 1") fun summary(date: String): Flow<DailySummaryEntity?>
    @Query("SELECT * FROM daily_summary WHERE date BETWEEN :start AND :end ORDER BY date") fun summaries(start: String, end: String): Flow<List<DailySummaryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSummary(row: DailySummaryEntity)
    @Query("SELECT * FROM app_limits") fun limits(): Flow<List<AppLimitEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLimit(row: AppLimitEntity)
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC") fun sessions(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE state IN ('RUNNING','PAUSED') ORDER BY startedAt DESC LIMIT 1") suspend fun activeSession(): FocusSessionEntity?
    @Insert suspend fun addSession(row: FocusSessionEntity): Long
    @Update suspend fun updateSession(row: FocusSessionEntity)
    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY id") fun goals(): Flow<List<GoalEntity>>
    @Insert suspend fun addGoal(row: GoalEntity): Long
    @Update suspend fun updateGoal(row: GoalEntity)
    @Query("SELECT * FROM goal_completions WHERE date = :date") fun completions(date: String): Flow<List<GoalCompletionEntity>>
    @Query("SELECT * FROM goal_completions WHERE date BETWEEN :start AND :end") fun completionRange(start: String, end: String): Flow<List<GoalCompletionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCompletion(row: GoalCompletionEntity)
    @Query("SELECT * FROM sleep_schedule LIMIT 1") fun sleep(): Flow<SleepScheduleEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSleep(row: SleepScheduleEntity)
    @Query("SELECT * FROM user_settings LIMIT 1") fun settings(): Flow<UserSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettings(row: UserSettingsEntity)
    @Query("SELECT * FROM extensions WHERE date = :date LIMIT 1") suspend fun extension(date: String): ExtensionUsageEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun grantExtension(row: ExtensionUsageEntity): Long
    @Query("DELETE FROM app_usage") suspend fun clearUsage()
    @Query("DELETE FROM daily_summary") suspend fun clearSummaries()
    @Query("DELETE FROM app_limits") suspend fun clearLimits()
    @Query("DELETE FROM focus_sessions") suspend fun clearSessions()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM goal_completions") suspend fun clearCompletions()
    @Query("DELETE FROM sleep_schedule") suspend fun clearSleep()
    @Query("DELETE FROM user_settings") suspend fun clearSettings()
    @Query("DELETE FROM extensions") suspend fun clearExtensions()
    @Transaction suspend fun clearAll() { clearUsage(); clearSummaries(); clearLimits(); clearSessions(); clearGoals(); clearCompletions(); clearSleep(); clearSettings(); clearExtensions() }
}
