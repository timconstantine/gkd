# gkd

<p align="center">
<a href="https://gkd.li/"><img src="https://e.gkd.li/2a0a7787-f2dd-4529-a885-93f3b8c857c3" alt="GKD.LI" width="40%" /></a>
</p>

This is a fork of the upstream [gkd-kit/gkd](https://github.com/gkd-kit/gkd) project, fully translated to English, with a from-scratch **guided rule builder** on top: capture a screen, tap the element you want, fill in a short form — no rule syntax required. See [Building rules in the app](#building-rules-in-the-app) below for how it works, and [subscriptions/README.md](subscriptions/README.md) / [subscriptions/CONTRIBUTING.md](subscriptions/CONTRIBUTING.md) for the curated rule set installed by default.

### Latest build

[![Latest build](https://img.shields.io/github/v/release/timconstantine/gkd?include_prereleases&label=latest%20build&sort=date)](https://github.com/timconstantine/gkd/releases/tag/latest-build)

Grab the newest APK from **[github.com/timconstantine/gkd/releases/tag/latest-build](https://github.com/timconstantine/gkd/releases/tag/latest-build)** (or download it directly: [gkd-latest-build.apk](https://github.com/timconstantine/gkd/releases/download/latest-build/gkd-latest-build.apk)) — these links always point to whatever build most recently passed CI (unsigned debug build, republished automatically after every successful [Build-Apk](.github/workflows/Build-Apk.yml) run).

An Android app for custom screen tapping, built on [advanced selectors](https://gkd.li/guide/selector) + [subscription rules](https://gkd.li/guide/subscription) + [snapshot inspection](https://github.com/gkd-kit/inspect)

Using custom rules, when a specified condition is met on a specified screen (e.g. specific text is present on screen), it taps a specific node or position, or performs another action

- **Shortcut actions**

  Helps you simplify repetitive workflows, such as automatically confirming a computer login on some software

- **Skip flows**

  Some software may show annoying flows on startup; this app can help you tap through and skip them

## Building rules in the app

Upstream GKD expects you to hand-write rules as JSON5 (see [Selector](#selector) below). This fork adds a guided path that doesn't require that:

1. **Capture a screen.** From the floating button, a volume-key press, or the app itself, take a snapshot of whatever screen has the element you want to act on. (Advanced Settings lets you pick which of those triggers "start a capture" uses by default.) The captured screen's elements open automatically afterward.
2. **Pick the element.** The native snapshot inspector lists every node on the captured screen — filterable by text, id, class, clickable/editable, and so on — and tapping one builds its selector for you. "Create rule" carries that selector straight into the rule builder.
3. **Fill in the form.** Name, action (click, long click, enter text, swipe, …), whether it's scoped to this screen or the whole app, and whether it's a global rule or specific to one app — with less common fields (timing, trigger limits, matching options) tucked under "Advanced". No JSON5 required; saving validates the same way the raw text editor does, so a rule built this way is just as valid as a hand-written one.
4. **It's on.** Saving a new rule enables it (and its subscription, if this was the subscription's first rule) immediately — no separate toggle to remember.

**Multi-step rules:** open an existing rule group's details and choose "Add rule to this group" to add another rule alongside it, and pick a **predecessor rule** from a dropdown of the other rules already in that group. The new rule only becomes eligible once the predecessor has already matched — useful for rules that need to fire in sequence (e.g. dismiss a dialog, *then* tap the button it was covering).

You can also **copy** any existing rule's JSON5 to the clipboard and **paste** it back in as a new rule (in the same subscription or a different one) from the same "Add rule" menu used to start a capture or type one in by hand.

### The Rules tab

The bottom-nav tab (formerly "Subscriptions") is now **Rules**, and lists both:

- **Subscriptions** — rule sets from a URL, updated independently (including this fork's default [`english-ui-rules`](subscriptions/english-ui-rules.gkd.json5)).
- **Your rules** — rules you created yourself, whether hand-typed, pasted, or built with the guided form. Marked with a "Your rules" pill so the two kinds are easy to tell apart at a glance. You can create additional local rule collections by name (not just by pasting a subscription URL) from the same "Add subscription" action.

## Disclaimer

**This project is open-sourced under [GPL-3.0-only](/LICENSE). It is for learning and communication purposes only, and must not be used for commercial or illegal purposes**

## Installation

For this fork's build (English UI, extra features, default rule set), see [Latest build](#latest-build) above.

The badges below install the official upstream app instead — same core engine, original (untranslated) UI, none of this fork's additions:

<a href="https://gkd.li/guide/"><img src="https://e.gkd.li/f23b704d-d781-494b-9719-393f95683b89" alt="Download from GKD.LI" width="32%" /></a><a href="https://play.google.com/store/apps/details?id=li.songe.gkd"><img src="https://e.gkd.li/f63fabeb-0342-4961-a46d-cac61b0f8856" alt="Download from Google Play" width="32%" /></a><a href="https://github.com/gkd-kit/gkd/releases"><img src="https://e.gkd.li/c1ef2bb9-7472-46d5-9806-81b4c37e5b4d" alt="Download from GitHub releases" width="32%" /></a>

If you run into issues, please check the [FAQ](https://gkd.li/guide/faq) first

## Screenshots

> The screenshots below are from upstream GKD and predate this fork's English translation and guided rule builder.

|                                                               |                                                               |                                                               |                                                               |
| ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- |
| ![img](https://e.gkd.li/1e8934c1-2303-4182-9ef2-ad4c46882570) | ![img](https://e.gkd.li/01f230d7-9b89-4314-b573-38bd233d22f9) | ![img](https://e.gkd.li/dfa0a782-b21e-473a-96e4-eef27773b71b) | ![img](https://e.gkd.li/641decd1-2e60-4e95-b78c-df38d1d98a4d) |
| ![img](https://e.gkd.li/b216b703-d3de-4798-81ba-29e0ae63264f) | ![img](https://e.gkd.li/76c25ac9-4189-47cd-b40b-b9e72c79b584) | ![img](https://e.gkd.li/7288502e-808b-4d9a-88b5-1085abaa0d46) | ![img](https://e.gkd.li/aa974940-7773-409a-ae84-3c02fee9c770) |

## Subscriptions

Upstream GKD **does not provide any rules by default**. This fork does: it installs [`english-ui-rules`](subscriptions/english-ui-rules.gkd.json5) automatically on first run (see [subscriptions/README.md](subscriptions/README.md)) — you can disable or remove it like any other subscription. Beyond that, you can build your own rules in-app (see [Building rules in the app](#building-rules-in-the-app) above), or obtain remote rules via a subscription link

You can also use [subscription-template](https://github.com/gkd-kit/subscription-template) to quickly build your own remote subscription

A list of third-party subscriptions can be found at <https://github.com/topics/gkd-subscription>

To be added to this list, click the settings icon on the top right of your repository's homepage and add `gkd-subscription` under Topics

<details>
<summary>Example image - adding to Topics (click to expand)</summary>

![image](https://e.gkd.li/9e340459-254f-4ca0-8a44-cc823069e5a7)

</details>

## Selector

A selector similar to a CSS selector, capable of referencing a node's contextual information, making it easier and more precise to find the target node

<https://gkd.li/guide/selector>

[@[vid=\"menu\"] < [vid=\"menu_container\"] - [vid=\"dot_text_layout\"] > [text^=\"ad\"]](https://i.gkd.li/i/14881985?gkd=QFt2aWQ9Im1lbnUiXSA8IFt2aWQ9Im1lbnVfY29udGFpbmVyIl0gLSBbdmlkPSJkb3RfdGV4dF9sYXlvdXQiXSA-IFt0ZXh0Xj0i5bm_5ZGKIl0)

<details>
<summary>Example image - selector path view (click to expand)</summary>

[![image](https://e.gkd.li/a2ae667b-b8c5-4556-a816-37743347b972)](https://i.gkd.li/i/14881985?gkd=QFt2aWQ9Im1lbnUiXSA8IFt2aWQ9Im1lbnVfY29udGFpbmVyIl0gLSBbdmlkPSJkb3RfdGV4dF9sYXlvdXQiXSA-IFt0ZXh0Xj0i5bm_5ZGKIl0)

</details>

## Donate

If GKD has been useful to you, you can support the upstream project via the following link

<https://github.com/lisonge/sponsor>

Or leave a good review on [Google Play](https://play.google.com/store/apps/details?id=li.songe.gkd)
