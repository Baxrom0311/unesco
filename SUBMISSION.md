# UNESCO Youth Hackathon 2026 — Submission Materials

---

## 1. Form fields (short answers)

**Name of your proposal:**
Trust Signal

**Brief introduction of your proposal (short version for the form):**
Trust Signal is an AI-powered critical-thinking companion that helps young people evaluate suspicious messages, links, and posts on any social or messaging platform. Instead of issuing a risky true/false verdict — something no AI can honestly guarantee — it highlights the exact phrases in a message that use disinformation, manipulation, or phishing techniques, explains why in plain language, and teaches a transferable critical-thinking skill for next time. It ships as a web app and a native Android app integrated directly at the operating-system level (text-selection menu, share sheet, and a floating quick-access button), so it works inside whichever app the user is already in, on any platform, in any region.

**Category:**
B — Applications / Websites

---

## 2. Full Project Proposal (for the PDF/Word document)

### Project Name
Trust Signal (in-app name: "Ishonasizmi?")

### Team Composition
> *[To fill in: full names, ages, countries, and role of each team member (2–6 people), plus a designated Team Leader]*

| Name | Age | Country | Role |
|---|---|---|---|
| | | | Team Leader |
| | | | |
| | | | |

### Problem Identification

**The behavioral gap.** Generative AI has drastically lowered the cost and increased the speed of producing convincing disinformation, manipulative content, and phishing scams, while people's ability to critically evaluate what they see has not kept pace. This is not primarily a knowledge problem: young people often *know* they should "check the source," yet in the real moment of scrolling a feed or opening a forwarded message, they still forward or click before verifying. Awareness campaigns teach the rule; they rarely change the moment of behavior.

**Why the gap exists — the psychology.** When a person encounters a message, it is processed through a fast, largely automatic pathway before conscious reasoning ever engages: attention is captured — often by design — and an emotional reaction fires first, ahead of any deliberate, effortful evaluation. Disinformation and phishing content is deliberately engineered to exploit this ordering:

- **Urgency and scarcity** ("act now," "your account will be blocked in 24 hours") create time pressure that short-circuits deliberation.
- **Social proof** ("everyone is sharing this") substitutes the judgment of the crowd for individual verification.
- **Authority mimicry** (fake bank, government, or brand identity) exploits trust heuristics built for legitimate institutions.
- **In-group familiarity** (a message forwarded by a trusted friend or family member) transfers trust in the messenger to trust in the message itself, regardless of its actual origin.

This is why generic education alone under-performs: knowing the rule doesn't help if the emotional and urgency triggers have already fired before the rule is recalled. Effective intervention has to occur at the exact moment attention and emotion are engaged — not earlier (abstract education) or later (post-hoc fact-checking after the message has already spread) — which is precisely the moment Trust Signal is designed to reach: the instant a user selects or receives a suspicious message, on whichever platform they are using.

Two further gaps shaped the design:

1. **Detection ≠ Understanding.** Existing AI "fake/real" detectors return a verdict without explaining *why*, so the underlying skill never transfers to the next piece of content the user encounters.
2. **A single destination app ≠ where people actually are.** Most literacy tools ask users to leave their current app and visit a separate checker — exactly the extra step that a person under urgency or social pressure will skip.

We also deliberately reject the idea that an AI should ever declare a claim "true" or "false." No system — ours or any other — can verify breaking events with certainty, and a wrongly labeled real news story would itself cause harm. Instead, Trust Signal focuses on what *is* reliably observable: the rhetorical and structural patterns of manipulation and phishing described above.

### Goals

1. Help users recognize manipulation techniques (urgency, unverified authority, emotional framing, decontextualized claims) and phishing patterns (credential requests, fake account alerts, prize scams, suspicious links) in any message, in seconds.
2. Build a transferable critical-thinking skill rather than a one-time answer — every result includes a general takeaway the user can apply to future content, independent of the tool.
3. Meet users where they already are, on any platform, with no app-switching and no copy-paste, through native OS-level integration rather than a single destination app.

### Intended Beneficiaries

**Primary:** any young person, on any social or messaging platform, in any region — the tool is platform-agnostic by design, integrated at the operating-system level rather than tied to one app or network.

**Secondary:** teachers and parents who can use it as a discussion aid, and communities currently underserved by English-first fact-checking tools, since the same architecture can operate in any language.

### Prototype / Concept Design

**Web application** (built and deployed): the user pastes any suspicious text, and an AI model returns:
- The exact phrases responsible, highlighted inline within the original text
- A plain-language explanation for each highlight, categorized as disinformation/manipulation *or* phishing
- A general, transferable tip for evaluating future content
- An explicit disclaimer that the tool does not verify facts and always encourages independent cross-checking

**Native Android application** (built and functional): the same analysis engine, integrated at the operating-system level so it works inside any app, not one specific platform:
- Registered in Android's text-selection toolbar (`ACTION_PROCESS_TEXT`) — select any text in any app, tap Trust Signal, get an instant analysis
- A floating on-screen quick-access button (clipboard-based, not invasive screen-reading) for one-tap analysis from any app
- Share Sheet integration as an additional entry point

Live demo: https://unesco-cyan.vercel.app

### Originality

Unlike existing binary "fake/real" detectors, Trust Signal never claims certainty about facts. It surfaces *observable* rhetorical and structural signals — for disinformation and, as a distinct category, for phishing/scam patterns — and explains its reasoning directly inline, in the original text, grounded in the psychological mechanisms these techniques actually exploit. This design was chosen deliberately after recognizing that false-certainty AI tools can cause real harm by mislabeling genuine, shocking-but-true news.

Combined with true OS-level integration (text-selection toolbar, floating button) rather than a single destination app, this removes the adoption friction that limits most existing MIL tools — it works on any platform the user is already using. The underlying architecture is also language-agnostic: our current build demonstrates the approach in a non-English language, proving the model does not require an English-first market to work, and can be extended to any language or region.

### Practical Feasibility & Long-Term Viability

The core product is fully built, deployed, and functioning today: the web app is live in production, and the Android app builds and installs successfully. The architecture is intentionally lightweight — a single AI-analysis endpoint reused by both the web and Android clients — which keeps ongoing hosting and maintenance costs low and allows rapid iteration. Because the design is platform- and language-agnostic, our sustainability path includes partnerships with youth organizations and schools across regions, and expansion to any messaging or social platform that supports OS-level text-selection integration.

---

## 3. Video Pitch — suggested structure (max 3 minutes)

1. **0:00–0:30 — The problem.** Show a real forwarded message containing a phishing or disinformation example on any messaging app. "How do you know if you should trust this?"
2. **0:30–1:00 — Why existing tools fall short.** Fake/real detectors return a verdict with no explanation, so the user learns nothing — and why people fall for it in the first place (urgency, social proof, emotional reaction before reasoning).
3. **1:00–2:15 — Live demo.** Select suspicious text in any app, tap Trust Signal in the toolbar, and show the highlighted phrases, explanations, and tip appearing instantly. Show a phishing example as well.
4. **2:15–2:45 — Why it matters.** Tie back to MIL: the tool builds a transferable skill rather than dependency on one app, works on any platform, and respects the difference between "questionable" and "false."
5. **2:45–3:00 — Close.** Introduce the team, call to action, live link.

---

## 4. Still needed from you

- Team member full names, ages, countries, and roles
- Recording of the pitch video
- Final read-through of the proposal text above before exporting to Word/PDF
