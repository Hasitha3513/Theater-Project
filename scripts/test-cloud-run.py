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



_runtime_spec=importlib.util.spec_from_file_location('cloud_runtime_artifact',Path(__file__).with_name('verify-cloud-connectivity.py'))
runtime_artifact=importlib.util.module_from_spec(_runtime_spec);_runtime_spec.loader.exec_module(runtime_artifact)

class RuntimeActivationBoundaries(unittest.TestCase):
    def ready_state(self):
        return {'status':'READY','productionAuthorization':'NONE','part1':{'completed':4,'total':7},'authority':{'requestSha256':runtime_artifact.APPROVAL_SHA256},'review':{'answer':'Approve task-only review exception','verifiedByteIdenticalFullReviewReuse':100,'taskScopedChangedFileReview':14,'fullCurrentKbReviewClaimed':False,'taskOnly':True},'sqlFingerprints':runtime_artifact.SQL_FILES,'qualification':{'status':'OFFLINE_TESTS_PASS'}}
    def evidence(self):
        from datetime import datetime,timezone
        return {'localActivation':'PASS','localFailureControls':'PASS','sourceGovernance':'PASS','hostedExactSourceCI':'PASS','cloudSessionConsumed':False,'remainingUsd':1,'priorReservedUsd':4,'preparationStartedAt':datetime.now(timezone.utc).isoformat()}
    def test_artifact_eligibility_never_means_cloud_pass(self):
        result=runtime_artifact.runtime_ready(self.ready_state(),self.evidence())
        self.assertEqual(result['status'],'ELIGIBLE_CONDITIONAL_OPERATION');self.assertEqual(result['cloudRunConnectivity'],'NOT_RUN');self.assertEqual(result['productionAuthorization'],'NONE')
    def test_each_required_gate_missing_blocks(self):
        for key in self.evidence():
            e=self.evidence();e.pop(key)
            with self.subTest(key=key):self.assertEqual(runtime_artifact.runtime_ready(self.ready_state(),e)['status'],'BLOCKED')
    def test_consumed_session_cannot_be_reused(self):
        e=self.evidence();e['cloudSessionConsumed']=True;self.assertEqual(runtime_artifact.runtime_ready(self.ready_state(),e)['status'],'BLOCKED')
    def test_modified_approval_or_sql_is_denied(self):
        import copy
        for key,value in [('authority',{'requestSha256':'0'*64}),('sqlFingerprints',{}),('productionAuthorization','GO'),('part1',{'completed':7,'total':7})]:
            state=copy.deepcopy(self.ready_state());state[key]=value
            with self.subTest(key=key):self.assertTrue(runtime_artifact.admission_errors(state))
    def test_review_exception_cannot_expand_or_claim_full_review(self):
        state=self.ready_state();state['review']['fullCurrentKbReviewClaimed']=True;self.assertTrue(runtime_artifact.admission_errors(state))
    def test_historical_migration_inventory_is_44(self):self.assertEqual(len(runtime_artifact.migration_inventory()),44)
    def test_symlink_artifacts_rejected(self):
        import tempfile
        with tempfile.TemporaryDirectory() as root:
            p=Path(root);(p/'source').write_text('synthetic-only');(p/'link').symlink_to(p/'source')
            with self.assertRaises(ValueError):runtime_artifact.fingerprint(p/'link')
    def image(self):return 'us-east1-docker.pkg.dev/'+runtime_artifact.PROJECT+'/cinenest-runtime/diagnostics@sha256:'+'a'*64
    def versions(self):return dict.fromkeys(['runtimeUsername','runtimePassword','migrationPassword','databaseCa'],'1')
    def test_migration_job_identity_and_secret_are_separate(self):
        obj=runtime_artifact.job_document('MIGRATE',self.image(),self.versions());pod=obj['spec']['template']['spec']['template']['spec'];self.assertTrue(pod['serviceAccountName'].startswith('cinenest-migrator@'));self.assertEqual(pod['maxRetries'],0);self.assertEqual(pod['timeoutSeconds'],'600');self.assertNotIn('cinenest-qualification-db-password',str(pod));self.assertEqual(obj['spec']['template']['metadata']['annotations']['run.googleapis.com/vpc-access-egress'],'private-ranges-only')
    def test_probe_job_cannot_access_migration_secret(self):
        obj=runtime_artifact.job_document('PROBE',self.image(),self.versions(),'https://cinenest-connectivity-qualification-980464067041.us-east1.run.app');self.assertNotIn('cinenest-qualification-migration-password',str(obj));self.assertEqual(obj['spec']['template']['metadata']['annotations']['run.googleapis.com/vpc-access-egress'],'all-traffic')
    def test_mutable_or_foreign_image_rejected(self):
        for image in ['diagnostics:latest',self.image().replace(runtime_artifact.PROJECT,'foreign'),self.image()+'x']:
            with self.subTest(image=image),self.assertRaises(ValueError):runtime_artifact.job_document('MIGRATE',image,self.versions())
    def test_latest_or_unexpected_secret_version_rejected(self):
        for value in ['latest','0','2']:
            v=self.versions();v['runtimePassword']=value
            with self.subTest(version=value),self.assertRaises(ValueError):runtime_artifact.job_document('MIGRATE',self.image(),v)
    def test_probe_url_cannot_leak_bearer_identity(self):
        for url in ['http://cinenest-connectivity-qualification-x.run.app','https://attacker.example','https://user:secret@cinenest-connectivity-qualification-x.run.app','https://cinenest-connectivity-qualification-x.run.app/path','https://cinenest-connectivity-qualification-x.run.app?token=secret']:
            with self.subTest(url=url),self.assertRaises(ValueError):runtime_artifact.job_document('PROBE',self.image(),self.versions(),url)
    def test_java_harness_does_not_expose_arbitrary_sql_mode_or_exception(self):
        source=runtime_artifact.JAVA_SOURCE;self.assertNotIn('args[0].equals("SQL")',source);self.assertNotIn('printStackTrace',source);self.assertIn('sslmode=verify-full',source);self.assertNotIn('sslmode=require',source)
    def test_context_refuses_overwrite_before_reading_secrets(self):
        import tempfile
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):runtime_artifact.prepare_context(Path(root),Path(root),Path(root))

