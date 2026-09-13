package com.sualtikasifi.cizimhafiza.domain.model

/**
 * Whether a "Sorun Bildir" submission is a feature idea or a problem report
 * — shown to the reporter as a two-way choice and stored so a developer can
 * triage the inbox by kind. [name] is persisted (see BugReportRepositoryImpl),
 * so never rename an existing constant.
 */
enum class BugReportCategory {
    SUGGESTION,
    COMPLAINT
}

/**
 * One in-app "Sorun Bildir" submission, as its own author sees it.
 *
 * Reports are one-way by design — no reply text goes back, so nobody is
 * waiting on a developer to type something. [seenAtMillis] is set from the
 * developer panel's "görüldü" button (see DrawingReportsScreen's Feedback
 * tab) once someone has actually looked at it, and that status is the one
 * thing this screen shows back to the reporter — proof the report reached a
 * person rather than a void, without promising a conversation.
 */
data class BugReport(
    val id: String,
    val category: BugReportCategory,
    val description: String,
    val submittedAtMillis: Long,
    /** Null until a developer marks this seen in the panel — see [isSeen]. */
    val seenAtMillis: Long? = null
) {
    val isSeen: Boolean get() = seenAtMillis != null
}

/**
 * One submission as the developer panel shows it.
 *
 * Separate from [BugReport] because the reviewer needs the one field its
 * author must never see in a list of everyone's reports — who wrote it —
 * and because a reporter's own view has no business carrying other
 * people's uids around.
 */
data class BugReportEntry(
    val report: BugReport,
    val uid: String,
    val appVersionName: String?,
    val deviceModel: String?
)
