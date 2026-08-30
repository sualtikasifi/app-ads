package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the shareable card for a finished daily challenge.
 *
 * **Spoiler-free by construction.** The card shows which of today's words
 * were right and wrong as a ✅/❌ row and nothing else — never the words
 * themselves, never the drawings. Someone who hasn't played yet can see a
 * friend's score without having today's round ruined, which is the only
 * reason a result like this is worth posting at all.
 */
object DailyChallengeShareUtil {

    fun shareResult(context: Context, correctFlags: List<Boolean>, streak: Int) {
        val grid = correctFlags.joinToString("") { if (it) "✅" else "❌" }
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        val playStoreLink = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val message = context.getString(
            R.string.daily_challenge_share_text,
            date,
            correctFlags.count { it },
            correctFlags.size,
            streak,
            playStoreLink
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$grid\n$message")
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.daily_challenge_share)))
    }
}
