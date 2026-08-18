import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy — Trust Signal",
  description: "What Trust Signal does and does not do with the text you submit.",
};

export default function PrivacyPage() {
  return (
    <div className="mx-auto max-w-2xl px-6 py-16 text-zinc-800 dark:text-zinc-200">
      <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
        Privacy Policy
      </h1>
      <p className="mt-2 text-sm text-zinc-500">Last updated: 2026-08-18</p>

      <p className="mt-6 text-sm leading-relaxed">
        Trust Signal (&quot;Ishonasizmi?&quot;) is a tool that analyzes text a
        user submits — pasted into the web app, shared via the Android share
        sheet, selected via the text-selection toolbar, or read from the
        clipboard when the floating button is tapped — and returns an
        analysis of possible manipulation or phishing patterns. This page
        explains exactly what happens to that text.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        What we collect
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        The only content we process is the text you submit for analysis. We
        do not require an account, a name, an email address, or any other
        personal information to use Trust Signal.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        How that text is used
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        When you submit text, it is sent from your device to our server
        (<code className="rounded bg-zinc-100 px-1 py-0.5 text-xs dark:bg-zinc-800">/api/analyze</code>),
        which forwards it to an AI model through Vercel AI Gateway to
        generate the analysis. The model provider processes the text to
        produce a response and returns it to us; we return it to you. We do
        not maintain a database of submitted text, and we do not store,
        log the full content of, sell, or share the text you submit with
        any third party for advertising or profiling purposes.
      </p>
      <p className="mt-2 text-sm leading-relaxed">
        Like most server software, transient error logs may briefly include
        fragments of a request if something fails unexpectedly; these logs
        are operational, are not reviewed for content, and are not used for
        any purpose beyond debugging.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        Clipboard access (Android app)
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        The Android app&apos;s floating button reads the device clipboard
        only at the exact moment you tap it, in order to analyze whatever
        you just copied. It does not read the clipboard continuously, does
        not read it in the background, and does not read it for any other
        purpose.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        &quot;Display over other apps&quot; permission (Android app)
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        The optional floating button feature uses Android&apos;s
        &quot;display over other apps&quot; permission so the button can
        stay accessible while you use other apps. It is off by default, can
        be turned on or off at any time from the app&apos;s main screen, and
        shows a persistent notification the entire time it is active. It
        never displays content from a third party and never intercepts taps
        intended for the app underneath it.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        What we do not do
      </h2>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm leading-relaxed">
        <li>We do not require sign-up or a user account.</li>
        <li>We do not run advertising or ad-tracking SDKs.</li>
        <li>We do not sell or share submitted text with third parties.</li>
        <li>We do not build user profiles from submitted content.</li>
      </ul>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        Changes to this policy
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        If Trust Signal&apos;s data handling changes materially (for
        example, if we add optional accounts or analytics in the future),
        this page will be updated before that change ships.
      </p>

      <h2 className="mt-8 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
        Contact
      </h2>
      <p className="mt-2 text-sm leading-relaxed">
        Questions about this policy: [team contact email — add before Play
        Store submission].
      </p>
    </div>
  );
}
