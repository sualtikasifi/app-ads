package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import com.sualtikasifi.cizimhafiza.BuildConfig
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
        val playStoreLink = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val message = buildString {
            appendLine("Karalak'ta bana katıl! 🎨")
            appendLine("Oda kodu: $roomCode")
            appendLine()
            appendLine("Karalak zaten yüklüyse: $deepLink")
            append("Yüklü değilse: $playStoreLink")
        }
        share(context, message)
    }

    /** Friend codes are permanent (unlike room codes), so no deep link — just plain text + the Play Store fallback. */
    fun shareFriendCode(context: Context, friendCode: String) {
        val playStoreLink = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val message = buildString {
            appendLine("Karalak'ta arkadaşım ol! 🎨")
            appendLine("Arkadaşlık kodum: $friendCode")
            appendLine()
            append("Karalak: $playStoreLink")
        }
        share(context, message)
    }

    private fun share(context: Context, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
