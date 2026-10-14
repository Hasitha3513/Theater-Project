# CineNest Customer Booking Page Specifications

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Original record: 2026-10-13. Updated: 2026-10-14, CN-KB-FEATURES-TECH-001. Decision: APPROVED for hybrid direction; DOCUMENTED_DRAFT for detailed behavior. Implementation: NOT_VERIFIED. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Shared customer flow
Use [design system](CINENEST-DESIGN-SYSTEM.md) and [reference decisions](CINENEST-DESIGN-DECISION-ADR-001.md). Maintain cinematic public/account shell and readable light booking panels. The consistent stepper is **1 Select Room → 2 Date & Time → 3 Verify → 4 Payment → 5 Confirmation**. Authentication is a prerequisite when needed, summary is part of Payment, and PIN is a secure post-confirmation subview. Fix duplicate/misnumbered steps shown in the composite reference.

Server-authoritative draft/hold → verified eligibility → quoted payment → confirmed booking is a proposed flow; actual lifecycle must be reconciled with domain rules. Steps cannot bypass backend authorization or policy. Safe Back/Edit preserves only allowed draft data; slot/price changes invalidate quotes. Page refresh resolves current state from server. Customer scope is the signed-in owner; admin support is a separate permissioned flow. All screens require keyboard, loading, empty, validation, network-error and session-expiry handling. Never use screenshot names, statistics, identity documents, PINs or transaction values as operational data.

## Landing Page

**Layout and components:** Dark cinema photography with readable scrim, gold logo, short hero, violet Book now, amenities, room previews, how-it-works and support/footer. Header shows Google sign-in or account.

**Interactions, data, permissions and safeguards:** Browse rooms without mandatory sign-in; booking continues into the five-stage flow. Content claims, venue, amenities and pricing are backend/CMS approved, not copied screenshot facts.

**Responsive, accessibility, error and empty behavior:** Room photos use alternatives; header becomes mobile drawer, hero text scales, CTA remains visible without covering copy. Image failure uses cinematic surface; unavailable catalog shows retry/support.

## Google Authentication

**Layout and components:** Provider-compliant Google sign-in button and a concise explanation of account use; return destination and privacy links.

**Interactions, data, permissions and safeguards:** Use configured identity integration; server verifies identity and establishes secure session. Handle popup blocked, cancelled consent, callback/state failure, session expiry and account linking safely; do not store OAuth tokens in URLs or local logs. Preserve safe room/slot draft on sign-in return. Alternate recovery/support depends on architecture.

**Responsive, accessibility, error and empty behavior:** Keyboard and accessible provider flow; disable duplicate requests, show pending; mobile redirect supported by chosen provider. Authentication does not imply age/identity verification.

## Cinema Room Selection

**Layout and components:** Stage 1 Select Room: light panel inside cinematic shell; room/package photo cards, room name, server-approved capacity/amenities and applicable package inclusions, backend price/currency/duration choices, availability and select CTA.

**Interactions, data, permissions and safeguards:** Selected room has border, label and radio semantics; Continue uses server room ID. Popular badge only when derived from a defined ranking/window; unavailable rooms state reason. Room rates are indicative until quote.

**Responsive, accessibility, error and empty behavior:** 1/2/3 card grid; essential terms and capacity remain visible; empty catalog, maintenance and catalog failure distinct. Guest count cannot exceed server capacity.

## Date and Time Selection

**Layout and components:** Stage 2 Date & Time: locale calendar, day selector, timezone label, available slot chips, duration/guest inputs and sticky-safe quote summary.

**Interactions, data, permissions and safeguards:** Read live availability; hold inventory only by server contract. Slot chosen by ID and exact start/end, not client wall-time string. Include operating windows, buffers and blackouts from backend. Revalidate when continuing; show hold expiration and accessible warning. Extensions require new availability/quote.

**Responsive, accessibility, error and empty behavior:** Calendar keyboard arrows and labelled disabled days; slot radio groups. Stack summary below md. No slots suggests permitted alternate day/room; conflict reselects; network failure never declares sold out.

