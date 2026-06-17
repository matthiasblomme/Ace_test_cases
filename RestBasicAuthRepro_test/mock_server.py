"""Header-capturing HTTP mock for the REST Request basic-auth repro.

Listens on :7801 and, for every request, records the full set of inbound
headers (the point of interest is whether `Authorization: Basic ...` arrives).
Each request is:
  - printed to stdout with the Authorization header called out, and
  - appended as one JSON line to mock_requests.log next to this script.
The response echoes the received headers back as JSON, so the value is also
visible in the ACE flow's HTTP reply (curl), not just here.

Run:  python mock_server.py            (port 7801)
      python mock_server.py 8088        (custom port)
"""
import base64
import datetime
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mock_requests.log")

# Reactive Basic-auth challenge: when True (default), a request that arrives with
# no Authorization header gets 401 + WWW-Authenticate, so a reactive client
# re-sends WITH credentials. Pass --no-challenge to always return 200 instead.
CHALLENGE = "--no-challenge" not in sys.argv


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _capture(self):
        headers = {k: v for k, v in self.headers.items()}
        auth = self.headers.get("Authorization")
        decoded = None
        if auth and auth.lower().startswith("basic "):
            try:
                decoded = base64.b64decode(auth.split(" ", 1)[1]).decode("utf-8", "replace")
            except Exception:
                decoded = "<undecodable>"

        record = {
            "time": datetime.datetime.now().isoformat(timespec="seconds"),
            "method": self.command,
            "path": self.path,
            "authorization_present": auth is not None,
            "authorization": auth,
            "authorization_decoded": decoded,
            "headers": headers,
        }

        with open(LOG_PATH, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(record) + "\n")

        marker = "AUTH PRESENT" if auth else "NO AUTH HEADER"
        print(f"\n=== {record['time']}  {self.command} {self.path}  [{marker}] ===")
        if auth:
            print(f"    Authorization: {auth}")
            if decoded:
                print(f"    decoded -> {decoded}")
        for k, v in headers.items():
            print(f"    {k}: {v}")
        sys.stdout.flush()
        return record

    def _respond(self, record):
        # Reactive challenge: no creds -> 401 so a reactive client retries with Basic.
        if CHALLENGE and not record["authorization_present"]:
            body = json.dumps({"error": "credentials required"}).encode("utf-8")
            self.send_response(401)
            self.send_header("WWW-Authenticate", 'Basic realm="mock"')
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)
            return
        body = json.dumps({
            "token": "dummy-token-12345",
            "authorization_present": record["authorization_present"],
            "received_headers": record["headers"],
        }).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _handle(self):
        # Drain any request body so the connection stays clean.
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length:
            self.rfile.read(length)
        record = self._capture()
        self._respond(record)

    do_GET = _handle
    do_POST = _handle
    do_PUT = _handle
    do_DELETE = _handle
    do_HEAD = _handle

    def log_message(self, *args):
        pass  # our _capture() already logs


if __name__ == "__main__":
    ports = [a for a in sys.argv[1:] if a.isdigit()]
    port = int(ports[0]) if ports else 7801
    print(f"Header-capturing mock listening on :{port}  (challenge={'on' if CHALLENGE else 'off'})")
    print(f"Logging requests to {LOG_PATH}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
