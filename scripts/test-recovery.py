"""Pure synthetic-model tests. All complete inputs are HYPOTHETICAL, not approvals."""
import ast
from dataclasses import FrozenInstanceError, replace
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('inert_recovery', ROOT / 'scripts/verify-recovery.py')
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)
A, E, R = m.Approval, m.Evidence, m.Reason
RUN = 'HYPOTHETICAL-RUN'
OWNER = 'HYPOTHETICAL-OWNER'


def resource(identity, category=m.Category.WORKLOAD, **changes):
    return replace(m.Resource(RUN, category, identity, OWNER, E.VERIFIED,
                              m.Lifecycle.DECLARED, m.Eligibility.ELIGIBLE, E.MISSING), **changes)


def hypothetical():
    ledger = m.Ledger(RUN, OWNER)
    for identity in ('controller-ref', 'agent-ref', 'probe-ref'):
        ledger, check = ledger.declare(resource(identity))
        assert check.model_valid
    ledger, check = ledger.declare(resource('listener-ref', m.Category.SOCKET))
    assert check.model_valid
    limits = m.Limits(3, A.APPROVED, 0, A.APPROVED, 3, 3, 60, 15, 45, 600,
                      10*1024*1024, 50*1024*1024)
    config = m.Preflight(A.APPROVED, RUN, frozenset(), True, 'HYPOTHETICAL-RUNTIME',
                         A.APPROVED, E.VERIFIED, A.APPROVED, E.VERIFIED,
                         'sha256:' + 'a'*64, limits, E.VERIFIED, E.VERIFIED,
                         E.VERIFIED, E.VERIFIED, E.VERIFIED, E.VERIFIED)
    accounting = m.Accounting((('controller-ref', 'controller'), ('agent-ref', 'agent'),
                               ('probe-ref', 'probe')), (), (), 3, 3, ())
    return config, accounting, ledger


