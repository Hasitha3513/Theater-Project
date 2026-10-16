#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/Run.sh"
case ${1:-} in
 --help|-h) printf 'Usage: Run-backend.sh [--check]\nRequires JAVA_HOME (JDK25), CINENEST_DEV_DATABASE=1, CINENEST_DB_URL/USER/PASSWORD.\nLoopback only; CINENEST_HTTP_PORT defaults to the existing 8080. Flyway may migrate your explicit local database.\n'; exit 0;;
 ''|--check) [[ $# -le 1 ]] || exit 2;;
 *) cn_error 'Usage: Run-backend.sh [--check]'; exit 2;;
esac
cn_backend_check
if [[ ${1:-} == --check ]]; then printf 'PASS backend preflight; no database connection made.\n'; exit; fi
cd -- "$CN_ROOT/backend"
export MAVEN_USER_HOME="$CN_ROOT/.cache/maven"
cn_run ./mvnw -B "-Dmaven.repo.local=$CN_ROOT/.cache/m2" spring-boot:run "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=$CINENEST_HTTP_PORT"
