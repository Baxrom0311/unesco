"""Validate decoded media before a paid model call."""
import io
import json
import math
import os
import subprocess
import tempfile

from fastapi import HTTPException
from PIL import Image

MAX_IMAGE_PIXELS = 16_000_000
MAX_AUDIO_SECONDS = 300


def validate_image(data: bytes, mime: str):
    try:
        with Image.open(io.BytesIO(data)) as img:
            width, height = img.size
            if width < 32 or height < 32 or width * height > MAX_IMAGE_PIXELS:
                raise HTTPException(400, "Rasm o'lchami 32px dan kichik yoki 16 megapikseldan katta.")
            if Image.MIME.get(img.format) != mime:
                raise HTTPException(400, "Rasm formati ko'rsatilgan turga mos emas.")
            img.verify()
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(400, "Rasm ma'lumotlari buzilgan yoki format noto'g'ri.") from None


def validate_audio(data: bytes):
    # A size limit does not bound the duration of compressed silence. Probe a
    # local temp file; no playlists, URL protocols, or arbitrary subprocess args.
    try:
        with tempfile.NamedTemporaryFile(suffix=".audio") as audio:
            audio.write(data)
            audio.flush()
            result = subprocess.run(
                [os.environ.get("FFPROBE_PATH", "ffprobe"), "-v", "error",
                 "-protocol_whitelist", "file", "-format_whitelist",
                 "ogg,mp3,mov,aac,wav,matroska,webm,amr,flac",
                 "-show_entries", "format=duration:stream=codec_type,duration",
                 "-of", "json", audio.name],
                capture_output=True, text=True, timeout=5, check=True,
            )
        metadata = json.loads(result.stdout)
        streams = metadata.get("streams", [])
        if not streams or any(s.get("codec_type") != "audio" for s in streams):
            raise ValueError("not audio-only")
        durations = [float(metadata.get("format", {}).get("duration", "nan"))]
        durations.extend(float(s["duration"]) for s in streams if "duration" in s)
        duration = max(durations)
        if not math.isfinite(duration) or duration <= 0:
            raise ValueError("unknown duration")
        if duration > MAX_AUDIO_SECONDS:
            raise HTTPException(400, "Audio 5 daqiqadan oshmasin.")
    except HTTPException:
        raise
    except FileNotFoundError:
        raise HTTPException(503, "Audio tekshiruvi vaqtincha mavjud emas.") from None
    except subprocess.CalledProcessError as exc:
        if exc.returncode < 0:
            raise HTTPException(503, "Audio tekshiruvi vaqtincha mavjud emas.") from None
        raise HTTPException(400, "Audio buzilgan yoki davomiyligini aniqlab bo'lmadi.") from None
    except (ValueError, KeyError, OSError, subprocess.SubprocessError):
        raise HTTPException(400, "Audio buzilgan yoki davomiyligini aniqlab bo'lmadi.") from None
