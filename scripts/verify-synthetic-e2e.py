"""Opt-in synthetic journey. Never downloads tooling or adopts a database.

Application cgroup: <=3 CPU/7 GiB; independent database: <=1 CPU/1 GiB.
Their sum enforces the approved <=4 CPU/8 GiB aggregate ceiling.
"""
import argparse
import itertools
import re
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


def current_cgroup():
    return Path('/sys/fs/cgroup') / Path('/proc/self/cgroup').read_text().strip().split('::')[1].lstrip('/')


def runtime_limits():
    group = current_cgroup()
    if not limits((group / 'cpu.max').read_text(), (group / 'memory.max').read_text()):
        raise RuntimeError("Application cgroup must enforce <=3 CPU and <=7 GiB before provisioning")


SUPERVISED = "supervised-h1"
ADMIN_QA = "supervised-admin"
CANCELLATION = "cancellation"
FUNDING = "extension-funding"
TWO_JOURNEYS = "reschedule-refund"
PACKAGE_BOOKING = "package-booking"
OPERATIONAL = "customer-turnover"
SUPERVISED_SCENARIOS = {SUPERVISED, ADMIN_QA}
SUPERVISED_ACTIVE_SECONDS = 1710
VERIFIED_CLEANUP_SCENARIOS = SUPERVISED_SCENARIOS | {CANCELLATION, FUNDING, TWO_JOURNEYS, PACKAGE_BOOKING, OPERATIONAL}


def preflight(supervised=False):
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
    if supervised:
        if os.environ.get('DOCKER_CONTEXT') or os.environ.get('DOCKER_HOST') != 'unix:///var/run/docker.sock':
            raise RuntimeError('Supervised mode requires the approved local Docker socket')
        if not (os.environ.get('DISPLAY') or os.environ.get('WAYLAND_DISPLAY')):
            raise RuntimeError('Supervised mode requires an existing desktop display')
        headed = next(b for b in browsers if b['name'] == 'chromium')
        executable = Path(call([node, '--input-type=module', '-e',
                                'const { chromium } = await import(process.argv[1]); console.log(chromium.executablePath());',
                                (pw / 'index.mjs').as_uri()]))
        if (headed['revision'], headed['browserVersion']) != ('1234', '151.0.7922.34') or not executable.is_file() or not os.access(executable, os.X_OK):
            raise RuntimeError('Pinned headed Chromium not installed; no download authorized')
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
        if state.get('scenario') in VERIFIED_CLEANUP_SCENARIOS:
            deadline = time.monotonic() + 5
            while True:
                absence = subprocess.run(
                    ['docker', 'container', 'inspect', name], capture_output=True, text=True, timeout=2,
                )
                if absence.returncode != 0:
                    if any(msg in absence.stderr for msg in ('No such container', 'No such object')):
                        break
                    raise RuntimeError('Supervised container removal unverified')
                if time.monotonic() >= deadline:
                    raise RuntimeError('Supervised container removal unverified')
                time.sleep(.1)
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
    cgroup_file = path / 'application-cgroup'
    if state.get('scenario') in VERIFIED_CLEANUP_SCENARIOS and cgroup_file.is_file():
        group = Path(cgroup_file.read_text())
        if group.resolve() != group or not group.is_relative_to('/sys/fs/cgroup') or group.name != name + '.service':
            raise RuntimeError('Owned supervised cgroup evidence is invalid')
        try:
            live = {int(pid) for pid in (group / 'cgroup.procs').read_text().split()}
        except FileNotFoundError:
            if group.exists():
                raise RuntimeError('Owned supervised process absence unverified') from None
            live = set()
        if live - {os.getpid()}:
            raise RuntimeError('Owned supervised processes remain')
        print('PASS supervised application cgroup contains no surviving task processes', flush=True)
    saved = path/'next-env.original'
    if saved.is_file():
        target = ROOT/'frontend/next-env.d.ts'
        original = saved.read_bytes()
        generated = original.replace(b'./.next/dev/types/', b'./.next/types/')
        if target.read_bytes() not in (original, generated):
            raise RuntimeError('Concurrent next-env change; retaining original snapshot for owner recovery')
        target.write_bytes(original)
    if state.get('scenario') == OPERATIONAL:
        evidence = ROOT / '.cache' / (name + '-operational-evidence')
        evidence.mkdir(mode=0o700, exist_ok=True)
        if evidence.resolve() != evidence or evidence.stat().st_mode & 0o077:
            raise RuntimeError('Unsafe operational evidence directory')
        for filename in ('result.json', 'private-backend-journey.log', 'private-browser.log', 'database-ownership.json', 'database-port.json'):
            source=path/filename
            if source.is_file() and not source.is_symlink():
                shutil.copyfile(source,evidence/filename)
                os.chmod(evidence/filename,0o600)
        for archive in path.glob('attempt-*'):
            if archive.is_dir() and not archive.is_symlink():shutil.copytree(archive,evidence/archive.name,dirs_exist_ok=True)
        (evidence/'cleanup.json').write_text(json.dumps({'ownedContainerAbsent':True,
            'recordedPortsClosed':True,'taskProcessesAbsent':True}))
        print('Operational private evidence: '+str(evidence),flush=True)
    if state.get('scenario') == ADMIN_QA:
        evidence = ROOT / '.cache' / (name + '-admin-evidence')
        evidence.mkdir(mode=0o700, exist_ok=True)
        if evidence.resolve() != evidence or evidence.stat().st_mode & 0o077:
            raise RuntimeError('Unsafe Admin evidence directory')
        results = path / 'remaining-results.json'
        if results.is_file() and not results.is_symlink():
            data = json.loads(results.read_text())
            safe = [{k: row[k] for k in ('id', 'status', 'http', 'actual', 'reason', 'source') if k in row}
                    for row in data]
            (evidence / 'results.json').write_text(json.dumps(safe, indent=2))
        for screenshot in path.glob('admin-case-*.png'):
            if screenshot.is_file() and not screenshot.is_symlink():
                shutil.copyfile(screenshot, evidence / screenshot.name)
        (evidence / 'cleanup.json').write_text(json.dumps({'ownedContainerAbsent': True,
            'recordedPortsClosed': True, 'cgroupVerified': cgroup_file.is_file()}))
        print('Admin evidence: ' + str(evidence), flush=True)
    print('PASS owned PostgreSQL absent/stopped; original next-env bytes restored; private fixture storage removed', flush=True)
    shutil.rmtree(path)  # Root verified by uid/mode/namespace; rmtree does not follow child symlinks.


