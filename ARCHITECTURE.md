# Trust Signal — Arxitektura hujjati (loyiha, tasdiqlanmagan)

> Bu hujjat qaror emas — muhokama uchun. Har bir bo'limda variantlar keltirilgan,
> siz kerakli variantni belgilab, izoh qoldirib, yoki butunlay boshqacha
> yo'nalish yozib chiqishingiz mumkin.

---

## 1. Hozirgi holat (2026-08-18 holatiga)

```
[Android ilova]
   |
   |-- WebView orqali yuklaydi --> https://unesco-cyan.vercel.app (Next.js)
   |-- ACTION_PROCESS_TEXT / ACTION_SEND / Suzuvchi tugma --> shu saytga query param bilan yo'naltiradi
   |
[Vercel'da joylashgan Next.js ilova]
   |
   |-- /api/analyze route --> AI SDK (generateObject) --> OpenAI gpt-4o-mini
       (hozirgina Vercel AI Gateway'dan to'g'ridan-to'g'ri OpenAI'ga
        o'zgartirilgan — SIZNING TASDIG'INGIZSIZ, buni orqaga qaytarish
        yoki saqlab qolish mumkin)
```

**Muammo:** Android ilova hali ham Vercel'dagi saytga bog'liq — "faqat backend
server almashtirish" sayt/WebView qatlamini olib tashlamaydi, faqat u qayerda
joylashganini o'zgartiradi.

---

## 2. Ochiq savol #1: Android ilova WebView orqali ishlaydimi, yoki to'liq native bo'ladimi?

| Variant | Tavsif | Afzallik | Kamchilik |
|---|---|---|---|
| **A. WebView (hozirgi)** | Ilova saytni ko'rsatadi | Tez, bitta kod bazasi (web + android bir xil UI) | "Alohida ilova emas" degan tuyg'u yo'q, saytga bog'liq |
| **B. To'liq native (Kotlin/Compose)** | UI butunlay Android'da qurilgan, faqat API chaqiriladi | Chinakam "native ilova" tuyg'usi, tezroq, offline UI | Butun UI qaytadan yozilishi kerak (bir necha kunlik ish) |

**Sizning belgingiz:** _______________

---

## 3. Ochiq savol #2: Backend qayerda joylashadi?

| Variant | Tavsif | Afzallik | Kamchilik |
|---|---|---|---|
| **A. Vercel (hozirgi)** | Joriy holat | Ishlab turibdi, hech narsa o'zgartirish shart emas | Siz "Vercel kerak emas" dedingiz |
| **B. Render/Railway/Fly.io** | Boshqa bulut hosting | Vercel'ga bog'liq emas, bepul tarif bor | Yangi hisob, qayta deploy, domen o'zgaradi |
| **C. O'z serverimiz (VPS)** | To'liq nazorat | Hech qanday uchinchi tomon platformasiga bog'liq emas | Sozlash, xavfsizlik, monitoring — hammasi qo'lda |
| **D. Backend umuman yo'q, hammasi telefonda** | On-device AI | Serversiz, offline ishlaydi | Model zaifroq, ilova hajmi katta, faqat kuchli telefonlarda |

**Sizning belgingiz:** _______________

---

## 4. Ochiq savol #3: AI qanday ishlaydi?

| Variant | Tavsif | Afzallik | Kamchilik |
|---|---|---|---|
| **A. Hozirgi: bulut LLM + system prompt** (GPT-4o-mini) | Umumiy modelga aniq ko'rsatma beriladi | Ishlab turibdi, tez qurildi, sifat yaxshi | "O'zimizniki" emas, uchinchi tomon API'siga bog'liq |
| **B. Kuchliroq bulut model** (masalan GPT-4o, Claude) | Xuddi shu yondashuv, kattaroq model | Aniqlik oshishi mumkin | Xarajat oshadi |
| **C. Ollama + kichik lokal model** | O'z serverimizda ochiq model ishga tushiriladi | Uchinchi tomon API'siga bog'liq emas | GPU/server kerak, kichik model zaifroq bo'lishi mumkin |
| **D. RAG qo'shish** | Model firibgarlik URL bazasi kabi tashqi ma'lumotni qidiradi | Havolalarni haqiqiy bazadan tekshirish mumkin bo'ladi | Bizning asosiy vazifamiz (uslub tahlili) uchun unchalik foydasi yo'q, alohida baza kerak |
| **E. O'z modelimizni o'qitish (LoRA)** | Maxsus dataset bilan fine-tuning | Nazariy jihatdan eng "bizniki" | Dataset yo'q, GPU kerak, haftalar/oylar vaqt |
| **F. MCP orqali tashqi vositalar** | Modelga URL-tekshirish kabi real vositalar ulanadi | Real tekshiruv qo'shadi | Alohida vosita/API kerak, hozircha yo'q |

**Sizning belgingiz:** _______________

---

## 5. Nima uchun bu savollarni ajratdim

"Backend" so'zi uchta mustaqil narsani anglatishi mumkin: (1) UI qayerda
ishlaydi — telefonda yoki WebView'da, (2) server qayerda joylashgan, (3) AI
qanday ishlaydi. Bu uchtasi bir-biriga bog'liq emas — masalan, backend'ni
Vercel'dan Render'ga ko'chirish AI'ning qanday ishlashiga umuman ta'sir
qilmaydi, va aksincha.

Shuning uchun har birini alohida hal qilish kerak, "hammasini qayta quramiz"
degan bitta katta qarordan ko'ra.

---

## 6. Keyingi qadam

Yuqoridagi uchta jadvalni to'ldirib bering (yoki qo'lda, yoki menga aytib) —
shundan keyin men ANIQ o'sha yo'nalishda, boshqa hech narsani o'zgartirmasdan
ishlayman.
