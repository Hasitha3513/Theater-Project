"""Six read-only, in-memory validator fault injections; no resource operations."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import runpy
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / 'knowledge'


def main():
    validator = runpy.run_path(str(K / 'execution/validate_execution.py'))
    controls = [
        ('requirements.json', 'requirements', 'CN-NFR-04', 'implementationStatus', 'VERIFIED_WITH_EVIDENCE'),
        ('requirements.json', 'requirements', 'CN-NFR-04', 'acceptanceStatus', 'ACCEPTED'),
        ('test-plan.json', 'tests', 'T-CN-NFR-04', 'status', 'PASS'),
        ('change-sets.json', 'changeSets', 'CN-ACCEPT-001', 'workStatus', 'VERIFIED'),
        ('change-sets.json', 'changeSets', 'CN-ACCEPT-001', 'dependencies', []),
    ]
    mutations = []
    for filename, key, identity, field, value in controls:
        path = K / 'execution' / filename
        data = json.loads(path.read_text())
        next(x for x in data[key] if x['id'] == identity)[field] = value
        mutations.append((path, json.dumps(data).encode(), identity + '/' + field))
    path = K / 'PROGRESS.md'
    original = path.read_text()
    changed = original.replace('- [ ] **P9-C06', '- [x] **P9-C06')
    if changed == original:
        raise AssertionError('Expected pending recovery criterion absent')
    mutations.append((path, changed.encode(), 'P9-C06 acceptance'))
    original_rb, original_rt = Path.read_bytes, Path.read_text
    for target, replacement, name in mutations:
        def rb(path, *args, **kwargs):
            return replacement if path == target else original_rb(path, *args, **kwargs)
        def rt(path, *args, **kwargs):
            return replacement.decode() if path == target else original_rt(path, *args, **kwargs)
        with patch.object(Path, 'read_bytes', rb), patch.object(Path, 'read_text', rt), redirect_stdout(io.StringIO()):
            result = validator['inert_ci_main']()
        if result == 0:
            raise AssertionError('Validator accepted ' + name)
        print('PASS rejected', name)

    test_future_register_controls()


def test_future_register_controls():
    """Positive and negative format/lifecycle tests use in-memory records only."""
    import re
    module = runpy.run_path(str(K / 'validate_design_kb.py'))
    path = K / 'backlog/FUTURE_FEATURES.md'
    original = path.read_text()
    template = re.search(r'```markdown\n(.*?)\n```', original, re.S).group(1)
    record = template.replace('FF-<NNNN> — <title>', 'FF-0001 — In-memory validation fixture')
    record = re.sub(r'^- Classification:.*$', '- Classification: FUTURE_ENHANCEMENT', record, flags=re.M)
    record = re.sub(r'^- Discovery date:.*$', '- Discovery date: 2026-10-22', record, flags=re.M)
    record = re.sub(r'^- Dependencies:.*$', '- Dependencies: NONE', record, flags=re.M)
    record = re.sub(r'^- Owner approval:.*$', '- Owner approval: PENDING', record, flags=re.M)
    record = re.sub(r'^- Implementation authority:.*$', '- Implementation authority: NOT_GRANTED', record, flags=re.M)
    record = re.sub(r'^- Commit SHA:.*$', '- Commit SHA: NOT_IMPLEMENTED', record, flags=re.M)
    record = re.sub(r'^- CI evidence:.*$', '- CI evidence: NOT_RUN', record, flags=re.M)
    record = re.sub(r'^- Acceptance:.*$', '- Acceptance: NOT_ACCEPTED', record, flags=re.M)
    rb = Path.read_text
    def errors_for(value):
        failures = []
        with patch.object(Path, 'read_text', lambda p, *a, **kw: value if p == path else rb(p, *a, **kw)):
            module['validate_future_features'](lambda ok, message: failures.append(message) if not ok else None)
        return failures
    assert not errors_for(original), 'Empty authorized register rejected'
    assert not errors_for(original + '\n' + record), 'Valid IDEA record rejected'
    bad = {
        'duplicate ID': record + '\n' + record,
        'invalid classification': record.replace('Classification: FUTURE_ENHANCEMENT', 'Classification: APPROVED_FEATURE'),
        'invalid lifecycle': record.replace('Implementation status: IDEA', 'Implementation status: COMPLETE'),
        'missing source': re.sub(r'^- Source/reference:.*\n', '', record, flags=re.M),
        'unapproved feature': record.replace('Implementation status: IDEA', 'Implementation status: APPROVED'),
        'unauthorized readiness': record.replace('Implementation status: IDEA', 'Implementation status: READY').replace('Owner approval: PENDING', 'Owner approval: in-memory bounded approval fixture'),
        'unverified success': record.replace('Implementation status: IDEA', 'Implementation status: VERIFIED').replace('Owner approval: PENDING', 'Owner approval: in-memory bounded approval fixture').replace('Implementation authority: NOT_GRANTED', 'Implementation authority: GRANTED in-memory test only'),
        'self dependency': record.replace('Dependencies: NONE', 'Dependencies: FF-0001'),
        'unknown dependency': record.replace('Dependencies: NONE', 'Dependencies: FF-9999'),
    }
    for name, broken in bad.items():
        assert errors_for(original + '\n' + broken), 'Future register accepted ' + name
        print('PASS future register rejected', name)
    print('PASS future register:2 positive and9 negative in-memory cases;no saved feature or authority')


if __name__ == '__main__': main()
