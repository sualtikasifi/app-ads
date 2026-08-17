package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sualtikasifi.cizimhafiza.BuildConfig
import java.io.File

/**
 * Hands the "Kelime İncele" review decisions off as a shareable .json file —
 * see WordReviewRepository.exportReviewedWordsJson. The device running the
 * review screen is the only place those decisions exist (local Room table,
 * never synced anywhere), so this export is the only way they can reach the
 * word pool shipped to every player: the file gets sent back to the
 * developer, who folds "Kalsın" entries into the permanent word list and
 * drops "Sil" entries from future batches before the next app update.
 */
object WordReviewShareUtil {

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
        val dir = File(context.cacheDir, "word_review_export").apply { mkdirs() }
        val file = File(dir, "karalak_kelime_incele_${System.currentTimeMillis()}.json")
        file.writeText(json)
        return file
    }
}
