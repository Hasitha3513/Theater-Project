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
    test_extension_preview_controls(validator)
    test_extension_reservation_controls(validator)
    test_ci_empty_diff_controls(validator)
    test_extension_frontend_controls(validator)
    test_extension_recovery_controls(validator)
    test_knowledge_reading_controls(validator)
    test_reservation_frontend_controls(validator)
    test_knowledge_traceability_controls(validator)
    test_extension_reservation_e2e_controls(validator)
    test_release1_controls(validator)
    test_release1_day2_controls(validator)
    test_r1_recovery_controls(validator)
    test_week1_controls(validator)
    test_roadmap12_controls(validator)
    test_bank_instruction_controls(validator)


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

def test_extension_preview_controls(validator):
    path=K/'execution/status.json';faults=[]
    for key,value in [('inventoryReserved',True),('fundingCreated',True),('purchasedRevisionChanged',True),('migrationsChanged',True),('staffAuthorityAdded',True),('fullRequirementAccepted',True),('pricingBasis','LATEST_PUBLICATION'),('customerOnly',False)]:
        data=json.loads(path.read_text());data['extensionPreviewImplementation'][key]=value;faults.append((path,json.dumps(data).encode(),key))
    rb,rt=Path.read_bytes,Path.read_text
    for target,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Extension preview accepted '+label
        print('PASS extension preview rejected',label)

def test_extension_reservation_controls(validator):
    path=K/'execution/status.json';faults=[]
    for key,value in [('financialMutation',True),('purchasedRevisionChanged',True),('originalConfirmedBlockChanged',True),('staffAuthorityAdded',True),('fullRequirementAccepted',True),('customerOnly',False),('tailOnly',False),('lifetimeSeconds',1200)]:
        data=json.loads(path.read_text());data['extensionReservationImplementation'][key]=value;faults.append((path,json.dumps(data).encode(),key))
    for key,value in [('maximumAttempts',6),('windowSeconds',120),('durable',False),('verifiedCommittedReplayFree',False)]:
        data=json.loads(path.read_text());data['extensionReservationImplementation']['admission'][key]=value;faults.append((path,json.dumps(data).encode(),'admission/'+key))
    rb,rt=Path.read_bytes,Path.read_text
    for target,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)),redirect_stdout(io.StringIO()):result=validator['inert_ci_main']()
        assert result!=0,'Extension reservation accepted '+label
        print('PASS extension reservation rejected',label)


