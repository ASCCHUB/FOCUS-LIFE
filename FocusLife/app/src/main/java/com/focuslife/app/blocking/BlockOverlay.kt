package com.focuslife.app.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "take back your time" overlay drawn by the accessibility service. */
class BlockOverlay(private val service: AccessibilityService) {
    data class Action(val label: String, val primary: Boolean = false, val onClick: () -> Unit = {})

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager get() = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var shownAt = 0L
    private val dismissRunnable = Runnable { dismiss() }

    val isShowing get() = view != null
    /** True for a short time after showing, so the navigation we trigger doesn't close it at once. */
    val isFresh get() = SystemClock.elapsedRealtime() - shownAt < 1_500L

    fun show(title: String, message: String, actions: List<Action>, autoDismissMs: Long? = null, emoji: String = "🌿") {
        dismiss()
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(48), dp(28), dp(48))
            setBackgroundColor(Color.parseColor("#F50B1220"))
            isClickable = true
        }
        panel.addView(TextView(service).apply { text = emoji; textSize = 52f; gravity = Gravity.CENTER })
        panel.addView(TextView(service).apply {
            text = title; textSize = 26f; setTextColor(Color.parseColor("#E6EDF7")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8))
        })
        panel.addView(TextView(service).apply {
            text = message; textSize = 16f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24)); setLineSpacing(0f, 1.2f)
        })
        actions.forEach { action ->
            panel.addView(Button(service).apply {
                text = action.label
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.parseColor(if (action.primary) "#04281F" else "#E6EDF7"))
                background = GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    if (action.primary) setColor(Color.parseColor("#6EE7B7")) else { setColor(Color.TRANSPARENT); setStroke(dp(1), Color.parseColor("#334155")) }
                }
                setOnClickListener { dismiss(); action.onClick() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(12) })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        try {
            windowManager.addView(panel, params)
            view = panel
            shownAt = SystemClock.elapsedRealtime()
            if (autoDismissMs != null) handler.postDelayed(dismissRunnable, autoDismissMs)
        } catch (_: Exception) { view = null }
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        view?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        view = null
    }
}
