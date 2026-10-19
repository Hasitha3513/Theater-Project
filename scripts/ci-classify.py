"""Fail-closed Git-diff classification. Execute the comparison-base copy in CI."""
from enum import Enum
import argparse
import re
import subprocess
from pathlib import Path


class Category(str, Enum):
    INERT = 'INERT_ONLY'
    APPLICATION = 'APPLICATION'
    MIXED = 'MIXED'
    UNKNOWN = 'UNKNOWN'
    GOVERNANCE = 'CI_OR_GOVERNANCE'


INERT = frozenset({'scripts/verify-recovery.py', 'scripts/test-recovery.py'})
COMPANIONS = frozenset({'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md',
                        'knowledge/manifest.json'})
GOVERNANCE = ('AGENTS.md', '.github/', 'knowledge/execution/', 'knowledge/decisions/',
              'knowledge/validate_', 'scripts/ci-', 'scripts/test-ci-', 'scripts/test-governance')
APPLICATION = ('backend/', 'frontend/', 'styles/', 'tokens/', 'infra/', 'docker/',
               'scripts/', 'Run', 'pom.xml', 'compose', 'Dockerfile', '.mvn/', 'mvnw')


def classify(changes):
    """Only exact A/M inert files plus exact companion docs can avoid full CI."""
    if not changes:
        return Category.UNKNOWN
    paths = [p for _, group in changes for p in group]
    if any(p.startswith(GOVERNANCE) for p in paths):
        return Category.GOVERNANCE
    if any(status not in ('A', 'M') or len(group) != 1
           or not group[0] or group[0].startswith('/')
           or '..' in group[0].split('/') for status, group in changes):
        return Category.UNKNOWN
    inert = any(p in INERT for p in paths)
    outside = [p for p in paths if p not in INERT | COMPANIONS]
    if not outside:
        return Category.INERT if inert else Category.UNKNOWN
    if any(not p.startswith(APPLICATION) for p in outside):
        return Category.UNKNOWN
    return Category.MIXED if inert else Category.APPLICATION


def parse_diff(raw):
    fields = raw.decode('utf-8', errors='strict').split('\0')
    if fields[-1] != '':
        raise ValueError('unterminated diff')
    fields.pop()
    changes = []
    while fields:
        status = fields.pop(0)
        count = 2 if status.startswith(('R', 'C')) else 1
        if not re.fullmatch(r'[ACDMRTUXB][0-9]*', status) or len(fields) < count:
            raise ValueError('invalid diff')
        changes.append((status, tuple(fields[:count])))
        del fields[:count]
    return changes


def inspect(repo, base, head):
    if not all(re.fullmatch(r'[0-9a-f]{40}', x or '') for x in (base, head)):
        return Category.UNKNOWN
    def git(*args):
        return subprocess.check_output(['git', '-C', str(repo), *args], stderr=subprocess.DEVNULL)
    try:
        for revision in (base, head):
            if git('rev-parse', revision + '^{commit}').decode().strip() != revision:
                return Category.UNKNOWN
        # Deletions/renames/type changes conservatively use full CI.
        changes = parse_diff(git('diff', '--name-status', '-z', '--find-renames', base, head, '--'))
        category = classify(changes)
        if category == Category.INERT:
            for _, group in changes:
                entry = git('ls-tree', '-z', head, '--', group[0]).split(b'\t', 1)[0]
                if entry.split(b' ', 1)[0] not in (b'100644', b'100755'):
                    return Category.UNKNOWN
        return category
    except (OSError, ValueError, UnicodeError, subprocess.CalledProcessError):
        return Category.UNKNOWN


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--head', required=True)
    args = parser.parse_args()
    print(inspect(args.repo, args.base, args.head).value)
