# Trust Signal API

Kiberfiribgarlik tahlili: Python 3.12+, FastAPI va Google Gemini. O‘rnatish: [bosh README](../README.md).
Tizim dependencylari: `ffmpeg` (ffprobe) va `libzbar0` (macOS: `zbar`).
Python paketlari `requirements.txt` orqali o‘rnatiladi. `.env.example` ni
`.env` ga ko‘chirib API kalitini kiriting.

## API

- `GET /health`: liveness, `{"status":"ok"}`. AI/QR ishlashini kafolatlamaydi.
- `POST /api/analyze`: `{"content":"..."}`, 3–8000 belgi.
- `POST /api/analyze-image`: `{"imageBase64":"...","mimeType":"image/png"}`;
  JPEG/PNG/WebP, 6MiB gacha, 32px dan kam emas, 16 megapikseldan oshmaydi.
- `POST /api/analyze-audio`: `{"audioBase64":"...","mimeType":"audio/ogg"}`;
  12MiB gacha, 5 daqiqagacha, faqat audio oqimlari. OGG, MP3, MP4/M4A, AAC,
  WAV, WebM, Opus, AMR, 3GPP va FLAC MIME’lari qabul qilinadi; kontent ham
  ffprobe bilan tekshiriladi. Aniqlanmagan davomiylik rad qilinadi.

Umumiy javob:

```json
{
  "analysisVersion": "cyber-v1",
  "riskLevel": "none",
  "riskTypes": [],
  "cautionLevel": "belgi_topilmadi",
  "summary": "Aniq belgi topilmadi.",
  "signals": [],
  "tip": "Manbani mustaqil tekshiring.",
  "immediateActions": [],
  "recoverySteps": [],
  "checkSteps": [],
  "extractedText": "",
  "warnings": []
}
```

Signal: `{"technique":"...","quote":"...","explanation":"...","category":"phishing","severity":"high"}`.
`riskLevel`: `none`, `suspicious`, `high`, `critical`; server eng jiddiy
signal bo‘yicha hisoblaydi. `info` kuzatuvi hisobga kirmaydi. `riskTypes`
toifalari va amaliy misollar [CYBER_LOGIC.md](../CYBER_LOGIC.md) da.
`cautionLevel` eski mijozlar uchun moslik maydoni; yangi Android `riskLevel` ishlatadi.
Caution qiymatlari: `belgi_topilmadi`, `ozgina_belgi`, `kop_belgi`.
URL-only so‘rovda sahifa yuklanadi; boshlang‘ich manzil, redirectlar va
sahifadagi dastlabki 100 ta noyob havolagacha tekshiriladi. Havolalar
soni chegarasiga redirectlar ham kiradi. Maqolaning dastlabki 6000 belgisi
modelga beriladi; kesilgan bo‘lsa `warnings` da aytiladi. QR tarkibi to‘liq
`quote` da qaytadi. QR decoder ishlamasa natija buni `warnings` da bildiradi.

Xatolar: 400 input/media/URL, 408 upload timeout, 413 body limiti,
415 siqilgan HTTP body, 429 rate/daily limit, 502 AI xatosi,
503 parallel so‘rovlar limiti yoki tekshiruv/hisob xizmati mavjud emas.
Javob `detail` maydonida o‘zbekcha xabar saqlaydi; input qaytarib chiqarilmaydi.

## Himoyalar va konfiguratsiya

| Sozlama | Default | Mazmuni |
|---|---|---|
| `RATE_LIMIT_PER_MINUTE` | 10 | IP uchun har 60s vaqt bo‘lagidagi so‘rovlar |
| `MAX_CONCURRENT_ANALYSES` | 4 | Body yuklashni ham qo‘shgan parallel tahlillar |
| `DAILY_MODEL_REQUEST_LIMIT` | 500 | UTC kuniga model urinishlari, xatolar ham hisoblanadi |
| `BUDGET_DB_PATH` | backend/data/usage.sqlite3 | Restartdan keyin saqlanadigan hisob bazasi |
| `CORS_ORIGINS` | bo‘sh | Vergul bilan ajratilgan brauzer origin’lari |
| `FFPROBE_PATH` | ffprobe | Audio tekshiruvchi binary |

`.env.example` dagi nisbiy DB yo‘li backend ishchi katalogiga nisbatan.
Qiymatlar musbat bo‘lishi kerak. **Bir worker bilan ishlating.** Bu ochiq
anonim API; IP limiter foydalanuvchi autentifikatsiyasi emas. Kunlik limit
chaqiruvlar soni, aniq dollar budjeti emas. SQLite o‘chirilsa hisob ham
qayta boshlanadi: deploymentda persistent disk ishlating.

JSON o‘qilishidan oldin endpointga mos body limit, 20s upload deadline va
siqilgan body taqiqi bor. Base64 uzunligi dekodlashdan oldin ham cheklanadi.
Gemini uchun SDK timeout 45s, 1 attempt, max output 4096 token. Bu SDK
network timeout’i, butun AI hisoblashiga alohida process deadline emas.
Maqola fetch’i esa 12s hard process timeout bilan cheklangan.

## Deployment

`deploy/trust-signal.service` va `deploy/nginx.conf` namunalari berilgan.
Path, user, domain va TLS konfiguratsiyasini serveringizga moslang. Ular
avtomatik o‘rnatilmagan va joriy serverga deploy qilinmagan.
Oldingi production hujjatlarida service porti 8002 deb ko‘rsatilgan;
namunalar shu portni saqlaydi. Uvicorn’ni systemd orqali bitta worker bilan
ishga tushiring. Maxfiy `.env` faylni yangi reliz bilan almashtirmang.

Nginx faqat loopback’dagi backendga ulanadi; `X-Forwarded-For` ni mijozdan
ko‘chirib qo‘shish o‘rniga ishonchli client manzili bilan almashtiradi.
Cloudflare kabi qo‘shimcha proxy ishlatilsa, nginx real-IP trust ro‘yxatini
faqat uning rasmiy IP tarmoqlariga sozlash kerak; aks holda limiter hamma
mijozni bitta proxy deb ko‘rishi mumkin. `--forwarded-allow-ips '*'` ishlatmang.
Uvicorn [rasmiy proxy sozlamalari](https://www.uvicorn.org/settings/).

## Tekshiruvlar

```bash
venv/bin/python -m unittest discover -s tests -v
```

Testlar hech qanday pullik Gemini chaqiruvi bajarmaydi. AI sifati uchun
[eval fixturelari va runner](evals/README.md) bor. QR uchun zbar yuklanishini
tekshirish: `venv/bin/python -c 'from pyzbar.pyzbar import decode'`.

Maxfiy kontent uchun access/application log retention’ni ham serverda
sozlang. Ilova tahlil kontentini DBga saqlamaydi; temp audio fayli tekshiruv
oxirida o‘chiriladi. Gemini ma’lumot siyosatini operator alohida tekshiradi.
