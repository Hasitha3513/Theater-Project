# CineNest Business Rules and Lifecycles

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.


## Canonical initial draft defaults
[Rule specification](cinenest-draft-rules.json) is machine-readable documentation, never a runtime configuration change. All initial values originate in S1 and are reaffirmed/classified by S8, not unseen PDFs. These are configurable initial defaults, not immutable application values; package content and rates are distinct from operational constraints. Backend configuration must be versioned and approved before launch. Open items: [decision register](../decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md).

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

Authoritative quote uses applicable package/fee/add-on versions and server line items; [parameterized calculation](CINENEST-PACKAGE-MANAGEMENT.md) handles included duration, eligible extension units and included extras without double charging. Labeled initial illustration (online, no discounts/decided tax treatment): Base booking = **LKR 2,600**; base plus PS4 = **LKR 3,600**. These examples are documentation fixtures, not operational prices or frontend constants. Pricing owns rounding, itemization and eligibility.

## Inventory, time and concurrency
`session_end = session_start + purchased_duration` and `blocked_until = session_end + cleaning_buffer`. Reservation blocking interval is **[session_start,blocked_until)**; guest access interval is **[session_start,session_end)**. Distinguish the two in APIs, calendars, UI and device scheduling. Back-to-back session beginning exactly blocked_until is permissible only when readiness is cleared. Operating checks use venue-local calendar boundaries; storage uses UTC instants with original timezone/config version. Cleaning ending after midnight needs OD-10 resolution.

Recommended PostgreSQL exclusion constraint or equivalent database-enforced conflict guard on room plus active blocking range; transaction serialization/locking validates holds, booking writes, extensions and reschedules together. Redis is an optional temporary hold/cache mechanism, never the sole overlap guarantee. If Redis fails, fail safe or fall back to authoritative database-backed holds; do not bypass reservation checks. Hold owner, expiry, quote version and consumed flag checked atomically. A hold is not a confirmed booking.

## Edge workflows
- Expired hold: mark expired, release inventory, refresh availability/quote. Do not extend from browser countdown alone.
- Late capture: deduplicate verified provider event; recheck reservation under database guard. Confirm only if inventory/policy remains valid; otherwise payment exception and compensating refund/support. External capture is not undone by database rollback.
- Duplicate/out-of-order webhook: durable inbox keyed by provider event/transaction; preserve facts, version-aware state projection, reconcile provider status; do not move captured to failed on a late earlier event.
- Retry: reuse logical operation idempotency key; provider lookup before another charge/refund/access write. Unknown outcome is not safe failure.
- Reschedule: quote delta/terms, lock old/new scopes in deterministic order, atomically swap reservation; retain original history and agreed policy. If external delta fails, retain original booking or documented compensating path. No silent release before new slot secured.
- Extend: applicable configured whole increment (initially 60 minutes), accepted amendment delta quote, check next booking plus buffer/closing/readiness; replace reservation range atomically. Capture delta then update access only through durable workflow; payment success does not guarantee PIN end-time updated. Reconcile any partial outcome.
- Cancel/refund: authorization and policy snapshot eligibility, cancel inventory and revoke access safely; refund remains separate asynchronous state until verified successful. No automatic card re-billing without separately approved consent/provider capability (OD-07).

## Proposed lifecycle transitions
All tables are desired behavior, not implemented states. State changes are versioned and server authorized. Timeouts are policy-configured (not invented numbers); each requires a durable audit event containing actor, resource, reason, prior/new state and correlation ID without secrets. Trigger codes below are reused in contracts. Account active/suspended is separate from verification.

