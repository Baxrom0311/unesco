"""Bound resource use before JSON parsing and before paid model calls.

Rate/concurrency limits are per process: deploy one worker. The daily attempt
counter is durable and transactionally shared by processes using the same DB.
"""
import asyncio
import hashlib
import logging
import os
import sqlite3
import threading
import time
from datetime import datetime, timezone
from contextlib import closing
from pathlib import Path

from fastapi import HTTPException
from starlette.responses import JSONResponse

IMAGE_MAX_BYTES = 6 * 1024 * 1024
AUDIO_MAX_BYTES = 12 * 1024 * 1024
IMAGE_MAX_BASE64 = 4 * ((IMAGE_MAX_BYTES + 2) // 3)
AUDIO_MAX_BASE64 = 4 * ((AUDIO_MAX_BYTES + 2) // 3)
BODY_LIMITS = {
    "/api/analyze": 64 * 1024,
    "/api/analyze-image": IMAGE_MAX_BASE64 + 4096,
    "/api/analyze-audio": AUDIO_MAX_BASE64 + 4096,
}


def positive_setting(name, default):
    value = int(os.environ.get(name, default))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


class RequestGuard:
    def __init__(self, app, *, per_minute=None, concurrency=None, body_timeout=20):
        self.app = app
        self.per_minute = per_minute or positive_setting("RATE_LIMIT_PER_MINUTE", 10)
        self.concurrency = concurrency or positive_setting("MAX_CONCURRENT_ANALYSES", 4)
        self.body_timeout = body_timeout
        self.clients = {}
        self.active = 0
        self.lock = threading.Lock()

    def admit(self, peer):
        minute = int(time.monotonic() // 60)
        # Hash IPs so the in-memory limiter does not retain raw addresses.
        key = hashlib.sha256(peer.encode()).digest()
        with self.lock:
            self.clients = {k: v for k, v in self.clients.items() if v[0] == minute}
            _, count = self.clients.get(key, (minute, 0))
            if count >= self.per_minute or (key not in self.clients and len(self.clients) >= 10000):
                return 429
            self.clients[key] = (minute, count + 1)
            if self.active >= self.concurrency:
                return 503
            self.active += 1
        return None

    async def __call__(self, scope, receive, send):
        path = scope.get("path", "").rstrip("/")
        if scope["type"] != "http" or scope.get("method") != "POST" or path not in BODY_LIMITS:
            return await self.app(scope, receive, send)

        async def reject(status, detail):
            headers = {"Retry-After": "60"} if status in (429, 503) else None
            await JSONResponse({"detail": detail}, status_code=status, headers=headers)(scope, receive, send)

        # Only the ASGI server resolves trusted proxy headers. Never trust a
        # caller-supplied X-Forwarded-For directly here.
        peer = (scope.get("client") or ("unknown", 0))[0]
        status = self.admit(peer)
        if status:
            return await reject(status, "So'rovlar chegarasiga yetildi. Birozdan so'ng qayta urining.")
        try:
            headers = dict(scope.get("headers", []))
            limit = BODY_LIMITS[path]
            if headers.get(b"content-encoding", b"identity").lower() != b"identity":
                return await reject(415, "Siqilgan so'rov qo'llab-quvvatlanmaydi.")
            try:
                length = int(headers.get(b"content-length", b"0"))
            except ValueError:
                return await reject(400, "So'rov hajmi noto'g'ri.")
            if length < 0:
                return await reject(400, "So'rov hajmi noto'g'ri.")
            if length > limit:
                return await reject(413, "Yuborilgan ma'lumot juda katta.")
            body = bytearray()
            try:
                async with asyncio.timeout(self.body_timeout):
                    while True:
                        message = await receive()
                        if message["type"] == "http.disconnect":
                            return
                        chunk = message.get("body", b"")
                        if len(body) + len(chunk) > limit:
                            return await reject(413, "Yuborilgan ma'lumot juda katta.")
                        body.extend(chunk)
                        if not message.get("more_body", False):
                            break
            except TimeoutError:
                return await reject(408, "Yuborish muddati tugadi. Qayta urining.")
            delivered = False

            async def bounded_receive():
                nonlocal delivered
                if delivered:
                    return await receive()
                delivered = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}

            await self.app(scope, bounded_receive, send)
        finally:
            with self.lock:
                self.active -= 1


class DailyBudget:
    """Count attempts, including failed model calls; never retry them for free."""
    def __init__(self, path=None, limit=None):
        self.path = Path(path or os.environ.get(
            "BUDGET_DB_PATH", str(Path(__file__).parent / "data" / "usage.sqlite3")
        ))
        self.limit = limit or positive_setting("DAILY_MODEL_REQUEST_LIMIT", 500)

    def reserve(self):
        day = datetime.now(timezone.utc).date().isoformat()
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            with closing(sqlite3.connect(self.path, timeout=3)) as db, db:
                db.execute("CREATE TABLE IF NOT EXISTS usage (day TEXT PRIMARY KEY, requests INTEGER NOT NULL)")
                db.execute("BEGIN IMMEDIATE")
                db.execute("DELETE FROM usage WHERE day < ?", (day,))
                db.execute("INSERT OR IGNORE INTO usage VALUES (?, 0)", (day,))
                count = db.execute("SELECT requests FROM usage WHERE day = ?", (day,)).fetchone()[0]
                if count >= self.limit:
                    raise HTTPException(429, "Bugungi tahlil limiti tugadi. Ertaga qayta urining.")
                db.execute("UPDATE usage SET requests = requests + 1 WHERE day = ?", (day,))
        except sqlite3.Error:
            logging.getLogger("trust-signal").exception("Budget storage unavailable")
            raise HTTPException(503, "Tahlil xizmati vaqtincha mavjud emas.") from None
        except OSError:
            raise HTTPException(503, "Tahlil xizmati vaqtincha mavjud emas.") from None
