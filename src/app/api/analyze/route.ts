import { NextRequest, NextResponse } from "next/server";
import { generateObject } from "ai";
import { z } from "zod";

export const maxDuration = 30;

const resultSchema = z.object({
  cautionLevel: z
    .enum(["belgi_topilmadi", "ozgina_belgi", "kop_belgi"])
    .describe(
      "Matnda topilgan ISHONTIRISH/MANIPULYATSIYA usullari SONIGA qarab baho — bu faktning rost yoki yolg'onligi haqida hukm EMAS"
    ),
  summary: z
    .string()
    .describe(
      "Bir jumlali, ehtiyotkor xulosa. Hech qachon 'bu yolg'on' yoki 'bu rost' demang — faqat uslub haqida gapiring, masalan: 'Bu matnda kuchli hissiy til va manba yo'q, shuning uchun ehtiyot bilan o'qing'"
    ),
  signals: z
    .array(
      z.object({
        technique: z.string().describe("Aniqlangan usul nomi, qisqa, o'zbek tilida"),
        quote: z
          .string()
          .describe(
            "Kiritilgan matndan olingan ANIQ, SO'ZMA-SO'Z parcha (3-15 so'z) — belgi aynan shu joyda ko'rinadi. Matnda harfma-harf mos kelishi shart, chunki bu ekranda belgilab ko'rsatiladi"
          ),
        explanation: z
          .string()
          .describe("Bu usul aynan shu parchada nega ko'rinayotgani"),
      })
    )
    .describe(
      "Matnda topilgan ishontirish/manipulyatsiya belgilari ro'yxati. Agar hech narsa topilmasa, bo'sh massiv qaytaring — 'hech narsa yo'q' deb ijobiy signal berish ham foydali"
    ),
  tip: z
    .string()
    .describe(
      "Foydalanuvchi KEYINGI safar har qanday boshqa kontentni ko'rganda qo'llay oladigan umumiy ko'nikma — shu matnga xos emas"
    ),
});

const SYSTEM_PROMPT = `Siz Media va Axborot Savodxonligi (MIL) bo'yicha tanqidiy o'qish yordamchisisiz.

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
6. Har bir signal uchun "quote" maydoni KIRITILGAN MATNDAN SO'ZMA-SO'Z, harfma-harf nusxa bo'lishi SHART (qisqartirmang, o'zgartirmang, tarjima qilmang) — aks holda tizim uni matn ichida topa olmaydi.`;

export async function POST(req: NextRequest) {
  let content: unknown;
  try {
    const body = await req.json();
    content = body?.content;
  } catch {
    return NextResponse.json({ error: "Noto'g'ri so'rov formati" }, { status: 400 });
  }

  if (typeof content !== "string" || content.trim().length < 3) {
    return NextResponse.json(
      { error: "Iltimos, tahlil qilish uchun matn kiriting (kamida 3 ta belgi)" },
      { status: 400 }
    );
  }

  if (content.length > 8000) {
    return NextResponse.json(
      { error: "Matn juda uzun. Iltimos, 8000 belgidan kamroq matn kiriting" },
      { status: 400 }
    );
  }

  try {
    const { object } = await generateObject({
      model: "openai/gpt-4o-mini",
      schema: resultSchema,
      system: SYSTEM_PROMPT,
      prompt: `Quyidagi kontentning USLUBINI va TUZILISHINI tahlil qiling (voqeaning haqiqatligi haqida hukm chiqarmang):\n\n"""\n${content}\n"""`,
    });

    return NextResponse.json(object);
  } catch (err) {
    console.error("analyze error", err);
    return NextResponse.json(
      { error: "Tahlil qilishda xatolik yuz berdi. Iltimos, qayta urinib ko'ring" },
      { status: 502 }
    );
  }
}
