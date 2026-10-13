"""Validate CineNest KB consistency. No application implementation or external dependencies."""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parent
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
        check((ROOT / name).is_file(), f'Missing {name}')
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
    ds = (ROOT / 'CINENEST-DESIGN-SYSTEM.md').read_text()
    for key, value in colors.items():
        check(f'| `{key}` | `{value}` |' in ds, f'Palette docs mismatch {key}')
    for path in ROOT.glob('*.md'):
        text = path.read_text()
        for target in re.findall(r'\]\(([^)]+)\)', text):
            if target.startswith(('http:', 'https:', '#')):
                continue
            target = target.split('#')[0].replace('%20', ' ')
            check((path.parent / target).is_file(), f'Broken link {path.name}: {target}')
        for hex_value in re.findall(r'#[0-9A-Fa-f]{6}\b', text):
            check(hex_value.upper() in colors.values(), f'Uncentralized color {path.name}: {hex_value}')
    for name, expected in IMAGES.items():
        path = ROOT / name
        check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected, f'Reference changed/missing {name}')
    admin = (ROOT / 'CINENEST-ADMIN-PAGES.md').read_text()
    for name in ADMIN:
        check(f'## {name}\n' in admin, f'Admin missing {name}')
        section = admin.split(f'## {name}\n', 1)[-1].split('\n## ', 1)[0]
        for field in FIELDS:
            check(f'| {field} |' in section, f'{name} missing {field}')
    customer = (ROOT / 'CINENEST-CUSTOMER-BOOKING-PAGES.md').read_text()
    for name in CUSTOMER:
        check(f'## {name}\n' in customer, f'Customer missing {name}')
    check(len(specs['charts']) == 8, 'Expected eight metric specs')
    check(len({c['id'] for c in specs['charts']}) == 8, 'Duplicate chart IDs')
    charts_doc = (ROOT / 'CINENEST-CHARTS-AND-METRICS.md').read_text()
    for chart in specs['charts']:
        for field in ['id', 'name', 'type', 'series', 'formula', 'contract', 'axes', 'tooltip', 'filters', 'legend', 'responsive']:
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
    if errors:
        print('\n'.join('FAIL '+message for message in errors))
        return 1
    print('PASS: required files; JSON; palette/CSS; links; 3 image hashes; 11 admin modules/all fields; 12 customer flows; 8 chart definitions/token references; breakpoints; 16 selected contrast pairs.')
    print('Limits: structural checks only; no rendered UI, backend validation, missing-source reconciliation or Git commit verified.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
