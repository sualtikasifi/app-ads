package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document id is load-bearing here, not cosmetic.
 *
 * firestore.rules refuses any report whose id does not end in `__<uid>`, and
 * that suffix is the only thing making "two DIFFERENT players reported this"
 * true — a generated id would let one person retire a stranger's round by
 * tapping twice. These tests pin both halves of that contract.
 */
class DrawingReportsTest {

    private val uid = "abc123UID"

    @Test
    fun `the id ends in the reporter uid, as the rules require`() {
        val id = DrawingReports.idFor(DrawingReports.scopeKeyForRun("run-1"), uid)
        assertTrue("Rules match '.*__' + uid, got $id", id.endsWith("__$uid"))
    }

    @Test
    fun `the same player reporting the same round twice writes the same document`() {
        val scope = DrawingReports.scopeKeyForRun("run-1")
        assertEquals(DrawingReports.idFor(scope, uid), DrawingReports.idFor(scope, uid))
    }

    @Test
    fun `two players reporting the same round write different documents`() {
        val scope = DrawingReports.scopeKeyForRun("run-1")
        assertNotEquals(DrawingReports.idFor(scope, uid), DrawingReports.idFor(scope, "other"))
    }

    @Test
    fun `a bot run id survives becoming a document id`() {
        // BotGhostRuns.idFor produces "ghost:<seed>:<id,id,id>" — a colon is
        // legal in a document id but a slash is not, and neither reads well;
        // the point is only that it comes out non-empty and comparable.
        val raw = BotGhostRuns.idFor(seed = -42L, wordIds = listOf(3, 17, 250))
        val id = DrawingReports.idFor(DrawingReports.scopeKeyForRun(raw), uid)
        assertTrue(id.endsWith("__$uid"))
        assertTrue("Document ids may not contain a slash: $id", !id.contains('/'))
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `room reports are scoped per opponent, not per room`() {
        val a = DrawingReports.idFor(DrawingReports.scopeKeyForRoom("WXYZ", "playerA"), uid)
        val b = DrawingReports.idFor(DrawingReports.scopeKeyForRoom("WXYZ", "playerB"), uid)
        assertNotEquals("Reporting two players in one room must be two reports", a, b)
    }

    @Test
    fun `retirement takes more than one reporter`() {
        assertTrue(DrawingReports.REPORTS_TO_RETIRE >= 2)
    }
}