### Booking
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| draft → held | HOLD_CREATED; available blocking range, capacity, owner | Customer or authorized booking creator | Hold + versioned quote | Server expiresAt; reselect | Hold ID/range/version |
| held → awaiting_payment | CHECKOUT_REQUESTED; valid hold, eligibility/consent | Owner / payments service | Payment attempt | Hold expiry; status lookup | Quote/policy IDs, attempt ID |
| held/awaiting_payment → expired | HOLD_EXPIRED; no confirmed reservation | Booking worker | Release hold; reconcile pending attempts | Late capture goes exception | Expiry and inventory result |
| awaiting_payment → confirmed | PAYMENT_VERIFIED; valid inventory and eligibility | Booking service | Final snapshots/reservation; durable access/notification work | Provision failures tracked independently | Verified event/reservation IDs |
| confirmed → confirmed (new revision) | CHANGE_ACCEPTED; modification/extension authorized, new range secured | Owner or booking.update actor | New agreed delta/snapshot, reschedule access | Partial payment/access reconcile | Old/new revision, consent |
| confirmed → cancelled | CANCEL_ACCEPTED; policy and safe revocation | Owner or booking.cancel actor | Release future inventory; refund/access work | Retries; support for partial result | Policy/reason/refund IDs |
| confirmed → completed | SESSION_FINISHED; session ended | Session service | Archive purchase/history | No exit/readiness inference | Session completion reference |
| awaiting_payment → payment_exception | LATE_CAPTURE_CONFLICT; capture but slot unavailable | Reconciliation service | Compensation request + incident | Manual support/refund reconciliation | Capture/conflict/compensation IDs |

### Payment (attempt state and refund ledger separate)
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| created → pending | PROVIDER_CHECKOUT; valid quote/hold | Payments service | Hosted session/reference | Provider expiry; reconcile | Attempt/idempotency/provider refs |
| pending → authorized/captured | VERIFIED_PROVIDER_EVENT; signature, amount, currency and booking match | Webhook/reconciliation service | Persist capture ledger; booking event | Poll authoritative status on ambiguity | Event dedupe key, ledger ref |
| pending → failed/cancelled/unknown | VERIFIED_FAILURE/CANCEL or timeout | Provider adapter / worker | UI status; no success | Unknown → reconcile before retry | Safe reason and last observation |
| unknown/authorized → captured/failed | PROVIDER_RECONCILED; verified outcome | Reconciliation service | Exactly-once ledger effect | Escalate bounded failures | Reconciliation ref |
| refund requested → pending → succeeded/failed/unknown | REFUND_REQUESTED/VERIFIED_RESULT; refundable balance, policy | payments.refund actor / worker | Separate refund ledger; captured facts retained | Provider lookup/idempotent retry; escalate | Refund amount/policy/event refs |

### Verification
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_submitted → submitted | DOCUMENT_UPLOADED; consent, allowed type/size | Booker | Private upload ref | Upload expiry/retry | Evidence ref, consent version |
| submitted → processing | OCR_REQUESTED; private validated input | Verification worker | OCR candidate facts | Provider timeout → review | Job/version/ref, no raw text in event |
| processing → review_required | AMBIGUOUS_RESULT; unreadable/uncertain/exception | Worker | Restricted review task | Staff SLA TBD, support | Reason code |
| processing/review_required → verified/rejected | VERIFICATION_DECIDED; approved eligibility process, not OCR alone | Authorized verifier | Minimal decision, expiry/scope | Appeal/reupload per policy | Reviewer, basis, policy version |
| verified → expired/revoked | VALIDITY_ENDED/REVIEW_REVOKED | Policy worker / restricted verifier | Block future eligible checkout | Reverify; existing session impact assessed | Reason/scope/time |

### Access provisioning
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| not_requested → requested → pending | BOOKING_CONFIRMED; tested lock capability | Access service | Private credential + durable install command | Bounded retries before session; incident | Booking/credential ref, no PIN |
| pending → installed | INSTALL_ACKNOWLEDGED; correct lock/version/window | Device/vendor adapter | Provisioning success, eligible delivery | Unverified ack → lookup | Command/credential version |
| pending → failed/unknown | INSTALL_FAILURE/TIMEOUT | Adapter / worker | Support incident; no access-ready claim | Reconcile before reprovision | Safe error/last attempt |
| installed → active | WINDOW_STARTED; valid session and policy | Lock/local schedule / service | Permit entry per tested device behavior | Clock/offline risk OD-02 | Configured window + observation |
| active → expired/revoked | WINDOW_ENDED/CANCEL/SECURITY_REVOCATION | Lock schedule / access authority | Deny new entry; preserve independent egress | Offline revocation reconciliation | Reason, ack/unknown state |

