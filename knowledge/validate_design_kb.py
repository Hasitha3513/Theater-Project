"""Validate CineNest KB consistency. No application implementation or external dependencies."""
from pathlib import Path
import hashlib
import json
import re
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent
# No remaining unmanaged artifacts: the owner now authorizes publication of the reviewed diagrams.
UNMANAGED_WORKSPACE_ASSETS = {}
REQUIRED = [
    'CINENEST-DESIGN-SYSTEM.md', 'CINENEST-ADMIN-PAGES.md',
    'CINENEST-CUSTOMER-BOOKING-PAGES.md', 'CINENEST-CHARTS-AND-METRICS.md',
    'CINENEST-DESIGN-DECISION-ADR-001.md', 'CINENEST-REFERENCE-TRACEABILITY.md',
    'CINENEST-UX-ACCEPTANCE-CHECKLIST.md', 'tokens/cinenest-design-tokens.json',
    'tokens/cinenest-chart-specs.json', 'styles/cinenest-theme.css',
    'README.md', 'CINENEST-VALIDATION-REPORT.md',
]
IMAGES = {
    'CineNest Cinema Admin Dashboard Mockup-1.png': '52747e612b9b707ac573051d0004475d77ec4aae2dfae61abc7ce0256e38b70f',
    'CineNest Cinema Dashboard Showcase-2.png': '27b8ec2a308f905b39fcbf2f5d6c24db241628475f6c29e9fc6060da5b528ca7',
    'CineNest Private Cinema Booking UI.png': 'a8669e2d3d21df7eda45aba2c95608079f02f280ad0d106de779378a681d8852',
}
ADMIN = ['Dashboard', 'Booking Management', 'Cinema Room Management', 'Room Live Control',
         'Customer Management', 'Payments and Refunds', 'IoT Device Management',
         'Analytics and Reports', 'Promotions and Loyalty', 'System Settings', 'Audit Logs']
CUSTOMER = ['Landing Page', 'Google Authentication', 'Cinema Room Selection',
            'Date and Time Selection', 'Identity Verification', 'Booking Summary',
            'Payment Processing', 'Booking Confirmation', 'Smart Door Access PIN',
            'Customer Dashboard', 'Booking History', 'Loyalty and Referrals']
FIELDS = ['Page layout', 'Components', 'Tables', 'Filters', 'Buttons', 'Forms', 'Charts',
          'Status indicators', 'Permissions', 'Responsive behavior', 'Error and empty states']



def consolidation_entries():
    return json.loads((ROOT / 'manifest.json').read_text())['consolidation']['documents']


def spec_path(name):
    entry = next((e for e in consolidation_entries() if e['sourcePath'] == 'knowledge/' + name), None)
    return ROOT.parent / entry['canonicalPath'] if entry else ROOT / name


def read_spec(name):
    """Read an anchored canonical section, never use the retained snapshot as current truth."""
    entry = next((e for e in consolidation_entries() if e['sourcePath'] == 'knowledge/' + name), None)
    if entry is None:
        return (ROOT / name).read_text()
    text = (ROOT.parent / entry['canonicalPath']).read_text()
    marker = '<a id="' + entry['anchor'] + '"></a>'
    body = text.split(marker, 1)[1].split('<a id="', 1)[0]
    lines = []; fenced = False
    for line in body.splitlines(keepends=True):
        if line.startswith('```'):
            fenced = not fenced
        if not fenced and re.match(r'^#{3,5} ', line):
            line = line[1:]
        lines.append(line)
    return ''.join(lines)


