"""One opt-in LOCAL packaging qualification. No GCP calls or production connections.

Builder <=2.5 CPU/4GiB + supervisor <=0.5 CPU/1GiB; stop builder before
runtime (Postgres1CPU/1GiB,backend1CPU/2GiB,frontend0.5CPU/1GiB). <=3CPU/7GiB.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.error

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
LABEL = 'com.cinenest.cloud-run-qualification'
# Exact repository-pinned image, not a similarly named mutable tag.
POSTGRES = 'postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336'
BUILDER = 'moby/buildkit@sha256:7583a0667093d941826bcd1d2878d6a360dfd08a82c93e6d6a6f6cce78801e12'


TRACE = []
EXPECTED = ['bounded-temporary-builder','backend-image-build-nonroot','frontend-image-build-nonroot','image-runtime-hygiene','empty-schema-denied','postgres-tls-44-migrations-readonly-runtime-role','missing-secret','synthetic-denied','tls-denied','backend-health-and-protected-route-denials','frontend-config-denied','frontend-assets-upstream-origin-and-csrf-denials','db-failure-recovery-no-schema-or-account-mutation','graceful-shutdown']

def complete(results):
    return len(results)==len(EXPECTED) and {r['id'] for r in results}==set(EXPECTED) and all(r['status']=='PASS' for r in results)

def call(args, timeout=30, env=None, input=None, deadline=None):
    if deadline is not None:
        timeout = min(timeout, deadline-time.monotonic()-240)
        if timeout <= 0: raise RuntimeError('Cleanup reserve reached')
    hidden = [v.split('=',1)[1] for v in args if 'PASSWORD=' in v]
    hidden += [str(v) for k,v in (env or {}).items() if 'PASSWORD' in k]
    def redacted(value):
        for secret in hidden:
            if secret:value=value.replace(secret,'[REDACTED]')
        return value
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, env=env, input=input)
    except subprocess.TimeoutExpired as failure:
        stderr=failure.stderr or ''
        if isinstance(stderr,bytes):stderr=stderr.decode(errors='replace')
        TRACE.append({'command':[redacted(a) for a in args],'exitCode':None,'timedOut':True,'stderr':redacted(stderr)})
        raise RuntimeError('Required command timed out: '+Path(args[0]).name) from None
    TRACE.append({'command':[redacted(a) for a in args],'exitCode':result.returncode,'stdout':redacted(result.stdout),'stderr':redacted(result.stderr)})
    if result.returncode:
        raise RuntimeError('Command failed: '+Path(args[0]).name+'; exit='+str(result.returncode)+'; '+redacted(result.stderr[-1600:]))
    return result.stdout+result.stderr if args[:2]==['docker','logs'] else result.stdout


def docker(*args, **kwargs): return call(['docker', *args], **kwargs)

def inventory():
    return {kind: sorted(docker(*args).splitlines()) for kind,args in {
        'containers':['ps','-aq','--no-trunc'], 'volumes':['volume','ls','-q'],
        'networks':['network','ls','--no-trunc','--format','{{.ID}}']}.items()}


def allowed_limits(cpu, memory):
    q,p=cpu.split()
    return q!='max' and memory.strip()!='max' and 0<int(q)<=int(p)//2 and 0<int(memory)<=1024**3


def preflight():
    if os.environ.get('DOCKER_HOST')!='unix:///var/run/docker.sock': raise RuntimeError('Explicit system Docker endpoint required')
    registry=json.loads((ROOT/'knowledge/execution/change-sets.json').read_text())['cloudRunPackaging']
    if registry['status']!='READY' or registry['productionAuthorization']!='NONE': raise RuntimeError('Bounded READY admission required')
    if isinstance(registry.get('qualification'),dict) and registry['qualification'].get('sessionsRemaining')==0:raise RuntimeError('Runtime authorization consumed; fresh Owner approval required')
    group=Path('/sys/fs/cgroup')/Path('/proc/self/cgroup').read_text().strip().split('::')[1].lstrip('/')
    if not allowed_limits((group/'cpu.max').read_text(),(group/'memory.max').read_text()): raise RuntimeError('Supervisor must enforce <=0.5CPU/1GiB')
    if shutil.disk_usage('/tmp').free<4*1024**3 or shutil.disk_usage('/').free<1024**3: raise RuntimeError('Insufficient isolated build storage; never prune shared resources')
    if json.loads(docker('info','--format','{{json .CgroupVersion}}'))!='2': raise RuntimeError('cgroup v2 required')
    if call(['git','-C',str(ROOT),'rev-parse','HEAD']).strip()!=registry['baseline']:raise RuntimeError('Exact uncommitted baseline required')
    import runpy
    validator=runpy.run_path(str(ROOT/'knowledge/execution/validate_execution.py'))
    if validator['cloud_run_errors']():raise RuntimeError('Exact source/governance fingerprint check failed')
    return registry


def http(port,path,method='GET',headers=None):
    request=urllib.request.Request('http://127.0.0.1:'+str(port)+path,method=method,headers=headers or {})
    try:
        with urllib.request.urlopen(request,timeout=8) as response:return response.status,response.read().decode(),dict(response.headers)
    except urllib.error.HTTPError as response:return response.code,response.read().decode(),dict(response.headers)


def wait_http(port,path,status,deadline):
    until=min(deadline-240,time.monotonic()+150)
    while time.monotonic()<until:
        try:
            result=http(port,path)
            if result[0]==status:return result
        except (OSError,TimeoutError):pass
        time.sleep(1)
    raise RuntimeError('Expected HTTP readiness unavailable')




def postgres_sql(docker_call, password, container, statement):
    # docker exec must attach stdin; otherwise psql silently receives no statement.
    return docker_call('exec','-i','-e','PGPASSWORD='+password,container,'psql','-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-U','fixture_owner','-d','cloudrun_qualification','-At',input=statement,timeout=20).strip()


def wait_postgres_tls(probe, deadline):
    """Reload acknowledgment is not TLS readiness; require authenticated verify-full TCP."""
    until=min(deadline-240,time.monotonic()+30)
    last=None
    while time.monotonic()<until:
        try:
            result=probe()
            if not (result.strip().startswith('18.6') and result.strip().endswith('|t')):
                raise RuntimeError('Unexpected PostgreSQL TLS version/session result')
            return result
        except RuntimeError as error:
            if 'server does not support SSL, but SSL was required' not in str(error):raise
            last=error
        time.sleep(0.25)
    raise RuntimeError('Authenticated PostgreSQL TLS readiness deadline exceeded') from last


class InternalNetworkRelay:
    """Bounded loopback-only TCP endpoints into one inspected task-owned internal network.

    Preserve Docker --internal isolation; never rely on unpublished Docker port bindings.
    TLS is opaque end-to-end, with certificate verification performed by the client.
    """
    def __init__(self, address):
        import ipaddress
        ip=ipaddress.ip_address(address)
        if ip.version!=4 or not ip.is_private or ip.is_unspecified or ip.is_multicast or ip.is_link_local:
            raise ValueError('Private task-container IPv4 address required')
        self.address=str(ip);self.ports={};self.loop=None;self.stop=None;self.error=None
        import threading
        self.ready=threading.Event();self.thread=threading.Thread(target=self._run,daemon=True,name='task-loopback-relay')

    def _run(self):
        import asyncio
        try:asyncio.run(self._serve())
        except BaseException as error:self.error=error;self.ready.set()

    async def _serve(self):
        import asyncio
        self.loop=asyncio.get_running_loop();self.stop=asyncio.Event();clients=set();servers=[]
        async def connection(reader,writer,target):
            task=asyncio.current_task()
            if len(clients)>=16:
                writer.close();await writer.wait_closed();return
            clients.add(task);remote=None
            async def copy(source,destination):
                while True:
                    data=await asyncio.wait_for(source.read(65536),30)
                    if not data:
                        if destination.can_write_eof():destination.write_eof()
                        return
                    destination.write(data);await asyncio.wait_for(destination.drain(),30)
            try:
                incoming,remote=await asyncio.wait_for(asyncio.open_connection(self.address,target),5)
                await asyncio.gather(copy(reader,remote),copy(incoming,writer))
            except (OSError,TimeoutError):pass
            finally:
                for stream in [writer,remote]:
                    if stream is not None:stream.close()
                clients.discard(task)
        try:
            for target in [5432,8081,8080]:
                server=await asyncio.start_server(lambda r,w,p=target:connection(r,w,p),'127.0.0.1',0)
                servers.append(server);self.ports[target]=server.sockets[0].getsockname()[1]
            self.ready.set();await self.stop.wait()
        finally:
            for server in servers:server.close();await server.wait_closed()
            for task in list(clients):task.cancel()
            if clients:await asyncio.gather(*list(clients),return_exceptions=True)

    def start(self):
        self.thread.start()
        if not self.ready.wait(5) or self.error is not None:
            self.close();raise RuntimeError('Task loopback relay startup failed') from self.error
        return self

    def close(self):
        if self.loop is not None and self.stop is not None and self.thread.is_alive():
            self.loop.call_soon_threadsafe(self.stop.set)
        if self.thread.ident is not None:self.thread.join(5)
        if self.thread.is_alive():raise RuntimeError('Task loopback relay cleanup failed')


def execute(evidence):
    state=preflight()
    evidence.mkdir(parents=True,exist_ok=True)
    approval=state['runtime'].get('approvalReferenceSha256',state['authority']['proposalSha256'])
    if len(approval)!=64 or any(c not in '0123456789abcdef' for c in approval):raise RuntimeError('Invalid runtime approval reference')
    marker=ROOT/'.cache'/('cloud-run-session-'+approval+'.json')
    marker.parent.mkdir(exist_ok=True)
    fd=os.open(marker,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    with os.fdopen(fd,'w') as f:json.dump({'consumed':True,'scope':state['id']},f)
    core=[n for n in state['orderedPaths'] if not n.startswith('knowledge/')]
    snapshot=hashlib.sha256(b''.join(n.encode()+b'\0'+(ROOT/n).read_bytes() for n in sorted(core))).hexdigest()
    source_epoch=call(['git','-C',str(ROOT),'show','-s','--format=%ct',state['baseline']]).strip()
    assert source_epoch.isdecimal()
    start=time.monotonic(); deadline=start+1800
    name='cn-pack-'+secrets.token_hex(5)
    private=Path(tempfile.mkdtemp(prefix=name+'-'));private.chmod(0o700)
    config=private/'docker-config';config.mkdir()
    env=dict(os.environ,DOCKER_CONFIG=str(config))
    before=inventory();before_images=set(docker('image','ls','-aq','--no-trunc').splitlines());results=[{'id':k,'status':'NOT_RUN'} for k in EXPECTED];owned=[];images=[];builder_created=False;volume_created=False;network_created=False
    builder=name+'-build';node=builder+'0';volume='buildx_buildkit_'+node+'_state';builder_container='buildx_buildkit_'+node
    cache=private/'build-state';cache.mkdir()
    password=secrets.token_hex(24);runtime_password=secrets.token_hex(24)
    pg=name+'-db';network=name+'-net';relay=None
    def record(check):
        next(r for r in results if r['id']==check)['status']='PASS'
        print('PASS '+check,flush=True)
    def d(*args,**kw):return docker(*args,env=env,deadline=deadline,**kw)
    def sql(text):return postgres_sql(d,password,pg,text)
    def run_container(suffix,extra,memory,cpus):
        container=name+'-'+suffix
        d('run','-d','--name',container,'--label',LABEL+'='+name,'--memory',memory,'--memory-swap',memory,'--cpus',cpus,'--pids-limit','128',*extra)
        owned.append(container)
        limits=json.loads(d('inspect',container))[0]['HostConfig']
        assert limits['NanoCpus']==int(float(cpus)*10**9) and limits['MemorySwap']==limits['Memory'] and limits['PidsLimit']==128
        assert limits['ReadonlyRootfs'] and limits['CapDrop']==['ALL'] and 'no-new-privileges' in limits['SecurityOpt']
        return container
    def remove(container):
        check=json.loads(docker('inspect',container))[0]
        if check['Config']['Labels'].get(LABEL)!=name:raise RuntimeError('Unowned container removal refused')
        docker('rm','-f',container,timeout=10);owned.remove(container)
    def release_builder():
        nonlocal builder_created,volume_created
        if builder_created:
            docker('buildx','rm','--keep-state','--force',builder,env=env,timeout=25)
            builder_created=False
        if volume_created:
            docker('volume','rm',volume,env=env,timeout=10)
            # Delete only the private cache after its daemon and volume are gone.
            docker('run','--rm','--name',name+'-cleanup','--label',LABEL+'='+name,'--network','none','--cpus','0.5','--memory','256m','--memory-swap','256m','--pids-limit','64','-v',str(cache)+':/owned','--entrypoint','sh',BUILDER,'-c','find /owned -mindepth 1 -delete && chown '+str(os.getuid())+':'+str(os.getgid())+' /owned && chmod 700 /owned',env=env,timeout=25)
            volume_created=False
    try:
        # Persistent BuildKit state is bound only to this private temporary directory.
        d('volume','create','--label',LABEL+'='+name,'--driver','local','--opt','type=none','--opt','device='+str(cache),'--opt','o=bind',volume);volume_created=True
        d('buildx','create','--name',builder,'--driver','docker-container','--driver-opt','image='+BUILDER+',memory=4g,memory-swap=4g,cpu-period=100000,cpu-quota=250000',timeout=30);builder_created=True
        d('buildx','inspect',builder,'--bootstrap',timeout=180)
        d('update','--pids-limit','256',builder_container)
        info=json.loads(d('inspect',builder_container))[0]
        limits=info['HostConfig']
        assert limits['Memory']==4*1024**3 and limits['MemorySwap']==4*1024**3 and limits['CpuQuota']==250000 and limits['CpuPeriod']==100000 and limits['PidsLimit']==256
        assert any(m.get('Name')==volume for m in info['Mounts'])
        volume_info=json.loads(d('volume','inspect',volume))[0]
        assert volume_info['Options']=={'device':str(cache),'o':'bind','type':'none'} and volume_info['Labels'].get(LABEL)==name
        record('bounded-temporary-builder')
        for app in ['backend','frontend']:
            image=name+'-'+app+':qualified';images.append(image)
            command=['buildx','build','--builder',builder,'--platform','linux/amd64','--progress','plain','--load','--build-arg','SOURCE_DATE_EPOCH='+source_epoch,'--build-arg','CINENEST_BUILD_ID='+snapshot,'--metadata-file',str(evidence/(app+'-build.json')),'--tag',image,'-f',str(ROOT/app/'Dockerfile'),str(ROOT)]
            output=d(*command,timeout=660)
            (evidence/(app+'-build.log')).write_text(output)
            image_info=json.loads(d('image','inspect',image))[0]
            assert image_info['Config']['User']=='10001:10001' and image_info['Config']['Labels']['com.cinenest.packaging.snapshot']==snapshot
            d(*command,timeout=90)  # Same frozen inputs, cached repeat; not a clean-room rebuild.
            assert json.loads(d('image','inspect',image))[0]['Id']==image_info['Id']
            assert not any(v.startswith(('CINENEST_DB_PASSWORD=','CINENEST_DB_USER=')) for v in image_info['Config'].get('Env',[]))
            (evidence/(app+'-image.json')).write_text(json.dumps(image_info,indent=2))
            record(app+'-image-build-nonroot')
        d('stop',builder_container,timeout=20)
        release_builder()  # Release RAM-backed build cache before database/application phase.
        import zipfile
        for app,image in zip(['backend','frontend'],images):
            c=run_container('inspect-'+app,['--network','none','--cap-drop','ALL','--security-opt','no-new-privileges','--read-only','--entrypoint','sh',image,'-c','find /app -type f'],'256m','0.5')
            assert d('wait',c,timeout=20).strip()=='0'
            names=d('logs',c).splitlines()
            forbidden={'.git','.cache','.env','.env.local','tests','__pycache__','target'}
            assert not any(any(part in forbidden or part.startswith('.env') for part in Path(n).parts) for n in names)
            (evidence/(app+'-runtime-files.json')).write_text(json.dumps(names,indent=2))
            if app=='backend':
                jar=private/'application.jar';d('cp',c+':/app/application.jar',str(jar))
                with zipfile.ZipFile(jar) as archive:
                    entries=archive.namelist()
                    assert not any(n.endswith(('SyntheticJourneyIT.class','CloudRunRuntimeIT.class','CloudRunRuntimeTest.class')) for n in entries)
                    assert not any(n.startswith('BOOT-INF/lib/') and any(x in n for x in ['junit-','mockito-','assertj-']) for n in entries)
                jar.unlink()
            remove(c)
        record('image-runtime-hygiene')
        # TLS material is ephemeral, never added to a build context or image.
        call(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(private/'server.key'),'-out',str(private/'server.crt'),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],deadline=deadline)
        (private/'server.key').chmod(0o600);(private/'server.crt').chmod(0o644)
        # Container postgres owns the private key; host remains able to remove its owned directory.
        d('network','create','--internal','--label',LABEL+'='+name,network);network_created=True
        ports=[]  # Internal Docker networks may not publish host ports; use bounded loopback relay.
        d('run','-d','--name',pg,'--label',LABEL+'='+name,'--network',network,'--cpus','1','--memory','1g','--memory-swap','1g','--pids-limit','128','--tmpfs','/var/lib/postgresql:rw,size=496m','--shm-size','16m',*ports,
          '-e','POSTGRES_USER=fixture_owner','-e','POSTGRES_PASSWORD='+password,'-e','POSTGRES_DB=cloudrun_qualification',POSTGRES)
        owned.append(pg)
        for _ in range(50):
            try:
                if d('exec',pg,'pg_isready','-U','fixture_owner','-d','cloudrun_qualification',timeout=5):break
            except RuntimeError:time.sleep(1)
        d('cp',str(private/'server.key'),pg+':/tmp/server.key');d('cp',str(private/'server.crt'),pg+':/tmp/server.crt')
        d('exec','-u','0',pg,'sh','-c','chown postgres:postgres /tmp/server.key /tmp/server.crt && chmod 600 /tmp/server.key')
        for statement in ["ALTER SYSTEM SET ssl='on';","ALTER SYSTEM SET ssl_cert_file='/tmp/server.crt';","ALTER SYSTEM SET ssl_key_file='/tmp/server.key';","SELECT pg_reload_conf();"]:
            response=sql(statement)
            assert response == ('t' if statement.startswith('SELECT') else 'ALTER SYSTEM'), 'PostgreSQL configuration command was not acknowledged'
        tls=wait_postgres_tls(lambda:d('exec','-e','PGPASSWORD='+password,pg,'psql','-X','-v','ON_ERROR_STOP=1','-At','host=127.0.0.1 user=fixture_owner dbname=cloudrun_qualification sslmode=verify-full sslrootcert=/tmp/server.crt', '-c',"SELECT current_setting('server_version'),ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid();"),deadline)
        assert tls.strip().startswith('18.6') and tls.strip().endswith('|t')
        (evidence/'postgres-authenticated-tls.txt').write_text(tls)
        info=json.loads(d('inspect',pg))[0];host=info['HostConfig'];assert host['NanoCpus']==10**9 and host['Memory']==1024**3 and host['PidsLimit']==128 and host['ShmSize']==16*1024**2
        assert host['Tmpfs']=={'/var/lib/postgresql':'rw,size=496m'}
        network_info=json.loads(d('network','inspect',network))[0]
        assert network_info['Internal'] and network_info['Labels'].get(LABEL)==name
        assert set(info['NetworkSettings']['Networks'])=={network}
        address=info['NetworkSettings']['Networks'][network]['IPAddress']
        relay=InternalNetworkRelay(address).start()
        db_port=relay.ports[5432];backend_port=relay.ports[8081];frontend_port=relay.ports[8080]
        (evidence/'loopback-relay.json').write_text(json.dumps({'targetTaskContainer':pg,'internalNetwork':network,'targetAddress':address,'bindAddress':'127.0.0.1','ports':relay.ports,'maximumConnections':16,'threads':1},indent=2))
        # Prove host loopback-to-internal-network TLS before launching either application.
        import ssl,struct
        context=ssl.create_default_context(cafile=str(private/'server.crt'))
        with socket.create_connection(('127.0.0.1',db_port),timeout=10) as connection:
            connection.sendall(struct.pack('!II',8,80877103))
            assert connection.recv(1)==b'S','PostgreSQL relay refused TLS; no plaintext fallback'
            with context.wrap_socket(connection,server_hostname='127.0.0.1') as secure:
                (evidence/'relay-tls.json').write_text(json.dumps({'certificateVerified':True,'hostnameVerified':'127.0.0.1','protocol':secure.version(),'peerCertificateSha256':hashlib.sha256(secure.getpeercert(binary_form=True)).hexdigest(),'plaintextFallback':False},indent=2))
        empty=run_container('empty-schema',['--network','container:'+pg,'--cap-drop','ALL','--security-opt','no-new-privileges','--read-only','--tmpfs','/tmp:rw,size=64m,uid=10001,gid=10001','-v',str(private/'server.crt')+':/run/secrets/db-ca.pem:ro','-e','PORT=8081','-e','CINENEST_BIND_ADDRESS=0.0.0.0','-e','CINENEST_DB_POOL_MAX=2','-e','CINENEST_DB_USER=fixture_owner','-e','CINENEST_DB_PASSWORD='+password,'-e','CINENEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/cloudrun_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem',images[0]],'512m','0.5')
        assert d('wait',empty,timeout=150).strip()!='0'
        logs=d('logs',empty);assert password not in logs and ('separate migration approval required' in logs or 'Validate failed' in logs)
        (evidence/'empty-schema.log').write_text(logs)
        assert sql("SELECT to_regclass('public.flyway_schema_history') IS NULL;")=='t'
        remove(empty);record('empty-schema-denied')
        dburl='jdbc:postgresql://127.0.0.1:'+str(db_port)+'/cloudrun_qualification?sslmode=verify-full&sslrootcert='+str(private/'server.crt')
        test_env=dict(os.environ,CINENEST_CLOUD_RUN_DB_TEST='true',CINENEST_PACKAGING_DB_URL=dburl,CINENEST_PACKAGING_DB_OWNER='fixture_owner',CINENEST_PACKAGING_DB_PASSWORD=password,CINENEST_PACKAGING_RUNTIME_PASSWORD=runtime_password)
        output=call([str(ROOT/'backend/mvnw'),'-B','-o','-f',str(ROOT/'backend/pom.xml'),'-Dmaven.repo.local='+str(ROOT/'.cache/m2'),'-Dtest=CloudRunRuntimeIT#prepareIsolatedSchema','test'],env=test_env,timeout=180,deadline=deadline)
        (evidence/'postgres-migration-test.log').write_text(output);record('postgres-tls-44-migrations-readonly-runtime-role')
        baseline=sql('SELECT md5(string_agg(row_to_json(h)::text,\'\' ORDER BY installed_rank)) FROM public.flyway_schema_history h;')
        common=['--network','container:'+pg,'--cap-drop','ALL','--security-opt','no-new-privileges','--read-only','--tmpfs','/tmp:rw,size=64m,uid=10001,gid=10001',
                '-v',str(private/'server.crt')+':/run/secrets/db-ca.pem:ro','-e','PORT=8081','-e','CINENEST_BIND_ADDRESS=0.0.0.0','-e','CINENEST_DB_POOL_MAX=2','-e','CINENEST_DB_USER=cloudrun_runtime','-e','CINENEST_DB_PASSWORD='+runtime_password,
                '-e','CINENEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/cloudrun_qualification?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem']
        for suffix,extra in [('missing-secret',['-e','CINENEST_DB_PASSWORD=']),('synthetic-denied',['-e','CINENEST_BANK_LEDGER_ENABLED=true']),('tls-denied',['-e','CINENEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/cloudrun_qualification?sslmode=disable'])]:
            c=run_container(suffix,common+extra+[images[0]],'512m','0.5')
            exit_code=d('wait',c,timeout=55).strip();assert exit_code!='0'
            logs=d('logs',c);assert password not in logs and runtime_password not in logs and 'Cloud Run configuration unavailable or denied' in logs
            (evidence/(suffix+'.log')).write_text(logs);remove(c);record(suffix)
        backend=run_container('backend',common+[images[0]],'2g','1')
        code,body,headers=wait_http(backend_port,'/health/ready',200,deadline);assert json.loads(body)=={'status':'UP'} and headers.get('Cache-Control')=='no-store'
        assert http(backend_port,'/health/live')[0]==200
        assert http(backend_port,'/api/accounts/me')[0] in (401,403)
        assert http(backend_port,'/health/ready','POST')[0] in (401,403)
        record('backend-health-and-protected-route-denials')
        for suffix,origin in [('missing-origin',None),('external-origin','http://unapproved.example:8081')]:
            extra=[] if origin is None else ['-e','CINENEST_AUTH_ORIGIN='+origin]
            c=run_container(suffix,['--network','container:'+pg,'--cap-drop','ALL','--security-opt','no-new-privileges','--read-only','--tmpfs','/tmp:rw,size=64m,uid=10001,gid=10001','-e','PORT=8080',*extra,images[1]],'512m','0.5')
            assert d('wait',c,timeout=35).strip()!='0'
            assert 'Cloud Run frontend configuration unavailable or denied' in d('logs',c)
            remove(c)
        record('frontend-config-denied')
        frontend=run_container('frontend',['--network','container:'+pg,'--cap-drop','ALL','--security-opt','no-new-privileges','--read-only','--tmpfs','/tmp:rw,size=64m,uid=10001,gid=10001','-e','PORT=8080','-e','CINENEST_AUTH_ORIGIN=http://127.0.0.1:8081',images[1]],'1g','0.5')
        assert json.loads(wait_http(frontend_port,'/healthz',200,deadline)[1])=={'status':'UP'}
        assert http(frontend_port,'/')[0]==200
        assert http(frontend_port,'/api/accounts/me')[0] in (401,403)
        assert http(frontend_port,'/api/session/logout','POST',{'Origin':'https://wrong.example'})[0]==403
        assert http(frontend_port,'/api/session/logout','POST',{'Origin':'http://127.0.0.1:'+str(frontend_port)})[0]==403
        record('frontend-assets-upstream-origin-and-csrf-denials')
        sql("ALTER ROLE cloudrun_runtime NOLOGIN; SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='cloudrun_runtime';")
        assert json.loads(wait_http(backend_port,'/health/ready',503,deadline)[1])=={'status':'DOWN'}
        assert http(backend_port,'/health/live')[0]==200
        sql('ALTER ROLE cloudrun_runtime LOGIN;');wait_http(backend_port,'/health/ready',200,deadline)
        assert sql('SELECT md5(string_agg(row_to_json(h)::text,\'\' ORDER BY installed_rank)) FROM public.flyway_schema_history h;')==baseline
        assert sql('SELECT count(*) FROM cinenest_identity.account;')=='0'
        record('db-failure-recovery-no-schema-or-account-mutation')
        for c in [frontend,backend]:
            logs=d('logs',c);assert password not in logs and runtime_password not in logs
            (evidence/(c.rsplit('-',1)[1]+'-runtime.log')).write_text(logs)
            tick=time.monotonic();d('stop','-t','8',c,timeout=15);assert time.monotonic()-tick<10
            info=json.loads(d('inspect',c))[0];assert not info['State']['OOMKilled'] and info['State']['ExitCode']!=137
            remove(c)
        record('graceful-shutdown')
    except BaseException:
        if pg in owned:
            try:(evidence/'postgres-failure.log').write_text(docker('logs',pg,env=env,timeout=10).replace(password,'[REDACTED]').replace(runtime_password,'[REDACTED]'))
            except Exception as error:print('ERROR PostgreSQL diagnostic capture '+type(error).__name__,flush=True)
        first=next((r for r in results if r['status']=='NOT_RUN'),None)
        if first is not None:first['status']='ERROR'
        raise
    finally:
        # Exact owned names only. No global prune, broad stop, or adoption of other resources.
        for c in list(reversed(owned)):
            try:remove(c)
            except Exception as error:print('ERROR owned cleanup '+type(error).__name__,flush=True)
        if relay is not None:
            relay.close()
            for port in relay.ports.values():
                with socket.socket() as probe:assert probe.connect_ex(('127.0.0.1',port))!=0,'Task loopback listener remains'
            (evidence/'loopback-cleanup.json').write_text(json.dumps({'threadStopped':not relay.thread.is_alive(),'listenersClosed':True}))
        release_builder()
        if network_created:docker('network','rm',network,env=env,timeout=10)
        for image in images:
            if docker('image','ls','--filter','reference='+image,'-q',env=env,timeout=10).strip():
                docker('image','rm',image,env=env,timeout=10)
        builder_image=json.loads(docker('image','inspect',BUILDER,env=env))[0]['Id']
        if builder_image not in before_images:docker('image','rm',BUILDER,env=env,timeout=10)
        shutil.rmtree(private)
        (evidence/'commands.json').write_text(json.dumps(TRACE,indent=2))
        after=inventory();(evidence/'inventories.json').write_text(json.dumps({'before':before,'after':after},indent=2))
        assert before==after,'Unexplained Docker inventory change'
        seconds=time.monotonic()-start;assert seconds<=1800
        (evidence/'runtime.json').write_text(json.dumps({'snapshot':snapshot,'repeatBuild':'IDENTICAL_IMAGE_ID_WITH_CACHE_REUSE_NOT_CLEAN_ROOM','secondsIncludingCleanup':seconds,'results':results,'counts':{k:sum(r['status']==k for r in results) for k in ['PASS','FAIL','ERROR','NOT_RUN']},'qualification':'PASS' if complete(results) else 'ERROR','cleanup':'PASS','cloudConnectivity':'NOT_RUN','production':'NONE'},indent=2))
    (evidence/'commands.json').write_text(json.dumps(TRACE,indent=2))
    assert complete(results)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute',action='store_true');parser.add_argument('--evidence',type=Path)
    args=parser.parse_args()
    if not args.execute:print('NOT_RUN: explicit bounded admission, resource preflight and --execute required');return 0
    if args.evidence is None or not args.evidence.is_absolute():parser.error('Absolute task evidence path required')
    signal.signal(signal.SIGTERM,lambda *_:(_ for _ in ()).throw(RuntimeError('Termination; cleanup required')))
    try:execute(args.evidence)
    except BaseException:
        args.evidence.mkdir(parents=True,exist_ok=True)
        (args.evidence/'commands.json').write_text(json.dumps(TRACE,indent=2))
        raise
    return 0


if __name__=='__main__':raise SystemExit(main())
