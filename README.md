# gkd

<p align="center">
<a href="https://gkd.li/"><img src="https://e.gkd.li/2a0a7787-f2dd-4529-a885-93f3b8c857c3" alt="GKD.LI" width="40%" /></a>
</p>

An Android app for custom screen tapping, built on [advanced selectors](https://gkd.li/guide/selector) + [subscription rules](https://gkd.li/guide/subscription) + [snapshot inspection](https://github.com/gkd-kit/inspect)

Using custom rules, when a specified condition is met on a specified screen (e.g. specific text is present on screen), it taps a specific node or position, or performs another action

- **Shortcut actions**

  Helps you simplify repetitive workflows, such as automatically confirming a computer login on some software

- **Skip flows**

  Some software may show annoying flows on startup; this app can help you tap through and skip them

## Disclaimer

**This project is open-sourced under [GPL-3.0-only](/LICENSE). It is for learning and communication purposes only, and must not be used for commercial or illegal purposes**

## Installation

<a href="https://gkd.li/guide/"><img src="https://e.gkd.li/f23b704d-d781-494b-9719-393f95683b89" alt="Download from GKD.LI" width="32%" /></a><a href="https://play.google.com/store/apps/details?id=li.songe.gkd"><img src="https://e.gkd.li/f63fabeb-0342-4961-a46d-cac61b0f8856" alt="Download from Google Play" width="32%" /></a><a href="https://github.com/gkd-kit/gkd/releases"><img src="https://e.gkd.li/c1ef2bb9-7472-46d5-9806-81b4c37e5b4d" alt="Download from GitHub releases" width="32%" /></a>

If you run into issues, please check the [FAQ](https://gkd.li/guide/faq) first

## Screenshots

|                                                               |                                                               |                                                               |                                                               |
| ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------- |
| ![img](https://e.gkd.li/1e8934c1-2303-4182-9ef2-ad4c46882570) | ![img](https://e.gkd.li/01f230d7-9b89-4314-b573-38bd233d22f9) | ![img](https://e.gkd.li/dfa0a782-b21e-473a-96e4-eef27773b71b) | ![img](https://e.gkd.li/641decd1-2e60-4e95-b78c-df38d1d98a4d) |
| ![img](https://e.gkd.li/b216b703-d3de-4798-81ba-29e0ae63264f) | ![img](https://e.gkd.li/76c25ac9-4189-47cd-b40b-b9e72c79b584) | ![img](https://e.gkd.li/7288502e-808b-4d9a-88b5-1085abaa0d46) | ![img](https://e.gkd.li/aa974940-7773-409a-ae84-3c02fee9c770) |

## Subscriptions

GKD **does not provide any rules by default** — you need to add local rules yourself, or obtain remote rules via a subscription link

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

## Related projects

Derived projects created during development, currently used by gkd, which may also help you

- [kotlin-json5](https://github.com/lisonge/kotlin-json5)
- [kotlin-codeorigin](https://github.com/lisonge/kotlin-codeorigin)
- [android-api-diff](https://github.com/android-cs/android-api-diff)
- [remap](https://github.com/lisonge/remap)
- [priv-kit](https://github.com/priv-kit/priv-kit)

## Donate

If GKD has been useful to you, you can support the project via the following link

<https://github.com/lisonge/sponsor>

Or leave a good review on [Google Play](https://play.google.com/store/apps/details?id=li.songe.gkd)
