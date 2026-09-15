package com.sualtikasifi.cizimhafiza.data.local

import android.content.Context
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads a bundled word pool (assets/words*.json) into Room entities. */
object WordSeeder {
    // Named for what each file IS, not for which one a device without a
    // manual language choice gets — that's ENGLISH_ASSET_FILE (see
    // assetFileFor below), now that res/values/strings.xml's own default
    // (no locale qualifier) is English rather than Turkish.
    const val TURKISH_ASSET_FILE = "words.json"
    const val ENGLISH_ASSET_FILE = "words_en.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The on-disk shape. Separate from [WordEntity] purely so `approved` can
     * be nullable here: the entity defaults it to true, which makes "the
     * file said true" and "the file said nothing" indistinguishable.
     */
    @Serializable
    private data class WordJson(
        val id: Int,
        val text: String,
        val category: String,
        val difficulty: Difficulty,
        val approved: Boolean? = null
    )

    /**
     * [approved] is the DEFAULT for the file being loaded — the caller says
     * whether it holds trusted content (words*.json → true) or still-pending
     * review candidates (word_review_batch_*.json → false). A word may
     * override it by carrying an explicit `"approved": false` of its own.
     *
     * That override exists for one case: words_en.json marks the 25 entries
     * that are real Turkish words with no playable English form (kokoreç,
     * künefe, cezve …). They stay in the file, and therefore stay resolvable
     * by id, but never enter an English draw — every random-draw query
     * filters on approved = 1 while WordDao.getWordsByIds deliberately does
     * not. That distinction is load-bearing: an online room's word list is
     * chosen by the host from THEIR language's pool, and both players then
     * look those exact ids up. Dropping the rows outright instead would have
     * handed an English player nine words where their Turkish opponent got
     * ten, silently, with no error anywhere — see GameRepositoryImpl's
     * mapNotNull. They can still be dealt such a word in a mixed room; they
     * simply cannot be dealt one by their own game.
     */
    fun loadFromAssets(context: Context, assetFileName: String = TURKISH_ASSET_FILE, approved: Boolean = true): List<WordEntity> {
        val text = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        val words: List<WordJson> = json.decodeFromString(text)
        return words.map {
            WordEntity(
                id = it.id,
                text = it.text,
                category = it.category,
                difficulty = it.difficulty,
                approved = it.approved ?: approved
            )
        }
    }

    /** The language currently applied to this context's resources — respects both a manual
     *  per-app override (Settings screen toggle) and, absent one, the system language. */
    fun currentLanguage(context: Context): String =
        context.resources.configuration.locales.get(0).language

    // English for anything that isn't specifically Turkish — not the other
    // way around. A device on a language this app has no word pool for
    // (Hindi, say) resolves its UI strings to the English default (see
    // res/values/strings.xml, no longer Turkish); the game words have to
    // follow the same rule, or that same player would see an English menu
    // hand them Turkish words to guess.
    fun assetFileFor(language: String): String =
        if (language == "tr") TURKISH_ASSET_FILE else ENGLISH_ASSET_FILE
}
