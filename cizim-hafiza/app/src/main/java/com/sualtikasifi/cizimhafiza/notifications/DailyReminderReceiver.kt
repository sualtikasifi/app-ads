package com.sualtikasifi.cizimhafiza.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes for the daily reminder alarm, and again after anything that would
 * have erased it.
 *
 * An AlarmManager alarm lives in RAM only: a reboot, and an app update that
 * replaces the package, both wipe every pending alarm this app has set.
 * Without the boot filter in AndroidManifest.xml the reminder would work
 * until the first restart and then be gone for good, with nothing to say so
 * — which is close to the failure this whole receiver exists to fix.
 */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm first, always. If enqueueing below fails, the worst case is
        // one missed reminder rather than a chain that stops for good.
        NotificationScheduler.armDailyAlarm(context)

        // A boot or an update only restores the schedule — the reminder
        // itself belongs to 19:00, not to whenever the phone happened to be
        // switched on.
        if (intent.action == NotificationScheduler.ACTION_DAILY_REMINDER) {
            NotificationScheduler.enqueueReminderNow(context)
        }
    }
}
