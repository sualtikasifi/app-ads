package com.sualtikasifi.cizimhafiza.data.local

import android.content.Context
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import kotlinx.serialization.json.Json

/** Reads a bundled word pool (assets/words*.json) into Room entities. */
object WordSeeder {
    const val DEFAULT_ASSET_FILE = "words.json"
    const val ENGLISH_ASSET_FILE = "words_en.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromAssets(context: Context, assetFileName: String = DEFAULT_ASSET_FILE): List<WordEntity> {
        val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    /** The language currently applied to this context's resources — respects both a manual
     *  per-app override (Settings screen toggle) and, absent one, the system language. */
    fun currentLanguage(context: Context): String =
        context.resources.configuration.locales.get(0).language

    fun assetFileFor(language: String): String =
        if (language == "en") ENGLISH_ASSET_FILE else DEFAULT_ASSET_FILE
}
