package com.sualtikasifi.cizimhafiza.data.local

import android.content.Context
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import kotlinx.serialization.json.Json

/** Reads a bundled word pool (assets/words*.json) into Room entities. */
object WordSeeder {
    const val DEFAULT_ASSET_FILE = "words.json"
    const val ENGLISH_ASSET_FILE = "words_en.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [approved] is applied to every loaded word regardless of what's in the
     * JSON (there is no "approved" field in these files at all) — it's the
     * caller's job to say whether the file being loaded is trusted content
     * (words*.json) or still-pending review candidates
     * (word_review_batch_*.json). See WordEntity.approved.
     */
    fun loadFromAssets(context: Context, assetFileName: String = DEFAULT_ASSET_FILE, approved: Boolean = true): List<WordEntity> {
        val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        val words: List<WordEntity> = json.decodeFromString(text)
        return words.map { it.copy(approved = approved) }
    }

    /** The language currently applied to this context's resources — respects both a manual
     *  per-app override (Settings screen toggle) and, absent one, the system language. */
    fun currentLanguage(context: Context): String =
        context.resources.configuration.locales.get(0).language

    fun assetFileFor(language: String): String =
        if (language == "en") ENGLISH_ASSET_FILE else DEFAULT_ASSET_FILE
}
