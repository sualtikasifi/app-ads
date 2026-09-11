package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.BugReport
import com.sualtikasifi.cizimhafiza.domain.model.BugReportCategory
import com.sualtikasifi.cizimhafiza.domain.model.BugReportEntry
import kotlinx.coroutines.flow.Flow

/**
 * Firestore-backed inbox for in-app "Sorun Bildir" (report a bug)
 * submissions — see presentation/reportbug/.
 *
 * Reports are stamped with their author's uid so [observeMyReports] can read
 * them back; a developer answering from the Firestore console writes a
 * `reply` field, which the reporter then sees in the app.
 */
interface BugReportRepository {
    suspend fun submitReport(description: String, category: BugReportCategory): Result<Unit>

    /** This device's own past reports, newest first, with any developer reply attached. */
    fun observeMyReports(): Flow<List<BugReport>>

    /**
     * Everyone's reports, newest first — the developer panel's inbox.
     *
     * Readable only by the reviewer (see firestore.rules); for anybody else
     * the query comes back empty rather than failing, which is what the
     * panel's own identity line is there to explain.
     */
    suspend fun allReports(limit: Int): Result<List<BugReportEntry>>
}
