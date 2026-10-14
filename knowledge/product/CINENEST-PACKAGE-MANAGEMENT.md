> **SUPERSEDED — historical snapshot retained pending owner deletion review.** Current canonical content: [open section](../PROJECT.md#package-management). Do not update this file; update the four canonical documents in place. Older status/next-action text below is historical.

# CineNest Package Management

Task CN-KB-PACKAGE-CONFIGURATION-001 · 2026-10-14 (Asia/Colombo). Source [S8 user request](../sources/CN-KB-PACKAGE-CONFIGURATION-001-request.txt). Core decision APPROVED (user-confirmed requirement); supporting design RECOMMENDED unless explicitly marked OPEN_DECISION. Implementation NOT_VERIFIED: no application manifest, package admin, API, database or execution test exists in inspected repository.

## Requirement and scope
**PR-PKG-001 / feature CN-A12:** “Authorized administrators can modify package details, session duration, and prices through the Admin Panel, without editing application code or requiring a deployment for each configuration change.”

This confirms the product capability, not a selected implementation, tested workflow, provider approval or legal authorization. Proposed listing/create/duplicate/edit/preview/publish/schedule/deactivate/archive mechanics below support that capability. Canonical ownership stays in existing proposed Pricing module; no second pricing module, generic rules engine, automatic demand pricing or implied unrelated promotion scope. Historical [ADR-001](../CINENEST-DESIGN-DECISION-ADR-001.md)/[ADR-002](../CINENEST-DESIGN-DECISION-ADR-002.md) remain; [ADR-003](../CINENEST-DESIGN-DECISION-ADR-003.md) records this update.

## Actors and permissions (proposed capability labels)
| Actor | Capability and scope | Separation |
|---|---|---|
| Package viewer | package.read in authorized venue scope | Read drafts/history only if permitted; customer sees eligible published catalog |
| Package editor | package.edit; create/duplicate/edit draft in allowed scope | Edit does not imply publication or policy/physical-capacity modification |
| Publisher | package.publish; explicit allowed venue/room scope | Validates complete revision, schedule and policy before atomic publication |
| Archive/deactivation operator | package.archive / package.deactivate | Must preserve sold versions and respect current checkout policy |
| Auditor | audit.read within scope | Redacted old/new values/reason/version/effective dates; cannot mutate history |
| Operations/policy owner | Separate policy and room configuration capabilities | Controls approved minimum, hours, cleaning and physical occupancy, never through package-edit privilege |

Exact role names, capability allocation, self-publication and two-person approval thresholds remain OD-18. Backend authorization on every read/write/publish and scope mapping; hiding controls is insufficient. Customer only accesses applicable versions and own quotes/purchases.

## Fields and joint validation
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

## Ownership and initial defaults
[Business rules](CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) / [draft JSON](cinenest-draft-rules.json) retain S1/S8-described initial defaults, not immutable code values. Original PDFs are missing; S8 reports a mandatory three-hour minimum, which is retained as a draft constraint pending final source/policy verification. No PDF rewritten or legal approval inferred.

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

## Duration and server quotation model
Proposed initial extension-only model: selected duration ≥ included duration and effective minimum. `effective_min = max(package.minimumSelectableMinutes, operational.minimumSessionMinutes)`; include must also meet operational minimum; maximum ≥ included and effective_min. Selecting fewer minutes than included does not imply proration; unsupported shorter selection rejected until OD-17 resolves a different model. If extensions disabled, selected must equal included. If enabled, extra minutes must be an exact nonnegative multiple of configured increment and selected ≤ maximum. Check venue schedule and [session_end, blocked_until](CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) against reservations/cleaning readiness before quoting or amending.

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

## Proposed administrative lifecycle
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

## Effective versions, scopes and concurrent publishing
Stable packageId owns immutable published packageVersionId/revision and purchased display/inclusion/price terms. Any sold-term edit creates a new revision; publication never destructively edits a referenced revision. Store sales [effectiveFrom,effectiveTo) and separate optional session/service [validFrom,validTo) with explicit venue-local timezone (Asia/Colombo), resolved UTC instants and policy version. Sales time selects new purchase applicability; service-time eligibility independently checks scheduled session. Date-only end boundary resolves to next local midnight exclusive.

Recommended: prevent overlapping sales intervals for the same stable package and intersecting venue/room scope. Joint validation and transactional version token/uniqueness guard reject ambiguity rather than “latest timestamp wins.” A superseding schedule closes prior sales eligibility atomically at cutoff, preserving immutable sold terms and audited prior schedule. More specific room scope does not silently override venue scope; ambiguous mappings rejected until OD-19 chooses precedence. Recheck concurrent policy/room revisions on publish; cached catalog never authorizes an invalid quote.

## Existing purchases and active checkout protections
Each confirmed booking stores package/version reference and purchased display details; included/selected duration, session_start/end and reserved cleaning-buffer snapshot; guest allowance and included/selected services; exact price line items, fees/discounts/final total/currency; accepted booking/refund-policy and consent versions; quote reference. Package name/description edits also cannot rewrite historical tickets/receipts/reports.

Catalog changes cannot silently alter existing bookings, receipts, historical duration/revenue reporting, inclusions or active PINs. Deactivation blocks new sales under defined cutoff; it does not cancel reservations. Sold versions remain referentially intact. Access reads booking/session snapshots, not live package duration.

Recommended quote policy: honor a still-valid authoritative quote with its package/fee/add-on/policy versions even if a new catalog version is published or package is deactivated. After quote expiry, return refreshed terms and require clear customer acceptance before a new attempt. Final policy for emergency withdrawal versus valid-quote honor is OD-20, not an automatic override. Quote/hold expire independently: a valid price quote cannot reserve unavailable inventory.

Payment confirmation validates **accepted quote** and expected amount/currency plus current reservation/eligibility and verified provider evidence; it never silently looks up a newly published rate. A callback arriving late is not proof of a late payment: compare verified capturedAt evidence with accepted quote validity and attempt linkage. Starting checkout in time is not evidence capture completed in time. Evidence requirements/authorization-versus-capture cutoff policy are OD-20. Valid in-time payment with delayed notification still requires safe inventory reconciliation; do not automatically reject solely because callback arrived after quote expiry.

Expired hold/late capture/conflict → durable reconciliation/payment exception and approved refund/support compensation; no duplicate reservation, retroactive reprice or unapproved charge. Dedupe callbacks/ledger by provider/operation ID; replay resolves original purchase references. Database rollback cannot undo external capture.

Purchased booking amendment is a separate authorized workflow: availability and capacity/readiness check → versioned delta quote and customer terms acceptance → payment/refund difference resolution → guarded reservation revision → access-window/notification coordination with durable retries/unknown outcomes. Do not derive amended terms automatically from latest package. Partial external outcomes require compensation/support while protecting previous purchase history. Package publication alone never sends access/IoT commands, extends a PIN, interrupts an active session or drops buffer/conflict guards.

## Proposed architecture/API and audit
Pricing owns stable catalog identities, immutable versions, applicability schedule and quote construction. Booking owns reservation/confirmed snapshot; Venues/Rooms own operational constraints/capacity; Identity & Access handles capabilities; Payments validates accepted quote and ledger; Access uses purchased session; Audit preserves evidence. No final tables, API routes, migrations, module additions or installed stack claimed. [Contracts](../architecture/CINENEST-DATA-AND-API-CONTRACTS.md) defines proposed capabilities: list/detail/history, create/duplicate draft, update with version, preview, publish/schedule, deactivate/archive, resolve applicable version and quote.

Persist atomic publication/audit intent; invalidate scoped catalog caches/durable projections without per-edit code release. Request idempotency and concurrent version tokens protect mutation retry/publish schedules. Audit actor, authorized scope, timestamp, reason, old/new values, stable package/version IDs, effective boundaries and policy references; no PII/PIN/card data. Booking amendment has its own audit and agreed difference. Exact table/ETag/event designs RECOMMENDED, not approved core capability.

## Future implementation acceptance (not tests run here)
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

## Open decisions and provenance
[OD-17–OD-22](../decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md) track proration/rounding/zero/taxes, price and two-person thresholds, scope/version precedence, quote/deactivation/capture timing, minimum-policy ownership and shared-fee/add-on overrides. S8 confirms core admin editability only; S1/S8 defaults remain initial draft values. Original PDFs missing and no production configuration exists. Documentation validation cannot establish these acceptance outcomes or hardware/legal approval.

## Bank payment — CN-KB-BANK-PAYMENT-001
Package editability CN-A12/PR-PKG-001 and existing approved/no-release requirement are unchanged. [Bank payment](CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) snapshots package/version/display/session/buffer/guests/inclusions/extras/item lines/consent/policy/quote and receiving-instruction version into request. Bank quotes preserve applicable explicitly approved method fee treatment; the online LKR 100 initial example does not decide bank fee (OD-25).

The earlier capturedAt/timing discussion applies to gateway. Manual timeliness distinguishes declared initiated time, actual bank credit, submission and authorized verification; selected authoritative clock is OD-24, not implicitly review time. Accepted quote/currency remains protected from catalog changes while valid; expiry/re-quote acceptance and independent received-fund traceability follow the method policy. Bank review/verification never modifies packages, cancels sold terms, auto-amends bookings or extends PINs. New bank-funded purchased amendments still use the existing separate guarded quote/payment/refund/access workflow, without assuming a stored card.
