import base64
import binascii
import io
import ipaddress
import logging
import os
import re
import socket
from enum import Enum
from typing import List, Tuple
from urllib.parse import urlparse

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

load_dotenv()

logger = logging.getLogger("trust-signal")

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


class LlmAnalyzeResult(BaseModel):
    """Gemini to'ldiradigan qism — extractedText bunga kirmaydi, uni maqola
    rejimida server o'zi qo'shadi."""

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
    checkSteps: List[str] = Field(
        default_factory=list,
        description=(
            "AYNAN SHU kontentni mustaqil tekshirish uchun 2-4 ta aniq, bajarish "
            "oson qadam (SIFT metodikasi ruhida): manba ortida kim turganini "
            "aniqlash, boshqa ishonchli manbalardan qidirish, asl kontekstni "
            "topish. Har biri bitta qisqa buyruq gapdan iborat bo'lsin. "
            "Belgi topilmagan toza matnda bo'sh qoldirsa ham bo'ladi."
        ),
    )

class AnalyzeResult(LlmAnalyzeResult):
    # Maqola/URL rejimida sahifadan olingan matn (highlight mijozda ishlashi uchun)
    extractedText: str = ""


class AnalyzeRequest(BaseModel):
    content: str


class AnalyzeImageRequest(BaseModel):
    imageBase64: str
    mimeType: str = "image/jpeg"


class AnalyzeAudioRequest(BaseModel):
    audioBase64: str
    mimeType: str = "audio/ogg"


class MediaAnalyzeResult(BaseModel):
    """Rasm va audio tahlili uchun umumiy natija — Gemini extractedText'ni
    o'zi to'ldiradi (skrinshotdagi matn yoki audio transkripti)."""

    extractedText: str = Field(
        description=(
            "Skrinshotdagi BARCHA ko'rinadigan matn yoki audiodagi gaplarning "
            "so'zma-so'z transkripti, asl tilida. Signal 'quote'lari aynan shu "
            "matndan olinishi kerak."
        )
    )
    cautionLevel: CautionLevel
    summary: str
    signals: List[Signal]
    tip: str
    checkSteps: List[str] = Field(
        default_factory=list,
        description=(
            "AYNAN SHU kontentni mustaqil tekshirish uchun 2-4 ta aniq, oson "
            "qadam. Har biri bitta qisqa buyruq gap. Toza kontentda bo'sh "
            "qoldirsa bo'ladi."
        ),
    )


# ---------- Havola tekshiruvi (deterministik, LLM'siz) ----------

# https://, www. yoki sxemasiz lekin yo'lli havolalar (bit.ly/abc kabi)
URL_RE = re.compile(
    r"(?:https?://|www\.)[^\s<>\"']+"
    r"|\b[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)+/[^\s<>\"']+",
    re.IGNORECASE,
)

SHORTENER_DOMAINS = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "clck.ru", "is.gd",
    "rb.gy", "shorturl.at", "t.ly", "lnkd.in", "rebrand.ly", "s.id", "v.gd",
    "u.to", "kutt.it", "soo.gd", "qps.ru",
}

SUSPICIOUS_TLDS = {
    "top", "xyz", "icu", "click", "link", "vip", "gq", "cf", "tk", "ml",
    "work", "rest", "fit", "loan", "win", "bid", "cam", "quest",
}

# Taqlid qilinishi mumkin bo'lgan mashhur brend/xizmat nomlari
BRAND_WORDS = {
    "click", "payme", "paynet", "uzum", "humo", "uzcard", "paypal", "google",
    "telegram", "instagram", "facebook", "whatsapp", "apple", "samsung",
    "agrobank", "kapitalbank", "ipoteka", "milliy",
}


def _domain_of(url: str) -> str:
    d = re.sub(r"^https?://", "", url, flags=re.IGNORECASE)
    d = d.split("/")[0].split("?")[0].split("#")[0].split(":")[0]
    return d.lower()


