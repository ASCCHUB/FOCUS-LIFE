package com.focuslife.app.worker

import android.content.Context
import android.app.NotificationManager
import android.app.Notification
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focuslife.app.data.FocusDao
import com.focuslife.app.domain.UsageMath
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import androidx.work.*
import com.focuslife.app.data.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import java.util.concurrent.TimeUnit

@HiltWorker class UsageWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, private val usage: UsageRepository, private val dao: FocusDao) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (usage.refresh()) {
            val day = LocalDate.now().toString()
            val summary = dao.summary(day).first()
            val settings = dao.settings().first()
            if (summary != null && settings?.monitoringEnabled != false) {
                val level = UsageMath.warning(summary.distractingMs, settings?.dailyLimitMinutes ?: 60)
                if (level == 40 || level == 50) notifyOnce(day, level)
            }
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }

    private fun notifyOnce(day: String, level: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = applicationContext.getSharedPreferences("warning_delivery", Context.MODE_PRIVATE)
        val key = "$day-$level"
        if (prefs.getBoolean(key, false)) return
        val notification = NotificationCompat.Builder(applicationContext, "focuslife")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus Life usage reminder")
            .setContentText(if (level == 40) "You're approaching your entertainment allowance." else "Only a little entertainment time remains today.")
            .setAutoCancel(true).build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(level, notification)
        prefs.edit().putBoolean(key, true).apply()
    }
    companion object { fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("usage_refresh", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<UsageWorker>(15, TimeUnit.MINUTES).build()) } }
}
