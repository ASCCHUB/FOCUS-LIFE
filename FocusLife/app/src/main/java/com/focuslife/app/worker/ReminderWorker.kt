package com.focuslife.app.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focuslife.app.service.FocusAccessibilityService
import java.util.concurrent.TimeUnit

/** After Payment mode, reminds the user to switch the blocker back on. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (FocusAccessibilityService.running) return Result.success()
        val intent = PendingIntent.getActivity(
            applicationContext, 7,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, "focuslife")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Turn Focus Life back on")
            .setContentText("Payment done? Tap to switch the reels blocker back on.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try { applicationContext.getSystemService(NotificationManager::class.java).notify(77, notification) } catch (_: SecurityException) { }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, minutes: Long = 10) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "payment_mode_reminder", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(minutes, TimeUnit.MINUTES).build(),
            )
        }
    }
}
