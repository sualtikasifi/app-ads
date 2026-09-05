package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent

/**
 * Relaunches the app from scratch after the signed-in account changed.
 *
 * Clearing the stored state is not enough on its own. A running process
 * also holds that account in memory: @Singleton repositories cache values
 * (FriendRepositoryImpl's friend code is read once and kept), StateFlows
 * hold their last emission, ViewModels survive on the back stack, and
 * Firestore keeps live snapshot listeners bound to the previous uid. Any
 * one of those is enough to show the old player's name or code under the
 * new account until the app happens to be killed — which is exactly the
 * class of bug that kept coming back one screen at a time.
 *
 * Killing the process makes that whole category impossible rather than
 * fixed-for-now: nothing in-memory can outlive it, so the new session
 * reads every value fresh from disk. It is the same approach a language
 * switch takes, one step further.
 *
 * Only ever call this once the account transition is fully persisted —
 * the backup uploaded, the local wipe committed — since anything still in
 * flight dies with the process.
 */
object AppRestarter {

    fun restart(context: Context) {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
        // exit() rather than finishAffinity(): the point is to drop the
        // PROCESS, not just the Activity stack. The intent above is already
        // queued with the system, so the app comes straight back up.
        Runtime.getRuntime().exit(0)
    }
}
