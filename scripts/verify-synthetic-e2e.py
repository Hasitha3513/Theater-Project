"""Opt-in synthetic journey. Never downloads tooling or adopts a database.

Application cgroup: <=3 CPU/7 GiB; independent database: <=1 CPU/1 GiB.
Their sum enforces the approved <=4 CPU/8 GiB aggregate ceiling.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import stat
import socket
import subprocess
import sys
import tempfile
import time

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("foundation", ROOT / "scripts/verify-foundation.py")
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
IMAGE = foundation.IMAGE
LABEL = "com.cinenest.synthetic-e2e"


def call(args, timeout=10, env=None):
    # Do not echo commands/environment/output on error: credentials may be present.
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, env=env)
    if result.returncode:
        raise RuntimeError("Required command failed: " + Path(args[0]).name)
    return result.stdout.strip()


def limits(cpu, memory):
    quota, period = cpu.split()
    return memory.strip() != "max" and quota != "max" and 0 < int(quota) <= 3 * int(period) and 0 < int(memory) <= 7 * 1024**3


def private_root(path):
    s = path.lstat()
    if not stat.S_ISDIR(s.st_mode) or s.st_uid != os.getuid() or stat.S_IMODE(s.st_mode) != 0o700:
        raise RuntimeError("Private run directory ownership/mode invalid")
    if path.parent != Path(tempfile.gettempdir()) or not path.name.startswith("cinenest-e2e-"):
        raise RuntimeError("Run directory outside owned temporary namespace")


def runtime_limits():
    group = Path('/sys/fs/cgroup') / Path('/proc/self/cgroup').read_text().strip().split('::')[1].lstrip('/')
    if not limits((group / 'cpu.max').read_text(), (group / 'memory.max').read_text()):
        raise RuntimeError("Application cgroup must enforce <=3 CPU and <=7 GiB before provisioning")


def preflight():
    if any(os.environ.get(k) for k in ['NODE_OPTIONS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS']):
        raise RuntimeError('Unset tool option-injection variables before qualification')
    java = Path(os.environ.get('JAVA_HOME', '')) / 'bin/java'
    node = shutil.which('node')
    npm = shutil.which('npm')
    pw = Path(os.environ.get('PLAYWRIGHT_MODULE_PATH', '/nonexistent'))
    if not java.is_file() or not node or not npm:
        raise RuntimeError('Configure existing JAVA_HOME, Node and npm; automatic installation prohibited')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, timeout=10)
    if '25.0.4.1+1' not in version.stderr + version.stdout:
        raise RuntimeError('Required Java 25.0.4.1+1 unavailable')
    if call([node, '--version']) != 'v24.22.0' or call([npm, '--version']) != '11.19.1':
        raise RuntimeError('Required Node24.22.0/npm11.19.1 unavailable')
    if json.loads((pw / 'package.json').read_text())['version'] != '1.62.1':
        raise RuntimeError('Required existing Playwright1.62.1 unavailable')
    browsers = json.loads((pw.parent / 'playwright-core/browsers.json').read_text())['browsers']
    chromium = next(b for b in browsers if b['name'] == 'chromium-headless-shell')
    if (chromium['revision'], chromium['browserVersion']) != ('1234', '151.0.7922.34'):
        raise RuntimeError('Unexpected Chromium provenance')
    binary = Path.home() / '.cache/ms-playwright/chromium_headless_shell-1234/chrome-linux/headless_shell'
    # Newer distributions use chrome-headless-shell-linux64.
    if not binary.is_file():
        binary = Path.home() / '.cache/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-linux64/chrome-headless-shell'
    if not binary.is_file():
        raise RuntimeError('Pinned Chromium not installed; no download authorized')
    wrapper = (ROOT / 'backend/.mvn/wrapper/maven-wrapper.properties').read_text()
    if '/3.9.16/' not in wrapper:
        raise RuntimeError('Qualified Maven wrapper changed')
    if call(['docker', 'image', 'inspect', IMAGE, '--format', '{{.Id}}']) != IMAGE.split('@')[1]:
        raise RuntimeError('Approved PostgreSQL image not cached; no pull authorized')
    if call(['docker', 'info', '--format', '{{.CgroupVersion}}']) != '2':
        raise RuntimeError('Docker cgroup v2 required')
    call(['systemctl', '--user', 'show', 'app.slice', '--property=ControlGroup'])
    return {'java': '25.0.4.1+1', 'maven': '3.9.16', 'node': '24.22.0', 'npm': '11.19.1',
            'playwright': '1.62.1', 'chromium': '151.0.7922.34', 'chromiumRevision': '1234', 'image': IMAGE}


def cleanup(path):
    private_root(path)
    state = json.loads((path / 'ownership.json').read_text())
    name = state['name']
    if not name.startswith('cinenest-e2e-') or path.name != name:
        raise RuntimeError('Cleanup ownership mismatch')
    result = subprocess.run(['docker', 'container', 'inspect', name], capture_output=True, text=True, timeout=5)
    if result.returncode == 0:
        container = json.loads(result.stdout)[0]
        if container['Config']['Labels'].get(LABEL) != name:
            raise RuntimeError('Refusing to stop unowned container')
        call(['docker', 'stop', '--time', '5', container['Id']], timeout=10)
    elif 'No such container' not in result.stderr and 'No such object' not in result.stderr:
        raise RuntimeError('Container absence unverified; retaining private run directory')
    for metadata in ['database-port.json', 'config.json', 'frontend-port.json']:
        if (path/metadata).is_file():
            record = json.loads((path/metadata).read_text())
            for port in record.get('ports', []):
                try:
                    connection = socket.create_connection(('127.0.0.1', int(port)), timeout=.2)
                except ConnectionRefusedError:
                    continue
                except OSError:
                    raise RuntimeError('Owned port release unverified; retaining cleanup evidence')
                else:
                    connection.close()
                    raise RuntimeError('Recorded test port remains occupied; no unrelated process stopped')
    saved = path/'next-env.original'
    if saved.is_file():
        target = ROOT/'frontend/next-env.d.ts'
        original = saved.read_bytes()
        generated = original.replace(b'./.next/dev/types/', b'./.next/types/')
        if target.read_bytes() not in (original, generated):
            raise RuntimeError('Concurrent next-env change; retaining original snapshot for owner recovery')
        target.write_bytes(original)
    print('PASS owned PostgreSQL absent/stopped; original next-env bytes restored; private fixture storage removed', flush=True)
    shutil.rmtree(path)  # Root verified by uid/mode/namespace; rmtree does not follow child symlinks.


def inside(path):
    private_root(path)
    runtime_limits()
    state = json.loads((path / 'ownership.json').read_text())
    name = state['name']
    (path/'next-env.original').write_bytes((ROOT/'frontend/next-env.d.ts').read_bytes())
    password = secrets.token_urlsafe(32)
    env = {k: v for k, v in os.environ.items() if k in {'PATH','HOME','USER','JAVA_HOME','PLAYWRIGHT_MODULE_PATH','DOCKER_HOST','DBUS_SESSION_BUS_ADDRESS','XDG_RUNTIME_DIR','LANG'}}
    env.update(POSTGRES_PASSWORD=password)
    call(['docker', 'run', '--pull=never', '--detach', '--rm', '--name', name, '--label', LABEL+'='+name,
          '--cpus=1', '--memory=1g', '--memory-swap=1g', '--pids-limit=128', '--tmpfs',
          '/var/lib/postgresql:rw,size=512m', '--publish', '127.0.0.1::5432', '--env', 'POSTGRES_DB=cinenest_test',
          '--env', 'POSTGRES_USER=cinenest_test', '--env', 'POSTGRES_PASSWORD', IMAGE], env=env, timeout=25)
    config = json.loads(call(['docker', 'inspect', name]))[0]
    host = config['HostConfig']
    if (host['NanoCpus'], host['Memory'], host['MemorySwap'], host['PidsLimit']) != (10**9, 1024**3, 1024**3, 128) or host['Tmpfs'].get('/var/lib/postgresql') != 'rw,size=512m':
        raise RuntimeError('Database limits not effective')
    print(json.dumps({'admission': 'LIMITS_VERIFIED', 'applicationCpuMax': 3, 'applicationMemoryGiBMax': 7, 'postgresCpuMax': 1, 'postgresMemoryGiBMax': 1, 'postgresPidsMax': 128, 'postgresTmpfsMiBMax': 512, 'run': name}), flush=True)
    for _ in range(100):
        ready = subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'cinenest_test'], capture_output=True, timeout=3)
        if ready.returncode == 0:
            break
        time.sleep(.2)
    else:
        raise RuntimeError('Owned database startup timeout')
    port = call(['docker', 'port', name, '5432/tcp']).removeprefix('127.0.0.1:')
    if not port.isdigit():
        raise RuntimeError('Database must have one loopback binding')
    (path/'database-port.json').write_text(json.dumps({'ports': [int(port)]}))
    env.update(CINENEST_DB_URL='jdbc:postgresql://127.0.0.1:'+port+'/cinenest_test',
               CINENEST_DB_USER='cinenest_test', CINENEST_DB_PASSWORD=password,
               CINENEST_E2E_RUN=str(path), MAVEN_USER_HOME=str(ROOT/'.cache/maven'), NEXT_TELEMETRY_DISABLED='1')
    # Only local caches; no npm install, Maven download or image pull.
    with (path / 'private-build.log').open('w') as log:
        for args, cwd in [(['npm', 'run', 'build'], ROOT/'frontend'),
                          ([str(ROOT/'backend/mvnw'), '-o', '-B', '-Dmaven.repo.local='+str(ROOT/'.cache/m2'),
                            '-Dtest=SyntheticJourneyIT', 'test'], ROOT/'backend')]:
            result = subprocess.run(args, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=540)
            if result.returncode:
                if (path/'result.json').is_file():
                    print((path/'result.json').read_text(), flush=True)
                raise RuntimeError('Build/journey failed; raw private diagnostics are not published')
    print((path/'result.json').read_text(), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--inside', type=Path)
    parser.add_argument('--cleanup', type=Path)
    args = parser.parse_args()
    try:
        if args.cleanup:
            cleanup(args.cleanup)
            return 0
        if args.inside:
            inside(args.inside)
            return 0
        versions = preflight()
        print(json.dumps({'preflight': 'TOOLS_PRESENT', 'versions': versions,
                          'execution': 'NOT_RUN', 'resourceEnforcement': 'NOT_YET_VERIFIED'}), flush=True)
        if not args.execute:
            return 0
        path = Path(tempfile.mkdtemp(prefix='cinenest-e2e-'))
        os.chmod(path, 0o700)
        (path/'ownership.json').write_text(json.dumps({'name': path.name}))
        script = str(Path(__file__).resolve())
        # The service kills/reaps descendants on exit/timeout; ExecStopPost owns DB cleanup even after SIGKILL.
        command = ['systemd-run', '--user', '--wait', '--pipe', '--collect', '--unit='+path.name,
                   '--working-directory='+str(ROOT), '--property=CPUQuota=300%', '--property=MemoryMax=7G',
                   '--property=MemorySwapMax=0', '--property=TasksMax=1024', '--property=RuntimeMaxSec=600',
                   '--property=TimeoutStopSec=30', '--property=KillMode=control-group',
                   '--property=ExecStopPost='+sys.executable+' "'+script+'" --cleanup "'+str(path)+'"']
        for key in ['PATH', 'JAVA_HOME', 'PLAYWRIGHT_MODULE_PATH', 'DOCKER_HOST']:
            if key in os.environ:
                command += ['--setenv='+key+'='+os.environ[key]]
        command += [sys.executable, '-B', script, '--inside', str(path)]
        result = subprocess.run(command, timeout=640)
        if path.exists():
            # No application service may remain before fallback cleanup.
            active = subprocess.run(['systemctl', '--user', 'is-active', path.name], capture_output=True, text=True, timeout=5).stdout.strip()
            if active in {'active', 'activating', 'deactivating'}:
                raise RuntimeError('Service still active; cleanup withheld')
            cleanup(path)
        return result.returncode
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as error:
        print('BLOCKED/FAIL '+type(error).__name__+': '+str(error) if isinstance(error, RuntimeError) else 'BLOCKED/FAIL '+type(error).__name__, file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
