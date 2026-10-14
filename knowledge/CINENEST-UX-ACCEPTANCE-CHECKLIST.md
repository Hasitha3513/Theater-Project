# CineNest UX Acceptance Checklist

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Original record: 2026-10-13. Updated: 2026-10-14, CN-KB-FEATURES-TECH-001. Decision: APPROVED for hybrid direction; DOCUMENTED_DRAFT for detailed behavior. Implementation: NOT_VERIFIED. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Knowledge-base acceptance (this task)
- [x] All requested documentation, token JSON, chart JSON and theme CSS exist.
- [x] Eleven admin modules and twelve customer destinations/flows are specified.
- [x] Ten chart/panel specifications currently have calculations, contracts and display guidance (bank extension included; historical task counts remain in changelog).
- [x] Shared typography, semantic colors, spacing, controls, navigation, tables and states documented.
- [x] Breakpoints, mobile layouts, chart/table alternatives and zoom/reflow targets documented.
- [x] Accessibility target and keyboard, contrast, focus, motion and screen-reader requirements documented.
- [x] Original three available references retained byte-for-byte with recorded hashes.
- [x] Screenshot numbers/PINs/identity data explicitly excluded from operational truth.
- [x] JSON parsing, semantic token references, local documentation links, CSS/token consistency and selected contrast pairs checked by [validator](validate_design_kb.py).
- [x] No frontend feature/source code introduced; theme is an unintegrated specification asset.
- [ ] Reconcile knowledge pack exact tokens, architecture, requirements and conditions (not supplied).
- [ ] Verify exact unavailable customer “(1)” source equivalence if needed.
- [x] Clone user-supplied canonical Git repository into a separate folder; verify it is empty.
- [x] Resolve author identity from existing local Git configuration and review staged knowledge-only diff.
- [x] Initial publication commit 16710dc verified locally with matching origin/main ref at baseline inspection. New task publication is reported separately.

See [validation report](CINENEST-VALIDATION-REPORT.md) for evidence and limits. Checked boxes describe documentation checks, not implemented UI compliance.

## Future frontend implementation gates
- [ ] Actual role/capability mapping and server ownership/tenant scope tested on every read, action and export.
- [ ] No literal component colors outside centralized token generation; semantic themes and chart aliases synchronized.
- [ ] All admin/customer screens match shared shell/components, state language and canonical stepper.
- [ ] Full keyboard flow, logical landmarks/headings, skip link, focus return/traps and 44px controls tested.
- [ ] Rendered text/gradient/image/control/focus/chart contrast meets target; automated scans plus manual QA.
- [ ] 320–1536px, landscape, 200%/400% zoom, reduced motion, screen-reader booking and live control verified.
- [ ] Forms retain safe data, expose error summary/inline labels and tolerate session/network failures.
- [ ] Charts match authorized backend aggregates, complete/null/zero semantics, currency and timezone definitions.
- [ ] Metric fixtures cover duplicate events, refunds, negative revenue, zero denominator, interval overlap, blackouts, DST, half-open boundaries and partial periods.
- [ ] Financial chart ledger semantics approved; no accounting-profit implication from operational revenue.
- [ ] Payment duplication/timeouts/webhook delays, hold expiry, quote change and paid-but-unconfirmed recovery tested.
- [ ] Identity policy, accepted inputs, age, consent, retention, restricted preview and manual-review support approved.
- [ ] PIN generation/validity/reveal/expiry, privacy and support policy confirmed; no sensitive data in logs/exports/shared artifacts.
- [ ] IoT stale/unknown states, command acknowledgment, timeout/retry and idempotency tested against real device contract.
- [ ] Lock/restart operations follow approved safety rules; emergency egress independent of network/UI confirmed by responsible owner.
- [ ] Promotions/referral/loyalty rules and reversals derive from authorized backend ledger.
- [ ] Loading/no-records/no-matches/denied/not-configured/stale/error states tested independently.
- [ ] No screenshot sample data reaches production; fixtures labelled synthetic and isolated.
- [ ] Design/architecture owners resolve traceability conflicts; ADR and published KB versions recorded.

## CN-KB-FEATURES-TECH-001 documentation and future gates
- [x] Product, rules, stack, architecture, contracts, IoT, privacy, phases and decision register authored with separate decision/implementation statuses.
- [x] Stable feature IDs, sources, proposed modules, permissions and acceptance recorded.
- [x] Active design tokens/CSS and original image bytes preserved; candidate palette conflict explicit, not applied.
- [x] Exact draft time/price/buffer/PIN rules centralized; unresolved hold/closing/all-guest policy explicit.
- [x] Net collections separate from recognized revenue; consistent donut denominator and payment/add-on panels documented.
- [ ] Verify missing PDFs, including relevant image-based/Sinhala pages, and reconcile discrepancies.
- [ ] Select/approve stack, providers, hardware and actual authorization/data implementation with evidence.
- [ ] Approve privacy/retention, all-guest checks, staffing/physical cleaning/emergency intervention, rights, refunds and hold/closing policy before launch.
- [ ] Execute lifecycle/race/refund/webhook/command/offline acceptance against actual backend and hardware; documentation validation is not implementation proof.

## Package configuration — CN-KB-PACKAGE-CONFIGURATION-001
- [x] PR-PKG-001/CN-A12 explicitly user-confirmed; implementation NOT_VERIFIED and supporting publication/API design RECOMMENDED.
- [x] Initial defaults classified by package/shared Pricing/operational ownership; canonical package spec linked across affected docs.
- [x] Active design/image/chart definitions preserved; customer UI consumes backend versioned configuration and purchased history.
- [ ] Execute PK-AC01–13: authorized no-release changes, scope denial, invalid joint publish, separate minimum/capacity, deterministic effective versions, valid/expired quote/payment reconciliation, no double charge, sold history and safe amendments/access.
- [ ] Approve OD-17–22 choices before affected implementation; doc validators do not establish product/hardware outcomes.

## Bank payment — CN-KB-BANK-PAYMENT-001
- [x] CN-C10/CN-A13 / PR-BANK-001 USER-CONFIRMED, core APPROVED and implementation NOT_VERIFIED; supporting recommendations/open method policies distinct.
- [x] [Canonical bank spec](product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) linked: receipts, verified funds, allocations, request, booking, access, refund states separate; online checkout/package protections retained.
- [x] Customer/admin under-review, correction/expiry/amount exceptions and protected bank instruction/evidence UI documented with existing tokens/a11y; claims not collections.
- [ ] Execute BP-AC01–13 with actual receiving bank records/backend/authorization and hardware outcomes as applicable; notification failure must retain queue work.
- [ ] Approve review/hold/payment timing/fee/upload/retention/matching/account/refund/staffing choices OD-23–29; no invented 24-hour rule/automatic extension/card token.
