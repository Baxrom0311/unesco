# Trust Signal — Python/Gemini backend

FastAPI backend replacing the old Next.js `/api/analyze` route. Same
request/response contract, so the Android app only needs its `BASE_URL`
updated once this is deployed somewhere reachable.

## Setup

```bash
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # then fill in GEMINI_API_KEY
```

## Run locally

```bash
uvicorn app:app --host 0.0.0.0 --port 8010
```

## Endpoints

- `GET /health` → `{"status": "ok"}`
- `POST /api/analyze` — body `{"content": "..."}`, returns:
  ```json
  {
    "cautionLevel": "belgi_topilmadi | ozgina_belgi | kop_belgi",
    "summary": "...",
    "signals": [{"technique": "...", "quote": "...", "explanation": "..."}],
    "tip": "...",
    "checkSteps": ["...", "..."],
    "extractedText": ""
  }
  ```
  - If `content` is a single URL, the server safely fetches the page
    (SSRF-guarded: private/loopback/link-local addresses blocked, redirects
    re-checked, 2MB cap) and analyzes it in article mode (headline-body
    mismatch, clickbait). The fetched text is returned in `extractedText`.
  - Deterministic link checks (shorteners, suspicious TLDs, IP-literal URLs,
    punycode, brand lookalikes) are merged into `signals`.
- `POST /api/analyze-image` — body `{"imageBase64": "...", "mimeType": "image/jpeg|png|webp"}`
  (≤6MB decoded). Gemini vision: extracts visible text into `extractedText`,
  flags textual + visual signals (fake UI, prize pages, chart-axis
  manipulation, fake-profile hints, cautious AI-artifact notes). QR codes are
  decoded server-side (pyzbar) and their URLs surfaced + link-checked.
- `POST /api/analyze-audio` — body `{"audioBase64": "...", "mimeType": "audio/ogg|mpeg|mp4|..."}`
  (≤12MB decoded). Transcribes the voice message into `extractedText` and
  flags scam-call patterns (bank impersonation, OTP requests, urgency,
  secrecy pressure).

Signal taxonomy in the system prompt: A) disinformation/manipulation,
B) phishing, C) scam-group/conversation patterns (10-15 messages),
D) statistics manipulation, E) named propaganda techniques, F) hidden
advertising, G) chain messages. `checkSteps` returns 2-4 SIFT-style
self-verification steps personalized to the analyzed content.

## Status

- ✅ Built and tested locally against the real Gemini API (`gemini-3.6-flash`)
- ✅ Verified: neutral text → no false positive; urgency, phishing, and
  prize-scam examples → correctly flagged with accurate signals
- ✅ Deployed on a shared VPS as `trust-signal.service` (systemd, port 8002),
  reverse-proxied by nginx and served over HTTPS at
  `https://signal.boos.uz` (Cloudflare-proxied DNS, Let's Encrypt cert via
  certbot). `AnalyzeApi.BASE_URL` in the Android app points here.
- ⏳ Not yet load-tested or rate-limited
