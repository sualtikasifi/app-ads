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
    // Whether this word is playable in real games. Never read from JSON —
    // WordSeeder.loadFromAssets always overrides it based on which file a
    // word came from (words*.json → true, word_review_batch_*.json →
    // false), so a word's approval is a function of the developer moving
    // its JSON record between those files (after processing a "Kelime
    // İncele" export), not anything that happens purely on one device.
    val approved: Boolean = true
)

fun WordEntity.toDomain() = Word(id = id, text = text, category = category, difficulty = difficulty)
