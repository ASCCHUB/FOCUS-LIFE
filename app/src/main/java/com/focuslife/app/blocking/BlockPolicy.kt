package com.focuslife.app.blocking

import com.focuslife.app.domain.BlockMode
import com.focuslife.app.domain.BlockerConfig

sealed interface Decision {
    data object Allow : Decision
    /** Leave the reels feed and show the "take back your time" screen. */
    data class Block(val reason: String) : Decision
    /** Soft warning; the user may continue. */
    data class Warn(val message: String) : Decision
    /** Stop playback and go to the home screen. */
    data class HardStop(val reason: String) : Decision
}

/**
 * Decides what happens when the user enters a short-video feed, swipes to a new video,
 * or keeps watching. Pure Kotlin (no Android types) so every rule is unit-tested.
 */
class BlockPolicy {
    private class AppState {
        var videos = 0
        var lastVideoAt = 0L
        var cooldownUntil = 0L
        var watchedMs = 0L
    }

    private val states = HashMap<String, AppState>()
    private fun state(pkg: String) = states.getOrPut(pkg) { AppState() }

    fun cooldownUntil(pkg: String): Long = states[pkg]?.cooldownUntil ?: 0L

    private fun applies(pkg: String, cfg: BlockerConfig, now: Long, scheduleActive: Boolean) =
        pkg in cfg.enabledApps && (scheduleActive || !cfg.isPaused(now))

    private fun usesXRule(pkg: String, cfg: BlockerConfig, scheduleActive: Boolean) =
        pkg == BlockerConfig.X && cfg.xEnabled && !scheduleActive

    private fun refresh(s: AppState, now: Long) {
        if (s.cooldownUntil in 1..now) { s.cooldownUntil = 0; s.videos = 0; s.watchedMs = 0 }
        if (s.videos > 0 && now - s.lastVideoAt > STALE_MS) s.videos = 0
    }

    private fun minutesLeft(until: Long, now: Long) = ((until - now + 59_999L) / 60_000L).coerceAtLeast(1)

    private fun hardBlockReason(scheduleActive: Boolean) =
        if (scheduleActive) "Reels and shorts are blocked during your focus time." else "Block All is on: no reels or shorts."

    fun onEnterFeed(pkg: String, cfg: BlockerConfig, now: Long, scheduleActive: Boolean): Decision {
        if (!applies(pkg, cfg, now, scheduleActive)) return Decision.Allow
        val s = state(pkg)
        refresh(s, now)
        if (usesXRule(pkg, cfg, scheduleActive)) {
            if (s.cooldownUntil > now) return Decision.HardStop("X videos are on a break for ${minutesLeft(s.cooldownUntil, now)} more min.")
            if (s.videos == 0) { s.videos = 1; s.lastVideoAt = now }
            return Decision.Allow
        }
        if (scheduleActive || cfg.mode == BlockMode.BLOCK_ALL) return Decision.Block(hardBlockReason(scheduleActive))
        if (s.cooldownUntil > now) return Decision.Block("Break time. Reels are back in ${minutesLeft(s.cooldownUntil, now)} min.")
        if (cfg.mode == BlockMode.CURIOUS && s.videos == 0) { s.videos = 1; s.lastVideoAt = now }
        return Decision.Allow
    }

    fun onNewVideo(pkg: String, cfg: BlockerConfig, now: Long, scheduleActive: Boolean): Decision {
        if (!applies(pkg, cfg, now, scheduleActive)) return Decision.Allow
        val s = state(pkg)
        refresh(s, now)
        if (usesXRule(pkg, cfg, scheduleActive)) {
            if (s.cooldownUntil > now) return Decision.HardStop("X videos are on a break for ${minutesLeft(s.cooldownUntil, now)} more min.")
            s.videos = s.videos.coerceAtLeast(1) + 1
            s.lastVideoAt = now
            if (s.videos >= cfg.xStopAfter) {
                s.cooldownUntil = now + cfg.xCooldownMin * 60_000L
                s.videos = 0
                return Decision.HardStop("You've watched ${cfg.xStopAfter} videos. X videos are paused for ${cfg.xCooldownMin} min.")
            }
            if (s.videos == cfg.xWarnAfter) return Decision.Warn("You've watched ${s.videos} videos on X. ${cfg.xStopAfter - s.videos} more and X will close.")
            return Decision.Allow
        }
        if (scheduleActive || cfg.mode == BlockMode.BLOCK_ALL) return Decision.Block(hardBlockReason(scheduleActive))
        if (s.cooldownUntil > now) return Decision.Block("Break time. Reels are back in ${minutesLeft(s.cooldownUntil, now)} min.")
        if (cfg.mode == BlockMode.CURIOUS) {
            s.videos = s.videos.coerceAtLeast(1) + 1
            s.lastVideoAt = now
            if (s.videos > cfg.curiousCount) {
                s.cooldownUntil = now + cfg.curiousCooldownMin * 60_000L
                s.videos = 0
                return Decision.Block("That's your ${cfg.curiousCount} for now. Take a ${cfg.curiousCooldownMin}-minute break.")
            }
        }
        return Decision.Allow
    }

    /**
     * Called every couple of seconds while the user is in a feed: re-applies an active break or
     * Block All even if the app sent no new events (e.g. the user came back from the background).
     */
    fun enforce(pkg: String, cfg: BlockerConfig, now: Long, scheduleActive: Boolean): Decision {
        if (!applies(pkg, cfg, now, scheduleActive)) return Decision.Allow
        val s = state(pkg)
        refresh(s, now)
        if (usesXRule(pkg, cfg, scheduleActive)) {
            return if (s.cooldownUntil > now) Decision.HardStop("X videos are on a break for ${minutesLeft(s.cooldownUntil, now)} more min.") else Decision.Allow
        }
        if (scheduleActive || cfg.mode == BlockMode.BLOCK_ALL) return Decision.Block(hardBlockReason(scheduleActive))
        if (s.cooldownUntil > now) return Decision.Block("Break time. Reels are back in ${minutesLeft(s.cooldownUntil, now)} min.")
        return Decision.Allow
    }

    /** Called every few seconds while the user stays in a reels feed. */
    fun onWatchTime(pkg: String, cfg: BlockerConfig, deltaMs: Long, now: Long, scheduleActive: Boolean): Decision {
        if (!applies(pkg, cfg, now, scheduleActive)) return Decision.Allow
        if (scheduleActive || usesXRule(pkg, cfg, scheduleActive) || cfg.mode != BlockMode.TIMED) return Decision.Allow
        val s = state(pkg)
        refresh(s, now)
        if (s.cooldownUntil > now) return Decision.Block("Break time. Reels are back in ${minutesLeft(s.cooldownUntil, now)} min.")
        s.watchedMs += deltaMs
        if (s.watchedMs >= cfg.timedWatchMin * 60_000L) {
            s.cooldownUntil = now + cfg.timedBlockMin * 60_000L
            s.watchedMs = 0
            return Decision.Block("Your ${cfg.timedWatchMin} minutes of reels are up. Break for ${cfg.timedBlockMin} min.")
        }
        return Decision.Allow
    }

    companion object { const val STALE_MS = 30 * 60_000L }
}
