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
 * Reports used to be strictly write-only: the player typed a problem, got a
 * "thanks" toast, and never heard anything again — which reads as shouting
 * into a void and is the main reason in-app feedback channels stop being
 * used. [reply] is written by a developer (Firebase console or admin SDK,
 * both of which bypass the client-side rules) and is what turns this into a
 * conversation the reporter can actually follow.
 */
data class BugReport(
    val id: String,
    val category: BugReportCategory,
    val description: String,
    val submittedAtMillis: Long,
    /** Null while nobody has answered yet — see [isAnswered]. */
    val reply: String? = null,
    val repliedAtMillis: Long? = null
) {
    val isAnswered: Boolean get() = !reply.isNullOrBlank()
}
