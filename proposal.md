% Trust Signal — Project Proposal
% UNESCO Youth Hackathon 2026

# Trust Signal

## Team Composition

| Name | Age | Country | Role |
|---|---|---|---|
| Baxrom Reyimberganov | 20 | Uzbekistan | Team Leader |
| Guli Salayeva | 20 | Uzbekistan | Member |
| Nafisa G'anijanova | 22 | Uzbekistan | Member |
| Nodirbek Boltaev | 22 | Uzbekistan | Member |

## Problem Identification

**The behavioral gap.** We built Trust Signal because we kept running into the same problem ourselves: generative AI has made convincing disinformation, manipulative content, and phishing scams almost free to produce, while our own ability to critically evaluate what we see online hasn't kept pace. This isn't really a knowledge problem for us or our friends — we usually *know* we're supposed to "check the source." But in the real moment of scrolling a feed or opening a forwarded message, we still catch ourselves clicking or forwarding before we've actually verified anything. We kept noticing that awareness campaigns teach us the rule, but they rarely change what we actually do in that moment.

**Why the gap exists — the psychology behind it.** This pushed us to look at why it happens, not just that it happens. When a person encounters a message, it's processed through a fast, largely automatic pathway before conscious reasoning ever kicks in: attention gets captured — often by design — and an emotional reaction fires first, ahead of any deliberate evaluation. We found that disinformation and phishing content is deliberately engineered to exploit this ordering:

- **Urgency and scarcity** ("act now," "your account will be blocked in 24 hours") create time pressure that short-circuits deliberation.
- **Social proof** ("everyone is sharing this") substitutes the judgment of the crowd for individual verification.
- **Authority mimicry** (fake bank, government, or brand identity) exploits trust heuristics built for legitimate institutions.
- **In-group familiarity** (a message forwarded by a trusted friend or family member) transfers our trust in the messenger to the message itself, regardless of where it actually came from.

This is why we think generic education alone under-performs: knowing the rule doesn't help if the emotional and urgency triggers have already fired before we remember it. We designed Trust Signal around the belief that intervention has to happen at the exact moment attention and emotion are engaged — not earlier, in a classroom, and not later, after a message has already spread — which is the instant a person selects or receives a suspicious message, on whatever platform they're already using.

Two more gaps shaped our design:

1. **Detection ≠ Understanding.** Existing AI "fake/real" detectors hand you a verdict without explaining *why*, so we noticed the underlying skill never actually transfers to the next piece of content you see.
2. **A single destination app ≠ where people actually are.** Most literacy tools ask you to leave what you're doing and go check somewhere else — exactly the extra step we knew we'd skip when we were in a hurry or under social pressure.

We also made a deliberate call early on: our AI never declares a claim "true" or "false." We don't believe any system — ours or anyone else's — can verify a breaking event with certainty, and we didn't want to risk wrongly labeling a real, shocking-but-true story, because that would cause real harm on its own. Instead, we built Trust Signal to focus on what we *can* reliably observe: the rhetorical and structural patterns of manipulation and phishing described above.

## Goals

1. Help people recognize manipulation techniques (urgency, unverified authority, emotional framing, decontextualized claims) and phishing patterns (credential requests, fake account alerts, prize scams, suspicious links) in any message, in seconds.
2. Build a skill that stays with the person, not a one-time answer — every result we show includes a general takeaway someone can apply the next time, without needing us.
3. Meet people where they already are, on any platform, with no app-switching and no copy-paste, by integrating at the operating-system level instead of building yet another destination app.

## Intended Beneficiaries

**Primary:** any young person, on any social or messaging platform, in any region we can reach — we built the tool platform-agnostic on purpose, integrated at the operating-system level rather than tied to one app or network.

**Secondary:** teachers and parents who can use it as a discussion aid with their students or kids, and communities that English-first fact-checking tools currently underserve, since our underlying architecture can operate in any language.

## Prototype / Concept Design

We didn't want to build another destination people have to remember to open. The main convenience we designed for is speed and ease: someone selects a suspicious message right where they already are, and Trust Signal analyzes it on the spot — no separate app to open, no switching screens, no copying text anywhere.

**On Android** (built and functional), this is real, working integration at the operating-system level, not a mockup:

- Registered directly in Android's own text-selection toolbar — select any text in any app, tap Trust Signal, and get an instant analysis right there
- A floating quick-access button that stays on screen so a single tap analyzes whatever was just copied, from inside any app
- Share Sheet integration as an additional, familiar entry point

Underneath, our AI does two things at once, not just one: it **detects** specific manipulation and phishing patterns, and it **teaches**, explaining each one in plain language as it goes. Every result is built to be a small lesson, not just a flag:

- The exact phrases responsible are highlighted inline, inside the person's own text
- Each highlight is explained in plain language, categorized as disinformation/manipulation *or* phishing
- A general, transferable tip closes every result, so the skill stays with the person even without us
- We never verify facts or claim certainty — we always encourage independent cross-checking

## Originality

Unlike existing binary "fake/real" detectors, we made sure Trust Signal never claims certainty about facts. It surfaces *observable* rhetorical and structural signals — for disinformation and, as a distinct category we added, for phishing/scam patterns — and explains its reasoning directly inline, in the person's own text, grounded in the psychological mechanisms these techniques actually exploit. We chose this design deliberately after realizing that false-certainty AI tools can cause real harm by mislabeling genuine, shocking-but-true news.

We also built true OS-level integration (text-selection toolbar, floating button) instead of a single destination app, because we wanted to remove the exact friction that we think limits adoption of most MIL tools today — it works on whatever platform someone is already using. Our architecture is also language-agnostic by design: our current build demonstrates the approach in a non-English language, which proves to us that this doesn't require an English-first market to work, and we can extend it to any language or region from here.

## Practical Feasibility & Long-Term Viability

We didn't just design this — we built and shipped it. The web app is live in production today, and our Android app builds and installs successfully. We kept the architecture intentionally lightweight, with a single AI-analysis endpoint that both our web and Android clients share, which keeps our hosting and maintenance costs low and lets us iterate quickly. Because we built it platform- and language-agnostic from the start, our path forward is partnering with youth organizations and schools across regions, and expanding to any messaging or social platform that supports the same OS-level text-selection integration we're already using.
