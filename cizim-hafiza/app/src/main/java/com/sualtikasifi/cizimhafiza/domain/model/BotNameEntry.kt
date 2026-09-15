package com.sualtikasifi.cizimhafiza.domain.model

/** One hand-typed entry in the curated bot-nickname pool — see BotNameRepository. */
data class BotNameEntry(
    val id: String,
    val name: String,
    val createdAtMillis: Long
)
