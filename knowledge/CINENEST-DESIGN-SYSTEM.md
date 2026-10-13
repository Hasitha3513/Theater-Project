# CineNest Design System

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Recorded: 2026-10-13. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Authority and scope
The user's approved direction combines Admin Mockup-1's operational layout and Showcase-2's room cards and device details with the customer reference's cinematic identity. Exact sampled colors and a font cannot be established authoritatively from raster images. The values in [design tokens](tokens/cinenest-design-tokens.json) are proposed normalized values, not recovered knowledge-pack tokens. This document is the shared baseline for future admin and customer interfaces, subject to reconciliation of the missing source documents. Existing approved architectural/business decisions take priority.

See [admin pages](CINENEST-ADMIN-PAGES.md), [customer pages](CINENEST-CUSTOMER-BOOKING-PAGES.md), [charts](CINENEST-CHARTS-AND-METRICS.md), [ADR](CINENEST-DESIGN-DECISION-ADR-001.md), [traceability](CINENEST-REFERENCE-TRACEABILITY.md), and [acceptance checklist](CINENEST-UX-ACCEPTANCE-CHECKLIST.md).

## Brand identity
CineNest is a private, automated cinema experience: welcoming, immersive, precise, and trustworthy. Preserve the gold film-reel identity, dark cinema photography, and violet actions. The references are visual evidence; their composite logos and room photos are not production asset or licensing approval. Use an approved original logo asset when supplied, with meaningful alternative text and consistent clear space (one icon width). No speculative claims about streaming availability, safety, or automation capabilities.

## Palette and usage
All exact values below come from the centralized proposed token file. Reuse semantic variables from [theme CSS](styles/cinenest-theme.css); never repeat literal colors in future components. Chart aliases deliberately share semantic values. Token changes require coordinated documentation, CSS regeneration, chart validation, and contrast review.

| Role | HEX | Usage |
|---|---|---|
| `brand.gold` | `#F4BE78` | See semantic key; use only in its named context. |
| `brand.primary` | `#5135FF` | See semantic key; use only in its named context. |
| `brand.gradientEnd` | `#754CFF` | See semantic key; use only in its named context. |
| `brand.primaryHover` | `#4126D9` | See semantic key; use only in its named context. |
| `sidebar.background` | `#0B1522` | See semantic key; use only in its named context. |
| `sidebar.text` | `#E8EDF5` | See semantic key; use only in its named context. |
| `sidebar.muted` | `#AFBDD0` | See semantic key; use only in its named context. |
| `cinematic.background` | `#080D18` | See semantic key; use only in its named context. |
| `cinematic.surface` | `#131C2E` | See semantic key; use only in its named context. |
| `cinematic.text` | `#F8FAFC` | See semantic key; use only in its named context. |
| `canvas` | `#F4F6FA` | See semantic key; use only in its named context. |
| `surface` | `#FFFFFF` | See semantic key; use only in its named context. |
| `surface.subtle` | `#F8FAFC` | See semantic key; use only in its named context. |
| `text.primary` | `#101828` | See semantic key; use only in its named context. |
| `text.secondary` | `#475467` | See semantic key; use only in its named context. |
| `border` | `#D0D5DD` | See semantic key; use only in its named context. |
| `border.control` | `#667085` | See semantic key; use only in its named context. |
| `focus` | `#5135FF` | See semantic key; use only in its named context. |
| `success.foreground` | `#067647` | See semantic key; use only in its named context. |
| `success.background` | `#ECFDF3` | See semantic key; use only in its named context. |
| `warning.foreground` | `#854A0E` | See semantic key; use only in its named context. |
| `warning.background` | `#FFFAEB` | See semantic key; use only in its named context. |
| `danger.foreground` | `#B42318` | See semantic key; use only in its named context. |
| `danger.background` | `#FEF3F2` | See semantic key; use only in its named context. |
| `info.foreground` | `#175CD3` | See semantic key; use only in its named context. |
| `info.background` | `#EFF8FF` | See semantic key; use only in its named context. |
| `neutral.foreground` | `#344054` | See semantic key; use only in its named context. |
| `neutral.background` | `#F2F4F7` | See semantic key; use only in its named context. |
| `chart.revenue` | `#5135FF` | Analytics series/grid alias. |
| `chart.bookings` | `#175CD3` | Analytics series/grid alias. |
| `chart.available` | `#067647` | Analytics series/grid alias. |
| `chart.occupied` | `#5135FF` | Analytics series/grid alias. |
| `chart.maintenance` | `#B42318` | Analytics series/grid alias. |
| `chart.cleaning` | `#854A0E` | Analytics series/grid alias. |
| `chart.unknown` | `#667085` | Analytics series/grid alias. |
| `chart.grid` | `#E4E7EC` | Analytics series/grid alias. |
| `chart.axis` | `#475467` | Analytics series/grid alias. |

## Typography
Proposed font: Inter with system fallbacks; confirm font licensing and delivery strategy before inclusion. Body 16px/1.5, small 14px/1.5, caption 12px/1.5, H3 20px/1.2, H2 24px/1.2, H1 32px/1.2, hero 48px/1.2. Use rem, retain browser zoom, and reduce hero to H1 at small widths. Weights 400 body, 500 labels, 600 controls/subheadings, 700 page titles/KPIs. One H1 per page; headings reflect document hierarchy. Financial values use tabular numerals; never use small captions for essential terms.

## Surfaces, cards, borders, shadows
Admin: canvas background, white cards, subtle table headers, dark sidebar. Customer: cinematic shell with explicit light booking-panel theme on forms. Image text uses a dark scrim whose final rendered contrast must be measured. Cards use card radius, 1px border, spacing-6 padding on desktop / spacing-4 mobile and card shadow. Overlays use dialog radius and overlay shadow. Input boundaries use border.control, because the decorative border is too light for essential control identification. Never use shadow alone to communicate selection. Elevation hierarchy: canvas, card, sticky header, drawer, modal, toast; dialogs block underlying focus.

