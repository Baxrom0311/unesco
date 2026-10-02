import base64
import binascii
import io
import ipaddress
import logging
import os
import re
from typing import List
from urllib.parse import urlparse

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

from article_fetch import safe_fetch_article
from guards import (RequestGuard, DailyBudget, IMAGE_MAX_BYTES, AUDIO_MAX_BYTES,
                    IMAGE_MAX_BASE64, AUDIO_MAX_BASE64)
from media import validate_audio, validate_image
from cyber import (CautionLevel, RiskLevel, Severity, ThreatCategory, Signal,
                   LlmAnalyzeResult, LlmMediaResult, AnalyzeResult, MediaAnalyzeResult,
                   finalize_risk, SYSTEM_PROMPT, ARTICLE_PROMPT, IMAGE_PROMPT, AUDIO_PROMPT)

load_dotenv()

logger = logging.getLogger("trust-signal")

API_KEY = os.environ.get("GEMINI_API_KEY")
if not API_KEY:
    raise RuntimeError("GEMINI_API_KEY environment variable is not set (see .env.example)")

client = genai.Client(api_key=API_KEY, http_options=types.HttpOptions(
    timeout=45000, retry_options=types.HttpRetryOptions(attempts=1)
))
budget = DailyBudget()
MODEL_NAME = os.environ.get("GEMINI_MODEL", "gemini-3.6-flash")


class AnalyzeRequest(BaseModel):
    content: str = Field(max_length=8000)


class AnalyzeImageRequest(BaseModel):
    imageBase64: str = Field(max_length=IMAGE_MAX_BASE64)
    mimeType: str = "image/jpeg"


class AnalyzeAudioRequest(BaseModel):
    audioBase64: str = Field(max_length=AUDIO_MAX_BASE64)
    mimeType: str = "audio/ogg"


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
    try:
        parsed = urlparse(url if "://" in url else "https://" + url)
        return (parsed.hostname or "").rstrip(".").encode("idna").decode("ascii").lower()
    except (ValueError, UnicodeError):
        return ""



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
            reasons.append(f"'.{tld}' domen zonasini tekshiring — zonaning o'zi zararli sayt isboti emas")
        try:
            ipaddress.ip_address(bare)
            reasons.append("domen nomi o'rniga IP-manzil ishlatilgan")
        except ValueError:
            pass
        if "@" in urlparse(url if "://" in url else "https://" + url).netloc:
            reasons.append("@ belgisi asl manzilni yashirishi mumkin")
        if "xn--" in bare:
            reasons.append("punycode — ko'zga o'xshash soxta harflar ishlatilgan bo'lishi mumkin")
        parts = bare.split(".")
        sld = parts[-2] if len(parts) >= 2 else bare
        for brand in sorted(BRAND_WORDS):
            if re.search(r"(?:^|[.-])" + re.escape(brand) + r"(?:$|[.-])", bare) and sld != brand:
                reasons.append(f"'{brand}' xizmatiga taqlid qilayotgan bo'lishi mumkin")
                break

        if reasons:
            out.append(
                Signal(
                    technique="Shubhali havola",
                    category=ThreatCategory.suspicious_link,
                    severity=Severity.suspicious,
                    quote=url,
                    explanation=("Bu havolada ehtiyot bo'ling: " + "; ".join(reasons) + ". Ochishdan oldin manzilni diqqat bilan tekshiring."),
                )
            )
    return out


def merge_link_signals(content: str, signals: List[Signal]) -> List[Signal]:
    existing = {s.quote for s in signals}
    extra = [s for s in link_signals(content) if s.quote not in existing]
    return list(signals) + extra


# ---------- Maqola/URL rejimi (xavfsiz yuklab olish) ----------

# Kontent faqat bitta havoladan iborat bo'lsa — maqola rejimi
SOLE_URL_RE = re.compile(r"(?:https?://|www\.)\S+", re.IGNORECASE)

# ---------- QR-kod dekodlash ----------

def decode_qr_texts(image_bytes: bytes) -> List[str] | None:
    try:
        from PIL import Image
        from pyzbar.pyzbar import decode as qr_decode, ZBarSymbol

        with Image.open(io.BytesIO(image_bytes)) as img:
            return list(dict.fromkeys(
                d.data.decode("utf-8", errors="replace")
                for d in qr_decode(img, symbols=[ZBarSymbol.QRCODE]) if d.data
            ))[:5]
    except Exception:
        logger.warning("QR decode unavailable", exc_info=True)
        return None


app = FastAPI(title="Trust Signal API", version="2.0.0")
app.add_middleware(RequestGuard)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[v.strip() for v in os.environ.get("CORS_ORIGINS", "").split(",") if v.strip()],
    allow_methods=["POST", "GET"],
    allow_headers=["Content-Type"],
)


@app.exception_handler(RequestValidationError)
async def invalid_request(request, exc):
    # Pydantic's default error includes the input (possibly private media).
    return JSONResponse(status_code=400, content={
        "detail": "So'rov formati yoki hajmi noto'g'ri. Matn 8000 belgidan, rasm 6MB dan, audio 12MB dan oshmasin."
    })


