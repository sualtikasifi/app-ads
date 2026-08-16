package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import com.sualtikasifi.cizimhafiza.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Title + body pair for a local "come back and play" notification. */
data class NotificationText(val title: String, val body: String)

/**
 * Message pools for the daily engagement reminder (see
 * notifications/DailyEngagementWorker.kt). Deliberately picked from
 * `LocalDate.now()` rather than a persisted "last shown index" — that keeps
 * the picker stateless while still avoiding back-to-back repeats: the
 * weekly pool alternates its two variants by ISO week number, and the
 * event-driven pools rotate by day-of-year. Text lives in
 * res/values(-en)/strings.xml as string-arrays so it follows the app's
 * current language.
 */
object NotificationMessages {

    // Weekly arrays are ordered Monday..Sunday, 2 variants per day (14 items).
    private fun weeklyIndex(date: LocalDate): Int {
        val dayOffset = (date.dayOfWeek.value - 1) * 2
        val week = date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
        return dayOffset + (week % 2)
    }

    fun weeklyVariety(context: Context, date: LocalDate): NotificationText =
        pick(context, R.array.notif_weekly_titles, R.array.notif_weekly_bodies, weeklyIndex(date))

    fun streakReminder(context: Context, date: LocalDate): NotificationText =
        pick(context, R.array.notif_streak_titles, R.array.notif_streak_bodies, date.dayOfYear)

    fun inactivityReminder(context: Context, date: LocalDate): NotificationText =
        pick(context, R.array.notif_inactivity_titles, R.array.notif_inactivity_bodies, date.dayOfYear)

    fun rankNudge(context: Context, date: LocalDate, rankName: String, pointsRemaining: Int): NotificationText {
        val titles = context.resources.getStringArray(R.array.notif_rank_titles)
        val bodies = context.resources.getStringArray(R.array.notif_rank_bodies)
        val index = date.dayOfYear % titles.size
        return NotificationText(
            title = String.format(Locale.getDefault(), titles[index], rankName, pointsRemaining),
            body = String.format(Locale.getDefault(), bodies[index], rankName, pointsRemaining)
        )
    }

    private fun pick(context: Context, titlesRes: Int, bodiesRes: Int, index: Int): NotificationText {
        val titles = context.resources.getStringArray(titlesRes)
        val bodies = context.resources.getStringArray(bodiesRes)
        val i = ((index % titles.size) + titles.size) % titles.size
        return NotificationText(titles[i], bodies[i])
    }
}
