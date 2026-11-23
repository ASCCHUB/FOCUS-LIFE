package com.focuslife.app.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Works out whether the foreground app is showing a short-video feed and when the user swipes
 * to a new video.
 *  1. Screen check: a visible element with a known feed ID (or an ID containing a hint word that
 *     fills most of the screen) means "in the feed".
 *  2. Scroll heuristic: a full-screen vertical list showing exactly one item at a time.
 * Once in the feed, any vertical move to another item counts as a new video.
 */
class ShortVideoDetector(private val screenHeight: () -> Int) {
    data class Result(val inFeed: Boolean, val entered: Boolean, val newVideo: Boolean, val debug: String? = null)

    private var foreground: String? = null
    private var idFeed = false
    private var scrollFeed = false
    private var enteredReported = false
    private var lastIdCheck = 0L
    private var lastIndex: Int? = null
    private var lastCountAt = 0L

    /** IDs seen on the last screen check (for the Detection check screen). */
    var lastVisibleIds: List<String> = emptyList()
        private set

    fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        reset()
    }

    fun reset() {
        idFeed = false; scrollFeed = false; enteredReported = false; lastIndex = null; lastIdCheck = 0L
    }

    fun isInFeed(pkg: String): Boolean {
        val rule = ShortVideoRules.apps[pkg] ?: return false
        return pkg == foreground && (rule.wholeAppIsFeed || idFeed || scrollFeed)
    }

    fun onEvent(event: AccessibilityEvent, root: () -> AccessibilityNodeInfo?, now: Long, collectDebug: Boolean = false): Result {
        val pkg = event.packageName?.toString()
        val rule = pkg?.let { ShortVideoRules.apps[it] }
        if (rule == null || pkg != foreground) return Result(false, false, false)
        val wasInFeed = isInFeed(pkg)
        var moved = false
        var debug: String? = null

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val info = scrollInfo(event)
                if (collectDebug) debug = info.describe()
                if (!info.horizontal) {
                    if (idFeed || rule.wholeAppIsFeed) {
                        moved = if (info.from >= 0) {
                            val previous = lastIndex
                            lastIndex = info.from
                            previous != null && previous != info.from
                        } else abs(info.dy) >= info.screen * 0.25
                    } else if (rule.useScrollHeuristic) {
                        when {
                            info.from >= 0 && info.to >= 0 && info.to == info.from && info.fullScreen -> {
                                val previous = lastIndex
                                lastIndex = info.from
                                scrollFeed = true
                                moved = previous != null && previous != info.from
                            }
                            info.from >= 0 && info.to == info.from + 1 && info.fullScreen -> Unit // mid-swipe
                            info.from < 0 && info.fullScreen && abs(info.dy) >= info.screen * 0.4 -> { scrollFeed = true; moved = true }
                            info.from >= 0 -> { scrollFeed = false; lastIndex = null } // normal multi-item list
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val hasRules = rule.feedViewIds.isNotEmpty() || rule.idHints.isNotEmpty()
                val stateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                if (hasRules && (stateChange || now - lastIdCheck >= ID_CHECK_MS)) {
                    lastIdCheck = now
                    val found = scan(root(), rule, collectDebug)
                    if (found != idFeed) lastIndex = null
                    idFeed = found
                    if (collectDebug) debug = "screen: feed=${if (found) "YES" else "no"} ids=${lastVisibleIds.joinToString(",")}"
                }
                if (stateChange && !rule.wholeAppIsFeed) { scrollFeed = false; if (!idFeed) lastIndex = null }
            }
        }

        var newVideo = false
        if (moved && now - lastCountAt >= DEBOUNCE_MS) { newVideo = true; lastCountAt = now }
        return finish(pkg, wasInFeed, newVideo, debug)
    }

    /**
     * Re-checks the current screen without waiting for an event from the app. Needed because an
     * app restored from the background can reopen straight into its video feed silently.
     */
    fun recheck(pkg: String, root: () -> AccessibilityNodeInfo?, now: Long, collectDebug: Boolean = false): Result {
        val rule = ShortVideoRules.apps[pkg]
        if (rule == null || pkg != foreground) return Result(false, false, false)
        val wasInFeed = isInFeed(pkg)
        var debug: String? = null
        if (rule.feedViewIds.isNotEmpty() || rule.idHints.isNotEmpty()) {
            lastIdCheck = now
            val found = scan(root(), rule, collectDebug)
            if (found != idFeed) lastIndex = null
            idFeed = found
            if (collectDebug) debug = "recheck: feed=${if (found) "YES" else "no"} ids=${lastVisibleIds.joinToString(",")}"
        }
        return finish(pkg, wasInFeed, false, debug)
    }

    private fun finish(pkg: String, wasInFeed: Boolean, newVideo: Boolean, debug: String?): Result {
        val inFeed = isInFeed(pkg)
        if (!inFeed) enteredReported = false
        val entered = inFeed && !enteredReported
        if (entered) enteredReported = true
        return Result(inFeed, entered, newVideo && wasInFeed && !entered, debug)
    }

    private data class ScrollInfo(
        val from: Int, val to: Int, val count: Int, val dx: Int, val dy: Int,
        val screen: Int, val heightPct: Int, val className: String, val id: String,
    ) {
        val horizontal get() = dx != 0 && abs(dx) > abs(dy)
        val fullScreen get() = heightPct >= 60
        fun describe() = "scroll from=$from to=$to count=$count dy=$dy dx=$dx h=$heightPct% cls=$className id=$id"
    }

    private fun scrollInfo(event: AccessibilityEvent): ScrollInfo {
        val screen = screenHeight().coerceAtLeast(1)
        val source = event.source
        val bounds = Rect()
        source?.getBoundsInScreen(bounds)
        return ScrollInfo(
            from = event.fromIndex, to = event.toIndex, count = event.itemCount,
            dx = event.scrollDeltaX, dy = event.scrollDeltaY,
            screen = screen, heightPct = bounds.height() * 100 / screen,
            className = event.className?.toString()?.substringAfterLast('.') ?: "?",
            id = source?.viewIdResourceName?.substringAfter(":id/") ?: "-",
        )
    }

    /** Walks the visible screen (bounded) looking for a feed element. */
    private fun scan(root: AccessibilityNodeInfo?, rule: AppRule, collect: Boolean): Boolean {
        if (root == null) return false
        // Measure against the app window: a real reels/shorts player fills (almost) all of it,
        // while previews such as YouTube's Shorts shelf on the home page don't.
        val window = Rect().also { root.getBoundsInScreen(it) }
        val windowH = (if (window.height() > 0) window.height() else screenHeight()).coerceAtLeast(1)
        val windowW = window.width().coerceAtLeast(1)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val seen = LinkedHashSet<String>()
        val rect = Rect()
        var visited = 0
        var found = false
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && node.isVisibleToUser) {
                if (collect && seen.size < 30) seen += id.substringAfter(":id/")
                if (!found && (id in rule.feedViewIds || rule.idHints.any { id.contains(it, ignoreCase = true) })) {
                    node.getBoundsInScreen(rect)
                    if (rect.height() >= windowH * FULL_SCREEN && rect.width() >= windowW * 0.8) found = true
                }
            }
            if (found && !collect) break
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        if (collect) lastVisibleIds = seen.toList()
        return found
    }

    companion object {
        const val DEBOUNCE_MS = 700L
        const val ID_CHECK_MS = 600L
        const val MAX_NODES = 400
        /** Share of the window height a feed element must cover. */
        const val FULL_SCREEN = 0.7
    }
}