# Approved single-line template successor. Replay the complete historical check
# against only the removed cap line;any other byte change still fails its old hash.
_historical_template_check=DeploymentContracts.test_template_is_the_unchanged_qualified_approval_placeholder_contract
def _bounded_service_cap_successor(self):
    target=m.ROOT/'deployment/cloud-run/service.template.yaml'
    data=target.read_bytes();line=b"    run.googleapis.com/maxScale: '__APPROVED_MAX_INSTANCES__'\n"
    self.assertEqual(data.count(line),1)
    original=Path.read_bytes
    with patch.object(Path,'read_bytes',lambda p:data.replace(line,b'',1) if p==target else original(p)):
        _historical_template_check(self)
DeploymentContracts.test_template_is_the_unchanged_qualified_approval_placeholder_contract=_bounded_service_cap_successor

class RuntimeServiceCapBoundary(unittest.TestCase):
    def test_only_the_approved_cap_is_accepted(self):
        d=deployment_fixture();d['deployment']['metadata']['annotations']['run.googleapis.com/maxScale']='1'
        self.assertEqual(m.deployment_report(d)['status'],'OFFLINE_CONTRACT_VALID')
        for value in ['0','2',True,'latest']:
            d['deployment']['metadata']['annotations']['run.googleapis.com/maxScale']=value
            with self.subTest(value=value):self.assertEqual(m.deployment_report(d)['status'],'FAIL')

def _expired_preparation_window_test(self):
    from datetime import datetime,timezone,timedelta
    now=datetime.now(timezone.utc)
    for value in [(now-timedelta(minutes=61)).isoformat(),(now+timedelta(minutes=1)).isoformat(),'invalid',now.replace(tzinfo=None).isoformat(),None]:
        e=self.evidence();e['preparationStartedAt']=value
        with self.subTest(start=value):self.assertEqual(runtime_artifact.runtime_ready(self.ready_state(),e)['status'],'BLOCKED')
RuntimeActivationBoundaries.test_expired_or_invalid_preparation_window_is_denied=_expired_preparation_window_test

# Preserve every historical assertion;adapt only the approved DNS presentation.
_hostname_prior_fixture=deployment_fixture
def deployment_fixture():
    d=_hostname_prior_fixture()
    back=next(x for x in d['deployment']['spec']['template']['spec']['containers'] if x['name']=='backend')
    env=next(x for x in back['env'] if x['name']=='CINENEST_DB_URL')
    env['value']=env['value'].replace(m.DEPLOYMENT_DNS+':5432',m.DEPLOYMENT_DNS+'.:5432')
    return d
_hostname_prior_template_check=DeploymentContracts.test_template_is_the_unchanged_qualified_approval_placeholder_contract
def _exact_hostname_template_successor(self):
    target=m.ROOT/'deployment/cloud-run/service.template.yaml';data=target.read_bytes()
    exact=(m.DEPLOYMENT_DNS+'.:5432').encode();old=(m.DEPLOYMENT_DNS+':5432').encode()
    self.assertEqual(data.count(exact),1);self.assertNotIn(old,data)
    original=Path.read_bytes
    with patch.object(Path,'read_bytes',lambda p:data.replace(exact,old,1) if p==target else original(p)):
        _hostname_prior_template_check(self)
DeploymentContracts.test_template_is_the_unchanged_qualified_approval_placeholder_contract=_exact_hostname_template_successor

