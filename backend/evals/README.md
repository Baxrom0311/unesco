# AI sifatini baholash

`cases.json` — qo‘lda kutilgan natija belgilangan 12 ta o‘zbekcha smoke
misol: neytral xabar/yangilik/xavfsizlik maslahati, OTP, yutuq, investitsiya,
masofaviy kirish, shantaj, soxta ish, yuz bergan zarar va prompt injection.
Bu vakillik qiluvchi benchmark emas. Modelning umumiy aniqligi yoki
xavfsizligini ushbu kichik dataset orqali e’lon qilmang.

```bash
cd backend
venv/bin/python evals/run.py --live --output eval-results.json
```

Bu buyruq haqiqiy Gemini’ga 12 tagacha so‘rov yuboradi va kunlik kvotadan
sarflaydi. Avtomatik CI testlari buni bajarmaydi. Natijalar lokal JSON’ga
yoziladi, gitga kiritilmaydi. Kutilgan riskLevel, xavf belgisi mavjudligi va iqtibos mosligi tekshiriladi;
izoh sifati, tavsiya mosligi va asossiz ayblovlar mutaxassis tomonidan alohida baholanadi.

Keyingi baholashda kamida bir nechta mustaqil annotator, turli mavzular va
yozuvlar (lotin/kirill), haqiqiy lekin hissiy yangiliklar hamda alohida test
to‘plami kerak. False positive/negative va har sinf bo‘yicha natijalarni
alohida hisoblang. Ushbu o‘zgarishda real AI eval ishga tushirilmagan.
