package com.tconstantine.clarity.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTextResultTest {
    @Test
    fun `response with no clarification section returns the whole trimmed text`() {
        val response = "  This is the cleaned up text.\nSecond paragraph.  "

        val result = parseAiTextResult(response)

        assertEquals("This is the cleaned up text.\nSecond paragraph.", result.mainText)
        assertEquals(emptyList<String>(), result.clarifications)
    }

    @Test
    fun `bulleted clarification section is split out from the main text`() {
        val response = """
            Meet at 4pm in the lobby.

            Needs clarification
            - Not sure if the 5pm mention was a correction or a separate meeting.
            - Unclear who is bringing the projector.
        """.trimIndent()

        val result = parseAiTextResult(response)

        assertEquals("Meet at 4pm in the lobby.", result.mainText)
        assertEquals(
            listOf(
                "Not sure if the 5pm mention was a correction or a separate meeting.",
                "Unclear who is bringing the projector.",
            ),
            result.clarifications,
        )
    }

    @Test
    fun `markdown heading variants for the clarification section are recognized`() {
        val response = "Body text.\n\n**Needs clarification:**\n1. First item.\n2. Second item."

        val result = parseAiTextResult(response)

        assertEquals("Body text.", result.mainText)
        assertEquals(listOf("First item.", "Second item."), result.clarifications)
    }
}