def admin_qa_options(scenario):
    return ["-Dcinenest.test.admin-qa=true"] if scenario == ADMIN_QA else []


def journey_test(scenario):
    tests={'campaign':'SyntheticJourneyIT','h1':'SyntheticJourneyIT',SUPERVISED:'SyntheticJourneyIT',ADMIN_QA:'SyntheticJourneyIT',CANCELLATION:'SyntheticJourneyIT',FUNDING:'SyntheticJourneyIT',TWO_JOURNEYS:'SyntheticJourneyIT',PACKAGE_BOOKING:'SyntheticJourneyIT',OPERATIONAL:'SyntheticJourneyIT','bank-instructions':'BankInstructionsJourneyIT'}
    if scenario not in tests:raise RuntimeError('Unsupported synthetic scenario')
    return tests[scenario]


def run_stage(stage, args, cwd, env, path, timeout):
    log_path = path / f'private-{stage}.log'
    with log_path.open('w') as log:
        try:
            result = subprocess.run(args, cwd=cwd, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=timeout)
        except subprocess.TimeoutExpired as error:
            raise RuntimeError(f'{stage} exceeded its {timeout}s timeout') from error
        except OSError as error:
            raise RuntimeError(f'{stage} could not start (errno {error.errno})') from error
    if "-Dcinenest.test.admin-qa=true" in args:
        raw = log_path.read_text(errors='replace')
        for stage_status in re.findall(r'ADMIN_SETUP [a-z-]+ expected=[0-9]{3} actual=[0-9]{3}', raw):
            print(stage_status, flush=True)
    if result.returncode:
        # Emit only fixed markers. Maven and Chromium output can contain fixture secrets.
        raw = log_path.read_text(errors='replace')
        markers = (
            "Could not parse output from TypeScript's --showConfig",
            'COMPILATION ERROR', 'Failed to load ApplicationContext',
            'FlywayValidateException', 'PSQLException', 'TimeoutError',
            'TargetClosedError', 'Missing X server or $DISPLAY',
            'Permission denied', 'No space left on device',
        )
        locations = sorted(set(re.findall(
            r'(?:SyntheticJourneyIT\.java:[0-9]+|synthetic-e2e\.mjs:[0-9]+:[0-9]+)', raw)))[:10]
        report = {'failedStage': stage, 'returnCode': result.returncode,
                  'markers': [marker for marker in markers if marker in raw],
                  'sourceLocations': locations}
        if any(arg == '-Dtest=BankInstructionsJourneyIT' for arg in args):
            report['holdStatuses'] = re.findall(r'BI_SAFE_HOLD_STATUS=([0-9]{3})', raw)
            report['sourceLocations'] += sorted(set(re.findall(
                r'(?:BankInstructionsJourneyIT|BankIntegrationTest)\.java:[0-9]+', raw)))[:10]
        print(json.dumps(report), flush=True)
        if (path / 'result.json').is_file():
            print((path / 'result.json').read_text(), flush=True)
        status = f'signal {-result.returncode}' if result.returncode < 0 else f'exit {result.returncode}'
        raise RuntimeError(f'{stage} failed ({status})')


