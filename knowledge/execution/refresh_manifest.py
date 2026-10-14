"""Refresh inventory/checksums after authorized KB edits; never changes versions or policy."""
from pathlib import Path
import argparse
import hashlib
import json

E = Path(__file__).resolve().parent
K = E.parent
R = K.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Write updated inventory only within an authorized edit/closeout')
    args = parser.parse_args()
    path = K / 'manifest.json'
    m = json.loads(path.read_text())
    for name, expected in m['preservedAssets'].items():
        if hashlib.sha256((K / name).read_bytes()).hexdigest() != expected:
            raise SystemExit('Preserved definition changed; requires explicit reconciliation: '+name)
    entries = []
    for asset in sorted(K.rglob('*')):
        if not asset.is_file() or '__pycache__' in asset.parts:
            continue
        name = asset.relative_to(K).as_posix()
        item = {'path': name, 'kind': 'visual-reference' if name.endswith('.png') else 'documentation-specification', 'implementationStatus': 'NOT_VERIFIED'}
        if name != 'manifest.json':
            item['sha256'] = hashlib.sha256(asset.read_bytes()).hexdigest()
        item['authority'] = 'canonical-prose' if name in {'PROJECT.md', 'ARCHITECTURE.md', 'DESIGN.md', 'PROGRESS.md'} else 'superseded-historical-snapshot' if 'knowledge/' + name in {e['sourcePath'] for e in m['consolidation']['documents']} else 'structured-asset-or-evidence'
        entries.append(item)
    m['files'] = entries
    m['externalInstructionFiles'] = [{'path': 'AGENTS.md', 'sha256': hashlib.sha256((R / 'AGENTS.md').read_bytes()).hexdigest(), 'purpose': 'Current root entry; charter consolidated into PROGRESS.md'}]
    text = json.dumps(m, indent=2) + '\n'
    changed = path.read_text() != text
    if args.write and changed:
        path.write_text(text)
    print(('UPDATED' if changed and args.write else 'NO_CHANGE' if not changed else 'STALE_INVENTORY_READ_ONLY') + f': KB {m["kbVersion"]}, {len(entries)} files, version/status choices untouched')
    return 0 if args.write or not changed else 1


if __name__ == '__main__':
    raise SystemExit(main())
