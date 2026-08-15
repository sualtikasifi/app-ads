package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.Serializable

/**
 * One continuous finger stroke, as the raw points the user dragged through.
 * A drawing is a list of these, which lets us re-render (or replay) it as
 * vector paths instead of a fixed-resolution bitmap.
 */
@Serializable
data class DrawingPoint(val x: Float, val y: Float)

typealias DrawingStroke = List<DrawingPoint>

data class DrawingResult(
    val sessionId: Long,
    val wordId: Int,
    val word: Word,
    val strokes: List<DrawingStroke>,
    var userAnswer: String = "",
    var isCorrect: Boolean = false,
    var responseTimeMs: Long = 0L,
    var pointsAwarded: Int = 0
)
