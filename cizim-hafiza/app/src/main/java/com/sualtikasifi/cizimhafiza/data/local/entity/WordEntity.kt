package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.Word
import kotlinx.serialization.Serializable

@Entity(tableName = "words")
@Serializable
data class WordEntity(
    @PrimaryKey val id: Int,
    val text: String,
    val category: String,
    val difficulty: Difficulty,
    // Whether this word may be DEALT by a random draw. Every draw query
    // filters on it; WordDao.getWordsByIds pointedly does not, so a
    // non-approved word is still resolvable when someone else's game names
    // it by id.
    //
    // Usually set per-file by WordSeeder.loadFromAssets (words*.json → true,
    // word_review_batch_*.json → false), so approval is normally a function
    // of which file a word lives in rather than of anything that happened on
    // one device. A word can also carry its own explicit `"approved": false`
    // to opt out of draws while staying in an otherwise-playable file —
    // words_en.json uses that for the entries with no playable English form.
    // See WordSeeder.loadFromAssets.
    val approved: Boolean = true
)

fun WordEntity.toDomain() = Word(id = id, text = text, category = category, difficulty = difficulty)
