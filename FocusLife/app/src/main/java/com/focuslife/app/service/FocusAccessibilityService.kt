package com.focuslife.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import com.focuslife.app.data.UsageRepository
import com.focuslife.app.data.ExtensionUsageEntity
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.view.Gravity
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import com.focuslife.app.MainActivity
import com.focuslife.app.data.FocusDatabase
import com.focuslife.app.domain.UsageMath
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Package-name-only foreground checks. No content retrieval or gestures. */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var db: FocusDatabase
    private var overlay: View? = null
    private var lastCheck = 0L
    private var lastPackage: String? = null
    private var lastInterruptionAt = 0L
    override fun onServiceConnected() { db = Room.databaseBuilder(applicationContext, FocusDatabase::class.java, "focuslife.db").build() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.permissioncontroller")) { dismiss(); return }
        val now = System.currentTimeMillis()
        if (now - lastCheck < 1500 && pkg == lastPackage) return
        lastCheck = now; lastPackage = pkg
        scope.launch {
            val dao = db.dao()
            val settings = withContext(Dispatchers.IO) { dao.settings().first() }
            if (settings?.monitoringEnabled == false) { dismiss(); return@launch }
            val enabled = withContext(Dispatchers.IO) { dao.limits().first().any { it.enabled && it.packageName == pkg } }
            if (!enabled) { dismiss(); return@launch }
            val date = LocalDate.now().toString()
            // Reconstruct fresh usage before deciding; periodic WorkManager snapshots can be 15+ minutes old.
            val usage = withContext(Dispatchers.IO) {
                val repo = UsageRepository(applicationContext, dao, db)
                if (!repo.permissionGranted()) return@withContext null
                repo.refresh()
                dao.summary(date).first()?.distractingMs
            }
            if (usage == null) { dismiss(); return@launch }
            val extension = withContext(Dispatchers.IO) { dao.extension(date)?.grantedMinutes ?: 0 }
            val active = withContext(Dispatchers.IO) { dao.activeSession() }
            if (active?.state == "RUNNING" || UsageMath.allowance(settings?.dailyLimitMinutes ?: 60, usage, extension) == 0L) {
                if (System.currentTimeMillis() - lastInterruptionAt > 60_000L) {
                    lastInterruptionAt = System.currentTimeMillis()
                    showInterruption(active?.state == "RUNNING", settings?.extensionEnabled != false && extension == 0 && active?.state != "RUNNING")
                }
            } else dismiss()
        }
    }
    private fun showInterruption(focus: Boolean, canExtend: Boolean) {
        if (overlay != null) return
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 70, 36, 36); setBackgroundColor(Color.rgb(8,10,18)) }
        fun text(value: String, size: Float) { panel.addView(TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0,12,0,20) }) }
        text(if (focus) "Return to Your Focus" else "Take Back Your Time", 26f)
        text(if (focus) "Your focus session is still running. Consider returning to it." else "You have reached your daily entertainment limit. Your time is valuable. Choose something meaningful to do next.", 17f)
        (listOf("Close App", "Start Focus Session", "Read Quran", "Learn English", "Take a Short Break") + if (canExtend) listOf("Use 5-minute extension") else emptyList()).forEach { label ->
            panel.addView(Button(this).apply { text = label; setOnClickListener {
                dismiss()
                when (label) {
                    "Close App" -> performGlobalAction(GLOBAL_ACTION_HOME)
                    "Use 5-minute extension" -> scope.launch(Dispatchers.IO) { db.dao().grantExtension(ExtensionUsageEntity(LocalDate.now().toString())) }
                    "Start Focus Session", "Read Quran", "Learn English" -> startActivity(Intent(this@FocusAccessibilityService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else -> Unit
                }
            } })
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        try { getSystemService(WindowManager::class.java).addView(panel, params); overlay = panel } catch (_: Exception) { }
    }
    private fun dismiss() { overlay?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) { }; overlay = null } }
    override fun onInterrupt() { dismiss() }
    override fun onDestroy() { dismiss(); scope.cancel(); super.onDestroy() }
}
