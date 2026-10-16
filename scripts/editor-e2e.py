#!/usr/bin/env python3
"""Build and exercise a disposable local editor stack; retain diagnostics on exit."""
import base64
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
SERVICES = ("user-service", "content-service", "meeting-service", "api-gateway")


class Run:
    def __init__(self):
        token = time.strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]
        self.project = "advent-editor-e2e-" + token
        self.artifacts = ROOT / "reports" / "editor-e2e" / token
        self.artifacts.mkdir(parents=True, mode=0o700)
        self.compose = ["docker", "compose", "--env-file", os.devnull, "-p", self.project,
                        "-f", str(ROOT / "docs/backend/runtime-editor-e2e.yml")]
        self.env = os.environ.copy()
        self.env["ADVENT_TEST_ARTIFACTS"] = str(self.artifacts / "browser")
        self.active = None
        self.frontend = None
        self.started = False

    def command(self, args, log, cwd=ROOT, timeout=900):
        print(f"[{log}] {' '.join(map(str, args))}", flush=True)
        with (self.artifacts / (log + ".log")).open("w") as output:
            self.active = subprocess.Popen(args, cwd=cwd, env=self.env, stdout=output,
                                           stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = self.active.wait(timeout=timeout)
                if code:
                    raise RuntimeError(f"{log} failed ({code}); see {output.name}")
            finally:
                self.stop(self.active)
                self.active = None

    @staticmethod
    def stop(process):
        if process is None:
            return
        # Also stop npm/node children when the parent already exited.
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            pass
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()

    def capture(self, args):
        return subprocess.check_output(args, cwd=ROOT, env=self.env, text=True, timeout=30).strip()

    def provision(self, gateway):
        container = self.capture(self.compose + ["ps", "-q", "postgres"])
        info = json.loads(self.capture(["docker", "inspect", container]))[0]
        labels = info["Config"]["Labels"]
        if (labels.get("com.docker.compose.project") != self.project
                or labels.get("com.docker.compose.service") != "postgres"
                or "/var/lib/postgresql/data" not in info["HostConfig"].get("Tmpfs", {})):
            raise RuntimeError("Refusing fixture SQL outside this run's tmpfs PostgreSQL")
        password = "Editor-2026!" + secrets.token_hex(16)
        users = {}
        for who in ("editor", "student", "foreign"):
            data = {"email": f"{self.project}-{who}@example.test", "password": password,
                    "firstName": who, "lastName": "Editor smoke"}
            request = urllib.request.Request(gateway + "/api/auth/register",
                                             data=json.dumps(data).encode(),
                                             headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=30) as response:
                user = json.load(response)
            users[who] = {"id": str(uuid.UUID(user["id"])), "email": data["email"]}
        ids = ",".join("'" + user["id"] + "'" for user in users.values())
        sql = f"""
BEGIN;
INSERT INTO roles(id,name,description,system_role,created_at,updated_at)
VALUES(gen_random_uuid(),'CONTENT_EDITOR','Disposable editor fixture',false,now(),now())
ON CONFLICT(name) DO NOTHING;
UPDATE users SET status='ACTIVE',email_verified=true WHERE id IN ({ids});
INSERT INTO user_roles(user_id,role_id)
SELECT '{users['editor']['id']}',id FROM roles WHERE name='CONTENT_EDITOR'
ON CONFLICT DO NOTHING;
INSERT INTO user_roles(user_id,role_id)
SELECT '{users['foreign']['id']}',id FROM roles WHERE name='INSTRUCTOR'
ON CONFLICT DO NOTHING;
COMMIT;
"""
        subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "sprint", "-d", "sprint",
                        "-v", "ON_ERROR_STOP=1"], input=sql, text=True, check=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        fixture = self.artifacts / "fixture.json"
        fixture.write_text(json.dumps({"users": users, "password": password}))
        fixture.chmod(0o600)
        self.env["ADVENT_EDITOR_FIXTURE"] = str(fixture)
        self.env.update(ADVENT_TEST_GATEWAY=gateway, ADVENT_TEST_POSTGRES=container,
                        ADVENT_TEST_PROJECT=self.project)

    def execute(self):
        print(f"Artifacts: {self.artifacts}", flush=True)
        node = self.capture(["node", "-p", "process.versions.node"])
        if node.split(".")[0] != "24":
            raise RuntimeError("Node.js 24 must be on PATH")
        self.command(["docker", "info"], "docker-info", timeout=30)
        self.command(["docker", "compose", "version"], "compose-version", timeout=30)
        self.command([str(ROOT / "gradlew"), *[f":{s}:bootJar" for s in SERVICES],
                      "--no-daemon"], "backend-build")
        self.command(["npm", "ci", "--no-audit", "--no-fund"], "npm-ci", FRONTEND)
        # Fail before starting containers if no usable browser is installed.
        self.command(["node", "--input-type=module", "-e",
                      "import {withSmokeBrowser} from './scripts/smoke-browser.mjs'; "
                      "await withSmokeBrowser('preflight', async () => {});"],
                     "browser-preflight", FRONTEND, timeout=60)
        self.env["NEXT_TELEMETRY_DISABLED"] = "1"
        self.command(["npm", "run", "check"], "frontend-check", FRONTEND)
        self.command(["npm", "run", "build"], "frontend-build", FRONTEND)
        self.started = True
        self.command(self.compose + ["up", "-d", "--build", "--wait", "--wait-timeout", "180"],
                     "compose-up", timeout=900)
        address = self.capture(self.compose + ["port", "api-gateway", "8090"])
        gateway = "http://" + address
        self.provision(gateway)
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
        origin = f"http://127.0.0.1:{port}"
        self.env.update(API_GATEWAY_URL=gateway, FRONTEND_ORIGIN=origin,
                        SESSION_ENCRYPTION_KEY=base64.b64encode(secrets.token_bytes(32)).decode(),
                        MEETING_SIGNALING_URL=gateway.replace("http:", "ws:") + "/ws/meetings",
                        ADVENT_TEST_ORIGIN=origin,
                        ADVENT_TEST_ARTIFACTS=str(self.artifacts / "browser"))
        with (self.artifacts / "frontend.log").open("w") as output:
            self.frontend = subprocess.Popen(["npm", "run", "start", "--", "--port", str(port)],
                                             cwd=FRONTEND, env=self.env, stdout=output,
                                             stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if self.frontend.poll() is not None:
                raise RuntimeError("Frontend exited before readiness; see frontend.log")
            try:
                with urllib.request.urlopen(origin + "/api/health", timeout=3) as response:
                    if response.status == 200:
                        break
            except (urllib.error.URLError, TimeoutError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError("Frontend readiness timed out; see frontend.log")
        self.command(["npm", "run", "test:editor"], "editor", FRONTEND, timeout=600)
        self.command(["npm", "run", "test:dialogs"], "dialogs", FRONTEND, timeout=600)
        self.command(["npm", "run", "test:lesson-design"], "lesson-design", FRONTEND, timeout=600)

    def cleanup(self):
        self.stop(self.active)
        self.stop(self.frontend)
        if not self.started:
            return True
        for args, name in ((["ps", "-a"], "compose-ps"),
                           (["logs", "--no-color"], "compose-services"),
                           (["down", "--volumes", "--remove-orphans", "--rmi", "local"], "cleanup")):
            try:
                self.command(self.compose + args, name, timeout=120)
            except Exception as error:
                print(f"Diagnostics/cleanup: {error}", file=sys.stderr)
                if name == "cleanup":
                    print("Retry cleanup: " + " ".join(self.compose + args), file=sys.stderr)
                    return False
        return True


def main():
    run = Run()

    def interrupt(signum, _frame):
        raise KeyboardInterrupt(f"signal {signum}")

    signal.signal(signal.SIGTERM, interrupt)
    result = 1
    try:
        run.execute()
        result = 0
    except (Exception, KeyboardInterrupt) as error:
        print(f"FAIL: {error}", file=sys.stderr)
    finally:
        # A second signal must not interrupt removal of this run's resources.
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        if not run.cleanup():
            result = 1
        (run.artifacts / "result.json").write_text(json.dumps({
            "status": "passed" if result == 0 else "failed", "project": run.project,
        }, indent=2) + "\n")
        print(f"{'PASS' if result == 0 else 'FAIL'}; artifacts: {run.artifacts}", flush=True)
    return result


if __name__ == "__main__":
    sys.exit(main())
