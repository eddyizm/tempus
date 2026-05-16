package com.cappielloantonio.tempo.service

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SleepTimerManager].
 *
 * A fake [SleepTimerManager.Scheduler] and a fake clock are injected so that no real
 * [android.os.Handler] or [android.os.Looper] is needed, keeping these as pure JVM tests.
 */
class SleepTimerManagerTest {

    // ---- Fake clock ----------------------------------------------------------
    private var fakeNowMs: Long = 0L

    // ---- Fake scheduler that records and immediately fires on demand ----------
    private val scheduledActions = mutableListOf<Pair<Long, Runnable>>()
    private val cancelledActions = mutableListOf<Runnable>()

    private val fakeScheduler = object : SleepTimerManager.Scheduler {
        override fun schedule(delayMs: Long, action: Runnable) {
            scheduledActions.add(delayMs to action)
        }
        override fun cancel(action: Runnable) {
            cancelledActions.add(action)
            scheduledActions.removeAll { it.second === action }
        }
    }

    private lateinit var manager: SleepTimerManager

    @Before
    fun setUp() {
        fakeNowMs = 0L
        scheduledActions.clear()
        cancelledActions.clear()
        manager = SleepTimerManager(clock = { fakeNowMs }, scheduler = fakeScheduler)
    }

    // ---- Initial state -------------------------------------------------------

    @Test
    fun `initial state is not active`() {
        assertFalse(manager.isActive)
    }

    @Test
    fun `initial isEndOfSong is false`() {
        assertFalse(manager.isEndOfSong)
    }

    @Test
    fun `getRemainingMs returns -1 when no timer is set`() {
        assertEquals(-1L, manager.getRemainingMs())
    }

    // ---- startCountdown ------------------------------------------------------

    @Test
    fun `startCountdown sets isActive to true`() {
        manager.startCountdown(60_000L) {}
        assertTrue(manager.isActive)
    }

    @Test
    fun `startCountdown does not set isEndOfSong`() {
        manager.startCountdown(60_000L) {}
        assertFalse(manager.isEndOfSong)
    }

    @Test
    fun `startCountdown schedules exactly one action`() {
        manager.startCountdown(30_000L) {}
        assertEquals(1, scheduledActions.size)
        assertEquals(30_000L, scheduledActions[0].first)
    }

    @Test
    fun `getRemainingMs reflects elapsed clock time`() {
        fakeNowMs = 0L
        manager.startCountdown(60_000L) {}
        fakeNowMs = 20_000L
        assertEquals(40_000L, manager.getRemainingMs())
    }

    @Test
    fun `getRemainingMs returns 0 when time is up but runnable not yet fired`() {
        fakeNowMs = 0L
        manager.startCountdown(60_000L) {}
        fakeNowMs = 70_000L
        assertEquals(0L, manager.getRemainingMs())
    }

    @Test
    fun `startCountdown callback is invoked when scheduled runnable fires`() {
        var fired = false
        manager.startCountdown(5_000L) { fired = true }
        // Simulate the Handler firing the runnable
        scheduledActions[0].second.run()
        assertTrue(fired)
    }

    @Test
    fun `isActive becomes false after countdown runnable fires`() {
        manager.startCountdown(5_000L) {}
        scheduledActions[0].second.run()
        assertFalse(manager.isActive)
    }

    @Test
    fun `startCountdown cancels existing timer before starting new one`() {
        manager.startCountdown(60_000L) {}
        manager.startCountdown(30_000L) {}
        // The first runnable should have been cancelled
        assertEquals(1, cancelledActions.size)
        // Only one active scheduled action
        assertEquals(1, scheduledActions.size)
        assertEquals(30_000L, scheduledActions[0].first)
    }

    // ---- startEndOfSong ------------------------------------------------------

    @Test
    fun `startEndOfSong sets isActive to true`() {
        manager.startEndOfSong {}
        assertTrue(manager.isActive)
    }

    @Test
    fun `startEndOfSong sets isEndOfSong to true`() {
        manager.startEndOfSong {}
        assertTrue(manager.isEndOfSong)
    }

    @Test
    fun `getRemainingMs returns -1 in end-of-song mode`() {
        manager.startEndOfSong {}
        assertEquals(-1L, manager.getRemainingMs())
    }

    @Test
    fun `startEndOfSong does not schedule any runnable`() {
        manager.startEndOfSong {}
        assertTrue(scheduledActions.isEmpty())
    }

    // ---- cancelTimer ---------------------------------------------------------

    @Test
    fun `cancelTimer clears isActive`() {
        manager.startCountdown(60_000L) {}
        manager.cancelTimer()
        assertFalse(manager.isActive)
    }

    @Test
    fun `cancelTimer clears isEndOfSong`() {
        manager.startEndOfSong {}
        manager.cancelTimer()
        assertFalse(manager.isEndOfSong)
    }

    @Test
    fun `cancelTimer removes scheduled runnable`() {
        manager.startCountdown(60_000L) {}
        manager.cancelTimer()
        assertTrue(scheduledActions.isEmpty())
        assertEquals(1, cancelledActions.size)
    }

    @Test
    fun `cancelTimer is safe when no timer is active`() {
        // Should not throw
        manager.cancelTimer()
        assertFalse(manager.isActive)
    }

    // ---- notifyEndOfSong -----------------------------------------------------

    @Test
    fun `notifyEndOfSong triggers callback in end-of-song mode`() {
        var called = false
        manager.startEndOfSong { called = true }
        manager.notifyEndOfSong()
        assertTrue(called)
    }

    @Test
    fun `notifyEndOfSong resets state after firing`() {
        manager.startEndOfSong {}
        manager.notifyEndOfSong()
        assertFalse(manager.isActive)
        assertFalse(manager.isEndOfSong)
    }

    @Test
    fun `notifyEndOfSong does nothing when countdown mode is active`() {
        var called = false
        manager.startCountdown(60_000L) { called = true }
        manager.notifyEndOfSong()
        assertFalse(called)
        assertTrue(manager.isActive)
    }

    @Test
    fun `notifyEndOfSong does nothing when no timer is set`() {
        // Should not throw and state remains unchanged
        manager.notifyEndOfSong()
        assertFalse(manager.isActive)
    }
}
