"""Disposable SMTP sink with a loopback-published test API. Never a production mail service."""
import email
from email import policy
from email.utils import parseaddr
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import socketserver
import threading

messages = []
failure = False
mutex = threading.Lock()


class SMTP(socketserver.StreamRequestHandler):
    def reply(self, line):
        self.wfile.write((line + "\r\n").encode())
        self.wfile.flush()

    def handle(self):
        self.connection.settimeout(20)
        self.reply("220 synthetic SMTP fixture")
        recipient = ""
        for raw in self.rfile:
            command = raw.decode(errors="replace").strip()
            upper = command.upper()
            if upper.startswith(("EHLO", "HELO")):
                self.reply("250-fixture")
                self.reply("250 SIZE 1048576")
            elif upper.startswith("RCPT TO:"):
                recipient = parseaddr(command[8:].strip())[1]
                self.reply("250 OK" if recipient.endswith("@example.test") else "550 Synthetic recipients only")
            elif upper == "DATA":
                self.reply("354 End with a dot")
                chunks = []
                size = 0
                for part in self.rfile:
                    if part == b".\r\n":
                        break
                    size += len(part)
                    if size > 1048576:
                        self.reply("552 Too large")
                        return
                    chunks.append(part[1:] if part.startswith(b"..") else part)
                with mutex:
                    failed = failure
                    if not failed:
                        message = email.message_from_bytes(b"".join(chunks), policy=policy.default)
                        body = message.get_body(preferencelist=("plain",)) if message.is_multipart() else message
                        messages.append({"to": recipient, "subject": str(message["subject"]), "text": body.get_content()})
                self.reply("451 Synthetic delivery failure" if failed else "250 Accepted for test delivery")
            elif upper == "QUIT":
                self.reply("221 Bye")
                return
            else:
                self.reply("250 OK")


class API(BaseHTTPRequestHandler):
    def respond(self, body, status=200):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        with mutex:
            if self.path == "/messages":
                self.respond(messages)
            elif self.path == "/health":
                self.respond({"status": "UP"})
            else:
                self.respond({}, 404)

    def do_POST(self):
        global failure
        if self.path != "/failure":
            return self.respond({}, 404)
        length = int(self.headers.get("Content-Length", "0"))
        if length > 1024:
            return self.respond({}, 413)
        with mutex:
            failure = bool(json.loads(self.rfile.read(length)).get("enabled"))
        self.respond({"enabled": failure})

    def log_message(self, *_args):
        pass


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


smtp = Server(("0.0.0.0", 1025), SMTP)
threading.Thread(target=smtp.serve_forever, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 8025), API).serve_forever()