def link_signals(content: str) -> List[Signal]:
    """Matndagi URL'larni LLM'siz, deterministik qoidalar bilan tekshiradi.
    Topilmalar odatdagi signals ro'yxatiga qo'shiladi — mijoz uchun alohida
    format kerak emas, highlight ham avtomatik ishlaydi (quote = URL)."""
    out: List[Signal] = []
    seen_domains: set = set()
    for m in URL_RE.finditer(content):
        url = m.group(0).rstrip(".,);!?'\"”»")
        domain = _domain_of(url)
        if not domain or domain in seen_domains:
            continue
        seen_domains.add(domain)

        reasons: List[str] = []
        bare = domain[4:] if domain.startswith("www.") else domain
        if bare in SHORTENER_DOMAINS:
            reasons.append("qisqartirilgan havola — asl manzil yashiringan")
        tld = bare.rsplit(".", 1)[-1] if "." in bare else ""
        if tld in SUSPICIOUS_TLDS:
            reasons.append(f"'.{tld}' zonasi firibgarlik sahifalarida tez-tez uchraydi")
        if re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", bare):
            reasons.append("domen nomi o'rniga IP-manzil ishlatilgan")
        if "xn--" in bare:
            reasons.append("punycode — ko'zga o'xshash soxta harflar ishlatilgan bo'lishi mumkin")
        parts = bare.split(".")
        sld = parts[-2] if len(parts) >= 2 else bare
        for brand in BRAND_WORDS:
            if brand in bare and sld != brand:
                reasons.append(f"'{brand}' xizmatiga taqlid qilayotgan bo'lishi mumkin")
                break

        if reasons:
            out.append(
                Signal(
                    technique="Shubhali havola",
                    quote=url,
                    explanation=("Bu havolada ehtiyot bo'ling: " + "; ".join(reasons) + ". Ochishdan oldin manzilni diqqat bilan tekshiring."),
                )
            )
    return out


def merge_link_signals(content: str, signals: List[Signal]) -> List[Signal]:
    existing = " ".join(s.quote for s in signals)
    extra = [s for s in link_signals(content) if s.quote not in existing]
    return list(signals) + extra


def ensure_caution_consistency(result) -> None:
    """Server tomonidan qo'shilgan deterministik signallar (havola/QR) bo'lsa,
    cautionLevel "belgi_topilmadi" bo'lib qolmasin — mijoz yashil belgi bilan
    signallarni birga ko'rsatib qo'ymasligi uchun."""
    if result.signals and result.cautionLevel == CautionLevel.belgi_topilmadi:
        result.cautionLevel = CautionLevel.ozgina_belgi


# ---------- Maqola/URL rejimi (xavfsiz yuklab olish) ----------

# Kontent faqat bitta havoladan iborat bo'lsa — maqola rejimi
SOLE_URL_RE = re.compile(r"(?:https?://|www\.)\S+", re.IGNORECASE)

FETCH_MAX_BYTES = 2 * 1024 * 1024
FETCH_MAX_REDIRECTS = 3
# Sekin (tarpit) serverlar sync ishchi oqimini band qilib qo'ymasligi uchun
# butun fetch jarayoniga yagona umumiy muddat
FETCH_TOTAL_DEADLINE_S = 12.0


def _public_ip_for(host: str) -> str:
    """SSRF himoyasi: hostni BIR MARTA yechib, ommaviy IP qaytaradi.
    Ulanish keyin aynan shu IP'ga qilinadi — DNS-rebinding (tekshiruvdan
    keyin javobni almashtirish) imkonsiz bo'ladi."""
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror:
        raise ValueError("host resolve failed")
    ips = []
    for info in infos:
        ip = ipaddress.ip_address(info[4][0])
        if (
            ip.is_private
            or ip.is_loopback
            or ip.is_link_local
            or ip.is_reserved
            or ip.is_multicast
            or ip.is_unspecified
        ):
            raise ValueError("private address blocked")
        ips.append(str(ip))
    if not ips:
        raise ValueError("no address")
    # IPv4 afzal (barqarorlik uchun)
    ips.sort(key=lambda s: ":" in s)
    return ips[0]


