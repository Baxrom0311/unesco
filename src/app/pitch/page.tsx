import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Trust Signal — UNESCO Youth Hackathon 2026",
  description:
    "An AI-powered critical-thinking companion that explains, rather than judges, suspicious messages — built for the UNESCO Youth Hackathon 2026.",
};

const STEPS = [
  {
    n: "01",
    title: "Select any text, anywhere",
    body: "In any app — a chat, a post, a browser page — the user selects a suspicious message. No copy-paste, no switching apps.",
  },
  {
    n: "02",
    title: "AI reads the pattern, not the fact",
    body: "The model never claims a story is true or false. It looks for observable rhetorical and phishing patterns — urgency, fake authority, credential requests.",
  },
  {
    n: "03",
    title: "The exact phrase is highlighted",
    body: "Inline, in the user's own text, each flagged phrase is highlighted and explained in plain language — disinformation in amber, phishing in red.",
  },
  {
    n: "04",
    title: "A transferable skill, not a verdict",
    body: "Every result ends with a general tip the user can apply to the next message, on any platform, without the tool.",
  },
];

const PSYCHOLOGY = [
  {
    label: "Urgency & scarcity",
    body: "“Act now” language short-circuits deliberation before it can start.",
  },
  {
    label: "Social proof",
    body: "“Everyone is sharing this” replaces individual verification with crowd judgment.",
  },
  {
    label: "Authority mimicry",
    body: "Fake bank or institutional branding borrows trust built for the real thing.",
  },
  {
    label: "In-group familiarity",
    body: "A message from a trusted friend transfers trust in the messenger to the message itself.",
  },
];

const INTEGRATIONS = [
  {
    title: "Web app",
    body: "Paste any text and get an instant, highlighted analysis. Live and in production.",
  },
  {
    title: "Android — text selection",
    body: "Registered in Android's native text-selection toolbar (ACTION_PROCESS_TEXT) — select text in any app, tap Trust Signal.",
  },
  {
    title: "Android — floating button",
    body: "A persistent on-screen button reads the clipboard on tap and shows results in a card, without leaving the current app.",
  },
  {
    title: "Android — share sheet",
    body: "A standard share-menu entry point as an additional, familiar path in.",
  },
];

export default function PitchPage() {
  return (
    <div className="min-h-full bg-zinc-50 text-zinc-900 dark:bg-black dark:text-zinc-50">
      {/* Hero */}
      <header className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="mx-auto max-w-4xl px-6 py-20 sm:py-28">
          <span className="inline-block rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium tracking-wide text-white dark:bg-white dark:text-black">
            UNESCO YOUTH HACKATHON 2026 &middot; MEDIA &amp; INFORMATION LITERACY
          </span>
          <h1 className="mt-6 text-4xl font-semibold tracking-tight sm:text-6xl">
            Trust Signal
          </h1>
          <p className="mt-5 max-w-2xl text-lg text-zinc-600 dark:text-zinc-400 sm:text-xl">
            An AI companion that never tells you what to believe. It shows you{" "}
            <em>why</em> a message is trying to persuade you &mdash; the exact
            phrase, the technique, and the pattern &mdash; so the skill stays
            with you, not the tool.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a
              href="/"
              className="rounded-full bg-zinc-900 px-6 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
            >
              Try the live tool &rarr;
            </a>
            <a
              href="#demo"
              className="rounded-full border border-zinc-300 px-6 py-3 text-sm font-medium transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
            >
              See it in action
            </a>
          </div>
        </div>
      </header>

      {/* The problem */}
      <section className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
          The problem
        </h2>
        <p className="mt-3 max-w-3xl text-2xl font-medium leading-snug tracking-tight sm:text-3xl">
          Generative AI made convincing disinformation and phishing nearly
          free to produce. Human judgment did not get faster.
        </p>
        <p className="mt-4 max-w-2xl text-zinc-600 dark:text-zinc-400">
          Young people usually <em>know</em> the rule &mdash; &ldquo;check the
          source.&rdquo; But in the real moment of scrolling, under urgency
          and social pressure, the rule is rarely the thing that fires first.
        </p>

        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {PSYCHOLOGY.map((p) => (
            <div
              key={p.label}
              className="rounded-2xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-950"
            >
              <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                {p.label}
              </div>
              <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
                {p.body}
              </p>
            </div>
          ))}
        </div>
        <p className="mt-6 max-w-2xl text-sm text-zinc-500 dark:text-zinc-500">
          Emotion and urgency are processed before deliberate reasoning
          engages. That is precisely why intervention has to happen{" "}
          <em>at the moment</em> a message is encountered &mdash; not before,
          in a classroom, and not after, once it has already spread.
        </p>
      </section>

      {/* How it works */}
      <section className="border-y border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950">
        <div className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
            How it works
          </h2>
          <div className="mt-8 grid gap-8 sm:grid-cols-2">
            {STEPS.map((s) => (
              <div key={s.n} className="flex gap-4">
                <span className="text-2xl font-semibold text-zinc-300 dark:text-zinc-700">
                  {s.n}
                </span>
                <div>
                  <div className="font-medium text-zinc-900 dark:text-zinc-100">
                    {s.title}
                  </div>
                  <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
                    {s.body}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Live demo embed */}
      <section id="demo" className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
          Live demo
        </h2>
        <p className="mt-3 max-w-2xl text-zinc-600 dark:text-zinc-400">
          This is the real, working product &mdash; not a mockup. Paste a
          message below and see it analyze in real time.
        </p>
        <div className="mt-6 overflow-hidden rounded-2xl border border-zinc-200 shadow-sm dark:border-zinc-800">
          <iframe
            src="/"
            title="Trust Signal live demo"
            className="h-[720px] w-full bg-white dark:bg-black"
          />
        </div>
      </section>

      {/* Platform integrations */}
      <section className="border-t border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950">
        <div className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
            Built into the platform, not next to it
          </h2>
          <p className="mt-3 max-w-2xl text-zinc-600 dark:text-zinc-400">
            Most MIL tools ask users to leave what they are doing. Trust
            Signal is integrated at the operating-system level so it works
            wherever the user already is.
          </p>
          <div className="mt-8 grid gap-4 sm:grid-cols-2">
            {INTEGRATIONS.map((i) => (
              <div
                key={i.title}
                className="rounded-2xl border border-zinc-200 p-5 dark:border-zinc-800"
              >
                <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                  {i.title}
                </div>
                <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
                  {i.body}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Originality / closing */}
      <section className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
          Why this is different
        </h2>
        <p className="mt-3 max-w-3xl text-xl font-medium leading-snug tracking-tight sm:text-2xl">
          We deliberately never claim a story is true or false &mdash; no
          system can guarantee that, and a wrongly labeled real story causes
          real harm.
        </p>
        <p className="mt-4 max-w-2xl text-zinc-600 dark:text-zinc-400">
          Instead we surface what is reliably observable: the manipulation
          and phishing techniques a message actually uses, explained where
          they occur, teaching a skill that outlives any single check.
        </p>

        <div className="mt-10 flex flex-wrap items-center gap-4 border-t border-zinc-200 pt-8 dark:border-zinc-800">
          <a
            href="/"
            className="rounded-full bg-zinc-900 px-6 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
          >
            Try the live tool &rarr;
          </a>
          <span className="text-sm text-zinc-500 dark:text-zinc-500">
            unesco-cyan.vercel.app
          </span>
        </div>
      </section>
    </div>
  );
}
