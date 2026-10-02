# Trust Signal — kod auditi

## Keyingi o‘zgarish: kiberfiribgarlik yo‘nalishi

Foydalanuvchining yangi topshirig‘i asosida MIL tahlili o‘rniga
`cyber-v1` shartnomasi joriy qilindi. Matn/URL/rasm/audio uchun yangi
promptlar, 12 ta toifa, severity asosida 4 darajali xavf bahosi,
himoya va tiklash qadamlari qo‘shildi. Android 2.0.0 shu natijalarni
ko‘rsatadi; backend ham yangilanishi kerak. Tafsilotlar: `CYBER_LOGIC.md`.

Bu bosqichda **42/42 backend testi o‘tdi**, Android debug build va lint
muvaffaqiyatli yakunlandi. 12 ta live-eval misoli tayyorlandi, haqiqiy model
eval va production deploy bajarilmadi. Production endpoint hali eski schema
qaytarayotgani tasdiqlandi. Pastdagi audit avvalgi holat tarixidir.

Sana: 2026-09-30. Lokal repozitoriy holati tekshirildi. Ishlab turgan server konfiguratsiyasi va real Gemini javoblarining sifati bu auditda tekshirilmagan.

## Tuzatishlar holati — 2026-09-30

Quyidagi dastlabki topilmalar foydalanuvchi topshirig‘i bilan tuzatildi.
Pastdagi audit tavsiflari va qator raqamlari o‘zgartirishdan oldingi holatga
tegishli; joriy arxitektura `ARCHITECTURE.md` da.

| Topilma | Bajarilgan ish |
|---|---|
| 1. API yuklamasi/xarajati | IP rate limit, parallel tahlillar limiti, SQLite’da UTC kunlik model urinishlari kvotasi; SDK timeout/retry/output cheklovi |
| 2. Maqola URL tekshiruvi | Boshlang‘ich URL, redirectlar va HTML href’lari natijaga qo‘shiladi |
| 3. Overlay matni | `extractedText` asosiy ekran bilan bir xil ishlatiladi |
| 4. QR ko‘rinishi | To‘liq QR tarkibi signal kartasida ko‘rsatiladi; avtomatik ochilmaydi; decoder ishlamasa xabar beriladi |
| 5. Media limitlari | JSON o‘qilishidan oldin body limiti/timeout, base64 uzunligi, haqiqiy rasm formati va 16MP, audio uchun ffprobe/5 daqiqa |
| 6. Fetch muddati | Alohida worker jarayoni 12s da to‘xtatiladi; katta sahifa rad qilinadi; 6000 belgiga qisqartirish ko‘rsatiladi |
| 7. URL parser | `urlparse().hostname`, IPv4/IPv6, userinfo belgisi, brend chegaralari va ommaviy IP tekshiruvi |
| 8. Hujjatlar/testlar | README/arxitektura/taklif yangilandi; 32 test, GitHub Actions, deployment namunalari, AI eval fixturelari qo‘shildi |
| 9. Android lint | API 29 tema resurslari ajratildi, accessibility va string resurslari yaxshilandi |

Qo‘shimcha: Unicode case-insensitive iqtibos qidiruvida asl matn indekslari
saqlanadi; input validatsiyasi xatolari maxfiy inputni qaytarib chiqarmaydi;
promptda kontent ichidagi ko‘rsatmalarni bajarmaslik qoidasi qo‘shildi.

Yakuniy tekshiruvlar:

- `backend/venv/bin/python -m unittest discover -s backend/tests -v`: **32/32 o‘tdi**; haqiqiy QR va WAV, SSRF/redirect, process timeout, kvota/rate/body, provider xatosi tekshirildi.
- `:app:assembleDebug :app:lintDebug`: **BUILD SUCCESSFUL**, **0 error, 6 warning**. Qolganlari uchta kutubxonaning yangi versiyasi borligi va uchta ishlatilmagan resurs haqida.
- `git diff --check`: o‘tdi. `pip check`: buzilgan dependency topilmadi.
- Lokal QR uchun zbar o‘rnatildi. Audio tekshiruvi uchun ffmpeg qayta o‘rnatilib, ishlashi WAV testi bilan tasdiqlandi.