def browser_mode(scenario):
    if scenario in SUPERVISED_SCENARIOS:
        return SUPERVISED
    return scenario if scenario in {'h1', CANCELLATION, FUNDING, TWO_JOURNEYS, PACKAGE_BOOKING, OPERATIONAL} else 'campaign'


def verify_cancellation_results(result):
    expected = {
        'startup', 'cancellation-owner-venue-denials', 'cancellation-customer',
        'cancellation-replay-persistence', 'cancellation-staff-view',
    }
    rows = result.get('results', [])
    if (result.get('mode') != CANCELLATION or result.get('syntheticOnly') is not True
            or len(rows) != len(expected) or {row['id'] for row in rows} != expected
            or any(row['status'] != 'PASS' for row in rows)):
        raise RuntimeError('Cancellation browser result inventory incomplete or failed')


def verify_funding_results(result):
    expected = {'startup', 'funding-scope-denials', 'funding-ui-recovery', 'funding-replay-expiry'}
    rows = result.get('results', [])
    if (result.get('mode') != FUNDING or result.get('syntheticOnly') is not True
            or len(rows) != len(expected) or {row['id'] for row in rows} != expected
            or any(row['status'] != 'PASS' for row in rows)):
        raise RuntimeError('Funding browser result inventory incomplete or failed')


def verify_two_journey_results(result):
    expected = {'startup', 'two-reschedule', 'two-refund'}
    rows = result.get('results', [])
    if (result.get('mode') != TWO_JOURNEYS or result.get('syntheticOnly') is not True
            or len(rows) != len(expected) or {row['id'] for row in rows} != expected
            or any(row['status'] != 'PASS' for row in rows)):
        raise RuntimeError('Two-journey browser result inventory incomplete or failed')


def verify_package_booking_results(result):
    expected = {'startup', 'package-publish', 'package-customer-quote', 'package-history'}
    rows = result.get('results')
    if (result.get('mode') != PACKAGE_BOOKING or result.get('syntheticOnly') is not True
            or not isinstance(rows, list) or len(rows) != len(expected)
            or any(not isinstance(row, dict) or row.get('status') != 'PASS'
                   or not isinstance(row.get('id'), str) for row in rows)
            or {row['id'] for row in rows} != expected):
        raise RuntimeError('Package booking browser result inventory incomplete or failed')


OPERATIONAL_GROUPS = {'startup','operational-customer-quote','operational-verification-bank',
    'operational-independent-finance','operational-roster-eligibility','operational-staff-session',
    'operational-independent-turnover','operational-persistence'}

