"""Opt-in, resource-bounded real HTTP/PostgreSQL/Chromium credential qualification.

--prepare compiles/builds only. --execute requires exact SQL admission and consumes
one disposable session. No cloud commands, provider delivery or real accounts.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import re
import runpy
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from xml.etree import ElementTree
sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336'
PREFIX = 'cinenest-customer-credential-'
LABEL = 'com.cinenest.customer-credential-owner'
MIGRATION = ROOT/'backend/src/main/resources/db/migration/V46__customer_credential_lifecycle.sql'
MAX_SECONDS = 1800
ACTIVE_SECONDS = 1710
AUTHORIZATION_SHA = 'ae78fc23623b7831e98c11e23940cdeb3de0aa394aff396db85f06a7eda5880e'
COMPLETION_SHA = '900f00985aa97a02506f9e90b3239e396b8fe755ad11d8a26acf6e1b6e83ad1f'
COMPLETION_SOURCE = 'Owner reply call_70ac2c82c29f43f59a1a622fd46bb88d: Approve exact security qualification supplement'
UNIT_TESTS = 'CustomerCredentialHttpSecurityTest,CustomerCredentialServiceTest,CustomerCredentialApplicationArchitectureTest,CustomerCredentialLifecycleTest,CustomerCredentialPortsTest,CustomerCredentialSecurityTest,CredentialEpochSessionsTest,LocalIdentityBoundaryTest,LocalCredentialRecoveryServiceTest'
PG_TESTS = 'CustomerCredentialPostgresTest,CustomerCredentialHttpSecurityTest,CustomerCredentialJourneyIT'
BROWSER_GROUPS = ['configured-real-backend-forms','registration-postgres-and-private-test-mail','verification-single-use-and-pending-login-denial','login-own-account-and-session-security','generic-recovery-request-and-private-test-mail','reset-invalidates-old-sessions-and-password','csrf-command-replay-conflict-and-no-secret-storage']


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def local_docker_environment(env=None):
    value = dict(os.environ if env is None else env)
    if value.get('DOCKER_HOST') not in [None, '', 'unix:///var/run/docker.sock'] or value.get('DOCKER_CONTEXT') not in [None, '', 'default']:
        raise RuntimeError('Only the approved local Docker daemon is permitted')
    value['DOCKER_HOST'] = 'unix:///var/run/docker.sock'
    value.pop('DOCKER_CONTEXT', None)
    return value


def run(args, *, env=None, timeout=30, cwd=None):
    if args[0] == 'docker':
        env = local_docker_environment(env)
    return subprocess.run(args, cwd=cwd or ROOT, env=env, text=True, capture_output=True,
                          timeout=timeout, check=True).stdout.strip()


def runtime_environment():
    allowed = ['PATH','HOME','JAVA_HOME','PLAYWRIGHT_MODULE_PATH','LANG','LC_ALL']
    value = {key:os.environ[key] for key in allowed if os.environ.get(key)}
    value.update(DOCKER_HOST='unix:///var/run/docker.sock',NEXT_TELEMETRY_DISABLED='1',
                 CINENEST_BUILD_CPUS='2',UV_THREADPOOL_SIZE='1',RAYON_NUM_THREADS='1')
    return value


def private_json(path, value):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    with os.fdopen(os.open(path, flags, 0o600), 'w') as out:
        json.dump(value, out, indent=2)
        out.write('\n')


def owned_path(path):
    p = Path(path)
    if (not p.is_absolute() or p.parent != Path('/tmp') or not p.name.startswith(PREFIX)
            or not re.fullmatch(PREFIX+r'[A-Za-z0-9_-]+', p.name)
            or p.is_symlink() or not p.is_dir() or p.resolve() != p
            or p.stat().st_uid != os.getuid() or p.stat().st_mode & 0o077):
        raise RuntimeError('Private task-owned /tmp evidence directory required')
    return p


def new_evidence():
    path = Path(tempfile.mkdtemp(prefix=PREFIX, dir='/tmp'))
    path.chmod(0o700)
    private_json(path/'ownership.json', {'owner': secrets.token_hex(16), 'name': path.name,
                                        'classification': 'SYNTHETIC_TEST_ONLY'})
    return path


def inventory():
    return {kind: sorted(run(['docker', *args]).splitlines()) for kind, args in {
        'containers': ['ps', '-aq', '--no-trunc'],
        'volumes': ['volume', 'ls', '-q'],
        'networks': ['network', 'ls', '-q', '--no-trunc']}.items()}


def source_pin():
    registry = json.loads((ROOT/'knowledge/execution/status.json').read_text())
    unit = registry['customerIdentityVertical']
    paths = [p for p in unit['orderedPaths'] if not p.startswith('knowledge/')]
    return hashlib.sha256(json.dumps({p: digest(ROOT/p) for p in paths},
                                    sort_keys=True).encode()).hexdigest()


def source_admission():
    module = runpy.run_path(str(ROOT/'knowledge/execution/validate_execution.py'))
    guard = module.get('VERTICAL_errors')
    if guard is None or guard(publication=False):
        raise RuntimeError('Exact READY source admission/fingerprints are unverified')
    unit = json.loads((ROOT/'knowledge/execution/status.json').read_text())['customerIdentityVertical']
    if (len(unit.get('orderedPaths', [])) != 40
            or unit.get('authority', {}).get('requestSha256') != AUTHORIZATION_SHA):
        raise RuntimeError('Exact forty-path source authorization differs')
    return unit


def consumption_path():
    return ROOT/'.cache/customer-credential-sessions'/AUTHORIZATION_SHA/'consumed.json'


def completion_marker(unit):
    approval=unit.get('completionSession',{})
    now=time.time();at=approval.get('recordedAtEpoch',0);expires=approval.get('launchExpiresEpoch',0)
    if (approval.get('status')!='APPROVED' or approval.get('source')!=COMPLETION_SOURCE
            or approval.get('requestSha256')!=COMPLETION_SHA or approval.get('sessions')!=1
            or approval.get('maximumSecondsIncludingCleanup')!=MAX_SECONDS
            or approval.get('cloudExecution') is not False
            or approval.get('runtimeSourceSha256')!=source_pin()
            or approval.get('sqlSha256')!=digest(MIGRATION)
            or not isinstance(at,(int,float)) or not isinstance(expires,(int,float))
            or not 0<=now-at<=86400 or not now<expires<=at+86400):
        raise RuntimeError('Exact fresh local completion admission missing, stale or drifted')
    original=consumption_path()
    if original.is_symlink() or not original.is_file():raise RuntimeError('Original consumed marker must remain intact')
    prior=json.loads(original.read_text())
    if prior.get('authoritySha256')!=AUTHORIZATION_SHA or prior.get('maximumSeconds')!=MAX_SECONDS:
        raise RuntimeError('Original consumed marker authority differs')
    return original.parent/('completion-'+COMPLETION_SHA)/'consumed.json'


def consume_session(path):
    unit=source_admission();authority=AUTHORIZATION_SHA
    if unit.get('localSessionsConsumed')==1:
        marker=completion_marker(unit);authority=COMPLETION_SHA
    elif unit.get('localSessionsConsumed')==0:
        marker=consumption_path()
    else:raise RuntimeError('Approved local session is already consumed')
    marker.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    if marker.parent.is_symlink() or marker.parent.resolve()!=marker.parent:
        raise RuntimeError('Unsafe session-consumption custody path')
    try:
        private_json(marker,{'evidence':str(path),'epoch':time.time(),
                             'authoritySha256':authority,'maximumSeconds':MAX_SECONDS})
    except FileExistsError:
        raise RuntimeError('Approved local session already consumed, including another evidence directory') from None


def sql_admission(reference):
    p = Path(reference)
    if not p.is_absolute() or p.is_symlink() or not p.is_file() or p.stat().st_uid != os.getuid():
        raise RuntimeError('Protected exact SQL-admission reference required')
    receipt = json.loads(p.read_text())
    if (receipt.get('status') != 'APPROVED' or not receipt.get('source')
            or receipt.get('migration') != str(MIGRATION.relative_to(ROOT))
            or receipt.get('sha256') != digest(MIGRATION)
            or receipt.get('scope') != 'DISPOSABLE_LOCAL_QUALIFICATION_ONLY'
            or receipt.get('cloudExecution') is not False):
        raise RuntimeError('Exact local-only SQL fingerprint admission missing')
    unit = json.loads((ROOT/'knowledge/execution/status.json').read_text())['customerIdentityVertical']
    admitted = unit.get('sqlAdmission', {})
    if (unit.get('status') != 'READY' or unit.get('productionAuthorization') != 'NONE'
            or unit.get('cloudSessions') != 0 or unit.get('localSessionsAuthorized') != 1
            or admitted.get('status') != 'APPROVED' or admitted.get('source') != receipt['source']
            or admitted.get('sha256') != receipt['sha256']):
        raise RuntimeError('Execution registry and exact SQL admission differ')
    return receipt


def bounded_group():
    relative = next((line.split(':', 2)[2] for line in Path('/proc/self/cgroup').read_text().splitlines()
                     if line.startswith('0::')), None)
    if relative is None:
        raise RuntimeError('Unified bounded application cgroup required')
    group = Path('/sys/fs/cgroup')/relative.lstrip('/')
    cpu = (group/'cpu.max').read_text().split()
    memory = (group/'memory.max').read_text().strip()
    tasks = (group/'pids.max').read_text().strip()
    swap = (group/'memory.swap.max').read_text().strip()
    if (cpu[0] == 'max' or not 0 < int(cpu[0]) <= 3*int(cpu[1]) or memory == 'max'
            or not 0 < int(memory) <= 7*1024**3 or tasks == 'max'
            or not 0 < int(tasks) <= 1024 or swap != '0'):
        raise RuntimeError('Application limits must be <=3 CPU/7 GiB/1024 tasks with no swap')


def supervision_group(path, mode):
    relative = next(line.split(':',2)[2] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
    group = Path('/sys/fs/cgroup')/relative.lstrip('/')
    unit = path.name+'-'+mode+'.service'
    if group.name != unit:
        raise RuntimeError('Exact task-owned supervised unit required; direct --inside invocation denied')
    return group


def toolchain():
    # This new local feature runner follows the approved Maven [25,26) contract.
    # Hosted CI retains its separate exact Temurin archive pin; no JDK is installed/replaced.
    local_docker_environment()
    if any(os.environ.get(k) for k in ['NODE_OPTIONS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS',
                                      '_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS']):
        raise RuntimeError('Tool option injection is prohibited')
    java = Path(os.environ.get('JAVA_HOME',''))/'bin/java'
    node, npm = shutil.which('node'), shutil.which('npm')
    pw = Path(os.environ.get('PLAYWRIGHT_MODULE_PATH','/nonexistent'))
    if not java.is_file() or not node or not npm:
        raise RuntimeError('Existing approved Java25/Node/npm tools required; no installation is performed')
    result = subprocess.run([str(java),'-version'],capture_output=True,text=True,timeout=10,check=True)
    version = re.search(r'version "(25(?:[.][0-9]+)*)',result.stdout+result.stderr)
    if not version or '<version>[25,26)</version>' not in (ROOT/'backend/pom.xml').read_text():
        raise RuntimeError('Approved Java25 toolchain contract differs')
    if run([node,'--version']) != 'v24.22.0' or run([npm,'--version']) != '11.19.1':
        raise RuntimeError('Pinned Node24.22.0/npm11.19.1 required')
    if json.loads((pw/'package.json').read_text())['version'] != '1.62.1':
        raise RuntimeError('Pinned existing Playwright1.62.1 required')
    entries = json.loads((pw.parent/'playwright-core/browsers.json').read_text())['browsers']
    browser = next(x for x in entries if x['name']=='chromium-headless-shell')
    if (browser['revision'],browser['browserVersion']) != ('1234','151.0.7922.34'):
        raise RuntimeError('Pinned Chromium provenance differs')
    binary = Path.home()/'.cache/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-linux64/chrome-headless-shell'
    if not binary.is_file():
        binary = Path.home()/'.cache/ms-playwright/chromium_headless_shell-1234/chrome-linux/headless_shell'
    if not binary.is_file():
        raise RuntimeError('Pinned existing Chromium binary required; no download is performed')
    wrapper = (ROOT/'backend/.mvn/wrapper/maven-wrapper.properties').read_text()
    if '/3.9.16/' not in wrapper or not re.search(r'distributionSha256Sum=[0-9a-f]{64}',wrapper):
        raise RuntimeError('Qualified Maven wrapper/checksum changed')
    if run(['docker','image','inspect',IMAGE,'--format','{{.Id}}']) != IMAGE.split('@')[1]:
        raise RuntimeError('Pinned PostgreSQL image must already be cached')
    if run(['docker','info','--format','{{.CgroupVersion}}']) != '2':
        raise RuntimeError('Docker cgroup v2 required')
    return {'java':version.group(1),'javaBinarySha256':digest(java),'node':'24.22.0','npm':'11.19.1',
            'maven':'3.9.16','playwright':'1.62.1','chromium':'151.0.7922.34','image':IMAGE}


def stage(name, args, cwd, env, evidence, deadline):
    remaining = int(deadline-time.monotonic())
    if remaining <= 0:
        raise RuntimeError('Qualification active deadline exceeded')
    log = evidence/(name+'.log')
    with os.fdopen(os.open(log,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as out:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=out, stderr=subprocess.STDOUT,
                                timeout=remaining)
    if result.returncode:
        raise RuntimeError(name+' failed; protected local log retained')


def lint_context(path):
    # Biome framework auto-detection differs beneath the historical .cache checkout.
    # Copy exact Git/source-admitted bytes to a normal project context; never change rules.
    target = path/'lint-context'
    target.mkdir(mode=0o700)
    unit = json.loads((ROOT/'knowledge/execution/status.json').read_text())['customerIdentityVertical']
    tracked = run(['git','ls-files','frontend']).splitlines()
    names = sorted(set(tracked)|{p for p in unit['orderedPaths'] if p.startswith('frontend/')})
    hashes = {}
    for name in names:
        original = ROOT/name
        if (not original.is_file() or original.is_symlink()
                or not original.resolve().is_relative_to(ROOT.resolve())):
            raise RuntimeError('Unsafe frontend lint source')
        destination = target/name
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(original,destination)
        if digest(destination) != digest(original):
            raise RuntimeError('Lint context source mismatch')
        hashes[name] = digest(original)
    shutil.copyfile(ROOT/'.gitignore',target/'.gitignore')
    (target/'frontend/node_modules').symlink_to(ROOT/'frontend/node_modules',target_is_directory=True)
    run(['git','-c','init.defaultBranch=main','init','--quiet',str(target)])
    private_json(path/'lint-context.json', {'sourceSha256':hashes,'configSha256':digest(ROOT/'frontend/biome.json'),
                                           'rulesChanged':False,'additionalFilesExcludedFromLint':False,
                                           'reason':'Exact frontend source in nonhidden framework-detection context'})
    return target/'frontend'


def prepare(path):
    bounded_group()
    supervision_group(path, 'prepare')
    source_admission()
    tools = toolchain()
    pin = source_pin()
    deadline = time.monotonic()+ACTIVE_SECONDS
    env = runtime_environment()
    env['NEXT_TELEMETRY_DISABLED'] = '1'
    stage('backend-compile', ['./mvnw','-o','-B','test-compile'], ROOT/'backend', env, path, deadline)
    stage('backend-offline', ['./mvnw','-o','-B','-Dtest='+UNIT_TESTS,'test'], ROOT/'backend', env, path, deadline)
    stage('frontend-customer-tests', ['node','--import','./tests/tsx-loader.mjs','tests/customer-credential.test.mjs'], ROOT/'frontend',env,path,deadline)
    for task in ['build','typecheck','lint','test']:
        cwd = lint_context(path) if task == 'lint' else ROOT/'frontend'
        stage('frontend-'+task, ['npm','run',task], cwd, env, path, deadline)
    source_admission()
    if pin != source_pin():
        raise RuntimeError('Source changed during preparation; no prepared receipt issued')
    private_json(path/'prepared.json', {'sourceSha256': pin, 'classification':'SYNTHETIC_TEST_ONLY',
                                       'postgresOrChromiumStarted':False,'toolchain':tools})


def certificates(path, env):
    tls = path/'tls'
    tls.mkdir(mode=0o700)
    run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-sha256',
         '-subj','/CN=CineNest disposable test CA','-keyout',str(tls/'ca.key'),'-out',str(tls/'ca.crt')])
    run(['openssl','req','-newkey','rsa:2048','-nodes','-subj','/CN=localhost',
         '-keyout',str(tls/'server.key'),'-out',str(tls/'server.csr')])
    (tls/'extensions').write_text('subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n')
    run(['openssl','x509','-req','-days','1','-sha256','-in',str(tls/'server.csr'),
         '-CA',str(tls/'ca.crt'),'-CAkey',str(tls/'ca.key'),'-CAcreateserial',
         '-extfile',str(tls/'extensions'),'-out',str(tls/'server.crt')])
    run(['openssl','pkcs12','-export','-name','cinenest-customer','-inkey',str(tls/'server.key'),
         '-in',str(tls/'server.crt'),'-certfile',str(tls/'ca.crt'),'-out',str(tls/'server.p12'),
         '-passout','env:CINENEST_CUSTOMER_TLS_PASSWORD'], env=env)
    for p in tls.iterdir():
        p.chmod(0o600)
    env.update(CINENEST_CUSTOMER_TLS_P12=str(tls/'server.p12'),
               CINENEST_CUSTOMER_CA=str(tls/'ca.crt'), CINENEST_CUSTOMER_TLS_CERT=str(tls/'server.crt'),
               CINENEST_CUSTOMER_TLS_KEY=str(tls/'server.key'))
    return tls


def owned_container(path):
    owner = json.loads((path/'ownership.json').read_text())
    value = subprocess.run(['docker','container','inspect',owner['name']],capture_output=True,text=True,timeout=10,env=local_docker_environment())
    if value.returncode:
        if 'No such container' in value.stderr or 'No such object' in value.stderr:
            return None
        raise RuntimeError('Container absence is unverified')
    data, = json.loads(value.stdout)
    if data['Name'] != '/'+owner['name'] or data['Config'].get('Labels',{}).get(LABEL) != owner['owner']:
        raise RuntimeError('Refusing cleanup of an unrelated container')
    receipt = path/'database-ownership.json'
    if receipt.exists() and json.loads(receipt.read_text())['containerId'] != data['Id']:
        raise RuntimeError('Owned container identity drift')
    return data


def process_snapshot(group):
    processes = {}
    for member in group.rglob('cgroup.procs'):
        for value in member.read_text().splitlines():
            pid = int(value)
            if pid != os.getpid():
                try:
                    processes[str(pid)] = (Path('/proc')/str(pid)/'comm').read_text().strip()
                except FileNotFoundError:
                    pass
    return processes


def verify_container_limits(container):
    config = container['HostConfig']
    if (config.get('NanoCpus') != 10**9 or config.get('Memory') != 2**30
            or config.get('MemorySwap') != 2**30 or config.get('PidsLimit') != 128
            or config.get('ShmSize') != 16*1024**2
            or config.get('Tmpfs',{}).get('/var/lib/postgresql') != 'rw,size=496m'
            or container['Config'].get('Image') != IMAGE):
        raise RuntimeError('Actual database image or resource limits differ from approval')


def verify_reports(started):
    totals = {}
    for name in PG_TESTS.split(','):
        reports = list((ROOT/'backend/target/surefire-reports').glob('TEST-*.'+name+'.xml'))
        if len(reports) != 1 or reports[0].stat().st_mtime < started:
            raise RuntimeError('Fresh required test report missing: '+name)
        suite = ElementTree.parse(reports[0]).getroot()
        counts = {key:int(suite.get(key,'0')) for key in ['tests','failures','errors','skipped']}
        if counts['tests'] < 1 or any(counts[key] for key in ['failures','errors','skipped']):
            raise RuntimeError('Required test class failed or skipped: '+name)
        totals[name] = counts
    return totals


def wait_owned_container_removal(path, container_id):
    # Docker --rm may converge shortly after stop returns; never remove a replacement.
    deadline=time.monotonic()+5
    while True:
        current=owned_container(path)
        if current is None:return
        if current['Id']!=container_id:raise RuntimeError('Owned container identity drift during cleanup')
        if time.monotonic()>=deadline:raise RuntimeError('Owned database removal incomplete')
        time.sleep(0.1)


def cleanup(path):
    path = owned_path(path)
    errors = []
    try:
        container = owned_container(path)
        if container:
            run(['docker','stop','--time','5',container['Id']],timeout=15)
            wait_owned_container_removal(path,container['Id'])
    except Exception as error:
        errors.append(type(error).__name__+': '+str(error))
    for name in ['ca.key','server.key','server.p12','server.csr']:
        private = path/'tls'/name
        if private.is_file() and not private.is_symlink():
            private.unlink()
    try:
        reference = path/'process-group.json'
        if reference.exists():
            group = Path(json.loads(reference.read_text())['path'])
            if not group.is_relative_to('/sys/fs/cgroup') or group.name != path.name+'-execute.service':
                raise RuntimeError('Task process-group custody mismatch')
            processes = process_snapshot(group) if group.exists() else {}
        elif (path/'session-started.json').exists():
            raise RuntimeError('Task process-group evidence missing')
        else:
            processes = {}
        (path/'processes-after.json').write_text(json.dumps(processes,indent=2)+'\n')
        if processes:
            errors.append('Task cgroup still contains child processes; supervisor cleanup required')
    except Exception:
        errors.append('Task process cleanup unverified')
    before = path/'inventory-before.json'
    try:
        after = inventory()
        (path/'inventory-after.json').write_text(json.dumps(after,indent=2)+'\n')
        if before.exists() and json.loads(before.read_text()) != after:
            errors.append('Before/after Docker inventory differs; no unrelated cleanup attempted')
    except Exception:
        errors.append('After-state inventory unavailable')
    (path/'cleanup.json').write_text(json.dumps({'status':'FAIL' if errors else 'PASS','errors':errors},indent=2)+'\n')
    attempts = path/'cleanup-attempts.json'
    history = json.loads(attempts.read_text()) if attempts.exists() else []
    history.append({'status':'FAIL' if errors else 'PASS','errors':errors,'epoch':time.time()})
    attempts.write_text(json.dumps(history,indent=2)+'\n')
    if errors:
        raise RuntimeError('Cleanup incomplete; protected evidence retained')


def execute(path, reference):
    bounded_group()
    group = supervision_group(path, 'execute')
    source_admission()
    tools = toolchain()
    sql_admission(reference)
    prepared = json.loads((path/'prepared.json').read_text())
    if prepared.get('toolchain') != tools:
        raise RuntimeError('Local toolchain differs from prepared qualification inputs')
    if prepared['sourceSha256'] != source_pin():
        raise RuntimeError('Source differs from prepared source')
    if (path/'session-started.json').exists():
        raise RuntimeError('This local session is already consumed')
    before = inventory()
    private_json(path/'process-group.json', {'path':str(group),'unit':path.name+'-execute.service'})
    private_json(path/'processes-before.json', process_snapshot(group))
    private_json(path/'inventory-before.json', before)
    deadline = time.monotonic()+ACTIVE_SECONDS
    consume_session(path)
    private_json(path/'session-started.json', {'epoch':time.time(),'maximumSeconds':MAX_SECONDS,
                                            'sourceSha256':prepared['sourceSha256']})
    owner = json.loads((path/'ownership.json').read_text())
    env = runtime_environment()
    env.update(POSTGRES_PASSWORD=secrets.token_hex(24),CINENEST_CUSTOMER_TLS_PASSWORD=secrets.token_hex(24),
               CINENEST_CUSTOMER_RUN=str(path),CINENEST_DB_USER='cinenest_test',
               CINENEST_NODE=shutil.which('node'), CINENEST_CUSTOMER_CONTROL_KEY=secrets.token_hex(32),
               NEXT_TELEMETRY_DISABLED='1')
    env['CINENEST_DB_PASSWORD'] = env['POSTGRES_PASSWORD']
    env['CINENEST_CUSTOMER_SQL_SHA256'] = digest(MIGRATION)
    try:
        tls = certificates(path,env)
        # Root entrypoint copies private test keys into owned tmpfs and drops to postgres normally.
        entry = ('mkdir -p /tmp/cinenest-tls && cp /run/input-tls/ca.crt /run/input-tls/server.crt '
                 '/run/input-tls/server.key /tmp/cinenest-tls/ && chown -R postgres:postgres /tmp/cinenest-tls '
                 '&& chmod 600 /tmp/cinenest-tls/server.key && exec docker-entrypoint.sh postgres '
                 '-c ssl=on -c ssl_cert_file=/tmp/cinenest-tls/server.crt '
                 '-c ssl_key_file=/tmp/cinenest-tls/server.key -c ssl_ca_file=/tmp/cinenest-tls/ca.crt')
        run(['docker','run','--pull=never','--detach','--rm','--name',owner['name'],
             '--label',LABEL+'='+owner['owner'],'--cpus=1','--memory=1g','--memory-swap=1g',
             '--pids-limit=128','--shm-size=16m','--tmpfs','/var/lib/postgresql:rw,size=496m',
             '--tmpfs','/tmp:rw,size=32m','--mount','type=bind,source='+str(tls)+',target=/run/input-tls,readonly',
             '--publish','127.0.0.1::5432','--env','POSTGRES_DB=cinenest_test',
             '--env','POSTGRES_USER=cinenest_test','--env','POSTGRES_PASSWORD',
             '--entrypoint','sh',IMAGE,'-ec',entry],env=env,timeout=30)
        container = owned_container(path)
        verify_container_limits(container)
        bindings = container['NetworkSettings']['Ports']['5432/tcp']
        if len(bindings) != 1 or bindings[0]['HostIp'] != '127.0.0.1':
            raise RuntimeError('Database loopback binding mismatch')
        env['CINENEST_DB_URL'] = ('jdbc:postgresql://127.0.0.1:'+bindings[0]['HostPort']+
                                 '/cinenest_test?sslmode=verify-full&sslrootcert='+str(tls/'ca.crt'))
        private_json(path/'database-ownership.json', {'containerId':container['Id'],'name':owner['name'],
                                                     'jdbcUrl':env['CINENEST_DB_URL'],'port':bindings[0]['HostPort']})
        for _ in range(60):
            ready = subprocess.run(['docker','exec',owner['name'],'pg_isready','-U','cinenest_test'],
                                   capture_output=True,timeout=3,env=local_docker_environment(env))
            if ready.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise RuntimeError('PostgreSQL process readiness timeout; not TLS/auth proof')
        test_started = time.time()
        stage('postgres-http-chromium',['./mvnw','-o','-B','-Dtest='+PG_TESTS,'test'],ROOT/'backend',env,path,deadline)
        report_totals = verify_reports(test_started)
        private_json(path/'required-test-totals.json', report_totals)
        if not (path/'browser-results.json').is_file():
            raise RuntimeError('Chromium result missing; no browser PASS inferred')
        browser = json.loads((path/'browser-results.json').read_text())
        if (browser.get('status') != 'PASS' or browser.get('interceptedRequests') != 0
                or browser.get('interception') is not False or browser.get('cookieInjection') is not False
                or [g.get('id') for g in browser.get('groups',[])] != BROWSER_GROUPS
                or any(g.get('status') != 'PASS' for g in browser.get('groups',[]))):
            raise RuntimeError('Genuine backend Chromium qualification incomplete')
        private_json(path/'runtime-results.json', {'status':'PASS','sourceSha256':prepared['sourceSha256'],
                                                  'realBankOrEmail':'NOT_RUN_NOT_AUTHORIZED'})
    finally:
        cleanup(path)


def supervise(mode, path, reference):
    command = ['systemd-run','--user','--wait','--pipe','--collect','--unit='+path.name+'-'+mode,
               '--working-directory='+str(ROOT),'--property=CPUQuota=300%',
               '--property=MemoryMax=7G','--property=MemorySwapMax=0','--property=TasksMax=1024',
               '--property=RuntimeMaxSec='+str(ACTIVE_SECONDS),'--property=TimeoutStopSec=30',
               '--property=KillMode=control-group']
    if mode == 'execute':
        command.append('--property=ExecStopPost='+sys.executable+' "'+str(Path(__file__).resolve())+'" --cleanup "'+str(path)+'"')
    command += [sys.executable,str(Path(__file__).resolve()),'--inside',mode,'--evidence',str(path)]
    if reference:
        command += ['--sql-admission',reference]
    for key in ['JAVA_HOME','PATH','PLAYWRIGHT_MODULE_PATH','DOCKER_HOST']:
        if os.environ.get(key):
            command.insert(command.index(sys.executable), '--setenv='+key+'='+os.environ[key])
    result = subprocess.run(command,timeout=MAX_SECONDS)
    if result.returncode:
        raise RuntimeError('Bounded '+mode+' failed; no PASS claimed')


def main(argv=None):
    p = argparse.ArgumentParser()
    mode = p.add_mutually_exclusive_group()
    mode.add_argument('--prepare',action='store_true')
    mode.add_argument('--execute',action='store_true')
    mode.add_argument('--cleanup')
    mode.add_argument('--inside',choices=['prepare','execute'])
    p.add_argument('--evidence')
    p.add_argument('--sql-admission')
    args = p.parse_args(argv)
    if args.cleanup:
        cleanup(args.cleanup)
        return 0
    if not (args.prepare or args.execute or args.inside):
        print('NOT_RUN: explicit source, exact SQL admission and --execute required; no resources started')
        return 0
    if args.execute and not args.evidence:
        p.error('--execute requires prepared task evidence')
    path = owned_path(args.evidence) if args.evidence else new_evidence()
    operation = args.inside or ('execute' if args.execute else 'prepare')
    if operation == 'execute':
        if not args.sql_admission:
            p.error('--sql-admission is required before runtime')
        sql_admission(args.sql_admission)
    if args.inside:
        prepare(path) if operation == 'prepare' else execute(path,args.sql_admission)
    else:
        supervise(operation,path,args.sql_admission)
    print(json.dumps({'operation':operation,'evidence':str(path),'classification':'SYNTHETIC_TEST_ONLY'}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
