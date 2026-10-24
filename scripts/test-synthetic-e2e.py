"""Inert admission/cleanup negative controls; never starts Docker, Java or a browser."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('journey', Path(__file__).with_name('verify-synthetic-e2e.py'))
journey = importlib.util.module_from_spec(spec)
spec.loader.exec_module(journey)


class AdmissionTests(unittest.TestCase):
    def test_aggregate_application_limits(self):
        self.assertTrue(journey.limits('300000 100000', str(7*1024**3)))
        for cpu, memory in [('max 100000', '1'), ('300001 100000', '1'), ('1 100000', 'max'),
                            ('1 100000', str(7*1024**3+1)), ('0 100000', '1')]:
            with self.subTest(cpu=cpu, memory=memory):
                self.assertFalse(journey.limits(cpu, memory))

    def test_reject_shared_and_symlink_cleanup_roots(self):
        with self.assertRaises(RuntimeError):
            journey.private_root(Path(tempfile.gettempdir()))
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name:
            root=Path(name)
            link=root/'link';link.symlink_to(root, target_is_directory=True)
            with self.assertRaises(RuntimeError): journey.private_root(link)
            os.chmod(root,0o755)
            with self.assertRaises(RuntimeError): journey.private_root(root)

    def test_cleanup_never_stops_foreign_container(self):
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name:
            root=Path(name);(root/'ownership.json').write_text(json.dumps({'name':root.name}))
            result=subprocess.CompletedProcess([],0,json.dumps([{'Config':{'Labels':{journey.LABEL:'foreign'}},'Id':'foreign'}]),'')
            with patch.object(journey.subprocess,'run',return_value=result) as run:
                with self.assertRaises(RuntimeError):journey.cleanup(root)
                self.assertEqual(run.call_count,1)
                self.assertTrue(root.exists())

    def test_daemon_failure_is_not_container_absence(self):
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name:
            root=Path(name);(root/'ownership.json').write_text(json.dumps({'name':root.name}))
            result=subprocess.CompletedProcess([],1,'','Cannot connect to daemon')
            with patch.object(journey.subprocess,'run',return_value=result):
                with self.assertRaises(RuntimeError):journey.cleanup(root)
                self.assertTrue(root.exists())

    def test_failed_budget_gate_cannot_provision(self):
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name:
            with patch.object(journey,'runtime_limits',side_effect=RuntimeError('unlimited')), patch.object(journey.subprocess,'run') as run:
                with self.assertRaises(RuntimeError):journey.inside(Path(name))
                run.assert_not_called()

    def test_all_scenario_outcomes_are_enumerated(self):
        source=(journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()
        self.assertIn('for (const id of planned)', source)
        self.assertIn('results.some', source)
        self.assertIn('Stopped after failure or missing independent safe scenario control', source)

    def test_concurrent_owner_file_change_is_not_overwritten(self):
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name, tempfile.TemporaryDirectory() as repo:
            root=Path(name);(root/'ownership.json').write_text(json.dumps({'name':root.name}))
            (root/'next-env.original').write_bytes(b'original')
            target=Path(repo)/'frontend/next-env.d.ts';target.parent.mkdir();target.write_bytes(b'concurrent owner change')
            missing=subprocess.CompletedProcess([],1,'','No such container')
            with patch.object(journey,'ROOT',Path(repo)),patch.object(journey.subprocess,'run',return_value=missing):
                with self.assertRaises(RuntimeError):journey.cleanup(root)
                self.assertEqual(target.read_bytes(),b'concurrent owner change')
                self.assertTrue((root/'next-env.original').exists())

    def test_no_business_interception_or_cookie_injection(self):
        source=(journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()+(journey.ROOT/'frontend/tests/extension-reservation-e2e.mjs').read_text()
        for forbidden in ['page.route(', 'route.fulfill(', 'context.addCookies(', 'storageState:', 'ignoreHTTPSErrors:']:
            self.assertNotIn(forbidden,source)

    def test_diagnostics_never_capture_raw_browser_secrets(self):
        source=(journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()
        for forbidden in ['tracing.start(', 'tracing.stop(', 'page.screenshot(', 'failure.message).slice']:
            self.assertNotIn(forbidden,source)
        self.assertIn('queryKeys:',source)
        self.assertIn('expectedNegative',source)
        self.assertIn('safeCustomerNavigation(u.href, base)',source)

    def test_workflow_untouched_and_offline_runtime(self):
        source=Path(journey.__file__).read_text()
        self.assertIn("'--pull=never'",source)
        self.assertIn("'-o', '-B'",source)
        self.assertNotIn("'npm', 'install'",source)
        self.assertIn("'--property=KillMode=control-group'",source)
        self.assertIn("'--property=RuntimeMaxSec=600'",source)


if __name__=='__main__':unittest.main()
