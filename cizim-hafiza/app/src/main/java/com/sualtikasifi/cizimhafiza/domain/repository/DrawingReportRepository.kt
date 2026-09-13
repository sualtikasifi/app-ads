package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.DrawingReport
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReportReason
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke

/**
 * Reporting somebody else's drawing, and the one question the matching
 * query asks back.
 *
 * Play requires an in-app way to report user-generated content, and Hızlı
 * Eşleş shows strangers' drawings to strangers — so this is not only an
 * anti-cheat backstop. It is also the only place labelled examples of
 * written-instead-of-drawn words can come from, which is what
 * [com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector] would need
 * to be tuned against real data rather than synthesised handwriting.
 */
interface DrawingReportRepository {

    /**
     * Files a report against one drawing from a recorded (Hızlı Eşleş) round.
     *
     * Reporting the same round twice is a no-op rather than a second vote —
     * see DrawingReports.idFor.
     */
    suspend fun reportRunDrawing(
        runId: String,
        reportedUid: String,
        word: String,
        strokes: List<DrawingStroke>,
        reason: DrawingReportReason
    ): Result<Unit>

    /** The same, for a drawing seen in an online room, which has no run id. */
    suspend fun reportRoomDrawing(
        roomCode: String,
        reportedUid: String,
        word: String,
        strokes: List<DrawingStroke>,
        reason: DrawingReportReason
    ): Result<Unit>

    /**
     * Whether this round has been reported by enough distinct players to
     * stop being offered as an opponent.
     *
     * Derived on read rather than stored as a flag on the round. A stored
     * flag would have to be writable by whoever noticed the reports, and a
     * modified client could then retire every round in the pool and leave
     * Hızlı Eşleş with nobody in it. Counting instead means retiring a round
     * genuinely takes two separate accounts, which the rules can enforce.
     */
    suspend fun isRetired(runId: String): Boolean

    /** Every report, newest first — for the review screen only. */
    suspend fun recentReports(limit: Int): Result<List<DrawingReport>>
}
