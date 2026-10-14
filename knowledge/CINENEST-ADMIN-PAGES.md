# CineNest Admin Page Specifications

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Original record: 2026-10-13. Updated: 2026-10-14, CN-KB-FEATURES-TECH-001. Decision: APPROVED for hybrid direction; DOCUMENTED_DRAFT for detailed behavior. Implementation: NOT_VERIFIED. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Shared admin contract
Use the shared shell, tokens, components, accessibility and responsive rules in [design system](CINENEST-DESIGN-SYSTEM.md). [Chart definitions](CINENEST-CHARTS-AND-METRICS.md) are authoritative for metric meaning. Capability names below are proposed contract labels, not existing roles. Server authorization is mandatory on each resource/action; hiding a button is insufficient. Resolve actual role mappings from the missing architecture. Never automatically grant Administrator every sensitive permission.

Lists use backend pagination, stable sort plus ID tie-breaker, abortable filters, loading/empty/error states and asOf timestamps. Detail edits use entity version/conflict handling. Monetary values are minor-unit integers plus ISO currency. Commands, booking changes and refunds use server-supported idempotency keys. Each mutation surfaces pending/success/failure, audit reference and safe retry behavior. Modals restore focus; tables preserve active filters. Confirm consequential actions and show reason/impact; routine viewing needs no confirmation. Proposed lifecycle labels must map to actual domain states before implementation.

## Dashboard

| Requirement | Specification |
|---|---|
| Page layout | Four KPI cards; revenue/bookings and occupancy plots; live rooms, recent bookings/activity, system health. Optional cinematic banner below operational content. |
| Components | KPI card, room thumbnail, activity feed, health list, date range. |
| Tables | Recent bookings: ID, customer display name, room, local start/end, booking status, payment status; room tiles link to controls. |
| Filters | Location if supported, date range, granularity; click chart segments applies explicit filter. |
| Buttons | View all bookings, View rooms, Refresh; global search uses permitted entities. |
| Forms | No operational edit form; date filter validation only. |
| Charts | Revenue & Bookings; Room Occupancy; financial KPI cards; health indicators. |
| Status indicators | Separate booking/payment/room/telemetry states; observedAt and stale labels. |
| Permissions | dashboard.read plus resource scopes; finance KPIs require payments.read; control links require room.control. |
| Responsive behavior | KPI 1/2/4 columns; charts and live rooms stack below lg. |
| Error and empty states | Distinguish empty range, no configured rooms, partial-service failure and denied financial widgets; retry each failed widget. |

## Booking Management

| Requirement | Specification |
|---|---|
| Page layout | Page title/primary action, booking-state tabs, filter toolbar, results table and details drawer. |
| Components | State tabs, date picker, room selector, server paging, booking timeline. |
| Tables | ID, customer, room, start/end, guests, amount/currency, booking status, payment status, actions; no full ID/PIN in rows. |
| Filters | Date, room, booking/payment state, permitted customer/ID search; sort start time or created time. |
| Buttons | New booking, View, Modify, Cancel, scoped Export; destructive confirmation states refund consequence. |
| Forms | Room, slot, guest count, customer reference and quote; edit checks latest version/availability. |
| Charts | Optional booking counts by state; link to Booking Statistics. |
| Status indicators | Proposed states held/pending/confirmed/ongoing/completed/cancelled/expired; map actual backend states explicitly. |
| Permissions | booking.read/create/update/cancel/export independently; enforce authorized venue/resource scope server-side; tenant architecture is undecided. |
| Responsive behavior | Table scroll or compact cards at <md; detail drawer becomes full-screen. |
| Error and empty states | Conflict/expired hold requires reselect and re-quote; no silent success; empty tabs distinguish zero counts from unavailable counts. |

## Cinema Room Management

