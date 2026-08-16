"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";

type CautionLevel = "belgi_topilmadi" | "ozgina_belgi" | "kop_belgi";

type Signal = { technique: string; quote: string; explanation: string };

type AnalyzeResult = {
  cautionLevel: CautionLevel;
  summary: string;
  signals: Signal[];
  tip: string;
};

type Segment = { text: string; signalIndex: number | null };

function isPhishingSignal(technique: string): boolean {
  return /fishing|phish/i.test(technique);
}

function buildHighlightSegments(source: string, signals: Signal[]): Segment[] {
  const matches: { start: number; end: number; index: number }[] = [];
  signals.forEach((s, i) => {
    if (!s.quote) return;
    const start = source.indexOf(s.quote);
    if (start !== -1) matches.push({ start, end: start + s.quote.length, index: i });
  });
  matches.sort((a, b) => a.start - b.start);

  const nonOverlapping: typeof matches = [];
  let lastEnd = -1;
  for (const m of matches) {
    if (m.start >= lastEnd) {
      nonOverlapping.push(m);
      lastEnd = m.end;
    }
  }

  const segments: Segment[] = [];
  let cursor = 0;
  for (const m of nonOverlapping) {
    if (m.start > cursor) segments.push({ text: source.slice(cursor, m.start), signalIndex: null });
    segments.push({ text: source.slice(m.start, m.end), signalIndex: m.index });
    cursor = m.end;
  }
  if (cursor < source.length) segments.push({ text: source.slice(cursor), signalIndex: null });
  return segments;
}

const CAUTION_STYLES: Record<CautionLevel, { label: string; badge: string; ring: string }> = {
  belgi_topilmadi: {
    label: "Aniq belgi topilmadi",
    badge: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
    ring: "ring-emerald-500/30",
  },
  ozgina_belgi: {
    label: "Bir nechta ehtiyot belgisi bor",
    badge: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
    ring: "ring-amber-500/30",
  },
  kop_belgi: {
    label: "Ko'plab ehtiyot belgisi bor",
    badge: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
    ring: "ring-red-500/30",
  },
};

const EXAMPLES = [
  "Olimlar yangi telefon texnologiyasi inson xotirasini butunlay yo'q qilishini aniqladi! Buni darhol hammaga yuboring!!!",
  "Vazirlik bugun rasmiy sayti orqali 2026-yil davlat byudjeti loyihasini e'lon qildi va jamoatchilik muhokamasiga qo'ydi.",
  "Hurmatli mijoz, hisobingiz 24 soatdan so'ng bloklanadi. Faollashtirish uchun quyidagi havolaga o'ting va karta ma'lumotlaringizni tasdiqlang: bit.ly/bank-tasdiq",
];