def test_ci_empty_diff_controls(validator):
    """Parser, strict successor and clean-checkout controls; no saved mutations."""
    import subprocess
    parse = validator['parse_git_paths']
    cases = [
        ('empty diff', '', False, set()),
        ('empty stdout bytes', b'', False, set()),
        ('blank lines', '\n\n', False, set()),
        ('whitespace-only lines', ' \n\t\n', False, set()),
        ('trailing newline', 'knowledge/PROGRESS.md\n', False, {'knowledge/PROGRESS.md'}),
        ('one valid path', 'knowledge/PROJECT.md', False, {'knowledge/PROJECT.md'}),
        ('multiple paths', 'knowledge/PROJECT.md\nknowledge/PROGRESS.md\n', False, {'knowledge/PROJECT.md','knowledge/PROGRESS.md'}),
        ('legitimate spaces preserved', ' path with spaces.md \n', False, {' path with spaces.md '}),
        ('NUL separators', b'knowledge/PROJECT.md\0\0', True, {'knowledge/PROJECT.md'}),
        ('empty NUL diff', '\0\0', True, set()),
    ]
    for label,value,nul,expected in cases:
        assert parse(value,nul=nul)==expected,label
        print('PASS empty-diff parser',label)
    assert not parse('backend/unauthorized.java\n') <= {'knowledge/PROJECT.md'}
    assert not parse('knowledge/execution/evidence/checks.json\n') <= {'knowledge/PROJECT.md'}
    print('PASS unauthorized and protected path classification remains negative')
    errors = validator['bank_instruction_successor_errors']()
    assert errors==[], errors
    print('PASS legitimate exact successor documentation admission')
    rb,rt,out = Path.read_bytes,Path.read_text,subprocess.check_output
    for rel in ['knowledge/PROJECT.md','knowledge/execution/requirements.json','knowledge/execution/status.json','scripts/test-governance-controls.py','knowledge/execution/evidence/checks.json']:
        target=ROOT/rel;replacement=rb(target)+b'\nUNAUTHORIZED_PAYLOAD\n'
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),rel
        print('PASS rejected successor changed-payload/protected-history',rel)
    def unknown_path(argv,*a,**kw):
        value=out(argv,*a,**kw)
        if isinstance(argv,list) and 'diff' in argv and '--name-only' in argv:
            sep='\0' if '-z' in argv else '\n';text=value if isinstance(value,str) else value.decode()
            text += sep+'backend/unauthorized.java'+sep
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(subprocess,'check_output',unknown_path):
        assert validator['bank_instruction_successor_errors'](),'Unauthorized source path accepted'
    print('PASS rejected unauthorized actual changed-path collection')
    # Model only the unrelated worktree file as its committed CI bytes. All task
    # sources, hashes, real changed paths and unauthorized-file guards remain real.
    target=ROOT/'frontend/next-env.d.ts';before=rb(target)
    committed=out(['git','-C',str(ROOT),'show','HEAD:frontend/next-env.d.ts'])
    def clean_output(argv,*a,**kw):
        value=out(argv,*a,**kw)
        if isinstance(argv,list) and 'diff' in argv and '--name-only' in argv:
            sep='\0' if '-z' in argv else '\n';text=value if isinstance(value,str) else value.decode()
            paths=[p for p in text.split(sep) if p and p!='frontend/next-env.d.ts']
            text=sep.join(paths)+(sep if paths else '')
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',lambda p,*a,**kw:committed if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:committed.decode() if p==target else rt(p,*a,**kw)),patch.object(subprocess,'check_output',clean_output),redirect_stdout(io.StringIO()):
        result=validator['inert_ci_main']()
    assert rb(target)==before,'Unrelated next-env modified by projection'
    assert result==0,'Actual full historical validator chain failed clean-checkout projection'
    print('PASS full clean-checkout projection;all historical controls;unrelated bytes preserved')

def test_extension_frontend_controls(validator):
    path=K/'execution/status.json';rb,rt=Path.read_bytes,Path.read_text
    for key in ['inventoryReserved','fundingCreated','financialMutation','purchasedRevisionChanged','accessChanged','grantsAssigned','fullRequirementAccepted','backendChanged']:
        value=json.loads(rt(path));value['extensionPreviewFrontend'][key]=True;replacement=json.dumps(value).encode()
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),key
        print('PASS extension frontend rejected',key)

def test_extension_recovery_controls(validator):
    path=K/'execution/status.json';rb,rt=Path.read_bytes,Path.read_text
    for key in ['inventoryReserved','fundingCreated','financialMutation','purchasedRevisionChanged','accessChanged','grantsAssigned','fullRequirementAccepted','frontendImplemented','missingReceiptMeansFailed']:
        value=json.loads(rt(path));value['extensionReservationRecoveryRead'][key]=True;replacement=json.dumps(value).encode()
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),key
        print('PASS extension recovery rejected',key)

