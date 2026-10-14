# ADR-002: Draft Business Defaults, Metric Clarification and Token Preservation

Task CN-KB-FEATURES-TECH-001 · 2026-10-14. Decision: DOCUMENTED_DRAFT (business and metric specification), OPEN_DECISION (palette migration). Implementation: NOT_VERIFIED. Source: S1 user request. ADR-001 hybrid direction remains APPROVED; its history and numbering are preserved.

## Context and resolution
New notes specify draft rates, capacity, schedule, buffer, admission and PIN window. Previous UI docs left these unresolved and referenced illustrative pre-entry timing. Adopt S1 as documented draft requirements via [canonical lifecycles](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md), without claiming PDF/legal/provider validation. PIN entry window is scheduled session start to end, not 15 minutes before; any source/mockup conflict is retained here. Protected access-link delivery via SMS/email is desired; ordinary events/notifications still exclude raw credentials. Raw PIN delivery remains a separate risk decision.

Old charts called captures minus refunds “recognized revenue.” Correct to net collections by financial event time; recognized revenue is a separate finance-policy-defined measure, unavailable until approved. Donut center now uses the same full scoped population as its slices (occupied / all included status rooms); a denominator limited to bookable rooms must be a separately labelled metric rather than the old ambiguous center. Booking-based status cannot certify physical occupancy. Proposed sellable minutes explicitly exclude approved non-sellable buffers/blackouts and remain finance/operations reviewed.

## Palette conflict (not applied)
The request calls the following source tokens existing; the checked-in JSON differs. Preserve current JSON/CSS bytes because these active approximations are already consistent and no source pack resolves the discrepancy. [Comparison data](decisions/cinenest-palette-comparison.json) is documentation, not an imported theme. OD-11 must be resolved by design owner with contrast/render evidence before migration. Typography, spacing, borders, shadows, shell and images remain unchanged.

| Semantic role | Checked-in active approximation | S1 requested candidate |
|---|---|---|
| `sidebar.background` | `#0B1522` | `#0B1526` |
| `canvas` | `#F4F6FA` | `#F5F7FC` |
| `surface` | `#FFFFFF` | `#FFFFFF` |
| `brand.primary` | `#5135FF` | `#5B4BFF` |
| `brand.primaryHover` | `#4126D9` | `#5142ED` |
| `brand.gradientEnd` | `#754CFF` | `#7254FF` |
| `chart.bookings` | `#175CD3` | `#2878FF` |
| `brand.gold` | `#F4BE78` | `#F4C77B` |
| `text.primary` | `#101828` | `#111827` |
| `text.secondary` | `#475467` | `#64748B` |
| `success.foreground` | `#067647` | `#15803D` |
| `warning.foreground` | `#854A0E` | `#B45309` |
| `danger.foreground` | `#B42318` | `#B91C1C` |

## Consequences and acceptance
Source PDFs remain missing. Final stack/provider/hardware choices remain recommended/open and implementation NOT_VERIFIED. Contract/metric semantics may change before code exists, but updates remain explicit and traceable. [Open decisions](decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md) and [changelog](CHANGELOG.md) record scope. No existing approved behavior is silently replaced; no application/runtime configuration changes are made.
