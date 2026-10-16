#!/usr/bin/env python3
"""Run the user administration scenario with real services and an isolated SMTP sink."""
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("editor_e2e", Path(__file__).with_name("editor-e2e.py"))
editor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(editor)
BaseRun = editor.Run


class AdminRun(BaseRun):
    def __init__(self):
        super().__init__()
        old = self.artifacts
        self.artifacts = editor.ROOT / "reports/user-admin-e2e" / old.name
        self.artifacts.mkdir(parents=True, mode=0o700)
        old.rmdir()
        self.env["ADVENT_TEST_ARTIFACTS"] = str(self.artifacts / "browser")
        self.project = self.project.replace("advent-editor", "advent-user-admin")
        self.compose[self.compose.index("-p") + 1] = self.project
        self.compose[-1] = str(editor.ROOT / "docs/backend/runtime-user-admin.yml")

    def provision(self, gateway):
        super().provision(gateway)
        fixture = json.loads(Path(self.env["ADVENT_EDITOR_FIXTURE"]).read_text())
        # Base provisioning verifies this project's PostgreSQL label and tmpfs before any SQL.
        editor.subprocess.run(["docker", "exec", "-i", self.capture(self.compose + ["ps", "-q", "postgres"]),
                               "psql", "-U", "sprint", "-d", "sprint", "-v", "ON_ERROR_STOP=1"],
                              input=f"INSERT INTO user_roles SELECT '{fixture['users']['editor']['id']}',id FROM roles WHERE name='ADMIN';",
                              text=True, check=True, capture_output=True)
        self.env["ADVENT_TEST_PROJECT"] = self.project
        self.env["ADVENT_TEST_POSTGRES"] = self.capture(self.compose + ["ps", "-q", "postgres"])
        self.env["ADVENT_TEST_SMTP"] = "http://" + self.capture(self.compose + ["port", "smtp-fixture", "8025"])

    def command(self, args, log, cwd=editor.ROOT, timeout=900):
        super().command(args, log, cwd, timeout)
        if log == "dialogs":
            super().command(["npm", "run", "test:user-admin"], "user-admin", editor.FRONTEND, 600)


editor.Run = AdminRun
if __name__ == "__main__":
    sys.exit(editor.main())