class Preflight(unittest.TestCase):
    def setUp(self):
        self.config, self.accounting, self.ledger = hypothetical()

    def evaluate(self, config=None, accounting=None, ledger=None):
        return m.evaluate(config if config is not None else self.config,
                          accounting if accounting is not None else self.accounting,
                          ledger if ledger is not None else self.ledger)

    def test_hypothetical_complete_model_never_authorizes_operation(self):
        check = self.evaluate()
        self.assertTrue(check.model_valid)
        self.assertFalse(check.operationally_authorized)
        with self.assertRaises(FrozenInstanceError): self.config.run_id = 'changed'
        with self.assertRaises(TypeError): m.Preflight()

    def test_refusal_matrix_config_A_to_Y(self):
        cases = [
            ('A', 'authorization', A.PENDING, R.AUTHORIZATION),
            ('B', 'run_id', '', R.RUN),
            ('C', 'synthetic_only', False, R.SYNTHETIC),
            ('D', 'runtime_qualification', E.DECLARED, R.RUNTIME_QUALIFICATION),
            ('E', 'image_digest', None, R.DIGEST),
            ('F', 'image_approval', A.PENDING, R.IMAGE),
            ('L', 'socket_ownership', E.MISSING, R.SOCKET),
            ('O', 'cleanup_requirements', E.MISSING, R.CLEANUP_VERIFICATION),
            ('U', 'network_isolation', E.MISSING, R.NETWORK),
            ('V', 'filesystem_isolation', E.DECLARED, R.FILESYSTEM),
            ('W', 'security_qualification', E.DECLARED, R.SECURITY),
            ('Y', 'independent_evidence', E.MISSING, R.INDEPENDENT),
            ('runtime selection', 'runtime', None, R.RUNTIME),
            ('runtime approval', 'runtime_approval', A.PENDING, R.RUNTIME),
            ('image qualification', 'image_qualification', E.MISSING, R.IMAGE),
            ('reused identity', 'used_run_ids', frozenset({RUN}), R.RUN_REUSED),
        ]
        for label, field, value, reason in cases:
            with self.subTest(case=label):
                check = self.evaluate(replace(self.config, **{field: value}))
                self.assertIn(reason, check.reasons)
                self.assertFalse(check.model_valid)
                self.assertFalse(check.operationally_authorized)

    def test_budget_refusals_G_H_P_Q_R_S_T(self):
        for field, value, reason in [
            ('helper_approval', A.PENDING, R.HELPER_BUDGET),
            ('helper_ceiling', None, R.HELPER_BUDGET),
            ('task_approval', A.PENDING, R.THREAD_TASK),
            ('thread_ceiling', None, R.THREAD_TASK),
            ('task_ceiling', None, R.THREAD_TASK),
            ('agent_seconds', 61, R.AGENT_TIME), ('shutdown_reserve', 0, R.AGENT_TIME),
            ('probe_seconds', 46, R.PROBE_TIME), ('probe_seconds', 61, R.PROBE_TIME),
            ('cumulative_seconds', 601, R.CUMULATIVE),
            ('synthetic_bytes', 10*1024*1024+1, R.DATA),
            ('temporary_bytes', 50*1024*1024+1, R.STORAGE),
            ('workload', 4, R.WORKLOAD), ('workload', True, R.WORKLOAD),
        ]:
            with self.subTest(field=field, value=value):
                limits = replace(self.config.limits, **{field: value})
                self.assertIn(reason, self.evaluate(replace(self.config, limits=limits)).reasons)

    def test_current_unresolved_baseline_refuses(self):
        # Faithfully represent pending gates; no invented real helper/task allowance.
        limits = replace(self.config.limits, helper_approval=A.PENDING, helper_ceiling=None,
                         task_approval=A.PENDING, thread_ceiling=None, task_ceiling=None)
        config = replace(self.config, authorization=A.PENDING, runtime_qualification=E.MISSING,
                         image_approval=A.PENDING, image_qualification=E.MISSING, image_digest=None,
                         limits=limits, socket_ownership=E.MISSING, cleanup_requirements=E.MISSING,
                         security_qualification=E.MISSING, independent_evidence=E.MISSING)
        self.assertTrue({R.AUTHORIZATION, R.RUNTIME_QUALIFICATION, R.IMAGE, R.DIGEST,
                         R.HELPER_BUDGET, R.THREAD_TASK, R.SOCKET, R.CLEANUP_VERIFICATION,
                         R.SECURITY, R.INDEPENDENT} <= set(self.evaluate(config).reasons))

    def test_accounting_I_J_and_missing_process_ownership(self):
        cases = [
            (replace(self.accounting, workloads=self.accounting.workloads+(('fourth', 'helper'),)), R.WORKLOAD),
            (replace(self.accounting, unexpected=('unclassified-child',)), R.UNEXPECTED),
            (replace(self.accounting, helpers=('unknown-helper',)), R.OWNERSHIP),
            (replace(self.accounting, workloads=(('missing', 'controller'), ('agent-ref', 'agent'))), R.OWNERSHIP),
            (replace(self.accounting, threads=None), R.THREAD_TASK),
            (replace(self.accounting, tasks=2), R.THREAD_TASK),
            (replace(self.accounting, workloads=(('controller-ref', 'controller'), ('agent-ref', 'controller'))), R.WORKLOAD),
        ]
        for accounting, reason in cases:
            with self.subTest(accounting=accounting): self.assertIn(reason, self.evaluate(accounting=accounting).reasons)
        ledger, _ = self.ledger.declare(resource('hidden-helper', m.Category.HELPER))
        self.assertIn(R.UNEXPECTED, self.evaluate(ledger=ledger).reasons)

    def test_missing_socket_provenance_and_cleanup_N(self):
        ledger = replace(self.ledger, entries=self.ledger.entries[:-1])
        self.assertIn(R.SOCKET, self.evaluate(ledger=ledger).reasons)
        for field, value, reason in [('provenance', E.DECLARED, R.PROVENANCE),
                                     ('cleanup', m.Eligibility.UNKNOWN, R.CLEANUP)]:
            entries = (replace(self.ledger.entries[0], **{field:value}),) + self.ledger.entries[1:]
            self.assertIn(reason, self.evaluate(ledger=replace(self.ledger, entries=entries)).reasons)

    def test_invalid_input_types_fail_closed(self):
        for config in (None, {}, 'APPROVED'):
            self.assertEqual(m.evaluate(config, self.accounting, self.ledger).reasons, (R.INPUT,))
        for field, value in [('limits', None), ('used_run_ids', []), ('authorization', 'APPROVED')]:
            self.assertFalse(self.evaluate(replace(self.config, **{field:value})).model_valid)
        for field in ['agent_seconds', 'probe_seconds', 'cumulative_seconds', 'synthetic_bytes', 'temporary_bytes']:
            for value in [None, True, -1, 0, float('nan'), '60']:
                with self.subTest(field=field,value=value):
                    self.assertFalse(self.evaluate(replace(self.config,limits=replace(self.config.limits, **{field:value}))).model_valid)
        for digest in ['', 'latest', 'sha256:'+'A'*64, 'sha256:'+'a'*63]:
            self.assertIn(R.DIGEST,self.evaluate(replace(self.config,image_digest=digest)).reasons)


