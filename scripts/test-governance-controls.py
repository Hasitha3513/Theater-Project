"""Six read-only, in-memory validator fault injections; no resource operations."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import runpy
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / 'knowledge'


def main():
    validator = runpy.run_path(str(K / 'execution/validate_execution.py'))
    controls = [
        ('requirements.json', 'requirements', 'CN-NFR-04', 'implementationStatus', 'VERIFIED_WITH_EVIDENCE'),
        ('requirements.json', 'requirements', 'CN-NFR-04', 'acceptanceStatus', 'ACCEPTED'),
        ('test-plan.json', 'tests', 'T-CN-NFR-04', 'status', 'PASS'),
        ('change-sets.json', 'changeSets', 'CN-ACCEPT-001', 'workStatus', 'VERIFIED'),
        ('change-sets.json', 'changeSets', 'CN-ACCEPT-001', 'dependencies', []),
    ]
    mutations = []
    for filename, key, identity, field, value in controls:
        path = K / 'execution' / filename
        data = json.loads(path.read_text())
        next(x for x in data[key] if x['id'] == identity)[field] = value
        mutations.append((path, json.dumps(data).encode(), identity + '/' + field))
    path = K / 'PROGRESS.md'
    original = path.read_text()
    changed = original.replace('- [ ] **P9-C06', '- [x] **P9-C06')
    if changed == original:
        raise AssertionError('Expected pending recovery criterion absent')
    mutations.append((path, changed.encode(), 'P9-C06 acceptance'))
    original_rb, original_rt = Path.read_bytes, Path.read_text
    for target, replacement, name in mutations:
        def rb(path, *args, **kwargs):
            return replacement if path == target else original_rb(path, *args, **kwargs)
        def rt(path, *args, **kwargs):
            return replacement.decode() if path == target else original_rt(path, *args, **kwargs)
        with patch.object(Path, 'read_bytes', rb), patch.object(Path, 'read_text', rt), redirect_stdout(io.StringIO()):
            result = validator['inert_ci_main']()
        if result == 0:
            raise AssertionError('Validator accepted ' + name)
        print('PASS rejected', name)

    test_future_register_controls()
    test_p1_c03_closure_controls(validator)
    test_p2_c06_closure_controls(validator)
    test_booking_confirmation_controls(validator)
    test_product_documentation_controls()
    test_popular_session_starts_controls(validator)
    test_synthetic_cancellation_controls(validator)
    test_cancellation_frontend_controls(validator)


def test_future_register_controls():
    """Positive and negative format/lifecycle tests use in-memory records only."""
    import re
    module = runpy.run_path(str(K / 'validate_design_kb.py'))
    path = K / 'backlog/FUTURE_FEATURES.md'
    original = path.read_text()
    template = re.search(r'```markdown\n(.*?)\n```', original, re.S).group(1)
    record = template.replace('FF-<NNNN> — <title>', 'FF-0001 — In-memory validation fixture')
    record = re.sub(r'^- Classification:.*$', '- Classification: FUTURE_ENHANCEMENT', record, flags=re.M)
    record = re.sub(r'^- Discovery date:.*$', '- Discovery date: 2026-10-22', record, flags=re.M)
    record = re.sub(r'^- Dependencies:.*$', '- Dependencies: NONE', record, flags=re.M)
    record = re.sub(r'^- Owner approval:.*$', '- Owner approval: PENDING', record, flags=re.M)
    record = re.sub(r'^- Implementation authority:.*$', '- Implementation authority: NOT_GRANTED', record, flags=re.M)
    record = re.sub(r'^- Commit SHA:.*$', '- Commit SHA: NOT_IMPLEMENTED', record, flags=re.M)
    record = re.sub(r'^- CI evidence:.*$', '- CI evidence: NOT_RUN', record, flags=re.M)
    record = re.sub(r'^- Acceptance:.*$', '- Acceptance: NOT_ACCEPTED', record, flags=re.M)
    rb = Path.read_text
    def errors_for(value):
        failures = []
        with patch.object(Path, 'read_text', lambda p, *a, **kw: value if p == path else rb(p, *a, **kw)):
            module['validate_future_features'](lambda ok, message: failures.append(message) if not ok else None)
        return failures
    assert not errors_for(original), 'Empty authorized register rejected'
    assert not errors_for(original + '\n' + record), 'Valid IDEA record rejected'
    bad = {
        'duplicate ID': record + '\n' + record,
        'invalid classification': record.replace('Classification: FUTURE_ENHANCEMENT', 'Classification: APPROVED_FEATURE'),
        'invalid lifecycle': record.replace('Implementation status: IDEA', 'Implementation status: COMPLETE'),
        'missing source': re.sub(r'^- Source/reference:.*\n', '', record, flags=re.M),
        'unapproved feature': record.replace('Implementation status: IDEA', 'Implementation status: APPROVED'),
        'unauthorized readiness': record.replace('Implementation status: IDEA', 'Implementation status: READY').replace('Owner approval: PENDING', 'Owner approval: in-memory bounded approval fixture'),
        'unverified success': record.replace('Implementation status: IDEA', 'Implementation status: VERIFIED').replace('Owner approval: PENDING', 'Owner approval: in-memory bounded approval fixture').replace('Implementation authority: NOT_GRANTED', 'Implementation authority: GRANTED in-memory test only'),
        'self dependency': record.replace('Dependencies: NONE', 'Dependencies: FF-0001'),
        'unknown dependency': record.replace('Dependencies: NONE', 'Dependencies: FF-9999'),
    }
    for name, broken in bad.items():
        assert errors_for(original + '\n' + broken), 'Future register accepted ' + name
        print('PASS future register rejected', name)
    print('PASS future register:2 positive and9 negative in-memory cases;no saved feature or authority')


def test_p1_c03_closure_controls(validator):
    """Only the exact approved criterion closure may advance the roadmap."""
    progress = K / 'PROGRESS.md'
    original = progress.read_text()
    faults = [
        (progress, original.replace('- [x] **P1-C03**', '- [ ] **P1-C03**').encode(), 'lost approved closure'),
        (progress, original.replace('- [ ] **P1-C02**', '- [x] **P1-C02**').encode(), 'another criterion closure'),
        (progress, original.replace('- [ ] **P1-ACCEPT**', '- [x] **P1-ACCEPT**').encode(), 'full P1 phase acceptance'),
    ]
    status = K / 'execution/status.json'
    for label, field, value in [
        ('false source', 'sourceCommit', '0' * 40),
        ('full requirements acceptance', 'requirementsAccepted', 92),
        ('extra closure', 'otherCriterionClosures', ['P1-C02']),
    ]:
        data = json.loads(status.read_text())
        data['p1C03Closure'][field] = value
        faults.append((status, json.dumps(data).encode(), label))
    data = json.loads(status.read_text())
    data['roadmapMaintenance']['planningCriteria']['checked'] = 16
    faults.append((status, json.dumps(data).encode(), 'false roadmap count'))
    data = json.loads(status.read_text())
    data['p1C03Closure']['ci']['conclusion'] = 'failure'
    faults.append((status, json.dumps(data).encode(), 'failed prerequisite CI'))
    read_bytes, read_text = Path.read_bytes, Path.read_text
    for path, replacement, label in faults:
        with patch.object(Path, 'read_bytes', lambda p, *a, **kw: replacement if p == path else read_bytes(p, *a, **kw)), patch.object(Path, 'read_text', lambda p, *a, **kw: replacement.decode() if p == path else read_text(p, *a, **kw)), redirect_stdout(io.StringIO()):
            result = validator['inert_ci_main']()
        assert result != 0, 'P1-C03 validator accepted ' + label
        print('PASS P1-C03 rejected', label)
    print('PASS P1-C03:8 in-memory negative controls;no stored acceptance mutations')

def test_p2_c06_closure_controls(validator):
    """Only the exact approved criterion closure may advance the roadmap."""
    progress = K / 'PROGRESS.md'
    original = progress.read_text()
    faults = [
        (progress, original.replace('- [x] **P2-C06**', '- [ ] **P2-C06**').encode(), 'lost approved closure'),
        (progress, original.replace('- [ ] **P1-C02**', '- [x] **P1-C02**').encode(), 'another criterion closure'),
        (progress, original.replace('- [ ] **P2-ACCEPT**', '- [x] **P2-ACCEPT**').encode(), 'full P2 phase acceptance'),
    ]
    status = K / 'execution/status.json'
    for label, field, value in [
        ('false source', 'sourceCommit', '0' * 40),
        ('full requirements acceptance', 'requirementsAccepted', 92),
        ('extra closure', 'otherCriterionClosures', ['P1-C02']),
    ]:
        data = json.loads(status.read_text())
        data['p2C06Closure'][field] = value
        faults.append((status, json.dumps(data).encode(), label))
    data = json.loads(status.read_text())
    data['roadmapMaintenance']['planningCriteria']['checked'] = 16
    faults.append((status, json.dumps(data).encode(), 'false roadmap count'))
    data = json.loads(status.read_text())
    data['p2C06Closure']['ci']['conclusion'] = 'failure'
    faults.append((status, json.dumps(data).encode(), 'failed prerequisite CI'))
    read_bytes, read_text = Path.read_bytes, Path.read_text
    for path, replacement, label in faults:
        with patch.object(Path, 'read_bytes', lambda p, *a, **kw: replacement if p == path else read_bytes(p, *a, **kw)), patch.object(Path, 'read_text', lambda p, *a, **kw: replacement.decode() if p == path else read_text(p, *a, **kw)), redirect_stdout(io.StringIO()):
            result = validator['inert_ci_main']()
        assert result != 0, 'P2-C06 validator accepted ' + label
        print('PASS P2-C06 rejected', label)
    print('PASS P2-C06:8 in-memory negative controls;no stored acceptance mutations')


def test_booking_confirmation_controls(validator):
    """Reject authority, audit, metric and acceptance expansion for the bounded slice."""
    faults=[]
    status=K/'execution/status.json'
    for key in ['automaticGrants','bootstrap','fullRequirementAccepted']:
        data=json.loads(status.read_text());data['bookingConfirmationStatisticsImplementation'][key]=True
        faults.append((status,json.dumps(data).encode(),key))
    decision=K/'decisions/cinenest-open-decisions.json'
    for key,value in [('cohort','createdAt'),('cancellationStatistics','0%')]:
        data=json.loads(decision.read_text());row=next(x for x in data['decisions'] if x['id']=='OD-16');row['boundedBookingConfirmationReportingApproval'][key]=value
        faults.append((decision,json.dumps(data).encode(),key))
    progress=K/'PROGRESS.md';faults.append((progress,progress.read_text().replace('- [ ] **P9-C02**','- [x] **P9-C02**').encode(),'full P9-C02 acceptance'))
    migration=ROOT/'backend/src/main/resources/db/migration/V22__booking_confirmation_report_read_audit.sql';faults.append((migration,migration.read_bytes()+b'\nALTER TABLE cinenest_booking.booking ADD COLUMN unauthorized INT;\n','business table change'))
    historical=ROOT/'backend/src/main/resources/db/migration/V21__maintenance_impact_read_audit.sql';faults.append((historical,historical.read_bytes()+b'\nUNAPPROVED\n','historical migration mutation'))
    rb,rt=Path.read_bytes,Path.read_text
    for path,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Synthetic confirmation accepted '+label
        print('PASS confirmation reporting rejected',label)
    print('PASS confirmation reporting:8 in-memory negative controls')



def test_product_documentation_controls():
    module=runpy.run_path(str(K/'validate_design_kb.py'))
    path=K/'product/FEATURE_CATALOG.md';original=path.read_text()
    for label,replacement in [('duplicate ID',original.replace('## DOC-F002 —','## DOC-F001 —')),('invalid status',original.replace('| Implementation status | PARTIAL |','| Implementation status | LIVE |',1)),('invalid requirement',original.replace('/ CN-C02 / T-CN-C02','/ CN-FAKE / T-CN-C02',1)),('missing synthetic warning',original.replace('production NOT AUTHORIZED','production authorized')),('broken source',original.replace('session-panel.tsx','missing-source.tsx'))]:
        rt=Path.read_text;errors=[]
        with patch.object(Path,'read_text',lambda p,*a,**kw:replacement if p==path else rt(p,*a,**kw)):
            module['validate_product_documentation'](lambda ok,message:errors.append(message) if not ok else None)
        assert errors,'Product documentation accepted '+label
        print('PASS product documentation rejected',label)


def test_popular_session_starts_controls(validator):
    faults=[]
    status=K/'execution/status.json'
    data=json.loads(status.read_text());data['popularSessionStartsImplementation']['automaticGrants']=True;faults.append((status,json.dumps(data).encode(),'automatic grants'))
    decision=K/'decisions/cinenest-open-decisions.json';data=json.loads(decision.read_text());next(x for x in data['decisions'] if x['id']=='OD-16')['boundedPopularSessionStartsApproval']['cohort']='confirmed_at';faults.append((decision,json.dumps(data).encode(),'confirmation-time substitution'))
    migration=ROOT/'backend/src/main/resources/db/migration/V23__popular_session_starts_read_audit.sql';faults.append((migration,migration.read_bytes()+b'\nALTER TABLE cinenest_booking.booking ADD COLUMN unsafe INT;\n','non-audit migration'))
    progress=K/'PROGRESS.md';faults.append((progress,progress.read_text().replace('- [ ] **P9-C02**','- [x] **P9-C02**').encode(),'automatic criterion acceptance'))
    requirement=K/'execution/requirements.json';data=json.loads(requirement.read_text());next(x for x in data['requirements'] if x['id']=='CN-M-POPULAR-TIME-SLOTS')['dependencies']=[];faults.append((requirement,json.dumps(data).encode(),'dependency deletion'))
    rb,rt=Path.read_bytes,Path.read_text
    for path,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Popular session starts accepted '+label
        print('PASS popular session starts rejected',label)

def test_synthetic_cancellation_controls(validator):
    faults=[]
    status=K/'execution/status.json'
    for key,value in [('automaticGrants',True),('bootstrap',True),('fullRequirementAccepted',True),('dependenciesChanged',True)]:
        data=json.loads(status.read_text());data['syntheticCancellationImplementation'][key]=value;faults.append((status,json.dumps(data).encode(),key))
    decision=K/'decisions/cinenest-open-decisions.json';data=json.loads(decision.read_text());next(x for x in data['decisions'] if x['id']=='OD-07')['boundedSyntheticCancellationApproval']['refundPercent']=50;faults.append((decision,json.dumps(data).encode(),'unapproved principal deduction'))
    migration=ROOT/'backend/src/main/resources/db/migration/V24__synthetic_booking_cancellation.sql';faults.append((migration,migration.read_bytes()+b"\nDELETE FROM cinenest_payments.payment_allocation;\n",'allocated principal deletion'))
    progress=K/'PROGRESS.md';faults.append((progress,progress.read_text().replace('- [ ] **P6-C04**','- [x] **P6-C04**').encode(),'false full cancellation acceptance'))
    rb,rt=Path.read_bytes,Path.read_text
    for path,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Synthetic cancellation accepted '+label
        print('PASS synthetic cancellation rejected',label)

def test_cancellation_frontend_controls(validator):
    faults=[]
    path=K/'execution/status.json'
    for key,value in [('automaticGrants',True),('fullRequirementAccepted',True),('businessRulesChanged',True),('migrationsChanged',True),('permission','booking.synthetic.read')]:
        data=json.loads(path.read_text());data['syntheticCancellationFrontendImplementation'][key]=value;faults.append((path,json.dumps(data).encode(),key))
    proxy=ROOT/'frontend/lib/booking-cancellation-backend.ts';faults.append((proxy,proxy.read_text().replace('x-csrf-token','x-ignored-token').encode(),'CSRF forwarding change'))
    component=ROOT/'frontend/components/booking-cancellation.tsx';original=component.read_text();changed=original.replace('[confirmed, setConfirmed] = useState(false)','[confirmed, setConfirmed] = useState(true)');assert changed!=original;faults.append((component,changed.encode(),'prechecked cancellation consent'))
    progress=K/'PROGRESS.md';faults.append((progress,progress.read_text().replace('- [ ] **P6-C01**','- [x] **P6-C01**').encode(),'automatic P6 acceptance'))
    rb,rt=Path.read_bytes,Path.read_text
    for target,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Cancellation frontend accepted '+label
        print('PASS cancellation frontend rejected',label)

if __name__ == '__main__': main()
