package com.sualtikasifi.cizimhafiza.domain.repository

/**
 * Firestore-backed inbox for in-app "Sorun Bildir" (report a bug)
 * submissions — see presentation/reportbug/. Write-only from the app;
 * developers check submissions directly in the Firestore console.
 */
interface BugReportRepository {
    suspend fun submitReport(description: String): Result<Unit>
}
