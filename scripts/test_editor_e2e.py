"""Regression checks for fixture isolation and failure reporting (no Docker needed)."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("editor_e2e", Path(__file__).with_name("editor-e2e.py"))
editor_e2e = importlib.util.module_from_spec(spec)
spec.loader.exec_module(editor_e2e)


class EditorE2ETest(unittest.TestCase):
    def test_fixture_refuses_foreign_project_and_persistent_database(self):
        for project, service, tmpfs in (
            ("main-local-stack", "postgres", {"/var/lib/postgresql/data": ""}),
            ("this-run", "postgres", {}),
            ("this-run", "other-database", {"/var/lib/postgresql/data": ""}),
        ):
            with self.subTest(project=project, service=service, tmpfs=tmpfs):
                run = editor_e2e.Run.__new__(editor_e2e.Run)
                run.project = "this-run"
                run.compose = ["docker", "compose"]
                inspection = [{"Config": {"Labels": {
                    "com.docker.compose.project": project,
                    "com.docker.compose.service": service,
                }}, "HostConfig": {"Tmpfs": tmpfs}}]
                with patch.object(run, "capture", side_effect=["container", json.dumps(inspection)]), \
                        patch.object(editor_e2e.urllib.request, "urlopen") as http, \
                        patch.object(editor_e2e.subprocess, "run") as sql:
                    with self.assertRaisesRegex(RuntimeError, "Refusing fixture SQL"):
                        run.provision("http://127.0.0.1:12345")
                    http.assert_not_called()
                    sql.assert_not_called()

    def test_failed_stage_and_interrupt_still_cleanup_and_keep_artifacts(self):
        for error in (RuntimeError("browser failed"), KeyboardInterrupt()):
            with self.subTest(error=type(error).__name__), tempfile.TemporaryDirectory() as directory:
                with patch.object(editor_e2e, "ROOT", Path(directory)), \
                        patch.object(editor_e2e.Run, "execute", side_effect=error), \
                        patch.object(editor_e2e.Run, "cleanup", return_value=True) as cleanup, \
                        patch.object(editor_e2e.signal, "signal"):
                    self.assertEqual(editor_e2e.main(), 1)
                    cleanup.assert_called_once()
                results = list(Path(directory).glob("reports/editor-e2e/*/result.json"))
                self.assertEqual(len(results), 1)
                self.assertEqual(json.loads(results[0].read_text())["status"], "failed")

    def test_cleanup_error_makes_successful_scenarios_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(editor_e2e, "ROOT", Path(directory)), \
                    patch.object(editor_e2e.Run, "execute"), \
                    patch.object(editor_e2e.Run, "cleanup", return_value=False), \
                    patch.object(editor_e2e.signal, "signal"):
                self.assertEqual(editor_e2e.main(), 1)


if __name__ == "__main__":
    unittest.main()
