package com.focuslife.app

import com.focuslife.app.blocking.BlockPolicy
import com.focuslife.app.blocking.Decision
import com.focuslife.app.detection.BrowserUrlMatcher
import com.focuslife.app.domain.BlockMode
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.ScheduleMath
import com.focuslife.app.domain.ScheduleWindow
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek

class BlockPolicyTest {
    private val yt = BlockerConfig.YOUTUBE
    private val x = BlockerConfig.X
    private val t0 = 1_000_000_000L

    @Test fun curiousAllowsTwoThenBlocksThird() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.CURIOUS, curiousCount = 2)
        assertEquals(Decision.Allow, p.onEnterFeed(yt, cfg, t0, false))          // video 1
        assertEquals(Decision.Allow, p.onNewVideo(yt, cfg, t0 + 10_000, false))   // video 2
        assertTrue(p.onNewVideo(yt, cfg, t0 + 20_000, false) is Decision.Block)   // video 3
        assertTrue(p.onEnterFeed(yt, cfg, t0 + 60_000, false) is Decision.Block) // still in cooldown
        assertEquals(Decision.Allow, p.onEnterFeed(yt, cfg, t0 + 20_000 + 5 * 60_000 + 1, false)) // cooldown over
    }

    @Test fun blockAllBlocksOnEnter() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.BLOCK_ALL)
        assertTrue(p.onEnterFeed(BlockerConfig.INSTAGRAM, cfg, t0, false) is Decision.Block)
    }

    @Test fun pauseAllowsUnlessScheduled() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.BLOCK_ALL, pauseUntil = t0 + 15 * 60_000)
        assertEquals(Decision.Allow, p.onEnterFeed(yt, cfg, t0, false))
        assertTrue(p.onEnterFeed(yt, cfg, t0, true) is Decision.Block)
        assertTrue(p.onEnterFeed(yt, cfg, t0 + 16 * 60_000, false) is Decision.Block) // pause expired
    }

    @Test fun disabledAppIsIgnored() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.BLOCK_ALL, enabledApps = setOf(yt))
        assertEquals(Decision.Allow, p.onEnterFeed(BlockerConfig.INSTAGRAM, cfg, t0, false))
    }

    @Test fun xWarnsAtTwoAndStopsAtFive() {
        val p = BlockPolicy(); val cfg = BlockerConfig(xWarnAfter = 2, xStopAfter = 5, xCooldownMin = 10)
        assertEquals(Decision.Allow, p.onEnterFeed(x, cfg, t0, false))              // 1
        assertTrue(p.onNewVideo(x, cfg, t0 + 1_000, false) is Decision.Warn)         // 2
        assertEquals(Decision.Allow, p.onNewVideo(x, cfg, t0 + 2_000, false))       // 3
        assertEquals(Decision.Allow, p.onNewVideo(x, cfg, t0 + 3_000, false))       // 4
        assertTrue(p.onNewVideo(x, cfg, t0 + 4_000, false) is Decision.HardStop)     // 5
        assertTrue(p.onEnterFeed(x, cfg, t0 + 60_000, false) is Decision.HardStop)   // cooldown
        assertEquals(Decision.Allow, p.onEnterFeed(x, cfg, t0 + 4_000 + 10 * 60_000 + 1, false))
    }

    @Test fun timedBlocksAfterWatchTime() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.TIMED, timedWatchMin = 5, timedBlockMin = 5)
        var now = t0
        repeat(59) { now += 5_000; assertEquals(Decision.Allow, p.onWatchTime(yt, cfg, 5_000, now, false)) }
        now += 5_000
        assertTrue(p.onWatchTime(yt, cfg, 5_000, now, false) is Decision.Block) // 300 s reached
        assertTrue(p.onEnterFeed(yt, cfg, now + 60_000, false) is Decision.Block)
    }

    @Test fun curiousCountResetsAfterLongGap() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.CURIOUS, curiousCount = 2)
        p.onEnterFeed(yt, cfg, t0, false)
        p.onNewVideo(yt, cfg, t0 + 1_000, false)
        val later = t0 + BlockPolicy.STALE_MS + 10_000
        assertEquals(Decision.Allow, p.onEnterFeed(yt, cfg, later, false))
        assertEquals(Decision.Allow, p.onNewVideo(yt, cfg, later + 1_000, false))
    }
}

class BrowserUrlMatcherTest {
    @Test fun blocksShortVideoPages() {
        assertNotNull(BrowserUrlMatcher.match("m.facebook.com/reel/123456"))
        assertNotNull(BrowserUrlMatcher.match("https://www.facebook.com/watch/?v=1"))
        assertNotNull(BrowserUrlMatcher.match("facebook.com/somepage/videos/123"))
        assertNotNull(BrowserUrlMatcher.match("instagram.com/reels/abc"))
        assertNotNull(BrowserUrlMatcher.match("m.youtube.com/shorts/xyz"))
        assertNotNull(BrowserUrlMatcher.match("tiktok.com/@user/video/1"))
        assertNotNull(BrowserUrlMatcher.match("x.com/user/status/123/video/1"))
    }

