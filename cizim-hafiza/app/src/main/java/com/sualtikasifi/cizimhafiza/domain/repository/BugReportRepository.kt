package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.BugReport
import com.sualtikasifi.cizimhafiza.domain.model.BugReportCategory
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
}