### Session
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| scheduled → active | SESSION_START; booking/window | Session worker | Approved scenes; status | Command failure does not falsify observed state | Booking/time/command refs |
| active → ending | FIVE_MINUTE_WARNING; five minutes before session_end | Scheduler | Idempotent notice | Late job says accurate remaining time | Scheduled vs delivered time |
| ending/active → ended | SESSION_END; server schedule | Scheduler | Expire entry, checkout prompt, cleaning task | No forced trap/power cut | End/revocation/task refs |
| active/ending → active (revision) | EXTENSION_CONFIRMED; range/delta/access reconciled | Booking/session services | New end, reschedule notices | Partial outcomes → incident | Revision and access result |
| ended → overstay_incident | OVERSTAY_SUSPECTED; evidence/operational check | Operator or monitoring service | Escalation to accountable staff | Never infer all occupants exited | Evidence type and intervention |

### Room readiness
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| ready → reserved/in_session | RESERVATION/SESSION_START; schedule + cleared readiness | Booking/session service | Availability projection | Projection lag → refresh | Reservation/session IDs |
| in_session → cleaning_required | SESSION_END; readiness not presumed | Session service | Physical cleaning work item | Staffing SLA OD-04 | End/task refs |
| cleaning_required → cleaning → ready | CLEANING_STARTED/CLEARANCE; authorized physical checklist | Operations staff | Clearance evidence; enable booking admission | Buffer elapsed alone cannot clear | Staff/checklist/completedAt |
| any → maintenance/unknown | FAULT/STALE_CLEARANCE | Operator/health service | Block unsafe readiness | Inspection/reconciliation | Reason/observation |
| maintenance/unknown → ready | INSPECTION_CLEARED; no conflict, physical assurance | Authorized operator | Updated safe readiness | Escalate failed checks | Inspection result |

### Device command
| Transition | Trigger / guard | Actor | Side effects | Timeout / recovery | Audit evidence |
|---|---|---|---|---|---|
| requested → pending | COMMAND_ACCEPTED; permission/capability/safety/expiry | device.command actor / worker | Durable publish with command ID | Bounded retries | Requested action/resource/actor |
| pending → acknowledged | DEVICE_ACK; correct command ID/version | Authenticated device adapter | Receipt acknowledgement only | Wait observed state if required | Device ack/observedAt |
| pending → failed/expired/unknown | DEVICE_NACK/TTL_END/OUTCOME_UNCERTAIN | Adapter / timeout worker | Error/incident, no invented OFF state | Query state before retry | Safe code/expiry/lastSeen |
| acknowledged → observed result recorded | TELEMETRY_CONFIRMED; matching expected state | Telemetry adapter | Separate observed state field | Missing telemetry remains unknown | Observation/correlation refs |
| unknown → acknowledged/failed | STATUS_RECONCILED; authoritative response | Worker/operator | Reconcile original command | Expired command never replayed | Original command + reconciliation |

See [IoT safety](../architecture/CINENEST-IOT-ACCESS-AND-FAILURE-MODES.md) and [data contracts](../architecture/CINENEST-DATA-AND-API-CONTRACTS.md) for durable integration and failure ownership.

## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
[Package management](CINENEST-PACKAGE-MANAGEMENT.md) is canonical for PR-PKG-001/CN-A12. Initial defaults do not prevent authorized future package-duration/price/content changes. Global/venue minimum-duration policy, hours, cleaning and physical capacity remain separate operational constraints; a two-hour package requires separately authorized minimum and customer-terms changes. Shared Pricing add-on/fee definitions are referenced, not duplicated per package. No pro-rata discount or new tax treatment inferred.

Payment/eligibility revalidation checks the accepted quote version, verified amount/currency and current reservation eligibility, not a newly published catalog rate. Valid quote honor is RECOMMENDED, expired quote refresh needs acceptance; arrival time of a delayed webhook is separate from verified capture timing. [Package quote/amendment protections](CINENEST-PACKAGE-MANAGEMENT.md) qualify the existing late-payment and lifecycle recovery rules. Deactivation does not cancel purchased bookings; changing live catalog cannot rewrite snapshots, cleaning intervals or access credentials. Source PDFs remain missing and unchanged.
