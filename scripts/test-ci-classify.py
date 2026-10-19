"""Deterministic classifier and workflow contracts; no external services."""
import ast
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ci_classify', ROOT / 'scripts/ci-classify.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class Classification(unittest.TestCase):
    def check(self, expected, *paths):
        self.assertEqual(m.classify([('M', (p,)) for p in paths]), expected)

    def test_execution_matrix(self):
        cases = [
            (m.Category.INERT, ['scripts/verify-recovery.py']),
            (m.Category.INERT, ['scripts/test-recovery.py', 'knowledge/PROGRESS.md', 'knowledge/manifest.json']),
            (m.Category.APPLICATION, ['frontend/app/page.tsx']),
            (m.Category.MIXED, ['scripts/verify-recovery.py', 'backend/pom.xml']),
            (m.Category.UNKNOWN, ['unexpected.txt']),
            (m.Category.UNKNOWN, ['knowledge/PROGRESS.md']),
            (m.Category.GOVERNANCE, ['.github/workflows/foundation.yml']),
            (m.Category.GOVERNANCE, ['scripts/ci-classify.py']),
            (m.Category.GOVERNANCE, ['scripts/test-ci-classify.py']),
            (m.Category.GOVERNANCE, ['knowledge/execution/validate_execution.py']),
            (m.Category.APPLICATION, ['frontend/package-lock.json']),
            (m.Category.APPLICATION, ['backend/src/main/resources/db/migration/V18.sql']),
            (m.Category.APPLICATION, ['backend/src/main/resources/application.yml']),
            (m.Category.APPLICATION, ['scripts/verify-foundation.py']),
            (m.Category.UNKNOWN, ['scripts/verify-recovery.py', 'unrecognized']),
        ]
        for category, paths in cases:
            with self.subTest(paths=paths): self.check(category, *paths)

    def test_statuses_and_paths(self):
        for status, group in [('D', ('scripts/verify-recovery.py',)),
                              ('R100', ('backend/a', 'scripts/verify-recovery.py')),
                              ('T', ('scripts/test-recovery.py',)),
                              ('M', ('../scripts/test-recovery.py',)), ('M', ('',))]:
            with self.subTest(status=status, group=group):
                self.assertEqual(m.classify([(status, group)]), m.Category.UNKNOWN)
        self.assertEqual(m.classify([('A', ('scripts/verify-recovery.py',))]), m.Category.INERT)
        self.assertEqual(m.classify([]), m.Category.UNKNOWN)

    def test_nul_parser_and_rename(self):
        self.assertEqual(m.parse_diff(b'M\0file with\nnewline\0'), [('M', ('file with\nnewline',))])
        self.assertEqual(m.classify(m.parse_diff(b'R100\0old\0new\0')), m.Category.UNKNOWN)
        for raw in [b'M\0', b'M\0file', b'garbage\0x\0', b'M\0\xff\0']:
            with self.assertRaises((ValueError, UnicodeError)): m.parse_diff(raw)

    def test_missing_base_and_git_failure(self):
        self.assertEqual(m.inspect(ROOT, '', 'a'*40), m.Category.UNKNOWN)
        with patch.object(m.subprocess, 'check_output', side_effect=OSError):
            self.assertEqual(m.inspect(ROOT, 'a'*40, 'b'*40), m.Category.UNKNOWN)

    def test_actual_diff_contract(self):
        # Verify exact base/head passed to Git and classification of its NUL output.
        with patch.object(m.subprocess, 'check_output', side_effect=[b'a'*40+b'\n', b'b'*40+b'\n', b'M\0scripts/verify-recovery.py\0', b'100644 blob hash\tscripts/verify-recovery.py\0']) as git:
            self.assertEqual(m.inspect(ROOT, 'a'*40, 'b'*40), m.Category.INERT)
            self.assertEqual(git.call_args_list[2].args[0][-3:], ['a'*40, 'b'*40, '--'])

    def test_symlink_rejected(self):
        with patch.object(m.subprocess, 'check_output', side_effect=[b'a'*40+b'\n', b'b'*40+b'\n', b'A\0scripts/verify-recovery.py\0', b'120000 blob hash\tpath\0']):
            self.assertEqual(m.inspect(ROOT, 'a'*40, 'b'*40), m.Category.UNKNOWN)

    def test_inert_module_architecture_when_present(self):
        path = ROOT / 'scripts/verify-recovery.py'
        if not path.exists():
            return  # Controller is deliberately not implemented in this CI change.
        tree = ast.parse(path.read_text())
        allowed = {'dataclasses', 'enum', 'typing', 're', 'math', '__future__'}
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                self.assertTrue(all(x.name.split('.')[0] in allowed for x in node.names))
            if isinstance(node, ast.ImportFrom):
                self.assertIn((node.module or '').split('.')[0], allowed)
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                self.assertNotIn(node.func.id, {'open', 'exec', 'eval', '__import__', 'compile', 'input'})
        self.assertTrue(all(isinstance(x, (ast.Import, ast.ImportFrom, ast.FunctionDef,
                                          ast.ClassDef, ast.Assign, ast.AnnAssign, ast.Expr)) for x in tree.body))

    def test_trusted_workflow_and_required_job(self):
        text = (ROOT / '.github/workflows/foundation.yml').read_text()
        self.assertIn('  verify:', text)
        self.assertIn('git show "$BASE:scripts/ci-classify.py"', text)
        self.assertIn("steps.classify.outputs.category != 'INERT_ONLY'", text)
        self.assertIn('python3 scripts/verify-foundation.py', text)
        self.assertIn('persist-credentials: false', text)
        self.assertNotIn('pull_request_target:', text)
        self.assertNotIn('continue-on-error:', text)


if __name__ == '__main__': unittest.main()