    @Test fun allowsNormalPages() {
        assertNull(BrowserUrlMatcher.match("facebook.com"))
        assertNull(BrowserUrlMatcher.match("m.facebook.com/home.php"))
        assertNull(BrowserUrlMatcher.match("facebook.com/watchparty"))
        assertNull(BrowserUrlMatcher.match("youtube.com/watch?v=abc"))
        assertNull(BrowserUrlMatcher.match("netflix.com/browse"))
        assertNull(BrowserUrlMatcher.match("x.com/user/status/123"))
        assertNull(BrowserUrlMatcher.match("facebook reels")) // search text
        assertNull(BrowserUrlMatcher.match(null))
    }

    @Test fun blocksWholeDomainsIncludingSubdomains() {
        val blocked = setOf("https://www.Reddit.com/")
        assertEquals("reddit.com", BrowserUrlMatcher.match("old.reddit.com/r/all", blocked))
        assertEquals("reddit.com", BrowserUrlMatcher.match("reddit.com", blocked))
        assertNull(BrowserUrlMatcher.match("notreddit.com", blocked))
    }
}

class ScheduleMathTest {
    @Test fun sameDayWindow() {
        val w = listOf(ScheduleWindow(9 * 60, 17 * 60, ScheduleMath.dayBit(DayOfWeek.MONDAY)))
        assertTrue(ScheduleMath.isActive(w, DayOfWeek.MONDAY, 10 * 60))
        assertFalse(ScheduleMath.isActive(w, DayOfWeek.MONDAY, 17 * 60))
        assertFalse(ScheduleMath.isActive(w, DayOfWeek.TUESDAY, 10 * 60))
    }

    @Test fun overnightWindowUsesPreviousDay() {
        val w = listOf(ScheduleWindow(22 * 60 + 30, 6 * 60 + 30, ScheduleMath.dayBit(DayOfWeek.FRIDAY)))
        assertTrue(ScheduleMath.isActive(w, DayOfWeek.FRIDAY, 23 * 60))
        assertTrue(ScheduleMath.isActive(w, DayOfWeek.SATURDAY, 5 * 60))
        assertFalse(ScheduleMath.isActive(w, DayOfWeek.SATURDAY, 23 * 60))
        assertFalse(ScheduleMath.isActive(w, DayOfWeek.FRIDAY, 5 * 60))
    }

    @Test fun disabledWindowIgnored() {
        assertFalse(ScheduleMath.isActive(listOf(ScheduleWindow(0, 1439, ScheduleMath.ALL_DAYS, enabled = false)), DayOfWeek.MONDAY, 600))
    }

    @Test fun parseAndFormat() {
        assertEquals(22 * 60 + 30, ScheduleMath.parse("22:30"))
        assertEquals(6 * 60 + 5, ScheduleMath.parse("6:05"))
        assertNull(ScheduleMath.parse("25:00"))
        assertEquals("06:05", ScheduleMath.format(6 * 60 + 5))
    }
}

class ShortLimitWarningTest {
    @Test fun oneMinuteLimitHasNoEarlyWarnings() {
        assertEquals(0, com.focuslife.app.domain.UsageMath.warning(0L, 1))
        assertEquals(60, com.focuslife.app.domain.UsageMath.warning(60_000L, 1))
    }
    @Test fun strictXClosesOnSecondVideo() {
        val p = BlockPolicy(); val cfg = BlockerConfig(xStopAfter = 2, xWarnAfter = 1)
        assertEquals(Decision.Allow, p.onEnterFeed(BlockerConfig.X, cfg, 1_000L, false))
        assertTrue(p.onNewVideo(BlockerConfig.X, cfg, 2_000L, false) is Decision.HardStop)
    }
}

class ReentryTest {
    @Test fun xBreakIsEnforcedWhenComingBack() {
        val p = BlockPolicy(); val cfg = BlockerConfig(xStopAfter = 2, xWarnAfter = 1, xCooldownMin = 10)
        p.onEnterFeed(BlockerConfig.X, cfg, 1_000L, false)
        assertTrue(p.onNewVideo(BlockerConfig.X, cfg, 2_000L, false) is Decision.HardStop)
        // User reopens X a minute later: both a fresh entry and a silent re-check must block.
        assertTrue(p.onEnterFeed(BlockerConfig.X, cfg, 62_000L, false) is Decision.HardStop)
        assertTrue(p.enforce(BlockerConfig.X, cfg, 63_000L, false) is Decision.HardStop)
        // After the break: first video allowed again.
        assertEquals(Decision.Allow, p.enforce(BlockerConfig.X, cfg, 2_000L + 10 * 60_000L + 1, false))
    }

    @Test fun curiousBreakIsEnforcedAndBlockAllAlwaysBlocks() {
        val p = BlockPolicy(); val cfg = BlockerConfig(mode = BlockMode.CURIOUS, curiousCount = 1)
        p.onEnterFeed(BlockerConfig.YOUTUBE, cfg, 1_000L, false)
        assertTrue(p.onNewVideo(BlockerConfig.YOUTUBE, cfg, 2_000L, false) is Decision.Block)
        assertTrue(p.enforce(BlockerConfig.YOUTUBE, cfg, 30_000L, false) is Decision.Block)
        assertTrue(BlockPolicy().enforce(BlockerConfig.INSTAGRAM, BlockerConfig(mode = BlockMode.BLOCK_ALL), 1L, false) is Decision.Block)
    }
}
