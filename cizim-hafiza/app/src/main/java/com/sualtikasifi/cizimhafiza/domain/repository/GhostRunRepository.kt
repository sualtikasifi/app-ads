package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.GhostRunWord
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem

/**
 * Records finished rounds so other players can be matched against them
 * later — see domain.model.GhostRuns for what makes a round worth keeping.
 *
 * Write-only for now. The matching query that reads these back is the next
 * phase; the pool needs history before a "find me an opponent" button can
 * find anything, so collecting starts first and quietly.
 */
interface GhostRunRepository {

    /**
     * Stores one finished round. The caller supplies what happened in the
     * round; this stamps who played it (uid, nickname, level, frame) and the
     * fields the matching query needs (band, shard, language).
     *
     * Deliberately NOT suspending, and deliberately returning nothing. This
     * runs at the exact moment the result screen is about to appear, and it
     * uploads a round's worth of stroke data — awaiting it would make the
     * player watch a spinner for a write they never asked for and get
     * nothing from. It runs on the repository's own scope so leaving the
     * screen cannot cancel it, and a failure is logged and dropped: one
     * missing opponent out of thousands is not worth a word to anyone.
     */
    fun record(
        wordIds: List<Int>,
        mode: GameMode,
        totalScore: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    )
}