Chegaralar: production serverga deploy qilinmadi; CI konfiguratsiyasi lokal
qo‘shildi, GitHub’da hali bajarilmadi. Real telefon/emulator va haqiqiy
Gemini sifat testi bajarilmadi. AI eval runner qo‘lda `--live` bilan ishlaydi.
PDF/DOCX/video eksportlari yangilanmadi; Markdown manbalari yangilandi va
eksportlarni qayta chiqarish zarurati submission hujjatida qayd etildi.

Deployment **1 worker** bilan ishlaydi; rate/concurrency limiter jarayon
miqyosida. Kunlik kvota chaqiruvlar sonini chegaralaydi, dollar budjeti emas.
Tizim dependencylari `ffmpeg` va `libzbar` yangi serverda ham o‘rnatilishi kerak.

## Hozirgi tuzilma

- Android: Kotlin, XML va dasturiy View interfeysi; MainActivity, BubbleService, umumiy AnalysisResultView, OkHttp va coroutines. WebView mavjud emas.
- Backend: FastAPI, Gemini, Pydantic javob sxemalari; matn, maqola URL, rasm va audio uchun uchta tahlil endpointi.
- Qo‘shimcha imkoniyatlar: deterministik domen tekshiruvi, QR dekodlash, matndagi belgilarni ajratish, SIFT tekshiruv qadamlari, Android Share va Process Text integratsiyasi.
- Ma’lumotlar bazasi, foydalanuvchi hisoblari va saqlanadigan tahlil tarixi kodi mavjud emas. Oxirgi Android natijasi jarayon xotirasida saqlanadi.

## Topilmalar

### 1. Yuqori: pullik AI endpointlarida foydalanish chegaralari yo‘q

Manba: `backend/app.py:470`, `:485`, `:589`, `:668`.

Ilova kodida autentifikatsiya, so‘rov chastotasi cheklovi, parallel tahlillar limiti yoki xarajat kvotasi mavjud emas. Har bir yaroqli so‘rov Gemini chaqiruviga olib keladi. Tashqi foydalanuvchi takroriy so‘rovlar bilan xarajat va server yukini oshirishi mumkin. Nginx/Cloudflare darajasida himoya bor-yo‘qligi lokal koddan aniqlanmaydi. CORS bu himoyalarning o‘rnini bosmaydi.

Tavsiya: serverda rate limit, global parallel so‘rov limiti va xarajat kvotasi; kerak bo‘lsa foydalanuvchi yoki qurilma sessiyasi. APK ichiga umumiy maxfiy kalit joylash mustaqil himoya bo‘la olmaydi.

### 2. Yuqori: maqola rejimi yuborilgan URL xavf belgilarini tekshirmaydi

Manba: `backend/app.py:503–550`.

URL alohida yuborilganda `prompt_content` sahifa matniga almashtiriladi. Yakunda `merge_link_signals` faqat shu matnni tekshiradi. Masalan, `https://payme-login.xyz/story` oddiy matn ichida signal beradi, ammo URL rejimida neytral sahifa va neytral model javobi bilan `signals=[]`, `cautionLevel=belgi_topilmadi` qaytdi. Bu holat tarmoqsiz, fetch va model javobi mock qilingan testda tasdiqlandi. HTML havolalarining `href` qiymatlari ham `get_text()` bilan yo‘qoladi.

Tavsiya: boshlang‘ich URL, redirect manzillari va tegishli sahifa havolalarini matndan alohida tekshirish; topilmalarni birlashtirish.

### 3. O‘rta: suzuvchi oynada maqola iqtiboslari ajratilmaydi

Manba: `android-app/app/src/main/java/uz/trustsignal/app/BubbleService.kt:428`.

