# CineNest Product Scope and Feature Catalog

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.

## Baseline and canonical links
No application manifests or implemented modules exist at the inspected HEAD. All workflows below are desired behavior, with Implementation NOT_VERIFIED. IDs are newly allocated because no feature IDs existed in the baseline. Source S1 and detailed evidence status are recorded in [traceability](../decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md). Rules refer to [canonical business defaults/lifecycles](CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md); no duplicated prices/policies here. Ownership means proposed module, not verified tables/classes. [UI page specs](../CINENEST-ADMIN-PAGES.md) and [customer specs](../CINENEST-CUSTOMER-BOOKING-PAGES.md) remain canonical layouts.

## CN-C01 — Discovery

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

## CN-C02 — Identity, profile and consent

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

## CN-C03 — Availability and temporary holds

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

## CN-C04 — Quotation and add-ons

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

## CN-C05 — Booker verification

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

## CN-C06 — Hosted payment and confirmation

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

## CN-C07 — Protected access and delivery

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

## CN-C08 — Self-service booking history

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

## CN-C09 — Referral and loyalty

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

## CN-A01 — Operational dashboard

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

## CN-A02 — Booking administration

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

## CN-A03 — Venue and room configuration

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

## CN-A04 — Equivalent room views

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

## CN-A05 — Live control and incidents

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

## CN-A06 — Account and verification review

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

## CN-A07 — Finance operations

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

## CN-A08 — Analytics and exports

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

## CN-A09 — Engagement administration

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

## CN-A10 — Policy and notification settings

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

## CN-A11 — Audit visibility

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

## CN-O01 — Access credential lifecycle

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

## CN-O02 — ESP32 and room automation

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

## CN-O03 — End-of-session operations

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

## CN-O04 — Connectivity and emergency recovery

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

## CN-A12 — Admin-editable package details duration and prices

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
| Source | [S8](../sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt), explicit user confirmation |

Canonical definitions: [package management](CINENEST-PACKAGE-MANAGEMENT.md). Core APPROVED is limited to PR-PKG-001; broader policy/notification settings CN-A10 and quote/add-on behavior CN-C04 retain their existing statuses.

## CN-C10 — Bank deposit transfer and receipt claim

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

Canonical [bank payment specification](CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md); supporting method policies remain open.


## CN-A13 — Bank payment review and reviewer notification

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

Canonical [bank payment specification](CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md); supporting method policies remain open.
