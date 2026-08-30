package com.sualtikasifi.cizimhafiza.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.presentation.MainActivity
import com.sualtikasifi.cizimhafiza.util.NotificationMessages
import com.sualtikasifi.cizimhafiza.util.DailyChallengeRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Runs once a day (see NotificationScheduler). Picks at most one reminder —
 * inactivity takes priority over streak, which takes priority over a rank
 * nudge, which falls back to the general weekly-variety message — and skips
 * entirely if the player already played today.
 */
@HiltWorker
class DailyEngagementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val dailyChallengeRepository: DailyChallengeRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.notificationsEnabled.value) return Result.success()

        val today = LocalDate.now()
        val todayEpochDay = today.toEpochDay()

        // The daily challenge takes priority over every other reminder, and
        // it fires even for someone who already played a normal game today:
        // an unplayed challenge is a streak about to break, which is the one
        // thing worth interrupting a player for.
        dailyChallengeRepository.refresh()
        val daily = dailyChallengeRepository.state.value
        if (daily.isAvailableToday && daily.currentStreak > 0) {
            val text = NotificationMessages.dailyChallengeStreakAtRisk(
                context = applicationContext,
                date = today,
                streak = daily.currentStreak
            )
            showNotification(text.title, text.body)
            return Result.success()
        }

        if (settingsRepository.lastPlayedEpochDay == todayEpochDay) return Result.success()

        val daysSincePlayed = todayEpochDay - settingsRepository.lastPlayedEpochDay
        val progress = LevelProgressState.forXp(settingsRepository.lifetimeXp.value)

        val text = when {
            settingsRepository.lastPlayedEpochDay < 0 -> null // never played — nothing to remind them of yet
            daily.isAvailableToday -> NotificationMessages.dailyChallengeWaiting(applicationContext, today)
            daysSincePlayed >= 3 -> NotificationMessages.inactivityReminder(applicationContext, today)
            settingsRepository.currentStreak >= 2 -> NotificationMessages.streakReminder(applicationContext, today)
            progress.nextTier != null && progress.progressFraction >= 0.7f ->
                NotificationMessages.rankNudge(
                    context = applicationContext,
                    date = today,
                    rankName = applicationContext.getString(progress.nextTier!!.rank.nameRes),
                    pointsRemaining = progress.xpToNextTier
                )
            else -> NotificationMessages.weeklyVariety(applicationContext, today)
        } ?: return Result.success()

        showNotification(text.title, text.body)
        return Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val context = applicationContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NotificationScheduler.NOTIFICATION_ID, notification)
    }
}
