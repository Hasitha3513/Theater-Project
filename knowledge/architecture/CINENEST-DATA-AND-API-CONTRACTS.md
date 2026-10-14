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
| Bookings and guests | Booking | bookingId, revision, ownerId, roomId, guestCount/required minimal guest checks, scheduled times, state, purchase snapshots | Owner list/detail/change; authorized manual bookings |
| Quotations | Pricing | quoteId/version, currency, integer minor-unit line items, subtotal/discount/tax/fee/total, expiresAt, policyRefs | Server quote, acceptance and immutable purchase copy |
| Payment attempts and captures | Payments | attemptId, booking/quote refs, providerRef, expected amount/currency, status, captured ledger refs | Create hosted checkout, verified callback/status, reconcile |
| Refunds | Payments | refundId, captureRef, policy snapshot, requested/successful amount, reason, state and provider ref | Restricted request/status; balance checked atomically |
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

## Verified payment notification workflow
1. Provider adapter validates configured authenticity/signature, expected merchant, transaction/attempt, amount/currency and replay/dedupe identifiers against official selected provider contract. Client redirect is advisory only.
2. Persist inbox event and financial fact once; atomically create durable integration work/audit. Acknowledge provider only after durable acceptance. Reject or quarantine mismatches safely.
3. Booking consumes verified fact once, checks quote/eligibility/hold/reservation under authoritative concurrency guard, confirms or records payment_exception; neither provider success nor Redis hold alone confirms inventory.
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