def validate_consolidation(check):
    """Verify source destinations, retained substantive lines, machine authorities and immutable historical originals."""
    import os
    import subprocess
    manifest = json.loads((ROOT / 'manifest.json').read_text())
    migration = manifest['consolidation']; repo = ROOT.parent
    authorization = migration.get('removalAuthorization', {})
    check(migration['deletionApproved'] is True and authorization.get('sourceId') == 'S14', 'Exact owner removal approval missing')
    check(authorization.get('requestSha256') == 'dae698471f7fd41372eac120aa32a2bba96b874b6844232f2f3cac4f3da7cb9d', 'Owner removal authorization provenance mismatch')
    check(migration.get('removalBaselineRevision') == '99fa99e935be72254368b412078e36bbb7a80a00', 'Removal baseline mismatch')
    check(manifest.get('unmanagedWorkspaceAssets') == UNMANAGED_WORKSPACE_ASSETS, 'Unmanaged preflight inventory changed')
    for name, expected_hash in UNMANAGED_WORKSPACE_ASSETS.items():
        # Local untracked artifacts need not exist in a fresh clone; validate exact bytes when present.
        if (ROOT / name).exists():
            check((ROOT / name).is_file() and hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected_hash, 'Unrelated workspace file changed ' + name)
    exports = manifest.get('diagramPublication', {}).get('files', [])
    check(hashlib.sha256(json.dumps(exports, sort_keys=True).encode()).hexdigest() == 'db61dbe6f6378b5ea52e11b45f1f20f555340aa48ecbc991b06cffbafb831878', 'Reviewed diagram export allowlist changed')
    export_paths = {item['path'] for item in exports}
    for item in exports:
        asset = ROOT / item['path']
        check(asset.is_file() and hashlib.sha256(asset.read_bytes()).hexdigest() == item['sha256'], 'Reviewed diagram export changed ' + item['path'])
        check(item['authority'] == 'owner-supplied-proposed-diagram-export' and item['implementationStatus'] == 'NOT_VERIFIED', 'Diagram promoted to canonical/runtime authority')
        if asset.suffix == '.md':
            body = asset.read_text()
            blocks = re.findall(r'^```mermaid[^\n]*\n(.*?)^```\s*$', body, re.S | re.M)
            check(len(re.findall(r'^```', body, re.M)) == 2 and len(blocks) == 1, 'Diagram fence/block mismatch ' + item['path'])
            if len(blocks) == 1:
                check(hashlib.sha256(blocks[0].encode()).hexdigest() == item['mermaidSha256'], 'Supplied Mermaid diagram code rewritten ' + item['path'])
            check(bool(re.search(r'(?i)proposed|planned behavior', body[:1000])) and bool(re.search(r'(?i)not verified|not evidence|not established', body[:1000])), 'Diagram status notice missing ' + item['path'])
    canonical = {'knowledge/PROJECT.md', 'knowledge/ARCHITECTURE.md', 'knowledge/DESIGN.md', 'knowledge/PROGRESS.md'}
    check(set(migration['canonicalDocuments']) == canonical, 'Four-document canonical inventory missing')
    entries = migration['documents']; paths = [e['sourcePath'] for e in entries]
    expected = set(subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', migration['baselineRevision']], text=True).splitlines())
    expected = {p for p in expected if p.endswith('.md') and p not in {'AGENTS.md', 'knowledge/README.md'}}
    check(set(paths) == expected and len(paths) == len(expected), 'Superseded prose source omitted/duplicated')
    check(set(authorization.get('approvedPaths', [])) == expected and len(authorization.get('approvedPaths', [])) == len(expected), 'Removal allowlist changed/duplicated')
    removal_tree = set(subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', migration['removalBaselineRevision']], text=True).splitlines())
    missing = {name for name in removal_tree if not (repo / name).is_file()}
    check(missing == expected, 'Unlisted deletion or incomplete approved removal')
    current_md = {name for name in subprocess.check_output(['git', '-C', str(repo), 'ls-files', '--cached', '--others', '--exclude-standard'], text=True).splitlines() if name.endswith('.md') and (repo / name).is_file() and name not in {'knowledge/' + name for name in UNMANAGED_WORKSPACE_ASSETS} and name not in {'knowledge/' + name for name in export_paths}}
    check(current_md == {name for name in removal_tree if name.endswith('.md')} - expected, 'New narrative file or unlisted narrative deletion')
    mapping = {(repo / e['sourcePath']).resolve(): (repo / e['canonicalPath'], e['anchor']) for e in entries}
    for e in entries:
        raw = subprocess.check_output(['git', '-C', str(repo), 'show', migration['baselineRevision'] + ':' + e['sourcePath']])
        check(hashlib.sha256(raw).hexdigest() == e['baselineSha256'], 'Migration baseline mismatch ' + e['sourcePath'])
        source = repo / e['sourcePath']; dest = repo / e['canonicalPath']
        text = dest.read_text()
        check(('<a id="' + e['anchor'] + '"></a>') in text, 'Canonical anchor missing ' + e['anchor'])
        # Editorial preservation checks the pre-edit substantive lines, not copied metadata/headings.
        editorial = migration['editorialBaselineRevision']
        old_doc = subprocess.check_output(['git', '-C', str(repo), 'show', editorial + ':' + e['canonicalPath']], text=True)
        begin = '<!-- migrated-begin:' + e['sourcePath'] + ' -->\n'; end = '\n<!-- migrated-end:' + e['sourcePath'] + ' -->'
        body = old_doc.split(begin, 1)[1].split(end, 1)[0]
        coverage = next((c for c in migration['editorialCoverage'] if c['sourcePath'] == e['sourcePath']), None)
        check(coverage is not None, 'Editorial source coverage omitted ' + e['sourcePath'])
        if coverage and coverage['method'] == 'RETAINED_SUBSTANTIVE_LINES':
            for number, line in enumerate(body.splitlines(), 1):
                if not line.strip() or line.startswith(('#', '> Task:', '> Decision:', '> Source S1:', '> Original record:', '> Status:')):
                    continue
                relocated = next((c for c in migration['editorialAllowedRelocations'] if c['sourcePath'] == e['sourcePath'] and c['sourceLine'] == number), None)
                if relocated:
                    check(hashlib.sha256(line.encode()).hexdigest() == relocated['sha256'] and bool(relocated['reason']), 'Relocation provenance mismatch ' + e['sourcePath'])
                else:
                    check(line in text, 'Unique substantive source line lost ' + e['sourcePath'] + ':' + str(number))
        elif coverage:
            check(coverage['method'] == 'EXISTING_REGISTRY_OR_APPROVAL_EVIDENCE_SUMMARY', 'Unknown editorial coverage method')
            check((repo / coverage['authority']).is_file(), 'Editorial authority missing ' + e['sourcePath'])
        ref = e.get('historicalReference', {})
        check(not source.exists() and e.get('status') == 'REMOVED_WITH_OWNER_APPROVAL', 'Approved removal incomplete ' + e['sourcePath'])
        check(ref == {'commit': migration['baselineRevision'], 'path': e['sourcePath'], 'sha256': e['baselineSha256'], 'url': 'https://github.com/Hasitha3513/Theater-Project/blob/' + migration['baselineRevision'] + '/' + e['sourcePath']}, 'Immutable history reference missing/changed ' + e['sourcePath'])
        check(ref.get('url', '') in (ROOT / 'PROGRESS.md').read_text(), 'Historical original inaccessible from mapping ' + e['sourcePath'])
    allowed_nonprose_edits = {'knowledge/manifest.json', 'knowledge/validate_design_kb.py', 'knowledge/execution/validate_execution.py', 'knowledge/execution/refresh_manifest.py', 'knowledge/product/cinenest-feature-catalog.json', 'knowledge/decisions/cinenest-open-decisions.json', 'knowledge/execution/change-sets.json', 'knowledge/execution/requirements.json', 'knowledge/execution/test-plan.json', 'knowledge/execution/status.json', 'knowledge/execution/omissions.json', 'knowledge/sources/cinenest-source-register.json'}
    all_baseline = subprocess.check_output(['git', '-C', str(repo), 'ls-tree', '-r', '--name-only', migration['baselineRevision']], text=True).splitlines()
    for name in all_baseline:
        if name.endswith('.md') or name in allowed_nonprose_edits:
            continue
        raw = subprocess.check_output(['git', '-C', str(repo), 'show', migration['baselineRevision'] + ':' + name])
        check((repo / name).is_file() and (repo / name).read_bytes() == raw, 'Historical evidence/source/asset changed ' + name)
    # Every active catalog field is preserved except correctly migrated canonical destinations.
    before = json.loads(subprocess.check_output(['git', '-C', str(repo), 'show', migration['baselineRevision'] + ':knowledge/product/cinenest-feature-catalog.json'], text=True))
    after = json.loads((ROOT / 'product/cinenest-feature-catalog.json').read_text())
    for f in before['features']:
        name = f.get('canonicalSpecification')
        if name:
            target, anchor = mapping[(ROOT / name).resolve()]
            f['canonicalSpecification'] = os.path.relpath(target, ROOT) + '#' + anchor
    check(before == after, 'Catalog semantics changed during consolidation')
    for name in canonical:
        path = repo / name
        check(path.is_file(), 'Canonical file missing ' + name)
        for link in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            if link.startswith(('https:', 'http:', 'codex:')):
                continue
            target, sep, anchor = link.partition('#')
            resolved = (path.parent / unquote(target)).resolve() if target else path
            check(resolved.is_file(), 'Canonical link broken ' + link)
            if sep and resolved.name in {'PROJECT.md', 'ARCHITECTURE.md', 'DESIGN.md', 'PROGRESS.md'}:
                check(('<a id="' + anchor + '"></a>') in resolved.read_text(), 'Canonical anchor broken ' + link)
    return len(entries)


def css_from_tokens(tokens):
    lines = ['/* Generated from tokens/cinenest-design-tokens.json by knowledge/validate_design_kb.py. Spec asset only; not wired into an application. */', ':root {']
    for key, value in tokens['color'].items():
        lines.append(f'  --cn-color-{key.replace(".", "-")}: {value};')
    lines.append(f'  --cn-font-family: {tokens["font"]["family"]};')
    for group in ['spacing', 'radius', 'shadow']:
        for key, value in tokens[group].items():
            lines.append(f'  --cn-{group}-{key}: {value};')
    for group in ['size', 'weight', 'lineHeight']:
        for key, value in tokens['font'][group].items():
            lines.append(f'  --cn-font-{group.lower()}-{key}: {value};')
    for key, value in tokens['layout'].items():
        lines.append(f'  --cn-layout-{key.lower()}: {value};')
    for key, value in tokens['motion'].items():
        lines.append(f'  --cn-motion-{key}: {value};')
    gradient = tokens['gradient']['primary']
    stops = ', '.join(f'var(--cn-color-{key.replace(".", "-")})' for key in gradient['stops'])
    lines += [f'  --cn-gradient-primary: linear-gradient({gradient["angle"]}, {stops});',
              '  --cn-background: var(--cn-color-canvas);',
              '  --cn-foreground: var(--cn-color-text-primary);',
              '  --cn-card-background: var(--cn-color-surface);',
              '  --cn-focus-ring: var(--cn-color-focus);', '}',
              '[data-cn-theme="cinematic"] {',
              '  --cn-background: var(--cn-color-cinematic-background);',
              '  --cn-foreground: var(--cn-color-cinematic-text);',
              '  --cn-card-background: var(--cn-color-cinematic-surface);',
              '  --cn-focus-ring: var(--cn-color-brand-gold);', '}',
              '[data-cn-theme="booking-panel"] {',
              '  --cn-background: var(--cn-color-canvas);',
              '  --cn-foreground: var(--cn-color-text-primary);',
              '  --cn-card-background: var(--cn-color-surface);',
              '  --cn-focus-ring: var(--cn-color-focus);', '}',
              '@media (prefers-reduced-motion: reduce) {',
              '  :root { --cn-motion-fast: 0ms; --cn-motion-standard: 0ms; }', '}']
    return '\n'.join(lines) + '\n'


def luminance(hex_value):
    channels = [int(hex_value[i:i+2], 16) / 255 for i in (1, 3, 5)]
    linear = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in channels]
    return sum(x * weight for x, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def main():
    errors = []
    def check(condition, message):
        if not condition:
            errors.append(message)
    for name in REQUIRED:
        check(spec_path(name).is_file(), f'Missing canonical {name}')
    try:
        tokens = json.loads((ROOT / 'tokens/cinenest-design-tokens.json').read_text())
        specs = json.loads((ROOT / 'tokens/cinenest-chart-specs.json').read_text())
    except (OSError, ValueError) as exc:
        print(f'FAIL JSON: {exc}')
        return 1
    colors = tokens['color']
    for key, value in colors.items():
        check(bool(re.fullmatch(r'#[0-9A-F]{6}', value)), f'Invalid HEX {key}')
    for key in tokens['gradient']['primary']['stops']:
        check(key in colors, f'Unknown gradient token {key}')
    for key in ['background', 'foreground', 'hover']:
        check(tokens['component']['button'][key] in colors, f'Invalid button {key}')
    css = css_from_tokens(tokens)
    if '--generate-css' in sys.argv:
        (ROOT / 'styles/cinenest-theme.css').write_text(css)
    check((ROOT / 'styles/cinenest-theme.css').read_text() == css, 'CSS not synchronized; run --generate-css')
    css_defs = set(re.findall(r'(--cn-[\w-]+)\s*:', css))
    check(set(re.findall(r'var\((--cn-[\w-]+)\)', css)) <= css_defs, 'Undefined CSS reference')
    palette = json.loads((ROOT / 'decisions/cinenest-palette-comparison.json').read_text())
    candidate_colors = set(palette['requestedCandidate'].values())
    check(palette['applied'] is False, 'Candidate palette unexpectedly applied')
    ds = read_spec('CINENEST-DESIGN-SYSTEM.md')
    for key, value in colors.items():
        check(f'| `{key}` | `{value}` |' in ds, f'Palette docs mismatch {key}')
    for path in ROOT.rglob('*.md'):
        text = path.read_text()
        for target in re.findall(r'\]\(([^)]+)\)', text):
            if target.startswith(('http:', 'https:', '#')):
                continue
            target = unquote(target.split('#')[0])
            check((path.parent / target).is_file(), f'Broken link {path.name}: {target}')
        export = next((item for item in json.loads((ROOT / 'manifest.json').read_text()).get('diagramPublication', {}).get('files', []) if item['path'] == path.relative_to(ROOT).as_posix()), None)
        for hex_value in re.findall(r'#[0-9A-Fa-f]{6}\b', text):
            check(hex_value.upper() in colors.values() or (path.name in {'CINENEST-DESIGN-DECISION-ADR-002.md', 'ARCHITECTURE.md'} and hex_value.upper() in candidate_colors) or (export is not None and hex_value.upper() in export.get('illustrativeColors', [])), f'Uncentralized color {path.name}: {hex_value}')
    for name, expected in IMAGES.items():
        path = ROOT / name
        check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected, f'Reference changed/missing {name}')
    admin = read_spec('CINENEST-ADMIN-PAGES.md')
    for name in ADMIN:
        check(f'## {name}\n' in admin, f'Admin missing {name}')
        section = admin.split(f'## {name}\n', 1)[-1].split('\n## ', 1)[0]
        for field in FIELDS:
            check(f'| {field} |' in section, f'{name} missing {field}')
    customer = read_spec('CINENEST-CUSTOMER-BOOKING-PAGES.md')
    for name in CUSTOMER:
        check(f'## {name}\n' in customer, f'Customer missing {name}')
    check(len(specs['charts']) == 10, 'Expected ten chart specifications')
    check(len({c['id'] for c in specs['charts']}) == len(specs['charts']), 'Duplicate chart IDs')
    charts_doc = read_spec('CINENEST-CHARTS-AND-METRICS.md')
    for chart in specs['charts']:
        for field in ['id', 'name', 'type', 'series', 'formula', 'contract', 'axes', 'tooltip', 'filters', 'legend', 'responsive', 'metricId', 'population', 'units', 'timeBasis', 'authorizedDataSource', 'freshness', 'accessibility', 'emptyErrorStates', 'decisionStatus', 'implementationStatus', 'sourceIds']:
            check(bool(chart.get(field)), f'Chart {chart.get("id")} missing {field}')
        check(f'## {chart["name"]}\n' in charts_doc, f'Chart docs missing {chart["id"]}')
        for series in chart['series']:
            check(series['colorToken'] in colors, f'Unresolved chart color {series["colorToken"]}')
    for field in ['gridColorToken', 'axisColorToken']:
        check(specs['shared'][field] in colors, f'Unresolved {field}')
    check(list(tokens['breakpoint'].values()) == sorted(tokens['breakpoint'].values()), 'Breakpoints not ascending')
    pairs = [('text.primary', 'surface', 4.5), ('text.secondary', 'surface', 4.5),
             ('sidebar.text', 'sidebar.background', 4.5), ('sidebar.muted', 'sidebar.background', 4.5),
             ('cinematic.text', 'cinematic.background', 4.5), ('surface', 'brand.primary', 4.5),
             ('surface', 'brand.gradientEnd', 4.5), ('surface', 'brand.primaryHover', 4.5),
             ('border.control', 'surface', 3), ('focus', 'surface', 3),
             ('brand.gold', 'cinematic.background', 3)]
    pairs += [(f'{s}.foreground', f'{s}.background', 4.5) for s in ['success', 'warning', 'danger', 'info', 'neutral']]
    for fg, bg, threshold in pairs:
        a, b = sorted([luminance(colors[fg]), luminance(colors[bg])])
        ratio = (b + 0.05) / (a + 0.05)
        print(f'Contrast {fg}/{bg}: {ratio:.2f}:1 (target {threshold})')
        check(ratio >= threshold, f'Contrast below target: {fg}/{bg} {ratio:.2f}')
    validate_expansion(check, tokens, specs)
    migrated_count = validate_consolidation(check)
    if errors:
        print('\n'.join('FAIL '+message for message in errors))
        return 1
    print(f'PASS consolidation: {migrated_count} source destinations/retained substantive content; exact approved removals, immutable historical originals, complete registry payloads and anchors preserved.')
    print('PASS: required files; JSON; palette/CSS; links; 3 image hashes; 11 admin modules/all fields; 12 customer flows; 10 chart definitions/token references; catalog/rules/statuses/package and bank requirements/traceability/manifest; breakpoints; 16 selected contrast pairs.')
    print('Limits: structural checks only; no rendered UI, backend validation, missing-source reconciliation or Git commit verified.')
    return 0

def validate_expansion(check, tokens, specs):
    """Check source/status and canonical rule consistency, not implementation correctness."""
    new_required = [
        'product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md',
        'product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md',
        'architecture/CINENEST-TECHNOLOGY-STACK-AND-DECISIONS.md',
        'architecture/CINENEST-SYSTEM-ARCHITECTURE.md',
        'architecture/CINENEST-DATA-AND-API-CONTRACTS.md',
        'architecture/CINENEST-IOT-ACCESS-AND-FAILURE-MODES.md',
        'security/CINENEST-SECURITY-PRIVACY-COMPLIANCE.md',
        'delivery/CINENEST-IMPLEMENTATION-PHASES-AND-ACCEPTANCE.md',
        'decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md',
        'CINENEST-INTERACTIONS-RESPONSIVE-A11Y.md', 'CINENEST-DESIGN-DECISION-ADR-002.md',
        'product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md', 'product/cinenest-bank-payment-lifecycles.json',
        'CINENEST-DESIGN-DECISION-ADR-004.md', 'delivery/CINENEST-BANK-PAYMENT-VALIDATION.md',
        'sources/CN-KB-BANK-PAYMENT-001-request.txt',
        'product/CINENEST-PACKAGE-MANAGEMENT.md', 'CINENEST-DESIGN-DECISION-ADR-003.md',
        'delivery/CINENEST-PACKAGE-CONFIGURATION-VALIDATION.md',
        'sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt',
        'CHANGELOG.md', 'manifest.json', 'product/cinenest-feature-catalog.json',
        'product/cinenest-draft-rules.json', 'decisions/cinenest-open-decisions.json',
        'decisions/cinenest-palette-comparison.json', 'sources/cinenest-source-register.json',
        'sources/CN-KB-FEATURES-TECH-001-request.txt',
    ]
    for name in new_required:
        check(spec_path(name).is_file(), f'Missing canonical {name}')
    parsed = {}
    for path in ROOT.rglob('*.json'):
        try:
            parsed[path.relative_to(ROOT).as_posix()] = json.loads(path.read_text())
        except ValueError as exc:
            check(False, f'Invalid JSON {path}: {exc}')
    if not all(key in parsed for key in ['manifest.json', 'product/cinenest-feature-catalog.json',
                                        'product/cinenest-draft-rules.json', 'decisions/cinenest-open-decisions.json',
                                        'sources/cinenest-source-register.json']):
        return
    decisions = {'APPROVED', 'DOCUMENTED_DRAFT', 'RECOMMENDED', 'OPEN_DECISION'}
    implementations = {'NOT_VERIFIED', 'PARTIAL_WITH_EVIDENCE', 'VERIFIED_WITH_EVIDENCE'}
    sources = parsed['sources/cinenest-source-register.json']['sources']
    source_ids = {x['id'] for x in sources}
    check(len(source_ids) == len(sources), 'Duplicate source IDs')
    for source in sources:
        if source.get('path'):
            path = ROOT / source['path']
            check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == source['sha256'], f'Source mismatch {source["id"]}')
        else:
            check(source['availability'] == 'MISSING' or (source.get('id') in {'S12', 'S13', 'S14', 'S15', 'S16', 'S17'} and source['availability'] == 'CONVERSATION' and source.get('kind') == 'user-conversation-authorization' and bool(source.get('scope'))), f'Unverified source location {source["id"]}')
    features = parsed['product/cinenest-feature-catalog.json']['features']
    ids = [f['id'] for f in features]
    check(len(ids) == len(set(ids)), 'Duplicate feature IDs')
    check(set(ids) == {f'CN-C{i:02d}' for i in range(1, 11)} | {f'CN-A{i:02d}' for i in range(1, 14)} | {f'CN-O{i:02d}' for i in range(1, 5)}, 'Incomplete feature inventory')
    feature_doc = read_spec('product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md')
    for f in features:
        for field in ['id', 'name', 'purpose', 'actors', 'workflow', 'rules', 'permissions', 'module',
                      'pages', 'integrations', 'failureStates', 'acceptance', 'sourceIds']:
            check(bool(f.get(field)), f'Feature {f.get("id")} missing {field}')
        check(f['decisionStatus'] in decisions and f['implementationStatus'] in implementations, f'Invalid feature status {f["id"]}')
        check(f['implementationStatus'] == 'NOT_VERIFIED', f'Unexpected implementation claim {f["id"]}')
        check(set(f['sourceIds']) <= source_ids, f'Unknown source {f["id"]}')
        check(f'| {f["id"]} |' in feature_doc, f'Missing catalog projection {f["id"]}')
    opens = parsed['decisions/cinenest-open-decisions.json']['decisions']
    check(len({d['id'] for d in opens}) == len(opens), 'Duplicate decision IDs')
    for d in opens:
        for field in ['conflict', 'source', 'impact', 'options', 'owner', 'evidenceRequired', 'launchBlocker']:
            check(bool(d.get(field)), f'Decision {d.get("id")} missing {field}')
        check(d['decisionStatus'] in decisions and d['implementationStatus'] in implementations, f'Invalid decision status {d["id"]}')
    rules = parsed['product/cinenest-draft-rules.json']
    expected = {'currency': 'LKR', 'timezone': 'Asia/Colombo', 'minimumSessionHours': 3,
                'extensionIncrementHours': 1, 'baseGuests': 4, 'maximumGuests': 5,
                'basePackageLkr': 2500, 'fifthGuestLkr': 800, 'additionalHourLkr': 1000,
                'ps4PerSessionLkr': 1000, 'onlineConvenienceFeeLkr': 100, 'cleaningBufferMinutes': 15,
                'minimumGuestAgeYears': 18, 'pinValidity': '[session_start,session_end)'}
    for key, value in expected.items():
        check(rules.get(key) == value, f'Draft rule mismatch {key}')
    check(rules['checkoutHold']['selectedMinutes'] is None and rules['checkoutHold']['decisionStatus'] == 'OPEN_DECISION', 'Hold duration silently selected')
    check(rules['operatingWindow'] == {'start': '09:00', 'end': '24:00'}, 'Operating window mismatch')
    check(rules['examples']['baseOnlineLkr'] == rules['basePackageLkr'] + rules['onlineConvenienceFeeLkr'], 'Base total mismatch')
    check(rules['examples']['baseOnlineWithPs4Lkr'] == rules['examples']['baseOnlineLkr'] + rules['ps4PerSessionLkr'], 'PS4 total mismatch')
    rules_doc = read_spec('product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md')
    for heading in ['Booking', 'Payment (attempt state and refund ledger separate)', 'Verification',
                    'Access provisioning', 'Session', 'Room readiness', 'Device command']:
        check(f'### {heading}' in rules_doc, f'Lifecycle missing {heading}')
    for formula in ['session_end = session_start + purchased_duration', 'blocked_until = session_end + cleaning_buffer']:
        check(formula in rules_doc, f'Missing time formula {formula}')
    for literal in ['LKR 2,600', 'LKR 3,600', '[session_start,session_end)', '[session_start,blocked_until)']:
        check(literal in rules_doc, f'Missing canonical rule {literal}')
    customer = read_spec('CINENEST-CUSTOMER-BOOKING-PAGES.md')
    check('Draft PIN entry validity is [scheduled session start, session end)' in customer, 'Customer PIN rule not reconciled')
    check(len({c['metricId'] for c in specs['charts']}) == len(specs['charts']), 'Duplicate metric IDs')
    for c in specs['charts']:
        check(c['decisionStatus'] in decisions and c['implementationStatus'] == 'NOT_VERIFIED', f'Chart status {c["id"]}')
        check(set(c['sourceIds']) <= source_ids, f'Chart sources {c["id"]}')
    manifest = parsed['manifest.json']
    check(manifest['kbVersion'] == '1.7.0' and manifest['baselineKbVersion'] == '1.6.1', 'Consolidation KB version/baseline mismatch')
    check(manifest['chartSpecVersion'] == specs['version'] == '1.2.0', 'Chart version mismatch')
    check(manifest['designTokenVersion'] == tokens['version'] == '1.0.0', 'Design-token version mismatch')
    actual = {p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.relative_to(ROOT).as_posix() not in UNMANAGED_WORKSPACE_ASSETS}
    entries = manifest['files']
    check(len({x['path'] for x in entries}) == len(entries), 'Duplicate manifest entries')
    check({x['path'] for x in entries} == actual, 'Manifest inventory mismatch')
    for entry in entries:
        path = ROOT / entry['path']
        check(path.is_file(), f'Missing manifest file {entry["path"]}')
        if entry.get('sha256') and path.is_file():
            check(hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], f'Manifest checksum mismatch {entry["path"]}')
    validate_package_spec(check, features, rules, source_ids)
    validate_bank_spec(check, features, specs, source_ids, parsed, manifest)
    for filename, expected_hash in manifest['preservedAssets'].items():
        check(hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == expected_hash, f'Preserved asset changed {filename}')