def test_knowledge_reading_controls(validator):
    agents=ROOT/'AGENTS.md';original=agents.read_text()
    assert original.startswith(__import__('subprocess').check_output(['git','-C',str(ROOT),'show',validator['KNOWLEDGE_READING_BASELINE']+':AGENTS.md'],text=True))
    for phrase in ['every file under `knowledge/`','SHA-256','verified full-reading ledger only','PDF and DOCX','PNG diagrams','FORMALLY ACCEPTED','successor-admission mechanism']:
        assert phrase in original,phrase
    print('PASS knowledge-reading rules preserve original and require complete coverage, verified reuse and authority')
    faults=[(agents,original.replace('every file under `knowledge/`','selected files').encode(),'weakened discovery'),(agents,original.replace('verified full-reading ledger only','any summary').encode(),'unverified reuse'),(agents,original.replace('FORMALLY ACCEPTED','ACCEPTED').encode(),'acceptance distinction')]
    path=K/'execution/status.json'
    for key in ['applicationChanged','migrationsChanged','businessPolicyChanged','decisionsApproved','acceptanceChanged','historicalEvidenceChanged','extensionFundingStarted','reusableFullReadingLedgerClaimed']:
        value=json.loads(path.read_text());value['agentsKnowledgeReading'][key]=True;faults.append((path,json.dumps(value).encode(),key))
    rb,rt=Path.read_bytes,Path.read_text
    for target,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),label
        print('PASS knowledge-reading rejected',label)

def test_reservation_frontend_controls(validator):
    assert not validator['bank_instruction_successor_errors']()
    path=K/'execution/status.json';original=json.loads(path.read_text())
    faults=[]
    for key in ['backendChanged','migrationsChanged','financialMutation','fundingCreated','purchasedRevisionChanged','accessChanged','grantsAssigned','fullRequirementAccepted','automaticCreation','automaticRenewal','missingReceiptMeansFailed','historicalSyntheticCampaignRerun']:
        value=json.loads(path.read_text());value['extensionReservationFrontend'][key]=True;faults.append((path,json.dumps(value).encode(),key))
    for key,value in [('complete',False),('reviewedFiles',115),('unresolvedCoverageGaps',['missing'])]:
        data=json.loads(path.read_text());data['extensionReservationFrontend']['knowledgeReadingLedger'][key]=value;faults.append((path,json.dumps(data).encode(),'reading '+key))
    target=ROOT/'frontend/lib/extension-reservation.ts';faults.append((target,target.read_bytes()+b'\n// unauthorized payload\n','changed application payload'))
    target=ROOT/'backend/pom.xml'
    faults.append((target,target.read_bytes()+b'\n','unrelated backend change'))
    rb,rt=Path.read_bytes,Path.read_text
    for target,replacement,label in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),label
        print('PASS reservation frontend rejected',label)
    print('PASS reservation frontend exact successor; historical positive/negative controls retained')

def test_knowledge_traceability_controls(validator):
    import copy
    matrix=json.loads((K/'execution/requirements.json').read_text())['mvpKnowledgeTraceability']
    assert validator['traceability_matrix_errors'](matrix)==[]
    print('PASS traceability positive92requirements74criteria37diagrams11modules')
    faults=[]
    def fault(label,mutate):
        value=copy.deepcopy(matrix);mutate(value);faults.append((label,value))
    fault('missing requirement',lambda m:m['requirements'].pop())
    fault('duplicate requirement',lambda m:m['requirements'].append(m['requirements'][0]))
    fault('formal acceptance',lambda m:m['requirements'][0].update(acceptanceStatus='ACCEPTED'))
    fault('criterion checkbox',lambda m:m['criterionIndex'][0].update(checked=False))
    fault('unknown test ID',lambda m:m['requirements'][0].update(testIds=['T-NOT-FOUND']))
    fault('test plan invented PASS',lambda m:m['requirements'][1].update(formalTestPlanStatuses={m['requirements'][1]['testIds'][0]:'PASS'}))
    fault('unknown workstream',lambda m:m['requirements'][0].update(workstreamIds=['W12']))
    fault('unknown module',lambda m:m['moduleIndex'][0].update(id='redis'))
    fault('omitted diagram',lambda m:m['diagramIndex'].pop())
    fault('diagram approval',lambda m:m['diagramIndex'][0].update(authority='APPROVED_IMPLEMENTED'))
    fault('diagram hash',lambda m:m['diagramIndex'][0].update(sha256='0'*64))
    fault('missing diagram family',lambda m:m['requirements'][0]['diagrams'].pop('ER'))
    fault('inapplicable without reason',lambda m:m['requirements'][0]['diagrams']['ER'].update(reason=''))
    fault('source path traversal',lambda m:m['implementationGroups']['identity']['source'][0].update(path='../secret'))
    fault('invented API',lambda m:m['implementationGroups']['identity']['apiContracts'][0].update(path='/api/automatic-grants'))
    fault('invented table',lambda m:m['implementationGroups']['identity']['database'][0].update(table='cinenest_identity.automatic_grants'))
    fault('next task unauthorized',lambda m:m['nextImplementationTask'].update(authority='GRANTED'))
    fault('global policy closed',lambda m:m.update(openDecisionIds=[]))
    fault('context as metric',lambda m:next(r for r in m['requirements'] if r['requirementId']=='BN-M03').update(apiStatus='REFERENCE_FOUND'))
    for label,value in faults:
        assert validator['traceability_matrix_errors'](value),label
        print('PASS traceability rejected',label)
    path=K/'execution/status.json';rb,rt=Path.read_bytes,Path.read_text
    for key in ['applicationChanged','migrationsChanged','businessPolicyChanged','decisionsApproved','acceptanceChanged','historicalEvidenceChanged','extensionFundingStarted','fullCampaignRerun']:
        value=json.loads(path.read_text());value['knowledgeTraceabilityReconciliation'][key]=True;replacement=json.dumps(value).encode()
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==path else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),key
        print('PASS traceability rejected scope',key)


