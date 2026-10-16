"""Isolated launcher contract tests; no application/database/Docker startup."""
from pathlib import Path
import os
import signal
import socket
import subprocess
import tempfile
import time
import unittest
ROOT = Path(__file__).resolve().parents[1]
class Launchers(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='cinenest-launcher-');self.addCleanup(self.tmp.cleanup)
        self.home=Path(self.tmp.name);tools=self.home/'bin';tools.mkdir()
        for name,output in [('java','openjdk version "25.0.4.1"'),('javac','javac 25'),('node','v24.22.0'),('npm','11.19.1')]:
            p=tools/name;p.write_text('#!/bin/sh\nprintf \'%s\\n\' \''+output+'\'\n');p.chmod(0o755)
        self.env={k:v for k,v in os.environ.items() if not k.startswith(('SPRING_','CINENEST_')) and k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS','NODE_OPTIONS','NODE_ENV')}
        self.env.update(JAVA_HOME=str(self.home),CINENEST_NODE_BIN=str(tools),CINENEST_DEV_DATABASE='1',CINENEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/synthetic',CINENEST_DB_USER='SYNTHETIC',CINENEST_DB_PASSWORD='SYNTHETIC-NOT-A-REAL-SECRET')
    def run_script(self,name,*args,env=None):
        return subprocess.run([str(ROOT/name),*args],env=env or self.env,cwd='/tmp',text=True,capture_output=True,timeout=15)
    def test_syntax_permissions_and_help_any_directory(self):
        for name in ['Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh']:
            self.assertTrue(os.access(ROOT/name,os.X_OK));subprocess.run(['bash','-n',str(ROOT/name)],check=True)
            self.assertEqual(self.run_script(name,'--help').returncode,0)
    def test_unknown_arguments_fail(self):
        for name in ['Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh']:self.assertNotEqual(self.run_script(name,'--unknown').returncode,0)
    def test_backend_requires_explicit_credentials(self):
        env=dict(self.env);env.pop('CINENEST_DB_PASSWORD');r=self.run_script('Run-backend.sh','--check',env=env)
        self.assertNotEqual(r.returncode,0);self.assertIn('no credentials',r.stderr)
    def test_remote_database_rejected_without_disclosure(self):
        r=self.run_script('Run-backend.sh','--check',env=dict(self.env,CINENEST_DB_URL='jdbc:postgresql://remote.invalid/private'))
        self.assertNotEqual(r.returncode,0);self.assertNotIn('private',r.stderr)
    def test_local_confirmation_required(self):
        env=dict(self.env);env.pop('CINENEST_DEV_DATABASE');self.assertNotEqual(self.run_script('Run-backend.sh','--check',env=env).returncode,0)
    def test_spring_overrides_rejected(self):
        self.assertNotEqual(self.run_script('Run-backend.sh','--check',env=dict(self.env,SPRING_PROFILES_ACTIVE='production')).returncode,0)
    def test_production_frontend_rejected(self):
        self.assertNotEqual(self.run_script('Run-frontend.sh','--check',env=dict(self.env,NODE_ENV='production')).returncode,0)
    def test_toolchain_mismatch_rejected(self):
        (self.home/'bin/node').write_text('#!/bin/sh\necho v22.0.0\n');self.assertNotEqual(self.run_script('Run-frontend.sh','--check').returncode,0)
    def test_port_conflict_preserves_owner(self):
        with socket.socket() as s:
            s.bind(('127.0.0.1',0));s.listen();r=self.run_script('Run-backend.sh','--check',env=dict(self.env,CINENEST_HTTP_PORT=str(s.getsockname()[1])))
            self.assertNotEqual(r.returncode,0);self.assertIn('unavailable',r.stderr);self.assertGreater(s.fileno(),0)
    def test_remote_auth_origin_rejected(self):
        r=self.run_script('Run-frontend.sh','--check',env=dict(self.env,CINENEST_AUTH_ORIGIN='https://remote.invalid'));self.assertNotEqual(r.returncode,0);self.assertIn('loopback HTTP',r.stderr)
    def test_local_backend_preflight(self):
        with socket.socket() as s:
            s.bind(('127.0.0.1',0));port=s.getsockname()[1]
        r=self.run_script('Run-backend.sh','--check',env=dict(self.env,CINENEST_HTTP_PORT=str(port)))
        self.assertEqual(r.returncode,0,r.stderr);self.assertIn('no database connection',r.stdout)
    def test_sibling_failure_stops_owned_group(self):
        marker=self.home/'sibling-pid';code='import os,time,pathlib;pathlib.Path('+repr(str(marker))+').write_text(str(os.getpid()));time.sleep(60)'
        r=subprocess.run(['bash','-c','source "$1/Run.sh"; cn_supervise python3 -c "import time; time.sleep(.6); raise SystemExit(7)" ::: python3 -c "$2"','test',str(ROOT),code],capture_output=True,timeout=15)
        self.assertEqual(r.returncode,7);self.assertTrue(marker.exists())
        with self.assertRaises(ProcessLookupError):os.kill(int(marker.read_text()),0)
    def test_supervisor_propagates_exit_status(self):
        r=subprocess.run(['bash','-c','source "$1/Run.sh"; cn_supervise python3 -c "raise SystemExit(7)"','test',str(ROOT)],capture_output=True,timeout=15)
        self.assertEqual(r.returncode,7)
        for _ in range(4):
            fast=subprocess.run(['bash','-c','source "$1/Run.sh"; cn_supervise false','test',str(ROOT)],capture_output=True,timeout=15)
            self.assertEqual(fast.returncode,1)
    def test_term_stops_owned_child(self):
        marker=self.home/'pid';code='import os,time,pathlib;pathlib.Path('+repr(str(marker))+').write_text(str(os.getpid()));time.sleep(60)'
        p=subprocess.Popen(['bash','-c','source "$1/Run.sh"; cn_supervise python3 -c "$2"','test',str(ROOT),code],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            for _ in range(100):
                if marker.exists():break
                time.sleep(.02)
            self.assertTrue(marker.exists());child=int(marker.read_text());p.send_signal(signal.SIGTERM);p.wait(timeout=15)
            with self.assertRaises(ProcessLookupError):os.kill(child,0)
        finally:
            if p.poll() is None:p.terminate();p.wait(timeout=15)
if __name__=='__main__':unittest.main()
