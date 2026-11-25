package dev.pocketagent.app.service

import dev.pocketagent.app.ui.pixel.stage.FireTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decay maths and the transitions of the fire feed. Reference values were recomputed with a throwaway
 * script (0.70 * 2^(-t / 9 s)): 0.35 at 9 s, 0.3241 at 10 s, 0.2778 at 12 s, 0.0069 at 60 s, and the heat 0.70
 * is under the cut at 55,164 ms.
 */
class FireMathTest {
    private val empty = FireFeedState()

    private fun heated(heat: Float, at: Long = 0L) = FireFeedState(heat = heat, heatAtMs = at)

    @Test fun decayHalvesEveryNineSeconds() {
        assertEquals(0.35f, FireMath.decayed(0.70f, 9_000L), 1e-5f)
        assertEquals(0.175f, FireMath.decayed(0.70f, 18_000L), 1e-5f)
        assertEquals(0.70f, FireMath.decayed(0.70f, 0L), 0f)
        assertEquals(0.70f, FireMath.decayed(0.70f, -5_000L), 0f)
    }

    @Test fun decayCutsBelowOnePercent() {
        assertEquals(0f, FireMath.decayed(0.70f, 60_000L), 0f)
        assertTrue(FireMath.decayed(0.70f, 50_000L) > FireMath.CUT)
        assertEquals(0f, FireMath.decayed(0.005f, 1L), 0f)
    }

    @Test fun levelAtTakesLargerOfFloorAndHeat() {
        // an open turn holds 0.35; heat 0.70 is above it at first and falls under it later
        val s = FireMath.textArrived(heated(0.70f), "a", 0L)
        assertEquals(0.70f, FireMath.levelAt(s, 0L), 1e-6f)
        assertEquals(0.35f, FireMath.levelAt(s, 9_000L), 1e-5f)
        assertEquals(FireMath.TURN_FLOOR, FireMath.levelAt(s, 18_000L), 0f)
        // no floor: the heat alone
        assertEquals(0.175f, FireMath.levelAt(heated(0.70f), 18_000L), 1e-5f)
        // a tool floor beats everything below it
        val tool = FireMath.toolStarted(empty, "a", "1", 0L)
        assertEquals(FireMath.TOOL_FLOOR, FireMath.levelAt(tool, 120_000L), 0f)
    }

    @Test fun floorOfOrdersToolsAboveTurns() {
        assertEquals(0f, FireMath.floorOf(empty), 0f)
        assertEquals(FireMath.TURN_FLOOR, FireMath.floorOf(FireFeedState(turns = setOf("a"))), 0f)
        assertEquals(FireMath.TOOL_FLOOR, FireMath.floorOf(FireFeedState(turns = setOf("a"), tools = setOf("a|1"))), 0f)
    }

    @Test fun turnStartedKicksToAtLeast055AndOpensFloor() {
        val s = FireMath.turnStarted(empty, "a", 1_000L)
        assertEquals(FireMath.START_KICK, s.heat, 0f)
        assertEquals(1_000L, s.heatAtMs)
        assertEquals(setOf("a"), s.turns)
        assertEquals(0.55f, FireMath.levelAt(s, 1_000L), 1e-6f)
        // 9 s later the heat is 0.275, the open turn holds 0.35
        assertEquals(FireMath.TURN_FLOOR, FireMath.levelAt(s, 10_000L), 0f)
        // a hotter fire is not cooled by a new turn
        val hot = FireMath.turnStarted(heated(0.8f), "a", 0L)
        assertEquals(0.8f, hot.heat, 1e-6f)
    }