def validate_package_spec(check, features, rules, source_ids):
    """Validate package documentation coverage; no service or hardware test claim."""
    path = spec_path('product/CINENEST-PACKAGE-MANAGEMENT.md')
    if not path.is_file():
        check(False, 'Missing canonical package specification')
        return
    text = read_spec('product/CINENEST-PACKAGE-MANAGEMENT.md')
    feature = next((f for f in features if f['id'] == 'CN-A12'), None)
    check(feature is not None, 'Missing package feature CN-A12')
    if feature:
        check(feature.get('requirementId') == 'PR-PKG-001', 'Package requirement ID mismatch')
        check(feature['decisionStatus'] == 'APPROVED' and feature['implementationStatus'] == 'NOT_VERIFIED', 'Core package requirement status mismatch')
        check(feature.get('supportingDesignDecisionStatus') == 'RECOMMENDED', 'Supporting design silently approved')
        check(feature['sourceIds'] == ['S8'] and 'S8' in source_ids, 'Missing package requirement provenance')
        check(feature.get('canonicalSpecification') == 'PROJECT.md#package-management', 'Conflicting package owner document')
    check('without editing application code or requiring a deployment for each configuration change' in text, 'Core no-release capability missing')
    for literal in ['PR-PKG-001', 'CN-A12', 'positive integer minutes', 'extra_minutes = selected_minutes - package_version.included_minutes',
                    'extra_minutes % extension_increment_minutes == 0', 'effective_min = max(',
                    'accepted quote', 'capturedAt', 'two-hour package', 'physical capacity', 'no binary floating point']:
        check(literal.lower() in text.lower(), f'Missing package constraint {literal}')
    for i in range(1, 14):
        check(text.count(f'| PK-AC{i:02d} |') == 1, f'Missing/duplicate package acceptance PK-AC{i:02d}')
    for heading in ['Actors and permissions', 'Fields and joint validation', 'Ownership and initial defaults',
                    'Duration and server quotation model', 'Proposed administrative lifecycle',
                    'Effective versions, scopes and concurrent publishing', 'Existing purchases and active checkout protections',
                    'Proposed architecture/API and audit', 'Open decisions and provenance']:
        check(f'## {heading}' in text, f'Missing package section {heading}')
    check(rules.get('initialDefaultsOnly') is True, 'Draft initial values presented as immutable runtime rules')
    check(rules.get('includedSessionMinutes') == 180, 'Initial included duration mismatch')
    check(rules.get('extensionIncrementMinutes') == rules['extensionIncrementHours'] * 60 == 60, 'Initial extension units inconsistent')
    check(set(rules.get('ownership', {})) == {'package', 'operations', 'sharedPricing'}, 'Default ownership incomplete')
    for name in ['CINENEST-ADMIN-PAGES.md', 'CINENEST-CUSTOMER-BOOKING-PAGES.md',
                 'product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md',
                 'architecture/CINENEST-DATA-AND-API-CONTRACTS.md', 'architecture/CINENEST-SYSTEM-ARCHITECTURE.md',
                 'delivery/CINENEST-IMPLEMENTATION-PHASES-AND-ACCEPTANCE.md',
                 'decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md']:
        body = read_spec(name)
        check('PROJECT.md#package-management' in body, f'Missing canonical package link {name}')
        if name == 'decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md':
            check('PR-PKG-001' in body and 'S8' in body and 'RECOMMENDED' in body, 'Package approval provenance missing in decision summary')
        else:
            check(body.count('## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001') == 1, f'Duplicate/missing task section {name}')


