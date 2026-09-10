package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.DetectorEvent
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector

/**
 * Writes down the rounds [WrittenWordDetector] refused, so that it is
 * possible to tell whether it is doing anything at all.
 *
 * Without this the only trace of a refusal is a log line on the device it
 * happened on, which means the two explanations for the detector never
 * firing — nobody is cheating, or it is broken — look exactly alike. It is
 * also the second route to real handwriting samples: reporting depends on a
 * player noticing and objecting, while this catches every round the detector
 * itself was sure about.
 */
interface DetectorEventRepository {

    /**
     * Records one refused round: the per-word scores, and the single drawing
     * that scored highest as a sample.
     *
     * Fire and forget by design — this runs on the way out of a round that is
     * already finished and paid out, and a failure here must never be allowed
     * to affect the player.
     */
    fun recordRefusal(verdict: WrittenWordDetector.RoundVerdict, items: List<ResultItem>)

    /** Newest first — for the hidden review screen only. */
    suspend fun recentEvents(limit: Int): Result<List<DetectorEvent>>
}
