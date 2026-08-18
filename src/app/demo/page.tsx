"use client";

import { useEffect, useRef, useState } from "react";

type Signal = { quote: string; technique: string; explanation: string; phishing?: boolean };

type ExampleKey = "urgency" | "phishing";

type Example = {
  sender: string;
  message: string;
  signals: Signal[];
  tip: string;
};

const EXAMPLES: Record<ExampleKey, Example> = {
  urgency: {
    sender: "Unknown",
    message:
      "URGENT! Your account will be blocked. Click immediately to confirm your identity: bit.ly/verify-now",
    signals: [
      {
        quote: "URGENT!",
        technique: "Urgency",
        explanation: "This creates pressure and encourages you to act before thinking.",
      },
      {
        quote: "Click immediately",
        technique: "Urgency manipulation",
        explanation: "This is a manipulation technique based on urgency.",
      },
      {
        quote: "Your account will be blocked",
        technique: "Fear appeal",
        explanation: "This creates fear and pushes the user toward a quick decision.",
      },
    ],
    tip: "Stop. Check the source. Don't click before verifying.",
  },
  phishing: {
    sender: "Unknown",
    message:
      "Congratulations! You've won $500. Click here now and confirm your card details to claim your prize: bit.ly/claim-prize",
    signals: [
      {
        quote: "Congratulations! You've won $500",
        technique: "Phishing — prize scam",
        explanation: "Unexpected prizes are a classic phishing lure designed to lower your guard.",
        phishing: true,
      },
      {
        quote: "confirm your card details",
        technique: "Phishing — credential request",
        explanation: "Legitimate services never ask you to confirm card details through a random link.",
        phishing: true,
      },
      {
        quote: "bit.ly/claim-prize",
        technique: "Phishing — suspicious link",
        explanation: "Shortened links hide the real destination and are commonly used in scams.",
        phishing: true,
      },
    ],
    tip: "Never enter payment details through a link sent in a message — go to the official app or website directly.",
  },
};

type Stage = "message" | "selected" | "analyzing" | "result";

function buildSegments(source: string, signals: Signal[]) {
  const matches: { start: number; end: number; index: number }[] = [];
  signals.forEach((s, i) => {
    const start = source.indexOf(s.quote);
    if (start !== -1) matches.push({ start, end: start + s.quote.length, index: i });
  });
  matches.sort((a, b) => a.start - b.start);

  const segments: { text: string; signalIndex: number | null }[] = [];
  let cursor = 0;
  for (const m of matches) {
    if (m.start > cursor) segments.push({ text: source.slice(cursor, m.start), signalIndex: null });
    segments.push({ text: source.slice(m.start, m.end), signalIndex: m.index });
    cursor = m.end;
  }
  if (cursor < source.length) segments.push({ text: source.slice(cursor), signalIndex: null });
  return segments;
}

function markClass(phishing: boolean | undefined) {
  return phishing
    ? "rounded bg-rose-200 px-0.5 text-rose-950 dark:bg-rose-900/60 dark:text-rose-100"
    : "rounded bg-orange-200 px-0.5 text-orange-950 dark:bg-orange-900/60 dark:text-orange-100";
}