def verify_operational_results(result):
    rows=result.get('results')
    if (result.get('mode') != OPERATIONAL or result.get('syntheticOnly') is not True
        or not isinstance(rows,list) or len(rows)!=len(OPERATIONAL_GROUPS)
        or any(not isinstance(row,dict) or row.get('status')!='PASS' for row in rows)
        or {row.get('id') for row in rows}!=OPERATIONAL_GROUPS):
        raise RuntimeError('Operational browser result inventory incomplete or failed')


def cancellation_database_version(name, env, attempts=100):
    # The entrypoint's temporary initialization server accepts Unix sockets before
    # the final TCP listener is ready. Authenticate against the final listener.
    args = ['docker', 'exec', '--env', 'PGPASSWORD', name, 'psql', '-XAt', '-w',
            '-h', '127.0.0.1', '-U', 'cinenest_test', '-d', 'cinenest_test',
            '-c', 'SHOW server_version']
    probe_env = dict(env, PGPASSWORD=env['POSTGRES_PASSWORD'])
    for _ in range(attempts):
        try:
            result = subprocess.run(args, capture_output=True, text=True, timeout=3, env=probe_env)
        except subprocess.TimeoutExpired:
            pass
        else:
            if result.returncode == 0:
                version = result.stdout.strip()
                if not version or version.split()[0] != '18.6':
                    raise RuntimeError('Unexpected cancellation qualification PostgreSQL version')
                return version
        time.sleep(.2)
    raise RuntimeError('Cancellation PostgreSQL authenticated TCP readiness and version check failed')


