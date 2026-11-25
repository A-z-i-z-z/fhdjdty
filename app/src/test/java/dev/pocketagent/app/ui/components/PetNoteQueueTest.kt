package dev.pocketagent.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The rules of the note queue (the clock is a parameter, so time is whatever the test says). */
class PetNoteQueueTest {
    private var now = 0L
    private val queue = PetNoteQueue { now }

    private fun attached(): PetNoteQueue = queue.also { it.attach() }

    @Test
    fun oneVisibleAtATime() {
        attached()
        val a = queue.offer("first", PetNoteKind.INFO)
        queue.offer("second", PetNoteKind.INFO)
        queue.offer("third", PetNoteKind.SUCCESS)
        assertSame(a, queue.visible)
        assertEquals(2, queue.waitingCount)
        queue.dismissVisible()
        assertEquals("second", queue.visible?.text)
        assertEquals(1, queue.waitingCount)
    }

    @Test
    fun waitingCapOfThreeDropsOldestWithoutAction() {
        attached()
        queue.offer("visible", PetNoteKind.INFO)
        queue.offer("w1", PetNoteKind.INFO, "Undo") { }
        queue.offer("w2", PetNoteKind.INFO)
        queue.offer("w3", PetNoteKind.INFO)
        assertEquals(3, queue.waitingCount)
        queue.offer("w4", PetNoteKind.INFO)
        assertEquals(3, queue.waitingCount)
        // w1 has an action and stays; w2 was the oldest without one
        queue.dismissVisible()
        assertEquals("w1", queue.visible?.text)
        queue.dismissVisible()
        assertEquals("w3", queue.visible?.text)
        queue.dismissVisible()
        assertEquals("w4", queue.visible?.text)
        queue.dismissVisible()
        assertNull(queue.visible)
    }

    @Test
    fun duplicateWithinASecondIgnored() {
        attached()
        assertNotNull(queue.offer("same", PetNoteKind.INFO))
        now = 500
        assertNull(queue.offer("same", PetNoteKind.INFO))
        // another kind is another note
        assertNotNull(queue.offer("same", PetNoteKind.ERROR))
        now = 1700
        assertNotNull(queue.offer("same", PetNoteKind.ERROR))
        assertEquals(2, queue.waitingCount)
    }

    @Test
    fun displayMillisFormula() {
        assertEquals(2500, PetNoteQueue.displayMillis("", false))
        assertEquals(2500, PetNoteQueue.displayMillis("hi", false))
        assertEquals(3800, PetNoteQueue.displayMillis("x".repeat(40), false))
        assertEquals(5000, PetNoteQueue.displayMillis("x".repeat(80), false))
        assertEquals(5000, PetNoteQueue.displayMillis("x".repeat(300), false))
        assertEquals(6000, PetNoteQueue.displayMillis("hi", true))
    }

    @Test
    fun expiresAfterItsTimeAndPromotesTheNext() {
        attached()
        queue.offer("one", PetNoteKind.INFO)
        queue.offer("two", PetNoteKind.SUCCESS)
        now = 2400
        queue.tick()
        assertEquals("one", queue.visible?.text)
        now = 2600
        queue.tick()
        assertEquals("two", queue.visible?.text)
    }

    @Test
    fun pausedWhileOverlayOpen() {
        attached()
        queue.offer("note", PetNoteKind.INFO)
        now = 1000
        queue.overlayOpened()
        now = 100_000
        queue.tick()
        assertEquals("note", queue.visible?.text)
        queue.overlayClosed()
        now = 100_900
        queue.tick()
        assertEquals("note", queue.visible?.text)
        now = 102_000
        queue.tick()
        assertNull(queue.visible)
    }

    @Test
    fun staleNotesDroppedOnAttach() {
        queue.offer("old", PetNoteKind.INFO)
        now = 5000
        queue.offer("recent", PetNoteKind.SUCCESS)
        assertNull("nothing is shown before a host exists", queue.visible)
        now = 9000
        queue.attach()
        assertEquals("recent", queue.visible?.text)
        assertEquals(0, queue.waitingCount)
    }

    @Test
    fun secondHostLeavingKeepsTheFirstOneShowingNotes() {
        val a = Any()
        val b = Any()
        queue.attach(a)
        queue.attach(b)
        assertSame("the newest host draws", b, queue.activeHost)
        queue.detach(b)
        assertSame(a, queue.activeHost)
        queue.offer("after B left", PetNoteKind.ERROR)
        assertEquals("after B left", queue.visible?.text)
    }

    @Test
    fun firstHostLeavingFirstKeepsTheSecondOneShowingNotes() {
        val a = Any()
        val b = Any()
        queue.attach(a)
        queue.attach(b)
        queue.detach(a)
        assertSame(b, queue.activeHost)
        queue.offer("after A left", PetNoteKind.ERROR)
        assertEquals("after A left", queue.visible?.text)
    }

    @Test
    fun visibleNoteSurvivesOneOfTwoHostsLeavingAndGoesBackOnlyWhenNoneIsLeft() {
        val a = Any()
        val b = Any()
        queue.attach(a)
        queue.attach(b)
        queue.offer("shown", PetNoteKind.INFO)
        queue.detach(b)
        assertEquals("shown", queue.visible?.text)
        assertEquals(0, queue.waitingCount)
        queue.detach(a)
        assertNull(queue.visible)
        assertEquals(1, queue.waitingCount)
        queue.attach()
        assertEquals("shown", queue.visible?.text)
    }

    @Test
    fun detachingAnUnknownHostChangesNothing() {
        val a = Any()
        queue.attach(a)
        queue.detach(Any())
        assertSame(a, queue.activeHost)
        queue.offer("still shown", PetNoteKind.INFO)
        assertEquals("still shown", queue.visible?.text)
    }

    @Test
    fun clearEmptiesAll() {
        attached()
        queue.offer("a", PetNoteKind.INFO)
        queue.offer("b", PetNoteKind.INFO)
        queue.clear()
        assertNull(queue.visible)
        assertEquals(0, queue.waitingCount)
        now = 5000
        queue.offer("c", PetNoteKind.INFO)
        assertEquals("c", queue.visible?.text)
    }
}