| Requirement | Specification |
|---|---|
| Page layout | Room grid/table switch, room filters, room detail and configuration drawer. |
| Components | Photo cards, amenities, capacity, state badges, device summaries, availability calendar. |
| Tables | Room ID/name, capacity, amenities, operating state, connectivity, current/next booking, actions. |
| Filters | Location, operating state, connectivity, name; selected view persists locally. |
| Buttons | Add room, View details, Edit, Control room, Archive only if business policy permits. |
| Forms | Name, capacity, photos, amenities, operating schedule, approved rate reference and device associations; unique identifiers validated. |
| Charts | Room Utilization in details; no fake occupancy on room cards. |
| Status indicators | Available/occupied/cleaning/maintenance/unknown; device health separate from bookability. |
| Permissions | room.read/write; room.control separate; room pricing and device associations require their own capability. |
| Responsive behavior | 1/2/3 room cards; table mode scrolls; forms single-column on mobile. |
| Error and empty states | Prevent unsafe archive/schedule changes affecting bookings; unavailable image fallback; no rooms gives authorized setup action. |

## Room Live Control

| Requirement | Specification |
|---|---|
| Page layout | Room header and session timer, room photo/info, command panels, device status and command history. |
| Components | Lock panel; AC setpoint; light dimmer; projector power; last-seen time; pending/acknowledged command feedback. |
| Tables | Command ID, device, actor, requested action, requestedAt, acknowledgment/result; device ID/type/state/lastSeen. |
| Filters | Room and device type/state; command time range; current room stays visible. |
| Buttons | Lock/Unlock, AC adjustment, Lights, Projector, Test alert, Restart where supported; confirm high-impact actions with reason. |
| Forms | Bounded AC temperature and light percentage from server capability metadata; reason for exceptional override. |
| Charts | IoT health/status timeline only; telemetry values are actual observed values. |
| Status indicators | Requested/pending/acknowledged/failed/expired/unknown; commanded state never displayed as observed state before telemetry confirms. |
| Permissions | room.control and device.command scoped by room; exceptional override requires separate authorization and audit. |
| Responsive behavior | Stack room context and command panels; minimum target 44px; critical session context visible while controlling. |
| Error and empty states | Stale/offline disables unsupported commands; timeout permits status lookup before retry; emergency egress must remain independent of UI/network. Never infer occupancy or safe-to-lock from motion alone. |

## Customer Management

| Requirement | Specification |
|---|---|
| Page layout | Customer tabs/filter row, directory table, profile and restricted verification panel. |
| Components | Profile, booking history, verification status, account state, consent/preferences when supported. |
| Tables | Opaque ID, name, masked contact, joinedAt, verification state, booking count, account state, actions. |
| Filters | Verification/account state, joined range, permission-scoped contact search; sort latest joined. |
| Buttons | View, Add user only if architecture permits, restricted verification review, Suspend/reactivate, scoped Export. |
| Forms | Profile edits; review decision/reason; suspension reason; never expose raw identity data in list. |
| Charts | Booking statistics within permitted customer scope only. |
| Status indicators | Verification pending/verified/rejected/expired separate from active/suspended account. |
| Permissions | customer.read/write/export; identity.review separate; customer PII and uploads require restricted server scopes. |
| Responsive behavior | Cards for directory on small screens; verification panel full-screen with safe document preview. |
| Error and empty states | Missing identity provider, pending verification, unavailable image, denied PII and service failure each have explicit states; no automatic approval based on screenshot age. |

## Payments and Refunds

