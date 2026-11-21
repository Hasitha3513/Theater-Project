"""Offline refusal/result contracts. These do not start containers or authorize a runtime."""
import importlib.util
from pathlib import Path
import unittest
import sys
sys.dont_write_bytecode = True
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('cloud_run',Path(__file__).with_name('verify-cloud-run.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class Boundaries(unittest.TestCase):
    def test_loopback_relay_rejects_public_or_invalid_targets(self):
        for address in ['8.8.8.8','0.0.0.0','224.0.0.1','::1','169.254.1.1']:
            with self.subTest(address=address),self.assertRaises(ValueError):m.InternalNetworkRelay(address)
    def test_loopback_relay_preserves_bytes_and_closes_all_listeners(self):
        import asyncio,socket,socketserver,threading
        class Echo(socketserver.BaseRequestHandler):
            def handle(self):
                while data:=self.request.recv(65536):self.request.sendall(data)
        class Server(socketserver.ThreadingTCPServer):
            daemon_threads=True
        server=Server(('127.0.0.1',0),Echo);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        original=asyncio.open_connection;targets=[];relay=m.InternalNetworkRelay('127.0.0.1')
        async def connect(host,port):
            targets.append((host,port));return await original('127.0.0.1',server.server_address[1])
        try:
            with patch('asyncio.open_connection',side_effect=connect):
                relay.start();self.assertEqual(set(relay.ports),{5432,8081,8080})
                for target,port in relay.ports.items():
                    with socket.create_connection(('127.0.0.1',port),timeout=2) as client:
                        payload=b'opaque-TLS-like-\x00\xff';client.sendall(payload);self.assertEqual(client.recv(1024),payload)
                self.assertEqual(set(targets),{('127.0.0.1',5432),('127.0.0.1',8081),('127.0.0.1',8080)})
                relay.close();self.assertFalse(relay.thread.is_alive())
                for port in relay.ports.values():
                    with socket.socket() as probe:self.assertNotEqual(probe.connect_ex(('127.0.0.1',port)),0)
        finally:
            relay.close();server.shutdown();server.server_close();thread.join(2)

    def test_postgres_statements_reach_psql_stdin(self):
        docker=unittest.mock.Mock(return_value='ALTER SYSTEM\n')
        self.assertEqual(m.postgres_sql(docker,'synthetic-secret','owned-db',"ALTER SYSTEM SET ssl='on';"),'ALTER SYSTEM')
        args=docker.call_args.args
        self.assertEqual(args[:3],('exec','-i','-e'))
        self.assertIn('ON_ERROR_STOP=1',args)
        self.assertEqual(docker.call_args.kwargs['input'],"ALTER SYSTEM SET ssl='on';")
        self.assertEqual(docker.call_args.kwargs['timeout'],20)
    def test_postgres_statement_failure_is_not_suppressed(self):
        docker=unittest.mock.Mock(side_effect=RuntimeError('SQL rejected'))
        with self.assertRaisesRegex(RuntimeError,'SQL rejected'):m.postgres_sql(docker,'synthetic-secret','owned-db','invalid SQL')

    def test_tls_reload_waits_for_authenticated_connection(self):
        probe=unittest.mock.Mock(side_effect=[RuntimeError('server does not support SSL, but SSL was required'),'18.6|t\n'])
        with patch.object(m.time,'sleep') as sleep:
            self.assertEqual(m.wait_postgres_tls(probe,m.time.monotonic()+500),'18.6|t\n')
            self.assertEqual(probe.call_count,2);sleep.assert_called_once_with(0.25)
    def test_tls_wait_does_not_mask_bad_certificate_or_authentication(self):
        for message in ['certificate verify failed','password authentication failed']:
            with self.subTest(message=message),patch.object(m.time,'sleep') as sleep:
                with self.assertRaisesRegex(RuntimeError,message):m.wait_postgres_tls(unittest.mock.Mock(side_effect=RuntimeError(message)),m.time.monotonic()+500)
                sleep.assert_not_called()
    def test_tls_wait_is_bounded_and_requires_real_tls_result(self):
        with patch.object(m.time,'monotonic',side_effect=[100,131]):
            with self.assertRaisesRegex(RuntimeError,'deadline exceeded'):m.wait_postgres_tls(unittest.mock.Mock(),1000)
        with self.assertRaisesRegex(RuntimeError,'Unexpected PostgreSQL'):m.wait_postgres_tls(lambda:'18.6|f',m.time.monotonic()+500)

    def test_complete_requires_every_executed_group(self):
        results=[{'id':k,'status':'PASS'} for k in m.EXPECTED]
        self.assertTrue(m.complete(results))
        self.assertFalse(m.complete(results[:-1]))
        self.assertFalse(m.complete(results+[results[0]]))
        for status in ['NOT_RUN','SKIPPED','ERROR','FAIL']:
            changed=[dict(r) for r in results];changed[0]['status']=status
            self.assertFalse(m.complete(changed))
    def test_duplicate_cannot_replace_missing_result(self):
        results=[{'id':k,'status':'PASS'} for k in m.EXPECTED]
        results[-1]=dict(results[0]);self.assertFalse(m.complete(results))
    def test_supervisor_is_capped_not_just_declared(self):
        self.assertTrue(m.allowed_limits('50000 100000','1073741824'))
        for cpu,mem in [('max 100000','1073741824'),('50001 100000','1073741824'),('50000 100000','max'),('50000 100000','1073741825'),('0 100000','1')]:
            self.assertFalse(m.allowed_limits(cpu,mem))
    def test_default_cli_never_executes(self):
        with patch('sys.argv',['verify-cloud-run.py']),patch.object(m,'execute') as execute:
            self.assertEqual(m.main(),0);execute.assert_not_called()
    def test_execution_requires_absolute_evidence(self):
        with patch('sys.argv',['verify-cloud-run.py','--execute','--evidence','relative']),patch.object(m,'execute') as execute:
            with self.assertRaises(SystemExit):m.main()
            execute.assert_not_called()
    def test_wrong_docker_endpoint_refused_before_inspection(self):
        with patch.dict('os.environ',{'DOCKER_HOST':'tcp://remote:2375'}),patch.object(m,'docker') as docker:
            with self.assertRaises(RuntimeError):m.preflight()
            docker.assert_not_called()
    def test_consumed_approval_refuses_before_runtime_inspection(self):
        import json
        state={'cloudRunPackaging':{'status':'READY','productionAuthorization':'NONE','qualification':{'sessionsRemaining':0}}}
        with patch.dict('os.environ',{'DOCKER_HOST':'unix:///var/run/docker.sock'}),patch.object(Path,'read_text',return_value=json.dumps(state)),patch.object(m,'docker') as docker:
            with self.assertRaisesRegex(RuntimeError,'authorization consumed'):m.preflight()
            docker.assert_not_called()

    def test_images_are_immutable_and_postgres_is_existing_pin(self):
        self.assertRegex(m.POSTGRES,r'^postgres:18\.6@sha256:[a-f0-9]{64}$')
        self.assertRegex(m.BUILDER,r'^moby/buildkit@sha256:[a-f0-9]{64}$')
        spec=Path(__file__).with_name('verify-foundation.py').read_text()
        self.assertIn(m.POSTGRES,spec)
    def test_external_knowledge_imports_are_packaged(self):
        import re
        root=Path(__file__).resolve().parents[1]
        references=set()
        for source in (root/'frontend').rglob('*'):
            if source.suffix not in {'.tsx','.ts','.mjs','.css'} or any(x in source.parts for x in ['node_modules','.next']):continue
            for ref in re.findall(r"[\"']((?:\.\./)+knowledge/[^\"']+)[\"']",source.read_text()):
                path=(source.parent/ref).resolve()
                self.assertTrue(path.is_file(),ref)
                references.add(path.relative_to(root/'knowledge').parts[0])
        self.assertTrue(references)
        dockerfile=(root/'frontend/Dockerfile').read_text()
        for directory in references:
            self.assertIn('COPY knowledge/'+directory+'/ /source/knowledge/'+directory+'/',dockerfile)

    def test_timeout_evidence_never_echoes_password(self):
        import subprocess
        args=['docker','-e','CINENEST_DB_PASSWORD=unit-secret']
        error=subprocess.TimeoutExpired(args,1,stderr='unit-secret')
        with patch.object(m.subprocess,'run',side_effect=error):
            with self.assertRaisesRegex(RuntimeError,'timed out') as caught:m.call(args)
        self.assertNotIn('unit-secret',str(caught.exception))
        self.assertNotIn('unit-secret',str(m.TRACE[-1]))
        self.assertIsNone(caught.exception.__cause__)
    def test_failed_command_evidence_redacts_password(self):
        import subprocess
        args=['docker','-e','CINENEST_DB_PASSWORD=unit-secret']
        result=subprocess.CompletedProcess(args,1,stdout='unit-secret',stderr='unit-secret')
        with patch.object(m.subprocess,'run',return_value=result):
            with self.assertRaises(RuntimeError) as caught:m.call(args)
        self.assertNotIn('unit-secret',str(caught.exception))
        self.assertNotIn('unit-secret',str(m.TRACE[-1]))

    def test_no_cloud_mutation_surface(self):
        source=Path(__file__).with_name('verify-cloud-run.py').read_text()
        for forbidden in ["['gcloud'",'system prune','volume prune','network prune','image prune']:
            self.assertNotIn(forbidden,source)
    def test_deadline_reserves_cleanup_before_commands(self):
        with patch.object(m.time,'monotonic',return_value=100),patch.object(m.subprocess,'run') as run:
            with self.assertRaisesRegex(RuntimeError,'Cleanup reserve'):m.call(['true'],deadline=330)
            run.assert_not_called()



def deployment_fixture():
    """Synthetic configuration only: these identities, grants, images and secrets do not exist."""
    p=m.DEPLOYMENT_PROJECT;number=m.DEPLOYMENT_NUMBER;region=m.DEPLOYMENT_REGION
    runtime='cinenest-test-runtime@'+p+'.iam.gserviceaccount.com';deployer='cinenest-test-deployer@'+p+'.iam.gserviceaccount.com'
    repo='projects/'+p+'/locations/'+region+'/repositories/test-only';base=region+'.docker.pkg.dev/'+p+'/test-only/'
    secret_names=['test-only-db-user','test-only-db-password','test-only-db-ca']
    def probe(path,port,startup=False):return {'timeoutSeconds':5 if startup else 2,'periodSeconds':5 if startup else 10,'failureThreshold':60 if startup else 3,'httpGet':{'path':path,'port':port}}
    front={'name':'frontend','image':base+'frontend@sha256:'+'a'*64,'ports':[{'containerPort':8080}],'env':[{'name':'CINENEST_AUTH_ORIGIN','value':'http://127.0.0.1:8081'},{'name':'HOSTNAME','value':'0.0.0.0'}],'startupProbe':probe('/healthz',8080,True),'livenessProbe':probe('/healthz',8080),'resources':{'limits':{'cpu':'1','memory':'512Mi'}}}
    back={'name':'backend','image':base+'backend@sha256:'+'b'*64,'env':[{'name':'PORT','value':'8081'},{'name':'CINENEST_BIND_ADDRESS','value':'0.0.0.0'},{'name':'CINENEST_DB_URL','value':'jdbc:postgresql://'+m.DEPLOYMENT_DNS+':5432/test_only?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem'},{'name':'CINENEST_DB_POOL_MAX','value':'2'},*[{'name':key,'valueFrom':{'secretKeyRef':{'name':name,'key':'1'}}} for key,name in zip(['CINENEST_DB_USER','CINENEST_DB_PASSWORD'],secret_names)]],'volumeMounts':[{'name':'database-ca','mountPath':'/run/secrets'}],'startupProbe':probe('/health/ready',8081,True),'livenessProbe':probe('/health/live',8081),'resources':{'limits':{'cpu':'1','memory':'1Gi'}}}
    annotations={'run.googleapis.com/execution-environment':'gen2','run.googleapis.com/container-dependencies':'{"frontend":["backend"]}','run.googleapis.com/network-interfaces':'[{"network":"default","subnetwork":"default"}]','run.googleapis.com/vpc-access-egress':'private-ranges-only','autoscaling.knative.dev/maxScale':'1'}
    manifest={'apiVersion':'serving.knative.dev/v1','kind':'Service','metadata':{'name':'cinenest-test-only','namespace':number,'labels':{'cloud.googleapis.com/location':region},'annotations':{'run.googleapis.com/ingress':'internal'}},'spec':{'template':{'metadata':{'annotations':annotations},'spec':{'serviceAccountName':runtime,'containerConcurrency':4,'containers':[front,back],'volumes':[{'name':'database-ca','secret':{'secretName':secret_names[2],'items':[{'key':'1','path':'db-ca.pem'}]}}]}}}}
    attachment='projects/synthetic-producer/regions/'+region+'/serviceAttachments/test-only'
    network='projects/'+p+'/global/networks/default'
    role={'name':'test_only_runtime',**{k:False for k in ['superuser','createDb','createRole','replication','bypassRls','databaseOwner','schemaOwner','cloudSqlSuperuser','memberOfMigrationRole','ddlAllowed']},'login':True,'migrationHistoryReadable':True}
    bindings=[{'member':'serviceAccount:'+runtime,'role':'roles/secretmanager.secretAccessor','resource':'projects/'+p+'/secrets/'+s} for s in secret_names]
    bindings += [{'member':'serviceAccount:'+deployer,'role':'roles/run.developer','resource':'projects/'+p+'/locations/'+region+'/services/cinenest-test-only'},{'member':'serviceAccount:'+deployer,'role':'roles/iam.serviceAccountUser','resource':'projects/'+p+'/serviceAccounts/'+runtime},{'member':'serviceAccount:'+deployer,'role':'roles/artifactregistry.reader','resource':repo}]
    inventory={'project':{'projectId':p,'projectNumber':number},'sql':{'name':m.DEPLOYMENT_INSTANCE,'region':region,'databaseInstalledVersion':'POSTGRES_18_6','state':'RUNNABLE','dnsName':m.DEPLOYMENT_DNS+'.','pscServiceAttachmentLink':attachment,'settings':{'ipConfiguration':{'ipv4Enabled':False,'sslMode':'ENCRYPTED_ONLY','pscConfig':{'pscEnabled':True},'serverCaMode':'GOOGLE_MANAGED_CAS_CA'}}},'psc':[{'IPAddress':'10.40.32.2','pscConnectionStatus':'ACCEPTED','region':'projects/'+p+'/regions/'+region,'network':network,'target':attachment}],'dnsZone':{'visibility':'private','dnsName':'us-east1.sql.goog.','privateVisibilityConfig':{'networks':[{'networkUrl':network}]}},'dns':[{'name':m.DEPLOYMENT_DNS+'.','type':'A','rrdatas':['10.40.32.2']}],'subnets':[{'selfLink':'projects/'+p+'/regions/'+region+'/subnetworks/default','network':network,'purpose':'PRIVATE','ipCidrRange':'10.142.0.0/20'}],'serviceAccounts':[{'email':runtime,'disabled':False},{'email':deployer,'disabled':False}],'deployerIdentity':deployer,'repositories':[{'name':repo,'format':'DOCKER'}],'images':[{'uri':front['image'],'status':'AVAILABLE'},{'uri':back['image'],'status':'AVAILABLE'}],'secrets':[{'name':'projects/'+p+'/secrets/'+name,'versions':[{'version':'1','state':'ENABLED'}]} for name in secret_names+['test-only-migrator-password']],'databaseRoles':{'evidenceKind':'SYNTHETIC_TEST_ONLY','runtime':role,'migrationRole':'test_only_migrator','schemaVersion':'45'},'declaredIamBindings':bindings}
    return {'schemaVersion':1,'mode':'OFFLINE_ONLY','evidenceKind':'SYNTHETIC_TEST_ONLY','deployment':manifest,'inventory':inventory,'capacity':{'maxInstances':1,'concurrency':4,'poolMax':2,'maxOverlappingInstances':2,'databaseMaxConnections':20,'reservedConnections':4,'maximumCpu':'2','maximumMemoryBytes':2*1024**3},'rollback':{'backendImage':base+'backend@sha256:'+'c'*64,'frontendImage':base+'frontend@sha256:'+'d'*64,'schemaVersion':'45','migrationAction':'NONE'},'approvalReferences':{k:'SYNTHETIC_TEST_ONLY_NOT_APPROVAL' for k in ['configuration','capacity','sharedIdentity','databaseRoles','rollback']},'sharedIdentityAcknowledged':True,'migrationContract':{'role':'test_only_migrator','databaseName':'test_only','passwordSecretRef':{'name':'test-only-migrator-password','version':'1'},'mountedInService':False,'executionAuthorization':'NONE','custodyReference':'SYNTHETIC_TEST_ONLY_NOT_APPROVAL'},'applicationSigning':{'mode':'PENDING_SEPARATE_IDENTITY_APPROVAL','secretReferences':[]}}


class DeploymentContracts(unittest.TestCase):
    def test_synthetic_complete_contract_never_grants_live_readiness(self):
        report=m.deployment_report(deployment_fixture());self.assertEqual(report['status'],'OFFLINE_CONTRACT_VALID',report)
        for field in ['cloudRunConnectivity','effectiveIamVerification','liveSqlRoleVerification','rollbackExecution']:self.assertEqual(report[field],'NOT_RUN')
        self.assertEqual(report['productionAuthorization'],'NONE');self.assertFalse(report['deploymentExecuted']);self.assertEqual(report['evidenceKind'],'SYNTHETIC_TEST_ONLY');self.assertEqual(report['approvalProvenance'],'NOT_INDEPENDENTLY_VERIFIED')
    def test_missing_resources_are_blocked_and_never_faked(self):
        d=deployment_fixture()
        for name in ['serviceAccounts','repositories','images','secrets']:d['inventory'][name]=[]
        d['inventory']['databaseRoles']={};d['approvalReferences']={}
        report=m.deployment_report(d);self.assertEqual(report['status'],'BLOCKED');self.assertEqual(report['secretPayloads'],'NOT_READ')
    def test_each_missing_required_reference_blocks(self):
        for key in deployment_fixture()['approvalReferences']:
            d=deployment_fixture();del d['approvalReferences'][key]
            with self.subTest(reference=key):self.assertEqual(m.deployment_report(d)['status'],'BLOCKED')
    def test_new_dispatch_preserves_original_mode_and_routes_equals_syntax_inertly(self):
        with patch('sys.argv',['verify-cloud-run.py']),patch.object(m,'main',return_value=7) as old,patch.object(m,'deployment_main') as offline:
            self.assertEqual(m.connectivity_entrypoint(),7);old.assert_called_once();offline.assert_not_called()
        with patch('sys.argv',['verify-cloud-run.py','--validate-deployment=/absolute/config.json']),patch.object(m,'main') as old,patch.object(m,'deployment_main',return_value=8) as offline:
            self.assertEqual(m.connectivity_entrypoint(),8);offline.assert_called_once();old.assert_not_called()

    def test_no_network_or_container_commands_in_offline_cli(self):
        import tempfile,json,contextlib,io
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'config.json';source.write_text(json.dumps(deployment_fixture()))
            with patch.object(m,'execute') as execute,patch.object(m,'docker') as docker,patch.object(m,'call') as call,patch.object(m.urllib.request,'urlopen') as network,contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(m.deployment_main(['--validate-deployment',str(source),'--evidence',str(root/'evidence')]),0)
            for mocked in [execute,docker,call,network]:mocked.assert_not_called()
            report=json.loads((root/'evidence/deployment-contract.json').read_text());self.assertEqual(report['productionAuthorization'],'NONE')
    def test_cli_rejects_runtime_combination_without_echoing_argument_values(self):
        import contextlib,io
        output=io.StringIO()
        with patch.object(m,'execute') as execute,contextlib.redirect_stderr(output):
            with self.assertRaises(SystemExit):m.deployment_main(['--validate-deployment','/missing','--evidence','/tmp/no-write','--execute','private-sentinel-never-echo'])
        execute.assert_not_called();self.assertNotIn('private-sentinel-never-echo',output.getvalue())
    def test_cli_rejects_relative_input_and_checkout_evidence(self):
        import io,contextlib
        for args in [['--validate-deployment','relative','--evidence','/tmp/no-write'],['--validate-deployment','/missing','--evidence',str(m.ROOT/'qualification')]]:
            with self.subTest(args=args),contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):m.deployment_main(args)
    def test_cli_refuses_duplicate_json_members_symlink_oversize_and_nonfinite(self):
        import tempfile,contextlib,io,json
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);payload=root/'payload.json';link=root/'link.json';link.symlink_to(payload)
            for index,text in enumerate(['{"mode":"OFFLINE_ONLY","mode":"PRODUCTION"}','{"mode":NaN}','x'*262145]):
                payload.write_text(text)
                with contextlib.redirect_stdout(io.StringIO()):self.assertEqual(m.deployment_main(['--validate-deployment',str(payload),'--evidence',str(root/str(index))]),1)
            payload.write_text(json.dumps(deployment_fixture()))
            with contextlib.redirect_stdout(io.StringIO()):self.assertEqual(m.deployment_main(['--validate-deployment',str(link),'--evidence',str(root/'symlink')]),1)
    def test_invalid_credential_material_is_not_echoed(self):
        import json
        d=deployment_fixture();back=d['deployment']['spec']['template']['spec']['containers'][1];back['env'][2]['value']='jdbc:postgresql://user:private-sentinel-never-echo@outside.invalid/db?sslmode=disable';back['env'].append({'name':'private-sentinel-never-echo','value':'private-sentinel-never-echo'})
        report=m.deployment_report(d);self.assertEqual(report['status'],'FAIL');self.assertNotIn('private-sentinel-never-echo',json.dumps(report))
    def test_cli_preserves_prior_evidence(self):
        import tempfile,json,io,contextlib
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'config.json';source.write_text(json.dumps(deployment_fixture()));out=root/'evidence';out.mkdir();target=out/'deployment-contract.json';target.write_text('historical evidence')
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):m.deployment_main(['--validate-deployment',str(source),'--evidence',str(out)])
            self.assertEqual(target.read_text(),'historical evidence')
    def test_refuses_privileged_annotations_feature_activation_and_extra_actions(self):
        for field in ['invoker-iam-disabled','cloudsql-instances','binary-authorization-breakglass']:
            d=deployment_fixture();d['deployment']['metadata']['annotations']['run.googleapis.com/'+field]='true'
            with self.subTest(annotation=field):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        for name in ['CINENEST_IDENTITY_ENABLED','CINENEST_BANK_ENABLED','JAVA_TOOL_OPTIONS','SPRING_APPLICATION_JSON']:
            d=deployment_fixture();d['deployment']['spec']['template']['spec']['containers'][1]['env'].append({'name':name,'value':'true'})
            with self.subTest(environment=name):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        for key in ['productionAuthorization','cloudCommands','signingSecretValue']:
            d=deployment_fixture();d[key]='private-sentinel-never-echo';report=m.deployment_report(d)
            with self.subTest(extra=key):
                self.assertEqual(report['status'],'FAIL');self.assertNotIn('private-sentinel-never-echo',str(report))
    def test_dns_zone_and_psc_are_bound_to_the_selected_private_network_and_region(self):
        d=deployment_fixture();d['inventory']['dnsZone']['visibility']='public';self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        d=deployment_fixture();d['inventory']['dnsZone']['privateVisibilityConfig']['networks'][0]['networkUrl']='projects/foreign/global/networks/default';self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        d=deployment_fixture();d['inventory']['psc'][0]['region']='projects/'+m.DEPLOYMENT_PROJECT+'/regions/us-west1';self.assertEqual(m.deployment_report(d)['status'],'FAIL')

    def test_wrong_subnet_address_family_or_insufficient_range_is_rejected(self):
        for cidr in ['8.8.8.0/24','10.142.0.0/28','::1/128','not-a-network']:
            d=deployment_fixture();d['inventory']['subnets'][0]['ipCidrRange']=cidr
            with self.subTest(cidr=cidr):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
    def test_each_privileged_database_role_attribute_is_rejected(self):
        for key in ['superuser','createDb','createRole','replication','bypassRls','databaseOwner','schemaOwner','cloudSqlSuperuser','memberOfMigrationRole','ddlAllowed']:
            d=deployment_fixture();d['inventory']['databaseRoles']['runtime'][key]=True
            with self.subTest(attribute=key):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
    def test_unrelated_existing_identities_and_secret_versions_do_not_fabricate_availability(self):
        d=deployment_fixture();d['inventory']['serviceAccounts']=[{'email':'980464067041-compute@developer.gserviceaccount.com','disabled':False}]
        self.assertEqual(m.deployment_report(d)['status'],'BLOCKED')
        d=deployment_fixture();d['inventory']['secrets'][0]['versions']=[{'version':'2','state':'ENABLED'}]
        self.assertEqual(m.deployment_report(d)['status'],'BLOCKED')
    def test_template_is_the_unchanged_qualified_approval_placeholder_contract(self):
        import hashlib
        template=m.ROOT/'deployment/cloud-run/service.template.yaml'
        self.assertEqual(hashlib.sha256(template.read_bytes()).hexdigest(),'8f927369858fc20c3894e37d40f34dade51a9b90e9d5ec2dd34fa2e23af9f7d5')
        self.assertIn('__PINNED_SECRET_VERSION__',template.read_text())

    def test_missing_and_conflicting_evidence_remain_distinct(self):
        d=deployment_fixture();d['inventory']['databaseRoles']={}
        self.assertEqual(m.deployment_report(d)['status'],'BLOCKED')
        d['inventory']['databaseRoles']={'schemaVersion':'44'}
        self.assertEqual(m.deployment_report(d)['status'],'FAIL')
    def test_migration_credentials_remain_separate_and_not_executable(self):
        for key,value in [('role','postgres'),('databaseName','other_db'),('mountedInService',True),('executionAuthorization','GRANTED')]:
            d=deployment_fixture();d['migrationContract'][key]=value
            with self.subTest(field=key):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        for key,value in [('name','test-only-db-password'),('version','latest')]:
            d=deployment_fixture();d['migrationContract']['passwordSecretRef'][key]=value
            with self.subTest(secret=key):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
        d=deployment_fixture();d['migrationContract']['password']='private-sentinel-never-echo';r=m.deployment_report(d)
        self.assertEqual(r['status'],'FAIL');self.assertNotIn('private-sentinel-never-echo',str(r))
    def test_runtime_cannot_be_granted_migration_secret_access(self):
        d=deployment_fixture();d['inventory']['declaredIamBindings'].append({'member':d['inventory']['declaredIamBindings'][0]['member'],'role':'roles/secretmanager.secretAccessor','resource':'projects/'+m.DEPLOYMENT_PROJECT+'/secrets/test-only-migrator-password'})
        self.assertEqual(m.deployment_report(d)['status'],'FAIL')
    def test_no_signing_secrets_or_signing_approval_are_invented(self):
        for key,value in [('mode','PRODUCTION_APPROVED'),('secretReferences',[{'name':'imaginary-jwt-key','version':'1'}])]:
            d=deployment_fixture();d['applicationSigning'][key]=value
            with self.subTest(field=key):self.assertEqual(m.deployment_report(d)['status'],'FAIL')

    def test_synthetic_role_evidence_cannot_be_relabelled_as_live(self):
        d=deployment_fixture();d['evidenceKind']='CONTROL_PLANE_SNAPSHOT';r=m.deployment_report(d)
        self.assertEqual(r['evidenceKind'],'SYNTHETIC_TEST_ONLY');self.assertEqual(r['liveSqlRoleVerification'],'NOT_RUN')

    def test_input_size_cap_survives_a_stale_filesystem_stat(self):
        import tempfile,io,contextlib,os,json
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'large.json';payload=json.dumps(deployment_fixture());source.write_text(payload+' '*(262145-len(payload)));original=Path.stat
            def stale(path,*args,**kwargs):
                result=original(path,*args,**kwargs)
                if path==source:
                    values=list(result);values[6]=1;return os.stat_result(values)
                return result
            with patch.object(Path,'stat',stale),contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(m.deployment_main(['--validate-deployment',str(source),'--evidence',str(root/'out')]),1)
    def test_evidence_creation_is_exclusive_even_if_exists_probe_is_stale(self):
        import tempfile,json,io,contextlib
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'config.json';source.write_text(json.dumps(deployment_fixture()));out=root/'out';out.mkdir();target=out/'deployment-contract.json';target.write_text('preserved original');exists=Path.exists
            with patch.object(Path,'exists',lambda p:False if p==target else exists(p)),contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):
                m.deployment_main(['--validate-deployment',str(source),'--evidence',str(out)])
            self.assertEqual(target.read_text(),'preserved original')

    def test_missing_input_is_a_redacted_failure(self):
        import tempfile,contextlib,io,json
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with contextlib.redirect_stdout(io.StringIO()):self.assertEqual(m.deployment_main(['--validate-deployment',str(root/'missing'),'--evidence',str(root/'out')]),1)
            result=json.loads((root/'out/deployment-contract.json').read_text());self.assertEqual(result['status'],'FAIL');self.assertEqual(result['cloudRunConnectivity'],'NOT_RUN')


