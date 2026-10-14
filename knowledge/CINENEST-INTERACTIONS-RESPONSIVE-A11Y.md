> **SUPERSEDED — historical snapshot retained pending owner deletion review.** Current canonical content: [open section](DESIGN.md#interactions-a11y). Do not update this file; update the four canonical documents in place. Older status/next-action text below is historical.

# CineNest Interactions Responsive Design and Accessibility

CN-KB-FEATURES-TECH-001 · 2026-10-14. Decision: DOCUMENTED_DRAFT. Implementation: NOT_VERIFIED. Source S1 and existing visual/page guidance. No rendered application exists.

## Canonical shared references
[Design system](CINENEST-DESIGN-SYSTEM.md) defines tokens/typography/spacing/breakpoints; [admin](CINENEST-ADMIN-PAGES.md) and [customer](CINENEST-CUSTOMER-BOOKING-PAGES.md) define pages; [business lifecycles](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) define behavior. Reuse shared shell/components with role-specific navigation, not divergent page styles.

## Interaction states and safe feedback
Each request has initial/loading/complete/empty/error/denied/stale states. Async writes keep requested/pending/result distinct; button loading prevents duplicate submission while preserving label/focus. Success comes from authoritative resource state, not browser navigation. Failed transport shows unknown outcome/status lookup before retry. Use safe correlation IDs and actionable messages, no raw provider payload. Skeletons retain geometry; background refresh preserves previous data with freshness.

Hold countdown comes from server expiresAt with accessible non-ticking status announcements; warn before expiry and permit extension only by server policy. An expired hold revalidates availability/quote, never silently confirms late payment. Forms have persistent labels, fieldset/legend, descriptions, inline errors and focusable error summary. Preserve safe draft inputs across reauthentication, never evidence/PINs/tokens in query strings or generic storage.

Dialogs trap/restore focus; Escape closes safe dialogs, destructive actions explain consequences/reason. Confirmed booking with access pending, payment unknown, device offline, cleaning_required and physical occupancy unknown must be distinguishable with text/icons. Do not infer OFF from telemetry absence or success from device command dispatch. PIN reveal/copy is explicit and owner-scoped, masked by default; no automatic screen-reader announcement of full credential.

## Responsive acceptance
Use existing token breakpoints base/<640, sm/640, md/768, lg/1024, xl/1280, xxl/1536. Below lg sidebar becomes focus-managed drawer; KPI 1/2/4 columns base/sm/lg, room cards 1/2/3 base/md/xl. Summary/payment stack below md; <sm stepper gives current label/progress with all steps available. Dense ledgers/audit charts offer labelled scroll/data-table alternatives instead of hiding essential information. Preserve room-card/table parity. Mobile time bands/amount/currency/status stay unambiguous. Sticky controls cannot cover terms/errors/focus; safe-area and landscape checked.

## Accessibility acceptance target
WCAG 2.2 AA is a desired acceptance target, not certification. Keyboard-only critical flows, visible focus, semantic landmarks/one H1, skip link, logical order, labelled controls, ≥44px targets, appropriate text/nontext contrast, reduced motion, captions/alternatives and screen-reader announcements. Tables retain scope/caption/aria-sort; charts supply equivalent scoped data tables/non-color series cues. No hover-only action. Login/upload/payment provider flow must be accessible or have approved alternative/support. Test 320–1536px plus 200%/400% zoom/reflow; image/gradient/control/focus contrast measured in rendered states.

## Future evidence (not run here)
Booking conflict/hold expiry, quote revision, upload ambiguity/manual review, payment duplicate/pending/late capture, access installation/delivery failure, extension conflict and offline device control need keyboard/screen-reader and real backend/hardware tests. [Acceptance checklist](CINENEST-UX-ACCEPTANCE-CHECKLIST.md) and [delivery phases](delivery/CINENEST-IMPLEMENTATION-PHASES-AND-ACCEPTANCE.md) capture continuous gates.
