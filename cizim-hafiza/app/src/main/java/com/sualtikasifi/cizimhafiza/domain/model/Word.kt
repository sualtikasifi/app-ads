package com.sualtikasifi.cizimhafiza.domain.model

data class Word(
    val id: Int,
    val text: String,
    val category: String,
    val difficulty: Difficulty
)