class ExactCertificateHostnameBoundaries(unittest.TestCase):
    def test_only_exact_metadata_hostname_is_accepted(self):
        d=deployment_fixture();self.assertEqual(m.deployment_report(d)['status'],'OFFLINE_CONTRACT_VALID')
        back=next(x for x in d['deployment']['spec']['template']['spec']['containers'] if x['name']=='backend')
        env=next(x for x in back['env'] if x['name']=='CINENEST_DB_URL');original=env['value']
        for host in [m.DEPLOYMENT_DNS,'wrong.example',m.DEPLOYMENT_DNS+'..']:
            env['value']=original.replace(m.DEPLOYMENT_DNS+'.',host)
            with self.subTest(host=host):self.assertEqual(m.deployment_report(d)['status'],'FAIL')
    def test_job_and_java_keep_exact_hostname_and_verify_full(self):
        exact=m.DEPLOYMENT_DNS+'.:5432';self.assertIn(exact,runtime_artifact.JAVA_SOURCE);self.assertNotIn(m.DEPLOYMENT_DNS+':5432',runtime_artifact.JAVA_SOURCE)
        image='us-east1-docker.pkg.dev/'+runtime_artifact.PROJECT+'/cinenest-runtime/diagnostics@sha256:'+'a'*64
        job=runtime_artifact.job_document('MIGRATE',image,dict.fromkeys(['runtimeUsername','runtimePassword','migrationPassword','databaseCa'],'1'))
        env=job['spec']['template']['spec']['template']['spec']['containers'][0]['env'];url=next(x['value'] for x in env if x['name']=='CINENEST_DB_URL');self.assertIn(exact,url);self.assertIn('sslmode=verify-full',url)
    def test_actual_template_preserves_certificate_dns_dot(self):
        data=(m.ROOT/'deployment/cloud-run/service.template.yaml').read_text();self.assertEqual(data.count(m.DEPLOYMENT_DNS+'.:5432'),1);self.assertNotIn(m.DEPLOYMENT_DNS+':5432',data)
    def test_pinned_jdbc_verifier_rejects_stripped_and_invalid_names(self):
        import tempfile,subprocess,hashlib
        relative=Path('org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar')
        candidates=[m.ROOT/'.cache/m2'/relative,Path.home()/'.m2/repository'/relative]
        jar=next((p for p in candidates if p.is_file()),candidates[0])
        self.assertEqual(hashlib.sha256(jar.read_bytes()).hexdigest(),runtime_artifact.DEPENDENCIES['org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar'])
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'HostnameCheck.java';source.write_text('import org.postgresql.ssl.PGjdbcHostnameVerifier; class HostnameCheck { public static void main(String[] args) { var v=PGjdbcHostnameVerifier.INSTANCE; String host="'+m.DEPLOYMENT_DNS+'."; if(!v.verifyHostName(host,host)||v.verifyHostName(host.substring(0,host.length()-1),host)||v.verifyHostName("wrong.example",host))throw new AssertionError(); System.out.println("EXACT_NAME_AND_NEGATIVES:PASS"); } }')
            result=subprocess.run(['java','-cp',str(jar),str(source)],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stderr);self.assertIn('EXACT_NAME_AND_NEGATIVES:PASS',result.stdout)



