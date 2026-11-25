package dev.pocketagent.app

import dev.pocketagent.app.service.NotificationGroups
import dev.pocketagent.app.service.NotificationGroups.Active
import dev.pocketagent.app.service.ReplyNotifier
import dev.pocketagent.app.ui.home.statusScrimAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupsTest {
    private val replies = NotificationGroups.GROUP_REPLIES
    private val approvals = NotificationGroups.GROUP_APPROVALS
    private val routines = NotificationGroups.GROUP_ROUTINES

    private fun child(tag: String, id: Int, group: String) = Active(tag, id, group, false)
    private fun summary(group: String) = Active(NotificationGroups.SUMMARY_TAG, NotificationGroups.summaryId(group)!!, group, true)
    private val runtime = Active(null, 1, NotificationGroups.GROUP_RUNTIME, false)

    @Test fun groupsAndSummaryIdsAreDistinct() {
        val all = listOf(NotificationGroups.GROUP_RUNTIME) + NotificationGroups.SUMMARY_GROUPS
        assertEquals(all.size, all.toSet().size)
        val ids = NotificationGroups.SUMMARY_GROUPS.map { NotificationGroups.summaryId(it)!! }
        assertEquals(ids.size, ids.toSet().size)
        // The runtime group has no summary, and a summary id never collides with the foreground service's id 1 or the child ids.
        assertNull(NotificationGroups.summaryId(NotificationGroups.GROUP_RUNTIME))
        assertTrue(ids.none { it in setOf(1, 101, 102, 103, 104, 105, 106) })
    }

    @Test fun channelsMapToTheirOwnGroup() {
        assertEquals(replies, NotificationGroups.groupForChannel(ReplyNotifier.CHANNEL_REPLIES))
        assertEquals(approvals, NotificationGroups.groupForChannel(ReplyNotifier.CHANNEL_APPROVALS))
        assertEquals(routines, NotificationGroups.groupForChannel(ReplyNotifier.CHANNEL_ROUTINES))
        assertNotEquals(NotificationGroups.GROUP_RUNTIME, NotificationGroups.groupForChannel("anything else"))
    }

    @Test fun noChildrenMeansNoSummary() {
        val plan = NotificationGroups.plan(listOf(runtime))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    @Test fun aChildWithoutSummaryGetsOne() {
        val plan = NotificationGroups.plan(listOf(runtime, child("c1", 101, replies)))
        assertEquals(setOf(replies), plan.post)
        assertTrue(plan.cancel.isEmpty())
    }

    @Test fun aGroupThatIsFineIsLeftAlone() {
        val plan = NotificationGroups.plan(listOf(child("c1", 101, replies), summary(replies)))
        assertTrue(plan.post.isEmpty())
        assertTrue(plan.cancel.isEmpty())
    }

    @Test fun theSummaryGoesWhenTheLastChildIsGone() {
        val plan = NotificationGroups.plan(listOf(runtime, summary(replies)))
        assertTrue(plan.post.isEmpty())
        assertEquals(setOf(replies), plan.cancel)
    }

    @Test fun justCancelledChildrenDoNotCount() {
        // Android removes a cancelled notification asynchronously: it may still be listed when we look.
        val active = listOf(child("c1", 101, replies), child("c1", 105, replies), summary(replies))
        val stillOne = NotificationGroups.plan(active, setOf("c1" to 101))
        assertTrue(stillOne.cancel.isEmpty())
        val none = NotificationGroups.plan(active, setOf("c1" to 101, "c1" to 105))
        assertEquals(setOf(replies), none.cancel)
        // A child of another chat with the same id is a different notification.
        val other = NotificationGroups.plan(listOf(child("c2", 101, replies), summary(replies)), setOf("c1" to 101))
        assertTrue(other.cancel.isEmpty())
    }

    @Test fun aRecentlyPostedChildNotYetListedKeepsTheSummary() {
        // Chat B's reply is showing; chat A's was just posted (not listed yet); B is then cancelled.
        val active = listOf(child("b", 101, replies), summary(replies))
        val inFlight = listOf(child("a", 101, replies))
        val without = NotificationGroups.plan(active, setOf("b" to 101))
        assertEquals(setOf(replies), without.cancel) // the old behaviour: the summary (and A's reply with it) would go
        val with = NotificationGroups.plan(active, setOf("b" to 101), inFlight)
        assertTrue(with.cancel.isEmpty())
        assertTrue(with.post.isEmpty())
        // Only the group of the in-flight child is protected, and an in-flight child that was cancelled does not count.
        val other = NotificationGroups.plan(listOf(child("c", 102, approvals), summary(approvals), summary(replies)), setOf("c" to 102), inFlight)
        assertEquals(setOf(approvals), other.cancel)
        val cancelled = NotificationGroups.plan(listOf(summary(replies)), setOf("a" to 101), inFlight)
        assertEquals(setOf(replies), cancelled.cancel)
        // An in-flight child whose group has no summary yet asks for one.
        assertEquals(setOf(replies), NotificationGroups.plan(emptyList(), inFlight = inFlight).post)
    }

    @Test fun groupsAreIndependent() {
        val active = listOf(child("c1", 102, approvals), summary(approvals), child("c2", 101, replies), summary(routines))
        val plan = NotificationGroups.plan(active, setOf("c1" to 102))
        assertEquals(setOf(replies), plan.post)
        assertEquals(setOf(approvals, routines), plan.cancel)
    }

    @Test fun aSummaryItselfIsNeverAChild() {
        assertTrue(NotificationGroups.groupsWithChildren(listOf(summary(replies), summary(approvals))).isEmpty())
        assertFalse(replies in NotificationGroups.groupsWithChildren(listOf(summary(replies))))
    }

    @Test fun foreignGroupsAreIgnored() {
        assertTrue(NotificationGroups.groupsWithChildren(listOf(Active("x", 7, "ranker_group", false), Active("y", 8, null, false))).isEmpty())
    }

    // ---- the status-bar scrim of the home list --------------------------------------------------------------------

    @Test fun scrimIsInvisibleAtTheTopAndFullOnceTheSceneIsGone() {
        assertEquals(0f, statusScrimAlpha(0, 0, 840, 90), 0f)
        assertEquals(1f, statusScrimAlpha(1, 0, 840, 90), 0f)
        assertEquals(1f, statusScrimAlpha(5, 300, 840, 90), 0f)
        // Fully shown when the scene's bottom edge has reached the bottom of the status bar.
        assertEquals(1f, statusScrimAlpha(0, 840 - 90, 840, 90), 0f)
        assertEquals(1f, statusScrimAlpha(0, 839, 840, 90), 0f)
    }

    @Test fun scrimGrowsMonotonicallyWhileTheSceneScrollsAway() {
        var last = -1f
        for (offset in 0..750 step 50) {
            val a = statusScrimAlpha(0, offset, 840, 90)
            assertTrue(a in 0f..1f)
            assertTrue(a >= last)
            last = a
        }
        assertEquals(0.5f, statusScrimAlpha(0, 375, 840, 90), 0.001f)
    }

    @Test fun scrimToleratesAnUnmeasuredScene() {
        assertEquals(0f, statusScrimAlpha(0, 0, 0, 90), 0f)
        assertEquals(1f, statusScrimAlpha(0, 10, 50, 90), 0f)
    }
}
