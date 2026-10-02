import io
import json
import socket
import subprocess
import sys
import time
import unittest
import wave
from pathlib import Path
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import article_fetch as fetch
from fastapi import HTTPException
from PIL import Image
from media import validate_audio, validate_image


def response(body, content_type="text/html", status=200, location=None):
    stream = io.BytesIO(body)
    r = MagicMock(status=status, headers={"Content-Type": content_type})
    if location:
        r.headers["Location"] = location
    r.read.side_effect = stream.read
    return r


class FetchTests(unittest.TestCase):
    def test_private_dns_and_mixed_answers_rejected(self):
        for ip in ["127.0.0.1", "10.0.0.1", "169.254.169.254", "::1", "100.64.0.1"]:
            answers = [(socket.AF_INET, 1, 6, "", ("8.8.8.8", 0)), (socket.AF_INET, 1, 6, "", (ip, 0))]
            with patch.object(fetch.socket, "getaddrinfo", return_value=answers):
                with self.assertRaises(ValueError):
                    fetch._public_ip_for("example.test")

    def test_unsafe_url_shapes_rejected_before_dns(self):
        for url in ["file:///etc/passwd", "http://user:pass@example.com", "http://example.com:8080", "https://example.com:0"]:
            with patch.object(fetch, "_public_ip_for") as dns:
                with self.assertRaises(ValueError): fetch._fetch_article(url)
                dns.assert_not_called()

    def test_ip_pinning_links_and_truncation(self):
        html = ('<html><title>Title</title><p>' + 'Words ' * 1200 + '</p><a href="/login">Login</a></html>').encode()
        with patch.object(fetch, "_public_ip_for", return_value="8.8.8.8"), patch("urllib3.HTTPSConnectionPool") as pool:
            pool.return_value.urlopen.return_value = response(html)
            article = fetch._fetch_article("https://example.com/story")
            self.assertEqual(pool.call_args.args[0], "8.8.8.8")
            self.assertEqual(pool.call_args.kwargs["server_hostname"], "example.com")
            self.assertEqual(pool.call_args.kwargs["assert_hostname"], "example.com")
        self.assertIn("https://example.com/login", article["links"])
        self.assertIn("https://example.com/story", article["links"])
        self.assertTrue(article["truncated"])
        self.assertEqual(len(article["text"]), 6000)

    def test_redirect_revalidates_target(self):
        with patch.object(fetch, "_public_ip_for", side_effect=["8.8.8.8", ValueError("private")]) as dns, patch("urllib3.HTTPSConnectionPool") as pool:
            pool.return_value.urlopen.return_value = response(b"", status=302, location="http://127.0.0.1/")
            with self.assertRaises(ValueError): fetch._fetch_article("https://example.com/")
            self.assertEqual(dns.call_args.args[0], "127.0.0.1")

    def test_oversize_and_non_html_fail_without_partial_result(self):
        for r in [response(b"a" * 1025), response(b"binary", "application/pdf")]:
            with patch.object(fetch, "FETCH_MAX_BYTES", 1024), patch.object(fetch, "_public_ip_for", return_value="8.8.8.8"), patch("urllib3.HTTPSConnectionPool") as pool:
                pool.return_value.urlopen.return_value = r
                with self.assertRaises(ValueError): fetch._fetch_article("https://example.com/")

    def test_worker_timeout_is_propagated(self):
        with patch.object(fetch.subprocess, "run", side_effect=subprocess.TimeoutExpired("worker", 12)) as run:
            with self.assertRaises(ValueError): fetch.safe_fetch_article("https://example.com/")
            self.assertEqual(run.call_args.kwargs["timeout"], 12)

    def test_real_worker_rejects_private_url(self):
        with self.assertRaises(ValueError):
            fetch.safe_fetch_article("http://127.0.0.1/")

    def test_hard_timeout_terminates_a_stuck_child(self):
        original_run = subprocess.run
        def stuck_worker(command, **kwargs):
            return original_run([sys.executable, "-c", "import time; time.sleep(30)"], **kwargs)
        started = time.monotonic()
        with patch.object(fetch, "FETCH_TOTAL_DEADLINE_S", 0.1), patch.object(fetch.subprocess, "run", side_effect=stuck_worker):
            with self.assertRaises(ValueError): fetch.safe_fetch_article("https://example.com/")
        self.assertLess(time.monotonic() - started, 3)


class MediaTests(unittest.TestCase):
    def test_small_valid_png_and_mime_mismatch(self):
        output = io.BytesIO()
        Image.new("1", (64, 64)).save(output, format="PNG")
        validate_image(output.getvalue(), "image/png")
        with self.assertRaises(HTTPException): validate_image(output.getvalue(), "image/jpeg")
        with self.assertRaises(HTTPException): validate_image(b"not an image", "image/png")

    def test_pixel_limit_before_decode(self):
        with patch("media.Image.open") as image:
            image.return_value.__enter__.return_value.size = (5000, 5000)
            with self.assertRaises(HTTPException): validate_image(b"x", "image/png")

    def test_audio_duration_and_video_rejected(self):
        examples = [
            {"streams": [{"codec_type": "audio"}], "format": {"duration": "301"}},
            {"streams": [{"codec_type": "video"}], "format": {"duration": "1"}},
            {"streams": [{"codec_type": "audio"}], "format": {"duration": "nan"}},
        ]
        for metadata in examples:
            with patch("media.subprocess.run", return_value=MagicMock(stdout=json.dumps(metadata))):
                with self.assertRaises(HTTPException): validate_audio(b"fake")

    def test_real_wav_probe(self):
        output = io.BytesIO()
        with wave.open(output, "wb") as audio:
            audio.setnchannels(1)
            audio.setsampwidth(2)
            audio.setframerate(8000)
            audio.writeframes(b"\0\0" * 8000)
        validate_audio(output.getvalue())

    def test_missing_probe_fails_closed(self):
        with patch("media.subprocess.run", side_effect=FileNotFoundError):
            with self.assertRaises(HTTPException) as exc: validate_audio(b"fake")
            self.assertEqual(exc.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