## Identity Verification

**Layout and components:** Stage 3 Verify: policy explanation, required document type/input, restricted preview, verification progress/result and Continue.

**Interactions, data, permissions and safeguards:** S1 documents draft 18+ for all guests and booker NIC/passport processing; on-site original-document checks for every guest require a defined process. Driving licence shown in mockup is not a selected accepted document. Final eligibility, upload limits, consent, retention and review policy remain open. Server determines eligibility. Do not expose full ID number, birth date or document image unnecessarily. Delete draft preview after secure submission; no public storage links. Pending review cannot silently advance to a paid eligible booking.

**Responsive, accessibility, error and empty behavior:** Accessible file input with upload alternative/support when policy allows; mobile camera optional. Clear rejection reason and safe retry; manual-review/pending/expired/network states distinct. Preview masked by default and permission scoped.

## Booking Summary

**Layout and components:** Persistent summary at payment stage: room, local date/time/timezone, duration, guests, itemized base/extras/discount/tax/fees, currency and server total, hold expiry and applicable cancellation terms.

**Interactions, data, permissions and safeguards:** Use versioned server quote; extras such as gaming or extra hour appear only when supported. Editing room/slot/extras invalidates quote and rechecks availability. Require explicit terms consent if policy mandates; disclose full payable total before payment. Screenshot totals and fees are illustrative.

**Responsive, accessibility, error and empty behavior:** Two columns ≥md, stacked below; total visible with terms, no overlapping footer. Quote stale/changed shows before/after and explicit acceptance; no hardcoded amounts.

## Payment Processing

**Layout and components:** Stage 4 Payment: booking summary and methods actually enabled by server/provider, primary Pay with formatted amount, secure provider handoff and progress.

**Interactions, data, permissions and safeguards:** PayHere, cards, bank transfer, Apple/Google Pay in reference are options to validate, not approved integrations. Use provider tokenization/hosted form; UI does not collect/store raw card credentials. Create idempotent payment against quote/hold. Server webhook/reconciliation determines payment result; redirect alone cannot confirm success. Payment captured but booking unavailable requires exception/reconciliation path, not false confirmation.

**Responsive, accessibility, error and empty behavior:** Accessible provider iframe/redirect and return path; pending/cancelled/failed/timeout show status lookup and support. Prevent duplicate charge. Mobile summary collapses only with total and essential terms still visible.

## Booking Confirmation

**Layout and components:** Stage 5 Confirmation: success only after server-confirmed booking/payment policy, booking ID, room, schedule/timezone, guests, paid amount and next steps.

**Interactions, data, permissions and safeguards:** Pending reconciliation gets explicit processing view. Ticket download/calendar include no PIN or identity documents by default; share is safe/redacted and opt-in. Calendar timestamps use real instant with timezone; receipt details come from backend. Download failures do not cancel booking.

**Responsive, accessibility, error and empty behavior:** Stack details below md; heading/status announced once. Confirmation refresh must recover server booking by authorized ID; inaccessible/nonexistent booking returns safe error.

## Smart Door Access PIN

**Layout and components:** Secure subview on confirmation/dashboard with masked credential, intentional Reveal/Hide and Copy, server validity window, room instructions and support.

**Interactions, data, permissions and safeguards:** Draft PIN entry validity is [scheduled session start, session end), replacing the mockup pre-entry suggestion via ADR-002. Length, generation, rotation, revocation/re-entry, rate limits and offline compatibility remain open. Never adopt sample 482915. Reveal requires authorized owner/session and server eligibility; no raw PIN in URLs, exports, chart events, ordinary notifications, shared tickets or logs. SMS/email should deliver protected access links/instructions; raw-secret delivery requires a separate security decision. Clipboard copy explicit with warning about shared devices; conceal on session end. Unlock success requires actual device response.

