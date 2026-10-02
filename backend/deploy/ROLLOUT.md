# Joriy Trust Signal serveriga 2.0.0 chiqarish

Production health tekshirildi: `https://signal.boos.uz/health` javobi
`{"status":"ok"}`. `/openapi.json` hozir eski 0.1.0 MIL API bo‘lib, uch
endpoint mavjud. Oldingi deployment ma’lumotida service porti 8002 va HTTPS
reverse proxy qayd qilingan. Server hosti, SSH ruxsati, haqiqiy systemd unit
va venv katalogini tasdiqlash kerak; `/opt/trust-signal` deb taxmin qilib
fayllarni almashtirmang.

Deploy tartibi:

1. Serverga ruxsatli SSH orqali kiring. Eski systemd unit, Uvicorn command,
   reverse proxy port va `.env` joylashuvini o‘qing. Eski backend kodini va
   unit/nginx sozlamalarini backup qiling. `.env` ni release arxiviga yoki
   chatga qo‘shmang.
2. Release arxivini masofadagi yangi versiya papkasiga yuklang. Uni mavjud
   ishlab turgan papkaning ustiga to‘g‘ridan-to‘g‘ri yozmang.
3. Python talablarini venv ichiga o‘rnating. `ffmpeg` va `libzbar` borligini
   tekshiring. `GEMINI_API_KEY`, `GEMINI_MODEL` va eski maxfiy sozlamalarni
   saqlang. `BUDGET_DB_PATH` ni doimiy diskka belgilang.
4. Backendni 127.0.0.1:8002 da bitta worker bilan staging/start qiling.
   Nginx 8002 ga qarayotganini tekshiring; TLS va domen sozlamalariga
   tegmang. SQLite yozish huquqini service foydalanuvchisiga bering.
5. `GET /health` dan `analysisVersion=cyber-v1`, `appVersion=2.0.0`;
   `GET /openapi.json` dan version `2.0.0` kelganini tekshiring.
6. Keyin alohida Android 2.0.0 klientini HTTPS backendga ulab tekshiring.
   Backend versiyasi tasdiqlanmaguncha APKni foydalanuvchilarga tarqatmang.
7. Xato bo‘lsa, avvalgi release’ni tiklang, systemd’ni qayta yuklang va
   `/health` ni tekshiring. Eski usage DB faylini o‘chirmang.

Production API hali eski schema qaytaryapti. Deploy bajarilmadi: VPN’da
ko‘rinib turgan `inc` qurilmasi Windows workstation va unga SSH autentifikatsiya
muvaffaqiyatsiz tugadi. Uning production Linux server ekaniga dalil yo‘q.
Amaldagi Linux backend hostiga ruxsatli SSH kirishi kerak.
