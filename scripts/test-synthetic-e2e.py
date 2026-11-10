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
        source=(journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()+(journey.ROOT/'frontend/tests/extension-reservation-e2e.mjs').read_text()+(journey.ROOT/'frontend/tests/release1-booking-e2e.mjs').read_text()+(journey.ROOT/'frontend/tests/release1-recovery-e2e.mjs').read_text()
        for forbidden in ['page.route(', 'route.fulfill(', 'context.addCookies(', 'storageState:', 'ignoreHTTPSErrors:']:
            self.assertNotIn(forbidden,source)

    def test_diagnostics_never_capture_raw_browser_secrets(self):
        source=(journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()
        for forbidden in ['tracing.start(', 'tracing.stop(', 'page.screenshot(', 'failure.message).slice']:
            self.assertNotIn(forbidden,source)
        self.assertIn('queryKeys:',source)
        self.assertIn('expectedNegative',source)
        self.assertIn('safeCustomerNavigation(u.href, base)',source)

    def test_bank_instruction_scenario_is_explicit_and_bounded(self):
        self.assertEqual(journey.journey_test('campaign'),'SyntheticJourneyIT')
        self.assertEqual(journey.journey_test('h1'),'SyntheticJourneyIT')
        self.assertEqual(journey.journey_test('bank-instructions'),'BankInstructionsJourneyIT')
        with self.assertRaises(RuntimeError):journey.journey_test('arbitrary')
        source=(journey.ROOT/'frontend/tests/bank-instructions-e2e.mjs').read_text()
        for forbidden in ['page.route(', 'route.fulfill(', 'addCookies(', 'storageState:']:
            self.assertNotIn(forbidden,source)

    def test_h1_uses_direct_transport_and_crops_allowlisted_labels(self):
        source = (journey.ROOT/'frontend/tests/synthetic-e2e.mjs').read_text()
        self.assertIn('h1 ? null : await uploadFaultProxy(config.backend)', source)
        self.assertIn('faultTransport?.origin ?? config.backend', source)
        self.assertIn('h1 && Object.hasOwn(targets, name)', source)
        self.assertIn('element.textContent.trim() === expected', source)
        self.assertIn('element.childElementCount === 0', source)
        self.assertIn('target.screenshot(', source)
        self.assertIn('mode: 0o600, flag: "wx"', source)
        self.assertIn('createHash("sha256")', source)

    def test_h1_reload_uses_the_owned_detail_control(self):
        frontend = journey.ROOT / 'frontend'
        component = (frontend / 'components/bank-request-detail.tsx').read_text()
        self.assertRegex(component, r'<Button busy=\{busy\} variant="secondary" onClick=\{\(\) => void load\(\)\}>\s+Reload current request\s+</Button>')
        self.assertIn('loadBankRequest(reference, controller.signal)', component)
        source = (frontend / 'tests/synthetic-e2e.mjs').read_text()
        helper = source[source.index('async function reloadH1BankRequest('):
                        source.index('async function login(')]
        self.assertIn('const current = await reloadH1BankRequest(page, bank.id);', source)
        subprocess.run(['node', '--input-type=module', '-'], input=
            'import assert from "node:assert/strict";\n' + helper + r"""
const path = "/api/bank/requests/owned";
async function check(count, visible, enabled, responseId = "owned", status = 200) {
    let clicks = 0;
    const page = {
        getByRole(role, options) {
            assert.equal(role, "region");
            assert.deepEqual(options, {name: "Owned bank request detail", exact: true});
            return {getByRole(role, options) {
                if (role === "region") {
                    assert.deepEqual(options, {name: "Current bank request summary", exact: true});
                    return {waitFor: async options => assert.deepEqual(options, {state: "visible"})};
                }
                assert.equal(role, "button");
                assert.deepEqual(options, {name: "Reload current request", exact: true});
                return {count: async () => count, isVisible: async () => visible,
                    isEnabled: async () => enabled, click: async () => { clicks++; }};
            }};
        },
        async waitForResponse(match) {
            const response = (pathname, method) => ({
                url: () => "http://127.0.0.1" + pathname, request: () => ({method: () => method}),
                status: () => status, json: async () => ({id: responseId}),
            });
            assert.equal(clicks, 0);
            assert.ok(!match(response(path + "-other", "GET")));
            assert.ok(!match(response(path, "POST")));
            assert.ok(match(response(path, "GET")));
            return response(path, "GET");
        },
    };
    if (count !== 1 || !visible || !enabled) {
        await assert.rejects(reloadH1BankRequest(page, "owned"), /reload button/);
        assert.equal(clicks, 0);
    } else {
        if (responseId !== "owned" || status !== 200) {
            await assert.rejects(reloadH1BankRequest(page, "owned"), assert.AssertionError);
        } else {
            assert.deepEqual(await reloadH1BankRequest(page, "owned"), {id: "owned"});
        }
        assert.equal(clicks, 1);
    }
}
await check(0, true, true);
await check(2, true, true);
await check(1, false, true);
await check(1, true, false);
await check(1, true, true, "other");
await check(1, true, true, "owned", 403);
await check(1, true, true);
""", text=True, check=True)

    def test_h1_opaque_evidence_route_requires_exact_owned_reference(self):
        frontend = journey.ROOT / 'frontend'
        source = (frontend / 'tests/synthetic-e2e.mjs').read_text()
        helper = source[source.index('function ownedH1EvidenceUrl('):
                        source.index('async function login(')]
        nav = (frontend / 'tests/safe-navigation.mjs').as_uri()
        subprocess.run(['node', '--input-type=module', '-'], input=
            'import assert from "node:assert/strict";\n'
            + f'import {{ safeCustomerNavigation }} from {json.dumps(nav)};\n'
            + helper + r"""
const base = "http://127.0.0.1:4443";
const id = "11111111-2222-4333-8444-555555555555";
const other = "11111111-2222-4333-8444-555555555556";
const href = "/bank?evidence=" + id;
assert.equal(ownedH1EvidenceUrl(href, base, id), base + href);
for (const invalid of [null, "/bank?evidence=malformed", "/bank?evidence=" + other,
    "/bank?request=" + id, href + "&evidence=" + id, href + "&token=secret",
    href + "#fragment", "https://outside.invalid" + href]) {
    assert.throws(() => ownedH1EvidenceUrl(invalid, base, id));
}
""", text=True, check=True)
        start = source.index('await scenario("P-safe-URLs"')
        expiry = source.index('await scenario("G-expired-session"', start)
        positive = source[start:source.index('await scenario("A-expired-quote"', start)]
        self.assertIn('link.click()', positive)
        self.assertIn('assert.deepEqual(owned.evidence, []);', positive)
        self.assertIn('navigations.some((n) => n.queryKeys.includes("evidence") && n.safe)', positive)
        self.assertIn('navigations.some((n) => n.expectedNegative && !n.safe)', positive)
        expired = source[expiry:source.index('await expiredSessionRecovery(', expiry)]
        self.assertIn('control({op:"expire-session"})', expired)
        self.assertIn('expired.sessionDeadlineExpired,true', expired)
        self.assertIn('"/api/accounts/me")).status, 401', expired)
        self.assertIn('`/api/bank/requests/${bank.id}`)).status,401', expired)
        self.assertIn('if (!h1) {\n\tawait scenario("A-expired-quote"', source[start:expiry])

    def test_postgres_budget_rejects_mismatched_live_configuration(self):
        host = dict(NanoCpus=10**9, Memory=1024**3, MemorySwap=1024**3,
                    PidsLimit=128, ShmSize=64*1024**2,
                    Tmpfs={'/var/lib/postgresql': 'rw,size=496m'})
        with tempfile.TemporaryDirectory(prefix='cinenest-e2e-') as name:
            root = Path(name)
            (root/'ownership.json').write_text(json.dumps({'name':root.name,'scenario':'h1'}))
            with patch.object(journey, 'runtime_limits'), patch.object(
                journey, 'call', side_effect=['owned',json.dumps([{'HostConfig':host}])]
            ) as call:
                with self.assertRaisesRegex(RuntimeError, 'Database limits not effective'):
                    journey.inside(root)
                args = call.call_args_list[0].args[0]
                self.assertIn('--shm-size=16m', args)
                self.assertIn('/var/lib/postgresql:rw,size=496m', args)
                self.assertEqual(call.call_args_list[0].kwargs['env']['CINENEST_E2E_MODE'], 'h1')
                self.assertEqual(call.call_count, 2)

    def test_workflow_untouched_and_offline_runtime(self):
        source=Path(journey.__file__).read_text()
        self.assertIn("'--pull=never'",source)
        self.assertIn("'-o', '-B'",source)
        self.assertNotIn("'npm', 'install'",source)
        self.assertIn("'--property=KillMode=control-group'",source)
        self.assertIn("'--property=RuntimeMaxSec=600'",source)


if __name__=='__main__':unittest.main()
