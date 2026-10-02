import base64
import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

os.environ["GEMINI_API_KEY"] = "test-key-not-real"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app
from fastapi.testclient import TestClient
from PIL import Image


def neutral():
    return app.LlmAnalyzeResult(summary="Belgi yo'q", signals=[], tip="Tekshiring")


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.budget = app.DailyBudget(Path(self.temp.name) / "usage.db", limit=100)
        p = patch.object(app, "budget", self.budget)
        p.start()
        self.addCleanup(p.stop)
        # Each test gets independent request guard state.
        app.app.middleware_stack = None
        self.client = TestClient(app.app)
        self.addCleanup(self.client.close)

    def test_bad_input_never_calls_model(self):
        with patch.object(app.client.models, "generate_content") as model:
            for path, body in [
                ("/api/analyze", {"content": " "}),
                ("/api/analyze", {"content": "x" * 8001}),
                ("/api/analyze", {"content": 1}),
                ("/api/analyze-image", {"imageBase64": "!bad"}),
                ("/api/analyze-audio", {"audioBase64": "!bad"}),
            ]:
                self.assertEqual(self.client.post(path, json=body).status_code, 400)
            model.assert_not_called()

    def test_health_advertises_cyber_api_version(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "analysisVersion": "cyber-v1", "appVersion": "2.0.0"})
        self.assertEqual(self.client.get("/openapi.json").json()["info"]["version"], "2.0.0")

    def test_suspicious_article_and_redirect_urls_are_checked(self):
        article = {"title": "Sarlavha", "text": "Oddiy matn " * 20,
                   "links": ["https://bit.ly/redirect", "https://google.evil.com/login"], "truncated": True}
        with patch.object(app, "safe_fetch_article", return_value=article), patch.object(
            app.client.models, "generate_content", return_value=SimpleNamespace(parsed=neutral())
        ):
            r = self.client.post("/api/analyze", json={"content": "https://payme-login.xyz/story"})
        self.assertEqual(r.status_code, 200)
        body = r.json()
        quotes = [s["quote"] for s in body["signals"]]
        self.assertIn("https://payme-login.xyz/story", quotes)
        self.assertIn("https://bit.ly/redirect", quotes)
        self.assertIn("https://google.evil.com/login", quotes)
        self.assertEqual(body["cautionLevel"], "ozgina_belgi")
        self.assertTrue(body["warnings"])
        self.assertIn("Oddiy matn", body["extractedText"])

    def test_fetch_failure_does_not_spend_budget(self):
        with patch.object(app, "safe_fetch_article", side_effect=ValueError), patch.object(self.budget, "reserve") as reserve:
            r = self.client.post("/api/analyze", json={"content": "http://127.0.0.1/"})
        self.assertEqual(r.status_code, 400)
        reserve.assert_not_called()

    def test_neutral_and_provider_failure(self):
        with patch.object(app.client.models, "generate_content", return_value=SimpleNamespace(parsed=neutral())):
            r = self.client.post("/api/analyze", json={"content": "Bugun kutubxonaga bordim."})
            self.assertEqual(r.status_code, 200)
            self.assertEqual(r.json()["signals"], [])
        with patch.object(app.client.models, "generate_content", side_effect=RuntimeError("provider-secret")):
            r = self.client.post("/api/analyze", json={"content": "Salom dunyo"})
            self.assertEqual(r.status_code, 502)
            self.assertNotIn("provider-secret", r.text)

    def test_missing_model_output(self):
        with patch.object(app.client.models, "generate_content", return_value=SimpleNamespace(parsed=None)):
            r = self.client.post("/api/analyze", json={"content": "Salom dunyo"})
            self.assertEqual(r.status_code, 502)

    def test_qr_retains_full_destination_and_reports_decoder_failure(self):
        image = io.BytesIO()
        Image.new("RGB", (64, 64)).save(image, format="PNG")
        body = {"imageBase64": base64.b64encode(image.getvalue()).decode(), "mimeType": "image/png"}
        qr = "https://example.com/" + "x" * 250
        for decoded in ([qr], None):
            result = app.MediaAnalyzeResult(**neutral().model_dump(), extractedText="Salom")
            with patch.object(app, "decode_qr_texts", return_value=decoded), patch.object(
                app.client.models, "generate_content", return_value=SimpleNamespace(parsed=result)
            ):
                r = self.client.post("/api/analyze-image", json=body)
            self.assertEqual(r.status_code, 200)
            if decoded:
                self.assertEqual(r.json()["signals"][0]["quote"], qr)
                self.assertEqual(r.json()["riskLevel"], "none")
                self.assertEqual(r.json()["riskTypes"], [])
            else:
                self.assertTrue(r.json()["warnings"])

    def test_validation_does_not_echo_private_input(self):
        r = self.client.post("/api/analyze", json={"content": {"private": "secret-message"}})
        self.assertEqual(r.status_code, 400)
        self.assertNotIn("secret-message", r.text)

    def test_domains(self):
        self.assertEqual(app._domain_of("https://google.com@8.8.8.8/"), "8.8.8.8")
        self.assertEqual(app._domain_of("https://[2606:4700:4700::1111]/"), "2606:4700:4700::1111")
        self.assertTrue(app.link_signals("https://[2606:4700:4700::1111]/"))
        self.assertTrue(app.link_signals("https://payme-login.xyz/"))
        self.assertFalse(app.link_signals("https://pineapple.com/"))
        self.assertFalse(app.link_signals("https://accounts.google.com/"))
        self.assertEqual(app._domain_of("https://[broken/"), "")

    def test_real_qr_decode(self):
        # These fixtures ship with our pinned pyzbar wheel; no extra generator
        # dependency or network access is needed for the integration check.
        import pyzbar
        fixtures = Path(pyzbar.__file__).parent / "tests"
        self.assertEqual(app.decode_qr_texts((fixtures / "qrcode.png").read_bytes()), ["Thalassiodracon"])
        self.assertEqual(app.decode_qr_texts((fixtures / "code128.png").read_bytes()), [])

    def test_high_risk_text_contract(self):
        signal = app.Signal(technique="SMS-kod so'ralmoqda", quote="Kodni menga ayting",
                            explanation="Begona shaxs kod so'ramoqda", category="phishing", severity="high")
        result = app.LlmAnalyzeResult(summary="Kod orqali hisobga kirish xavfi bor", signals=[signal], tip="Kodni bermang")
        with patch.object(app.client.models, "generate_content", return_value=SimpleNamespace(parsed=result)) as model:
            response = self.client.post("/api/analyze", json={"content": "Kodni menga ayting"})
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["analysisVersion"], "cyber-v1")
        self.assertEqual(body["riskLevel"], "high")
        self.assertEqual(body["riskTypes"], ["phishing"])
        self.assertEqual(body["cautionLevel"], "kop_belgi")
        self.assertTrue(body["immediateActions"])
        self.assertTrue(body["recoverySteps"])
        self.assertEqual(model.call_args.kwargs["config"].system_instruction, app.SYSTEM_PROMPT)

    def test_audio_uses_cyber_contract(self):
        result = app.LlmMediaResult(summary="To'lov talabi shubhali", signals=[app.Signal(
            technique="Soxta xavfsiz hisob", quote="Pulni shu hisobga o'tkazing", explanation="Bosim ostida to'lov",
            category="payment_scam", severity="high")], tip="Rasmiy kanalni tekshiring",
            extractedText="Pulni shu hisobga o'tkazing")
        with patch.object(app, "validate_audio"), patch.object(app.client.models, "generate_content", return_value=SimpleNamespace(parsed=result)) as model:
            response = self.client.post("/api/analyze-audio", json={
                "audioBase64": base64.b64encode(b"x" * 1024).decode(), "mimeType": "audio/ogg"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["riskLevel"], "high")
        self.assertEqual(response.json()["riskTypes"], ["payment_scam"])
        self.assertTrue(response.json()["recoverySteps"])
        self.assertEqual(model.call_args.kwargs["config"].system_instruction, app.AUDIO_PROMPT)


if __name__ == "__main__":
    unittest.main()