@app.get("/health")
def health():
    return {"status": "ok", "analysisVersion": "cyber-v1", "appVersion": "2.0.0"}


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
    article_links = [content]
    warnings = []
    if SOLE_URL_RE.fullmatch(content):
        try:
            article = safe_fetch_article(content)
            title, text = article["title"], article["text"]
            article_links.extend(article["links"])
            if article["truncated"]:
                warnings.append("Maqolaning dastlabki 6000 belgisi tahlil qilindi; qolgan qismini alohida yuboring.")
        except Exception:
            logger.warning("article fetch failed")
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

    budget.reserve()
    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=(
                "Quyidagi kontentda kiberfiribgarlik va zarar xavfini dalillar asosida tahlil qiling:\n\n"
                f'"""\n{prompt_content}\n"""'
            ),
            config=types.GenerateContentConfig(
                system_instruction=system_prompt,
                max_output_tokens=4096,
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
    result = AnalyzeResult(**parsed.model_dump(), extractedText=article_text, warnings=warnings)
    result.signals = merge_link_signals("\n".join(article_links + [prompt_content]), result.signals)
    finalize_risk(result)
    return result


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
    if len(image_bytes) > IMAGE_MAX_BYTES:
        raise HTTPException(413, "Rasm juda katta (6MB dan oshmasin).")
    validate_image(image_bytes, req.mimeType)

    budget.reserve()
    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=[
                types.Part.from_bytes(data=image_bytes, mime_type=req.mimeType),
                "Ushbu skrinshotni yuqoridagi qoidalar bo'yicha tahlil qiling "
                "va kiberfiribgarlik xavfi hamda himoya choralarini ko'rsating.",
            ],
            config=types.GenerateContentConfig(
                system_instruction=IMAGE_PROMPT,
                max_output_tokens=4096,
                response_mime_type="application/json",
                response_schema=LlmMediaResult,
            ),
        )
    except Exception:
        logger.exception("Gemini image request failed")
        raise HTTPException(
            status_code=502,
            detail="Tahlil xizmatida vaqtincha uzilish. Iltimos, birozdan so'ng qayta urinib ko'ring.",
        )

    parsed = response.parsed
    if parsed is None:
        raise HTTPException(
            status_code=502,
            detail="Rasmni tahlil qilib bo'lmadi. Iltimos, qayta urinib ko'ring",
        )
    result = MediaAnalyzeResult(**parsed.model_dump())
    result.signals = merge_link_signals(result.extractedText, result.signals)

    # QR-kod bo'lsa: ichidagi havolani ochiq ko'rsatamiz va tekshiramiz
    qr_texts = decode_qr_texts(image_bytes)
    if qr_texts is None:
        result.warnings.append("QR-kodni tekshirish vaqtincha mavjud emas. Rasm matni tahlil qilindi.")
    for qr_text in qr_texts or []:
        existing = {s.quote for s in result.signals}
        result.signals = list(result.signals) + [
            Signal(
                technique="QR-kod tarkibi",
                severity=Severity.info,
                quote=qr_text,
                explanation=(
                    "QR-kod tarkibi yuqorida ko‘rsatilgan. QR mavjudligining o‘zi xavf belgisi emas. "
                    "Kirish yoki to‘lovni tasdiqlashdan oldin manzil va talabni tekshiring."
                ),
            )
        ] + [s for s in link_signals(qr_text) if s.quote not in existing]
    finalize_risk(result)
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
    if len(audio_bytes) > AUDIO_MAX_BYTES:
        raise HTTPException(
            status_code=400,
            detail="Audio juda katta (12MB dan oshmasin). Qisqaroq xabar yuboring",
        )

    validate_audio(audio_bytes)

    budget.reserve()
    try:
        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=[
                types.Part.from_bytes(data=audio_bytes, mime_type=req.mimeType),
                "Ushbu ovozli xabarni yuqoridagi qoidalar bo'yicha tahlil qiling "
                "va kiberfiribgarlik xavfi hamda himoya choralarini ko'rsating.",
            ],
            config=types.GenerateContentConfig(
                system_instruction=AUDIO_PROMPT,
                max_output_tokens=4096,
                response_mime_type="application/json",
                response_schema=LlmMediaResult,
            ),
        )
    except Exception:
        logger.exception("Gemini audio request failed")
        raise HTTPException(
            status_code=502,
            detail="Tahlil xizmatida vaqtincha uzilish. Iltimos, birozdan so'ng qayta urinib ko'ring.",
        )

    parsed = response.parsed
    if parsed is None:
        raise HTTPException(
            status_code=502,
            detail="Audioni tahlil qilib bo'lmadi. Iltimos, qayta urinib ko'ring",
        )
    result = MediaAnalyzeResult(**parsed.model_dump())
    result.signals = merge_link_signals(result.extractedText, result.signals)
    finalize_risk(result)
    return result