**Responsive, accessibility, error and empty behavior:** Readable masked/reveal controls and text explanation; do not announce full PIN automatically. Outside validity: unavailable reason and window. Device/network failure shows approved support path, never an invented bypass; emergency egress remains independently governed.

## Customer Dashboard

**Layout and components:** Cinematic header/sidebar, greeting, upcoming/past tabs, booking cards, loyalty balance and referral banner only when enabled.

**Interactions, data, permissions and safeguards:** Owner-scoped server bookings; show room, schedule, state, summary and View. PIN is masked and eligibility-gated. Modify/cancel eligibility and fees come from server policy; re-quote amendments. Loyalty and referral placeholders are omitted when feature unavailable.

**Responsive, accessibility, error and empty behavior:** Sidebar becomes drawer below lg; cards stack; zero bookings offers Browse rooms; partial loyalty failure does not hide bookings. Expired session returns to auth preserving safe destination.

## Booking History

**Layout and components:** Account destination with range/state/room filters, paginated list and detail/receipt.

**Interactions, data, permissions and safeguards:** Record ID, room, date/time/timezone, amount/currency, booking and payment/refund state separately; owner authorization on every query/download. Retention limits labelled; paid cancellation is not automatically refunded.

**Responsive, accessibility, error and empty behavior:** Compact cards <md or labelled horizontal table; filter reset for no matches, first-booking CTA for empty history, retry for service failure; downloads accessible and redacted.

## Loyalty and Referrals

**Layout and components:** Balance panel, earned/pending/redeemed ledger, referral link, qualification rules, redemption options and progress when enabled.

**Interactions, data, permissions and safeguards:** Backend calculates points, expiry, reversals and qualifying referrals. Do not copy 320 points or invent reward rates. Share/copy is intentional; no automatic messages. Reward discounts re-quote booking and respect eligibility/caps; referral attribution is privacy limited.

**Responsive, accessibility, error and empty behavior:** Balance uses text, not color alone; ledger scroll/cards. No eligible rewards, expired points, disqualified referral and service failure clearly distinguished. Disabled feature has no misleading active CTA.

## Draft business behavior and source traceability
[Feature catalog](product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md) CN-C01–CN-C09 and [business rules/lifecycles](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) are canonical. Display capacity/guest surcharge, whole-hour extensions, optional PS4, online fee, itemized total, server hold expiry and purchase/refund-policy versions; do not duplicate price constants in page code. Illustrative total with selected PS4 must use the canonical example, not the inconsistent mockup amount.

Sign-in is separate from explicit policy consent and verification. OCR extracts text only; uncertain documents need restricted manual review. Pending payment, confirmed booking, pending/installed access and delivery failure are separate customer messages. Confirmation must not imply PIN installation or guaranteed entry. Access window excludes the cleaning buffer. Modifications/extensions re-quote and recheck inventory, payment and access window revisions; late payment and refunds expose reconciliation status/support. Five-minute warning and checkout/exit events never imply everybody left or physical cleaning completed. Loyalty/referral rates remain OPEN_DECISION and hidden/disabled until supported.

## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
[Package specification](product/CINENEST-PACKAGE-MANAGEMENT.md) owns configurable catalog/duration/price behavior. Room/package cards and supported duration choices resolve applicable published package/service/venue configuration from backend. No production component hardcodes “3 hours” or a base price; existing values are labeled initial examples only. Display customer-visible inclusions and optional charged add-ons clearly; included extras are not charged again.

Booking Summary/Payment use authoritative accepted quote/version/line items, expiry and policy references. A catalog update does not silently replace a valid quote; expired quote refresh exposes before/after terms for explicit acceptance. Reservation hold and price quote validity are separate. Confirmation/history/receipts show purchased package/version/display/duration/guest/inclusion/price/policy snapshots, not current catalog. Existing booking changes go through authorized amendment workflow, inventory/cleaning/payment checks and access/notification coordination. Keep existing cinematic shell, stepper, responsive behavior and accessibility rules.
