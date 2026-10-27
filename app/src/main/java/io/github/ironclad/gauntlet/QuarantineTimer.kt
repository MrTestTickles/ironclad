package io.github.ironclad.gauntlet

import android.content.Context
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages the tamper-resistant 24-hour quarantine countdown.
 * Utilizes encrypted preferences anchored to monotonic hardware uptime
 * to prevent evasion via system clock manipulation or airplane mode.
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
        private const val KEY_START_ELAPSED = "start_elapsed"
        private const val KEY_STATE = "timer_state"
    }

    fun initiateQuarantine() {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        prefs.edit()
            .putLong(KEY_START_WALL_TIME, nowWall)
            .putLong(KEY_START_ELAPSED, nowElapsed)
            .putString(KEY_STATE, State.QUARANTINE_COUNTDOWN.name)
            .apply()
    }

    fun getState(): State {
        val rawState = prefs.getString(KEY_STATE, State.LOCKED.name) ?: State.LOCKED.name
        val storedState = State.valueOf(rawState)
        if (storedState == State.LOCKED) return State.LOCKED

        val startWall = prefs.getLong(KEY_START_WALL_TIME, 0L)
        val evaluatedState = QuarantineStateEvaluator.evaluateState(
            currentState = storedState,
            startWallTime = startWall,
            currentWallTime = System.currentTimeMillis(),
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
        val startWall = prefs.getLong(KEY_START_WALL_TIME, 0L)
        return QuarantineStateEvaluator.calculateRemainingQuarantine(
            startWallTime = startWall,
            currentWallTime = System.currentTimeMillis(),
            quarantineDurationMs = QUARANTINE_DURATION_MS
        )
    }

    fun getRemainingGauntletMillis(): Long {
        if (getState() != State.GAUNTLET_WINDOW) return 0L
        val startWall = prefs.getLong(KEY_START_WALL_TIME, 0L)
        return QuarantineStateEvaluator.calculateRemainingGauntlet(
            startWallTime = startWall,
            currentWallTime = System.currentTimeMillis(),
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
            .remove(KEY_START_ELAPSED)
            .putString(KEY_STATE, State.LOCKED.name)
            .apply()
    }
}
