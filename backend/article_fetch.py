"""Article fetch runs in a disposable process so DNS and slow reads are cancellable."""
import ipaddress
import json
import re
import socket
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse

FETCH_MAX_BYTES = 2 * 1024 * 1024
FETCH_MAX_REDIRECTS = 3
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
        if not ip.is_global or ip.is_multicast:
            raise ValueError("private address blocked")
        ips.append(str(ip))
    if not ips:
        raise ValueError("no address")
    # IPv4 afzal (barqarorlik uchun)
    ips.sort(key=lambda s: ":" in s)
    return ips[0]


def _fetch_article(url: str) -> dict:
    """Return title, bounded text, checked-path links and truncation status.

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
    visited = []
    body = None
    encoding = "utf-8"

    for _ in range(FETCH_MAX_REDIRECTS + 1):
        if _time.monotonic() > deadline:
            raise ValueError("fetch deadline exceeded")
        visited.append(current)
        parsed = urlparse(current)
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError("bad scheme")
        if parsed.username or parsed.password:
            raise ValueError("userinfo not allowed")
        port = parsed.port if parsed.port is not None else (443 if parsed.scheme == "https" else 80)
        if port not in (80, 443):
            raise ValueError("port not allowed")

        host = parsed.hostname
        pinned_ip = _public_ip_for(host)
        remaining = deadline - _time.monotonic()
        if remaining <= 0:
            raise ValueError("fetch deadline exceeded")
        timeout = urllib3.Timeout(connect=min(5.0, remaining), read=min(6.0, remaining))
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
                    "Host": parsed.netloc,
                    "Accept-Encoding": "identity",
                    "User-Agent": "Mozilla/5.0 (compatible; TrustSignal/2.0)",
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
                ctype = resp.headers.get("Content-Type", "").lower()
                if ctype.split(";", 1)[0].strip() not in ("text/html", "application/xhtml+xml", "text/plain"):
                    raise ValueError("unsupported page content type")
                chunks, total = [], 0
                while True:
                    if _time.monotonic() > deadline:
                        raise ValueError("fetch deadline exceeded")
                    chunk = resp.read(min(65536, FETCH_MAX_BYTES + 1 - total))
                    if not chunk:
                        break
                    chunks.append(chunk)
                    total += len(chunk)
                    if total > FETCH_MAX_BYTES:
                        raise ValueError("page too large")
                body = b"".join(chunks)
                m = re.search(r"charset=([\w-]+)", ctype)
                if m:
                    encoding = m.group(1)
                break
            finally:
                resp.close()
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
    links = list(dict.fromkeys(visited))
    for anchor in soup.find_all("a", href=True):
        try:
            link = urljoin(current, anchor["href"])
            if len(link) <= 2048 and urlparse(link).scheme in ("http", "https") and link not in links:
                links.append(link)
        except ValueError:
            # One malformed href must not discard an otherwise valid article.
            continue
        if len(links) >= 100:
            break
    full_text = re.sub(r"\s+", " ", soup.get_text(" ", strip=True))
    text = full_text[:6000]
    if len(text) < 100:
        raise ValueError("page has too little text")
    return {"title": title, "text": text, "links": links, "truncated": len(full_text) > len(text)}



def safe_fetch_article(url: str) -> dict:
    # subprocess.run kills AND waits for the worker on timeout. A thread timeout
    # would leave DNS/socket work running and eventually exhaust the server.
    try:
        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve()), "--worker"],
            input=url, capture_output=True, text=True, timeout=FETCH_TOTAL_DEADLINE_S,
            check=True,
        )
    except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as exc:
        raise ValueError("article unavailable or deadline exceeded") from exc
    return json.loads(result.stdout)


if __name__ == "__main__":
    try:
        print(json.dumps(_fetch_article(sys.stdin.read(8193))))
    except Exception:
        # Do not log URL query tokens or other caller data.
        sys.exit(1)
