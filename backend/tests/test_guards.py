import asyncio
import json
import sys
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fastapi import HTTPException
from guards import BODY_LIMITS, DailyBudget, RequestGuard


class GuardTests(unittest.IsolatedAsyncioTestCase):
    async def call_guard(self, chunks, headers=None, guard=None, peer="1.2.3.4"):
        called, sent = [], []
        async def downstream(scope, receive, send):
            called.append(await receive())
        guard = guard or RequestGuard(downstream)
        messages = iter(chunks)
        async def receive():
            return next(messages)
        async def send(message):
            sent.append(message)
        scope = {"type": "http", "method": "POST", "path": "/api/analyze", "client": (peer, 1), "headers": headers or []}
        await guard(scope, receive, send)
        return called, sent, guard

    async def test_chunked_body_rejected_before_json_parsing(self):
        chunks = [{"type": "http.request", "body": b"x" * 40000, "more_body": True},
                  {"type": "http.request", "body": b"x" * 40000, "more_body": False}]
        called, sent, guard = await self.call_guard(chunks)
        self.assertFalse(called)
        self.assertEqual(sent[0]["status"], 413)
        self.assertEqual(guard.active, 0)

    async def test_declared_oversize_never_reads_body(self):
        called, sent, _ = await self.call_guard([], [(b"content-length", b"999999999")])
        self.assertFalse(called)
        self.assertEqual(sent[0]["status"], 413)

    async def test_small_body_replayed(self):
        called, sent, guard = await self.call_guard([
            {"type": "http.request", "body": b"abc", "more_body": True},
            {"type": "http.request", "body": b"def", "more_body": False}])
        self.assertEqual(called[0]["body"], b"abcdef")
        self.assertEqual(guard.active, 0)

    async def test_disconnect_releases_slot(self):
        called, _, guard = await self.call_guard([{"type": "http.disconnect"}])
        self.assertFalse(called)
        self.assertEqual(guard.active, 0)

    async def test_slow_upload_timeout(self):
        async def downstream(*args):
            self.fail("should not parse body")
        async def receive():
            await asyncio.sleep(1)
        sent = []
        async def send(message): sent.append(message)
        guard = RequestGuard(downstream, body_timeout=0.01)
        await guard({"type": "http", "method": "POST", "path": "/api/analyze"}, receive, send)
        self.assertEqual(sent[0]["status"], 408)
        self.assertEqual(guard.active, 0)

    async def test_rate_limit_does_not_trust_spoofed_forwarded_header(self):
        async def downstream(*args): pass
        guard = RequestGuard(downstream, per_minute=1)
        await self.call_guard([{"type": "http.request", "body": b""}], guard=guard)
        _, sent, _ = await self.call_guard([], headers=[(b"x-forwarded-for", b"9.9.9.9")], guard=guard)
        self.assertEqual(sent[0]["status"], 429)

    async def test_concurrency_and_exception_cleanup(self):
        async def downstream(*args): raise RuntimeError("test")
        guard = RequestGuard(downstream, concurrency=1)
        self.assertIsNone(guard.admit("first"))
        self.assertEqual(guard.admit("second"), 503)
        guard.active = 0
        with self.assertRaises(RuntimeError):
            await self.call_guard([{"type": "http.request", "body": b""}], guard=guard)
        self.assertEqual(guard.active, 0)

    async def test_rate_window_resets(self):
        guard = RequestGuard(None, per_minute=1, concurrency=3)
        with patch("guards.time.monotonic", return_value=60):
            self.assertIsNone(guard.admit("client"))
            self.assertEqual(guard.admit("client"), 429)
        with patch("guards.time.monotonic", return_value=120):
            self.assertIsNone(guard.admit("client"))


class BudgetTests(unittest.TestCase):
    def test_daily_budget_is_atomic_and_survives_new_instance(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            def reserve(_):
                try:
                    DailyBudget(path, limit=3).reserve()
                    return True
                except HTTPException as exc:
                    self.assertEqual(exc.status_code, 429)
                    return False
            with ThreadPoolExecutor(max_workers=5) as pool:
                accepted = list(pool.map(reserve, range(10)))
            self.assertEqual(sum(accepted), 3)
            self.assertFalse(reserve(None))

    def test_storage_failure_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(HTTPException) as error:
                DailyBudget(Path(directory), limit=1).reserve()
            self.assertEqual(error.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