def test_extension_reservation_e2e_controls(validator):
    assert not validator['bank_instruction_successor_errors']()
    print('PASS extension E2E exact successor positive')
    path=K/'execution/status.json';rb,rt=Path.read_bytes,Path.read_text
    for key in ['fundingCreated','allocationChanged','purchasedRevisionChanged','accessChanged','grantsAssigned','acceptanceChanged','migrationsChanged']:
        data=json.loads(rt(path));data['extensionReservationE2E'][key]=True;payload=json.dumps(data).encode()
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:payload if p==path else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:payload.decode() if p==path else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),key
        print('PASS extension E2E rejected',key)
    for name in ['frontend/lib/extension-preview-backend.ts','frontend/lib/extension-reservation-backend.ts','backend/src/main/resources/db/migration/V25__synthetic_extension_reservations.sql']:
        target=ROOT/name;payload=rb(target)+b'\nUNAUTHORIZED\n'
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:payload if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:payload.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),name
        print('PASS extension E2E rejected changed source/migration',name)

def test_release1_controls(validator):
    import copy
    assert not validator['bank_instruction_successor_errors']()
    plan=validator['r1_day2_planning_projection'](validator['r1_recovery_planning_projection'](validator['week1_planning_projection'](validator['bank_instruction_planning_projection'](json.loads((K/'execution/change-sets.json').read_text())['release1DeliverySlice']))))
    check=validator['release1_plan_errors']
    assert check(plan)==[]
    print('PASS Release1 exact planning successor,92IDs21days and blocked real-world gates')
    faults=[]
    def fault(name,mutate):
        value=copy.deepcopy(plan);mutate(value);faults.append((name,value))
    fault('missing requirement',lambda p:p['requirementMapping'].pop())
    fault('duplicate requirement',lambda p:p['requirementMapping'].append(p['requirementMapping'][0]))
    fault('unknown requirement',lambda p:p['schedule'][1]['requirementIds'].append('CN-UNKNOWN'))
    fault('changed criterion',lambda p:p['criterionMapping'][0].update(criterionId='P11-C01'))
    fault('missing day',lambda p:p['schedule'].pop())
    fault('invalid dates',lambda p:p['schedule'][2].update(targetDate='2026-10-25'))
    fault('unknown dependency',lambda p:p['schedule'][2]['dependencies'].append('CN-UNKNOWN'))
    fault('automatic implementation',lambda p:p['schedule'][1].update(status='READY'))
    fault('launch approved',lambda p:p.update(launchStatus='AUTHORIZED'))
    fault('gate waived',lambda p:p['approvalGates'][0].update(status='APPROVED'))
    fault('false physical verification',lambda p:p['acceptanceUnchanged'].update(physicalQualified=True))
    fault('empty acceptance matrix',lambda p:p.update(acceptanceMatrix=[]))
    fault('future test invented PASS',lambda p:p['acceptanceMatrix'][0].update(releaseTestResult='PASS'))
    fault('formal acceptance',lambda p:p['acceptanceUnchanged'].update(formalRequirementsAccepted=92))
    for name,value in faults:
        assert check(value),name
        print('PASS Release1 rejected',name)
    rb,rt=Path.read_bytes,Path.read_text
    faults=[]
    for name in ['backend/src/main/java/com/cinenest/access/internal/AccessService.java','backend/src/main/resources/db/migration/V25__synthetic_extension_reservations.sql','knowledge/decisions/cinenest-open-decisions.json','knowledge/execution/requirements.json']:
        target=ROOT/name;faults.append((target,target.read_bytes()+b'\n','protected '+name))
    for target,replacement,name in faults:
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),name
        print('PASS Release1 rejected',name)