    @Test fun textNeverRaisesAboveCap() {
        var s = empty
        repeat(1_000) { s = FireMath.textArrived(s, "a", 0L) }
        assertEquals(FireMath.TEXT_CAP, s.heat, 0f)
        assertEquals(FireMath.TEXT_CAP, FireMath.levelAt(s, 0L), 0f)
        assertEquals(setOf("a"), s.turns)
        // heat already above the cap is left alone, including its time stamp (so it goes on decaying)
        val hot = FireMath.textArrived(heated(0.9f, 0L), "a", 0L)
        assertEquals(0.9f, hot.heat, 0f)
        assertEquals(0L, hot.heatAtMs)
        // after it has decayed under the cap a piece adds a step again
        val cooled = FireMath.textArrived(heated(0.9f, 0L), "a", 9_000L)
        assertEquals(0.45f + FireMath.TEXT_STEP, cooled.heat, 1e-5f)
        assertEquals(9_000L, cooled.heatAtMs)
    }

    @Test fun textOpensTurnOnceAndReturnsSameStateWhenNothingChanges() {
        val hot = FireMath.textArrived(heated(0.9f), "a", 0L)
        assertEquals(setOf("a"), hot.turns)
        assertSame(hot, FireMath.textArrived(hot, "a", 0L))
    }

    @Test fun toolStartedKicksToAtLeast09AndFloors() {
        val s = FireMath.toolStarted(empty, "a", "1", 500L)
        assertEquals(FireMath.TOOL_KICK, s.heat, 0f)
        assertEquals(setOf("a|1"), s.tools)
        assertEquals(FireMath.TOOL_FLOOR, FireMath.floorOf(s), 0f)
        // a repeated running event only kicks again
        val again = FireMath.toolStarted(s, "a", "1", 9_500L)
        assertEquals(1, again.tools.size)
        assertEquals(FireMath.TOOL_KICK, again.heat, 0f)
        assertEquals(9_500L, again.heatAtMs)
        // two calls are two keys
        assertEquals(2, FireMath.toolStarted(s, "a", "2", 600L).tools.size)
    }

    @Test fun toolFinishedBakesFloorThenEases() {
        var s = FireMath.turnStarted(empty, "a", 0L)
        s = FireMath.toolStarted(s, "a", "1", 0L)
        s = FireMath.toolFinished(s, "a", "1", 0L)
        assertTrue(s.tools.isEmpty())
        assertEquals(0.90f, FireMath.levelAt(s, 0L), 1e-6f)
        assertEquals(0.45f, FireMath.levelAt(s, 9_000L), 1e-5f)
        // the open turn keeps the fire at 0.35 however long it takes
        assertEquals(FireMath.TURN_FLOOR, FireMath.levelAt(s, 600_000L), 0f)
        for (t in 0L..120_000L step 3_000L) assertTrue(FireMath.levelAt(s, t) >= FireMath.TURN_FLOOR)
    }

    @Test fun toolFinishedOfUnknownCallChangesNothing() {
        val s = FireMath.toolStarted(empty, "a", "1", 0L)
        assertSame(s, FireMath.toolFinished(s, "a", "2", 10L))
        assertSame(empty, FireMath.toolFinished(empty, "a", "1", 10L))
    }

    @Test fun turnEndedClearsOnlyThatConversation() {
        var s = empty
        s = FireMath.turnStarted(s, "a", 0L)
        s = FireMath.turnStarted(s, "b", 0L)
        s = FireMath.toolStarted(s, "a", "1", 0L)
        s = FireMath.toolStarted(s, "a", "2", 0L)
        s = FireMath.toolStarted(s, "b", "1", 0L)
        val afterA = FireMath.turnEnded(s, "a", 0L)
        assertEquals(setOf("b"), afterA.turns)
        assertEquals(setOf("b|1"), afterA.tools)
        assertEquals(FireMath.TOOL_FLOOR, FireMath.floorOf(afterA), 0f)
        val afterB = FireMath.turnEnded(afterA, "b", 0L)
        assertTrue(afterB.turns.isEmpty())
        assertTrue(afterB.tools.isEmpty())
        assertEquals(0f, FireMath.floorOf(afterB), 0f)
        // baked: the fire eases from where it was
        assertEquals(0.90f, FireMath.levelAt(afterB, 0L), 1e-6f)
        assertEquals(0.45f, FireMath.levelAt(afterB, 9_000L), 1e-5f)
    }

