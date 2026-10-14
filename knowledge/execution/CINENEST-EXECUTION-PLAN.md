> **SUPERSEDED — historical snapshot retained pending owner deletion review.** Current canonical content: [open section](../PROGRESS.md#execution-plan). Do not update this file; update the four canonical documents in place. Older status/next-action text below is historical.

# CineNest Execution Plan

CN-DEC-001 · 2026-10-14. Canonical backlog [change-sets.json](change-sets.json). Setup completed; S11 authorized the initial decision brief only, now VERIFIED documentation / CLOSED. All other future units remain PROPOSED / BLOCKED; future implementation plan NOT_APPROVED. [Prepared proposal](../decisions/CN-DEC-001-INITIAL-STACK-BRIEF.md) is awaiting explicit owner approval, not an accepted stack.

## Readiness and selection
READY = prerequisites + bounded acceptance/checks + tools + applicable task authority + all necessary decisions. Future outlines must be refined to actual selected-stack files/test/migration/rollback scope before READY; listed proposed paths/controls are not final tables/routes/frameworks. No whole-plan approval by implication. Default one change set per session. Current checkpoint ends after CN-DEC-001. Next single action: **owner approval response to the prepared CN-DEC-001 brief**. CN-FND-001 remains BLOCKED until stack acceptance and separate refined implementation authorization. Spring Modulith remains recommendation, NestJS alternative; frontend/database/cloud/provider choices keep actual status.

Every vertical slice carries associated UI/backend scope checks, notifications/failure/reconciliation/support and tests rather than postponing them to phase 11. Phases 1–12 here reflect delivery dependencies, not schedules or completion. No optional requirement removed/deferred without user approval. One writer controls shared status; no invented atomic work claim.

## CN-AGENT-SETUP-001 — Execution workflow setup

| Field | Specification |
|---|---|
| Phase | 0 |
| Priority | P0 |
| Linked requirements | CN-AGENT-WORKFLOW-001, PR-UI-001, PR-PKG-001, PR-BANK-001 |
| In scope | Repository instructions, charter, complete available-source review, requirement/test mapping, blocked backlog, status/evidence/handoff |
| Out of scope | No app bootstrap, stack/policy decision, live external operation |
| Dependencies | None |
| Blocking decisions | None |
| Files/modules | AGENTS.md; docs/agents; knowledge/execution; related indexes/source register/manifest |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Documentation links/IDs/hashes/coverage, baseline KB validator, fresh read-only discovery and bounded review |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | All setup acceptance checks recorded; product still unimplemented; one exact next action |
| Approval | Explicit user S10 and current setup request |
| Readiness | CLOSED (was READY under explicit setup authorization) |
| Work status | VERIFIED (documentation and explicit discovery only) |

## CN-DEC-001 — Bounded initial-stack decision brief

| Field | Specification |
|---|---|
| Phase | 1 |
| Priority | P0 |
| Linked requirements | CN-AGENT-WORKFLOW-001, PR-UI-001, PR-PKG-001, PR-BANK-001 |
| In scope | Compare initial backend/frontend/transactional storage recommendations to official supported docs and required module/safety boundaries; prepare proposed selection ADR for user |
| Out of scope | No installing/scaffolding, cloud/provider/hardware choice or automatic final stack approval |
| Dependencies | CN-AGENT-SETUP-001 |
| Blocking decisions | None to prepare authorized brief; OD-01 awaits owner acceptance before code |
| Files/modules | architecture stack/boundaries; new proposed ADR; execution evidence |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Official sources/date, criterion matrix, approved requirements and risk/gate review |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | One bounded proposed stack choice/alternatives and impact; user decision needed before code |
| Approval | S11 authorizes brief only; stack acceptance and implementation remain pending |
| Readiness | CLOSED (was READY under S11 brief authorization) |
| Work status | VERIFIED documentation; proposal AWAITING OWNER APPROVAL |

## CN-GATE-001 — Source and accountable policy gate brief

| Field | Specification |
|---|---|
| Phase | 1 |
| Priority | P0 |
| Linked requirements | CN-NFR-02, CN-NFR-05, CN-C05 |
| In scope | Obtain missing sources and record named privacy/admission/operating/safety decision owners with required evidence |
| Out of scope | No final legal/admission/privacy/deadline policy assumed |
| Dependencies | CN-AGENT-SETUP-001 |
| Blocking decisions | OD-03, OD-04, OD-05, OD-15 |
| Files/modules | sources; security; existing open-decision register |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Actual source page review, conflicts and owner/evidence mapping |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Missing/available sources and accountable launch gates explicit |
| Approval | User authorization + supplied sources/owners; qualified acceptance remains external |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-FND-001 — Selected-stack repository skeleton and CI

| Field | Specification |
|---|---|
| Phase | 1 |
| Priority | P0 |
| Linked requirements | PR-UI-001, PR-PKG-001, PR-BANK-001, CN-NFR-02, CN-NFR-03 |
| In scope | Only approved local project/module skeleton, safe build/test and scoped CI definition after stack selection |
| Out of scope | No production/cloud provisioning, provider setup or business feature |
| Dependencies | CN-DEC-001 |
| Blocking decisions | OD-01 |
| Files/modules | Future source/build/tests/CI paths TBD after approved ADR |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Actual selected build/lint/unit/module-boundary commands, no production data |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Repeatable approved local skeleton and test evidence; no feature-complete claim |
| Approval | Explicit implementation unit approval and accepted stack ADR |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-UI-001 — Shared shell and token consumers

| Field | Specification |
|---|---|
| Phase | 2 |
| Priority | P1 |
| Linked requirements | PR-UI-001, CN-A04, CN-NFR-01 |
| In scope | First admin/customer shell and reusable control/card/table primitives, only labelled fixtures |
| Out of scope | No backend business implementation or new palette/library without decision |
| Dependencies | CN-FND-001 |
| Blocking decisions | OD-11 |
| Files/modules | Future UI components; design tokens unchanged unless authorized |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Rendered mobile/reflow/keyboard/contrast and card-table parity |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Approved hybrid reproduced with usable primitives and DEMO-only fixtures |
| Approval | Explicit UI slice approval and component/palette decisions |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-AUTH-001 — Identity/session and resource scope skeleton

| Field | Specification |
|---|---|
| Phase | 2 |
| Priority | P1 |
| Linked requirements | CN-C02, CN-A06, CN-NFR-02 |
| In scope | First owner/venue scoped session/auth interfaces and permitted account read |
| Out of scope | No real OAuth secrets/users/MFA policy chosen |
| Dependencies | CN-FND-001 |
| Blocking decisions | OD-01, OD-12 |
| Files/modules | Identity & Access/UI/auth contracts TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Session/CSRF/revoke/object-scope denial tests with isolated data |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Owner/scope guards demonstrable; admin policy gaps visible |
| Approval | Slice + identity/role/session choices approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-ROOM-001 — Catalog and scoped room configuration

| Field | Specification |
|---|---|
| Phase | 2 |
| Priority | P1 |
| Linked requirements | CN-C01, CN-A03, CN-A04 |
| In scope | First room/catalog/capacity/config read/edit slice using approved operations model and identical card/table query |
| Out of scope | No hardware mapping changes or rights/safety approval inferred |
| Dependencies | CN-AUTH-001, CN-UI-001 |
| Blocking decisions | OD-04, OD-10, OD-14 |
| Files/modules | Venues/Rooms and catalog UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Config permission/version and catalog/empty/error/room-view parity |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Room settings cannot rewrite purchases or certify safety |
| Approval | Slice + venue/room/operating scope approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-PKG-001 — Package drafts and scoped editor

| Field | Specification |
|---|---|
| Phase | 3 |
| Priority | P1 |
| Linked requirements | PR-PKG-001, CN-A12, PK-AC01, PK-AC02, PK-AC03 |
| In scope | Bounded draft package model/edit/preview and separate publish privilege metadata |
| Out of scope | No selling/publishing unfinished policy, demand pricing or operational minimum override |
| Dependencies | CN-ROOM-001, CN-UI-001 |
| Blocking decisions | OD-17, OD-18, OD-21, OD-22 |
| Files/modules | Pricing + Settings Packages & Pricing TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Draft permission/stale edit/exact amount/duration/inclusions validation |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Authorized core editing demonstrated without code release; no sales yet |
| Approval | Slice and relevant package/role/policy approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-PKG-002 — Immutable package publication/applicability

| Field | Specification |
|---|---|
| Phase | 3 |
| Priority | P1 |
| Linked requirements | PR-PKG-001, CN-A12, PK-AC04, PK-AC05, PK-AC06, PK-AC09, PK-AC11 |
| In scope | One versioned publish/schedule/deactivate/history slice with atomic scope/time constraint checks |
| Out of scope | No purchased amendment/access change |
| Dependencies | CN-PKG-001 |
| Blocking decisions | OD-17, OD-18, OD-19, OD-21, OD-22 |
| Files/modules | Pricing publication/read projection TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Concurrent publish/effective boundaries/old snapshots/minimum-capacity tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Joint valid publication and no retroactive sold-term edits |
| Approval | Explicit publication slice and policy choices approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-BOOK-001 — Shared inventory hold/conflict guard

| Field | Specification |
|---|---|
| Phase | 3 |
| Priority | P1 |
| Linked requirements | CN-C03, CN-A02, BR-01, BR-02, BR-06, BR-07 |
| In scope | Bounded database-enforced intervals and gateway-hold creation/expiry slice |
| Out of scope | No Redis-only authority or indefinite holds/bank deadline assumption |
| Dependencies | CN-ROOM-001 |
| Blocking decisions | OD-09, OD-10, OD-21 |
| Files/modules | Booking/database owned schema/API/UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Concurrent overlapping holds/reservations/buffer/closing/idempotency tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Conflicting room ranges cannot both commit under approved semantics |
| Approval | Inventory slice + hold/closing/minimum policy approval; migration explicitly reviewed |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-QUOTE-001 — Exact quote and purchase snapshot contract

| Field | Specification |
|---|---|
| Phase | 3 |
| Priority | P1 |
| Linked requirements | CN-C04, PR-PKG-001, PK-AC07, PK-AC10, PK-AC13, BR-03, BR-04, BR-05, BR-10 |
| In scope | One quote construction/expiry/version/snapshot slice and itemized customer summary |
| Out of scope | No guessed proration/fee/tax or automatic latest-price substitution |
| Dependencies | CN-PKG-002, CN-BOOK-001 |
| Blocking decisions | OD-17, OD-20, OD-22, OD-25 |
| Files/modules | Pricing/Booking/UI quote contract TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Exact unit/guest/included-extra/quote-expiry/snapshot fixture tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Accepted versioned quote and explicit channel basis preserved |
| Approval | Quote slice + price/timing/method policy approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-VERIFY-001 — Private booker evidence and eligibility review

| Field | Specification |
|---|---|
| Phase | 4 |
| Priority | P1 |
| Linked requirements | CN-C05, CN-A06, CN-NFR-02 |
| In scope | One authenticated private evidence/review/decision and explicit consent slice |
| Out of scope | No OCR authenticity claim or all-guest process bypass |
| Dependencies | CN-AUTH-001, CN-QUOTE-001 |
| Blocking decisions | OD-03, OD-05 |
| Files/modules | Verification/storage/identity UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Upload/private scope/retention/review/consent tests and operational gate |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Restricted booker eligibility separate from on-site guest entry |
| Approval | Slice + approved evidence/admission/privacy/storage process |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-PAY-GW-001 — Gateway sandbox attempt/verification slice

| Field | Specification |
|---|---|
| Phase | 5 |
| Priority | P1 |
| Linked requirements | CN-C06, CN-A07, PK-AC08 |
| In scope | One approved hosted sandbox attempt, verified inbox/ledger and guarded confirmation |
| Out of scope | No real charges/refunds/merchant secrets or redirect success assumption |
| Dependencies | CN-QUOTE-001, CN-VERIFY-001 |
| Blocking decisions | OD-06, OD-07, OD-20 |
| Files/modules | Payments/Booking/Notifications sandbox UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Signature/replay/amount/late-success/idempotency/hold conflict tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | One verified fact/booking or explicit compensation incident |
| Approval | Slice + provider/merchant sandbox and refund/quote policy authority |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-PAY-BANK-001 — Bank request/receipt and durable reviewer work

| Field | Specification |
|---|---|
| Phase | 5 |
| Priority | P1 |
| Linked requirements | PR-BANK-001, CN-C10, CN-A13, BP-AC04, BP-AC06, BP-AC07, BP-AC08, BP-AC09, BP-AC10 |
| In scope | One method request/shared durable hold/private receipt/status/notification queue slice |
| Out of scope | No real receiving account/credit verification or indefinite review hold |
| Dependencies | CN-QUOTE-001, CN-VERIFY-001 |
| Blocking decisions | OD-23, OD-24, OD-25, OD-26, OD-28, OD-29 |
| Files/modules | Payments/Booking/Notifications/customer/review UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Own/scoped upload/version/expiry/quarantine/failed notification tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Claim is under review, queue survives delivery failure, quotes protected |
| Approval | Slice + approved review/timing/fee/privacy/account/staffing controls |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-PAY-BANK-002 — Bank evidence/allocation and guarded confirmation

| Field | Specification |
|---|---|
| Phase | 5 |
| Priority | P1 |
| Linked requirements | PR-BANK-001, CN-C10, CN-A13, BP-AC01, BP-AC02, BP-AC03, BP-AC05 |
| In scope | One isolated/mock reconciliation ledger/allocation/booking-resolution slice |
| Out of scope | No actual bank approval/money movement, forged webhook or wallet |
| Dependencies | CN-PAY-BANK-001 |
| Blocking decisions | OD-24, OD-27, OD-28, OD-29 |
| Files/modules | Payments/Booking/review UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Concurrent reviewer/deduped credit/partial-excess/mixed-method/expired hold tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Funds retained independently; one eligible booking; no duplicate allocation |
| Approval | Explicit isolated-ledger slice approval and final matching/allocation controls |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-REFUND-001 — Evidence-based refunds and corrections

| Field | Specification |
|---|---|
| Phase | 5 |
| Priority | P1 |
| Linked requirements | CN-A07, BP-AC12, CN-NFR-02 |
| In scope | One simulated/sandbox refund/correction workflow with destination/evidence/status guard |
| Out of scope | No real refund/card re-bill or deletion of finance history |
| Dependencies | CN-PAY-GW-001, CN-PAY-BANK-002 |
| Blocking decisions | OD-07, OD-28 |
| Files/modules | Payments/UI/Audit TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Refund balance/destination/unknown send/retry/append-only correction tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Approval distinct from outgoing completion; receipt facts preserved |
| Approval | Slice + method refund/control approval; live money remains separately gated |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-ACCESS-001 — Simulated credential scheduling/recovery

| Field | Specification |
|---|---|
| Phase | 6 |
| Priority | P1 |
| Linked requirements | CN-C07, CN-O01, BP-AC11, PK-AC12 |
| In scope | One explicitly simulated credential lifecycle/status/retry slice using purchased window |
| Out of scope | No physical certification/unlock/cloud request; no release for unverified payment |
| Dependencies | CN-PAY-GW-001, CN-PAY-BANK-002 |
| Blocking decisions | OD-02, OD-04 |
| Files/modules | Access/Booking/Notifications/UI and simulator TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Paid-confirmed-provisioned distinction/expiry/revoke/unknown/retry tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Software behavior verified only with simulator; physical gate remains open |
| Approval | Simulation slice and access-window/process approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-IOT-001 — Simulated device command/observation slice

| Field | Specification |
|---|---|
| Phase | 7 |
| Priority | P1 |
| Linked requirements | CN-O02, CN-O04, CN-A05 |
| In scope | One desired/ack/observed status adapter and simulator with TTL/permissions |
| Out of scope | No live lock/power/alarm or claimed offline safety |
| Dependencies | CN-ACCESS-001 |
| Blocking decisions | OD-01, OD-02, OD-13 |
| Files/modules | IoT/adapters/operator UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | TTL/ACL/reconnect/out-of-order/no-stale-actuation tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Unknown never OFF/available; simulator evidence qualified |
| Approval | Simulator slice + protocol/capability approval, physical work separate |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-OPS-001 — Room readiness/incident software slice

| Field | Specification |
|---|---|
| Phase | 7 |
| Priority | P1 |
| Linked requirements | CN-O03, CN-O04, CN-A05, CN-NFR-05 |
| In scope | One cleaning task/clearance/session-warning/incident interface under approved staffing process |
| Out of scope | No timer-is-clean or exit-proves-empty inference |
| Dependencies | CN-IOT-001 |
| Blocking decisions | OD-04, OD-10, OD-13, OD-29 |
| Files/modules | Booking/Rooms/IoT/notification/operator UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Missing clearance/overstay/warning-delay/incident authorization tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Physical readiness requires accountable evidence and staff |
| Approval | Slice + staffing/readiness/escalation policy approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-ADMIN-001 — Read-only scoped account/audit operations

| Field | Specification |
|---|---|
| Phase | 8 |
| Priority | P1 |
| Linked requirements | CN-A06, CN-A11, CN-A10, CN-NFR-02 |
| In scope | One restricted account status/audit listing slice; mutations handled by owning prior units |
| Out of scope | No unrestricted administrator, secret exposure or operational policy edits |
| Dependencies | CN-AUTH-001, CN-VERIFY-001 |
| Blocking decisions | OD-05, OD-12, OD-18, OD-28 |
| Files/modules | Identity/Verification/Audit/UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Scoped list/detail/export/redaction/paging tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Role-specific safe read controls and audit visibility |
| Approval | Bounded admin slice + role/retention/export approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-NOTIFY-001 — One notification channel and durable delivery

| Field | Specification |
|---|---|
| Phase | 9 |
| Priority | P1 |
| Linked requirements | CN-C07, CN-A13, CN-SUP-01 |
| In scope | One approved sandbox channel/templated protected link and queue recovery slice |
| Out of scope | No real customer messages or raw evidence/PIN attachments |
| Dependencies | CN-PAY-BANK-001, CN-ACCESS-001 |
| Blocking decisions | OD-01, OD-05, OD-29 |
| Files/modules | Notifications/customer/operator UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Dedupe/retry/bounce/failed-alert queue and privacy tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Delivery failure does not delete review or change payment truth |
| Approval | Channel/sandbox scope and notification/privacy approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-CUSTOMER-001 — Owned history and support status

| Field | Specification |
|---|---|
| Phase | 9 |
| Priority | P1 |
| Linked requirements | CN-C08, CN-SUP-01 |
| In scope | One upcoming/history/detail and actionable support-status slice |
| Out of scope | No amendment policy guessed; amendment later refined task under package/payment owners |
| Dependencies | CN-ACCESS-001, CN-NOTIFY-001 |
| Blocking decisions | OD-07, OD-20, OD-28 |
| Files/modules | Booking/customer UI/support TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Owner denial/snapshot/pending review/access/refund/error tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Historical purchase shown; unresolved state never false success |
| Approval | Slice + support/contact/privacy policy approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-AMEND-001 — One purchased booking change/extension slice

| Field | Specification |
|---|---|
| Phase | 9 |
| Priority | P1 |
| Linked requirements | CN-C08, CN-A02, PK-AC12, BR-10 |
| In scope | One agreed amendment with range/delta money/access coordination |
| Out of scope | No automatic latest catalog rate or free unsafe extension |
| Dependencies | CN-CUSTOMER-001, CN-QUOTE-001, CN-REFUND-001 |
| Blocking decisions | OD-07, OD-20, OD-21, OD-24 |
| Files/modules | Booking/Pricing/Payments/Access/UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Conflict/buffer/consent/partial payment/access recovery tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Original purchase history retained with safe accepted revision |
| Approval | Amendment slice and agreed policy/payment method approval |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-REPORT-001 — Core authorized money/booking projection

| Field | Specification |
|---|---|
| Phase | 9 |
| Priority | P1 |
| Linked requirements | CN-A01, CN-A08, CN-NFR-03, BP-AC13 |
| In scope | One collections/booking/channel report with matched export/scopes and snapshot times |
| Out of scope | No certified accounting profit or fabricated room attribution |
| Dependencies | CN-PAY-GW-001, CN-PAY-BANK-002 |
| Blocking decisions | OD-16 |
| Files/modules | Analytics/Payments/Booking/UI TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Credited/verified/allocated clock, no-double-count/null/asOf/export tests |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Only authoritative safe metrics, further charts refined before READY |
| Approval | Report slice + approved metric definition/recognition exclusions |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-ENG-001 — Engagement scope/rule decision brief

| Field | Specification |
|---|---|
| Phase | 10 |
| Priority | P1 |
| Linked requirements | CN-C09, CN-A09 |
| In scope | Bounded optional loyalty/referral/promotion proposal with rule/abuse/reversal choices |
| Out of scope | No earning rate/feature implementation or silent scope deferral |
| Dependencies | CN-REPORT-001 |
| Blocking decisions | OD-08 |
| Files/modules | Existing Engagement/Pricing decision docs TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Trace rule options/eligibility and acceptance proposal |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Explicit approve/defer decision remains required |
| Approval | User authorizes brief; optional feature scope/rules separately approved |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-ACCEPT-001 — Integrated evidence and recovery gate

| Field | Specification |
|---|---|
| Phase | 11 |
| Priority | P1 |
| Linked requirements | CN-NFR-01, CN-NFR-02, CN-NFR-03, CN-NFR-04, CN-NFR-05, CN-NFR-06 |
| In scope | Review actual integrated tests/restore/physical results and missing launch acceptance |
| Out of scope | No waive prior gates; physical test is separately authorized/qualified, no simulator substitution |
| Dependencies | CN-AMEND-001, CN-REPORT-001, CN-OPS-001 |
| Blocking decisions | OD-02, OD-03, OD-04, OD-05, OD-14, OD-15 |
| Files/modules | Cross-module CI/acceptance/runbooks TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Actual performance/auth/concurrency/backup restore/UI/physical owner evidence |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Evidence classes/owner acceptance distinct; unresolved gates remain blocking |
| Approval | Integrated test scope + qualified physical/business/legal authority |
| Readiness | BLOCKED |
| Work status | BLOCKED |

## CN-LAUNCH-001 — Authorized controlled launch/handover

| Field | Specification |
|---|---|
| Phase | 12 |
| Priority | P1 |
| Linked requirements | CN-NFR-05, CN-NFR-06, PR-UI-001, PR-PKG-001, PR-BANK-001 |
| In scope | Approved controlled rollout with responders/monitoring/recovery/handover |
| Out of scope | No automatic deployment/cost or omitted optional feature disguised as done |
| Dependencies | CN-ACCEPT-001 |
| Blocking decisions | OD-01, OD-02, OD-04, OD-06, OD-14, OD-29 |
| Files/modules | Deployment/operations/provider/hardware paths TBD |
| DB/API/UI | None for setup/decision briefs; later proposed owned schema/API/UI must be specified and approved before READY |
| Risks | Owner/venue denial, evidence/secret leakage, financial integrity, safe egress/offline uncertainty; no live operation authorized |
| Migrations | None for setup/briefs; later TBD after approved stack/schema; never implicit migration permission |
| Checks/tests | Named signoff, environment/rollback/alert/recovery evidence |
| Rollback | Revert only this reviewed documentation/isolated-code change via normal history-preserving revert; future data/external compensation requires explicit plan before READY |
| Definition of done | Launch accepted by responsible owners; NOT just code/test green |
| Approval | Explicit release/production/cost/live-device authorizations and all launch gates |
| Readiness | BLOCKED |
| Work status | BLOCKED |
