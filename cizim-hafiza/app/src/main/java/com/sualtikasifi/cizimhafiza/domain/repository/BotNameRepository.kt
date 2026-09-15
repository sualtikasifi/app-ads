package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.BotNameEntry
import kotlinx.coroutines.flow.Flow

/**
 * The curated bot-nickname pool — typed in by hand from Bot İsimleri (see
 * presentation/botnames/), replacing the old procedurally generated names
 * (BOT_NAME_PREFIX/SUFFIX in functions/src/index.ts, which read as
 * obviously AI-generated). Reviewer-only end to end (see firestore.rules);
 * for anybody else these calls come back empty/no-op rather than failing.
 *
 * functions/src/index.ts's league builder switches to drawing from this
 * pool once it holds enough names — that switch is a separate, later step
 * from filling the pool itself.
 */
interface BotNameRepository {
    /** All curated names, newest first — lets the panel show a running count and catch duplicates before they're typed twice. */
    fun observeNames(): Flow<List<BotNameEntry>>

    suspend fun addName(name: String): Result<Unit>

    suspend fun deleteName(id: String): Result<Unit>
}
