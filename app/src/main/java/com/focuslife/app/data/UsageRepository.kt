package com.focuslife.app.data

import androidx.room.withTransaction
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.focuslife.app.domain.UsageMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UsageRepository @Inject constructor(@ApplicationContext private val context: Context, private val dao: FocusDao, private val db: FocusDatabase) {
    fun permissionGranted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    /** Reconstruct from events each time, then replace day snapshots transactionally; never accumulate polling deltas. */
    suspend fun refresh(date: LocalDate = LocalDate.now()): Boolean = withContext(Dispatchers.IO) {
        if (!permissionGranted()) return@withContext false
        val zone = ZoneId.systemDefault()
        val bounds = UsageMath.dayBounds(date, zone)
        val end = minOf(System.currentTimeMillis(), bounds.end)
        if (end <= bounds.start) return@withContext false
        val manager = context.getSystemService(UsageStatsManager::class.java)
        // Look back one day to recover apps already foreground at midnight.
        val events = manager.queryEvents(bounds.start - 86_400_000L, end)
        val event = UsageEvents.Event()
        val foreground = mutableMapOf<String, Long>()
        val intervals = mutableMapOf<String, MutableList<UsageMath.Interval>>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foreground[pkg]?.let { start -> if (event.timeStamp > start) intervals.getOrPut(pkg) { mutableListOf() }.add(UsageMath.Interval(maxOf(start, bounds.start), minOf(event.timeStamp, end))) }
                    foreground[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    foreground.remove(pkg)?.let { start ->
                        val s = maxOf(start, bounds.start)
                        val e = minOf(event.timeStamp, end)
                        if (e > s) intervals.getOrPut(pkg) { mutableListOf() }.add(UsageMath.Interval(s, e))
                    }
                }
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    foreground.forEach { (p, start) -> val s = maxOf(start, bounds.start); val e = minOf(event.timeStamp, end); if (e > s) intervals.getOrPut(p) { mutableListOf() }.add(UsageMath.Interval(s, e)) }
                    foreground.clear()
                }
            }
        }
        foreground.forEach { (p, start) -> val s = maxOf(start, bounds.start); if (end > s) intervals.getOrPut(p) { mutableListOf() }.add(UsageMath.Interval(s, end)) }
        val today = date.toString()
        val now = System.currentTimeMillis()
        val rows = intervals.map { (pkg, list) -> AppUsageEntity(today, pkg, UsageMath.duration(list), now) }.filter { it.durationMs > 0 }
        val monitored = dao.limits().first().filter { it.enabled }.map { it.packageName }.toSet()
        val total = UsageMath.duration(intervals.values.flatten())
        val distracting = rows.filter { it.packageName in monitored }.sumOf { it.durationMs }
        db.withTransactionSafe { dao.deleteUsageDate(today); dao.putUsage(rows); dao.putSummary(DailySummaryEntity(today, total, distracting, null, now)) }
        true
    }
}

private suspend fun FocusDatabase.withTransactionSafe(block: suspend () -> Unit) = withTransaction(block)
