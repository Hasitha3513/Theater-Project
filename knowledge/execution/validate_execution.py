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
    for path in R.rglob('*.md'):
        if '.git' in path.parts:
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
    changes = backlog['changeSets']; reqs = coverage['requirements']; tests = tests_doc['tests']
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
            check(c.get('authorizationSource') == 'S11' and c.get('approvalStatus') == 'AWAITING_OWNER_APPROVAL' and c.get('implementationAuthorization') == 'NOT_GRANTED', 'Decision brief promoted to stack/code authority')
            check(c.get('pendingDecisionIds') == ['OD-01'] and c['blockingDecisionIds'] == [], 'Brief/selection gates conflated')
        elif c['id'] == 'CN-KB-CONSOLIDATE-001':
            check(c['authorityAvailable'] is True and c['readiness'] == 'CLOSED' and c['workStatus'] == 'VERIFIED' and c.get('authorizationSource') == 'S12', 'Consolidation authority/status missing')
            check(c.get('deletionApproval') == 'PENDING_OWNER_REVIEW', 'Unauthorized deletion approval')
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
    check(state['futurePlanApproval'] == 'NOT_APPROVED' and state['nextReadyChangeSet'] is None, 'Unsupported READY plan')
    check(state['taskId'] == 'CN-KB-CONSOLIDATE-001' and state['nextRecommendedChangeSet'] is None, 'Unexpected next checkpoint')
    check(state.get('stackApproval') == 'AWAITING_OWNER_APPROVAL' and state.get('implementationAuthorization') == 'NOT_GRANTED', 'Unsupported stack/implementation approval')
    od = json.loads((K / 'decisions/cinenest-open-decisions.json').read_text())['decisions']
    check(next(x for x in od if x['id'] == 'OD-01')['decisionStatus'] == 'OPEN_DECISION', 'OD-01 silently accepted')
    brief = K / 'ARCHITECTURE.md'
    check(brief.is_file() and 'PROPOSED / AWAITING OWNER APPROVAL' in brief.read_text(), 'Missing proposed decision brief')
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
                    check(after[id].get(key) == old[key], 'Baseline status changed ' + id + '/' + key)
    canonical_map = {e['sourcePath'].removeprefix('knowledge/'): e['canonicalPath'].removeprefix('knowledge/') + '#' + e['anchor'] for e in manifest['consolidation']['documents']}
    def migrated(value):
        if isinstance(value, dict):
            return {key: migrated(item) for key, item in value.items()}
        if isinstance(value, list):
            return [migrated(item) for item in value]
        return canonical_map.get(value, value) if isinstance(value, str) else value
    # Compare full requirement/test/decision payloads, not just counts/statuses.
    for path in ['execution/requirements.json', 'execution/test-plan.json', 'decisions/cinenest-open-decisions.json']:
        expected = migrated(json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/' + path], text=True)))
        if path == 'execution/requirements.json':
            workflow = expected['requirements'][0]
            workflow['sourceIds'].append('S12'); workflow['changeSetIds'].append('CN-KB-CONSOLIDATE-001'); workflow['verificationEvidence'].append('PROGRESS.md#current-verification')
        if path == 'execution/test-plan.json':
            workflow = expected['tests'][0]
            workflow['owningChangeSetIds'].append('CN-KB-CONSOLIDATE-001'); workflow['evidencePaths'].append('PROGRESS.md#current-verification')
        check(expected == json.loads((K / path).read_text()), 'Baseline requirement/test/decision content changed beyond authorized mapping ' + path)
    old_backlog = migrated(json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/execution/change-sets.json'], text=True)))
    check(changes[:-1] == old_backlog['changeSets'] and changes[-1]['id'] == 'CN-KB-CONSOLIDATE-001' and changes[-1].get('aliases') == ['CN-KB-CONSOLIDATION-001'] and changes[-1].get('continuationAuthorizationSource') == 'S13', 'Existing backlog content lost/altered')
    old_sources = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/sources/cinenest-source-register.json'], text=True))['sources']
    current_sources = json.loads((K / 'sources/cinenest-source-register.json').read_text())['sources']
    check(current_sources[:-2] == old_sources and [x['id'] for x in current_sources[-2:]] == ['S12', 'S13'], 'Prior source evidence lost/altered')
    old_omissions = json.loads(subprocess.check_output(['git', '-C', str(R), 'show', baseline + ':knowledge/execution/omissions.json'], text=True))['omissions']
    current_omissions = read_json('omissions.json')['omissions']
    check([o['id'] for o in current_omissions] == [o['id'] for o in old_omissions], 'Omission IDs dropped/changed')
    for old, current in zip(old_omissions, current_omissions):
        expected = dict(old)
        if old['id'] in {'OM-02', 'OM-07'}:
            expected['recovery'] = current['recovery']  # Updated pending-stack/canonical-instruction guidance only.
        check(expected == current, 'Omission gap/status silently changed ' + old['id'])
    progress = (K / 'PROGRESS.md').read_text()
    task_rows = ''.join('| ' + c['id'] + ' | ' + str(c['phase']) + ' | ' + c['objective'] + ' | ' + (', '.join(c['dependencies']) or 'None') + ' | ' + c['workStatus'] + ' / ' + c['readiness'] + ' |\n' for c in changes)
    check(task_rows in progress, 'PROGRESS task projection diverges from authority')
    mapping_rows = ''.join('| ' + e['sourcePath'] + ' | [' + e['canonicalPath'] + '#' + e['anchor'] + '](' + e['canonicalPath'].removeprefix('knowledge/') + '#' + e['anchor'] + ') |\n' for e in manifest['consolidation']['documents'])
    check(mapping_rows in progress and '<a id="consolidation"></a>' in progress, 'PROGRESS source/destination/removal mapping omitted')
    project = (K / 'PROJECT.md').read_text()
    feature_catalog = json.loads((K / 'product/cinenest-feature-catalog.json').read_text())['features']
    feature_rows = ''.join('| ' + f['id'] + ' | ' + f['name'] + ' — ' + f['purpose'] + ' | ' + f['module'] + ' | ' + f['decisionStatus'] + ' |\n' for f in feature_catalog)
    check(feature_rows in project, 'PROJECT feature projection diverges from authority')
    architecture = (K / 'ARCHITECTURE.md').read_text()
    decision_rows = ''.join('| ' + d['id'] + ' | ' + d['conflict'] + ' | ' + d['launchBlocker'] + ' |\n' for d in od)
    check(decision_rows in architecture, 'ARCHITECTURE decision projection diverges from authority')
    for number in range(1, 9):
        check(f'AG-AC{number:02d}' in progress, 'Setup acceptance history omitted')
    check(state.get('requestedTaskId') == 'CN-KB-CONSOLIDATION-001', 'Owner task ID alias lost')
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