`resultView.render(text, outcome.result)` ga boshlang‘ich URL uzatiladi. Server qaytargan `extractedText` ishlatilmaydi. Natijada maqola iqtiboslari URL ichidan qidiriladi va ajratilmaydi. MainActivity bu holatni `extractedText.ifBlank { trimmed }` bilan to‘g‘ri ishlaydi.

Tavsiya: ikkala kirish yo‘lida bir xil kontent tanlash qoidasini ishlatish.

### 4. O‘rta: QR manzili foydalanuvchiga ko‘rinmay qoladi

Manba: `backend/app.py:649–663`; `AnalysisResultView.kt:211–233`.

Backend dekodlangan QR manzilini `Signal.quote` ga yozadi, lekin extractedText ichiga qo‘shmaydi. Android signal kartasi faqat technique va explanation ni chiqaradi, quote ni chiqarmaydi. Rasmda manzil oddiy matn sifatida yo‘q bo‘lsa, foydalanuvchi «shu manzilga olib boradi» degan izohni ko‘radi, ammo manzilni ko‘rmaydi. Vizual signalning matnga mos kelmagan tavsifi ham shu sabab yo‘qolishi mumkin.

Tavsiya: kartada iqtibosni, ayniqsa QR manzilini alohida ko‘rsatish; uni avtomatik ochmaslik.

### 5. O‘rta: media hajmi xotiraga yuklangandan keyin tekshiriladi

Manba: `backend/app.py:93–104`, `:596–617`, `:680–689`.

Base64 maydonlariga sxemada maksimal uzunlik berilmagan. Avval HTTP JSON qabul qilinadi va base64 to‘liq dekodlanadi, keyin 6/12 MB limiti tekshiriladi. Katta so‘rov limitdan qaytarilguncha sezilarli xotira sarflaydi. Faqat sxema limiti ham HTTP body ni qabul qilish bosqichini to‘liq himoya qilmaydi.

Tavsiya: reverse proxy/ASGI qatlamida body limiti, dekodlashdan oldin base64 uzunligi limiti, rasm uchun maksimal piksel soni va audio davomiyligi chegarasi. Deploymentdagi mavjud body limiti tekshirilishi kerak.

### 6. O‘rta: URL yuklashning 12 soniyalik umumiy chegarasi qat’iy emas

Manba: `backend/app.py:239`, `:278–337`.

Deadline tarmoq amallari orasida tekshiriladi. DNS `getaddrinfo` va `resp.read(65536)` vaqtida alohida umumiy bekor qilish mexanizmi yo‘q. Read timeout umumiy operatsiya muddati bilan bir xil emas; sekin oqim ishchi oqimni kutilganidan uzoq band qilishi mumkin. Deadline yoki hajm chegarasiga yetganda olingan qisman sahifa ham tahlil qilinadi, foydalanuvchiga kesilgani aytilmaydi. Bu kod tahlilidan topilgan; real sekin server bilan yuklama testi bajarilmadi.

Tavsiya: butun yuklash operatsiyasiga bekor qilinadigan timeout, qolgan vaqtga mos socket timeout va qisman kontent holatini ochiq qaytarish.

### 7. O‘rta: domen parseri ayrim URL turlarini noto‘g‘ri ajratadi

Manba: `backend/app.py:160–163`.

`_domain_of` URL ni `/`, `?`, `#`, `:` bilan qo‘lda bo‘ladi. Lokal tekshiruvda IPv6 URL uchun domen `[2606`, userinfo bor URL uchun esa `google.com@8.8.8.8` chiqdi. IPv6 literal uchun IP signali aniqlanmadi. Bu deterministik signal qatlamidagi kamchilik; fetch qatlamida alohida `urlparse` va SSRF himoyasi bor.

Tavsiya: `urlparse(...).hostname`, `ipaddress.ip_address` va domen chegaralarini hisobga oladigan brend tekshiruvi.

### 8. Past: hujjatlar va takrorlanadigan tekshiruvlar yetishmaydi

