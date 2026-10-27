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
        if (currentState == QuarantineTimer.State.LOCKED) return QuarantineTimer.State.LOCKED
        val elapsed = currentWallTime - startWallTime

        return when (currentState) {
            QuarantineTimer.State.QUARANTINE_COUNTDOWN -> {
                if (elapsed >= quarantineDurationMs) {
                    val windowElapsed = elapsed - quarantineDurationMs
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
                val windowElapsed = elapsed - quarantineDurationMs
                if (windowElapsed >= gauntletWindowMs) {
                    QuarantineTimer.State.LOCKED
                } else {
                    QuarantineTimer.State.GAUNTLET_WINDOW
                }
            }
            QuarantineTimer.State.GRACE_UNLOCKED -> {
                val graceElapsed = elapsed - (quarantineDurationMs + gauntletWindowMs)
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
        val remaining = quarantineDurationMs - (currentWallTime - startWallTime)
        return remaining.coerceAtLeast(0L)
    }

    fun calculateRemainingGauntlet(
        startWallTime: Long,
        currentWallTime: Long,
        quarantineDurationMs: Long = QuarantineTimer.QUARANTINE_DURATION_MS,
        gauntletWindowMs: Long = QuarantineTimer.GAUNTLET_WINDOW_MS
    ): Long {
        val elapsed = currentWallTime - startWallTime
        val remaining = (quarantineDurationMs + gauntletWindowMs) - elapsed
        return remaining.coerceAtLeast(0L)
    }
}
