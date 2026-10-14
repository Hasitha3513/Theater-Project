> **SUPERSEDED — historical snapshot retained pending owner deletion review.** Current canonical content: [open section](DESIGN.md#adr-001). Do not update this file; update the four canonical documents in place. Older status/next-action text below is historical.

# ADR-001: Unified CineNest Hybrid UI

> Status: approved hybrid visual direction; detailed specifications below are proposed implementation guidance. The supplied knowledge pack, architecture, requirements, and rules/conditions are absent from this workspace. Reconcile them before implementing affected functionality. No backend, frontend, roles, providers, or operational statistics have been verified.
> Recorded: 2026-10-13. Scope: knowledge base only. Maintainer: CineNest project design/architecture owners.

## Decision
Accept the user's approval of combining both admin references into one coherent admin interface and retaining the customer cinematic theme. Approval covers visual direction; this ADR does not assert approval of missing business policies or exact proposed HEX values. Adopt Mockup-1's operational dashboard/sidebar, Showcase-2's room cards, device details and health visibility, and the customer reference's dark hero/light booking panels. All future interfaces must use shared semantic tokens and components in [design system](CINENEST-DESIGN-SYSTEM.md).

## Context and alternatives
The two admin composites overlap but differ in dashboard hero prominence, room state labels, live-control arrangement and analytics naming. Copying either alone loses useful approved features; allowing independent page styling creates inconsistent state/control language. A unified shell/component model preserves both references and supports customer identity without applying dark backgrounds to every form.

## Consequences
Use operational information before decorative hero content; hero is optional and never replaces status. Canonical labels: Customer Management (mockups “Users”), Analytics and Reports (mockups “Analytics”/“Reports”), Room Live Control nested under Rooms. Use consistent room cards and independent room/device states. Five customer booking steps are stable across all screens. Preserve charts but define their actual metrics and backend data sources. Room control presents context, requested state and observed state separately. Responsive behavior and accessibility can alter exact screenshot density/spacing.

## Priority and conflict resolution
1. Confirmed business, safety, privacy and legal requirements and existing approved architecture govern behavior.
2. This approved hybrid direction and shared accessibility acceptance target govern visual consistency.
3. Proposed detailed tokens/contracts govern only until reconciled with missing authoritative sources.
4. Screenshot details are illustrative, not operational policy or truth.

No source documents were found to verify level 1. Record unresolved decisions in [traceability](CINENEST-REFERENCE-TRACEABILITY.md); do not invent architecture compliance. Security-sensitive actions must remain unimplemented until backend authorization, device safety, identity handling and payment reconciliation rules are confirmed. Approving a visual does not authorize unsafe automation.

## Change and publication governance
Before implementation, design/architecture owners attach knowledge-pack and source versions, map proposed capabilities/contracts to actual services, approve privacy/safety/finance decisions, and reconcile exact tokens. Design-system changes update token JSON first, regenerate theme, update chart specs and affected page specs, run [validation](validate_design_kb.py) and complete [acceptance](CINENEST-UX-ACCEPTANCE-CHECKLIST.md). A material change to this hybrid direction requires a superseding ADR linked here; preserve history.

The user supplied the canonical repository https://github.com/Hasitha3513/Theater-Project.git on 2026-10-13. A separate clone under repository/ confirms it is empty (no commits or files). Its initial branch is main and origin points to that URL. No AGENTS.md or commit/publication conventions exist in the remote yet. Preserve the original workspace and its read-only .git placeholder; use the separate clone for Git operations. The user explicitly authorized commit and push. Reuse the existing local Git author configuration for the initial knowledge-only commit. Do not invent an identity, copy unrelated repository policies, or publish externally without authorization. Report commit/push results accurately. This ADR records the pre-publication decision; inspect Git history and the task completion report for the resulting commit and push evidence.

## Acceptance and follow-up
Documentation and machine-readable checks can pass now. Full architecture reconciliation, implementation QA, source-pack approval remain outstanding; initial publication is authorized. See [validation report](CINENEST-VALIDATION-REPORT.md). Scope contains no new frontend features.

## Historical follow-up — 2026-10-14
Decision status: APPROVED (hybrid visual direction only). Implementation: NOT_VERIFIED. Initial publication is now evidenced locally by commit 16710dcf3e3bfb2453bab24910890279691fc06d and its origin/main tracking ref at inspection; the initial empty-clone narrative above is historical. CN-KB-FEATURES-TECH-001 adds features, draft rules and architecture recommendations. [ADR-002](CINENEST-DESIGN-DECISION-ADR-002.md) records explicit PIN-window/metric clarifications and preserves active tokens amid a source-palette conflict. It does not supersede hybrid direction approval or invent approved technology.