def inside(path):
    private_root(path)
    runtime_limits()
    state = json.loads((path / 'ownership.json').read_text())
    name = state['name']
    scenario = state.get('scenario', 'campaign')
    test = journey_test(scenario)
    supervised = scenario in SUPERVISED_SCENARIOS
    (path/'next-env.original').write_bytes((ROOT/'frontend/next-env.d.ts').read_bytes())
    password = secrets.token_urlsafe(32)
    env = {k: v for k, v in os.environ.items() if k in {'PATH','HOME','USER','JAVA_HOME','PLAYWRIGHT_MODULE_PATH','DOCKER_HOST','DBUS_SESSION_BUS_ADDRESS','XDG_RUNTIME_DIR','LANG'}}
    env.update(POSTGRES_PASSWORD=password, CINENEST_E2E_MODE=browser_mode(scenario))
    if scenario in VERIFIED_CLEANUP_SCENARIOS:
        group = current_cgroup()
        if group.name != name + '.service':
            raise RuntimeError('Supervised run must be in its owned service cgroup')
        (path / 'application-cgroup').write_text(str(group))
        env['SERVER_ADDRESS'] = '127.0.0.1'
    if supervised:
        env['TMPDIR'] = str(path)
        for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY'):
            if key in os.environ:
                env[key] = os.environ[key]
    env['NEXT_TELEMETRY_DISABLED'] = '1'
    active_deadline = time.monotonic() + 1500
    timeout = SUPERVISED_ACTIVE_SECONDS if supervised else 540
    run_stage('frontend-build', ['npm', 'run', 'build'], ROOT/'frontend', env, path, timeout)
    env['MAVEN_USER_HOME'] = str(ROOT/'.cache/maven')
    maven = [str(ROOT/'backend/mvnw'), '-o', '-B', '-Dmaven.repo.local='+str(ROOT/'.cache/m2')]
    run_stage('backend-test-compile', [*maven, 'test-compile'], ROOT/'backend', env, path, timeout)
    call(['docker', 'run', '--pull=never', '--detach', '--rm', '--name', name, '--label', LABEL+'='+name,
          '--cpus=1', '--memory=1g', '--memory-swap=1g', '--pids-limit=128', '--shm-size=16m', '--tmpfs',
          '/var/lib/postgresql:rw,size=496m', '--publish', '127.0.0.1::5432', '--env', 'POSTGRES_DB=cinenest_test',
          '--env', 'POSTGRES_USER=cinenest_test', '--env', 'POSTGRES_PASSWORD', IMAGE], env=env, timeout=25)
    config = json.loads(call(['docker', 'inspect', name]))[0]
    host = config['HostConfig']
    if (host['NanoCpus'], host['Memory'], host['MemorySwap'], host['PidsLimit'], host['ShmSize']) != (
            10**9, 1024**3, 1024**3, 128, 16 * 1024**2
    ) or host['Tmpfs'].get('/var/lib/postgresql') != 'rw,size=496m':
        raise RuntimeError('Database limits not effective')
    print(json.dumps({'admission': 'LIMITS_VERIFIED', 'applicationCpuMax': 3, 'applicationMemoryGiBMax': 7, 'postgresCpuMax': 1, 'postgresMemoryGiBMax': 1, 'postgresPidsMax': 128, 'postgresTmpfsMiBMax': 496, 'postgresShmMiBMax': 16, 'run': name}), flush=True)
    port = call(['docker', 'port', name, '5432/tcp']).removeprefix('127.0.0.1:')
    if not port.isdigit():
        raise RuntimeError('Database must have one loopback binding')
    (path/'database-port.json').write_text(json.dumps({'ports': [int(port)]}))
    (path/'database-ownership.json').write_text(json.dumps({'containerId':config['Id'],'name':name,'image':IMAGE,'binding':'127.0.0.1:'+port,'label':LABEL+'='+name}))
    print(json.dumps({'ownedContainer':config['Id'],'binding':'127.0.0.1:'+port}),flush=True)
    if scenario in {CANCELLATION, FUNDING, TWO_JOURNEYS, PACKAGE_BOOKING, OPERATIONAL}:
        print(json.dumps({'postgresql': cancellation_database_version(name, env)}), flush=True)
    else:
        for _ in range(100):
            ready = subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'cinenest_test'], capture_output=True, timeout=3)
            if ready.returncode == 0:
                break
            time.sleep(.2)
        else:
            raise RuntimeError('Owned database startup timeout')
    env.update(CINENEST_DB_URL='jdbc:postgresql://127.0.0.1:'+port+'/cinenest_test',
               CINENEST_DB_USER='cinenest_test', CINENEST_DB_PASSWORD=password,
               CINENEST_E2E_RUN=str(path), CINENEST_E2E_SOURCE=call(['git','rev-parse','HEAD']), MAVEN_USER_HOME=str(ROOT/'.cache/maven'), NEXT_TELEMETRY_DISABLED='1')
    # Only local caches; no npm install, Maven download or image pull.
    if scenario == OPERATIONAL:
        # Explicit in-session retries only. Retain each failed database and logs;
        # never provision a second container or delete prior immutable evidence.
        for attempt in itertools.count(1):
            if time.monotonic() >= active_deadline-90:raise RuntimeError("Operational runtime budget exhausted; cleanup required")
            try:
                run_stage('backend-journey', [*maven, '-Dtest='+test, 'test'], ROOT/'backend', env, path,
                          min(timeout, max(1, int(active_deadline-time.monotonic()))))
                break
            except RuntimeError:
                archive=path/('attempt-'+str(attempt));archive.mkdir(mode=0o700)
                for filename in ('result.json','private-backend-journey.log','private-browser.log'):
                    if (path/filename).is_file():shutil.copyfile(path/filename,archive/filename)
                print(json.dumps({'operationalAttempt':attempt,'status':'FAIL','retry':'AWAITING_EXPLICIT_REQUEST_SAME_CONTAINER'}),flush=True)
                request=path/'retry-request.json'
                until=min(active_deadline-90,time.monotonic()+120)
                while not request.exists() and time.monotonic()<until:time.sleep(.2)
                if not request.exists():raise
                if request.is_symlink() or json.loads(request.read_text()) != {'retryAfterAttempt':attempt}:raise RuntimeError('Invalid bounded retry request')
                request.unlink()
                database='cinenest_retry_'+str(attempt)
                call(['docker','exec','--env','PGPASSWORD',name,'createdb','-h','127.0.0.1','-U','cinenest_test',database],env=dict(env,PGPASSWORD=password))
                env['CINENEST_DB_URL']='jdbc:postgresql://127.0.0.1:'+port+'/'+database
                for filename in ('result.json','command.json','reply.json'):
                    if (path/filename).is_file():(path/filename).unlink()
    else:
        try:
            run_stage('backend-journey', [*maven, *admin_qa_options(scenario), '-Dtest='+test, 'test'], ROOT/'backend', env, path, timeout)
        except RuntimeError:
            if scenario == PACKAGE_BOOKING and (path/'result.json').is_file():
                print((path/'result.json').read_text(), flush=True)
            raise
    if scenario == OPERATIONAL:
        retained=ROOT/'.cache'/(name+'-operational-evidence');retained.mkdir(mode=0o700,exist_ok=True)
        for archive in path.glob('attempt-*'):
            shutil.copytree(archive,retained/archive.name,dirs_exist_ok=True)
    browser_result = (path/'result.json').read_text()
    if scenario == OPERATIONAL:
        verify_operational_results(json.loads(browser_result))
    elif scenario == CANCELLATION:
        verify_cancellation_results(json.loads(browser_result))
    elif scenario == FUNDING:
        verify_funding_results(json.loads(browser_result))
    elif scenario == TWO_JOURNEYS:
        verify_two_journey_results(json.loads(browser_result))
    elif scenario == PACKAGE_BOOKING:
        verify_package_booking_results(json.loads(browser_result))
    print(browser_result, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--scenario', choices=['campaign','bank-instructions','h1',SUPERVISED,ADMIN_QA,CANCELLATION,FUNDING,TWO_JOURNEYS,PACKAGE_BOOKING,OPERATIONAL], default='campaign')
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
        supervised = args.scenario in SUPERVISED_SCENARIOS
        versions = preflight(supervised=True) if supervised else preflight()
        print(json.dumps({'preflight': 'TOOLS_PRESENT', 'versions': versions,
                          'execution': 'NOT_RUN', 'resourceEnforcement': 'NOT_YET_VERIFIED'}), flush=True)
        if not args.execute:
            return 0
        path = Path(tempfile.mkdtemp(prefix='cinenest-e2e-'))
        os.chmod(path, 0o700)
        (path/'ownership.json').write_text(json.dumps({'name': path.name, 'scenario': args.scenario}))
        script = str(Path(__file__).resolve())
        # The service kills/reaps descendants on exit/timeout; ExecStopPost owns DB cleanup even after SIGKILL.
        command = ['systemd-run', '--user', '--wait', '--pipe', '--collect', '--unit='+path.name,
                   '--working-directory='+str(ROOT), '--property=CPUQuota=300%', '--property=MemoryMax=7G',
                   '--property=MemorySwapMax=0', '--property=TasksMax=1024', '--property=RuntimeMaxSec=600',
                   '--property=TimeoutStopSec=30', '--property=KillMode=control-group',
                   '--property=ExecStopPost='+sys.executable+' "'+script+'" --cleanup "'+str(path)+'"']
        if args.scenario == OPERATIONAL:
            command[command.index('--property=RuntimeMaxSec=600')] = '--property=RuntimeMaxSec=1710'
        if supervised:
            command[command.index('--property=RuntimeMaxSec=600')] = f'--property=RuntimeMaxSec={SUPERVISED_ACTIVE_SECONDS}'
            print('Supervised synthetic session: close the Rooms or Admin tab to finish early.', flush=True)
        keys = ['PATH', 'JAVA_HOME', 'PLAYWRIGHT_MODULE_PATH', 'DOCKER_HOST']
        if supervised:
            keys += ['DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY', 'XDG_RUNTIME_DIR']
        for key in keys:
            if key in os.environ:
                command += ['--setenv='+key+'='+os.environ[key]]
        command += [sys.executable, '-B', script, '--inside', str(path)]
        result = subprocess.run(command, timeout=1800 if supervised or args.scenario == OPERATIONAL else 640)
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