| Requirement | Specification |
|---|---|
| Page layout | Financial summary, transaction tabs including proposed Bank Deposit Reviews subsection, filters, ledger and transaction/refund detail. |
| Components | Currency-aware amount, reconciliation status, refund history and reason dialog. |
| Tables | Transaction ID, booking ID, provider reference masked, gross/fee/refund/net amounts, currency, settlement state, timestamps. |
| Filters | Transaction/refund state, provider, currency, date basis, booking reference. |
| Buttons | View, Request refund, Check status, Export; server confirms actual eligibility and amount. |
| Forms | Refund amount/reason within server-computed refundable balance; confirmation names currency and consequence. |
| Charts | Financial KPI Cards; Revenue Trend labelled net collections or separate approved recognition measure; Payment/Add-on Breakdown panels. |
| Status indicators | Gateway pending/authorized/captured/failed/refunded separately from bank under-review/verified-funds/allocated/booking-resolution/refund progress; pending refund does not reduce collections until verified successful. |
| Permissions | payments.read/refund/export independently; no card credentials or provider secrets in UI. |
| Responsive behavior | Ledger scrolls; amount columns retained; refund dialog stacks without hiding balances. |
| Error and empty states | Duplicate request returns existing operation; webhook delay shows pending; charge/refund timeout never offers blind retry; no transactions differs from failed reconciliation. |

## IoT Device Management

| Requirement | Specification |
|---|---|
| Page layout | Fleet health cards, device inventory, device detail/configuration and logs. |
| Components | Connectivity badges, capability list, lastSeen, firmware metadata, command/event history. |
| Tables | Device ID, type, room, connection, firmware, lastSeen, safe diagnostics, actions. |
| Filters | Room, device type, health, firmware; time range for events. |
| Buttons | Register/pair, Assign room, View logs, Test, Restart, Disable only if supported and authorized. |
| Forms | Pairing workflow and room mapping; capability-specific settings; secrets write-only and never echoed. |
| Charts | System and IoT Status Indicators; availability trends only from telemetry. |
| Status indicators | Online/offline/degraded/unknown and freshness threshold from backend policy. |
| Permissions | device.read/manage/command; firmware/security operations separately controlled. |
| Responsive behavior | Fleet cards wrap; logs scroll; settings stack below md. |
| Error and empty states | Device not provisioned, pairing expired, incompatible firmware, stale events and failed commands have actionable safe diagnostics; operations affecting occupied rooms require server safety checks. |

## Analytics and Reports

| Requirement | Specification |
|---|---|
| Page layout | Filter toolbar, KPI row, revenue/slot/utilization plots, supporting tables and export. |
| Components | Date/granularity controls, currency selector, metric definitions, comparison period. |
| Tables | Bucket/date, room, booking counts, sellable/booked minutes, occupancy, financial amounts; accessible chart-equivalent table. |
| Filters | Range, timezone, location/room, currency, date basis, granularity; independent filters explicitly labelled. |
| Buttons | Apply, Reset, Download permitted report, View source records. |
| Forms | Filter form only; bounded ranges enforced by service. |
| Charts | All specifications in charts document; avoid duplicate conflicting definitions. |
| Status indicators | Fresh/stale/partial/unavailable; no-data is not zero unless dataset complete. |
| Permissions | analytics.read; finance data additionally payments.read; reports.export scoped. |
| Responsive behavior | Charts stack below lg; legends wrap; full supporting table remains available. |
| Error and empty states | Sparse ranges, zero denominator, missing FX support and partial data labelled; export failures keep current view. |

## Promotions and Loyalty

| Requirement | Specification |
|---|---|
| Page layout | Campaign tabs, promotion list, reward ledger, campaign detail. |
| Components | Eligibility preview, redemption limits, referral state, scheduled validity window. |
| Tables | Campaign ID/code, scope, discount type/value, validFrom/Until, usage/cap, state; loyalty ledger customer reference, delta, reason, timestamp. |
| Filters | Active/scheduled/expired, room/location, campaign type; ledger date/customer scope. |
| Buttons | Create, Edit, Disable, View redemptions; reward adjustment with restricted permission and reason. |
| Forms | Discount bounds, eligible rooms, validity timezone, stacking and usage limits; reward rules pending source approval. |
| Charts | Redemption counts and attribution only when server contract exists; do not invent ROI. |
| Status indicators | Draft/scheduled/active/expired/disabled; rewards pending/earned/reversed/redeemed as backend-defined. |
| Permissions | promotion.read/write; loyalty.read/adjust; export separate. |
| Responsive behavior | Tables scroll; create form stacks and validation remains beside fields. |
| Error and empty states | Duplicate code, ineligible booking, cap reached, reversal conflict and unavailable loyalty service; no campaigns offers authorized create action. |

