# Subscriptions

Rule subscriptions maintained in this repo, for use with GKD's "add subscription" feature.

## english-ui-rules.gkd.json5

An original English-language rule set for dismissing common UI annoyances in
international/English apps — splash-screen ads, full-screen/interstitial ads,
update nag dialogs, "rate us" popups, notification/permission prompts, cookie
consent banners, and onboarding/promo popups.

**This is not a translation of an existing subscription.** Most third-party
GKD subscriptions (see [github.com/topics/gkd-subscription](https://github.com/topics/gkd-subscription))
match literal on-screen Chinese text, because they're written for Chinese-only
apps (WeChat, Taobao, Xiaohongshu, etc.). Translating those match strings to
English would just break them — the apps they target still render Chinese
text regardless of the rule file's language. So instead, this file is a
freshly authored rule set built around the generic English UI text patterns
(`Skip`, `Not Now`, `No Thanks`, `Accept All`, …) that show up across many
English-language/international apps.

**Status:** best-effort, not verified against a real device (this repo's
automation environment has no Android device or emulator). Only the
"Splash / Open Screen Ads" and "Full-Screen & Interstitial Ads" categories
are enabled by default; the rest (update prompts, rating prompts,
notification/permission prompts, cookie consent, onboarding/promo) are
included but disabled by default since text-pattern matching is inherently
heuristic and more prone to false positives — enable only what you want.

### How to use it

In the GKD app: **Subscriptions → Add subscription** and paste this URL:

```
https://raw.githubusercontent.com/timconstantine/gkd/main/subscriptions/english-ui-rules.gkd.json5
```

Then enable/disable individual rule groups under **Global Rules**, and use
GKD's **Capture Snapshot** feature to debug a rule against a specific app
screen if something isn't matching (or is matching when it shouldn't).

Found a bad match or want a new category covered? Open an issue at
https://github.com/timconstantine/gkd/issues.

### Want to add or fix a rule?

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the actual workflow (capture a
snapshot on-device, read the real node attributes, test the selector live in
GKD before committing it) and the selector syntax reference.
