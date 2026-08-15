package com.sualtikasifi.cizimhafiza.data.local

import android.content.Context
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import kotlinx.serialization.json.Json

/** Reads the bundled word pool (assets/words.json) into Room entities on first launch. */
object WordSeeder {
    private const val ASSET_FILE = "words.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromAssets(context: Context): List<WordEntity> {
        val text = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
