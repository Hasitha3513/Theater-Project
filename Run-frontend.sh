#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/Run.sh"
case ${1:-} in
 --help|-h) printf 'Usage: Run-frontend.sh [--check]\nRequires qualified Node/npm on PATH or CINENEST_NODE_BIN, and existing npm ci dependencies.\nLoopback development only; CINENEST_FRONTEND_PORT=3000, local backend CINENEST_AUTH_ORIGIN defaults to http://127.0.0.1:8080.\n'; exit 0;;
 ''|--check) [[ $# -le 1 ]] || exit 2;;
 *) cn_error 'Usage: Run-frontend.sh [--check]'; exit 2;;
esac
cn_frontend_check
if [[ ${1:-} == --check ]]; then printf 'PASS frontend preflight; no service started.\n'; exit; fi
cd -- "$CN_ROOT/frontend"
cn_run npm run dev -- --webpack --port "$CINENEST_FRONTEND_PORT"
