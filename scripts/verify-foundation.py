"""Run isolated foundation checks; removes only its own ephemeral database container."""
from pathlib import Path
import argparse
import contextlib
import json
import os
import re
import secrets
import shutil
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.request
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
IMAGE = "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336"

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

V42_CLASS = "com.cinenest.pricing.internal.OperationalCommercialPostgresTest"
V42_METHODS = (
    "cleanMigrationPreservesDefaultDenialAndRejectsRevocationRollback",
    "populatedV41UpgradePreservesHistoryAndFlywayChecksums",
)
V42_DATABASES = ("cinenest_test", "cinenest_v40_upgrade")
V42_LIMITS = {
    "NanoCpus": 10**9, "Memory": 2**30, "MemorySwap": 2**30, "PidsLimit": 128,
    "ShmSize": 2**24, "Tmpfs": {"/var/lib/postgresql": "rw,size=496m"},
}
V42_OWNER_LABEL = "com.cinenest.v42-owner"
BACKEND = ROOT / "backend"


def database_command(name, task, owner=None):
    options = ["--tmpfs", "/var/lib/postgresql:rw"]
    if owner is not None:
        options = ["--cpus", "1", "--memory", "1g", "--memory-swap", "1g",
                   "--pids-limit", "128", "--shm-size", "16m",
                   "--tmpfs", "/var/lib/postgresql:rw,size=496m",
                   "--label", f"{V42_OWNER_LABEL}={owner}"]
    return ["docker", "run", "--detach", "--rm", "--name", name,
            "--label", f"com.cinenest.task={task}", *options,
            "--publish", "127.0.0.1::5432", "--env", "POSTGRES_DB=cinenest_test",
            "--env", "POSTGRES_USER=cinenest_test", "--env", "POSTGRES_PASSWORD", IMAGE]


def verify_v42_source():
    expected = os.environ.get("V42_EXPECTED_SHA", "")
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or os.environ.get("V42_FOCUSED") != "true"
            or not re.fullmatch(r"[a-f0-9]{40}", expected)
            or expected != os.environ.get("GITHUB_SHA")):
        raise RuntimeError("V42 requires a manual opt-in and an exact expected event SHA")
    current = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)
    if current != expected or dirty:
        raise RuntimeError("V42 checkout differs from the clean expected source")
    print(f"V42 verified source: {current}", flush=True)
    return current


