# Clarity

A voice-to-text Android app for people who have trouble organizing their thoughts out loud.

1. **Dictate.** Tap the mic and talk. Recognition keeps listening across natural pauses instead of cutting off after a few seconds of silence, so you don't have to keep re-tapping the mic mid-thought.
2. **Clean up with AI.** Sends the raw transcript to Anthropic with a prompt that removes filler and false starts, resolves self-corrections ("put it at 3pm — actually no, 4pm") in your favor of the later statement, and flags genuinely ambiguous corrections under "Needs clarification" instead of guessing.
3. **Revise.** Dictate or type further instructions against the cleaned-up text ("make the second paragraph shorter") and it applies just that change, preserving the rest.

Every screen's text can be copied or shared at any point.

## Setup

The app needs your own Anthropic API key (Settings tab → API key). The key is stored on-device with `EncryptedSharedPreferences` and is only ever sent directly to `api.anthropic.com`.

## Structure

- `speech/` — wraps `android.speech.SpeechRecognizer` with a restart-on-result loop for continuous dictation.
- `ai/` — the Anthropic Messages API client, the two prompt templates, and the parser that splits a "Needs clarification" section out of the model's reply.
- `data/` — encrypted local storage for the API key and chosen model.
- `viewmodel/` + `ui/` — a single `ClarityViewModel` driving three Compose screens (Record, Result, Settings).
