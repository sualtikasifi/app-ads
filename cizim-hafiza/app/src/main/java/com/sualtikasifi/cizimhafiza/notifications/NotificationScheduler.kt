package com.sualtikasifi.cizimhafiza.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sualtikasifi.cizimhafiza.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Sets up the reminder notification channel and schedules DailyEngagementWorker to run once a day. */
object NotificationScheduler {

    const val CHANNEL_ID = "daily_engagement"
    const val NOTIFICATION_ID = 1001
    private const val WORK_NAME = "daily_engagement_reminder"
    private val TARGET_HOUR: LocalTime = LocalTime.of(19, 0)

    fun schedule(context: Context) {
        createChannel(context)

        val now = LocalDateTime.now()
        var nextRun = LocalDateTime.of(now.toLocalDate(), TARGET_HOUR)
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
        val initialDelay = Duration.between(now, nextRun)

        val request = PeriodicWorkRequestBuilder<DailyEngagementWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
