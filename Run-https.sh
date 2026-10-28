#!/usr/bin/env bash
# Explicit, non-production ingress only. Does not start services or enable providers.
set -Eeuo pipefail
CN_HTTPS_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
case ${1:-} in
  --help|-h)
    printf 'Usage: CINENEST_LOCAL_HTTPS=true Run-https.sh\nLoopback listener https://localhost:4443 -> existing 127.0.0.1 frontend.\nRequires a browser-trusted localhost certificate outside Git. No provider activation.\n'
    exit 0;;
  '') [[ $# -eq 0 ]] || exit 2;;
  *) exit 2;;
esac
: "${CINENEST_LOCAL_HTTPS:?Explicit local HTTPS opt-in required}"
CN_HTTPS_PRIVATE=${XDG_CONFIG_HOME:-$HOME/.config}/cinenest/local-https
export CINENEST_LOCAL_HTTPS_KEY=${CINENEST_LOCAL_HTTPS_KEY:-$CN_HTTPS_PRIVATE/localhost-key.pem}
export CINENEST_LOCAL_HTTPS_CERT=${CINENEST_LOCAL_HTTPS_CERT:-$CN_HTTPS_PRIVATE/localhost.pem}
export CINENEST_LOCAL_HTTPS_UPSTREAM_PORT=${CINENEST_LOCAL_HTTPS_UPSTREAM_PORT:-42137}
exec node "$CN_HTTPS_ROOT/scripts/local-https-ingress.mjs"