## System Settings

| Requirement | Specification |
|---|---|
| Page layout | Section navigation for general, Packages & Pricing (proposed subsection), rooms/policies, integrations, access and notifications. |
| Components | Scoped forms, secret masked fields, effective-date notices, unsaved-change guard. |
| Tables | Configured integrations and permission assignments with server-supported scope; no invented role matrix. |
| Filters | Section and location if supported; search visible settings. |
| Buttons | Save section, Test connection, Revert draft; sensitive changes require review of impact. |
| Forms | Timezone/currency/operating schedules, booking policies, identity settings, integration references; values supplied by backend. |
| Charts | System indicators for integration test outcomes only. |
| Status indicators | Saved/unsaved/test pending/failed; effective configuration version visible. |
| Permissions | settings.read/write; secrets/manage-access capabilities separate; backend prevents privilege escalation. |
| Responsive behavior | Tabs become section selector; long forms one column below md. |
| Error and empty states | Concurrent update prompts reload/diff; never silently overwrite; absent integration differs from offline; failed save preserves safe drafts. |

## Audit Logs

| Requirement | Specification |
|---|---|
| Page layout | Read-only filter toolbar, chronological event table, event detail panel. |
| Components | Actor/resource links with permissions, redacted before/after, correlation IDs. |
| Tables | Event ID, occurredAt, actor ID/display, action, resource, outcome, reason, correlation ID; redacted change summary. |
| Filters | Time, actor, action, resource, result and room/location scope; cursor ordering timestamp+ID. |
| Buttons | View details, authorized Export; no edit/delete action. |
| Forms | Filter form only; retention controlled by backend policy. |
| Charts | Optional event outcome summary derived from same scoped events; never operational booking KPIs. |
| Status indicators | Success/denied/failed; late-arriving events retain event and ingestion timestamps. |
| Permissions | audit.read/export; restrict sensitive diffs independently and record audit access as architecture requires. |
| Responsive behavior | Scrollable table; details full-screen at <md with readable wrapped diffs. |
| Error and empty states | No events, no matching filters, retention boundary, partial ingestion and permission denial separately explained; redact PINs/tokens/ID scans from all events. |

## Canonical feature/rule integration
[Catalog](product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md) CN-A01–CN-A11 and CN-O01–CN-O04 map these pages to modules, permissions, integrations, failures and acceptance. [Lifecycles](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) define separate booking, payment, verification, access, session, readiness and command state machines. Tabs/counters must not mix those states.

Manual bookings, changes, extensions and cancellations use the same authoritative database guard/quote/eligibility checks as customer flow. Approved operator capability is required, not assumed from admin sign-in. Customer session end and blocked-until/cleaning clearance are separate fields. Extension quotes check next booking and end-of-day rules. Configuration/price/refund-policy edits preserve existing purchase and consent snapshots. Room card/table modes use the same scoped dataset and action capabilities.

Dashboard “today's confirmed bookings” and “today's scheduled sessions” are separate cards if both shown. Booking-based room status is not sensor-confirmed physical occupancy. Charts use [current metric specifications](CINENEST-CHARTS-AND-METRICS.md); net collections and recognized revenue cannot share a mislabeled value. Live Control shows paid/confirmed, provisioning and observed device state independently, plus cleaning work and overstay incidents. Emergency/cleaning staff process remains a launch gate, not a timer automation.

## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
Information architecture: **System Settings → Packages & Pricing**, a proposed subsection, not a new verified route or duplicate pricing module. CN-A12/PR-PKG-001 core editable details/duration/prices are APPROVED; the supporting page/workflow below is RECOMMENDED, implementation NOT_VERIFIED. [Package specification](product/CINENEST-PACKAGE-MANAGEMENT.md) owns fields, constraints, exact pricing and lifecycle.

