package com.focuslife.app.domain

import java.time.*

object UsageMath {
    data class Interval(val start: Long, val end: Long)
    fun merge(intervals: List<Interval>): List<Interval> {
        val result = mutableListOf<Interval>()
        for (i in intervals.filter { it.end > it.start }.sortedBy { it.start }) {
            val previous = result.lastOrNull()
            if (previous != null && i.start <= previous.end) result[result.lastIndex] = previous.copy(end = maxOf(previous.end, i.end))
            else result.add(i)
        }
        return result
    }
    fun duration(intervals: List<Interval>): Long = merge(intervals).sumOf { it.end - it.start }
    fun allowance(limitMinutes: Int, usedMs: Long, extensionMinutes: Int = 0): Long = ((limitMinutes + extensionMinutes) * 60_000L - usedMs).coerceAtLeast(0)
    fun warning(usedMs: Long, limitMinutes: Int, extensionMinutes: Int = 0): Int {
        val minutes = usedMs / 60_000L
        return when {
            minutes >= limitMinutes + extensionMinutes -> 60
            minutes >= limitMinutes - 10 -> 50
            minutes >= limitMinutes - 20 -> 40
            else -> 0
        }
    }
    fun dateAt(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    fun dayBounds(date: LocalDate, zone: ZoneId): Interval = Interval(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    fun elapsed(plannedMs: Long, accumulatedMs: Long, resumedElapsedRealtime: Long?, nowElapsedRealtime: Long): Long = (accumulatedMs + (resumedElapsedRealtime?.let { (nowElapsedRealtime - it).coerceAtLeast(0) } ?: 0)).coerceIn(0, plannedMs)
}
