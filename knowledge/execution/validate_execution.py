"""Read-only execution/coverage validator. No application, bank or device operations."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
from urllib.parse import unquote

E = Path(__file__).resolve().parent
K = E.parent
R = K.parent


def historical_main():
    errors = []
    def check(ok, message):
        if not ok:
            errors.append(message)
    # Owner-approved IoT readiness-only delta; exact input validation precedes unchanged historical replay.
    iot_base='104d84c2ebea166d974c0e6775ffa976310d246e'
    iot_expected={'execution/change-sets.json': 'c916a1e643c324d9f39e1766ad7e2179a02c73d766bc4111ff97c356a996359b', 'execution/status.json': '8edb6a9070369f6ba0fbdaf9c9748524bddf6ce6325d1129934b4bf18a100685', 'decisions/cinenest-open-decisions.json': 'e0bb99a81a31b2296ea4bd65fddd5e4f661f53e44c32fd9dc9a6d5b2427f1f5e'}
    def iot_historical(path):
        if path in iot_expected:
            return json.loads(subprocess.check_output(['git','-C',str(R),'show',iot_base+':knowledge/'+path],text=True))
        return json.loads((K/path).read_text())
    for path,digest in iot_expected.items():
        current=json.loads((K/path).read_text())
        check(hashlib.sha256(json.dumps(current,sort_keys=True).encode()).hexdigest()==digest,'IoT readiness approval/status/history changed '+path)
    iot_actual=json.loads((E/'change-sets.json').read_text())
    iot_task=next(x for x in iot_actual['changeSets'] if x['id']=='CN-IOT-001')
    check(iot_task['dependencies']==['CN-ACCESS-001'] and not iot_task['authorityAvailable'] and iot_task['readiness']==iot_task['workStatus']=='BLOCKED','IoT readiness cannot authorize implementation or change dependencies')
    check(iot_task['definitionOfDone']=='Unknown never OFF/available; simulator evidence qualified' and iot_task['remainingReadinessGates']==['Separate bounded synthetic CN-IOT-001 implementation authorization'],'IoT DoD or remaining authority gate changed')
    for path in ('execution/requirements.json','execution/test-plan.json','execution/source-ledger.json','sources/cinenest-source-register.json'):
        check(json.loads((K/path).read_text())==json.loads(subprocess.check_output(['git','-C',str(R),'show',iot_base+':knowledge/'+path],text=True)),'IoT altered full coverage/source evidence '+path)
    for decision in json.loads((K/'decisions/cinenest-open-decisions.json').read_text())['decisions']:
        if decision['id'] in ('OD-01','OD-02','OD-13'):
            check(decision['decisionStatus']=='OPEN_DECISION' and decision['boundedIotApproval']==iot_task['boundedIotScopeApproval'],'IoT globally closed decision or lost bounded approval')
    def read_json(name):
        try:
            return iot_historical('execution/'+name)
        except (OSError, ValueError) as exc:
            check(False, f'{name}: {exc}')
            return {}
    controls = [
                'change-sets.json', 'requirements.json', 'test-plan.json', 'source-ledger.json',
                'status.json', 'omissions.json',
                'evidence/source-review.json']
    for name in controls:
        check((E / name).is_file(), f'Missing control {name}')
    check((R / 'AGENTS.md').is_file(), 'No root instructions')
    check((K / 'PROGRESS.md').is_file() and '<a id="execution-charter"></a>' in (K / 'PROGRESS.md').read_text(), 'No consolidated charter')
    entry = (R / 'AGENTS.md').read_text()
    for required in ['every CineNest request', 'PROGRESS.md', 'PROJECT.md', 'ARCHITECTURE.md', 'DESIGN.md',
                     'New knowledge files require explicit owner approval',
                     'READY authorized change set', 'read-only', 'No production']:
        check(required in entry, f'Instruction guard missing {required}')
    check(len(entry.encode()) < 12000, 'Instruction entry point too long')
    # All actual repository Markdown paths; exclude Git internals, URL schemes and headings-only anchors.
    for relative in subprocess.check_output(['git', '-C', str(R), 'ls-files', '--cached', '--others', '--exclude-standard'], text=True).splitlines():
        path = R / relative
        if path.suffix != '.md':
            continue
        if '.git' in path.parts or path.relative_to(R).as_posix() in {'knowledge/' + name for name in json.loads((K / 'manifest.json').read_text()).get('unmanagedWorkspaceAssets', {})}:
            continue
        for target in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            target = target.strip('<>')
            if target.startswith(('http:', 'https:', '#', 'codex:')):
                continue
            check((path.parent / unquote(target.split('#')[0])).is_file(), f'Broken link {path.relative_to(R)}: {target}')
    backlog = read_json('change-sets.json')
    coverage = read_json('requirements.json')
    tests_doc = read_json('test-plan.json')
    ledger = read_json('source-ledger.json')
    state = read_json('status.json')
    if not all([backlog, coverage, tests_doc, ledger, state]):
        print('\n'.join(errors))
        return 1
    # Separately authorized bounded amendment successor. Validate actual inputs before historical replay.
    mi_base='94acd3fe0e434cd85db152ba16a9c1b1f0f65892'
    mi_allowed={'backend/src/main/java/com/cinenest/payments/internal/AmendmentFundingAdapter.java', 'frontend/tests/customer-bookings.test.mjs', 'backend/src/main/java/com/cinenest/pricing/internal/AmendmentPricingAdapter.java', 'frontend/app/api/bookings/[id]/amendments/route.ts', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationService.java', 'backend/src/main/resources/db/migration/V15__synthetic_booking_amendments.sql', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/booking/AmendmentGuards.java', 'frontend/app/api/bookings/[id]/amendments/[amendmentId]/route.ts', 'frontend/components/notifications.tsx', 'backend/src/main/java/com/cinenest/booking/internal/InventoryWorker.java', 'backend/src/main/java/com/cinenest/notifications/NotificationFacts.java', 'backend/src/main/java/com/cinenest/access/internal/AccessService.java', 'backend/src/main/java/com/cinenest/booking/internal/SyntheticBookingAdapter.java', 'backend/src/main/java/com/cinenest/booking/internal/AmendmentService.java', 'backend/src/main/java/com/cinenest/booking/internal/BookingAccessAdapter.java', 'backend/src/main/java/com/cinenest/booking/BookingAccess.java', 'frontend/tests/customer-bookings-browser.mjs', 'backend/src/main/java/com/cinenest/booking/package-info.java', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteService.java', 'frontend/components/booking-amendment.tsx', 'backend/src/main/java/com/cinenest/notifications/internal/JdbcNotificationRepository.java', 'backend/src/main/java/com/cinenest/booking/internal/AmendmentNotificationContext.java', 'frontend/package.json', 'frontend/tests/amendments.test.mjs', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/booking/internal/AmendmentController.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/app/api/bookings/[id]/amendments/[amendmentId]/confirm/route.ts', 'backend/src/main/java/com/cinenest/access/internal/AmendmentAccessAdapter.java', 'frontend/app/api/bookings/[id]/amendments/[amendmentId]/withdrawals/route.ts', 'frontend/components/customer-bookings.tsx'}
    def mi_prior(path):return json.loads(subprocess.check_output(['git','-C',str(R),'show',mi_base+':knowledge/'+path],text=True))
    mi=state.get('amendImplementation',{})
    mi_authority={'source':'Owner inline instruction and attachment bdf9e9b5-02cc-40c7-a5d0-b37a97eecb7e','requestSha256':'4449a1fba53cfc34bd8be7966c7c37d9734a2194e676cdc1b616b6f80aa87df0','scope':'GRANTED_BOUNDED_SYNTHETIC_AMENDMENT','date':'2026-10-18','realOperations':'DISABLED','gateway':'DEFERRED_BLOCKED'}
    check(mi.get('authority')==mi_authority and mi.get('baselineHead')==mi_base,'Amendment implementation authority/baseline invalid')
    check(mi.get('workStatus') in ('IN_PROGRESS','VERIFIED'),'Amendment implementation work status invalid')
    mi_closed=mi.get('workStatus')=='VERIFIED'
    mi_expected=mi_prior('execution/change-sets.json')
    mi_task=next(t for t in mi_expected['changeSets'] if t['id']=='CN-AMEND-001')
    mi_task.update({'affectedFilesModules': 'Booking-owned amendment/revisions/inventory; Pricing/Payments/Access public guards; one typed Notifications event; existing customer detail composition', 'dbApiUiImpact': 'Additive V15; four amendment APIs; current purchased revision/history reads and booking-detail UI', 'rollback': 'Disable new amendment writes; retain V15, pending/confirmed proposals, immutable purchase/financial/access/notification history; compatible code rollback or reviewed forward fix only, never revision-1-only rollback after revision2 exists'})
    mi_task['evidencePaths'].append('PROGRESS.md#amend-implementation')
    mi_task.update(authorityAvailable=True,readiness='CLOSED' if mi_closed else 'READY',workStatus=mi.get('workStatus'),remainingReadinessGates=[],implementationAuthorization=mi_authority)
    check(backlog==mi_expected,'Amendment changed scope/DoD/dependency edges or unrelated task')
    mi_expected_state=mi_prior('execution/status.json')
    mi_expected_state.update(mode='SYNTHETIC_AMENDMENT_IMPLEMENTATION',workStatus=mi.get('workStatus'),implementationAuthorization=mi_authority['scope'],nextAction='Review the next independently eligible task and its own approval gates; no implementation' if mi_closed else 'Complete bounded amendment verification and exact-source CI',evidencePath='PROGRESS.md#amend-implementation',amendImplementation=mi)
    mi_expected_state.update({'productStage': 'BOUNDED_SYNTHETIC_AMENDMENT_REAL_OPERATIONS_DISABLED', 'implementationEvidence': 'CN-AMEND-001 bounded synthetic implementation; actual checks at PROGRESS.md#amend-implementation; full commercial amendment acceptance remains pending', 'blockers': ['Current bounded closure requires completed checks and exact-source CI', 'Downstream only: PayHere deferred/BLOCKED; venue/specialist and wider commercial/privacy/physical gates pending'], 'approvalQueue': ['Commercial/venue/specialist/provider/physical acceptance remains separately gated'], 'verificationScope': 'Bounded synthetic software only; no commercial/provider/physical/customer acceptance', 'publicationState': 'CURRENT_TASK_SOURCE_NOT_YET_PUBLISHED', 'evidenceFreshness': '2026-10-18 bounded amendment local evidence; exact-source CI pending'})
    if mi_closed:
        mi_expected_state.update(publicationEvidence='CN-AMEND-001 source '+mi.get('sourceCommit','')+' pushed to origin/main; exact-source CI39147723761 SUCCESS; final closure receipt publication/CI verified separately',lastObservedHead=mi.get('sourceCommit',''),nextRecommendedChangeSet='CN-IOT-001',nextAction='Review CN-IOT-001 bounded simulator scope and OD-01/02/13 gates; read-only',blockers=['Downstream only: PayHere deferred/BLOCKED; venue/specialist and wider commercial/privacy/physical gates pending'],publicationState='SOURCE_PUBLISHED_EXACT_CI_SUCCESS',evidenceFreshness='2026-10-18 exact amendment source CI verified;32 owned source hashes')
    check(state==mi_expected_state,'Amendment changed unrelated status/history')
    for k,v in {'realOperations':'DISABLED','gateway':'DEFERRED_BLOCKED','fullRequirementAcceptance':'NOT_ACCEPTED','evidencePath':'PROGRESS.md#amend-implementation'}.items():check(mi.get(k)==v,'Amendment safety boundary invalid '+k)
    mi_mapping={'changeSetId':'CN-AMEND-001','scope':'One pre-credential synthetic zero-difference reschedule; no full commercial or physical acceptance','status':mi.get('workStatus'),'evidencePath':'PROGRESS.md#amend-implementation','fullAcceptance':'NOT_ACCEPTED'}
    for path,key,ids in [('execution/requirements.json','requirements',{'CN-C08','CN-A02','PK-AC12','BR-10'}),('execution/test-plan.json','tests',{'T-CN-C08','T-CN-A02','T-PK-AC12','T-BR-10','T-CN-NFR-02'})]:
        doc=iot_historical(path)
        for row in doc[key]:
            receipt=row.pop('boundedAmendmentImplementationEvidence',None)
            check(receipt==(mi_mapping if row['id'] in ids else None),'Amendment bounded mapping invalid '+row['id'])
        check(doc==mi_prior(path),'Amendment altered full requirement/test history '+path)
    for path in ('decisions/cinenest-open-decisions.json','execution/source-ledger.json','sources/cinenest-source-register.json'):
        check(iot_historical(path)==mi_prior(path),'Amendment altered decisions/source evidence '+path)
    package=json.loads((R/'frontend/package.json').read_text());original_package=json.loads(subprocess.check_output(['git','-C',str(R),'show',mi_base+':frontend/package.json'],text=True));original_package['scripts']['test']+=' && node --import ./tests/tsx-loader.mjs tests/amendments.test.mjs'
    check(package==original_package,'Amendment changed dependencies/toolchain or unrelated frontend scripts')
    migration=(R/'backend/src/main/resources/db/migration/V15__synthetic_booking_amendments.sql').read_text()
    check(set(re.findall(r'CREATE TABLE ([a-z_]+[.][a-z_]+)',migration))=={'cinenest_booking.booking_amendment','cinenest_booking.booking_amendment_command','cinenest_booking.booking_amendment_audit'} and 'cinenest_payments.' not in migration,'Amendment V15 changed datastore ownership/scope')
    controller=(R/'backend/src/main/java/com/cinenest/booking/internal/AmendmentController.java').read_text()
    check(set(re.findall(r'@(Get|Post)Mapping[(]"([^"]+)"[)]',controller))=={('Post','/api/bookings/{booking}/amendments'),('Get','/api/bookings/{booking}/amendments/{amendment}'),('Post','/api/bookings/{booking}/amendments/{amendment}/confirm'),('Post','/api/bookings/{booking}/amendments/{amendment}/withdrawals')},'Amendment API boundary invalid')
    check(set(mi.get('ownedFiles',{}))==mi_allowed,'Amendment source allowlist invalid')
    for name,digest in mi.get('ownedFiles',{}).items():check((R/name).is_file() and hashlib.sha256((R/name).read_bytes()).hexdigest()==digest,'Amendment owned source checksum mismatch '+name)
    mi_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',mi_base,'--','backend','frontend'],text=True).splitlines());mi_added=mi_allowed-mi_app
    mi_current=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(mi_current==mi_app|mi_added,'Amendment changed unrelated application inventory')
    mi_delta=set(subprocess.check_output(['git','-C',str(R),'diff','--name-only',mi_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh','knowledge/tokens','knowledge/styles'],text=True).splitlines())
    check(mi_delta-mi_allowed<={'frontend/next-env.d.ts'},'Amendment changed unrelated source/tokens/launchers')
    mi_migrations=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',mi_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}==mi_migrations|{'backend/src/main/resources/db/migration/V15__synthetic_booking_amendments.sql'},'Amendment V15 scope invalid')
    for name in mi_migrations:check((R/name).read_bytes()==subprocess.check_output(['git','-C',str(R),'show',mi_base+':'+name]),'Amendment altered historical migration '+name)
    if mi.get('checks'):
        check(mi.get('evidenceCounts')=={'backendTests': 325, 'bankIntegrationTests': 130, 'amendmentIntegrationCases': 28, 'architectureTests': 14, 'frontendTests': 105, 'customerAmendmentBrowserGroups': 64, 'notificationBrowserGroups': 28, 'accessBrowserGroups': 32, 'refundBrowserGroups': 30, 'bankBrowserGroups': 70, 'adminBrowserGroups': 63, 'launcherTests': 14, 'governanceNegativeControls': 16, 'failures': 0, 'errors': 0, 'skipped': 0},'Amendment executed evidence counts invalid')
        check(mi.get('primaryExecutionValidation')=='PRE_EXISTING_NEXT_ENV_ONLY' and mi.get('isolatedExecutionValidation')=='PASS','Amendment validation isolation boundary invalid')
    if mi_closed:
        check(mi.get('checks')=={'backend':'PASS','frontend':'PASS','browser':'PASS','governance':'PASS','launchers':'PASS'} and mi.get('sourceCI')=='SUCCESS','Amendment closure lacks executed evidence/exact-source CI')
        commit=mi.get('sourceCommit','');check(bool(re.fullmatch('[0-9a-f]{40}',commit)) and str(mi.get('sourceRun','')).startswith('https://github.com/Hasitha3513/Theater-Project/actions/runs/'),'Amendment CI receipt invalid')
        if re.fullmatch('[0-9a-f]{40}',commit):
            for name,digest in mi.get('ownedFiles',{}).items():
                original=subprocess.run(['git','-C',str(R),'show',commit+':'+name],capture_output=True);check(original.returncode==0 and hashlib.sha256(original.stdout).hexdigest()==digest,'Amendment exact-source CI stale '+name)
    if errors:
        print('\n'.join('FAIL '+m for m in errors));return 1
    def mi_original(payload,path):
        if path in ('execution/change-sets.json','execution/status.json','execution/requirements.json','execution/test-plan.json'):return mi_prior(path)
        return payload
    def mi_source(name):
        if name in mi_allowed-mi_added:return subprocess.check_output(['git','-C',str(R),'show',mi_base+':'+name])
        return (R/name).read_bytes()
    backlog=mi_original(backlog,'execution/change-sets.json');state=mi_original(state,'execution/status.json')
    # Explicit owner-approved amendment design/readiness only; no code or migration authority.
    am_base='b07ef7d2dd4a8182853d6fc1532d772eb1e2a98a'
    am_expected={'execution/change-sets.json': 'a20b155f5c39bd1087092e36587855584e6f47a9dcad48a1dc0e5333031e078e', 'execution/status.json': 'c0d4e3893e284837ff7f0c970843e2f1f3477e9397fc8eb6691456c8be99fd21', 'decisions/cinenest-open-decisions.json': '5c45c2f1bb1cdfe23f0a76117dcf06eb6a339f81896d8479c343b718e5f12b5e', 'execution/requirements.json': '0e32ea1f4e26006005cb230a1e72926aaeb54380c8a0e4bc0c95cea23045edd4', 'execution/test-plan.json': '2e59ca63f49ae66e64c736e4125b601b2be4576598cf850307a9e5adb802af38'}
    am_prior={}
    for path,digest in am_expected.items():
        actual=mi_original(iot_historical(path),path)
        check(hashlib.sha256(json.dumps(actual,sort_keys=True).encode()).hexdigest()==digest,'Amendment bounded approval/history changed '+path)
        am_prior[path]=json.loads(subprocess.check_output(['git','-C',str(R),'show',am_base+':knowledge/'+path],text=True))
    am_task=next(t for t in backlog['changeSets'] if t['id']=='CN-AMEND-001')
    check(not am_task['authorityAvailable'] and am_task['readiness']==am_task['workStatus']=='BLOCKED','Amendment recording-only approval cannot authorize implementation')
    check(am_task['dependencies']==['CN-CUSTOMER-001','CN-QUOTE-001','CN-REFUND-001'] and am_task['remainingReadinessGates']==['Separate bounded synthetic CN-AMEND-001 implementation authorization'],'Amendment dependency/authority gate changed')
    check(am_task['blockingDecisionIds']==[] and am_task['downstreamDecisionIds']==['OD-07','OD-20','OD-21','OD-24'],'Amendment bounded/global decision boundary changed')
    check(len(backlog['changeSets'])==28,'Amendment task count changed')
    for row,old in zip(backlog['changeSets'],am_prior['execution/change-sets.json']['changeSets']):
        check(row['dependencies']==old['dependencies'],'Amendment changed dependency edge '+row['id'])
        if row['id']!='CN-AMEND-001':check(row==old,'Amendment changed unrelated task '+row['id'])
        else:
            for key in ('id','objective','inScope','outOfScope','requirementIds','definitionOfDone','requiredApproval','plannedChecks','rollback'):
                check(row[key]==old[key],'Amendment changed registered scope/history '+key)
    for dependency in am_task['dependencies']:
        row=next(t for t in backlog['changeSets'] if t['id']==dependency)
        check(row['readiness']=='CLOSED' and row['workStatus']=='VERIFIED','Amendment prerequisite not verified '+dependency)
    decisions=iot_historical('decisions/cinenest-open-decisions.json')
    for row,old in zip(decisions['decisions'],am_prior['decisions/cinenest-open-decisions.json']['decisions']):
        stripped=json.loads(json.dumps(row));approval=stripped.pop('boundedAmendmentApproval',None)
        check(stripped==old,'Amendment changed global/unrelated decision '+row['id'])
        if row['id'] in ('OD-07','OD-20','OD-21','OD-24'):check(approval==am_task['boundedAmendmentScopeApproval'] and row['decisionStatus']=='OPEN_DECISION','Amendment global decision closure invalid '+row['id'])
        else:check(approval is None,'Amendment approved unrelated decision '+row['id'])
    for path,key,rid in [('execution/requirements.json','requirements','PK-AC12'),('execution/test-plan.json','tests','T-PK-AC12')]:
        doc=mi_original(iot_historical(path),path);row=next(x for x in doc[key] if x['id']==rid);association=row.pop('boundedAmendmentEvidenceAssociation',None)
        check(association==state['amendReadiness']['mappingCorrection'] and association['changeSetId']=='CN-AMEND-001' and association['tests']=='NOT_RUN' and association['fullAcceptance']=='NOT_ACCEPTED','Amendment evidence association invalid '+rid)
        check(doc==am_prior[path],'Amendment changed full requirement/test coverage '+path)
    stripped=json.loads(json.dumps(state));stripped.pop('amendReadiness',None)
    for key in ('taskId','currentChangeSet','mode','workStatus','implementationAuthorization','nextAction','implementationEvidence','approvalQueue','blockers','evidencePath','verificationScope'):stripped[key]=am_prior['execution/status.json'][key]
    check(stripped==am_prior['execution/status.json'],'Amendment changed unrelated execution history')
    check(state['amendReadiness']['approval']==am_task['boundedAmendmentScopeApproval'] and state['amendReadiness']['approval']['implementationAuthorization']=='NOT_GRANTED_READINESS_ONLY' and state['amendReadiness']['tests']=='NOT_RUN','Amendment approval promoted to implementation/test evidence')
    am_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',am_base,'--','backend','frontend'],text=True).splitlines())
    am_current=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(am_current-mi_added==am_app,'Amendment approval changed application inventory')
    am_delta=set(subprocess.check_output(['git','-C',str(R),'diff','--name-only',am_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh','knowledge/tokens','knowledge/styles'],text=True).splitlines())
    check(am_delta-mi_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Amendment recording-only approval changed application/tokens/launchers')
    am_migrations=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',am_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added==am_migrations,'Amendment recording-only approval added migration')
    for name in am_migrations:check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',am_base+':'+name]),'Amendment altered historical migration '+name)
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def am_original(payload,path):
        payload=mi_original(payload,path)
        if path in am_expected and hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()==am_expected[path]:return json.loads(json.dumps(am_prior[path]))
        return payload
    def am_bytes(path):
        if path in am_expected:return subprocess.check_output(['git','-C',str(R),'show',am_base+':knowledge/'+path])
        return (K/path).read_bytes()
    am_read_json=read_json
    def read_json(name):return am_original(am_read_json(name),'execution/'+name)
    backlog=am_original(backlog,'execution/change-sets.json');state=am_original(state,'execution/status.json');coverage=am_original(coverage,'execution/requirements.json');tests_doc=am_original(tests_doc,'execution/test-plan.json')
    # Separately authorized bounded customer implementation; historical guards replay original inputs only after exact checks.
    cu_base='9b1a361870f24f5f2611d0e1e14179bd101d4076'
    cu_allowed={'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/tests/transport.test.mjs', 'backend/src/main/java/com/cinenest/booking/internal/SyntheticBookingAdapter.java', 'backend/src/main/java/com/cinenest/payments/internal/LedgerRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/LedgerController.java', 'frontend/app/api/bank/requests/route.ts', 'frontend/tests/customer-bookings-browser.mjs', 'frontend/tests/customer-bookings.test.mjs', 'backend/src/main/java/com/cinenest/payments/internal/JdbcRefundRepository.java', 'frontend/components/shell.tsx', 'frontend/tests/foundation.test.mjs', 'backend/src/test/java/com/cinenest/IdentityIntegrationTest.java', 'frontend/app/bookings/page.tsx', 'backend/src/test/java/com/cinenest/VerificationIntegrationTest.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcBankRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/BankService.java', 'backend/src/main/java/com/cinenest/payments/internal/CustomerReads.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/payments/internal/BankController.java', 'backend/src/main/java/com/cinenest/payments/internal/RefundService.java', 'frontend/components/customer-bookings.tsx', 'backend/src/main/java/com/cinenest/payments/internal/RefundRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/BankRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcLedgerRepository.java', 'backend/src/main/java/com/cinenest/booking/SyntheticBooking.java', 'frontend/app/globals.css', 'frontend/app/bookings/[id]/page.tsx', 'frontend/app/api/bank/requests/[id]/refunds/route.ts', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/payments/internal/LedgerService.java', 'backend/src/main/java/com/cinenest/payments/internal/RefundController.java', 'frontend/app/api/bookings/route.ts', 'frontend/package.json'}
    cu_authority={'source': 'Owner inline instruction and attachment 5c0c34f5-bf45-4f67-9f68-7687f547a88f', 'requestSha256': 'c125f698b4faa3f6f52ff919fcb904933724b15c39e008f53034dd13e0ad6544', 'scope': 'GRANTED_BOUNDED_SYNTHETIC_CUSTOMER', 'date': '2026-10-18', 'realPayments': 'DISABLED', 'gateway': 'DEFERRED_BLOCKED'}
    def cu_prior(path):return json.loads(subprocess.check_output(['git','-C',str(R),'show',cu_base+':knowledge/'+path],text=True))
    cu=state.get('customerImplementation',{})
    cu_task=next(row for row in backlog['changeSets'] if row['id']=='CN-CUSTOMER-001')
    check(cu.get('authority')==cu_authority and cu.get('baselineHead')==cu_base,'Customer implementation authority/baseline invalid')
    check(cu.get('workStatus') in ('IN_PROGRESS','VERIFIED'),'Customer implementation status invalid')
    cu_closed=cu.get('workStatus')=='VERIFIED'
    expected_task=next(t for t in cu_prior('execution/change-sets.json')['changeSets'] if t['id']=='CN-CUSTOMER-001').copy()
    expected_task.update({'affectedFilesModules': 'Booking-owned typed purchased history; Payments-owned typed request/funding/refund reads; Identity route authentication; existing frontend shell and owner-composed customer pages', 'dbApiUiImpact': 'Three GET APIs and /bookings plus /bookings/[id]; no migration or new mutation', 'rollback': 'Disable customer routes or compatible code revert; retain V1-V14 and all immutable purchase/financial/evidence history; no down migration or financial compensation', 'evidencePaths': ['PROJECT.md#customer-bank-only-policy', 'ARCHITECTURE.md#customer-bank-only-scope', 'DESIGN.md#customer-bank-only-ui', 'PROGRESS.md#customer-readiness-reconciliation', 'PROGRESS.md#customer-implementation']})
    expected_task.update(authorityAvailable=True,readiness='CLOSED' if cu_closed else 'READY',workStatus=cu.get('workStatus'),remainingReadinessGates=[],implementationAuthorization=cu_authority)
    check(cu_task==expected_task,'Customer registered scope/dependencies/DoD changed')
    check(len(backlog['changeSets'])==28,'Customer implementation changed task count')
    for row,old in zip(backlog['changeSets'],cu_prior('execution/change-sets.json')['changeSets']):
        if row['id']!='CN-CUSTOMER-001':check(row==old,'Customer changed unrelated task '+row['id'])
    expected_state=cu_prior('execution/status.json');expected_state.update({'mode': 'SYNTHETIC_CUSTOMER_IMPLEMENTATION', 'implementationAuthorization': 'GRANTED_BOUNDED_SYNTHETIC_CUSTOMER', 'implementationEvidence': 'CN-CUSTOMER-001 bounded synthetic implementation; full customer/support acceptance remains pending', 'evidencePath': 'PROGRESS.md#customer-implementation', 'verificationScope': 'Bounded synthetic software only; no commercial/provider/physical/customer acceptance', 'approvalQueue': ['Commercial/support/venue/specialist/provider/production approvals remain separate downstream gates'], 'blockers': ['Downstream only: PayHere deferred/BLOCKED; venue/specialists and wider commercial/privacy/support/physical acceptance pending']})
    expected_state.update(workStatus=cu.get('workStatus'),nextRecommendedChangeSet='CN-AMEND-001' if cu_closed else 'CN-CUSTOMER-001',nextAction='Review CN-AMEND-001 bounded scope and its own policy gates; no implementation' if cu_closed else 'Complete bounded customer checks and exact-source CI',customerImplementation=cu)
    check(state==expected_state,'Customer changed unrelated execution evidence')
    for key,value in {'realPayments':'DISABLED','gateway':'DEFERRED_BLOCKED','fullRequirementAcceptance':'NOT_ACCEPTED','evidencePath':'PROGRESS.md#customer-implementation'}.items():check(cu.get(key)==value,'Customer safety/full acceptance boundary invalid '+key)
    cu_coverage_paths={'execution/requirements.json':{'CN-C08','CN-SUP-01'},'execution/test-plan.json':{'T-CN-C08','T-CN-SUP-01','T-CN-NFR-02'}}
    for path in cu_coverage_paths:
        check(am_original(iot_historical(path),path)==cu_prior(path),'Customer changed full requirement/test history '+path)
    for path in ('decisions/cinenest-open-decisions.json','execution/source-ledger.json','sources/cinenest-source-register.json'):
        check(am_original(iot_historical(path),path)==cu_prior(path),'Customer changed decisions/source evidence '+path)
    check(set(cu.get('ownedFiles',{}))==cu_allowed,'Customer owned source inventory invalid')
    for name,digest in cu.get('ownedFiles',{}).items():check((R/name).is_file() and hashlib.sha256(mi_source(name)).hexdigest()==digest,'Customer source checksum mismatch '+name)
    cu_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',cu_base,'--','backend','frontend'],text=True).splitlines());cu_added=(cu_allowed-cu_app)|mi_added
    cu_inventory=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(cu_inventory==cu_app|cu_added,'Customer changed unrelated application inventory')
    cu_delta=set(subprocess.check_output(['git','-C',str(R),'diff','--name-only',cu_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines())
    check(cu_delta-cu_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Customer changed unrelated source/launcher')
    cu_migrations=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',cu_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added==cu_migrations,'Customer added unapproved migration')
    for name in cu_migrations:check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',cu_base+':'+name]),'Customer altered historical migration '+name)
    if cu.get('checks'):
        check(cu.get('evidenceCounts')=={'backendTests': 297, 'bankIntegrationTests': 102, 'architectureTests': 14, 'frontendTests': 95, 'customerBrowserGroups': 32, 'notificationBrowserGroups': 28, 'accessBrowserGroups': 32, 'refundBrowserGroups': 30, 'bankBrowserGroups': 70, 'adminBrowserGroups': 63, 'launcherTests': 14, 'governanceNegativeControls': 12, 'failures': 0, 'errors': 0, 'skipped': 0},'Customer executed evidence counts invalid')
    if cu_closed:
        check(set(cu.get('checks',{}))=={'backend','frontend','browser','governance','launchers'} and all(v=='PASS' for v in cu.get('checks',{}).values()) and cu.get('sourceCI')=='SUCCESS','Customer closure lacks executed checks/exact-source CI')
        source=cu.get('sourceCommit','');check(bool(re.fullmatch('[0-9a-f]{40}',source)) and str(cu.get('sourceRun','')).startswith('https://github.com/Hasitha3513/Theater-Project/actions/runs/'),'Customer CI receipt invalid')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in cu.get('ownedFiles',{}).items():
                original=subprocess.run(['git','-C',str(R),'show',source+':'+name],capture_output=True);check(original.returncode==0 and hashlib.sha256(original.stdout).hexdigest()==digest,'Customer exact-source CI stale '+name)
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def cu_original(payload,path):
        payload=am_original(payload,path)
        if path in ('execution/change-sets.json','execution/status.json') or path in cu_coverage_paths:return cu_prior(path)
        return payload
    def cu_source(name):
        if name in cu_allowed-cu_added:return subprocess.check_output(['git','-C',str(R),'show',cu_base+':'+name])
        return mi_source(name)
    backlog=cu_original(backlog,'execution/change-sets.json');state=cu_original(state,'execution/status.json')
    # Explicit owner-approved customer policy/readiness successor. Implementation remains withheld.
    customer_base='de213bfe67a48075c2aa8d668bab5b8c53c35476'
    customer_expected={'execution/change-sets.json': '3321b57791ceda412dd061f687ae6ed2d2497e0e6d25f20981c1e94f255da4f8', 'execution/status.json': 'ae8491c6eec5bd7379fe80055d09a8d21da5bd3c996900607ee611a68e31bdf3', 'decisions/cinenest-open-decisions.json': 'ed414321fb63c8e0cff712abb9d0861513f46d9c7a0904f2bb247e6abbfae7ea'}
    customer_prior={}
    for path,digest in customer_expected.items():
        actual=cu_original(am_original(iot_historical(path),path),path)
        check(hashlib.sha256(json.dumps(actual,sort_keys=True).encode()).hexdigest()==digest,'Customer bounded approval/scope/history changed '+path)
        customer_prior[path]=json.loads(subprocess.check_output(['git','-C',str(R),'show',customer_base+':knowledge/'+path],text=True))
    customer_task=next(row for row in backlog['changeSets'] if row['id']=='CN-CUSTOMER-001')
    check(customer_task['readiness']=='BLOCKED' and customer_task['workStatus']=='BLOCKED' and not customer_task['authorityAvailable'],'Customer recording-only approval cannot authorize implementation')
    check(customer_task['dependencies']==['CN-ACCESS-001','CN-NOTIFY-001','CN-REFUND-001'] and customer_task['historicalImplementationDependencies']==['CN-ACCESS-001','CN-NOTIFY-001'],'Customer dependency amendment outside approved scope')
    check(customer_task['blockingDecisionIds']==[] and customer_task['remainingReadinessGates']==['Separate bounded synthetic CN-CUSTOMER-001 implementation authorization'],'Customer remaining gate changed')
    for dependency in customer_task['dependencies']:
        row=next(t for t in backlog['changeSets'] if t['id']==dependency)
        check(row['workStatus']=='VERIFIED' and row['readiness']=='CLOSED','Customer prerequisite not verified '+dependency)
    old_tasks=customer_prior['execution/change-sets.json']['changeSets']
    check(len(backlog['changeSets'])==28,'Customer changed task count')
    for row,old in zip(backlog['changeSets'],old_tasks):
        if row['id']!='CN-CUSTOMER-001':check(row==old,'Customer changed unrelated task '+row['id'])
        else:
            for key in ('id','objective','inScope','outOfScope','definitionOfDone','requirementIds','plannedChecks','rollback','requiredApproval'):
                check(row[key]==old[key],'Customer altered registered scope/DoD '+key)
    graph={r['id']:r['dependencies'] for r in backlog['changeSets']}
    def customer_acyclic(node,seen):
        if node in seen:return False
        return all(d in graph and customer_acyclic(d,seen|{node}) for d in graph[node])
    check(all(customer_acyclic(node,set()) for node in graph),'Customer dependency graph cyclic/invalid')
    for path in ('execution/requirements.json','execution/test-plan.json','execution/source-ledger.json','sources/cinenest-source-register.json'):
        check(cu_original(am_original(iot_historical(path),path),path)==json.loads(subprocess.check_output(['git','-C',str(R),'show',customer_base+':knowledge/'+path],text=True)),'Customer changed full coverage/source history '+path)
    decisions=am_original(iot_historical('decisions/cinenest-open-decisions.json'),'decisions/cinenest-open-decisions.json')
    for row,old in zip(decisions['decisions'],customer_prior['decisions/cinenest-open-decisions.json']['decisions']):
        stripped=json.loads(json.dumps(row));bounded=stripped.pop('boundedCustomerApproval',None)
        check(stripped==old,'Customer changed global/other decision '+row['id'])
        if row['id'] in ('OD-07','OD-20','OD-28'):
            check(bounded==customer_task['boundedCustomerScopeApproval'] and row['decisionStatus']=='OPEN_DECISION','Customer bounded decision approval/global boundary invalid '+row['id'])
        else:check(bounded is None,'Customer approved unrelated decision '+row['id'])
    allowed_state=json.loads(json.dumps(state));allowed_state.pop('customerReadiness',None)
    for key in ('taskId','currentChangeSet','mode','workStatus','implementationAuthorization','nextAction','implementationEvidence','approvalQueue','blockers','evidencePath','verificationScope'):allowed_state[key]=customer_prior['execution/status.json'][key]
    check(allowed_state==customer_prior['execution/status.json'],'Customer changed unrelated execution evidence')
    check(state['customerReadiness']['approval']['implementationAuthorization']=='NOT_GRANTED_READINESS_ONLY' and state['customerReadiness']['fullRequirementAcceptance']=='NOT_ACCEPTED','Customer approval promoted to implementation/full acceptance')
    baseline_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',customer_base,'--','backend','frontend'],text=True).splitlines())
    current_app=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(current_app-cu_added==baseline_app,'Customer approval changed application inventory')
    delta=set(subprocess.check_output(['git','-C',str(R),'diff','--name-only',customer_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines())
    check(delta-cu_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Customer readiness-only approval changed application/launcher source')
    migrations=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',customer_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added==migrations,'Customer readiness-only approval created migration')
    for name in migrations:check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',customer_base+':'+name]),'Customer altered historical migration '+name)
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def customer_original(payload,path):
        payload=cu_original(payload,path)
        if path in customer_expected and hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()==customer_expected[path]:return json.loads(json.dumps(customer_prior[path]))
        return payload
    backlog=customer_original(backlog,'execution/change-sets.json');state=customer_original(state,'execution/status.json')
    # Explicitly authorized Notifications implementation successor. Validate it before immutable historical replay.
    ni_base='f285607b37c5f6da4c409442f4ec2e8781ae786e'
    ni_allowed={'backend/src/test/java/com/cinenest/notifications/internal/NotificationTestDriver.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/java/com/cinenest/notifications/NotificationContext.java', 'frontend/app/globals.css', 'backend/src/main/java/com/cinenest/payments/package-info.java', 'backend/src/main/java/com/cinenest/payments/internal/BankService.java', 'frontend/app/api/notifications/[id]/route.ts', 'backend/src/main/java/com/cinenest/notifications/internal/LocalNotificationCapture.java', 'frontend/package.json', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationController.java', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationWorker.java', 'frontend/app/api/notifications/[id]/read/route.ts', 'frontend/app/notifications/page.tsx', 'backend/src/main/resources/db/migration/V14__synthetic_notifications.sql', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationPolicy.java', 'frontend/app/api/admin/venues/[venueId]/notifications/[id]/reconciliations/route.ts', 'backend/src/main/java/com/cinenest/notifications/NotificationFacts.java', 'backend/src/main/java/com/cinenest/access/package-info.java', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/notifications/package-info.java', 'backend/src/main/java/com/cinenest/access/internal/AccessNotificationContext.java', 'frontend/app/api/admin/venues/[venueId]/notifications/[id]/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseNotificationRecipients.java', 'frontend/components/notifications.tsx', 'frontend/app/api/notifications/route.ts', 'frontend/tests/notifications-browser.mjs', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationService.java', 'backend/src/test/java/com/cinenest/notifications/internal/NotificationPolicyTest.java', 'backend/src/main/java/com/cinenest/payments/internal/BankNotificationContext.java', 'backend/src/main/java/com/cinenest/notifications/internal/JdbcNotificationRepository.java', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationFailure.java', 'frontend/app/api/admin/venues/[venueId]/notifications/[id]/retries/route.ts', 'frontend/app/admin/notifications/page.tsx', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationRepository.java', 'frontend/app/api/admin/venues/[venueId]/notifications/[id]/simulations/route.ts', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/notifications/internal/NotificationCapture.java', 'frontend/tests/notifications.test.mjs', 'backend/src/main/java/com/cinenest/access/internal/AccessService.java', 'backend/src/main/java/com/cinenest/identity/NotificationRecipients.java', 'frontend/app/api/admin/venues/[venueId]/notifications/route.ts'}
    def ni_prior(path):return json.loads(subprocess.check_output(['git','-C',str(R),'show',ni_base+':knowledge/'+path],text=True))
    ni=state.get('notificationImplementation',{})
    ni_task=next(row for row in backlog['changeSets'] if row['id']=='CN-NOTIFY-001')
    ni_authority={'source': 'Owner inline instruction and attachment 32fb5987-00ae-45c8-b370-048f03b37017', 'requestSha256': 'db63f19f03efc988f12565b6d39798b6b7711bea4b325adc082d06a218b384b9', 'scope': 'GRANTED_BOUNDED_SYNTHETIC_NOTIFICATIONS', 'date': '2026-10-18', 'liveMessaging': 'DISABLED', 'gateway': 'DEFERRED_BLOCKED'}
    check(ni.get('authority')==ni_authority and ni.get('baselineHead')==ni_base,'Notifications authority/baseline invalid')
    check(ni.get('workStatus') in ('IN_PROGRESS','VERIFIED'),'Notifications implementation status invalid')
    ni_closed=ni.get('workStatus')=='VERIFIED'
    ni_backlog=ni_prior('execution/change-sets.json');ni_state=ni_prior('execution/status.json')
    ni_expected_task=next(row for row in ni_backlog['changeSets'] if row['id']=='CN-NOTIFY-001').copy()
    ni_expected_task.update(**{'affectedFilesModules': 'Notifications-owned capability; narrow typed Payments/Access facts and Identity recipient resolution; existing customer/admin shell; no foreign repository access', 'dbApiUiImpact': 'Implemented bounded synthetic V14 six Notifications-owned tables, eight operations and inbox/admin UI; validation evidence at PROGRESS.md#notification-implementation', 'migrationNeeds': 'Additive V14 synthetic notifications; V1-V13 immutable; no destructive down migration', 'evidencePaths': ['PROJECT.md#notification-synthetic-policy', 'ARCHITECTURE.md#notification-synthetic-scope', 'DESIGN.md#notification-synthetic-ui', 'PROGRESS.md#notification-readiness-reconciliation', 'PROGRESS.md#notification-implementation']})
    ni_expected_task.update(authorityAvailable=True,readiness='CLOSED' if ni_closed else 'READY',workStatus=ni.get('workStatus'),remainingReadinessGates=[],implementationAuthorization=ni_authority)
    check(ni_task==ni_expected_task,'Notifications approved task/scope/dependency changed')
    check(len(backlog['changeSets'])==28,'Notifications changed task count')
    for row,old in zip(backlog['changeSets'],ni_backlog['changeSets']):
        if row['id']!='CN-NOTIFY-001':check(row==old,'Notifications changed unrelated task '+row['id'])
    expected_state=json.loads(json.dumps(ni_state));expected_state.update(mode='SYNTHETIC_NOTIFICATION_IMPLEMENTATION',workStatus=ni.get('workStatus'),implementationAuthorization='GRANTED_BOUNDED_SYNTHETIC_NOTIFICATIONS',nextAction='Review CN-CUSTOMER-001 scope and its own decision gates; no implementation' if ni_closed else 'Complete bounded notification checks and exact-source CI; no live messaging',notificationImplementation=ni)
    if ni_closed:expected_state['nextRecommendedChangeSet']='CN-CUSTOMER-001'
    check(state==expected_state,'Notifications changed unrelated execution history')
    for key,value in {'liveMessaging':'DISABLED','gateway':'DEFERRED_BLOCKED','fullRequirementAcceptance':'NOT_ACCEPTED','evidencePath':'PROGRESS.md#notification-implementation'}.items():check(ni.get(key)==value,'Notifications safety boundary changed '+key)
    for path in ('execution/requirements.json','execution/test-plan.json','decisions/cinenest-open-decisions.json','sources/cinenest-source-register.json'):
        check(customer_original(am_original(iot_historical(path),path),path)==ni_prior(path),'Notifications changed full coverage/decisions/sources '+path)
    check(set(ni.get('ownedFiles',{}))==ni_allowed,'Notifications owned file inventory invalid')
    for name,digest in ni.get('ownedFiles',{}).items():check((R/name).is_file() and hashlib.sha256(cu_source(name)).hexdigest()==digest,'Notifications source checksum mismatch '+name)
    ni_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',ni_base,'--','backend','frontend'],text=True).splitlines());ni_added=ni_allowed-ni_app
    current_inventory=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(current_inventory-cu_added==ni_app|ni_added,'Notifications changed unrelated application inventory')
    delta=set(subprocess.check_output(['git','-C',str(R),'diff','--name-only',ni_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines())
    check(delta-ni_allowed-cu_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Notifications changed unrelated application or launchers')
    migrations=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',ni_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added==migrations|{'backend/src/main/resources/db/migration/V14__synthetic_notifications.sql'},'Notifications migration scope invalid')
    for name in migrations:check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',ni_base+':'+name]),'Notifications altered historical migration '+name)
    if ni.get('checks'):
        check(ni.get('evidenceCounts')=={'backendTests': 288, 'bankIntegrationTests': 93, 'architectureTests': 14, 'notificationPolicyTests': 2, 'frontendTests': 89, 'notificationBrowserGroups': 28, 'accessBrowserGroups': 32, 'refundBrowserGroups': 30, 'bankBrowserGroups': 70, 'adminBrowserGroups': 63, 'launcherTests': 14, 'governanceNegativeControls': 11, 'failures': 0, 'errors': 0, 'skipped': 0},'Notifications executed evidence counts invalid')
    if ni_closed:
        check(set(ni.get('checks',{}))=={'backend','frontend','browser','governance','launchers'} and all(v=='PASS' for v in ni.get('checks',{}).values()) and ni.get('sourceCI')=='SUCCESS','Notifications closure lacks executed checks/CI')
        source=ni.get('sourceCommit','');check(bool(re.fullmatch('[0-9a-f]{40}',source)) and str(ni.get('sourceRun','')).startswith('https://github.com/Hasitha3513/Theater-Project/actions/runs/'),'Notifications exact-source CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in ni.get('ownedFiles',{}).items():
                actual=subprocess.run(['git','-C',str(R),'show',source+':'+name],capture_output=True);check(actual.returncode==0 and hashlib.sha256(actual.stdout).hexdigest()==digest,'Notifications CI source stale '+name)
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def ni_original(payload,path):
        payload=customer_original(payload,path)
        if path=='execution/status.json' and 'notificationImplementation' in payload:return ni_prior(path)
        if path=='execution/change-sets.json' and any(row['id']=='CN-NOTIFY-001' and 'implementationAuthorization' in row for row in payload.get('changeSets',[])):return ni_prior(path)
        return payload
    def ni_source(name):
        if name in ni_allowed-ni_added-cu_added:return subprocess.check_output(['git','-C',str(R),'show',ni_base+':'+name])
        return cu_source(name)
    backlog=ni_original(backlog,'execution/change-sets.json');state=ni_original(state,'execution/status.json')
    # Owner-approved notification readiness-only successor; no implementation, new edges or acceptance.
    notify_base='96a2eb0ebcec784aac9e62b13a0fa3404a798a3b'
    notify_expected={'execution/change-sets.json': 'a146ba085cff5db6434c5270236f65e1fb96bd2b496fc34ffb95151f87325d3b', 'execution/status.json': 'c2c2f83b61e2f591c379d03de07a723d9843671169d036cca0c6873589c6aca0', 'decisions/cinenest-open-decisions.json': 'b5f351badda1f9da99aeefe03019aa82d600e45cb9c3f938c25b2ea6ea51cb2f'}
    notify_prior={}
    for path,digest in notify_expected.items():
        current=ni_original(am_original(iot_historical(path),path),path)
        check(hashlib.sha256(json.dumps(current,sort_keys=True).encode()).hexdigest()==digest,'Notification readiness scope/authority/history changed '+path)
        notify_prior[path]=json.loads(subprocess.check_output(['git','-C',str(R),'show',notify_base+':knowledge/'+path],text=True))
    notify_task=next(row for row in backlog['changeSets'] if row['id']=='CN-NOTIFY-001')
    check(not notify_task['authorityAvailable'] and notify_task['readiness']=='BLOCKED' and notify_task['workStatus']=='BLOCKED','Readiness-only notification approval cannot authorize implementation')
    old_tasks=notify_prior['execution/change-sets.json']['changeSets']
    check(len(backlog['changeSets'])==28 and {x['id']:x['dependencies'] for x in backlog['changeSets']}=={x['id']:x['dependencies'] for x in old_tasks},'Notification approval changed dependency edges/count')
    for row,old in zip(backlog['changeSets'],old_tasks):
        if row['id']!='CN-NOTIFY-001':check(row==old,'Notification approval changed unrelated task '+row['id'])
    for path in ('execution/requirements.json','execution/test-plan.json'):
        check(am_bytes(path)==subprocess.check_output(['git','-C',str(R),'show',notify_base+':knowledge/'+path]),'Notification approval changed full requirement/test coverage '+path)
    notify_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',notify_base,'--','backend','frontend'],text=True).splitlines())
    current_app=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(current_app-ni_added-cu_added==notify_app,'Readiness-only notification approval changed application inventory')
    app_delta=subprocess.check_output(['git','-C',str(R),'diff','--name-only',notify_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines()
    check(set(app_delta)-ni_allowed-cu_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Readiness-only notification approval changed application/launcher source')
    notify_migrations=subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',notify_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines()
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added-ni_added-cu_added==set(notify_migrations),'Readiness-only notification approval created migration')
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def notify_original(payload,path):
        payload=ni_original(payload,path)
        if path in notify_expected and hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()==notify_expected[path]:return json.loads(json.dumps(notify_prior[path]))
        return payload
    backlog=notify_original(backlog,'execution/change-sets.json');state=notify_original(state,'execution/status.json')
    # Authorized Access implementation successor: current source is validated before historical replay.
    ai_base = '23512e5502ebdfbf29cf7e0a66c532b8bd214cb8'
    ai_allowed = {'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/test/java/com/cinenest/access/internal/AccessFaults.java', 'backend/src/main/java/com/cinenest/access/internal/AccessFailure.java', 'backend/src/main/java/com/cinenest/access/internal/LocalAccessSimulator.java', 'frontend/app/api/bookings/[id]/access/simulated-attempts/route.ts', 'backend/src/main/java/com/cinenest/access/internal/AccessSimulator.java', 'frontend/components/shell.tsx', 'frontend/app/api/bookings/[id]/access/credentials/route.ts', 'backend/src/main/java/com/cinenest/access/internal/JdbcAccessRepository.java', 'frontend/package.json', 'frontend/app/globals.css', 'frontend/app/api/admin/venues/[venueId]/access/credentials/[id]/route.ts', 'backend/src/main/java/com/cinenest/access/internal/AccessController.java', 'frontend/app/api/bookings/[id]/access/route.ts', 'backend/src/main/java/com/cinenest/payments/AccessFunding.java', 'backend/src/main/java/com/cinenest/access/internal/AccessRepository.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'frontend/app/api/admin/venues/[venueId]/access/credentials/route.ts', 'frontend/tests/access-browser.mjs', 'frontend/app/api/bookings/[id]/access/credentials/[credential]/reveal/route.ts', 'frontend/app/api/admin/venues/[venueId]/access/credentials/[id]/simulations/route.ts', 'backend/src/test/java/com/cinenest/access/internal/AccessPolicyTest.java', 'frontend/tests/access.test.mjs', 'frontend/app/admin/access/page.tsx', 'frontend/app/api/admin/venues/[venueId]/access/credentials/[id]/revocations/route.ts', 'backend/src/main/java/com/cinenest/access/internal/AccessService.java', 'backend/src/main/java/com/cinenest/booking/BookingAccess.java', 'backend/src/main/java/com/cinenest/access/internal/AccessWorker.java', 'frontend/components/access.tsx', 'frontend/app/access/page.tsx', 'backend/src/main/resources/db/migration/V13__synthetic_access.sql', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/booking/internal/BookingAccessAdapter.java', 'backend/src/main/java/com/cinenest/access/internal/AccessPolicy.java', 'backend/src/main/java/com/cinenest/identity/ScopedAuthorization.java', 'backend/src/main/java/com/cinenest/payments/internal/AccessFundingAdapter.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/java/com/cinenest/access/package-info.java'}
    ai = state.get('accessImplementation', {})
    ai_task = next(row for row in backlog['changeSets'] if row['id']=='CN-ACCESS-001')
    def ai_prior(path):return json.loads(subprocess.check_output(['git','-C',str(R),'show',ai_base+':knowledge/'+path],text=True))
    ai_backlog=ai_prior('execution/change-sets.json');ai_state=ai_prior('execution/status.json')
    ai_expected_task=next(row for row in ai_backlog['changeSets'] if row['id']=='CN-ACCESS-001').copy()
    ai_authority={'source': 'Owner inline instruction and attachment fafad47b-3401-4902-af63-7ca426db46f1', 'requestSha256': 'f41c4764914a753dae9f123de55b3630db6ed342c335a0c41fbeebc21a05dd2c', 'scope': 'GRANTED_BOUNDED_SYNTHETIC_BANK_ONLY_ACCESS', 'date': '2026-10-18', 'realDoorControl': 'DISABLED', 'gateway': 'DEFERRED_BLOCKED'}
    check(ai.get('authority')==ai_authority and ai.get('baselineHead')==ai_base,'Access implementation authority/baseline changed')
    check(ai.get('workStatus') in ('IN_PROGRESS','VERIFIED'),'Access implementation status invalid')
    ai_closed=ai.get('workStatus')=='VERIFIED'
    ai_expected_task.update(authorityAvailable=True,readiness='CLOSED' if ai_closed else 'READY',workStatus=ai.get('workStatus'),implementationAuthorization=ai_authority,remainingReadinessGates=[])
    check(ai_task==ai_expected_task,'Access implementation changed approved task/scope/dependency')
    for row,old in zip(backlog['changeSets'],ai_backlog['changeSets']):
        if row['id']!='CN-ACCESS-001':check(row==old,'Access implementation changed unrelated task '+row['id'])
    expected_state=json.loads(json.dumps(ai_state));expected_state.update(mode='SYNTHETIC_BANK_ACCESS_IMPLEMENTATION',workStatus=ai.get('workStatus'),implementationAuthorization='GRANTED_BOUNDED_SYNTHETIC_BANK_ONLY_ACCESS',nextAction='Review next downstream simulator task and its own approval gates; no implementation' if ai_closed else 'Complete bounded Access implementation checks and exact-source CI; preserve physical/gateway gates',accessImplementation=ai)
    if ai_closed:expected_state.update({'nextRecommendedChangeSet': 'CN-NOTIFY-001', 'nextAction': 'Review CN-NOTIFY-001 bounded channel/privacy scope and OD-01/05/29 gates; no implementation', 'productStage': 'BOUNDED_SYNTHETIC_ACCESS_VERIFIED_REAL_DOOR_CONTROL_DISABLED', 'blockers': ['PayHere deferred/BLOCKED; gateway-backed execution/full acceptance retained', 'Venue/specialist and commercial/physical acceptance gates remain pending', 'Full OD-02 hardware/offline/re-entry/revocation/clocks/power/egress acceptance remains unverified'], 'approvalQueue': ['CN-NOTIFY-001 separate channel/sandbox/privacy scope and implementation approval; OD-01/05/29 remain open', 'Real commercial banking/fee/privacy/staffing/provider/production approvals remain separate'], 'implementationEvidence': 'CN-ACCESS-001 VERIFIED/CLOSED for approved synthetic bank-only software scope; physical/gateway/full acceptance incomplete', 'verificationScope': 'Bounded synthetic access lifecycle and simulator only; no real admission or physical/customer acceptance', 'evidencePath': 'PROGRESS.md#access-implementation', 'evidenceFreshness': '2026-10-18 exact Access source CI39111005350 SUCCESS;39 owned source hash receipt', 'publicationEvidence': 'Source 05ad25908d320b0d4a90a9c5cf7ae3da10183131 pushed to origin/main; exact-source CI39111005350 SUCCESS; final documentation receipt verified separately', 'lastObservedHead': '05ad25908d320b0d4a90a9c5cf7ae3da10183131'})
    check(state==expected_state,'Access implementation changed unrelated execution history')
    for key,value in {'realDoorControl':'DISABLED','gateway':'DEFERRED_BLOCKED','fullRequirementAcceptance':'NOT_ACCEPTED','evidencePath':'PROGRESS.md#access-implementation'}.items():check(ai.get(key)==value,'Access implementation boundary changed '+key)
    check(set(ai.get('ownedFiles',{}))==ai_allowed,'Access owned source inventory changed')
    for name,digest in ai.get('ownedFiles',{}).items():check((R/name).is_file() and hashlib.sha256(ni_source(name)).hexdigest()==digest,'Access source checksum mismatch '+name)
    ai_baseline_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',ai_base,'--','backend','frontend'],text=True).splitlines())
    ai_added=ai_allowed-ai_baseline_app
    ai_current_app=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    check(ai_current_app-ni_added-cu_added==ai_baseline_app|ai_added,'Access changed unrelated application inventory')
    for path in ('execution/requirements.json','execution/test-plan.json','decisions/cinenest-open-decisions.json'):
        check(notify_original(am_original(iot_historical(path),path),path)==ai_prior(path),'Access changed full requirement/test/decision history '+path)
    ai_migrations=subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',ai_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines()
    for name in ai_migrations:check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',ai_base+':'+name]),'Access altered historical migration '+name)
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added-ni_added-cu_added==set(ai_migrations)|{'backend/src/main/resources/db/migration/V13__synthetic_access.sql'},'Access migration scope invalid')
    if ai.get('checks',{}).get('backend')=='PASS':
        check(ai.get('evidenceCounts')=={'backendTests': 264, 'bankIntegrationTests': 72, 'architectureTests': 13, 'accessPolicyTests': 2, 'frontendTests': 85, 'accessBrowserGroups': 32, 'refundBrowserGroups': 30, 'bankBrowserGroups': 70, 'adminBrowserGroups': 63, 'launcherTests': 14, 'failures': 0, 'errors': 0, 'skipped': 0},'Access executed evidence counts changed/missing')
    if ai_closed:
        check(ai.get('checks') and all(v=='PASS' for v in ai.get('checks',{}).values()) and set(ai.get('checks',{}))=={'backend','frontend','browser','governance','launchers'} and ai.get('sourceCI')=='SUCCESS','Access closure lacks actual checks/exact-source CI')
        source=ai.get('sourceCommit','');check(bool(re.fullmatch('[0-9a-f]{40}',source)) and str(ai.get('sourceRun','')).startswith('https://github.com/Hasitha3513/Theater-Project/actions/runs/'),'Access closure CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in ai.get('ownedFiles',{}).items():
                result=subprocess.run(['git','-C',str(R),'show',source+':'+name],capture_output=True);check(result.returncode==0 and hashlib.sha256(result.stdout).hexdigest()==digest,'Access CI source stale '+name)
    if errors:
        print('\n'.join('FAIL '+message for message in errors));return 1
    def ai_original(payload,path):
        payload=notify_original(payload,path)
        if path=='execution/status.json' and 'accessImplementation' in payload:return ai_prior(path)
        if path=='execution/change-sets.json' and any(row['id']=='CN-ACCESS-001' and 'implementationAuthorization' in row for row in payload.get('changeSets',[])):return ai_prior(path)
        return payload
    def ai_source(name):
        if name in ai_allowed-ai_added:return subprocess.check_output(['git','-C',str(R),'show',ai_base+':'+name])
        return ni_source(name)
    backlog=ai_original(backlog,'execution/change-sets.json');state=ai_state
    # Readiness-only Access successor: validate exact owner-approved records before historical replay.
    access_base = 'ced1ee7d16215315927f28a996e3dccac96cd2bb'
    access_expected = {'execution/change-sets.json': 'deb69bca1c3a15e92a1f5fdb7b470b64dafc5f7c79fa83641431242df873a0b9', 'execution/status.json': '4ff7c501a87ca1702c9c374aafc41f926d0ee6d7dc703aeafe64275d5c1e0239', 'decisions/cinenest-open-decisions.json': 'cc173d926e3e2f4528817737fcec8aa886951678801b8b649fd97695079eea07'}
    access_prior = {}
    for path, digest in access_expected.items():
        actual = ai_original(am_original(iot_historical(path),path),path)
        check(hashlib.sha256(json.dumps(actual,sort_keys=True).encode()).hexdigest()==digest, 'Access readiness scope/authority/history changed '+path)
        access_prior[path] = json.loads(subprocess.check_output(['git','-C',str(R),'show',access_base+':knowledge/'+path],text=True))
    access_task = next(row for row in backlog['changeSets'] if row['id']=='CN-ACCESS-001')
    old_access = next(row for row in access_prior['execution/change-sets.json']['changeSets'] if row['id']=='CN-ACCESS-001')
    check(access_task['dependencies']==['CN-PAY-BANK-002'] and access_task['historicalImplementationDependencies']==old_access['dependencies'] and [d['taskId'] for d in access_task['deferredAcceptanceDependencies']]==['CN-PAY-GW-001'], 'Access hard/deferred dependency distinction lost')
    check(not access_task['authorityAvailable'] and access_task['readiness']=='BLOCKED' and access_task['workStatus']=='BLOCKED', 'Readiness-only Access approval cannot authorize implementation')
    for row,old in zip(backlog['changeSets'],access_prior['execution/change-sets.json']['changeSets']):
        if row['id']!='CN-ACCESS-001': check(row==old,'Access changed unrelated task '+row['id'])
    for path in ('execution/requirements.json','execution/test-plan.json'):
        check(am_bytes(path)==subprocess.check_output(['git','-C',str(R),'show',access_base+':knowledge/'+path]),'Access changed full requirement/test coverage '+path)
    app_delta=subprocess.check_output(['git','-C',str(R),'diff','--name-only',access_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines()
    check(set(app_delta)-ai_allowed-ni_allowed-cu_allowed-mi_allowed<={'frontend/next-env.d.ts'},'Readiness-only Access session changed application/launcher source')
    old_migrations=subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',access_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines()
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added-ai_added-ni_added-cu_added==set(old_migrations),'Readiness-only Access session created migration')
    if errors:
        print('\n'.join('FAIL '+message for message in errors)); return 1
    def access_original(payload,path):
        if path not in access_expected:return payload
        if hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()==access_expected[path]:return json.loads(json.dumps(access_prior[path]))
        return payload
    backlog=access_original(backlog,'execution/change-sets.json')
    state=access_original(state,'execution/status.json')
    # Explicit synthetic refund implementation successor. Validate current bytes and task authority,
    # then replay the preceding readiness-only record as immutable historical evidence.
    impl_base = 'd4cf681531c73ff32b586447ed6ce0b2089b4423'
    impl_allowed = {'frontend/app/api/admin/venues/[venueId]/refunds/route.ts', 'frontend/app/api/bank/requests/[id]/refunds/route.ts', 'frontend/tests/refunds-browser.mjs', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'frontend/app/admin/refunds/page.tsx', 'backend/src/main/java/com/cinenest/payments/internal/JdbcRefundRepository.java', 'frontend/components/refunds.tsx', 'backend/src/main/java/com/cinenest/payments/internal/RefundPolicy.java', 'frontend/app/refunds/page.tsx', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/payments/internal/LedgerRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcLedgerRepository.java', 'frontend/app/api/admin/venues/[venueId]/refunds/[id]/attempts/[attemptId]/evidence/route.ts', 'frontend/tests/refunds.test.mjs', 'backend/src/main/java/com/cinenest/payments/internal/RefundController.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/app/api/admin/venues/[venueId]/refunds/[id]/attempts/route.ts', 'backend/src/main/resources/db/migration/V12__synthetic_bank_refunds.sql', 'frontend/app/api/admin/venues/[venueId]/refunds/[id]/reconciliations/route.ts', 'frontend/app/api/admin/venues/[venueId]/refunds/[id]/destination-verifications/route.ts', 'frontend/app/api/refunds/[id]/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/RefundService.java', 'backend/src/test/java/com/cinenest/AdminReadIntegrationTest.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/package.json', 'backend/src/main/java/com/cinenest/payments/internal/RefundRepository.java', 'frontend/app/api/admin/venues/[venueId]/refunds/[id]/decisions/route.ts', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/payments/internal/LedgerService.java'}
    impl = state.get('refundImplementation', {})
    impl_task = next(row for row in backlog['changeSets'] if row['id']=='CN-REFUND-001')
    def impl_prior(path):
        return json.loads(subprocess.check_output(['git','-C',str(R),'show',impl_base+':knowledge/'+path],text=True))
    impl_state = impl_prior('execution/status.json')
    current_state = json.loads(json.dumps(state)); current_state.pop('refundImplementation',None)
    impl_expected_state=json.loads(json.dumps(impl_state))
    impl_expected_state.update(mode='SYNTHETIC_BANK_REFUND_IMPLEMENTATION',workStatus=impl.get('workStatus'),implementationAuthorization='GRANTED_BOUNDED_SYNTHETIC_BANK_ONLY',nextAction='Review CN-ACCESS-001 bank-only scope and retained gateway dependency; no implementation' if impl.get('workStatus')=='VERIFIED' else 'Complete scoped refund checks and exact-source CI; preserve real-money/gateway gates')
    impl_expected_state['nextRecommendedChangeSet']='CN-ACCESS-001' if impl.get('workStatus')=='VERIFIED' else impl_state['nextRecommendedChangeSet']
    check(current_state == impl_expected_state, 'Refund implementation changed unrelated execution history')
    check(impl.get('baselineHead')==impl_base, 'Refund implementation baseline changed')
    check(impl.get('authority') == {'source': 'Owner inline instruction and attachment 9cc7b569-fe73-44e6-9ca7-7052cca6fb06', 'requestSha256': '0204e099ec667c57aa10e8e8cfcafe4c42eaff9840ee0d5dc34dc8b054bcf9ec', 'scope': 'APPROVED_SYNTHETIC_BANK_ONLY_IMPLEMENTATION', 'date': '2026-10-17', 'realRefunds': 'DISABLED', 'gateway': 'DEFERRED_BLOCKED'}, 'Refund implementation owner authority missing/changed')
    for field,value in {'realRefunds':'DISABLED','gateway':'DEFERRED_BLOCKED','fullRequirementAcceptance':'NOT_ACCEPTED','commercialRefunds':'NOT_IMPLEMENTED','evidencePath':'PROGRESS.md#refund-implementation'}.items():
        check(impl.get(field)==value,'Refund boundary changed '+field)
    check(set(impl.get('ownedFiles',{}))==impl_allowed,'Refund owned source inventory changed')
    for name,digest in impl.get('ownedFiles',{}).items():
        check((R/name).is_file() and hashlib.sha256(ai_source(name)).hexdigest()==digest,'Refund source checksum mismatch '+name)
    check(impl.get('workStatus') in ('IN_PROGRESS','VERIFIED'), 'Refund status invalid')
    if impl.get('workStatus')=='VERIFIED':
        check(all(value=='PASS' for value in impl.get('checks',{}).values()) and set(impl.get('checks',{}))=={'backend','frontend','browser','governance'} and impl.get('sourceCI')=='SUCCESS', 'Refund closure lacks actual checks/source CI')
        source=impl.get('sourceCommit','')
        check(bool(re.fullmatch('[0-9a-f]{40}',source)) and str(impl.get('sourceRun','')).startswith('https://github.com/Hasitha3513/Theater-Project/actions/runs/'), 'Refund closure source CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in impl.get('ownedFiles',{}).items():
                result=subprocess.run(['git','-C',str(R),'show',source+':'+name],capture_output=True)
                check(result.returncode==0 and hashlib.sha256(result.stdout).hexdigest()==digest,'Refund CI source stale '+name)
    original_backlog=impl_prior('execution/change-sets.json')
    expected_task=next(row for row in original_backlog['changeSets'] if row['id']=='CN-REFUND-001').copy()
    expected_task.update(authorityAvailable=True,readiness='CLOSED' if impl.get('workStatus')=='VERIFIED' else 'READY',workStatus=impl.get('workStatus'),remainingReadinessGates=[],implementationAuthorization=impl.get('authority'))
    expected_task.update({'affectedFilesModules': 'Payments refund application/persistence/HTTP, Identity public scoped authorization and existing customer/finance shells; no Booking mutation or foreign repository', 'dbApiUiImpact': 'V12 seven Payments-owned refund records; eight approved bank-only APIs; /refunds and /admin/refunds with existing tokens/primitives', 'migrationNeeds': 'V12__synthetic_bank_refunds.sql additive; V1-V11 unchanged; shared PostgreSQL allocation/refund commitment guard retained on rollback', 'evidencePaths': ['ARCHITECTURE.md#refund-bank-only-scope', 'PROJECT.md#refund-bank-only-policy', 'PROGRESS.md#refund-readiness-reconciliation', 'PROGRESS.md#refund-implementation']})
    check(impl_task==expected_task,'Refund task scope/dependency/approval changed beyond implementation status')
    for row,prior in zip(backlog['changeSets'],original_backlog['changeSets']):
        if row['id']!='CN-REFUND-001': check(row==prior,'Refund changed unrelated task '+row['id'])
    check(len(backlog['changeSets'])==28,'Refund task count changed')
    for name in ('requirements.json','test-plan.json'):
        check(read_json(name)==impl_prior('execution/'+name),'Refund changed full requirements/test acceptance '+name)
    def impl_original(payload,path):
        payload=access_original(ai_original(payload,path),path)
        if path=='execution/change-sets.json' and any('implementationAuthorization' in row and row['id']=='CN-REFUND-001' for row in payload.get('changeSets',[])):
            return impl_prior(path)
        return payload
    def impl_source(name):
        if name in impl_allowed:
            old=subprocess.run(['git','-C',str(R),'show',impl_base+':'+name],capture_output=True)
            if old.returncode==0:return old.stdout
        return ai_source(name)
    impl_added=impl_allowed-set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',impl_base,'--','backend','frontend'],text=True).splitlines())
    # V1-V11 are immutable; only one additive V12 is authorized.
    old_migrations=subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',impl_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines()
    for name in old_migrations: check(mi_source(name)==subprocess.check_output(['git','-C',str(R),'show',impl_base+':'+name]),'Refund altered historical migration '+name)
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added-ai_added-ni_added-cu_added==set(old_migrations)|{'backend/src/main/resources/db/migration/V12__synthetic_bank_refunds.sql'},'Refund migration scope invalid')
    backlog=impl_original(backlog,'execution/change-sets.json')
    state=impl_state
    # Approved refund readiness-only successor: exact scoped delta, two dependency classes, no implementation.
    refund_base = '6befd9c3da3b898fbc3f8f5bf6823580aaa54291'
    refund_expected = {'execution/change-sets.json': 'ec04d1fd68c66d3aa8c01ea4a23dd9f4e8d0583b5c544ecceef5674c2c7d152e', 'execution/requirements.json': '3c8f750afc0696351c9bbe28fa2c7f801398ef0516156d308d7a90d1c82befc0', 'execution/test-plan.json': '8d71332b2fbcf1ba102d5b358d54064279ecef52cb4d167fccb5d3f86432cc95', 'decisions/cinenest-open-decisions.json': '9c965c8254c2095888c1fc629035bf8e1a2ed78cdf2a3fd83e9ac3598eb99671'}
    refund_prior = {}
    for path, digest in refund_expected.items():
        actual = impl_original(am_original(iot_historical(path),path),path)
        check(hashlib.sha256(json.dumps(actual, sort_keys=True).encode()).hexdigest() == digest, 'Refund readiness scope/history/requirement evidence changed '+path)
        refund_prior[path] = json.loads(subprocess.check_output(['git','-C',str(R),'show',refund_base+':knowledge/'+path],text=True))
    check(hashlib.sha256(json.dumps(state, sort_keys=True).encode()).hexdigest() == 'f877a7caef62600ab4691685954bed5d653ce5c7db73d55af57aaa50d3db6aa6', 'Refund readiness state/authority changed')
    refund_actual_task = next(row for row in backlog['changeSets'] if row['id']=='CN-REFUND-001')
    check(refund_actual_task['dependencies']==['CN-PAY-BANK-002'] and refund_actual_task['historicalImplementationDependencies']==['CN-PAY-GW-001','CN-PAY-BANK-002'] and [x['taskId'] for x in refund_actual_task['deferredAcceptanceDependencies']]==['CN-PAY-GW-001'], 'Refund hard/deferred gateway dependency missing')
    check(refund_actual_task['readiness']=='BLOCKED' and refund_actual_task['workStatus']=='BLOCKED' and refund_actual_task['authorityAvailable'] is False, 'Readiness-only approval cannot authorize implementation')
    prior_edges={x['id']:x['dependencies'] for x in refund_prior['execution/change-sets.json']['changeSets']}
    for row in backlog['changeSets']:
        check(row['dependencies']==(['CN-PAY-BANK-002'] if row['id']=='CN-REFUND-001' else prior_edges[row['id']]), 'Unapproved refund dependency amendment '+row['id'])
    check(len(coverage['requirements'])==92 and len(tests_doc['tests'])==92, 'Refund approval lost full coverage')
    app_delta = subprocess.check_output(['git','-C',str(R),'diff','--name-only',refund_base,'--','backend','frontend','scripts','Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh'],text=True).splitlines()
    check(set(app_delta) - impl_allowed - ai_allowed - ni_allowed - cu_allowed -mi_allowed<= {'frontend/next-env.d.ts'}, 'Refund readiness session changed application/launcher source')
    migration_base = subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',refund_base,'--','backend/src/main/resources/db/migration'],text=True).splitlines()
    check({p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}-mi_added-ai_added-ni_added-cu_added == set(migration_base)|{'backend/src/main/resources/db/migration/V12__synthetic_bank_refunds.sql'}, 'Readiness-only approval cannot create a migration')

    if errors:
        print('\n'.join('FAIL '+message for message in errors))
        return 1
    def refund_original(payload,path):
        payload=impl_original(payload,path)
        if path not in refund_prior: return payload
        field={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        marked=any('boundedRefundScopeApproval' in row or 'boundedRefundApproval' in row or 'boundedRefundReadiness' in row for row in payload[field])
        if marked:
            check(hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()==refund_expected[path], 'Refund replay encountered unapproved successor '+path)
            return json.loads(json.dumps(refund_prior[path]))
        return payload
    backlog=refund_original(backlog,'execution/change-sets.json')
    coverage=refund_original(coverage,'execution/requirements.json')
    tests_doc=refund_original(tests_doc,'execution/test-plan.json')
    state=json.loads(subprocess.check_output(['git','-C',str(R),'show',refund_base+':knowledge/execution/status.json'],text=True))
    # Owner-approved documentation closure: validate current evidence/gates, then replay immutable history.
    closure_base = '88d5746ed1a4cb987dee56d4579380984a876e63'
    closure = state.get('gateDocumentationClosure')
    check(hashlib.sha256(json.dumps(closure, sort_keys=True).encode()).hexdigest() == '4f8db4294676ca98fca79c45d10a37fb6174f8dd6dc762edc09f5d6c26c60f5c', 'Documentation closure approval/evidence/downstream gates changed')
    prior_state = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', closure_base + ':knowledge/execution/status.json'], text=True))
    prior_backlog = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', closure_base + ':knowledge/execution/change-sets.json'], text=True))
    prior_gate = next(row for row in prior_backlog['changeSets'] if row['id'] == 'CN-GATE-001')
    current_gate = next(row for row in backlog['changeSets'] if row['id'] == 'CN-GATE-001')
    expected_gate = dict(prior_gate)
    expected_gate.update(authorityAvailable=True, readiness='CLOSED', workStatus='VERIFIED', evidencePaths=['PROJECT.md#accountability-appointments', 'PROGRESS.md#gate-documentation-closure', 'sources/cinenest-source-register.json'], documentationClosureApproval={'status': 'OWNER_APPROVED_DOCUMENTATION_ONLY', 'source': 'Owner conversation: CN-GATE-001 — Approve Documentation Closure Boundary and Reconcile Task Status', 'date': '2026-10-17', 'canonicalSpecification': 'PROJECT.md#accountability-appointments', 'retainedDecisionGates': ['OD-03', 'OD-05', 'OD-15'], 'scope': 'Pending venue/specialist appointments are explicit downstream acceptance gates; no real-operation approval'})
    check(current_gate == expected_gate, 'Documentation closure changed task scope, dependencies or retained policies')
    expected_state = json.loads(json.dumps(prior_state))
    expected_state['ownerAccountabilityApproval'].update(gateReadiness='CLOSED', gateClosure='VERIFIED_DOCUMENTATION_ONLY_DOWNSTREAM_GATES_RETAINED', scopeClarification='OWNER_APPROVED_DOCUMENTATION_ONLY; PROJECT.md#accountability-appointments')
    expected_state['gateDocumentationClosure'] = closure
    expected_state.update({'taskId': 'CN-GATE-001', 'currentChangeSet': 'CN-GATE-001', 'mode': 'DOCUMENTATION_GATE_CLOSURE', 'workStatus': 'VERIFIED', 'nextReadyChangeSet': None, 'nextRecommendedChangeSet': 'CN-REFUND-001', 'nextAction': 'Review bank-only CN-REFUND-001 scope and dependency proposal; preserve existing edges until approved', 'implementationAuthorization': 'NOT_GRANTED', 'blockers': ['Venue assignment and venue-specific operational authority PENDING', 'Qualified privacy/legal, physical-safety and independent finance appointments PENDING', 'OD-03/05/15 unresolved downstream policy and qualified acceptance gates retained', 'PayHere deferred/BLOCKED; no independent READY implementation', 'Commercial financial/privacy/safety/production gates remain unapproved']})
    check(state == expected_state, 'Documentation closure changed unrelated state/authority/history')
    check(len(backlog['changeSets']) == 28 and sum(row['readiness']=='CLOSED' for row in backlog['changeSets']) == 16 and sum(row['readiness']=='BLOCKED' for row in backlog['changeSets']) == 12 and not any(row['readiness']=='READY' for row in backlog['changeSets']), 'Documentation closure task totals/readiness incorrect')
    check({row['id']: row['dependencies'] for row in backlog['changeSets']} == {row['id']: row['dependencies'] for row in prior_backlog['changeSets']}, 'Documentation closure changed dependency edges')
    for evidence in ['PROJECT.md#accountability-appointments', 'PROGRESS.md#gate-documentation-closure', 'sources/cinenest-source-register.json']:
        check((K / evidence.split('#')[0]).is_file(), 'Missing documentation closure evidence '+evidence)
    # Recovered bytes remain checked later; this comparison preserves all unrelated task/state payloads.
    current_gate.clear(); current_gate.update(prior_gate)
    state = prior_state
    # Validate the bounded owner-only documentation successor; preserve all prior execution history.
    accountability = state.pop('ownerAccountabilityApproval', None)
    check(hashlib.sha256(json.dumps(accountability, sort_keys=True).encode()).hexdigest() == 'feb9b43648be08eac7c4d3961a31d21d78c6935fe89dd0b08925dbf158e4c8f9', 'Owner accountability approval scope/identity/pending gates changed')
    gate = next(row for row in backlog['changeSets'] if row['id'] == 'CN-GATE-001')
    check(gate['readiness'] == 'BLOCKED' and gate['workStatus'] == 'BLOCKED' and gate['authorityAvailable'] is False and gate['dependencies'] == ['CN-AGENT-SETUP-001'] and gate['blockingDecisionIds'] == ['OD-03', 'OD-05', 'OD-15'], 'Accountability approval must not bypass named owner/policy/dependency gates')
    # Current admin successor is independently constrained before immutable historical checks.
    admin_base='e5b3d58434b3e4aa8ff41f1d813af7700d6f0d47'
    admin_allowlist={'frontend/app/api/admin/venues/[venueId]/accounts/[id]/route.ts', 'backend/src/main/java/com/cinenest/verification/internal/VerificationAuditProjection.java', 'frontend/tests/admin-browser.mjs', 'backend/src/main/java/com/cinenest/identity/ScopedAuditSource.java', 'backend/src/test/java/com/cinenest/AdminReadIntegrationTest.java', 'frontend/app/api/admin/venues/[venueId]/audit/[id]/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/AdminRepository.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'frontend/app/api/admin/venues/[venueId]/audit/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/AdminService.java', 'frontend/app/admin/accounts/page.tsx', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/lib/admin-backend.ts', 'frontend/package.json', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/app/api/admin/venues/[venueId]/accounts/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/AdminController.java', 'frontend/components/admin-reads.tsx', 'frontend/tests/admin-reads.test.mjs', 'backend/src/main/resources/db/migration/V11__scoped_admin_read_audit.sql', 'backend/src/main/java/com/cinenest/identity/internal/JdbcAdminRepository.java', 'frontend/components/shell.tsx'}
    admin_added={'frontend/lib/admin-backend.ts', 'backend/src/main/java/com/cinenest/identity/ScopedAuditSource.java', 'frontend/components/admin-reads.tsx', 'backend/src/main/resources/db/migration/V11__scoped_admin_read_audit.sql', 'frontend/app/api/admin/venues/[venueId]/audit/[id]/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/AdminRepository.java', 'frontend/app/api/admin/venues/[venueId]/audit/route.ts', 'backend/src/test/java/com/cinenest/AdminReadIntegrationTest.java', 'backend/src/main/java/com/cinenest/identity/internal/JdbcAdminRepository.java', 'frontend/tests/admin-reads.test.mjs', 'backend/src/main/java/com/cinenest/identity/internal/AdminController.java', 'frontend/app/api/admin/venues/[venueId]/accounts/[id]/route.ts', 'frontend/tests/admin-browser.mjs', 'backend/src/main/java/com/cinenest/identity/internal/AdminService.java', 'backend/src/main/java/com/cinenest/verification/internal/VerificationAuditProjection.java', 'frontend/app/api/admin/venues/[venueId]/accounts/route.ts', 'frontend/app/admin/accounts/page.tsx'}
    admin_evidence=state.get('adminReadImplementation',{})
    check(set(admin_evidence.get('ownedFiles',{}))==admin_allowlist,'Admin owned source inventory changed')
    check(admin_evidence.get('approval')=={'taskId': 'CN-ADMIN-001', 'status': 'APPROVED_BOUNDED_SYNTHETIC_POLICY', 'requestSha256': 'b9432d4fe4c2c8f1ca52a49f829fe1278b9cd0bbf04d5081531906bb0ab744ef', 'approvalSource': 'Owner instruction and attachment a05eb028-208e-4fe1-9310-1728833e5ed7', 'canonicalSpecification': 'ARCHITECTURE.md#admin-read-boundary', 'fullDecisionStatus': 'OPEN_DECISION'},'Admin owner approval lost/changed')
    check(admin_evidence.get('baselineHead')==admin_base,'Admin baseline changed')
    for field,value in {'realPayments':'DISABLED','productionPersonalData':'DISABLED','exports':'DISABLED','dependencyEdges':'UNCHANGED','gateway':'BLOCKED_ACCESS_UNAVAILABLE'}.items():check(admin_evidence.get(field)==value,'Admin boundary changed '+field)
    for name,digest in admin_evidence.get('ownedFiles',{}).items():check((R/name).is_file() and hashlib.sha256(impl_source(name)).hexdigest()==digest,'Admin source checksum mismatch '+name)
    def admin_source(name):
        if name in admin_allowlist-admin_added:return subprocess.check_output(['git','-C',str(R),'show',admin_base+':'+name])
        return impl_source(name)
    def admin_original(payload,path,current=False):
        payload=refund_original(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',admin_base+':knowledge/'+path],text=True))
        field={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        if path == 'execution/change-sets.json':
            for row in payload[field]:
                if row['id'] == 'CN-GATE-001' and 'documentationClosureApproval' in row:
                    check(row == expected_gate, 'Historical replay encountered unapproved documentation closure')
                    row.clear(); row.update(prior_gate)
        # Current raw records were checked at entry; historical projections retain their own guards.
        marked=any('boundedAdminApproval' in row or 'adminReadImplementationEvidence' in row or row.get('id')=='CN-ADMIN-001' and 'historicalPreApproval' in row for row in payload[field])
        if not marked and not current:return payload
        prior={x['id']:x for x in original[field]}
        check(set(prior)=={x['id'] for x in payload[field]},'Admin stable IDs lost '+path)
        for row in payload[field]:
            old=prior[row['id']];expected=json.loads(json.dumps(old))
            if field=='changeSets' and row['id']=='CN-ADMIN-001':
                expected={'id': 'CN-ADMIN-001', 'phase': 8, 'priority': 'P1', 'objective': 'Read-only scoped account/audit operations', 'requirementIds': ['CN-A06', 'CN-A11', 'CN-A10', 'CN-NFR-02'], 'inScope': 'One restricted account status/audit listing slice; mutations handled by owning prior units', 'outOfScope': 'No unrestricted administrator, secret exposure or operational policy edits', 'dependencies': ['CN-AUTH-001', 'CN-VERIFY-001'], 'blockingDecisionIds': [], 'affectedFilesModules': 'Identity owned admin reads/persistence/HTTP; public ScopedAuditSource implemented by Verification; existing shell/transport/UI and checks', 'dbApiUiImpact': 'Identity admin_read_audit; four venue-scoped GET operations; /admin/accounts; no business mutations or exports', 'securityIotRisks': 'Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized', 'migrationNeeds': 'V11 additive admin_read_audit; V1-V10 unchanged', 'plannedChecks': 'Scoped list/detail/export/redaction/paging tests', 'rollback': 'Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY', 'definitionOfDone': 'Role-specific safe read controls and audit visibility', 'requiredApproval': 'Bounded admin slice + role/retention/export approval', 'authorityAvailable': True, 'readiness': 'READY', 'workStatus': 'IN_PROGRESS', 'evidencePaths': ['ARCHITECTURE.md#admin-read-boundary', 'PROGRESS.md#admin-read-implementation'], 'resolvedDecisionIds': ['OD-12', 'OD-05', 'OD-18', 'OD-28'], 'historicalPreApproval': {'id': 'CN-ADMIN-001', 'phase': 8, 'priority': 'P1', 'objective': 'Read-only scoped account/audit operations', 'requirementIds': ['CN-A06', 'CN-A11', 'CN-A10', 'CN-NFR-02'], 'inScope': 'One restricted account status/audit listing slice; mutations handled by owning prior units', 'outOfScope': 'No unrestricted administrator, secret exposure or operational policy edits', 'dependencies': ['CN-AUTH-001', 'CN-VERIFY-001'], 'blockingDecisionIds': ['OD-05', 'OD-18', 'OD-28'], 'affectedFilesModules': 'Identity/Verification/Audit/UI TBD', 'dbApiUiImpact': 'None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY', 'securityIotRisks': 'Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized', 'migrationNeeds': 'None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission', 'plannedChecks': 'Scoped list/detail/export/redaction/paging tests', 'rollback': 'Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY', 'definitionOfDone': 'Role-specific safe read controls and audit visibility', 'requiredApproval': 'Bounded admin slice + role/retention/export approval', 'authorityAvailable': False, 'readiness': 'BLOCKED', 'workStatus': 'BLOCKED', 'evidencePaths': [], 'resolvedDecisionIds': ['OD-12']}, 'scopeApproval': {'taskId': 'CN-ADMIN-001', 'status': 'APPROVED_BOUNDED_SYNTHETIC_POLICY', 'requestSha256': 'b9432d4fe4c2c8f1ca52a49f829fe1278b9cd0bbf04d5081531906bb0ab744ef', 'approvalSource': 'Owner instruction and attachment a05eb028-208e-4fe1-9310-1728833e5ed7', 'canonicalSpecification': 'ARCHITECTURE.md#admin-read-boundary', 'fullDecisionStatus': 'OPEN_DECISION'}, 'implementationEvidence': {'status': 'LOCAL_VERIFIED_CI_PENDING', 'evidencePath': 'PROGRESS.md#admin-read-implementation', 'fullRequirementAcceptance': 'NOT_ACCEPTED'}}
                closed=admin_evidence.get('remoteCI')=='SUCCESS'
                expected['readiness']='CLOSED' if closed else 'READY';expected['workStatus']='VERIFIED' if closed else 'IN_PROGRESS'
                expected['implementationEvidence']['status']=admin_evidence.get('status')
            elif field=='decisions' and row['id'] in {'OD-05','OD-18','OD-28'}:expected['boundedAdminApproval']={'taskId': 'CN-ADMIN-001', 'status': 'APPROVED_BOUNDED_SYNTHETIC_POLICY', 'requestSha256': 'b9432d4fe4c2c8f1ca52a49f829fe1278b9cd0bbf04d5081531906bb0ab744ef', 'approvalSource': 'Owner instruction and attachment a05eb028-208e-4fe1-9310-1728833e5ed7', 'canonicalSpecification': 'ARCHITECTURE.md#admin-read-boundary', 'fullDecisionStatus': 'OPEN_DECISION'}
            elif field in {'requirements','tests'} and row['id'].removeprefix('T-') in {'CN-A06','CN-A10','CN-A11','CN-NFR-02'}:expected['adminReadImplementationEvidence']={'taskId':'CN-ADMIN-001','status':admin_evidence.get('status'),'evidencePath':'PROGRESS.md#admin-read-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED'}
            check(row==expected,'Admin changed unrelated scope/history '+row['id'])
        return original
    original_state=json.loads(subprocess.check_output(['git','-C',str(R),'show',admin_base+':knowledge/execution/status.json'],text=True))
    expected_state=json.loads(json.dumps(original_state))
    expected_state.update({key:state.get(key) for key in ['adminReadImplementation','previousExecutionContextBeforeAdmin']})
    for field,value in {'currentChangeSet': 'CN-ADMIN-001', 'mode': 'BOUNDED_SYNTHETIC_ADMIN_READ_IMPLEMENTATION', 'workStatus': 'IN_PROGRESS', 'nextAction': 'Review CN-GATE-001 missing sources/accountable owners; PayHere pending and no independent next implementation authority', 'nextRecommendedChangeSet': 'CN-GATE-001', 'implementationAuthorization': 'GRANTED_BOUNDED_SYNTHETIC_ADMIN_READ_ONLY'}.items():expected_state[field]=value
    if admin_evidence.get('remoteCI')=='SUCCESS':expected_state['workStatus']='VERIFIED'
    check(state==expected_state,'Admin changed unrelated status history')
    check(state.get('previousExecutionContextBeforeAdmin')=={p:original_state[p] for p in ['currentChangeSet', 'mode', 'workStatus', 'nextAction', 'nextRecommendedChangeSet', 'implementationAuthorization']},'Admin lost previous context')
    closed=admin_evidence.get('remoteCI')=='SUCCESS'
    check(admin_evidence.get('status')==('VERIFIED_BOUNDED_SYNTHETIC_ADMIN_READ' if closed else 'LOCAL_VERIFIED_CI_PENDING'),'Admin premature verification')
    if closed:
        receipt=admin_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Admin exact CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in admin_evidence['ownedFiles'].items():check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Admin CI source mismatch '+name)
        check(admin_evidence.get('checks',{}).get('allRequiredChecks')=='PASS','Admin checks incomplete')
    if closed:
        projection=re.findall(r'^\| CN-ADMIN-001 \|.*\| ([^|]+) \|$',(K/'PROGRESS.md').read_text(),re.M)
        check(projection==['VERIFIED / CLOSED','VERIFIED / CLOSED'],'Admin current task projections stale')
    if admin_evidence.get('checks',{}).get('allRequiredChecks')=='PASS':
        for field,value in {'backendTests':220,'adminIntegrationTests':7,'architectureTests':11,'frontendTests':75,'adminFrontendTests':3,'adminBrowserGroups':63,'regressionBrowserGroups':190,'launcherTests':14,'failures':0,'errors':0,'skipped':0,'allRequiredChecks':'PASS'}.items():check(admin_evidence['checks'].get(field)==value,'Admin required evidence missing '+field)
    backlog=admin_original(backlog,'execution/change-sets.json',True);coverage=admin_original(coverage,'execution/requirements.json',True);tests_doc=admin_original(tests_doc,'execution/test-plan.json',True)
    admin_original(iot_historical('decisions/cinenest-open-decisions.json'),'decisions/cinenest-open-decisions.json',True)
    state=original_state
    # Bounded gateway decision successor: validate exact deltas before historical receipts.
    gateway_base='18c4b3c6913d952d1c64bd619b695431d03bb6c6'
    def gateway_baseline(path):
        return json.loads(subprocess.check_output(['git','-C',str(R),'show',gateway_base+':knowledge/'+path],text=True))
    gateway_approval={'taskId': 'CN-PAY-GW-001', 'status': 'APPROVED_BOUNDED_SANDBOX_POLICY', 'dateLocal': '2026-10-17', 'approvalSource': 'Owner inline instruction and attachment 8ccb6e72-e9ec-4059-b7d1-258fad34c2eb', 'requestSha256': '9f2543219ae0f453fbbf5b7d5381452937386ef59da3e87970986590f1191050', 'canonicalSpecification': 'ARCHITECTURE.md#gateway-sandbox-policy', 'fullDecisionStatus': 'OPEN_DECISION', 'implementationStatus': 'NOT_VERIFIED'}
    gateway_task_approval={'status': 'APPROVED_CONDITIONAL_ON_SANDBOX_ACCESS', 'approvalSource': 'Owner inline instruction and attachment 8ccb6e72-e9ec-4059-b7d1-258fad34c2eb', 'requestSha256': '9f2543219ae0f453fbbf5b7d5381452937386ef59da3e87970986590f1191050', 'canonicalSpecification': 'ARCHITECTURE.md#gateway-sandbox-policy', 'implementationAuthorization': 'INLINE_AUTHORIZED_ONLY_IF_SANDBOX_AVAILABLE; attachment limits session to recording/qualification; no implementation while access unavailable', 'remainingGate': 'PayHere sandbox credentials, approved integrating domain/public callback and authenticated checkout/retrieval qualification unavailable', 'readiness': 'BLOCKED', 'realPayments': 'DISABLED'}
    gateway_state={'taskId': 'CN-PAY-GW-001', 'baselineHead': '18c4b3c6913d952d1c64bd619b695431d03bb6c6', 'status': 'BLOCKED_ACCESS_UNAVAILABLE', 'approval': {'taskId': 'CN-PAY-GW-001', 'status': 'APPROVED_BOUNDED_SANDBOX_POLICY', 'dateLocal': '2026-10-17', 'approvalSource': 'Owner inline instruction and attachment 8ccb6e72-e9ec-4059-b7d1-258fad34c2eb', 'requestSha256': '9f2543219ae0f453fbbf5b7d5381452937386ef59da3e87970986590f1191050', 'canonicalSpecification': 'ARCHITECTURE.md#gateway-sandbox-policy', 'fullDecisionStatus': 'OPEN_DECISION', 'implementationStatus': 'NOT_VERIFIED'}, 'credentialsAvailable': False, 'configurationInspection': 'No PAYHERE/GATEWAY/MERCHANT environment names or matching local development env keys; values not emitted; external secret stores not assumed', 'domainCallback': 'NOT_CONFIGURED_OR_VERIFIED', 'providerDocumentation': 'DOCUMENTED_ACCESSIBLE', 'providerSandboxVerification': 'NOT_RUN', 'implementation': 'NOT_STARTED', 'migrationHead': 'V10', 'realPayments': 'DISABLED', 'evidencePath': 'PROGRESS.md#gateway-sandbox-qualification', 'scopeReference': 'ARCHITECTURE.md#gateway-sandbox-policy', 'nextAction': 'Provide PayHere sandbox access through private local configuration and a registered sandbox domain/public callback; then qualify checkout, notification and retrieval before CN-PAY-GW-001 implementation'}
    check(state.get('gatewaySandboxQualification')==gateway_state,'Gateway qualification evidence/approval changed')
    original_state=gateway_baseline('execution/status.json')
    expected_state=json.loads(json.dumps(original_state));expected_state['nextAction']='Provide PayHere sandbox access through private local configuration and a registered sandbox domain/public callback; then qualify checkout, notification and retrieval before CN-PAY-GW-001 implementation';expected_state['gatewaySandboxQualification']=gateway_state
    check(state==expected_state,'Gateway changed unrelated status or financial safety')
    state=original_state
    gateway_paths=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',gateway_base,'--','backend','frontend'],text=True).splitlines())
    check(set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())-admin_added-impl_added-ai_added-ni_added-cu_added==gateway_paths,'Gateway decision session changed application inventory')
    for name in gateway_paths-{'frontend/next-env.d.ts'}:
        check(admin_source(name)==subprocess.check_output(['git','-C',str(R),'show',gateway_base+':'+name]),'Gateway decision session changed application '+name)
    for path,field in [('execution/change-sets.json','changeSets'),('decisions/cinenest-open-decisions.json','decisions')]:
        current=admin_original(am_original(iot_historical(path),path),path);expected=gateway_baseline(path)
        for row in current[field]:
            if field=='changeSets' and row['id']=='CN-PAY-GW-001':
                check(row.pop('boundedGatewayScopeApproval',None)==gateway_task_approval,'Gateway scope approval lost/changed')
                check(row['readiness']=='BLOCKED' and row['workStatus']=='BLOCKED' and not row['authorityAvailable'],'Gateway promoted without sandbox access')
                check(row['evidencePaths']==['ARCHITECTURE.md#gateway-sandbox-policy','PROGRESS.md#gateway-sandbox-qualification'],'Gateway evidence references missing')
                row['evidencePaths']=[]
            if field=='decisions' and row['id'] in {'OD-06','OD-07','OD-20'}:
                check(row.pop('boundedGatewayApproval',None)==gateway_approval,'Gateway bounded owner policy lost/changed '+row['id'])
                check(row['decisionStatus']=='OPEN_DECISION','Gateway closed wider commercial decision')
        check(current==expected,'Gateway changed unrelated task/decision/history '+path)
    check('## CN-PAY-GW-001 — bounded gateway approval and access gate' in (K/'ARCHITECTURE.md').read_text(),'Gateway canonical policy absent')
    check('<a id="gateway-sandbox-qualification"></a>' in (K/'PROGRESS.md').read_text(),'Gateway current outcome absent')
    backlog=gateway_baseline('execution/change-sets.json')
    # Validate the current owner-authorized ledger delta before projecting immutable older
    # receipts. Current source, policies, coverage and gates are checked independently;
    # historical comparison never authorizes an unlisted successor or source deletion.
    ledger_base='89b9cd42e693277037275641cf44d0ebd1f884c5'
    ledger_evidence=state.get('bankLedgerImplementation',{})
    ledger_owned=ledger_evidence.get('ownedFiles',{})
    ledger_allowlist={'backend/src/main/java/com/cinenest/booking/SyntheticBooking.java', 'frontend/package.json', 'backend/src/main/java/com/cinenest/booking/internal/SyntheticBookingAdapter.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcLedgerRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/BankService.java', 'frontend/components/bank.tsx', 'frontend/lib/bank.ts', 'frontend/lib/bank-backend.ts', 'frontend/app/api/admin/venues/[venueId]/bank/credits/[id]/corrections/route.ts', 'frontend/app/api/admin/venues/[venueId]/bank/corrections/[id]/decisions/route.ts', 'frontend/app/api/admin/venues/[venueId]/bank/exceptions/[id]/actions/route.ts', 'frontend/app/api/admin/venues/[venueId]/bank/requests/[id]/allocations/route.ts', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'frontend/app/api/bookings/[id]/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/LedgerWorker.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/app/api/admin/venues/[venueId]/bank/exceptions/[id]/route.ts', 'backend/src/main/java/com/cinenest/pricing/BankQuotation.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/resources/db/migration/V10__synthetic_credit_allocation.sql', 'frontend/app/api/admin/venues/[venueId]/bank/exceptions/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/LedgerController.java', 'frontend/app/api/admin/venues/[venueId]/bank/credits/[id]/matches/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/SyntheticCreditSource.java', 'frontend/app/api/admin/venues/[venueId]/bank/credits/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/LedgerService.java', 'frontend/tests/bank-browser.mjs', 'frontend/tests/bank-finance.test.mjs', 'backend/src/main/java/com/cinenest/payments/internal/LedgerRepository.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/components/bank-finance.tsx', 'backend/src/main/java/com/cinenest/pricing/internal/BankQuotationAdapter.java', 'frontend/app/admin/bank/page.tsx', 'frontend/app/api/admin/venues/[venueId]/bank/credits/[id]/route.ts'}
    check(set(ledger_owned)==ledger_allowlist and 'frontend/next-env.d.ts' not in ledger_owned,'Ledger source allowlist changed')
    check(ledger_evidence.get('baselineHead')==ledger_base and ledger_evidence.get('authorizationRequestSha256')=='607271b7d93cc9c232e3ea197e906196edddcf8b2057f89fd7650bf105d7d48d','Ledger owner approval provenance missing')
    for name,digest in ledger_owned.items():check((R/name).is_file() and hashlib.sha256(admin_source(name)).hexdigest()==digest,'Ledger source checksum mismatch '+name)
    ledger_baseline_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',ledger_base,'--','backend','frontend'],text=True).splitlines())
    ledger_current_app=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())-impl_added-ai_added-ni_added-cu_added
    check(ledger_current_app-admin_added==ledger_baseline_app|ledger_allowlist,'Ledger changed application inventory outside owner scope')
    for migration in (R/'backend/src/main/resources/db/migration').glob('V*__*.sql'):
        if migration.relative_to(R).as_posix() in mi_added|ai_added|ni_added:continue # Independently validated Access successor.
        if migration.name=='V12__synthetic_bank_refunds.sql': continue # Checked independently by the implementation successor above.
        if migration.relative_to(R).as_posix() in admin_added:continue
        if migration.name.startswith('V10__'):continue
        check(migration.read_bytes()==subprocess.check_output(['git','-C',str(R),'show',ledger_base+':'+migration.relative_to(R).as_posix()]),'Ledger changed historical migration '+migration.name)
    for field,value in {'realPayments':'DISABLED','commercialConfirmation':'DISABLED','refunds':'DISABLED','liveDelivery':'DISABLED','hardware':'DISABLED','d2':'NOT_READY'}.items():check(ledger_evidence.get(field)==value,'Ledger safety boundary changed '+field)
    ledger_ci=ledger_evidence.get('remoteCI')=='SUCCESS'
    check(ledger_evidence.get('status')==('VERIFIED_BOUNDED_SYNTHETIC_LEDGER' if ledger_ci else 'IN_PROGRESS'),'Ledger premature verification')
    if ledger_ci:
        receipt=ledger_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and isinstance(receipt.get('runId'),int) and re.fullmatch(r'[0-9a-f]{40}',sha) is not None,'Ledger exact-source CI receipt missing')
        if re.fullmatch(r'[0-9a-f]{40}',sha):
            for name,digest in ledger_owned.items():check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Ledger CI/source mismatch '+name)
        check(ledger_evidence.get('checks',{}).get('allRequiredChecks')=='PASS','Ledger required checks incomplete')
    if ledger_ci or ledger_evidence.get('checks',{}).get('allRequiredChecks')=='PASS':
        for field,value in {'backendTests':212,'bankIntegrationTests':32,'ledgerIntegrationTests':14,'architectureTests':10,'frontendTests':72,'ledgerFrontendTests':4,'browserGroups':190,'bankBrowserGroups':70,'ledgerBrowserGroups':35,'launcherTests':14,'negativeControls':8,'failures':0,'errors':0,'skipped':0,'allRequiredChecks':'PASS'}.items():check(ledger_evidence.get('checks',{}).get(field)==value,'Ledger required evidence missing '+field)
    def ledger_source(name):
        if name in ledger_allowlist and name in ledger_baseline_app:return subprocess.check_output(['git','-C',str(R),'show',ledger_base+':'+name])
        return admin_source(name)
    ledger_validated=set()
    def ledger_original_payload(payload,path):
        # Gateway additions were independently checked above, never authorize application changes.
        payload=admin_original(payload,path)
        payload=json.loads(json.dumps(payload))
        if path=='decisions/cinenest-open-decisions.json':
            for row in payload['decisions']:
                if row['id'] in {'OD-06','OD-07','OD-20'}:row.pop('boundedGatewayApproval',None)
        if path=='execution/change-sets.json':
            for row in payload['changeSets']:
                if row['id']=='CN-PAY-GW-001' and 'boundedGatewayScopeApproval' in row:
                    row.pop('boundedGatewayScopeApproval');row['evidencePaths']=[]
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',ledger_base+':knowledge/'+path],text=True))
        if path in ledger_validated and payload==original:return original
        field={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        check({r['id'] for r in payload[field]}=={r['id'] for r in original[field]},'Ledger lost stable IDs '+path)
        prior={r['id']:r for r in original[field]}
        for row in payload[field]:
            old=prior[row['id']];copy=json.loads(json.dumps(row))
            if field=='changeSets' and row['id']=='CN-PAY-BANK-002':
                check(copy.pop('historicalPreApproval',None)==old,'Ledger changed pre-approval task history')
                approval=copy.pop('scopeApproval',{})
                check(approval=={'status':'APPROVED_BOUNDED_SYNTHETIC_IMPLEMENTATION','approvalSource':'Owner instruction and attachment 5030b7c9-38bf-4189-9705-39caebae3640','requestSha256':'607271b7d93cc9c232e3ea197e906196edddcf8b2057f89fd7650bf105d7d48d','canonicalSpecification':'ARCHITECTURE.md#bank-ledger-boundary','realPayments':'DISABLED'},'Ledger scope approval changed')
                for k,v in {'authorityAvailable':True,'readiness':'CLOSED' if ledger_ci else 'READY','workStatus':'VERIFIED' if ledger_ci else 'IN_PROGRESS','blockingDecisionIds':[],'resolvedDecisionIds':['OD-24','OD-27','OD-28','OD-29']}.items():check(copy.get(k)==v,'Ledger task gate changed '+k)
                check(copy.pop('implementationEvidence',None)=={'status':ledger_evidence.get('status'),'evidencePath':'PROGRESS.md#bank-ledger-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Ledger task evidence unsupported')
                check(copy.get('evidencePaths')==['ARCHITECTURE.md#bank-ledger-boundary','PROGRESS.md#bank-ledger-implementation'],'Ledger task evidence references missing')
                copy['evidencePaths']=old['evidencePaths']
                copy.pop('resolvedDecisionIds',None)
                for k in ['authorityAvailable','readiness','workStatus','blockingDecisionIds']:copy[k]=old[k]
            elif field=='decisions' and row['id'] in {'OD-24','OD-27','OD-28','OD-29'}:
                approval=copy.pop('boundedBankLedgerApproval',None)
                check(approval=={'taskId':'CN-PAY-BANK-002','status':'APPROVED_BOUNDED_SYNTHETIC_POLICY','dateLocal':'2026-10-17','approvalSource':'Owner instruction and attachment 5030b7c9-38bf-4189-9705-39caebae3640','requestSha256':'607271b7d93cc9c232e3ea197e906196edddcf8b2057f89fd7650bf105d7d48d','canonicalSpecification':'ARCHITECTURE.md#bank-ledger-boundary','fullDecisionStatus':'OPEN_DECISION'},'Ledger bounded policy changed '+row['id'])
            elif field in {'requirements','tests'} and row['id'].removeprefix('T-') in {'PR-BANK-001','CN-C10','CN-A13','BP-AC01','BP-AC02','BP-AC03','BP-AC05','CN-NFR-02'}:
                check(copy.pop('bankLedgerImplementationEvidence',None)=={'taskId':'CN-PAY-BANK-002','status':ledger_evidence.get('status'),'evidencePath':'PROGRESS.md#bank-ledger-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED','deferred':'Real bank/commercial/gateway-provider/refund/access/notification/physical/customer acceptance remains incomplete'},'Ledger full acceptance overstated '+row['id'])
            check(copy==old,'Ledger changed unapproved task/requirement/test/decision payload '+row['id'])
        ledger_validated.add(path)
        return original
    # All original checks continue against the validated predecessor representation.
    backlog=ledger_original_payload(backlog,'execution/change-sets.json')
    coverage=ledger_original_payload(coverage,'execution/requirements.json')
    tests_doc=ledger_original_payload(tests_doc,'execution/test-plan.json')
    historical_state=json.loads(subprocess.check_output(['git','-C',str(R),'show',ledger_base+':knowledge/execution/status.json'],text=True))
    ledger_state_delta={'taskId':'CN-PAY-BANK-002','requestedTaskId':'CN-PAY-BANK-002','currentChangeSet':'CN-PAY-BANK-002','workStatus':'VERIFIED' if ledger_ci else 'IN_PROGRESS','nextRecommendedChangeSet':'CN-PAY-GW-001','nextAction':'Review CN-PAY-GW-001 bounded provider-sandbox scope and OD-06/07/20; no next implementation authorized','implementationAuthorization':'GRANTED_SYNTHETIC_BANK_LEDGER_ONLY'}
    ledger_context_fields=['mode','publicationState','publicationEvidence','publicationReceipt','lastObservedHead','baselineHead','baselineWorktree','evidencePath','verificationScope','implementationEvidence']
    check(state.get('previousExecutionContextBeforeBankLedger')=={k:historical_state[k] for k in ledger_context_fields},'Ledger erased earlier execution/publication history')
    receipt=ledger_evidence.get('ciReceipt',{})
    ledger_state_delta.update({'mode':'BOUNDED_SYNTHETIC_BANK_LEDGER_IMPLEMENTATION','publicationState':'SOURCE_PUBLISHED_EXACT_CI_SUCCESS' if ledger_ci else 'NOT_PUBLISHED','publicationEvidence':('Source '+receipt.get('headSha','')+' pushed to origin/main; exact-source CI'+str(receipt.get('runId',''))+' SUCCESS; final documentation receipt verified separately') if ledger_ci else 'Pending scoped source publication and exact-source CI','publicationReceipt':receipt if ledger_ci else {'status':'PENDING_SOURCE_CI','branch':'main'},'lastObservedHead':receipt.get('headSha') if ledger_ci else ledger_base,'baselineHead':ledger_base,'baselineWorktree':'Unrelated frontend/next-env.d.ts unstaged; all other baseline paths clean','evidencePath':'PROGRESS.md#bank-ledger-implementation','verificationScope':'Bounded synthetic credit/allocation/guarded confirmation; no real financial or customer acceptance','implementationEvidence':'CN-PAY-BANK-002 VERIFIED/CLOSED for approved synthetic scope; wider acceptance incomplete' if ledger_ci else 'CN-PAY-BANK-002 IN_PROGRESS; local bounded checks PASS, exact-source CI required before closure'})
    for k,v in ledger_state_delta.items():check(state.get(k)==v,'Ledger current status unsupported '+k)
    check({k:v for k,v in state.items() if k not in ledger_state_delta and k not in {'bankLedgerImplementation','previousExecutionContextBeforeBankLedger'}}=={k:v for k,v in historical_state.items() if k not in ledger_state_delta},'Ledger changed unrelated status history')
    state=historical_state
    # Owner-approved synthetic bank successor: validate current deltas before checking all
    # immutable historical receipts against their original source. No prior guard is removed.
    bank_base='f257460b156249b377c87a007480f039e542f986'
    bank_evidence=state.get('bankRequestImplementation',{})
    bank_owned=bank_evidence.get('ownedFiles',{})
    bank_ci=bank_evidence.get('remoteCI')=='SUCCESS'
    bank_status='VERIFIED_BOUNDED_SYNTHETIC_BANK_REQUEST' if bank_ci else 'LOCAL_VERIFIED_CI_PENDING'
    check(bank_evidence.get('status')==bank_status,'Bank verification state unsupported')
    check(bank_evidence.get('baselineHead')==bank_base and bank_evidence.get('authorizationRequestSha256')=='22fb4107bb4cc126cbc9a658a7a8a121df0f6d5acd15a269366463464824a407','Bank owner provenance changed')
    check(set(bank_owned)=={'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/test/java/com/cinenest/payments/internal/SyntheticEvidenceTest.java', 'frontend/app/bank/page.tsx', 'backend/src/main/java/com/cinenest/payments/internal/EncryptedEvidenceStore.java', 'backend/src/main/java/com/cinenest/payments/internal/ReviewerNotice.java', 'frontend/app/api/admin/venues/[venueId]/bank/requests/[id]/route.ts', 'frontend/lib/bank.ts', 'frontend/app/api/admin/venues/[venueId]/bank/requests/[id]/decisions/route.ts', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'frontend/app/api/admin/venues/[venueId]/bank/requests/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'frontend/app/globals.css', 'frontend/app/api/bank/requests/[id]/resubmissions/route.ts', 'frontend/app/api/admin/venues/[venueId]/bank/requests/[id]/escalations/route.ts', 'backend/src/main/java/com/cinenest/verification/BankEligibility.java', 'frontend/app/api/admin/venues/[venueId]/bank/requests/[id]/evidence/[evidenceId]/route.ts', 'frontend/app/api/bank/requests/[id]/claims/route.ts', 'frontend/tests/bank.test.mjs', 'backend/src/main/java/com/cinenest/payments/internal/SyntheticEvidenceProcessor.java', 'frontend/app/api/bank/uploads/[id]/complete/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/BankController.java', 'frontend/app/admin/bank/page.tsx', 'frontend/app/api/bank/requests/[id]/route.ts', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/payments/internal/SyntheticFixtures.java', 'backend/src/main/java/com/cinenest/payments/package-info.java', 'backend/src/main/java/com/cinenest/payments/internal/BankFailure.java', 'backend/src/main/java/com/cinenest/payments/internal/DisabledReviewerNotice.java', 'frontend/lib/bank-backend.ts', 'backend/src/main/java/com/cinenest/booking/internal/BankRequestHoldAdapter.java', 'backend/src/main/java/com/cinenest/payments/internal/BankRepository.java', 'backend/src/main/java/com/cinenest/pricing/internal/BankQuotationAdapter.java', 'frontend/app/api/bank/requests/route.ts', 'frontend/app/api/bank/uploads/[id]/content/route.ts', 'frontend/components/bank.tsx', 'backend/src/test/java/com/cinenest/payments/internal/BankFaults.java', 'frontend/app/api/bank/claims/[id]/uploads/route.ts', 'backend/src/main/java/com/cinenest/payments/internal/BankService.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcBankRepository.java', 'backend/src/main/java/com/cinenest/pricing/BankQuotation.java', 'backend/src/main/resources/db/migration/V9__synthetic_bank_requests.sql', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/payments/internal/BankWorker.java', 'frontend/tests/bank-browser.mjs', 'backend/src/main/java/com/cinenest/payments/internal/BankPolicy.java', 'backend/src/main/java/com/cinenest/verification/internal/BankEligibilityAdapter.java', 'backend/src/main/java/com/cinenest/booking/BankRequestHolds.java', 'frontend/package.json', 'backend/src/test/java/com/cinenest/VerificationIntegrationTest.java', 'backend/src/main/java/com/cinenest/payments/internal/PrivateEvidenceStore.java'} and 'frontend/next-env.d.ts' not in bank_owned,'Bank source inventory exceeds owner scope')
    baseline_app=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',bank_base,'--','backend','frontend'],text=True).splitlines())
    current_app=set(subprocess.check_output(['git','-C',str(R),'ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())-impl_added-ai_added-ni_added-cu_added
    check(current_app-admin_added-(ledger_allowlist-ledger_baseline_app)==baseline_app|set(bank_owned),'Bank introduced unreviewed application files or removed retained sources')
    for name,digest in bank_owned.items():
        check((R/name).is_file() and hashlib.sha256(ledger_source(name)).hexdigest()==digest,'Bank source mismatch '+name)
    for migration in (R/'backend/src/main/resources/db/migration').glob('V[1-8]__*.sql'):
        check(migration.read_bytes()==subprocess.check_output(['git','-C',str(R),'show',bank_base+':'+migration.relative_to(R).as_posix()]),'Bank changed historical migration '+migration.name)
    for field,value in {'realPayments':'DISABLED','creditVerification':'NOT_IMPLEMENTED','allocation':'NOT_IMPLEMENTED','bookingConfirmation':'NOT_IMPLEMENTED','liveDelivery':'DISABLED','d2':'NOT_READY'}.items():check(bank_evidence.get(field)==value,'Bank safety boundary changed '+field)
    def bank_source(name):
        if name in bank_owned:
            exists=subprocess.run(['git','-C',str(R),'cat-file','-e',bank_base+':'+name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
            if exists:return subprocess.check_output(['git','-C',str(R),'show',bank_base+':'+name])
        return ledger_source(name)
    def bank_original_payload(payload,path):
        payload=ledger_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',bank_base+':knowledge/'+path],text=True))
        key={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        prior={r['id']:r for r in original[key]}
        check(set(prior)=={r['id'] for r in payload[key]},'Bank stable IDs changed '+path)
        normalized=json.loads(json.dumps(payload))
        for row in normalized[key]:
            old=prior[row['id']]
            if key=='changeSets' and row['id']=='CN-PAY-BANK-001':
                check(hashlib.sha256(json.dumps({k:v for k,v in row.items() if k not in {'workStatus','readiness','implementationEvidence'}},sort_keys=True).encode()).hexdigest()=='45744033a22addd238cc09c790aca79dcc72a361d04f7abcbd82968eb5f97146','Bank exact approved scope/history changed')
                check(row.get('historicalPreApproval')==old and row.get('dependencies')==old['dependencies'],'Bank dependency/history changed')
                check(row.get('authorityAvailable') is True and row.get('blockingDecisionIds')==[] and row.get('resolvedDecisionIds')==['OD-23','OD-24','OD-25','OD-26','OD-28','OD-29'],'Bank approval gates missing')
                check(row.get('implementationEvidence')=={'status':bank_status,'evidencePath':'PROGRESS.md#bank-request-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Bank implementation evidence overstated')
                check(row.get('scopeApproval')=={'status': 'APPROVED_BOUNDED_SYNTHETIC_IMPLEMENTATION', 'approvalSource': 'Owner instruction and attachment aef4fd7c-aef4-4391-8fb1-812b64a05b2a', 'canonicalSpecification': 'ARCHITECTURE.md#bank-request-boundary', 'realPayments': 'DISABLED'},'Bank approved scope changed')
                check(row.get('workStatus')==('VERIFIED' if bank_ci else 'IN_PROGRESS') and row.get('readiness')==('CLOSED' if bank_ci else 'READY'),'Bank premature closure')
                for field in ['objective','inScope','outOfScope','requirementIds','definitionOfDone','requiredApproval']:check(row.get(field)==old[field],'Bank original scope changed '+field)
                row.clear();row.update(old)
            elif key=='decisions' and row['id'] in {'OD-23','OD-24','OD-25','OD-26','OD-28','OD-29'}:
                check(row.pop('boundedBankRequestApproval',None)=={'taskId': 'CN-PAY-BANK-001', 'status': 'APPROVED_BOUNDED_SYNTHETIC_POLICY', 'dateLocal': '2026-10-17', 'approvalSource': 'Owner instruction and attachment aef4fd7c-aef4-4391-8fb1-812b64a05b2a', 'requestSha256': '22fb4107bb4cc126cbc9a658a7a8a121df0f6d5acd15a269366463464824a407', 'scope': 'Synthetic-only bank requests/private receipts/durable scoped review; explicit configured timing and retention, zero CineNest-added synthetic BANK fee, no real funds/credit/allocation/confirmation/refunds/live delivery. Exact approved specification in ARCHITECTURE.md#bank-request-boundary.', 'fullDecisionStatus': 'OPEN_DECISION'},'Bank policy approval changed '+row['id'])
            elif key in {'requirements','tests'} and row['id'].removeprefix('T-') in {'BP-AC09', 'PR-BANK-001', 'CN-A13', 'BP-AC08', 'BP-AC07', 'CN-C10', 'CN-NFR-02', 'BP-AC06', 'BP-AC04', 'BP-AC10'}:
                check(row.pop('bankRequestImplementationEvidence',None)=={'taskId': 'CN-PAY-BANK-001', 'status': bank_status, 'evidencePath': 'PROGRESS.md#bank-request-implementation', 'fullRequirementVerified': False, 'fullRequirementAcceptance': 'NOT_ACCEPTED', 'deferred': 'Real receipts/commercial policy, independent bank credits, allocation, confirmation/refunds/live delivery remain unimplemented'},'Bank subset evidence changed '+row['id'])
            check(row==old,'Bank unauthorized registry delta '+path+'/'+row['id'])
        return normalized
    for field,value in {'backendTests':197,'bankIntegrationTests':18,'bankDomainTests':10,'architectureTests':9,'frontendTests':68,'bankFrontendTests':6,'browserGroups':155,'bankBrowserGroups':35,'launcherTests':14,'failures':0,'errors':0,'skipped':0,'negativeControls':7,'allRequiredChecks':'PASS'}.items():check(bank_evidence.get('checks',{}).get(field)==value,'Bank required check missing '+field)
    if bank_ci:
        receipt=bank_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Bank exact-source CI missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in bank_owned.items():check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Bank CI/source mismatch '+name)
        check(bank_evidence.get('checks',{}).get('allRequiredChecks')=='PASS','Bank closure lacks required checks')
    # Owner-authorized CN-VERIFY-001 successor. Every delta is checked before reconstructing
    # earlier immutable receipts; generated owner files and global acceptance remain protected.
    verification_base='069a3f3ec9e8713bbfa4d6e683ac8ff1747918f9'
    verification_evidence=state.get('verificationImplementation',{})
    verification_owned=verification_evidence.get('ownedFiles',{})
    verification_ci=verification_evidence.get('remoteCI')=='SUCCESS'
    verification_status='VERIFIED_BOUNDED_SYNTHETIC_VERIFICATION' if verification_ci else 'LOCAL_VERIFIED_CI_PENDING'
    check(set(verification_owned)=={'backend/src/main/java/com/cinenest/verification/internal/SyntheticEvidenceProcessor.java', 'backend/src/main/java/com/cinenest/verification/internal/VerificationFailure.java', 'backend/src/main/java/com/cinenest/pricing/VerificationQuotation.java', 'frontend/app/api/verification/requests/[id]/uploads/route.ts', 'frontend/app/api/admin/venues/[venueId]/verification/requests/[id]/route.ts', 'backend/src/main/java/com/cinenest/verification/package-info.java', 'backend/src/main/java/com/cinenest/verification/internal/PrivateEvidenceStore.java', 'backend/src/main/java/com/cinenest/verification/internal/JdbcVerificationRepository.java', 'frontend/lib/verification-backend.ts', 'backend/src/main/java/com/cinenest/verification/internal/VerificationController.java', 'backend/src/test/java/com/cinenest/PublicationIntegrationTest.java', 'backend/src/test/java/com/cinenest/VerificationIntegrationTest.java', 'frontend/app/api/verification/uploads/[id]/content/route.ts', 'backend/src/main/java/com/cinenest/verification/internal/VerificationWorker.java', 'backend/src/main/java/com/cinenest/pricing/internal/VerificationQuotationAdapter.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/java/com/cinenest/verification/internal/VerificationRepository.java', 'backend/src/test/java/com/cinenest/QuoteIntegrationTest.java', 'frontend/app/admin/verification/page.tsx', 'frontend/app/api/verification/requests/route.ts', 'frontend/app/api/verification/uploads/[id]/complete/route.ts', 'frontend/tests/verification.test.mjs', 'backend/src/test/java/com/cinenest/verification/internal/VerificationFaults.java', 'backend/src/main/java/com/cinenest/verification/internal/VerificationService.java', 'frontend/package.json', 'frontend/app/verification/page.tsx', 'backend/src/main/java/com/cinenest/verification/internal/EncryptedEvidenceStore.java', 'frontend/app/api/admin/venues/[venueId]/verification/requests/[id]/decisions/route.ts', 'frontend/app/api/admin/venues/[venueId]/verification/requests/[id]/evidence/[evidenceId]/route.ts', 'backend/src/main/java/com/cinenest/verification/internal/VerificationPolicy.java', 'frontend/lib/verification.ts', 'frontend/app/api/verification/requests/[id]/route.ts', 'frontend/app/api/verification/requests/[id]/resubmissions/route.ts', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/resources/db/migration/V8__synthetic_verification.sql', 'frontend/components/verification.tsx', 'backend/src/test/java/com/cinenest/verification/internal/SyntheticEvidenceTest.java', 'backend/src/main/java/com/cinenest/verification/internal/SyntheticFixtures.java', 'frontend/app/api/admin/venues/[venueId]/verification/requests/route.ts', 'frontend/tests/verification-browser.mjs', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java'},'Verification source inventory exceeds approved slice')
    check('frontend/next-env.d.ts' not in verification_owned,'Generated owner change cannot enter verification allowlist')
    for name,digest in verification_owned.items():
        check((R/name).is_file() and hashlib.sha256(bank_source(name)).hexdigest()==digest,'Verification source mismatch '+name)
    verification_task=next(c for c in backlog['changeSets'] if c['id']=='CN-VERIFY-001')
    check(hashlib.sha256(json.dumps({k:v for k,v in verification_task.items() if k not in {'workStatus','readiness','implementationEvidence'}},sort_keys=True).encode()).hexdigest()=='15f3b66bbf74133c2902e305d3f7378376234ad43975d5f4558e2c6402ecd648','Verification owner scope/history changed')
    check(verification_evidence.get('scopeApproval')==verification_task.get('scopeApproval') and verification_evidence.get('status')==verification_status,'Verification evidence/scope mismatch')
    check(verification_evidence.get('realIdentityProcessing')=='DISABLED' and verification_evidence.get('ocr')=='DISABLED' and verification_evidence.get('physicalAdmission')=='NOT_APPROVED' and verification_evidence.get('d1')=='NOT_READY','Synthetic-only boundary exceeded')
    for key,value in {'backendTests':168,'verificationIntegrationTests':14,'verificationDomainTests':9,'architectureTests':8,'frontendTests':62,'verificationFrontendTests':6,'verificationBrowserGroups':21,'failures':0,'errors':0,'skipped':0}.items():
        check(verification_evidence.get('checks',{}).get(key)==value,'Verification checks missing '+key)
    check(verification_evidence.get('checks',{}).get('browserWidths')==[320,375,640,768,1024,1280,1536],'Verification responsive evidence missing')
    for migration in (R/'backend/src/main/resources/db/migration').glob('V[1-7]__*.sql'):
        check(migration.read_bytes()==subprocess.check_output(['git','-C',str(R),'show',verification_base+':'+migration.relative_to(R).as_posix()]),'Historical migration changed '+migration.name)
    if verification_ci:
        receipt=verification_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Verification exact-source CI missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in verification_owned.items():
                check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Verification CI/current source mismatch '+name)
    def verification_source(name):
        return subprocess.check_output(['git','-C',str(R),'show',verification_base+':'+name]) if name in verification_owned else bank_source(name)
    verification_subset={'taskId': 'CN-VERIFY-001', 'status': 'LOCAL_VERIFIED_CI_PENDING', 'evidencePath': 'PROGRESS.md#verification-implementation', 'fullRequirementVerified': False, 'fullRequirementAcceptance': 'NOT_ACCEPTED', 'actualTestPaths': ['backend/src/test/java/com/cinenest/VerificationIntegrationTest.java', 'backend/src/test/java/com/cinenest/verification/internal/SyntheticEvidenceTest.java', 'frontend/tests/verification.test.mjs', 'frontend/tests/verification-browser.mjs'], 'deferred': 'Real-person/OCR/physical all-guest admission, production privacy/providers/retention and full authorization scenarios remain unverified'}
    verification_subset['status']=verification_status
    def verification_original_payload(payload,path):
        payload=bank_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',verification_base+':knowledge/'+path],text=True))
        kind={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        before={row['id']:row for row in original[kind]}
        check(set(before)=={row['id'] for row in payload[kind]},'Verification changed stable IDs '+path)
        for row in payload[kind]:
            old=before.get(row['id'],{});expected=json.loads(json.dumps(old))
            if kind=='changeSets' and row['id']=='CN-VERIFY-001':
                check(row.get('historicalPreApproval')==old and row.get('dependencies')==['CN-AUTH-001','CN-QUOTE-001'],'Verification prerequisites/history lost')
                check(row.get('workStatus')==('VERIFIED' if verification_ci else 'IN_PROGRESS') and row.get('readiness')==('CLOSED' if verification_ci else 'READY'),'Verification closure exceeds checks')
                check(row.get('authorityAvailable') is True and row.get('blockingDecisionIds')==[] and row.get('resolvedDecisionIds')==['OD-03','OD-05'],'Verification bounded gates inconsistent')
                check(row.get('implementationEvidence')=={'status':verification_status,'evidencePath':'PROGRESS.md#verification-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Verification completion overstated')
                expected=row
            elif kind=='decisions' and row['id'] in {'OD-03','OD-05'}:
                approval=row.get('boundedVerificationApproval');check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()=='c82be8eea5dad1bfdff11e4760a1292794208ecc225cfe4036daa4926a56ca5a','Verification policy changed '+row['id']);expected['boundedVerificationApproval']=approval
            elif kind in {'requirements','tests'} and row['id'].removeprefix('T-') in {'CN-C05','CN-A06','CN-NFR-02'}:
                expected['verificationImplementationEvidence']=verification_subset
            check(row==expected,'Unapproved verification delta '+path+'/'+row['id'])
        return original
    # Explicit synthetic-only CN-QUOTE-001 successor. Validate exact authorized deltas;
    # earlier source/evidence receipts are reconstructed, never rewritten or bypassed.
    quote_base='13a27464a281205f897670cc6593cbddbb1f3ecd'
    quote_evidence=state.get('quotationImplementation',{})
    quote_owned=quote_evidence.get('ownedFiles',{})
    quote_ci=quote_evidence.get('remoteCI')=='SUCCESS'
    quote_status='VERIFIED_BOUNDED_SYNTHETIC_QUOTATIONS' if quote_ci else 'LOCAL_VERIFIED_CI_PENDING'
    check(set(quote_owned)=={'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/app/api/quotes/[quoteId]/route.ts', 'frontend/tests/quotes-browser.mjs', 'backend/src/test/java/com/cinenest/InventoryIntegrationTest.java', 'frontend/app/api/quotes/route.ts', 'backend/src/main/java/com/cinenest/pricing/package-info.java', 'frontend/app/api/quotes/[quoteId]/accept/route.ts', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteRepository.java', 'frontend/tests/quotes.test.mjs', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteController.java', 'frontend/package.json', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteService.java', 'frontend/components/inventory.tsx', 'frontend/lib/room-backend.ts', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/test/java/com/cinenest/pricing/internal/QuoteRulesTest.java', 'frontend/app/api/quotes/[quoteId]/revisions/route.ts', 'backend/src/test/java/com/cinenest/QuoteIntegrationTest.java', 'frontend/components/quotes.tsx', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteDenialAudit.java', 'backend/src/main/java/com/cinenest/booking/QuotationHolds.java', 'backend/src/main/java/com/cinenest/pricing/internal/JdbcQuoteRepository.java', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteRules.java', 'backend/src/main/resources/db/migration/V7__synthetic_quotations.sql', 'frontend/lib/quotes.ts', 'frontend/components/rooms.tsx', 'backend/src/main/java/com/cinenest/booking/internal/InventoryService.java'},'Quotation source inventory differs from approved slice')
    for name,digest in quote_owned.items():
        check((R/name).is_file() and hashlib.sha256(verification_source(name)).hexdigest()==digest,'Quotation source mismatch '+name)
    quote_task=next(c for c in backlog['changeSets'] if c['id']=='CN-QUOTE-001')
    check(hashlib.sha256(json.dumps({k:v for k,v in quote_task.items() if k not in {'workStatus','readiness','implementationEvidence'}},sort_keys=True).encode()).hexdigest()=='022400a4e0cef0e41b1e37f52c6e24e6d8078fe15c0509b71b93612c5a539e03','Quotation scope/owner approval/history changed')
    check(quote_evidence.get('scopeApproval')==quote_task.get('scopeApproval') and quote_evidence.get('status')==quote_status and quote_evidence.get('commercialIssuance')=='DISABLED' and quote_evidence.get('d1')=='NOT_READY','Synthetic boundary or evidence exceeded')
    for key,value in {'backendTests':144,'quoteIntegrationTests':13,'quoteDomainTests':6,'architectureTests':7,'frontendTests':56,'quoteFrontendTests':5,'quoteBrowserGroups':14,'failures':0,'errors':0,'skipped':0}.items():
        check(quote_evidence.get('checks',{}).get(key)==value,'Quotation verification missing '+key)
    check(quote_evidence.get('checks',{}).get('browserWidths')==[320,375,640,768,1024,1280,1536],'Quotation responsive evidence missing')
    for migration in (R/'backend/src/main/resources/db/migration').glob('V[1-6]__*.sql'):
        check(migration.read_bytes()==subprocess.check_output(['git','-C',str(R),'show',quote_base+':'+migration.relative_to(R).as_posix()]),'Historical migration changed '+migration.name)
    check('frontend/next-env.d.ts' not in quote_owned,'Generated owner change cannot enter quotation allowlist')
    if quote_ci:
        receipt=quote_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Quotation exact-source CI missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in quote_owned.items():
                check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Quotation CI/current source mismatch '+name)
    def quote_source(name):
        return subprocess.check_output(['git','-C',str(R),'show',quote_base+':'+name]) if name in quote_owned else verification_source(name)
    quote_subset={'taskId':'CN-QUOTE-001','status':quote_status,'evidencePath':'PROGRESS.md#quotation-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED','actualTestPaths':['backend/src/test/java/com/cinenest/QuoteIntegrationTest.java','backend/src/test/java/com/cinenest/pricing/internal/QuoteRulesTest.java','frontend/tests/quotes.test.mjs','frontend/tests/quotes-browser.mjs'],'deferred':'Real commercial issuance/acceptance, purchased booking/payment/reporting and full authorization plans remain unverified'}
    quote_approvals={'OD-17': '861874fd3d033673e573e36d63cc1142d2e57022759176ab5cdd60136c0acef5', 'OD-20': '17ec3582298e14dbf8cb91d77e250afa6c2e68c50806345a862e56080efe486b', 'OD-22': '778ec8b3911c1e51c9723c00319d4309d34a1349c02f6cda38c550dadf024d07', 'OD-25': 'c3263e488753872c0c966de77d396c8f0bda0996c9480c5a882bfd72feacb594'}
    def quote_original_payload(payload,path):
        payload=verification_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',quote_base+':knowledge/'+path],text=True))
        kind={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        before={row['id']:row for row in original[kind]}
        check(set(before)=={row['id'] for row in payload[kind]},'Quotation changed stable IDs '+path)
        for row in payload[kind]:
            old=before.get(row['id'],{});expected=json.loads(json.dumps(old))
            if kind=='changeSets' and row['id']=='CN-QUOTE-001':
                check(row.get('historicalPreApproval')==old and row.get('dependencies')==['CN-PKG-002','CN-BOOK-001'],'Quotation task prerequisites/history lost')
                check(row.get('workStatus')==('VERIFIED' if quote_ci else 'IN_PROGRESS') and row.get('readiness')==('CLOSED' if quote_ci else 'READY'),'Quotation closure exceeds checks')
                check(row.get('implementationEvidence')=={'status':quote_status,'evidencePath':'PROGRESS.md#quotation-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Quotation completion overstated')
                expected=row
            elif kind=='decisions' and row['id'] in quote_approvals:
                approval=row.get('boundedQuotationApproval');check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()==quote_approvals[row['id']],'Quotation policy changed '+row['id']);expected['boundedQuotationApproval']=approval
            elif kind in {'requirements','tests'} and row['id'].removeprefix('T-') in {'CN-C04','PR-PKG-001','PK-AC07','PK-AC10','PK-AC13','BR-03','BR-04','BR-05','BR-10','CN-NFR-02'}:
                expected['quotationImplementationEvidence']=quote_subset
            check(row==expected,'Unapproved quotation delta '+path+'/'+row['id'])
        return original
    # Owner-approved CN-BOOK-001 successor: exact paths/policy authority, additive migration,
    # bounded evidence and CI-gated closure. Reconstruct only verified deltas for historical receipts.
    inventory_base='53d0e8ea713888d213ff85c157f73924a9b78778'
    inventory_evidence=state.get('inventoryImplementation',{})
    inventory_owned=inventory_evidence.get('ownedFiles',{})
    inventory_ci=inventory_evidence.get('remoteCI')=='SUCCESS'
    inventory_status='VERIFIED_BOUNDED_INVENTORY' if inventory_ci else 'LOCAL_VERIFIED_CI_PENDING'
    check(set(inventory_owned)=={'backend/src/main/java/com/cinenest/booking/internal/InventoryRepository.java', 'backend/src/main/java/com/cinenest/rooms/internal/InventoryConstraintAdapter.java', 'frontend/app/api/admin/venues/[venueId]/inventory/route.ts', 'frontend/package.json', 'frontend/tests/inventory-browser.mjs', 'frontend/app/api/rooms/[roomId]/availability/route.ts', 'backend/src/main/java/com/cinenest/booking/package-info.java', 'backend/src/test/java/com/cinenest/booking/internal/InventoryRulesTest.java', 'backend/src/main/java/com/cinenest/booking/internal/InventoryDenialAudit.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/java/com/cinenest/booking/internal/InventoryController.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/test/java/com/cinenest/PublicationIntegrationTest.java', 'frontend/lib/inventory.ts', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/resources/db/migration/V6__inventory_holds.sql', 'backend/src/main/java/com/cinenest/booking/internal/InventoryRules.java', 'backend/src/main/java/com/cinenest/booking/internal/InventoryWorker.java', 'frontend/app/api/holds/[holdId]/release/route.ts', 'backend/src/main/java/com/cinenest/booking/internal/JdbcInventoryRepository.java', 'backend/src/main/java/com/cinenest/booking/internal/InventoryFailure.java', 'frontend/lib/room-backend.ts', 'frontend/app/api/holds/[holdId]/route.ts', 'frontend/components/publications.tsx', 'backend/src/main/java/com/cinenest/booking/internal/InventoryService.java', 'frontend/app/api/holds/route.ts', 'frontend/components/inventory.tsx', 'frontend/components/rooms.tsx', 'backend/src/test/resources/application.yaml', 'backend/src/test/java/com/cinenest/InventoryIntegrationTest.java', 'frontend/tests/inventory.test.mjs', 'backend/src/main/java/com/cinenest/rooms/InventoryConstraints.java', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/identity/ScopedAuthorization.java', 'frontend/app/admin/inventory/page.tsx'},'Inventory source inventory changed')
    for name,digest in inventory_owned.items():
        check((R/name).is_file() and hashlib.sha256(quote_source(name)).hexdigest()==digest,'Inventory source mismatch '+name)
    inventory_task=next(c for c in backlog['changeSets'] if c['id']=='CN-BOOK-001')
    check(hashlib.sha256(json.dumps(inventory_task.get('scopeApproval'),sort_keys=True).encode()).hexdigest()=='05c9bbdc6645f07ff7d6514fed601f313fc0e2e62b31df9076d3a65ba45d3758','Inventory approval/scope changed')
    check(inventory_evidence.get('scopeApproval')==inventory_task.get('scopeApproval') and inventory_evidence.get('status')==inventory_status and inventory_evidence.get('d1')=='NOT_READY','Inventory evidence exceeds authority/checks')
    checks=inventory_evidence.get('checks',{})
    check(checks.get('backendTests',0)>=124 and checks.get('frontendTests',0)>=50 and checks.get('inventoryIntegrationTests')==17 and checks.get('inventoryDomainTests')==4 and checks.get('inventoryBrowserGroups')==15 and checks.get('failures')==checks.get('skipped')==0,'Inventory checks missing')
    if inventory_ci:
        receipt=inventory_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Inventory CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in inventory_owned.items():check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Inventory CI/current source mismatch '+name)
    def inventory_source(name):
        return subprocess.check_output(['git','-C',str(R),'show',inventory_base+':'+name]) if name in inventory_owned else quote_source(name)
    inventory_subset={'taskId': 'CN-BOOK-001', 'status': 'LOCAL_VERIFICATION_IN_PROGRESS', 'evidencePath': 'PROGRESS.md#inventory-implementation', 'fullRequirementVerified': False, 'fullRequirementAcceptance': 'NOT_ACCEPTED', 'actualTestPaths': ['backend/src/test/java/com/cinenest/InventoryIntegrationTest.java', 'backend/src/test/java/com/cinenest/booking/internal/InventoryRulesTest.java', 'frontend/tests/inventory.test.mjs', 'frontend/tests/inventory-browser.mjs'], 'deferred': 'Commercial quote/confirmation, full administration and actual physical readiness remain at future owning tasks'}
    inventory_subset['status']=inventory_status
    inventory_policy_hashes={'OD-09': 'ca70503988c014376dde45bd4a723f30e85435c8275455894c96060e8717b63c', 'OD-21': 'ca70503988c014376dde45bd4a723f30e85435c8275455894c96060e8717b63c'}
    def inventory_original_payload(payload,path):
        payload=quote_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',inventory_base+':knowledge/'+path],text=True))
        kind={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        before={row['id']:row for row in original[kind]}
        check({row['id'] for row in payload[kind]}==set(before),'Inventory changed stable IDs '+path)
        for row in payload[kind]:
            old=before.get(row['id'],{});expected=json.loads(json.dumps(old))
            if kind=='changeSets' and row['id']=='CN-BOOK-001':
                allowed={'authorityAvailable','readiness','workStatus','blockingDecisionIds','resolvedDecisionIds','verificationDecisionIds','affectedFilesModules','dbApiUiImpact','migrationNeeds','evidencePaths','scopeApproval','historicalPreApproval','implementationEvidence'}
                check({k:v for k,v in row.items() if k not in allowed}=={k:v for k,v in old.items() if k not in allowed},'Inventory changed stable task scope/dependencies')
                check(row.get('historicalPreApproval')==old and row.get('dependencies')==['CN-ROOM-001'],'Inventory preapproval/dependency history lost')
                check(row.get('resolvedDecisionIds')==['OD-09','OD-10','OD-21'] and row.get('blockingDecisionIds')==[] and row.get('authorityAvailable') is True,'Inventory approved gates diverged')
                check(row.get('workStatus')==('VERIFIED' if inventory_ci else 'IN_PROGRESS') and row.get('readiness')==('CLOSED' if inventory_ci else 'READY'),'Inventory closure exceeds checks')
                check(row.get('implementationEvidence')=={'status':inventory_status,'evidencePath':'PROGRESS.md#inventory-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Inventory completion exceeds bounded evidence')
                expected=row
            elif kind=='decisions' and row['id'] in inventory_policy_hashes:
                approval=row.get('boundedInventoryApproval');check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()==inventory_policy_hashes[row['id']],'Inventory policy changed '+row['id']);expected['boundedInventoryApproval']=approval
            elif kind in {'requirements','tests'} and row['id'].removeprefix('T-') in {'CN-C03','CN-A02','BR-01','BR-02','BR-06','BR-07','CN-NFR-02'}:
                expected['inventoryImplementationEvidence']=inventory_subset
            check(row==expected,'Unapproved inventory delta '+path+'/'+row['id'])
        return original
    # Explicit CN-PKG-002 successor: validate every allowed delta before checking historical receipts.
    pub_base='61c2fd5f444a1a3ee2f5231eb34760c02a3d3a14'
    pub_evidence=state.get('publicationImplementation',{})
    pub_ci=pub_evidence.get('remoteCI')=='SUCCESS'
    pub_status='VERIFIED_BOUNDED_INDICATIVE_PUBLICATION' if pub_ci else 'LOCAL_VERIFIED_CI_PENDING'
    pub_owned=pub_evidence.get('ownedFiles',{})
    check(set(pub_owned)=={'backend/src/main/resources/db/migration/V5__indicative_package_publication.sql', 'frontend/components/rooms.tsx', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/publications/[publicationId]/route.ts', 'backend/src/main/java/com/cinenest/pricing/internal/DraftService.java', 'backend/src/main/java/com/cinenest/pricing/internal/PublicationController.java', 'backend/src/test/java/com/cinenest/PricingIntegrationTest.java', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/publication-preview/route.ts', 'backend/src/main/java/com/cinenest/rooms/internal/PackageConstraintAdapter.java', 'frontend/lib/publications.ts', 'frontend/package.json', 'frontend/app/api/venues/[venueId]/packages/[packageId]/route.ts', 'frontend/tests/packages.test.mjs', 'backend/src/test/java/com/cinenest/pricing/internal/PublicationTimeTest.java', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/publications/route.ts', 'frontend/components/publications.tsx', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/components/packages.tsx', 'frontend/tests/publications.test.mjs', 'backend/src/test/java/com/cinenest/PublicationIntegrationTest.java', 'frontend/tests/publications-browser.mjs', 'backend/src/main/java/com/cinenest/pricing/internal/PublicationService.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/java/com/cinenest/rooms/PackageConstraints.java', 'backend/src/main/java/com/cinenest/pricing/internal/PublicationRepository.java', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/publications/[publicationId]/deactivate/route.ts', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/publications/[publicationId]/retry/route.ts', 'frontend/app/api/venues/[venueId]/packages/route.ts', 'backend/src/main/java/com/cinenest/pricing/internal/PublicationWorker.java', 'backend/src/main/java/com/cinenest/pricing/internal/JdbcPublicationRepository.java', 'frontend/lib/room-backend.ts', 'backend/src/test/resources/application.yaml', 'backend/src/main/java/com/cinenest/pricing/internal/JdbcDraftRepository.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java'},'Publication source inventory changed')
    for name,digest in pub_owned.items():
        check((R/name).is_file() and hashlib.sha256(inventory_source(name)).hexdigest()==digest,'Publication source mismatch '+name)
    check(pub_evidence.get('scopeApproval')==next(c for c in backlog['changeSets'] if c['id']=='CN-PKG-002').get('scopeApproval'),'Publication approval receipt diverged')
    check(pub_evidence.get('status')==pub_status and pub_evidence.get('d1')=='NOT_READY','Publication evidence/acceptance conflated')
    for key,value in {'backendTests':102,'publicationIntegrationTests':17,'publicationTimeTests':3,'architectureTests':5,'backendFailures':0,'backendSkipped':0,'frontendTests':44,'publicationFrontendTests':5,'publicationBrowserGroups':17}.items():
        check(pub_evidence.get('checks',{}).get(key)==value,'Publication check receipt missing '+key)
    if pub_ci:
        receipt=pub_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Publication exact-source CI missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in pub_owned.items():
                check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Publication CI/current source mismatch '+name)
    def publication_source(name):
        return subprocess.check_output(['git','-C',str(R),'show',pub_base+':'+name]) if name in pub_owned else inventory_source(name)
    pub_approvals={'OD-17': '32cd464934e0aeb2608eba6aa65e6ebc62a1b56b8c08fd657a0a968a3348ec49', 'OD-18': 'd82400f5ec76a972a158fbbbd3b42c90c71f762eccdeb909168b1a0cf369b57f', 'OD-19': '82335ac70fb465f8e58f5af4b0f0a34b5f3c5635f5bf00257d0653836dbca3e2', 'OD-21': 'bbd8bd56fb92139f525aa72e96b3532c94d057572eaa53ad3e3ffffc03a36624', 'OD-22': 'f438267f4327bf520e11a9788233eb5373b246da4c11c6f4cec90bb4607b44e6'}
    pub_subset={'taskId': 'CN-PKG-002', 'status': 'LOCAL_VERIFIED_CI_PENDING', 'evidencePath': 'PROGRESS.md#publication-implementation', 'fullRequirementVerified': False, 'fullRequirementAcceptance': 'NOT_ACCEPTED', 'deferred': 'Actual purchase, accepted quote honor, reservation/payment/receipt/report preservation require owning future endpoints; no full test-plan pass'}
    pub_subset['status']=pub_status
    def publication_original_payload(payload,path):
        payload=inventory_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',pub_base+':knowledge/'+path],text=True))
        kind={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        before={r['id']:r for r in original[kind]}
        for row in payload[kind]:
            expected=json.loads(json.dumps(before.get(row['id'],{})))
            if kind=='changeSets' and row['id']=='CN-PKG-002':
                stable={k:v for k,v in row.items() if k not in ['workStatus','readiness','implementationEvidence']}
                check(hashlib.sha256(json.dumps(stable,sort_keys=True).encode()).hexdigest()=='b354766bf683338721e0da602a7448802b0f3f646c378c3d340f3ab3f4b4fd1d','Publication task/scope/authority changed')
                check(row.get('workStatus')==('VERIFIED' if pub_ci else 'IN_PROGRESS') and row.get('readiness')==('CLOSED' if pub_ci else 'READY') and row.get('implementationEvidence')=={'status':pub_status,'evidencePath':'PROGRESS.md#publication-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Publication closure exceeds evidence')
                expected=row
            elif kind=='decisions' and row['id'] in pub_approvals:
                approval=row.get('boundedPublicationApproval');check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()==pub_approvals[row['id']],'Publication policy/source changed '+row['id']);expected['boundedPublicationApproval']=approval
            elif kind in {'requirements','tests'} and row['id'].removeprefix('T-') in {'PR-PKG-001','CN-A12','PK-AC04','PK-AC05','PK-AC06','PK-AC09','PK-AC11','CN-NFR-02','CN-C01'}:
                expected['publicationImplementationEvidence']=pub_subset
            check(row==expected,'Unapproved publication delta '+path+'/'+row['id'])
        check(set(before)=={r['id'] for r in payload[kind]},'Publication IDs lost/added '+path)
        return original
    workflow=state.get('localDevelopmentWorkflow',{})
    workflow_owned=workflow.get('ownedFiles',{})
    check(set(workflow_owned)=={'Run.sh','Run-backend.sh','Run-frontend.sh','Run-verify.sh','scripts/test-launchers.py','frontend/next.config.mjs','.github/workflows/foundation.yml'},'Local workflow source inventory changed')
    for name,digest in workflow_owned.items():
        check((R/name).is_file() and hashlib.sha256(mi_source(name)).hexdigest()==digest,'Local workflow source mismatch '+name)
        if name.endswith('.sh'):check((R/name).stat().st_mode & 0o111 != 0,'Local launcher not executable '+name)
    workflow_before=subprocess.check_output(['git','-C',str(R),'show','00164620cb03487e9dd74722e4763d12dce99ec2:.github/workflows/foundation.yml'])
    check((R/'.github/workflows/foundation.yml').read_bytes()==workflow_before.replace(b'      - name: Knowledge and whitespace',b'      - name: Local launcher contracts\n        run: python3 scripts/test-launchers.py\n      - name: Knowledge and whitespace'),'Unrelated CI changes in local workflow')
    if workflow.get('checks',{}).get('ci')=='SUCCESS':
        receipt=workflow.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and isinstance(receipt.get('runId'),int) and re.fullmatch('[0-9a-f]{40}',sha) is not None,'Local workflow CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in workflow_owned.items():
                check(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])).hexdigest()==digest,'Local workflow current/CI source mismatch '+name)
    check(workflow.get('baselineHead')=='00164620cb03487e9dd74722e4763d12dce99ec2' and workflow.get('checks',{}).get('launcherTests')==14,'Local workflow baseline/evidence missing')
    changes = backlog['changeSets']; reqs = coverage['requirements']; tests = tests_doc['tests']
    # Explicit bounded CN-PKG-001 successor; compare its exact authorized delta before historical checks.
    pkg_base='478198fe87fa1e8291b7cf35e5c2339e068b301b'
    pkg_evidence=state.get('packageImplementation',{})
    pkg_ci=pkg_evidence.get('remoteCI')=='SUCCESS'
    pkg_status='VERIFIED_BOUNDED_PACKAGE_DRAFTS' if pkg_ci else 'LOCAL_VERIFIED_CI_PENDING'
    pkg_owned=pkg_evidence.get('ownedFiles',{})
    check(set(pkg_owned)=={'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/draft/route.ts', 'frontend/app/api/admin/venues/[venueId]/package-options/route.ts', 'backend/src/main/resources/db/migration/V4__package_drafts_and_minimum_policy.sql', 'backend/src/main/java/com/cinenest/rooms/internal/MinimumPolicyBootstrap.java', 'frontend/lib/packages.ts', 'backend/src/main/java/com/cinenest/pricing/internal/DraftController.java', 'backend/src/main/java/com/cinenest/rooms/PackageConstraints.java', 'backend/src/main/java/com/cinenest/pricing/package-info.java', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/duplicate/route.ts', 'backend/src/main/java/com/cinenest/rooms/internal/RoomService.java', 'frontend/app/api/admin/venues/[venueId]/packages/route.ts', 'frontend/components/shell.tsx', 'backend/src/test/java/com/cinenest/rooms/internal/MinimumPolicyBootstrapTest.java', 'backend/src/main/java/com/cinenest/pricing/internal/DraftRules.java', 'backend/src/main/java/com/cinenest/rooms/internal/RoomRepository.java', 'frontend/tests/packages.test.mjs', 'backend/src/test/java/com/cinenest/PricingIntegrationTest.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'frontend/components/packages.tsx', 'backend/src/main/java/com/cinenest/pricing/internal/DraftFailure.java', 'backend/src/main/java/com/cinenest/rooms/internal/JdbcRoomRepository.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/java/com/cinenest/pricing/internal/DraftService.java', 'backend/src/test/java/com/cinenest/RoomsIntegrationTest.java', 'backend/src/main/java/com/cinenest/pricing/internal/JdbcDraftRepository.java', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/history/route.ts', 'frontend/app/admin/settings/packages/page.tsx', 'backend/src/main/java/com/cinenest/pricing/internal/DraftRepository.java', 'frontend/package.json', 'backend/src/main/java/com/cinenest/pricing/internal/DeniedDraftAudit.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/tests/packages-browser.mjs', 'frontend/lib/room-backend.ts', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/route.ts', 'frontend/app/api/admin/venues/[venueId]/packages/[packageId]/preview/route.ts', 'backend/src/test/java/com/cinenest/pricing/internal/DraftRulesTest.java', 'backend/src/main/java/com/cinenest/rooms/internal/PackageConstraintAdapter.java'},'Package source inventory changed')
    check(pkg_evidence.get('baselineHead')==pkg_base and pkg_evidence.get('authorizationRequestSha256')=='d10289dd345746c7f4f0bcc4c0a72a97e033fc3d7e307b3c60ba9d0413849569','Package explicit owner authority missing')
    check(pkg_evidence.get('status')==pkg_status,'Package evidence status exceeds checks')
    for name,digest in pkg_owned.items():
        check((R/name).is_file() and hashlib.sha256(publication_source(name)).hexdigest()==digest,'Package source checksum mismatch '+name)
    for key,value in {'backendTests':82,'pricingIntegrationTests':16,'pricingDomainTests':3,'minimumBootstrapTests':2,'architectureTests':5,'backendFailures':0,'backendSkipped':0,'frontendTests':39,'frontendFailures':0,'frontendSkipped':0,'packageBrowserGroups':17,'shellBrowserGroups':19,'roomBrowserGroups':17,'browserWidths':[320,375,640,768,1024,1280,1536]}.items():
        check(pkg_evidence.get('checks',{}).get(key)==value,'Package check evidence missing '+key)
    if pkg_ci:
        receipt=pkg_evidence.get('ciReceipt',{});sha=receipt.get('headSha','')
        check(receipt.get('conclusion')=='success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int),'Package exact-source CI missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in pkg_owned.items():
                source=subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])
                check(hashlib.sha256(source).hexdigest()==digest,'Package current/CI source mismatch '+name)
    def prior_pkg_bytes(name):
        return subprocess.check_output(['git','-C',str(R),'show',pkg_base+':'+name]) if name in pkg_owned else publication_source(name)
    pkg_subset={'taskId':'CN-PKG-001','status':pkg_status,'evidencePath':'PROGRESS.md#package-draft-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED','actualTestPaths':['backend/src/test/java/com/cinenest/PricingIntegrationTest.java','backend/src/test/java/com/cinenest/pricing/internal/DraftRulesTest.java','backend/src/test/java/com/cinenest/rooms/internal/MinimumPolicyBootstrapTest.java','frontend/tests/packages.test.mjs','frontend/tests/packages-browser.mjs']}
    pkg_mapped={'PR-PKG-001','CN-A12','PK-AC01','PK-AC02','PK-AC03','CN-NFR-02'}
    pkg_approvals={'OD-17': '644811faf745e388caa78b113ecaceabd8348c97aaa7b26dfd0994f1c472243e', 'OD-18': '874650e3a8c3ca307c5d9ebe16c41754dfceb165ae8ed47bf862e3929193089b', 'OD-21': '116551cd4333959a6c80e77ab87b19efe3bd805b76c9ccd36aa1eb8429992bb0', 'OD-22': '32eb412751bb23b8178c6d3a666e47a41e98019b8ed4c692a32369a32d590e63'}
    def package_original_payload(payload,path):
        payload=publication_original_payload(payload,path)
        original=json.loads(subprocess.check_output(['git','-C',str(R),'show',pkg_base+':knowledge/'+path],text=True))
        kind={'execution/change-sets.json':'changeSets','execution/requirements.json':'requirements','execution/test-plan.json':'tests','decisions/cinenest-open-decisions.json':'decisions'}[path]
        before={r['id']:r for r in original[kind]}
        for row in payload[kind]:
            old=before.get(row['id']);check(old is not None,'Unapproved package-review ID '+row['id'])
            if old is None:continue
            expected=json.loads(json.dumps(old))
            if kind=='decisions' and row['id'] in pkg_approvals:
                approval=row.get('boundedPackageApproval')
                check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()==pkg_approvals[row['id']],'Package policy/source/deferral changed '+row['id'])
                expected['boundedPackageApproval']=approval
            elif kind in {'requirements','tests'} and row['id'].removeprefix('T-') in pkg_mapped:
                expected['packageImplementationEvidence']=pkg_subset
            elif kind=='changeSets' and row['id']=='CN-PKG-001':
                approval=row.get('scopeApproval')
                check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()=='73fc81c59911f4d1c1f7ebb92f2cca77db7d828f7ee9599526f9ecec2dc92da6','Package scope approval/provenance changed')
                expected.update(scopeApproval=approval,blockingDecisionIds=[],authorityAvailable=True,readiness='CLOSED' if pkg_ci else 'READY',workStatus='VERIFIED' if pkg_ci else 'IN_PROGRESS',evidencePaths=['PROGRESS.md#package-draft-implementation'],implementationEvidence={'status':pkg_status,'evidencePath':'PROGRESS.md#package-draft-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},affectedFilesModules='Pricing module; public Rooms PackageConstraints and controlled minimum policy bootstrap; Identity package capabilities; existing admin shell and explicit transport; tests/knowledge',dbApiUiImpact='Eight Pricing tables plus Rooms minimum_session_policy, eight scoped draft API operations and /admin/settings/packages; no sales',migrationNeeds='V4__package_drafts_and_minimum_policy.sql additive only; V1-V3 unchanged; no automatic production seed or destructive rollback')
            check(row==expected,'Unapproved package-review delta '+path+'/'+row['id'])
        check(set(before)=={r['id'] for r in payload[kind]},'Package review lost/added IDs '+path)
        return original
    for data,path in [(backlog,'execution/change-sets.json'),(coverage,'execution/requirements.json'),(tests_doc,'execution/test-plan.json'),(iot_historical('decisions/cinenest-open-decisions.json'),'decisions/cinenest-open-decisions.json')]:
        package_original_payload(data,path)
    # CN-ROOM-001 is a separately authorized successor; immutable earlier source receipts remain historical.
    room_evidence = state.get('roomImplementation', {})
    room_ci = room_evidence.get('remoteCI') == 'SUCCESS'
    room_status = 'VERIFIED_BOUNDED_ROOM_CATALOG' if room_ci else 'LOCAL_VERIFIED_CI_PENDING'
    room_owned = room_evidence.get('ownedFiles', {})
    check(set(room_owned) == {'backend/src/main/java/com/cinenest/foundation/internal/ClosedHttpBoundary.java', 'backend/src/test/java/com/cinenest/IdentityIntegrationTest.java', 'backend/src/main/java/com/cinenest/rooms/internal/DeniedConfigurationAudit.java', 'backend/src/main/java/com/cinenest/rooms/internal/JdbcRoomRepository.java', 'frontend/lib/rooms.ts', 'frontend/app/globals.css', 'frontend/app/api/catalog/rooms/[roomId]/route.ts', 'backend/src/main/java/com/cinenest/rooms/internal/ConfigurationRules.java', 'frontend/app/api/admin/venues/[venueId]/rooms/[roomId]/route.ts', 'backend/src/main/java/com/cinenest/rooms/internal/RoomController.java', 'frontend/app/admin/venues/page.tsx', 'frontend/lib/room-backend.ts', 'frontend/app/api/admin/venues/route.ts', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/identity/ScopedAuthorization.java', 'backend/src/main/java/com/cinenest/rooms/internal/RoomService.java', 'frontend/app/rooms/[roomId]/page.tsx', 'frontend/app/admin/rooms/page.tsx', 'frontend/app/api/catalog/rooms/route.ts', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'frontend/tests/rooms.test.mjs', 'frontend/components/shell.tsx', 'frontend/app/api/admin/venues/[venueId]/rooms/route.ts', 'frontend/app/api/catalog/venues/route.ts', 'backend/src/test/java/com/cinenest/RoomsIntegrationTest.java', 'frontend/components/rooms.tsx', 'backend/src/main/java/com/cinenest/rooms/package-info.java', 'backend/src/main/java/com/cinenest/rooms/internal/RoomFailure.java', 'frontend/app/api/admin/venues/[venueId]/route.ts', 'frontend/tests/rooms-browser.mjs', 'frontend/package.json', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'frontend/tests/tsx-loader.mjs', 'backend/src/main/java/com/cinenest/rooms/internal/RoomRepository.java', 'backend/src/test/java/com/cinenest/FoundationIntegrationTest.java', 'backend/src/main/resources/db/migration/V3__venue_room_catalog.sql', 'backend/src/test/java/com/cinenest/rooms/internal/ConfigurationRulesTest.java', 'frontend/app/rooms/page.tsx'}, 'Room owned source inventory changed')
    check(room_evidence.get('baselineHead') == '93e36a188dd245891b6f2cf089a616331a53105b'
          and room_evidence.get('authorizationRequestSha256') == '8384de0b4ae43803b6830874132757bcc418d915e94b35ab5ed5b9ec58000f06', 'Room explicit owner authority missing')
    check(room_evidence.get('status') == room_status, 'Room status exceeds evidence')
    for name,digest in room_owned.items():
        check((R/name).is_file() and hashlib.sha256(prior_pkg_bytes(name)).hexdigest() == digest, 'Room source checksum mismatch ' + name)
    room_checks = room_evidence.get('checks', {})
    for key,value in {'backendTests':60,'roomIntegrationTests':19,'domainTests':3,'identityRegressionTests':31,'foundationIntegrationTests':3,'architectureTests':4,'backendFailures':0,'backendSkipped':0,'frontendTests':32,'frontendFailures':0,'frontendSkipped':0,'browserGroups':17,'browserWidths':[320,375,640,768,1024,1280,1536]}.items():
        check(room_checks.get(key) == value, 'Room executed check mismatch ' + key)
    if room_ci:
        receipt=room_evidence.get('ciReceipt', {})
        sha=receipt.get('headSha','')
        check(receipt.get('conclusion') == 'success' and re.fullmatch('[0-9a-f]{40}',sha) is not None and isinstance(receipt.get('runId'),int), 'Room exact-source CI receipt missing')
        if re.fullmatch('[0-9a-f]{40}',sha):
            for name,digest in room_owned.items():
                content=subprocess.check_output(['git','-C',str(R),'show',sha+':'+name])
                check(hashlib.sha256(content).hexdigest()==digest,'Room CI/current source mismatch '+name)
    def historical_source_matches(name,digest):
        path=R/name
        if not path.is_file():return False
        current=hashlib.sha256(prior_pkg_bytes(name)).hexdigest()
        if name not in room_owned:return current==digest
        historical=subprocess.check_output(['git','-C',str(R),'show','93e36a188dd245891b6f2cf089a616331a53105b:'+name])
        return hashlib.sha256(historical).hexdigest()==digest and current==room_owned[name]
    room_subset = {'taskId':'CN-ROOM-001','status':room_status,'evidencePath':'PROGRESS.md#room-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED','actualTestPaths':['backend/src/test/java/com/cinenest/RoomsIntegrationTest.java','backend/src/test/java/com/cinenest/rooms/internal/ConfigurationRulesTest.java','frontend/tests/rooms.test.mjs','frontend/tests/rooms-browser.mjs']}
    for records,key in [(coverage,'requirements'),(tests_doc,'tests')]:
        for row in records[key]:
            if row['id'].removeprefix('T-') in {'CN-C01','CN-A03','CN-A04','CN-NFR-02'}:
                check(row.get('roomImplementationEvidence')==room_subset,'Bounded room/full requirement evidence conflated '+row['id'])
    # Owner-authorized OD-04/10/14 policy resolution and proposed (never READY) room scope.
    # Validate the exact delta against the last published checkpoint, then reconstruct
    # only that authorized delta for the older immutable-payload comparisons below.
    room_ids = {'OD-04','OD-10','OD-14'}
    room_base = '3ad4a17ecfcbb0d52ebc93232f98f6fa0ebc163a'
    room_approval_hashes = {'OD-04': '269a9cf5aa93972f7cf1840eaf5f7ea4ff0eeafaeae4ea22277b5461fbe68938', 'OD-10': '0878e6831223cefff5dfb5880369896f4244aa9111247903b7c06ca25979c920', 'OD-14': '1194db8d4a74bd7d34e57777a259e4f922dab1decf4ba5fb8ee88cf42b3ba2e4'}
    room_proposal_hash = '3affc4f83c8252b4692d756f18521377ab0255b648c1e2576073f1c00960472e'
    def room_historical_payload(payload, path):
        payload=package_original_payload(payload,path)
        historical = json.loads(subprocess.check_output(['git','-C',str(R),'show',room_base + ':knowledge/' + path], text=True))
        kind = {'execution/requirements.json':'requirements','execution/change-sets.json':'changeSets','decisions/cinenest-open-decisions.json':'decisions'}[path]
        originals = {row['id']:row for row in historical[kind]}
        for row in payload[kind]:
            old = originals.get(row['id'])
            if old is None:
                check(False, 'Unapproved room-review ID ' + row['id']); continue
            if kind == 'requirements' and row['id'] in {'CN-C01','CN-A03','CN-A04','CN-NFR-02'}:
                check(row.pop('roomImplementationEvidence',None)==room_subset,'Room subset evidence lost '+row['id'])
            if kind == 'changeSets' and row['id']=='CN-ROOM-001':
                approval=row.pop('scopeApproval',None)
                check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest()=='89e0c557598ed58f16c0d4f712e4389ecea7fa9d6585c537ff67c1e375eea0aa','Room owner-approved exact scope/provenance changed')
                check(row.pop('implementationEvidence',None)=={'status':room_status,'evidencePath':'PROGRESS.md#room-implementation','fullRequirementAcceptance':'NOT_ACCEPTED'},'Room task evidence changed')
                check(row['evidencePaths']==['PROGRESS.md#room-implementation'],'Room evidence reference missing')
                check(row['affectedFilesModules']=='com.cinenest.rooms; Identity ScopedAuthorization; HTTP security allowlists; frontend catalog/configuration routes, explicit transport, tests and existing knowledge records','Room implemented scope changed affectedFilesModules')
                row['affectedFilesModules']=old['affectedFilesModules']
                check(row['dbApiUiImpact']=='Six cinenest_rooms tables; eleven approved API endpoints; /rooms, /rooms/[roomId], /admin/venues, /admin/rooms; no package/booking/payment/device operations','Room implemented scope changed dbApiUiImpact')
                row['dbApiUiImpact']=old['dbApiUiImpact']
                check(row['migrationNeeds']=='V3__venue_room_catalog.sql additive schema/tables/constraints/indexes; existing V1/V2 unchanged; no production data seed or destructive down migration','Room implemented scope changed migrationNeeds')
                row['migrationNeeds']=old['migrationNeeds']
                for field in ['authorityAvailable','readiness','workStatus','evidencePaths']:row[field]=old[field]
            expected = json.loads(json.dumps(old))
            if kind == 'decisions' and row['id'] in room_ids:
                approval = row.get('ownerApproval')
                check(hashlib.sha256(json.dumps(approval,sort_keys=True).encode()).hexdigest() == room_approval_hashes[row['id']], 'Room owner decision/source/exclusions changed ' + row['id'])
                expected.update(historicalContext=old, decisionStatus='APPROVED', owner='Project owner', ownerApproval=approval)
            elif kind in {'requirements','changeSets'}:
                matched = [i for i in old['blockingDecisionIds'] if i in room_ids]
                if matched:
                    expected['blockingDecisionIds'] = [i for i in old['blockingDecisionIds'] if i not in room_ids]
                    expected['resolvedDecisionIds'] = old.get('resolvedDecisionIds',[]) + matched
                    expected['verificationDecisionIds'] = matched
                    if 'unresolvedQuestions' in old:
                        expected['unresolvedQuestions'] = [i for i in old['unresolvedQuestions'] if i not in room_ids]
                if kind == 'changeSets' and row['id'] == 'CN-ROOM-001':
                    proposal = row.get('scopeProposal')
                    check(hashlib.sha256(json.dumps(proposal,sort_keys=True).encode()).hexdigest() == room_proposal_hash, 'Room proposed scope/status changed without approval')
                    expected['scopeProposal'] = proposal
            check(row == expected, 'Unauthorized room-review delta ' + path + '/' + row['id'])
        check(set(originals) == {row['id'] for row in payload[kind]}, 'Room-review coverage changed ' + path)
        return historical
    room_task = next(row for row in changes if row['id'] == 'CN-ROOM-001')
    check(room_task['authorityAvailable'] is True and room_task['readiness'] == ('CLOSED' if room_ci else 'READY') and room_task['workStatus'] == ('VERIFIED' if room_ci else 'IN_PROGRESS')
          and room_task['blockingDecisionIds'] == [] and room_task.get('resolvedDecisionIds') == ['OD-04','OD-10','OD-14'], 'Room decisions conflated with implementation authority')
    check(state.get('roomScopePreparation',{}).get('sessionScope') == 'DECISION_RECORDING_AND_SCOPE_PREPARATION_ONLY'
          and state['roomScopePreparation'].get('authorityAvailable') is False, 'Room session authority broadened')
    room_historical_payload(json.loads(json.dumps(backlog)), 'execution/change-sets.json')
    # Bounded owner-authorized CN-FND-001 successor. Historical payload checks below remain intact.
    foundation_evidence = state.get('foundationImplementation', {})
    check(foundation_evidence.get('authorizationRequestSha256') == 'bb1985f3433d63595f5ff15cb22db92aec17ea5359be55d26de5cca0cddfab4c', 'Foundation owner provenance missing/changed')
    check(foundation_evidence.get('status') == 'VERIFIED_LOCAL_FOUNDATION' and foundation_evidence.get('implementationAuthorization') == 'GRANTED_CN_FND_001_ONLY', 'Foundation authority/evidence missing')
    check(foundation_evidence.get('qualificationStatus') == 'LOCAL_BUILD_TEST_VERIFIED', 'Foundation qualification missing')
    auth_evidence = state.get('authImplementation', {})
    check(auth_evidence.get('authorizationRequestSha256') == 'a16002da5ca579ff5693881b6ffe8ac0b45c6e8a45983440bec01faa599d9ac0', 'Auth implementation owner provenance missing')
    check(auth_evidence.get('status') == 'VERIFIED_BOUNDED_SYNTHETIC_ACCOUNT_READ' and auth_evidence.get('baselineHead') == 'ec355715c1942d060dc58360bded4c1f423a82bc', 'Auth bounded implementation evidence missing')
    auth_owned = auth_evidence.get('ownedFiles', {})
    expected_auth_owned = {
        'backend/pom.xml', 'backend/src/main/java/com/cinenest/foundation/internal/ClosedHttpBoundary.java',
        'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java',
        'backend/src/main/java/com/cinenest/identity/package-info.java',
        'backend/src/main/java/com/cinenest/identity/AccountView.java',
        'backend/src/main/java/com/cinenest/identity/internal/AccountController.java',
        'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java',
        'backend/src/main/java/com/cinenest/identity/internal/IdentityStore.java',
        'backend/src/main/resources/db/migration/V2__identity_accounts_grants_sessions.sql',
        'backend/src/test/java/com/cinenest/IdentityIntegrationTest.java',
        'backend/src/test/java/com/cinenest/SyntheticOidcIssuer.java'}
    check(set(auth_owned) == expected_auth_owned, 'Auth source inventory changed beyond bounded task')
    for name, digest in auth_owned.items():
        check(historical_source_matches(name,digest), 'Auth source checksum mismatch ' + name)
    auth_checks = auth_evidence.get('checks', [])
    check(len(auth_checks) == 1 and auth_checks[0].get('exitCode') == 0 and auth_checks[0].get('backendTests') == 36
          and auth_checks[0].get('identityIntegrationTests') == 31 and auth_checks[0].get('architectureTests') == 3
          and auth_checks[0].get('foundationIntegrationTests') == 2 and auth_checks[0].get('packagedStartup') is True
          and all(auth_checks[0].get(k) == 0 for k in ['failures','errors','skipped']), 'Auth execution evidence incomplete')
    check(set(auth_evidence.get('authorizationScenarioMapping', {})) == {'1','2','3','4','5','11','12','6-10,13-18'}
          and all(v.startswith('PARTIAL:') if k != '6-10,13-18' else v.startswith('NOT_RUN:') for k,v in auth_evidence.get('authorizationScenarioMapping', {}).items()), 'Full future authorization scenarios falsely complete')
    check(auth_evidence.get('p1') == 'PARTIAL_NOT_ACCEPTED' and auth_evidence.get('d1') == 'NOT_READY_NOT_HELD_NOT_ACCEPTED', 'Auth promoted to phase/demo acceptance')
    if auth_evidence.get('remoteCI') == 'SUCCESS':
        check(any(run.get('runId') == 38699715108 and run.get('headSha') == '7fc0f62fa3db7b29f8be289f73b57d01809bf14a'
                  and run.get('conclusion') == 'success' for run in auth_evidence.get('ciRuns', [])), 'Auth source CI receipt missing')
        for name, digest in auth_owned.items():
            source = subprocess.check_output(['git', '-C', str(R), 'show', '7fc0f62fa3db7b29f8be289f73b57d01809bf14a:' + name])
            check(hashlib.sha256(source).hexdigest() == digest, 'Auth CI source does not match current evidence ' + name)
    auth_subset = {'taskId':'CN-AUTH-001','status':'VERIFIED_BOUNDED_SYNTHETIC_ACCOUNT_READ','evidencePath':'PROGRESS.md#auth-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED'}
    ui_evidence = state.get('uiImplementation', {})
    check(ui_evidence.get('authorizationRequestSha256') == 'd5e0c2f2835e0d24cfb8e18252429916ad06b0cc5352c60d5808354553c7c2de'
          and ui_evidence.get('baselineHead') == 'fd19bc5a02dc7ed598aebb06731001c7b98e674d'
          and ui_evidence.get('status') == 'VERIFIED_BOUNDED_SHELL_AND_PRIMITIVES', 'UI bounded owner implementation provenance missing')
    ui_owned = ui_evidence.get('ownedFiles', {})
    expected_ui_owned = {'frontend/package.json', 'frontend/app/globals.css', 'frontend/lib/backend.ts', 'frontend/tests/tsx-loader.mjs', 'frontend/app/api/session/logout/route.ts', 'frontend/app/layout.tsx', 'frontend/app/page.tsx', 'frontend/app/api/session/csrf/route.ts', 'frontend/components/examples.tsx', 'frontend/app/account/page.tsx', 'frontend/app/admin/page.tsx', 'frontend/scripts/generate-breakpoints.mjs', 'frontend/components/session-panel.tsx', 'frontend/lib/session.ts', 'frontend/tests/ui-browser.mjs', 'frontend/tests/ui.test.mjs', 'frontend/tests/transport.test.mjs', 'frontend/app/admin/account/page.tsx', 'frontend/components/shell.tsx', 'frontend/components/primitives.tsx', 'frontend/tests/foundation.test.mjs', 'frontend/styles/breakpoints.css', 'frontend/app/admin/components/page.tsx', 'frontend/app/api/accounts/me/route.ts'}
    check(set(ui_owned) == expected_ui_owned, 'UI bounded source inventory changed')
    for name, digest in ui_owned.items():
        check(historical_source_matches(name,digest), 'UI source checksum mismatch ' + name)
    ui_checks = ui_evidence.get('checks', {})
    check(ui_checks.get('frontendTests', {}).get('total') == 23 and ui_checks.get('frontendTests', {}).get('failures') == 0
          and ui_checks.get('frontendTests', {}).get('skipped') == 0 and ui_checks.get('backendRegression', {}).get('total') == 36
          and ui_checks.get('backendRegression', {}).get('failures') == 0 and ui_checks.get('browser', {}).get('checkGroups') == 19
          and ui_checks.get('browser', {}).get('widths') == [320,375,640,768,1024,1280,1536], 'UI executed verification evidence incomplete')
    check(ui_evidence.get('p1') == 'PARTIAL_NOT_ACCEPTED' and ui_evidence.get('d1') == 'NOT_READY', 'UI promoted to phase/demo acceptance')
    ui_subset = {'taskId':'CN-UI-001','status':'VERIFIED_BOUNDED_SHELL_AND_PRIMITIVES','evidencePath':'PROGRESS.md#ui-implementation','fullRequirementVerified':False,'fullRequirementAcceptance':'NOT_ACCEPTED'}
    ui_ci = ui_evidence.get('remoteCI') == 'SUCCESS'
    if ui_ci:
        receipt = ui_evidence.get('ciReceipt', {})
        check(receipt.get('conclusion') == 'success' and isinstance(receipt.get('runId'), int)
              and re.fullmatch(r'[0-9a-f]{40}', receipt.get('headSha', '')) is not None, 'UI exact-source CI receipt missing')
        if re.fullmatch(r'[0-9a-f]{40}', receipt.get('headSha', '')):
            for name, digest in ui_owned.items():
                content = subprocess.check_output(['git', '-C', str(R), 'show', receipt['headSha'] + ':' + name])
                check(hashlib.sha256(content).hexdigest() == digest, 'UI CI source mismatch ' + name)
    # Scripts may extend verification; dependencies, engines and all other metadata remain identical.
    old_package = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', 'fd19bc5a02dc7ed598aebb06731001c7b98e674d:frontend/package.json'], text=True))
    current_package = json.loads((R / 'frontend/package.json').read_text())
    old_package['scripts'].pop('test'); current_package['scripts'].pop('test')
    check(old_package == current_package, 'Unapproved frontend dependency/toolchain/script change')
    owned = foundation_evidence.get('ownedFiles', {})
    required_foundation = {'backend/pom.xml', 'backend/mvnw', 'frontend/package-lock.json', 'frontend/package.json', 'scripts/verify-foundation.py', '.github/workflows/foundation.yml'}
    check(required_foundation <= set(owned) and len(owned) == 29, 'Foundation source/build/test inventory missing')
    for name, digest in owned.items():
        path = R / name
        # Historical foundation evidence stays immutable. Only explicitly owned auth successors
        # may differ now; both their current hashes above and original source hashes are checked.
        data = subprocess.check_output(['git', '-C', str(R), 'show', 'aa2bb4e13a9a7bf4e495c856a6f2c8e199c56d15:' + name]) if name in auth_owned or name in ui_owned or name in room_owned or name in {'frontend/next.config.mjs','.github/workflows/foundation.yml'} else inventory_source(name) if path.is_file() else b''
        check(path.is_file() and hashlib.sha256(data).hexdigest() == digest, 'Foundation source checksum mismatch ' + name)
    results = foundation_evidence.get('checks', [])
    check(len(results) == 7 and all(c.get('exitCode') == 0 for c in results), 'Foundation verification failure/missing checks')
    check(bool(results) and results[0].get('backendTests') == 4 and results[0].get('packagedStartup') is True and foundation_evidence.get('frontendTests') == 1, 'Foundation executed checks missing')
    check(foundation_evidence.get('remoteCI') == 'SUCCESS' and any(run.get('runId') == 38689239097 and run.get('headSha') == 'aa2bb4e13a9a7bf4e495c856a6f2c8e199c56d15' and run.get('conclusion') == 'success' for run in foundation_evidence.get('ciRuns', [])), 'Verified source-commit CI receipt lost/promoted')
    foundation_subset = {'taskId':'CN-FND-001','evidencePath':'PROGRESS.md#foundation-implementation','status':'FOUNDATION_SUBSET_ONLY','fullRequirementVerified':False}

    cids = [x['id'] for x in changes]; rids = [x['id'] for x in reqs]; tids = [x['id'] for x in tests]
    for label, ids in [('changes', cids), ('requirements', rids), ('tests', tids)]:
        check(len(ids) == len(set(ids)), f'Duplicate {label} IDs')
    decisions = {d['id'] for d in iot_historical('decisions/cinenest-open-decisions.json')['decisions']}
    sources = {s['id'] for s in json.loads((K / 'sources/cinenest-source-register.json').read_text())['sources']}
    check(backlog['futurePlanApproval'] == 'NOT_APPROVED', 'Future plan silently approved')
    for c in changes:
        for field in ['id', 'phase', 'priority', 'objective', 'requirementIds', 'inScope', 'outOfScope',
                      'dependencies', 'blockingDecisionIds', 'affectedFilesModules', 'dbApiUiImpact',
                      'securityIotRisks', 'migrationNeeds', 'plannedChecks', 'rollback', 'definitionOfDone',
                      'requiredApproval', 'authorityAvailable', 'readiness', 'workStatus']:
            check(field in c and c[field] is not None, f'Incomplete change {c["id"]}/{field}')
        check(set(c['requirementIds']) <= set(rids), f'Unknown requirement {c["id"]}')
        check(set(c['dependencies']) <= set(cids), f'Unknown dependency {c["id"]}')
        check(c['id'] not in c['dependencies'], f'Self dependency {c["id"]}')
        check(set(c['blockingDecisionIds']) <= decisions, f'Unknown blocking decision {c["id"]}')
        if c['id'] == 'CN-DEC-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED', 'Decision brief status unsupported')
            check(c.get('authorizationSource') == 'S11' and c.get('approvalStatus') == 'OWNER_APPROVED_INITIAL_DIRECTION' and c.get('implementationAuthorization') == 'NOT_GRANTED', 'Decision brief promoted to stack/code authority')
            check(c.get('pendingDecisionIds') == ['OD-01'] and c['blockingDecisionIds'] == [], 'Brief/selection gates conflated')
        elif c['id'] == 'CN-KB-CONSOLIDATE-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED' and c.get('authorizationSource') == 'S12', 'Consolidation authority/status missing')
            check(c.get('deletionApproval') == 'APPROVED_EXACT_ALLOWLIST' and c.get('removalAuthorizationSource') == 'S14' and c.get('removalStatus') == 'VERIFIED', 'Exact removal approval/status missing')
        elif c['id'] == 'CN-FND-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED', 'Foundation task/evidence diverge')
            check(c['dependencies'] == ['CN-DEC-001'] and c['blockingDecisionIds'] == ['OD-01'], 'Foundation dependency/policy edges changed')
            check(c.get('evidencePaths') == ['PROGRESS.md#foundation-implementation'], 'Foundation completion evidence missing')
        elif c['id'] == 'CN-AUTH-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED', 'Auth bounded task completion/evidence diverge')
            check(c['dependencies'] == ['CN-FND-001'] and c['blockingDecisionIds'] == [] and c.get('resolvedDecisionIds') == ['OD-12'], 'Auth prerequisite/scope dependency altered')
            check(c.get('evidencePaths') == ['PROGRESS.md#auth-implementation'] and c.get('implementationEvidence') == auth_subset, 'Auth scoped implementation evidence missing')
        elif c['id'] == 'CN-UI-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED', 'UI bounded completion/evidence diverge')
            check(c['dependencies'] == ['CN-FND-001'] and c['blockingDecisionIds'] == [] and c.get('resolvedDecisionIds') == ['OD-11'], 'UI dependency/resolution changed')
            check(c.get('evidencePaths') == ['PROGRESS.md#ui-implementation'] and c.get('implementationEvidence') == ui_subset and c.get('implementationAuthorization') == 'GRANTED_BOUNDED_CN_UI_001_AFTER_READINESS', 'UI implementation evidence invented or scope missing')
        elif c['id'] == 'CN-ROOM-001':
            check(c['authorityAvailable'] is True and c['readiness']==('CLOSED' if room_ci else 'READY') and c['workStatus']==('VERIFIED' if room_ci else 'IN_PROGRESS'), 'Room state exceeds scoped evidence')
        elif c['id']=='CN-PKG-002':
            check(c['authorityAvailable'] is True and c['readiness']==('CLOSED' if pub_ci else 'READY') and c['workStatus']==('VERIFIED' if pub_ci else 'IN_PROGRESS'),'Publication task state exceeds approval/checks')
        elif c['id']=='CN-PKG-001':
            check(c['authorityAvailable'] is True and c['readiness']==('CLOSED' if pkg_ci else 'READY') and c['workStatus']==('VERIFIED' if pkg_ci else 'IN_PROGRESS'),'Package state exceeds scoped approval/checks')
        elif c['id']=='CN-BOOK-001':
            check(c['authorityAvailable'] is True and c['workStatus']==('VERIFIED' if inventory_ci else 'IN_PROGRESS') and c['readiness']==('CLOSED' if inventory_ci else 'READY'),'Inventory task state unsupported')
        elif c['id']=='CN-VERIFY-001':
            check(c['authorityAvailable'] is True and c['workStatus']==('VERIFIED' if verification_ci else 'IN_PROGRESS') and c['readiness']==('CLOSED' if verification_ci else 'READY'),'Verification state exceeds scoped evidence')
        elif c['id']=='CN-QUOTE-001':
            check(c['authorityAvailable'] is True and c['workStatus']==('VERIFIED' if quote_ci else 'IN_PROGRESS') and c['readiness']==('CLOSED' if quote_ci else 'READY'),'Quotation state exceeds scoped evidence')
        elif c['id']=='CN-PAY-BANK-001':
            check(c['authorityAvailable'] is True and c['workStatus']==('VERIFIED' if bank_ci else 'IN_PROGRESS') and c['readiness']==('CLOSED' if bank_ci else 'READY'),'Bank task state unsupported')
        elif c['id'] != 'CN-AGENT-SETUP-001':
            check(c['authorityAvailable'] is False and c['readiness'] == c['workStatus'] == 'BLOCKED', f'Unauthorized future work ready {c["id"]}')
    visiting = set(); done = set(); cmap = {c['id']: c for c in changes}
    def visit(id):
        if id in visiting:
            check(False, f'Dependency cycle at {id}')
            return
        if id in done:
            return
        visiting.add(id)
        for dep in cmap[id]['dependencies']:
            if dep in cmap:
                visit(dep)
        visiting.remove(id); done.add(id)
    for id in cids:
        visit(id)
    check({c['phase'] for c in changes if c['phase'] > 0} == set(range(1, 13)), 'Delivery workstream omitted')
    feature_ids = {f['id'] for f in json.loads((K / 'product/cinenest-feature-catalog.json').read_text())['features']}
    check(feature_ids <= set(rids), 'Catalog requirement omitted')
    check({'PR-UI-001', 'PR-PKG-001', 'PR-BANK-001', 'CN-AGENT-WORKFLOW-001'} <= set(rids), 'Approved core/workflow omitted')
    for r in reqs:
        for field in ['sourceIds', 'approvalEvidence', 'priority', 'scope', 'acceptanceCriteria',
                      'moduleOwnership', 'uiApiOwnership', 'changeSetIds', 'dependencies', 'blockingDecisionIds',
                      'securityPrivacyIotImpact', 'testIds', 'plannedTestStatus', 'workStatus', 'implementationStatus',
                      'codeEvidence', 'verificationEvidence', 'acceptanceStatus', 'unresolvedQuestions']:
            check(field in r, f'Coverage field {r["id"]}/{field}')
        check(set(r['sourceIds']) <= sources and bool(r['sourceIds']), f'Unknown/missing source {r["id"]}')
        check(set(r['changeSetIds']) <= set(cids) and bool(r['changeSetIds']), f'Unmapped requirement {r["id"]}')
        check(set(r['testIds']) <= set(tids) and bool(r['testIds']), f'Acceptance test plan missing {r["id"]}')
        check(set(r['blockingDecisionIds']) <= decisions, f'Unknown requirement decision {r["id"]}')
        if r['id'] != 'CN-AGENT-WORKFLOW-001':
            check(r['implementationStatus'] == 'NOT_VERIFIED' and r['workStatus'] == 'BLOCKED', f'Unverified product claimed implemented {r["id"]}')
            check(r['codeEvidence'] == [] and r['acceptanceStatus'] == 'NOT_ACCEPTED', f'Unsupported code/acceptance {r["id"]}')
    for t in tests:
        check(set(t['requirementIds']) <= set(rids) and set(t['owningChangeSetIds']) <= set(cids), f'Orphan test {t["id"]}')
        if t['id'] != 'T-CN-AGENT-WORKFLOW-001':
            check(t['status'] == 'NOT_RUN' and t['actualTestPaths'] == [] and t['evidencePaths'] == [], f'Future test falsely run {t["id"]}')
    expected_paths = set(subprocess.check_output(['git', '-C', str(R), 'ls-tree', '-r', '--name-only', ledger['reviewedRevision']], text=True).splitlines())
    entries = ledger['baselineFiles']
    check({x['path'] for x in entries} == expected_paths and len(entries) == len(expected_paths), 'Source inventory incomplete/duplicated')
    for x in entries:
        data = subprocess.check_output(['git', '-C', str(R), 'show', ledger['reviewedRevision']+':'+x['path']])
        check(hashlib.sha256(data).hexdigest() == x['sha256'], f'Review hash mismatch {x["path"]}')
        if x['logicalLines'] is not None:
            covered = {n for start, end in x['rangesRead'] for n in range(start, end+1)}
            check(covered == set(range(1, len(data.decode().splitlines())+1)) and x['readStatus'] == 'FULLY_READ', f'Unread source range {x["path"]}')
        else:
            check(x['readStatus'] == 'VISUALLY_REVIEWED', f'Visual unreviewed {x["path"]}')
    check(ledger['missingSources'] == ['S2', 'S3', 'S4'], 'Missing PDFs hidden')
    check(state['futurePlanApproval'] == 'NOT_APPROVED' and state['nextReadyChangeSet'] is None and [c['id'] for c in changes if c['readiness'] == 'READY'] == ([] if bank_ci else ['CN-PAY-BANK-001']), 'Unsupported READY plan')
    check(state['taskId'] == 'CN-PAY-BANK-001' and state['nextRecommendedChangeSet'] == 'CN-PAY-BANK-002' and state.get('nextReadyChangeSet') is None, 'Unexpected next checkpoint')
    check(state['workStatus'] == ('VERIFIED' if bank_ci else 'IN_PROGRESS') and cmap['CN-KB-CONSOLIDATE-001']['workStatus'] == 'VERIFIED' and cmap['CN-FND-001']['workStatus'] == 'VERIFIED' and state.get('authorizationUpdate', {}).get('status') == 'VERIFIED_DOCUMENTATION_ONLY', 'Documentation and implementation status conflated')
    check(state.get('stackApproval') == 'OWNER_APPROVED_INITIAL_DIRECTION' and state.get('implementationAuthorization') == 'GRANTED_SYNTHETIC_BANK_REQUEST_ONLY', 'Unsupported stack/implementation approval')
    od = iot_historical('decisions/cinenest-open-decisions.json')['decisions']
    check(next(x for x in od if x['id'] == 'OD-01')['decisionStatus'] == 'OPEN_DECISION', 'OD-01 silently accepted')
    brief = K / 'ARCHITECTURE.md'
    check(brief.is_file() and 'OWNER_APPROVED_INITIAL_DIRECTION / QUALIFICATION_PENDING' in brief.read_text(), 'Missing proposed decision brief')
    check((K / 'sources/CN-DEC-001-request.txt').is_file() and 'S11' in sources, 'Missing decision scope authority')
    check(state['hostAutomaticInstructionLoading'] == 'NOT_VERIFIED' and state['nativeAgentRegistration'] == 'NOT_PERFORMED', 'Host/native claim unsupported')
    manifest = json.loads((K / 'manifest.json').read_text())
    for item in manifest.get('externalInstructionFiles', []):
        check((R / item['path']).is_file() and hashlib.sha256((R / item['path']).read_bytes()).hexdigest() == item['sha256'], f'Instruction hash mismatch {item["path"]}')
    check({x['path'] for x in manifest.get('externalInstructionFiles', [])} == {'AGENTS.md'}, 'External instruction inventory incomplete')
    baseline = manifest['consolidation']['baselineRevision']
    for path, field in [('execution/requirements.json', 'requirements'), ('execution/test-plan.json', 'tests'), ('execution/change-sets.json', 'changeSets'), ('decisions/cinenest-open-decisions.json', 'decisions')]:
        prior = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/' + path], text=True))
        current = ledger_original_payload(am_original(iot_historical(path),path),path)
        before = {x['id']: x for x in prior[field]}; after = {x['id']: x for x in current[field]}
        check(set(before) <= set(after), 'Baseline IDs dropped ' + path)
        for id, old in before.items():
            for key in ['decisionStatus', 'implementationStatus', 'acceptanceStatus', 'plannedTestStatus', 'workStatus', 'status', 'authorityAvailable', 'readiness', 'approvalStatus', 'implementationAuthorization']:
                if key in old:
                    expected_status = 'APPROVED' if id in {'OD-04','OD-10','OD-11','OD-12','OD-14'} and key == 'decisionStatus' else 'OWNER_APPROVED_INITIAL_DIRECTION' if id == 'CN-DEC-001' and key == 'approvalStatus' else old[key]
                    if id=='CN-PAY-BANK-001':
                        expected_status={'workStatus':'VERIFIED' if bank_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if bank_ci else 'READY'}.get(key,expected_status)
                    if id=='CN-VERIFY-001':
                        expected_status={'workStatus':'VERIFIED' if verification_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if verification_ci else 'READY'}.get(key,expected_status)
                    if id=='CN-QUOTE-001':
                        expected_status={'workStatus':'VERIFIED' if quote_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if quote_ci else 'READY'}.get(key,expected_status)
                    if id=='CN-BOOK-001':
                        expected_status={'workStatus':'VERIFIED' if inventory_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if inventory_ci else 'READY'}.get(key,expected_status)
                    if id=='CN-PKG-002':
                        expected_status={'workStatus':'VERIFIED' if pub_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if pub_ci else 'READY'}.get(key,expected_status)
                    if id=='CN-PKG-001':
                        expected_status={'workStatus':'VERIFIED' if pkg_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if pkg_ci else 'READY'}.get(key,expected_status)
                    if id == 'CN-ROOM-001':
                        expected_status={'workStatus':'VERIFIED' if room_ci else 'IN_PROGRESS','authorityAvailable':True,'readiness':'CLOSED' if room_ci else 'READY'}.get(key,expected_status)
                    if id == 'CN-UI-001':
                        expected_status = {'workStatus':'VERIFIED','authorityAvailable':True,'readiness':'CLOSED'}.get(key, expected_status)
                    if id == 'CN-AUTH-001':
                        expected_status = {'workStatus':'VERIFIED','authorityAvailable':True,'readiness':'CLOSED'}.get(key, expected_status)
                    check(after.get(id, {}).get(key) == ({'workStatus':'VERIFIED','authorityAvailable':True,'readiness':'CLOSED'}.get(key, expected_status) if id == 'CN-FND-001' else expected_status), 'Baseline status changed beyond scoped foundation authority ' + id + '/' + key)
    canonical_map = {e['sourcePath'].removeprefix('knowledge/'): e['canonicalPath'].removeprefix('knowledge/') + '#' + e['anchor'] for e in manifest['consolidation']['documents']}
    def migrated(value):
        if isinstance(value, dict):
            return {key: migrated(item) for key, item in value.items()}
        if isinstance(value, list):
            return [migrated(item) for item in value]
        return canonical_map.get(value, value) if isinstance(value, str) else value
    od12_requirement_ids = {'CN-C02','CN-A06','CN-A10','CN-A11','CN-NFR-02'}
    od12_task_ids = {'CN-AUTH-001','CN-ADMIN-001'}
    # Compare full requirement/test/decision payloads, not just counts/statuses.
    for path in ['execution/requirements.json', 'execution/test-plan.json', 'decisions/cinenest-open-decisions.json']:
        expected = migrated(json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/' + path], text=True)))
        if path == 'execution/requirements.json':
            workflow = expected['requirements'][0]
            workflow['sourceIds'].append('S12'); workflow['changeSetIds'].append('CN-KB-CONSOLIDATE-001'); workflow['verificationEvidence'].append('PROGRESS.md#current-verification')
        if path == 'execution/test-plan.json':
            workflow = expected['tests'][0]
            workflow['owningChangeSetIds'].append('CN-KB-CONSOLIDATE-001'); workflow['evidencePaths'].append('PROGRESS.md#current-verification')
        current_payload = am_original(iot_historical(path),path)
        if path=='execution/test-plan.json':current_payload=package_original_payload(current_payload,path)
        if path != 'execution/test-plan.json':
            current_payload = room_historical_payload(current_payload, path)
        if path in {'execution/requirements.json','execution/test-plan.json'}:
            kind = 'requirements' if path.endswith('requirements.json') else 'tests'
            if kind=='tests':
                for row in current_payload[kind]:
                    if row['id'].removeprefix('T-') in {'CN-C01','CN-A03','CN-A04','CN-NFR-02'}:
                        check(row.pop('roomImplementationEvidence',None)==room_subset,'Room planned/executed subset missing '+row['id'])
            targets = {'CN-C02','CN-A06','CN-NFR-02'}
            if kind == 'tests':
                targets = {'T-' + value for value in targets}
            for row in current_payload[kind]:
                if row['id'].removeprefix('T-') in {'PR-UI-001','CN-A04','CN-NFR-01'}:
                    check(row.pop('uiImplementationEvidence', None) == ui_subset, 'UI subset/full feature evidence lost ' + row['id'])
                if row['id'] in targets:
                    check(row.pop('authImplementationEvidence', None) == auth_subset, 'Bounded auth evidence missing/overstated ' + row['id'])
                    check(row.pop('boundedAuthPolicyApproval', None) == {'taskId':'CN-AUTH-001','canonicalSpecification':'ARCHITECTURE.md#auth-bounded-policy','readiness':'READY','implementationStatus':'NOT_VERIFIED','tests':'NOT_RUN','fullRequirementAcceptance':'NOT_ACCEPTED'}, 'Auth approval conflated with test/requirement completion ' + row['id'])

        if path in {'execution/requirements.json', 'execution/test-plan.json'}:
            key = 'requirements' if path.endswith('requirements.json') else 'tests'
            mapped = {'PR-UI-001','PR-PKG-001','PR-BANK-001','CN-NFR-02','CN-NFR-03'}
            if key == 'tests':
                mapped = {'T-' + value for value in mapped}
            for row in current_payload[key]:
                if row['id'] in mapped:
                    check(row.pop('foundationEvidence', None) == foundation_subset, 'Foundation subset/full feature distinction lost ' + row['id'])

        if path == 'execution/requirements.json':
            for row in current_payload['requirements']:
                if row['id'] in od12_requirement_ids:
                    original = next(x for x in expected['requirements'] if x['id'] == row['id'])
                    check(row.pop('resolvedDecisionIds', None) == ['OD-12'], 'Resolved venue decision trace missing ' + row['id'])
                    for field in ['blockingDecisionIds','unresolvedQuestions']:
                        check(row[field] == [value for value in original[field] if value != 'OD-12'], 'Unrelated requirement blocker changed ' + row['id'])
                        row[field] = original[field]
        if path == 'execution/requirements.json':
            for row in current_payload['requirements']:
                if row['id'] in {'PR-UI-001','CN-A04','CN-NFR-01'}:
                    original = next(x for x in expected['requirements'] if x['id'] == row['id'])
                    check(row.pop('resolvedDecisionIds', None) == ['OD-11'], 'UI requirement resolution trace missing ' + row['id'])
                    for field in ['blockingDecisionIds','unresolvedQuestions']:
                        check(row[field] == [v for v in original[field] if v != 'OD-11'], 'Unrelated UI requirement gate changed ' + row['id'])
                        row[field] = original[field]
        # S15 adds only the bounded authorization extension; preserve every prior field/criterion.
        if path == 'execution/requirements.json':
            row = next(x for x in current_payload['requirements'] if x['id'] == 'CN-NFR-02')
            added = {key: row.pop(key, None) for key in ['authorizationDirection', 'authorizationAcceptanceScenarios']}
            check(hashlib.sha256(json.dumps(added, sort_keys=True).encode()).hexdigest() == '453d05d5a48e099a8c8db5c85ed26097ada87be67b77702ed112513599ed209e', 'Authorized requirement extension lost/changed')
            original_row = next(x for x in expected['requirements'] if x['id'] == 'CN-NFR-02')
            check(row['sourceIds'] == original_row['sourceIds'] + ['S15'], 'S15 requirement provenance changed')
            row['sourceIds'] = original_row['sourceIds']
        if path == 'execution/test-plan.json':
            row = next(x for x in current_payload['tests'] if x['id'] == 'T-CN-NFR-02')
            added = {key: row.pop(key, None) for key in ['authorizationScenarioSource', 'authorizationEvidenceNeeded']}
            check(hashlib.sha256(json.dumps(added, sort_keys=True).encode()).hexdigest() == '3c59995ce6b04b02dc2b681665eb941e3782744c0926ae741df8cde53e7ef5b8', 'Authorized test extension lost/changed')
        if path == 'decisions/cinenest-open-decisions.json':
            ui_decision = next(x for x in current_payload['decisions'] if x['id'] == 'OD-11')
            original_ui_decision = next(x for x in expected['decisions'] if x['id'] == 'OD-11')
            check(ui_decision.get('historicalContext') == original_ui_decision, 'OD-11 original question/options/history lost')
            check(ui_decision.get('decisionStatus') == 'APPROVED' and ui_decision.get('owner') == 'Project owner'
                  and ui_decision.get('implementationStatus') == 'NOT_VERIFIED' and ui_decision.get('launchBlocker') == 'RESOLVED_CURRENT_TOKENS_APPROVED', 'OD-11 decision promoted to implementation')
            owner_approval = ui_decision.get('ownerApproval')
            check(hashlib.sha256(json.dumps(owner_approval, sort_keys=True).encode()).hexdigest() == '54b3470f9bd1f950f2140dbe5864668df6e12c767ab07d8dccbb91ad2ec49396', 'OD-11 exact owner scope/exclusions/future review changed')
            restored = dict(ui_decision)
            for field in ['historicalContext','ownerApproval']:
                restored.pop(field, None)
            for field in ['decisionStatus','owner','launchBlocker']:
                restored[field] = original_ui_decision[field]
            check(restored == original_ui_decision, 'OD-11 unrelated fields changed')
            current_payload['decisions'][current_payload['decisions'].index(ui_decision)] = original_ui_decision
        if path == 'decisions/cinenest-open-decisions.json':
            current_od = next(x for x in current_payload['decisions'] if x['id'] == 'OD-01')
            bounded = current_od.pop('boundedAuthPolicyApproval', None)
            check(hashlib.sha256(json.dumps(bounded, sort_keys=True).encode()).hexdigest() == 'a37aa83d3e180d8efb8e5fb333510551596c2b13fd5fca07346db88451c98332', 'Exact bounded auth owner policy/source/deferrals changed')
            qualification = current_od.pop('foundationQualification', None)
            check(qualification == {'status':'LOCAL_BUILD_TEST_VERIFIED','taskId':'CN-FND-001','evidencePath':'PROGRESS.md#foundation-implementation','remainingScope':'Broader cloud/providers/libraries and auth-specific session/revocation decisions remain open; no automatic CN-AUTH-001 readiness'}, 'Foundation qualification promoted beyond scope')
            check(hashlib.sha256(json.dumps(current_od, sort_keys=True).encode()).hexdigest() == '3f3f05eb387eb006672f8e38a559af25fc5c30d69ff0bc7a9221a0eda02e495d', 'S16 bounded OD-01 approval/status/conditions changed')
            old_od = next(x for x in expected['decisions'] if x['id'] == 'OD-01')
            current_payload['decisions'][current_payload['decisions'].index(current_od)] = old_od
        if path == 'decisions/cinenest-open-decisions.json':
            current_od12 = next(x for x in current_payload['decisions'] if x['id'] == 'OD-12')
            check(hashlib.sha256(json.dumps(current_od12, sort_keys=True).encode()).hexdigest() == 'f0c28928c78d85f729be6ee8d57a904889d524210c37af8b798978b0adef9060', 'OD-12 exact owner decision/provenance/exclusions changed')
            original_od12 = next(x for x in expected['decisions'] if x['id'] == 'OD-12')
            check(current_od12.get('historicalContext') == original_od12, 'OD-12 historical context lost')
            current_payload['decisions'][current_payload['decisions'].index(current_od12)] = original_od12
        if path == 'decisions/cinenest-open-decisions.json':
            for row in current_payload['decisions']:
                if row['id'] in {'OD-03','OD-05','OD-18','OD-28'}:
                    check(row.pop('boundedAuthApplicability', None) == {'taskId':'CN-AUTH-001','approvalReference':'ARCHITECTURE.md#auth-bounded-policy','policyGateForBoundedUnit':'SATISFIED_OR_EXCLUDED_BY_OWNER_SCOPE','fullDecisionStatus':'OPEN_DECISION','implementationStatus':'NOT_VERIFIED','scope':'Synthetic account/session and minimum scoped reads only; document/admission, production data/provider, finance/independent-review and provisioning policies remain deferred at their owning feature gates'}, 'Unrelated feature decision closed/promoted ' + row['id'])
        check(expected == current_payload, 'Baseline requirement/test/decision content changed beyond authorized mapping ' + path)
    old_backlog = migrated(json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/execution/change-sets.json'], text=True)))
    prior_changes = room_historical_payload(json.loads(json.dumps(backlog)), 'execution/change-sets.json')['changeSets'][:-1]
    ui_task = next(x for x in prior_changes if x['id'] == 'CN-UI-001')
    check(ui_task.pop('implementationEvidence', None) == ui_subset and ui_task['evidencePaths'] == ['PROGRESS.md#ui-implementation'], 'UI scoped implementation evidence missing')
    ui_task['evidencePaths'] = []
    ui_approval = ui_task.pop('readinessApproval', None)
    check(hashlib.sha256(json.dumps(ui_approval, sort_keys=True).encode()).hexdigest() == '2ed1969a5cbfa74fdd11c72ba96f5d29562ca876b9e66bbcf806842e1d2fd22c', 'UI readiness approval/history changed')
    check(ui_task.pop('implementationAuthorization', None) == 'GRANTED_BOUNDED_CN_UI_001_AFTER_READINESS'
          and ui_task.pop('resolvedDecisionIds', None) == ['OD-11'], 'UI bounded authority/resolution missing')
    historical_ui = next(x for x in old_backlog['changeSets'] if x['id'] == 'CN-UI-001')
    for field in ['authorityAvailable','readiness','workStatus','blockingDecisionIds']:
        ui_task[field] = historical_ui[field]
    auth_task = next(x for x in prior_changes if x['id'] == 'CN-AUTH-001')
    check(auth_task.pop('implementationEvidence', None) == auth_subset, 'Auth historical/full scope evidence changed')
    check(auth_task['evidencePaths'] == ['PROGRESS.md#auth-implementation'], 'Auth implementation reference missing')
    auth_task['evidencePaths'] = []
    auth_policy = auth_task.pop('policyApproval', None)
    check(hashlib.sha256(json.dumps(auth_policy, sort_keys=True).encode()).hexdigest() == '174d165e8369dbbbaf9cb641822462fa26217e4a4ac64c3f8a1a930364821d09', 'Auth task approval/source/historical readiness lost')
    check(auth_task.pop('implementationAuthorization', None) == 'GRANTED_BOUNDED_TASK_ON_READY_PREREQUISITES', 'Auth bounded conditional authority changed')
    check(auth_task.pop('decisionScopeApprovals', None) == [{'decisionId':'OD-01','scope':'CN-AUTH-001 bounded account/session policy only','status':'APPROVED'}], 'OD-01 broader scope silently closed')
    for field, value in {'authorityAvailable':False,'readiness':'BLOCKED','workStatus':'BLOCKED','blockingDecisionIds':['OD-01']}.items():
        auth_task[field] = value
    update = auth_task.pop('documentationUpdate', None)
    check(hashlib.sha256(json.dumps(update, sort_keys=True).encode()).hexdigest() == 'c4a84f904aed007cbd50cc4a6d6b44e985f101024e0f9f4397b223a2621b022f', 'Authorization documentation scope/status changed')
    dec_task = next(x for x in prior_changes if x['id'] == 'CN-DEC-001')
    stack_update = dec_task.pop('stackApproval', None)
    check(hashlib.sha256(json.dumps(stack_update, sort_keys=True).encode()).hexdigest() == '5c437231891b422e60b3c884a50a6c4590570bb91fa33a45e3e69977fd47f251', 'S16 brief approval/source boundary changed')
    check(dec_task['approvalStatus'] == 'OWNER_APPROVED_INITIAL_DIRECTION', 'Initial direction approval lost')
    dec_task['approvalStatus'] = 'AWAITING_OWNER_APPROVAL'  # Reconstruct historical S11 baseline; no other field change permitted.
    foundation = next(x for x in prior_changes if x['id'] == 'CN-FND-001')
    prerequisite = foundation.pop('prerequisiteEvidence', None)
    check(hashlib.sha256(json.dumps(prerequisite, sort_keys=True).encode()).hexdigest() == '06daec5b97345a591adc27d4403fb205e2b07f4cbdb94eedea10fff76289819f', 'Foundation qualification/authority prerequisites changed')
    implementation = foundation.pop('implementationEvidence', None)
    check(implementation == {'status':'VERIFIED_LOCAL_FOUNDATION','authorizationRequestSha256':'bb1985f3433d63595f5ff15cb22db92aec17ea5359be55d26de5cca0cddfab4c','evidencePath':'PROGRESS.md#foundation-implementation','remoteCI':'SUCCESS','remoteCIReference':'execution/status.json#foundationImplementation'}, 'Foundation scoped implementation record changed')
    historical_foundation = next(x for x in old_backlog['changeSets'] if x['id'] == 'CN-FND-001')
    check(foundation['affectedFilesModules'] == 'backend/, frontend/, scripts/verify-foundation.py, .github/workflows/foundation.yml; scoped knowledge closeout', 'Foundation owned scope changed')
    for field in ['authorityAvailable','readiness','workStatus','evidencePaths','affectedFilesModules']:
        foundation[field] = historical_foundation[field]
    for row in prior_changes:
        if row['id'] in od12_task_ids:
            original = next(x for x in old_backlog['changeSets'] if x['id'] == row['id'])
            check(row.pop('resolvedDecisionIds', None) == ['OD-12'], 'Task resolved OD-12 trace missing ' + row['id'])
            check(row['blockingDecisionIds'] == [value for value in original['blockingDecisionIds'] if value != 'OD-12'], 'Unrelated task blocker changed ' + row['id'])
            row['blockingDecisionIds'] = original['blockingDecisionIds']
    check(prior_changes == old_backlog['changeSets'] and changes[-1]['id'] == 'CN-KB-CONSOLIDATE-001' and changes[-1].get('aliases') == ['CN-KB-CONSOLIDATION-001'] and changes[-1].get('continuationAuthorizationSource') == 'S13', 'Existing backlog content lost/altered')
    old_sources = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/sources/cinenest-source-register.json'], text=True))['sources']
    current_sources = json.loads((K / 'sources/cinenest-source-register.json').read_text())['sources']
    # Preserve exact historical records while validating the owner-confirmed recovery separately.
    recovered_sources = [x for x in current_sources if x['id'] in {'S2', 'S3', 'S4'}]
    expected_recovery = {'S2': ('2d4961de671a356db2a6d81a0c2f9b185d969952fb9e656d19bcb6e97da6dea2', 26), 'S3': ('f414d822f6c5ce4f3c2e8fd9eb3558220ce9c82fa96ca3dfdab74b29da7e115f', 14), 'S4': ('84cc8394eb94068ee01be729344b9c740cde5bb4ea9c63dc11ab589d268a3ba2', 12)}
    for source in recovered_sources:
        digest, pages = expected_recovery[source['id']]
        check(source.get('availability') == 'AVAILABLE' and source.get('sha256') == digest and source.get('pageCount') == pages, 'Recovered source metadata mismatch '+source['id'])
        check(hashlib.sha256((K/source['path']).read_bytes()).hexdigest() == digest, 'Recovered PDF bytes changed '+source['id'])
        check(source.get('authority') == 'owner-confirmed-historical-source-not-current-policy', 'Source promoted to policy')
    check(len(recovered_sources) == 3 and cmap['CN-GATE-001']['readiness'] == 'BLOCKED', 'Source recovery must not bypass accountability gates')
    current_sources = [x.get('historicalMissingRecord', x) for x in current_sources]
    check(current_sources[:-6] == old_sources and [x['id'] for x in current_sources[-6:]] == ['S12', 'S13', 'S14', 'S15', 'S16', 'S17'], 'Prior source evidence lost/altered')
    removal_sources = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', manifest['consolidation']['removalBaselineRevision'] + ':knowledge/sources/cinenest-source-register.json'], text=True))['sources']
    check(current_sources[:-4] == removal_sources, 'Consolidation/editorial approval history changed')
    removal = manifest['consolidation']['removalAuthorization']
    check(manifest['consolidation']['status'] == 'COMPLETE_OWNER_APPROVED_REMOVAL' and state.get('removalOutcome', {}).get('status') == 'VERIFIED', 'Removal completion/status mismatch')
    check(state.get('removalOutcome', {}).get('deletedFiles') == len(manifest['consolidation']['documents']) and state.get('removalOutcome', {}).get('retainedExceptions') == [], 'Removal outcome/count inconsistent')
    check(current_sources[-4].get('requestSha256') == removal['requestSha256'] and current_sources[-4].get('scope') == removal['scope'], 'S14 approval provenance/scope diverges')
    check(hashlib.sha256(json.dumps(current_sources[-3], sort_keys=True).encode()).hexdigest() == 'bd5c27afa50fafa8e4b1bff49946ffe1fc467665c055baf5785001a5e2e009f7', 'S15 owner direction/scope provenance changed')
    check(hashlib.sha256(json.dumps(current_sources[-2], sort_keys=True).encode()).hexdigest() == '90019c67e0d2864accc21fa918d76fcb52ff364c789f0a4026123498277ae35c', 'S16 owner approval provenance changed')
    check(hashlib.sha256(json.dumps(current_sources[-1], sort_keys=True).encode()).hexdigest() == '875be64d3ec278c5ec8a9cec24a3e7e586e1100ffab90417d1f64fffaca9f8d4', 'S17 planning authority/scope changed')
    check(hashlib.sha256(json.dumps(changes[-1].get('roadmapMaintenance'), sort_keys=True).encode()).hexdigest() == '42b7eff46c72c78c5faad65c88b12971235b3c63961552d7e9e81400952400f9', 'Roadmap maintenance promoted beyond documentation authority')
    auth = (K / 'ARCHITECTURE.md').read_text()
    for required in ['Finance Reviewer grant at Venue A', 'No Cartesian product', 'mandatory global restrictions', 'An unset required limit is not unlimited', 'accepted operation', 'CN-NFR-02', 'NOT_VERIFIED', 'raw PINs']:
        check(required.lower() in auth.lower(), 'Authorization specification missing ' + required)
    check('<a id="authorization-requirements"></a>' in (K / 'PROJECT.md').read_text() and '<a id="permission-aware-ui"></a>' in (K / 'DESIGN.md').read_text(), 'Authorization product/UI sections missing')
    old_omissions = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/execution/omissions.json'], text=True))['omissions']
    current_omissions = read_json('omissions.json')['omissions']
    check([o['id'] for o in current_omissions] == [o['id'] for o in old_omissions], 'Omission IDs dropped/changed')
    for old, current in zip(old_omissions, current_omissions):
        expected = dict(old)
        if old['id'] in {'OM-02', 'OM-07'}:
            expected['recovery'] = current['recovery']  # Updated pending-stack/canonical-instruction guidance only.
        if old['id'] == 'OM-02':
            check(hashlib.sha256(json.dumps(current, sort_keys=True).encode()).hexdigest() == '0fa71dc92228f079187378f0fd14feb102a172c2cac991158d53bb1a6bbd2a15', 'S16 remaining qualification/authority omission changed')
        else:
            check(expected == current, 'Omission gap/status silently changed ' + old['id'])
    progress = (K / 'PROGRESS.md').read_text()
    task_rows = ''.join('| ' + c['id'] + ' | ' + str(c['phase']) + ' | ' + c['objective'] + ' | ' + (', '.join(customer_task['dependencies'] if c['id']=='CN-CUSTOMER-001' else access_task['dependencies'] if c['id']=='CN-ACCESS-001' else refund_actual_task['dependencies'] if c['id']=='CN-REFUND-001' else c['dependencies']) or 'None') + ' | ' + (mi_task['workStatus']+' / '+mi_task['readiness'] if c['id']=='CN-AMEND-001' else cu_task['workStatus']+' / '+cu_task['readiness'] if c['id']=='CN-CUSTOMER-001' else ni_task['workStatus']+' / '+ni_task['readiness'] if c['id']=='CN-NOTIFY-001' else ai_task['workStatus']+' / '+ai_task['readiness'] if c['id']=='CN-ACCESS-001' else impl_task['workStatus']+' / '+impl_task['readiness'] if c['id']=='CN-REFUND-001' else 'VERIFIED / CLOSED' if c['id']=='CN-GATE-001' else 'VERIFIED / CLOSED' if c['id']=='CN-ADMIN-001' and admin_evidence.get('remoteCI')=='SUCCESS' else c['workStatus'] + ' / ' + c['readiness']) + ' |\n' for c in changes)
    check(task_rows in progress, 'PROGRESS task projection diverges from authority')
    mapping_rows = ''.join('| ' + e['sourcePath'] + ' | [' + e['canonicalPath'] + '#' + e['anchor'] + '](' + e['canonicalPath'].removeprefix('knowledge/') + '#' + e['anchor'] + ') | [original @ ' + e['historicalReference']['commit'][:7] + '](' + e['historicalReference']['url'] + ') |\n' for e in manifest['consolidation']['documents'])
    check(mapping_rows in progress and '<a id="consolidation"></a>' in progress, 'PROGRESS source/destination/removal mapping omitted')
    project = (K / 'PROJECT.md').read_text()
    feature_catalog = json.loads((K / 'product/cinenest-feature-catalog.json').read_text())['features']
    feature_rows = ''.join('| ' + f['id'] + ' | ' + f['name'] + ' — ' + f['purpose'] + ' | ' + f['module'] + ' | ' + f['decisionStatus'] + ' |\n' for f in feature_catalog)
    check(feature_rows in project, 'PROJECT feature projection diverges from authority')
    architecture = (K / 'ARCHITECTURE.md').read_text()
    decision_rows = ''.join('| ' + d['id'] + ' | ' + d['conflict'] + ' | ' + d['launchBlocker'] + ' |\n' for d in od)
    check(decision_rows in architecture, 'ARCHITECTURE decision projection diverges from authority')
    approval = state.get('od12DecisionUpdate', {})
    check(approval.get('decisionId') == 'OD-12' and approval.get('status') == 'APPROVED_DECISION_ONLY' and approval.get('implementationAuthorization') == 'NOT_GRANTED_BY_THIS_DECISION', 'OD-12 approval promoted to implementation')
    check(cmap['CN-AUTH-001']['readiness'] == 'CLOSED' and cmap['CN-AUTH-001']['blockingDecisionIds'] == [], 'Auth READY does not match bounded final approval')
    ui_reconciliation = state.get('uiReadinessReconciliation', {})
    check(ui_reconciliation.get('baselineHead') == '78e4c74cf1462bcf69b384df045f019d61fafe3f'
          and ui_reconciliation.get('decisionStatus') == 'APPROVED' and ui_reconciliation.get('readiness') == 'READY'
          and ui_reconciliation.get('workStatus') == 'PLANNED' and ui_reconciliation.get('implementationStatus') == 'NOT_VERIFIED'
          and ui_reconciliation.get('tests') == 'NOT_RUN' and ui_reconciliation.get('sessionScope') == 'DECISION_AND_READINESS_ONLY'
          and ui_reconciliation.get('p1C06') == 'OPEN' and ui_reconciliation.get('d1') == 'NOT_READY', 'UI readiness promoted beyond owner authority/evidence')
    # This decision session changes no implementation, token/style or frontend dependency bytes.
    protected = subprocess.check_output(['git', '-C', str(R), 'ls-tree', '-r', '--name-only', '78e4c74cf1462bcf69b384df045f019d61fafe3f', '--', 'backend', 'frontend', 'knowledge/tokens', 'knowledge/styles'], text=True).splitlines()
    for name in protected:
        baseline_bytes = subprocess.check_output(['git', '-C', str(R), 'show', '78e4c74cf1462bcf69b384df045f019d61fafe3f:' + name])
        if name in room_owned and name not in ui_owned:
            check(historical_source_matches(name,hashlib.sha256(baseline_bytes).hexdigest()), 'Unapproved room successor of protected source ' + name)
        elif name == 'frontend/next.config.mjs':
            check(mi_source(name)==b'const nextConfig = { poweredByHeader: false, agentRules: false };\nexport default nextConfig;\n','Local workflow changed unrelated frontend configuration')
        elif name not in ui_owned:
            check((R / name).is_file() and inventory_source(name) == baseline_bytes, 'Unapproved protected source/token/dependency change ' + name)
    check('<a id="od11-approved-baseline"></a>' in (K / 'DESIGN.md').read_text() and '<a id="ui-readiness"></a>' in progress, 'UI canonical approval/readiness missing')
    reconciliation = state.get('authReadinessReconciliation', {})
    check(reconciliation.get('requestSha256') == '7411e72e0db40ad99d70ac76c4d2c92876635449376bb9a3203df02a34730945' and reconciliation.get('readiness') == 'READY' and reconciliation.get('implementationStatus') == 'NOT_VERIFIED' and reconciliation.get('sessionScope') == 'DECISION_RECORDING_ONLY', 'Auth readiness reconciliation scope lost/promoted')
    check('<a id="auth-bounded-policy"></a>' in architecture and '<a id="auth-policy-readiness"></a>' in progress, 'Approved auth contract/handoff missing')
    check('<a id="organization-scope"></a>' in architecture and '<a id="organization-scope"></a>' in project and '<a id="od12-decision"></a>' in progress, 'OD-12 canonical scope/reference missing')
    for phrase in ['SINGLE-ORGANIZATION WITH VENUE-SCOPED AUTHORIZATION','customers are not tenants','tenant_id','SaaS']:
        check(phrase in project, 'Approved organization requirement missing ' + phrase)
    for number in range(1, 9):
        check(f'AG-AC{number:02d}' in progress, 'Setup acceptance history omitted')
    check(state.get('requestedTaskId') == 'CN-UI-001' and state.get('stackApprovalUpdate', {}).get('sourceId') == 'S16' and state.get('authorizationUpdate', {}).get('sourceId') == 'S15', 'Authorization documentation task/provenance lost')
    check(state.get('stackApprovalUpdate', {}).get('qualificationStatus') == 'PENDING' and state.get('stackApprovalUpdate', {}).get('implementationAuthorization') == 'NOT_GRANTED', 'Approval promoted to qualification/implementation')
    for required in ['<a id="stack-approval"></a>', 'No material incompatibility found', 'compatible, explicitly recorded npm', 'No pre-release dependencies', 'CN-FND-001 implementation **NOT_GRANTED**']:
        check(required in architecture, 'S16 canonical approval condition missing ' + required)
    check('<a id="foundation-prerequisites"></a>' in progress and 'Selected-stack repository skeleton and CI' in progress, 'Actual foundation scope review missing')
    roadmap = progress.split('<a id="mvp-roadmap"></a>', 1)[-1].split('<!-- mvp-roadmap-end -->', 1)[0]
    check(progress.count('<a id="mvp-roadmap"></a>') == 1 and progress.count('<!-- mvp-roadmap-end -->') == 1, 'Roadmap duplicated/missing')
    criteria = re.findall(r'^- \[([ x])\] \*\*(P(\d+)-C(\d{2}))\*\* (.*?) — Req: (.*?); tests: (.*?); tasks: (.*?)\. Evidence: (.*?)\.$', roadmap, re.M)
    expected_counts = [8, 6, 6, 6, 8, 6, 6, 6, 7, 8, 7]
    expected_ids = [f'P{i}-C{j:02d}' for i, count in enumerate(expected_counts) for j in range(1, count+1)]
    check([row[1] for row in criteria] == expected_ids, '74 planning criteria missing/duplicated/renumbered')
    grouped = [[row[4] for row in criteria if int(row[2]) == i] for i in range(11)]
    roadmap_meta = manifest['roadmapMaintenance']
    check(hashlib.sha256(json.dumps(grouped, sort_keys=True).encode()).hexdigest() == roadmap_meta['checklistSha256'], 'Owner planning criterion wording lost/changed')
    check(roadmap_meta['templateCriterionCount'] == 74 and roadmap_meta['phaseIds'] == ['P'+str(i) for i in range(11)] and roadmap_meta['demonstrationIds'] == ['D'+str(i) for i in range(7)], 'Phase/demo identities changed')
    graph = re.search(r'```mermaid\n(.*?)\n```', roadmap, re.S)
    check(graph is not None, 'Graphical roadmap missing')
    if graph:
        graph_nodes = re.findall(r'^  (\w+)\[', graph[1], re.M)
        check(graph_nodes == ['G'] + ['P'+str(i) for i in range(11)] + ['D'+str(i) for i in range(7)], 'Roadmap graph phase/demo source nodes diverge')
        check('P0 --> P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7 --> P8 --> P9 --> P10' in graph[1], 'Illustrative roadmap dependency chain omitted')
    req_by_id = {r['id']: r for r in reqs}
    checked = []
    criterion_requirements = {}
    for box, cid, number, seq, wording, req_text, test_text, task_text, evidence in criteria:
        rset = set(req_text.split(', ')); tset = set(test_text.split(', ')); cset = set(task_text.split(', '))
        check(bool(rset) and rset <= set(rids) and tset <= set(tids) and cset <= set(cids), 'Roadmap orphan mapping ' + cid)
        if rset <= set(rids):
            check(tset == {tid for rid in rset for tid in req_by_id[rid]['testIds']} and cset == {task for rid in rset for task in req_by_id[rid]['changeSetIds']}, 'Criterion mapping differs from authority ' + cid)
        criterion_requirements[cid] = rset
        if box == 'x':
            checked.append(cid)
            check((number == '0' and 'DOC_VERIFIED' in evidence) or (cid == 'P1-C01' and 'LOCAL_FOUNDATION_VERIFIED' in evidence) or (cid == 'P1-C06' and ui_ci and 'UI_SHELL_VERIFIED' in evidence) or (cid == 'P2-C01' and room_ci and 'ROOM_SLICE_VERIFIED' in evidence) or (cid=='P2-C03' and pub_ci and 'INDICATIVE_PUBLICATION_VERIFIED' in evidence) or (cid=='P2-C02' and pkg_ci and 'PACKAGE_DRAFT_SLICE_VERIFIED' in evidence) or (cid in {'P3-C03','P3-C05'} and inventory_ci and 'INVENTORY_SLICE_VERIFIED' in evidence), 'Implementation checked without actual scoped evidence ' + cid)
    check(checked == ['P0-C01','P0-C02','P0-C03','P0-C04','P0-C05','P0-C06','P1-C01'] + (['P1-C06'] if ui_ci else []) + (['P2-C01'] if room_ci else []) + (['P2-C02'] if pkg_ci else []) + (['P2-C03'] if pub_ci else []) + (['P3-C03','P3-C05'] if inventory_ci else []), 'Readiness preparation/evidence roll-up changed')
    check(re.findall(r'^#### P(\d+)\.', roadmap, re.M) == [str(i) for i in range(11)], 'Phase definitions duplicated/missing')
    gates = re.findall(r'^- \[([ x])\] \*\*(P\d+-ACCEPT)\*\*', roadmap, re.M)
    check(gates == [(' ', 'P'+str(i)+'-ACCEPT') for i in range(11)], 'Phase accepted without reviewer/evidence/prerequisite closure')
    demos = re.findall(r'^#### (D\d+) — ', roadmap, re.M)
    check(demos == ['D'+str(i) for i in range(7)], 'Demonstration definitions missing/duplicated')
    demo_boxes = re.findall(r'^- \[([ x])\] \*\*(D\d+-(?:HELD|SCOPE-ACCEPTED))\*\*', roadmap, re.M)
    check(demo_boxes == [(' ', f'D{i}-{kind}') for i in range(7) for kind in ['HELD','SCOPE-ACCEPTED']], 'Demo held/accepted without actual evidence')
    coverage_rows = re.findall(r'^\| ([^|]+) \| (P\d+-C\d{2}) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$', roadmap.split('<!-- roadmap:coverage -->',1)[-1].split('<!-- roadmap-end:coverage -->',1)[0], re.M)
    check([row[0] for row in coverage_rows] == rids, 'Retained requirement delivery-home coverage lost/duplicated')
    for rid, home, test_text, task_text, evidence in coverage_rows:
        if rid in req_by_id:
            check(rid in criterion_requirements.get(home, set()) and test_text.split(', ') == req_by_id[rid]['testIds'] and task_text.split(', ') == req_by_id[rid]['changeSetIds'], 'Requirement home mapping invalid ' + rid)
    task_section = roadmap.split('<!-- roadmap:tasks -->',1)[-1].split('<!-- roadmap-end:tasks -->',1)[0]
    task_projection = re.findall(r'^\| (CN-[^|]+) \| (P\d+) \| ([^|]+) \| ([^|]+) \|$', task_section, re.M)
    check([row[0] for row in task_projection] == cids, 'Existing task roadmap coverage lost/duplicated')
    for tid, phase, deps, status in task_projection:
        check(phase in roadmap_meta['phaseIds'] and deps == (', '.join(customer_task['dependencies'] if tid=='CN-CUSTOMER-001' else access_task['dependencies'] if tid=='CN-ACCESS-001' else refund_actual_task['dependencies'] if tid=='CN-REFUND-001' else cmap[tid]['dependencies']) or 'None') and status == (mi_task['workStatus']+' / '+mi_task['readiness'] if tid=='CN-AMEND-001' else cu_task['workStatus']+' / '+cu_task['readiness'] if tid=='CN-CUSTOMER-001' else ni_task['workStatus']+' / '+ni_task['readiness'] if tid=='CN-NOTIFY-001' else ai_task['workStatus']+' / '+ai_task['readiness'] if tid=='CN-ACCESS-001' else impl_task['workStatus']+' / '+impl_task['readiness'] if tid=='CN-REFUND-001' else 'VERIFIED / CLOSED' if tid=='CN-GATE-001' else 'VERIFIED / CLOSED' if tid=='CN-ADMIN-001' and admin_evidence.get('remoteCI')=='SUCCESS' else cmap[tid]['workStatus']+' / '+cmap[tid]['readiness']), 'Task dependency/status altered by roadmap ' + tid)
    state_roadmap = state.get('roadmapMaintenance', {})
    check(state_roadmap.get('readinessCriteria') == {'checked':6,'total':8,'evidenceClass':'DOCUMENTATION_AND_SCOPED_AUTHORITY','accepted':False} and state_roadmap.get('planningCriteria') == {'checked':13 if inventory_ci else 11 if pub_ci else 10 if pkg_ci else 9 if room_ci else 8 if ui_ci else 7,'total':74,'overallEffortPercentage':None}, 'Roadmap ratio conflates readiness with effort')
    check(state_roadmap.get('acceptedDeliveryPhases') == state_roadmap.get('demonstrationsHeld') == state_roadmap.get('demonstrationScopesAccepted') == 0 and state_roadmap.get('implementationAuthorization') == 'NOT_GRANTED', 'Unsupported phase/demo/implementation acceptance')
    for warning in ['illustrative','UNKNOWN/TBD','NOT_HELD','NOT_ACCEPTED','NEEDS_RECHECK','CN-NOTIFY-001 (P5) depends on CN-ACCESS-001 (P7)','HTML','one venue']:
        check(warning in roadmap, 'Roadmap qualification missing ' + warning)
    check('MVP phase criteria/blockers' in entry and 'graphical roadmap' in entry and 'No background monitoring' in entry, 'Roadmap closeout instruction missing')
    # Active reference fields must resolve to real canonical sections/assets; historical ledgers stay snapshots.
    def references(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in {'canonicalSpecification', 'proposalReference', 'evidencePath', 'evidencePaths', 'verificationEvidence', 'decisionBriefEvidence', 'actualTestPaths'}:
                    for target in item if isinstance(item, list) else [item]:
                        if not target or not isinstance(target, str):
                            continue
                        name, sep, anchor = target.partition('#'); asset = K / name
                        if not asset.exists():
                            asset = R / name
                        check(asset.is_file(), 'Active reference missing ' + target)
                        if sep and asset.name in {'PROJECT.md', 'ARCHITECTURE.md', 'DESIGN.md', 'PROGRESS.md'}:
                            check(('<a id="' + anchor + '"></a>') in asset.read_text(), 'Active anchor missing ' + target)
                references(item)
        elif isinstance(value, list):
            for item in value:
                references(item)
    for data in [backlog, coverage, tests_doc, state, {'decisions': od}]:
        references(data)
    if errors:
        print('\n'.join('FAIL '+e for e in errors))
        return 1
    print(f'PASS execution: instruction links; {len(entries)} reviewed baseline assets; {len(reqs)} requirements/{len(tests)} planned-test records; {len(changes)} acyclic change sets across 12 workstreams; approved mapping; future authority/acceptance gates; missing sources explicit.')
    print('Limits: static evidence/range consistency, not proof of model auto-loading, product implementation, external/physical safety or business acceptance.')
    return 0


def iot_implementation_main():
    """Validate the actual authorized successor before in-memory historical receipt replay. No disk writes."""
    import io
    from unittest.mock import patch
    base='cb4f7bbca657fac6eff362b6b22f57c437b9689a'
    errors=[]
    def require(ok,message):
        if not ok:errors.append(message)
    def prior(path):return subprocess.check_output(['git','-C',str(R),'show',base+':'+path])
    protected={'execution/change-sets.json': '22368be24c1546b975f8fe0b5f9d5f57ed349e7ec0158f61b3735f0bb8a9be37', 'execution/status.json': '57b6b85504ca11185d0c21cc65debe8d5f0487f32bc7de6a62df46585e384a8c', 'execution/requirements.json': '0611d1d9726946ce2fe32825d41de299c564411339f6dcdfb261603bcae51af6', 'execution/test-plan.json': 'a654fdac7b65e358e9a4683dc14fc5aa023b60df1cbd393f7d6190330fc34790'}
    actual={p:json.loads((K/p).read_text()) for p in protected}
    for p,h in protected.items():require(hashlib.sha256(json.dumps(actual[p],sort_keys=True).encode()).hexdigest()==h,'IoT implementation registry/history changed '+p)
    task=next(x for x in actual['execution/change-sets.json']['changeSets'] if x['id']=='CN-IOT-001')
    evidence=actual['execution/status.json']['iotImplementation'];owned={'backend/src/main/java/com/cinenest/iot/internal/JdbcIotRepository.java', 'frontend/lib/bank-backend.ts', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/iot/internal/IotService.java', 'frontend/app/api/admin/venues/[venueId]/iot/devices/[device]/commands/[command]/reconciliations/route.ts', 'backend/src/main/java/com/cinenest/access/internal/DeviceAccessAdapter.java', 'frontend/app/admin/iot/page.tsx', 'frontend/app/api/admin/venues/[venueId]/iot/devices/[device]/commands/route.ts', 'frontend/app/api/admin/venues/[venueId]/iot/devices/[device]/commands/[command]/simulations/route.ts', 'frontend/tests/iot.test.mjs', 'backend/src/main/java/com/cinenest/iot/internal/IotBootstrap.java', 'frontend/components/iot.tsx', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/app/api/admin/venues/[venueId]/iot/devices/route.ts', 'backend/src/main/java/com/cinenest/iot/package-info.java', 'backend/src/main/java/com/cinenest/iot/internal/IotController.java', 'backend/src/test/java/com/cinenest/iot/internal/IotFaults.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/java/com/cinenest/access/DeviceAccess.java', 'backend/src/main/resources/db/migration/V16__synthetic_lighting.sql', 'backend/src/main/java/com/cinenest/iot/internal/IotPolicy.java', 'frontend/package.json', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/tests/iot-browser.mjs', 'backend/src/main/java/com/cinenest/iot/internal/IotWorker.java', 'backend/src/main/java/com/cinenest/iot/internal/IotRepository.java', 'backend/src/main/java/com/cinenest/iot/internal/IotFailure.java', 'frontend/app/api/admin/venues/[venueId]/iot/devices/[device]/route.ts', 'backend/src/main/java/com/cinenest/iot/internal/LightingSimulator.java'}
    require(set(evidence['ownedFiles'])==owned,'IoT source allowlist changed')
    for p,h in evidence['ownedFiles'].items():require((R/p).is_file() and hashlib.sha256((R/p).read_bytes()).hexdigest()==h,'IoT owned source checksum mismatch '+p)
    before=json.loads(prior('knowledge/execution/change-sets.json'))['changeSets']
    require([(x['id'],x['dependencies']) for x in actual['execution/change-sets.json']['changeSets']]==[(x['id'],x['dependencies']) for x in before],'IoT dependency graph changed')
    require(task['dependencies']==['CN-ACCESS-001'] and task['authorityAvailable'] and task['remainingReadinessGates']==[] and task['implementationAuthorization']==evidence['authority'],'IoT implementation authority invalid')
    require(task['definitionOfDone']=='Unknown never OFF/available; simulator evidence qualified','IoT DoD changed')
    require(evidence['authority']['scope']=='GRANTED_BOUNDED_SYNTHETIC_LIGHTING' and evidence['authority']['requestSha256']=='76f47b3160debd0e8005090cc48aeac44230fb675f95476104e080c3e1f3f3e2','IoT owner authority lost')
    require(evidence['realEquipmentControl']=='DISABLED' and evidence['gateway']=='DEFERRED_BLOCKED' and evidence['fullRequirementAcceptance']=='NOT_ACCEPTED','IoT safety boundary changed')
    for path,key,ids in [('execution/requirements.json','requirements',{'CN-O02','CN-O04','CN-A05'}),('execution/test-plan.json','tests',{'T-CN-O02','T-CN-O04','T-CN-A05','T-CN-NFR-02'})]:
        doc=json.loads(json.dumps(actual[path]));old=json.loads(prior('knowledge/'+path))
        for row in doc[key]:
            receipt=row.pop('boundedIotImplementationEvidence',None)
            require((receipt is not None)==(row['id'] in ids),'IoT bounded coverage missing '+row['id'])
            if receipt:require(receipt['fullAcceptance']=='NOT_ACCEPTED' and receipt['status']==evidence['workStatus'],'IoT full acceptance/test promotion '+row['id'])
        require(doc==old,'IoT altered full requirement/test history '+path)
    for path in ['knowledge/decisions/cinenest-open-decisions.json','knowledge/execution/source-ledger.json','knowledge/sources/cinenest-source-register.json']:
        require(json.loads((R/path).read_text())==json.loads(prior(path)),'IoT changed prior decisions/source evidence '+path)
    baseline=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',base,'--','backend','frontend'],text=True).splitlines());added=owned-baseline
    inventory=set(subprocess.check_output(['git','-C',str(R),'-c','core.quotePath=false','ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines())
    require(inventory==baseline|added,'IoT unrelated application inventory changed')
    for path in baseline-owned-{'frontend/next-env.d.ts'}:require((R/path).read_bytes()==prior(path),'IoT unrelated application changed '+path)
    require({p.name for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}=={Path(p).name for p in baseline if '/db/migration/' in p}|{'V16__synthetic_lighting.sql'},'IoT migration inventory changed')
    sql=(R/'backend/src/main/resources/db/migration/V16__synthetic_lighting.sql').read_text()
    require(set(re.findall(r'CREATE TABLE ([a-z_]+[.][a-z_]+)',sql))=={'cinenest_iot.'+x for x in ['device','assignment','command','receipt','attempt','audit']},'IoT datastore ownership changed')
    require(all(x not in sql for x in ['cinenest_access.','cinenest_payments.','cinenest_booking.']),'IoT foreign datastore mutation')
    controller=(R/'backend/src/main/java/com/cinenest/iot/internal/IotController.java').read_text()
    require(set(re.findall(r'@(Get|Post)Mapping[(]"([^"]+)"[)]',controller))=={('Get','/api/admin/venues/{v}/iot/devices'),('Get','/api/admin/venues/{v}/iot/devices/{d}'),('Post','/api/admin/venues/{v}/iot/devices/{d}/commands'),('Post','/api/admin/venues/{v}/iot/devices/{d}/commands/{c}/simulations'),('Post','/api/admin/venues/{v}/iot/devices/{d}/commands/{c}/reconciliations')},'IoT API surface changed')
    progress=(K/'PROGRESS.md').read_text();old_progress=prior('knowledge/PROGRESS.md').decode()
    require(re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',progress,re.M)==re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',old_progress,re.M),'IoT promoted phase/demo criteria')
    require('SYNTHETIC IOT TEST — NO REAL EQUIPMENT CONTROL' in (R/'frontend/components/iot.tsx').read_text(),'IoT simulation label lost')
    closed=evidence['workStatus']=='VERIFIED'
    require(task['workStatus']==evidence['workStatus'] and task['readiness']==('CLOSED' if closed else 'READY'),'IoT status mismatched')
    require('| CN-IOT-001 | 7 | Simulated device command/observation slice | CN-ACCESS-001 | '+task['workStatus']+' / '+task['readiness']+' |' in progress,'IoT current task projection stale')
    if closed:
        require(evidence.get('checks')=={'backend':'PASS','frontend':'PASS','browser':'PASS','architecture':'PASS','launchers':'PASS','governance':'PASS'} and evidence.get('sourceCI')=='SUCCESS','IoT closure lacks required checks/CI')
        source=evidence.get('sourceCommit','');require(bool(re.fullmatch('[0-9a-f]{40}',source)),'IoT exact-source receipt invalid')
        if re.fullmatch('[0-9a-f]{40}',source):
            for p,h in evidence['ownedFiles'].items():require(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',source+':'+p])).hexdigest()==h,'IoT CI source stale '+p)
    if errors:
        print('\n'.join('FAIL '+x for x in errors));return 1
    # Actual new inputs passed above. Replay frozen earlier receipts without disguising unexpected source changes.
    replacements={R/p:prior(p) for p in owned&baseline}
    replacements.update({K/p:prior('knowledge/'+p) for p in protected})
    replacements[K/'PROGRESS.md']=prior('knowledge/PROGRESS.md')
    original_read=Path.read_bytes;original_text=Path.read_text;original_glob=Path.glob;original_output=subprocess.check_output
    def read_bytes(path,*a,**kw):return replacements[path] if path in replacements else original_read(path,*a,**kw)
    def read_text(path,*a,**kw):return replacements[path].decode() if path in replacements else original_text(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in original_glob(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(args,*a,**kw):
        value=original_output(args,*a,**kw)
        if args[0]=='git' and (('diff' in args and '--name-only' in args) or 'ls-files' in args):
            text=value if isinstance(value,str) else value.decode();removed=owned if 'diff' in args else added
            text=''.join(line+'\n' for line in text.splitlines() if line not in removed);return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',read_bytes),patch.object(Path,'read_text',read_text),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):
        result=historical_main()
    if result==0:print('PASS IoT successor: exact authority/source/API/schema,unchanged dependencies/full coverage,qualified acceptance and historical receipts')
    return result


def ops_readiness_main():
    """Check exact approved successor, then replay immutable historical receipts in memory."""
    from unittest.mock import patch
    base='8b5bb2467e034a52efb21946abfab59aeef70a7b'
    expected={'decisions/cinenest-open-decisions.json': '55382ebe16e0c79ab4883f8a26448842153518acb5273a0f476904b85fc70626', 'execution/change-sets.json': '0bba3ccaf7a224467196c195714f010f2b0874037d05cf796c7828abcd522adc', 'execution/status.json': '81d9cb8feead09999ff15b60fc908eb0b5d0c3017aef65faa77e01f9c9b21889'}
    replacements={}
    for name,digest in expected.items():
        if hashlib.sha256((K/name).read_bytes()).hexdigest()!=digest:
            print('FAIL operations approved registry changed '+name);return 1
        replacements[K/name]=subprocess.check_output(['git','-C',str(R),'show',base+':knowledge/'+name])
    tasks=json.loads((K/'execution/change-sets.json').read_text())['changeSets']
    previous=json.loads(replacements[K/'execution/change-sets.json'])['changeSets']
    if [(x['id'],x['dependencies'],x['workStatus'],x['readiness']) for x in tasks]!=[(x['id'],x['dependencies'],x['workStatus'],x['readiness']) for x in previous]:
        print('FAIL operations changed dependency/status');return 1
    task=next(x for x in tasks if x['id']=='CN-OPS-001')
    if task['authorityAvailable'] or task['implementationAuthorization']!='NOT_GRANTED_READINESS_ONLY' or task['definitionOfDone']!='Physical readiness requires accountable evidence and staff':
        print('FAIL operations authority/DoD changed');return 1
    for name in ['execution/requirements.json','execution/test-plan.json','sources/cinenest-source-register.json','execution/source-ledger.json']:
        if (K/name).read_bytes()!=subprocess.check_output(['git','-C',str(R),'show',base+':knowledge/'+name]):
            print('FAIL operations altered coverage/source '+name);return 1
    for name in subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',base,'--','backend','frontend'],text=True).splitlines():
        if name!='frontend/next-env.d.ts' and (R/name).read_bytes()!=subprocess.check_output(['git','-C',str(R),'show',base+':'+name]):
            print('FAIL operations altered application '+name);return 1
    original_bytes=Path.read_bytes;original_text=Path.read_text
    def rb(path,*a,**kw):return replacements[path] if path in replacements else original_bytes(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else original_text(path,*a,**kw)
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt):result=iot_implementation_main()
    if result==0:print('PASS operations readiness: exact owner approval, unchanged task states/dependencies/coverage/source/application; no implementation authority')
    return result

def ops_implementation_main():
    """Validate authorized implementation delta before replaying frozen readiness receipts."""
    from unittest.mock import patch
    base='cdf93324800e366e207e67c82a8b3a068dd28d5d'
    errors=[]
    def require(ok,message):
        if not ok:errors.append(message)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',base+':'+name])
    path='execution/change-sets.json';actual=json.loads((K/path).read_text());old=json.loads(prior('knowledge/'+path));tasks=actual['changeSets'];task=next(t for t in tasks if t['id']=='CN-OPS-001');expected=next(t for t in old['changeSets'] if t['id']=='CN-OPS-001')
    allowed={'implementationAuthorization','authorityAvailable','remainingReadinessGates','readiness','workStatus','evidencePaths'}
    for key in allowed:
        if key in task:expected[key]=task[key]
    require(actual==old,'Operations altered unrelated task/history/dependency/scope')
    evidence=json.loads((K/'execution/status.json').read_text())['opsImplementation']
    current_status=json.loads((K/'execution/status.json').read_text());prior_status=json.loads(prior('knowledge/execution/status.json'))
    current_status.pop('opsImplementation',None)
    current_status['nextAction']=prior_status['nextAction']
    require(current_status==prior_status,'Operations altered unrelated execution history/status')
    authority=task['implementationAuthorization']
    require(isinstance(authority,dict) and authority.get('scope')=='GRANTED_BOUNDED_SYNTHETIC_TURNOVER' and authority.get('requestSha256')=='37a415aef1afc807e560e327072c007ade759da881c8de863710be2788a6f2d3','Operations implementation authority invalid')
    require(task['authorityAvailable'] and task['remainingReadinessGates']==[] and authority==evidence['authority'],'Operations readiness gates mismatched')
    require(evidence['physicalOccupancy']=='UNKNOWN' and evidence['physicalReadiness']=='UNVERIFIED' and evidence['realOperations']=='DISABLED' and evidence['gateway']=='DEFERRED_BLOCKED' and evidence['fullRequirementAcceptance']=='NOT_ACCEPTED','Operations safety boundary changed')
    state=evidence['workStatus'];require(task['workStatus']==state and task['readiness']==('CLOSED' if state=='VERIFIED' else 'READY'),'Operations task status mismatched')
    owned=evidence['ownedFiles'];require(set(owned)=={'backend/src/main/java/com/cinenest/rooms/internal/TurnoverRepository.java', 'backend/src/main/java/com/cinenest/rooms/internal/TurnoverService.java', 'frontend/components/operations.tsx', 'frontend/package.json', 'backend/src/main/java/com/cinenest/rooms/internal/TurnoverController.java', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/main/resources/db/migration/V17__synthetic_room_turnover.sql', 'frontend/tests/operations-browser.mjs', 'frontend/app/api/admin/venues/[venueId]/operations/cases/[id]/route.ts', 'frontend/app/api/admin/venues/[venueId]/operations/cases/[id]/[action]/route.ts', 'backend/src/main/java/com/cinenest/rooms/OperationsEvidence.java', 'backend/src/main/java/com/cinenest/iot/internal/TurnoverIotEvidence.java', 'frontend/app/api/admin/venues/[venueId]/operations/cases/route.ts', 'frontend/lib/bank-backend.ts', 'frontend/tests/operations.test.mjs', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/app/admin/operations/page.tsx', 'backend/src/main/java/com/cinenest/rooms/internal/JdbcTurnoverRepository.java', 'backend/src/main/java/com/cinenest/booking/internal/TurnoverBookingEvidence.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/test/java/com/cinenest/rooms/internal/TurnoverServiceTest.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/components/shell.tsx'},'Operations source allowlist changed')
    for name,digest in owned.items():require((R/name).is_file() and hashlib.sha256((R/name).read_bytes()).hexdigest()==digest,'Operations owned source mismatch '+name)
    baseline=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',base,'--','backend','frontend'],text=True).splitlines());added=set(owned)-baseline
    inventory=set(subprocess.check_output(['git','-C',str(R),'-c','core.quotePath=false','ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines());require(inventory==baseline|added,'Operations unrelated application inventory changed')
    for name in baseline-set(owned)-{'frontend/next-env.d.ts'}:require((R/name).read_bytes()==prior(name),'Operations altered unrelated source '+name)
    for name in ['decisions/cinenest-open-decisions.json','execution/source-ledger.json','sources/cinenest-source-register.json']:require((K/name).read_bytes()==prior('knowledge/'+name),'Operations altered prior decision/source '+name)
    for name,key,ids in [('execution/requirements.json','requirements',{'CN-O03','CN-O04','CN-A05','CN-NFR-05'}),('execution/test-plan.json','tests',{'T-CN-O03','T-CN-O04','T-CN-A05','T-CN-NFR-05'})]:
        doc=json.loads((K/name).read_text())
        for row in doc[key]:
            receipt=row.pop('boundedOperationsImplementationEvidence',None)
            require((receipt is not None)==(row['id'] in ids),'Operations coverage mismatch '+row['id'])
            if receipt:require(receipt['fullAcceptance']=='NOT_ACCEPTED' and receipt['status']==state,'Operations full acceptance promoted '+row['id'])
        require(doc==json.loads(prior('knowledge/'+name)),'Operations changed full requirement/test history '+name)
    progress=(K/'PROGRESS.md').read_text();require(re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',progress,re.M)==re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',prior('knowledge/PROGRESS.md').decode(),re.M),'Operations promoted phase/demo criteria')
    sql=(R/'backend/src/main/resources/db/migration/V17__synthetic_room_turnover.sql').read_text();require(set(re.findall(r'CREATE TABLE ([a-z_]+[.][a-z_]+)',sql))=={'cinenest_rooms.turnover_'+x for x in ['case','entry','receipt','audit']},'Operations datastore scope changed')
    require(all(x not in sql for x in ['cinenest_booking.','cinenest_access.','cinenest_iot.','cinenest_payments.']),'Operations foreign datastore mutation')
    if state=='VERIFIED':
        require(all(evidence['checks'].get(x)=='PASS' for x in ['backend','frontend','browser','architecture','launchers','governance']) and evidence['sourceCI']=='SUCCESS','Operations closure lacks checks/CI')
        source=evidence.get('sourceCommit','');require(bool(re.fullmatch('[0-9a-f]{40}',source)),'Operations source receipt invalid')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in owned.items():require(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',source+':'+name])).hexdigest()==digest,'Operations CI source stale '+name)
    if errors:print('\n'.join('FAIL '+x for x in errors));return 1
    replacements={R/name:prior(name) for name in set(owned)&baseline}
    for name in ['execution/change-sets.json','execution/status.json','execution/requirements.json','execution/test-plan.json','PROGRESS.md']:replacements[K/name]=prior('knowledge/'+name)
    rb0=Path.read_bytes;rt0=Path.read_text;g0=Path.glob;o0=subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(args,*a,**kw):
        result=o0(args,*a,**kw)
        if args[0]=='git' and (('diff' in args and '--name-only' in args) or 'ls-files' in args):
            text=result if isinstance(result,str) else result.decode();removed=set(owned) if 'diff' in args else added;text=''.join(line+'\n' for line in text.splitlines() if line not in removed);return text if isinstance(result,str) else text.encode()
        return result
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ops_readiness_main()
    if result==0:print('PASS operations implementation: exact authority/owned source, unchanged dependency/full coverage, synthetic-only safety and historical receipts')
    return result

def report_readiness_main():
    """Validate exact approved report policy/edge delta, then replay frozen prior receipts in memory."""
    from unittest.mock import patch
    base='d09586acc402bc19f6548c132364a127ea4c3b49'
    expected={'decisions/cinenest-open-decisions.json': '8c5609386c21141b487f97760ef31521d74be94822f97dd00e92199752df69c8', 'execution/omissions.json': '90d2872f68b3520b6f10ae063a51cb7ddc61de97eed8493fcb9ceff5c2db0c3c', 'execution/change-sets.json': '093817fe9e614266a0688c6a5d58d61ed13fb0ca56a900c6d4f2646e3ed34a1e', 'execution/status.json': 'b3f3dab081346d2469cf96dc4cad8fd8a1fe7994056c031a4a4274f6d210dfa5'}
    errors=[]
    def require(ok,message):
        if not ok:errors.append(message)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',base+':'+name])
    actual={name:json.loads((K/name).read_text()) for name in expected}
    for name,digest in expected.items():require(hashlib.sha256(json.dumps(actual[name],sort_keys=True).encode()).hexdigest()==digest,'Reporting approved registry/history changed '+name)
    tasks=actual['execution/change-sets.json'];old=json.loads(prior('knowledge/execution/change-sets.json'));task=next(t for t in tasks['changeSets'] if t['id']=='CN-REPORT-001');original=next(t for t in old['changeSets'] if t['id']=='CN-REPORT-001')
    require(task['dependencies']==['CN-PAY-BANK-002','CN-REFUND-001'] and task['historicalImplementationDependencies']==['CN-PAY-GW-001','CN-PAY-BANK-002'],'Reporting exact owner-approved dependency amendment lost')
    require(task['deferredAcceptanceDependencies'][0]['taskId']=='CN-PAY-GW-001' and task['deferredAcceptanceDependencies'][0]['status']=='DEFERRED_BLOCKED_NOT_IMPLEMENTED','Reporting gateway acceptance obligation lost')
    normalized=json.loads(json.dumps(tasks));row=next(t for t in normalized['changeSets'] if t['id']=='CN-REPORT-001');row['dependencies']=original['dependencies']
    for key in set(row)-set(original):row.pop(key)
    require(normalized==old,'Reporting changed unrelated task/status/edge/original scope')
    require(task['workStatus']=='BLOCKED' and task['readiness']=='BLOCKED' and task['authorityAvailable'] is False and task['implementationAuthorization']=='NOT_GRANTED_READINESS_ONLY' and task['remainingReadinessGates']==['Separate owner implementation authorization'],'Reporting authorization/status promoted')
    require(all(next(t for t in tasks['changeSets'] if t['id']==dep)['workStatus']=='VERIFIED' for dep in task['dependencies']),'Reporting hard dependency not verified')
    decisions=json.loads(json.dumps(actual['decisions/cinenest-open-decisions.json']));decision=next(x for x in decisions['decisions'] if x['id']=='OD-16');approval=decision.pop('boundedBankReportingApproval',None)
    require(decisions==json.loads(prior('knowledge/decisions/cinenest-open-decisions.json')) and decision['decisionStatus']=='OPEN_DECISION','Reporting globally closed/altered decision')
    require(approval==task['boundedScopeApproval'] and approval['implementationAuthorization']=='NOT_GRANTED_READINESS_ONLY','Reporting bounded approval/source mismatched')
    omissions=json.loads(json.dumps(actual['execution/omissions.json']));om=next(x for x in omissions['omissions'] if x['id']=='OM-06');mapping=om.pop('boundedBankReportingMapping',None)
    require(omissions==json.loads(prior('knowledge/execution/omissions.json')) and om['status']=='OPEN' and mapping['status']=='APPROVED_MAPPING_NOT_IMPLEMENTED','Reporting OM-06 wider requirements/status changed')
    status=json.loads(json.dumps(actual['execution/status.json']));receipt=status.pop('reportReadiness',None);previous=json.loads(prior('knowledge/execution/status.json'));status['nextAction']=previous['nextAction'];require(status==previous and receipt['approval']==approval and receipt['migrationAuthorization']=='NONE','Reporting changed prior execution history/migration authority')
    for name in ['execution/requirements.json','execution/test-plan.json','execution/source-ledger.json','sources/cinenest-source-register.json']:require((K/name).read_bytes()==prior('knowledge/'+name),'Reporting altered full requirement/test/source record '+name)
    baseline=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',base,'--','backend','frontend'],text=True).splitlines())
    inventory=set(subprocess.check_output(['git','-C',str(R),'-c','core.quotePath=false','ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines());require(inventory==baseline,'Reporting approval created application files')
    for name in baseline-{'frontend/next-env.d.ts'}:require((R/name).read_bytes()==prior(name),'Reporting approval altered application '+name)
    progress=(K/'PROGRESS.md').read_text();require(re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',progress,re.M)==re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',prior('knowledge/PROGRESS.md').decode(),re.M),'Reporting approval promoted roadmap/demo criteria')
    require('| CN-REPORT-001 | 9 | Core authorized money/booking projection | CN-PAY-BANK-002, CN-REFUND-001 | BLOCKED / BLOCKED |' in progress,'Reporting active task projection stale')
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    replacements={K/name:prior('knowledge/'+name) for name in expected};replacements[K/'PROGRESS.md']=prior('knowledge/PROGRESS.md')
    rb0=Path.read_bytes;rt0=Path.read_text
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt):result=ops_implementation_main()
    if result==0:print('PASS report readiness: exact bounded approval/dependency amendment, deferred gateway/full coverage retained, no implementation authority/application change; historical receipts preserved')
    return result

def report_implementation_main():
    """Qualify the authorized source successor before immutable historical receipt replay."""
    from unittest.mock import patch
    base='8d669e72c45748c43f5405f076886c54af7d605e';errors=[]
    def require(ok,message):
        if not ok:errors.append(message)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',base+':'+name])
    tasks=json.loads((K/'execution/change-sets.json').read_text());old=json.loads(prior('knowledge/execution/change-sets.json'));task=next(t for t in tasks['changeSets'] if t['id']=='CN-REPORT-001');expected=next(t for t in old['changeSets'] if t['id']=='CN-REPORT-001')
    for key in ['authorityAvailable','implementationAuthorization','remainingReadinessGates','readiness','workStatus','evidencePaths']:expected[key]=task[key]
    require(tasks==old,'Report changed dependency/scope/unrelated task/history')
    status=json.loads((K/'execution/status.json').read_text());evidence=status.pop('reportImplementation');previous=json.loads(prior('knowledge/execution/status.json'));status['nextAction']=previous['nextAction'];require(status==previous,'Report changed prior execution history')
    authority=task['implementationAuthorization'];require(authority==evidence['authority'] and authority.get('scope')=='GRANTED_BOUNDED_SYNTHETIC_BANK_REPORT' and authority.get('requestSha256')=='6bdc8a350a30d4731a784291f3d7392b9dc3e2f9cb1256355c4b353451ac6121' and task['authorityAvailable'] and task['remainingReadinessGates']==[],'Report implementation authority invalid')
    state=evidence['workStatus'];require(task['workStatus']==state and task['readiness']==('CLOSED' if state=='VERIFIED' else 'READY'),'Report status mismatched')
    for key,value in {'realFinancialReporting':'DISABLED','exports':'DISABLED','gateway':'DEFERRED_BLOCKED','migration':'NONE_V17_UNCHANGED','fullRequirementAcceptance':'NOT_ACCEPTED'}.items():require(evidence[key]==value,'Report safety boundary changed '+key)
    owned=evidence['ownedFiles'];require(set(owned)=={'backend/src/main/java/com/cinenest/payments/BankBalanceReport.java', 'backend/src/main/java/com/cinenest/payments/internal/BankReportRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/BankReportMath.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcBankReportRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/ReportInvariant.java', 'backend/src/main/java/com/cinenest/rooms/internal/ReportingClockAdapter.java', 'backend/src/test/java/com/cinenest/payments/internal/ReportFaults.java', 'frontend/components/shell.tsx', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'frontend/tests/bank-report-browser.mjs', 'backend/src/test/java/com/cinenest/FoundationArchitectureTest.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/test/java/com/cinenest/reporting/internal/BankReportControllerTest.java', 'frontend/app/api/admin/venues/[venueId]/reports/synthetic-bank-balances/route.ts', 'frontend/components/bank-report.tsx', 'backend/src/main/java/com/cinenest/reporting/internal/BankReportController.java', 'frontend/lib/bank-backend.ts', 'frontend/tests/bank-report.test.mjs', 'backend/src/main/java/com/cinenest/reporting/package-info.java', 'backend/src/main/java/com/cinenest/rooms/VenueReportingClock.java', 'frontend/package.json', 'frontend/app/admin/reports/bank/page.tsx', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/test/java/com/cinenest/payments/internal/BankReportMathTest.java', 'backend/src/main/java/com/cinenest/payments/internal/BankReportAdapter.java'},'Report source allowlist changed')
    for name,digest in owned.items():require((R/name).is_file() and hashlib.sha256((R/name).read_bytes()).hexdigest()==digest,'Report owned source mismatch '+name)
    baseline=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',base,'--','backend','frontend'],text=True).splitlines());added=set(owned)-baseline
    inventory=set(subprocess.check_output(['git','-C',str(R),'-c','core.quotePath=false','ls-files','--cached','--others','--exclude-standard','--','backend','frontend'],text=True).splitlines());require(inventory==baseline|added,'Report unrelated application inventory changed')
    for name in baseline-set(owned)-{'frontend/next-env.d.ts'}:require((R/name).read_bytes()==prior(name),'Report changed unrelated application '+name)
    require({p.name for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}=={Path(n).name for n in baseline if '/db/migration/' in n},'Report created a migration')
    omissions=json.loads((K/'execution/omissions.json').read_text());om=next(x for x in omissions['omissions'] if x['id']=='OM-06');mapping=om.pop('boundedBankReportImplementationEvidence',None)
    require(omissions==json.loads(prior('knowledge/execution/omissions.json')) and om['status']=='OPEN' and (mapping is None if state!='VERIFIED' else mapping is not None and mapping['status']=='VERIFIED' and mapping['taskId']=='CN-REPORT-001' and mapping['evidencePath']=='PROGRESS.md#report-bank-implementation'),'Report bounded mapping/wider omission status changed')
    for name in ['decisions/cinenest-open-decisions.json','execution/source-ledger.json','sources/cinenest-source-register.json']:require((K/name).read_bytes()==prior('knowledge/'+name),'Report altered prior approval/source '+name)
    for name,key,ids in [('execution/requirements.json','requirements',{'CN-A01','CN-NFR-03','BP-AC13','BN-M06'}),('execution/test-plan.json','tests',{'T-CN-A01','T-CN-NFR-03','T-BP-AC13','T-BN-M06'})]:
        doc=json.loads((K/name).read_text())
        for row in doc[key]:
            receipt=row.pop('boundedBankReportImplementationEvidence',None);require((receipt is not None)==(row['id'] in ids),'Report bounded coverage mismatch '+row['id'])
            if receipt:require(receipt['fullAcceptance']=='NOT_ACCEPTED' and receipt['status']==state,'Report full acceptance promoted '+row['id'])
        require(doc==json.loads(prior('knowledge/'+name)),'Report altered full requirement/test history '+name)
    progress=(K/'PROGRESS.md').read_text();require(re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',progress,re.M)==re.findall(r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))',prior('knowledge/PROGRESS.md').decode(),re.M),'Report promoted phase/demo criteria')
    require('| CN-REPORT-001 | 9 | Core authorized money/booking projection | CN-PAY-BANK-002, CN-REFUND-001 | '+state+' / '+task['readiness']+' |' in progress,'Report active task projection stale')
    sql=(R/'backend/src/main/java/com/cinenest/payments/internal/JdbcBankReportRepository.java').read_text();require("c.source_kind='SYNTHETIC_BANK'" in sql and all(x not in sql for x in ['UPDATE ','DELETE ','cinenest_booking.','cinenest_rooms.']),'Report source/query ownership changed')
    require('ISOLATION_REPEATABLE_READ' in (R/'backend/src/main/java/com/cinenest/payments/internal/BankReportAdapter.java').read_text(),'Report snapshot isolation lost')
    if state=='VERIFIED':
        require(all(evidence['checks'].get(k)=='PASS' for k in ['backend','frontend','browser','architecture','launchers','governance']) and evidence['sourceCI']=='SUCCESS','Report closure lacks checks/CI')
        source=evidence.get('sourceCommit','');require(bool(re.fullmatch('[0-9a-f]{40}',source)),'Report CI source receipt invalid')
        if re.fullmatch('[0-9a-f]{40}',source):
            for name,digest in owned.items():require(hashlib.sha256(subprocess.check_output(['git','-C',str(R),'show',source+':'+name])).hexdigest()==digest,'Report CI source stale '+name)
    if errors:print('\n'.join('FAIL '+x for x in errors));return 1
    replacements={R/name:prior(name) for name in set(owned)&baseline}
    for name in ['execution/change-sets.json','execution/status.json','execution/requirements.json','execution/test-plan.json','execution/omissions.json','PROGRESS.md']:replacements[K/name]=prior('knowledge/'+name)
    rb0=Path.read_bytes;rt0=Path.read_text;g0=Path.glob;o0=subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(args,*a,**kw):
        result=o0(args,*a,**kw)
        if args[0]=='git' and (('diff' in args and '--name-only' in args) or 'ls-files' in args):
            text=result if isinstance(result,str) else result.decode();removed=set(owned) if 'diff' in args else added;text=''.join(line+'\n' for line in text.splitlines() if line not in removed);return text if isinstance(result,str) else text.encode()
        return result
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=report_readiness_main()
    if result==0:print('PASS report implementation: qualified exact source/authority/snapshot ownership, unchanged schema/dependencies/full acceptance and historical receipts')
    return result


def engagement_decision_main():
    """Validate the exact owner-approved documentation transition, then replay prior receipts."""
    from unittest.mock import patch
    base='ca7f70edc984a9c109b4d1d7cfe70f0fb4cfcabd'
    errors=[]
    def require(ok,message):
        if not ok: errors.append(message)
    def prior(name): return subprocess.check_output(['git','-C',str(R),'show',base+':knowledge/'+name])
    transitions={'execution/change-sets.json': {'changeSets': {'CN-ENG-001': {'authorityAvailable': True, 'readiness': 'CLOSED', 'workStatus': 'VERIFIED', 'evidencePaths': ['PROJECT.md#engagement-execution-deferral', 'PROGRESS.md#engagement-decision-closure']}}}, 'decisions/cinenest-open-decisions.json': {'decisions': {'OD-08': {'decisionStatus': 'APPROVED', 'owner': 'Hasitha Wijerathna (project owner)', 'deferredExecution': {'scope': 'INITIAL_COMMERCIAL_RELEASE_ONLY', 'loyalty': 'DEFER_EXECUTION', 'referrals': 'DEFER_EXECUTION', 'promotions': 'DEFER_EXECUTION', 'policyValues': 'UNAPPROVED', 'fullScope': 'PRESERVED', 'approvalSource': 'Current owner instruction: CN-ENG-001 — Record Owner Engagement Feature Decisions', 'evidencePath': 'PROJECT.md#engagement-execution-deferral'}}}}, 'execution/requirements.json': {'requirements': {'CN-C09': {'executionDeferralEvidence': 'PROJECT.md#engagement-execution-deferral'}, 'CN-A09': {'executionDeferralEvidence': 'PROJECT.md#engagement-execution-deferral'}}}, 'execution/omissions.json': {'omissions': {'OM-08': {'gap': 'Engagement execution explicitly deferred for initial commercial release; future commercial rules and implementation remain unapproved', 'status': 'OPEN', 'recovery': 'Explicit reactivation, versioned rules, abuse/reversal/privacy controls, separate implementation authority and acceptance; CN-C09/CN-A09 retained', 'deferralEvidence': 'PROJECT.md#engagement-execution-deferral'}}}}
    replacements={}
    for name,groups in transitions.items():
        old=json.loads(prior(name));actual=json.loads((K/name).read_text())
        for group,rows in groups.items():
            for ident,fields in rows.items():
                next(x for x in old[group] if x['id']==ident).update(fields)
        require(actual==old,'Engagement unauthorized registry/status/dependency/acceptance transition '+name)
        replacements[K/name]=prior(name)
    name='execution/status.json';old=json.loads(prior(name))
    old['nextAction']='Read-only review of CN-ACCEPT-001 prerequisites and remaining acceptance gates; PayHere deferred/BLOCKED'
    old['engagementDecisionClosure']={'taskId':'CN-ENG-001','scope':'DOCUMENTATION_ONLY','workStatus':'VERIFIED','featuresImplemented':False,'fullAcceptance':'NOT_ACCEPTED','policyValues':'UNAPPROVED','dependencies':'UNCHANGED','evidencePath':'PROGRESS.md#engagement-decision-closure','baselineHead':base}
    require(json.loads((K/name).read_text())==old,'Engagement execution evidence/history changed')
    replacements[K/name]=prior(name)
    require((K/'execution/test-plan.json').read_bytes()==prior('execution/test-plan.json'),'Engagement planned tests changed/promoted')
    progress=(K/'PROGRESS.md').read_text();previous=prior('PROGRESS.md').decode()
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    require(re.findall(pattern,progress,re.M)==re.findall(pattern,previous,re.M),'Engagement phase/demo acceptance changed')
    require('| CN-ENG-001 | 10 | Engagement scope/rule decision brief | CN-REPORT-001 | VERIFIED / CLOSED |' in progress,'Engagement task projection stale')
    require('25 CLOSED,3 BLOCKED,0 READY,0 IN_PROGRESS /28' in progress,'Engagement task count stale')
    require('<a id="engagement-execution-deferral"></a>' in (K/'PROJECT.md').read_text(),'Engagement authority reference missing')
    allowed={'knowledge/PROJECT.md','knowledge/ARCHITECTURE.md','knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','knowledge/execution/change-sets.json','knowledge/execution/status.json','knowledge/execution/requirements.json','knowledge/execution/omissions.json','knowledge/decisions/cinenest-open-decisions.json','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',base,'--name-only'],text=True).splitlines())
    require(changed<=allowed,'Engagement changed unrelated files: '+str(changed-allowed))
    # Application inventory/content checks remain enforced by the prior implementation validator.
    if errors:
        print('\n'.join('FAIL '+e for e in errors));return 1
    replacements[K/'PROGRESS.md']=prior('PROGRESS.md')
    require('| OD-08 | Initial-release execution deferral APPROVED; future earning/reversal/expiry/redemption/stacking rules unapproved; [decision](PROJECT.md#engagement-execution-deferral) | FEATURE_LAUNCH_BLOCKER on reactivation |' in (K/'ARCHITECTURE.md').read_text(),'Engagement decision projection stale')
    if errors:
        print('\n'.join('FAIL '+e for e in errors));return 1
    replacements[K/'ARCHITECTURE.md']=prior('ARCHITECTURE.md')
    rb0=Path.read_bytes;rt0=Path.read_text
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt):result=report_implementation_main()
    if result==0: print('PASS engagement decision: exact owner deferral, brief-only closure, unchanged dependencies/full acceptance/test plans, historical receipts retained')
    return result

def historical_inert_ci_main():
    """Qualify the owner-authorized CI successor before immutable historical replay."""
    from unittest.mock import patch
    baseline = '14c4602b8b3a6002756fb1b04dd282c313a44c16'
    owned = {'.github/workflows/foundation.yml': 'cce9bf6b8231e2378e59ad705226004bb6429b89a6783abe0e4804e5fcfbeefb', 'scripts/ci-classify.py': '10ea359b7cf88a9a7121e081dc3c4b0ceb5abf5fc5b474dda1f12d821ccfe89b', 'scripts/test-ci-classify.py': '597d2f19408e020c05ae800c10a353a19a364f765e1e1f1de3961888da671786', 'scripts/test-governance-controls.py': 'b2fc80add6c6ee1c85eb884aa9b55ad953278f19f82210b46ee83cf0c1121f4e'}
    # Owner-authorized inert successor; operational requirements remain blocked.
    owned.update({'scripts/verify-recovery.py': '8637602691226a537ed25dc474038a5aac5b847d111b7ca788f6e087e9d23356', 'scripts/test-recovery.py': '0d25534a0d02f4b51552c1599973f24e2e88589cd73f4a238f761417b7ab1922'})
    errors = []
    for name, digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest() != digest:
            errors.append('CI successor source mismatch ' + name)
    for name in ['execution/change-sets.json', 'execution/requirements.json', 'execution/test-plan.json',
                 'execution/status.json', 'decisions/cinenest-open-decisions.json']:
        prior = subprocess.check_output(['git', '-C', str(R), 'show', baseline+':knowledge/'+name])
        if (K/name).read_bytes() != prior:
            errors.append('CI successor changed task/requirement/decision record ' + name)
    architecture = (K/'ARCHITECTURE.md').read_text()
    if '<a id="inert-ci-classification-boundary"></a>' not in architecture or 'dfa9fad1-83c9-490e-a77b-466160ed0a29' not in architecture:
        errors.append('CI successor approval missing')
    if '<a id="inert-controller-implementation"></a>' not in architecture or '1c8b4f41-9c1e-4ea2-a923-94b2a6afdc71' not in architecture:
        errors.append('Inert controller implementation authority missing')
    allowed = set(owned) | {'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md', 'knowledge/manifest.json',
                           'knowledge/execution/validate_execution.py', 'frontend/next-env.d.ts'}
    changed = set(subprocess.check_output(['git', '-C', str(R), 'diff', baseline, '--name-only'], text=True).splitlines())
    if not changed <= allowed:
        errors.append('CI successor unrelated change ' + str(changed-allowed))
    if errors:
        print('\n'.join('FAIL '+e for e in errors))
        return 1
    original = subprocess.check_output
    def historical_output(argv, *args, **kwargs):
        result = original(argv, *args, **kwargs)
        # Only the exact, hash-qualified CI source delta is removed from the
        # historical publication allowlist. All other changes remain visible.
        if isinstance(argv, list) and argv[:3] == ['git', '-C', str(R)] and 'diff' in argv and '--name-only' in argv:
            if isinstance(result, str):
                return '\n'.join(x for x in result.splitlines() if x not in owned) + '\n'
            return b'\n'.join(x for x in result.splitlines() if x.decode() not in owned) + b'\n'
        return result
    workflow = R / '.github/workflows/foundation.yml'
    previous_workflow = original(['git', '-C', str(R), 'show', baseline+':.github/workflows/foundation.yml'])
    rb, rt = Path.read_bytes, Path.read_text
    def historical_bytes(path, *args, **kwargs):
        return previous_workflow if path == workflow else rb(path, *args, **kwargs)
    def historical_text(path, *args, **kwargs):
        return previous_workflow.decode() if path == workflow else rt(path, *args, **kwargs)
    with patch.object(subprocess, 'check_output', historical_output), \
         patch.object(Path, 'read_bytes', historical_bytes), patch.object(Path, 'read_text', historical_text):
        result = engagement_decision_main()
    if result == 0:
        print('PASS scoped CI successor: owned hashes, unchanged task/requirement/decision records, historical checks retained')
    return result


def backend_handoff_main():
    """Qualify the exact owner-authorized handoff successor before historical replay.

    Existing receipts keep their original bytes. Only hash-qualified task sources
    are projected back during replay; all other application/acceptance checks stay live.
    """
    from unittest.mock import patch
    baseline = 'f3275e14b12d1818b151dab9b02a7d31a63dd967'
    owned = {'backend/src/main/java/com/cinenest/pricing/VerificationQuotation.java': '7da4723f417d0d233a823602e693d58d5f7a06715ae1d374883b135893beb044', 'backend/src/main/java/com/cinenest/pricing/internal/QuoteService.java': '0515df8644d1a8497915c19b5f72143597a251d1ac7a8c583eaae58989c7f399', 'backend/src/main/java/com/cinenest/pricing/internal/VerificationQuotationAdapter.java': '05df3d34219778383dad05f39ee2cd1405817895779a26674dc8f38672d2fc75', 'backend/src/main/java/com/cinenest/verification/internal/VerificationService.java': '32c879d64cdd4f36dd26848a5c25d62545a3fae387dc140601260f9b4f1721c5', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '026c23634f32c2d7524dc2f2707f9fac6606cee9d9c87210f6b269b161232782', 'backend/src/test/java/com/cinenest/VerificationIntegrationTest.java': 'fb12a6be7f35777a1419191bdad1f6fe514e04214bc299303c1043e060f95f13', 'backend/src/test/java/com/cinenest/pricing/internal/VerificationHandoffTest.java': '1e233dc625230abcf825bc8fc580ba81fb60835fae28b63579fb8fa25adeeece'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:
            errors.append('Handoff successor source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Handoff changed task/dependency/acceptance history '+name)
    architecture=(K/'ARCHITECTURE.md').read_text()
    if '<a id="verification-handoff-eligibility"></a>' not in architecture or '25561861-2c12-43de-a23d-de4f3e89c746' not in architecture:errors.append('Handoff owner authority missing')
    progress=(K/'PROGRESS.md').read_text()
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Handoff promoted phase/demo acceptance')
    if '<a id="verification-handoff-implementation"></a>' not in progress:errors.append('Handoff evidence missing')
    added={'backend/src/test/java/com/cinenest/pricing/internal/VerificationHandoffTest.java'}
    allowed=set(owned)|{'knowledge/ARCHITECTURE.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Handoff unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode()
            text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=historical_inert_ci_main()
    if result==0:print('PASS handoff successor: exact owned source/owner approval; unchanged requirements, dependencies, phase/demo gates; historical integrity retained')
    return result


def ui_flow_main():
    """Validate the exact frontend successor, then replay immutable backend/CI receipts."""
    from unittest.mock import patch
    baseline='7f64e34cd790b8dbcfc3a77b93cc0ad229158986'
    owned={'frontend/app/globals.css': '7b579873b6e7470cae3792045198e1554071968657d61f68ea6345c6084d53d6', 'frontend/app/verification/page.tsx': 'c0c98d0d8a8eebf752e5110de25f892578b902c919bfc6091c841cd0121f6aa7', 'frontend/components/quotes.tsx': 'e04f28304eb90c960d8b9218f97beb170897e16b688d2f6c95903c8a2ddd2aa3', 'frontend/components/verification.tsx': '2f97a5e134bf360b4ebc3d7ed9ccee52a489eb3c179f24b622f6f3697ce6b6a3', 'frontend/tests/quotes-browser.mjs': 'b7aac6ee7b03491e39fdab9749c3eabdf26ba36b4ed1da7bff85b5b67e4be8d6', 'frontend/tests/quotes.test.mjs': '425178682a75d22695ac531574c684ddd593fa5741bb3f016bd37d4565738d19', 'frontend/tests/verification-browser.mjs': '5f31fbaa3bda3d1acfdc7bea6cbfe8fb5a8285c1fd7d3f11cde29611c1e7e658', 'frontend/lib/quote-handoff.ts': '5c9d6b8f1fcf9e3b2a41b91548f7ca9350a4e93d68964d74b449afeda0ee6ca7', 'frontend/components/quote-handoff.tsx': 'b892c533bde067c14f14e77c85800eb653dd28aef1437cfbe22b8eeea3c2796b'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('UI flow successor source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('UI flow changed dependency/requirement/approval history '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('UI flow rewrote prior design authority')
    if '<a id="quote-verification-handoff"></a>' not in design or '3af434ff-9b75-4285-93c1-90d7db5ef3dc' not in design:errors.append('UI flow owner authority missing')
    if '<a id="ui-flow-001-implementation"></a>' not in progress or '39286392998 COMPLETED/SUCCESS' not in progress:errors.append('UI flow prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('UI flow promoted phase/demo acceptance')
    added={'frontend/lib/quote-handoff.ts','frontend/components/quote-handoff.tsx'}
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('UI flow unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=backend_handoff_main()
    if result==0:print('PASS UI flow successor: exact frontend ownership/authority; unchanged backend, dependencies, full requirements and acceptance; historical receipts retained')
    return result


def bank_readiness_main():
    """Qualify exact owner-authorized bank readiness sources before historical replay.

    No application inventory exemption applies until source hashes and scope,
    unchanged registries and preserved acceptance gates have all been checked.
    """
    from unittest.mock import patch
    baseline='1c1cc57ced53d3e2ed469148ec107ed84c88d4fa'
    owned={'backend/src/main/java/com/cinenest/booking/BankRequestHolds.java': 'f41100b11f22fb65622d4bd61ce8e1db3d6e59b6fe0c0e910f31d236cbeafa63', 'backend/src/main/java/com/cinenest/booking/QuotationHolds.java': 'e98d09f2890f4a68549766c7a61f0a293e5348a67cc3de001efa06f56d8e30df', 'backend/src/main/java/com/cinenest/booking/internal/BankRequestHoldAdapter.java': '5d32b0797cb91e828c5b4fdfa673492f3d94b3c977f9291aa2dbd4c1ee9bdb89', 'backend/src/main/java/com/cinenest/booking/internal/InventoryService.java': 'a5b08443b1ef0ae9a5840b53c5e3be2ef1bceab44b02e31b1e8dd21a0b9c6eee', 'backend/src/main/java/com/cinenest/payments/internal/BankController.java': 'acc657c0cde6828aec998529b04813660fa584452fb1a8c6f1a5d17a49eb21b8', 'backend/src/main/java/com/cinenest/payments/internal/BankService.java': 'fd81940fd0aef0967bb911096373ae2a45b3e5c3ec0e4089bc225878e9b26a1f', 'backend/src/main/java/com/cinenest/pricing/BankQuotation.java': '95041bffbb97b94e4b951782b95d4e88179f68f094caa9f1d17ba425da6cd9d7', 'backend/src/main/java/com/cinenest/pricing/internal/BankQuotationAdapter.java': '81153dea26bcef7ee3c1b14a6ef394f6d78747a4a786b77e20f0714f3c60a387', 'backend/src/main/java/com/cinenest/rooms/InventoryConstraints.java': '4b0e18583014b690fb74024b44454c512ce42b2f50cc8d8fa174769dc450303c', 'backend/src/main/java/com/cinenest/rooms/internal/InventoryConstraintAdapter.java': '10c04bb631bf1bc60fb07a2cfdce730ea280d18ae92f36db322c5fbcb021dc1d', 'backend/src/main/java/com/cinenest/verification/BankEligibility.java': 'e64ce20f958626a814e006527f508276467c6e772a66d51ac73277b99edc30d9', 'backend/src/main/java/com/cinenest/verification/internal/BankEligibilityAdapter.java': 'ccfadb918ee5e985568324cd72858e3edb75ef8566dce361226cc4dd26e32db8', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '9ad44211086ebbaebedd3489e641deafce09215dcdc170d9b82f9abdb1f9c592', 'backend/src/main/java/com/cinenest/payments/internal/BankCreationRules.java': '93664bcb2712b55f053d1953bbbb0cf989eca53b09efae9440f081b651c5dd59', 'backend/src/test/java/com/cinenest/payments/internal/BankCreationRulesTest.java': '02af46f57d3d8a13f45508f822dd91298e8ef796f8d393b567f0df1e3a88c680', 'backend/src/test/java/com/cinenest/payments/internal/BankReadinessTest.java': 'b2a3ec50a934de49102cc46edf749f41a03b173ee5578672c0128aa387a4d227', 'backend/src/test/java/com/cinenest/booking/internal/BankConversionRulesTest.java': 'd24ec350a3b3e52949be3fbbca4e1da50d840cdf6d3eaed17a3a590762894886', 'backend/src/test/java/com/cinenest/pricing/internal/BankQuotationObservationTest.java': 'faef7574daf84043449bebf2b3d2c5e3622585c21b81bf5d31823ce34952db55'}
    added={'backend/src/test/java/com/cinenest/booking/internal/BankConversionRulesTest.java', 'backend/src/test/java/com/cinenest/payments/internal/BankCreationRulesTest.java', 'backend/src/main/java/com/cinenest/payments/internal/BankCreationRules.java', 'backend/src/test/java/com/cinenest/pricing/internal/BankQuotationObservationTest.java', 'backend/src/test/java/com/cinenest/payments/internal/BankReadinessTest.java'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:
            errors.append('Bank readiness source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Bank readiness changed dependencies/requirements/acceptance '+name)
    architecture=(K/'ARCHITECTURE.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not architecture.startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Bank readiness rewrote prior architecture authority')
    if '<a id="bank-request-readiness"></a>' not in architecture or 'd3cb6a54-0bea-4e7d-8cf0-264c0df6a609' not in architecture:errors.append('Bank readiness owner approval missing')
    if '<a id="bank-handoff-implementation"></a>' not in progress or 'CI39297920501 COMPLETED/SUCCESS' not in progress:errors.append('Bank readiness prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Bank readiness promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/ARCHITECTURE.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Bank readiness unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/ARCHITECTURE.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ui_flow_main()
    if result==0:print('PASS bank readiness successor: exact owned sources/authority; unchanged dependencies, full requirements and phase/demo acceptance; historical integrity retained')
    return result


def ui_flow_002_main():
    """Validate the exact authorized UI-flow-002 successor before historical replay."""
    from unittest.mock import patch
    baseline='7a52544e8fc1400afeacfb9f5c357178bb05f66f'
    owned={'frontend/app/bank/page.tsx': 'db7369e89cade948d45a5592f96f29116bff1dbe0b85cf1c0db7c4da959b3768', 'frontend/app/globals.css': '668ea3cf836029f3b682111c9df431a2fc88c1ae02b528c44ed593d6b78f4341', 'frontend/components/bank.tsx': 'ea46f0bd707ec1ff6a67910a116cf6dc3be997b1fa28709a91eb7cac6328dc8f', 'frontend/components/verification.tsx': '9250cf27d7e877425bb584868b2cc8fc8b4eddb93d981b470f3a9ac3b8c5e3c5', 'frontend/lib/bank-backend.ts': 'acf9f0818103ccb0ac6ff483c1476eceeb72f30847a99e7bb8cdbf1a5cf9da63', 'frontend/tests/bank-browser.mjs': '3c4109561cb3aa4a17dcb8f593409bd53eec5e9b6a95096d67fc67a6e2b81f0b', 'frontend/tests/bank.test.mjs': 'a7dbdbe029b6b6565b79721bab0292c639b4b9c0fb1b167b28fa29b2b6a875e0', 'frontend/tests/verification-browser.mjs': 'f0049e55f579781a0c3a6b0287e328b82ca7218cc33e41570f9d95df1ed87868', 'frontend/components/bank-handoff.tsx': 'ea0b8d3f395c40d72b328c498551864b752319310f10f6c7fc9fd3d33e98d151', 'frontend/lib/bank-handoff.ts': 'b753a78c1b22ae3705b3d23946821a22a7e55452c25aa7e00153bb7e9f58f38f', 'frontend/app/api/bank/requests/readiness/route.ts': '4251601820fa102dfc138984712694a6c35ff57844b46dff2e4d8f3bc42dba3d', 'frontend/tests/bank-handoff-browser.mjs': '5333cd282a45726f469e9196e83876b8b3b3ba80dd128bc209832fe8f358cc52'}
    added={'frontend/tests/bank-handoff-browser.mjs', 'frontend/lib/bank-handoff.ts', 'frontend/components/bank-handoff.tsx', 'frontend/app/api/bank/requests/readiness/route.ts'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Bank UI handoff source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Bank UI handoff changed registered dependency/requirement/acceptance '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Bank UI handoff rewrote prior design authority')
    if '<a id="verification-bank-handoff"></a>' not in design or 'fce98c5d-edb5-4021-8e92-3f21deb154b8' not in design:errors.append('Bank UI handoff owner authority missing')
    if '<a id="ui-flow-002-implementation"></a>' not in progress or 'CI39301592319 COMPLETED/SUCCESS' not in progress:errors.append('Bank UI handoff prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Bank UI handoff promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Bank UI handoff unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact source/authority qualification precedes projection of these paths only.
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=bank_readiness_main()
    if result==0:print('PASS bank UI handoff: exact frontend source/owner authority, unchanged backend/dependencies/full acceptance, historical integrity retained')
    return result


def ui_flow_003_main():
    """Validate the exact authorized UI-flow-003 successor before historical replay."""
    from unittest.mock import patch
    baseline='6e9d833070d0cbba1ab37068ec44ad6027af221b'
    owned={'frontend/app/bank/page.tsx': '672023f12c549e6cb9fdaede693ec1d8a160990dcdfe6b9a2908af0284a8d304', 'frontend/components/customer-bookings.tsx': '5c44fffc681aa0ce24962a0053560103dd80788805b9ddc521c5be46bc89b845', 'frontend/tests/bank.test.mjs': '727f8cb387c796dad9d985fb881c9d94971086492d482388284ffb5efa37c6d8', 'frontend/lib/bank-request.ts': '9fa523f5a0867fe728fa92c8dceea21b3aa0737c9a62fce4d9e9d1e7074c9804', 'frontend/components/bank-request-detail.tsx': 'aaddbe77aede0588a32b2a2a01b4674ee09a93f8cfb9cdaf3a95ff643997d8b0', 'frontend/tests/bank-request-browser.mjs': 'fda578d5c71d27487612182bf2f4e20eeab8f655f3a322d3d565c850cc2da3c8'}
    added={'frontend/components/bank-request-detail.tsx', 'frontend/tests/bank-request-browser.mjs', 'frontend/lib/bank-request.ts'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Exact request navigation source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Exact request navigation changed registered dependency/requirement/acceptance '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Exact request navigation rewrote prior design authority')
    if '<a id="history-bank-request"></a>' not in design or 'e5ad9cd8-87bd-43ff-99e9-20e9dd5c4c9c' not in design:errors.append('Exact request navigation owner authority missing')
    if '<a id="ui-flow-003-implementation"></a>' not in progress or 'CI39369656169 COMPLETED/SUCCESS' not in progress:errors.append('Exact request navigation prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Exact request navigation promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Exact request navigation unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact source/authority qualification precedes projection of these paths only.
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ui_flow_002_main()
    if result==0:print('PASS exact request navigation: exact frontend source/owner authority, unchanged backend/dependencies/full acceptance, historical integrity retained')
    return result


def ui_flow_004_main():
    """Validate the exact authorized UI-flow-004 successor before historical replay."""
    from unittest.mock import patch
    baseline='61a9685e08f73e46a499cd914af842ad5d8a3b53'
    owned={'frontend/components/bank.tsx': '2aab1b0481efd9b7164f68a533ebf511f2883557a5c19ea8243ef91cab915188', 'frontend/components/bank-request-detail.tsx': '27158f7c2e9ba828bc4e99f109f5e9485f84810e1d45a490d437a520f6e5ca72', 'frontend/components/customer-bookings.tsx': 'f982d35595290755aaad48ec206d195441e42f41ec819c80a6e6af866f63310b', 'frontend/lib/bank-request.ts': 'bc8587173822eb68650b6ac4740357a35bb9035c42c51d1a8e751cfcd3505297', 'frontend/tests/bank.test.mjs': '9a0e65982366975b9f7e208a85afbbb397aaa977b2def3ceaa5ea65a43c625e7', 'frontend/tests/customer-bookings.test.mjs': '0ef318a3cd79972e51e137839991b739644beb0ca59cd2aacfc05c7e28189e91', 'frontend/tests/customer-bookings-browser.mjs': '8972717cbe17cdb65c56383ba8a226e2500a74f22e7a41901e616389acd13447', 'frontend/tests/booking-handoff-browser.mjs': '43fae245e27f3d441f70a133c42c5f6a55b58686cf89de98b309aec88f7de7a6'}
    added={'frontend/tests/booking-handoff-browser.mjs'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Simulated booking navigation source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Simulated booking navigation changed registered dependency/requirement/acceptance '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Simulated booking navigation rewrote prior design authority')
    if '<a id="simulated-confirmation-booking"></a>' not in design or 'Owner instruction CN-UI-FLOW-004' not in design:errors.append('Simulated booking navigation owner authority missing')
    if '<a id="ui-flow-004-implementation"></a>' not in progress or 'CI39371866547 COMPLETED/SUCCESS' not in progress:errors.append('Simulated booking navigation prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Simulated booking navigation promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Simulated booking navigation unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact source/authority qualification precedes projection of these paths only.
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ui_flow_003_main()
    if result==0:print('PASS simulated booking navigation: exact frontend source/owner authority, unchanged backend/dependencies/full acceptance, historical integrity retained')
    return result


def ui_flow_005_main():
    """Validate the exact authorized UI-flow-005 successor before historical replay."""
    from unittest.mock import patch
    baseline='d850793cddf54e213384b1286ef30cf4d69bc143'
    owned={'frontend/app/page.tsx': 'aa58ace78d8b369ae1fd4a7f20166cc0ce59ba1cd99e8adb683899753034721e', 'frontend/app/globals.css': '77b21547292c12cda5bd45848f17f9fab41b4ed52301e668394f7d96d2e8b0cb', 'frontend/components/shell.tsx': '6e39fbec1a7849b40cf6f5c7baf6c7c1d33abdf23b2d3aebd0dee80831202d4f', 'frontend/tests/foundation.test.mjs': 'a463635c8d9731d80d2b940f99919c576c2da69cdb25f99ee56d50e981241281', 'frontend/tests/ui.test.mjs': '150c92dda17df4e97c7951c6fdeb796ad1f5e1b969b3cf7f5c54b0b8bb0cd959', 'frontend/tests/landing-browser.mjs': '483f73bd678a066ae9008cc9ee0c567e7c709e8ddca1f726381ce12fbe1c3d61'}
    added={'frontend/tests/landing-browser.mjs'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Landing catalog integration source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Landing catalog integration changed registered dependency/requirement/acceptance '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Landing catalog integration rewrote prior design authority')
    if '<a id="landing-catalog-integration"></a>' not in design or 'Owner instruction CN-UI-FLOW-005' not in design:errors.append('Landing catalog integration owner authority missing')
    if '<a id="ui-flow-005-implementation"></a>' not in progress or 'CI39374052483 COMPLETED/SUCCESS' not in progress:errors.append('Landing catalog integration prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Landing catalog integration promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Landing catalog integration unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact source/authority qualification precedes projection of these paths only.
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ui_flow_004_main()
    if result==0:print('PASS landing catalog integration: exact frontend source/owner authority, unchanged backend/dependencies/full acceptance, historical integrity retained')
    return result


def bank_evidence_context_main():
    """Validate the exact authorized bank evidence context successor before historical replay."""
    from unittest.mock import patch
    baseline='e55450a1a3e66030f34947455cd9dc6928ca484f'
    owned={'frontend/app/bank/page.tsx': '56279ae6f2a7a11d7b27d15a7036c0381c28538d5e486717835e258b968c950b', 'frontend/components/bank.tsx': 'b3059ee8b241bc550ab8ab2e4716b769d14b22af5ae7d30cd5d32d4d75373ff2', 'frontend/components/bank-request-detail.tsx': '7a1054e596fb8433b2eea12ef2ee64af12c9745598c7a0e64224ddd04180328a', 'frontend/lib/bank-request.ts': '2d19880bdd18f70a8bdbbc36c73738e0f69f6d164ce74658881e4568dc600d8f', 'frontend/tests/bank.test.mjs': 'f052f092432dda07cffde35777d38f1d46f52c920083ebc4266891fd279cbe4b', 'frontend/tests/bank-evidence-browser.mjs': '72eb65cfb01c62cbdbf2bbd9f3df8ece3f1afdcfa1a7d86e764792ef1bf6fe67'}
    added={'frontend/tests/bank-evidence-browser.mjs'}
    errors=[]
    for name,digest in owned.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Bank evidence context source mismatch '+name)
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name in ['execution/change-sets.json','execution/requirements.json','execution/test-plan.json','execution/status.json','execution/omissions.json','decisions/cinenest-open-decisions.json']:
        if (K/name).read_bytes()!=prior('knowledge/'+name):errors.append('Bank evidence context changed registered dependency/requirement/acceptance '+name)
    design=(K/'DESIGN.md').read_text();progress=(K/'PROGRESS.md').read_text()
    if not design.startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Bank evidence context rewrote prior design authority')
    if '<a id="bank-evidence-context"></a>' not in design or 'Owner instruction CN-BANK-EVIDENCE-CONTEXT-001' not in design:errors.append('Bank evidence context owner authority missing')
    if '<a id="bank-evidence-context-implementation"></a>' not in progress or 'CI39376769458 COMPLETED/SUCCESS' not in progress:errors.append('Bank evidence context prerequisite/evidence missing')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Bank evidence context promoted phase/demo acceptance')
    allowed=set(owned)|{'knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Bank evidence context unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact source/authority qualification precedes projection of these paths only.
    replacements={R/name:prior(name) for name in set(owned)-added}
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|{'knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=ui_flow_005_main()
    if result==0:print('PASS bank evidence context: exact frontend source/owner authority, unchanged backend/dependencies/full acceptance, historical integrity retained')
    return result



def synthetic_e2e_main():
    """Qualify exact opt-in test-only successor, then retain every historical control."""
    from unittest.mock import patch
    baseline='b21e80a1a1d0ae63d79a5b41867d11264203a665'
    owned={'scripts/verify-synthetic-e2e.py': 'a94d902ae9bc4be169d353a154bb08f13d016d95c59f0c41c8004ffe813d6f11', 'scripts/test-synthetic-e2e.py': '38f2cab4212362007e2c1093f023e1aa0378202e5ba62a97a85d37f959f6b07c', 'frontend/tests/synthetic-e2e.mjs': '27cf8f1bc102b39396c472a748f001af156175ec6938647f9a8cadd1aa34a7f0', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '448ddfc57f66851880d3dc7798f4653181f5a8326c0b8b6a81418053cf390bb1', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': 'a7aeb1d91ed8726458d3d80294628b7c2801f395aea9de6aedc614d8080a13f5'}
    registries={'knowledge/execution/status.json': '9d36cb807c5fc3e4376c3b987809109f6b3ba9dcdda59db06f9d8fd2da52444a', 'knowledge/execution/requirements.json': 'e48dbe60f95ebc296532c27531fed9d33558d1f6b8e9646cc6e0594e4a250a16', 'knowledge/execution/test-plan.json': 'b90885e2c20a1e8b3ec494925acd29539a53ac5d6d445eb53fb2128cd76555a4'}
    added=set(owned)-{'backend/src/test/java/com/cinenest/BankIntegrationTest.java'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in {**owned,**registries}.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Synthetic E2E exact source/record mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text());previous=json.loads(prior(name))
        for row in current[key]:row.pop('syntheticE2eEvidence',None)
        if current!=previous:errors.append('Synthetic E2E changed full requirement/test-plan authority '+name)
    state=json.loads((K/'execution/status.json').read_text());state.pop('syntheticE2eImplementation',None)
    if state!=json.loads(prior('knowledge/execution/status.json')):errors.append('Synthetic E2E changed unrelated execution status')
    for name in ['knowledge/execution/change-sets.json','knowledge/execution/omissions.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','backend/pom.xml','frontend/package.json','frontend/package-lock.json']:
        if (R/name).read_bytes()!=prior(name):errors.append('Synthetic E2E changed dependencies/policy/workflow '+name)
    progress=(K/'PROGRESS.md').read_text();architecture=(K/'ARCHITECTURE.md').read_text()
    if not architecture.startswith(prior('knowledge/ARCHITECTURE.md').decode()) or '<a id="synthetic-e2e-harness"></a>' not in architecture:errors.append('Synthetic E2E architecture authority missing/rewritten')
    if '<a id="synthetic-e2e-implementation"></a>' not in progress or '14 groups PASS' not in progress or '9 NOT_RUN' not in progress:errors.append('Synthetic E2E partial failure ledger absent')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Synthetic E2E promoted acceptance')
    allowed=set(owned)|set(registries)|{'knowledge/ARCHITECTURE.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Synthetic E2E unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # New source and bounded evidence have passed exact checks. Replay prior invariants
    # against their prior source, not an unvalidated inventory exemption.
    replacements={R/'backend/src/test/java/com/cinenest/BankIntegrationTest.java':prior('backend/src/test/java/com/cinenest/BankIntegrationTest.java')}
    replacements.update({R/name:prior(name) for name in registries})
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if name in registries:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|{'knowledge/ARCHITECTURE.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=bank_evidence_context_main()
    if result==0:print('PASS synthetic E2E exact test source,partial failed evidence,no production/workflow/dependency/acceptance changes; historical controls retained')
    return result


def guest_roster_main():
    """Qualify owner-approved guest source/schema/evidence before historical replay."""
    from unittest.mock import patch
    baseline='cbeff875c8d68fa2d8dd0cf1c4924131a6350bab'
    owned={'backend/src/main/java/com/cinenest/booking/internal/GuestRosterController.java': 'fa3a231365b558aa556b1b6a5cad65f7c9f17aa0f4a4ec49a846c9d265fd4a48', 'backend/src/main/java/com/cinenest/booking/internal/GuestRosterService.java': '229ca39b26b07f336267711dcb6397dfee83c266a9895bc93dc7ec07015da37e', 'backend/src/main/java/com/cinenest/booking/internal/GuestRosterStore.java': 'eef0d750bd473a9ac01d956f426bc764625a40794f020ead565cc22345a46bf2', 'backend/src/main/java/com/cinenest/booking/internal/GuestRules.java': '914210dee3ac8462f4fb247ce2eab567d38074234facf65d9fdf0545e9bdfe7a', 'backend/src/main/java/com/cinenest/booking/internal/JdbcGuestRosterAdapter.java': '5f28d719ca4d54e39354702a239293415993db047876b8eb621f24d0334f3030', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java': '251d94726dbacd474efc483bbb84e20244f4643425685d99de9fa8f4275a1cb6', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java': '7c0ed63f25a1fcc0c6fbe9e413127da9c821de770218eb5475f64376f214b076', 'backend/src/main/resources/db/migration/V18__synthetic_guest_roster.sql': '9f5a2b064f382a8e22f9bd58d8c4ff69972ed7f21a7edd2d882507abb74af29c', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '568c9ae9b1e6f11f71b7393511df744b83d5b4d52485433ef101e130571c022a', 'backend/src/test/java/com/cinenest/booking/internal/GuestRosterFaults.java': 'a4c7d45c8a954e60b10c458e55b21ea182d38454e62827555cecf9d1073deb58', 'backend/src/test/java/com/cinenest/booking/internal/GuestRulesTest.java': '17f6a6d2bf88c4bb1b30ae1657913dae827d2930bcd800f90cace19f90589c44', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/guest-roster/route.ts': 'be10bb7cea6f974cf54c7c960655674208c13049f5429999bfad15fd88f76640', 'frontend/app/api/bookings/[id]/guest-roster/companions/route.ts': '4b7c1b405106b2f853d5cd84bb2ef353c73b6b9c1d65525345907ec348c289a1', 'frontend/app/api/bookings/[id]/guest-roster/route.ts': 'd562c025e5e6e4a9b230d383da23df53c102872a45213b84237d0b43f50ea28b', 'frontend/components/customer-bookings.tsx': '631ba7e05913bec94de9c0eb3d09255d6e9b287e3c0e43d8e192e7bdf052c098', 'frontend/components/guest-roster.tsx': 'c98c6991b1b2809e51fe10394681d084bd0734a967931cd1bf07fbfa9cee6e9a', 'frontend/lib/bank-backend.ts': '3b54efceeb72fef7ba817e1932754c79a4424127bbd4d637923e43496f5fe30f', 'frontend/package.json': 'e07666a3657009c2171957caa814ac3181aa4d375278ff737195641d826d795b', 'frontend/tests/guest-roster-browser.mjs': '7d2a38fa623e877cc5bf701237ac7be8c7baab724f8f204c6ac1eee8e1d2cf9a', 'frontend/tests/guest-roster.test.mjs': '12f92c572379518ebe084fa22a53e34b1b62147dd2829a8ec1b043a7218ede1f'}
    registries={'knowledge/execution/status.json': '38368375a8f2014f645d8d8228c5b15590c8a2855b5bb1688575be1093edf9f9', 'knowledge/execution/requirements.json': 'b7ae1388782e909d8fdaa6360405c32f764a4f813c5ae22e576b47a3d7b19e45', 'knowledge/execution/test-plan.json': '84ac1b8e92a3ec429e5584512ed5f317f2701458e65baac2de8b6a137608d2de', 'knowledge/decisions/cinenest-open-decisions.json': '31f6427c94419b841eecfcf883b22d9693ca32ed7e97336bf36539d737211191'}
    added={'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/guest-roster/route.ts', 'backend/src/main/java/com/cinenest/booking/internal/JdbcGuestRosterAdapter.java', 'backend/src/test/java/com/cinenest/booking/internal/GuestRosterFaults.java', 'frontend/tests/guest-roster.test.mjs', 'frontend/components/guest-roster.tsx', 'frontend/app/api/bookings/[id]/guest-roster/companions/route.ts', 'backend/src/main/java/com/cinenest/booking/internal/GuestRules.java', 'backend/src/main/java/com/cinenest/booking/internal/GuestRosterController.java', 'backend/src/main/java/com/cinenest/booking/internal/GuestRosterService.java', 'backend/src/main/java/com/cinenest/booking/internal/GuestRosterStore.java', 'frontend/tests/guest-roster-browser.mjs', 'frontend/app/api/bookings/[id]/guest-roster/route.ts', 'backend/src/test/java/com/cinenest/booking/internal/GuestRulesTest.java', 'backend/src/main/resources/db/migration/V18__synthetic_guest_roster.sql'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in {**owned,**registries}.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Guest roster source/authority mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('guestRosterImplementationEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Guest roster changed full requirement/test authority '+name)
    state=json.loads((K/'execution/status.json').read_text());state.pop('guestRosterImplementation',None)
    if state!=json.loads(prior('knowledge/execution/status.json')):errors.append('Guest roster changed unrelated execution state')
    decisions=json.loads((K/'decisions/cinenest-open-decisions.json').read_text())
    for decision in decisions['decisions']:decision.pop('boundedGuestRosterApproval',None)
    if decisions!=json.loads(prior('knowledge/decisions/cinenest-open-decisions.json')):errors.append('Guest roster changed unrelated/global decision')
    for name in ['knowledge/execution/change-sets.json','knowledge/execution/omissions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Guest roster changed dependency/workflow '+name)
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/guest-roster.test.mjs':errors.append('Guest roster lost existing frontend tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Guest roster changed frontend dependencies/toolchain')
    migrations={p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}
    old=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',baseline,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    if migrations!=old|{'backend/src/main/resources/db/migration/V18__synthetic_guest_roster.sql'}:errors.append('Guest roster migration scope drift')
    for name in ['PROJECT.md','ARCHITECTURE.md','DESIGN.md']:
        if not (K/name).read_text().startswith(prior('knowledge/'+name).decode()):errors.append('Guest roster rewrote existing authority '+name)
    progress=(K/'PROGRESS.md').read_text();pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,progress,re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Guest roster promoted phase/demo acceptance')
    if '<a id="guest-roster-implementation"></a>' not in progress:errors.append('Guest roster evidence absent')
    allowed=set(owned)|set(registries)|{'knowledge/PROJECT.md','knowledge/ARCHITECTURE.md','knowledge/DESIGN.md','knowledge/PROGRESS.md','knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Guest roster unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if name in registries:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|{'knowledge/PROJECT.md','knowledge/ARCHITECTURE.md','knowledge/DESIGN.md'} if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=synthetic_e2e_main()
    if result==0:print('PASS guest roster exact source/V18/bounded authority; unchanged full requirements/dependencies/acceptance and historical controls')
    return result



def staff_review_context_main():
    """Qualify the exact bounded staff-navigation successor, then replay all prior controls."""
    from unittest.mock import patch
    baseline='bfde74c980452f2a92670f8995d449b605b7a301'
    hashes={'frontend/lib/staff-review.ts': '325495b9c2481490c5e4a02eba08cd540cc1cb1e8d232894781bba5fbb75b701', 'frontend/components/staff-review-detail.tsx': '9af3c9404bf08f0530a0a7af7a3e68b774d70efe30c503ce58a69a374995e4f1', 'frontend/components/verification.tsx': '2b8f5f26937b40d9d96cc9de52ccaf743cd5b073df912068562783b9144dc7b2', 'frontend/components/bank.tsx': '21813bb86082f6784b96e0986cec8832024b111963a62e52e2a86aa401be1b4e', 'frontend/app/admin/verification/page.tsx': '9cd7f5bc1be2f0bc87740389e8a3f58c59a95a5af5703d7ff2b7e62faa864d4d', 'frontend/app/admin/bank/page.tsx': 'b5b716262088b519e40e3e1688a95ed178cbcaec6630e7cac40bf4572ba26d4b', 'frontend/app/admin/verification/venues/[venueId]/requests/[id]/page.tsx': 'bb9f70630c148bddfa5ab2b6504ff624d662f5e4b497ec2064c4d77c748ad75d', 'frontend/app/admin/bank/venues/[venueId]/requests/[id]/page.tsx': '6b43456146d540856782d458cc33783176c4621207ff05ecf0f8df1c21ff902d', 'frontend/tests/staff-review.test.mjs': '8f3b90765d35a428297c09f6e3114ff3c627c46dd8e379bd687401c7032449a2', 'frontend/tests/staff-review-browser.mjs': 'db8593f3c6fd32238f8800877eb7f59d20cb62edaff4dbeb631f8a6ee5f0ae64', 'frontend/package.json': 'f1fcbd65a5468947155b13af352fa0720f06ab5ddbc71e01061bb92ddb2ec435', 'knowledge/execution/status.json': 'd82bfcd774dac83c399c917d1d5781db555a4a18119a214228bdb58df2c5dc08', 'knowledge/execution/requirements.json': '34a806627775fd9d92cd321526327ee2f43bbfdfe48dca1b76c3eb34c9e8fd7e', 'knowledge/execution/test-plan.json': '2df6a83937ab7e8a695a96b2e7bc2a67a5e1b68eaf138519261c2d1f4bc783f5', 'knowledge/ARCHITECTURE.md': '1d9413904a15f9a6126727d13aae087064e0c719da13369ea6909bf29737e90e', 'knowledge/DESIGN.md': '338d66d2e0307a47af0924f794c036ddf03124bed89c10284e9e891d2088fc42', 'knowledge/PROGRESS.md': 'dfce5272eebbdf279ec0f3c972fcad61a0d4757a80b643b329a07556c71ac266'}
    owned={'frontend/app/admin/verification/page.tsx', 'frontend/components/verification.tsx', 'frontend/app/admin/bank/venues/[venueId]/requests/[id]/page.tsx', 'frontend/tests/staff-review.test.mjs', 'frontend/tests/staff-review-browser.mjs', 'frontend/components/staff-review-detail.tsx', 'frontend/components/bank.tsx', 'frontend/lib/staff-review.ts', 'frontend/app/admin/bank/page.tsx', 'frontend/app/admin/verification/venues/[venueId]/requests/[id]/page.tsx', 'frontend/package.json'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md', 'knowledge/DESIGN.md'}
    added={'frontend/app/admin/bank/venues/[venueId]/requests/[id]/page.tsx', 'frontend/tests/staff-review-browser.mjs', 'frontend/components/staff-review-detail.tsx', 'frontend/lib/staff-review.ts', 'frontend/tests/staff-review.test.mjs', 'frontend/app/admin/verification/venues/[venueId]/requests/[id]/page.tsx'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff review exact source/evidence mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('adminReviewContextEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff review changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('adminReviewContextImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff review changed unrelated execution state')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/staff-review.test.mjs':errors.append('Staff review lost existing frontend tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Staff review changed dependencies/toolchain')
    for name in ['knowledge/ARCHITECTURE.md','knowledge/DESIGN.md']:
        if not (R/name).read_text().startswith(prior(name).decode()):errors.append('Staff review rewrote existing canonical authority '+name)
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff review promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff review unrelated source/dependency/policy/workflow delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Only exact independently checked successor bytes may be projected away;
    # prior requirements, graph, source-integrity and acceptance checks still execute.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if name in replacements or R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=guest_roster_main()
    if result==0:print('PASS exact staff review navigation; backend/graph/policy/acceptance unchanged; full historical governance retained')
    return result



def staff_booking_backend_main():
    """Qualify owner-approved Booking staff reads/V19 before replaying prior controls."""
    from unittest.mock import patch
    baseline='01c73d27037a5973b0eeb3951361f2dcd71ea4fe'
    owned={'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadRules.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadAudit.java', 'backend/src/test/java/com/cinenest/booking/internal/StaffBookingReadRulesTest.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadController.java', 'backend/src/main/resources/db/migration/V19__staff_booking_read_audit.sql', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadService.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java', 'backend/src/main/java/com/cinenest/booking/internal/JdbcStaffBookingReadStore.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadStore.java'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added={'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadRules.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadAudit.java', 'backend/src/test/java/com/cinenest/booking/internal/StaffBookingReadRulesTest.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadController.java', 'backend/src/main/resources/db/migration/V19__staff_booking_read_audit.sql', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadService.java', 'backend/src/main/java/com/cinenest/booking/internal/JdbcStaffBookingReadStore.java', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadStore.java'}
    hashes={'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadStore.java': 'cc95d8652bfd9c1b6d7edf82c3dbf527424774b4d60efd092f7e1233a78c7cc7', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadRules.java': 'bdfd052877e3c7e7e435e8e6f2dc7719e6192f881697c4cbe56b390fba7b4012', 'backend/src/main/java/com/cinenest/booking/internal/JdbcStaffBookingReadStore.java': 'a69a4e95e884716ce37e833e548e65617761490c1f13295bf2a1370e87791431', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadAudit.java': 'a20f9176729c60bc93641cc863b91d2480431294c7ddd09a3d76a330a3eb5dae', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadService.java': 'e6a01d95536d2a8bee8b0de9aa3b68e619dbe21ef8af066980560fec28966b47', 'backend/src/main/java/com/cinenest/booking/internal/StaffBookingReadController.java': 'fc3779fe6917d197e6a7e832cd4f598e15865d4860699b05a78daad5b1230823', 'backend/src/main/java/com/cinenest/identity/internal/DatabaseScopedAuthorization.java': 'fe1242224c4bcca18963101ae2e75b661680d4f5fe971300879bf87f6b2a59f0', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java': 'f8c5bbc8b2aa8a6a2d053fb0c58282b8ba4283a0a565c91fb8694448e8903b6e', 'backend/src/main/resources/db/migration/V19__staff_booking_read_audit.sql': '67064d3b6939dc717b8c2d9d4d99f3b740004e6c4ee731841108c7a8acd02df6', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': 'f95936443e0cad4eb3770e332a15ba62614401e6da2d46ee71bd9b61fb3809fc', 'backend/src/test/java/com/cinenest/booking/internal/StaffBookingReadRulesTest.java': 'efadef17266e5514dd6fad040865743279d097a097a9e6be2b6b30657a4f71a8', 'knowledge/execution/requirements.json': '5dea0276bab1311cc93e006498ad2757ab64e610d800fce75aa3dad5a33a3abd', 'knowledge/execution/test-plan.json': 'dba7b40cfae17698468d71b93556aa426573e2c9a75846a9aa8e492a3a794f01', 'knowledge/execution/status.json': '11aef56db66977d57e519a454db2970b3d35837b8f8d8fd28c7252e85f95f223', 'knowledge/ARCHITECTURE.md': '86026bd6badb14a96c65e7ef449160651acaaf0306dfe24a524d5f51e0eb0787', 'knowledge/PROGRESS.md': 'd95a4e98f7a0a95493a2e1d4f0be7808a82278248a55bb5ad9ea19b8f3bd014f'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff booking read exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('staffBookingReadEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff booking read changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('staffBookingReadImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff booking read changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package.json','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking read changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Staff booking read rewrote prior architecture')
    migrations={p.relative_to(R).as_posix() for p in (R/'backend/src/main/resources/db/migration').glob('*.sql')}
    old=set(subprocess.check_output(['git','-C',str(R),'ls-tree','-r','--name-only',baseline,'--','backend/src/main/resources/db/migration'],text=True).splitlines())
    if migrations!=old|{'backend/src/main/resources/db/migration/V19__staff_booking_read_audit.sql'}:errors.append('Staff booking read migration scope drift')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff booking read promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff booking read unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=staff_review_context_main()
    if result==0:print('PASS exact Booking staff read/V19/owner decisions; frontend/business/full acceptance/graph unchanged; historical controls retained')
    return result






def staff_booking_frontend_main():
    """Qualify exact frontend-only staff booking successor before historical replay."""
    from unittest.mock import patch
    baseline='99e825ec8991fc62335d420896f3a50ea527d28d'
    owned={'frontend/tests/staff-bookings-browser.mjs', 'frontend/lib/admin-backend.ts', 'frontend/app/admin/bookings/venues/[venueId]/[booking]/page.tsx', 'frontend/app/admin/bookings/page.tsx', 'frontend/lib/staff-bookings.ts', 'frontend/tests/staff-bookings.test.mjs', 'frontend/package.json', 'frontend/components/shell.tsx', 'frontend/components/staff-bookings.tsx', 'frontend/tests/staff-bookings-fixtures.mjs', 'frontend/app/api/admin/venues/[venueId]/bookings/route.ts', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/route.ts'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    added={'frontend/tests/staff-bookings-browser.mjs', 'frontend/app/admin/bookings/venues/[venueId]/[booking]/page.tsx', 'frontend/app/admin/bookings/page.tsx', 'frontend/lib/staff-bookings.ts', 'frontend/tests/staff-bookings.test.mjs', 'frontend/components/staff-bookings.tsx', 'frontend/tests/staff-bookings-fixtures.mjs', 'frontend/app/api/admin/venues/[venueId]/bookings/route.ts', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/route.ts'}
    hashes={'frontend/lib/staff-bookings.ts': '342b76a17b005315471c9d7aab6881d1b6bcdc98e6327c3d22fa1d0eec00c8d5', 'frontend/components/staff-bookings.tsx': '0cb970f43553ea7b91314cd4a1f29cecdf31ee5ae8ffe09e5e13224673c51cea', 'frontend/app/admin/bookings/page.tsx': 'd95ed454d731bed5dd8168c464753936ccbb6a4e20cef12dbe4515390bd9ecf8', 'frontend/app/admin/bookings/venues/[venueId]/[booking]/page.tsx': '12e82b912265993d1b0618011dc7228f621fa0ef1f1c43851ee95db5de4e299d', 'frontend/app/api/admin/venues/[venueId]/bookings/route.ts': '34de03915815a15fd96a681ffded768ca1d719c80b9269d9c2cc8fa41ac53c12', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/route.ts': '86aa820508536b75f7d7adb4fee5f79c0177f248f61e84aa8fec5867a919344f', 'frontend/tests/staff-bookings-fixtures.mjs': '838937c2495aeb5f5b6887c1f0ae3371843ec20552e3312a19a38150e4338a39', 'frontend/tests/staff-bookings.test.mjs': 'ad344066f978bf32d9d52364a4b0ce0a99ae831c3ced3a4b4ad083c27a64a66c', 'frontend/tests/staff-bookings-browser.mjs': '84c9183bc33a3aa19c9057d548615926c4aeffbd85e3d37ba5416de3ff3711ce', 'frontend/lib/admin-backend.ts': '13fe11c66a4972ae783a62bd40e84020618bba3c7e7c8aa737b50fca32533acf', 'frontend/components/shell.tsx': '0f0eb1a3c70c3daa5d82bc5e9d29556c7377c3caeaf3d3f299331d121fd06b09', 'frontend/package.json': '8b079c081c89ade1c89213be8008c32d6c4d5303c706d157d37c10e3a787765e', 'knowledge/execution/requirements.json': 'baf9f2e9c00d4a98876062866dbd2f25cb021845f3051416f84c9a1f3d298e47', 'knowledge/execution/test-plan.json': 'f1210871328a0a5d3f1c5ac064496848627e90e319cdf2983b39268e3e12da55', 'knowledge/execution/status.json': 'ef7a7d82d28a51585070778dc868ad90e29e65c935cd4745d281265c01a470a1', 'knowledge/ARCHITECTURE.md': 'd086f9e6c154fc8f2e977c40c70978737162f72106ee7799695206484314bdbe', 'knowledge/DESIGN.md': 'c85f4635f08f012caff3d68160abfba28baebe89da34e532174e5aa9c055d63a', 'knowledge/PROGRESS.md': '57418f5fdf5dc83272962c6797afe030a75739ed539effd3765dc158d9d145ff'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff booking frontend exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('staffBookingFrontendEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff booking frontend changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('staffBookingFrontendImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff booking frontend changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking frontend changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Staff booking frontend rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/staff-bookings.test.mjs':errors.append('Staff booking frontend lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Staff booking frontend changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Staff booking frontend rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff booking frontend promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff booking frontend unrelated delta '+str(changed-allowed))
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=staff_booking_backend_main()
    if result==0:print('PASS exact staff booking frontend; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def frontend_experience_a_main():
    """Qualify exact frontend-only staff booking successor before historical replay."""
    from unittest.mock import patch
    baseline='a4f808907a1616a5bb3a75386a2e09c23c6f1ba5'
    owned={'frontend/app/layout.tsx', 'frontend/package.json', 'frontend/app/page.tsx', 'frontend/app/admin/page.tsx', 'frontend/components/shell.tsx', 'frontend/tests/foundation.test.mjs', 'frontend/tests/experience-browser.mjs', 'frontend/tests/experience.test.mjs', 'frontend/lib/navigation.ts', 'frontend/app/globals.css'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/DESIGN.md', 'knowledge/PROGRESS.md', 'knowledge/ARCHITECTURE.md'}
    added={'frontend/tests/experience-browser.mjs', 'frontend/tests/experience.test.mjs', 'frontend/lib/navigation.ts'}
    hashes={'frontend/components/shell.tsx': 'bc522d2103fb1d315a22766c58bf0d1870c62c38bd7b03800e9fc802e57b3021', 'frontend/lib/navigation.ts': '8ea6769b3e5a96499525583d26f298b914db54443f3e2bfc74dd7b0fc39845e8', 'frontend/app/page.tsx': '6b0b94d18fcf22a5d89beb558d7580d9dae8a737454cb3fd6b5332b990bb8932', 'frontend/app/admin/page.tsx': '52d37b69232b65db761508fe2ddc79c17e09fd5d409e9b904dc8cce7a16434b3', 'frontend/app/layout.tsx': '9e42f36210ff68aa345b010969e01ff15f780ce67fd6077e7ed755850dfc3d3b', 'frontend/app/globals.css': 'b03cf3be422f8068155fa4084817bc53f59c3ee1a67800518f5673b335fd295b', 'frontend/package.json': '4f722ff171d3c9ee45a650c887815c85b01939a1009e39620715095426a262fa', 'frontend/tests/foundation.test.mjs': 'b58ac8425d96a26a86566b156948be37e2ca577858126022877b7cebe520d551', 'frontend/tests/experience.test.mjs': 'c200b6ce2f96e5b164ddd2c23ebd6f009d47ad00d52232aa680d9078acd87cfc', 'frontend/tests/experience-browser.mjs': 'e6ee2309a96b1040154a70775d168374a30219ba6e274fc658381a5fd92118f1', 'knowledge/execution/requirements.json': 'd7ae697df7c2d24db640b0e3bacf571c7ad3f15845ca2e5fbb10d48fea6c20c3', 'knowledge/execution/test-plan.json': 'b89ee160fad6bbba254fe4f5330814bc2f0e31c3fc51aaacd12f04832f183cb3', 'knowledge/execution/status.json': 'b265f4f84d591ba7c3d060bf05798ae8e53ea29548211a40cd4d59536d9ee7de', 'knowledge/ARCHITECTURE.md': '087dd327cd7c021746f093b97b6257237cee01b2f8a86c5b83fc6a78368899e0', 'knowledge/DESIGN.md': 'a9c5c84b352cf5ba7c53a66943bf0330ed45d7bfc647c76a29ee0b223c4dc81f', 'knowledge/PROGRESS.md': 'c767c0b2d96e965108b99343be4ebaad59aabf48c07d3489fceb6fdd0b751ab8'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Frontend experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('frontendExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Frontend experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('frontendExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Frontend experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Frontend experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Frontend experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/experience.test.mjs':errors.append('Frontend experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Frontend experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Frontend experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Frontend experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Frontend experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Frontend experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=staff_booking_frontend_main()
    if result==0:print('PASS exact bounded frontend experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def frontend_experience_b_main():
    """Qualify exact frontend-only staff booking successor before historical replay."""
    from unittest.mock import patch
    baseline='417666a5090bc00ea906ae5571a75cfe248c6055'
    owned={'frontend/tests/inventory-browser.mjs', 'frontend/tests/packages-browser.mjs', 'frontend/components/primitives.tsx', 'frontend/tests/rooms-browser.mjs', 'frontend/components/examples.tsx', 'frontend/components/staff-bookings.tsx', 'frontend/lib/state-presentation.ts', 'frontend/tests/states-browser.mjs', 'frontend/lib/rooms.ts', 'frontend/tests/ui-browser.mjs', 'frontend/tests/experience-browser.mjs', 'frontend/components/rooms.tsx', 'frontend/components/staff-review-detail.tsx', 'frontend/app/globals.css', 'frontend/tests/states.test.mjs', 'frontend/package.json'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    added={'frontend/lib/state-presentation.ts', 'frontend/tests/states.test.mjs', 'frontend/tests/states-browser.mjs'}
    hashes={'frontend/app/globals.css': 'f24432b89c8d1828af16919de41ecd948e8203e3c7dbb2395ef7a65957e6b0f2', 'frontend/components/examples.tsx': '865cd5877badbe743fc850e15e146a85c9ef7f061168132aa3b6228f7be1f970', 'frontend/components/primitives.tsx': 'ff7508010cd090195037e17551c0f4af5f61754fd882508c64f68bb963733d60', 'frontend/components/rooms.tsx': '588f662156f8cf8e2f728e2c6593e39cedb7248ab7ae0a860c3459dd0e7a1401', 'frontend/components/staff-bookings.tsx': 'b852a4ee41a3210fc960f653b5eca6fdb2bc39f8210424b67bdf1d0a9f74b89f', 'frontend/components/staff-review-detail.tsx': '16fe8fc0211d8e0388b371e0bb3a366a8cceeb8181242130ad6a1f2d703bc662', 'frontend/lib/rooms.ts': 'd88570372b4ff4a3a789bfe3233037e8cf0acb69e70403695c54d6274ecc67f8', 'frontend/lib/state-presentation.ts': '51686bf9bee362fc08b86ea6fd256fcaf5a1245a3456456d8f853e84bfca105f', 'frontend/package.json': '333dd9bc56b467da97a0eb1a9026b7d982774383cd38290ed7ae1f03de01b4de', 'frontend/tests/ui-browser.mjs': 'a22f2fd82eeeeee83b0b09e3ef8bf894c5425874ef7eacb4fb209f0aad55f38f', 'frontend/tests/experience-browser.mjs': '2b9918b26c77fa81fe815f9c198039a43f5b67da6c6ff64dbda76871941fa8d5', 'frontend/tests/packages-browser.mjs': '64e57234429ca01eb19bec87320b0dea6844f2a62e3369204dc8e4b257c565f4', 'frontend/tests/rooms-browser.mjs': '70645abed2230e82ae8b19a008b666856d5cecd50c6f805fa9cdd1dcbd2486f4', 'frontend/tests/inventory-browser.mjs': '487520e0c24cbe57a3bbdefc69b3f2a1a9f0181268d95835c0aac278300cb0b8', 'frontend/tests/states.test.mjs': 'b54bff4fabbffc2925b3a66c881a0a61200c16fe2fec745ddf5969e1007d4665', 'frontend/tests/states-browser.mjs': '6ed87b81ae99b9fe297d57b110bdba1bf3351467bf48e389fcfdce89ab6be79d', 'knowledge/execution/requirements.json': '55645ea16a88732e68b9237fb13eab73df4f50f865fb0b168a2c50e14d107af0', 'knowledge/execution/test-plan.json': 'ec646ce94eac1f942448b7e24b57594cb7b763869bf4ad1ef410f0444b6f81d2', 'knowledge/execution/status.json': '8479016cd036439a5968ad07e6a3b58a45f04c9a1e27b2b2fe62c0e6887c0aa7', 'knowledge/ARCHITECTURE.md': '722ab003afd083856a6a6b8de2c1dea11227855535020b494b659d7ddd4e493f', 'knowledge/DESIGN.md': '5bd5b0ea3a4b47a68edc336c7f92173c947156c89b944da2880e0629bddcece5', 'knowledge/PROGRESS.md': '10e8b5976048a8d2e37a3586bee1ea54261e26e38adbd9df1e56293bb340fee0'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Shared state experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('frontendSharedStatesEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Shared state experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('frontendSharedStatesImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Shared state experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Shared state experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Shared state experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/states.test.mjs':errors.append('Shared state experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Shared state experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Shared state experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Shared state experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Shared state experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Shared state experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=frontend_experience_a_main()
    if result==0:print('PASS exact bounded shared state experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def customer_discovery_main():
    """Qualify exact owner-authorized customer discovery source before historical replay."""
    from unittest.mock import patch
    baseline='31fe5fc1f3897426a24df4dbd373c6b3587df2b6'
    owned={'frontend/components/publications.tsx', 'frontend/tests/discovery.test.mjs', 'frontend/app/globals.css', 'frontend/tests/landing-browser.mjs', 'frontend/tests/publications-browser.mjs', 'frontend/tests/discovery-browser.mjs', 'frontend/components/rooms.tsx', 'frontend/package.json'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md', 'knowledge/DESIGN.md'}
    added={'frontend/tests/discovery-browser.mjs', 'frontend/tests/discovery.test.mjs'}
    hashes={'frontend/app/globals.css': '13ad9c8e6c618c7105883bfe4170c551ece9fde7c39c11b4ca2ab2efde01da20', 'frontend/components/rooms.tsx': '9a33742f7993e34454ba4e41067ce24ee63495c1c26c9569c046b4a91fe568d3', 'frontend/components/publications.tsx': '90f39de835b51f0c359a9631d873195fe44a180e59b13f2c0022f41191dc6108', 'frontend/package.json': 'a4073899c83bf1fb77ac23a27eb7e50b2a6f60518f5c7aabb3a768e4487acf49', 'frontend/tests/landing-browser.mjs': 'bcd649936d61c8fd4c008bb4cd1399df3e7dd8b575a89abbbedcd679e1c07591', 'frontend/tests/publications-browser.mjs': '11ee90ca446ae9ff5caec78f49cebe56fd62b6b05babaddfdbfa8f56ea194555', 'frontend/tests/discovery.test.mjs': 'eb2f6bb6e0c781b7ffab3ef8eae074e644fbcd5ffa3018fc71a684b97af2fbce', 'frontend/tests/discovery-browser.mjs': '977470afe6013183adb4c5fe34fb6b17f4315651e2083ccd33339b94692d5b92', 'knowledge/execution/requirements.json': 'ce0f9e41e42f2f87138fa3bfde4d02a6e66285774b49caab8fcb73057ca4e7f9', 'knowledge/execution/test-plan.json': 'c80ba5d8737428ddcf19bef1a76b1114640b5ca5dcd1efd070b5c6504252a42f', 'knowledge/execution/status.json': 'dad360650d6203b87536548f8189fad8527ac7394c1926e39732fa711709dcde', 'knowledge/ARCHITECTURE.md': '2334248d258a0b5b6d4589e0c3ad6c1abce9ab53b9b2c7d2820c2e3a86e0960c', 'knowledge/DESIGN.md': 'f14519d9433b478be79f3c78e6e0fb321052e79e8162535acbdfa668345c210c', 'knowledge/PROGRESS.md': '7a83d932821bd132f36e494217afc8253d8ebf1e55661bb29a337685f072f4a6'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Customer discovery exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('customerDiscoveryEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Customer discovery changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('customerDiscoveryImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Customer discovery changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Customer discovery changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Customer discovery rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/discovery.test.mjs':errors.append('Customer discovery lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Customer discovery changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Customer discovery rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Customer discovery promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Customer discovery unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Customer discovery changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=frontend_experience_b_main()
    if result==0:print('PASS exact bounded customer discovery; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result





def cinema_image_assets_main():
    """Qualify exact owner-authorized cinema image source before historical replay."""
    from unittest.mock import patch
    baseline='4890a0013ca9497a0a566ebd32b17e13a02cd029'
    owned={'frontend/public/cinema/amber-1672.webp', 'frontend/public/cinema/blue-960.webp', 'frontend/components/cinema-imagery.tsx', 'frontend/public/cinema/sources/amber.png', 'frontend/public/cinema/amber-480.webp', 'frontend/public/cinema/sources/hero.png', 'frontend/components/rooms.tsx', 'frontend/tests/tsx-loader.mjs', 'frontend/public/cinema/hero-960.webp', 'frontend/public/cinema/sources/blue.png', 'frontend/public/cinema/hero-480.webp', 'frontend/public/cinema/blue-480.webp', 'frontend/tests/cinema-imagery-browser.mjs', 'frontend/public/cinema/hero-1672.webp', 'frontend/public/cinema/blue-1672.webp', 'frontend/public/cinema/violet-1672.webp', 'frontend/package.json', 'frontend/public/cinema/recliners-1672.webp', 'frontend/public/cinema/recliners-960.webp', 'frontend/public/cinema/recliners-480.webp', 'frontend/tests/discovery-browser.mjs', 'frontend/app/globals.css', 'frontend/app/page.tsx', 'frontend/tests/discovery.test.mjs', 'frontend/public/cinema/violet-960.webp', 'frontend/tests/cinema-imagery.test.mjs', 'frontend/public/cinema/sources/violet.png', 'frontend/public/cinema/violet-480.webp', 'frontend/public/cinema/amber-960.webp', 'frontend/public/cinema/sources/recliners.png'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/DESIGN.md', 'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added={'frontend/public/cinema/amber-1672.webp', 'frontend/public/cinema/blue-960.webp', 'frontend/components/cinema-imagery.tsx', 'frontend/public/cinema/sources/amber.png', 'frontend/public/cinema/amber-480.webp', 'frontend/public/cinema/sources/hero.png', 'frontend/public/cinema/hero-960.webp', 'frontend/public/cinema/sources/blue.png', 'frontend/public/cinema/hero-480.webp', 'frontend/public/cinema/blue-480.webp', 'frontend/tests/cinema-imagery-browser.mjs', 'frontend/public/cinema/hero-1672.webp', 'frontend/public/cinema/blue-1672.webp', 'frontend/public/cinema/violet-1672.webp', 'frontend/public/cinema/recliners-1672.webp', 'frontend/public/cinema/recliners-960.webp', 'frontend/public/cinema/recliners-480.webp', 'frontend/public/cinema/violet-960.webp', 'frontend/tests/cinema-imagery.test.mjs', 'frontend/public/cinema/sources/violet.png', 'frontend/public/cinema/violet-480.webp', 'frontend/public/cinema/amber-960.webp', 'frontend/public/cinema/sources/recliners.png'}
    hashes={'frontend/app/globals.css': '0dfbca40071b613a405f8a58d98dce4e00a1fb8d5068fef37fd7ebd553751f00', 'frontend/app/page.tsx': '8768849c6ee868f6f477fedadc94113321ac1c2ec21c060fb3b5c917fdcbdd80', 'frontend/components/rooms.tsx': 'd30f8716f81729848c9049ef7e15aa29482def8b8d016a79d993ccb81c6d5295', 'frontend/components/cinema-imagery.tsx': 'a8b5478566c3eb1bf1214795324efd1affa0a00e4a2fd9a9eed6e04b3bcdec21', 'frontend/package.json': '3c09e4a0dbadf7fa5fddb3775782fc1908bf6fc38bd0a7a26f019ef92c17d941', 'frontend/tests/discovery.test.mjs': '2de856e95998cd839e588a6ddd5887f6198b500e8f7e6c0892c734418cccd452', 'frontend/tests/discovery-browser.mjs': 'b122e21d88cc1a9b253d08bb817e60e6474907bc98f973248495d160a80a20bf', 'frontend/tests/tsx-loader.mjs': '5649d736b0ae92fa2b321816574df8bf359cbc713ea8bcbb6be678d31224059c', 'frontend/tests/cinema-imagery.test.mjs': '00f8f94477431b873610381950d02e010433c636a9581a3f13f8ee3f31178bb5', 'frontend/tests/cinema-imagery-browser.mjs': 'ba840e1a1a47a40375e42061300cd1a1cb77b141a58fe8c15b48e48f00001216', 'frontend/public/cinema/amber-1672.webp': 'e1f33de83b49cbc093bba3fc2852a2e7dc3a39b2f0f18e4c82c19c201f4e859e', 'frontend/public/cinema/amber-480.webp': '0c6a207972027288a266f3bc70c4973c6ff4a2010a6690237a96f90f3db46bd5', 'frontend/public/cinema/amber-960.webp': '3083d63321771b841370e4cdbdbe238c1454fc6b057f143cd81e28db5dd8f15f', 'frontend/public/cinema/blue-1672.webp': 'f8e57258e33961528b9c3a039d234400e8312e1a587d2d4d4b90486c9c487d54', 'frontend/public/cinema/blue-480.webp': 'e4baa6c9beec17521d2b20a534669c6d7ca7c7107b21c7df74f47a204df740c5', 'frontend/public/cinema/blue-960.webp': '5515c0fcf888360dcee04c78aea4043b54421e68abaf84dbd9d966db314394c8', 'frontend/public/cinema/hero-1672.webp': '7c469a62ea469b5e89c48bba29d1d1fdd523356952ced7c4a6732375a482b81e', 'frontend/public/cinema/hero-480.webp': 'cc2a87f880578cfa79d0f4f7491b47a941e4c1a286286fa73cda86048b56c2b4', 'frontend/public/cinema/hero-960.webp': '079fd9168cda6d270f477dfca1f94938e64474b8946bab70b5a8d311bc6cd069', 'frontend/public/cinema/recliners-1672.webp': '4c0919c7a1cab0562ef988a50c76e29ec58d9afaaf00e7586609064953657951', 'frontend/public/cinema/recliners-480.webp': '9e51a01c0baec37edabc17b589987f0bc83b684bb3b545303d2fdbb76377c24e', 'frontend/public/cinema/recliners-960.webp': '66f73b4dfe80e35eef4e39d7a510b289b9597fd0da4a48a9c7762b1510a5d131', 'frontend/public/cinema/sources/amber.png': '79d0c6d28d6e68e21d10172e1b36ecc8c10949d2419048800023bdbc03c9de0f', 'frontend/public/cinema/sources/blue.png': '0056446937b887bda6a4a7c833ef071489684a56382572e0bc853e9026fb7da4', 'frontend/public/cinema/sources/hero.png': '8ed7d3efa4b14db41bf385cfc0d895072b65c85df3b8778b02674e38dcaabfc3', 'frontend/public/cinema/sources/recliners.png': 'fc52ec556fcd4285af13d4c93b4b37d4a2135099f8807e393a94c0c127e6862e', 'frontend/public/cinema/sources/violet.png': '84ba620fcae4ef8b8e57e20a973b73b9a152c94b9fdf6d3d6b23af3848b5f87d', 'frontend/public/cinema/violet-1672.webp': '1057d71005123db60407bdcf9a866d090d799e084298117b49d7e13c706ea3ce', 'frontend/public/cinema/violet-480.webp': '30fe26cc3e7a8520d1e703aae67db9ecbdf9b46589d681c38ff7d236aff7e9db', 'frontend/public/cinema/violet-960.webp': 'f009496a5fb02907bf554c5e9706b6be3f222f5e7595c788b48aa010ea919879', 'knowledge/execution/requirements.json': 'e3a0996f45770d2ffad3c05d7103d1071ae5ae900dfa86d89bcef4ce93783e55', 'knowledge/execution/test-plan.json': 'fee0ccd036278a1710ccb24b12a2c9b743e7d943d1d35225d12251035e6c3d6e', 'knowledge/execution/status.json': '115b848ceac99964c2ebb14dc37afd4024710924d8d0617697f2a93b8c31188c', 'knowledge/ARCHITECTURE.md': '48d9c1a0e583c0129f21b2d9c21f0343d8e94edc77333d16d6069861919c7ebd', 'knowledge/DESIGN.md': '8896a574ef9884fac6c057726a8e1db067f5623e9b69d8b3ae6c2c1118019feb', 'knowledge/PROGRESS.md': '07366a39d894b1f5c49a4d2e6fd2c079d98f80bb475fd99f2ea79e81d29c9a19'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Cinema image assets exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('cinemaImageAssetsEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Cinema image assets changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('cinemaImageAssetsImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Cinema image assets changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Cinema image assets changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Cinema image assets rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/cinema-imagery.test.mjs':errors.append('Cinema image assets lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Cinema image assets changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Cinema image assets rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Cinema image assets promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Cinema image assets unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Cinema image assets changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=customer_discovery_main()
    if result==0:print('PASS exact bounded cinema image assets; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def booking_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='75762034452591903de60af59aa0ab240502f79d'
    owned={'frontend/components/inventory.tsx', 'frontend/components/booking-steps.tsx', 'frontend/package.json', 'frontend/tests/quotes-browser.mjs', 'frontend/tests/booking-steps-browser.mjs', 'frontend/app/globals.css', 'frontend/components/quotes.tsx', 'frontend/tests/landing-browser.mjs', 'frontend/tests/booking-steps.test.mjs'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/PROGRESS.md', 'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md'}
    added={'frontend/tests/booking-steps-browser.mjs', 'frontend/components/booking-steps.tsx', 'frontend/tests/booking-steps.test.mjs'}
    hashes={'frontend/app/globals.css': '2ca95caa37e7c54cd908bb941de89dd1ffb1c9e5d4c0dde3f2cba9958ffe6172', 'frontend/components/inventory.tsx': 'a2714ddefe4d80dc956bbd14e60861c8af70ede682a88324fd02f8b22af4e964', 'frontend/components/quotes.tsx': '8e9cdea8ab2296d1354c67b0bed2a8cc40fe269a4427935b0a45bc19e24ffdde', 'frontend/components/booking-steps.tsx': '10739c93ea3941e07f654f3bc6d492f0de29cee28aba61259ccb9aac4eef5725', 'frontend/package.json': '21b8298f8e0e11a99fb7a2341dfa91ccffe2455d2aa5b1b5b6fe3dfe108c58b4', 'frontend/tests/landing-browser.mjs': 'f29c336bac5d01ded01302ae715609fc803ffc6adadcfd33066c9157a02bf8cd', 'frontend/tests/quotes-browser.mjs': 'de0dcd08d67eca71631a22b35e1d152f62e15b0d1abc130b7dba68562555ee1c', 'frontend/tests/booking-steps.test.mjs': '413d4a2dde99d7bb56e7c3b2071618229e96fa0d76f2b92e816e666d0d732e88', 'frontend/tests/booking-steps-browser.mjs': '45c6dc0a85d32208b1b6654b5754bea733c5af680110461b018b8b1c838ce55d', 'knowledge/execution/requirements.json': 'c47b0b8ae2b05b556fc3810d5f2f7a05d44e9f0edff0c6b2d72193740945d915', 'knowledge/execution/test-plan.json': 'fd0c8d560a99164ea9a0e52323419f1433d3a6affbd1ba45ff294f4919832e85', 'knowledge/execution/status.json': '06de3c784c64eb22564e53f5b6fdb25a144c4d5395321febfbc2a5821f7e27c3', 'knowledge/ARCHITECTURE.md': '04edb2e049a83777746cee912f73950a7b7c92ebb0007a9c3d5f13ce46d9c5e9', 'knowledge/DESIGN.md': '05bff84c8b458c21984f2a0df5151279ab48bf8a6d4d74af3486f20034290107', 'knowledge/PROGRESS.md': 'dff40114147bf5a40a8d76de974a0a22936701f1db7071ef37c5c9c59e3aed09'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Booking section experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('bookingExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Booking section experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('bookingExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Booking section experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking section experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Booking section experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/booking-steps.test.mjs':errors.append('Booking section experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Booking section experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Booking section experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Booking section experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Booking section experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking section experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=cinema_image_assets_main()
    if result==0:print('PASS exact bounded booking section experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result





def verification_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='54ec3eb9cdfd46fa70750c7400b4d4efe97e84b5'
    owned={'frontend/components/verification-stages.tsx', 'frontend/tests/verification-experience-browser.mjs', 'frontend/components/verification.tsx', 'frontend/package.json', 'frontend/app/globals.css', 'frontend/tests/verification-experience.test.mjs', 'frontend/app/verification/page.tsx'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/PROGRESS.md', 'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md'}
    added={'frontend/tests/verification-experience.test.mjs', 'frontend/components/verification-stages.tsx', 'frontend/tests/verification-experience-browser.mjs'}
    hashes={'frontend/app/globals.css': '428b81e8cd36584f481fe803ddcce2e2e964cd102202384b70c68d6c0612e3b0', 'frontend/app/verification/page.tsx': 'ac2f0ed4559224b3b2db6b6d21df16970954e9f7053d865a556b4eb3d0ee8c5e', 'frontend/components/verification.tsx': 'd38eb1d711955ce8110c3471197dc3c567b9a8637f9be12f0d47eb02d0b073de', 'frontend/components/verification-stages.tsx': '24c2039eb4bc3d0ff43cb9bfee56a66572785c5f98307d23e267a50be37c062d', 'frontend/package.json': '8d5dd4a5b130cc32fb0f1e0504db23ca1b798c16d6581065317706327c1fe631', 'frontend/tests/verification-experience.test.mjs': 'fdc76d67cc683ba81bcee656aca60944a7d4e87dc069c4a5e2663ec45fbda3be', 'frontend/tests/verification-experience-browser.mjs': '55efccbe847161e9c0371076d7e415720992c279dcd50b6786d8e2ea4775af0a', 'knowledge/execution/requirements.json': '77bd4ff623c32dc641a5c8cf3c5ebeda5cf517445afcf24df8ccb96b37ce176c', 'knowledge/execution/test-plan.json': 'a701d2fcef209d7d4541bd030d522564e7cc92238cb20b518b25b4e23ead1d7e', 'knowledge/execution/status.json': '8bdc4962d01cb12fe7b7e1347974cf16eb0a8db9eddd7a26ef2915dbf46aafe3', 'knowledge/ARCHITECTURE.md': 'ac934ad823bc103b78aa668ba166000083ae8dfe4c8c1fb0732b548c6caa0e8f', 'knowledge/DESIGN.md': '6fea33bab36f369f0362ac9ecd9c918f2d85d8f7edd67e4c575f38ba31d705e9', 'knowledge/PROGRESS.md': '69ff578964dc3d27f27c705fd8c5288b2984a4b3d57def9dc6b53770e21912e5'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Verification experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('verificationExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Verification experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('verificationExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Verification experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Verification experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Verification experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/verification-experience.test.mjs':errors.append('Verification experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Verification experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Verification experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Verification experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Verification experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Verification experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=booking_experience_main()
    if result==0:print('PASS exact bounded verification experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result



def banking_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='95c987c561f7298179cc8de114cdb8cf61c36534'
    owned={'frontend/components/bank-handoff.tsx', 'frontend/components/bank-request-detail.tsx', 'frontend/components/bank.tsx', 'frontend/app/bank/page.tsx', 'frontend/tests/bank-evidence-browser.mjs', 'frontend/app/globals.css', 'frontend/tests/bank-handoff-browser.mjs', 'frontend/package.json', 'frontend/tests/bank-experience.test.mjs', 'frontend/components/bank-experience.tsx', 'frontend/tests/bank-browser.mjs'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    added={'frontend/components/bank-experience.tsx', 'frontend/tests/bank-experience.test.mjs'}
    hashes={'frontend/app/globals.css': '7085278b600cd4753b968c64c2ed22d77d346ad309cd4c2fb072b1ea259b08e6', 'frontend/app/bank/page.tsx': '1e4d14e51c308bc0cb9a77fdfa576468fa3bfe842ccdfe85ec1ebe330ae8fd37', 'frontend/components/bank.tsx': '63cabf46f2d053a5c4ca364ea31fc84ed105d1526406f79bb69980adf6f00b37', 'frontend/components/bank-experience.tsx': '334a38fe610fea209eb3ce81fe356b3ee1b1c65bae2b644bf8efc72d3f17e202', 'frontend/components/bank-handoff.tsx': '4f3805b0e2d3973e188dd4ebf1081ed50cdee102f7214f97e0e58653124b0957', 'frontend/components/bank-request-detail.tsx': 'eebd4c528b8666ff619a8550607831db9112da8e942508b479284492ad2bfbcb', 'frontend/package.json': '647be9c5e97b121c961c819a1f84ff842aaac65c2eefee1732cf5073e100b315', 'frontend/tests/bank-experience.test.mjs': '8c0e2446f0641aafe4b9f3597a822ba490472c96c1b2ad32b4f0eb6de3d35162', 'frontend/tests/bank-evidence-browser.mjs': '1ec325784a887111e56fb3e74c23685841003d9fd6602adeb8d61103a174bfc8', 'frontend/tests/bank-handoff-browser.mjs': '65b1cb50bade550a78559342e1ac1e253326a8f63de11168b80af450c308ac2d', 'frontend/tests/bank-browser.mjs': '8419fe45ffb2a05355224801dbf173a4f7c60069ce82b56e74989ca9ffeed7cc', 'knowledge/execution/requirements.json': 'd80e74c30f21c6d75ddd8e52f811cf1c70b21d368db57fb90ae39178e9ceebeb', 'knowledge/execution/test-plan.json': '3fbd6671cd23e7b24685e6e56369782c55560c023f61be5ba7e81733df06c3e7', 'knowledge/execution/status.json': 'c65726519da9163a48bfbb53aedc21d6435bb6785d666d9542bb4e4afe911a14', 'knowledge/ARCHITECTURE.md': 'c8f52eac349b6172b3a6d3078afa7bb48bb33ab423b9fe685a89b6421fd395e6', 'knowledge/DESIGN.md': 'e3eaa9c90cf0c736ce9942d0b617ac34e41dd92b16105026a3d4fb38d84d2eaa', 'knowledge/PROGRESS.md': '6010dbdb2781defdb551f3fc4edd8080617c4bf7606fe362a8e9059a20ef07ad'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Banking experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('bankingExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Banking experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('bankingExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Banking experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Banking experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Banking experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/bank-experience.test.mjs':errors.append('Banking experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Banking experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Banking experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Banking experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Banking experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Banking experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=verification_experience_main()
    if result==0:print('PASS exact bounded banking experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result



def booking_history_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='efef8156fe8f158750460c5cfb2f6d8265346916'
    owned={'frontend/tests/booking-history-experience.test.mjs', 'frontend/components/guest-roster.tsx', 'frontend/tests/customer-bookings-browser.mjs', 'frontend/app/globals.css', 'frontend/package.json', 'frontend/components/customer-bookings.tsx'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md', 'knowledge/DESIGN.md'}
    added={'frontend/tests/booking-history-experience.test.mjs'}
    hashes={'frontend/app/globals.css': '8030698a2d26aeb54ff2d0c1404a4c4d03eb409535ea9aea728279adef1be860', 'frontend/components/customer-bookings.tsx': 'd114bba86c4ff41d13f10cb0c936503143cb7c5238b56dad4224fb88c92f7f6f', 'frontend/components/guest-roster.tsx': '15422307d58dfc52bc509f4bdccdf4ddcb1900e8b2a6e59abdffebf9b1976bd8', 'frontend/package.json': '4ec5d0d6c30305b89750921b56eb104692e0dcef98feae841370f4a451a616ec', 'frontend/tests/booking-history-experience.test.mjs': 'a6e46f3b98c9659d257d32cc334712f8b2e3154c17eedef838aa84a7c27c7163', 'frontend/tests/customer-bookings-browser.mjs': '73a78eea9e5f5c617141597b1ae8359b9ce310a3c41a80ac0513b100f96c39b1', 'knowledge/execution/requirements.json': 'f8aab8b78b59b4ea8d6e6363e1edcf82e8d6ba38f496bbf27225b0ddcf05536f', 'knowledge/execution/test-plan.json': '2888c73845e9000b0ce431f9f5727012d62d48e7fef51840453f84a6777d787b', 'knowledge/execution/status.json': 'f4b69ac188ee650ad8cf42afe3849d6364ad926f7fd28cd78678f95ff0dc92e4', 'knowledge/ARCHITECTURE.md': 'fc691172f30d51d99b58860eec4a8a9cf6f9d043c1b53f2f72c344929ccbc14a', 'knowledge/DESIGN.md': 'dcab170aaad2ff034ebf669a144418c6fa78fc99b63133b11dda7c9a1ff63204', 'knowledge/PROGRESS.md': 'adc240352dd50b967d221f939d4a5a65d6359af8175556f89f9a204eb369373d'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Booking history experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('bookingHistoryExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Booking history experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('bookingHistoryExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Booking history experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking history experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Booking history experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/booking-history-experience.test.mjs':errors.append('Booking history experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Booking history experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Booking history experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Booking history experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Booking history experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking history experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=banking_experience_main()
    if result==0:print('PASS exact bounded booking history experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result



def admin_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='6ff6cc3ff805a3e9d962924cffe22b781e59af79'
    owned={'frontend/components/staff-bookings.tsx', 'frontend/app/admin/bank/venues/[venueId]/requests/[id]/page.tsx', 'frontend/components/bank.tsx', 'frontend/tests/tsx-loader.mjs', 'frontend/components/staff-review-detail.tsx', 'frontend/tests/staff-review-browser.mjs', 'frontend/components/staff-venue-context.tsx', 'frontend/components/verification.tsx', 'frontend/app/globals.css', 'frontend/tests/admin-experience.test.mjs', 'frontend/app/admin/verification/venues/[venueId]/requests/[id]/page.tsx', 'frontend/app/admin/verification/page.tsx', 'frontend/components/shell.tsx', 'frontend/lib/navigation.ts', 'frontend/app/admin/bank/page.tsx', 'frontend/package.json'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    added={'frontend/tests/admin-experience.test.mjs', 'frontend/components/staff-venue-context.tsx'}
    hashes={'frontend/app/globals.css': '86a0207adbba263d89b0467d920758b858fb013e4da55c3fcc7ac82788dc7858', 'frontend/lib/navigation.ts': '94d22fea1d633ec69e922f45d15da93c88791bb258ea43ffdab666a3825c6b01', 'frontend/components/shell.tsx': '68512f237fe2d5a3ad47013382e224b5c6615109b518920ce937f384e09b55c1', 'frontend/components/staff-venue-context.tsx': 'c400f84e51753ff696987fa345001501ceb0ce1b21b596b3bb2521482e1fc1c7', 'frontend/components/staff-review-detail.tsx': 'b5dc3bcc5a6f265424105d7f953f8a9d0a5902c3d7c404c564636e121c0f2c6e', 'frontend/components/staff-bookings.tsx': 'a65c151d5fe6f6a0b82f381eeb0528c6d5b98606be0a2487eef00f685591323d', 'frontend/components/bank.tsx': '0dbb3b4c8f11ee433853d636fbdf0467fa87cf4d10d4a1439b425a406c9b917b', 'frontend/components/verification.tsx': '1e53cb18ae726ba7ef3565e2dc61a8d6e990d3bd1588364c667e903e4a15be74', 'frontend/app/admin/verification/page.tsx': '43084755fa389e8af92adf882295213fb2f460e522bdc7764786b5ed105cd437', 'frontend/app/admin/bank/page.tsx': 'bcb412e70c4d8c51e93ac67903a3ac9da560b8df524e0b9d13279f51ebc05ab0', 'frontend/app/admin/verification/venues/[venueId]/requests/[id]/page.tsx': '1df5dcb1e507c0ce5fb1ef6985ad09a6a4200b4bae449bc14c54bc780b833cdb', 'frontend/app/admin/bank/venues/[venueId]/requests/[id]/page.tsx': '3ef9f6f436b81abe0f5335665d9a622c55e7aefbe9b986ad62eb33018814f852', 'frontend/package.json': '5fb8358a048bf40a9c726c3af44627c70d7831095e7f2290d54e77efd4a900cc', 'frontend/tests/tsx-loader.mjs': '4bd8e19114fcf61d6897eb3b915ccd2dfe9b221a15e3a18c732bb9933aad0779', 'frontend/tests/admin-experience.test.mjs': 'cc5efe059e885625579f67976799232e39bd55805fb501810276594b53caca52', 'frontend/tests/staff-review-browser.mjs': '97a0192bdfd4a7c698a4323f3bbc6c5d29d651b72844a1e516c3982338032fb1', 'knowledge/execution/requirements.json': 'abe11a018016798b5ef4f154208a12fc4ba9b2b843b84fae763ae7cd4653eb50', 'knowledge/execution/test-plan.json': '524a8c51f0c12ae319e35baf899f613efb119f3ae8acc3c29fb1a9d0f5b4f446', 'knowledge/execution/status.json': '8d5cd83f473352ff40e7875dc7e906eeb8403fdf3b213ca6963c26dc89465f3c', 'knowledge/ARCHITECTURE.md': '386ce45aa3329b3a5de77067329e4d326184e93a075e6c366bdf1e661b9fdee8', 'knowledge/DESIGN.md': '3b0d599cfd609b018f9f09db1f6c8997a28d9ea0d8d4ef14147d57a5b19929c2', 'knowledge/PROGRESS.md': '1d8008f0847f15053d3b01bedb7c6d0ae7f1bcc351410ed60d1409c23f9393b4'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Admin experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('adminExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Admin experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('adminExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Admin experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Admin experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Admin experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/admin-experience.test.mjs':errors.append('Admin experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Admin experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Admin experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Admin experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Admin experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Admin experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=booking_history_experience_main()
    if result==0:print('PASS exact bounded admin experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def operations_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='0dfaba067e3f75ba67348cb48a66a16011e8d3cc'
    owned={'frontend/components/operations.tsx', 'frontend/package.json', 'frontend/app/globals.css', 'frontend/tests/shared-forms-browser.mjs', 'frontend/components/bank-report.tsx', 'frontend/components/primitives.tsx', 'frontend/tests/bank-report-browser.mjs', 'frontend/tests/operations-experience.test.mjs', 'frontend/tests/operations-browser.mjs'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/DESIGN.md', 'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added={'frontend/tests/shared-forms-browser.mjs', 'frontend/tests/operations-experience.test.mjs'}
    hashes={'frontend/app/globals.css': 'bce7b9cd71ddda5bc7290e4ee1241820c19197114d2eacbcf2a635f2e7e10365', 'frontend/components/operations.tsx': '4f9ae616e69b907b91ff524e80ca0f611b31b79d84be66559caf499a4c8d3185', 'frontend/components/bank-report.tsx': '8f0ce4a29d98828dbff4d3f64460fc5990bc174015eff57edd18cec524480c39', 'frontend/components/primitives.tsx': '1a9301df1013b2b33f07d7e12ace5a58eb84412b23b7dd1373b63c6eab9ea7fe', 'frontend/package.json': '18af93f72b3065c9ee8a83f1bf310fde9b2bba7ff2eea7c882434925fa283105', 'frontend/tests/operations-experience.test.mjs': 'f180a4cf2a02e69afb9ebd49186be0cd7c3d75e52ea53e09f269d7935eee235a', 'frontend/tests/operations-browser.mjs': '00ed580b132a85d4aa986d75810f4666057e1f0caa1a2149fe2040d5239ca865', 'frontend/tests/bank-report-browser.mjs': 'dcc5df82aa984053299caa3c5bab79db28f365d6ece8c310d85885ac0425b869', 'frontend/tests/shared-forms-browser.mjs': 'c50f6dad988340ec609c3d1a69d8e3f42cba7d444fcc715fdb190f36814547be', 'knowledge/execution/requirements.json': 'e5ff837fb25c018aeabfa4cc52bc349eaef86ca6cadd70777603a6f93f4804af', 'knowledge/execution/test-plan.json': 'ba42832ca92184a00c9de612872fc4f97979a287b8a1c633ae5d48e07b4b53d0', 'knowledge/execution/status.json': '5ee4c8893a78ff363ba1b9fa73c4145bbd4181beba64f407e2d123e89d2903ca', 'knowledge/ARCHITECTURE.md': 'b4f7d624874df06a616b42dd9c4ac50fb9ac80b2cd4ac71193ed3709f30ac985', 'knowledge/DESIGN.md': '19e4bf2e66233f18af9290bfd381a351a7de519304608476f8a959feb4299b64', 'knowledge/PROGRESS.md': 'a0ebb2e956794e3d98a8cbdd4cd7a3b6ca565abed89329ded60a7ae01ac7d103'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Operations experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('operationsExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Operations experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('operationsExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Operations experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Operations experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Operations experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/operations-experience.test.mjs':errors.append('Operations experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Operations experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Operations experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Operations experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Operations experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Operations experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=admin_experience_main()
    if result==0:print('PASS exact bounded operations experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result





def configuration_experience_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='f795c7b66cb0adf3a3dbbbbdfc4f0cbe34e8b42d'
    owned={'frontend/components/publications.tsx', 'frontend/tests/configuration-experience.test.mjs', 'frontend/tests/publications-browser.mjs', 'frontend/app/globals.css', 'frontend/components/packages.tsx', 'frontend/components/rooms.tsx', 'frontend/package.json', 'frontend/tests/configuration-experience-browser.mjs', 'frontend/components/configuration-summary.tsx'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/PROGRESS.md', 'knowledge/DESIGN.md', 'knowledge/ARCHITECTURE.md'}
    added={'frontend/tests/configuration-experience-browser.mjs', 'frontend/components/configuration-summary.tsx', 'frontend/tests/configuration-experience.test.mjs'}
    hashes={'frontend/app/globals.css': '5423ce3ac29bd448a3d50aba74ca69722bbdb50de9c3fd14d5eac314d7d2aa52', 'frontend/components/configuration-summary.tsx': 'cad5899f34fd020eda3469248ac6b8e7032450d542e45bfbefb87de1045b7930', 'frontend/components/packages.tsx': '9a5756c3f5ba322ea4c8af0ba6d4a3f9782c1f136216725d0ce3f5e51bdadd7d', 'frontend/components/publications.tsx': 'a330b060d6debfd8af40a454dd8bf68da3a9ab2e2c5295865ac48a828b172307', 'frontend/components/rooms.tsx': 'a1acc14991a4cd8924866cd2e33c133b1958b4697d2eafd17ccae68d715f8a48', 'frontend/package.json': '98864bf1d90962b14b82bf2dad96987ae0d1dc39464cf569446fe35c13612fdd', 'frontend/tests/configuration-experience.test.mjs': '6471ceab30dfe33a68c44aebc80a9385dc695e854117792129c0dffecf2d2912', 'frontend/tests/configuration-experience-browser.mjs': 'af513b6dd9b24d0108b0d6dc78d46592f1acc1907135162612fd44963861806c', 'frontend/tests/publications-browser.mjs': '4fc4b8b22f5ba30c0e9b270418f9bc98a9a46efb74c6fa11c4653f7028b7ebe9', 'knowledge/execution/requirements.json': 'b423358d7327e9c9db06e7e156a81a84046e2b0e2bb73eef38e17aeab89ab7f1', 'knowledge/execution/test-plan.json': 'e3354fbe742ff512adc516d85bddb5538453fd191be7f2a34f9015952197530a', 'knowledge/execution/status.json': '93a0df59eb357ed6ec44d856f6aa3879c2093cc3adf325f8a69a73f112f4ff27', 'knowledge/ARCHITECTURE.md': '4b09454562da9c61cd3131925717d0ced095ebf73708617810448237b837a01c', 'knowledge/DESIGN.md': '7614c398644d63b410145e93cecfab49d49251d73dd7679b6d2cf174b24d7b21', 'knowledge/PROGRESS.md': '87c9364fdb026669e9f50361525ba514c0ff8f42038306ed839b43686c870790'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Configuration experience exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('configurationExperienceEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Configuration experience changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('configurationExperienceImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Configuration experience changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Configuration experience changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Configuration experience rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/configuration-experience.test.mjs':errors.append('Configuration experience lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Configuration experience changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Configuration experience rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Configuration experience promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Configuration experience unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Configuration experience changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=operations_experience_main()
    if result==0:print('PASS exact bounded configuration experience; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def booking_e2e_remediation_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='cc4595329d4ea0a21877a2d4924e3843c36ae6c1'
    owned={'frontend/tests/synthetic-e2e.mjs', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added=set()
    hashes={'knowledge/execution/test-plan.json': '8f672e25ffecdbcbae03616984f093a4529a575afbf445e4c5238b4d78e68de0', 'knowledge/ARCHITECTURE.md': '7dd01c24f99c3bebc2786f7e1411a2fe88d6160ee5b4cc6f57fc827ff7d21271', 'knowledge/PROGRESS.md': '2532ad6fd7dc783a6a84217ab7b295fbd8d5e60cd58607f2717db4ff691f5aab', 'knowledge/execution/requirements.json': 'c5548e452be493eee26c20b37110689a3b897d1957f1cd33b91e593d1a548652', 'frontend/tests/synthetic-e2e.mjs': '682bb7a8b228417313e670921a335995299708b1034a6d8add7602c47bfd0292', 'knowledge/execution/status.json': '39c6df3c7fe3e0ce575c984629be0cd93b732b9f920db425c876f18492a900e3', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '665dbe70d3551026296579444a66cd1316fbf0a502b4e2bb08c9e21819fd21b9'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Booking E2E remediation exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eBookingRemediationEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Booking E2E remediation changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eBookingRemediation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Booking E2E remediation changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking E2E remediation changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Booking E2E remediation rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Booking E2E remediation changed frontend dependencies/tests')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Booking E2E remediation rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Booking E2E remediation promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Booking E2E remediation unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking E2E remediation changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=configuration_experience_main()
    if result==0:print('PASS exact bounded booking E2E remediation; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result






def safe_urls_e2e_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='cae75d8ee0ddf653e1982e8d13fa33f3b7cd42f1'
    owned={'scripts/test-synthetic-e2e.py', 'frontend/tests/synthetic-e2e.mjs', 'frontend/package.json', 'frontend/tests/safe-navigation.mjs', 'frontend/tests/safe-navigation.test.mjs'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added={'frontend/tests/safe-navigation.mjs', 'frontend/tests/safe-navigation.test.mjs'}
    hashes={'knowledge/execution/test-plan.json': '8ea227c7cc0b8d304931a8f6884bc23ac76902b67e88a7d5c1d33c76f7f9fcf7', 'scripts/test-synthetic-e2e.py': 'ec756c2f2cda43612e875a89afb6be7b6ffee1a7bf66f8b076734f8fca2e1b1f', 'frontend/tests/synthetic-e2e.mjs': 'f133d4234626bb9b469543d336ccceb24fce403af45532c5cb482c283ca43d7e', 'frontend/package.json': '21076d3af7f52336e13c1873130c5d9b94ce008e18fd083d0aea2e37d47ae907', 'frontend/tests/safe-navigation.mjs': 'f424c9537bba32f80e853303c99e801246dbcef2efa5a54c2c97b8bf45b65ce7', 'knowledge/execution/status.json': '3116b2f29af550236faf85107846b47ff670f252a54bce28102b0860bceee9f8', 'frontend/tests/safe-navigation.test.mjs': 'd997796958227f18528858a6a7f12b0d9c1390aab1f17790acf12cd7ff1efac7', 'knowledge/ARCHITECTURE.md': '1d34af16a073ab2c80ada1bd59e0f25c05fd2a1a918d049bc19b4113858b2485', 'knowledge/PROGRESS.md': '5aa747f64acd696a89f982dd373d24af792329b9f2e65eec93cc38b0adbed3ae', 'knowledge/execution/requirements.json': '9fbf0c9ee3fcd6cc016c4d7ed42eefd368897f7921299a7e5889dd0eb291665f'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Safe URL E2E remediation exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eSafeUrlsEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Safe URL E2E remediation changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eSafeUrlsRemediation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Safe URL E2E remediation changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Safe URL E2E remediation changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Safe URL E2E remediation rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());previous=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=previous['scripts']['test']+' && node tests/safe-navigation.test.mjs':errors.append('Safe URL E2E remediation lost existing tests')
    package['scripts']['test']=previous['scripts']['test']
    if package!=previous:errors.append('Safe URL E2E remediation changed dependencies')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Safe URL E2E remediation rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Safe URL E2E remediation promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Safe URL E2E remediation unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Safe URL E2E remediation changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=booking_e2e_remediation_main()
    if result==0:print('PASS exact bounded safe URL E2E remediation; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def expired_quote_e2e_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='f901f8f715855a81a1f4e20f49466dcf9d7bfae8'
    owned={'frontend/tests/synthetic-e2e.mjs', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added=set()
    hashes={'knowledge/ARCHITECTURE.md': 'a20b63d8f70ac9908a0f923bb90736ed841bf267ec5885a12972df772e69eb41', 'knowledge/execution/status.json': '4b0bcd3b17862872de7a9289075b93f254d8235370ea8c4e89a17f8cb5cfa8d2', 'knowledge/execution/test-plan.json': 'a3ef467d1ca67f5b1d285764a4f2899d74eb9a4dbd16c4c072ca2a5b2b6cecab', 'frontend/tests/synthetic-e2e.mjs': 'ddde8aa38364b70862ffa5e2f456705449c89c2ddc55e082fe0358b0f5591b31', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '5baecb19beb8c9aa93a5b933f32d2b1d9b64f72549a1d06003cf274cbc89a2f9', 'knowledge/execution/requirements.json': '0306275f28951548c4a1d3b185459adba48d79b32906edbfb39fc1205781d55b', 'knowledge/PROGRESS.md': '6cecbd9c0ed62a2adf490efc72374ee6bc21b5fb560cbce8641f213e86edaa88'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Expired quote E2E verification exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eExpiredQuoteEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Expired quote E2E verification changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eExpiredQuoteVerification',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Expired quote E2E verification changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Expired quote E2E verification changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Expired quote E2E verification rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Expired quote E2E changed frontend dependencies/tests')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Expired quote E2E verification rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Expired quote E2E verification promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Expired quote E2E verification unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Expired quote E2E verification changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=safe_urls_e2e_main()
    if result==0:print('PASS exact bounded expired quote E2E verification; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def invalid_hold_e2e_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='babe7254ea8de86d5f864a4885da81ad785b045e'
    owned={'frontend/tests/synthetic-e2e.mjs', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added=set()
    hashes={'knowledge/PROGRESS.md': 'b318fe54ef07f5c7ce83f29964dacd557304a310eeb9ba4a9167e09de9507233', 'knowledge/execution/test-plan.json': '0c234d269d3e47a4e5c30b733931c89270cae808045852cf170a33524d37d45b', 'frontend/tests/synthetic-e2e.mjs': '9d4d9ae912de85f15da3bb5fa3fb72b898bc72a8c6c6edc532d46042835cb23f', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '6c0ca7ebb8f62831e6e54c6fcc16d19cec0d37e2608b9422a5ae70187775ae2b', 'knowledge/ARCHITECTURE.md': '318e22efe2ef328ef8d88395cd1134e6a66f046f4a9c1e55a30afeb6af17d000', 'knowledge/execution/requirements.json': 'ada8b53f523c1e745ace14d6553dd2393d8408025c22f2a90bbaa510b2e49697', 'knowledge/execution/status.json': 'd66910d34311cabbc583899babb0daa64f6ca0fc2496708eb1492b95a902830f'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Invalid hold E2E verification exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eInvalidHoldEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Invalid hold E2E verification changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eInvalidHoldVerification',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Invalid hold E2E verification changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Invalid hold E2E verification changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Invalid hold E2E verification rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Expired quote E2E changed frontend dependencies/tests')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Invalid hold E2E verification rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Invalid hold E2E verification promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Invalid hold E2E verification unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Invalid hold E2E verification changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=expired_quote_e2e_main()
    if result==0:print('PASS exact bounded invalid hold E2E verification; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result




def rejected_verification_e2e_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='57e7383252b5c980954e0196f816b07e02adff59'
    owned={'frontend/tests/safe-navigation.test.mjs', 'frontend/tests/synthetic-e2e.mjs', 'frontend/tests/safe-navigation.mjs', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java'}
    registries={'knowledge/execution/status.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added=set()
    hashes={'knowledge/ARCHITECTURE.md': 'a5b55891cdced9779793e68947532c858c49b78a2970323dcd9d1944806e9f25', 'knowledge/execution/test-plan.json': 'a3e593d4f06c1eb9971b4e5c9ffcb20ecfdbf81e4afd145364fbecc6bf264926', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '3d4827958a85a559122b048bfa0db9bc12adeeee3fa72534ae81fcc30ab557a4', 'knowledge/execution/requirements.json': '9d29380fd6d5214d19cb94a5c7661da809e5dfae8a84a2e62d43f5179cc7163c', 'knowledge/execution/status.json': 'ffff87c345aab09bcbbd48de2be289624c26f0f67d5e8e5b63ccfc67f839c136', 'knowledge/PROGRESS.md': '90b82798c79143dd11acc9d5fd3f69e16ac36da103cd6e6759d009d12d2fd1ed', 'frontend/tests/synthetic-e2e.mjs': 'c2f927a677dbda1500e2997370aedee1c8206d674e1a60f3d4a9f5b729487122', 'frontend/tests/safe-navigation.test.mjs': 'e64dad296c5f9ed28421cbc6a57784b4594d8c5f8ae9daab6af557e2b8830540', 'frontend/tests/safe-navigation.mjs': '9abe0f093cda2bfff8beebb4cc14863e0782427f99ea5841b3d2261c2c79a49e'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Rejected verification E2E exact source/approval mismatch '+name)
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eRejectedVerificationEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Rejected verification E2E changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eRejectedVerification',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Rejected verification E2E changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Rejected verification E2E changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Rejected verification E2E rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Expired quote E2E changed frontend dependencies/tests')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Rejected verification E2E rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Rejected verification E2E promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Rejected verification E2E unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Rejected verification E2E changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=invalid_hold_e2e_main()
    if result==0:print('PASS exact bounded rejected verification E2E; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result





def uncertain_upload_e2e_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='4fccf4daafa13fa06a7d85ffcf87b6f75298beec'
    owned={'frontend/package.json', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java', 'frontend/tests/synthetic-e2e.mjs', 'frontend/tests/upload-fault-proxy.mjs', 'frontend/tests/upload-fault-proxy.test.mjs'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/PROGRESS.md', 'knowledge/ARCHITECTURE.md'}
    added={'frontend/tests/upload-fault-proxy.mjs', 'frontend/tests/upload-fault-proxy.test.mjs'}
    hashes={'frontend/package.json': '5e1924632e60203d0a787f040f50e8a9f90f500e67f804317f1f828334f0eaaa', 'knowledge/PROGRESS.md': '3da80a7129d806df0b1f0b0c3e67e2081be77079be3c3e44cd2c59e72a821efd', 'knowledge/execution/status.json': '621f564faa0b1dd3dbde05a4c409f6218450f3b53347c493bddc91676928b6e3', 'knowledge/execution/test-plan.json': '0af65b2fa7ff7b6ad2aed2fde41950f4bebe6ac3a2902675d39190067504e72e', 'frontend/tests/upload-fault-proxy.test.mjs': '192a86501c50b673ffab7e9cec50fc1ffd022ae201ec1e17391de558c05d57ea', 'backend/src/test/java/com/cinenest/SyntheticJourneyIT.java': '3f806222ddca6563cb7fa1b366682fdc8fc7e05e73f21bc6ac82f721494127cf', 'knowledge/execution/requirements.json': 'c25ab3f21e34c4d495891bddb231ff10a3bd0aa58da942c7e1fede9a915f91b1', 'frontend/tests/synthetic-e2e.mjs': '52a37a67a6c4c1e07b9bd65f63c08a6a167cbb31368a2d812fc44599b61cffa7', 'frontend/tests/upload-fault-proxy.mjs': 'f302187b10d47b6ced32e2c61cbb5a37dcb3444f0c8da3392608aa17b9b772fb', 'knowledge/ARCHITECTURE.md': '00b29e209271d5ca0f67d1f0867ace9e4d5af42e2e400268b13d21c2765a1050'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Uncertain upload E2E verification exact source/approval mismatch '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('e2eUncertainUploadEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Uncertain upload E2E verification changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('e2eUncertainUploadVerification',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Uncertain upload E2E verification changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Uncertain upload E2E verification changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Uncertain upload E2E verification rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());original=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=original['scripts']['test']+' && node --test tests/upload-fault-proxy.test.mjs':errors.append('Uncertain upload E2E changed existing frontend test coverage')
    package['scripts']['test']=original['scripts']['test']
    if package!=original:errors.append('Uncertain upload E2E changed frontend dependency/toolchain authority')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Uncertain upload E2E verification rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Uncertain upload E2E verification promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Uncertain upload E2E verification unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Uncertain upload E2E verification changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=rejected_verification_e2e_main()
    if result==0:print('PASS exact bounded uncertain upload E2E verification; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result





def staff_roster_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='2ea73dec2152dc6979813137278f6352e200582b'
    owned={'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'frontend/tests/staff-guest-roster-fixtures.mjs', 'frontend/components/staff-guest-roster.tsx', 'frontend/tests/staff-guest-roster.test.mjs', 'frontend/tests/staff-guest-roster-browser.mjs', 'frontend/components/staff-bookings.tsx', 'frontend/package.json'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/requirements.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md', 'knowledge/DESIGN.md'}
    added={'frontend/tests/staff-guest-roster-fixtures.mjs', 'frontend/components/staff-guest-roster.tsx', 'frontend/tests/staff-guest-roster.test.mjs', 'frontend/tests/staff-guest-roster-browser.mjs'}
    hashes={'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '4c25f07b6d360b021b48db645c92f024e94241ea94ad37f934b782ba076937b9', 'frontend/components/staff-guest-roster.tsx': '7285507f745a282e4f08ce9254cd70a58a37124465d0fda65d5d001a588dd260', 'knowledge/execution/requirements.json': '599f079e15fadb80770595a5724ae64ec54d6689a7bdd64947c743cdb35aefd7', 'frontend/tests/staff-guest-roster-browser.mjs': '03b26b75891026bf3d2fcf31f8b9d51542340369ea87184ca4c855b4348ac64f', 'knowledge/execution/status.json': 'feed740c5c9241b3b174bd3be0de78da3149c8fef7d5e7cdb45b9984c0985538', 'knowledge/ARCHITECTURE.md': '4f2a10e271d5365a362c6ea17220ba3eb2bf0cb402d21def5235f7c1c7519c2c', 'frontend/tests/staff-guest-roster-fixtures.mjs': '2fb47ad19374f76f3e2aebb421082ac84954d8c0c5aaa73d931d9e6c85548f62', 'knowledge/PROGRESS.md': '2633828e9ae7731a4c3ba669da2fdb4348bb3222010931cf14ee210d026aeafb', 'frontend/tests/staff-guest-roster.test.mjs': '8068f5b8a6c3943c3eb25cab5dcb8eb2c941ad09cc4beee7bd702f11621416ef', 'knowledge/DESIGN.md': 'f5ae350c8d6bb20ed2c1f51367eea08771b38d5110acc1b72fd27d8f8e1d1cba', 'frontend/components/staff-bookings.tsx': '718df8b8811c994ad24f19d3c8d60fda1593a0424a9bab1a21dcd95ec2c3bc49', 'knowledge/execution/test-plan.json': '714090814a1ada867e15cc2f6a30b76946c724476eb880fb828bc162936d6430', 'frontend/package.json': '8218c376f3ba1ad80e4978ce25ac166abfeb7b7030148d49babda73b9a7d9c89'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff roster integration exact source/approval mismatch '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('staffGuestRosterEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff roster integration changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('staffGuestRosterImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff roster integration changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff roster integration changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Staff roster integration rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());original=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=original['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/staff-guest-roster.test.mjs':errors.append('Staff roster integration changed existing frontend test coverage')
    package['scripts']['test']=original['scripts']['test']
    if package!=original:errors.append('Staff roster integration changed frontend dependency/toolchain authority')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Staff roster integration rewrote prior design')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff roster integration promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff roster integration unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff roster integration changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=uncertain_upload_e2e_main()
    if result==0:print('PASS exact bounded staff roster read integration; backend/policy/graph/full acceptance unchanged; historical controls retained')
    return result








def staff_booking_access_contract_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='40c88e9c4339a095646d57ecfa2f4c94a5876c88'
    owned={'backend/src/test/java/com/cinenest/access/internal/StaffBookingAccessTest.java', 'backend/src/main/java/com/cinenest/access/internal/AccessController.java', 'backend/src/main/java/com/cinenest/access/internal/JdbcAccessRepository.java', 'backend/src/main/java/com/cinenest/access/internal/StaffBookingAccessView.java', 'backend/src/main/java/com/cinenest/access/internal/AccessService.java', 'backend/src/main/java/com/cinenest/access/internal/AccessRepository.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/status.json', 'knowledge/execution/test-plan.json'}
    canonical={'knowledge/PROGRESS.md', 'knowledge/ARCHITECTURE.md'}
    added={'backend/src/test/java/com/cinenest/access/internal/StaffBookingAccessTest.java', 'backend/src/main/java/com/cinenest/access/internal/StaffBookingAccessView.java'}
    hashes={'backend/src/test/java/com/cinenest/access/internal/StaffBookingAccessTest.java': '2c88450895c13d7f4ae0d7cf45a0500bacd2588ce021d110aec672c36a2acf8c', 'knowledge/PROGRESS.md': '5edece7c19974d18eac772e5bc9ea5234f5c43d63a21f8f8ba290bc2050e52c8', 'knowledge/ARCHITECTURE.md': '9a8c3a70ae6997e1f262cd705dd4d30cde21e4df881ad704a5de2820a9301ab5', 'backend/src/main/java/com/cinenest/access/internal/AccessController.java': 'ad532e156e47f4deb9200550e7921174b9649b428ccb082d9ef4dc10b28da426', 'backend/src/main/java/com/cinenest/access/internal/JdbcAccessRepository.java': '4fa87eb9977dddc12b6d43cfed7287f55726a29a6958a26dd97f23b5bbcb84f4', 'backend/src/main/java/com/cinenest/access/internal/StaffBookingAccessView.java': 'a212627ff0af13393ca227417e5cc2e6db653d47ae8ef245e37c144297863090', 'backend/src/main/java/com/cinenest/access/internal/AccessService.java': '6f336419759cd03b53b4a6d30cc2d4088a8e1e63a5f662836e1e0a87bbe6c2ed', 'knowledge/execution/test-plan.json': '7afd9afcc28a1d4f4b482f1bebbdef3c2635dd454556d9697709c8f7c3c5e409', 'backend/src/main/java/com/cinenest/access/internal/AccessRepository.java': '180e5093bbec5f9ce439ca1281b3a4b98e6e59ee8db1a41a883d4e9a06c8a3bc', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '181dc8d6462eb25d09b32c0d21b75da7ef944f8e69f5ed27ed8bf2257cede73e', 'knowledge/execution/requirements.json': '55aef810d84fcacba1e89e11f8423de771e5fbf84102dc7d1cbd82967afd5c19', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java': '14214595d1695e4285a5e068b63498453b7cdc2cc19f6e8d9f3439886ced01df', 'knowledge/execution/status.json': 'c91293b3f6e9c57eb6814ec7aae5a4a8321744322857767bbf59602ac0de9a74'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff booking access read exact source/approval mismatch '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('staffBookingAccessReadEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff booking access read changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('staffBookingAccessReadImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff booking access read changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking access read changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Staff booking access read rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Staff booking access read changed frontend commands/dependencies')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff booking access read promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff booking access read unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking access read changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=staff_roster_main()
    if result==0:print('PASS exact bounded staff booking access read; approved backend delta pinned;policy/graph/full acceptance unchanged; historical controls retained')
    return result




def booking_access_context_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='8cc9c5aa1bf14e154ba40242ca215705cdbd7090'
    owned={'frontend/package.json', 'frontend/tests/staff-booking-access-fixtures.mjs', 'frontend/components/staff-bookings.tsx', 'frontend/tests/staff-booking-access.test.mjs', 'frontend/components/staff-booking-access.tsx', 'frontend/lib/bank-backend.ts', 'frontend/tests/staff-booking-access-browser.mjs', 'frontend/components/access.tsx', 'frontend/app/admin/access/page.tsx', 'frontend/app/globals.css', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/access/route.ts', 'frontend/lib/staff-booking-access.ts'}
    registries={'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/status.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    added={'frontend/tests/staff-booking-access.test.mjs', 'frontend/components/staff-booking-access.tsx', 'frontend/tests/staff-booking-access-browser.mjs', 'frontend/tests/staff-booking-access-fixtures.mjs', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/access/route.ts', 'frontend/lib/staff-booking-access.ts'}
    hashes={'frontend/package.json': '6618ad578663d69a9ddd1898b7445db478a335763ba1a4bd13cc6a189f2bd18b', 'knowledge/DESIGN.md': '115d30c38761fc1bbeae15478239a19bfa0f907c7f1a82bd5f2d7ff421bd45de', 'frontend/tests/staff-booking-access-fixtures.mjs': 'b6215f9b2e97e6ae331258c0ff044f66fb49d2199d9ba3141672f6ec6af349f2', 'knowledge/PROGRESS.md': '6d62652211916dcc0312cbba380846c737eb29a7656cde779ec444a6b1b15c00', 'frontend/components/staff-bookings.tsx': 'afab87959a45a3e4f7dfc53db81ac0eb4d6c5775f66b6288db7112037103478e', 'frontend/tests/staff-booking-access.test.mjs': '671cde4bc3cb0a64dc92f9bb710247643c8eb59cc96000367a41299634b7e331', 'knowledge/execution/test-plan.json': 'd2fadcd6b5abc718f56b55e78b9a83bf83ba3b320de8d52ad752756b18e2c109', 'frontend/components/staff-booking-access.tsx': '87dd7f5f6b3a1976cbf689c33117b1468aa53486af9bfaf4d27655fba12a067d', 'frontend/lib/bank-backend.ts': '214d1f4a64809f4610c7aa68444247dead6efdeacbdebdf74afcf7a5a9619b74', 'knowledge/execution/requirements.json': '7c9869ad02fa3a6096c2e5b3a4974cce7bcb0ee6135c6e6bbc86b0959bf65371', 'frontend/tests/staff-booking-access-browser.mjs': 'e082ff52197a0ec723aaf34aa4f428793359eb85d978a3fa9bc04d8c12bd3a6b', 'knowledge/ARCHITECTURE.md': '28e2e5a7eea73644eb0a2b9565e01d3e446836b5cce65f017509d77530132668', 'frontend/components/access.tsx': '1b7ecbb1ea431c94f668dcd45921348cf11c89cf71587cc0d515519f214960a4', 'frontend/app/admin/access/page.tsx': 'fa4f0f1f41dd814c5bb6a6d67abd6e71a0333c7755dd785faaed797db2ad8577', 'frontend/app/globals.css': '5d435ba89da427cbbec7a0a90b239640edecf65308228ecb10be1f5b781fbcbb', 'knowledge/execution/status.json': '975df2fee49b889fca6491122dd0d02614775b698352b76dc20673be66dd5e63', 'frontend/app/api/admin/venues/[venueId]/bookings/[booking]/access/route.ts': 'be10bb7cea6f974cf54c7c960655674208c13049f5429999bfad15fd88f76640', 'frontend/lib/staff-booking-access.ts': 'e7620c52271e8859826e826041bd03cef9703d79068cbc6de32b5ee1560a096e'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Booking access context integration exact source/approval mismatch '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('bookingAccessContextEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Booking access context integration changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('bookingAccessContextImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Booking access context integration changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking access context integration changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Booking access context integration rewrote prior architecture')
    package=json.loads((R/'frontend/package.json').read_text());original=json.loads(prior('frontend/package.json'))
    if package['scripts']['test']!=original['scripts']['test']+' && node --import ./tests/tsx-loader.mjs tests/staff-booking-access.test.mjs':errors.append('Booking access context integration changed existing frontend test coverage')
    package['scripts']['test']=original['scripts']['test']
    if package!=original:errors.append('Booking access context integration changed dependency/toolchain authority')
    if not (K/'DESIGN.md').read_text().startswith(prior('knowledge/DESIGN.md').decode()):errors.append('Booking access context integration rewrote prior design')
    if not (R/'frontend/app/globals.css').read_text().startswith(prior('frontend/app/globals.css').decode()):errors.append('Booking access context integration rewrote prior global styles')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Booking access context integration promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Booking access context integration unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Booking access context integration changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=staff_booking_access_contract_main()
    if result==0:print('PASS exact bounded booking access context integration; approved frontend delta pinned;policy/graph/full acceptance unchanged; historical controls retained')
    return result




def inert_ci_main():
    """Qualify exact owner-authorized booking presentation source before historical replay."""
    from unittest.mock import patch
    baseline='ac3275500e0ed30e880d89f0060ff4bd264546ab'
    owned={'backend/src/main/java/com/cinenest/booking/SyntheticBooking.java', 'backend/src/test/java/com/cinenest/payments/internal/StaffBookingRefundQueryTest.java', 'backend/src/main/java/com/cinenest/payments/internal/RefundController.java', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java', 'backend/src/main/java/com/cinenest/booking/internal/SyntheticBookingAdapter.java', 'backend/src/main/java/com/cinenest/payments/internal/JdbcRefundRepository.java', 'backend/src/main/java/com/cinenest/payments/internal/RefundRepository.java', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java', 'backend/src/main/java/com/cinenest/payments/internal/StaffBookingRefundQuery.java'}
    registries={'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/requirements.json'}
    canonical={'knowledge/ARCHITECTURE.md', 'knowledge/PROGRESS.md'}
    added={'backend/src/test/java/com/cinenest/payments/internal/StaffBookingRefundQueryTest.java', 'backend/src/main/java/com/cinenest/payments/internal/StaffBookingRefundQuery.java'}
    hashes={'knowledge/PROGRESS.md': '38cc25b87504d5bb33da8a9333243d4663cabddf5b68e0c455c0aa4bfec1b75e', 'knowledge/ARCHITECTURE.md': '1eda64f0aebf3ae9820b51264898931803e5f2f40ce5f4ebb52eeccf170cf169', 'backend/src/main/java/com/cinenest/booking/SyntheticBooking.java': '9f8df1119010072eb582877a61b3494c44b97bd22bca371f8d9bb191e32244d7', 'knowledge/execution/requirements.json': '529028c6b24bb38bca8de40cbffda6994f41a5c279b6a3c79af641465aa458b9', 'backend/src/test/java/com/cinenest/payments/internal/StaffBookingRefundQueryTest.java': '05ff3f90f041890bcf12e5bbc4ed219aeb6ae0483e1827551cd46a01cbaa1736', 'knowledge/execution/status.json': 'fe70fa9f8b61a567698a8fcf1ea32d5511372c2a4abf4fccd1d3b18dc807213c', 'backend/src/main/java/com/cinenest/payments/internal/RefundController.java': 'b144f8ca848a8263046f9fcdd31e672ebf852680d1a05f3de314bd3a16af9971', 'backend/src/test/java/com/cinenest/BankIntegrationTest.java': '40063390b617d258a82deceb701b8b96f3fa0fbc9a72d61f29d2498108c78c83', 'backend/src/main/java/com/cinenest/booking/internal/SyntheticBookingAdapter.java': '88585286f0ad579d0089c8d0d5b7e5c7b77cff04fe92384be864cb6cee5c6b75', 'backend/src/main/java/com/cinenest/payments/internal/JdbcRefundRepository.java': 'dbecfcc5779364ef605ddcc5cc34e262ad2f917927054875a9bb7bff13ac266e', 'knowledge/execution/test-plan.json': 'e7e03fdf6da9402b8286a24c20f1b5b52f6c80e7255d5660974614e3ef5e161d', 'backend/src/main/java/com/cinenest/payments/internal/RefundRepository.java': '63718ba2e3f539b1111896eeb0af9720294defad60037c614c109565142e6cb8', 'backend/src/main/java/com/cinenest/identity/internal/IdentitySecurity.java': '6d56b2eb2af0393a8ade8a40cb89074657e6d0e2274853fc86e404957c7512aa', 'backend/src/main/java/com/cinenest/payments/internal/StaffBookingRefundQuery.java': '9836a1a911ba2c89c3db0358b12f0d309e1812cf4193078add197570c243fd59'}
    errors=[]
    def prior(name):return subprocess.check_output(['git','-C',str(R),'show',baseline+':'+name])
    for name,digest in hashes.items():
        if not (R/name).is_file() or hashlib.sha256((R/name).read_bytes()).hexdigest()!=digest:errors.append('Staff booking refund read exact source/approval mismatch '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    for name,key in [('knowledge/execution/requirements.json','requirements'),('knowledge/execution/test-plan.json','tests')]:
        current=json.loads((R/name).read_text())
        for row in current[key]:row.pop('staffBookingRefundReadEvidence',None)
        if current!=json.loads(prior(name)):errors.append('Staff booking refund read changed full requirement/test authority '+name)
    current=json.loads((K/'execution/status.json').read_text());current.pop('staffBookingRefundReadImplementation',None)
    if current!=json.loads(prior('knowledge/execution/status.json')):errors.append('Staff booking refund read changed unrelated execution state')
    for name in ['knowledge/execution/change-sets.json','knowledge/decisions/cinenest-open-decisions.json','.github/workflows/foundation.yml','scripts/ci-classify.py','frontend/package-lock.json','backend/pom.xml']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking refund read changed graph/global policy/frontend/dependency/CI '+name)
    if not (K/'ARCHITECTURE.md').read_text().startswith(prior('knowledge/ARCHITECTURE.md').decode()):errors.append('Staff booking refund read rewrote prior architecture')
    if (R/'frontend/package.json').read_bytes()!=prior('frontend/package.json'):errors.append('Staff booking refund read changed frontend commands/dependencies')
    pattern=r'^- \[([ x])\] \*\*(P\d+-C\d+|D\d+-(?:HELD|SCOPE-ACCEPTED))'
    if re.findall(pattern,(K/'PROGRESS.md').read_text(),re.M)!=re.findall(pattern,prior('knowledge/PROGRESS.md').decode(),re.M):errors.append('Staff booking refund read promoted phase/demo acceptance')
    allowed=set(owned)|set(registries)|set(canonical)|{'knowledge/manifest.json','knowledge/execution/validate_execution.py','frontend/next-env.d.ts'}
    changed=set(subprocess.check_output(['git','-C',str(R),'diff',baseline,'--name-only'],text=True).splitlines())
    if not changed<=allowed:errors.append('Staff booking refund read unrelated delta '+str(changed-allowed))
    for name in ['knowledge/tokens/cinenest-design-tokens.json','knowledge/styles/cinenest-theme.css','frontend/styles/breakpoints.css','frontend/lib/bank.ts','frontend/lib/verification.ts','frontend/lib/quotes.ts','knowledge/CineNest Cinema Admin Dashboard Mockup-1.png','knowledge/CineNest Cinema Dashboard Showcase-2.png','knowledge/CineNest Private Cinema Booking UI.png']:
        if (R/name).read_bytes()!=prior(name):errors.append('Staff booking refund read changed protected visual/disclosure authority '+name)
    if errors:print('\n'.join('FAIL '+e for e in errors));return 1
    # Exact successor source and scope have passed. Historical validation receives
    # its original inputs only; no unrelated integrity exception is introduced.
    replacements={R/name:prior(name) for name in (set(owned)-added)|set(registries)|set(canonical)}
    manifest=json.loads((K/'manifest.json').read_text())
    for entry in manifest['files']:
        name='knowledge/'+entry['path']
        if R/name in replacements:entry['sha256']=hashlib.sha256(prior(name)).hexdigest()
    replacements[K/'manifest.json']=json.dumps(manifest,ensure_ascii=False).encode()
    rb0,rt0,g0,out0=Path.read_bytes,Path.read_text,Path.glob,subprocess.check_output
    def rb(path,*a,**kw):return replacements[path] if path in replacements else rb0(path,*a,**kw)
    def rt(path,*a,**kw):return replacements[path].decode() if path in replacements else rt0(path,*a,**kw)
    def glob(path,pattern,*a,**kw):return (p for p in g0(path,pattern,*a,**kw) if p.relative_to(R).as_posix() not in added)
    def output(argv,*a,**kw):
        value=out0(argv,*a,**kw)
        if isinstance(argv,list) and argv[:3]==['git','-C',str(R)] and (('diff' in argv and '--name-only' in argv) or 'ls-files' in argv):
            removed=set(owned)|set(registries)|set(canonical) if 'diff' in argv else added
            text=value if isinstance(value,str) else value.decode();text=''.join(line+'\n' for line in text.splitlines() if line not in removed)
            return text if isinstance(value,str) else text.encode()
        return value
    with patch.object(Path,'read_bytes',rb),patch.object(Path,'read_text',rt),patch.object(Path,'glob',glob),patch.object(subprocess,'check_output',output):result=booking_access_context_main()
    if result==0:print('PASS exact bounded staff booking refund read; approved backend delta pinned;policy/graph/full acceptance unchanged; historical controls retained')
    return result

if __name__ == '__main__':
    raise SystemExit(inert_ci_main())
