# CineNest Project

CineNest is the Smart Automated Private Cinema and IoT Management Platform. This document owns product scope, business rules, package/booking/payment lifecycles and acceptance. [Architecture](ARCHITECTURE.md), [design](DESIGN.md), [progress](PROGRESS.md).

Approved core: hybrid visuals (ADR-001); PR-PKG-001/CN-A12 admin-editable details, duration and price without per-edit code deployment (S8); PR-BANK-001/CN-C10/CN-A13 bank deposit/receipt/reviewer notification and independent authorized credit verification alongside gateway payment (S9). Supporting rules/designs remain draft or recommended; all product implementation NOT_VERIFIED. No new fee, deadline, role, capacity exception or policy is selected here. Original PDFs/knowledge pack remain missing.

Users include customers/bookers, booking/room operators, identity reviewers, bank reviewers/verifiers/allocators, finance/refund staff, package editors/publishers, auditors and responders. Capability allocation and thresholds remain open; admin login does not grant every sensitive capability. Business rules below own customer obligations; ARCHITECTURE owns technical enforcement; DESIGN specifies presentation and links to these rules.

## Navigation

- [MVP scope](#mvp-scope)
- [Actors and access](#authorization-requirements)
- [Features](#features)
- [Business Rules](#business-rules)
- [Package Management](#package-management)
- [Bank Payments](#bank-payments)

<a id="auth-bounded-scope"></a>
## Approved first account/session scope

Owner approval 2026-10-15 makes CN-AUTH-001 READY for its existing bounded scope using explicitly associated synthetic/test accounts. Minimum own-account reads and grant-matched staff/service reads preserve ownership and authoritative venue/resource scope. No directory or administrator bypass, automatic production registration, email-based account merging or speculative SaaS identity behavior. Database-backed sessions require explicit expiry; deterministic test values are not production policy. Current-session logout invalidates server state. Every protected request evaluates current eligibility and applicable database-backed grants without a positive decision cache; suspension/ineligibility and revoked/expired grants deny subsequent requests. Authentication/audit must not leak tokens, credentials or private evidence.

[ARCHITECTURE](ARCHITECTURE.md#auth-bounded-policy) owns the approved technical contract and deferrals; OD-01's existing record owns approval provenance. Full admission/privacy/financial/device/production policies remain at their owning feature gates. This approval is not product implementation, acceptance of CN-C02/CN-A06/CN-NFR-02 in full, or approval of all future role bundles. No code is implemented in the decision-recording session.

<a id="organization-scope"></a>
## Approved MVP organization scope — OD-12

Owner decision 2026-10-15: **SINGLE-ORGANIZATION WITH VENUE-SCOPED AUTHORIZATION**. CineNest serves one operating business organization with one or more cinema venues. Customers, employees, administrators, finance reviewers, verification personnel and operations staff belong to this CineNest organization; customers are not tenants. Roles/permissions may differ by venue. Protected bookings, receipts and access credentials require ownership and applicable venue/resource authority. Permission and scope must match an applicable grant: finance at Venue A plus operations at Venue B never creates finance at Venue B. A system administrator label cannot bypass financial, evidence-access or independent physical-safety controls.

The MVP has no tenant subscriptions/billing/Super Admin product, holding-company hierarchy, organization signup/self-service provisioning, per-tenant databases/schemas, speculative tenant_id propagation, cross-organization sharing rules or tenant-aware infrastructure without another approved requirement. Venue IDs remain business authorization attributes. Future SaaS tenancy requires a separate evidence-backed architecture/product decision; avoid gratuitous coupling without adding speculative abstractions. [ARCHITECTURE](ARCHITECTURE.md#organization-scope) owns technical boundaries; existing OD-12 registry preserves the original question, current owner approval and NOT_VERIFIED implementation. This decision does not approve exact role bundles, limits, revocation/privacy policies or CN-AUTH-001 implementation.

<a id="authorization-requirements"></a>
## Actors and access requirements

S15 establishes the user-requested hybrid authorization direction, not implementation acceptance. Roles group action permissions; restrictions bind those permissions to the same authorized venue/resource grant, customer ownership, limits and relevant circumstances. Backend denial is the default. [Technical policy](ARCHITECTURE.md#authorization) owns evaluation/enforcement; [interface behavior](DESIGN.md#permission-aware-ui) owns presentation. Exact bundles and exception/independent-review procedures remain PROPOSED or OPEN. These groups are not staffing mandates; one person may hold multiple roles only as approved separation-of-duties rules allow.

| Proposed grouping | Responsibility and restrictions |
|---|---|
| Customer | Permitted own profile/bookings, bank evidence submission, receipts and protected access information; verified ownership plus appropriate permission. Booking ID knowledge is insufficient; no staff/payment approval authority. |
| Venue Administrator | Assigned venues and granted package/room/booking/local operations only; finance approval and identity evidence are not automatic. |
| Finance Reviewer | Scoped bank review/match/independent credit verification, reconciliation and explicitly granted refund actions; approved account/venue/currency/amount limits, no assumed unlimited financial authority. |
| Verification Officer | Authorized verification cases and necessary evidence for legitimate review purpose; dedicated evidence access, minimal disclosure and audited sensitive reads. |
| Operations Staff | Authorized room monitoring/readiness and permitted device commands; no default identity-document, bank approval or refund powers. |
| Platform/System Administrator | Explicit account/role/permission/configuration functions; no implicit financial, privacy or safety bypass and no ordinary self-escalation. |

Customers and staff have separate ownership versus venue-scoped access paths. Package edit and publication remain distinct; multi-venue publication requires authority over its whole intended scope and cannot rewrite purchased terms. Financial grants never replace independent bank-credit evidence, capped/deduped allocations or valid refundable balance. Prohibiting one's own customer-payment approval and additional independent refund approval are recommendations pending OD-28, not new approved policies. Third-party payer matching remains the approved-policy gate OD-27/28. General customer management cannot disclose identity documents; dashboard visibility cannot authorize a room command or role/bank-account assignment. Physical egress remains independent of authentication, payment and session validity.

Existing **CN-NFR-02** owns the additional 18 planned scenarios in [requirements.json](execution/requirements.json); **T-CN-NFR-02** references that authority in [test-plan.json](execution/test-plan.json). Existing CN-AUTH-001/CN-GATE-001/CN-VERIFY-001/CN-ADMIN-001/CN-ACCEPT-001 mappings remain, with financial/package/device/booking boundary cases exercised in their separately authorized slices. CN-C02/CN-A06 and all PK/BP/booking requirements remain intact. This documentation adds no feature/task denominator or product acceptance. Tests NOT_RUN, product implementation NOT_VERIFIED; exact role matrix, revocation policy, financial thresholds and accountable owners remain unresolved.

<a id="features"></a>
## Features

Full feature definitions (actors, workflow, rules, capabilities, ownership, pages, integration, failure and acceptance) are owned by the existing [feature catalog](product/cinenest-feature-catalog.json). The following overview is derived from it; no feature is removed or deferred by this table. Approval and implementation are independent. Core approval provenance: S8 explicit user confirmation for CN-A12; S9 explicit user feature request for CN-C10/CN-A13.

| ID | Feature / purpose | Module | Decision |
|---|---|---|---|
| CN-C01 | Discovery — Discover cinematic venue/rooms with photos, amenities, capacity and indicative prices | Venues/Rooms | DOCUMENTED_DRAFT |
| CN-C02 | Identity, profile and consent — Google sign-in, profile and secure session with explicit versioned policy acceptance | Identity & Access | DOCUMENTED_DRAFT |
| CN-C03 | Availability and temporary holds — Choose room/date/time with conflict protection | Booking | DOCUMENTED_DRAFT |
| CN-C04 | Quotation and add-ons — Authoritative guest/duration/PS4 itemization | Pricing | DOCUMENTED_DRAFT |
| CN-C05 | Booker verification — NIC/passport upload, OCR assistance, age policy and manual exceptions | Verification | DOCUMENTED_DRAFT |
| CN-C06 | Hosted payment and confirmation — Hosted checkout, receipts and authoritative confirmation | Payments | DOCUMENTED_DRAFT |
| CN-C07 | Protected access and delivery — Provisioning status, time-bound PIN and SMS/email access instructions | Access Control; Notifications | DOCUMENTED_DRAFT |
| CN-C08 | Self-service booking history — Upcoming/past records, receipt and permitted changes | Booking | DOCUMENTED_DRAFT |
| CN-C09 | Referral and loyalty — Proposed earning, expiry, redemption and referral rewards | Customer Engagement | OPEN_DECISION |
| CN-A01 | Operational dashboard — KPIs, charts, live rooms, activity and system health | Analytics | DOCUMENTED_DRAFT |
| CN-A02 | Booking administration — Search/filter/page/detail/manual creation/change/cancel/extend | Booking | DOCUMENTED_DRAFT |
| CN-A03 | Venue and room configuration — Amenities/photos/hours/maintenance/enable/disable/device mapping | Venues/Rooms | DOCUMENTED_DRAFT |
| CN-A04 | Equivalent room views — Card/table views of same rooms and permitted actions | Venues/Rooms | DOCUMENTED_DRAFT |
| CN-A05 | Live control and incidents — Health, acknowledged controls, timers and incident handling | IoT; Access Control | DOCUMENTED_DRAFT |
| CN-A06 | Account and verification review — Separate account/verification statuses and restricted evidence review | Identity & Access; Verification | DOCUMENTED_DRAFT |
| CN-A07 | Finance operations — Transactions, fees, refunds, reconciliation and scoped exports | Payments | DOCUMENTED_DRAFT |
| CN-A08 | Analytics and exports — Reports and financial/nonfinancial chart drill-down | Analytics | DOCUMENTED_DRAFT |
| CN-A09 | Engagement administration — Promotions, loyalty/referrals and audited adjustments | Customer Engagement | OPEN_DECISION |
| CN-A10 | Policy and notification settings — Versioned pricing/policies/templates/integration settings | Pricing; Notifications; Identity & Access | DOCUMENTED_DRAFT |
| CN-A11 | Audit visibility — Scoped immutable operational evidence | Audit | DOCUMENTED_DRAFT |
| CN-O01 | Access credential lifecycle — Install/activate/expire/revoke and supported re-entry | Access Control | DOCUMENTED_DRAFT |
| CN-O02 | ESP32 and room automation — MQTT lighting scenes, AC and supported projector control | IoT | DOCUMENTED_DRAFT |
| CN-O03 | End-of-session operations — Five-minute warning, checkout/exit recording, cleaning and overstay escalation | Booking; Notifications; Venues/Rooms | DOCUMENTED_DRAFT |
| CN-O04 | Connectivity and emergency recovery — Monitoring, tested offline entry, bounded retries/recovery and intervention | IoT; Access Control | DOCUMENTED_DRAFT |
| CN-A12 | Admin-editable package details duration and prices — Authorized administrators can modify package details, session duration, and prices through the Admin Panel, without editing application code or requiring a deployment for each configuration change. | Pricing (existing proposed owner); Booking owns purchased snapshots | APPROVED |
| CN-C10 | Bank deposit transfer and receipt claim — Customer bank deposit/transfer and receipt submission with confirmation after authorized independent verification; retain online gateway option | Payments; Booking; Notifications | APPROVED |
| CN-A13 | Bank payment review and reviewer notification — Notify scoped admins of review-required bank claims and enable independent receiving-account verification/approval | Payments; Booking; Notifications; Audit | APPROVED |

<a id="business-rules"></a>
## Business Rules

### Canonical initial draft defaults
[Rule specification](product/cinenest-draft-rules.json) is machine-readable documentation, never a runtime configuration change. All initial values originate in S1 and are reaffirmed/classified by S8, not unseen PDFs. These are configurable initial defaults, not immutable application values; package content and rates are distinct from operational constraints. Backend configuration must be versioned and approved before launch. Open items: [decision register](ARCHITECTURE.md#open-decisions).

| ID | Draft rule | Constraints |
|---|---|---|
| BR-01 | LKR; Asia/Colombo; operating window 09:00–24:00 local | End is next-day midnight; customer session must end by closing under this draft. Post-close cleaning is conditionally permitted under approved OD-10; illustrative hours remain draft. |
| BR-02 | Initial operational minimum and included package duration 180 minutes; extension increment 60 minutes | Separate minimum-policy control; package cannot bypass it. Extension calculations use the applicable package version/increment; purchased duration includes accepted extensions. |
| BR-03 | LKR 2,500 for up to 4 guests; maximum 5 | Fifth guest adds LKR 800; an individual room capacity may be lower and cannot be exceeded. |
| BR-04 | Additional hour LKR 1,000; PS4 LKR 1,000/session | Supported add-ons only; gaming availability/rights require validation. |
| BR-05 | Online convenience fee LKR 100 | Manual-booking fee treatment, taxes, discounts and refundable components undecided; label quote channel. |
| BR-06 | Cleaning buffer 15 minutes after session | Scheduled cleaning is a reservation buffer, not proof of physical cleaning. Readiness requires operational clearance. |
| BR-07 | Checkout hold proposed 10–15 minutes | Exact configurable duration OPEN_DECISION OD-09; no chosen default encoded. Display server expiresAt. |
| BR-08 | Baseline admission 18+ for every guest | Draft only; booker digital NIC/passport processing does not verify companions. All-guest original-document process OD-03. |
| BR-09 | PIN valid [session_start,session_end) | No 15-minute pre-entry allowance. Length, revocation/re-entry/offline compatibility remain OD-02. |
| BR-10 | Preserve booked price, refund-policy and consent snapshots | Future policy/admin edits affect new quotes; existing purchases never silently rewritten. |

Authoritative quote uses applicable package/fee/add-on versions and server line items; [parameterized calculation](PROJECT.md#package-management) handles included duration, eligible extension units and included extras without double charging. Labeled initial illustration (online, no discounts/decided tax treatment): Base booking = **LKR 2,600**; base plus PS4 = **LKR 3,600**. These examples are documentation fixtures, not operational prices or frontend constants. Pricing owns rounding, itemization and eligibility.

### Inventory, time and concurrency
`session_end = session_start + purchased_duration` and `blocked_until = session_end + cleaning_buffer`. Reservation blocking interval is **[session_start,blocked_until)**; guest access interval is **[session_start,session_end)**. Distinguish the two in APIs, calendars, UI and device scheduling. Back-to-back session beginning exactly blocked_until is permissible only when readiness is cleared. Operating checks use venue-local calendar boundaries; storage uses UTC instants with original timezone/config version. Post-close cleaning requires the approved OD-10 staffing/process condition; special-day exceptions remain unresolved.

Recommended PostgreSQL exclusion constraint or equivalent database-enforced conflict guard on room plus active blocking range; transaction serialization/locking validates holds, booking writes, extensions and reschedules together. Redis is an optional temporary hold/cache mechanism, never the sole overlap guarantee. If Redis fails, fail safe or fall back to authoritative database-backed holds; do not bypass reservation checks. Hold owner, expiry, quote version and consumed flag checked atomically. A hold is not a confirmed booking.

### Edge workflows
- Expired hold: mark expired, release inventory, refresh availability/quote. Do not extend from browser countdown alone.
- Late gateway capture: deduplicate verified provider event; recheck reservation under database guard. Confirm only if inventory/policy remains valid; otherwise payment exception and compensating refund/support. External capture is not undone by database rollback.
- Duplicate/out-of-order webhook: durable inbox keyed by provider event/transaction; preserve facts, version-aware state projection, reconcile provider status; do not move captured to failed on a late earlier event.
- Retry: reuse logical operation idempotency key; provider lookup before another charge/refund/access write. Unknown outcome is not safe failure.
- Reschedule: quote delta/terms, lock old/new scopes in deterministic order, atomically swap reservation; retain original history and agreed policy. If external delta fails, retain original booking or documented compensating path. No silent release before new slot secured.
- Extend: applicable configured whole increment (initially 60 minutes), accepted amendment delta quote, check next booking plus buffer/closing/readiness; replace reservation range atomically. Capture delta then update access only through durable workflow; payment success does not guarantee PIN end-time updated. Reconcile any partial outcome.
- Cancel/refund: authorization and policy snapshot eligibility, cancel inventory and revoke access safely; refund remains separate asynchronous state until verified successful. No automatic card re-billing without separately approved consent/provider capability (OD-07).

### Proposed lifecycle transitions
All tables are desired behavior, not implemented states. State changes are versioned and server authorized. Timeouts are policy-configured (not invented numbers); each requires a durable audit event containing actor, resource, reason, prior/new state and correlation ID without secrets. Trigger codes below are reused in contracts. Account active/suspended is separate from verification.

#### Booking
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| draft → held | HOLD_CREATED; available blocking range, capacity, owner | Customer or authorized booking creator | Hold + versioned quote | Server expiresAt; reselect | Hold ID/range/version |
| held → awaiting_payment | CHECKOUT_REQUESTED; valid hold, eligibility/consent | Owner / payments service | Payment attempt | Hold expiry; status lookup | Quote/policy IDs, attempt ID |
| held/awaiting_payment → expired | HOLD_EXPIRED; no confirmed reservation | Booking worker | Release hold; reconcile pending attempts | Late capture goes exception | Expiry and inventory result |
| awaiting_payment → confirmed | PAYMENT_VERIFIED; authenticated gateway or authorized bank-reconciled decision, sufficient allocated payment, valid inventory and pre-confirmation eligibility | Booking service | Final snapshots/reservation; durable access/notification work | Provision failures tracked independently | Verified event/reservation IDs |
| confirmed → confirmed (new revision) | CHANGE_ACCEPTED; modification/extension authorized, new range secured | Owner or booking.update actor | New agreed delta/snapshot, reschedule access | Partial payment/access reconcile | Old/new revision, consent |
| confirmed → cancelled | CANCEL_ACCEPTED; policy and safe revocation | Owner or booking.cancel actor | Release future inventory; refund/access work | Retries; support for partial result | Policy/reason/refund IDs |
| confirmed → completed | SESSION_FINISHED; session ended | Session service | Archive purchase/history | No exit/readiness inference | Session completion reference |
| awaiting_payment → payment_exception | LATE_CAPTURE_CONFLICT; capture but slot unavailable | Reconciliation service | Compensation request + incident | Manual support/refund reconciliation | Capture/conflict/compensation IDs |

#### Payment (attempt state and refund ledger separate)
These attempt/capture transitions describe gateway payment only. Manual receipt/funds/request/refund transitions are separately canonical in the bank specification; a bank credit is not a card capture.
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| created → pending | PROVIDER_CHECKOUT; valid quote/hold | Payments service | Hosted session/reference | Provider expiry; reconcile | Attempt/idempotency/provider refs |
| pending → authorized/captured | VERIFIED_PROVIDER_EVENT; signature, amount, currency and booking match | Webhook/reconciliation service | Persist capture ledger; booking event | Poll authoritative status on ambiguity | Event dedupe key, ledger ref |
| pending → failed/cancelled/unknown | VERIFIED_FAILURE/CANCEL or timeout | Provider adapter / worker | UI status; no success | Unknown → reconcile before retry | Safe reason and last observation |
| unknown/authorized → captured/failed | PROVIDER_RECONCILED; verified outcome | Reconciliation service | Exactly-once ledger effect | Escalate bounded failures | Reconciliation ref |
| refund requested → pending → succeeded/failed/unknown | REFUND_REQUESTED/VERIFIED_RESULT; refundable balance, policy | payments.refund actor / worker | Separate refund ledger; captured facts retained | Provider lookup/idempotent retry; escalate | Refund amount/policy/event refs |

#### Verification
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_submitted → submitted | DOCUMENT_UPLOADED; consent, allowed type/size | Booker | Private upload ref | Upload expiry/retry | Evidence ref, consent version |
| submitted → processing | OCR_REQUESTED; private validated input | Verification worker | OCR candidate facts | Provider timeout → review | Job/version/ref, no raw text in event |
| processing → review_required | AMBIGUOUS_RESULT; unreadable/uncertain/exception | Worker | Restricted review task | Staff SLA TBD, support | Reason code |
| processing/review_required → verified/rejected | VERIFICATION_DECIDED; approved eligibility process, not OCR alone | Authorized verifier | Minimal decision, expiry/scope | Appeal/reupload per policy | Reviewer, basis, policy version |
| verified → expired/revoked | VALIDITY_ENDED/REVIEW_REVOKED | Policy worker / restricted verifier | Block future eligible checkout | Reverify; existing session impact assessed | Reason/scope/time |

#### Access provisioning
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_requested → requested → pending | BOOKING_CONFIRMED; tested lock capability | Access service | Private credential + durable install command | Bounded retries before session; incident | Booking/credential ref, no PIN |
| pending → installed | INSTALL_ACKNOWLEDGED; correct lock/version/window | Device/vendor adapter | Provisioning success, eligible delivery | Unverified ack → lookup | Command/credential version |
| pending → failed/unknown | INSTALL_FAILURE/TIMEOUT | Adapter / worker | Support incident; no access-ready claim | Reconcile before reprovision | Safe error/last attempt |
| installed → active | WINDOW_STARTED; valid session and policy | Lock/local schedule / service | Permit entry per tested device behavior | Clock/offline risk OD-02 | Configured window + observation |
| active → expired/revoked | WINDOW_ENDED/CANCEL/SECURITY_REVOCATION | Lock schedule / access authority | Deny new entry; preserve independent egress | Offline revocation reconciliation | Reason, ack/unknown state |

#### Session
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| scheduled → active | SESSION_START; booking/window | Session worker | Approved scenes; status | Command failure does not falsify observed state | Booking/time/command refs |
| active → ending | FIVE_MINUTE_WARNING; five minutes before session_end | Scheduler | Idempotent notice | Late job says accurate remaining time | Scheduled vs delivered time |
| ending/active → ended | SESSION_END; server schedule | Scheduler | Expire entry, checkout prompt, cleaning task | No forced trap/power cut | End/revocation/task refs |
| active/ending → active (revision) | EXTENSION_CONFIRMED; range/delta/access reconciled | Booking/session services | New end, reschedule notices | Partial outcomes → incident | Revision and access result |
| ended → overstay_incident | OVERSTAY_SUSPECTED; evidence/operational check | Operator or monitoring service | Escalation to accountable staff | Never infer all occupants exited | Evidence type and intervention |

#### Room readiness
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| ready → reserved/in_session | RESERVATION/SESSION_START; schedule + cleared readiness | Booking/session service | Availability projection | Projection lag → refresh | Reservation/session IDs |
| in_session → cleaning_required | SESSION_END; readiness not presumed | Session service | Physical cleaning work item | Staffing SLA OD-04 | End/task refs |
| cleaning_required → cleaning → ready | CLEANING_STARTED/CLEARANCE; authorized physical checklist | Operations staff | Clearance evidence; enable booking admission | Buffer elapsed alone cannot clear | Staff/checklist/completedAt |
| any → maintenance/unknown | FAULT/STALE_CLEARANCE | Operator/health service | Block unsafe readiness | Inspection/reconciliation | Reason/observation |
| maintenance/unknown → ready | INSPECTION_CLEARED; no conflict, physical assurance | Authorized operator | Updated safe readiness | Escalate failed checks | Inspection result |

#### Device command
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| requested → pending | COMMAND_ACCEPTED; permission/capability/safety/expiry | device.command actor / worker | Durable publish with command ID | Bounded retries | Requested action/resource/actor |
| pending → acknowledged | DEVICE_ACK; correct command ID/version | Authenticated device adapter | Receipt acknowledgement only | Wait observed state if required | Device ack/observedAt |
| pending → failed/expired/unknown | DEVICE_NACK/TTL_END/OUTCOME_UNCERTAIN | Adapter / timeout worker | Error/incident, no invented OFF state | Query state before retry | Safe code/expiry/lastSeen |
| acknowledged → observed result recorded | TELEMETRY_CONFIRMED; matching expected state | Telemetry adapter | Separate observed state field | Missing telemetry remains unknown | Observation/correlation refs |
| unknown → acknowledged/failed | STATUS_RECONCILED; authoritative response | Worker/operator | Reconcile original command | Expired command never replayed | Original command + reconciliation |

See [IoT safety](ARCHITECTURE.md#iot-access) and [data contracts](ARCHITECTURE.md#data-api) for durable integration and failure ownership.

### Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
[Package management](PROJECT.md#package-management) is canonical for PR-PKG-001/CN-A12. Initial defaults do not prevent authorized future package-duration/price/content changes. Global/venue minimum-duration policy, hours, cleaning and physical capacity remain separate operational constraints; a two-hour package requires separately authorized minimum and customer-terms changes. Shared Pricing add-on/fee definitions are referenced, not duplicated per package. No pro-rata discount or new tax treatment inferred.

Payment/eligibility revalidation checks the accepted quote version, verified amount/currency and current reservation eligibility, not a newly published catalog rate. Valid quote honor is RECOMMENDED, expired quote refresh needs acceptance; arrival time of a delayed webhook is separate from verified capture timing. [Package quote/amendment protections](PROJECT.md#package-management) qualify the existing late-payment and lifecycle recovery rules. Deactivation does not cancel purchased bookings; changing live catalog cannot rewrite snapshots, cleaning intervals or access credentials. Source PDFs remain missing and unchanged.

### Bank payment — CN-KB-BANK-PAYMENT-001
[Bank deposit/manual payment](PROJECT.md#bank-payments) owns PR-BANK-001/CN-C10/CN-A13. Core USER-CONFIRMED / APPROVED; supporting flow RECOMMENDED, implementation NOT_VERIFIED. Receipt submission, verified bank fund, allocation/satisfaction, inventory/booking, access and bank refund states are distinct; on-site original-ID entry checks remain separate. Payment verification accepts authenticated supported gateway result OR authorized receiving-bank reconciliation, not screenshot/OCR. Sufficient allocated payment plus current inventory/pre-confirmation eligibility gates one booking.

Bank request/hold uses durable shared inventory with session/cleaning/maintenance constraints and method-specific lead/payment/submission/review/hold/quote policies. Do not reuse gateway short-hold candidate or assume 24-hour minimum; no numeric bank default selected. Reuploads do not restart deadlines. Hold expires independently of price validity; timely compliance/operator delay requires accountable restoration/alternative/refund handling, never displacement/forfeit. Verify accepted quote/currency, not newest package. Underpayment, excess, late received funds and dual-method payments retain real ledger facts independently of booking resolution. Bank-method convenience fee is OD-25, not automatic LKR 100/waiver; no card token/damage re-billing assumed. All existing operational/package/safety constraints remain.

<a id="package-management"></a>
## Package Management

Task CN-KB-PACKAGE-CONFIGURATION-001 · 2026-10-14 (Asia/Colombo). Source [S8 user request](sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt). Core decision APPROVED (user-confirmed requirement); supporting design RECOMMENDED unless explicitly marked OPEN_DECISION. Implementation NOT_VERIFIED: no application manifest, package admin, API, database or execution test exists in inspected repository.

### Requirement and scope
**PR-PKG-001 / feature CN-A12:** “Authorized administrators can modify package details, session duration, and prices through the Admin Panel, without editing application code or requiring a deployment for each configuration change.”

This confirms the product capability, not a selected implementation, tested workflow, provider approval or legal authorization. Proposed listing/create/duplicate/edit/preview/publish/schedule/deactivate/archive mechanics below support that capability. Canonical ownership stays in existing proposed Pricing module; no second pricing module, generic rules engine, automatic demand pricing or implied unrelated promotion scope. Historical [ADR-001](DESIGN.md#adr-001)/[ADR-002](ARCHITECTURE.md#adr-002) remain; [ADR-003](ARCHITECTURE.md#adr-003) records this update.

### Actors and permissions (proposed capability labels)
| Actor | Capability and scope | Separation |
|---|---|---|
| Package viewer | package.read in authorized venue scope | Read drafts/history only if permitted; customer sees eligible published catalog |
| Package editor | package.edit; create/duplicate/edit draft in allowed scope | Edit does not imply publication or policy/physical-capacity modification |
| Publisher | package.publish; explicit allowed venue/room scope | Validates complete revision, schedule and policy before atomic publication |
| Archive/deactivation operator | package.archive / package.deactivate | Must preserve sold versions and respect current checkout policy |
| Auditor | audit.read within scope | Redacted old/new values/reason/version/effective dates; cannot mutate history |
| Operations/policy owner | Separate policy and room configuration capabilities | Controls approved minimum, hours, cleaning and physical occupancy, never through package-edit privilege |

Exact role names, capability allocation, self-publication and two-person approval thresholds remain OD-18. Backend authorization on every read/write/publish and scope mapping; hiding controls is insufficient. Customer only accesses applicable versions and own quotes/purchases.

### Fields and joint validation
Core editability is APPROVED; field representations and validation design below are RECOMMENDED. Publishing validates the entire candidate snapshot and related configured constraints in one authoritative operation, never partially updates duration/inclusions/price.

| Field/group | Decision | Representation and validation |
|---|---|---|
| Package name and description | APPROVED core editability | Required name, bounded accessible/sanitized description; limits/localization later selected |
| Included services and amenities | APPROVED core editability | Stable service references and customer-facing inclusions; only compatible/authorized services, no unverified streaming/gaming claims |
| Included customer session duration | APPROVED core editability | Positive integer minutes; no cleaning minutes; respects effective minimum policy and maximum selectable duration |
| Base package price | APPROVED core editability | Exact integer minor units plus ISO currency or exact decimal persisted/converted per approved currency scale; no binary floating point. Reject negative; zero requires explicit OD-17 decision |
| Promotional image/display order | RECOMMENDED | Approved asset reference, alt text, integer order with stable ID tie-breaker; rights/private-upload checks |
| Minimum/maximum selectable duration | RECOMMENDED | Positive integer minutes; minimum ≤ included ≤ maximum for proposed initial model; effective minimum also respects operational policy |
| Extensions, increment, charge | RECOMMENDED | Explicit enabled flag; positive integer increment and nonnegative exact unit charge; maximum/hours/conflicts validated; included duration not charged again |
| Included guest allowance/extra guest charge | RECOMMENDED | Positive integer allowance and compatible approved room capacity; price-policy reference for excess permitted guests, not physical-capacity change |
| Add-ons and inclusion/charge mode | RECOMMENDED | Versioned shared add-on references with included vs optional-charged flags; reject contradictory/unsupported selection, no double charge |
| Venue/compatible-room scope | RECOMMENDED | Authorized stable references, explicit compatibility and capacity validation; cannot publish into unowned venue |
| Sales effective interval and optional session-validity interval | RECOMMENDED | Timezone-aware half-open intervals and deterministic scope/version applicability; separate purchase time from cinema-service date |
| Availability/publication status | RECOMMENDED | Draft/scheduled/published/inactive/archived lifecycle; backend computed applicability separate from editing state |
| Versioned booking/refund policy reference | RECOMMENDED | Existing immutable policy version applicable to scope; preserve actual accepted consent version |

Do not duplicate shared fee/add-on definitions inside each package by default. Versioned references preserve the configured values used in quotes; intentional overrides need an explicit future design decision.

### Ownership and initial defaults
[Business rules](PROJECT.md#business-rules) / [draft JSON](product/cinenest-draft-rules.json) retain S1/S8-described initial defaults, not immutable code values. Original PDFs are missing; S8 reports a mandatory three-hour minimum, which is retained as a draft constraint pending final source/policy verification. No PDF rewritten or legal approval inferred.

Initial rates, duration, guest allowance, occupancy ceiling, fee and buffer are listed once in [business rules](PROJECT.md#business-rules), with owning modules in the existing draft-rules registry. Package settings cannot change the independent minimum/hours/cleaning/physical ceiling. References to physical capacity describe required approval, not evidence of certification (OM-04).

A two-hour package is eligible only after a separately authorized minimum-duration policy and corresponding customer terms are updated. A package editor cannot lower the venue minimum or exceed physical capacity by changing included guests. The current capacity ceiling is not a configurable safety exception. Exact scope/precedence of global versus venue minimum changes and approval gates are OD-21.

### Duration and server quotation model
Proposed initial extension-only model: selected duration ≥ included duration and effective minimum. `effective_min = max(package.minimumSelectableMinutes, operational.minimumSessionMinutes)`; include must also meet operational minimum; maximum ≥ included and effective_min. Selecting fewer minutes than included does not imply proration; unsupported shorter selection rejected until OD-17 resolves a different model. If extensions disabled, selected must equal included. If enabled, extra minutes must be an exact nonnegative multiple of configured increment and selected ≤ maximum. Check venue schedule and [session_end, blocked_until](PROJECT.md#business-rules) against reservations/cleaning readiness before quoting or amending.

Parameterized pseudocode (desired server behavior; not implemented):
```text
resolve applicable published package_version, room_constraints, pricing_refs, policy_refs
validate authorization, sale/session applicability, capacity, included services and currency
validate positive integer selected_minutes and effective minimum/maximum
extra_minutes = selected_minutes - package_version.included_minutes
if extra_minutes < 0: reject unless an explicitly approved shorter-session model applies
if not extensions_enabled and extra_minutes != 0: reject
if extra_minutes > 0:
    validate extra_minutes % extension_increment_minutes == 0
    extra_units = extra_minutes / extension_increment_minutes  # exact integer
else: extra_units = 0
line_items = [package_base_line(package_version)]
append extension line(extra_units * configured_extension_unit_minor) when extra_units > 0
append configured extra-guest lines only for guests beyond purchased included allowance
for selected_addon in supported_selections:
    if selected_addon is already included: record inclusion, do not add a payable line
    else: append configured versioned add-on line
append configured channel-fee line; approved discount/tax handling only when defined
total_minor = exact_sum(authoritative_line_items)
return versioned_quote(line_items, total_minor, currency, expiry, terms_and_package_refs)
```
Rates/counts never substituted from browser text or live catalog after acceptance. Tax, proration, rounding, zero-price and price/approval thresholds remain OD-17/18; no invented tax treatment. Labeled default examples only: three hours/four guests/no PS4 **LKR 2,600** with online fee; optional PS4 **LKR 3,600**, no discounts or decided tax treatment.

### Proposed administrative lifecycle
| Action/transition | Guard and result | Publication/recovery |
|---|---|---|
| List/search → view | package.read and scope; list applicable/draft/history versions | Safe pagination; no unrelated tenant assumption |
| Create/duplicate → draft | package.edit; duplicate copies permitted content with new stable identity; does not copy bookings | No sale until publish; changing draft retains history/version token |
| Edit/save draft → draft revision | Optimistic concurrency/version token, all draft fields submitted together | Preserve safe inputs; stale edit rejects/reload-diff rather than overwrite |
| Preview → preview only | Compute customer card/quote illustration from draft and policies; label not for sale | No payment, live rate or hardware side effect |
| Publish → published or schedule → scheduled | package.publish, complete joint validation, scoped effective ranges/policy/compatibility | Atomic immutable revision + audited schedule; no partial duration/price/inclusions |
| Scheduled → applicable at sales boundary | Validated schedule and operational validity; deterministic version selection | Worker/reconciliation refreshes projections; server applicability authoritative, cache invalidation monitored |
| Deactivate → no new quote sales under selected policy | package.deactivate; explain valid-quote handling and existing booking protections | Recommended honor valid issued quotes, no new quotes; explicit cutoff recorded |
| Archive → historical/read-only | package.archive; stable ID/versions referenced by purchases remain retained | Hide from new-sale catalog; never delete sold snapshots/history |

Two-person review is proposed, not approved. Suggested lifecycle state names are not implemented schema. Draft save is distinct from publish; exact state model/provider/deployment remains architecture work.

### Effective versions, scopes and concurrent publishing
Stable packageId owns immutable published packageVersionId/revision and purchased display/inclusion/price terms. Any sold-term edit creates a new revision; publication never destructively edits a referenced revision. Store sales [effectiveFrom,effectiveTo) and separate optional session/service [validFrom,validTo) with explicit venue-local timezone (Asia/Colombo), resolved UTC instants and policy version. Sales time selects new purchase applicability; service-time eligibility independently checks scheduled session. Date-only end boundary resolves to next local midnight exclusive.

Recommended: prevent overlapping sales intervals for the same stable package and intersecting venue/room scope. Joint validation and transactional version token/uniqueness guard reject ambiguity rather than “latest timestamp wins.” A superseding schedule closes prior sales eligibility atomically at cutoff, preserving immutable sold terms and audited prior schedule. More specific room scope does not silently override venue scope; ambiguous mappings rejected until OD-19 chooses precedence. Recheck concurrent policy/room revisions on publish; cached catalog never authorizes an invalid quote.

### Existing purchases and active checkout protections
Each confirmed booking stores package/version reference and purchased display details; included/selected duration, session_start/end and reserved cleaning-buffer snapshot; guest allowance and included/selected services; exact price line items, fees/discounts/final total/currency; accepted booking/refund-policy and consent versions; quote reference. Package name/description edits also cannot rewrite historical tickets/receipts/reports.

Catalog changes cannot silently alter existing bookings, receipts, historical duration/revenue reporting, inclusions or active PINs. Deactivation blocks new sales under defined cutoff; it does not cancel reservations. Sold versions remain referentially intact. Access reads booking/session snapshots, not live package duration.

Recommended quote policy: honor a still-valid authoritative quote with its package/fee/add-on/policy versions even if a new catalog version is published or package is deactivated. After quote expiry, return refreshed terms and require clear customer acceptance before a new attempt. Final policy for emergency withdrawal versus valid-quote honor is OD-20, not an automatic override. Quote/hold expire independently: a valid price quote cannot reserve unavailable inventory.

Payment confirmation validates **accepted quote** and expected amount/currency plus current reservation/eligibility and verified provider evidence; it never silently looks up a newly published rate. A callback arriving late is not proof of a late payment: compare verified capturedAt evidence with accepted quote validity and attempt linkage. Starting checkout in time is not evidence capture completed in time. Evidence requirements/authorization-versus-capture cutoff policy are OD-20. Valid in-time payment with delayed notification still requires safe inventory reconciliation; do not automatically reject solely because callback arrived after quote expiry.

Expired hold/late capture/conflict → durable reconciliation/payment exception and approved refund/support compensation; no duplicate reservation, retroactive reprice or unapproved charge. Dedupe callbacks/ledger by provider/operation ID; replay resolves original purchase references. Database rollback cannot undo external capture.

Purchased booking amendment is a separate authorized workflow: availability and capacity/readiness check → versioned delta quote and customer terms acceptance → payment/refund difference resolution → guarded reservation revision → access-window/notification coordination with durable retries/unknown outcomes. Do not derive amended terms automatically from latest package. Partial external outcomes require compensation/support while protecting previous purchase history. Package publication alone never sends access/IoT commands, extends a PIN, interrupts an active session or drops buffer/conflict guards.

### Proposed architecture/API and audit
Pricing owns stable catalog identities, immutable versions, applicability schedule and quote construction. Booking owns reservation/confirmed snapshot; Venues/Rooms own operational constraints/capacity; Identity & Access handles capabilities; Payments validates accepted quote and ledger; Access uses purchased session; Audit preserves evidence. No final tables, API routes, migrations, module additions or installed stack claimed. [Contracts](ARCHITECTURE.md#data-api) defines proposed capabilities: list/detail/history, create/duplicate draft, update with version, preview, publish/schedule, deactivate/archive, resolve applicable version and quote.

Persist atomic publication/audit intent; invalidate scoped catalog caches/durable projections without per-edit code release. Request idempotency and concurrent version tokens protect mutation retry/publish schedules. Audit actor, authorized scope, timestamp, reason, old/new values, stable package/version IDs, effective boundaries and policy references; no PII/PIN/card data. Booking amendment has its own audit and agreed difference. Exact table/ETag/event designs RECOMMENDED, not approved core capability.

### Future implementation acceptance (not tests run here)
| ID | Scenario / required evidence |
|---|---|
| PK-AC01 | Authorized admin edits details, included duration and price; eligible customer catalog changes without application-code edit/release; config/audit evidence |
| PK-AC02 | Unauthorized/out-of-venue reader/editor/publisher cannot access or publish; edit privilege does not grant publish/archive or operational-policy change |
| PK-AC03 | Backend rejects noninteger/nonpositive durations, negative charges, unsupported/duplicate charged inclusions and capacity excess |
| PK-AC04 | Two-hour edit rejected under 180-minute minimum; separately authorized minimum/terms update required before shorter package eligibility |
| PK-AC05 | New eligible purchase resolves deterministic package version by sales time, service window and scope; boundaries and overlapping versions tested |
| PK-AC06 | Confirmed purchase preserves display/rate/inclusions/duration/buffer/policy/receipt/report snapshots after edits/deactivation/archive |
| PK-AC07 | Valid quote honors accepted terms; expired quote requires refreshed explicit acceptance; inventory hold tested independently |
| PK-AC08 | Duplicate/delayed capture events reconcile accepted quote/capturedAt/amount/currency; no extra booking/reprice/charge; unavailable slot goes exception/compensation |
| PK-AC09 | Scheduled edits and simultaneous admins produce atomic complete publication or deterministic stale/conflict failure; no partial combination |
| PK-AC10 | Included PS4/service/add-on never charged twice; configured exact extension-unit and guest-charge calculations validated |
| PK-AC11 | Deactivation prevents new quote sales according to approved cutoff while preserving issued-quote policy and existing reservations/history |
| PK-AC12 | Purchased amendment rechecks availability/cleaning/payment/consent and coordinates PIN/notification revision safely; catalog edit alone leaves active session/PIN unchanged |
| PK-AC13 | Historical reports use purchased snapshots/ledger refs and correct metric time basis, not current catalog prices or durations |

### Open decisions and provenance
[OD-17–OD-22](ARCHITECTURE.md#open-decisions) track proration/rounding/zero/taxes, price and two-person thresholds, scope/version precedence, quote/deactivation/capture timing, minimum-policy ownership and shared-fee/add-on overrides. S8 confirms core admin editability only; S1/S8 defaults remain initial draft values. Original PDFs missing and no production configuration exists. Documentation validation cannot establish these acceptance outcomes or hardware/legal approval.

### Bank payment — CN-KB-BANK-PAYMENT-001
Package editability CN-A12/PR-PKG-001 and existing approved/no-release requirement are unchanged. [Bank payment](PROJECT.md#bank-payments) snapshots package/version/display/session/buffer/guests/inclusions/extras/item lines/consent/policy/quote and receiving-instruction version into request. Bank quotes preserve applicable explicitly approved method fee treatment; the online LKR 100 initial example does not decide bank fee (OD-25).

The earlier capturedAt/timing discussion applies to gateway. Manual timeliness distinguishes declared initiated time, actual bank credit, submission and authorized verification; selected authoritative clock is OD-24, not implicitly review time. Accepted quote/currency remains protected from catalog changes while valid; expiry/re-quote acceptance and independent received-fund traceability follow the method policy. Bank review/verification never modifies packages, cancels sold terms, auto-amends bookings or extends PINs. New bank-funded purchased amendments still use the existing separate guarded quote/payment/refund/access workflow, without assuming a stored card.

<a id="bank-payments"></a>
## Bank Payments

Task CN-KB-BANK-PAYMENT-001 · 2026-10-14 (Asia/Colombo). Source [S9 user request](sources/CN-KB-BANK-PAYMENT-001-request.txt). Provenance **USER-CONFIRMED**; core Decision **APPROVED**, Implementation **NOT_VERIFIED**. Supporting controls/workflows/data design **RECOMMENDED**; numeric deadlines, fee/config/provider/operational choices **OPEN_DECISION**. No code, real account details/bank connectivity, transactions, money movement or review/hardware test evidenced.

### Core requirement and scope
**PR-BANK-001 / CN-C10 and CN-A13:** Customers can deposit/transfer funds to the business bank account, upload payment evidence, and obtain confirmation after an authorized administrator independently verifies and approves payment; the system notifies authorized administrators when review is required. Existing online gateway payment remains available.

**RECEIPT UPLOADED is not PAYMENT VERIFIED. PAYMENT VERIFIED is not automatically BOOKING CONFIRMED or ACCESS READY.** Bank deposit/transfer is distinct from bank IPG online gateway; do not forge a webhook or record bank credit as card capture. A customer document is a payment claim, not a CineNest-issued receipt or proof funds arrived. Post-confirmation financial acknowledgement/receipt comes from verified ledger and allocation.

Recommended launch scope: full accepted quoted payment before confirmation, request bound to package/room/session/guests/extras; no installment booking, wallet, transferable credit, open-date voucher, subscription or multi-booking shared-deposit entitlement. Partial funds still recorded; additional top-up handling requires an explicit policy (OD-27), not an installment product. Third-party payer name may differ legitimately; collect adequate matching context, never reject solely for name difference.

### Actors, permissions and configuration
| Actor | Proposed capabilities | Boundary |
|---|---|---|
| Customer | bank.request.create/read; payment.claim.submit for own booking/request | No cross-owner preview; cannot verify funds or allocate credit |
| Review staff | bank.review; bank.evidence.read within authorized venue scope | Can inspect/request information/await credit/reject claim; evidence access audited |
| Financial verifier/allocator | bank.verify and bank.allocate, independently granted | Requires business receiving-account facts; assignment is not approval permission |
| Resolution/correction staff | bank.resolve / bank.adjust | Controlled auditable corrections; cannot delete financial facts or bypass booking guards |
| Refund staff | payments.refund.request/approve; bank.refund.execute/reconcile as selected | Recipient verification and amount/operation controls; approval != sent/confirmed |
| Receiving-account manager | bank.account.manage separate from review | Versioned independently verified account changes, role/threshold choice OD-28 |
| Booking/access/notification workers | Narrow service scope | Process durable verified facts and eligible reservation only; no automatic broad admin grant |

Capability names/role allocation are proposals, not existing code. Bank settings expose verified business account identity/instructions via authenticated request-bound view, with instruction version/check timestamp/account-manager audit. Do not invent a live bank name, account number, support, bank fee or integration. Secrets/credentials never displayed or requested. Changes do not rewrite issued requests; emergency redirect requires controlled direct customer verification, never an unauthenticated message.

### Customer workflow
1. Choose package, room/session, guests/add-ons; backend resolves versioned quote, scope and pre-confirmation eligibility.
2. Select **Online Payment** (existing hosted gateway) or **Bank Deposit / Transfer**. Bank option only enabled after receiving instructions, manual-review capacity, fee and timing policy are approved/configured.
3. Create linked bank payment request plus bounded durable provisional inventory hold. Display request/reference, account-instruction version, exact accepted amount/currency/fee treatment, separate deadlines/review hours and actual hold status.
4. Customer deposits/transfers using instructions. Never request banking password, PIN, OTP or card security code.
5. Submit own receipt file and declared payment amount/date/time, bank reference if available, optional payer name and note. Declared facts remain distinct from independently verified credit; preserve each correction submission and timestamp.
6. Acknowledge submitted claim and durably queue reviewer notification/work. Show **Payment Under Review**, not Paid/Confirmed. Safe microcopy: “Receipt submitted for verification. Your booking is not yet confirmed. We will notify you after review. Please do not pay again.” Add “Your provisional hold expires at [date/time]” only when a valid hold actually exists.
7. Staff matches actual receiving-account credit, records verified fund fact and lawful allocation. If payment sufficient and reservation/eligibility valid, confirm one booking and queue notification/access provisioning. Otherwise **Funds Received / Booking Resolution Required**, never discard actual money.
8. Customer tracks separate review/payment/booking/access/refund outcomes and corrections/support. Do not invite payment against expired request; accept late evidence through reconciliation intake without promising inventory.

### Proposed reviewer queue and actions
Location **Admin → Payments → Bank Deposit Reviews** (proposed subsection, no route invented). Queue columns: request/booking/customer, venue/room/session, package version, expected/declared amount/currency, submittedAt, review status/age, assignee, hold expiry, overdue/exception flag and notification status. Filters: scope/room/session date, review/hold status, assignee/overdue, safe request reference search; server pagination and privacy-scoped searches.

Detail: restricted safe preview, declared versus verified facts, accepted quote/item lines/policies/package, instruction version, full submission/correction history, receiving-bank match, verified/allocated/unallocated balance, current inventory/eligibility, timeline/audit and notification state. Controls **Verify Funds and Approve**, **Request More Information**, **Await Bank Credit**, **Reject Payment Claim** (reason), **Resolve Booking/Amount Exception**. “Verify Funds and Approve” is a composite UI action with independently persisted funds and guarded allocation/confirmation; display each result instead of conflating them. Server result controls pending/success/conflict/error. Rejection of claim does not delete funds or prevent later independent credit discovery.

### Independent verification, matching and allocation
Require receiving-account identity and instruction version; bank-side transaction/reference (or controlled statement-entry identity), bank credit date/time, verified currency/amount, reviewer and reason/notes with evidence reference. Customer screenshot/email/OCR/provider notification alone cannot authorize credit verification. No current bank connection assumed; manual matching against the business’s own authoritative records is the launch recommendation. OCR can suggest fields only; bank feeds/APIs are future scope after official support/account/privacy evidence.

Recommended matching key: receiving-account identity + genuinely bank-assigned stable transaction ID where available. Otherwise staff records controlled statement identity/period plus unique statement-entry/line and verified credit details, with cross-statement reconciliation/deduplication and ambiguity review. Customer-entered reference is a matching hint, not a globally unique ledger key. Same receipt hash is a duplicate-risk signal, not evidence of payment or allocation protection.

Verified incoming fund fact persists once regardless of booking fulfillment. Allocation is a separate append-only record linked to fund/request/quote/currency. Lock/conditionally version fund, payment request, aggregate payment attempts and reservation; total allocated/refunded/reserved balance cannot exceed actual verified credit. Initial single-request allocation scope rejects claiming the same credit against another booking; any multi-booking shared allocation is a future approved extension, not default. Underpayment remains unallocated/partial satisfaction and outstanding due; overpayment allocates only quote due, remainder retained for controlled resolution. Incoming receipt verification and allocation are not two receipts of money.

For a composite approval: (A) persist independently verified unique fund fact and audit, even if inventory later conflicts; (B) in guarded local transaction dedupe allocation operation, check aggregate bank+gateway satisfaction, accepted quote/currency/eligibility and shared reservation inventory, append bounded allocation and confirm one booking if all guards pass; otherwise record payment/allocation/booking exception as appropriate. If enough funds but no inventory, keep verified funds separately and resolution case; never silently displace another booking. Durable outbox queues notices/access only after committed eligible confirmation. External failure cannot roll back/erase actual received funds. Repeated review actions and concurrent reviewers resolve same operation/version; image hashes are not concurrency guards.

Error/suspicion correction appends a reversal/adjustment of match/allocation with authorized actor/reason/original references. It does not delete bank credit or rewrite approved history; actual credit later reversed by bank is a separate verified bank reversal fact and linked resolution. Booking/access consequences are explicit safe incident/amendment actions, never occupant trapping.

### Holds, quotes and deadlines (unapproved values)
Separate configurable settings: minimum session lead time; payment deadline; evidence-submission deadline; review service hours/deadline; provisional/review-hold expiry and bounded extensions; quote/payment-request validity; overdue/escalation thresholds. Exact values **OPEN_DECISION OD-23**; 24-hour lead time is only an earlier suggestion/example, not approved or selected. Do not copy gateway 10–15 minute candidates into bank flow, or create indefinite review holds.

Durable bank holds use the same database-enforced room/session+cleaning reservation ranges as gateway holds/confirmed bookings/maintenance. Redis may cache, never sole long-hold record. Timely submission may transition to policy-bounded review hold; repeated uploads/corrections do not reset clock automatically. Show separately quote validity, request validity and whether/through-when inventory is reserved. At approval recheck reservation atomically; expired hold never authorizes displacement. Manual review creates a real staffed service commitment, hours and escalation staffing OD-29.

Timestamps separate: payment_initiated_at (declared unless independently evidenced), bank_credited_at, evidence_submitted_at, verified_at, allocated_at and booking_confirmed_at. Which timestamp satisfies timeliness is **OD-24**, not implicitly verified_at or notification arrival. No quote repricing to newest catalog at approval. Valid accepted quote honor remains recommended with method-specific review/request validity policy; after expiry clear re-quote acceptance, received funds still traceable. Operator delay after timely customer compliance: restore slot if available under policy, customer-agreed alternative or appropriate approved refund if service unavailable; never silently forfeit or assume new terms. Hold extension/exceptions remain bounded and audited.

### Package, eligibility and confirmation invariant
Request snapshots package/version/display/duration/session/buffer/guests/inclusions/extras/item lines/fee treatment/currency/policy/consent/quote validity and receiving-account instruction version. Protect [package terms](PROJECT.md#package-management); bank-method convenience fee requires OD-25 decision, neither automatically LKR 100 nor waived. No invented tax/bank surcharge. Show selected approved treatment before paying and preserve it.

**Payment is verified through either an authenticated supported gateway result or an authorized bank-reconciled manual-payment decision. Booking confirmation additionally requires sufficient allocated payment, valid reservation inventory, and applicable pre-confirmation eligibility.** Independent on-site original-ID checks remain a separate entry requirement; bank approval cannot bypass identity/age/consent/capacity/hours/physical-safety rules. Verified partial payment or unmatched funds do not satisfy full quote. No PIN release while payment unverified; confirmed purchase then uses existing acknowledged provisioning and purchased [session_start,session_end) validity. Provisioning failure is access pending/incident, not payment failed.

### Money and service exception matrix
| Situation | Required desired handling |
|---|---|
| Underpayment | Persist actual received credit, partial allocation and outstanding; full-payment launch does not erase partial money or authorize confirmation; top-up policy OD-27 |
| Overpayment | Allocate ≤ accepted due; excess unresolved/refund balance, never extra booking or recognized revenue |
| Same credit/multiple receipts or bookings | Bank-side credit dedupe and locked allocation uniqueness; receipt hash helps review only; reject second unauthorized allocation |
| Bank plus gateway payment | Track both verified sources, one booking satisfaction/reservation; route duplicate/excess funds to resolution/refund, no blind additional charge |
| Rejected claim then later bank credit | Append discovery/reopen reconciliation; received money stays recorded |
| Wrong account/currency, missing reference, unmatched credit | Explicit matching exception; no invented FX/credit; correct declared details with history, only actual business credit recorded as received |
| Unreadable/malicious/suspicious evidence | Quarantine/safe correction/restricted review; no approval from OCR/hash |
| Expired/cancelled session with funds | Financial fact retained; agreed alternate/refund/support, never automatic new booking or silent forfeiture |
| Notification/review staffing delay | Work remains visible; delivery retry and overdue/escalation; operator accountability, no reset of customer compliance time |
| Access install failure | Payment verified + confirmed booking + access pending/incident; bounded recovery, no false payment reversal |
| Erroneous approval | Append reviewed adjustment/reversal and safe booking incident; never delete financial history |
| Bank refund | Separate requested/approved/initiated/sent/confirmed/failed/unknown; verify destination, outgoing evidence and actual completion; approval not completion |

No card token assumed for bank customer; automatic damage card re-billing is unavailable merely because bank booking was approved. Any alternate damage payment policy requires independent approval. Refund recipient identity/third-party payer/authority and high-risk controls remain OD-28; avoid directing refunds based only on a newly messaged account number.

### Security and instruction lifecycle
Authenticated owner/scoped uploads; proposed JPEG/PNG/PDF with approved size/retention limits **OD-26**. Validate true content type, generate private object keys, scan/quarantine and restrict PDF/image safe preview (no active document execution); approved storage/private encryption/short scoped signed links/access audit and deletion lifecycle. Receipt-upload requirements do not substitute for physical original-ID entry checks. Preserve original submissions for controlled review subject to approved retention, not infinite storage.

Keep raw receipt/statement/customer payer data, bank instructions/details, credentials and PINs out of public URLs, ordinary logs, event payloads and notification attachments. Notifications use authenticated links; instruction versions change only through separately authorized independently verified bank-account management. Evidence-view/review/verify/allocate/refund/account-change permissions distinct. Exact roles/approval limits independent review are recommendations or OD-28, not blanket Admin access. Audit account changes, evidence access, match/reject/approve/adjust/allocate/refund with actor/time/reason/scope/correlation and redacted before/after. No real bank setting changed here.

### Notifications and reporting
Durable customer submission acknowledgement, correction/await-credit, verification outcome, booking outcome, expiry and refund progress. Durable admin new-review, overdue, unmatched-funds and access-failure alerts with scoped deep links; send retries/bounded failure escalations never delete queue work. A sent notification does not verify funds, clear claim, confirm booking or prove PIN installed. In-app review queue remains authoritative; no sensitive attachments or credentials.

[Bank reporting](DESIGN.md#charts-metrics) defines pending-review age/population, verified credit, booking allocation, unmatched/excess and refunds/channel breakdown. Never use uploaded/declared amounts as collections; verification+allocation is one incoming credit with two processing timestamps. Keep bank_credited_at, verified_at, allocated_at and session_start_at separate. Collections ≠ recognized service revenue. Historical package/duration reports read immutable purchase snapshots; LKR/Asia/Colombo and existing chart semantic colors/authorization unchanged.

### Proposed architecture and acceptance
Within Payments (request/evidence/verified bank credit/allocation/review/refund/instruction metadata), Booking (durable hold/guarded confirmation/snapshot), Notifications, Access and Audit; no second booking system/microservice. Conceptual names/APIs/events are unimplemented. [Data/API contracts](ARCHITECTURE.md#data-api) detail safe evidence-source events and local atomic/durable boundaries. State-machine specification follows; all transitions are RECOMMENDED design and have actor/permission/evidence/guard/side effect/timeout/retry/audit. No numeric deadline approved.

#### receipt_review

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| none → submitted | Customer / payment.claim.submit, own request | Authenticated allowed upload and declared details; evidence submission timestamp | Valid own request or explicitly late-claim recovery; private scan/quarantine | Immutable submission revision; durable reviewer work/acknowledgement | Upload/submission deadline configurable; late claim retained, no hold reset | Retry same submission ID/idempotency; preserve history | Actor, request/submission refs, declared facts, correlation |
| submitted → review_required/quarantined | Upload worker / service scope | Actual file validation/scan outcome | File safe to preview; quarantined cannot be opened ordinarily | Safe preview or isolated quarantine; queue not dropped | Scan timeout pending/unknown with staff escalation | Bounded scan retry; no payment approval from scan | Scan/result/version refs; no raw image |
| review_required → reviewing | Reviewer / bank.review, venue scope | Assignment and current record version | Authorized reviewer, conditional version/assignment claim | Review ownership recorded; no fund allocation | Assignment timeout configurable; reassign without resetting customer deadline | Concurrent claims return conflict/current assignee | Reviewer/time/assignment history |
| reviewing → information_requested/awaiting_credit/rejected_claim | Reviewer / bank.review | Safe reason, receiving bank query if available | Insufficient matching/credit or suspicious/unreadable claim; rejection is not denial that money may later arrive | Customer correction/credit/rejection status and durable notice | Review/correction due times policy open; late funds trigger new reconciliation | New submissions retained; rejection reversible via auditable new decision | Reason/evidence/previous decision refs |
| reviewing/awaiting_credit → funds_matched | Reviewer / bank.verify | Independent receiving-account record and verified fund ID | Currency/credit identity and match sufficient, not screenshot/OCR; credited funds persisted separately | Link verified fund to claim; allocation/payment status checked separately | Review overdue escalates; no promised booking | Idempotent match; original bank fact retained | Bank match, reviewer, decision/version |
| information_requested/rejected_claim → review_required | Customer or reviewer / own submit or bank.review | New correction or independently discovered later credit | Preserve prior submissions/decisions; no deadline reset | Append history, reopen review/reconciliation | Late claim stays traceable even if inventory expired | Deduped reopen with new evidence ID | Reopen actor/reason/parent decision |

#### verified_funds_allocation

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| unmatched bank record → verified_unallocated | Reviewer / bank.verify | Receiving-account identity, bank-side reference/controlled statement entry, credit date, amount/currency, notes | Independent bank credit uniqueness/authentication within controlled process | Append immutable incoming bank fund ledger fact even if booking unavailable | No funds expiry; unresolved balance escalation policy open | Deduplicate bank-side identity, not customer ref/file hash | Verifier/evidence/credit identity and recordedAt |
| verified_unallocated → allocated/partially_allocated | Reviewer / bank.allocate (or approved verify command) | Accepted quote/request refs, sufficient outstanding balance | Lock fund/request; allocation ≤ available credit and due; initial no shared-deposit multi-booking scope | Allocation ledger append; payment satisfaction projection; excess remains unallocated | No silent forfeit; unresolved remainder tracked | Logical operation key + unique allocation constraints/versions | Fund/request/allocation refs, exact amounts and actor |
| allocated → adjusted/reversed allocation (new record) | Authorized correction reviewer / bank.adjust | Error/suspicion decision and linked original allocation | Approved correction process; no delete/overwrite bank receipt fact | Append compensating allocation entry; recalc satisfaction/booking incident, no egress trap | Incident/refund/recovery deadlines open | Idempotent correction ID; dual control threshold open | Old/new ledger refs, reason, reviewer, resulting incident |
| verified_unallocated/excess → refund_resolution | Finance / payments.refund or resolution scope | Verified balance, destination and approved resolution | No double allocation/refund; customer accepted resolution if alternative service | Link refund/resolution operation, funds remain traceable until completion | Escalation configured; never erase received money | Reconcile outgoing transfer before retry | Destination-check/result and ledger refs |

#### payment_request_satisfaction

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| draft → awaiting_payment | Customer / own bank.request.create | Versioned quote, receiving instruction version, scope/session/policy refs | Bank method enabled, lead time/deadlines approved, provisional inventory available | Immutable request snapshot/reference and durable bounded reservation hold | Separate payment/submission/quote/hold deadlines; no invented duration | Idempotent request creation | Quote/package/account instruction/hold IDs |
| awaiting_payment → under_review | Customer / payment.claim.submit | Submission revision timestamp | Timely under approved policy to qualify for bounded review hold; late goes resolution | Review work; hold condition displayed accurately, not Paid | Review service hours/deadline/hold bounded; repeated upload never extends automatically | Submission dedupe; do not request another payment blindly | SubmittedAt/hold transition and scope |
| under_review → satisfied/partially_satisfied | Reviewer / bank.verify + bank.allocate as permitted | Verified ledger allocations in accepted quote currency | Accepted quote/payment timing policy; total allocated capped at due; no latest-price lookup | Payment satisfaction only; booking transaction guard separately evaluated | Insufficient balance remains outstanding, no paid-in-full confirmation | Aggregate allocations idempotent; cross-method payment lock | Allocation/quote/verification refs and outstanding balance |
| awaiting_payment/under_review → expired/cancelled_resolution | Scheduler or owner / scoped request policy | Deadline/cancel facts | Expiry stops new payment invitation; verified credits retained; pending claims remain reviewable | Release hold when policy requires, resolution queue for late claims/funds | No indefinite extension; operator-delay escalation/open resolution | Dedupe expiry; recover with explicit re-quote/customer acceptance | Expiry/cancel/time and retained claim/ledger refs |
| expired/rejected resolution → funds_received_booking_resolution_required | Reviewer / bank.verify | Later verified actual credit | Request no longer eligible or quote/inventory conflict | Persist received funds; alternative/refund/support, not failed payment | Resolution SLA open; no silent forfeiture | Idempotent ledger and incident, no new booking automatically | Credit/exception/customer agreed resolution refs |

#### reservation_booking

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| available → provisional_bank_hold | Booking service / bank.request.create scope | Request/quote/config and room range including buffer | Database-enforced no conflict with gateway/manual holds, maintenance and confirmed bookings | Durable hold in same Booking inventory; no Redis-only guarantee | Exact hold policy OPEN_DECISION; bounded initial/review phases | Idempotent hold ID and conditional range writes | Range, expiry, policy, request refs |
| provisional_bank_hold → bounded_review_hold | Booking service / service scope | Timely submission and policy version | Current hold and policy permits review interval; repeated upload not reset | Controlled bounded extension/transition; show reserved vs not reserved | Review deadline/staffing gate; release on approved expiry | Version/transition key prevents repeated extensions | SubmittedAt/policy/old-new expiry |
| held → confirmed | Booking service / authorized verified payment decision | Sufficient allocated gateway/bank payment facts and accepted quote | Atomically validate inventory/current reservation, pre-confirmation identity/age/consent/capacity/hours; manual reviewer cannot bypass | One immutable booking snapshot; durable notifications/access work | Access result separate; unknown command/notice outcomes tracked | Request/booking identity and unique guarded confirmation | Evidence source, allocations, reservation/eligibility snapshot |
| expired hold + received funds → resolution, not displacement | Reviewer + Booking service / resolution scope | Verified credit, expired range and availability check | Never displace another confirmed booking; restoration only if available under policy | Customer-agreed alternate/refund if service unavailable; retain fund fact | Operator-caused delay after timely compliance escalates accountable staffing | Single resolution operation, explicit alternative/re-quote acceptance | Cause/timing/attempted restore/customer agreement |
| confirmed → cancelled/amended revision | Owner or booking agent / existing change permissions | Purchase policy + consent + delta quote and payment/refund facts | Existing amendment/cancel guards; bank method is no bypass | Durable allocation/refund/access revision, preserve history | Partial result incident; independent egress intact | Existing idempotent revision/compensation workflow | Original/new snapshot and financial/access refs |

#### access_provisioning

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| not_requested → requested/pending | Access worker / service scope | Confirmed booking with sufficient verified allocated payment | No PIN release while payment unverified; accepted session window and tested lock capability | Durable credential install; protected instruction delivery when eligible | Bounded before-arrival retries; expiry prevents stale install | Same credential/command version, reconcile before retry | Booking/allocation/credential/command refs, no PIN |
| pending → installed/failed/unknown | Authenticated adapter / device scope | Actual acknowledgement/status lookup or safe failure | Never infer installed from dispatch or bank approval | Access-ready only with verified install outcome; payment remains verified on failure | Incident/escalation if unresolved; no payment-failed fiction | Bounded retry/status reconciliation | Device evidence, observation time and incident |
| installed/active → expired/revoked | Access schedule/authority / existing scope | Purchased session end/cancel/security decision | Keep purchased validity, independent egress; no bank review override | Deny new entry safely; actual revocation acknowledgement tracked | Offline revoke remains unknown until evidence | Idempotent command/version; no replay of expired action | Reason/window and actual device result |

#### bank_refund

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| resolution → requested | Customer/finance / own request or payments.refund | Verified unallocated/excess or eligible allocation/refund-policy snapshot | Refundable balance, currency and legitimate recipient; no card token assumed | Separate refund case; bank receipt/ledger retained | Policy review deadline open | Idempotent request and balance reservation | Reason, funds/booking refs and requester |
| requested → approved/rejected | Reviewer / payments.refund.approve | Destination check, entitlement/amount/currency and review | Approval threshold/account verification policy selected | Approval only, no refunded flag | Escalate review delay; preserve reason/history | Version conflict check; append decision | Approver, amount/destination verification ref |
| approved → initiated → sent | Finance operator / bank.refund.execute | Authorized outgoing transfer instruction then bank-side sending evidence | Locked refundable amount; independent controls if policy requires | Persist outgoing operation/reference; neither approval nor initiation proves final transfer | Unknown send outcome → bank reconciliation before resend | Same operation ID; prevent duplicate outgoing refund | Executor/approval/outgoing transaction/time refs |
| sent/unknown → confirmed/failed | Reconciler / bank.refund.reconcile | Receiving/outgoing bank settlement evidence appropriate to selected bank | Supported verified completion/failure; do not invent connectivity | Final outgoing ledger fact or safe retry/incident; failure retains obligation | Bounded reconciliation and escalation | Bank lookup before retry, no blind repeat transfer | Confirmed/failedAt, bank reference and reconciliation evidence |

### Future implementation acceptance (not run in this task)

| ID | Scenario | Evidence needed |
|---|---|---|
| BP-AC01 | Successful bank approval | Own request, independent receiving credit, full allocation/current valid inventory/eligibility → one confirmed snapshot, separate acknowledged access |
| BP-AC02 | Partial/excess/wrong funds | Persist partial/excess credit, cap allocation, reject wrong currency/account proof, no full-paid/extra revenue fiction |
| BP-AC03 | Duplicate receipt/credit/approval | Hashes do not authorize; bank-side dedupe, repeated operation and simultaneous reviewers allocate once |
| BP-AC04 | Cross-user/scope denial | Own upload/preview and reviewer venue/permission gates; assignment does not grant verify/account/refund power |
| BP-AC05 | Mixed gateway/bank | Record both sources, one reservation/quote satisfaction, excess resolution; no forged webhook/card token |
| BP-AC06 | Stale package rates/expiry | Accepted quote/version/fee/instruction retained; clear accepted re-quote after expiry; no latest rate substitution |
| BP-AC07 | Expired hold/operator delay | No displacement, timely compliance evidence separate from reviewAt; available restoration/agreed alternative/refund, no forfeit |
| BP-AC08 | Malicious/corrected evidence | True type/size/quarantine/safe preview, correction history/late claims; OCR alone never approval |
| BP-AC09 | Instruction/account changes | Version old requests; independent changes and controlled verified emergency communication; no live account overwritten |
| BP-AC10 | Notification failure | Claim stays in queue, bounded retry/escalation and accurate submitted/under-review labels; no sensitive attachment |
| BP-AC11 | Access failure/safety | Verified payment remains, incident/retry, no PIN until eligible confirmed provisioning; egress and original-ID checks intact |
| BP-AC12 | Refund/correction | Destination/outgoing proof, no completed refund from approval, unknown send reconciles before retry, append correction audit |
| BP-AC13 | Historical reports | Declared amounts excluded, credits counted once, allocations/excess separated, timestamps/quote snapshots/recognition basis correct |

### Open decisions and source limits
OD-23–29 in [decision register](ARCHITECTURE.md#open-decisions) track lead/payment/submission/review/hold validity, time basis, fee treatment, upload/retention, matching/allocation/top-ups, roles/account/refund thresholds and operational-delay staffing. All owners TBD. Suggested 24 hours and gateway hold candidates are not bank defaults. Original PDFs missing and not rewritten; no banking/provider/legal claim inferred. Core USER-CONFIRMED does not approve every supporting proposal or imply implementation. [ADR-004](ARCHITECTURE.md#adr-004) records the gateway-only wording correction and separate financial state semantics.

<a id="mvp-scope"></a>
## MVP scope and customer demonstration responsibilities

S17 requests phased delivery planning, not product execution authority. [PROGRESS P0–P10](PROGRESS.md#mvp-roadmap) owns74 planning criteria, one delivery-home projection for all92 canonical requirements and links to their existing test/task IDs. Preserve27 features and detailed business/package/bank rules above. Approved core remains hybrid visuals, editable packages and bank-review capability; other target functionality retains its existing draft/recommended status. Loyalty/referrals/promotions and advanced engagement remain visible/UNDECIDED under OD-08, not silently excluded. Approved-deferred scope: none recorded. A proposed one-venue/representative-room pilot is not scope reduction or evidence multi-venue isolation is unnecessary.

Customer outcomes progress from discovery/versioned offers through valid inventory/quotes, eligibility, independently verified money, amendments/refunds, timed access, safe physical turnaround, truthful reporting and supported handover. [D0–D6](PROGRESS.md#customer-demonstrations) reviews happen during development, with held and accepted scope recorded independently. Product/owner and customer reviewers must be assigned for each scoped acceptance; finance and qualified physical/safety reviewers remain separate responsibilities, all named people TBD. No invitation, agreed meeting date, staffing commitment or completed demonstration is established. Planned tests/design artifacts cannot meet acceptance until actual evidence exists.

<a id="room-operating-decisions"></a>
### Approved operating and content direction — OD-04 / OD-10 / OD-14

Owner approval recorded 2026-10-16 from the current instruction; exact original questions and prior statuses are preserved in the [decision registry](decisions/cinenest-open-decisions.json). These three policy decisions are APPROVED; implementation and physical/legal acceptance remain NOT_VERIFIED. No CN-ROOM-001 implementation authority is granted.

- **OD-04:** MVP operations are staff-supported. Responsible personnel perform or supervise required check-in, physical cleaning, room-readiness confirmation, maintenance intervention, incident and emergency response. Do not claim fully staffless operation. A timer, door event or device acknowledgement never certifies an empty, clean or safe room. Emergency egress is independent of application, payment, network and credentials. Detailed staffing levels, responder drills and physical acceptance remain separate gates.
- **OD-10:** Customer sessions end by configured venue closing unless an explicitly approved exception applies. Cleaning/reset may extend after closing only when an approved staffing/operational process supports it; that interval is never sellable viewing time. Customer start/end, cleaning buffer and inventory blocked-until remain distinct. Interpret boundaries in venue-local time. No illustrative operating hours or buffer duration is approved as a default; special-day policies remain unresolved. Without the required post-close approval, configurations must not imply permission for post-close operation.
- **OD-14:** Restrict media/content offerings until commercial rights and provider terms are independently verified. Neutral physical capabilities (projector/display, sound, inputs, equipment/console compatibility and approved facilities) may be described. Do not advertise unverified screening/streaming/gaming-content entitlements, or treat consumer subscriptions as commercial exhibition rights. Independent licensing/legal review remains a separate acceptance gate; this is not legal certification.

The [bounded room implementation proposal](ARCHITECTURE.md#room-scope-proposal) remains PROPOSED/BLOCKED for separate owner approval. These approvals select policy direction, not physical acceptance, production defaults, package content rights or operational staffing commitments.


Historical pre-approval wording below is retained for source provenance only; the approved operating decisions above supersede its unresolved-policy statements.

> | BR-01 | LKR; Asia/Colombo; operating window 09:00–24:00 local | End is next-day midnight; customer session must end by closing under this draft. Cleaning beyond closing remains OD-10. |
> `session_end = session_start + purchased_duration` and `blocked_until = session_end + cleaning_buffer`. Reservation blocking interval is **[session_start,blocked_until)**; guest access interval is **[session_start,session_end)**. Distinguish the two in APIs, calendars, UI and device scheduling. Back-to-back session beginning exactly blocked_until is permissible only when readiness is cleared. Operating checks use venue-local calendar boundaries; storage uses UTC instants with original timezone/config version. Cleaning ending after midnight needs OD-10 resolution.

### Bounded room implementation evidence

CN-ROOM-001 implements only the approved catalog/configuration portion of CN-C01/CN-A03/CN-A04; [technical boundary](ARCHITECTURE.md#room-implemented-boundary) and [evidence](PROGRESS.md#room-implementation). No real photography, prices, availability, reservations, package publication or physical readiness is inferred. Full existing-reservation-impact acceptance for CN-A03 remains future; no requirement or later task is dropped. OD-04/10/14 policies are unchanged; physical, operational and licensing acceptance remain separate.

<a id="package-bounded-approval"></a>
## Owner-approved CN-PKG-001 policies — 2026-10-16

Current owner instruction and attachment approve the preceding conversation proposal and explicitly authorize only CN-PKG-001. Exact request SHA-256 and scoped decision payloads are retained in existing decision/task registries. OD-17/18/21/22 remain globally OPEN_DECISION; boundedPackageApproval satisfies only this task's draft gates. No source PDF verification, published commercial terms or whole future plan approval is inferred.

- OD-17: LKR scale2, integer minor units/checked overflow, reject excess decimal precision rather than rounding; positive base and nonnegative supplementary charges; positive integer durations, whole-unit extension-only model/no proration. Unpublished draft increases/decreases allowed. Zero-base packages, discounts, tax, convenience fees, refund calculations and final payable quotations remain unresolved. No illustrative selling amounts seeded. Previews show known package components and separate unselected optional rates; no payable total.
- OD-18: package.read and package.edit; edit implies same-venue read/preview. Create/edit/duplicate require edit; read/preview/history/options require the applicable read/edit grant. Initially one venue per draft; no cross-venue copying/shared administration, grant stitching or admin bypass. Current eligibility/validity, optimistic revisions and attributable audit required. No publication approval needed for saving an unsellable draft. Final role bundles, publish/deactivate authority, published-price thresholds and two-person publication review remain open.
- OD-21: Owner explicitly approves initial configurable minimum 180 customer minutes, not missing-PDF evidence. Rooms owns immutable approved policy revisions/effective time/provenance. Missing policy blocks validated save/preview; included duration must satisfy current minimum. Package editing cannot alter policy. Changes require revalidation, future publication rechecks it, and future quote/purchase workflows preserve their terms. No global engine or policy administration API. Controlled initial bootstrap may be explicitly configured; no implicit venue seed or live production operation is authorized.
- OD-22: Pricing owns shared service definitions/immutable rate references. Each package service is INCLUDED or OPTIONAL; included never creates a second charge. Fixed per-session optional units; no arbitrary package overrides/shared-master administration/fee or tax engine. Room compatibility comes from Rooms configuration, not live availability/content rights. No approved definitions means no selectable extras. Broader shared-fee, final quotation and publication decisions remain their existing gates.

[Implemented boundary](ARCHITECTURE.md#package-draft-boundary) and [actual evidence](PROGRESS.md#package-draft-implementation) cover only drafts. Core package editability is demonstrated without per-edit code release; full PR-PKG-001/CN-A12/PK-AC01 customer publication and later purchase scenarios remain future. No approved requirement is removed or silently deferred.

<a id="publication-approved-policy"></a>
## Approved bounded publication policies

OD-17: Indicative-only LKR exact components, unresolved tax/fees disclosed, purchasable=false; no zero-base/discounts/overrides; sales deferred
OD-18: Separate venue publish/deactivate; independent exact-revision author/publisher; content/scope/dates/hash/reason bound; current authority rechecked including activation; no edit implication or grant stitching
OD-19: Immutable single-venue versions, frozen room set; separate half-open sales/service intervals; venue-local conversion rejects DST gaps/ambiguity; reject intersecting room/sales overlap; explicit atomic replacement; durable fail-closed activation/recovery; quote honor deferred
OD-21: Rooms current/known future effective policies authoritative; initial180 unchanged; publication/activation/catalog revalidation; persist invalidation; restoration requires new publication approval; no policy API
OD-22: Freeze shared selected rate/display/mode/currency/basis/amount; INCLUDED zero additional; OPTIONAL displayed separately; current service/room compatibility; no auto-upgrade or fee engine
Owner authorization SHA256 f27c9494bea4951f6d8c66abe001f01d0813d5bc82556a114109880aeb5b18a2. Earlier draft approvals preserved; all wider decisions remain open. Publication means indicative catalog, not commercial sale. Full purchase/quote acceptance deferred to owning endpoints.

<a id="inventory-approved-policy"></a>
## Owner-approved bounded inventory policies

CN-BOOK-001 owner approval is retained at [PROGRESS](PROGRESS.md#local-development-maintenance) and explicitly reconfirmed for implementation in the current conversation. OD-09's inventory subset approves configurable initial **600 seconds (10 minutes)**, expiry at the earlier of creation plus configured TTL and customer session start, using fresh PostgreSQL time after locks. Browser countdowns, retries, logout and abandonment never extend a hold. An idempotency replay returns the original hold, including terminal state; another hold requires an explicit new request/key. No bank deadline/provider timing is selected.

OD-21's Booking subset uses Rooms-owned current and known future effective minimum policy throughout the intended session. Missing or incompatible policy fails closed. Unpaid holds are revalidated on reads, commands and reconciliation; expiry or invalidity releases the inventory block and records an attributable transition atomically. Never rewrite the accepted duration or cleaning buffer silently. Broader minimum-policy administration/precedence remains unresolved. OD-10 applies unchanged: customer time ends by venue closing; post-close cleaning requires approved staffing/process evidence; missing configuration fails closed. Future nonoverlapping inventory is not certification of physical readiness.

Only eligible associated synthetic/test accounts may hold their own inventory in this slice, at most one ACTIVE hold per account. Explicit server-configured create-rate limits and provenance are required; absent configuration disables creation. Staff inspection requires an applicable inventory.read grant at the target venue; other permissions do not imply that grant. No production admission, financial terms or role-administration policy is approved.

<a id="quotation-approved-policy"></a>
## Approved synthetic quotation policy — CN-QUOTE-001

Owner instruction 2026-10-16 and attachment 51bdfb3e-a05e-4f12-acb7-59f4ec9d5bd5 approve only bounded synthetic OD-17/20/22/25 policies (existing decision registry retains provenance and wider OPEN decisions). LKR integer minor units, positive base/nonnegative supplements, whole extension units; no proration, rounding, discount or package override. Frozen published prices/service-rate selections; INCLUDED zero extra, OPTIONAL PER_SESSION quantity0/1. Versioned explicit zero CineNest-added BANK/GATEWAY fee is independent of external provider costs; method changes require a new reviewed revision. Missing applicable policy fails closed.

Only explicitly labelled isolated development/test tax fixtures are permitted; no Sri Lankan commercial tax rate/exemption/applicability is approved. Real commercial issuance, real customer acceptance, checkout, payment and booking confirmation remain disabled. Synthetic quote acceptance is not a purchase or booking. Expiry never exceeds original hold expiry; no renewal/latest-price substitution. Ordinary catalog edits/withdrawal preserve issued snapshots; operational invalidity and expiry deny unsafe acceptance. Wider tax, payment/provider timing, refund and commercial acceptance gates remain open. [Technical boundary](ARCHITECTURE.md#quotation-boundary).

<a id="synthetic-verification-policy"></a>
## Bounded booker verification approval

Owner 2026-10-17 approves OD-03/05 only for CN-VERIFY-001 synthetic manual review. Initial minimum booker and attendee age18 is a business rule, not independently verified law. Digital submission belongs to the quotation owner only; no delegation, transfer, Google/OCR approval or automatic age exception. Human decisions require attributable reasons; corrections retain history and a changed booker requires new verification. Rejection is distinct from account suspension. All-guest original documents, staffing and physical admission remain separate gates. Real-person processing, production retention/provider/region and qualified legal/privacy acceptance remain unresolved. [Technical boundary](ARCHITECTURE.md#verification-boundary); [bounded evidence](PROGRESS.md#verification-implementation). Full OCR assistance remains planned, disabled in this slice.

<a id="synthetic-bank-request-policy"></a>
## Approved synthetic bank request policy — CN-PAY-BANK-001

Owner approval 2026-10-17 permits internal dry-run requests using current accepted synthetic BANK quotations and current synthetic digital verification. Zero CineNest-added synthetic BANK surcharge stays explicit; external costs and commercial fees remain unresolved. Real instructions, receipts, payment collection, independent credits, allocations, confirmation and refunds are excluded. A claim/receipt/review is not proof of money.

Distinct configured test request/initiation/submission/review/inventory/escalation deadlines and venue-local review windows have no production defaults. Only server evidence-finalization establishes receipt-submission timeliness; an empty claim or customer-declared payment time cannot establish it. Reuploads/corrections/review delay never renew inventory. Late claims remain reviewable with actual expired/released inventory shown. No displacement or silent forfeiture. Physical admission remains separate.

Synthetic receipts are built-in visibly watermarked JPEG/PNG fixtures only, two per submission,5MiB each,20MP decoded. PDF/real receipts fail closed. Synthetic private retention/deletion, simulated scanning and local encrypted storage do not qualify production financial privacy/scanning/backup obligations. Customer ownership and current exact venue review/evidence/escalation grants apply; no self-review, administrator bypass or grant stitching. Reviewer actions cannot verify funds, change receiving accounts, allocate, refund or confirm bookings. Full bank requirements and BP-AC01–13 remain unaccepted. Technical contract: [approved boundary](ARCHITECTURE.md#bank-request-boundary).

<a id="synthetic-bank-ledger-policy"></a>
## Approved bounded synthetic financial policy

Owner attachment 5030b7c9-38bf-4189-9705-39caebae3640 approves CN-PAY-BANK-002 only. Independent synthetic creditedAt determines credit timeliness; declared/submitted/reviewed/verified/allocated/confirmed clocks remain separate. Request-bound accepted terms survive original quote TTL only within valid request and inventory; delays/late discovery never restore inventory. Multiple verified top-ups may fund one request; each credit funds one request, allocations capped to credit and accepted outstanding, excess/unmatched/disputed facts retained. Independent verifier/allocator and correction approval require current scoped synthetic authority. Durable exceptions retain funds after confirmation failure. Real banking, commercial terms/timing, staffing/SLA/compensation/refunds remain open and disabled. [Technical scope](ARCHITECTURE.md#bank-ledger-boundary); no full requirement/phase/customer acceptance inferred.

<a id="manual-payment-first"></a>
## Owner-approved manual-payment-first direction

Approval source: owner's explicit instruction dated 2026-10-17, “I approve the phased manual-payment-first direction.” This is a roadmap/execution-priority decision only. Initial intended commercial payments are manual bank transfer/deposit with independent staff verification; hosted online gateway development is deferred to a later stage. The full MVP and all gateway requirements, research and prior bounded decisions remain retained. Existing synthetic bank software is not qualified for real commercial use.

Approved development direction: (1) source and accountability readiness; (2) commercial manual-payment readiness; (3) manual-payment booking and customer services; (4) physical operations and customer acceptance; (5) hosted online payment gateway integration. This priority overlay does not replace P0-P10 or D0-D6, reduce acceptance scope, authorize implementation or change task dependencies. [Execution interpretation](PROGRESS.md#manual-first-phases) and [dependency controls](ARCHITECTURE.md#manual-first-dependency-impact).

A real manual payment path requires approved receiving-bank instructions, customer claim/private evidence, independent incoming-bank-credit verification, authorized allocation, guarded booking confirmation, financial reconciliation and accountable late/unmatched-funds handling. Receipt reviewed, funds verified, funds allocated and booking confirmed remain separate facts. A receipt upload never establishes payment success. Money evidence survives failed booking confirmation; expired inventory is not silently revived. Existing financial separation, refund obligations and independent emergency-egress requirements remain intact. This direction enables no real collection, commercial issuance, production deployment, live delivery or physical operation.