function HomeInner() {
  const searchParams = useSearchParams();
  const [content, setContent] = useState("");
  const [analyzedContent, setAnalyzedContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AnalyzeResult | null>(null);
  const [activeSignal, setActiveSignal] = useState<number | null>(null);
  const [sharedNotice, setSharedNotice] = useState(false);

  async function handleAnalyze(overrideText?: string) {
    const text = overrideText ?? content;
    if (text.trim().length < 3) {
      setError("Iltimos, tahlil qilish uchun matn kiriting");
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);
    setActiveSignal(null);
    try {
      const res = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: text }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error ?? "Xatolik yuz berdi");
        return;
      }
      setAnalyzedContent(text);
      setResult(data as AnalyzeResult);
    } catch {
      setError("Tarmoq xatosi. Iltimos, qayta urinib ko'ring");
    } finally {
      setLoading(false);
    }
  }

  // Android "Ulashish" (share) orqali kelgan matnni avtomatik tahlil qilish
  useEffect(() => {
    const shared = searchParams.get("q") || searchParams.get("u") || searchParams.get("t");
    if (shared) {
      setContent(shared);
      setSharedNotice(true);
      handleAnalyze(shared);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function focusSignal(index: number) {
    setActiveSignal(index);
    document
      .getElementById(`signal-mark-${index}`)
      ?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  const style = result ? CAUTION_STYLES[result.cautionLevel] : null;
  const segments = result ? buildHighlightSegments(analyzedContent, result.signals) : [];

  return (
    <div className="flex flex-1 flex-col items-center bg-zinc-50 px-4 py-10 dark:bg-black sm:py-16">
      <main className="w-full max-w-2xl">
        <div className="mb-8 text-center">
          <span className="mb-3 inline-block rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-white dark:bg-white dark:text-black">
            MIL Youth Hackathon 2026
          </span>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50 sm:text-3xl">
            Bu xabar sizni ishontirishga urinyaptimi?
          </h1>
          <p className="mt-2 text-base text-zinc-600 dark:text-zinc-400">
            Shubhali xabar, post yoki matnni pastga joylashtiring — dezinformatsiya,
            manipulyatsiya va fishing (firibgarlik) belgilarini toping, qayerda va
            nega ekanini tushunib oling.
          </p>
          <p className="mx-auto mt-3 max-w-md rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-800 dark:bg-blue-950 dark:text-blue-300">
            ⚠️ Bu vosita voqeaning rost yoki yolg&apos;onligini aniqlamaydi — faqat matn
            uslubidagi ehtiyot belgilarini ko&apos;rsatadi. Faktni bilish uchun har doim
            bir nechta mustaqil manbadan tekshiring.
          </p>
        </div>

        {sharedNotice && (
          <p className="mx-auto mb-3 max-w-md rounded-lg bg-zinc-900 px-3 py-2 text-center text-xs text-white dark:bg-white dark:text-black">
            📤 Ulashilgan matn qabul qilindi — avtomatik tahlil qilinmoqda
          </p>
        )}

        <div className="rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm dark:border-zinc-800 dark:bg-zinc-950 sm:p-6">
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Masalan: 'Olimlar yangi texnologiya haqida shov-shuv xabar bermoqda...'"
            rows={6}
            className="w-full resize-none rounded-lg border border-zinc-300 bg-white p-3 text-sm text-zinc-900 outline-none focus:border-zinc-500 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
          />

          <div className="mt-3 flex flex-wrap gap-2">
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => setContent(ex)}
                className="rounded-full border border-zinc-300 px-3 py-1 text-xs text-zinc-600 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-900"
              >
                Namuna: {ex.slice(0, 28)}...
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => handleAnalyze()}
            disabled={loading}
            className="mt-4 w-full rounded-lg bg-zinc-900 px-4 py-3 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:opacity-50 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
          >
            {loading ? "Tahlil qilinmoqda..." : "Tahlil qilish"}
          </button>

          {error && (
            <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>

        {result && style && (
          <div
            className={`mt-6 rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm ring-1 dark:border-zinc-800 dark:bg-zinc-950 ${style.ring}`}
          >
            <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${style.badge}`}>
              {style.label}
            </span>
            <p className="mt-3 text-base font-medium text-zinc-900 dark:text-zinc-100">
              {result.summary}
            </p>

            {result.signals.length > 0 && (
              <div className="mt-4">
                <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
                  Matn ichida qayerda:
                </h2>
                <p className="mt-2 whitespace-pre-wrap rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-sm leading-relaxed text-zinc-700 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300">
                  {segments.map((seg, i) => {
                    if (seg.signalIndex === null) return <span key={i}>{seg.text}</span>;
                    const idx = seg.signalIndex;
                    const phishing = isPhishingSignal(result.signals[idx].technique);
                    const active = activeSignal === idx;
                    const markClass = phishing
                      ? active
                        ? "bg-rose-500 text-white dark:bg-rose-500 dark:text-white"
                        : "bg-rose-200 text-rose-950 hover:bg-rose-300 dark:bg-rose-900/60 dark:text-rose-100 dark:hover:bg-rose-900"
                      : active
                        ? "bg-orange-400 text-orange-950 dark:bg-orange-500 dark:text-orange-50"
                        : "bg-orange-200 text-orange-950 hover:bg-orange-300 dark:bg-orange-900/60 dark:text-orange-100 dark:hover:bg-orange-900";
                    return (
                      <mark
                        key={i}
                        id={`signal-mark-${idx}`}
                        title={result.signals[idx].explanation}
                        onClick={() => focusSignal(idx)}
                        className={`cursor-pointer rounded px-0.5 transition-colors ${markClass}`}
                      >
                        {phishing ? "🎣 " : ""}
                        {seg.text}
                        <sup className="ml-0.5 font-semibold">{idx + 1}</sup>
                      </mark>
                    );
                  })}
                </p>
              </div>
            )}

            <div className="mt-4">
              <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
                Topilgan belgilar:
              </h2>
              {result.signals.length === 0 ? (
                <p className="mt-2 text-sm text-zinc-600 dark:text-zinc-400">
                  Aniq ishontirish/manipulyatsiya belgisi topilmadi. Baribir manbani
                  mustaqil tekshiring.
                </p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {result.signals.map((s, i) => {
                    const phishing = isPhishingSignal(s.technique);
                    const active = activeSignal === i;
                    const liClass = phishing
                      ? active
                        ? "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40"
                        : "border-zinc-200 dark:border-zinc-800"
                      : active
                        ? "border-orange-400 bg-orange-50 dark:border-orange-600 dark:bg-orange-950/40"
                        : "border-zinc-200 dark:border-zinc-800";
                    return (
                      <li
                        key={i}
                        onClick={() => focusSignal(i)}
                        className={`cursor-pointer rounded-lg border p-2 text-sm transition-colors ${liClass}`}
                      >
                        <span className="font-medium text-zinc-800 dark:text-zinc-200">
                          {phishing ? "🎣 " : ""}
                          {i + 1}. {s.technique}
                        </span>
                        <p className="mt-0.5 text-zinc-600 dark:text-zinc-400">
                          {s.explanation}
                        </p>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            <div className="mt-4 rounded-lg bg-zinc-50 p-3 dark:bg-zinc-900">
              <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
                💡 Keyingi safar uchun maslahat:
              </h2>
              <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">{result.tip}</p>
            </div>
          </div>
        )}

        <p className="mt-8 text-center text-xs text-zinc-400 dark:text-zinc-600">
          UNESCO Youth Hackathon 2026 — Media va Axborot Savodxonligi (MIL) uchun loyiha
        </p>
      </main>
    </div>
  );
}

export default function Home() {
  return (
    <Suspense fallback={null}>
      <HomeInner />
    </Suspense>
  );
}
