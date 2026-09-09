#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import mimetypes
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAMS = {
    "google": "https://dl.google.com/dl/android/maven2/",
    "maven": "https://repo.maven.apache.org/maven2/",
    "plugins": "https://plugins.gradle.org/m2/",
}

UPSTREAM_SLOTS = threading.BoundedSemaphore(3)
_LOCKS_GUARD = threading.Lock()
_URL_LOCKS: dict[str, threading.Lock] = {}


def cache_path(cache_root: pathlib.Path, url: str) -> pathlib.Path:
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()
    return cache_root / digest[:2] / digest[2:4] / digest


def url_lock(url: str) -> threading.Lock:
    with _LOCKS_GUARD:
        lock = _URL_LOCKS.get(url)
        if lock is None:
            lock = threading.Lock()
            _URL_LOCKS[url] = lock
        return lock


class ProxyHandler(BaseHTTPRequestHandler):
    server_version = "DSHMMavenProxy/2.1"

    def log_message(self, fmt: str, *args: object) -> None:
        sys.stderr.write("[maven-proxy] " + (fmt % args) + "\n")
        sys.stderr.flush()

    def do_HEAD(self) -> None:  # noqa: N802
        self._handle(send_body=False)

    def do_GET(self) -> None:  # noqa: N802
        self._handle(send_body=True)

    def _handle(self, send_body: bool) -> None:
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path == "/__health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", "3")
            self.end_headers()
            if send_body:
                self.wfile.write(b"ok\n")
            return

        parts = parsed.path.lstrip("/").split("/", 1)
        if len(parts) != 2 or parts[0] not in UPSTREAMS:
            self.send_error(404, "Unknown repository route")
            return

        route, rel = parts
        if not rel or ".." in pathlib.PurePosixPath(rel).parts:
            self.send_error(400, "Invalid repository path")
            return

        upstream_url = urllib.parse.urljoin(UPSTREAMS[route], rel)
        if parsed.query:
            upstream_url += "?" + parsed.query

        cache_root: pathlib.Path = self.server.cache_root  # type: ignore[attr-defined]
        cached = cache_path(cache_root, upstream_url)

        if cached.is_file():
            self._serve_file(cached, send_body=send_body)
            return

        lock = url_lock(upstream_url)
        with lock:
            if cached.is_file():
                self._serve_file(cached, send_body=send_body)
                return

            cached.parent.mkdir(parents=True, exist_ok=True)
            fd, tmp_name = tempfile.mkstemp(prefix="maven-", dir=str(cached.parent))
            os.close(fd)
            try:
                with UPSTREAM_SLOTS:
                    result = subprocess.run(
                        [
                            "curl",
                            "--location",
                            "--silent",
                            "--show-error",
                            "--retry",
                            "8",
                            "--retry-delay",
                            "1",
                            "--retry-max-time",
                            "240",
                            "--retry-all-errors",
                            "--connect-timeout",
                            "20",
                            "--max-time",
                            "240",
                            "--write-out",
                            "%{http_code}",
                            "--output",
                            tmp_name,
                            "--user-agent",
                            "DeepSeek-Harness-Mobile-Maven-Proxy/2.1",
                            upstream_url,
                        ],
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True,
                        timeout=300,
                    )

                http_code = (result.stdout or "").strip()[-3:]
                if result.returncode != 0:
                    stderr = (result.stderr or "curl fetch failed").strip()
                    self.log_message("upstream failure %s: %s", upstream_url, stderr)
                    self.send_error(504, "Upstream unavailable after retries")
                    return

                if http_code == "404":
                    self.log_message("not found %s", upstream_url)
                    self.send_error(404, "Not found in this repository")
                    return

                if http_code != "200":
                    self.log_message("upstream HTTP %s %s", http_code, upstream_url)
                    self.send_error(504, f"Upstream HTTP {http_code} after retries")
                    return

                if not os.path.isfile(tmp_name) or os.path.getsize(tmp_name) == 0:
                    self.log_message("empty upstream response %s", upstream_url)
                    self.send_error(504, "Empty upstream response")
                    return

                os.replace(tmp_name, cached)
                self.log_message("cached %s (%d bytes)", upstream_url, cached.stat().st_size)
                self._serve_file(cached, send_body=send_body)
            except subprocess.TimeoutExpired:
                self.log_message("upstream timeout %s", upstream_url)
                self.send_error(504, "Upstream fetch timed out")
            except Exception as exc:  # noqa: BLE001
                self.log_message("proxy failure %s: %r", upstream_url, exc)
                self.send_error(500, "Proxy internal error")
            finally:
                if os.path.exists(tmp_name):
                    os.unlink(tmp_name)

    def _serve_file(self, path: pathlib.Path, send_body: bool = True) -> None:
        size = path.stat().st_size
        content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(size))
        self.end_headers()
        if send_body:
            try:
                with path.open("rb") as src:
                    shutil.copyfileobj(src, self.wfile)
            except (BrokenPipeError, ConnectionResetError):
                pass


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18765)
    parser.add_argument("--cache", required=True)
    args = parser.parse_args()

    cache_root = pathlib.Path(args.cache).resolve()
    cache_root.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer((args.host, args.port), ProxyHandler)
    server.cache_root = cache_root  # type: ignore[attr-defined]
    print(f"[maven-proxy] listening on http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
