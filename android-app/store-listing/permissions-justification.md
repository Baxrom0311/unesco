# Play Console Permissions Declaration — SYSTEM_ALERT_WINDOW

Use this when Play Console's App Content → Permissions Declaration form asks
you to justify the "Display over other apps" (`SYSTEM_ALERT_WINDOW`)
permission.

---

## Short version (paste into the Play Console form field)

> Trust Signal shows a small, user-controlled floating button (similar to
> Messenger's "chat heads") that lets the user re-analyze text they just
> copied — anywhere on the device — without switching apps. This is a core
> feature of the app: instantly checking a suspicious message for
> manipulation or phishing signals at the exact moment they encounter it, on
> any platform they're already using. The overlay is opt-in (off by default,
> toggled from the app's main screen), shows a persistent notification while
> active, can be dragged and repositioned, and can be turned off at any
> time. It never displays ads, never intercepts input in other apps, and
> only acts when the user explicitly taps it.

*(~600 characters — trim further if the form has a lower limit.)*

## Why this permission is core, not incidental

Trust Signal's entire value proposition is reducing the friction between
"I see a suspicious message" and "I understand why it's suspicious." We
already offer two lower-friction entry points that do **not** need this
permission:

1. Android's native text-selection toolbar (`ACTION_PROCESS_TEXT`)
2. The system Share Sheet (`ACTION_SEND`)

The floating button is a **third, complementary** entry point for the
specific case where the user has already copied something (e.g. from a
screenshot, a locked message, or an app that disables text selection) and
wants to check it without hunting back through recent apps. Removing it
would remove a distinct, real use case — it is not a decorative or
duplicate feature.

## Addressing common review concerns

- **Not an ad overlay / not tapjacking**: the bubble only ever opens our own
  analysis card when the user taps it; it never renders content from a
  third party or intercepts taps intended for the app underneath.
- **User control**: off by default; one tap in the app to enable/disable;
  draggable; a persistent low-priority notification is shown the entire
  time it's active, so its presence is never hidden from the user.
- **Data handling**: the bubble reads the OS clipboard only at the instant
  the user taps it — never continuously, never in the background — and only
  to populate the analysis request. Full data flow:
  https://unesco-cyan.vercel.app/privacy
- **Minimal footprint**: the overlay is a single small icon; the expanded
  result card only appears after an explicit tap and can be dismissed
  instantly (✕ button or back button).

## Suggested supporting material for the review

Play sometimes asks for a short demo video alongside the written
justification. Reuse the existing `/demo` page (phone-mockup walkthrough)
or record the real device flow: enable the bubble → copy a message in
another app → tap the bubble → see the analysis card → dismiss it.

## Related manifest declaration

The app also declares a `specialUse` foreground service type for the
service that hosts this overlay (`BubbleService`), with its own required
justification string in `AndroidManifest.xml`:

> "Persistent floating shortcut that lets the user re-analyze text they
> just copied, without switching apps."

Keep this wording consistent with the SYSTEM_ALERT_WINDOW justification
above if either is revised later.
