"""Offline resource/admission/cleanup refusal tests; never starts a container."""
import importlib.util
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import json
import tempfile
import time
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('credential_runner',Path(__file__).with_name('verify-customer-credential.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class AdmissionTests(unittest.TestCase):
    def test_lint_context_preserves_source_and_config(self):
        with tempfile.TemporaryDirectory(dir="/tmp") as tmp:
            root=Path(tmp)/"source";evidence=Path(tmp)/"evidence";evidence.mkdir()
            (root/"frontend").mkdir(parents=True);(root/"knowledge/execution").mkdir(parents=True)
            (root/"frontend/biome.json").write_text("{}")
            (root/"frontend/a.ts").write_text("const a = 1;")
            (root/"frontend/node_modules").mkdir()
            (root/".gitignore").write_text("node_modules/\n")
            (root/"knowledge/execution/status.json").write_text(json.dumps({"customerIdentityVertical":{"orderedPaths":["frontend/a.ts"]}}))
            def command(args,**kw):return "frontend/biome.json\nfrontend/a.ts" if args[1]=="ls-files" else ""
            with patch.object(m,"ROOT",root),patch.object(m,"run",side_effect=command):
                target=m.lint_context(evidence)
                self.assertEqual((target/"a.ts").read_bytes(),(root/"frontend/a.ts").read_bytes())
                self.assertEqual((target/"biome.json").read_bytes(),(root/"frontend/biome.json").read_bytes())
                self.assertFalse(json.loads((evidence/"lint-context.json").read_text())["rulesChanged"])

    def test_real_stage_creates_private_log(self):
        with tempfile.TemporaryDirectory(dir="/tmp") as tmp:
            p=Path(tmp)
            m.stage("unit-smoke",[sys.executable,"-c","print(123)"],p,{},p,time.monotonic()+10)
            self.assertEqual((p/"unit-smoke.log").read_text().strip(),"123")
            self.assertEqual((p/"unit-smoke.log").stat().st_mode&0o777,0o600)
            with self.assertRaises(FileExistsError):m.stage("unit-smoke",[sys.executable,"-c","print(456)"],p,{},p,time.monotonic()+10)

    def completion_approval(self):
        return {'status':'APPROVED','source':m.COMPLETION_SOURCE,'requestSha256':m.COMPLETION_SHA,
                'sessions':1,'maximumSecondsIncludingCleanup':1800,'cloudExecution':False,
                'runtimeSourceSha256':'source','sqlSha256':'sql','recordedAtEpoch':100,'launchExpiresEpoch':200}
    def test_completion_rejects_missing_forged_stale_or_drifted_approval(self):
        valid=self.completion_approval()
        cases=[{},*[{**valid,k:v} for k,v in [('status','PROPOSED'),('source','invented'),('requestSha256','f'*64),
               ('sessions',2),('maximumSecondsIncludingCleanup',1801),('cloudExecution',True),
               ('runtimeSourceSha256','drift'),('sqlSha256','drift'),('recordedAtEpoch',151),('launchExpiresEpoch',149)]]]
        with patch.object(m,'source_pin',return_value='source'),patch.object(m,'digest',return_value='sql'),patch.object(m.time,'time',return_value=150):
            for case in cases:
                with self.subTest(case=case),self.assertRaises(RuntimeError):m.completion_marker({'completionSession':case})
    def test_completion_requires_original_consumption_evidence(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as tmp,patch.object(m,'source_pin',return_value='source'),patch.object(m,'digest',return_value='sql'),patch.object(m.time,'time',return_value=150),patch.object(m,'consumption_path',return_value=Path(tmp)/'absent.json'):
            with self.assertRaisesRegex(RuntimeError,'Original consumed marker'):m.completion_marker({'completionSession':self.completion_approval()})
    def test_completion_consumes_once_and_preserves_original_marker(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as tmp:
            original=Path(tmp)/'consumed.json';original.write_text(json.dumps({'authoritySha256':m.AUTHORIZATION_SHA,'maximumSeconds':1800}));before=original.read_bytes()
            unit={'localSessionsConsumed':1,'completionSession':self.completion_approval()}
            with patch.object(m,'source_admission',return_value=unit),patch.object(m,'source_pin',return_value='source'),patch.object(m,'digest',return_value='sql'),patch.object(m.time,'time',return_value=150),patch.object(m,'consumption_path',return_value=original):
                m.consume_session(Path('/tmp/task'))
                with self.assertRaisesRegex(RuntimeError,'already consumed'):m.consume_session(Path('/tmp/another'))
            self.assertEqual(original.read_bytes(),before)
    def test_production_environment_not_forwarded(self):
        with patch.dict(m.os.environ,{"CINENEST_DB_PASSWORD":"do-not-forward","NEXT_PUBLIC_SECRET":"do-not-forward","GOOGLE_APPLICATION_CREDENTIALS":"/private/credential"}):
            value=m.runtime_environment()
            self.assertNotIn("CINENEST_DB_PASSWORD",value)
            self.assertNotIn("NEXT_PUBLIC_SECRET",value)
            self.assertNotIn("GOOGLE_APPLICATION_CREDENTIALS",value)
            self.assertEqual(value["CINENEST_BUILD_CPUS"],"2")

    def test_direct_inside_outside_owned_unit_denied(self):
        with self.assertRaises(RuntimeError):m.supervision_group(Path("/tmp/"+m.PREFIX+"not-current"),"execute")

    def test_remote_daemon_denied(self):
        with self.assertRaises(RuntimeError):m.local_docker_environment({"DOCKER_HOST":"tcp://remote:2375"})
    def test_default_context_does_not_choose_another_daemon(self):
        value=m.local_docker_environment({})
        self.assertEqual(value["DOCKER_HOST"],"unix:///var/run/docker.sock")
        self.assertNotIn("DOCKER_CONTEXT",value)
    def test_drifted_actual_container_limits_denied(self):
        with self.assertRaises(RuntimeError):m.verify_container_limits({"HostConfig":{},"Config":{}})

    def test_default_is_nonexecuting(self):
        with patch.object(m,'supervise') as run,patch.object(m,'inventory') as inv:
            self.assertEqual(m.main([]),0);run.assert_not_called();inv.assert_not_called()
    def test_execute_requires_prepared_evidence(self):
        with self.assertRaises(SystemExit):m.main(['--execute'])
    def test_execute_requires_sql(self):
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            with self.assertRaises(SystemExit):m.main(['--execute','--evidence',tmp])
    def test_relative_evidence_denied(self):
        with self.assertRaises(RuntimeError):m.owned_path('relative')
    def test_unowned_prefix_denied(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as tmp:
            with self.assertRaises(RuntimeError):m.owned_path(tmp)
    def test_world_readable_evidence_denied(self):
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            Path(tmp).chmod(0o755)
            with self.assertRaises(RuntimeError):m.owned_path(tmp)
    def test_new_evidence_private(self):
        import shutil
        tmp=m.new_evidence()
        try:self.assertEqual(tmp.stat().st_mode&0o777,0o700);self.assertEqual((tmp/'ownership.json').stat().st_mode&0o777,0o600)
        finally:shutil.rmtree(tmp)
    def test_sql_status_and_fingerprint_denied(self):
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            p=Path(tmp)/'sql.json';p.write_text(json.dumps({'status':'PROPOSED'}))
            with self.assertRaises(RuntimeError):m.sql_admission(str(p))
    def test_no_arbitrary_database_target(self):
        source=Path(m.__file__).read_text()
        self.assertIn("'--publish','127.0.0.1::5432'",source)
        self.assertIn('sslmode=verify-full',source)
        self.assertNotIn('sslmode=require',source)
    def test_runtime_image_and_limits(self):
        source=Path(m.__file__).read_text()
        for value in ['--pull=never','--cpus=1','--memory=1g','--pids-limit=128','--shm-size=16m','rw,size=496m','KillMode=control-group']:
            self.assertIn(value,source)
        self.assertEqual(m.MAX_SECONDS,1800);self.assertLess(m.ACTIVE_SECONDS,m.MAX_SECONDS)
    def test_owned_removal_allows_bounded_async_convergence(self):
        with patch.object(m,'owned_container',side_effect=[{'Id':'owned'},None]),patch.object(m.time,'sleep'):
            m.wait_owned_container_removal(Path('/tmp/task'),'owned')
    def test_owned_removal_refuses_identity_drift(self):
        with patch.object(m,'owned_container',return_value={'Id':'replacement'}):
            with self.assertRaisesRegex(RuntimeError,'identity drift'):m.wait_owned_container_removal(Path('/tmp/task'),'owned')
    def test_owned_removal_timeout_stays_failed(self):
        with patch.object(m,'owned_container',return_value={'Id':'owned'}),patch.object(m.time,'monotonic',side_effect=[0,6]):
            with self.assertRaisesRegex(RuntimeError,'removal incomplete'):m.wait_owned_container_removal(Path('/tmp/task'),'owned')
    def test_cleanup_refuses_wrong_owner(self):
        class Result:returncode=0;stdout=json.dumps([{'Name':'/wrong','Config':{'Labels':{}},'Id':'other'}])
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            p=Path(tmp);(p/'ownership.json').write_text(json.dumps({'name':p.name,'owner':'exact'}))
            with patch.object(m.subprocess,'run',return_value=Result()):
                with self.assertRaises(RuntimeError):m.owned_container(p)
    def test_permission_error_is_not_absence(self):
        class Result:returncode=1;stderr='permission denied';stdout=''
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            p=Path(tmp);(p/'ownership.json').write_text(json.dumps({'name':p.name,'owner':'exact'}))
            with patch.object(m.subprocess,'run',return_value=Result()):
                with self.assertRaises(RuntimeError):m.owned_container(p)
    def test_typed_absence(self):
        class Result:returncode=1;stderr='Error: No such container';stdout=''
        with tempfile.TemporaryDirectory(prefix=m.PREFIX,dir='/tmp') as tmp:
            p=Path(tmp);(p/'ownership.json').write_text(json.dumps({'name':p.name,'owner':'exact'}))
            with patch.object(m.subprocess,'run',return_value=Result()):self.assertIsNone(m.owned_container(p))
    def test_global_consumption_covers_new_evidence_directory(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as tmp:
            marker=Path(tmp)/'owned'/'consumed.json'
            with patch.object(m,'consumption_path',return_value=marker),patch.object(m,'source_admission',return_value={'localSessionsConsumed':0}):
                m.consume_session(Path('/tmp/first'))
                with self.assertRaises(RuntimeError):m.consume_session(Path('/tmp/second'))
                self.assertEqual(json.loads(marker.read_text())['evidence'],'/tmp/first')
    def test_recorded_consumption_refuses_restart(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as tmp:
            with patch.object(m,'consumption_path',return_value=Path(tmp)/'owned'/'consumed.json'),patch.object(m,'source_admission',return_value={'localSessionsConsumed':1}):
                with self.assertRaises(RuntimeError):m.consume_session(Path('/tmp/another'))
    def test_source_admission_refuses_narrowed_scope(self):
        with patch.object(m.runpy,'run_path',return_value={'VERTICAL_errors':lambda **kw:['scope drift']}):
            with self.assertRaises(RuntimeError):m.source_admission()
    def test_source_admission_required_before_preparation(self):
        with patch.object(m,'bounded_group'),patch.object(m,'supervision_group'),patch.object(m,'source_admission',side_effect=RuntimeError('missing')),patch.object(m,'stage') as stage:
            with self.assertRaises(RuntimeError):m.prepare(Path('/tmp/unused'))
            stage.assert_not_called()
    def test_no_cloud_or_broad_cleanup_commands(self):
        source=Path(m.__file__).read_text()
        for value in ['docker system prune','docker volume prune','gcloud ','allContainers','rm -rf']:
            self.assertNotIn(value,source)

if __name__=='__main__':unittest.main()
