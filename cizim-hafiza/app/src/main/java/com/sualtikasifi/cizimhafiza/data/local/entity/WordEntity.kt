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
    val difficulty: Difficulty
)

fun WordEntity.toDomain() = Word(id = id, text = text, category = category, difficulty = difficulty)
