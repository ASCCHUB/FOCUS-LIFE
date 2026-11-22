package com.focuslife.app.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

data class ScheduleWindow(val startMinute: Int, val endMinute: Int, val daysMask: Int, val enabled: Boolean = true)

object ScheduleMath {
    const val ALL_DAYS = 127
    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isActive(windows: List<ScheduleWindow>, day: DayOfWeek, minute: Int): Boolean = windows.any { w ->
        val today = (w.daysMask and dayBit(day)) != 0
        val yesterday = (w.daysMask and dayBit(day.minus(1))) != 0
        when {
            !w.enabled || w.startMinute == w.endMinute -> false
            w.startMinute < w.endMinute -> today && minute >= w.startMinute && minute < w.endMinute
            else -> (today && minute >= w.startMinute) || (yesterday && minute < w.endMinute) // crosses midnight
        }
    }

    fun isActive(windows: List<ScheduleWindow>, now: LocalDateTime): Boolean = isActive(windows, now.dayOfWeek, now.hour * 60 + now.minute)

    fun format(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    fun parse(text: String): Int? = Regex("""([01]?\d|2[0-3]):([0-5]\d)""").matchEntire(text.trim())?.let {
        it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()
    }

    fun daysLabel(mask: Int): String {
        if (mask == ALL_DAYS) return "Every day"
        if (mask == 0b0011111) return "Mon–Fri"
        return DayOfWeek.values().filter { (mask and dayBit(it)) != 0 }.joinToString(" ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }
}
