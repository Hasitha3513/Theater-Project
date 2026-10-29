"""Owner-authorized Unit A only. One cached local container/volume, no application or DB."""
from pathlib import Path
import argparse, datetime, fcntl, hashlib, json, os, re, secrets, shutil, signal, subprocess, tempfile, time
ROOT=Path(__file__).resolve().parents[1]
IMAGE='chrislusf/seaweedfs:4.50@sha256:c2658820e763294a9e9de7b36c81e59b879e3459148351aee4e31a126ca64860'
TASK='CN-R1-ADMIN-ADVERTISING-001-A'
MAX_DATA=128*1024*1024
# Conservative accounting for bounded tmpfs (8 MiB) and Docker log (1 MiB)
# outside the measured bind directory; not a filesystem quota.
EXTERNAL_DATA_RESERVE=9*1024*1024

def measure(root):
    if root.is_symlink() or not root.is_dir():raise RuntimeError('BUDGET_UNMEASURABLE')
    for p in root.rglob('*'):
        if p.is_symlink() or not (p.is_file() or p.is_dir()):raise RuntimeError('BUDGET_UNMEASURABLE')
    totals=[]
    for flag,mult in [('-sb',1),('-sk',1024)]:
        r=subprocess.run(['du',flag,'--',str(root)],capture_output=True,text=True,timeout=2,check=True)
        totals.append(int(r.stdout.split()[0])*mult)
    value=max(totals)+EXTERNAL_DATA_RESERVE
    if value>MAX_DATA:raise RuntimeError('MONITORED_BUDGET_EXCEEDED')
    return value

def owned_manifest(m,run):
    if not re.fullmatch('[a-f0-9]{32}',m.get('run','')) or m.get('task')!=TASK or m.get('image')!=IMAGE:raise RuntimeError('OWNERSHIP_MISMATCH')
    name='cn-ads-a-'+m['run']
    if m.get('name')!=name or run.name!=name or run.is_symlink() or run.resolve()!=run:raise RuntimeError('OWNERSHIP_MISMATCH')
    return name