def safe_fetch_article(url: str) -> Tuple[str, str]:
    """Sahifani xavfsiz yuklab, (sarlavha, matn) qaytaradi.

    Himoyalar: sxema/port cheklovi (faqat http/https, 80/443), userinfo
    taqiqi, har bir redirect bosqichida IP qayta tekshiruvi, IP-pinning
    (rebinding'ga qarshi), 2MB hajm va 12s umumiy muddat chegarasi."""
    import time as _time
    from urllib.parse import urljoin

    import certifi
    import urllib3
    from bs4 import BeautifulSoup

    if url.lower().startswith("www."):
        url = "https://" + url

    deadline = _time.monotonic() + FETCH_TOTAL_DEADLINE_S
    current = url
    body = None
    encoding = "utf-8"

    for _ in range(FETCH_MAX_REDIRECTS + 1):
        if _time.monotonic() > deadline:
            raise ValueError("fetch deadline exceeded")
        parsed = urlparse(current)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError("bad scheme")
        if parsed.username or parsed.password:
            raise ValueError("userinfo not allowed")
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        if port not in (80, 443):
            raise ValueError("port not allowed")

        host = parsed.hostname
        pinned_ip = _public_ip_for(host)
        timeout = urllib3.Timeout(connect=5.0, read=6.0)
        if parsed.scheme == "https":
            pool = urllib3.HTTPSConnectionPool(
                pinned_ip,
                port=port,
                server_hostname=host,
                assert_hostname=host,
                cert_reqs="CERT_REQUIRED",
                ca_certs=certifi.where(),
                timeout=timeout,
                retries=False,
            )
        else:
            pool = urllib3.HTTPConnectionPool(pinned_ip, port=port, timeout=timeout, retries=False)

        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        try:
            resp = pool.urlopen(
                "GET",
                path,
                headers={
                    "Host": host,
                    "User-Agent": "Mozilla/5.0 (compatible; TrustSignal-MIL/1.0)",
                    "Accept": "text/html,*/*",
                },
                redirect=False,
                preload_content=False,
            )
            try:
                if resp.status in (301, 302, 303, 307, 308) and resp.headers.get("Location"):
                    current = urljoin(current, resp.headers["Location"])
                    continue
                if resp.status != 200:
                    raise ValueError(f"fetch failed ({resp.status})")
                chunks, total = [], 0
                while total < FETCH_MAX_BYTES:
                    if _time.monotonic() > deadline:
                        break
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    chunks.append(chunk)
                    total += len(chunk)
                body = b"".join(chunks)
                ctype = resp.headers.get("Content-Type", "")
                m = re.search(r"charset=([\w-]+)", ctype)
                if m:
                    encoding = m.group(1)
                break
            finally:
                resp.release_conn()
        finally:
            pool.close()
    else:
        raise ValueError("too many redirects")

    if body is None:
        raise ValueError("no response body")
    try:
        html = body.decode(encoding, errors="ignore")
    except LookupError:
        html = body.decode("utf-8", errors="ignore")

    soup = BeautifulSoup(html, "html.parser")
    title = (soup.title.get_text(strip=True) if soup.title else "")[:300]
    for tag in soup(["script", "style", "noscript", "nav", "header", "footer", "aside", "form"]):
        tag.decompose()
    text = re.sub(r"\s+", " ", soup.get_text(" ", strip=True))[:6000]
    if len(text) < 100:
        raise ValueError("page has too little text")
    return title, text


# ---------- QR-kod dekodlash ----------

def decode_qr_texts(image_bytes: bytes) -> List[str]:
    try:
        from PIL import Image
        from pyzbar.pyzbar import decode as qr_decode

        img = Image.open(io.BytesIO(image_bytes))
        return [
            d.data.decode("utf-8", errors="ignore")
            for d in qr_decode(img)
            if d.data
        ][:5]
    except Exception:
        logger.warning("QR decode skipped", exc_info=True)
        return []


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

