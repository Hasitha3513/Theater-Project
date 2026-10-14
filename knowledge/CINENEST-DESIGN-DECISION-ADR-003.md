# ADR-003: User-confirmed Admin Package Configuration

Task CN-KB-PACKAGE-CONFIGURATION-001 · 2026-10-14. Source S8. Decision APPROVED for PR-PKG-001 / CN-A12 core requirement; RECOMMENDED for supporting publication, quote and implementation design. Implementation NOT_VERIFIED. ADR-001/002 historical decisions retained.

## Decision and provenance
User confirms authorized administrators can modify package details, customer session duration and prices via Admin Panel without code edits or a deployment per configuration change. [Canonical package management](product/CINENEST-PACKAGE-MANAGEMENT.md) owns this topic within existing proposed Pricing boundaries; Packages & Pricing is a proposed subsection of System Settings, not a new module/verified route or independent promotion engine.

## Defaults and invariant protection
Source-described three-hour/base/guest/extension/add-on/fee/buffer values remain initial defaults. Package settings cannot override separate operational minimum-duration policy, cleaning requirements or approved physical capacity. A shorter package requires authorized operational policy and customer terms changes. Core editability does not imply approval of proration, zero prices, dynamic pricing or tax/threshold choices.

## Supporting recommendations
Immutable published versions; joint validation/atomic publication; explicit scoped sales versus service windows; valid accepted quote honor; historical purchase snapshots; separate purchased amendment and access coordination. These designs are recommendations, not implemented/automatically approved requirements. Existing payment revalidation is clarified to accepted quote amount/currency and current inventory eligibility, not latest catalog price. No catalog edit changes booked terms/PINs or sends device commands. Conflicts/options are OD-17–22; missing PDFs/legal/hardware validation unchanged.

## Consequences and acceptance
Existing feature IDs retained; CN-A12 added. [Package acceptance scenarios](product/CINENEST-PACKAGE-MANAGEMENT.md) are future implementation requirements. KB 1.0.0 → 1.1.0 is a material additive documentation revision; token/chart versions and bytes are unchanged. A no-change rerun does not duplicate IDs/sections or bump versions. No application/runtime/migration/dependency/deployment/device changes.
