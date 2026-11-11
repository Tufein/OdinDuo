# Main Odin display stays black after connecting Retroid

## Reported solution

The maintainer reported that the built-in Odin 3 screen stayed black while the Retroid showed the normal Android launcher, including with protection stopped. Unplugging the Retroid restored the Odin screen immediately. Changing a setting resolved the issue. The maintainer located it in **Odin settings**, with wording recalled as **“turn off screen”**.

1. Unplug the Retroid to recover the built-in display.
2. Open **Android Settings → Odin settings**.
3. Find the option that turns off the built-in screen when an external display connects, and turn that option off. “Turn off screen” is a recognition hint, not a verified exact label. Any submenu may depend on firmware.
4. Reconnect and check that both displays show an image.

This is a user-confirmed settings solution for this reported case, not a new software fix in OdinDuo or a diagnosis of every black-screen problem. OdinDuo does not change this setting automatically.

## Research and limits

- **Direct Odin 3 report:** the maintainer confirmed the settings change resolved the reported case. The complete switch label, submenu and current firmware version were not supplied, and no connected Odin was available for independent inspection.
- [Retro Game Corps: AYN Odin & Odin 2 Starter Guide](https://retrogamecorps.com/2022/05/28/ayn-odin-starter-guide/) independently documents the parent path Settings → Odin Settings. It covers earlier Odin models and does not establish the exact Odin 3 switch label.
- [OdinHandheld discussion: connecting to an external display](https://www.reddit.com/r/OdinHandheld/comments/vy8lfd/i_have_a_question_about_connecting_to_an_external/) has an indexed reply describing an option in Odin settings to turn off the screen when connected, and a follow-up saying the setting was found. This was visible in Brave Search excerpts; the Reddit page itself could not be read from this environment. It is corroboration from an earlier Odin, not an Odin 3 firmware reference.

If this setting does not resolve another case, collect diagnostics while connected if possible and again after unplugging. Android-reported display states do not prove that the physical panel shows an image. The reported case is resolved; other cases still need their own evidence.