## Spacing and grid
Base unit 4px: 0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64px. Token rem values assume a 16px browser default. Desktop 12-column grid with 24px gutters; tablet 8 columns/16px; mobile 4 columns/16px. Content max 1440px; sidebar 240px; topbar 64px; forms max 640px. Page padding 32px desktop, 24px tablet, 16px mobile. Avoid fixed-height cards containing variable text.

## Navigation and icons
Admin shell: persistent dark sidebar, gold logo, global search, notifications, user menu, branch selector only when architecture supports branches. Group modules consistently; active link uses violet background, icon, text and aria-current. Room Live Control is a nested destination of Rooms. Audit Logs is explicit and permission-gated. Customer shell: public header and booking stepper; authenticated account navigation becomes a drawer on mobile. Keep role contexts visibly distinct.
Use one approved outline icon library (not yet selected), 20px navigation / 16px inline / 24px feature icons, consistent stroke and rounded alignment. Icons inherit semantic text colors; icon-only controls require accessible names. Decorative icons are aria-hidden.

## Buttons and interactions
Primary: brand.primary to brand.gradientEnd, white text, primaryHover interaction. Secondary: surface with primary text and border.control; tertiary: text.secondary with visible hover/focus; destructive: danger.foreground with surface text and explicit consequence. One dominant action per section. Minimum target 44×44px, spacing-3 horizontal padding; no action conveyed solely by icon or color. Gradient is directional decoration, never a metric scale. Busy actions show a label and spinner, prevent duplicate submission, and retain width. Disabled controls use explicit unavailable reason where needed, not opacity alone. Focus uses 2px ring + 2px offset; cinematic focus uses gold. No hover-only functionality.

## Forms and controls
Persistent labels, optional/required annotation, examples, inline error tied by aria-describedby, and page error summary focused on failed submission. Use appropriate input modes/autocomplete. Do not use placeholders as labels. Checkbox/radio groups have fieldsets/legends; toggle states are textual and expose aria-checked. Dates and prices are server validated; a UI date picker never overrides availability. Preserve safe form values after failure, never credentials or sensitive uploads. Native semantics preferred. File uploads expose type/size limits supplied by backend; do not copy 5MB from the mockup as policy. Dialogs trap focus, Escape closes safe dialogs, and focus returns to trigger.

## Tables and pagination
Semantic tables with captions and scoped headers; sortable headers use aria-sort. Row selection is explicit, never automatic on page change. Keep identifier, status and primary action visible. Server-side pagination/filter/sort; default proposal 25 rows with 10/25/50 choices, bounded by backend. Cursor paging shows next/previous; display total/pages only when supplied. Filters survive safe navigation and are represented in URL without PII. CSV export obeys server permissions and current scope. Below md: compact cards for short records, horizontal scroll with labelled region for dense financial/audit tables. Do not hide essential values to fit width.

## Status, loading, empty, errors
Success: available/confirmed/online; warning: pending/cleaning/degraded; danger: failed/offline/maintenance; info: upcoming/in progress; neutral: completed/unknown. Occupied room chart color is violet to match references, while booking in-progress badge uses info. Statuses are domain-specific and must not imply equivalence (paid does not mean confirmed; unknown does not mean offline). Every badge has text and optional icon.
Initial load: geometry-matched skeleton with aria-busy; live refresh retains prior data with updated timestamp. Zero is valid data; missing is “Unavailable.” Empty states distinguish no records, no filter matches, no permission, not configured, and network failure. Provide appropriate reset/retry/action. Errors include safe correlation ID, no raw service payloads. Offline telemetry is stale with last observation time. Live announcements are polite and throttled; urgent failures announce once.

## Responsive behavior
Mobile-first: <640px base; sm ≥640; md ≥768; lg ≥1024; xl ≥1280; xxl ≥1536. Breakpoints in JSON are canonical; CSS media queries in future builds must be generated from these numbers (CSS custom properties do not work in media conditions). At <lg, sidebar becomes a labelled drawer, focus-trapped while open. KPI grid 1/2/4 columns at base/sm/lg. Room grid 1/2/3 at base/md/xl; main dashboard 1 column below lg. Charts use full width, minimum 240px plot height, reduced tick density and persistent data-table access. Booking summary stacks below md, becomes two columns above md. Stepper becomes current-step label plus progress at <sm; all five steps remain accessible. Sticky CTAs must not obscure focus, terms, or content; account for safe-area insets. Test 320, 375, 640, 768, 1024, 1280, 1536px, landscape, 200% zoom, and 400% zoom/reflow.

## Accessibility requirements
Target WCAG 2.2 AA (project acceptance target, not a certification). Text contrast ≥4.5:1, large text ≥3:1, essential graphics/controls/focus ≥3:1. Automated token contrast checks are necessary but insufficient: test final gradients, photos, disabled states, overlays, charts, and focus against adjacent colors. Full keyboard operation, visible focus, skip link, landmarks, correct labels, logical reading order and no keyboard traps. Charts offer equivalent tables; status must not depend on hue. Respect reduced motion, avoid flashing/autoplay audio, caption content video, and give room images useful alternative text. Support password-manager/paste where applicable, no cognitive-only authentication test, accessible Google sign-in and verification fallback. Warn before timed holds expire and offer extension only if backend policy allows. PINs and ID data require intentional disclosure and accessible masking controls. Test a screen reader and keyboard in each future critical booking and live-control flow.
