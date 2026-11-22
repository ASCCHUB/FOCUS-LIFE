package com.focuslife.app.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Works out whether the foreground app is showing a short-video feed and when the user swipes
 * to a new video. Uses known view IDs first, then a scroll heuristic: a full-screen vertical
 * list showing exactly one item at a time.
 */
class ShortVideoDetector(private val screenHeight: () -> Int) {
    data class Result(val inFeed: Boolean, val entered: Boolean, val newVideo: Boolean)

    private var foreground: String? = null
    private var feed = false
    private var enteredReported = false
    private var lastIdCheck = 0L
    private var lastHeuristicAt = 0L
    private var lastIndex: Int? = null
    private var lastCountAt = 0L

    fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        reset()
    }

    fun reset() {
        feed = false; enteredReported = false; lastIndex = null; lastHeuristicAt = 0L; lastIdCheck = 0L
    }

    fun isInFeed(pkg: String): Boolean {
        val rule = ShortVideoRules.apps[pkg] ?: return false
        return pkg == foreground && (rule.wholeAppIsFeed || feed)
    }

    fun onEvent(event: AccessibilityEvent, root: () -> AccessibilityNodeInfo?, now: Long): Result {
        val pkg = event.packageName?.toString()
        val rule = pkg?.let { ShortVideoRules.apps[it] }
        if (rule == null || pkg != foreground) return Result(false, false, false)
        val wasInFeed = isInFeed(pkg)
        var newVideo = false

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> when (classifyScroll(event)) {
                Scroll.FEED_SWIPE_NEW -> {
                    feed = true; lastHeuristicAt = now
                    if (now - lastCountAt >= DEBOUNCE_MS) { newVideo = true; lastCountAt = now }
                }
                Scroll.FEED_SETTLED -> { feed = true; lastHeuristicAt = now }
                Scroll.NORMAL_LIST -> if (rule.feedViewIds.isEmpty() || now - lastHeuristicAt > 3_000L) { feed = false; lastIndex = null }
                Scroll.IGNORE -> Unit
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (rule.feedViewIds.isNotEmpty() && now - lastIdCheck >= ID_CHECK_MS) {
                    lastIdCheck = now
                    val match = matchesIds(root(), rule.feedViewIds)
                    if (match) feed = true
                    else if (now - lastHeuristicAt > 3_000L) feed = false
                } else if (rule.feedViewIds.isEmpty() && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    feed = false; lastIndex = null // new screen in a heuristic-only app
                }
            }
        }

        val inFeed = isInFeed(pkg)
        if (!inFeed) enteredReported = false
        val entered = inFeed && !enteredReported
        if (entered) enteredReported = true
        return Result(inFeed, entered, newVideo && wasInFeed && !entered)
    }

    private enum class Scroll { FEED_SWIPE_NEW, FEED_SETTLED, NORMAL_LIST, IGNORE }

    private fun classifyScroll(event: AccessibilityEvent): Scroll {
        val dx = event.scrollDeltaX
        val dy = event.scrollDeltaY
        if (dx != 0 && abs(dx) > abs(dy)) return Scroll.IGNORE // horizontal carousel
        val source = event.source ?: return Scroll.IGNORE
        val bounds = Rect().also { source.getBoundsInScreen(it) }
        val height = screenHeight()
        if (height <= 0) return Scroll.IGNORE
        val fullScreen = bounds.height() >= height * 0.6
        val from = event.fromIndex
        val to = event.toIndex
        if (from >= 0 && to >= 0) {
            if (to != from) return if (fullScreen && to == from + 1) Scroll.IGNORE /* mid-swipe */ else Scroll.NORMAL_LIST
            if (!fullScreen) return Scroll.NORMAL_LIST
            val previous = lastIndex
            lastIndex = from
            return if (previous != null && previous != from) Scroll.FEED_SWIPE_NEW else Scroll.FEED_SETTLED
        }
        // No item indices: fall back to a large vertical jump of a full-screen view.
        return if (fullScreen && abs(dy) >= height * 0.4) Scroll.FEED_SWIPE_NEW else Scroll.IGNORE
    }

    private fun matchesIds(root: AccessibilityNodeInfo?, ids: List<String>): Boolean {
        if (root == null) return false
        return ids.any { id -> root.findAccessibilityNodeInfosByViewId(id)?.any { it.isVisibleToUser } == true }
    }

    companion object {
        const val DEBOUNCE_MS = 700L
        const val ID_CHECK_MS = 600L
    }
}
