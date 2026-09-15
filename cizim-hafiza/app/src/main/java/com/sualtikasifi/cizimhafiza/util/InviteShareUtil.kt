package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen

/**
 * Builds and launches the system share sheet for a room invite (WhatsApp,
 * SMS, whatever the user picks — deliberately not hard-coded to one app).
 *
 * The room code is always included in plain text as the one guaranteed-to-
 * work fallback: WhatsApp (and most messaging apps) reliably turns http(s)
 * links into tappable text but is inconsistent about custom URI schemes
 * like "karalak://", so a friend who already has the app may still need to
 * type the code into "Koda Katıl" by hand rather than tapping the link.
 */
object InviteShareUtil {

    fun shareRoomInvite(context: Context, roomCode: String) {
        val deepLink = Screen.inviteDeepLink(roomCode)
        val playStoreLink = playStoreLink()
        // Localised, like everything else the player can see. This text used
        // to be Turkish literals, which meant an English player's invite
        // arrived in their friend's WhatsApp in a language neither of them
        // had chosen — and this is the one string in the app that leaves the
        // app, so it is the last place a hard-coded language belongs.
        val message = buildString {
            appendLine(context.getString(R.string.share_room_invite))
            appendLine(context.getString(R.string.share_room_code, roomCode))
            appendLine()
            appendLine(context.getString(R.string.share_room_installed, deepLink))
            append(context.getString(R.string.share_room_not_installed, playStoreLink))
        }
        share(context, message)
    }

    /**
     * Unlike a room code, a friend code carries a standing reward pitch: see
     * FriendRepositoryImpl.recordReferralIfEligible / the referral cron in
     * functions/src/index.ts — reaching level 5 after opening this link
     * credits whoever sent it with 500 XP.
     *
     * Deliberately does NOT lead with the "karalak://friend/..." deep link
     * the way shareRoomInvite does: most share targets (WhatsApp, SMS,
     * Instagram DM, ...) don't linkify a non-http(s) custom scheme at all,
     * so it renders as inert plain text with nothing to tap — which reads
     * as "the invite is broken" even though the in-app deep-link handling
     * itself is fine. The code is the one part of this message guaranteed
     * to work everywhere: it's plain text either way, so leading with
     * explicit instructions for typing it into "Add a friend" beats
     * implying a tap that, most of the time, cannot happen.
     */
    fun shareFriendCode(context: Context, friendCode: String) {
        val playStoreLink = playStoreLink()
        val message = buildString {
            appendLine(context.getString(R.string.share_friend_invite))
            appendLine(context.getString(R.string.share_friend_reward_hint))
            appendLine()
            appendLine(context.getString(R.string.share_friend_code, friendCode))
            appendLine(context.getString(R.string.share_friend_code_instructions))
            appendLine()
            append(context.getString(R.string.share_room_not_installed, playStoreLink))
        }
        share(context, message)
    }

    private fun playStoreLink() =
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

    private fun share(context: Context, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
