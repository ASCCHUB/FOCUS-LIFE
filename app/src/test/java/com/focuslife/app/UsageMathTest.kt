package com.focuslife.app

import com.focuslife.app.domain.UsageMath
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UsageMathTest {
    @Test fun overlappingIntervalsAreNotDoubleCounted() { assertEquals(2000L, UsageMath.duration(listOf(UsageMath.Interval(0, 1500), UsageMath.Interval(1000, 2000)))) }
    @Test fun allowanceClampsAtZero() { assertEquals(0L, UsageMath.allowance(60, 70 * 60_000L)) }
    @Test fun extensionAddsFiveMinutes() { assertEquals(5 * 60_000L, UsageMath.allowance(60, 60 * 60_000L, 5)) }
    @Test fun warningThresholds() { assertEquals(0, UsageMath.warning(39 * 60_000L, 60)); assertEquals(40, UsageMath.warning(40 * 60_000L, 60)); assertEquals(50, UsageMath.warning(50 * 60_000L, 60)); assertEquals(60, UsageMath.warning(60 * 60_000L, 60)) }
    @Test fun daylightSavingDayIsNotAlways24Hours() { val bounds = UsageMath.dayBounds(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York")); assertEquals(23 * 3_600_000L, bounds.end - bounds.start) }
    @Test fun timezoneChangesDateMapping() { val instant = Instant.parse("2026-11-21T23:30:00Z").toEpochMilli(); assertNotEquals(UsageMath.dateAt(instant, ZoneId.of("UTC")), UsageMath.dateAt(instant, ZoneId.of("Asia/Riyadh"))) }
    @Test fun timerElapsedClamped() { assertEquals(10_000L, UsageMath.elapsed(25_000, 4_000, 100L, 6_100L)); assertEquals(25_000L, UsageMath.elapsed(25_000, 20_000, 0L, 90_000L)) }
}