C) Guruh/suhbat firibgarligi belgilari — agar kiritilgan kontent BIR NECHTA XABARDAN iborat suhbat yoki guruh yozishmalari bo'lsa (masalan Telegram guruhidan 10-15 xabar), suhbat DARAJASIDAGI sxemalarni ham qidiring:
- "Kafolatlangan daromad", "kuniga X% foyda" kabi tez boyish va'dalari
- Oldindan to'lov, "komissiya", "aktivatsiya to'lovi" so'rash
- "Admin bilan shaxsiy chatga yozing" deb ochiq muhokamadan uzoqlashtirish
- Soxta daromad skrinshotlari yoki "mana men oldim" tipidagi guvohliklar zanjiri
- Yangi a'zolarni jalb qilganlarga mukofot va'dasi (piramida belgisi)
- "Joylar cheklangan", "faqat bugungi ro'yxat" kabi sun'iy taqchillik
- Bir xil uslubdagi bir nechta "mamnun mijoz" xabarlari (soxta faollik)
Bunday belgi topilsa "technique" maydonida guruh sxemasi ekanini ayting (masalan "Firibgar guruh belgisi — kafolatlangan daromad va'dasi").

D) Statistika bilan manipulyatsiya belgilari:
- Asossiz foizlar va raqamlar ("80% odamlar..." — qaysi tadqiqot, qancha odam so'ralgan?)
- Qulay davr yoki namuna tanlab olish (cherry-picking)
- Korrelyatsiyani sabab-oqibat deb taqdim etish ("X dan keyin Y bo'ldi, demak X sabab")
- Baza ko'rsatilmagan o'sish ("2 barobar oshdi" — nimadan nimaga?)
- Mutlaq va nisbiy sonlarni chalkashtirish

E) Klassik propaganda usullari — topilsa usulning NOMINI ayting:
- Yorliq yopishtirish (label): raqibga tahqirlovchi nom berish, mohiyatni muhokama qilmaslik
- "Hammada shunday" (bandwagon): ko'pchilikka ergashishga undash
- Obro'ga suyanish: mashhur shaxs/unvon nomidan, lekin dalilsiz
- Oddiy xalq nomidan gapirish: "biz oddiy odamlar bilamiz..."
- Faktlarni tanlab berish (card stacking): faqat bir tomonni ko'rsatish
- Yaltiroq umumiy so'zlar: "buyuk", "milliy", "haqiqiy" — mazmunsiz his uyg'otish

F) Yashirin reklama belgilari:
- "Shaxsiy tavsiya" niqobidagi sotuv matni (aloqa/buyurtma havolasi bilan tugaydi)
- E'lon qilinmagan sponsorlik yoki manfaat to'qnashuvi ehtimoli
- "Do'stim ishlatib ko'rdi" tipidagi tekshirib bo'lmaydigan guvohlik + mahsulot havolasi

G) Zanjir-xabar belgilari:
- "Buni 10 kishiga tarqating", "o'chirishdan oldin ulashing" kabi majburiy tarqatish talabi
- "Buni sizdan yashirishyapti, tezda saqlab qoling"
- Tarqatmaganlarga tahdid yoki tarqatganlarga mukofot va'dasi

Qoidalar:
1. Agar hech qanday belgi topilmasa, buni ochiq ayting — bu ham foydali natija.
2. "summary" doim ehtiyotkor bo'lsin, hech qachon aniq true/false da'vo qilmang.
3. "tip" faqat shu matnga emas, umumiy, boshqa har qanday kontentga ham qo'llanadigan ko'nikma bo'lsin.
4. Javob har doim o'zbek tilida, sodda va tushunarli bo'lsin.
5. Muvozanatli bo'ling — har bir matnda majburan "muammo" qidirmang, agar toza bo'lsa shunday deng.
6. Har bir signal uchun "quote" maydoni KIRITILGAN MATNDAN SO'ZMA-SO'Z, harfma-harf nusxa bo'lishi SHART (qisqartirmang, o'zgartirmang, tarjima qilmang) — aks holda tizim uni matn ichida topa olmaydi.
7. "checkSteps" — foydalanuvchi AYNAN SHU kontentni o'zi mustaqil tekshirishi uchun 2-4 ta aniq qadam yozing (masalan: "Bu yangilikni kamida ikkita mustaqil manbadan qidiring", "Rasmiy saytga o'zingiz kirib tekshiring"). Belgi topilmagan toza matnda bo'sh qoldirish mumkin."""