class Ledger(unittest.TestCase):
    def test_K_X_ownership_conflicts_and_cross_run(self):
        ledger = m.Ledger(RUN, OWNER)
        for entry, reason in [(resource('x', owner=''), R.OWNERSHIP),
                              (resource('x', owner='other'), R.OWNERSHIP),
                              (resource('x', run_id='other-run'), R.CROSS_RUN),
                              (resource(''), R.OWNERSHIP)]:
            new, check = ledger.declare(entry)
            self.assertIs(new, ledger)
            self.assertEqual(check.reasons, (reason,))
        ledger, _ = ledger.declare(resource('x'))
        unchanged, check = ledger.declare(resource('x', m.Category.SOCKET))
        self.assertIs(unchanged,ledger)
        self.assertEqual(check.reasons,(R.CONFLICT,))
        with self.assertRaises(ValueError): m.Ledger(RUN,OWNER,(resource('x'),resource('x')))

    def test_M_O_lifecycle_and_cleanup_order(self):
        empty = m.Ledger(RUN, OWNER)
        ledger, _ = empty.declare(resource('x'))
        self.assertEqual(len(empty.entries),0)
        unchanged, check = ledger.transition('x',m.Lifecycle.DISPOSED,E.VERIFIED)
        self.assertIs(unchanged,ledger);self.assertEqual(check.reasons,(R.TRANSITION,))
        ledger, check = ledger.transition('x',m.Lifecycle.ACTIVE,E.MISSING)
        self.assertTrue(check.model_valid)
        self.assertIn(R.TRANSITION,ledger.cleanup_check('x').reasons)
        ledger, _ = ledger.transition('x',m.Lifecycle.STOPPED,E.MISSING)
        self.assertEqual(ledger.cleanup_check('x').reasons,(R.CLEANUP_VERIFICATION,))
        self.assertEqual(ledger.transition('x',m.Lifecycle.DISPOSED,E.VERIFIED)[1].reasons,(R.CLEANUP_VERIFICATION,))
        # A separate hypothetical STOPPED receipt is required before disposal declaration.
        ledger = replace(ledger,entries=(replace(ledger.entries[0],cleanup_verification=E.VERIFIED),))
        self.assertTrue(ledger.cleanup_check('x').model_valid)
        ledger, check = ledger.transition('x',m.Lifecycle.DISPOSED,E.VERIFIED)
        self.assertTrue(check.model_valid)
        self.assertEqual(ledger.transition('x',m.Lifecycle.ACTIVE,E.MISSING)[1].reasons,(R.TRANSITION,))
        self.assertEqual(ledger.cleanup_check('unknown').reasons,(R.OWNERSHIP,))

    def test_all_categories_and_shared_infrastructure_protection(self):
        ledger = m.Ledger(RUN,OWNER)
        for category in m.Category:
            entry = resource(category.value,category)
            if category is m.Category.SHARED:
                entry = replace(entry,owner='SHARED-OWNER',cleanup=m.Eligibility.PROHIBITED)
            ledger, check = ledger.declare(entry)
            self.assertTrue(check.model_valid)
        self.assertEqual(len(ledger.entries),len(m.Category))
        self.assertIn(R.CLEANUP,ledger.cleanup_check(m.Category.SHARED.value).reasons)
        self.assertEqual(ledger.declare(resource('unsafe-shared',m.Category.SHARED))[1].reasons,(R.CLEANUP,))

    def test_filesystem_cleanup_waits_for_all_processes_and_socket_receipts(self):
        ledger=m.Ledger(RUN,OWNER)
        for entry in [resource('agent'),resource('socket',m.Category.SOCKET),resource('file',m.Category.FILE)]:
            ledger,_=ledger.declare(entry)
        for identity in ['socket','file']:
            ledger,_=ledger.transition(identity,m.Lifecycle.ACTIVE,E.MISSING)
            ledger,_=ledger.transition(identity,m.Lifecycle.STOPPED,E.VERIFIED)
        self.assertIn(R.TRANSITION,ledger.cleanup_check('socket').reasons)
        self.assertIn(R.TRANSITION,ledger.cleanup_check('file').reasons)
        ledger,_=ledger.transition('agent',m.Lifecycle.ACTIVE,E.MISSING)
        ledger,_=ledger.transition('agent',m.Lifecycle.STOPPED,E.VERIFIED)
        self.assertTrue(ledger.cleanup_check('socket').model_valid)
        self.assertTrue(ledger.cleanup_check('file').model_valid)
        entries=tuple(replace(x,cleanup_verification=E.MISSING) if x.identity=='socket' else x for x in ledger.entries)
        self.assertIn(R.CLEANUP_VERIFICATION,replace(ledger,entries=entries).cleanup_check('file').reasons)

    def test_constructor_and_enum_integrity(self):
        with self.assertRaises(ValueError):m.Ledger('',OWNER)
        with self.assertRaises(ValueError):m.Ledger(RUN,OWNER,[])
        ledger=m.Ledger(RUN,OWNER)
        self.assertEqual(ledger.declare(resource('x',category='WORKLOAD'))[1].reasons,(R.INPUT,))
        self.assertEqual(ledger.declare(resource('x',lifecycle=m.Lifecycle.ACTIVE))[1].reasons,(R.TRANSITION,))


class Architecture(unittest.TestCase):
    def test_no_operational_surface_or_module_execution(self):
        tree=ast.parse((ROOT/'scripts/verify-recovery.py').read_text())
        self.assertTrue(all(isinstance(n,(ast.Import,ast.ImportFrom,ast.FunctionDef,ast.ClassDef,ast.Expr)) for n in tree.body))
        for n in ast.walk(tree):
            if isinstance(n,ast.Import):self.assertTrue(all(x.name=='re' for x in n.names))
            if isinstance(n,ast.ImportFrom):self.assertIn(n.module,{'dataclasses','enum'})
            if isinstance(n,ast.Call) and isinstance(n.func,ast.Name):
                self.assertNotIn(n.func.id,{'open','exec','eval','compile','__import__','input'})
        self.assertFalse(hasattr(m,'main'))
        self.assertFalse(m.Result(()).operationally_authorized)


if __name__ == '__main__':unittest.main()
