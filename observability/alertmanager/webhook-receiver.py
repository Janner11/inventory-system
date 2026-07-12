"""Receptor de webhooks de Alertmanager, minimo y sin dependencias externas.

OBS-005: el ticket pide configurar "route con receiver: default (webhook o
email)". El proyecto no tiene ningun servidor SMTP ni un servicio externo de
webhooks real (ver CLAUDE.md - patron ya establecido de no depender de
servicios externos como webhook.site para evidencia reproducible offline).
Este script es el receptor: registra en stdout (visible con `docker logs`)
cada POST que Alertmanager le envie, para poder verificar "Alertmanager
recibe alertas" contra un target real y committeado, no un placeholder que
nunca se prueba.
"""

import json
import sys
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer


class AlertWebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        timestamp = datetime.now(timezone.utc).isoformat()

        try:
            payload = json.loads(body)
            alerts = payload.get("alerts", [])
            print(f"[{timestamp}] Recibidas {len(alerts)} alerta(s):", flush=True)
            for alert in alerts:
                name = alert.get("labels", {}).get("alertname", "?")
                status = alert.get("status", "?")
                severity = alert.get("labels", {}).get("severity", "?")
                summary = alert.get("annotations", {}).get("summary", "")
                print(f"  - {name} [{status}/{severity}]: {summary}", flush=True)
        except json.JSONDecodeError:
            print(f"[{timestamp}] POST recibido con body no-JSON ({length} bytes)", flush=True)

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"received"}')

    def do_GET(self):
        # Healthcheck simple para el docker-compose.dev.yml.
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, format, *args):
        pass  # el logging real ya ocurre en do_POST/do_GET


if __name__ == "__main__":
    port = 5001
    server = HTTPServer(("0.0.0.0", port), AlertWebhookHandler)
    print(f"Webhook receiver escuchando en :{port}", flush=True)
    sys.stdout.flush()
    server.serve_forever()
