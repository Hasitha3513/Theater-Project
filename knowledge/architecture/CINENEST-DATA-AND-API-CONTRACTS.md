> **SUPERSEDED — historical snapshot retained pending owner deletion review.** Current canonical content: [open section](../ARCHITECTURE.md#data-api). Do not update this file; update the four canonical documents in place. Older status/next-action text below is historical.

# CineNest Data and API Contracts

> Task: CN-KB-FEATURES-TECH-001 · Recorded: 2026-10-14 (Asia/Colombo).
> Decision: DOCUMENTED_DRAFT unless a row specifies otherwise. Implementation: NOT_VERIFIED.
> Source S1: user-supplied task text; source PDFs are missing. This is documentation, not proof of implementation, legal compliance, merchant approval or working hardware.

## Contract conventions
Conceptual entities, table names and capabilities are proposals, not existing schema/endpoints. No database migrations/runtime API changes in this task. ISO currency with integer minor-unit amounts (LKR draft), UTC RFC3339 instants and IANA reporting timezone. Half-open session/reservation intervals follow [business rules](../product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md). Opaque IDs, version/ETag for writes, stable timestamp+ID sorting and bounded server pagination. Requests/exports/streams enforce owner and authorized venue scope before reading data; venue scope does not assert tenancy.

| Entity | Owner | Proposed fields | API capabilities (not paths) |
|---|---|---|---|
| Accounts | Identity & Access | accountId, provider subject mapping, profile, accountState; no OAuth tokens in response | Self profile/session; restricted account support |
| Venues and rooms | Venues/Rooms | venueId, roomId, capacity, operatingWindowVersion, amenities, readiness and observedAt | Catalog read; scoped configuration/clearance |
| Holds | Booking | holdId, ownerId, roomId, sessionStart/end, blockedUntil, expiresAt, quoteRef, version, consumed | Owner create/read/expire; no transferable hold |
| Bookings and guests | Booking | bookingId, revision, packageVersionRef/purchased display+line item+duration+buffer+policy snapshots, ownerId, roomId, guestCount/required minimal guest checks, scheduled times, state, purchase snapshots | Owner list/detail/change; authorized manual bookings |
| Package catalog and published versions | Pricing | stable packageId, immutable packageVersionId/revision, display/inclusion/duration/exact-price refs, venue/room scope, sales interval and optional service interval | Authorized list/create/duplicate/edit/preview/publish/schedule/deactivate/archive/history; actual routes/tables proposed only |
| Quotations | Pricing | quoteId/version, packageVersionRef, price/add-on/fee snapshots, currency, integer minor-unit line items, subtotal/discount/tax/fee/total, expiresAt, policyRefs | Server quote, acceptance and immutable purchase copy |
| Gateway payment attempts and captures | Payments | attemptId, booking/quote refs, providerRef, expected amount/currency, status, captured ledger refs | Create hosted checkout, verified callback/status, reconcile |
| Gateway refunds | Payments | refundId, captureRef, policy snapshot, requested/successful amount, reason, state and provider ref | Restricted request/status; balance checked atomically |
| Verification decisions | Verification | evidenceRef, decisionId/version, eligible, policy/basis/reviewer/time/expiry; candidate OCR fields restricted | Owner submit/status; reviewer decision; separate evidence access |
| Access credentials | Access Control | credentialRef/version, booking/lock refs, startsAt/endsAt, provisioning state; secret never in generic entity/list | Authorized reveal via separate protected channel; install/revoke status |
| Sessions | Booking | sessionId, bookingRevision, scheduled start/end, warning schedule and state; exit evidence separately typed | Session status, extension and incident capabilities |
| Devices and commands | IoT | deviceId, roomId, capability/schema/firmware, commandId, desiredAction, TTL, ack and observedState separately | Scoped capability-limited command and status lookup |
| Telemetry | IoT | deviceId, sequence/bootId, observedAt/receivedAt, measurements/units, quality and freshness | Authenticated ingest; authorized safe projection |
| Notifications | Notifications | notificationId, templateVersion, resourceRef, destinationRef, delivery status, scheduledAt/attemptedAt | Durable send/status; restricted contacts; no raw PIN in ordinary payload |
| Policy versions and consent | Pricing; Identity & Access | policyId/version/effectiveAt, immutable terms content/hash; consent actor/time/version/context | Versioned publish and explicit acceptance; login not acceptance |
| Engagement ledger | Customer Engagement | ledgerId, accountRef, ruleVersion, delta/reason/expiry/reversalRef; eligibility decisions | Owner read; restricted idempotent adjust/redeem |
| Audit events | Audit | eventId, actor, action, authorized venue, resourceRef, reason, timestamp, correlation and redacted diff | Scoped immutable read/export; never expose credentials |

## Application and integration responses
Successful writes return resource ID, new version, operation status, correlation ID and server asOf. Async writes return accepted/pending plus status capability, never an unconfirmed success. Failed operations return a safe code (availability_conflict, hold_expired, quote_changed, verification_required, scope_denied, provider_pending, access_unknown), recoverability, safe message and correlation ID; no raw provider secret or identity evidence. A failed HTTP transport is unknown outcome until status queried. Validation fields reference persistent UI labels.

Idempotency key is owner/scope/operation bound with request digest, durable result and retention defined by final policy. Same key/same intent returns original result; same key/different intent conflicts. Authenticate and authorize before key lookup so results cannot leak across users. Mutations check entity version; stale edits surface reload/diff, not overwrite.

## Verified gateway payment notification workflow
1. Provider adapter validates configured authenticity/signature, expected merchant, transaction/attempt, amount/currency and replay/dedupe identifiers against official selected provider contract. Client redirect is advisory only.
2. Persist inbox event and financial fact once; atomically create durable integration work/audit. Acknowledge provider only after durable acceptance. Reject or quarantine mismatches safely.
3. For gateway, Booking consumes verified fact once, checks accepted quote reference/amount/currency and eligibility/hold/reservation under authoritative concurrency guard (never silently checks latest catalog rate), confirms or records payment_exception; neither provider success nor Redis hold alone confirms inventory.
4. Create durable access provisioning/notification work. Access failure remains visible independently of paid/confirmed booking.
5. Reconciliation polls verified provider status on missing/out-of-order/unknown callbacks. Refund/compensation is a separate provider operation with its own idempotency and ledger.

A database rollback does not reverse an external capture. Outbox workers retry with bounded exponential backoff/jitter and per-operation TTL/maximum attempts determined before launch. Persist attempts and nextAttemptAt; exhausted work goes to incident/manual review, not infinite retry. Access work expires when no longer useful; no replay of stale unlock/power command. Provider operations query status before retry where outcome uncertain. Inbox/event IDs deduplicate; aggregate version/sequence handles out-of-order facts without losing original events.

## Proposed safe event envelope
```json
{
  "eventId": "opaque-unique-id",
  "eventType": "BookingConfirmed",
  "eventVersion": 1,
  "schemaVersion": "1.0.0",
  "occurredAt": "RFC3339-UTC-instant",
  "receivedAt": "RFC3339-UTC-instant",
  "correlationId": "opaque-correlation-id",
  "causationId": "opaque-parent-event-or-command-id",
  "venueId": "authorized-venue-id",
  "aggregateId": "opaque-resource-id",
  "aggregateVersion": 1,
  "payload": {"bookingId": "opaque-booking-id", "revision": 1}
}
```
This is a shape example, not a valid production timestamp fixture. Scope validated server-side, never trusted from caller payload. No raw identity document/OCR dump, PIN, card data, access token or secret in ordinary events/logs. Protected secret installation uses an explicitly secured narrow adapter channel and credential reference in normal events. Envelope timestamp distinguishes occurrence from ingestion. Consumers idempotently checkpoint; projection rebuilds preserve authorization/freshness and do not reexecute external side effects.

## Reconciliation acceptance
Future tests: duplicate/out-of-order captures, expired holds, concurrency, partial refunds, late success after cancellation, provider timeout, lost outbox ack, notification failure, failed access installation and stale telemetry. Audit evidence links original facts and compensation. [Chart contracts](../tokens/cinenest-chart-specs.json) consume safe authorized projections, not screenshots or client-subset totals.

## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
[Canonical package contract](../product/CINENEST-PACKAGE-MANAGEMENT.md) proposes scoped package.read/edit/publish/deactivate/archive; opaque immutable revision IDs, version/ETag conditional updates and idempotent publish commands. Publication validates duration/inclusions/price/operational refs/schedule together; ambiguity/stale state rejects atomically, not partial publish. Versioned quote response includes package/price/fee/add-on/policy refs, snapshot line items, currency, total and expiry. Capture reconciliation uses accepted quote and verified capture timing rather than webhook receipt time/new catalog values.

Confirmed Booking preserves purchased display details, duration/start/end/buffer, guests/services, exact total/line items, consent/policy/quote refs. Amendments have distinct accepted quote and guarded revision/payment/refund/access work; catalog events never update active PINs. PackagePublished safe event may contain stable ID/version, effective range, scope and correlation only; audit holds actor/time/reason/old-new values/version. Table names/routes, approval thresholds and distributed cache design unimplemented/proposed. See OD-17–22 for policy options.

## Bank payment — CN-KB-BANK-PAYMENT-001
[Canonical spec](../product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) supplies full workflows/lifecycles; conceptual names/capabilities below are proposals, not tables/routes.

| Record | Proposed owner/fields | Capabilities |
|---|---|---|
| BankPaymentRequest | Payments: requestId, owner/venue, accepted quote/package/session/inclusions/policy snapshots, expected amount/currency, instructionVersionId, due/submission/review/hold/quote-validity timestamps | Own create/status; late-claim intake; scoped reviewer list/detail; no live rate substitution |
| ReceiptSubmission/ReviewDecision | Payments: immutable submissionId/revision, private evidence ref, declared facts/payer/note, submittedAt, assignee, safe reason, reviewer/action/evidence refs/version | Own upload/correction; restricted preview/assign/await-credit/request-info/reject/match; history retained |
| VerifiedBankTransaction | Payments: fundId, receiving-account/instruction refs, independent bank transaction or controlled statement entry identity, creditedAt, verifiedAt, exact amount/currency, reviewer/evidence | Authorized independent verify/dedupe/reconcile; persist even booking impossible |
| PaymentAllocation | Payments: allocationId/fund/request/quote refs, amount/currency, allocatedAt, version/adjustment parent; unallocated/refund-reserved balances | Locked capped idempotent allocation, aggregate bank+gateway satisfaction, separate adjustment; no shared-deposit product by default |
| BankAccountInstructionVersion | Payments with bank.account.manage: stable account ref, immutable protected instruction version/effective time/verification evidence | Separately authorized change/history; old request protected, emergency redirect controlled |
| BankRefund | Payments: refundId/credit+allocation refs, approval/destination verification, outgoing operation/ref/amount/currency, requested/approved/initiated/sent/confirmed/failed timestamps | Scoped request/approve/execute/reconcile; unknown send looked up before retry, no fake gateway refund |
| BankReservationHold | Booking: request ref, durable session/cleaning range, policy/expiry/version, review extension provenance | Same conflict guard as gateway; bounded no-reupload-reset; guarded confirmation or resolution |

Inbound gateway authenticity and bank authorized reconciliation are different evidence sources. Optional proposed PaymentVerified envelope carries eventId/correlation/schema/occurredAt, evidenceSource=GATEWAY_VERIFIED or BANK_RECONCILED, stable payment/fund/request/quote refs, amount/currency, authorized venue and actor/evidence reference. No raw receipt/statement, bank credentials/instructions or PIN. Do not forge webhook/card capture for bank source. Notification/work events use references and authorized links only.

Record unique incoming credit with bank-side identity independently; then local guarded allocation/satisfaction/booking confirmation and durable outbox. Lock available credit/request/payment aggregate and room range; request/currency/version and eligibility validated, repeat operation returns original result. Notification/access external failure leaves financial fact/queued work intact. Concurrent reviewers return conflict/current assignment and cannot allocate twice. Bank statement ambiguity is controlled review, not customer-ref uniqueness. Received funds with invalid/expired slot return Funds Received / Booking Resolution Required; no displacement/new booking/reprice.

API outcomes expose review, verified-funds/allocation balance, booking and access separately; rejected receipt ≠ failed actual credit. Model initiated/credited/submitted/verified/allocated/confirmed timestamps independently; manual timeliness policy open. Existing refunds/adjustments use approved bank evidence/destination process, append history and bounded recovery. All actual routes/schema/thresholds/support remain unimplemented.
