package io.github.ironclad.gauntlet

import org.junit.Assert.*
import org.junit.Test

class QuarantineStateEvaluatorTest {

    private val oneHourMs = 60 * 60 * 1000L
    private val twentyFourHoursMs = 24 * oneHourMs
    private val fifteenMinutesMs = 15 * 60 * 1000L

    @Test
    fun testLockedStateRemainsLockedRegardlessOfTime() {
        val state = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.LOCKED,
            startWallTime = 1000L,
            currentWallTime = 1000L + twentyFourHoursMs * 2
        )
        assertEquals(QuarantineTimer.State.LOCKED, state)

        val stateElapsed = QuarantineStateEvaluator.evaluateStateByElapsed(
            currentState = QuarantineTimer.State.LOCKED,
            elapsedMs = twentyFourHoursMs * 2
        )
        assertEquals(QuarantineTimer.State.LOCKED, stateElapsed)
    }

    @Test
    fun testQuarantineCountdownRemainsActiveBefore24Hours() {
        val start = 1_000_000L
        val justBefore24h = start + twentyFourHoursMs - 1000L // 1 second before 24h

        val state = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.QUARANTINE_COUNTDOWN,
            startWallTime = start,
            currentWallTime = justBefore24h
        )
        assertEquals(QuarantineTimer.State.QUARANTINE_COUNTDOWN, state)

        val remaining = QuarantineStateEvaluator.calculateRemainingQuarantine(start, justBefore24h)
        assertEquals(1000L, remaining)

        // Monotonic elapsed check
        val remainingElapsed = QuarantineStateEvaluator.calculateRemainingQuarantineByElapsed(twentyFourHoursMs - 1000L)
        assertEquals(1000L, remainingElapsed)
    }

    @Test
    fun testQuarantineTransitionsToGauntletWindowAt24Hours() {
        val start = 1_000_000L
        val exactly24h = start + twentyFourHoursMs

        val state = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.QUARANTINE_COUNTDOWN,
            startWallTime = start,
            currentWallTime = exactly24h
        )
        assertEquals(QuarantineTimer.State.GAUNTLET_WINDOW, state)

        val remainingGauntlet = QuarantineStateEvaluator.calculateRemainingGauntlet(start, exactly24h)
        assertEquals(fifteenMinutesMs, remainingGauntlet)

        // Monotonic elapsed check
        val stateByElapsed = QuarantineStateEvaluator.evaluateStateByElapsed(
            currentState = QuarantineTimer.State.QUARANTINE_COUNTDOWN,
            elapsedMs = twentyFourHoursMs
        )
        assertEquals(QuarantineTimer.State.GAUNTLET_WINDOW, stateByElapsed)
    }

    @Test
    fun testGauntletWindowExpiresAndRelocksIfUnansweredAfter15Minutes() {
        val start = 1_000_000L
        val afterGauntletExpired = start + twentyFourHoursMs + fifteenMinutesMs + 1000L // 1 second late

        val state = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.GAUNTLET_WINDOW,
            startWallTime = start,
            currentWallTime = afterGauntletExpired
        )
        assertEquals(QuarantineTimer.State.LOCKED, state)

        val remaining = QuarantineStateEvaluator.calculateRemainingGauntlet(start, afterGauntletExpired)
        assertEquals(0L, remaining)

        // Monotonic elapsed check
        val stateByElapsed = QuarantineStateEvaluator.evaluateStateByElapsed(
            currentState = QuarantineTimer.State.GAUNTLET_WINDOW,
            elapsedMs = twentyFourHoursMs + fifteenMinutesMs + 1000L
        )
        assertEquals(QuarantineTimer.State.LOCKED, stateByElapsed)
    }

    @Test
    fun testGraceUnlockExpiresAndRelocksAfterOneHour() {
        val start = 1_000_000L
        val withinGrace = start + twentyFourHoursMs + fifteenMinutesMs + (oneHourMs / 2)

        val activeState = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.GRACE_UNLOCKED,
            startWallTime = start,
            currentWallTime = withinGrace
        )
        assertEquals(QuarantineTimer.State.GRACE_UNLOCKED, activeState)

        val pastGrace = start + twentyFourHoursMs + fifteenMinutesMs + oneHourMs + 1000L
        val expiredState = QuarantineStateEvaluator.evaluateState(
            currentState = QuarantineTimer.State.GRACE_UNLOCKED,
            startWallTime = start,
            currentWallTime = pastGrace
        )
        assertEquals(QuarantineTimer.State.LOCKED, expiredState)

        // Monotonic elapsed check
        val expiredByElapsed = QuarantineStateEvaluator.evaluateStateByElapsed(
            currentState = QuarantineTimer.State.GRACE_UNLOCKED,
            elapsedMs = twentyFourHoursMs + fifteenMinutesMs + oneHourMs + 1000L
        )
        assertEquals(QuarantineTimer.State.LOCKED, expiredByElapsed)
    }
}
