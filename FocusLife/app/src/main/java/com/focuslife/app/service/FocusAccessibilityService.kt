package com.focuslife.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.focuslife.app.MainActivity
import com.focuslife.app.blocking.BlockOverlay
import com.focuslife.app.blocking.BlockPolicy
import com.focuslife.app.blocking.Decision
import com.focuslife.app.data.ExtensionUsageEntity
import com.focuslife.app.data.FocusDatabase
import com.focuslife.app.data.Preferences
import com.focuslife.app.data.UsageRepository
import com.focuslife.app.detection.BrowserUrlMatcher
import com.focuslife.app.detection.ShortVideoDetector
import com.focuslife.app.detection.ShortVideoRules
import com.focuslife.app.domain.BlockerConfig
import com.focuslife.app.domain.LimitAction
import com.focuslife.app.domain.ScheduleMath
import com.focuslife.app.domain.ScheduleWindow
import com.focuslife.app.domain.UsageMath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reads only: the foreground app's package name, the view IDs of known short-video screens,
 * scroll events, and the browser address bar. Nothing is stored except counters.
 */
class FocusAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var running = false
            private set
        @Volatile private var instance: java.lang.ref.WeakReference<FocusAccessibilityService>? = null

        /** Detection check screen: when on, the service keeps the last lines of what it sees. */
        @Volatile var debugEnabled = false
        private val debugLines = ArrayDeque<String>()
        fun debugSnapshot(): List<String> = synchronized(debugLines) { debugLines.toList() }
        fun clearDebug() = synchronized(debugLines) { debugLines.clear() }
        internal fun log(line: String) {
            if (!debugEnabled) return
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            synchronized(debugLines) {
                debugLines.addLast("$time $line")
                while (debugLines.size > 60) debugLines.removeFirst()
            }
        }

        /** Payment mode: the service switches itself off so banking apps don't see an active accessibility service. */
        fun turnOffForPayment(): Boolean {
            val service = instance?.get() ?: return false
            service.disableSelf()
            return true
        }
        private const val TICK_MS = 5_000L
        private val TRANSIENT = setOf("com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard", "com.touchtype.swiftkey")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var db: FocusDatabase
    private lateinit var prefs: Preferences
    private lateinit var overlay: BlockOverlay
    private val detector = ShortVideoDetector { resources.displayMetrics.heightPixels }
    private val policy = BlockPolicy()

    @Volatile private var config = BlockerConfig()
    @Volatile private var schedules: List<ScheduleWindow> = emptyList()
    private var foreground: String? = null
    private var lastActionAt = 0L
    private var lastTickAt = 0L
    private val pendingFeedMs = HashMap<String, Long>()
    private var lastBrowserCheck = 0L
    private var lastBlockedUrl = ""
    private var lastBlockedUrlAt = 0L
    private var lastLimitCheck = 0L
    private var lastLimitPackage: String? = null
    private var lastInterruptionAt = 0L

    private val ticker = object : Runnable {
        override fun run() { onTick(); handler.postDelayed(this, TICK_MS) }
    }

    override fun onServiceConnected() {
        running = true
        instance = java.lang.ref.WeakReference(this)
        db = FocusDatabase.get(applicationContext)
        prefs = Preferences(applicationContext)
        overlay = BlockOverlay(this)
        scope.launch { prefs.blocker.collect { config = it } }
        scope.launch { db.dao().schedules().collect { list -> schedules = list.map { ScheduleWindow(it.startMinute, it.endMinute, it.daysMask, it.enabled) } } }
        handler.postDelayed(ticker, TICK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!::overlay.isInitialized) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg !in TRANSIENT && !pkg.contains("inputmethod")) onForeground(pkg)
        if (pkg != foreground) return

        val urlBar = ShortVideoRules.browsers[pkg]
        if (urlBar != null) { checkBrowser(e, pkg, urlBar, now); return }
        if (ShortVideoRules.apps.containsKey(pkg)) handleShortVideo(e, pkg, now)
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) checkDailyLimit(pkg)
    }

    private fun onForeground(pkg: String) {
        if (pkg == foreground) return
        flushFeedTime()
        foreground = pkg
        detector.onForeground(pkg)
        lastTickAt = 0L
        if (overlay.isShowing && !overlay.isFresh && !ShortVideoRules.apps.containsKey(pkg)) overlay.dismiss()
    }

    private fun scheduleActive() = ScheduleMath.isActive(schedules, LocalDateTime.now())
    private fun inCooldownAfterAction(now: Long) = now - lastActionAt < 1_500L

    // ---------- Short-video apps ----------

    private fun handleShortVideo(e: AccessibilityEvent, pkg: String, now: Long) {
        val result = detector.onEvent(e, { rootInActiveWindow }, now, debugEnabled)
        if (debugEnabled && result.debug != null) log("${pkg.substringAfterLast('.')} ${result.debug} → feed=${result.inFeed}${if (result.entered) " ENTERED" else ""}${if (result.newVideo) " NEW-VIDEO" else ""}")
        if (!result.inFeed || inCooldownAfterAction(now)) return
        val cfg = config
        val scheduled = scheduleActive()
        if (result.entered && act(pkg, policy.onEnterFeed(pkg, cfg, now, scheduled))) return
        if (result.newVideo) {
            record(pkg, videos = 1)
            act(pkg, policy.onNewVideo(pkg, cfg, now, scheduled))
        }
    }

    private fun onTick() {
        if (!::overlay.isInitialized) return
        val pkg = foreground ?: return
        val now = System.currentTimeMillis()
        // We only receive events from watched apps, so check whether the user has left this one.
        if (rootInActiveWindow?.packageName?.toString() != pkg) {
            flushFeedTime(); foreground = null; detector.onForeground(""); lastTickAt = 0L
            return
        }
        if (!detector.isInFeed(pkg)) { lastTickAt = 0L; return }
        if (lastTickAt == 0L) { lastTickAt = now; return }
        val delta = (now - lastTickAt).coerceIn(0L, TICK_MS * 2)
        lastTickAt = now
        pendingFeedMs[pkg] = (pendingFeedMs[pkg] ?: 0L) + delta
        if ((pendingFeedMs[pkg] ?: 0L) >= 30_000L) flushFeedTime()
        if (!inCooldownAfterAction(now)) act(pkg, policy.onWatchTime(pkg, config, delta, now, scheduleActive()))
    }

    /** Returns true if the user was moved out of the feed. */
    private fun act(pkg: String, decision: Decision): Boolean {
        if (decision != Decision.Allow) log("${pkg.substringAfterLast('.')} decision: $decision")
        return actInternal(pkg, decision)
    }

    private fun actInternal(pkg: String, decision: Decision): Boolean = when (decision) {
        Decision.Allow -> false
        is Decision.Block -> { record(pkg, blocked = 1); leaveFeed(pkg, toHome = false); showBlocked(decision.reason); true }
        is Decision.HardStop -> { record(pkg, blocked = 1); leaveFeed(pkg, toHome = true); showBlocked(decision.reason); true }
        is Decision.Warn -> {
            overlay.show("Heads up", decision.message, listOf(
                BlockOverlay.Action("Continue"),
                BlockOverlay.Action("Leave", primary = true) { leaveFeed(pkg, toHome = true) },
            ), autoDismissMs = 5_000L, emoji = "⚠️")
            false
        }
    }

    private fun leaveFeed(pkg: String, toHome: Boolean) {
        lastActionAt = System.currentTimeMillis()
        val wholeApp = ShortVideoRules.apps[pkg]?.wholeAppIsFeed == true
        detector.reset()
        if (toHome || wholeApp) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 250L)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            // If BACK didn't leave the feed (e.g. reels is a tab), go home instead.
            handler.postDelayed({ if (foreground == pkg && detector.isInFeed(pkg)) performGlobalAction(GLOBAL_ACTION_HOME) }, 1_200L)
        }
    }

    private fun showBlocked(reason: String) {
        handler.postDelayed({
            overlay.show("Take back your time", reason, listOf(
                BlockOverlay.Action("OK", primary = true),
                BlockOverlay.Action("Start a focus session") { openApp() },
            ), autoDismissMs = 4_000L)
        }, 300L)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- Browsers ----------

    private fun checkBrowser(e: AccessibilityEvent, pkg: String, urlBarId: String, now: Long) {
        val cfg = config
        if (!cfg.browserEnabled) return
        if (cfg.isPaused(now) && !scheduleActive()) return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (now - lastBrowserCheck < 800L || inCooldownAfterAction(now)) return
        lastBrowserCheck = now
        val node = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(urlBarId)?.firstOrNull() ?: return
        if (node.isFocused) return // user is typing
        val url = node.text?.toString() ?: return
        val what = BrowserUrlMatcher.match(url, cfg.blockedDomains)
        log("${pkg.substringAfterLast('.')} url=$url → ${what ?: "allowed"}")
        if (what == null) return
        if (url == lastBlockedUrl && now - lastBlockedUrlAt < 4_000L) return
        lastBlockedUrl = url; lastBlockedUrlAt = now; lastActionAt = now
        record(pkg, blocked = 1)
        performGlobalAction(GLOBAL_ACTION_BACK)
        showBlocked("$what blocked in your browser.")
    }

    // ---------- Daily limit ----------

    private fun checkDailyLimit(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastLimitCheck < 1_500L && pkg == lastLimitPackage) return
        lastLimitCheck = now; lastLimitPackage = pkg
        scope.launch {
            val dao = db.dao()
            val settings = withContext(Dispatchers.IO) { dao.settings().first() }
            if (settings?.monitoringEnabled == false) return@launch
            val limited = withContext(Dispatchers.IO) { dao.limits().first().any { it.enabled && it.packageName == pkg } }
            if (!limited) return@launch
            val cfg = config
            val date = LocalDate.now().toString()
            val usage = withContext(Dispatchers.IO) {
                val repo = UsageRepository(applicationContext, dao, db)
                if (!repo.permissionGranted()) return@withContext null
                repo.refresh()
                dao.summary(date).first()?.distractingMs
            } ?: return@launch
            val extension = withContext(Dispatchers.IO) { dao.extension(date)?.grantedMinutes ?: 0 }
            val focusRunning = withContext(Dispatchers.IO) { dao.activeSession() }?.state == "RUNNING"
            val limit = settings?.dailyLimitMinutes ?: 60
            val over = UsageMath.allowance(limit, usage, if (cfg.limitAction == LimitAction.CLOSE) 0 else extension) == 0L
            if (!focusRunning && (!over || cfg.limitAction == LimitAction.NOTIFY)) return@launch
            if (foreground != pkg) return@launch
            val strict = !focusRunning && cfg.limitAction == LimitAction.CLOSE
            if (System.currentTimeMillis() - lastInterruptionAt < (if (strict) 3_000L else 60_000L)) return@launch
            lastInterruptionAt = System.currentTimeMillis()
            when {
                focusRunning -> showInterruption(focus = true, canExtend = false)
                strict -> {
                    overlay.show("Daily limit reached", "You've used your $limit minutes for today. This app stays closed until midnight.", emptyList(), autoDismissMs = 3_000L, emoji = "🌙")
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 3_000L)
                }
                else -> showInterruption(focus = false, canExtend = settings?.extensionEnabled != false && extension == 0)
            }
        }
    }

    private fun showInterruption(focus: Boolean, canExtend: Boolean) {
        val actions = mutableListOf(
            BlockOverlay.Action("Close app", primary = true) { performGlobalAction(GLOBAL_ACTION_HOME) },
            BlockOverlay.Action(if (focus) "Back to my focus session" else "Start a focus session") { openApp() },
        )
        if (canExtend) actions += BlockOverlay.Action("Use 5-minute extension") {
            scope.launch(Dispatchers.IO) { db.dao().grantExtension(ExtensionUsageEntity(LocalDate.now().toString())) }
        }
        actions += BlockOverlay.Action("Dismiss")
        overlay.show(
            if (focus) "Return to your focus" else "Take back your time",
            if (focus) "Your focus session is still running." else "You've reached your daily entertainment limit. Choose something meaningful to do next.",
            actions,
        )
    }

    // ---------- Stats ----------

    private fun record(pkg: String, videos: Int = 0, blocked: Int = 0, feedMs: Long = 0L) {
        val date = LocalDate.now().toString()
        val statPkg = if (pkg == BlockerConfig.TIKTOK_ALT) BlockerConfig.TIKTOK else pkg
        scope.launch(Dispatchers.IO) { try { db.dao().bumpStat(date, statPkg, videos, blocked, feedMs) } catch (_: Exception) { } }
    }

    private fun flushFeedTime() {
        if (pendingFeedMs.isEmpty()) return
        val copy = HashMap(pendingFeedMs)
        pendingFeedMs.clear()
        copy.forEach { (pkg, ms) -> if (ms > 0) record(pkg, feedMs = ms) }
    }

    override fun onInterrupt() { if (::overlay.isInitialized) overlay.dismiss() }

    override fun onDestroy() {
        running = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) { flushFeedTime(); overlay.dismiss() }
        scope.cancel()
        super.onDestroy()
    }
}