class ManagedRoleConfirmationRegression(unittest.TestCase):
    def fixture(self,phase='DELEGATED'):
        from datetime import datetime,timezone,timedelta
        import uuid
        now=datetime.now(timezone.utc); nonce=str(uuid.uuid4())
        user={'name':runtime_artifact.MANAGED_PRINCIPAL,'type':'CLOUD_IAM_USER'}
        sql={'nonce':nonce,'observedAt':now.isoformat(),'currentUser':'cloudsqlsuperuser','sessionUser':user['name'],'database':'postgres','tls':True,'member':True,'canSet':True,'runtimeAdmin':True,'migratorAdmin':True,'restricted':True,'edges':[{'role':'cloudsqlsuperuser','member':user['name'],'grantor':'provider_fixture','admin':False,'inherit':False,'set':False}]}
        context={'project':runtime_artifact.MANAGED_PROJECT,'instance':runtime_artifact.MANAGED_INSTANCE,'principal':user['name'],'nonce':nonce,'sslmode':'verify-full','host':'ddc7a2d85eb1.18o4rx1o80d5g.us-east1.sql.goog.','hostaddr':'10.40.32.2','caSha256':'b9c1735f6619bc814020948a0da2e8970efa76f7d885fd28dbe1c21a85193f46','operation':{'id':str(uuid.uuid4()),'status':'DONE','phase':phase,'completedAt':(now-timedelta(seconds=1)).isoformat()},'revokerArmed':True,'probeSha256':runtime_artifact.AUTHORITY_PROBE_SHA256,'probePassed':True}
        if phase=='REVOKED':
            sql.update(currentUser=user['name'],member=False,canSet=False,runtimeAdmin=False,migratorAdmin=False,edges=[])
        return user,sql,context,now
    def result(self,parts,phase='DELEGATED'):
        return runtime_artifact.managed_role_confirmation(*parts[:3],phase,parts[3])
    def test_original_optional_metadata_assertion_regression(self):
        f=self.fixture();self.assertEqual(f[0].get('databaseRoles',[]),[])
        with self.assertRaises(AssertionError):assert f[0].get('databaseRoles',[])==['cloudsqlsuperuser']
        self.assertIsNone(runtime_artifact.managed_role_metadata(f[0]));self.assertEqual(self.result(f)['status'],'CONFIRMED')
    def test_api_metadata_alone_never_proves_authority(self):
        f=self.fixture();f[0]['databaseRoles']=['cloudsqlsuperuser'];self.assertEqual(runtime_artifact.managed_role_confirmation(f[0],{},f[2],'DELEGATED',f[3])['status'],'DENIED')
    def test_explicit_empty_or_conflicting_metadata_denied(self):
        for roles in [[],['other'],['cloudsqlsuperuser','cloudsqlsuperuser'],None,'cloudsqlsuperuser']:
            f=self.fixture();f[0]['databaseRoles']=roles;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_explicit_matching_metadata_requires_sql(self):
        f=self.fixture();f[0]['databaseRoles']=['cloudsqlsuperuser'];self.assertEqual(self.result(f)['status'],'CONFIRMED')
        f[1]['member']=False;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_current_session_and_effective_role_are_distinct(self):
        for key,value in [('currentUser','cinenest44@gmail.com'),('sessionUser','postgres'),('database','cinenest_connectivity_qualification')]:
            f=self.fixture();f[1][key]=value;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_missing_revoked_or_unusable_authority_denied(self):
        for key in ['member','canSet','runtimeAdmin','migratorAdmin','tls']:
            for value in [False,None,'true',1]:
                f=self.fixture();f[1][key]=value;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_stored_options_not_confused_with_effective_provider_rights(self):
        f=self.fixture();self.assertEqual(self.result(f)['status'],'CONFIRMED')
        self.assertFalse(f[1]['edges'][0]['set']);f[1]['canSet']=False;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_wrong_grant_scope_and_identity_denied(self):
        for key in ['project','instance','principal','host','hostaddr','sslmode','caSha256']:
            f=self.fixture();f[2][key]='wrong';self.assertEqual(self.result(f)['status'],'DENIED')
        for key in ['name','type','project','instance']:
            f=self.fixture();f[0][key]='wrong';self.assertEqual(self.result(f)['status'],'DENIED')
    def test_stale_future_or_pre_update_sql_denied(self):
        from datetime import timedelta
        for seconds in [-11,1,-2]:
            f=self.fixture();f[1]['observedAt']=(f[3]+timedelta(seconds=seconds)).isoformat();self.assertEqual(self.result(f)['status'],'DENIED')
    def test_nonce_replay_denied(self):
        import uuid
        f=self.fixture();f[1]['nonce']=str(uuid.uuid4());self.assertEqual(self.result(f)['status'],'DENIED')
    def test_unfinished_or_failed_operation_denied(self):
        for key,value in [('status','PENDING'),('error',{'code':'denied'}),('phase','REVOKED'),('id','')]:
            f=self.fixture();f[2]['operation'][key]=value;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_reviewed_noop_probe_and_independent_revoker_mandatory(self):
        for key,value in [('revokerArmed',False),('probePassed',False),('probeSha256','0'*64)]:
            f=self.fixture();f[2][key]=value;self.assertEqual(self.result(f)['status'],'DENIED')
    def test_readonly_query_has_only_nonsecret_catalogs(self):
        import uuid
        q=runtime_artifact.managed_role_query(str(uuid.uuid4()));self.assertTrue(q.startswith('SELECT '))
        for text in ['pg_auth_members','admin_option','inherit_option','set_option','current_user','session_user','clock_timestamp()']:self.assertIn(text,q)
        for text in ['pg_authid','rolpassword','ALTER ','GRANT ','REVOKE ','PASSWORD']:self.assertNotIn(text,q)
        with self.assertRaises(ValueError):runtime_artifact.managed_role_query("';GRANT x--")
    def test_actual_revocation_required_not_empty_optional_metadata(self):
        f=self.fixture('REVOKED');self.assertEqual(self.result(f,'REVOKED')['status'],'CONFIRMED')
        for key in ['member','canSet','runtimeAdmin','migratorAdmin']:
            bad=self.fixture('REVOKED');bad[1][key]=True;self.assertEqual(self.result(bad,'REVOKED')['status'],'DENIED')
    def test_revocation_requires_restricted_login_and_no_edges(self):
        f=self.fixture('REVOKED');f[1]['restricted']=False;self.assertEqual(self.result(f,'REVOKED')['status'],'DENIED')
        f=self.fixture('REVOKED');f[1]['edges']=self.fixture()[1]['edges'];self.assertEqual(self.result(f,'REVOKED')['status'],'DENIED')
    def test_failed_entry_revokes_without_running_action(self):
        calls=[]
        with self.assertRaises(RuntimeError):runtime_artifact.managed_role_lifecycle(lambda:calls.append('grant'),lambda:{'status':'DENIED'},lambda:calls.append('revoke'),lambda:{'status':'CONFIRMED'},lambda:calls.append('action'))
        self.assertEqual(calls,['grant','revoke'])
    def test_grant_or_action_exception_still_revokes(self):
        for where in ['grant','action']:
            calls=[]
            def step(name):
                calls.append(name)
                if name==where:raise RuntimeError('injected failure')
            with self.assertRaises(RuntimeError):runtime_artifact.managed_role_lifecycle(lambda:step('grant'),lambda:{'status':'CONFIRMED'},lambda:step('revoke'),lambda:{'status':'CONFIRMED'},lambda:step('action'))
            self.assertEqual(calls[-1],'revoke')
    def test_unverified_revocation_never_returns_success(self):
        with self.assertRaises(RuntimeError):runtime_artifact.managed_role_lifecycle(lambda:None,lambda:{'status':'CONFIRMED'},lambda:None,lambda:{'status':'DENIED'},lambda:'done')



class ManagedAssignmentMetadataRegression(unittest.TestCase):
    fixture=ManagedRoleConfirmationRegression.fixture
    def test_actual_catalog_assignment_replaces_optional_api_lookup(self):
        f=self.fixture();f[1]['currentUser']=runtime_artifact.MANAGED_PRINCIPAL;f[1]['tls']=False
        self.assertEqual(runtime_artifact.managed_assignment_roles(f[1],f[2]['nonce'],f[3]),['cloudsqlsuperuser'])
        self.assertEqual(runtime_artifact.managed_role_confirmation(f[0],f[1],f[2],'DELEGATED',f[3])['status'],'DENIED')
    def test_revoked_assignment_catalog_is_empty(self):
        f=self.fixture('REVOKED');self.assertEqual(runtime_artifact.managed_assignment_roles(f[1],f[2]['nonce'],f[3]),[])
    def test_unknown_concurrent_membership_is_preserved_by_refusal(self):
        f=self.fixture('REVOKED');f[1]['edges']=[{'role':'other','member':runtime_artifact.MANAGED_PRINCIPAL,'grantor':'operator','admin':False,'inherit':True,'set':True}]
        with self.assertRaises(ValueError):runtime_artifact.managed_assignment_roles(f[1],f[2]['nonce'],f[3])
    def test_missing_stale_wrong_identity_catalog_cannot_be_empty_assignment(self):
        from datetime import timedelta
        for field,value in [('edges',None),('currentUser','postgres'),('sessionUser','postgres'),('nonce','wrong'),('observedAt',(self.fixture()[3]-timedelta(minutes=1)).isoformat()),('member',True)]:
            f=self.fixture('REVOKED');f[1][field]=value
            with self.assertRaises(ValueError):runtime_artifact.managed_assignment_roles(f[1],f[2]['nonce'],f[3])
    def test_catalog_query_includes_actual_role_options_and_no_secret_fields(self):
        import uuid
        q=runtime_artifact.managed_assignment_query(str(uuid.uuid4()));self.assertIn('WHERE m.rolname=session_user',q);self.assertNotIn("WHERE r.rolname='cloudsqlsuperuser'",q)
        for name in ['pg_authid','rolpassword','GRANT ','REVOKE ','ALTER ']:self.assertNotIn(name,q)



