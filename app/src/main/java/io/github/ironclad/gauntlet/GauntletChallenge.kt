package io.github.ironclad.gauntlet

import java.security.SecureRandom

/**
 * Handles the 120-character randomized typing challenge.
 * Enforces zero backspace tolerance and resets upon any mistake.
 */
class GauntletChallenge(val targetLength: Int = 120) {

    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val random = SecureRandom()

    val targetString: String = generateRandomString(targetLength)
    var currentProgress: String = ""
        private set

    var totalMistakes: Int = 0
        private set

    private fun generateRandomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(alphabet[random.nextInt(alphabet.length)])
        }
        return sb.toString()
    }

    /**
     * Feeds the next typed character into the challenge.
     * @return true if accepted, false if it was a typo (causing a reset).
     */
    fun appendCharacter(c: Char): Boolean {
        val expectedChar = targetString[currentProgress.length]
        return if (c == expectedChar) {
            currentProgress += c
            true
        } else {
            // Typo committed! Penalize by wiping progress back to zero
            totalMistakes++
            currentProgress = ""
            false
        }
    }

    fun isCompleted(): Boolean {
        return currentProgress == targetString
    }

    fun getNextExpectedCharacter(): Char? {
        return if (currentProgress.length < targetString.length) {
            targetString[currentProgress.length]
        } else null
    }

    fun getProgressPercent(): Float {
        return currentProgress.length.toFloat() / targetLength.toFloat()
    }
}
