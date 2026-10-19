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


if __name__ == '__main__': main()
