"""Run isolated foundation checks; removes only its own ephemeral database container."""
from pathlib import Path
import os
import re
import secrets
import shutil
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
IMAGE = "postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336"

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def main():
    name = "cinenest-fnd-" + secrets.token_hex(5)
    password = secrets.token_urlsafe(32)
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    env = dict(os.environ, MAVEN_USER_HOME=str(cache / "maven"))
    owned = False
    app = None
    try:
        run(["docker", "run", "--detach", "--rm", "--name", name,
             "--label", "com.cinenest.task=CN-FND-001", "--tmpfs", "/var/lib/postgresql:rw",
             "--publish", "127.0.0.1::5432", "--env", "POSTGRES_DB=cinenest_test",
             "--env", "POSTGRES_USER=cinenest_test", "--env", "POSTGRES_PASSWORD",
             IMAGE], env=dict(os.environ, POSTGRES_PASSWORD=password), stdout=subprocess.DEVNULL)
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
    main()
