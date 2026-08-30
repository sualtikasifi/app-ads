package com.sualtikasifi.cizimhafiza.domain.model

data class WordReviewCounts(val pending: Int, val kept: Int, val deleted: Int) {
    val totalDecided: Int get() = kept + deleted
    val total: Int get() = pending + totalDecided
}
