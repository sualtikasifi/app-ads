package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity

/**
 * A row only ever exists here once a word has been reviewed — absence of a
 * row means "still pending" (see WordReviewDao.getNextPendingWord), so
 * seeding 10,000+ new candidate words never requires 10,000+ placeholder
 * rows up front.
 */
@Entity(tableName = "word_review", primaryKeys = ["wordId"])
data class WordReviewEntity(
    val wordId: Int,
    // "KEPT" or "DELETED" — plain String (not an enum + TypeConverter) since
    // this is the only place it's used; keeps the DAO's raw SQL simple.
    val status: String,
    val reviewedAtMillis: Long
)

object WordReviewStatus {
    const val KEPT = "KEPT"
    const val DELETED = "DELETED"
}
