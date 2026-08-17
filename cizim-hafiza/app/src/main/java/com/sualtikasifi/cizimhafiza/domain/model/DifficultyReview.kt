package com.sualtikasifi.cizimhafiza.domain.model

data class DifficultyReviewCounts(val pending: Int, val easy: Int, val medium: Int, val hard: Int) {
    val totalDecided: Int get() = easy + medium + hard
    val total: Int get() = pending + totalDecided
}
