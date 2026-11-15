"""Offline contracts for V42 workflow, evidence and task-owned cleanup."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from xml.etree import ElementTree as XML

SCRIPT = Path(__file__).with_name("verify-foundation.py")
spec = importlib.util.spec_from_file_location("foundation", SCRIPT)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
SHA = "a" * 40


class SourceAndWorkflow(unittest.TestCase):
    def test_source_guard_accepts_exact_clean_manual_checkout(self):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": "workflow_dispatch",
               "V42_FOCUSED": "true", "V42_EXPECTED_SHA": SHA, "GITHUB_SHA": SHA}
        with patch.dict(os.environ, env, clear=True), patch.object(
            subprocess, "check_output", side_effect=[SHA + "\n", ""]
        ):
            self.assertEqual(runner.verify_v42_source(), SHA)

    def test_source_guard_rejects_unselected_other_event_or_wrong_sha(self):
        good = {"GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": "workflow_dispatch",
                "V42_FOCUSED": "true", "V42_EXPECTED_SHA": SHA, "GITHUB_SHA": SHA}
        for invalid in ({"V42_FOCUSED": "false"}, {"GITHUB_EVENT_NAME": "push"},
                        {"V42_EXPECTED_SHA": ""}, {"GITHUB_SHA": "b" * 40}):
            with self.subTest(invalid=invalid), patch.dict(os.environ, good | invalid, clear=True):
                with patch.object(runner, "docker_output") as docker, self.assertRaises(RuntimeError):
                    runner.v42_qualification()
                docker.assert_not_called()

    def test_source_guard_rejects_dirty_or_different_checkout(self):
        env = {"GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": "workflow_dispatch",
               "V42_FOCUSED": "true", "V42_EXPECTED_SHA": SHA, "GITHUB_SHA": SHA}
        for answers in ((SHA, " M path"), ("b" * 40, "")):
            with self.subTest(answers=answers), patch.dict(os.environ, env, clear=True):
                with patch.object(subprocess, "check_output", side_effect=answers), self.assertRaises(RuntimeError):
                    runner.verify_v42_source()

    def test_workflow_opt_in_and_disjoint_steps(self):
        text = (runner.ROOT / ".github/workflows/foundation.yml").read_text()
        self.assertIn("     default: false", text)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.v42_postgres", text)
        self.assertLess(text.index("--verify-v42-source"), text.index("--v42-postgres"))
        self.assertIn("always() && env.V42_FOCUSED == 'true'\n        run: python3 scripts/verify-foundation.py --cleanup-v42", text)
        for step in ("Backend build", "Frontend clean install", "Local launcher contracts"):
            self.assertIn("V42_FOCUSED != 'true'", text.split(f"- name: {step}", 1)[1].splitlines()[1])
        self.assertIn("scripts/test-governance-controls.py", text)
        self.assertIn("persist-credentials: false", text)

    def test_exact_maven_selector_and_container_policy(self):
        cmd = runner.v42_maven_command(Path("/tmp/example-cache"))
        self.assertEqual([arg for arg in cmd if arg.startswith("-Dtest=")], [
            "-Dtest=com.cinenest.pricing.internal.OperationalCommercialPostgresTest"
            "#cleanMigrationPreservesDefaultDenialAndRejectsRevocationRollback"
            "+populatedV41UpgradePreservesHistoryAndFlywayChecksums"
        ])
        self.assertEqual(cmd[-2:], ["clean", "test"])
        docker = runner.database_command("task", "V42-POSTGRES-CI", "owner")
        self.assertEqual(docker[-1], runner.IMAGE)
        for arg in ("127.0.0.1::5432", "POSTGRES_PASSWORD", "128", "16m", "1g",
                    "/var/lib/postgresql:rw,size=496m"):
            self.assertIn(arg, docker)
        self.assertNotIn("--cpus", runner.database_command("normal", "CN-FND-001"))


class Evidence(unittest.TestCase):
    def report(self, cases, suite_name=None, totals=None):
        with tempfile.TemporaryDirectory() as directory:
            suite_name = suite_name or runner.V42_CLASS
            totals = totals or [len(cases), sum(tag == "failure" for _, tag in cases),
                                sum(tag == "error" for _, tag in cases),
                                sum(tag == "skipped" for _, tag in cases)]
            suite = XML.Element("testsuite", name=suite_name, attrib=dict(
                zip(("tests", "failures", "errors", "skipped"), map(str, totals))))
            for method, tag in cases:
                case = XML.SubElement(suite, "testcase", name=method, classname=suite_name)
                if tag:
                    XML.SubElement(case, tag)
            XML.ElementTree(suite).write(Path(directory) / "TEST-example.xml")
            return runner.v42_results(Path(directory))

    def test_exact_two_passes(self):
        cases, unexpected = self.report([(m, None) for m in runner.V42_METHODS])
        self.assertEqual(list(cases.values()), ["PASS", "PASS"])
        self.assertFalse(unexpected)

    def test_distinguishes_failure_error_and_skip(self):
        for tag, status in (("failure", "FAIL"), ("error", "ERROR"), ("skipped", "SKIPPED")):
            with self.subTest(tag=tag):
                cases, _ = self.report([(runner.V42_METHODS[0], tag), (runner.V42_METHODS[1], None)])
                self.assertEqual(list(cases.values()), [status, "PASS"])

    def test_missing_extra_duplicate_and_wrong_suite_are_detected(self):
        first, second = runner.V42_METHODS
        cases, _ = self.report([(first, None)])
        self.assertEqual(cases[second], "NOT_RUN")
        for bad in ([(first, None), (second, None), ("extra", None)], [(first, None), (first, None)]):
            self.assertTrue(self.report(bad)[1])
        self.assertTrue(self.report([(first, None)], suite_name="UnexpectedTest")[1])

    def test_rejects_inconsistent_totals_and_malformed_xml(self):
        self.assertTrue(self.report([(m, None) for m in runner.V42_METHODS], totals=[5, 0, 0, 0])[1])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-malformed.xml").write_text("<testsuite")
            with self.assertRaises(XML.ParseError):
                runner.v42_results(root)


class Lifecycle(unittest.TestCase):
    def test_container_policy_checks_actual_inspect(self):
        container = {"HostConfig": runner.V42_LIMITS.copy(),
                     "NetworkSettings": {"Ports": {"5432/tcp": [{"HostIp": "127.0.0.1", "HostPort": "9000"}]}}}
        self.assertEqual(runner.v42_port(container), 9000)
        container["HostConfig"]["ShmSize"] *= 2
        with self.assertRaises(RuntimeError):
            runner.v42_port(container)
        container["HostConfig"]["ShmSize"] //= 2
        container["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostIp"] = "0.0.0.0"
        with self.assertRaises(RuntimeError):
            runner.v42_port(container)

    def test_cleanup_checks_owned_container_port_and_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            marker = root / "task.json"
            reports = root / "target/surefire-reports"
            reports.mkdir(parents=True)
            marker.with_suffix(".scratch").mkdir()
            runner.write_v42_state(marker, {"owner": "a" * 32, "port": 9000, "before": ["unrelated"]})
            with (
                patch.object(runner, "BACKEND", root),
                patch.object(runner, "v42_scope", return_value=("task", marker)),
                patch.object(runner, "owned_container", side_effect=[{"Id": "owned"}, None]),
                patch.object(runner, "docker_output") as docker,
                patch.object(runner, "inventory", return_value={"unrelated"}),
                patch.object(runner.socket, "create_connection", side_effect=ConnectionRefusedError),
            ):
                runner.cleanup_v42()
            docker.assert_called_once_with("rm", "-f", "-v", "owned")
            self.assertFalse(marker.exists())
            self.assertFalse(reports.exists())
            self.assertFalse(marker.with_suffix(".scratch").exists())

    def test_cleanup_refuses_other_owner(self):
        output = [{"Id": "foreign", "Name": "/task", "Config": {"Labels": {runner.V42_OWNER_LABEL: "other"}}}]
        with patch.object(runner, "docker_output", side_effect=["foreign", json.dumps(output)]) as docker:
            with self.assertRaisesRegex(RuntimeError, "unowned"):
                runner.owned_container("task", "expected")
        self.assertEqual(docker.call_count, 2)

    def test_cleanup_runs_if_provisioning_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "task.json"
            with (
                patch.object(runner, "BACKEND", Path(directory)),
                patch.object(runner, "verify_v42_source", return_value=SHA),
                patch.object(runner, "v42_scope", return_value=("task", marker)),
                patch.object(runner, "docker_output", return_value=""),
                patch.object(runner, "inventory", return_value=set()),
                patch.object(runner, "run", side_effect=subprocess.CalledProcessError(1, "docker")),
                patch.object(runner, "cleanup_v42") as cleanup,
            ):
                with self.assertRaises(subprocess.CalledProcessError):
                    runner.v42_qualification()
                cleanup.assert_called_once_with()

    def test_timeout_terminates_test_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            process = MagicMock(pid=123)
            process.wait.side_effect = [subprocess.TimeoutExpired("mvn", 1), -9]
            with (
                patch.object(runner.subprocess, "Popen", return_value=process),
                patch.object(runner.os, "killpg", side_effect=[None, ProcessLookupError]) as kill,
                self.assertRaises(subprocess.TimeoutExpired),
            ):
                runner.run_v42_maven({}, Path(directory), 1)
            kill.assert_any_call(123, runner.signal.SIGKILL)


if __name__ == "__main__":
    unittest.main()
