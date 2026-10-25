"""Explicit opt-in synthetic-only local qualification. Never invokes AWS or application APIs."""
import argparse
import datetime
import base64
import shutil
import uuid
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ROOT / 'qualification/receipt-local/compose.yaml'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--integration', action='store_true', help='Explicit isolated synthetic application integration')
    args = parser.parse_args()
    if not args.execute:
        parser.error('Explicit --execute required; creates only isolated local test resources')
    if os.environ.get('DOCKER_HOST') != 'unix:///var/run/docker.sock':
        raise SystemExit('Explicit qualified local DOCKER_HOST required')
    sources = [Path(__file__), COMPOSE, ROOT / 'backend/src/test/java/com/cinenest/payments/internal/LocalReceiptQualificationTest.java']
    if args.integration:
        package = ROOT / 'backend/src/main/java/com/cinenest/payments/internal'
        sources += [package / name for name in ['BankEvidenceProcessor.java', 'BankService.java', 'SyntheticEvidenceProcessor.java',
            'LocalDockerEvidenceConfiguration.java', 'LocalS3EvidenceStore.java', 'LocalEvidenceReceiptJournal.java',
            'ClamAvSocketClient.java', 'ClamAvEvidenceProcessor.java']]
        sources += [ROOT / 'backend/src/test/java' / name for name in ['com/cinenest/LocalReceiptIntegrationTest.java',
            'com/cinenest/payments/internal/LocalReceiptJournalTest.java', 'com/cinenest/payments/internal/LocalReceiptRestartTest.java', 'com/cinenest/payments/internal/BankFaults.java']]
    source_hashes = {str(p.relative_to(ROOT)): __import__('hashlib').sha256(p.read_bytes()).hexdigest() for p in sources}
    cache = ROOT / '.cache/local-receipts'
    cache.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=cache))
    run.chmod(0o700)
    project = 'cinenest-receipt-' + secrets.token_hex(6)
    access, secret, password = secrets.token_hex(12), secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    resource_run = Path(tempfile.mkdtemp(prefix='cn-ri-')) if args.integration else run
    if args.integration:
        (resource_run / 'socket').mkdir(mode=0o700)
        (resource_run / 'clamd.conf').write_text('LocalSocket /run/receipt/clamd.sock\nLocalSocketMode 600\nDatabaseDirectory /var/lib/clamav\nForeground yes\nLogTime yes\nMaxScanTime 60000\nStreamMaxLength 5M\nMaxFileSize 5M\nMaxScanSize 20M\nAlertExceedsMax yes\n')
        (resource_run / 'clamd.conf').chmod(0o600)
    config = resource_run / 's3.json'
    config.write_text(json.dumps({'identities': [{'name': 'qualification', 'credentials': [
        {'accessKey': access, 'secretKey': secret}], 'actions': ['Admin', 'Read', 'Write', 'List', 'Tagging']}]}))
    config.chmod(0o600)
    env = dict(os.environ, QUAL_RUN=str(resource_run), QUAL_DB_PASSWORD=password, QUAL_UID=str(os.getuid()), QUAL_GID=str(os.getgid()))
    compose = ['docker', 'compose', *(['--profile', 'integration'] if args.integration else []), '-p', project, '-f', str(COMPOSE)]

    scanner_service = 'scanner-integration' if args.integration else 'scanner'

    def call(argv, **kw):
        return subprocess.check_output(argv, env=env, text=True, **kw).strip()

    def cid(service):
        result = call(compose + ['ps', '-a', '-q', service])
        info = json.loads(call(['docker', 'inspect', result]))[0]
        if info['Config']['Labels'].get('com.docker.compose.project') != project:
            raise RuntimeError('Resource ownership mismatch')
        return result

    try:
        subprocess.run(compose + ['up', '-d', '--pull', 'never', 'storage', 'database', scanner_service], env=env, check=True)
        # No evidence mounted into updater. This isolated process may download official signatures only.
        scanner = cid(scanner_service)
        info = json.loads(call(['docker', 'inspect', scanner]))[0]
        volume = next(m['Name'] for m in info['Mounts'] if m['Destination'] == '/var/lib/clamav')
        subprocess.run(['docker', 'run', '--rm', '--name', project + '-updater', '--label', 'com.cinenest.task=CN-R1-PAYSLIP-LOCAL-QUALIFICATION-001',
                        '--cpus=1', '--memory=1g', '--pids-limit=64', '-v', volume + ':/var/lib/clamav',
                        '--entrypoint', 'freshclam',
                        'clamav/clamav:1.5.4@sha256:e19130cdebaeacc71fa46eab3ed6f69941f2f22aa5e08d55e9197f8edf43ce5e',
                        '--stdout', '--foreground'], env=env, check=True, timeout=180)
        subprocess.run(compose + ['restart', scanner_service], env=env, check=True)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            states = [json.loads(call(['docker', 'inspect', cid(s)]))[0]['State'] for s in ['storage', scanner_service, 'database']]
            if all(s.get('Health', {}).get('Status') == 'healthy' for s in states):
                break
            if any(s['Status'] == 'exited' for s in states):
                raise RuntimeError('Owned service exited; inspect only project-scoped logs')
            time.sleep(1)
        else:
            raise RuntimeError('Local service health deadline exceeded')
        metadata = call(['docker', 'exec', scanner, 'clamdscan', *(['--config-file=/run/receipt-config/clamd.conf'] if args.integration else []), '--version'])
        # ClamAV reports engine/signature version/signature date. Fail if unsupported or stale.
        fields = metadata.split('/', 2)
        signature = datetime.datetime.strptime(fields[2].strip(), '%a %b %d %H:%M:%S %Y').replace(tzinfo=datetime.timezone.utc)
        if not datetime.timedelta(0) <= datetime.datetime.now(datetime.timezone.utc) - signature <= datetime.timedelta(hours=24):
            raise RuntimeError('Actual signature database outside approved local freshness limit')
        s3_port = call(compose + ['port', 'storage', '8333']).rsplit(':', 1)[1]
        db_port = call(compose + ['port', 'database', '5432']).rsplit(':', 1)[1]
        env.update(CINENEST_LOCAL_RECEIPT='true', QUAL_ACCESS=access, QUAL_SECRET=secret,
                   QUAL_S3_URL='http://127.0.0.1:' + s3_port, QUAL_SCANNER=scanner,
                   QUAL_STORAGE=cid('storage'), QUAL_DATABASE=cid('database'),
                   QUAL_DB_URL='jdbc:postgresql://127.0.0.1:' + db_port + '/receipt_qualification',
                   QUAL_SIGNATURE_TIME=signature.isoformat().replace('+00:00', 'Z'))
        if args.integration:
            env.update(CINENEST_LOCAL_RECEIPT_INTEGRATION='true',
                CINENEST_DB_URL=env['QUAL_DB_URL'], CINENEST_DB_USER='qualification', CINENEST_DB_PASSWORD=password,
                QUAL_LOCAL_JOURNAL=str(resource_run / 'journal'), QUAL_LOCAL_NAMESPACE=str(uuid.uuid4()),
                QUAL_LOCAL_ENDPOINT=env['QUAL_S3_URL'], QUAL_LOCAL_BUCKET='receipts', QUAL_LOCAL_ACCESS=access,
                QUAL_LOCAL_SECRET=secret, QUAL_LOCAL_SOCKET=str(resource_run / 'socket/clamd.sock'),
                QUAL_JOURNAL_KEY=base64.b64encode(secrets.token_bytes(32)).decode(),
                QUAL_RESTART_RECEIPT=str(run / 'restart.json'))
            env.pop('CINENEST_LOCAL_RECEIPT', None)
        with (run / 'tests.log').open('w') as log:
            subprocess.run([str(ROOT / 'backend/mvnw'), '-B', '-Dmaven.repo.local=' + str(ROOT / '.cache/m2'),
                            '-Dtest=' + ('LocalReceiptJournalTest,LocalReceiptIntegrationTest' if args.integration else 'LocalReceiptQualificationTest'), 'test'], cwd=ROOT / 'backend', env=env,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        if args.integration:
            with (run / 'application-restart.log').open('w') as log:
                subprocess.run([str(ROOT / 'backend/mvnw'), '-B', '-Dmaven.repo.local=' + str(ROOT / '.cache/m2'),
                    '-Dtest=LocalReceiptApplicationRestartTest', 'test'], cwd=ROOT / 'backend', env=env,
                    stdout=log, stderr=subprocess.STDOUT, check=True)
            with (run / 'restart.log').open('w') as log:
                subprocess.run([str(ROOT / 'backend/mvnw'), '-B', '-Dmaven.repo.local=' + str(ROOT / '.cache/m2'),
                    '-Dtest=LocalReceiptRestartTest', 'test'], cwd=ROOT / 'backend', env=env,
                    stdout=log, stderr=subprocess.STDOUT, check=True)
        prefix = 'LOCAL_INTEGRATION_PASS ' if args.integration else 'LOCAL_RECEIPT_PASS '
        lines = [line.strip() for line in ((run / 'tests.log').read_text() + (((run / 'application-restart.log').read_text() + (run / 'restart.log').read_text()) if args.integration else '')).splitlines() if line.startswith(prefix)]
        result = {'source': call(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']),
            'scope': 'LOCAL_RUNTIME_SYNTHETIC_ONLY' if args.integration else 'LOCAL_CONTAINERS_SYNTHETIC_ONLY', 'awsQualified': False, 'scanner': metadata,
            'results': lines, 'status': 'PASS'}
    finally:
        updater = subprocess.run(['docker', 'inspect', project + '-updater'], env=env, capture_output=True, text=True)
        if updater.returncode == 0:
            labels = json.loads(updater.stdout)[0]['Config']['Labels']
            if labels.get('com.cinenest.task') != 'CN-R1-PAYSLIP-LOCAL-QUALIFICATION-001':
                raise RuntimeError('Updater cleanup ownership mismatch')
            subprocess.run(['docker', 'stop', '--time', '5', project + '-updater'], env=env, check=True, stdout=subprocess.DEVNULL)
        # Compose confines removal to this unpredictable project; shared images are retained.
        subprocess.run(compose + ['down', '--volumes', '--remove-orphans', '--timeout', '5'], env=env, check=True)
        config.unlink(missing_ok=True)
        if args.integration:
            shutil.rmtree(resource_run)
    remaining = call(['docker', 'ps', '-a', '-q', '--filter', 'label=com.docker.compose.project=' + project])
    volumes = call(['docker', 'volume', 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + project])
    if remaining or volumes:
        raise RuntimeError('Owned cleanup incomplete')
    result['cleanupVerified'] = True
    if source_hashes != {str(p.relative_to(ROOT)): __import__('hashlib').sha256(p.read_bytes()).hexdigest() for p in sources}:
        raise RuntimeError('Source changed during execution; evidence rejected')
    result['sourceHashes'] = source_hashes
    (run / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
    print('\n'.join(lines))
    print('PASS local qualification; evidence:', run / 'results.json')


if __name__ == '__main__':
    main()
