package io.github.ironclad.gauntlet

import org.junit.Assert.*
import org.junit.Test

class GauntletChallengeTest {

    @Test
    fun testDefaultTargetLengthIs120() {
        val gauntlet = GauntletChallenge()
        assertEquals(120, gauntlet.targetLength)
        assertEquals(120, gauntlet.targetString.length)
        assertEquals(0, gauntlet.currentProgress.length)
        assertEquals(0, gauntlet.totalMistakes)
        assertFalse(gauntlet.isCompleted())
    }

    @Test
    fun testTargetExcludesAmbiguousLookalikeCharacters() {
        // Run multiple generations to ensure alphabet coverage
        val ambiguousChars = setOf('0', 'O', '1', 'l', 'I')
        for (i in 0 until 50) {
            val gauntlet = GauntletChallenge(120)
            for (c in gauntlet.targetString) {
                assertFalse("Target string should not contain ambiguous char: $c", ambiguousChars.contains(c))
            }
        }
    }

    @Test
    fun testSequentialCorrectTypingLeadsToCompletion() {
        val gauntlet = GauntletChallenge(10)
        val target = gauntlet.targetString

        for (i in 0 until 10) {
            val char = target[i]
            assertEquals(char, gauntlet.getNextExpectedCharacter())
            val accepted = gauntlet.appendCharacter(char)
            assertTrue("Expected character $char to be accepted", accepted)
            assertEquals(i + 1, gauntlet.currentProgress.length)
        }

        assertTrue(gauntlet.isCompleted())
        assertNull(gauntlet.getNextExpectedCharacter())
        assertEquals(1.0f, gauntlet.getProgressPercent(), 0.001f)
        assertEquals(0, gauntlet.totalMistakes)
    }

    @Test
    fun testTypoResetsProgressImmediatelyToZero() {
        val gauntlet = GauntletChallenge(10)
        val target = gauntlet.targetString

        // Correctly type first 5 characters
        for (i in 0 until 5) {
            gauntlet.appendCharacter(target[i])
        }
        assertEquals(5, gauntlet.currentProgress.length)
        assertEquals(0.5f, gauntlet.getProgressPercent(), 0.001f)

        // Find a character that is deliberately wrong
        val expected = target[5]
        val wrongChar = if (expected == 'A') 'B' else 'A'

        val accepted = gauntlet.appendCharacter(wrongChar)
        assertFalse("Typo must be rejected", accepted)
        assertEquals("Progress must be completely reset to empty string after typo", 0, gauntlet.currentProgress.length)
        assertEquals(1, gauntlet.totalMistakes)
        assertEquals(0.0f, gauntlet.getProgressPercent(), 0.001f)

        // Ensure user can start over from index 0
        assertEquals(target[0], gauntlet.getNextExpectedCharacter())
        assertTrue(gauntlet.appendCharacter(target[0]))
        assertEquals(1, gauntlet.currentProgress.length)
    }
}
