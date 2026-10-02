# Trust Signal — joriy arxitektura

Joriy yo‘nalish: kiberfiribgarlik va raqamli aldov. Tafsilotlar `CYBER_LOGIC.md` da.

## Oqim

```text
Android MainActivity / BubbleService
  → OkHttp, HTTPS JSON
  → FastAPI RequestGuard (body, upload timeout, IP rate, concurrency)
  → input/media validation
  → [URL bo‘lsa: alohida fetch worker, SSRF himoyasi, 12s hard timeout]
  → durable daily budget reservation
  → Gemini (45s SDK timeout, 1 attempt, 4096 output tokens)
  → deterministic URL/QR signals + severity-based risk policy
  → AnalysisResultView (iqtiboslar, tekshirish qadamlari, warnings)
```

## Android

Kotlin, AppCompat, XML + dasturiy View, coroutines va OkHttp ishlatiladi.
`ACTION_SEND` matn, image va audio qabul qiladi; `ACTION_PROCESS_TEXT`
tanlangan matnni oladi. Foydalanuvchi yoqqan foreground service suzuvchi
tugmani ushlab turadi. Karta fokus olganda clipboard o‘qiladi; tahlil
avtomatik boshlanishi mumkin. So‘rov karta/activity lifecycle’i bilan
bekor qilinadi. Server hisoblashining bekor bo‘lishi kafolatlanmaydi.

`AnalysisResultView` asosiy ekran va overlay uchun umumiy. Maqola/rasm/audio
uchun `extractedText` ishlatiladi; QR yoki matnga mos kelmagan iqtibos signal
kartasida ham ko‘rinadi. Havolalar avtomatik ochilmaydi. `warnings` kesilgan
maqola yoki mavjud bo‘lmagan QR tekshiruvini bildiradi. Eski javoblarda bu
maydon bo‘lmasa, Android bo‘sh ro‘yxat ishlatadi. Yangi Android `analysisVersion=cyber-v1` ni talab
qiladi; eski backendni yangilash haqida tushunarli xabar chiqaradi.

## Backend modullari

- `app.py`: endpointlar, havola signallari va QR.
- `cyber.py`: kiberfiribgarlik promptlari, kategoriyalar, javob sxemalari va
  belgining jiddiyligiga asoslangan xavf baholash. `info` QR kuzatuvi xavf
  darajasini oshirmaydi; bir dona `high` dalil yuqori xavf uchun yetarli.
- `guards.py`: ASGI body/rate/concurrency himoyasi va SQLite kunlik kvotasi.
- `article_fetch.py`: faqat ommaviy IP, 80/443 port, TLS host tekshiruvi,
  IP-pinning va har redirectda qayta tekshiruv. 2MiB dan katta sahifa rad
  qilinadi. DNS yoki sekin o‘qish qotib qolsa worker o‘ldiriladi va kutiladi.
  Tahlil 6000 belgiga kesilsa, natijada bu haqda xabar beriladi.
- `media.py`: haqiqiy rasm formati/piksel tekshiruvi; ffprobe bilan audio
  turi va 5 daqiqalik davomiylik limiti. Tekshiruv mavjud bo‘lmasa audio
  modelga yuborilmaydi.

## Limitlar va joylashtirish

Standart qiymatlar: IP uchun 10 so‘rov/minut, bir vaqtda 4 tahlil, UTC kuniga
500 model urinish. Rate/concurrency bitta jarayonga tegishli: **1 worker**.
Ko‘p serverga o‘tishda umumiy Redis kabi limiter kerak; SQLite faqat bir
saqlash yo‘lidan foydalanadigan jarayonlarda kunlik hisobni birlashtiradi.

Kunlik limit pul miqdori emas: model narxi va input hajmi xarajatni
belgilaydi. Provayder hisobidagi billing cheklovini ham sozlash kerak.
Muvaffaqiyatsiz model urinishlari ham hisoblanadi, avtomatik retry yo‘q.
Ochiq anonim xizmatda distributed abuse’ni IP limiti butunlay to‘xtatmaydi.
Hisob/session autentifikatsiyasi hozircha mahsulot oqimiga kiritilmagan;
APK ichiga umumiy maxfiy kalit joylashtirilmaydi.

Backend internetga to‘g‘ridan-to‘g‘ri ochilmaydi; nginx ortida loopback’da
ishlaydi. Proxy sozlamalari va ishga tushirish namunalari `backend/deploy/`.
Default CORS bo‘sh: native Android CORS’ga bog‘liq emas. Brauzer mijoz
qo‘shilsa origin’lar `.env` da aniq sanaladi.

## Tekshiruv

Backend testlari model/fetch javoblarini mock qiladi, SSRF, redirect,
body/rate/budget, QR, provider failure va media validatsiyasini tekshiradi.
ffprobe bilan real qisqa WAV ham sinovdan o‘tadi. Model sifati uchun kichik
qo‘lda belgilangan fixturelar va opt-in eval runner alohida; ular keng
miqyosdagi aniqlik isboti emas.
