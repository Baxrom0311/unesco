# Android tekshiruvlari

Avtomatik: JDK 17 bilan `./gradlew :app:assembleDebug :app:lintDebug`.
Quyidagi ro‘yxat emulator/qurilma uchun; uni build muvaffaqiyatli bo‘lishi
o‘rniga bajarilgan deb hisoblamang.

1. Oddiy matnni asosiy ekranga yozing, tahlil natijasi va signal iqtiboslarini ko‘ring.
2. Telegram/brauzerdan matnni Share → Trust Signal orqali yuboring.
3. Matn tanlang → Process Text menyusidagi Trust Signal; oldingi so‘rov ishlayotgan bo‘lsa yangi natija bilan adashmasin.
4. Overlay ruxsatini bering, suzuvchi tugmani yoqing, matn nusxalang, tugmani bosing. Natija kelayotganida kartani yoping va qayta oching.
5. Maqola URL nusxalang va overlay’da tahlil qiling: URL o‘rniga maqola matni va uning ajratilgan iqtiboslari ko‘rinsin.
6. QR bor PNG ni Share qiling: to‘liq QR tarkibi signal kartasida ko‘rinsin, bosilganda avtomatik sayt ochilmasin.
7. Uzun maqola natijasida qisman tahlil haqida xabar ko‘rinsin. QR decoder mavjud bo‘lmagan backendda QR tekshiruvi bajarilmagani ko‘rinsin.
8. Audio Share: qisqa haqiqiy WAV/OGG qabul qilinsin; 5 daqiqadan uzun yoki buzilgan fayl tushunarli xato bersin.
9. Internetni uzing, qayta urinishni tekshiring; 429/503 javoblari foydalanuvchiga xabar sifatida ko‘rinsin.
10. Karta ochiq/yopiq holatda service’ni o‘chiring. Overlay ruxsatini olib qo‘ying va qayta yoqing.
11. Light/dark, landshaft, katta shrift va TalkBack bilan tugma hamda yopish amalini tekshiring. Android API 24 va 29+, shuningdek target 36 qurilmada sinang.
12. `İ` kabi Unicode harflardan keyingi kichik/katta harf farqli iqtiboslar ajratilganda ilova yiqilmasin.
13. Bitta yuqori xavfli SMS-kod so‘rovi uchun «Yuqori xavf», tegishli toifa va himoya qadamlari chiqsin; ko‘p kuchsiz URL belgisi darajani o‘z-o‘zidan oshirmasin.
14. Oddiy QR va neytral yangilik xavf deb ko‘rsatilmasin. Zarar haqida foydalanuvchi xabari bilan firibgarning yolg‘on da’vosi alohida baholansin.
15. Eski backenddan `analysisVersion` yo‘q javob kelsa, MIL natijasi kiberxavf sifatida ko‘rsatilmasin; server yangilanishi kerakligi aytilsin.

Real AI chaqiruvlari kvota va xarajat sarflaydi. Avval ajratilgan test
backenddan foydalaning. Qurilmada sinov sanasi/modelini release qaydiga yozing.
