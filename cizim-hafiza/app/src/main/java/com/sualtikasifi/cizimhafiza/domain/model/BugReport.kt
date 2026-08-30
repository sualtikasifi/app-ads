package com.sualtikasifi.cizimhafiza.domain.model

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
    val description: String,
    val submittedAtMillis: Long,
    /** Null while nobody has answered yet — see [isAnswered]. */
    val reply: String? = null,
    val repliedAtMillis: Long? = null
) {
    val isAnswered: Boolean get() = !reply.isNullOrBlank()
}
