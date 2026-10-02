# Trust Signal 2.0 — kiberfiribgarlik tahlili

Yangi maqsad: foydalanuvchi uchratgan yoki boshidan kechirgan raqamli aldov
holatini tushuntirish, dalillarni ko‘rsatish va keyingi himoya amallarini berish.
Tahlil kirishi: matn/suhbat/hodisa tavsifi, havola, rasm/QR, audio.

## Xavf darajalari

| API | Ekrandagi ma’nosi | Asos |
|---|---|---|
| none | Aniq xavf belgisi topilmadi | Dalil yo‘q yoki faqat neytral kuzatuv; xavfsizlik kafolati emas |
| suspicious | Shubhali — tekshirish kerak | Kontekst yoki dalil cheklangan, masalan qisqartirilgan URL |
| high | Yuqori xavf | Kod/parol/pul/kirishni olishga aniq zararli urinish yoki shantaj |
| critical | Shoshilinch himoya kerak | Foydalanuvchining o‘zi kod/pul/kirish bergani yoki zarar yuz berganini bildirgan |

Umumiy daraja belgilar sonidan emas, eng jiddiy signalning `severity`
qiymatidan hisoblanadi. 20 ta kuchsiz belgi o‘z-o‘zidan yuqori xavf bo‘lmaydi.
Oddiy QR mavjudligi `info`: xavfni oshirmaydi. Firibgarning «hisobing
buzilgan» degan da’vosi foydalanuvchi haqiqatan buzilganini bildirgani bilan
bir xil emas. Ushbu kontekstni model ajratadi; xato ehtimoli mavjud.

## Aniqlanadigan toifalar

Fishing (`phishing`), tashkilot/shaxsga taqlid (`impersonation`), to‘lov aldovi
(`payment_scam`), investitsiya/piramida (`investment_scam`), akkauntni egallash
(`account_takeover`), zararli dastur yoki masofaviy kirishga undash
(`malicious_software`), shantaj (`extortion`), savdo (`shopping_scam`), soxta
ish (`job_scam`), munosabat orqali aldash (`romance_scam`), shubhali URL
(`suspicious_link`) va boshqa raqamli aldov (`other`).

## Natija oqimi

1. Xavf darajasi va qisqa izoh.
2. Aniqlangan toifalar.
3. Hozir bajariladigan 2–4 himoya amali (`immediateActions`).
4. Pul, kod yoki kirish allaqachon berilgan bo‘lsa shartli tiklash qadamlari (`recoverySteps`).
5. Matndagi dalillar va har bir belgining izohi.
6. Rasmiy kanal orqali tekshirish (`checkSteps`) va himoyalanish maslahati.

Savolni yoki maxfiy kodning o‘zini yuborish shart emas: «kodimni aytdim,
hisobimdan pul ketdi» kabi hodisa tavsifi ham tahlil qilinadi.

## Nimalar xavf deb olinmaydi

Oddiy yangilik, siyosiy fikr, hissiy matn, clickbait, logoning sifati yoki
QR’ning o‘zi kiberjinoyat dalili emas. Xavfsizlik bo‘yicha ogohlantirishdagi
«kod bermang» iborasi kod o‘g‘irlashga urinish sifatida talqin qilinmasligi
prompt va eval misollarida belgilangan. Modelning shunday ishlashi real
baholashda alohida tekshiriladi, unit testlar bilan kafolatlanmaydi.

Ilova faylni ishga tushirib tekshirmaydi, qurilmani skanerlamaydi, shaxsni
aniqlamaydi, domen reputatsiyasi yoki deepfake ekspertizasi bajarmaydi.
APK va virus fayllarini bevosita qabul qilish bu versiyaga kirmaydi; bunday
faylni o‘rnatishga undovchi xabar tahlil qilinadi.

## API va chiqarish

Endpointlar o‘zgarmaydi. Javobda `analysisVersion: cyber-v1`, `riskLevel`,
`riskTypes`, signal uchun `category`/`severity`, `immediateActions` va
`recoverySteps` qo‘shildi. `cautionLevel` eski ilovalar uchun saqlanadi:
none → belgi_topilmadi; suspicious → ozgina_belgi; high/critical → kop_belgi.

Avval yangi backendni deploy qiling, keyin Android 2.0.0 ni tarqating.
Yangi Android eski MIL javobini qabul qilsa, serverni yangilash kerakligini
ko‘rsatadi. Backendni deploy qilmasdan APKni almashtirish yetarli emas.
Joriy ish lokal kodga qo‘llandi, production deploy bajarilmadi.

## Tekshiruv va tavsiya manbalari

Backend unit/integratsiya testlari xavf siyosati, javob shartnomasi va
avvalgi xavfsizlik himoyalarini tekshiradi. `backend/evals/cases.json` da
12 ta kutilgan xavf darajasi belgilangan o‘zbekcha misol bor; haqiqiy model
uchun `evals/run.py --live` alohida ishga tushiriladi. Bu ishda real model
eval bajarilmagan. Telefon sinovlari `android-app/TESTING.md` da.

Hisob va to‘lov zarari bo‘yicha umumiy tiklash yo‘nalishlari
[FTC tavsiyalari](https://consumer.ftc.gov/articles/what-do-if-you-were-scammed),
fishingni rasmiy kanal orqali tekshirish esa
[FTC fishing yo‘riqnomasi](https://consumer.ftc.gov/articles/how-recognize-avoid-phishing-scams)
bilan moslashtirilgan. Mahalliy telefon/qonun yoki pulni qaytarish kafolati
o‘ylab topilmaydi. Bu manbalar modelning barcha javoblarini tasdiqlamaydi.