def test_release1_day2_controls(validator):
    import copy
    assert not validator['bank_instruction_successor_errors']()
    value=json.loads((K/'execution/status.json').read_text())['release1BookingJourney']
    check=validator['r1_day2_evidence_errors'];assert check(value)==[]
    print('PASS Release1 Day2 exact real-stack37PASS successor and pending-state proof')
    faults=[]
    def fault(label,mutate):
        data=copy.deepcopy(value);mutate(data);faults.append((label,data))
    fault('missing scenario',lambda d:d['scenarios'].pop())
    fault('duplicate scenario',lambda d:d['scenarios'].append(d['scenarios'][0]))
    fault('invented campaign count',lambda d:d.update(counts={'PASS':38,'FAIL':0,'NOT_RUN':0}))
    fault('pending booking created',lambda d:d['day2DatabaseEvidence'][0].update(bookings=1))
    fault('duplicate pending request',lambda d:d['day2DatabaseEvidence'][0].update(requests=2))
    fault('missing exclusion verification',lambda d:d['day2DatabaseEvidence'][0].pop('databaseExclusionVerified'))
    fault('inventory overlap',lambda d:d['day2DatabaseEvidence'][0].update(overlapPairs=1))
    fault('race duplicate receipt',lambda d:d['day2DatabaseEvidence'][3].update(createReceipts=2))
    fault('race fixture not released',lambda d:d['day2DatabaseEvidence'][4].update(blocking=True))
    fault('fabricated upstream success',lambda d:d['faultCheckpoint'].update(upstreamStatus=503))
    fault('automatic extra creation',lambda d:d['faultCheckpoint'].update(attempts=4))
    fault('pending marked funded',lambda d:d['pendingState'].update(funding='SIMULATED_ALLOCATED'))
    fault('cleanup missing',lambda d:d.update(cleanupVerified=False))
    for key in ['applicationChanged','migrationsChanged','businessPolicyChanged','operationalGrantsAssigned','realFundsExecuted','deviceCommandsExecuted','acceptanceChanged','historicalEvidenceChanged','productionAuthorized','extensionFundingStarted']:
        fault(key,lambda d,k=key:d.update({k:True}))
    for label,data in faults:
        assert check(data),label
        print('PASS Release1 Day2 rejected',label)
    rb,rt=Path.read_bytes,Path.read_text
    for name in ['backend/src/main/java/com/cinenest/payments/internal/BankService.java','frontend/lib/bank-backend.ts','backend/src/main/resources/db/migration/V25__synthetic_extension_reservations.sql','knowledge/decisions/cinenest-open-decisions.json']:
        target=ROOT/name;replacement=rb(target)+b'\nUNAUTHORIZED\n'
        with patch.object(Path,'read_bytes',lambda p,*a,**kw:replacement if p==target else rb(p,*a,**kw)),patch.object(Path,'read_text',lambda p,*a,**kw:replacement.decode() if p==target else rt(p,*a,**kw)):
            assert validator['bank_instruction_successor_errors'](),name
        print('PASS Release1 Day2 rejected protected',name)

