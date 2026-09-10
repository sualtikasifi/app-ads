package com.sualtikasifi.cizimhafiza.domain.model

/**
 * Why a player reported somebody else's drawing.
 *
 * [name] is persisted (see DrawingReportRepositoryImpl and firestore.rules,
 * which allows exactly these three strings), so never rename an existing
 * constant.
 */
enum class DrawingReportReason {
    /**
     * The word was written out instead of drawn — see [WrittenWordDetector],
     * which catches most of these before they are ever recorded. This is the
     * human backstop for the ones it misses, and the only source of labelled
     * examples the detector can later be tuned against.
     */
    WRITTEN,

    /** Obscene, hateful or otherwise unwelcome. */
    OFFENSIVE,

    /** Not an attempt at the word at all — a scribble to get through the round. */
    MEANINGLESS
}

/**
 * One report of one player's drawing, as the review screen reads it back.
 *
 * [strokesJson] is a copy of the drawing taken at the moment of reporting,
 * not a pointer to it. That is deliberate and it is what makes a report
 * reviewable at all: an online room's drawings live only as long as the room
 * does, and a ghost run's are pruned once its author has left ten newer ones
 * behind (GhostRuns.MAX_RUNS_PER_PLAYER). A report whose evidence has been
 * collected since is a report nobody can act on.
 */
data class DrawingReport(
    val id: String,
    val reason: DrawingReportReason,
    /** Whose drawing this is — never shown to the reporter, only in review. */
    val reportedUid: String,
    /** The ghost run this drawing came from, or empty for an online room. */
    val runId: String,
    /** The word the player was asked to draw. */
    val word: String,
    val strokesJson: String,
    val submittedAtMillis: Long
)

object DrawingReports {

    /**
     * How many DISTINCT players must report a recorded round before it stops
     * being offered as an opponent.
     *
     * Two rather than one because a single report is not evidence: the
     * player most likely to reach for this button is the one who just lost,
     * and one spiteful tap should not be able to remove a stranger's honest
     * round. Two rather than five because the pool is small and a retired
     * round costs almost nothing — its author has up to nine others, and
     * there is no shortage of opponents to replace it with.
     *
     * The rules enforce the "distinct" part rather than trusting the client:
     * a report's document id ends in its author's uid (see [idFor]), so a
     * second report of the same round by the same person overwrites nothing
     * and creates nothing.
     */
    const val REPORTS_TO_RETIRE = 2

    /**
     * Only ever this many reports are read when deciding whether a round is
     * retired. The question is "are there at least [REPORTS_TO_RETIRE]", not
     * "how many", and this runs on the way into a match.
     */
    const val RETIREMENT_QUERY_LIMIT = REPORTS_TO_RETIRE

    /** Firestore caps a document id at 1500 bytes; a run id plus a uid is far under it. */
    private const val SEPARATOR = "__"

    /**
     * The document id for one person's report of one drawing.
     *
     * Deterministic on purpose. Firestore has no "unique per (run, reporter)"
     * constraint, and one player tapping the button ten times must not be
     * able to retire a round on their own — building that pair into the id
     * makes a repeat report a rewrite of the same document instead of a new
     * vote, with no counting or de-duplication anywhere.
     */
    fun idFor(scopeKey: String, reporterUid: String): String =
        scopeKey + SEPARATOR + reporterUid

    /**
     * What a report is scoped to.
     *
     * A recorded round is identified by its run id. An online room's drawing
     * has no run, so it is scoped to the room and the player inside it —
     * which still gives one report per reporter per opponent per room.
     */
    fun scopeKeyForRun(runId: String): String = sanitize(runId)

    fun scopeKeyForRoom(roomCode: String, reportedUid: String): String =
        sanitize(roomCode) + "_" + sanitize(reportedUid)

    /**
     * A document id may not contain a slash, and a run id built by
     * BotGhostRuns carries colons and commas. Nothing here needs to be
     * reversible — the id is only ever compared with itself — so anything
     * awkward simply becomes a dash.
     */
    private fun sanitize(raw: String): String =
        raw.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
}

/**
 * One round [WrittenWordDetector] refused, as the review screen reads it back.
 *
 * Deliberately keeps only ONE drawing — the highest-scoring word — rather
 * than all ten. The question this answers is "is the detector firing, and on
 * what?", and one sample answers it; ten would multiply the storage by ten
 * for a round nobody is going to study word by word. [scores] carries the
 * shape of the whole round anyway.
 */
data class DetectorEvent(
    val id: String,
    /** Per word, 0..100 — the same order the round was played in. */
    val scores: List<Int>,
    val flaggedCount: Int,
    val wordCount: Int,
    /** Whether the round was also perfect and suspiciously fast — see WrittenWordDetector. */
    val outcomeLooksRead: Boolean,
    val sampleWord: String,
    val sampleStrokesJson: String,
    val appVersionCode: Int,
    val createdAtMillis: Long
)
