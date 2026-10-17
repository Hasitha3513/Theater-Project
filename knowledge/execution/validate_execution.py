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


def main():
    errors = []
    def check(ok, message):
        if not ok:
            errors.append(message)
    def read_json(name):
        try:
            return json.loads((E / name).read_text())
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
        check((R/name).is_file() and hashlib.sha256((R/name).read_bytes()).hexdigest()==digest,'Verification source mismatch '+name)
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
        return subprocess.check_output(['git','-C',str(R),'show',verification_base+':'+name]) if name in verification_owned else (R/name).read_bytes()
    verification_subset={'taskId': 'CN-VERIFY-001', 'status': 'LOCAL_VERIFIED_CI_PENDING', 'evidencePath': 'PROGRESS.md#verification-implementation', 'fullRequirementVerified': False, 'fullRequirementAcceptance': 'NOT_ACCEPTED', 'actualTestPaths': ['backend/src/test/java/com/cinenest/VerificationIntegrationTest.java', 'backend/src/test/java/com/cinenest/verification/internal/SyntheticEvidenceTest.java', 'frontend/tests/verification.test.mjs', 'frontend/tests/verification-browser.mjs'], 'deferred': 'Real-person/OCR/physical all-guest admission, production privacy/providers/retention and full authorization scenarios remain unverified'}
    verification_subset['status']=verification_status
    def verification_original_payload(payload,path):
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
        check((R/name).is_file() and hashlib.sha256((R/name).read_bytes()).hexdigest()==digest,'Local workflow source mismatch '+name)
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
    for data,path in [(backlog,'execution/change-sets.json'),(coverage,'execution/requirements.json'),(tests_doc,'execution/test-plan.json'),(json.loads((K/'decisions/cinenest-open-decisions.json').read_text()),'decisions/cinenest-open-decisions.json')]:
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
    decisions = {d['id'] for d in json.loads((K / 'decisions/cinenest-open-decisions.json').read_text())['decisions']}
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
    check(state['futurePlanApproval'] == 'NOT_APPROVED' and state['nextReadyChangeSet'] is None and [c['id'] for c in changes if c['readiness'] == 'READY'] == ([] if verification_ci else ['CN-VERIFY-001']), 'Unsupported READY plan')
    check(state['taskId'] == 'CN-VERIFY-001' and state['nextRecommendedChangeSet'] == 'CN-PAY-BANK-001' and state.get('nextReadyChangeSet') is None, 'Unexpected next checkpoint')
    check(state['workStatus'] == ('VERIFIED' if verification_ci else 'IN_PROGRESS') and cmap['CN-KB-CONSOLIDATE-001']['workStatus'] == 'VERIFIED' and cmap['CN-FND-001']['workStatus'] == 'VERIFIED' and state.get('authorizationUpdate', {}).get('status') == 'VERIFIED_DOCUMENTATION_ONLY', 'Documentation and implementation status conflated')
    check(state.get('stackApproval') == 'OWNER_APPROVED_INITIAL_DIRECTION' and state.get('implementationAuthorization') == 'GRANTED_SYNTHETIC_CN_VERIFY_001_ONLY', 'Unsupported stack/implementation approval')
    od = json.loads((K / 'decisions/cinenest-open-decisions.json').read_text())['decisions']
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
        current = json.loads((K / path).read_text())
        before = {x['id']: x for x in prior[field]}; after = {x['id']: x for x in current[field]}
        check(set(before) <= set(after), 'Baseline IDs dropped ' + path)
        for id, old in before.items():
            for key in ['decisionStatus', 'implementationStatus', 'acceptanceStatus', 'plannedTestStatus', 'workStatus', 'status', 'authorityAvailable', 'readiness', 'approvalStatus', 'implementationAuthorization']:
                if key in old:
                    expected_status = 'APPROVED' if id in {'OD-04','OD-10','OD-11','OD-12','OD-14'} and key == 'decisionStatus' else 'OWNER_APPROVED_INITIAL_DIRECTION' if id == 'CN-DEC-001' and key == 'approvalStatus' else old[key]
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
        current_payload = json.loads((K / path).read_text())
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
    task_rows = ''.join('| ' + c['id'] + ' | ' + str(c['phase']) + ' | ' + c['objective'] + ' | ' + (', '.join(c['dependencies']) or 'None') + ' | ' + c['workStatus'] + ' / ' + c['readiness'] + ' |\n' for c in changes)
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
            check((R/name).read_bytes()==b'const nextConfig = { poweredByHeader: false, agentRules: false };\nexport default nextConfig;\n','Local workflow changed unrelated frontend configuration')
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
        check(phase in roadmap_meta['phaseIds'] and deps == (', '.join(cmap[tid]['dependencies']) or 'None') and status == cmap[tid]['workStatus']+' / '+cmap[tid]['readiness'], 'Task dependency/status altered by roadmap ' + tid)
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


if __name__ == '__main__':
    raise SystemExit(main())
