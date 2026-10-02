# Trust Signal

O‘zbek tilidagi kiberfiribgarlikdan himoya yordamchisi. Matn, hodisa tavsifi,
havola, skrinshot/QR va ovozli xabarlardagi aldov xavfini tahlil qiladi.
Fishing, soxta bank/kuryer, to‘lov va investitsiya aldovi, akkauntni egallash,
masofaviy kirish, shantaj va soxta ish takliflariga yo‘naltirilgan.

Natija: xavf darajasi, aldov turi, dalillar, hozir bajariladigan himoya
choralari va allaqachon zarar yuz bergan bo‘lsa tiklash qadamlari.
[Yangi tahlil mantiqi](CYBER_LOGIC.md) va API migratsiyasi bilan tanishing.

## Tuzilma

- `android-app/`: native Kotlin Android ilovasi (Android 7+, API 24).
- `backend/`: FastAPI + Gemini; xavfsiz URL yuklash va deterministik havola tekshiruvi.
- `CYBER_LOGIC.md`: kiberxavf baholash qoidalari va API migratsiyasi.
- `proposal.md`, `SUBMISSION.md`: loyiha taqdimoti manbalari.
- `REVIEW.md`: dastlabki audit va tuzatishlar holati.

Bu repozitoriyda Next.js/WebView ilovasi mavjud emas.

## Backendni ishga tushirish

Python 3.12+, ffmpeg (`ffprobe`) va zbar kerak:

```bash
# Ubuntu/Debian
sudo apt-get install ffmpeg libzbar0
# macOS: brew install ffmpeg zbar
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env ichidagi GEMINI_API_KEY ni to‘ldiring
uvicorn app:app --host 127.0.0.1 --port 8010 --workers 1 --no-proxy-headers
```

Model nomi `.env` orqali boshqariladi. Kalit/model mavjudligi sizning
provayder hisobingizga bog‘liq; lokal testlar haqiqiy AI xizmatini chaqirmaydi.
Android 2.0.0 uchun yangi `cyber-v1` backend ham deploy qilinishi shart.
Eski MIL javobi yangi ilovada kiberxavf tahlili sifatida ko‘rsatilmaydi.
API va deployment tafsilotlari: [backend/README.md](backend/README.md).

## Android

JDK 17 va Android SDK 36 kerak. Android Studio’da `android-app/` ni oching
va Gradle JVM sifatida JDK 17 ni tanlang. API manzili
`AnalyzeApi.kt` dagi `BASE_URL` orqali belgilanadi (joriy qiymat:
`https://signal.boos.uz`).

```bash
cd android-app
./gradlew :app:assembleDebug :app:lintDebug
```

APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`.
Release imzolash uchun gitga kiritilmaydigan `keystore.properties` kerak.

## Tekshirish

```bash
backend/venv/bin/python -m unittest discover -s backend/tests -v
```

GitHub Actions backend testlari va Android build/lint’ni bajaradi.
[Qurilma sinovi](android-app/TESTING.md) haqiqiy Android qurilmasi yoki
emulator bilan bajariladi. [AI baholash](backend/evals/README.md) alohida,
qo‘lda ishga tushiriladi va haqiqiy model chaqiruvlarini sarflaydi.

## Ma’lumotlar oqimi

Foydalanuvchi tanlagan matn/media HTTPS orqali backendga, keyin Gemini’ga
uzatiladi. Ilovada tahlil tarixi bazasi yo‘q; oxirgi natija jarayon xotirasida.
Backend faqat kunlik model chaqiruvlari sonini SQLite’da saqlaydi. Audio
tekshiruvi uchun vaqtinchalik lokal fayl yaratiladi va tekshiruvdan so‘ng
o‘chiriladi. Provayderning ma’lumot saqlash siyosati alohida amal qiladi.