def test_r1_recovery_controls(validator):
    import copy
    assert validator['bank_instruction_successor_errors']()==[]
    receipt=json.loads((K/'execution/status.json').read_text())['release1CustomerRecovery']
    check=validator['r1_recovery_evidence_errors'];assert check(receipt)==[]
    print('PASS exact real-stack customer recovery40PASS successor')
    mutations=[]
    def fault(name,mutate):
        value=copy.deepcopy(receipt);mutate(value);mutations.append((name,value))
    fault('invented count',lambda x:x.update(counts={'PASS':41,'FAIL':0,'NOT_RUN':0}))
    fault('missing scenario',lambda x:x['scenarios'].pop())
    fault('duplicate scenario',lambda x:x['scenarios'].append(x['scenarios'][0]))
    fault('history duplicate request',lambda x:x['revisit']['database'].update(requests=2))
    fault('refresh duplicate receipt',lambda x:x['refresh']['database'].update(commandReceipts=2))
    fault('recovery automatic POST',lambda x:x['refresh'].update(writeRequests=1))
    fault('fabricated history discovery',lambda x:x['revisit'].update(discoveredThroughOwnedHistory=False))
    fault('expiry false success',lambda x:x['session'].update(requestStatus=200))
    fault('logout false success',lambda x:x['session'].update(postLogoutReadStatus=200))
    fault('stale summary retained',lambda x:x['session'].update(protectedSummaryCleared=False))
    fault('BFCache qualification invented',lambda x:x['refresh'].update(physicalBFCacheQualification=True))
    fault('cleanup omitted',lambda x:x.update(cleanupVerified=False))
    for key in ['credits','verifications','allocations','bookings','credentials','provisioningWork']:
        fault('premature '+key,lambda x,k=key:x['revisit']['database'].update({k:1}))
    for key in ['applicationChanged','migrationsChanged','policyChanged','acceptanceChanged','grantsAssigned','realBanking','physicalCommands','extensionFunding','productionAuthorized']:
        fault('authority '+key,lambda x,k=key:x.update({k:True}))
    for name,value in mutations:
        assert check(value),name
        print('PASS recovery rejected',name)
    rb,rt=Path.read_bytes,Path.read_text
    for name in ['frontend/components/bank-handoff.tsx','backend/src/main/java/com/cinenest/payments/internal/BankService.java','backend/src/main/resources/db/migration/V25__synthetic_extension_reservations.sql','knowledge/decisions/cinenest-open-decisions.json']:
        path=ROOT/name;changed=rb(path)+b'\nUNAUTHORIZED\n'
        with patch.object(Path,'read_bytes',lambda p,*a,**k:changed if p==path else rb(p,*a,**k)),patch.object(Path,'read_text',lambda p,*a,**k:changed.decode() if p==path else rt(p,*a,**k)):
            assert validator['bank_instruction_successor_errors'](),name
        print('PASS recovery rejected protected',name)

def test_week1_controls(validator):
    import copy
    assert validator['bank_instruction_successor_errors']()==[]
    value=json.loads((K/'execution/status.json').read_text())['release1Week1Gate'];check=validator['week1_evidence_errors'];assert check(value)==[]
    print('PASS bounded Week1 exact-source gate evidence')
    mutations=[]
    def fault(label,mutate):
        d=copy.deepcopy(value);mutate(d);mutations.append((label,d))
    fault('missing criterion',lambda d:d['matrix'].pop())
    fault('false criterion result',lambda d:d['matrix'][0].update(result='NOT_RUN'))
    fault('unknown requirement',lambda d:d['matrix'][0].update(requirementIds=['CN-UNKNOWN']))
    fault('invented test',lambda d:d['matrix'][0].update(scenarioIds=['FAKE']))
    fault('historical sum77',lambda d:d['evidenceReuse'].update(currentCampaignCount=77))
    fault('invented new execution',lambda d:d['evidenceReuse'].update(newCampaignsExecuted=1))
    fault('software defect hidden',lambda d:d['blockers'].update(software=['FAIL']))
    for key in ['applicationChanged','migrationChanged','formalAcceptanceChanged','launchAuthorized','realBankingQualified','physicalAccessQualified']:
        fault(key,lambda d,k=key:d.update({k:True}))
    for label,d in mutations:
        assert check(d),label
        print('PASS Week1 rejected',label)