ARTICLE_PROMPT = SYSTEM_PROMPT + """

QO'SHIMCHA — MAQOLA/SAHIFA TAHLILI:
Sizga veb-sahifadan olingan SARLAVHA va MATN beriladi. Yuqoridagi barcha toifalarga qo'shimcha:
- SARLAVHA-MATN NOMUVOFIQLIGI (clickbait): sarlavha va'da qilgan narsani matn bermasa yoki bo'rttirsa — bu alohida muhim belgi, uni birinchi tekshiring
- Sarlavhada savol/shov-shuv, matnda esa "aniq emas", "taxmin qilinmoqda" bo'lsa
- Muallif va sana ko'rsatilganmi, manbalar keltirilganmi
- "Shok", "aql bovar qilmaydi" kabi bosishga undovchi so'zlar"""

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

    # Kontent faqat bitta havola bo'lsa — sahifani yuklab, maqola rejimida tahlil qilamiz
    article_text = ""
    prompt_content = content
    system_prompt = SYSTEM_PROMPT
    if SOLE_URL_RE.fullmatch(content):
        try:
            title, text = safe_fetch_article(content)
        except Exception:
            logger.warning("article fetch failed: %s", content, exc_info=True)
            raise HTTPException(
                status_code=400,
                detail=(
                    "Havoladagi sahifani ochib bo'lmadi. Maqola matnini "
                    "nusxalab, o'zini yuboring."
                ),
            )
        article_text = (f"SARLAVHA: {title}\n\n" if title else "") + text
        prompt_content = article_text
        system_prompt = ARTICLE_PROMPT

    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=(
                "Quyidagi kontentning USLUBINI va TUZILISHINI tahlil qiling "
                f'(voqeaning haqiqatligi haqida hukm chiqarmang):\n\n"""\n{prompt_content}\n"""'
            ),
            config=types.GenerateContentConfig(
                system_instruction=system_prompt,
                response_mime_type="application/json",
                response_schema=LlmAnalyzeResult,
            ),
        )
    except Exception:
        # Texnik tafsilot faqat server logiga — foydalanuvchiga xom inglizcha
        # exception matni ko'rsatilmaydi
        logger.exception("Gemini request failed")
        raise HTTPException(
            status_code=502,
            detail="Tahlil xizmatida vaqtincha uzilish. Iltimos, birozdan so'ng qayta urinib ko'ring.",
        )

    parsed = response.parsed
    if parsed is None:
        raise HTTPException(
            status_code=502,
            detail="Tahlil qilishda xatolik yuz berdi. Iltimos, qayta urinib ko'ring",
        )
    result = AnalyzeResult(**parsed.model_dump(), extractedText=article_text)
    # Deterministik havola tekshiruvi natijalarini qo'shamiz (maqola rejimida
    # foydalanuvchi kiritgan URL emas, sahifa matnidagi havolalar tekshiriladi)
    result.signals = merge_link_signals(prompt_content, result.signals)
    ensure_caution_consistency(result)
    return result


