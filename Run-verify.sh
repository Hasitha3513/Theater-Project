#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/Run.sh"
case ${1:-} in
 --help|-h) printf 'Usage: Run-verify.sh [--offline]\nExisting isolated PostgreSQL/backend checks; locked npm ci/build/typecheck/lint/test; knowledge/whitespace and launcher checks.\nDefault includes npm audit. --offline uses existing npm cache and explicitly skips online audit.\nRequires Java25, qualified Node/npm, Python3, Docker daemon (DOCKER_HOST may be set). No development DB credentials required.\nOnly the existing harness-owned ephemeral test container is stopped; no volumes/databases are deleted by these launchers.\n'; exit 0;;
 ''|--offline) [[ $# -le 1 ]] || exit 2;;
 *) cn_error 'Usage: Run-verify.sh [--offline]'; exit 2;;
esac
cn_python; cn_java; cn_node
command -v docker >/dev/null || cn_error 'Docker is required by the existing isolated PostgreSQL harness.'
docker info >/dev/null 2>&1 || cn_error 'Docker daemon unavailable; check DOCKER_HOST and socket permissions. No daemon was started.'
cd -- "$CN_ROOT"
for script in Run.sh Run-backend.sh Run-frontend.sh Run-verify.sh; do bash -n "$script"; done
python3 scripts/test-launchers.py
# Supervision also lets the existing harness finally block stop only its ephemeral container.
cn_supervise --interrupt python3 scripts/verify-foundation.py
cd -- "$CN_ROOT/frontend"
if [[ ${1:-} == --offline ]]; then cn_supervise npm ci --offline --cache "$CN_ROOT/.cache/npm"; else cn_supervise npm ci; fi
cn_supervise npm run build
cn_supervise npm run typecheck
cn_supervise npm run lint
cn_supervise npm test
if [[ ${1:-} == --offline ]]; then printf 'NOT RUN: npm audit (offline mode).\n'; else cn_supervise npm audit --audit-level=high; fi
cd -- "$CN_ROOT"
python3 knowledge/validate_design_kb.py
python3 knowledge/execution/validate_execution.py
git diff --check
printf 'PASS requested verification commands; see explicit NOT RUN entries above. Browser/provider/hardware/customer acceptance is separate.\n'