- Bosh README Next.js ishga tushirishini ko‘rsatadi, lekin ushbu repozitoriyda Next.js kodi/package.json yo‘q.
- ARCHITECTURE.md eski WebView/OpenAI holatini tasvirlaydi; joriy kod native Android/Gemini.
- Proposal va submission matnidagi web ilova haqidagi ma’lumotlar joriy kod tarkibi bilan moslashtirilishi kerak; tashqi saytning mavjudligi tekshirilmadi.
- Git kuzatuvidagi avtomatik testlar va CI konfiguratsiyasi topilmadi.
- QR uchun `pyzbar` ishlamay qolsa exception loglanib, bo‘sh natija qaytadi; zarur tizim kutubxonasi o‘rnatilishi backend setup hujjatida ko‘rsatilmagan.

### 9. O‘rta: Android lint tekshiruvi o‘tmaydi

Manba: `android-app/app/src/main/res/values/themes.xml:13` va `values-night/themes.xml:10`.

Debug APK muvaffaqiyatli yig‘ildi, lekin `lintDebug` 2 ta error va 48 ta warning bilan tugadi. Ikkala error ham `android:forceDarkAllowed` API 29 atributi minSdk 24 uchun umumiy resource fayllarida berilgani haqida. API 29 ga tegishli qiymatni tegishli versiyali resource orqali ajratish kerak. Ogohlantirishlar orasida tarjima resurslariga chiqarilmagan matnlar va suzuvchi tugma accessibility masalasi ham bor.

## Yaxshi ishlangan qismlar

- Faktning rost/yolg‘onligini qat’iy hukm qilishdan saqlovchi mahsulot yo‘nalishi prompt va natijani ulashish matnida bor.
- SSRF himoyasi: private/local IP tekshiruvi, IP pinning, redirectlarda qayta tekshiruv, port va userinfo cheklovi.
- API kaliti environment orqali olinadi; Android kodiga joylanmagan.
- Tarmoq so‘rovi coroutine bekor qilinishi bilan uziladi; overlay/service yopilishida joblar tozalanadi.
- Audio oqimini o‘qishda limit, rasmlarni kichraytirish va foydalanuvchiga o‘zbekcha xatolik xabarlari bor.
- Natija UI asosiy ekran va overlay o‘rtasida qayta ishlatiladi; light/dark ranglar ajratilgan.

## Bajarilgan tekshiruvlar va chegaralar

- Backend lokal virtual environment orqali import qilindi; haqiqiy API kaliti o‘rniga dummy kalit ishlatildi.
- `/health` → 200; bo‘sh matn, 8000 belgidan uzun matn, buzilgan rasm/audio base64 → 400.
- Maqola URL tekshiruvi yo‘qolishi mock orqali takrorlandi; domen parsing holatlari lokal bajarildi.
- JDK 17 bilan `:app:assembleDebug` o‘tdi; `:app:lintDebug` 2 error va 48 warning bilan tugadi. Dastlab offline kesh yetishmagani sabab build to‘xtagan edi; dependencylar yuklangach qayta tekshirildi.
- Real Gemini chaqirilmadi; aniqlik, false-positive ko‘rsatkichi va prompt injectionga chidamlilik o‘lchanmadi.
- Telefon/emulatorda UI sinovi, production infratuzilma auditi va yuklama testi bajarilmadi.
- PDF/DOCX eksportlarining maketi va video ko‘rib chiqilmadi; taklifning Markdown manbasi o‘qildi.

## Ish tartibi

1. So‘rov/xarajat/body limitlari va URL rejimida havola tekshiruvini tuzatish.
2. Overlay maqola matni va QR manzilining ko‘rinishini tuzatish.
3. Regressiya testlari: oddiy matn, URL/redirect, noto‘g‘ri input, media limitlari, model xatosi.
4. Android build/lint va qurilmada Share, Process Text, overlay, back/close holatlarini tekshirish.
5. Hujjatlarni yangilash; belgilangan o‘zbekcha dataset bilan AI sifatini o‘lchash.
