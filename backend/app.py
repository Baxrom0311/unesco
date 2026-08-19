import os
from enum import Enum
from typing import List

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

load_dotenv()

API_KEY = os.environ.get("GEMINI_API_KEY")
if not API_KEY:
    raise RuntimeError("GEMINI_API_KEY environment variable is not set (see .env.example)")

client = genai.Client(api_key=API_KEY)
MODEL_NAME = os.environ.get("GEMINI_MODEL", "gemini-3.6-flash")


class CautionLevel(str, Enum):
    belgi_topilmadi = "belgi_topilmadi"
    ozgina_belgi = "ozgina_belgi"
    kop_belgi = "kop_belgi"


class Signal(BaseModel):
    technique: str = Field(description="Aniqlangan usul nomi, qisqa, o'zbek tilida")
    quote: str = Field(
        description=(
            "Kiritilgan matndan olingan ANIQ, SO'ZMA-SO'Z parcha (3-15 so'z) — "
            "belgi aynan shu joyda ko'rinadi. Matnda harfma-harf mos kelishi shart."
        )
    )
    explanation: str = Field(description="Bu usul aynan shu parchada nega ko'rinayotgani")


class AnalyzeResult(BaseModel):
    cautionLevel: CautionLevel = Field(
        description=(
            "Matnda topilgan ISHONTIRISH/MANIPULYATSIYA usullari SONIGA qarab baho — "
            "bu faktning rost yoki yolg'onligi haqida hukm EMAS"
        )
    )
    summary: str = Field(
        description=(
            "Bir jumlali, ehtiyotkor xulosa. Hech qachon 'bu yolg'on' yoki 'bu rost' demang — "
            "faqat uslub haqida gapiring."
        )
    )
    signals: List[Signal] = Field(
        description=(
            "Matnda topilgan ishontirish/manipulyatsiya belgilari ro'yxati. "
            "Agar hech narsa topilmasa, bo'sh massiv qaytaring."
        )
    )
    tip: str = Field(
        description=(
            "Foydalanuvchi KEYINGI safar har qanday boshqa kontentni ko'rganda "
            "qo'llay oladigan umumiy ko'nikma — shu matnga xos emas."
        )
    )


class AnalyzeRequest(BaseModel):
    content: str


SYSTEM_PROMPT = """Siz Media va Axborot Savodxonligi (MIL) bo'yicha tanqidiy o'qish yordamchisisiz.

ENG MUHIM QOIDA: Siz FAKT SUDYASI EMASSIZ. Sizda voqealarni real vaqtda tekshirish imkoniyati yo'q, shuning uchun HECH QACHON "bu rost" yoki "bu yolg'on" deb hukm chiqarmang. Buning o'rniga faqat matnning USLUBI va TUZILISHIDA obyektiv kuzatiladigan ishontirish/manipulyatsiya belgilarini toping va tushuntiring.

Nima uchun bu muhim: shokka soluvchi lekin HAQIQIY yangilik ham hissiy, keskin tilda yozilgan bo'lishi mumkin. Agar siz "hissiy til = yolg'on" deb hisoblasangiz, haqiqiy voqealarga asossiz shubha uyg'otasiz. Shuning uchun signal topilishi "bu yolg'on" degani emas — bu "ehtiyot bilan, mustaqil manbalardan tekshiring" degani.

Qidirishingiz mumkin bo'lgan belgilar, IKKI TOIFADA (misol, to'liq ro'yxat emas):

A) Dezinformatsiya/manipulyatsiya belgilari:
- Kuchli hissiy/qo'rqinchli/g'azablantiruvchi til
- Manba ko'rsatilmagan yoki noaniq ("olimlar", "mutaxassislar" — kim aynan?)
- Soxta shoshilinchlik ("DARHOL", "HOZIROQ ulashing")
- Tekshirib bo'lmaydigan yoki haddan tashqari umumlashtirilgan da'volar
- "Ular sizdan yashiryapti" uslubidagi ishonch buzuvchi ritorika
- Kontekstdan uzilgan bo'lishi mumkin (masalan eski voqea yangi sifatida taqdim etilishi)

B) Fishing (firibgarlik/scam) belgilari — bularga ALOHIDA sinchkovlik bilan qarang, chunki bu odamga to'g'ridan-to'g'ri moddiy zarar keltirishi mumkin:
- Parol, PIN-kod, bir martalik kod (OTP), karta raqami yoki CVV so'ralishi
- "Hisobingiz bloklanadi/o'chiriladi" kabi soxta shoshilinchlik + havolaga o'tishga undash
- Bank, davlat idorasi, mashhur kompaniya nomidan yozilgan, lekin rasmiy bo'lmagan uslub yoki xatoliklar bor xabar
- Kutilmagan yutuq/sovg'a/pul haqida xabar ("siz g'olib bo'ldingiz", "omadli raqam")
- Shubhali, qisqartirilgan yoki asl domendan farq qiladigan havolalar
- Shaxsiy/moliyaviy ma'lumotni tasdiqlashni so'rovchi so'rovlar

Agar fishing belgisi topilsa, "technique" maydonida buni ANIQ ayting (masalan "Fishing xavfi — parol so'ralmoqda"), chunki bu boshqa umumiy dezinformatsiya belgilaridan farqli, alohida jiddiy xavf.

Qoidalar:
1. Agar hech qanday belgi topilmasa, buni ochiq ayting — bu ham foydali natija.
2. "summary" doim ehtiyotkor bo'lsin, hech qachon aniq true/false da'vo qilmang.
3. "tip" faqat shu matnga emas, umumiy, boshqa har qanday kontentga ham qo'llanadigan ko'nikma bo'lsin.
4. Javob har doim o'zbek tilida, sodda va tushunarli bo'lsin.
5. Muvozanatli bo'ling — har bir matnda majburan "muammo" qidirmang, agar toza bo'lsa shunday deng.
6. Har bir signal uchun "quote" maydoni KIRITILGAN MATNDAN SO'ZMA-SO'Z, harfma-harf nusxa bo'lishi SHART (qisqartirmang, o'zgartirmang, tarjima qilmang) — aks holda tizim uni matn ichida topa olmaydi."""

app = FastAPI(title="Trust Signal API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/analyze", response_model=AnalyzeResult)
def analyze(req: AnalyzeRequest):
    content = req.content.strip()
    if len(content) < 3:
        raise HTTPException(
            status_code=400,
            detail="Iltimos, tahlil qilish uchun matn kiriting (kamida 3 ta belgi)",
        )
    if len(content) > 8000:
        raise HTTPException(
            status_code=400,
            detail="Matn juda uzun. Iltimos, 8000 belgidan kamroq matn kiriting",
        )

    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=(
                "Quyidagi kontentning USLUBINI va TUZILISHINI tahlil qiling "
                f'(voqeaning haqiqatligi haqida hukm chiqarmang):\n\n"""\n{content}\n"""'
            ),
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                response_mime_type="application/json",
                response_schema=AnalyzeResult,
            ),
        )
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=f"Tahlil qilishda xatolik yuz berdi. Iltimos, qayta urinib ko'ring ({e})",
        )

    result = response.parsed
    if result is None:
        raise HTTPException(
            status_code=502,
            detail="Tahlil qilishda xatolik yuz berdi. Iltimos, qayta urinib ko'ring",
        )
    return result
