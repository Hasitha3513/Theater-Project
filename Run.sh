#!/usr/bin/env bash
# Shared local-development entry point and preflight functions. Never source an .env file.
set -Eeuo pipefail
CN_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cn_error() { printf 'CineNest: %s\n' "$*" >&2; return 1; }
cn_usage() {
  cat <<'HELP'
Usage: Run.sh [all|backend|frontend|verify|check|--help]
  all       Run backend and frontend in isolated process groups (default).
  backend   Same as Run-backend.sh.
  frontend  Same as Run-frontend.sh.
  verify    Same as Run-verify.sh (full existing checks).
  check     Preflight both servers without starting them.
See Run-backend.sh --help, Run-frontend.sh --help, Run-verify.sh --help.
No database, credentials, runtime downloads or production services are provisioned.
HELP
}
cn_python() { command -v python3 >/dev/null || cn_error 'python3 is required.'; }
cn_java() {
  [[ -z ${JAVA_TOOL_OPTIONS:-}${JDK_JAVA_OPTIONS:-}${_JAVA_OPTIONS:-}${MAVEN_OPTS:-}${MAVEN_ARGS:-} ]] || cn_error 'Unset Java/Maven option-injection variables for this supported workflow.'
  [[ -n ${JAVA_HOME:-} && -x $JAVA_HOME/bin/java && -x $JAVA_HOME/bin/javac ]] || cn_error 'Set JAVA_HOME to the qualified Java 25 JDK.'
  local version
  version=$("$JAVA_HOME/bin/java" -version 2>&1) || cn_error 'Cannot execute JAVA_HOME/bin/java.'
  [[ $version =~ version\ \"25[.\"] ]] || cn_error 'Java 25 is required; correct JAVA_HOME.'
  export PATH="$JAVA_HOME/bin:$PATH"
}
cn_node() {
  if [[ -n ${CINENEST_NODE_BIN:-} ]]; then export PATH="$CINENEST_NODE_BIN:$PATH"; fi
  command -v node >/dev/null && command -v npm >/dev/null || cn_error 'Put the qualified Node/npm on PATH, or set CINENEST_NODE_BIN.'
  [[ -z ${NODE_OPTIONS:-} ]] || cn_error 'Unset NODE_OPTIONS for this supported workflow.'
  local required actual manager
  required=$(<"$CN_ROOT/frontend/.node-version")
  actual=$(node --version)
  [[ ${actual#v} == "${required#v}" ]] || cn_error "Node ${required#v} required; set CINENEST_NODE_BIN or PATH."
  manager=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["packageManager"].split("@")[1])' "$CN_ROOT/frontend/package.json")
  [[ $(npm --version) == "$manager" ]] || cn_error "npm $manager required on the qualified Node PATH."
  export NEXT_TELEMETRY_DISABLED=1
}
cn_port() {
  python3 - "$1" "$2" <<'PY'
import socket,sys
label,value=sys.argv[1:]
if not value.isascii() or not value.isdecimal() or not 1024<=int(value)<=65535 or str(int(value))!=value:
    sys.exit(f'CineNest: {label} port must be an integer from 1024 to 65535.')
try:
    with socket.socket() as s:
        s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        s.bind(('127.0.0.1',int(value)))
except OSError:
    sys.exit(f'CineNest: {label} port {value} is unavailable. Stop its owner yourself or select another development port; no process was killed.')
PY
}
cn_backend_check() {
  cn_python; cn_java
  [[ ${CINENEST_DEV_DATABASE:-} == 1 ]] || cn_error 'Set CINENEST_DEV_DATABASE=1 only for your local development database; startup may apply Flyway migrations.'
  [[ -n ${CINENEST_DB_URL:-} && -n ${CINENEST_DB_USER:-} && -n ${CINENEST_DB_PASSWORD:-} ]] || cn_error 'Set CINENEST_DB_URL, CINENEST_DB_USER and CINENEST_DB_PASSWORD; no credentials are supplied.'
  [[ $CINENEST_DB_URL =~ ^jdbc:postgresql://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?/[a-zA-Z0-9_-]+$ ]] || cn_error 'Use a simple loopback PostgreSQL JDBC URL with a database name; remote hosts, URL credentials and query overrides are unsupported.'
  local name
  while IFS= read -r name; do
    [[ $name != SPRING_* ]] || cn_error "Unset $name; Spring overrides are unsupported by the local launcher."
  done < <(compgen -e)
  export CINENEST_HTTP_PORT=${CINENEST_HTTP_PORT:-8080}
  cn_port backend "$CINENEST_HTTP_PORT"
}
cn_frontend_check() {
  cn_python; cn_node
  [[ ${NODE_ENV:-development} == development ]] || cn_error 'This launcher supports NODE_ENV=development only.'
  export NODE_ENV=development
  export CINENEST_FRONTEND_PORT=${CINENEST_FRONTEND_PORT:-3000}
  cn_port frontend "$CINENEST_FRONTEND_PORT"
  export CINENEST_AUTH_ORIGIN=${CINENEST_AUTH_ORIGIN:-http://127.0.0.1:${CINENEST_HTTP_PORT:-8080}}
  python3 - <<'PY'
import os,sys,urllib.parse
try:
    u=urllib.parse.urlsplit(os.environ['CINENEST_AUTH_ORIGIN'])
    assert u.scheme=='http' and u.hostname in ('127.0.0.1','localhost','::1')
    assert not u.username and not u.password and u.path in ('','/') and not u.query and not u.fragment
    assert u.port and 1024<=u.port<=65535
except (ValueError,AssertionError):
    sys.exit('CineNest: CINENEST_AUTH_ORIGIN must be an explicit loopback HTTP origin and port, without credentials or overrides.')
PY
  [[ -x $CN_ROOT/frontend/node_modules/.bin/next ]] || cn_error 'Missing frontend dependencies. Use the qualified toolchain and run npm ci in frontend/ first.'
}
# Each child gets a new session. Signals target only those owned process groups.
cn_supervise() {
  cn_python
  python3 - "$@" <<'PY' &
import os,signal,subprocess,sys,time
children=[]
interrupt_child=len(sys.argv)>1 and sys.argv[1]=='--interrupt'
if interrupt_child: del sys.argv[1]
def stop(signum=None,frame=None):
    for p in children:
        try: os.killpg(p.pid,signal.SIGINT if interrupt_child else signal.SIGTERM)
        except ProcessLookupError: pass
    until=time.monotonic()+(25 if interrupt_child else 10)
    # Include descendants whose immediate parent already exited.
    while time.monotonic()<until:
        alive=False
        for p in children:
            p.poll()
            try: os.killpg(p.pid,0);alive=True
            except ProcessLookupError: pass
        if not alive: return
        time.sleep(.1)
    for p in children:
        try: os.killpg(p.pid,signal.SIGKILL)
        except ProcessLookupError: pass
    for p in children: p.wait()
interrupted=[]
def interrupt(sig,frame):
    interrupted.append(sig)
    raise KeyboardInterrupt
for sig in (signal.SIGINT,signal.SIGTERM,signal.SIGHUP): signal.signal(sig,interrupt)
try:
    groups=[];part=[]
    for arg in sys.argv[1:]:
        if arg==':::': groups.append(part);part=[]
        else: part.append(arg)
    groups.append(part)
    for args in groups: children.append(subprocess.Popen(args,start_new_session=True))
    while True:
        for p in children:
            code=p.poll()
            if code is not None:
                if len(children)>1 or code: print('CineNest: launched process exited; stopping its owned siblings.',file=sys.stderr)
                sys.exit(code if code>=0 else 128-code)
        time.sleep(.1)
except KeyboardInterrupt:
    sys.exit(128+(interrupted[0] if interrupted else signal.SIGINT))
finally: stop()
PY
  local cn_supervisor_pid=$! status=0
  trap 'kill -TERM "$cn_supervisor_pid" 2>/dev/null || true' INT TERM HUP
  while true; do
    if wait "$cn_supervisor_pid"; then status=0; break; else status=$?; fi
    kill -0 "$cn_supervisor_pid" 2>/dev/null || break
  done
  trap - INT TERM HUP
  return "$status"
}
cn_run() {
  if [[ ${CINENEST_LAUNCHER_MANAGED:-} == 1 ]]; then exec "$@"; else cn_supervise "$@"; fi
}
cn_main() {
  [[ $# -le 1 ]] || { cn_usage; return 2; }
  case ${1:-all} in
    --help|-h) cn_usage;;
    backend) exec "$CN_ROOT/Run-backend.sh";;
    frontend) exec "$CN_ROOT/Run-frontend.sh";;
    verify) exec "$CN_ROOT/Run-verify.sh";;
    check|all)
      cn_backend_check; cn_frontend_check
      [[ $CINENEST_HTTP_PORT != "$CINENEST_FRONTEND_PORT" ]] || cn_error 'Backend and frontend must use different ports.'
      if [[ ${1:-all} == check ]]; then printf 'PASS local server preflight (no connections or services started).\n'; return; fi
      cn_supervise env CINENEST_LAUNCHER_MANAGED=1 "$CN_ROOT/Run-backend.sh" ::: env CINENEST_LAUNCHER_MANAGED=1 "$CN_ROOT/Run-frontend.sh";;
    *) cn_usage; return 2;;
  esac
}
if [[ ${BASH_SOURCE[0]} == "$0" ]]; then cn_main "$@"; fi
