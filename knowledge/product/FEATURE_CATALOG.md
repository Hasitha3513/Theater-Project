# CineNest implemented feature catalogue

Supplementary product documentation, authorized by CN-PRODUCT-DOCUMENTATION-001 on 2026-10-22. This is not a fifth canonical document or a replacement for the [requirement registry](../execution/requirements.json), [machine feature catalogue](cinenest-feature-catalog.json), [PROJECT](../PROJECT.md), [ARCHITECTURE](../ARCHITECTURE.md), [DESIGN](../DESIGN.md) or [PROGRESS](../PROGRESS.md). [Sinhala user guide](../guides/USER_GUIDE.md). Existing MVP obligations remain intact.

## Verification and classification

Initial records DOC-F001–026 were source-inspected at `24b73caf16eebda1490d0f3552c268d15e024229`; [exact-source CI 39807842759](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) COMPLETED / SUCCESS, attempt 1. That CI verifies the committed software, not physical/commercial acceptance. Existing evidence records include 686 backend and 403 frontend tests; this documentation task does not rerun those suites. Historical real-backend synthetic E2E 24 PASS is bounded campaign evidence, not proof of every current workflow or a new campaign execution.

Feature IDs `DOC-F001` onward are stable documentation identities, not new requirements or backlog IDs. Each record inherits this exact last-verified commit/CI receipt and operational classification: **synthetic/test software; production NOT AUTHORIZED, real banking/identity/admission NOT QUALIFIED**. Demo availability is conditional on an isolated synthetic environment, eligible session, independently authorized permissions and fixtures; no appointments or grants are supplied by this document. Never use real evidence or money.

Implementation status vocabulary: IMPLEMENTED (code exists), VERIFIED (bounded checks passed), PARTIAL (wider requirement remains incomplete), SIMULATED (implemented test behavior), DEFERRED (owner-deferred execution), BLOCKED (unmet prerequisite), NOT_IMPLEMENTED (no implemented capability). These are not formal requirement acceptance states. Records below use PARTIAL for wider MVP scope and SIMULATED for bounded test functions; verification evidence is identified independently.

<a id="doc-f001"></a>
## DOC-F001 — Account session

