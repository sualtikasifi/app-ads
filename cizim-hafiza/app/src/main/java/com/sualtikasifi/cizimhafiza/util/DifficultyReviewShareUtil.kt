package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sualtikasifi.cizimhafiza.BuildConfig
import java.io.File

/**
 * Hands the "Zorluk Belirle" manual difficulty classifications off as a
 * shareable .json file — see DifficultyReviewRepository.exportReviewedDifficultiesJson.
 * Mirrors WordReviewShareUtil: the device running the classification screen
 * is the only place these decisions exist, so this export is the only way
 * they can reach the difficulty tags shipped to every player.
 */
object DifficultyReviewShareUtil {

    fun shareReviewExport(context: Context, json: String) {
        val file = writeToCache(context, json)
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun writeToCache(context: Context, json: String): File {
        val dir = File(context.cacheDir, "difficulty_review_export").apply { mkdirs() }
        val file = File(dir, "karalak_zorluk_belirle_${System.currentTimeMillis()}.json")
        file.writeText(json)
        return file
    }
}
