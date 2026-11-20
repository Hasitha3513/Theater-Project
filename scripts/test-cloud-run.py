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

if __name__=='__main__':unittest.main()
