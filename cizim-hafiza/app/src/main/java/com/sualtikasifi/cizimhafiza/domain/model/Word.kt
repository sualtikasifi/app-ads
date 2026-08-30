package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Word(
    val id: Int,
    val text: String,
    val category: String,
    val difficulty: Difficulty
)