    @Test fun turnEndedMatchesToolKeysByWholeConversationId() {
        var s = FireMath.toolStarted(empty, "ab", "1", 0L)
        s = FireMath.toolStarted(s, "a", "1", 0L)
        val ended = FireMath.turnEnded(s, "a", 0L)
        assertEquals(setOf("ab|1"), ended.tools)
    }

    @Test fun turnEndedOfUnknownConversationChangesNothing() {
        val s = FireMath.turnStarted(empty, "a", 0L)
        assertSame(s, FireMath.turnEnded(s, "zzz", 5L))
    }

    @Test fun idleMinuteSettles() {
        var s = FireMath.turnStarted(empty, "a", 0L)
        repeat(40) { s = FireMath.textArrived(s, "a", 1_000L + it * 100L) }
        s = FireMath.toolStarted(s, "a", "1", 5_000L)
        s = FireMath.toolFinished(s, "a", "1", 6_000L)
        s = FireMath.turnEnded(s, "a", 7_000L)
        assertTrue(FireMath.levelAt(s, 7_000L) > 0.8f)
        assertEquals(0f, FireMath.levelAt(s, 7_000L + 60_000L), 0f)
    }

    @Test fun highSpriteWindowAfterStreamEnds() {
        // heat 0.70 baked at the end of a stream: the HIGH sprite (level 0.30 and up) lasts about 11 s
        val s = heated(0.70f)
        assertEquals(0.3241f, FireMath.levelAt(s, 10_000L), 1e-3f)
        assertEquals(0.2778f, FireMath.levelAt(s, 12_000L), 1e-3f)
        assertTrue(FireMath.levelAt(s, 10_000L) >= FireTuning.HIGH_AT)
        assertTrue(FireMath.levelAt(s, 12_000L) < FireTuning.HIGH_AT)
    }

    @Test fun settledAtMsMatchesLevel() {
        val s = heated(0.70f, 1_000L)
        val at = FireMath.settledAtMs(s)
        assertNotNull(at)
        assertEquals(56_164L, at)
        assertEquals(0f, FireMath.levelAt(s, at!!), 0f)
        assertTrue(FireMath.levelAt(s, at - 1_000L) > 0f)
        // a floor means it never settles
        assertNull(FireMath.settledAtMs(FireMath.turnStarted(empty, "a", 0L)))
        assertNull(FireMath.settledAtMs(FireMath.toolStarted(empty, "a", "1", 0L)))
        // heat already under the cut: settled at once
        assertEquals(77L, FireMath.settledAtMs(heated(0.005f, 77L)))
        assertEquals(0L, FireMath.settledAtMs(empty))
    }

    @Test fun levelStaysBetween0And1() {
        assertEquals(1f, FireMath.levelAt(heated(3f), 0L), 0f)
        assertEquals(0f, FireMath.levelAt(heated(-1f), 0L), 0f)
        var s = FireMath.kick(empty, 5f, 0L)
        assertEquals(1f, s.heat, 0f)
        s = FireMath.kick(s, -3f, 10L)
        assertTrue(s.heat in 0f..1f)
        for (t in 0L..200_000L step 7_000L) assertTrue(FireMath.levelAt(s, t) in 0f..1f)
    }

    @Test fun kickRaisesNeverLowers() {
        assertEquals(0.8f, FireMath.kick(heated(0.8f), 0.3f, 0L).heat, 1e-6f)
        assertEquals(0.6f, FireMath.kick(heated(0.2f), 0.6f, 0L).heat, 1e-6f)
        val nan = heated(0.2f)
        assertSame(nan, FireMath.kick(nan, Float.NaN, 0L))
        // a kick bakes the decay first: 0.8 after 9 s is 0.4, a kick of 0.3 keeps 0.4
        val later = FireMath.kick(heated(0.8f), 0.3f, 9_000L)
        assertEquals(0.4f, later.heat, 1e-5f)
        assertEquals(9_000L, later.heatAtMs)
    }
}
