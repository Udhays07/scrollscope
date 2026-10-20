package com.scrollscope.app.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrollscope.app.MainActivity
import com.scrollscope.app.R
import com.scrollscope.app.ScrollScopeApp
import com.scrollscope.app.util.Permissions
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Nightly: posts the day's summary and deletes data older than the retention window. */
class DailySummaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as ScrollScopeApp
        val s = app.settings.flow.first()
        val dao = app.db.stats()
        val today = LocalDate.now()

        val cutoff = today.minusDays(s.retentionDays.toLong()).toString()
        dao.deleteEventsBefore(cutoff)
        dao.deleteSessionsBefore(cutoff)

        val count = dao.countForDayNow(today.toString())
        val yesterday = dao.countForDayNow(today.minusDays(1).toString())
        val diff = count - yesterday
        val trend = when {
            diff > 0 -> "↑ $diff vs yesterday"
            diff < 0 -> "↓ ${-diff} vs yesterday"
            else -> "same as yesterday"
        }
        val title = if (count >= s.dailyLimit) "$count reels today · over your limit" else "$count reels today"

        if (Permissions.notificationsAllowed(app)) {
            val tap = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(app, ScrollScopeApp.CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_stat_owl)
                .setContentTitle(title)
                .setContentText(trend)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(app).notify(1, n)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-summary"

        fun schedule(ctx: Context, hour: Int, replace: Boolean) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()

            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
