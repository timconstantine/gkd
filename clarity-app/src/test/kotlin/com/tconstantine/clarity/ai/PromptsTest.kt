package com.tconstantine.clarity.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test
    fun `cleanup prompt embeds the dictated text verbatim`() {
        val dictated = "put it at 3pm actually no make it 4pm"
        val prompt = buildCleanupPrompt(dictated)

        assertTrue("prompt should end with the raw dictation so nothing is appended after it", prompt.endsWith(dictated))
        assertTrue(prompt.contains("later, final version"))
        assertTrue(prompt.contains(NEEDS_CLARIFICATION_HEADING))
    }

    @Test
    fun `revision prompt embeds both the existing text and the new instructions`() {
        val existing = "Meet at 4pm in the lobby."
        val instructions = "Change the lobby to the rooftop."
        val prompt = buildRevisionPrompt(existing, instructions)

        assertTrue(prompt.contains(existing))
        assertTrue("prompt should end with the revision instructions so nothing is appended after them", prompt.endsWith(instructions))
        assertTrue(prompt.contains("my new instruction wins"))
    }
}
