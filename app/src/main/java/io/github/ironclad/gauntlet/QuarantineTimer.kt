package io.github.ironclad.gauntlet

import android.content.Context
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the tamper-resistant 24-hour quarantine countdown.
 * Utilizes encrypted preferences anchored to monotonic hardware uptime (SystemClock.elapsedRealtime())
 * across deep sleep and device reboots, completely eliminating evasion via system clock manipulation.
 */
class QuarantineTimer(context: Context) {

    enum class State {
        LOCKED,                 // Blocker active, no unlock requested
        QUARANTINE_COUNTDOWN,   // 24-hour compulsory cooling-off running
        GAUNTLET_WINDOW,        // 15-minute challenge window open
        GRACE_UNLOCKED          // Challenge passed, 1-hour temporary unlock
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ironclad_quarantine_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        const val QUARANTINE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours
        const val GAUNTLET_WINDOW_MS = 15 * 60 * 1000L           // 15 Minutes
        const val GRACE_DURATION_MS = 60 * 60 * 1000L            // 1 Hour

        private const val KEY_START_WALL_TIME = "start_wall_time"
        private const val KEY_LAST_ELAPSED = "last_elapsed"
        private const val KEY_ACCUMULATED_ELAPSED = "accumulated_elapsed"
        private const val KEY_STATE = "timer_state"
    }

    fun initiateQuarantine() {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        prefs.edit()
            .putLong(KEY_START_WALL_TIME, nowWall)
            .putLong(KEY_LAST_ELAPSED, nowElapsed)
            .putLong(KEY_ACCUMULATED_ELAPSED, 0L)
            .putString(KEY_STATE, State.QUARANTINE_COUNTDOWN.name)
            .apply()
    }

    /**
     * Updates and retrieves the true monotonic hardware elapsed milliseconds.
     * Prevents clock manipulation: if user sets Android date forward,
     * SystemClock.elapsedRealtime() does not jump, so accumulated time only tracks true physics.
     * Across reboots, the delta is preserved and added upon subsequent measurement.
     */
    private fun updateAndGetAccumulatedElapsed(): Long {
        val rawState = prefs.getString(KEY_STATE, State.LOCKED.name) ?: State.LOCKED.name
        if (State.valueOf(rawState) == State.LOCKED) return 0L

        val nowElapsed = SystemClock.elapsedRealtime()
        val lastElapsed = prefs.getLong(KEY_LAST_ELAPSED, nowElapsed)
        val delta = if (nowElapsed >= lastElapsed) {
            nowElapsed - lastElapsed
        } else {
            // Device rebooted: add the current boot session's uptime
            nowElapsed
        }

        val accumulated = prefs.getLong(KEY_ACCUMULATED_ELAPSED, 0L) + delta
        prefs.edit()
            .putLong(KEY_ACCUMULATED_ELAPSED, accumulated)
            .putLong(KEY_LAST_ELAPSED, nowElapsed)
            .apply()

        return accumulated
    }

    fun getState(): State {
        val rawState = prefs.getString(KEY_STATE, State.LOCKED.name) ?: State.LOCKED.name
        val storedState = State.valueOf(rawState)
        if (storedState == State.LOCKED) return State.LOCKED

        val accumulated = updateAndGetAccumulatedElapsed()
        val evaluatedState = QuarantineStateEvaluator.evaluateStateByElapsed(
            currentState = storedState,
            elapsedMs = accumulated,
            quarantineDurationMs = QUARANTINE_DURATION_MS,
            gauntletWindowMs = GAUNTLET_WINDOW_MS,
            graceDurationMs = GRACE_DURATION_MS
        )

        if (evaluatedState != storedState) {
            if (evaluatedState == State.LOCKED) {
                resetToLocked()
            } else {
                prefs.edit().putString(KEY_STATE, evaluatedState.name).apply()
            }
        }

        return evaluatedState
    }

    fun getRemainingQuarantineMillis(): Long {
        if (getState() != State.QUARANTINE_COUNTDOWN) return 0L
        val accumulated = updateAndGetAccumulatedElapsed()
        return QuarantineStateEvaluator.calculateRemainingQuarantineByElapsed(
            elapsedMs = accumulated,
            quarantineDurationMs = QUARANTINE_DURATION_MS
        )
    }

    fun getRemainingGauntletMillis(): Long {
        if (getState() != State.GAUNTLET_WINDOW) return 0L
        val accumulated = updateAndGetAccumulatedElapsed()
        return QuarantineStateEvaluator.calculateRemainingGauntletByElapsed(
            elapsedMs = accumulated,
            quarantineDurationMs = QUARANTINE_DURATION_MS,
            gauntletWindowMs = GAUNTLET_WINDOW_MS
        )
    }

    fun markGauntletPassed() {
        prefs.edit().putString(KEY_STATE, State.GRACE_UNLOCKED.name).apply()
    }

    fun resetToLocked() {
        prefs.edit()
            .remove(KEY_START_WALL_TIME)
            .remove(KEY_LAST_ELAPSED)
            .remove(KEY_ACCUMULATED_ELAPSED)
            .putString(KEY_STATE, State.LOCKED.name)
            .apply()
    }
}