IMAGE_PROMPT = SYSTEM_PROMPT + """

QO'SHIMCHA — SKRINSHOT TAHLILI:
Sizga matn o'rniga SKRINSHOT (rasm) beriladi. Vazifangiz:
1. Rasmda ko'ringan BARCHA matnni "extractedText" maydoniga asl tilida, so'zma-so'z ko'chiring (tugma yozuvlari, sarlavhalar, xabar matni — hammasi).
2. Yuqoridagi A/B/C toifadagi belgilarni extractedText bo'yicha qidiring — har bir signal "quote"i extractedText ichidan so'zma-so'z olinishi SHART.
3. Rasmning VIZUAL tomonini ham baholang va topilmalarni signal sifatida qo'shing:
- Mashhur xizmat (bank, to'lov tizimi, ijtimoiy tarmoq) interfeysiga taqlid, lekin sifatsiz yoki noto'g'ri detallar (logo buzilgan, shrift g'alati, rang farqi)
- Soxta "yutuq" sahifalari, aylanuvchi g'ildiraklar, sovg'a qutilari
- Brauzer manzil satrida rasmiy domenga o'xshamagan manzil
- Sun'iy vaqt bosimi elementlari (taymer, "oxirgi 2 ta joy")
- Shaxsiy/karta ma'lumotlarini so'rovchi formalar
- GRAFIK/DIAGRAMMA manipulyatsiyasi: 0 dan boshlanmagan kesilgan o'q, notekis shkala, nomlanmagan o'qlar, nomutanosib ustunlar — grafik taassurotni bo'rttirsa
- SOXTA PROFIL belgilari (profil skrinshoti bo'lsa): umumiy/stok avatar, obunachi soni va faollik nomutanosibligi, yaqinda ochilgan sahifa, bir xil qolipdagi postlar
- SUN'IY YARATILGANLIK (AI) artefaktlari: barmoq/quloq buzilishlari, rasm ichidagi o'qib bo'lmas matn, notabiiy silliq tekstura — bularni FAQAT ehtimol sifatida ayting ("AI yaratgan bo'lishi MUMKIN belgilari bor"), hech qachon qat'iy da'vo qilmang
Vizual signal uchun "quote" maydoniga o'sha element yonidagi ko'rinadigan matnni yozing (agar matn bo'lmasa, elementni qisqa tasvirlab yozing).
4. Rasmda hech qanday belgi bo'lmasa — buni ochiq ayting, majburan muammo qidirmang."""

AUDIO_PROMPT = SYSTEM_PROMPT + """

QO'SHIMCHA — OVOZLI XABAR TAHLILI:
Sizga matn o'rniga OVOZLI XABAR (audio) beriladi. Vazifangiz:
1. Avval audiodagi gaplarni "extractedText" maydoniga asl tilida so'zma-so'z transkript qiling.
2. Yuqoridagi A-G toifadagi belgilarni transkript bo'yicha qidiring — har bir signal "quote"i transkriptdan so'zma-so'z olinishi SHART.
3. Ovozli firibgarlikka XOS belgilarga alohida e'tibor bering:
- O'zini bank, huquq-tartibot yoki davlat idorasi xodimi deb tanishtirish
- SMS-kod, parol, karta raqamini aytishni so'rash
- "Hisobingizdan shubhali amaliyot", "kartangiz bloklanadi" deb vahima yaratish
- Hoziroq, telefonni qo'ymay turib pul o'tkazish yoki kod aytishni talab qilish
- "Hech kimga aytmang" deb sir saqlashga undash
4. Ovoz notabiiy sintetik tuyulsa, buni ehtimol sifatida ayting ("sun'iy ovoz bo'lishi mumkin"), qat'iy da'vo qilmang.
5. Toza bo'lsa shuni ochiq ayting."""


