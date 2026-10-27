package io.github.ironclad.gauntlet

/**
 * Pure deterministic state evaluation for the 24-hour quarantine protocol.
 * Decoupled from Android system APIs to ensure strict mathematical testability.
 */
object QuarantineStateEvaluator {

    fun evaluateState(
        currentState: QuarantineTimer.State,
        startWallTime: Long,
        currentWallTime: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS,
        gauntletWindowMs: Long = QuarantineTimer.GAUNTLET_WINDOW_MS,
        graceDurationMs: Long = QuarantineTimer.GRACE_DURATION_MS
    ): QuarantineTimer.State {
        val elapsed = currentWallTime - startWallTime
        return evaluateStateByElapsed(
            currentState = currentState,
            elapsedMs = elapsed,
            quarantineDurationMs = quarantineDurationMs,
            gauntletWindowMs = gauntletWindowMs,
            graceDurationMs = graceDurationMs
        )
    }

    fun evaluateStateByElapsed(
        currentState: QuarantineTimer.State,
        elapsedMs: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS,
        gauntletWindowMs: Long = QuarantineTimer.GAUNTLET_WINDOW_MS,
        graceDurationMs: Long = QuarantineTimer.GRACE_DURATION_MS
    ): QuarantineTimer.State {
        if (currentState == QuarantineTimer.State.LOCKED) return QuarantineTimer.State.LOCKED

        return when (currentState) {
            QuarantineTimer.State.QUARANTINE_COUNTDOWN -> {
                if (elapsedMs >= quarantineDurationMs) {
                    val windowElapsed = elapsedMs - quarantineDurationMs
                    if (windowElapsed >= gauntletWindowMs) {
                        QuarantineTimer.State.LOCKED
                    } else {
                        QuarantineTimer.State.GAUNTLET_WINDOW
                    }
                } else {
                    QuarantineTimer.State.QUARANTINE_COUNTDOWN
                }
            }
            QuarantineTimer.State.GAUNTLET_WINDOW -> {
                val windowElapsed = elapsedMs - quarantineDurationMs
                if (windowElapsed >= gauntletWindowMs) {
                    QuarantineTimer.State.LOCKED
                } else {
                    QuarantineTimer.State.GAUNTLET_WINDOW
                }
            }
            QuarantineTimer.State.GRACE_UNLOCKED -> {
                val graceElapsed = elapsedMs - (quarantineDurationMs + gauntletWindowMs)
                if (graceElapsed >= graceDurationMs) {
                    QuarantineTimer.State.LOCKED
                } else {
                    QuarantineTimer.State.GRACE_UNLOCKED
                }
            }
            else -> QuarantineTimer.State.LOCKED
        }
    }

    fun calculateRemainingQuarantine(
        startWallTime: Long,
        currentWallTime: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS
    ): Long {
        return calculateRemainingQuarantineByElapsed(
            elapsedMs = currentWallTime - startWallTime,
            quarantineDurationMs = quarantineDurationMs
        )
    }

    fun calculateRemainingQuarantineByElapsed(
        elapsedMs: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS
    ): Long {
        val remaining = quarantineDurationMs - elapsedMs
        return remaining.coerceAtLeast(0L)
    }

    fun calculateRemainingGauntlet(
        startWallTime: Long,
        currentWallTime: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS,
        gauntletWindowMs: Long = QuarantineTimer.GAUNTLET_WINDOW_MS
    ): Long {
        return calculateRemainingGauntletByElapsed(
            elapsedMs = currentWallTime - startWallTime,
            quarantineDurationMs = quarantineDurationMs,
            gauntletWindowMs = gauntletWindowMs
        )
    }

    fun calculateRemainingGauntletByElapsed(
        elapsedMs: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS,
        gauntletWindowMs: Long = QuarantineTimer.GAUNTLET_WINDOW_MS
    ): Long {
        val remaining = (quarantineDurationMs + gauntletWindowMs) - elapsedMs
        return remaining.coerceAtLeast(0L)
    }
}
