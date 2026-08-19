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
    "tip": "..."
  }
  ```

## Status

- ✅ Built and tested locally against the real Gemini API (`gemini-3.6-flash`)
- ✅ Verified: neutral text → no false positive; urgency, phishing, and
  prize-scam examples → correctly flagged with accurate signals
- ⏳ Not yet deployed anywhere reachable from the Android app — the app
  still points at the old Vercel URL until this is hosted somewhere
  (VPS / Render / Railway / Cloud Run, etc.) and `AnalyzeApi.BASE_URL`
  is updated
- ⏳ Not yet load-tested or rate-limited