def v42_scope():
    runner_temp = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    identity = "-".join(os.environ[key] for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"))
    if not re.fullmatch(r"[0-9]{1,20}-[0-9]{1,10}", identity):
        raise RuntimeError("Invalid V42 workflow run identity")
    return f"cinenest-v42-{identity}", runner_temp / f"cinenest-v42-{identity}.json"


def docker_output(*args):
    return subprocess.check_output(["docker", *args], text=True, timeout=20).strip()


def inventory():
    return set(docker_output("ps", "-aq", "--no-trunc").splitlines())


def owned_container(name, identity):
    ids = docker_output("ps", "-aq", "--no-trunc", "--filter", f"name=^{name}$").splitlines()
    if not ids:
        return None
    if len(ids) != 1:
        raise RuntimeError("V42 container identity is ambiguous")
    details, = json.loads(docker_output("inspect", ids[0]))
    if (details["Id"] != ids[0] or details["Name"] != "/" + name
            or details["Config"]["Labels"].get(V42_OWNER_LABEL) != identity):
        raise RuntimeError("V42 cleanup refused an unowned container")
    return details


def write_v42_state(path, state):
    pending = path.with_suffix(".pending")
    with open(pending, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as handle:
        json.dump(state, handle)
    pending.replace(path)


def cleanup_v42():
    name, marker = v42_scope()
    if not marker.exists():
        print("V42 cleanup: no pending resource record", flush=True)
        return
    state = json.loads(marker.read_bytes())
    if (not isinstance(state.get("owner"), str) or not re.fullmatch(r"[a-f0-9]{32}", state["owner"])
            or not isinstance(state.get("before"), list)):
        raise RuntimeError("Invalid V42 cleanup record")
    container = owned_container(name, state["owner"])
    port = state.get("port")
    if container:
        if port is None:
            for binding in (container["NetworkSettings"]["Ports"] or {}).get("5432/tcp") or []:
                if binding["HostIp"] == "127.0.0.1":
                    port = int(binding["HostPort"])
        docker_output("rm", "-f", "-v", container["Id"])
    if owned_container(name, state["owner"]) is not None:
        raise RuntimeError("V42 owned container still present")
    if port:
        try:
            connection = socket.create_connection(("127.0.0.1", port), timeout=1)
        except ConnectionRefusedError:
            pass
        else:
            with connection:
                raise RuntimeError("V42 loopback port still has a listener")
    if inventory() != set(state["before"]):
        raise RuntimeError("V42 unrelated container inventory differs")
    # Maven/Surefire XML can include inherited process properties. Retain only sanitized counts.
    for directory in (BACKEND / "target/surefire-reports", marker.with_suffix(".scratch")):
        if directory.exists():
            shutil.rmtree(directory)
        if directory.exists() or directory.is_symlink():
            raise RuntimeError("V42 scratch removal unverified")
    marker.with_suffix(".pending").unlink(missing_ok=True)
    marker.unlink()
    print("PASS V42 owned container, two disposable databases, loopback port and raw reports removed;"
          " unrelated container IDs unchanged", flush=True)


def v42_results(reports):
    cases = {}
    unexpected = []
    for path in sorted(reports.glob("TEST-*.xml")):
        suite = ElementTree.parse(path).getroot()
        if suite.tag != "testsuite" or suite.attrib.get("name") != V42_CLASS:
            unexpected.append(suite.attrib.get("name", "<unknown>"))
            continue
        observed = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        for case in suite.findall("testcase"):
            name = case.attrib.get("name")
            categories = [(tag, status) for tag, status in
                          (("failure", "FAIL"), ("error", "ERROR"), ("skipped", "SKIPPED"))
                          if case.find(tag) is not None]
            if (case.attrib.get("classname") != V42_CLASS or name not in V42_METHODS
                    or name in cases or len(categories) > 1):
                unexpected.append(name or "<unnamed>")
                continue
            observed["tests"] += 1
            if categories:
                tag, status = categories[0]
                observed[{"failure": "failures", "error": "errors", "skipped": "skipped"}[tag]] += 1
            else:
                status = "PASS"
            cases[name] = status
        if any(suite.attrib.get(key) != str(count) for key, count in observed.items()):
            unexpected.append("inconsistent Surefire totals")
    return {method: cases.get(method, "NOT_RUN") for method in V42_METHODS}, unexpected


def v42_maven_command(cache):
    return [str(BACKEND / "mvnw"), "-B", f"-Dmaven.repo.local={cache}",
            "-Dtest=" + V42_CLASS + "#" + "+".join(V42_METHODS),
            "-Dsurefire.failIfNoSpecifiedTests=true", "clean", "test"]


def run_v42_maven(env, scratch, timeout):
    with (scratch / "maven.log").open("wb") as log:
        process = subprocess.Popen(v42_maven_command(ROOT / ".cache/m2"), cwd=BACKEND,
                                   env=env, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            return process.wait(timeout=timeout)
        finally:
            # Stop any fork surviving an interruption, including Surefire.
            with contextlib.suppress(ProcessLookupError):
                os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)
            try:
                os.killpg(process.pid, 0)
            except ProcessLookupError:
                pass
            else:
                raise RuntimeError("V42 Maven process group remains after cleanup")


def v42_port(container):
    if any(container["HostConfig"].get(key) != value for key, value in V42_LIMITS.items()):
        raise RuntimeError("V42 container resource policy mismatch")
    bindings = container["NetworkSettings"]["Ports"]
    if set(bindings) != {"5432/tcp"} or len(bindings["5432/tcp"]) != 1:
        raise RuntimeError("V42 unexpected container port bindings")
    binding, = bindings["5432/tcp"]
    port = int(binding["HostPort"])
    if binding["HostIp"] != "127.0.0.1" or not 0 < port < 65536:
        raise RuntimeError("V42 PostgreSQL must bind only to IPv4 loopback")
    return port


def stop_v42(signum, _frame):
    raise SystemExit(128 + signum)


def v42_qualification():
    source = verify_v42_source()
    name, marker = v42_scope()
    if (any(marker.with_suffix(suffix).exists() or marker.with_suffix(suffix).is_symlink()
            for suffix in (".json", ".scratch", ".pending"))
            or docker_output("ps", "-aq", "--filter", f"name=^{name}$")):
        raise RuntimeError("V42 attempt already owns a resource; refusing to reuse it")
    if (BACKEND / "target/surefire-reports").exists():
        raise RuntimeError("V42 requires a checkout without pre-existing Surefire evidence")
    state = {"owner": secrets.token_hex(16), "before": sorted(inventory())}
    write_v42_state(marker, state)
    result = {"sourceSha": source, "cases": dict.fromkeys(V42_METHODS, "NOT_RUN"), "cleanup": "FAIL"}
    # The allowlist omits ambient application flags, trust, provider secrets and other test opt-ins.
    env = {k: os.environ[k] for k in ("PATH", "HOME", "JAVA_HOME", "LANG", "LC_ALL", "CI") if k in os.environ}
    password = secrets.token_urlsafe(32)
    deadline = time.monotonic() + 480
    try:
        run(database_command(name, "V42-POSTGRES-CI", state["owner"]),
            env=dict(env, POSTGRES_PASSWORD=password), stdout=subprocess.DEVNULL, timeout=90)
        container = owned_container(name, state["owner"])
        if not container:
            raise RuntimeError("V42 owned container could not be verified")
        port = v42_port(container)
        state["port"] = port
        write_v42_state(marker, state)
        ready_deadline = min(deadline, time.monotonic() + 30)
        while time.monotonic() < ready_deadline:
            ready = subprocess.run(["docker", "exec", name, "pg_isready", "-h", "127.0.0.1",
                                    "-U", "cinenest_test", "-d", "cinenest_test"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("V42 PostgreSQL failed readiness")
        version = docker_output("exec", name, "psql", "-XAt", "-U", "cinenest_test",
                                "-d", "cinenest_test", "-c", "SHOW server_version")
        if not re.match(r"^18\.6(?:$| )", version):
            raise RuntimeError("V42 PostgreSQL version mismatch")
        result["postgresql"] = version
        docker_output("exec", name, "createdb", "-U", "cinenest_test", V42_DATABASES[1])
        scratch = marker.with_suffix(".scratch")
        scratch.mkdir(mode=0o700)
        env.update(CINENEST_DB_URL=f"jdbc:postgresql://127.0.0.1:{port}/cinenest_test",
                   CINENEST_DB_USER="cinenest_test", CINENEST_DB_PASSWORD=password,
                   CINENEST_V42_QUALIFICATION="AUTHORIZED_DISPOSABLE",
                   JDK_JAVA_OPTIONS=f"-Djava.io.tmpdir={scratch}",
                   MAVEN_USER_HOME=str(ROOT / ".cache/maven"))
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("V42 provisioning exceeded its runtime budget")
        result["mavenExit"] = run_v42_maven(env, scratch, remaining)
        result["cases"], unexpected = v42_results(BACKEND / "target/surefire-reports")
        result["unexpectedCaseCount"] = len(unexpected)
        result["counts"] = {kind: list(result["cases"].values()).count(kind)
                            for kind in ("PASS", "FAIL", "ERROR", "SKIPPED", "NOT_RUN")}
        if (result["mavenExit"] or unexpected or result["counts"]["PASS"] != 2):
            raise RuntimeError("V42 qualification requires exactly two passing Surefire cases")
        for database in V42_DATABASES:
            history = docker_output("exec", name, "psql", "-XAt", "-U", "cinenest_test",
                                    "-d", database, "-c",
                                    "SELECT count(*), count(*) FILTER (WHERE version='42'), "
                                    "count(*) FILTER (WHERE version='31'), count(*) FILTER (WHERE NOT success) "
                                    "FROM flyway_schema_history WHERE version IS NOT NULL")
            if history != "41|1|0|0":
                raise RuntimeError("V42 Flyway history differs from the qualified contract")
            result.setdefault("migrations", {})[database] = {"head": 42, "successful": 41}
    finally:
        result.setdefault("counts", {kind: list(result["cases"].values()).count(kind)
                                     for kind in ("PASS", "FAIL", "ERROR", "SKIPPED", "NOT_RUN")})
        try:
            cleanup_v42()
            result["cleanup"] = "PASS"
        finally:
            print("V42_RESULT " + json.dumps(result, sort_keys=True), flush=True)


def main():
    name = "cinenest-fnd-" + secrets.token_hex(5)
    password = secrets.token_urlsafe(32)
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    env = dict(os.environ, MAVEN_USER_HOME=str(cache / "maven"))
    owned = False
    app = None
    try:
        run(database_command(name, "CN-FND-001"),
            env=dict(os.environ, POSTGRES_PASSWORD=password), stdout=subprocess.DEVNULL)
        owned = True
        for attempt in range(100):
            ready = subprocess.run(["docker", "exec", name, "pg_isready", "-U", "cinenest_test", "-d", "cinenest_test"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Isolated PostgreSQL did not become ready")
        port = subprocess.check_output(["docker", "port", name, "5432/tcp"], text=True).strip().split(":")[-1]
        env.update(CINENEST_DB_URL="jdbc:postgresql://127.0.0.1:" + port + "/cinenest_test",
                   CINENEST_DB_USER="cinenest_test", CINENEST_DB_PASSWORD=password,
                   CINENEST_HTTP_PORT="0")
        wrapper = str(ROOT / "backend" / "mvnw")
        run([wrapper, "-B", "-Dmaven.repo.local=" + str(cache / "m2"), "clean", "verify"], cwd=ROOT / "backend", env=env)
        java = str(Path(env["JAVA_HOME"]) / "bin/java") if env.get("JAVA_HOME") else shutil.which("java")
        with (cache / "packaged-startup.log").open("w+") as log:
            app = subprocess.Popen([java, "-jar", str(ROOT / "backend/target/cinenest-0.0.1-SNAPSHOT.jar")], env=env, stdout=log, stderr=subprocess.STDOUT)
            for attempt in range(150):
                if app.poll() is not None:
                    raise RuntimeError("Packaged application exited; inspect .cache/packaged-startup.log")
                log.seek(0)
                match = re.search(r"Tomcat started on port (\d+)", log.read())
                if match:
                    try:
                        urllib.request.urlopen("http://127.0.0.1:" + match[1] + "/api/bookings", timeout=2)
                        raise AssertionError("Unimplemented protected capability was exposed")
                    except urllib.error.HTTPError as error:
                        assert error.code in (401, 403), error.code
                    print("PASS packaged startup and real HTTP denial against migrated PostgreSQL")
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("Packaged startup timed out")
    finally:
        if app is not None:
            app.terminate()
            try:
                app.wait(timeout=10)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait()
        if owned:
            run(["docker", "stop", "--time", "5", name], stdout=subprocess.DEVNULL)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--verify-v42-source", action="store_true")
    modes.add_argument("--v42-postgres", action="store_true")
    modes.add_argument("--cleanup-v42", action="store_true")
    args = parser.parse_args()
    if args.verify_v42_source:
        verify_v42_source()
    elif args.v42_postgres:
        signal.signal(signal.SIGTERM, stop_v42)
        signal.signal(signal.SIGINT, stop_v42)
        v42_qualification()
    elif args.cleanup_v42:
        cleanup_v42()
    else:
        main()