export default function DemoPage() {
  const [exampleKey, setExampleKey] = useState<ExampleKey>("urgency");
  const [stage, setStage] = useState<Stage>("message");
  const [revealed, setRevealed] = useState<number[]>([]);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const example = EXAMPLES[exampleKey];
  const segments = buildSegments(example.message, example.signals);
  const orderedSignalIndexes = Array.from(
    new Set(
      segments
        .map((s) => s.signalIndex)
        .filter((i): i is number => i !== null)
    )
  );

  function clearTimers() {
    timers.current.forEach(clearTimeout);
    timers.current = [];
  }

  function switchExample(key: ExampleKey) {
    clearTimers();
    setRevealed([]);
    setExampleKey(key);
    setStage("message");
  }

  function resetDemo() {
    clearTimers();
    setRevealed([]);
    setStage("message");
  }

  useEffect(() => {
    if (stage !== "analyzing") return;
    clearTimers();
    const t: ReturnType<typeof setTimeout>[] = [];
    orderedSignalIndexes.forEach((idx, i) => {
      t.push(setTimeout(() => setRevealed((r) => [...r, idx]), 320 + i * 380));
    });
    t.push(
      setTimeout(
        () => setStage("result"),
        320 + orderedSignalIndexes.length * 380 + 550
      )
    );
    timers.current = t;
    return () => t.forEach(clearTimeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage]);

  useEffect(() => {
    return () => clearTimers();
  }, []);

  return (
    <div className="flex min-h-full flex-col items-center gap-8 bg-zinc-100 px-4 py-12 dark:bg-zinc-900 sm:py-20">
      <div className="text-center">
        <span className="inline-block rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white dark:bg-white dark:text-black">
          SCRIPTED DEMO — for recording the video pitch
        </span>
        <h1 className="mt-4 text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Trust Signal — mobile flow
        </h1>
        <p className="mt-2 max-w-md text-sm text-zinc-600 dark:text-zinc-400">
          Click through the steps below in order while recording your screen.
        </p>
      </div>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => switchExample("urgency")}
          className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
            exampleKey === "urgency"
              ? "bg-zinc-900 text-white dark:bg-white dark:text-black"
              : "border border-zinc-300 text-zinc-600 dark:border-zinc-700 dark:text-zinc-400"
          }`}
        >
          Example 1 — Urgency
        </button>
        <button
          type="button"
          onClick={() => switchExample("phishing")}
          className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
            exampleKey === "phishing"
              ? "bg-zinc-900 text-white dark:bg-white dark:text-black"
              : "border border-zinc-300 text-zinc-600 dark:border-zinc-700 dark:text-zinc-400"
          }`}
        >
          Example 2 — Phishing
        </button>
      </div>

      {/* Phone frame */}
      <div className="relative h-[680px] w-[340px] rounded-[3rem] border-[10px] border-zinc-900 bg-black shadow-2xl dark:border-zinc-700">
        <div className="absolute left-1/2 top-0 z-20 h-6 w-32 -translate-x-1/2 rounded-b-2xl bg-zinc-900 dark:bg-zinc-700" />

        <div className="relative flex h-full w-full flex-col overflow-hidden rounded-[2.2rem] bg-zinc-50 dark:bg-zinc-950">
          {/* Fake status bar */}
          <div className="flex items-center justify-between px-6 pb-1 pt-3 text-[11px] font-medium text-zinc-500 dark:text-zinc-400">
            <span>9:41</span>
            <span>●●● 5G 100%</span>
          </div>

          {/* Fake messaging app header */}
          <div className="border-b border-zinc-200 px-4 py-3 dark:border-zinc-800">
            <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
              Messages
            </div>
            <div className="text-xs text-zinc-500 dark:text-zinc-500">{example.sender}</div>
          </div>

          {/* Message content area */}
          <div className="relative flex-1 overflow-hidden px-4 py-5">
            <div className="relative max-w-[85%] overflow-hidden rounded-2xl rounded-tl-sm bg-white p-3 text-[15px] leading-relaxed text-zinc-900 shadow-sm dark:bg-zinc-800 dark:text-zinc-100">
              {stage === "analyzing" && (
                <div
                  className="scan-sweep pointer-events-none absolute inset-x-0 h-8 bg-gradient-to-b from-transparent via-blue-400/40 to-transparent"
                  style={{ animation: "scanSweep 1.4s ease-in-out" }}
                />
              )}

              {stage === "message" && <span>{example.message}</span>}

              {stage === "selected" && (
                <span className="rounded bg-blue-200/70 px-0.5 dark:bg-blue-500/30">
                  {example.message}
                </span>
              )}

              {(stage === "analyzing" || stage === "result") &&
                segments.map((seg, i) =>
                  seg.signalIndex === null ? (
                    <span key={i}>{seg.text}</span>
                  ) : (
                    <mark
                      key={i}
                      className={`transition-colors duration-300 ${
                        stage === "result" || revealed.includes(seg.signalIndex)
                          ? markClass(example.signals[seg.signalIndex].phishing)
                          : ""
                      }`}
                    >
                      {seg.text}
                    </mark>
                  )
                )}
            </div>

            {/* Android-style selection toolbar */}
            {stage === "selected" && (
              <div className="mt-2 inline-flex items-center gap-3 rounded-xl bg-zinc-900 px-3 py-2 text-xs font-medium text-white shadow-lg dark:bg-zinc-100 dark:text-zinc-900">
                <span className="text-zinc-400 dark:text-zinc-500">Copy</span>
                <span className="text-zinc-400 dark:text-zinc-500">Share</span>
                <button
                  type="button"
                  onClick={() => {
                    setRevealed([]);
                    setStage("analyzing");
                  }}
                  className="flex items-center gap-1 rounded-lg bg-white/10 px-2 py-1 text-white dark:bg-black/10 dark:text-zinc-900"
                >
                  🛡️ Trust Signal
                </button>
              </div>
            )}

            {/* Analyzing indicator */}
            {stage === "analyzing" && (
              <div className="mt-2 inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-3 py-2 text-xs font-medium text-white shadow-lg dark:bg-zinc-100 dark:text-zinc-900">
                <span>Analyzing message</span>
                <span className="flex gap-0.5">
                  {[0, 0.2, 0.4].map((d, i) => (
                    <span
                      key={i}
                      className="dot-pulse inline-block h-1.5 w-1.5 rounded-full bg-current"
                      style={{ animation: "dotPulse 1s ease-in-out infinite", animationDelay: `${d}s` }}
                    />
                  ))}
                </span>
              </div>
            )}

            {/* Tap hint */}
            {stage === "message" && (
              <button
                type="button"
                onClick={() => setStage("selected")}
                className="mt-4 animate-pulse rounded-full bg-zinc-900 px-4 py-2 text-xs font-medium text-white dark:bg-white dark:text-black"
              >
                👆 Select suspicious text
              </button>
            )}
          </div>

          {/* Result card sliding up */}
          <div
            className={`absolute inset-x-0 bottom-0 rounded-t-3xl border-t border-zinc-200 bg-white p-4 shadow-[0_-8px_24px_rgba(0,0,0,0.1)] transition-transform duration-300 dark:border-zinc-800 dark:bg-zinc-950 ${
              stage === "result" ? "translate-y-0" : "translate-y-full"
            }`}
            style={{ maxHeight: "62%" }}
          >
            <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-zinc-300 dark:bg-zinc-700" />
            <div className="flex items-center justify-between">
              <span className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                🛡️ Trust Signal
              </span>
              <button
                type="button"
                onClick={resetDemo}
                className="text-xs text-zinc-400"
              >
                ✕
              </button>
            </div>

            <div className="mt-3 max-h-[70%] overflow-y-auto">
              <p className="rounded-lg bg-zinc-50 p-2 text-[13px] leading-relaxed text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
                {segments.map((seg, i) =>
                  seg.signalIndex === null ? (
                    <span key={i}>{seg.text}</span>
                  ) : (
                    <mark
                      key={i}
                      className={markClass(example.signals[seg.signalIndex].phishing)}
                    >
                      {seg.text}
                    </mark>
                  )
                )}
              </p>

              <ul className="mt-2 space-y-1.5">
                {example.signals.map((s, i) => (
                  <li key={i} className="text-[13px]">
                    <span className="font-medium text-zinc-800 dark:text-zinc-200">
                      {s.phishing ? "🎣 " : "⚠️ "}
                      {s.technique}
                    </span>
                    <p className="text-zinc-600 dark:text-zinc-400">{s.explanation}</p>
                  </li>
                ))}
              </ul>

              <div className="mt-3 rounded-lg bg-zinc-900 p-2.5 text-[13px] text-white dark:bg-white dark:text-black">
                💡 {example.tip}
              </div>
            </div>
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={resetDemo}
        className="text-sm text-zinc-500 underline dark:text-zinc-400"
      >
        Reset demo
      </button>
    </div>
  );
}