class ManagedAssignmentSequencingRegression(unittest.TestCase):
    def test_bounded_observation_retries_visibility_not_grants(self):
        ticks=[0.0];observations=iter([[],[],['cloudsqlsuperuser']]);calls=[]
        def fetch():calls.append('read');return next(observations)
        result=runtime_artifact.wait_managed_assignment(fetch,['cloudsqlsuperuser'],1,lambda:ticks[0],lambda t:ticks.__setitem__(0,ticks[0]+t))
        self.assertEqual(result,['cloudsqlsuperuser']);self.assertEqual(calls,['read']*3)
    def test_missing_authority_times_out_without_positive_default(self):
        ticks=[0.0]
        with self.assertRaises(TimeoutError):runtime_artifact.wait_managed_assignment(lambda:[],['cloudsqlsuperuser'],1,lambda:ticks[0],lambda t:ticks.__setitem__(0,ticks[0]+t))
        self.assertEqual(ticks[0],1)
    def test_invalid_snapshot_not_retried_or_accepted(self):
        for value in [None,['other'],'cloudsqlsuperuser']:
            with self.assertRaises(ValueError):runtime_artifact.wait_managed_assignment(lambda:value,['cloudsqlsuperuser'])
    def test_invalid_or_unbounded_deadlines_denied(self):
        for timeout in [0,16,True,float('inf'),float('nan'),'10']:
            with self.assertRaises(ValueError):runtime_artifact.wait_managed_assignment(lambda:[],[],timeout)



import datetime
import hashlib
import json
import os
import stat
import tempfile
from unittest.mock import Mock
staging_contract = runtime_artifact
SYNTHETIC_SQL = {name: ('-- SYNTHETIC_TEST_ONLY ' + name + '\nSELECT 1;\n').encode() for name in runtime_artifact.SQL_FILES}
APPROVED = {name: hashlib.sha256(data).hexdigest() for name, data in SYNTHETIC_SQL.items()}
MANIFEST = json.dumps(APPROVED)
NOW = datetime.datetime(2026, 11, 22, 8, 30, tzinfo=datetime.timezone.utc)
TOKEN = 'SYNTHETIC_CANARY_NOT_A_CREDENTIAL_123456789'


class SqlManifestStagingRegression(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.src = self.root / 'source'
        self.src.mkdir()
        for name in APPROVED:
            (self.src / name).write_bytes(SYNTHETIC_SQL[name])

    def stage(self, manifest=MANIFEST):
        return staging_contract.stage_approved_sql(self.src, self.root, manifest, APPROVED)

    def rejects(self, manifest=MANIFEST):
        with self.assertRaises((ValueError, OSError)):
            with self.stage(manifest):
                self.fail('Unsafe staging accepted')
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['source'])

    def test_exact_seven_ordered_files_and_private_permissions(self):
        with self.stage() as (directory, names):
            self.assertEqual(names, tuple(APPROVED))
            self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)
            for name in names:
                self.assertEqual(stat.S_IMODE((directory / name).stat().st_mode), 0o600)
                self.assertEqual(hashlib.sha256((directory / name).read_bytes()).hexdigest(), APPROVED[name])
        self.assertFalse(directory.exists())
        self.assertEqual(set(p.name for p in self.src.iterdir()), set(APPROVED))

    def test_original_glob_regression_catches_psql_omission(self):
        omitted = set(APPROVED) - {p.name for p in self.src.glob('*.sql.proposed')}
        self.assertEqual(omitted, {'01-protected-credentials-and-migrator-login.psql.proposed'})

    def test_missing_psql_file(self):
        (self.src / '01-protected-credentials-and-migrator-login.psql.proposed').unlink()
        self.rejects()

    def test_extra_sql_file(self):
        (self.src / '99-unapproved.sql.proposed').write_text('SELECT 1')
        self.rejects()

    def test_extra_psql_file(self):
        (self.src / '99-unapproved.psql.proposed').write_text('SELECT 1')
        self.rejects()

    def test_checksum_failure_cleans_partial_copy(self):
        (self.src / list(APPROVED)[3]).write_text('CHANGED')
        self.rejects()

    def test_duplicate_manifest_entry(self):
        name = next(iter(APPROVED))
        self.rejects(MANIFEST[:-1] + ',' + json.dumps(name) + ':' + json.dumps(APPROVED[name]) + '}')

    def test_order_mismatch(self):
        self.rejects(json.dumps(dict(reversed(list(APPROVED.items())))))

    def test_changed_manifest_hash(self):
        bad = dict(APPROVED)
        bad[next(iter(bad))] = '0' * 64
        self.rejects(json.dumps(bad))

    def test_extra_manifest_entry(self):
        self.rejects(json.dumps(dict(APPROVED, **{'extra.sql.proposed': '0' * 64})))

    def test_manifest_path_traversal(self):
        bad = dict(APPROVED)
        digest = bad.pop(next(iter(bad)))
        bad['../unapproved.sql.proposed'] = digest
        self.rejects(json.dumps(bad))

    def test_symlink_input_denied(self):
        name = next(iter(APPROVED))
        data = (self.src / name).read_bytes()
        (self.root / 'original').write_bytes(data)
        (self.src / name).unlink()
        (self.src / name).symlink_to(self.root / 'original')
        with self.assertRaises(OSError):
            with self.stage():
                self.fail('Symlink accepted')
        self.assertEqual(sorted(p.name for p in self.root.iterdir()), ['original', 'source'])

    def test_injected_partial_write_failure(self):
        original = staging_contract.os.open
        def opened(path, flags, *args, **kwargs):
            if flags & os.O_WRONLY and str(path).endswith(list(APPROVED)[2]):
                raise OSError('Synthetic failure')
            return original(path, flags, *args, **kwargs)
        with patch.object(staging_contract.os, 'open', opened):
            self.rejects()

    def test_rejected_execution_removes_staging(self):
        with self.assertRaises(RuntimeError):
            with self.stage() as (directory, _):
                raise RuntimeError('Rejected before authority acquisition')
        self.assertFalse(directory.exists())