def validate_bank_spec(check, features, specs, source_ids, parsed, manifest):
    """Structural bank design coverage, not bank/payment/device behavior verification."""
    path = spec_path('product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md')
    if not path.is_file():
        check(False, 'Missing canonical bank spec')
        return
    text = read_spec('product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md')
    check('S9' in source_ids, 'Missing bank source')
    for feature_id in ['CN-C10', 'CN-A13']:
        f = next((f for f in features if f['id'] == feature_id), None)
        check(f is not None, f'Missing bank feature {feature_id}')
        if f:
            check(f.get('requirementId') == 'PR-BANK-001', f'Bank requirement ID {feature_id}')
            check(f['decisionStatus'] == 'APPROVED' and f['implementationStatus'] == 'NOT_VERIFIED', f'Bank status {feature_id}')
            check(f.get('confirmationStatus') == 'USER-CONFIRMED' and f.get('supportingDesignDecisionStatus') == 'RECOMMENDED', f'Bank provenance/options {feature_id}')
            check(f['sourceIds'] == ['S9'] and f['canonicalSpecification'] == 'PROJECT.md#bank-payments', f'Bank source/spec {feature_id}')
    for phrase in ['RECEIPT UPLOADED is not PAYMENT VERIFIED', 'PAYMENT VERIFIED is not automatically BOOKING CONFIRMED or ACCESS READY',
                   'bank_credited_at', 'verified_at', 'allocated_at', 'Funds Received / Booking Resolution Required',
                   '24-hour lead time is only', 'no numeric deadline approved', 'not a globally unique ledger key',
                   'LKR 100 nor waived', 'same database-enforced', 'No PIN release while payment unverified']:
        check(phrase.lower() in text.lower(), f'Missing bank rule {phrase}')
    for i in range(1, 14):
        check(text.count(f'| BP-AC{i:02d} |') == 1, f'Missing/duplicate bank acceptance BP-AC{i:02d}')
    lifecycle = parsed.get('product/cinenest-bank-payment-lifecycles.json', {})
    check(lifecycle.get('decisionStatus') == 'RECOMMENDED' and lifecycle.get('implementationStatus') == 'NOT_VERIFIED', 'Bank lifecycle status')
    machines = lifecycle.get('machines', {})
    check(set(machines) == {'receipt_review', 'verified_funds_allocation', 'payment_request_satisfaction',
                            'reservation_booking', 'access_provisioning', 'bank_refund'}, 'Incomplete bank state machines')
    for name, rows in machines.items():
        check(f'### {name}' in text and bool(rows), f'Missing bank state documentation {name}')
        for row in rows:
            for field in ['transition', 'actorPermissions', 'evidence', 'guards', 'sideEffects', 'timeout', 'retry', 'audit']:
                check(bool(row.get(field)), f'Incomplete transition {name}/{field}')
            check(row['transition'] in text, f'Lifecycle JSON/docs mismatch {name}')
    bank_metrics = specs.get('bankMetrics', [])
    check({m['id'] for m in bank_metrics} == {f'BN-M{i:02d}' for i in range(1, 9)}, 'Bank metric IDs incomplete/duplicate')
    check(len(bank_metrics) == 8, 'Bank metric count')
    charts_doc = read_spec('CINENEST-CHARTS-AND-METRICS.md')
    for metric in bank_metrics:
        for field in ['id', 'key', 'population', 'formula', 'units', 'timeBasis', 'colorToken']:
            check(bool(metric.get(field)), f'Bank metric {metric.get("id")} missing {field}')
        check(metric['id'] in charts_doc and metric['key'] in charts_doc, f'Bank metric documentation {metric["id"]}')
        colors = parsed['tokens/cinenest-design-tokens.json']['color']
        check(metric['colorToken'] in colors, f'Unknown bank metric color {metric["id"]}')
    for chart_id, expected_colors in manifest.get('preservedChartSeriesColors', {}).items():
        chart = next((c for c in specs['charts'] if c['id'] == chart_id), None)
        check(chart is not None and [s['colorToken'] for s in chart['series']] == expected_colors, f'Original chart colors changed {chart_id}')
    old_package = dict(next(f for f in features if f['id'] == 'CN-A12'))
    check(old_package['canonicalSpecification'] == 'PROJECT.md#package-management', 'Package canonical destination mismatch')
    old_package['canonicalSpecification'] = 'product/CINENEST-PACKAGE-MANAGEMENT.md'
    digest = hashlib.sha256(json.dumps(old_package, sort_keys=True).encode()).hexdigest()
    check(digest == manifest.get('preservedPackageRequirementHash'), 'Existing approved package feature altered')
    for name in ['product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md', 'product/CINENEST-PACKAGE-MANAGEMENT.md',
                 'CINENEST-ADMIN-PAGES.md', 'CINENEST-CUSTOMER-BOOKING-PAGES.md', 'CINENEST-UX-ACCEPTANCE-CHECKLIST.md',
                 'architecture/CINENEST-TECHNOLOGY-STACK-AND-DECISIONS.md', 'architecture/CINENEST-SYSTEM-ARCHITECTURE.md',
                 'architecture/CINENEST-DATA-AND-API-CONTRACTS.md', 'architecture/CINENEST-IOT-ACCESS-AND-FAILURE-MODES.md',
                 'security/CINENEST-SECURITY-PRIVACY-COMPLIANCE.md', 'delivery/CINENEST-IMPLEMENTATION-PHASES-AND-ACCEPTANCE.md',
                 'decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md']:
        body = read_spec(name)
        check('PROJECT.md#bank-payments' in body, f'Missing bank canonical link {name}')
        if name == 'decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md':
            check('PR-BANK-001' in body and 'S9' in body and 'OPEN_DECISION' in body, 'Bank approval provenance/policy gates missing in decision summary')
        else:
            check(body.count('## Bank payment — CN-KB-BANK-PAYMENT-001') == 1, f'Duplicate/missing bank task section {name}')
    customer = read_spec('CINENEST-CUSTOMER-BOOKING-PAGES.md')
    check('not an online bank IPG or fake webhook' in customer, 'Customer gateway/manual distinction missing')


if __name__ == '__main__':
    raise SystemExit(main())
