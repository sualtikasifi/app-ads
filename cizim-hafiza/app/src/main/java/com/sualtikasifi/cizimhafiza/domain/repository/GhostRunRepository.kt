package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.GhostRun
import com.sualtikasifi.cizimhafiza.domain.model.GhostRunWord
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem

/**
 * The opponent pool behind "Hızlı Eşleş" — see domain.model.GhostRuns for
 * what makes a round worth keeping and why an opponent never has to be
 * online.
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
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    )

    /**
     * Picks one recorded round for a player at [level] to face, or null when
     * the pool has nothing to offer them yet.
     *
     * Never returns the caller's own round: being handed your own drawings to
     * beat is the one outcome that would give the whole illusion away.
     */
    suspend fun findOpponent(level: Int): Result<GhostRun?>

    /**
     * The opponent's own drawings, fetched only once the challenger has
     * finished and there is finally something to compare.
     *
     * Split out from [findOpponent] because these are two orders of magnitude
     * larger than the run itself, and a search that ends in "no thanks" — or
     * a match abandoned halfway — should not have paid for them.
     */
    suspend fun loadItems(runId: String): Result<List<ResultItem>>
}
