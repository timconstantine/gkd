package com.tconstantine.clarity.ai

const val NEEDS_CLARIFICATION_HEADING = "Needs clarification"

fun buildCleanupPrompt(dictatedText: String): String = """
    You're cleaning up raw voice dictation into clear, organized text. The dictation is unstructured and may include false starts, filler words, and points where I correct or contradict something I said earlier (e.g., "put it at 3pm — actually no, make it 4pm").

    Rules:

    1. When I contradict an earlier statement, use my later, final version — don't include both.
    2. If a contradiction is ambiguous (unclear whether it's a correction or a new, separate point), flag it briefly at the end under "$NEEDS_CLARIFICATION_HEADING" rather than guessing.
    3. Remove filler, repetition, and false starts. Keep my actual meaning and tone.
    4. Organize into clear paragraphs or a list if the content is naturally list-like.
    5. Don't add information, conclusions, or content I didn't say.
    6. Output only the cleaned-up text itself — no title, heading, preamble, or closing summary, and no notes or commentary about what you did or why. Only add the "$NEEDS_CLARIFICATION_HEADING" section when there's an actual ambiguity to flag; leave it out entirely otherwise.

    Here's the dictation:
    $dictatedText
""".trimIndent()

fun buildRevisionPrompt(existingText: String, revisionInstructions: String): String = """
    You're revising a previously cleaned-up piece of text based on new instructions I'm giving you now. I'll provide the existing text and then tell you what to change.

    Rules:

    1. Apply only the changes I indicate — don't rewrite or "improve" parts I didn't ask you to touch.
    2. If a new instruction contradicts something in the existing text, my new instruction wins — update the text to reflect it, don't keep both versions.
    3. If it's unclear whether I'm asking for a small edit or a full rewrite of a section, make the smaller, more conservative change and flag the ambiguity at the end under "$NEEDS_CLARIFICATION_HEADING".
    4. Preserve the tone, structure, and formatting of the original unless I specifically ask you to change those.
    5. Don't add information, conclusions, or content I didn't say — either in the original or in the revision instructions.
    6. Output only the revised text itself — no title, heading, preamble, or closing summary, and no notes or commentary about what you changed or why. Only add the "$NEEDS_CLARIFICATION_HEADING" section when there's an actual ambiguity to flag; leave it out entirely otherwise.

    Existing text:
    $existingText

    Revisions:
    $revisionInstructions
""".trimIndent()
