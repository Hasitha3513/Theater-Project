# CineNest Project

Canonical product knowledge: overview, scope, feature IDs, business rules and acceptance requirements. Update this file in place; new knowledge files need explicit owner approval. [Architecture](ARCHITECTURE.md), [design](DESIGN.md), [progress and handoff](PROGRESS.md).

CineNest is the Smart Automated Private Cinema and IoT Management Platform. Confirmed core: approved hybrid customer/admin visuals; authorized admin-editable package details/duration/prices without per-edit deployment; bank deposit/receipt upload/notified independent verification alongside gateway payments. Supporting defaults/policies and implementation remain draft/unverified. All 27 feature IDs, BR-01–10, PK-AC01–13 and BP-AC01–13 are retained below. Machine catalog/rules/lifecycles and execution registries remain structured specifications, not a deployed application. No requirements are dropped by consolidation.

## Contents

- [Features](#features)
- [Business Rules](#business-rules)
- [Package Management](#package-management)
- [Bank Payments](#bank-payments)

<a id="features"></a>
<!-- migrated-begin:knowledge/product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md -->
### CineNest Product Scope and Feature Catalog

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.

#### Baseline and canonical links
No application manifests or implemented modules exist at the inspected HEAD. All workflows below are desired behavior, with Implementation NOT_VERIFIED. IDs are newly allocated because no feature IDs existed in the baseline. Source S1 and detailed evidence status are recorded in [traceability](ARCHITECTURE.md#open-decisions). Rules refer to [canonical business defaults/lifecycles](PROJECT.md#business-rules); no duplicated prices/policies here. Ownership means proposed module, not verified tables/classes. [UI page specs](DESIGN.md#admin-pages) and [customer specs](DESIGN.md#customer-pages) remain canonical layouts.

#### CN-C01 — Discovery

| Field | Specification |
|---|---|
| Purpose | Discover cinematic venue/rooms with photos, amenities, capacity and indicative prices |
| Actors | Customer |
| Workflow | Browse catalog → inspect room → choose |
| Business rules | BR-01/03; truthful rights/amenities |
| Permissions | Public catalog read |
| Owning module | Venues/Rooms |
| UI pages | Landing Page; Cinema Room Selection |
| Integrations | Private asset delivery/CMS candidate |
| Failure states | Catalog/photo unavailable |
| Acceptance criteria | Usable catalog without auth; no fabricated availability or production sample price |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C02 — Identity, profile and consent

| Field | Specification |
|---|---|
| Purpose | Google sign-in, profile and secure session with explicit versioned policy acceptance |
| Actors | Customer; support |
| Workflow | OIDC → server session → profile → consent record |
| Business rules | BR-08/10; auth does not equal consent/verification |
| Permissions | Self profile; restricted account support |
| Owning module | Identity & Access |
| UI pages | Google Authentication; Customer Dashboard |
| Integrations | Google OIDC candidate |
| Failure states | Callback/session/consent failure |
| Acceptance criteria | Owner-scoped session/profile; explicit policy version/time persisted |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C03 — Availability and temporary holds

| Field | Specification |
|---|---|
| Purpose | Choose room/date/time with conflict protection |
| Actors | Customer; booking agent |
| Workflow | Query slots → create expiring hold → checkout |
| Business rules | BR-01/02/06/07; database interval guard |
| Permissions | Owner hold; booking.create |
| Owning module | Booking |
| UI pages | Date and Time Selection |
| Integrations | PostgreSQL; Redis recommended |
| Failure states | Expired hold; concurrent slot conflict |
| Acceptance criteria | Two conflicting reservations cannot both commit; server expiresAt shown |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C04 — Quotation and add-ons

| Field | Specification |
|---|---|
| Purpose | Authoritative guest/duration/PS4 itemization |
| Actors | Customer; pricing manager |
| Workflow | Choose guests/extras → server quote → accept version |
| Business rules | BR-02/03/04/05/10 |
| Permissions | Owner quote; pricing.write separate |
| Owning module | Pricing |
| UI pages | Booking Summary |
| Integrations | Booking availability |
| Failure states | Price change; capacity/extension conflict |
| Acceptance criteria | Base/PS4 examples correct; fifth guest charged once; final total server generated |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C05 — Booker verification

| Field | Specification |
|---|---|
| Purpose | NIC/passport upload, OCR assistance, age policy and manual exceptions |
| Actors | Booker; verifier |
| Workflow | Consent → private upload → extract → validate/review → decide |
| Business rules | BR-08; all-guests process OD-03 |
| Permissions | Self submission; identity.review/evidence.read separate |
| Owning module | Verification |
| UI pages | Identity Verification; Customer Management |
| Integrations | OCR/private storage candidates |
| Failure states | Unreadable upload; ambiguous/rejected/pending |
| Acceptance criteria | OCR cannot alone prove authenticity; restricted decision/evidence access |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C06 — Hosted payment and confirmation

| Field | Specification |
|---|---|
| Purpose | Hosted checkout, receipts and authoritative confirmation |
| Actors | Customer; finance |
| Workflow | Attempt → hosted provider → verified notification → reserve/confirm |
| Business rules | BR-05/10; webhook and inventory guard |
| Permissions | Owner pay/read; payments.read |
| Owning module | Payments |
| UI pages | Payment Processing; Booking Confirmation |
| Integrations | PayHere or bank IPG candidates |
| Failure states | Pending/failure/late success/duplicate notification |
| Acceptance criteria | Redirect never grants success; duplicate events have one effect; receipt from ledger |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C07 — Protected access and delivery

| Field | Specification |
|---|---|
| Purpose | Provisioning status, time-bound PIN and SMS/email access instructions |
| Actors | Customer; access operator |
| Workflow | Confirm → install → acknowledge → deliver protected access → admit |
| Business rules | BR-09; paid != installed |
| Permissions | Owner reveal; access.manage restricted |
| Owning module | Access Control; Notifications |
| UI pages | Smart Door Access PIN; Booking Confirmation |
| Integrations | Lock API; SMS/email candidates |
| Failure states | Install/delivery/access failure |
| Acceptance criteria | No access-ready state before install evidence; delivery failure yields support |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C08 — Self-service booking history

| Field | Specification |
|---|---|
| Purpose | Upcoming/past records, receipt and permitted changes |
| Actors | Customer |
| Workflow | Owner list → detail → policy-checked modify/extend/cancel/refund |
| Business rules | BR-02/06/10; refund snapshot |
| Permissions | Self booking; separate refund eligibility |
| Owning module | Booking |
| UI pages | Customer Dashboard; Booking History |
| Integrations | Pricing/Payments/Access |
| Failure states | Expired session; conflict; pending refund |
| Acceptance criteria | No cross-customer reads; retain history and original terms |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-C09 — Referral and loyalty

| Field | Specification |
|---|---|
| Purpose | Proposed earning, expiry, redemption and referral rewards |
| Actors | Customer; engagement manager |
| Workflow | Qualify → pending/earned → redeem/expire/reverse |
| Business rules | OD-08; rules not selected |
| Permissions | Self ledger; loyalty.adjust restricted |
| Owning module | Customer Engagement |
| UI pages | Loyalty and Referrals; Promotions and Loyalty |
| Integrations | Booking/payment ledger |
| Failure states | Ineligible referral; reversal; unavailable rules |
| Acceptance criteria | No invented earn rate; reversals audited; unavailable feature not promoted |
| Source | S1 |
| Decision | OPEN_DECISION |
| Implementation | NOT_VERIFIED |

#### CN-A01 — Operational dashboard

| Field | Specification |
|---|---|
| Purpose | KPIs, charts, live rooms, activity and system health |
| Actors | Admin; operator; finance |
| Workflow | Select scope/range → authorized aggregates → drill-down |
| Business rules | Metric definitions; live vs physical occupancy separate |
| Permissions | dashboard.read plus finance/room scope |
| Owning module | Analytics |
| UI pages | Dashboard |
| Integrations | Module read projections |
| Failure states | Stale/partial metrics |
| Acceptance criteria | All card labels include basis/asOf; no unavailable-to-zero fallback |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A02 — Booking administration

| Field | Specification |
|---|---|
| Purpose | Search/filter/page/detail/manual creation/change/cancel/extend |
| Actors | Booking agent; operator |
| Workflow | Find booking → permitted action → validate/quote → audited result |
| Business rules | BR-01–10; agreed revision and policy snapshots |
| Permissions | booking.read/create/update/cancel/export |
| Owning module | Booking |
| UI pages | Booking Management |
| Integrations | Pricing/Verification/Payments/Access |
| Failure states | Concurrent edits; expiry; late payment |
| Acceptance criteria | Stable paging; permissions checked server-side; no silent price rewriting |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A03 — Venue and room configuration

| Field | Specification |
|---|---|
| Purpose | Amenities/photos/hours/maintenance/enable/disable/device mapping |
| Actors | Room manager |
| Workflow | Configure version → impact review → effective update |
| Business rules | BR-01/03/06; booked purchases preserved |
| Permissions | room.read/write; device assignment distinct |
| Owning module | Venues/Rooms |
| UI pages | Cinema Room Management; System Settings |
| Integrations | IoT inventory/private asset storage |
| Failure states | Unsafe disable; conflicting schedules |
| Acceptance criteria | Existing reservation impacts shown; enabled does not mean clean/healthy |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A04 — Equivalent room views

| Field | Specification |
|---|---|
| Purpose | Card/table views of same rooms and permitted actions |
| Actors | Room manager; operator |
| Workflow | Load scoped dataset → switch view |
| Business rules | One shared query, status map and permissions |
| Permissions | room.read; action capabilities independent |
| Owning module | Venues/Rooms |
| UI pages | Cinema Room Management |
| Integrations | Same room projection |
| Failure states | Empty/filter/no-permission states |
| Acceptance criteria | Both views return same IDs/states and preserve filters |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A05 — Live control and incidents

| Field | Specification |
|---|---|
| Purpose | Health, acknowledged controls, timers and incident handling |
| Actors | Operator; responder |
| Workflow | Inspect freshness → safe command → ack → observe/escalate |
| Business rules | Safety guards; no inference of empty room from exit/motion |
| Permissions | room.control/device.command; override separate |
| Owning module | IoT; Access Control |
| UI pages | Room Live Control |
| Integrations | ESP32/MQTT/vendor adapters |
| Failure states | Unknown outcome; offline; overstay |
| Acceptance criteria | Requested != observed; no unsafe egress/power action |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A06 — Account and verification review

| Field | Specification |
|---|---|
| Purpose | Separate account/verification statuses and restricted evidence review |
| Actors | Support; verifier |
| Workflow | Find owner → permitted profile/review → reasoned decision |
| Business rules | BR-08/10; minimal evidence exposure |
| Permissions | customer.read/write; identity.review; evidence.read |
| Owning module | Identity & Access; Verification |
| UI pages | Customer Management |
| Integrations | OIDC/storage/OCR candidate |
| Failure states | Denied evidence; expired decision |
| Acceptance criteria | Suspended != rejected; raw documents never in customer list |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A07 — Finance operations

| Field | Specification |
|---|---|
| Purpose | Transactions, fees, refunds, reconciliation and scoped exports |
| Actors | Finance; auditor |
| Workflow | Ledger → eligibility → refund/reconcile → verified result |
| Business rules | Booked refund snapshot; ledger event dedupe |
| Permissions | payments.read/refund/export distinct |
| Owning module | Payments |
| UI pages | Payments and Refunds |
| Integrations | Hosted provider/bank IPG candidate |
| Failure states | Partial/failed/unknown refund; duplicate capture |
| Acceptance criteria | Refund balance enforced; reconciliation discoverable; no card-data collection |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A08 — Analytics and exports

| Field | Specification |
|---|---|
| Purpose | Reports and financial/nonfinancial chart drill-down |
| Actors | Analyst; finance |
| Workflow | Scope/query → definitions → aggregate → export |
| Business rules | Canonical units/population/time basis |
| Permissions | analytics.read; finance and export separate |
| Owning module | Analytics |
| UI pages | Analytics and Reports |
| Integrations | Authorized read models |
| Failure states | Partial data; mixed currency; undefined recognition |
| Acceptance criteria | Export matches visible filters and metric IDs; separate scales |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A09 — Engagement administration

| Field | Specification |
|---|---|
| Purpose | Promotions, loyalty/referrals and audited adjustments |
| Actors | Campaign manager |
| Workflow | Version draft → approve rules → publish → ledger reversal |
| Business rules | OD-08; stacking/caps/expiry undecided |
| Permissions | promotion.read/write; loyalty.adjust |
| Owning module | Customer Engagement |
| UI pages | Promotions and Loyalty |
| Integrations | Pricing/booking facts |
| Failure states | Duplicate code; caps; invalid reversal |
| Acceptance criteria | Versioned eligibility; no retroactive rule changes to earned rewards without approved process |
| Source | S1 |
| Decision | OPEN_DECISION |
| Implementation | NOT_VERIFIED |

#### CN-A10 — Policy and notification settings

| Field | Specification |
|---|---|
| Purpose | Versioned pricing/policies/templates/integration settings |
| Actors | Policy manager; admin |
| Workflow | Draft → review impact → effective version |
| Business rules | BR-10; secrets write-only; consent versions |
| Permissions | settings.read/write; secrets/manage-access separate |
| Owning module | Pricing; Notifications; Identity & Access |
| UI pages | System Settings |
| Integrations | Selected providers only |
| Failure states | Concurrent update; invalid provider; failed test |
| Acceptance criteria | Prior purchases/consent retained; no privilege escalation |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A11 — Audit visibility

| Field | Specification |
|---|---|
| Purpose | Scoped immutable operational evidence |
| Actors | Auditor; restricted admin |
| Workflow | Filter → redacted event detail → export |
| Business rules | No credentials/raw ID/card data; retention OD-05 |
| Permissions | audit.read/export |
| Owning module | Audit |
| UI pages | Audit Logs |
| Integrations | All module audit envelopes |
| Failure states | Delayed ingestion; denied/retention boundary |
| Acceptance criteria | Actor/resource/reason/correlation recorded; no edit/delete UX |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-O01 — Access credential lifecycle

| Field | Specification |
|---|---|
| Purpose | Install/activate/expire/revoke and supported re-entry |
| Actors | Access service; operator; customer |
| Workflow | Confirmed purchase → credential version → device ack → timed entry |
| Business rules | BR-09; egress independent; OD-02 |
| Permissions | Service scope; owner reveal; access.manage |
| Owning module | Access Control |
| UI pages | Smart Door Access PIN; Room Live Control |
| Integrations | Tested TTLock/Tuya-compatible hardware candidates |
| Failure states | Offline install/revoke; clock drift; lock failure |
| Acceptance criteria | Valid only session interval; re-entry tested, not presumed |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-O02 — ESP32 and room automation

| Field | Specification |
|---|---|
| Purpose | MQTT lighting scenes, AC and supported projector control |
| Actors | Operator; IoT service |
| Workflow | Register device → capability map → authorized command → ack/telemetry |
| Business rules | TLS; bounded setpoints; safety circuits excluded |
| Permissions | device.manage/command |
| Owning module | IoT |
| UI pages | Room Live Control; IoT Device Management |
| Integrations | ESP32; EMQX or Mosquitto candidates |
| Failure states | Firmware mismatch; stale telemetry; command expiry |
| Acceptance criteria | Only capability-supported operations; per-device auth and expiring commands |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-O03 — End-of-session operations

| Field | Specification |
|---|---|
| Purpose | Five-minute warning, checkout/exit recording, cleaning and overstay escalation |
| Actors | Scheduler; customer; staff |
| Workflow | Warn → session ends → safe expiry → cleaning task → staff clearance |
| Business rules | BR-06/09; exit event != all persons exited |
| Permissions | Worker scope; incident/cleaning staff |
| Owning module | Booking; Notifications; Venues/Rooms |
| UI pages | Customer Dashboard; Room Live Control |
| Integrations | SMS/email; optional sensors |
| Failure states | Missed warning; no cleaner; suspected overstay |
| Acceptance criteria | No physical-cleaning claim from timer; accountable intervention path |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-O04 — Connectivity and emergency recovery

| Field | Specification |
|---|---|
| Purpose | Monitoring, tested offline entry, bounded retries/recovery and intervention |
| Actors | Operator; responder |
| Workflow | Detect stale → safe degraded state → reconcile → tested fallback |
| Business rules | OD-02/04; cloud unlock is not offline access |
| Permissions | Health read; recovery/override restricted |
| Owning module | IoT; Access Control |
| UI pages | IoT Device Management; Room Live Control |
| Integrations | Lock/controller/broker connectivity |
| Failure states | Power/cloud/broker/vendor loss |
| Acceptance criteria | Unknown not OFF/empty; independent egress and trained response verified before launch |
| Source | S1 |
| Decision | DOCUMENTED_DRAFT |
| Implementation | NOT_VERIFIED |

#### CN-A12 — Admin-editable package details duration and prices

| Field | Specification |
|---|---|
| Requirement ID | PR-PKG-001 |
| Purpose | Authorized administrators can modify package details, session duration, and prices through the Admin Panel, without editing application code or requiring a deployment for each configuration change. |
| Actors | Authorized package administrator; publisher/reviewer roles proposed |
| Workflow | Edit core package settings via Admin Panel; supporting draft/preview/versioned publish/schedule/deactivate/archive flow RECOMMENDED |
| Business rules | Canonical package specification; separate operational minimum, physical capacity and cleaning; immutable sold terms and accepted quotes |
| Permissions | Proposed package.read/edit/publish/deactivate/archive independently within authorized venue scope |
| Owning module | Pricing (existing proposed owner); Booking owns purchased snapshots |
| UI pages | System Settings → Packages & Pricing (proposed subsection, no verified route); customer package/quote/confirmation views |
| Integrations | Venues/Rooms constraints; Identity & Access; Booking; Payments; Audit; no device command from catalog edit |
| Failure states | Invalid fields/constraints; denied scope; stale draft; overlapping versions; expired quote/hold; partial amendment outcome |
| Acceptance criteria | PK-AC01–13 in canonical package specification; no code/deploy per authorized edit, sold/valid-quote protections |
| Decision | APPROVED |
| Supporting design | RECOMMENDED |
| Implementation | NOT_VERIFIED |
| Source | [S8](sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt), explicit user confirmation |

Canonical definitions: [package management](PROJECT.md#package-management). Core APPROVED is limited to PR-PKG-001; broader policy/notification settings CN-A10 and quote/add-on behavior CN-C04 retain their existing statuses.

#### CN-C10 — Bank deposit transfer and receipt claim

| Field | Specification |
|---|---|
| Requirement | PR-BANK-001 |
| Purpose | Customer bank deposit/transfer and receipt submission with confirmation after authorized independent verification; retain online gateway option |
| Actors | Customer; authorized financial reviewer |
| Workflow | Accepted quote/request and provisional hold → evidence claim → admin review → verified funds/allocation → eligible confirmation → access workflow |
| Business rules | PR-BANK-001; receipt not verified; verified not confirmed/access-ready; method policy/fee/deadline open; purchased terms retained |
| Permissions | Own bank request/claim read/submit; restricted review/verify/allocate; server venue/owner scope |
| Owner | Payments; Booking; Notifications |
| UI | Payment Processing; Customer Dashboard/Booking History; protected claim upload/status |
| Integrations | Manual receiving-bank record matching recommended; private receipt storage; no bank API support claimed |
| Failures | Under/excess/wrong funds; unmatched/late claim; expired inventory; notification/access failure |
| Acceptance | BP-AC01–13; independent bank credit and guarded one-booking result; not tests run |
| Decision | APPROVED |
| Provenance | USER-CONFIRMED |
| Supporting design | RECOMMENDED |
| Implementation | NOT_VERIFIED |
| Source | S9, explicit user feature request |

Canonical [bank payment specification](PROJECT.md#bank-payments); supporting method policies remain open.


#### CN-A13 — Bank payment review and reviewer notification

| Field | Specification |
|---|---|
| Requirement | PR-BANK-001 |
| Purpose | Notify scoped admins of review-required bank claims and enable independent receiving-account verification/approval |
| Actors | Authorized reviewer/verifier/allocator; bank settings and refund staff separately authorized |
| Workflow | Durable review queue/alert → inspect restricted evidence and actual bank record → record credit → guard allocation/booking or resolution |
| Business rules | Bank credit fact retained even if service unavailable; no duplicate allocation/booking; adjustment/refund separate audited evidence |
| Permissions | bank.review/evidence.read/verify/allocate/resolve distinct from bank.account.manage and refund execute/approve |
| Owner | Payments; Booking; Notifications; Audit |
| UI | Payments → Bank Deposit Reviews (proposed subsection) |
| Integrations | Manual statement matching recommended; existing gateway unchanged; durable outbox/access interfaces |
| Failures | Concurrent reviewers; reused receipt/transaction; unmatched bank credit; overdue review; suspicious correction; failed alerts |
| Acceptance | BP-AC01–13; alert failure retains queue; approval alone no completed booking/access/refund |
| Decision | APPROVED |
| Provenance | USER-CONFIRMED |
| Supporting design | RECOMMENDED |
| Implementation | NOT_VERIFIED |
| Source | S9, explicit user feature request |

Canonical [bank payment specification](PROJECT.md#bank-payments); supporting method policies remain open.

<!-- migrated-end:knowledge/product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md -->

<a id="business-rules"></a>
<!-- migrated-begin:knowledge/product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md -->
### CineNest Business Rules and Lifecycles

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.


#### Canonical initial draft defaults
[Rule specification](product/cinenest-draft-rules.json) is machine-readable documentation, never a runtime configuration change. All initial values originate in S1 and are reaffirmed/classified by S8, not unseen PDFs. These are configurable initial defaults, not immutable application values; package content and rates are distinct from operational constraints. Backend configuration must be versioned and approved before launch. Open items: [decision register](ARCHITECTURE.md#open-decisions).

| ID | Draft rule | Constraints |
|---|---|---|
| BR-01 | LKR; Asia/Colombo; operating window 09:00–24:00 local | End is next-day midnight; customer session must end by closing under this draft. Cleaning beyond closing remains OD-10. |
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

#### Inventory, time and concurrency
`session_end = session_start + purchased_duration` and `blocked_until = session_end + cleaning_buffer`. Reservation blocking interval is **[session_start,blocked_until)**; guest access interval is **[session_start,session_end)**. Distinguish the two in APIs, calendars, UI and device scheduling. Back-to-back session beginning exactly blocked_until is permissible only when readiness is cleared. Operating checks use venue-local calendar boundaries; storage uses UTC instants with original timezone/config version. Cleaning ending after midnight needs OD-10 resolution.

Recommended PostgreSQL exclusion constraint or equivalent database-enforced conflict guard on room plus active blocking range; transaction serialization/locking validates holds, booking writes, extensions and reschedules together. Redis is an optional temporary hold/cache mechanism, never the sole overlap guarantee. If Redis fails, fail safe or fall back to authoritative database-backed holds; do not bypass reservation checks. Hold owner, expiry, quote version and consumed flag checked atomically. A hold is not a confirmed booking.

#### Edge workflows
- Expired hold: mark expired, release inventory, refresh availability/quote. Do not extend from browser countdown alone.
- Late gateway capture: deduplicate verified provider event; recheck reservation under database guard. Confirm only if inventory/policy remains valid; otherwise payment exception and compensating refund/support. External capture is not undone by database rollback.
- Duplicate/out-of-order webhook: durable inbox keyed by provider event/transaction; preserve facts, version-aware state projection, reconcile provider status; do not move captured to failed on a late earlier event.
- Retry: reuse logical operation idempotency key; provider lookup before another charge/refund/access write. Unknown outcome is not safe failure.
- Reschedule: quote delta/terms, lock old/new scopes in deterministic order, atomically swap reservation; retain original history and agreed policy. If external delta fails, retain original booking or documented compensating path. No silent release before new slot secured.
- Extend: applicable configured whole increment (initially 60 minutes), accepted amendment delta quote, check next booking plus buffer/closing/readiness; replace reservation range atomically. Capture delta then update access only through durable workflow; payment success does not guarantee PIN end-time updated. Reconcile any partial outcome.
- Cancel/refund: authorization and policy snapshot eligibility, cancel inventory and revoke access safely; refund remains separate asynchronous state until verified successful. No automatic card re-billing without separately approved consent/provider capability (OD-07).

#### Proposed lifecycle transitions
All tables are desired behavior, not implemented states. State changes are versioned and server authorized. Timeouts are policy-configured (not invented numbers); each requires a durable audit event containing actor, resource, reason, prior/new state and correlation ID without secrets. Trigger codes below are reused in contracts. Account active/suspended is separate from verification.

##### Booking
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

##### Payment (attempt state and refund ledger separate)
These attempt/capture transitions describe gateway payment only. Manual receipt/funds/request/refund transitions are separately canonical in the bank specification; a bank credit is not a card capture.
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| created → pending | PROVIDER_CHECKOUT; valid quote/hold | Payments service | Hosted session/reference | Provider expiry; reconcile | Attempt/idempotency/provider refs |
| pending → authorized/captured | VERIFIED_PROVIDER_EVENT; signature, amount, currency and booking match | Webhook/reconciliation service | Persist capture ledger; booking event | Poll authoritative status on ambiguity | Event dedupe key, ledger ref |
| pending → failed/cancelled/unknown | VERIFIED_FAILURE/CANCEL or timeout | Provider adapter / worker | UI status; no success | Unknown → reconcile before retry | Safe reason and last observation |
| unknown/authorized → captured/failed | PROVIDER_RECONCILED; verified outcome | Reconciliation service | Exactly-once ledger effect | Escalate bounded failures | Reconciliation ref |
| refund requested → pending → succeeded/failed/unknown | REFUND_REQUESTED/VERIFIED_RESULT; refundable balance, policy | payments.refund actor / worker | Separate refund ledger; captured facts retained | Provider lookup/idempotent retry; escalate | Refund amount/policy/event refs |

##### Verification
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_submitted → submitted | DOCUMENT_UPLOADED; consent, allowed type/size | Booker | Private upload ref | Upload expiry/retry | Evidence ref, consent version |
| submitted → processing | OCR_REQUESTED; private validated input | Verification worker | OCR candidate facts | Provider timeout → review | Job/version/ref, no raw text in event |
| processing → review_required | AMBIGUOUS_RESULT; unreadable/uncertain/exception | Worker | Restricted review task | Staff SLA TBD, support | Reason code |
| processing/review_required → verified/rejected | VERIFICATION_DECIDED; approved eligibility process, not OCR alone | Authorized verifier | Minimal decision, expiry/scope | Appeal/reupload per policy | Reviewer, basis, policy version |
| verified → expired/revoked | VALIDITY_ENDED/REVIEW_REVOKED | Policy worker / restricted verifier | Block future eligible checkout | Reverify; existing session impact assessed | Reason/scope/time |

##### Access provisioning
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_requested → requested → pending | BOOKING_CONFIRMED; tested lock capability | Access service | Private credential + durable install command | Bounded retries before session; incident | Booking/credential ref, no PIN |
| pending → installed | INSTALL_ACKNOWLEDGED; correct lock/version/window | Device/vendor adapter | Provisioning success, eligible delivery | Unverified ack → lookup | Command/credential version |
| pending → failed/unknown | INSTALL_FAILURE/TIMEOUT | Adapter / worker | Support incident; no access-ready claim | Reconcile before reprovision | Safe error/last attempt |
| installed → active | WINDOW_STARTED; valid session and policy | Lock/local schedule / service | Permit entry per tested device behavior | Clock/offline risk OD-02 | Configured window + observation |
| active → expired/revoked | WINDOW_ENDED/CANCEL/SECURITY_REVOCATION | Lock schedule / access authority | Deny new entry; preserve independent egress | Offline revocation reconciliation | Reason, ack/unknown state |

##### Session
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| scheduled → active | SESSION_START; booking/window | Session worker | Approved scenes; status | Command failure does not falsify observed state | Booking/time/command refs |
| active → ending | FIVE_MINUTE_WARNING; five minutes before session_end | Scheduler | Idempotent notice | Late job says accurate remaining time | Scheduled vs delivered time |
| ending/active → ended | SESSION_END; server schedule | Scheduler | Expire entry, checkout prompt, cleaning task | No forced trap/power cut | End/revocation/task refs |
| active/ending → active (revision) | EXTENSION_CONFIRMED; range/delta/access reconciled | Booking/session services | New end, reschedule notices | Partial outcomes → incident | Revision and access result |
| ended → overstay_incident | OVERSTAY_SUSPECTED; evidence/operational check | Operator or monitoring service | Escalation to accountable staff | Never infer all occupants exited | Evidence type and intervention |

##### Room readiness
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| ready → reserved/in_session | RESERVATION/SESSION_START; schedule + cleared readiness | Booking/session service | Availability projection | Projection lag → refresh | Reservation/session IDs |
| in_session → cleaning_required | SESSION_END; readiness not presumed | Session service | Physical cleaning work item | Staffing SLA OD-04 | End/task refs |
| cleaning_required → cleaning → ready | CLEANING_STARTED/CLEARANCE; authorized physical checklist | Operations staff | Clearance evidence; enable booking admission | Buffer elapsed alone cannot clear | Staff/checklist/completedAt |
| any → maintenance/unknown | FAULT/STALE_CLEARANCE | Operator/health service | Block unsafe readiness | Inspection/reconciliation | Reason/observation |
| maintenance/unknown → ready | INSPECTION_CLEARED; no conflict, physical assurance | Authorized operator | Updated safe readiness | Escalate failed checks | Inspection result |

##### Device command
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| requested → pending | COMMAND_ACCEPTED; permission/capability/safety/expiry | device.command actor / worker | Durable publish with command ID | Bounded retries | Requested action/resource/actor |
| pending → acknowledged | DEVICE_ACK; correct command ID/version | Authenticated device adapter | Receipt acknowledgement only | Wait observed state if required | Device ack/observedAt |
| pending → failed/expired/unknown | DEVICE_NACK/TTL_END/OUTCOME_UNCERTAIN | Adapter / timeout worker | Error/incident, no invented OFF state | Query state before retry | Safe code/expiry/lastSeen |
| acknowledged → observed result recorded | TELEMETRY_CONFIRMED; matching expected state | Telemetry adapter | Separate observed state field | Missing telemetry remains unknown | Observation/correlation refs |
| unknown → acknowledged/failed | STATUS_RECONCILED; authoritative response | Worker/operator | Reconcile original command | Expired command never replayed | Original command + reconciliation |

See [IoT safety](ARCHITECTURE.md#iot-access) and [data contracts](ARCHITECTURE.md#data-api) for durable integration and failure ownership.

#### Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
[Package management](PROJECT.md#package-management) is canonical for PR-PKG-001/CN-A12. Initial defaults do not prevent authorized future package-duration/price/content changes. Global/venue minimum-duration policy, hours, cleaning and physical capacity remain separate operational constraints; a two-hour package requires separately authorized minimum and customer-terms changes. Shared Pricing add-on/fee definitions are referenced, not duplicated per package. No pro-rata discount or new tax treatment inferred.

Payment/eligibility revalidation checks the accepted quote version, verified amount/currency and current reservation eligibility, not a newly published catalog rate. Valid quote honor is RECOMMENDED, expired quote refresh needs acceptance; arrival time of a delayed webhook is separate from verified capture timing. [Package quote/amendment protections](PROJECT.md#package-management) qualify the existing late-payment and lifecycle recovery rules. Deactivation does not cancel purchased bookings; changing live catalog cannot rewrite snapshots, cleaning intervals or access credentials. Source PDFs remain missing and unchanged.

#### Bank payment — CN-KB-BANK-PAYMENT-001
[Bank deposit/manual payment](PROJECT.md#bank-payments) owns PR-BANK-001/CN-C10/CN-A13. Core USER-CONFIRMED / APPROVED; supporting flow RECOMMENDED, implementation NOT_VERIFIED. Receipt submission, verified bank fund, allocation/satisfaction, inventory/booking, access and bank refund states are distinct; on-site original-ID entry checks remain separate. Payment verification accepts authenticated supported gateway result OR authorized receiving-bank reconciliation, not screenshot/OCR. Sufficient allocated payment plus current inventory/pre-confirmation eligibility gates one booking.

Bank request/hold uses durable shared inventory with session/cleaning/maintenance constraints and method-specific lead/payment/submission/review/hold/quote policies. Do not reuse gateway short-hold candidate or assume 24-hour minimum; no numeric bank default selected. Reuploads do not restart deadlines. Hold expires independently of price validity; timely compliance/operator delay requires accountable restoration/alternative/refund handling, never displacement/forfeit. Verify accepted quote/currency, not newest package. Underpayment, excess, late received funds and dual-method payments retain real ledger facts independently of booking resolution. Bank-method convenience fee is OD-25, not automatic LKR 100/waiver; no card token/damage re-billing assumed. All existing operational/package/safety constraints remain.

<!-- migrated-end:knowledge/product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md -->

<a id="package-management"></a>
<!-- migrated-begin:knowledge/product/CINENEST-PACKAGE-MANAGEMENT.md -->
### CineNest Package Management

Task CN-KB-PACKAGE-CONFIGURATION-001 · 2026-10-14 (Asia/Colombo). Source [S8 user request](sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt). Core decision APPROVED (user-confirmed requirement); supporting design RECOMMENDED unless explicitly marked OPEN_DECISION. Implementation NOT_VERIFIED: no application manifest, package admin, API, database or execution test exists in inspected repository.

#### Requirement and scope
**PR-PKG-001 / feature CN-A12:** “Authorized administrators can modify package details, session duration, and prices through the Admin Panel, without editing application code or requiring a deployment for each configuration change.”

This confirms the product capability, not a selected implementation, tested workflow, provider approval or legal authorization. Proposed listing/create/duplicate/edit/preview/publish/schedule/deactivate/archive mechanics below support that capability. Canonical ownership stays in existing proposed Pricing module; no second pricing module, generic rules engine, automatic demand pricing or implied unrelated promotion scope. Historical [ADR-001](DESIGN.md#adr-001)/[ADR-002](ARCHITECTURE.md#adr-002) remain; [ADR-003](ARCHITECTURE.md#adr-003) records this update.

#### Actors and permissions (proposed capability labels)
| Actor | Capability and scope | Separation |
|---|---|---|
| Package viewer | package.read in authorized venue scope | Read drafts/history only if permitted; customer sees eligible published catalog |
| Package editor | package.edit; create/duplicate/edit draft in allowed scope | Edit does not imply publication or policy/physical-capacity modification |
| Publisher | package.publish; explicit allowed venue/room scope | Validates complete revision, schedule and policy before atomic publication |
| Archive/deactivation operator | package.archive / package.deactivate | Must preserve sold versions and respect current checkout policy |
| Auditor | audit.read within scope | Redacted old/new values/reason/version/effective dates; cannot mutate history |
| Operations/policy owner | Separate policy and room configuration capabilities | Controls approved minimum, hours, cleaning and physical occupancy, never through package-edit privilege |

Exact role names, capability allocation, self-publication and two-person approval thresholds remain OD-18. Backend authorization on every read/write/publish and scope mapping; hiding controls is insufficient. Customer only accesses applicable versions and own quotes/purchases.

#### Fields and joint validation
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

#### Ownership and initial defaults
[Business rules](PROJECT.md#business-rules) / [draft JSON](product/cinenest-draft-rules.json) retain S1/S8-described initial defaults, not immutable code values. Original PDFs are missing; S8 reports a mandatory three-hour minimum, which is retained as a draft constraint pending final source/policy verification. No PDF rewritten or legal approval inferred.

| Initial default | Owner / constraint |
|---|---|
| Included package duration 180 minutes; base LKR 2,500 / up to 4 guests | Pricing package content/version settings |
| Minimum customer session 180 minutes | Separate global/venue operational minimum-duration policy; package cannot bypass |
| Extension increment 60 minutes; LKR 1,000 per increment | Pricing versioned extension model/reference |
| Fifth guest LKR 800 | Pricing guest-charge rule/reference; not occupancy permission |
| Current documented maximum occupancy 5 guests | Venues/Rooms approved physical capacity; individual room may be lower |
| Optional PS4 LKR 1,000 per session | Existing Pricing shared add-on definition; include/optional mode via versioned package reference |
| Online convenience fee LKR 100 | Existing Pricing shared fee definition, selected by channel/version |
| Post-session cleaning buffer 15 minutes | Separate Venues/Rooms operational requirements/policy; reservation retains buffer snapshot |
| Operating hours | Separate venue operational settings; see canonical rules |

A two-hour package is eligible only after a separately authorized minimum-duration policy and corresponding customer terms are updated. A package editor cannot lower the venue minimum or exceed physical capacity by changing included guests. The current capacity ceiling is not a configurable safety exception. Exact scope/precedence of global versus venue minimum changes and approval gates are OD-21.

#### Duration and server quotation model
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

#### Proposed administrative lifecycle
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

#### Effective versions, scopes and concurrent publishing
Stable packageId owns immutable published packageVersionId/revision and purchased display/inclusion/price terms. Any sold-term edit creates a new revision; publication never destructively edits a referenced revision. Store sales [effectiveFrom,effectiveTo) and separate optional session/service [validFrom,validTo) with explicit venue-local timezone (Asia/Colombo), resolved UTC instants and policy version. Sales time selects new purchase applicability; service-time eligibility independently checks scheduled session. Date-only end boundary resolves to next local midnight exclusive.

Recommended: prevent overlapping sales intervals for the same stable package and intersecting venue/room scope. Joint validation and transactional version token/uniqueness guard reject ambiguity rather than “latest timestamp wins.” A superseding schedule closes prior sales eligibility atomically at cutoff, preserving immutable sold terms and audited prior schedule. More specific room scope does not silently override venue scope; ambiguous mappings rejected until OD-19 chooses precedence. Recheck concurrent policy/room revisions on publish; cached catalog never authorizes an invalid quote.

#### Existing purchases and active checkout protections
Each confirmed booking stores package/version reference and purchased display details; included/selected duration, session_start/end and reserved cleaning-buffer snapshot; guest allowance and included/selected services; exact price line items, fees/discounts/final total/currency; accepted booking/refund-policy and consent versions; quote reference. Package name/description edits also cannot rewrite historical tickets/receipts/reports.

Catalog changes cannot silently alter existing bookings, receipts, historical duration/revenue reporting, inclusions or active PINs. Deactivation blocks new sales under defined cutoff; it does not cancel reservations. Sold versions remain referentially intact. Access reads booking/session snapshots, not live package duration.

Recommended quote policy: honor a still-valid authoritative quote with its package/fee/add-on/policy versions even if a new catalog version is published or package is deactivated. After quote expiry, return refreshed terms and require clear customer acceptance before a new attempt. Final policy for emergency withdrawal versus valid-quote honor is OD-20, not an automatic override. Quote/hold expire independently: a valid price quote cannot reserve unavailable inventory.

Payment confirmation validates **accepted quote** and expected amount/currency plus current reservation/eligibility and verified provider evidence; it never silently looks up a newly published rate. A callback arriving late is not proof of a late payment: compare verified capturedAt evidence with accepted quote validity and attempt linkage. Starting checkout in time is not evidence capture completed in time. Evidence requirements/authorization-versus-capture cutoff policy are OD-20. Valid in-time payment with delayed notification still requires safe inventory reconciliation; do not automatically reject solely because callback arrived after quote expiry.

Expired hold/late capture/conflict → durable reconciliation/payment exception and approved refund/support compensation; no duplicate reservation, retroactive reprice or unapproved charge. Dedupe callbacks/ledger by provider/operation ID; replay resolves original purchase references. Database rollback cannot undo external capture.

Purchased booking amendment is a separate authorized workflow: availability and capacity/readiness check → versioned delta quote and customer terms acceptance → payment/refund difference resolution → guarded reservation revision → access-window/notification coordination with durable retries/unknown outcomes. Do not derive amended terms automatically from latest package. Partial external outcomes require compensation/support while protecting previous purchase history. Package publication alone never sends access/IoT commands, extends a PIN, interrupts an active session or drops buffer/conflict guards.

#### Proposed architecture/API and audit
Pricing owns stable catalog identities, immutable versions, applicability schedule and quote construction. Booking owns reservation/confirmed snapshot; Venues/Rooms own operational constraints/capacity; Identity & Access handles capabilities; Payments validates accepted quote and ledger; Access uses purchased session; Audit preserves evidence. No final tables, API routes, migrations, module additions or installed stack claimed. [Contracts](ARCHITECTURE.md#data-api) defines proposed capabilities: list/detail/history, create/duplicate draft, update with version, preview, publish/schedule, deactivate/archive, resolve applicable version and quote.

Persist atomic publication/audit intent; invalidate scoped catalog caches/durable projections without per-edit code release. Request idempotency and concurrent version tokens protect mutation retry/publish schedules. Audit actor, authorized scope, timestamp, reason, old/new values, stable package/version IDs, effective boundaries and policy references; no PII/PIN/card data. Booking amendment has its own audit and agreed difference. Exact table/ETag/event designs RECOMMENDED, not approved core capability.

#### Future implementation acceptance (not tests run here)
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

#### Open decisions and provenance
[OD-17–OD-22](ARCHITECTURE.md#open-decisions) track proration/rounding/zero/taxes, price and two-person thresholds, scope/version precedence, quote/deactivation/capture timing, minimum-policy ownership and shared-fee/add-on overrides. S8 confirms core admin editability only; S1/S8 defaults remain initial draft values. Original PDFs missing and no production configuration exists. Documentation validation cannot establish these acceptance outcomes or hardware/legal approval.

#### Bank payment — CN-KB-BANK-PAYMENT-001
Package editability CN-A12/PR-PKG-001 and existing approved/no-release requirement are unchanged. [Bank payment](PROJECT.md#bank-payments) snapshots package/version/display/session/buffer/guests/inclusions/extras/item lines/consent/policy/quote and receiving-instruction version into request. Bank quotes preserve applicable explicitly approved method fee treatment; the online LKR 100 initial example does not decide bank fee (OD-25).

The earlier capturedAt/timing discussion applies to gateway. Manual timeliness distinguishes declared initiated time, actual bank credit, submission and authorized verification; selected authoritative clock is OD-24, not implicitly review time. Accepted quote/currency remains protected from catalog changes while valid; expiry/re-quote acceptance and independent received-fund traceability follow the method policy. Bank review/verification never modifies packages, cancels sold terms, auto-amends bookings or extends PINs. New bank-funded purchased amendments still use the existing separate guarded quote/payment/refund/access workflow, without assuming a stored card.

<!-- migrated-end:knowledge/product/CINENEST-PACKAGE-MANAGEMENT.md -->

<a id="bank-payments"></a>
<!-- migrated-begin:knowledge/product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md -->
### CineNest Bank Deposit and Manual Payment

Task CN-KB-BANK-PAYMENT-001 · 2026-10-14 (Asia/Colombo). Source [S9 user request](sources/CN-KB-BANK-PAYMENT-001-request.txt). Provenance **USER-CONFIRMED**; core Decision **APPROVED**, Implementation **NOT_VERIFIED**. Supporting controls/workflows/data design **RECOMMENDED**; numeric deadlines, fee/config/provider/operational choices **OPEN_DECISION**. No code, real account details/bank connectivity, transactions, money movement or review/hardware test evidenced.

#### Core requirement and scope
**PR-BANK-001 / CN-C10 and CN-A13:** Customers can deposit/transfer funds to the business bank account, upload payment evidence, and obtain confirmation after an authorized administrator independently verifies and approves payment; the system notifies authorized administrators when review is required. Existing online gateway payment remains available.

**RECEIPT UPLOADED is not PAYMENT VERIFIED. PAYMENT VERIFIED is not automatically BOOKING CONFIRMED or ACCESS READY.** Bank deposit/transfer is distinct from bank IPG online gateway; do not forge a webhook or record bank credit as card capture. A customer document is a payment claim, not a CineNest-issued receipt or proof funds arrived. Post-confirmation financial acknowledgement/receipt comes from verified ledger and allocation.

Recommended launch scope: full accepted quoted payment before confirmation, request bound to package/room/session/guests/extras; no installment booking, wallet, transferable credit, open-date voucher, subscription or multi-booking shared-deposit entitlement. Partial funds still recorded; additional top-up handling requires an explicit policy (OD-27), not an installment product. Third-party payer name may differ legitimately; collect adequate matching context, never reject solely for name difference.

#### Actors, permissions and configuration
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

#### Customer workflow
1. Choose package, room/session, guests/add-ons; backend resolves versioned quote, scope and pre-confirmation eligibility.
2. Select **Online Payment** (existing hosted gateway) or **Bank Deposit / Transfer**. Bank option only enabled after receiving instructions, manual-review capacity, fee and timing policy are approved/configured.
3. Create linked bank payment request plus bounded durable provisional inventory hold. Display request/reference, account-instruction version, exact accepted amount/currency/fee treatment, separate deadlines/review hours and actual hold status.
4. Customer deposits/transfers using instructions. Never request banking password, PIN, OTP or card security code.
5. Submit own receipt file and declared payment amount/date/time, bank reference if available, optional payer name and note. Declared facts remain distinct from independently verified credit; preserve each correction submission and timestamp.
6. Acknowledge submitted claim and durably queue reviewer notification/work. Show **Payment Under Review**, not Paid/Confirmed. Safe microcopy: “Receipt submitted for verification. Your booking is not yet confirmed. We will notify you after review. Please do not pay again.” Add “Your provisional hold expires at [date/time]” only when a valid hold actually exists.
7. Staff matches actual receiving-account credit, records verified fund fact and lawful allocation. If payment sufficient and reservation/eligibility valid, confirm one booking and queue notification/access provisioning. Otherwise **Funds Received / Booking Resolution Required**, never discard actual money.
8. Customer tracks separate review/payment/booking/access/refund outcomes and corrections/support. Do not invite payment against expired request; accept late evidence through reconciliation intake without promising inventory.

#### Proposed reviewer queue and actions
Location **Admin → Payments → Bank Deposit Reviews** (proposed subsection, no route invented). Queue columns: request/booking/customer, venue/room/session, package version, expected/declared amount/currency, submittedAt, review status/age, assignee, hold expiry, overdue/exception flag and notification status. Filters: scope/room/session date, review/hold status, assignee/overdue, safe request reference search; server pagination and privacy-scoped searches.

Detail: restricted safe preview, declared versus verified facts, accepted quote/item lines/policies/package, instruction version, full submission/correction history, receiving-bank match, verified/allocated/unallocated balance, current inventory/eligibility, timeline/audit and notification state. Controls **Verify Funds and Approve**, **Request More Information**, **Await Bank Credit**, **Reject Payment Claim** (reason), **Resolve Booking/Amount Exception**. “Verify Funds and Approve” is a composite UI action with independently persisted funds and guarded allocation/confirmation; display each result instead of conflating them. Server result controls pending/success/conflict/error. Rejection of claim does not delete funds or prevent later independent credit discovery.

#### Independent verification, matching and allocation
Require receiving-account identity and instruction version; bank-side transaction/reference (or controlled statement-entry identity), bank credit date/time, verified currency/amount, reviewer and reason/notes with evidence reference. Customer screenshot/email/OCR/provider notification alone cannot authorize credit verification. No current bank connection assumed; manual matching against the business’s own authoritative records is the launch recommendation. OCR can suggest fields only; bank feeds/APIs are future scope after official support/account/privacy evidence.

Recommended matching key: receiving-account identity + genuinely bank-assigned stable transaction ID where available. Otherwise staff records controlled statement identity/period plus unique statement-entry/line and verified credit details, with cross-statement reconciliation/deduplication and ambiguity review. Customer-entered reference is a matching hint, not a globally unique ledger key. Same receipt hash is a duplicate-risk signal, not evidence of payment or allocation protection.

Verified incoming fund fact persists once regardless of booking fulfillment. Allocation is a separate append-only record linked to fund/request/quote/currency. Lock/conditionally version fund, payment request, aggregate payment attempts and reservation; total allocated/refunded/reserved balance cannot exceed actual verified credit. Initial single-request allocation scope rejects claiming the same credit against another booking; any multi-booking shared allocation is a future approved extension, not default. Underpayment remains unallocated/partial satisfaction and outstanding due; overpayment allocates only quote due, remainder retained for controlled resolution. Incoming receipt verification and allocation are not two receipts of money.

For a composite approval: (A) persist independently verified unique fund fact and audit, even if inventory later conflicts; (B) in guarded local transaction dedupe allocation operation, check aggregate bank+gateway satisfaction, accepted quote/currency/eligibility and shared reservation inventory, append bounded allocation and confirm one booking if all guards pass; otherwise record payment/allocation/booking exception as appropriate. If enough funds but no inventory, keep verified funds separately and resolution case; never silently displace another booking. Durable outbox queues notices/access only after committed eligible confirmation. External failure cannot roll back/erase actual received funds. Repeated review actions and concurrent reviewers resolve same operation/version; image hashes are not concurrency guards.

Error/suspicion correction appends a reversal/adjustment of match/allocation with authorized actor/reason/original references. It does not delete bank credit or rewrite approved history; actual credit later reversed by bank is a separate verified bank reversal fact and linked resolution. Booking/access consequences are explicit safe incident/amendment actions, never occupant trapping.

#### Holds, quotes and deadlines (unapproved values)
Separate configurable settings: minimum session lead time; payment deadline; evidence-submission deadline; review service hours/deadline; provisional/review-hold expiry and bounded extensions; quote/payment-request validity; overdue/escalation thresholds. Exact values **OPEN_DECISION OD-23**; 24-hour lead time is only an earlier suggestion/example, not approved or selected. Do not copy gateway 10–15 minute candidates into bank flow, or create indefinite review holds.

Durable bank holds use the same database-enforced room/session+cleaning reservation ranges as gateway holds/confirmed bookings/maintenance. Redis may cache, never sole long-hold record. Timely submission may transition to policy-bounded review hold; repeated uploads/corrections do not reset clock automatically. Show separately quote validity, request validity and whether/through-when inventory is reserved. At approval recheck reservation atomically; expired hold never authorizes displacement. Manual review creates a real staffed service commitment, hours and escalation staffing OD-29.

Timestamps separate: payment_initiated_at (declared unless independently evidenced), bank_credited_at, evidence_submitted_at, verified_at, allocated_at and booking_confirmed_at. Which timestamp satisfies timeliness is **OD-24**, not implicitly verified_at or notification arrival. No quote repricing to newest catalog at approval. Valid accepted quote honor remains recommended with method-specific review/request validity policy; after expiry clear re-quote acceptance, received funds still traceable. Operator delay after timely customer compliance: restore slot if available under policy, customer-agreed alternative or appropriate approved refund if service unavailable; never silently forfeit or assume new terms. Hold extension/exceptions remain bounded and audited.

#### Package, eligibility and confirmation invariant
Request snapshots package/version/display/duration/session/buffer/guests/inclusions/extras/item lines/fee treatment/currency/policy/consent/quote validity and receiving-account instruction version. Protect [package terms](PROJECT.md#package-management); bank-method convenience fee requires OD-25 decision, neither automatically LKR 100 nor waived. No invented tax/bank surcharge. Show selected approved treatment before paying and preserve it.

**Payment is verified through either an authenticated supported gateway result or an authorized bank-reconciled manual-payment decision. Booking confirmation additionally requires sufficient allocated payment, valid reservation inventory, and applicable pre-confirmation eligibility.** Independent on-site original-ID checks remain a separate entry requirement; bank approval cannot bypass identity/age/consent/capacity/hours/physical-safety rules. Verified partial payment or unmatched funds do not satisfy full quote. No PIN release while payment unverified; confirmed purchase then uses existing acknowledged provisioning and purchased [session_start,session_end) validity. Provisioning failure is access pending/incident, not payment failed.

#### Money and service exception matrix
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

#### Security and instruction lifecycle
Authenticated owner/scoped uploads; proposed JPEG/PNG/PDF with approved size/retention limits **OD-26**. Validate true content type, generate private object keys, scan/quarantine and restrict PDF/image safe preview (no active document execution); approved storage/private encryption/short scoped signed links/access audit and deletion lifecycle. Receipt-upload requirements do not substitute for physical original-ID entry checks. Preserve original submissions for controlled review subject to approved retention, not infinite storage.

Keep raw receipt/statement/customer payer data, bank instructions/details, credentials and PINs out of public URLs, ordinary logs, event payloads and notification attachments. Notifications use authenticated links; instruction versions change only through separately authorized independently verified bank-account management. Evidence-view/review/verify/allocate/refund/account-change permissions distinct. Exact roles/approval limits independent review are recommendations or OD-28, not blanket Admin access. Audit account changes, evidence access, match/reject/approve/adjust/allocate/refund with actor/time/reason/scope/correlation and redacted before/after. No real bank setting changed here.

#### Notifications and reporting
Durable customer submission acknowledgement, correction/await-credit, verification outcome, booking outcome, expiry and refund progress. Durable admin new-review, overdue, unmatched-funds and access-failure alerts with scoped deep links; send retries/bounded failure escalations never delete queue work. A sent notification does not verify funds, clear claim, confirm booking or prove PIN installed. In-app review queue remains authoritative; no sensitive attachments or credentials.

[Bank reporting](DESIGN.md#charts-metrics) defines pending-review age/population, verified credit, booking allocation, unmatched/excess and refunds/channel breakdown. Never use uploaded/declared amounts as collections; verification+allocation is one incoming credit with two processing timestamps. Keep bank_credited_at, verified_at, allocated_at and session_start_at separate. Collections ≠ recognized service revenue. Historical package/duration reports read immutable purchase snapshots; LKR/Asia/Colombo and existing chart semantic colors/authorization unchanged.

#### Proposed architecture and acceptance
Within Payments (request/evidence/verified bank credit/allocation/review/refund/instruction metadata), Booking (durable hold/guarded confirmation/snapshot), Notifications, Access and Audit; no second booking system/microservice. Conceptual names/APIs/events are unimplemented. [Data/API contracts](ARCHITECTURE.md#data-api) detail safe evidence-source events and local atomic/durable boundaries. State-machine specification follows; all transitions are RECOMMENDED design and have actor/permission/evidence/guard/side effect/timeout/retry/audit. No numeric deadline approved.

##### receipt_review

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| none → submitted | Customer / payment.claim.submit, own request | Authenticated allowed upload and declared details; evidence submission timestamp | Valid own request or explicitly late-claim recovery; private scan/quarantine | Immutable submission revision; durable reviewer work/acknowledgement | Upload/submission deadline configurable; late claim retained, no hold reset | Retry same submission ID/idempotency; preserve history | Actor, request/submission refs, declared facts, correlation |
| submitted → review_required/quarantined | Upload worker / service scope | Actual file validation/scan outcome | File safe to preview; quarantined cannot be opened ordinarily | Safe preview or isolated quarantine; queue not dropped | Scan timeout pending/unknown with staff escalation | Bounded scan retry; no payment approval from scan | Scan/result/version refs; no raw image |
| review_required → reviewing | Reviewer / bank.review, venue scope | Assignment and current record version | Authorized reviewer, conditional version/assignment claim | Review ownership recorded; no fund allocation | Assignment timeout configurable; reassign without resetting customer deadline | Concurrent claims return conflict/current assignee | Reviewer/time/assignment history |
| reviewing → information_requested/awaiting_credit/rejected_claim | Reviewer / bank.review | Safe reason, receiving bank query if available | Insufficient matching/credit or suspicious/unreadable claim; rejection is not denial that money may later arrive | Customer correction/credit/rejection status and durable notice | Review/correction due times policy open; late funds trigger new reconciliation | New submissions retained; rejection reversible via auditable new decision | Reason/evidence/previous decision refs |
| reviewing/awaiting_credit → funds_matched | Reviewer / bank.verify | Independent receiving-account record and verified fund ID | Currency/credit identity and match sufficient, not screenshot/OCR; credited funds persisted separately | Link verified fund to claim; allocation/payment status checked separately | Review overdue escalates; no promised booking | Idempotent match; original bank fact retained | Bank match, reviewer, decision/version |
| information_requested/rejected_claim → review_required | Customer or reviewer / own submit or bank.review | New correction or independently discovered later credit | Preserve prior submissions/decisions; no deadline reset | Append history, reopen review/reconciliation | Late claim stays traceable even if inventory expired | Deduped reopen with new evidence ID | Reopen actor/reason/parent decision |

##### verified_funds_allocation

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| unmatched bank record → verified_unallocated | Reviewer / bank.verify | Receiving-account identity, bank-side reference/controlled statement entry, credit date, amount/currency, notes | Independent bank credit uniqueness/authentication within controlled process | Append immutable incoming bank fund ledger fact even if booking unavailable | No funds expiry; unresolved balance escalation policy open | Deduplicate bank-side identity, not customer ref/file hash | Verifier/evidence/credit identity and recordedAt |
| verified_unallocated → allocated/partially_allocated | Reviewer / bank.allocate (or approved verify command) | Accepted quote/request refs, sufficient outstanding balance | Lock fund/request; allocation ≤ available credit and due; initial no shared-deposit multi-booking scope | Allocation ledger append; payment satisfaction projection; excess remains unallocated | No silent forfeit; unresolved remainder tracked | Logical operation key + unique allocation constraints/versions | Fund/request/allocation refs, exact amounts and actor |
| allocated → adjusted/reversed allocation (new record) | Authorized correction reviewer / bank.adjust | Error/suspicion decision and linked original allocation | Approved correction process; no delete/overwrite bank receipt fact | Append compensating allocation entry; recalc satisfaction/booking incident, no egress trap | Incident/refund/recovery deadlines open | Idempotent correction ID; dual control threshold open | Old/new ledger refs, reason, reviewer, resulting incident |
| verified_unallocated/excess → refund_resolution | Finance / payments.refund or resolution scope | Verified balance, destination and approved resolution | No double allocation/refund; customer accepted resolution if alternative service | Link refund/resolution operation, funds remain traceable until completion | Escalation configured; never erase received money | Reconcile outgoing transfer before retry | Destination-check/result and ledger refs |

##### payment_request_satisfaction

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| draft → awaiting_payment | Customer / own bank.request.create | Versioned quote, receiving instruction version, scope/session/policy refs | Bank method enabled, lead time/deadlines approved, provisional inventory available | Immutable request snapshot/reference and durable bounded reservation hold | Separate payment/submission/quote/hold deadlines; no invented duration | Idempotent request creation | Quote/package/account instruction/hold IDs |
| awaiting_payment → under_review | Customer / payment.claim.submit | Submission revision timestamp | Timely under approved policy to qualify for bounded review hold; late goes resolution | Review work; hold condition displayed accurately, not Paid | Review service hours/deadline/hold bounded; repeated upload never extends automatically | Submission dedupe; do not request another payment blindly | SubmittedAt/hold transition and scope |
| under_review → satisfied/partially_satisfied | Reviewer / bank.verify + bank.allocate as permitted | Verified ledger allocations in accepted quote currency | Accepted quote/payment timing policy; total allocated capped at due; no latest-price lookup | Payment satisfaction only; booking transaction guard separately evaluated | Insufficient balance remains outstanding, no paid-in-full confirmation | Aggregate allocations idempotent; cross-method payment lock | Allocation/quote/verification refs and outstanding balance |
| awaiting_payment/under_review → expired/cancelled_resolution | Scheduler or owner / scoped request policy | Deadline/cancel facts | Expiry stops new payment invitation; verified credits retained; pending claims remain reviewable | Release hold when policy requires, resolution queue for late claims/funds | No indefinite extension; operator-delay escalation/open resolution | Dedupe expiry; recover with explicit re-quote/customer acceptance | Expiry/cancel/time and retained claim/ledger refs |
| expired/rejected resolution → funds_received_booking_resolution_required | Reviewer / bank.verify | Later verified actual credit | Request no longer eligible or quote/inventory conflict | Persist received funds; alternative/refund/support, not failed payment | Resolution SLA open; no silent forfeiture | Idempotent ledger and incident, no new booking automatically | Credit/exception/customer agreed resolution refs |

##### reservation_booking

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| available → provisional_bank_hold | Booking service / bank.request.create scope | Request/quote/config and room range including buffer | Database-enforced no conflict with gateway/manual holds, maintenance and confirmed bookings | Durable hold in same Booking inventory; no Redis-only guarantee | Exact hold policy OPEN_DECISION; bounded initial/review phases | Idempotent hold ID and conditional range writes | Range, expiry, policy, request refs |
| provisional_bank_hold → bounded_review_hold | Booking service / service scope | Timely submission and policy version | Current hold and policy permits review interval; repeated upload not reset | Controlled bounded extension/transition; show reserved vs not reserved | Review deadline/staffing gate; release on approved expiry | Version/transition key prevents repeated extensions | SubmittedAt/policy/old-new expiry |
| held → confirmed | Booking service / authorized verified payment decision | Sufficient allocated gateway/bank payment facts and accepted quote | Atomically validate inventory/current reservation, pre-confirmation identity/age/consent/capacity/hours; manual reviewer cannot bypass | One immutable booking snapshot; durable notifications/access work | Access result separate; unknown command/notice outcomes tracked | Request/booking identity and unique guarded confirmation | Evidence source, allocations, reservation/eligibility snapshot |
| expired hold + received funds → resolution, not displacement | Reviewer + Booking service / resolution scope | Verified credit, expired range and availability check | Never displace another confirmed booking; restoration only if available under policy | Customer-agreed alternate/refund if service unavailable; retain fund fact | Operator-caused delay after timely compliance escalates accountable staffing | Single resolution operation, explicit alternative/re-quote acceptance | Cause/timing/attempted restore/customer agreement |
| confirmed → cancelled/amended revision | Owner or booking agent / existing change permissions | Purchase policy + consent + delta quote and payment/refund facts | Existing amendment/cancel guards; bank method is no bypass | Durable allocation/refund/access revision, preserve history | Partial result incident; independent egress intact | Existing idempotent revision/compensation workflow | Original/new snapshot and financial/access refs |

##### access_provisioning

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| not_requested → requested/pending | Access worker / service scope | Confirmed booking with sufficient verified allocated payment | No PIN release while payment unverified; accepted session window and tested lock capability | Durable credential install; protected instruction delivery when eligible | Bounded before-arrival retries; expiry prevents stale install | Same credential/command version, reconcile before retry | Booking/allocation/credential/command refs, no PIN |
| pending → installed/failed/unknown | Authenticated adapter / device scope | Actual acknowledgement/status lookup or safe failure | Never infer installed from dispatch or bank approval | Access-ready only with verified install outcome; payment remains verified on failure | Incident/escalation if unresolved; no payment-failed fiction | Bounded retry/status reconciliation | Device evidence, observation time and incident |
| installed/active → expired/revoked | Access schedule/authority / existing scope | Purchased session end/cancel/security decision | Keep purchased validity, independent egress; no bank review override | Deny new entry safely; actual revocation acknowledgement tracked | Offline revoke remains unknown until evidence | Idempotent command/version; no replay of expired action | Reason/window and actual device result |

##### bank_refund

| Transition | Actor / permissions | Evidence | Guards | Side effects | Timeout | Retry | Audit |
|---|---|---|---|---|---|---|---|
| resolution → requested | Customer/finance / own request or payments.refund | Verified unallocated/excess or eligible allocation/refund-policy snapshot | Refundable balance, currency and legitimate recipient; no card token assumed | Separate refund case; bank receipt/ledger retained | Policy review deadline open | Idempotent request and balance reservation | Reason, funds/booking refs and requester |
| requested → approved/rejected | Reviewer / payments.refund.approve | Destination check, entitlement/amount/currency and review | Approval threshold/account verification policy selected | Approval only, no refunded flag | Escalate review delay; preserve reason/history | Version conflict check; append decision | Approver, amount/destination verification ref |
| approved → initiated → sent | Finance operator / bank.refund.execute | Authorized outgoing transfer instruction then bank-side sending evidence | Locked refundable amount; independent controls if policy requires | Persist outgoing operation/reference; neither approval nor initiation proves final transfer | Unknown send outcome → bank reconciliation before resend | Same operation ID; prevent duplicate outgoing refund | Executor/approval/outgoing transaction/time refs |
| sent/unknown → confirmed/failed | Reconciler / bank.refund.reconcile | Receiving/outgoing bank settlement evidence appropriate to selected bank | Supported verified completion/failure; do not invent connectivity | Final outgoing ledger fact or safe retry/incident; failure retains obligation | Bounded reconciliation and escalation | Bank lookup before retry, no blind repeat transfer | Confirmed/failedAt, bank reference and reconciliation evidence |

#### Future implementation acceptance (not run in this task)

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

#### Open decisions and source limits
OD-23–29 in [decision register](ARCHITECTURE.md#open-decisions) track lead/payment/submission/review/hold validity, time basis, fee treatment, upload/retention, matching/allocation/top-ups, roles/account/refund thresholds and operational-delay staffing. All owners TBD. Suggested 24 hours and gateway hold candidates are not bank defaults. Original PDFs missing and not rewritten; no banking/provider/legal claim inferred. Core USER-CONFIRMED does not approve every supporting proposal or imply implementation. [ADR-004](ARCHITECTURE.md#adr-004) records the gateway-only wording correction and separate financial state semantics.

<!-- migrated-end:knowledge/product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md -->

