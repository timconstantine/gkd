# Contributing rules

How to add or fix a rule in this folder's subscriptions, the way the established
third-party GKD subscriptions actually do it — from a real device, not from
guesswork.

## 1. Capture a snapshot on the device

Open the screen you want a rule for (the ad, the popup, the prompt), then in
GKD tap **Capture Snapshot** (or the floating snapshot button, if enabled).
This records the full accessibility node tree *and* a screenshot at that
exact moment. That capture is your ground truth — every attribute below
should come from a captured node, never from assuming what an app "probably"
renders.

Capture the same screen a few different ways before trusting a selector:
different countdown values on a splash ad, light/dark theme, a couple of
device sizes. A selector that only works on the one screen you happened to
see is overfit.

## 2. Find the node and read its real attributes

Browse the captured tree, tap the element you want to match (the Skip
button, the Close icon, …), and read off what's actually there. The
selector engine (`gkd-selector`) exposes these attributes on a node:

| Attribute | Meaning |
|---|---|
| `text` | Visible text |
| `desc` | Content description (used for icon-only buttons) |
| `id` | Full view id |
| `vid` | View id, package prefix stripped |
| `name` | Class name |
| `clickable`, `focusable`, `checkable`, `checked`, `editable`, `longClickable`, `visibleToUser` | Booleans |
| `childCount`, `index`, `depth` | Tree position |
| `left`, `top`, `right`, `bottom`, `width`, `height` | Bounds in screen pixels (no `screenWidth`/`screenHeight` — there's no device-independent way to say "top-right corner") |

Comparisons: `=` `!=` `^=` (starts with) `!^=` `*=` (contains) `!*=` `$=`
(ends with) `!$=` `<` `<=` `>` `>=` `~=` (regex match) `!~=`. Combine with
`&&` / `||` and parentheses inside one `[...]` block, e.g.:

```
[clickable=true][visibleToUser=true][text="Skip" || text="Skip Ad"]
```

**String-literal gotcha:** a quoted value inside a selector goes through its
*own* escape parser (`StringScanner.kt`), separate from JSON5's. Only
`\\ \' \" \` \n \r \t \b \xHH \uHHHH` are valid there. Regex shorthand like
`\s` or `\d` is **not** a valid escape at this layer — even though it looks
fine after JSON5 decodes it once, the selector's own parser then chokes on
the bare `\s`. (This shipped broken in this repo's history — see the
`Fix selector compile error` commit.) If you need `\s`/`\d` in a `~=` regex,
you'd need to double-escape through both layers (`\\\\s` in the JSON5
source) — simpler to just avoid regex shorthand classes and match literal
text instead wherever you can.

## 3. Write and test the rule in GKD itself, before committing anything

Add a **local** rule/subscription in GKD and use its editor: it validates
selector syntax as you type, and — with a snapshot loaded — shows you
whether the selector actually matches a node, live. This is the real
feedback loop; don't hand-author a selector and assume it's right. It's also
how you catch an overbroad match (a generic word or single character that
also matches something unrelated elsewhere on screen) before it ships.

## 4. Watch for overbroad matches

A short/common word is tempting but risky on a **global** rule (applies to
every app, all the time). Before adding one, ask what else it could mean:

- `"X"` is also the name of the social platform X (Twitter)
- `"Accept"` / `"Ignore"` are also the answer/decline buttons on Android's
  own incoming-call UI
- bare `"Skip"` matches far more than the one feature you're targeting

Prefer the more specific phrase (`"Skip Ad"`, `"Skip Tutorial"`) or scope the
match with more attributes (small icon + `childCount=0` + a `desc*=` hint)
over a bare generic word.

## 5. Bump `version`

The app's update check is `remoteVersion <= localVersion → no update`. If you
change the file's content and don't bump the top-level `version` field,
nobody who's already subscribed will ever see your change, no matter how
many times you push. Bump it on every content change, however small.

## 6. Validate before you push

At minimum, confirm the file still parses as JSON5:

```bash
npm install json5 --no-save
node -e "require('json5').parse(require('fs').readFileSync('subscriptions/english-ui-rules.gkd.json5','utf8')); console.log('OK')"
```

That catches structural mistakes but **not** selector-level errors (like the
`\s`/`\d` case above) — those only surface when the app itself compiles the
selector, or from actually loading it against a snapshot. When in doubt,
send a snapshot and the exact match you want and open an issue at
https://github.com/timconstantine/gkd/issues.

## 7. Submit the change

Open a pull request against this repo with the updated `.gkd.json5` file.
Describe: what screen/app the rule is for, what node attributes you read off
the snapshot, and why the match is scoped the way it is (not just "add Skip
button").

See [`README.md`](README.md) in this folder for what's already published and
how to add the subscription URL inside GKD.