| Requirement | Package subsection specification |
|---|---|
| Page layout/components | Header/search/filter toolbar, versioned package table; details/services/duration/price/applicability/publication/preview/history editor sections |
| Table/filter | Package identity/name, included duration, currency/price, scope, version, availability, sales effective date and optional service window; search name/ID, filter state/venue/room/date, server paging |
| Actions/permissions | View/history; create/duplicate/edit draft with package.edit; preview; Save draft distinct from Publish/Schedule with package.publish; deactivate/archive separately scoped |
| Forms/preview | Persistent labels, positive integer minutes, exact monetary fields/currency, policy/service references and full joint validation. Customer card/quote preview labelled draft, before/after inclusions/duration/price/scope/effective summary |
| Concurrency/errors | Unsaved-change guard; version token and stale-edit reload/diff; field errors and error summary; overlapping publication conflict; preserve safe draft data, no silent overwrite |
| Confirmation/status | Publish confirmation names new version/time/scope and states existing bookings remain unchanged. Draft/scheduled/applicable/inactive/history are distinct; audit result visible |
| Loading/empty/denied | Geometry-matched loading; no packages versus no filter matches; retry on failed load/publish; no edit/publish permission yields read-only allowed fields or scoped denied state |
| Responsive/a11y | Existing hybrid shell/tokens; table scroll or compact cards below md; editor stacks, preview/history below form; minimum 44px actions, accessible dialogs/errors/version labels; no new theme |
| Charts | Existing report definitions unchanged; historical revenue/duration use sold snapshots, not current catalog; no new demand-pricing chart |

Separate operational-policy/room permissions control minimum/hours/cleaning/capacity. Package controls cannot increase safety capacity or extend active PINs. Exact backend capabilities/state names remain proposed.

## Bank payment — CN-KB-BANK-PAYMENT-001
**Payments → Bank Deposit Reviews** is a proposed subsection (no verified route), supporting CN-A13 notified independent review and preserved online transactions. [Canonical specification](product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) defines authority, matches, allocation/confirmation guards and bank instructions; review privilege does not imply account-change/refund/verify permission.

| Requirement | Design specification |
|---|---|
| Queue/layout | Review filters/summary and server-paged table; detail drawer/page with restricted preview, accepted quote, instruction version, submission history, bank-match/evidence, balances, inventory, audit and notice timeline |
| Columns/filter | Booking/customer, venue/room/session, package/version, expected/declared amount, submittedAt, status/queue age, assignee, hold expiry, overdue/exception and notification flags; scoped reference search/date/status/assignee filters |
| Buttons/forms | Verify Funds and Approve; Request More Information; Await Bank Credit; Reject Claim with reason; Resolve Booking/Amount Exception. Verification requires receiving-account/bank-side reference or controlled statement entry, creditedAt/amount/currency/reviewer/notes; conditional versions and pending server results |
| States/errors | Uploaded != verified != sufficiently allocated != confirmed != access-ready; partial/excess/wrong currency, unmatched/expired hold, duplicate allocation/concurrent reviewer, malformed/quarantined preview, denied scope and notification failure explicit; no silent replacement/false success |
| Settings/audit | Separate bank-account management permission and instruction version/history; ordinary reviewer cannot modify destination; independently verified/high-risk controls proposed, thresholds open; evidence/approval/allocation/refund access audited |
| Responsive/a11y | Existing hybrid shell; dense queue labelled scroll/compact cards below md, details/preview stacked with exact amount/currency/expiry visible; 44px controls, keyboard preview/link access, error summary and polite updates |
| Charts/empty | Bank review age/funds/allocation/unmatched/refund/channel metrics from verified ledger only; no pending-review amount in collections. Empty queue vs filtered/no-permission/service error distinguished |

Pending/success/error comes from authoritative operations. Composite approval can show funds verified but booking resolution required. No money/real-bank/device action performed by documentation task.