def owned_labels(info,m):
    labels=info.get('Labels',info.get('Config',{}).get('Labels',{})) or {}
    if labels.get('com.cinenest.task')!=TASK or labels.get('com.cinenest.run')!=m['run']:raise RuntimeError('OWNERSHIP_MISMATCH')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--execute',action='store_true');args=parser.parse_args()
    if not args.execute:parser.error('Explicit --execute required')
    if os.environ.get('DOCKER_HOST')!='unix:///var/run/docker.sock':raise SystemExit('QUALIFIED_LOCAL_DOCKER_REQUIRED')
    java=Path(os.environ.get('JAVA_HOME',''))/'bin/java'
    if not java.is_file() or 'version "25' not in subprocess.run([str(java),'-version'],capture_output=True,text=True,check=True).stderr:raise SystemExit('JAVA25_REQUIRED')
    os.umask(0o077);cache=ROOT/'.cache/advertising-media-a';cache.mkdir(parents=True,exist_ok=True,mode=0o700)
    lock=os.open(cache/'.lock',os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
    try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:raise SystemExit('UNIT_A_ALREADY_RUNNING')
    run_id=secrets.token_hex(16);name='cn-ads-a-'+run_id;run=cache/name;run.mkdir(mode=0o700);data=run/'data';data.mkdir(mode=0o700)
    source_files=[Path(__file__),ROOT/'backend/src/main/java/com/cinenest/rooms/internal/AdvertisingMediaStore.java',ROOT/'backend/src/test/java/com/cinenest/rooms/internal/LocalAdvertisingMediaTestAdapter.java',ROOT/'backend/src/test/java/com/cinenest/rooms/internal/AdvertisingMediaStorageContractTest.java']
    source_hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}
    manifest={'sourceBase':subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip(),'sourceHashes':source_hashes,'run':run_id,'task':TASK,'name':name,'image':IMAGE,'started':datetime.datetime.now(datetime.timezone.utc).isoformat(),'limits':{'versionsIncludingMarkers':10,'objectBytes':2*1024*1024,'submittedBytes':20*1024*1024,'monitoredBytes':MAX_DATA,'cpu':1,'memoryBytes':1024**3,'pids':128,'executionSeconds':900,'cleanupSeconds':30},'quota':'MONITORED_NOT_KERNEL_FILESYSTEM_QUOTA'}
    (run/'manifest.json').write_text(json.dumps(manifest,indent=2));owned_manifest(manifest,run)
    deadline=time.monotonic()+900;cleanup_deadline=None;volume=False;container=False;child=None;result={'status':'FAILED','scope':'ROOMS_TEST_ONLY','awsQualified':False,'publicDelivery':False,'productionWiring':False};cleanup={};high=0
    env={k:v for k,v in os.environ.items() if not k.startswith(('CINENEST_','QUAL_','AD_MEDIA_'))};env['DOCKER_HOST']='unix:///var/run/docker.sock'
    def command(argv,check=True,timeout=5):
        remaining=(cleanup_deadline if cleanup_deadline is not None else deadline)-time.monotonic()
        if remaining<=0:raise RuntimeError('BOUNDED_DEADLINE_EXCEEDED')
        return subprocess.run(argv,env=env,capture_output=True,text=True,check=check,timeout=min(timeout,remaining))
    def docker(*args,**kw):return command(['docker',*args],**kw)
    def inspect(kind):
        r=docker(*(['volume','inspect',name] if kind=='volume' else ['inspect',name]),check=False)
        return json.loads(r.stdout)[0] if r.returncode==0 else None
    def stop_signal(*_):raise KeyboardInterrupt()
    for sig in (signal.SIGINT,signal.SIGTERM,signal.SIGHUP):signal.signal(sig,stop_signal)
    try:
        if docker('ps','-a','-q','--filter','label=com.cinenest.task='+TASK).stdout.strip() or docker('volume','ls','-q','--filter','label=com.cinenest.task='+TASK).stdout.strip():raise RuntimeError('PREVIOUS_OWNED_RESOURCES_NEED_REVIEW')
        image=json.loads(docker('image','inspect',IMAGE).stdout)[0]
        if image.get('Id')!=IMAGE.split('@')[1]:raise RuntimeError('CACHED_PINNED_IMAGE_MISMATCH')
        if inspect('volume') is not None or inspect('container') is not None:raise RuntimeError('RESOURCE_ALREADY_EXISTS')
        access=secrets.token_hex(12);secret=secrets.token_urlsafe(32)
        config=run/'s3.json';config.write_text(json.dumps({'identities':[{'name':'unit-a','credentials':[{'accessKey':access,'secretKey':secret}],'actions':['Admin','Read','Write','List']}]}));config.chmod(0o600)
        master=run/'master.toml';master.write_text('[master.volume_growth]\ncopy_1 = 1\n');master.chmod(0o600)
        startup=run/'start.sh';startup.write_text("\n".join([
            "#!/bin/sh","set -eu","mkdir -p /data/master /data/volume /data/filer","pids=""",
            "trap 'kill $pids 2>/dev/null || true; wait' TERM INT HUP",
            "weed master -ip=127.0.0.1 -ip.bind=127.0.0.1 -port=9333 -port.grpc=19333 -mdir=/data/master -volumeSizeLimitMB=128 -telemetry=false &",
            'pids="$pids $!"',
            "weed volume -ip=127.0.0.1 -ip.bind=127.0.0.1 -port=8080 -port.grpc=18080 -mserver=127.0.0.1:9333 -dir=/data/volume -max=1 &",
            'pids="$pids $!"',
            "weed filer -ip=127.0.0.1 -ip.bind=127.0.0.1 -port=8888 -port.grpc=18888 -master=127.0.0.1:9333 -defaultStoreDir=/data/filer &",
            'pids="$pids $!"',
            "weed s3 -ip.bind=0.0.0.0 -port=8333 -filer=127.0.0.1:8888 -config=/run/s3.json &",
            'pids="$pids $!"',
            'while true; do for p in $pids; do kill -0 "$p" 2>/dev/null || { kill $pids 2>/dev/null || true; wait; exit 1; }; done; sleep 0.2; done',""
        ]));startup.chmod(0o600)

        docker('volume','create','--label','com.cinenest.task='+TASK,'--label','com.cinenest.run='+run_id,'--driver','local','--opt','type=none','--opt','o=bind','--opt','device='+str(data),name);volume=True
        info=inspect('volume');owned_labels(info,manifest)
        if info.get('Options',{}).get('device')!=str(data):raise RuntimeError('VOLUME_BINDING_MISMATCH')
        high=max(high,measure(run))
        docker('run','--detach','--pull','never','--name',name,'--label','com.cinenest.task='+TASK,'--label','com.cinenest.run='+run_id,'--hostname',name,'--user',str(os.getuid())+':'+str(os.getgid()),'--cpus','1','--memory','1g','--pids-limit','128','--cap-drop','ALL','--security-opt','no-new-privileges','--entrypoint','/bin/sh','--env','GOMAXPROCS=1','--read-only','--workdir','/data','--tmpfs','/tmp:rw,size=8m,mode=1777','--log-opt','max-size=1m','--log-opt','max-file=1','--publish','127.0.0.1::8333','--mount','type=volume,src='+name+',dst=/data','--mount','type=bind,src='+str(config)+',dst=/run/s3.json,readonly','--mount','type=bind,src='+str(master)+',dst=/data/master.toml,readonly','--mount','type=bind,src='+str(startup)+',dst=/run/start.sh,readonly',IMAGE,'/run/start.sh',timeout=15);container=True
        info=inspect('container');owned_labels(info,manifest);manifest['containerId']=info['Id'];(run/'manifest.json').write_text(json.dumps(manifest,indent=2))
        if info['Config']['Image']!=IMAGE or not any(x.get('Name')==name and x.get('Destination')=='/data' for x in info['Mounts']):raise RuntimeError('CONTAINER_BINDING_MISMATCH')
        port=docker('port',name,'8333/tcp').stdout.strip()
        if not re.fullmatch(r'127\.0\.0\.1:[0-9]+',port):raise RuntimeError('NON_LOOPBACK_PORT')
        # Fixed local health probe; no credentials/response data retained.
        import urllib.request
        for _ in range(150):
            high=max(high,measure(run))
            try:
                urllib.request.urlopen('http://'+port+'/',timeout=.5)
                break
            except urllib.error.HTTPError:break
            except OSError:time.sleep(.2)
        else:raise RuntimeError('STORAGE_STARTUP_FAILED')
        for _ in range(150):
            high=max(high,measure(run))
            health=docker('exec',name,'wget','-q','-O','-','http://127.0.0.1:9333/cluster/status',check=False)
            if health.returncode==0 and json.loads(health.stdout).get('IsLeader') is True:break
            time.sleep(.2)
        else:raise RuntimeError('MASTER_LEADERSHIP_NOT_READY')
        env.update(CINENEST_AD_MEDIA_A='true',AD_MEDIA_RUN_ID=run_id,AD_MEDIA_RUN_ROOT=str(run),AD_MEDIA_ENDPOINT='http://'+port,AD_MEDIA_ACCESS=access,AD_MEDIA_SECRET=secret,MAVEN_USER_HOME=str(ROOT/'.cache/maven'))
        with (run/'tests.log').open('w') as log:
            child=subprocess.Popen([str(ROOT/'backend/mvnw'),'-B','-o','-Dmaven.repo.local='+str(ROOT/'.cache/m2'),'-Dtest=AdvertisingMediaStorageContractTest','test'],cwd=ROOT/'backend',env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            while child.poll() is None:
                if time.monotonic()>=deadline:raise RuntimeError('EXECUTION_DEADLINE_EXCEEDED')
                high=max(high,measure(run))
                marker=run/'cleanup.started'
                if marker.exists():
                    cleanup_deadline=cleanup_deadline or time.monotonic()+max(0,30-(time.time()-int(marker.read_text())/1000))
                    if cleanup_deadline-time.monotonic()<8:raise RuntimeError('CLEANUP_RESERVATION_REACHED')
                time.sleep(.2)
            if child.returncode!=0:raise RuntimeError('CONTRACT_TEST_FAILED')
        evidence=json.loads((run/'contract-results.json').read_text())
        if evidence.get('status')!='PASS' or evidence.get('objectsAndBucketAbsent') is not True:raise RuntimeError('MISSING_QUALIFICATION_EVIDENCE')
        if any(hashlib.sha256((ROOT/n).read_bytes()).hexdigest()!=h for n,h in source_hashes.items()):raise RuntimeError('SOURCE_CHANGED_DURING_QUALIFICATION')
        result.update(status='PASS',contract=evidence,sourceBase=manifest['sourceBase'],sourceHashes=source_hashes)
    except (Exception,KeyboardInterrupt) as e:
        marker=run/'cleanup.started'
        if cleanup_deadline is None:
            cleanup_deadline=time.monotonic()+max(0,30-(time.time()-int(marker.read_text())/1000)) if marker.exists() else time.monotonic()+30
        result.update(status='FAILED',failureStage=str(e) if isinstance(e,RuntimeError) else type(e).__name__)
        try:
            info=inspect('container')
            if info is not None:
                owned_labels(info,manifest)
                result['providerProcessState']={k:info.get('State',{}).get(k) for k in ['Status','OOMKilled','ExitCode']}
                text=docker('logs','--tail','300',name,check=False).stderr.lower()
                result['serverDiagnostics']={code:text.count(token) for code,token in {'PERMISSION_DENIED':'permission denied','READ_ONLY_FILESYSTEM':'read-only file system','NO_FREE_VOLUME':'no free volume','ASSIGN_FAILED':'fail to assign','CONNECTION_REFUSED':'connection refused','ALLOCATION_FAILED':'failed to allocate','DISK_SPACE':'no space left','BIND_FAILED':'address already in use','NO_WRITABLE_VOLUME':'no writable volume','RPC_UNAVAILABLE':'rpc error','VOLUME_GROW':'grow'}.items()}
                allowed=set('rpc error code unavailable unknown deadlineexceeded desc connection transport while dialing failed to do connect handshake http response bad gateway proxy no such host read server preface refused context deadline exceeded service volume grpc master filer cannot assign allocate invalid argument permission denied writable directories space free disk full capacity not enough volumes grow topology data node maximum reached create lock timeout resource exhausted file exists address already in use local socket eof'.split())
                result['rpcDiagnosticWords']=[[w for w in re.findall('[a-z]+',line) if w in allowed] for line in text.splitlines() if 'rpc error' in line][:8]
                allowed.update('listen listening bind failed operation permitted supported only filesystem system path long socket error invalid argument network address broken pipe file directory'.split())
                result['listenerFailureWords']=[[w for w in re.findall('[a-z]+',line) if w in allowed] for line in text.splitlines() if ('socket' in line or 'listen' in line) and ('fail' in line or 'error' in line)][:8]
                stat=docker('exec',name,'stat','-c','%a %u','/tmp',check=False)
                result['tmpPermissions']=stat.stdout.strip() if re.fullmatch(r'[0-7]+ [0-9]+\n?',stat.stdout) else 'UNVERIFIED'


        except Exception:result['serverDiagnostics']={'INSPECTION_UNAVAILABLE':1}

    finally:
        marker=run/'cleanup.started'
        if marker.exists():cleanup_deadline=cleanup_deadline or time.monotonic()+max(0,30-(time.time()-int(marker.read_text())/1000))
        else:cleanup_deadline=cleanup_deadline or time.monotonic()+30
        def cleanup_expired(*_):raise RuntimeError('CLEANUP_DEADLINE_EXCEEDED')
        signal.signal(signal.SIGALRM,cleanup_expired);signal.setitimer(signal.ITIMER_REAL,max(.001,cleanup_deadline-time.monotonic()))
        try:
            owned_manifest(manifest,run)
            if child is not None and child.poll() is None:
                os.killpg(child.pid,signal.SIGTERM)
                try:child.wait(timeout=3)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=2)
            info=inspect('container')
            if info is not None:
                owned_labels(info,manifest)
                if manifest.get('containerId') is not None and info['Id']!=manifest['containerId']:raise RuntimeError('CONTAINER_ID_MISMATCH')
                if info['Config']['Image']!=IMAGE:raise RuntimeError('IMAGE_OWNERSHIP_MISMATCH')
                docker('stop','--time','2',name,timeout=5);docker('rm',name);container=False
            info=inspect('volume')
            if info is not None:
                owned_labels(info,manifest)
                if info.get('Options',{}).get('device')!=str(data):raise RuntimeError('VOLUME_OWNERSHIP_MISMATCH')
                docker('volume','rm',name);volume=False
            if inspect('container') is not None or inspect('volume') is not None:raise RuntimeError('RESOURCE_ABSENCE_NOT_VERIFIED')
            if data.resolve()!=run/'data' or data.is_symlink():raise RuntimeError('DATA_OWNERSHIP_MISMATCH')
            try:high=max(high,measure(run))
            except Exception:cleanup['finalMeasurement']='UNAVAILABLE_OR_OVER_BUDGET'
            shutil.rmtree(data)
            if data.exists():raise RuntimeError('OBJECT_DATA_ABSENCE_NOT_VERIFIED')
            cleanup={'verified':True,'containerAbsent':True,'volumeAbsent':True,'ownedDataNamespaceAbsent':True,'verification':'EXACT_S3_CLEANUP_WHEN_AVAILABLE_AND_OWNED_VOLUME_DATA_REMOVAL','seconds':round(30-(cleanup_deadline-time.monotonic()),3)}
            (run/'s3.json').unlink(missing_ok=True)
        except Exception as e:
            cleanup={'verified':False,'failure':str(e) if isinstance(e,RuntimeError) else type(e).__name__};result['status']='FAILED'
        signal.setitimer(signal.ITIMER_REAL,0)
        result.update(cleanup=cleanup,monitoredHighWaterBytes=high,quota='MONITORED_NOT_KERNEL_FILESYSTEM_QUOTA',runReference=run.name,ended=datetime.datetime.now(datetime.timezone.utc).isoformat())
        (run/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2),flush=True)
    return 0 if result['status']=='PASS' and cleanup.get('verified') else 1
if __name__=='__main__':raise SystemExit(main())