class IamTokenExpiryRegression(unittest.TestCase):
    def setUp(self):
        self.wall = [NOW]
        self.monotonic = [100.0]

    def credential(self, seconds=600, token=TOKEN, principal=staging_contract.PRINCIPAL):
        expiry = (NOW + datetime.timedelta(seconds=seconds)).strftime('%Y-%m-%dT%H:%M:%SZ')
        return staging_contract.ProtectedIamCredential(token, expiry, principal, lambda: self.wall[0], lambda: self.monotonic[0])

    def helper_result(self, token=TOKEN, principal=staging_contract.PRINCIPAL, expiry=None):
        return Mock(returncode=0, stdout=json.dumps({'credential': {'access_token': token,
                   'token_expiry': expiry or (NOW + datetime.timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%SZ')},
                   'configuration': {'properties': {'core': {'account': principal}}}}))

    def test_valid_token_used_once_and_cleared(self):
        credential = self.credential()
        self.assertNotIn(TOKEN, repr(credential))
        self.assertEqual(credential.use(lambda value: {'authenticated': value == TOKEN}), {'authenticated': True})
        self.assertIsNone(credential._token)
        with self.assertRaises(RuntimeError):
            credential.use(lambda _: 'unexpected')

    def test_expired_and_near_expiry_rejected(self):
        for seconds in [-1, 0, 149, 150]:
            with self.assertRaises(ValueError):
                self.credential(seconds)

    def test_expiry_checked_immediately_before_authentication(self):
        credential = self.credential()
        self.wall[0] += datetime.timedelta(seconds=500)
        send = Mock()
        with self.assertRaises(RuntimeError):
            credential.use(send)
        send.assert_not_called()
        self.assertIsNone(credential._token)

    def test_monotonic_elapsed_prevents_wallclock_rollback_extension(self):
        credential = self.credential()
        self.monotonic[0] += 500
        with self.assertRaises(RuntimeError):
            credential.use(lambda _: 'unexpected')

    def test_wallclock_rollback_denied(self):
        credential = self.credential()
        self.wall[0] -= datetime.timedelta(seconds=1)
        with self.assertRaises(RuntimeError):
            credential.use(lambda _: 'unexpected')

    def test_invalid_or_missing_expiry(self):
        for value in ['', 'UNKNOWN', '2026-11-22T08:40:00+05:30']:
            with self.assertRaises(ValueError):
                staging_contract.ProtectedIamCredential(TOKEN, value, staging_contract.PRINCIPAL)

    def test_wrong_identity_denied(self):
        with self.assertRaises(ValueError):
            self.credential(principal='other@example.invalid')
        for field in ['impersonate_service_account', 'credential_file_override', 'access_token_file']:
            result = self.helper_result()
            data = json.loads(result.stdout)
            data['configuration']['properties']['auth'] = {field: 'SYNTHETIC_UNAPPROVED_OVERRIDE'}
            result.stdout = json.dumps(data)
            with self.assertRaises(RuntimeError):
                staging_contract.acquire_protected_iam('API', Mock(return_value=result), lambda: NOW, lambda: 100)
        with patch.dict(os.environ, {'CLOUDSDK_AUTH_ACCESS_TOKEN': TOKEN}):
            runner = Mock()
            with self.assertRaises(RuntimeError):
                staging_contract.acquire_protected_iam('API', runner, lambda: NOW, lambda: 100)
            runner.assert_not_called()

    def test_malformed_token_denied_without_echo(self):
        for token in [TOKEN + '\n', TOKEN + '\x00', TOKEN + ' ', TOKEN + chr(34), TOKEN + chr(92), 'short']:
            with self.assertRaises(ValueError) as caught:
                self.credential(token=token)
            self.assertNotIn(TOKEN, str(caught.exception))

    def test_unbounded_duration_denied(self):
        for value in [True, float('inf'), float('nan'), -1, 121, '120']:
            with self.assertRaises(ValueError):
                self.credential().check(value)

    def test_cli_acquisition_no_secret_arguments_or_output_files(self):
        runner = Mock(return_value=self.helper_result())
        credential = staging_contract.acquire_protected_iam('SQL', runner, lambda: NOW, lambda: 100)
        self.assertNotIn(TOKEN, repr(credential))
        self.assertEqual(runner.call_count, 1)
        for call in runner.call_args_list:
            self.assertNotIn(TOKEN, repr(call.args))
            self.assertNotIn('--output-file', repr(call.args))
            self.assertTrue(call.kwargs['capture_output'])
        self.assertIn('--force-auth-refresh', runner.call_args_list[0].args[0])
        self.assertIn('--scopes=https://www.googleapis.com/auth/sqlservice.login', runner.call_args_list[0].args[0])

    def test_sql_pair_does_not_compare_independently_refreshed_tokens(self):
        api_token = 'SYNTHETIC_API_TOKEN_123456789'
        sql_token = 'SYNTHETIC_SQL_TOKEN_987654321'
        def sdk(args, **kwargs):
            # The installed SDK independently refreshes each scoped credential.
            return self.helper_result(sql_token if any(a.startswith('--scopes=') for a in args) else api_token)
        runner = Mock(side_effect=sdk)
        api = staging_contract.acquire_protected_iam('API', runner, lambda: NOW, lambda: 100)
        sql = staging_contract.acquire_protected_iam('SQL', runner, lambda: NOW, lambda: 100)
        self.assertEqual(api.use(lambda value: value == api_token), True)
        self.assertEqual(sql.use(lambda value: value == sql_token), True)
        self.assertEqual(runner.call_count, 2)  # Exactly one call per purpose.
        self.assertTrue(all(call.args[0][1:3] == ['config', 'config-helper'] for call in runner.call_args_list))

    def test_failed_cli_hides_secret_bearing_error(self):
        runner = Mock(side_effect=RuntimeError(TOKEN))
        with self.assertRaises(RuntimeError) as caught:
            staging_contract.acquire_protected_iam('API', runner, lambda: NOW, lambda: 100)
        self.assertNotIn(TOKEN, str(caught.exception))

    def test_missing_expiry_from_cli_denied(self):
        result = self.helper_result()
        data = json.loads(result.stdout)
        data['credential']['token_expiry'] = ''
        result.stdout = json.dumps(data)
        with self.assertRaises(RuntimeError):
            staging_contract.acquire_protected_iam('API', Mock(return_value=result), lambda: NOW, lambda: 100)

    def test_401_or_unknown_mutation_outcome_never_replayed(self):
        for kind in ['MUTATION', 'READ_ONLY']:
            acquire = Mock(side_effect=self.credential)
            send = Mock(side_effect=RuntimeError('401 ' + TOKEN))
            with self.assertRaises(RuntimeError) as caught:
                staging_contract.authenticated_operation(acquire, send, kind)
            self.assertEqual(acquire.call_count, 1)
            self.assertEqual(send.call_count, 1)
            self.assertNotIn(TOKEN, str(caught.exception))

    def test_secret_in_return_value_denied(self):
        for value in [TOKEN, {'output': TOKEN}, TOKEN.encode()]:
            with self.assertRaises(RuntimeError) as caught:
                self.credential().use(lambda _: value)
            self.assertNotIn(TOKEN, str(caught.exception))

    def test_failed_authentication_uses_fresh_revoker_credential(self):
        calls = []
        action_credential = self.credential()
        revoke_credential = self.credential()
        self.wall[0] += datetime.timedelta(seconds=500)
        def revoke():
            self.wall[0] = NOW
            revoke_credential.use(lambda _: calls.append('fresh_revoke'))
        with self.assertRaises(RuntimeError):
            runtime_artifact.managed_role_lifecycle(
                lambda: calls.append('grant'), lambda: {'status': 'CONFIRMED'},
                revoke, lambda: {'status': 'CONFIRMED'},
                lambda: action_credential.use(lambda _: calls.append('unsafe_action')))
        self.assertEqual(calls, ['grant', 'fresh_revoke'])





class SqlBundleInstallationRegression(unittest.TestCase):
    setUp = SqlManifestStagingRegression.setUp

    def install(self):
        with patch.dict(runtime_artifact.SQL_FILES, APPROVED, clear=True):
            return runtime_artifact.install_approved_sql(self.src, self.destination, MANIFEST)

    def make_destination(self):
        self.destination = self.root / 'destination'
        self.destination.mkdir(mode=0o700)

    def test_complete_manifest_installation_includes_psql(self):
        self.make_destination()
        result = self.install()
        self.assertEqual(result['orderedFiles'], list(APPROVED))
        self.assertEqual({p.name for p in self.destination.iterdir()}, set(APPROVED))
        self.assertFalse(list(self.root.glob('cinenest-approved-sql-*')))

    def test_existing_sql_output_never_overwritten(self):
        self.make_destination()
        preserved = self.destination / next(iter(APPROVED))
        preserved.write_text('PRESERVE_EXISTING')
        with self.assertRaises(ValueError):
            self.install()
        self.assertEqual(preserved.read_text(), 'PRESERVE_EXISTING')

    def test_partial_installation_failure_removes_only_own_outputs(self):
        self.make_destination()
        retained = self.destination / 'public-helper.py'
        retained.write_text('PRESERVE_HELPER')
        link = runtime_artifact.os.link
        calls = []
        def fail_later(*args, **kwargs):
            calls.append(args)
            if len(calls) == 3:
                raise OSError('Synthetic installation failure')
            return link(*args, **kwargs)
        with patch.object(runtime_artifact.os, 'link', fail_later):
            with self.assertRaises(OSError):
                self.install()
        self.assertEqual(list(self.destination.iterdir()), [retained])
        self.assertFalse(list(self.root.glob('cinenest-approved-sql-*')))

    def test_insecure_destination_rejected(self):
        self.make_destination()
        self.destination.chmod(0o755)
        with self.assertRaises(ValueError):
            self.install()

    def test_explicit_remote_command_has_no_extension_glob(self):
        command = runtime_artifact.sql_staging_command()
        self.assertIn('install_approved_sql', command)
        self.assertNotIn('*.sql', command)
        self.assertNotIn(TOKEN, command)

    def test_changed_contract_or_wrong_order_cannot_install(self):
        self.make_destination()
        for manifest in [json.dumps(dict(reversed(list(APPROVED.items())))),
                         json.dumps(dict(APPROVED, **{'bad.psql.proposed': '0' * 64}))]:
            with patch.dict(runtime_artifact.SQL_FILES, APPROVED, clear=True):
                with self.assertRaises(ValueError):
                    runtime_artifact.install_approved_sql(self.src, self.destination, manifest)
            self.assertEqual(list(self.destination.iterdir()), [])


class RuntimeSafetyArtifactRegression(unittest.TestCase):
    def fixture(self):
        controller = "def preflight(v):return v['managed_confirmation_errors']()\ndef setup():\n return 'managed-role-source.sha256 *.sql.proposed cinenest-runtime/; cd cinenest-runtime;'\n"
        helpers = "E=Path('/tmp/cinenest-role-confirmation-fix-20261122/cloud-candidate');\n" + 'def auth():\n pass\ndef api():\n pass\ndef operation(op):\n pass\ndef private_sql(mode):\n pass\ndef role_context(result):\n pass\n'
        private = "fd=None;protected=[];authority_facts=None\ntry:\n token=request.pop('iamToken');protected.append(token);mode='probe'\n fd=os.memfd_create('cinenest-protected-passfile',0);value=1\n if mode=='credentials':\n  assert len(passwords)==2\nfinally:\n if fd is not None:os.close(fd)\n protected.clear()\n"
        return controller, helpers, private

    def test_offline_artifact_generation_and_remote_expiry_guard(self):
        files = runtime_artifact.runtime_safety_artifacts(*self.fixture())
        self.assertEqual(set(files), {'cloud-session.py', 'cloud-helpers.py', 'private-sql-helper.py'})
        self.assertIn("M['sql_staging_command']()", files['cloud-session.py'])
        self.assertNotIn('*.sql.proposed', files['cloud-session.py'])
        self.assertIn('iamTokenExpiry', files['cloud-helpers.py'])
        self.assertIn('E=Path(__file__).resolve().parent;', files['cloud-helpers.py'])
        self.assertNotIn('/tmp/cinenest-role-confirmation-fix-20261122/cloud-candidate', files['cloud-helpers.py'])
        private = files['private-sql-helper.py']
        self.assertLess(private.index('credential.check(maximum)'), private.index('fd=os.memfd_create'))
        self.assertIn('credential.close()', private)
        self.assertNotIn('token_time>1200', files['cloud-helpers.py'])

    def test_unknown_or_duplicate_predecessor_shape_denied(self):
        controller, helpers, private = self.fixture()
        for changed in [controller.replace('*.sql.proposed', 'unknown'), controller + controller]:
            with self.assertRaises(ValueError):
                runtime_artifact.runtime_safety_artifacts(changed, helpers, private)

    def test_generated_api_failure_never_replays_mutation(self):
        files = runtime_artifact.runtime_safety_artifacts(*self.fixture())
        credential = Mock()
        def use(sender, required_seconds):
            self.assertEqual(required_seconds, 30)
            return sender(TOKEN)
        credential.use.side_effect = use
        acquire = Mock(return_value=credential)
        urllib_request = Mock()
        urllib_error = __import__('urllib.error', fromlist=['HTTPError'])
        urllib_request.urlopen.side_effect = urllib_error.HTTPError('https://example.invalid', 401, 'Unauthorized', {}, None)
        scope = {'M': {'authenticated_operation': runtime_artifact.authenticated_operation,
                       'acquire_protected_iam': acquire,
                       'ProtectedRequestFailure': runtime_artifact.ProtectedRequestFailure}, 'urllib': Mock(request=urllib_request, error=urllib_error),
                 'json': json, 'deadline': None, 'time': __import__('time'),
                 'Path': Path, '__file__': str(Path.cwd() / 'synthetic-helper.py')}
        exec(files['cloud-helpers.py'], scope)
        with self.assertRaises(RuntimeError):
            scope['api']('PUT', 'https://example.invalid', {'public': 'fixture'})
        self.assertEqual(acquire.call_count, 1)
        self.assertEqual(urllib_request.urlopen.call_count, 1)

    def test_generation_never_executes_controller_or_cloud_calls(self):
        with patch.object(runtime_artifact.subprocess, 'run', side_effect=AssertionError('Unexpected subprocess')):
            files = runtime_artifact.runtime_safety_artifacts(*self.fixture())
        for name, text in files.items():
            compile(text, name, 'exec')

if __name__=='__main__':unittest.main()
