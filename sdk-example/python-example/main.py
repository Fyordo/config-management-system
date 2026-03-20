import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

_sdk_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "python-sdk")
if os.path.isdir(_sdk_path):
    sys.path.insert(0, _sdk_path)

from cms import PropertyManager

_CONFIG_PATH = os.environ.get("CMS_PROPERTIES_FILE", "/app/config/application.json")
_SOCKET_PATH = os.environ.get("CMS_UNIX_SOCKET_PATH", "/app/config/cms.sock")
_HTTP_ADDR = os.environ.get("HTTP_ADDR", "0.0.0.0:8080")

property_manager = PropertyManager(
    config_file_path=_CONFIG_PATH,
    unix_socket_path=_SOCKET_PATH,
)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[http] {self.address_string()} - {fmt % args}")

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path in ("/actuator/health/liveness", "/actuator/health/readiness"):
            self._send_json({"status": "UP"})
            return

        if parsed.path != "/test/property":
            self._send_json({"error": "not found"}, 404)
            return

        params = parse_qs(parsed.query)
        keys = params.get("key")
        if not keys:
            self._send_json({"error": "query parameter 'key' is required"}, 400)
            return

        key = keys[0]
        value = property_manager.get(key)
        if value is None:
            self._send_json({"error": f"property '{key}' not found"}, 404)
            return

        self._send_json({"key": key, "value": value if not isinstance(value, bytes) else value.decode("utf-8", errors="replace")})

    def _send_json(self, body: dict, status: int = 200):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def main():
    property_manager.add_update_callback(
        "app.python.example",
        lambda key, old, new: print("PROPERTY CHANGE !"),
    )
    property_manager.init()

    host, _, port_str = _HTTP_ADDR.rpartition(":")
    host = host or "0.0.0.0"
    port = int(port_str)

    server = HTTPServer((host, port), Handler)
    print(f"listening on {host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