def test_roadmap12_controls(validator):
    import copy
    check=validator['roadmap12_planning_errors']
    plan=json.loads((K/'execution/change-sets.json').read_text())['proposed12WeekPlanning']
    assert check(plan)==[]
    print('PASS proposed twelve-week planning projection')
    changes=[('missing week',lambda d:d['weeks'].pop()),('week implementation approved',lambda d:d['weeks'][3].update(status='APPROVED')),('new scope',lambda d:d.update(requirementsCount=93)),('criteria reduction',lambda d:d.update(criteriaCount=73)),('workstream loss',lambda d:d.update(workstreamsCount=10)),('false closure',lambda d:d.update(checkedCriteria=16)),('formal acceptance',lambda d:d.update(acceptedRequirements=1)),('scope reclassification',lambda d:d['release1Classification'].update(retainedPostRelease=25)),('implementation authority',lambda d:d.update(implementationAuthorized=True)),('launch authority',lambda d:d.update(launchAuthorized=True)),('release1 mutation',lambda d:d.update(release1PlanChanged=True)),('target drift',lambda d:d.update(release1Target='2026-11-15'))]
    for label,mutate in changes:
        d=copy.deepcopy(plan);mutate(d);assert check(d),label
        print('PASS roadmap12 rejected',label)

def test_bank_instruction_controls(validator):
    import copy
    check=validator['bank_instruction_receipt_errors'];value=json.loads((K/'execution/status.json').read_text())['bankInstructionsStagingImplementation'];assert check(value)==[]
    print('PASS bounded bank instruction approval/evidence')
    for key in ['defaultEnabled','realAccounts','runtimeGrantsAssigned','publicationCommands','financialMutations','commercialAccepted','formalAcceptanceChanged','syntheticCampaignRerun']:
        d=copy.deepcopy(value);d[key]=True;assert check(d),key;print('PASS bank instructions rejected',key)
    d=copy.deepcopy(value);d['fixtureOnly']=False;assert check(d);print('PASS bank instructions rejected arbitrary resolver')
    d=copy.deepcopy(value);d['tests']['realStackCampaign']='PASS';assert check(d);print('PASS bank instructions rejected fabricated E2E')
    for name in ['backend/src/main/resources/db/migration/V9__synthetic_bank_requests.sql','backend/src/main/resources/db/migration/V10__synthetic_credit_allocation.sql','knowledge/backlog/FUTURE_FEATURES.md']:
        target=ROOT/name;rb,rt=Path.read_bytes,Path.read_text;replacement=rb(target)+b'\n'
        with patch.object(Path,'read_bytes',lambda p,*a,**k:replacement if p==target else rb(p,*a,**k)),patch.object(Path,'read_text',lambda p,*a,**k:replacement.decode() if p==target else rt(p,*a,**k)):
            assert validator['bank_instruction_successor_errors'](),name
        print('PASS bank instructions rejected historical drift',name)
    glob=Path.glob;folder=ROOT/'backend/src/main/resources/db/migration'
    with patch.object(Path,'glob',lambda p,*a,**k:iter([*glob(p,*a,**k),folder/'V27__unapproved.sql']) if p==folder else glob(p,*a,**k)):
        assert validator['bank_instruction_successor_errors']()
    print('PASS bank instructions rejected unapproved migration')

if __name__ == '__main__': main()
