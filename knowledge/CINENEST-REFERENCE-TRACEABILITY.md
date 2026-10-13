# CineNest Reference Traceability

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Recorded: 2026-10-13. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Inspected repository inventory
Initial workspace: knowledge/ containing exactly three PNG images; empty read-only .git/.agents/.codex/.aws directories. No existing knowledge-base prose, UI documentation, source code, frontend architecture, package manifest, tokens, theme configuration, chart components, development standards or applicable AGENTS.md were found. Follow-up: the user supplied the canonical GitHub URL, which was successfully cloned into repository/ and found to be empty, with no commits or files. Thus no remote architecture or standards are available either. No existing source file was modified. Proposed architecture-neutral specification assets are under knowledge/, not application styles or API code. The requested knowledge pack, architecture, requirements and conditions remain unavailable after user accepted proceeding (“ok”). Do not interpret this absence as approval of a framework, provider or policy.

## Preserved references
The third requested name includes “(1)”, but the only customer image available is named without it. Preserve the available original without renaming and record the mapping; it was visually inspected and is the customer composite described. Exact-file equivalence to an unavailable “(1)” image is not verified.

| Reference | Original file | Evidence | SHA-256 |
|---|---|---|---|
| R1 | [CineNest Cinema Admin Dashboard Mockup-1.png](CineNest%20Cinema%20Admin%20Dashboard%20Mockup-1.png) | Operational dashboard, dark sidebar, KPI tiles, revenue+bookings, room live-control, customers/analytics. | `52747e612b9b707ac573051d0004475d77ec4aae2dfae61abc7ce0256e38b70f` |
| R2 | [CineNest Cinema Dashboard Showcase-2.png](CineNest%20Cinema%20Dashboard%20Showcase-2.png) | Cinematic banner, room/device cards, health list, detailed room controls and booking/customer tables. | `27b8ec2a308f905b39fcbf2f5d6c24db241628475f6c29e9fc6060da5b528ca7` |
| R3 | [CineNest Private Cinema Booking UI.png](CineNest%20Private%20Cinema%20Booking%20UI.png) | Dark customer hero, gold identity, light wizard, room selection, verification/payment/confirmation and account cards. | `a8669e2d3d21df7eda45aba2c95608079f02f280ad0d106de779378a681d8852` |

## Decision-to-source mapping
| Decision / pages | Evidence | Resolution and specification |
|---|---|---|
| Unified admin shell, Dashboard | R1 dashboard/topbar; R2 hero/health | Operational-first, optional banner; [admin](CINENEST-ADMIN-PAGES.md), [ADR](CINENEST-DESIGN-DECISION-ADR-001.md) |
| Booking Management | R1/R2 booking tables | Shared filters, state/payment separation, scoped actions; admin spec |
| Room Management and Live Control | R1 room grid/control; R2 device cards/control | Card/table toggle, telemetry freshness and acknowledged commands; admin spec |
| Customer Management | R1/R2 user tables | Canonical customer naming, restricted verification; admin spec |
| Payments/Refunds, IoT, Promotions, Settings | R1/R2 sidebar references; limited full-page evidence | Full-page proposals derived from named modules, not unseen visual approval; admin spec |
| Analytics/Reports | R1 analytics and both dashboards | Shared metrics and chart tokens; [charts](CINENEST-CHARTS-AND-METRICS.md) |
| Audit Logs | User required module; no full mockup | Proposed permissioned read-only page; admin spec |
| Landing, room selection, verification, summary, payment, confirmation, account | R3 composite | Cinematic shell/light panels; [customer](CINENEST-CUSTOMER-BOOKING-PAGES.md) |
| Google auth, Date/Time, PIN, history, loyalty | R3 controls/step labels plus user scope | Full interactions are proposed; no provider/policy claims; customer spec |
| Gold/violet identity, dark sidebar, white cards | R1/R2/R3 | Normalized proposed palette; [tokens](tokens/cinenest-design-tokens.json) and [design system](CINENEST-DESIGN-SYSTEM.md) |
| Responsive, accessibility, capabilities, backend contracts | User scope and architectural guidance; not screenshot evidence | Proposed shared baseline; acceptance checklist, charts and page specs |

## Conflict and missing-source register
| ID | Conflict or uncertainty | Resolution now | Owner / implementation gate |
|---|---|---|---|
| C01 | Exact proposed knowledge-pack tokens unavailable | Mark palette/font proposed; no invented supplied-token claim | Design owner supplies pack and approves/reconciles values |
| C02 | Existing architecture/roles/endpoints absent | Capability and data contracts are proposals; framework neutral | Architect maps actual roles/services before implementation |
| C03 | Requirements/conditions/safety documents absent | Do not claim age, upload limits, pricing, cancellation or access policy approved | Product/security owners attach source versions |
| C04 | Booking step numbering inconsistent in R3 | Stable five-step sequence; summary within Payment | Resolved visual inconsistency under hybrid decision |
| C05 | Screenshot price/extras totals inconsistent and static metrics | Server itemized quote/aggregates only; sample numbers never operational | Product/finance validate quote and ledger semantics |
| C06 | Occupancy donut vs time utilization and maintenance states | Separate snapshot rate and interval utilization; cleaning/unknown explicit | Backend owner confirms state map and sellable intervals |
| C07 | R1 binary lights/lock vs R2 dimmer/device detail | Capability-driven controls with requested vs observed state | IoT/safety owner confirms bounds, occupied-room operations and safe egress |
| C08 | R3 full identity card/age and exposed sample PIN | Restrict previews, mask data, intentional PIN reveal; no sample credentials | Privacy/security approve retention, verification and PIN lifecycle |
| C09 | 18+, 5MB, 15-minute activation, providers and amenities shown | Illustrations only; no policy inference | Business/security approve exact rules/integrations |
| C10 | Hero competes with operational dashboard | Optional secondary hero; status first | Resolved visual hierarchy |
| C11 | Screenshot text/control contrast and dense desktop layout | Accessible normalized tokens, reflow/table alternatives | Future UI requires rendered keyboard/screen-reader/contrast QA |
| C12 | Customer requested filename differs | Keep available original and hash; exact missing alias unverified | User can supply alternate source if materially different |
| C13 | Initial workspace Git placeholder unusable; supplied remote is empty | Separate clone of https://github.com/Hasitha3513/Theater-Project.git created; knowledge assets prepared for initial main commit | Existing local Git author configuration reused; user explicitly authorized commit and push; no remote governance yet |
| C14 | Loyalty/referral earning, eligibility and refund rules absent | Render only backend-supported feature/rules; no invented rate | Product/finance approve ledger and reversals |
| C15 | Branch/currency/timezone support unknown | Location scopes conditional; Asia/Colombo/LKR proposed presentation | Architect/business confirms venue configuration |

No actual contradiction with an existing architecture document can be assessed because none is present. Safety/privacy guidance here is proposed design guardrails, not a replacement for project-specific rules. Resolve open rows with dated source references; preserve the original evidence and update ADR/checklist together.