@app.post("/api/analyze-image", response_model=MediaAnalyzeResult)
def analyze_image(req: AnalyzeImageRequest):
    if req.mimeType not in ("image/jpeg", "image/png", "image/webp"):
        raise HTTPException(
            status_code=400,
            detail="Faqat JPEG, PNG yoki WebP rasm qabul qilinadi",
        )
    try:
        image_bytes = base64.b64decode(req.imageBase64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="Rasm ma'lumotlari buzilgan")
    # Hajmni bayt emas, PIKSEL bo'yicha tekshiramiz: 1-bitli ixcham QR PNG
    # 1KB dan kichik bo'lishi mumkin, lekin mutlaqo yaroqli rasm
    try:
        from PIL import Image

        with Image.open(io.BytesIO(image_bytes)) as _img:
            _w, _h = _img.size
    except Exception:
        raise HTTPException(
            status_code=400,
            detail="Rasm ma'lumotlari buzilgan yoki format noto'g'ri",
        )
    if _w < 32 or _h < 32:
        raise HTTPException(status_code=400, detail="Rasm juda kichik yoki bo'sh")
    if len(image_bytes) > 6 * 1024 * 1024:
        raise HTTPException(
            status_code=400,
            detail="Rasm juda katta (6MB dan oshmasin). Ilova rasmni avtomatik kichraytiradi — yangiroq versiyani o'rnating",
        )

    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=[
                types.Part.from_bytes(data=image_bytes, mime_type=req.mimeType),
                "Ushbu skrinshotni yuqoridagi qoidalar bo'yicha tahlil qiling "
                "(voqeaning haqiqatligi haqida hukm chiqarmang).",
            ],
            config=types.GenerateContentConfig(
                system_instruction=IMAGE_PROMPT,
                response_mime_type="application/json",
                response_schema=MediaAnalyzeResult,
            ),
        )
    except Exception:
        logger.exception("Gemini image request failed")
        raise HTTPException(
            status_code=502,
            detail="Tahlil xizmatida vaqtincha uzilish. Iltimos, birozdan so'ng qayta urinib ko'ring.",
        )

    result = response.parsed
    if result is None:
        raise HTTPException(
            status_code=502,
            detail="Rasmni tahlil qilib bo'lmadi. Iltimos, qayta urinib ko'ring",
        )
    result.signals = merge_link_signals(result.extractedText, result.signals)

    # QR-kod bo'lsa: ichidagi havolani ochiq ko'rsatamiz va tekshiramiz
    for qr_text in decode_qr_texts(image_bytes):
        existing = " ".join(s.quote for s in result.signals)
        result.signals = list(result.signals) + [
            Signal(
                technique="QR-kod aniqlandi",
                quote=qr_text[:200],
                explanation=(
                    "Skrinshotdagi QR-kod shu manzilga olib boradi. QR-kodni "
                    "skanerlashdan oldin manzilni diqqat bilan tekshiring — "
                    "ko'z bilan ko'rib bo'lmagani uchun firibgarlar undan tez-tez "
                    "foydalanadi."
                ),
            )
        ] + [s for s in link_signals(qr_text) if s.quote not in existing]
    ensure_caution_consistency(result)
    return result


@app.post("/api/analyze-audio", response_model=MediaAnalyzeResult)
def analyze_audio(req: AnalyzeAudioRequest):
    allowed = {
        "audio/ogg", "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac",
        "audio/wav", "audio/webm", "audio/opus", "audio/amr", "audio/3gpp",
        "audio/flac",
    }
    if req.mimeType not in allowed:
        raise HTTPException(
            status_code=400,
            detail="Bu audio format qo'llab-quvvatlanmaydi",
        )
    try:
        audio_bytes = base64.b64decode(req.audioBase64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="Audio ma'lumotlari buzilgan")
    if len(audio_bytes) < 1000:
        raise HTTPException(status_code=400, detail="Audio juda qisqa yoki bo'sh")
    if len(audio_bytes) > 12 * 1024 * 1024:
        raise HTTPException(
            status_code=400,
            detail="Audio juda katta (12MB dan oshmasin). Qisqaroq xabar yuboring",
        )

    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=[
                types.Part.from_bytes(data=audio_bytes, mime_type=req.mimeType),
                "Ushbu ovozli xabarni yuqoridagi qoidalar bo'yicha tahlil qiling "
                "(voqeaning haqiqatligi haqida hukm chiqarmang).",
            ],
            config=types.GenerateContentConfig(
                system_instruction=AUDIO_PROMPT,
                response_mime_type="application/json",
                response_schema=MediaAnalyzeResult,
            ),
        )
    except Exception:
        logger.exception("Gemini audio request failed")
        raise HTTPException(
            status_code=502,
            detail="Tahlil xizmatida vaqtincha uzilish. Iltimos, birozdan so'ng qayta urinib ko'ring.",
        )

    result = response.parsed
    if result is None:
        raise HTTPException(
            status_code=502,
            detail="Audioni tahlil qilib bo'lmadi. Iltimos, qayta urinib ko'ring",
        )
    result.signals = merge_link_signals(result.extractedText, result.signals)
    ensure_caution_consistency(result)
    return result