def _contract_negative_test(path,value):
    def test(self):
        d=deployment_fixture();target=d
        for part in path[:-1]:target=target[part]
        target[path[-1]]=value
        report=m.deployment_report(d);self.assertIn(report['status'],['FAIL','BLOCKED'],report)
    return test

_pod=('deployment','spec','template','spec');_ann=('deployment','spec','template','metadata','annotations');_back=_pod+('containers',1);_front=_pod+('containers',0)
for _name,_path,_value in [
 ('project',('deployment','metadata','namespace'),'111111111111'),('region',('deployment','metadata','labels','cloud.googleapis.com/location'),'us-west1'),('public_ingress',('deployment','metadata','annotations','run.googleapis.com/ingress'),'all'),('first_generation',_ann+('run.googleapis.com/execution-environment',),'gen1'),('all_egress',_ann+('run.googleapis.com/vpc-access-egress',),'all-traffic'),('foreign_subnet',_ann+('run.googleapis.com/network-interfaces',),'[{"network":"foreign","subnetwork":"default"}]'),('missing_dependency',_ann+('run.googleapis.com/container-dependencies',),'{}'),('default_runtime',_pod+('serviceAccountName',),'980464067041-compute@developer.gserviceaccount.com'),('same_deployer',('inventory','deployerIdentity'),'cinenest-test-runtime@'+m.DEPLOYMENT_PROJECT+'.iam.gserviceaccount.com'),('disabled_sa',('inventory','serviceAccounts',0,'disabled'),True),('mutable_image',_back+('image',),'us-east1-docker.pkg.dev/'+m.DEPLOYMENT_PROJECT+'/test-only/backend:latest'),('foreign_image',_front+('image',),'us-east1-docker.pkg.dev/foreign/test-only/frontend@sha256:'+'a'*64),('wrong_instance',('inventory','sql','name'),'other-instance'),('wrong_sql_version',('inventory','sql','databaseInstalledVersion'),'POSTGRES_18_7'),('public_sql',('inventory','sql','settings','ipConfiguration','ipv4Enabled'),True),('plaintext_server',('inventory','sql','settings','ipConfiguration','sslMode'),'ALLOW_UNENCRYPTED_AND_ENCRYPTED'),('psc_disabled',('inventory','sql','settings','ipConfiguration','pscConfig','pscEnabled'),False),('psc_pending',('inventory','psc',0,'pscConnectionStatus'),'PENDING'),('dns_wrong_ip',('inventory','dns',0,'rrdatas'),['8.8.8.8']),('wrong_subnet_link',('inventory','subnets',0,'selfLink'),'projects/foreign/regions/us-east1/subnetworks/default'),('url_no_verify',_back+('env',2,'value'),'jdbc:postgresql://'+m.DEPLOYMENT_DNS+':5432/test_only?sslmode=require&sslrootcert=/run/secrets/db-ca.pem'),('url_duplicate_mode',_back+('env',2,'value'),'jdbc:postgresql://'+m.DEPLOYMENT_DNS+':5432/test_only?sslmode=verify-full&sslmode=disable&sslrootcert=/run/secrets/db-ca.pem'),('url_public_host',_back+('env',2,'value'),'jdbc:postgresql://8.8.8.8:5432/test_only?sslmode=verify-full&sslrootcert=/run/secrets/db-ca.pem'),('latest_secret',_back+('env',4,'valueFrom','secretKeyRef','key'),'latest'),('zero_secret',_back+('env',5,'valueFrom','secretKeyRef','key'),'0'),('unavailable_secret',('inventory','secrets',0,'versions',0,'state'),'DISABLED'),('ca_latest',_pod+('volumes',0,'secret','items',0,'key'),'latest'),('ca_path',_pod+('volumes',0,'secret','items',0,'path'),'other.pem'),('missing_shared_ack',('sharedIdentityAcknowledged',),False),('runtime_postgres',('inventory','databaseRoles','runtime','name'),'postgres'),('runtime_superuser',('inventory','databaseRoles','runtime','superuser'),True),('runtime_ddl',('inventory','databaseRoles','runtime','ddlAllowed'),True),('runtime_owner',('inventory','databaseRoles','runtime','schemaOwner'),True),('runtime_migration_member',('inventory','databaseRoles','runtime','memberOfMigrationRole'),True),('same_sql_role',('inventory','databaseRoles','migrationRole'),'test_only_runtime'),('missing_history_grant',('inventory','databaseRoles','runtime','migrationHistoryReadable'),False),('stale_schema',('inventory','databaseRoles','schemaVersion'),'44'),('role_string_false',('inventory','databaseRoles','runtime','superuser'),'false'),('oversized_pool',('capacity','poolMax'),33),('insufficient_connections',('capacity','databaseMaxConnections'),4),('missing_rollout_reserve',('capacity','maxOverlappingInstances'),1),('boolean_capacity',('capacity','maxInstances'),True),('over_cpu_budget',('capacity','maximumCpu'),'1'),('over_memory_budget',('capacity','maximumMemoryBytes'),100),('unbounded_concurrency',_pod+('containerConcurrency',),0),('unbounded_scale',_ann+('autoscaling.knative.dev/maxScale',),'0'),('probe_exceeds_period',_back+('startupProbe','timeoutSeconds'),6),('startup_budget',_back+('startupProbe','failureThreshold'),121),('db_dependent_liveness',_back+('livenessProbe','httpGet','path'),'/health/ready'),('role_admin_grant',('inventory','declaredIamBindings',0,'role'),'roles/owner'),('project_secret_grant',('inventory','declaredIamBindings',0,'resource'),'projects/'+m.DEPLOYMENT_PROJECT),('wildcard_principal',('inventory','declaredIamBindings',0,'member'),'allUsers'),('rollback_schema',('rollback','schemaVersion'),'44'),('rollback_migration',('rollback','migrationAction'),'MIGRATE'),('rollback_mutable',('rollback','backendImage'),'backend:latest'),('production_mode',('mode',),'PRODUCTION'),('forged_evidence',('evidenceKind',),'VERIFIED_PRODUCTION')]:
    setattr(DeploymentContracts,'test_reject_'+_name,_contract_negative_test(_path,_value))


if __name__=='__main__':unittest.main()
