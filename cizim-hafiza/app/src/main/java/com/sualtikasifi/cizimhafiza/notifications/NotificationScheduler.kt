package com.sualtikasifi.cizimhafiza.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sualtikasifi.cizimhafiza.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Owns the reminder channels and the two schedulers that drive the daily
 * "come back and play" notification.
 *
 * Two, because the reminder was only ever arriving when the player opened
 * the app — which is the exact symptom of driving a clock-time reminder off
 * a 24-hour [PeriodicWorkRequestBuilder]. WorkManager's periodic contract is
 * "once per period, at a time of our choosing": Doze pushes it to the next
 * maintenance window, and on the aggressive OEM battery managers a large
 * share of this game's players are running (the same Xiaomi/MIUI family that
 * forced the Google Sign-In client choice — see app/build.gradle.kts), a
 * force-stopped app has its jobs cancelled outright until something launches
 * it again. Opening the app re-runs WorkManager's scheduler, which
 * immediately flushes the overdue job — so the notification appeared to be
 * *caused* by opening the app, when really that was the only moment it was
 * allowed to run.
 *
 * So the primary is now [armDailyAlarm]: a real alarm, at a real time, that
 * fires through Doze. The WorkManager job stays on as a backstop rather than
 * being deleted, because the two fail in genuinely different ways — an alarm
 * is erased by a reboot (hence [DailyReminderReceiver]'s boot filter) while
 * WorkManager restores its own schedule, and WorkManager is the one the OEM
 * killer goes for first. Neither is reliable alone; together, one of them
 * usually survives.
 *
 * What makes the redundancy safe is [SettingsRepository.lastReminderEpochDay]
 * — whichever fires first claims the day, so the player can never be told
 * twice.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "daily_engagement"
    const val NOTIFICATION_ID = 1001

    // Friend match invites — a separate, higher-importance channel since
    // (unlike the once-a-day reminder) these are time-sensitive: the sender
    // is waiting in a room right now. Created eagerly at app startup (see
    // CizimHafizaApp.onCreate()), not lazily when the first invite arrives,
    // because Android silently drops a notify() call whose channel doesn't
    // exist yet on API 26+ — an FCM push can arrive before any other app
    // code has run.
    const val FRIEND_INVITE_CHANNEL_ID = "friend_invite"

    const val ACTION_DAILY_REMINDER = "com.sualtikasifi.cizimhafiza.DAILY_REMINDER"

    private const val TAG = "NotificationScheduler"
    private const val WORK_NAME = "daily_engagement_reminder"
    private const val ALARM_REQUEST_CODE = 4101
    private val TARGET_HOUR: LocalTime = LocalTime.of(20, 0)

    /** Called on every app start — see [armDailyAlarm] for why re-arming each time is the point. */
    fun schedule(context: Context) {
        createChannel(context)
        armDailyAlarm(context)
        scheduleBackstopWork(context)
    }

    /**
     * Sets (or re-sets) the next 19:00 reminder alarm.
     *
     * Called from app start, from the receiver after each firing, and after
     * a reboot. Re-arming on every app start is not redundant bookkeeping:
     * an alarm is the one part of this that a force-stop destroys silently,
     * so the next launch is the first chance to notice and put it back. It
     * also repairs the old scheduler's other quiet failure — the periodic
     * work was enqueued with [ExistingPeriodicWorkPolicy.KEEP], so once its
     * period had drifted off 19:00 (which WorkManager is entitled to do,
     * since it re-anchors from the actual run time) nothing ever pulled it
     * back, and the reminder wandered to a different hour every week.
     *
     * [AlarmManager.setAndAllowWhileIdle] rather than an exact alarm on
     * purpose: exact alarms need SCHEDULE_EXACT_ALARM on Android 12+, and
     * Play only grants that to alarm-clock and calendar apps — a game
     * reminder is squarely not one. The inexact version still pierces Doze,
     * and it lands within a maintenance window of the target, which for
     * "come back and play" is indistinguishable from on time.
     */
    fun armDailyAlarm(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        // Documented to throw when the app has too many alarms pending, and
        // OEM builds have been seen to throw SecurityException here too.
        // A missed alarm costs one reminder; letting it escape would take
        // down Application.onCreate with it.
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerAtMillis(),
                reminderIntent(context)
            )
        }.onFailure { Log.w(TAG, "Daily reminder alarm not set", it) }
    }

    private fun nextTriggerAtMillis(): Long {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), TARGET_HOUR)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun reminderIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, DailyReminderReceiver::class.java).setAction(ACTION_DAILY_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Hands the reminder to [DailyEngagementWorker] instead of building it in
     * the receiver: deciding which reminder to show reads Firestore (the
     * daily challenge's state) and a BroadcastReceiver has about ten seconds
     * before the system considers it hung.
     */
    fun enqueueReminderNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<DailyEngagementWorker>().build())
    }

    private fun scheduleBackstopWork(context: Context) {
        val now = LocalDateTime.now()
        var nextRun = LocalDateTime.of(now.toLocalDate(), TARGET_HOUR)
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)

        val request = PeriodicWorkRequestBuilder<DailyEngagementWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, nextRun).toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            // UPDATE, not KEEP: KEEP is what let the drift above become
            // permanent, since it declines to touch an already-enqueued
            // request no matter how far off its schedule has wandered.
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
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

    fun createFriendInviteChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            FRIEND_INVITE_CHANNEL_ID,
            context.getString(R.string.notification_channel_invite_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_invite_description)
        }
        manager.createNotificationChannel(channel)
    }
}