| Field | Evidence-backed value |
|---|---|
| Category / module | Customer Portal / Identity |
| Intended user | Customer / staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Inspect current backend session and explicitly log out of the current session. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/account` |
| Required permissions | authenticated eligible account; server remains authoritative |
| Known limitations | Production registration/provider onboarding is not supplied by this screen. |
| MVP / requirement / test plan | P1-C04 / CN-C02 / T-CN-C02 |
| Automated test evidence | [IdentityIntegrationTest](../../backend/src/test/java/com/cinenest/IdentityIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [session-panel.tsx](../../frontend/components/session-panel.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f002"></a>
## DOC-F002 — Room discovery

| Field | Evidence-backed value |
|---|---|
| Category / module | Cinema Rooms and Packages / Rooms |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Filter discoverable rooms by venue, name, capacity and facility; open exact room details. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/rooms` |
| Required permissions | eligible customer; server discovery policy; server remains authoritative |
| Known limitations | Illustrative images do not verify room identity or physical facilities. |
| MVP / requirement / test plan | P2-C04 / CN-C01 / T-CN-C01 |
| Automated test evidence | [RoomsIntegrationTest](../../backend/src/test/java/com/cinenest/RoomsIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [rooms.tsx](../../frontend/components/rooms.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/catalog.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f003"></a>
## DOC-F003 — Package discovery

| Field | Evidence-backed value |
|---|---|
| Category / module | Cinema Rooms and Packages / Pricing |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read published indicative package choices associated with a selected room. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/rooms/[roomId]` |
| Required permissions | eligible customer; published discovery policy; server remains authoritative |
| Known limitations | Indicative prices are not an accepted quotation; no standalone package route. |
| MVP / requirement / test plan | P2-C02 / CN-A12 / T-CN-A12 |
| Automated test evidence | [PublicationIntegrationTest](../../backend/src/test/java/com/cinenest/PublicationIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [packages.tsx](../../frontend/components/packages.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/catalog.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f004"></a>
## DOC-F004 — Availability calendar

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Availability / Booking / Inventory |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read one-room one-local-day minute-precision inventory; select an available candidate. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/rooms/[roomId]` |
| Required permissions | eligible customer; room visibility; server remains authoritative |
| Known limitations | Advisory only; PENDING_RECONCILIATION is unknown, not available. |
| MVP / requirement / test plan | P3-C03 / CN-C03 / T-CN-C03 |
| Automated test evidence | [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [customer-calendar.tsx](../../frontend/components/customer-calendar.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f005"></a>
## DOC-F005 — Explicit reservation hold

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Availability / Booking / Inventory |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Check an interval, explicitly create a temporary hold, refresh or release it. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/rooms/[roomId]` |
| Required permissions | eligible customer; owned hold; server remains authoritative |
| Known limitations | Server expiry/configuration/conflicts remain authoritative; no physical readiness. |
| MVP / requirement / test plan | P3-C03 / CN-C03 / T-CN-C03 |
| Automated test evidence | [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [inventory.tsx](../../frontend/components/inventory.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f006"></a>
## DOC-F006 — Quotation and acceptance

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Availability / Pricing |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Review itemized immutable quote revision and explicitly acknowledge disclosures and accept. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/rooms/[roomId]` |
| Required permissions | eligible customer; owned quote and hold; server remains authoritative |
| Known limitations | Changed/expired quotes require refreshed context; acceptance is not payment or booking confirmation. |
| MVP / requirement / test plan | P3-C01 / CN-C04 / T-CN-C04 |
| Automated test evidence | [QuoteIntegrationTest](../../backend/src/test/java/com/cinenest/QuoteIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [quotes.tsx](../../frontend/components/quotes.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f007"></a>
## DOC-F007 — Synthetic identity verification

| Field | Evidence-backed value |
|---|---|
| Category / module | Guest Registration and Verification / Verification |
| Intended user | Customer / authorized reviewer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Create consent-bound synthetic request, submit supported test evidence and inspect authoritative review state. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/verification` |
| Required permissions | ownership; staff verification.review; separate verification.evidence.read; server remains authoritative |
| Known limitations | No real identity documents, automatic age proof or physical admission. |
| MVP / requirement / test plan | P4-C03 / CN-C05 / T-CN-C05 |
| Automated test evidence | [VerificationIntegrationTest](../../backend/src/test/java/com/cinenest/VerificationIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [verification.tsx](../../frontend/components/verification.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f008"></a>
## DOC-F008 — Bank request and receipt claim

| Field | Evidence-backed value |
|---|---|
| Category / module | Payments and Refunds / Payments |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Continue from eligible verification, explicitly create a synthetic bank request, submit claim and receipt evidence. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/bank` |
| Required permissions | authenticated owner; protected readiness; server remains authoritative |
| Known limitations | Receipt submission is not independently verified credit; uncertain upload is not READY. |
| MVP / requirement / test plan | P4-C08 / CN-C10 / T-CN-C10 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [bank.tsx](../../frontend/components/bank.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f009"></a>
## DOC-F009 — Customer booking history and detail

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Availability / Booking |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read exact purchased terms, synthetic booking state and booking detail. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/bookings` |
| Required permissions | authenticated owner; server remains authoritative |
| Known limitations | Only SIMULATED_CONFIRMED is synthetic confirmation; pending allocation is not confirmation. |
| MVP / requirement / test plan | P6-C01 / CN-C08 / T-CN-C08 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [customer-bookings.tsx](../../frontend/components/customer-bookings.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f010"></a>
## DOC-F010 — Synthetic guest roster

| Field | Evidence-backed value |
|---|---|
| Category / module | Guest Registration and Verification / Booking |
| Intended user | Customer / authorized staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Explicitly register synthetic guests within purchased capacity; staff inspect exact roster. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/bookings/[id]` |
| Required permissions | ownership; staff guest.synthetic.read independently; server remains authoritative |
| Known limitations | Self-declaration proves neither age nor physical admission; no real identity evidence. |
| MVP / requirement / test plan | P4-C03 / BR-08 / T-BR-08 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [guest-roster.tsx](../../frontend/components/guest-roster.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f011"></a>
## DOC-F011 — Booking amendment

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Availability / Booking |
| Intended user | Customer; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Review and explicitly accept supported synthetic amendment proposals. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/bookings/[id]` |
| Required permissions | owned booking; current server eligibility; server remains authoritative |
| Known limitations | Bounded amendment support does not establish general cancellation or extension completeness. |
| MVP / requirement / test plan | P6-C01 / CN-C08 / T-CN-C08 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [booking-amendment.tsx](../../frontend/components/booking-amendment.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f012"></a>
## DOC-F012 — Access credential simulator

| Field | Evidence-backed value |
|---|---|
| Category / module | Cinema Access / Access |
| Intended user | Customer / authorized staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Inspect protected synthetic access state and perform only explicitly permitted simulator actions. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/access` |
| Required permissions | customer access.synthetic.* command grants; staff access.synthetic.review; server remains authoritative |
| Known limitations | No qualified lock, physical entry or emergency egress integration. Never share secrets/screenshots. |
| MVP / requirement / test plan | P6-C04 / CN-O01 / T-CN-O01 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [access.tsx](../../frontend/components/access.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f013"></a>
## DOC-F013 — Lighting simulator

| Field | Evidence-backed value |
|---|---|
| Category / module | IoT and Room Automation / IoT |
| Intended user | Authorized operations staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Inspect registered synthetic devices and explicitly exercise supported lighting command simulation. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/iot` |
| Required permissions | iot.synthetic.read; independent command/simulate/reconcile grants; server remains authoritative |
| Known limitations | ACK is not observed physical device state; no physical control. |
| MVP / requirement / test plan | P8-C01 / CN-O02 / T-CN-O02 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [iot.tsx](../../frontend/components/iot.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f014"></a>
## DOC-F014 — Staff booking administration

| Field | Evidence-backed value |
|---|---|
| Category / module | Admin Dashboard / Booking |
| Intended user | Reception / authorized staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Search/page selected-venue bookings and reload exact booking details. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/bookings` |
| Required permissions | booking.synthetic.read at exact venue; server remains authoritative |
| Known limitations | Access/refund/operations/guest sections each require independent permissions. |
| MVP / requirement / test plan | P6-C03 / CN-A02 / T-CN-A02 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [staff-bookings.tsx](../../frontend/components/staff-bookings.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f015"></a>
## DOC-F015 — Booking-linked operations read

| Field | Evidence-backed value |
|---|---|
| Category / module | Staff Operations / Operations |
| Intended user | Authorized operations staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read explicitly booking-linked cases, historical captured revisions and fixed-page hasNext. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/bookings/venues/[venueId]/[booking]` |
| Required permissions | operations.synthetic.read independently; server remains authoritative |
| Known limitations | Room-wide cases are not attributed to booking; empty list is not room readiness. |
| MVP / requirement / test plan | P8-C02 / CN-O03 / T-CN-O03 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [staff-booking-operations.tsx](../../frontend/components/staff-booking-operations.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f016"></a>
## DOC-F016 — Independent bank review

| Field | Evidence-backed value |
|---|---|
| Category / module | Payments and Refunds / Payments |
| Intended user | Finance staff with independent grants; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Review synthetic claims; independently verify synthetic credit and allocate only through permitted explicit commands. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/bank` |
| Required permissions | bank.claim.review; separate evidence/credit/allocate grants; server remains authoritative |
| Known limitations | No real bank integration; duties/grants are separate; evidence review is not credit verification. |
| MVP / requirement / test plan | P4-C08 / CN-A13 / T-CN-A13 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [bank.tsx](../../frontend/components/bank.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f017"></a>
## DOC-F017 — Synthetic refunds

| Field | Evidence-backed value |
|---|---|
| Category / module | Payments and Refunds / Payments |
| Intended user | Customer / independently authorized finance staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Inspect obligations and explicit supported review, approval and simulated repayment stages. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/refunds` |
| Required permissions | refund.synthetic.read/review/approve/destination.verify/execute/reconcile separately; server remains authoritative |
| Known limitations | APPROVED is not REPAID; simulated repayment is not real money movement. |
| MVP / requirement / test plan | P6-C05 / CN-A07 / T-CN-A07 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [refunds.tsx](../../frontend/components/refunds.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f018"></a>
## DOC-F018 — Synthetic bank reports and summary

| Field | Evidence-backed value |
|---|---|
| Category / module | Reports and Analytics / Reporting / Payments |
| Intended user | Authorized finance report reader; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read explicit venue/currency/date synthetic credit/allocation/refund reporting; summary also appears on /admin. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/reports/bank` |
| Required permissions | report.bank.synthetic.read; server remains authoritative |
| Known limitations | No qualified real balance, recognized revenue, export or financial acceptance. |
| MVP / requirement / test plan | P9-C02 / CN-M-BANK-PAYMENT-REVIEW-FINANCES / T-CN-M-BANK-PAYMENT-REVIEW-FINANCES |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [bank-report.tsx](../../frontend/components/bank-report.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/analytics.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f019"></a>
## DOC-F019 — Synthetic Booking Confirmations

| Field | Evidence-backed value |
|---|---|
| Category / module | Reports and Analytics / Reporting / Booking |
| Intended user | Authorized booking report reader; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Count persisted SIMULATED_CONFIRMED bookings by confirmed_at, daily venue-local buckets and current-room distribution. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin` |
| Required permissions | report.booking.synthetic.read; server remains authoritative |
| Known limitations | 31 local days maximum; latest revision room attribution; cancellation NOT_AVAILABLE; not session-start popularity. |
| MVP / requirement / test plan | P9-C02 / CN-M-BOOKING-STATISTICS / T-CN-M-BOOKING-STATISTICS |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [booking-confirmations.tsx](../../frontend/components/booking-confirmations.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/analytics.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f020"></a>
## DOC-F020 — Turnover and incident records

| Field | Evidence-backed value |
|---|---|
| Category / module | Staff Operations / Operations |
| Intended user | Authorized operations staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Load queue; explicitly create synthetic case, assign, record supported checklists/exceptions and review. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/operations` |
| Required permissions | operations.synthetic.read; assign/record/review independently; server remains authoritative |
| Known limitations | occupancy UNKNOWN; readiness UNVERIFIED; inventory unchanged; no physical cleaning proof. |
| MVP / requirement / test plan | P8-C02 / CN-O03 / T-CN-O03 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [operations.tsx](../../frontend/components/operations.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/operations.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f021"></a>
## DOC-F021 — Maintenance impact preview

| Field | Evidence-backed value |
|---|---|
| Category / module | Staff Operations / Booking / Inventory |
| Intended user | Independently authorized inventory reader; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Explicitly inspect exact room/local interval conflicts and completeness. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/operations` |
| Required permissions | inventory.read independently; server remains authoritative |
| Known limitations | Read only; no canClose, reopening approval, roomReady or safeToEnter; fail closed above completeness boundary. |
| MVP / requirement / test plan | P2-C01 / CN-A03 / T-CN-A03 |
| Automated test evidence | [InventoryIntegrationTest](../../backend/src/test/java/com/cinenest/InventoryIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [maintenance-impact.tsx](../../frontend/components/maintenance-impact.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/operations.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f022"></a>
## DOC-F022 — Account and audit reads

| Field | Evidence-backed value |
|---|---|
| Category / module | Identity and Security / Identity |
| Intended user | Independently authorized staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Load venue-scoped sanitized account and audit records. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/accounts` |
| Required permissions | account.read; audit.account.read independently; server remains authoritative |
| Known limitations | No general account provisioning, role assignment or automatic administrator authority. |
| MVP / requirement / test plan | P9-C01 / CN-A06 / T-CN-A06 |
| Automated test evidence | [AdminReadIntegrationTest](../../backend/src/test/java/com/cinenest/AdminReadIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [admin-reads.tsx](../../frontend/components/admin-reads.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/grants.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f023"></a>
## DOC-F023 — Managed grant inspection

| Field | Evidence-backed value |
|---|---|
| Category / module | Identity and Security / Identity |
| Intended user | Independently authorized grant inspector; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read booking.synthetic.read and guest.synthetic.read subset with signed pagination and mandatory auditing. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/accounts` |
| Required permissions | identity.grant.read at exact venue; server remains authoritative |
| Known limitations | Actual inspection assignments UNASSIGNED; bootstrap/delegation DISABLED; no assign/approve/revoke controls. |
| MVP / requirement / test plan | P9-C04 / CN-NFR-02 / T-CN-NFR-02 |
| Automated test evidence | [ManagedGrantReadIntegrationTest](../../backend/src/test/java/com/cinenest/ManagedGrantReadIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [managed-grant-inspection.tsx](../../frontend/components/managed-grant-inspection.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/grants.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f024"></a>
## DOC-F024 — Inbox and synthetic delivery queue

| Field | Evidence-backed value |
|---|---|
| Category / module | Notifications / Notifications |
| Intended user | Customer / authorized staff; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read inbox; mark read explicitly; inspect synthetic delivery attempts and permitted recovery. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/notifications` |
| Required permissions | own notification.read; notification.admin.read and separate queue-command grants; server remains authoritative |
| Known limitations | Live SMS/email disabled; synthetic delivery is not real customer notification. |
| MVP / requirement / test plan | P7-C03 / CN-C07 / T-CN-C07 |
| Automated test evidence | [BankIntegrationTest](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [notifications.tsx](../../frontend/components/notifications.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f025"></a>
## DOC-F025 — Venue and room configuration

| Field | Evidence-backed value |
|---|---|
| Category / module | System Administration / Rooms |
| Intended user | Venue manager with exact grants; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Read and explicitly edit authorized venue/room configuration. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/rooms` |
| Required permissions | venue.read/write; room.read/write separately; server remains authoritative |
| Known limitations | Configuration is not proof of physical capacity, facility health or readiness. |
| MVP / requirement / test plan | P2-C01 / CN-A03 / T-CN-A03 |
| Automated test evidence | [RoomsIntegrationTest](../../backend/src/test/java/com/cinenest/RoomsIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [rooms.tsx](../../frontend/components/rooms.tsx) |
| Screenshot | [Browser-fixture screenshot](screenshots/catalog.png); see provenance below |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

<a id="doc-f026"></a>
## DOC-F026 — Package drafts and publication

| Field | Evidence-backed value |
|---|---|
| Category / module | System Administration / Pricing |
| Intended user | Package editor / independent publisher; descriptive training grouping, never an automatic role grant |
| Description / available functionality | Manage draft/version comparisons, publication review and explicit supported publish/deactivate commands. |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic-test software only; not production-qualified |
| Demo availability | CONDITIONAL on isolated fixtures, session and exact permissions; no automatic provisioning |
| Route | `/admin/settings/packages` |
| Required permissions | package.read/edit/publish/deactivate separately; server remains authoritative |
| Known limitations | Independent publication rules and immutable purchased terms remain authoritative. |
| MVP / requirement / test plan | P2-C02 / CN-A12 / T-CN-A12 |
| Automated test evidence | [PublicationIntegrationTest](../../backend/src/test/java/com/cinenest/PublicationIntegrationTest.java); suite evidence in [PROGRESS](../PROGRESS.md#synthetic-booking-confirmation-report) |
| Source | [publications.tsx](../../frontend/components/publications.tsx) |
| Screenshot | NOT_CAPTURED in this portable guide; source/tests are evidence, not an invented screenshot |
| Last verified commit / CI | `24b73caf16eebda1490d0f3552c268d15e024229` / [39807842759 SUCCESS](https://github.com/Hasitha3513/Theater-Project/actions/runs/39807842759) |

## Unavailable or deferred capabilities

| Existing scope | Current truth |
|---|---|
| CN-C09 / CN-A09 loyalty, referrals, promotions | DEFERRED execution by owner; requirements and future acceptance obligations preserved |
| PayHere and commercial gateway | DEFERRED / BLOCKED; no live payment instructions |
| CN-M-POPULAR-TIME-SLOTS | PARTIAL; approved fixed hourly synthetic metric implemented; configured business bands/full acceptance outstanding |
| Full revenue/utilization/device analytics and exports | PARTIAL overall analytics; unimplemented measures must not display invented values |
| Grant assignment/approval/revocation administration | NOT_IMPLEMENTED commands; read capability is not delegation authority |
| Maintenance closure/reopening | NOT_IMPLEMENTED command scope; impact preview grants no closure authority |
| Production recovery / CN-NFR-04 | BLOCKED qualification; NOT_VERIFIED / NOT_ACCEPTED |

## CUSTOMER DEMONSTRATION OVERVIEW

CineNest supports private-cinema discovery and an explicitly controlled synthetic booking journey. AVAILABLE IN SOFTWARE: room/package discovery, inventory observations, holds, immutable quote review, protected booking history and scoped staff reads. SYNTHETIC DEMONSTRATION ONLY: identity evidence review, bank claims/credit allocation, SIMULATED_CONFIRMED bookings, guest registration, refund attempts, access and lighting simulators, turnover records, notification delivery and reports.

Customer sequence: discover room → inspect calendar/date and duration → explicitly check and create hold → choose supported package/quote → explicitly accept exact revision → consent and synthetic verification → eligible bank request → explicit claim/receipt → independent authorized synthetic review/credit/allocation → protected simulated booking/history. Each server gate can stop continuation. No automatic financial or identity action is implied by navigation.

Staff can inspect exact venue bookings and independently permitted roster/access/refund/operations context. Managers can manage configured rooms and package drafts/publications within granted scope. Administration supplies protected account/audit/grant-subset reads, not automatic grant administration. Reporting supplies synthetic bank summaries, confirmation-time statistics and [Popular Reserved Session Start Times](#doc-f027). These are bounded synthetic measures, not actual revenue or physical occupancy.

REQUIRES LIVE INTEGRATION: real bank/provider qualification, real identity processing, live SMS/email, physical access/devices and production disaster recovery. NOT YET IMPLEMENTED: the missing/deferred scope above and remaining registered MVP obligations. Demonstrating software neither holds D0–D6 nor supplies customer acceptance. P9-C02 remains unchecked.

## Screenshot provenance and coverage

The four images below were visually inspected during this task and copied byte-for-byte from existing local evidence; no screenshot was generated or altered. They are browser-test captures with synthetic fixture values, not production data, a new live demonstration or proof of real-backend integration. They predate this documentation commit; screen-specific freshness is limited to the recorded source/capture lineage. Features without a matching capture explicitly say NOT_CAPTURED.

- [catalog browser-fixture screenshot](screenshots/catalog.png): original `.cache/room-browser/catalog.png`; SHA-256 `4b1e661bfcd1698380978c5816ab9458ee25d9b8fa6d5d5dc7c5821dcdc0e51f`.
- [analytics browser-fixture screenshot](screenshots/analytics.png): original `.cache/booking-confirmations/after/1280.png`; SHA-256 `683c39bf5895f8f2870cd6517de7c4f5c94c398fd49a500b226e678275f89391`.
- [operations browser-fixture screenshot](screenshots/operations.png): original `.cache/operations-375.png`; SHA-256 `4da2255fa9a602778bb05fda9041f89ad1a5ca10297ac34ae7c0344f176dae88`.
- [grants browser-fixture screenshot](screenshots/grants.png): original `.cache/grant-inspection/after/results-1280.png`; SHA-256 `5b6ad59619588dd97c6574e207f6d3f25b81bcfdaed8360d92303f250c62e596`.

Approved design mockups (visual direction only, not implemented-function evidence): [Admin Mockup](../CineNest%20Cinema%20Admin%20Dashboard%20Mockup-1.png), [Dashboard Showcase](../CineNest%20Cinema%20Dashboard%20Showcase-2.png), [Customer Booking](../CineNest%20Private%20Cinema%20Booking%20UI.png). Mockup prices, customer examples, PINs, devices and charts must never be taught as actual product facts.

## Maintenance

For every user-visible change, review this catalogue and the corresponding guide workflow; update affected facts, source/test/screenshot references and exact source/CI evidence in the same bounded change set. Preserve historical evidence and synthetic/live distinctions. Backend-only changes update the catalogue when capability or restrictions change. New ideas belong in [FUTURE_FEATURES](../backlog/FUTURE_FEATURES.md), not here as implemented features. The four canonical owners retain policy/architecture/design/status authority.

<a id="doc-f027"></a>
## DOC-F027 — Popular Reserved Session Start Times

| Field | Evidence-backed value |
|---|---|
| Category / module | Reports and Analytics / Reporting and Booking |
| Intended user | Independently authorized venue reporting staff; job titles confer no authority |
| Description / available functionality | Explicitly load a source-backed distribution of synthetic reserved starting hours from latest committed purchased booking revisions;24 local-hour bands and equivalent table |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / synthetic test data;not attendance,occupancy,revenue or physical readiness |
| Demo availability | CONDITIONAL on authorized isolated synthetic fixtures/session;no grants assigned |
| Route | /admin;GET /api/admin/venues/{venueId}/reports/synthetic-popular-session-starts |
| Required permissions | Current eligible exact-venue member with report.booking.synthetic.read;fresh authorization after durable read audit |
| Known limitations | 31 local days;fixed hourly bands;latest purchased room and current venue timezone;no historical reconstruction;configured business bands outstanding |
| MVP / requirement / test plan | P9-C02 / CN-M-POPULAR-TIME-SLOTS / T-CN-M-POPULAR-TIME-SLOTS |
| Automated test evidence | [PostgreSQL cases](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java),[architecture](../../backend/src/test/java/com/cinenest/PopularSessionStartsArchitectureTest.java),[frontend](../../frontend/tests/popular-slots.test.mjs),[intercepted browser](../../frontend/tests/popular-slots-browser.mjs) |
| Source | [Public port](../../backend/src/main/java/com/cinenest/booking/PopularSessionStarts.java),[Booking adapter](../../backend/src/main/java/com/cinenest/booking/internal/PopularSessionStartsAdapter.java),[Reporting controller](../../backend/src/main/java/com/cinenest/reporting/internal/PopularSessionStartsController.java),[UI](../../frontend/components/popular-slots.tsx) |
| Screenshot | [375px](screenshots/popular-375.png),[768px](screenshots/popular-768.png),[1280px](screenshots/popular-1280.png);actual intercepted synthetic browser captures,not live operational evidence |
| Last verified commit / CI | New feature overrides initial catalogue default:local tested worktree based on338db745371a64c10845eee334d2d6a7104b1e6a;publication commit is this record's Git history;new exact-source CI PENDING until independently confirmed |

[Policy and actual verification](../PROGRESS.md#popular-session-starts). This existing MVP feature consumes no FF ID and closes no criterion.

<a id="doc-f028"></a>
## DOC-F028 — Synthetic pre-start booking cancellation

| Field | Evidence-backed value |
|---|---|
| Category / module | Booking and Refunds / Booking, Payments, Access, Identity, Notifications |
| Intended user | Eligible booking owner or independently authorized exact-venue staff |
| Description / available functionality | Explicit pre-start cancellation only with never-issued credentials,known reconciled synthetic funding and no pending amendment;source-linked100% allocated principal reservation and current purchased-block release |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED / no real repayment,physical vacancy or commercial acceptance |
| Demo availability | CONDITIONAL customer/staff booking-detail panels and backend;eligible session/exact permissions required;no grants assigned |
| Route | /bookings/[id] and /admin/bookings/venues/[venue]/[booking];GET /api/bookings/{bookingId}/cancellation;POST /api/bookings/{bookingId}/cancellations;matching exact-venue staff routes |
| Required permissions | Eligible owner;or exact-venue current eligible member with independent booking.synthetic.cancel;no implicit admin bypass |
| Known limitations | Reject started,ever-issued,amendment-pending or financially uncertain bookings;RESERVED_REVIEW_REQUIRED is not refund approval/execution/settlement;original allocations remain unavailable for spending |
| MVP / requirement / test plan | P6-C01 / CN-C08 / T-CN-C08 |
| Automated test evidence | [Frontend/proxy](../../frontend/tests/booking-cancellation.test.mjs),[intercepted browser](../../frontend/tests/booking-cancellation-browser.mjs),[PostgreSQL cases](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java),[architecture](../../backend/src/test/java/com/cinenest/CancellationArchitectureTest.java) |
| Source | [UI](../../frontend/components/booking-cancellation.tsx),[strict proxy](../../frontend/lib/booking-cancellation-backend.ts),[Booking service](../../backend/src/main/java/com/cinenest/booking/internal/CancellationService.java),[Payments adapter](../../backend/src/main/java/com/cinenest/payments/internal/CancellationFundingAdapter.java),[V24](../../backend/src/main/resources/db/migration/V24__synthetic_booking_cancellation.sql) |
| Screenshot | [Customer375](screenshots/cancellation-after-customer-375.png),[768](screenshots/cancellation-after-customer-768.png),[1280](screenshots/cancellation-after-customer-1280.png);[Staff375](screenshots/cancellation-after-staff-375.png),[768](screenshots/cancellation-after-staff-768.png),[1280](screenshots/cancellation-after-staff-1280.png);actual intercepted browser captures |
| Last verified commit / CI | Backend4b3ac401f510e97b4a21821530938e48fa8f0a20/CI39904035164 SUCCESS;frontend checkpoint based on that baseline;publication commit=this record's Git history;new exact-source CI PENDING |

[Actual verification and remaining obligations](../PROGRESS.md#synthetic-cancellation-frontend). Additional mappings:P6-C04/C05/C06,CN-A02/CN-O01/CN-A07/BR-10/BP-AC12 and their existing full plans. Existing MVP feature;no FF ID or criterion closure.

Current guard clarification: cancellation rejects an unresolved persisted ACTIVE extension reservation; expiry alone does not prove committed release. No refund settlement or physical clearance follows from cancellation.

<a id="doc-f029"></a>
## DOC-F029 — Synthetic owner extension eligibility and additional-price preview

| Field | Implemented boundary |
|---|---|
| Implementation status | PARTIAL |
| Category / module | Booking extensions / Booking, Pricing, Identity, Rooms, Access |
| Intended user | Current eligible booking owner only |
| Description / available functionality | Protected eligibility and exact positive frozen-term additional-price preview |
| Operational classification | SIMULATED;no real money or physical readiness |
| Demo availability | CONDITIONAL backend only;frontend PENDING |
| Required permissions | Authenticated eligible exact owner;no new staff capability or grants |
| Known limitations | Revision1/lifecycle0,pre-start,never-issued,no pending amendment;no reservation,funding or commit |
| Screenshot | Not applicable:backend-only,no customer controls implemented |
| Routes | GET /api/bookings/{bookingId}/extension-eligibility;POST /api/bookings/{bookingId}/extension-quotes |
| Authority | Current eligible owner;synthetic confirmed revision1/lifecycle0,pre-start,no pending amendment,no credential ever issued |
| Capability | Frozen purchased whole-unit exact LKR additional-price preview;no duplicated base/guests/add-ons;no current-price substitution |
| UI availability | FRONTEND PENDING;no customer/staff controls or new authenticated Next.js proxy |
| Truth | Indicative synthetic extension preview.No additional time reserved,no payment requested,booking unchanged;occupancy UNKNOWN/readiness UNVERIFIED |
| Source | [Booking](../../backend/src/main/java/com/cinenest/booking/internal/ExtensionPreviewService.java),[Pricing](../../backend/src/main/java/com/cinenest/pricing/internal/ExtensionPricingAdapter.java) |
| Automated test evidence | [PostgreSQL](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java),[pure price](../../backend/src/test/java/com/cinenest/pricing/internal/ExtensionPricingTest.java),[architecture](../../backend/src/test/java/com/cinenest/ExtensionPreviewArchitectureTest.java) |
| MVP / requirement / test plan | P6-C03 / CN-A02 / T-CN-A02 |
| Last verified commit / CI | [Current verification](../PROGRESS.md#extension-preview);baseline030acef6177756e5f937d098d49323b1335b350f/CI39911829949 SUCCESS;new exact-source CI PENDING |

No extension hold,additional funding,refund,revision,credential,device or notification action;no commercial/physical acceptance.

<a id="doc-f030"></a>
## DOC-F030 — Synthetic customer extension inventory reservation

| Field | Implemented boundary |
|---|---|
| Category / module | Booking extensions / Booking,Identity,Rooms,Pricing,Access |
| Intended user | Current eligible booking owner only |
| Description / available functionality | Explicit durable temporary [B,B') extension coverage;600-second validity clipped to original start;protected status and withdrawal;audited expiry/invalidation |
| Implementation status | PARTIAL |
| Operational classification | SIMULATED;no funding,purchased extension commitment or physical readiness |
| Demo availability | CONDITIONAL backend only;explicit approved synthetic configuration;frontend PENDING |
| Required permissions | Eligible owner,current session,revision1/lifecycle0,pre-start,never-issued,no pending amendment;no new staff permission/grant |
| Known limitations |5 creation attempts/rolling60s across owner bookings/venues;all non-replay eligible attempts count;verified committed replay free,no renewal;429 over budget. Pending reservation blocks conflicting booking changes. No payment/commit/credential/device/notification changes |
| Route | POST /api/bookings/{bookingId}/extension-reservations;GET .../{reservationId};POST .../{reservationId}/withdrawals |
| MVP / requirement / test plan | P6-C02 / CN-A02 / T-CN-A02 |
| Automated test evidence | [PostgreSQL](../../backend/src/test/java/com/cinenest/BankIntegrationTest.java),[architecture](../../backend/src/test/java/com/cinenest/ExtensionReservationArchitectureTest.java) |
| Source | [Service](../../backend/src/main/java/com/cinenest/booking/internal/ExtensionReservationService.java),[worker](../../backend/src/main/java/com/cinenest/booking/internal/ExtensionReservationWorker.java),[V25](../../backend/src/main/resources/db/migration/V25__synthetic_extension_reservations.sql) |
| Screenshot | Not applicable:backend-only,no new frontend control |
| Last verified commit / CI | Baseline7dad40cbd198069fc6f9e5db8ae0b5647f7736f1/CI39918547446 SUCCESS;[actual evidence](../PROGRESS.md#extension-reservation);new exact-source CI PENDING |

Original booking and purchased revision remain unchanged;temporary coverage is not permission to occupy or extend the session. Existing full requirements and financial/physical acceptance remain open.

CN-EXTENSION-CONTEXT-PORT-001 adds an internal typed owner reservation observation to DOC-F030 using its existing protected GET/audit path. No new customer control or API route;no payment request,matching,allocation or extension commitment. Caller transaction and current owner eligibility are required. [Evidence](../PROGRESS.md#extension-context-port).
