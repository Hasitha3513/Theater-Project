# CineNest Future Features Register

## Purpose and scope

Owner-authorized supplementary discovery register established by CN-FUTURE-FEATURES-BACKLOG-001 on 2026-10-22. Capture, evaluate and trace genuinely new enhancements alongside continued approved MVP delivery. This is not a fifth canonical knowledge document, a second MVP execution plan, feature approval or implementation authority. The four canonical documents remain [PROJECT](../PROJECT.md), [ARCHITECTURE](../ARCHITECTURE.md), [DESIGN](../DESIGN.md) and [PROGRESS](../PROGRESS.md).

No future features are recorded or approved at setup. Existing source material, requirements, decisions, tests and execution history keep their authority. Do not manufacture ideas to populate this register.

## MVP separation rules

The existing [74-criterion roadmap](../PROGRESS.md#mvp-roadmap), [92 requirements](../execution/requirements.json), [92 test plans](../execution/test-plan.json), [execution tasks/dependencies](../execution/change-sets.json) and [decision register](../decisions/cinenest-open-decisions.json) remain authoritative for MVP work.

- Never move, relabel or defer an existing MVP obligation here to reduce its workload or improve completion counts.
- Search existing requirement IDs, acceptance criteria, decisions, code/tests and execution history before labelling work new. Missing implementation, an unaccepted test or a deferred requirement is still existing MVP scope.
- Existing loyalty, referrals, promotions, PayHere integration, maintenance closure/reopening and grant administration are not newly discovered enhancements merely because execution is deferred or incomplete. Their original obligations remain in the existing records.
- For EXISTING_MVP, BUG or TECHNICAL_DEBT that overlaps MVP, retain only a discovery/reference entry here if useful; link its original requirement/task and use the existing authorized execution workflow. This register never replaces its owner or dependency edges.
- A genuinely additive enhancement receives no MVP requirement ID, roadmap checkbox, denominator change or release commitment by being recorded here. Any approved MVP scope change requires separate explicit owner authorization and canonical reconciliation.
- Source custody, scoped authorization, independent financial review, synthetic-only restrictions, physical safety and all production/acceptance gates remain intact. Nothing here authorizes real payments, identity processing, device commands, grants, bootstrap or deployment.

## Feature identification format

Use stable IDs **FF-0001**, **FF-0002**, and so on: `FF-` plus four decimal digits, starting at 0001 for the first genuine discovery. Never reuse or renumber IDs, including REJECTED or DUPLICATE entries. Choose the next unused number after checking all active and historical entries in this file. The setup task is not an FF feature and consumes no ID.

Record discovery dates as YYYY-MM-DD, actual sources and links, and explicit unknowns as PENDING or NOT_ESTABLISHED. Do not infer a named owner, acceptance, qualification or approval from a suggestion. Keep one entry per distinct feature; preserve merged/duplicate provenance and link the surviving FF or existing MVP ID.

## Classification

| Classification | Meaning and routing |
|---|---|
| EXISTING_MVP | Already covered by an existing requirement/criterion; link the existing authority and continue there. No future-scope transfer. |
| FUTURE_ENHANCEMENT | Genuinely additive capability outside the approved MVP; capture as IDEA for owner evaluation. |
| BUG | Observed behavior violates an existing contract; preserve reproduction evidence and route to bounded remediation. |
| TECHNICAL_DEBT | Maintainability, reliability or security work without an assumed new business capability; identify affected requirements and risks. |
| RESEARCH_IDEA | Unvalidated possibility requiring bounded research before a product commitment. No provider/tool access is implied. |

## Feature discovery workflow

1. Check the existing MVP requirements, roadmap, decisions, implementation and tests for overlap.
2. Search this register and existing tasks/history for duplicates.
3. During already authorized work, capture a genuinely new enhancement as IDEA with its source, rationale and unknowns. Do not interrupt active MVP delivery merely to elaborate optional ideas.
4. Triage its classification, MVP overlap, value, risks, owning module and dependencies. Route existing MVP work and defects to their original authorities.
5. Identify policy, architecture, privacy, security, finance, provider or physical decisions before seeking owner approval.
6. Seek explicit owner approval of a concrete bounded scope. Recording or triage is not approval; product approval alone is not coding authority.
7. Reconcile an approved, implementation-authorized unit with the existing canonical/execution workflow before READY. Preserve existing task dependencies; no automatic graph edits or task creation.
8. Implement only the authorized unit, record actual commits/tests/CI, then obtain the applicable independent acceptance. Preserve failures, limitations and residual obligations.

## Prioritization policy

Approved MVP completion remains the default development priority. Evaluate future work by customer/business benefit, safety/security or reliability impact, dependency readiness, effort, reversibility and acceptance cost. Record P0/P1/P2 as a proposed priority with rationale; priority does not grant authority or bypass gates.

P0 denotes a justified critical risk or blocker requiring owner triage; P1 a high-value improvement; P2 a lower-priority enhancement/research candidate. Do not interrupt an active bounded MVP change set for optional enhancement work. Owner-approved and separately implementation-authorized future features may proceed alongside MVP work in controlled change sets. Record the scheduling trade-off explicitly; never silently displace a committed MVP obligation.

## Approval and readiness requirements

APPROVED requires an explicit owner decision with source/date and bounded scope. Record exclusions, policy choices and whether implementation authority is GRANTED, NOT_GRANTED or PENDING. Approval of this register approves no feature.

READY additionally requires separately explicit implementation authority, satisfied dependencies, defined API/module ownership, approved migration authority where needed, security/privacy review, test plan, Definition of Done and rollback. Undefined policy/provider/physical prerequisites remain visible and prevent READY where required. Names and qualifications are recorded only when explicitly confirmed. No automatic production, grant assignment or operational authorization follows.

## Lifecycle and status tracking

Main lifecycle: **IDEA → TRIAGED → NEEDS_DECISION → APPROVED → READY → IN_PROGRESS → IMPLEMENTED → VERIFIED → ACCEPTED**.

NEEDS_DECISION is used when a decision remains outstanding; it may be bypassed after triage only when no decision is missing and explicit approval evidence exists. Also support **DEFERRED**, **REJECTED** and **DUPLICATE** from the appropriate review stage. Preserve the earlier state, reason, date and authority in history; reopening requires a recorded reason and renewed authority where applicable.

| Status | Evidence required |
|---|---|
| IDEA | Source-backed discovery; no approval inferred. |
| TRIAGED | Classification, overlap, module, dependencies and rationale reviewed. |
| NEEDS_DECISION | Exact unresolved owner/architecture/policy/external question identified. |
| APPROVED | Attributable bounded owner approval; implementation authority recorded separately. |
| READY | Authorized execution unit, dependencies and all applicable start gates satisfied. |
| IN_PROGRESS | That bounded authorized implementation has actually begun. |
| IMPLEMENTED | Code/documentation deliverable exists with actual commit/reference; verification may be incomplete. |
| VERIFIED | Required tests and exact-source CI completed successfully, with scope and limitations recorded. |
| ACCEPTED | Applicable named reviewer/date/build/environment and acceptance evidence explicitly recorded. CI alone is insufficient. |
| DEFERRED | Reason, authority, retained obligations and reactivation conditions recorded. |
| REJECTED | Attributable rejection and reason; ID/history retained. |
| DUPLICATE | Surviving FF or existing MVP ID and preserved source/history. |

If later tests fail, reopen the relevant status with evidence; never retain VERIFIED/ACCEPTED as an unsupported current claim. These feature statuses do not automatically change existing MVP tasks, requirements, phase checkboxes or demonstration states.

## Dependencies and execution links

Record required FF IDs, existing requirement/task/decision IDs, provider/hardware access and reviewer appointments separately. Distinguish hard dependencies from optional ordering; do not combine unrelated grants across venues. Keep dependencies acyclic and do not declare prerequisites satisfied from proposals or documentation alone. Existing MVP graph changes need their own explicit approval. Link an authorized execution record rather than creating a parallel task registry in this file.

## Testing and acceptance requirements

Define proposed measurable acceptance criteria, positive/negative tests, permission/ownership/venue isolation, validation/error/retry behavior, concurrency/idempotency and audit/rollback coverage as applicable. Database changes require authorized additive migrations and PostgreSQL integrity/race tests. Frontend work follows approved CineNest references/tokens and requires responsive/keyboard/error-state and visual evidence.

Separate unit, PostgreSQL integration, architecture, intercepted browser, real-backend E2E, provider, physical and formal acceptance evidence. Record actual PASS/FAIL/NOT_RUN, command/environment/build/commit and limitations. Never substitute synthetic evidence for real banking, identity, physical safety or production qualification. Run applicable knowledge/execution/dependency/governance/whitespace and full application CI requirements without weakening controls. Pending CI is PENDING, not VERIFIED.

## Feature record template

Copy this template only for a genuine discovery. Replace placeholders with evidence or explicit unknowns; the template itself is not an entry or approval.

```markdown
### FF-<NNNN> — <title>

- Classification: FUTURE_ENHANCEMENT / EXISTING_MVP / BUG / TECHNICAL_DEBT / RESEARCH_IDEA
- Description: <what is proposed or observed>
- Business justification: <source-backed rationale>
- User benefit: <expected benefit; unvalidated assumptions labelled>
- Discovery date: <YYYY-MM-DD>
- Source/reference: <owner request, issue, observation or evidence link>
- MVP overlap: <existing requirement/criterion/task IDs, or NONE_IDENTIFIED with search basis>
- Priority: <P0/P1/P2; proposed/approved and rationale>
- Owner module: <module or PENDING; no invented accountable appointee>
- Backend impact: <bounded impact or NOT_ESTABLISHED>
- Frontend impact: <bounded impact or NOT_ESTABLISHED>
- Database impact: <schema/data/migration impact and separate authority or NONE>
- Security impact: <permissions/privacy/audit/finance/physical implications>
- Dependencies: <FF, requirement, task, decision, external and appointment references>
- Proposed acceptance criteria: <measurable conditions; not accepted outcomes>
- Owner approval: <PENDING or explicit decision/source/date/scope/exclusions>
- Implementation authority: <PENDING / NOT_GRANTED / explicitly GRANTED with source>
- Implementation status: IDEA
- Execution record: <existing authorized change-set reference or NOT_ASSIGNED>
- Test/evidence: <actual results/environment/limitations or NOT_RUN>
- Commit SHA: <actual exact SHA or NOT_IMPLEMENTED>
- CI evidence: <exact-source run URL/status/conclusion or NOT_RUN>
- Acceptance: <reviewer/date/build/environment/evidence or NOT_ACCEPTED>
- Definition of Done / rollback: <bounded checks and safe rollback, initially PENDING>
- History: <dated status changes, authority, commits, failures and residual obligations>
```

## Feature records

No feature records yet. No FF ID has been allocated. No future feature has been approved, implemented, verified or accepted by this setup.

## Implementation history

| Date | Reference | Actual outcome | Evidence |
|---|---|---|---|
| 2026-10-22 | CN-FUTURE-FEATURES-BACKLOG-001 | Owner-authorized supplementary register established; no feature implementation or MVP status change | [Setup record](../PROGRESS.md#future-features-backlog); publication commit is discoverable from this file's Git history; final exact-source CI receipt is reported in closeout |

Future implementation entries must link actual FF/execution IDs, commit SHAs, exact-source CI and acceptance scope; never populate this table with planned successes. Preserve prior entries when work is rejected, deferred, superseded or reopened.


### FF-0001 — Facebook Login provider

- Classification: FUTURE_ENHANCEMENT
- Description: Additional customer login provider beyond the explicitly scoped Google/OIDC foundation
- Business justification: Owner requested November14 customer-facing release priority expansion;implementation not authorized
- User benefit: Requested customer access/content usability benefit;not user-acceptance evidence
- Discovery date: 2026-10-26
- Source/reference: Owner customer website/authentication attachment SHA-256 70796995206021ceac2be4e4302c72112053a3c7bef38d0ebb66be8fb7b819ef;PROGRESS.md#customer-website-preview
- MVP overlap: Identity/login overlaps CN-C02 for FF-0001;banner accessibility/security overlaps CN-NFR-01/02 for FF-0002. Additive provider/content scope is not a new requirement ID;no existing obligation moved or deferred
- Priority: P0 owner-requested preparation priority;implementation/launch remains unapproved
- Owner module: Identity
- Backend impact: Proposed owner-module ports and protected contracts;NOT_IMPLEMENTED
- Frontend impact: Proposed provider entry or protected banner management/home presentation;NOT_IMPLEMENTED
- Database impact: PENDING exact additive schema and separate authorization;no migration in discovery
- Security impact: Provider identity/account linking or dedicated publication authority and asset isolation;no grants
- Dependencies: CN-C02;OD-01;OD-05;provider app/credentials/callback/review evidence;separate implementation authority
- Proposed acceptance criteria: Exact approved scope;ownership/venue/CSRF/privacy and negative tests;real-stack responsive evidence;separate provider or publication qualification
- Owner approval: PENDING
- Implementation authority: NOT_GRANTED
- Implementation status: IDEA
- Execution record: change-sets.json#customerWebsitePriorityPreparation
- Test/evidence: NOT_RUN for new feature;existing local preview is not feature verification
- Commit SHA: NOT_IMPLEMENTED
- CI evidence: NOT_RUN
- Acceptance: NOT_ACCEPTED
- Definition of Done / rollback: PENDING approved contract;disable new provider/content mode and retain immutable history
- History: 2026-10-26 IDEA recorded from owner direction;no approval,code or acceptance implied


### FF-0002 — Non-financial homepage banner management

- Classification: FUTURE_ENHANCEMENT
- Description: Versioned homepage banner draft/image/placement/scheduling and publish/unpublish;no discounts or financial promotions
- Business justification: Owner requested November14 customer-facing release priority expansion;implementation not authorized
- User benefit: Requested customer access/content usability benefit;not user-acceptance evidence
- Discovery date: 2026-10-26
- Source/reference: Owner customer website/authentication attachment SHA-256 70796995206021ceac2be4e4302c72112053a3c7bef38d0ebb66be8fb7b819ef;PROGRESS.md#customer-website-preview
- MVP overlap: Identity/login overlaps CN-C02 for FF-0001;banner accessibility/security overlaps CN-NFR-01/02 for FF-0002. Additive provider/content scope is not a new requirement ID;no existing obligation moved or deferred
- Priority: P0 owner-requested preparation priority;implementation/launch remains unapproved
- Owner module: PENDING_EXISTING_MODULE_OWNER_DECISION
- Backend impact: Proposed owner-module ports and protected contracts;NOT_IMPLEMENTED
- Frontend impact: Proposed provider entry or protected banner management/home presentation;NOT_IMPLEMENTED
- Database impact: PENDING exact additive schema and separate authorization;no migration in discovery
- Security impact: Provider identity/account linking or dedicated publication authority and asset isolation;no grants
- Dependencies: CN-NFR-01;CN-NFR-02;site/venue scope and permission approval;public asset storage policy;separate implementation authority
- Proposed acceptance criteria: Exact approved scope;ownership/venue/CSRF/privacy and negative tests;real-stack responsive evidence;separate provider or publication qualification
- Owner approval: PENDING
- Implementation authority: NOT_GRANTED
- Implementation status: IDEA
- Execution record: change-sets.json#customerWebsitePriorityPreparation
- Test/evidence: NOT_RUN for new feature;existing local preview is not feature verification
- Commit SHA: NOT_IMPLEMENTED
- CI evidence: NOT_RUN
- Acceptance: NOT_ACCEPTED
- Definition of Done / rollback: PENDING approved contract;disable new provider/content mode and retain immutable history
- History: 2026-10-26 IDEA recorded from owner direction;no approval,code or acceptance implied


## Controlled FF-0001 bounded S1 implementation evidence — 2026-10-28

Owner explicitly authorized CN-R1-FACEBOOK-SSO-001 S1 local synthetic-only implementation after the original discovery. Existing IDEA/NOT_GRANTED lines above are frozen discovery history,not current S1 authority. Current whole-feature lifecycle:IN_PROGRESS;S1 VERIFIED_SYNTHETIC only. Live-provider scope remains unapproved/unqualified;no formal acceptance. Exact scope,tests,publication and next unit:execution/status.json facebookSsoS1 and PROGRESS.md#facebook-sso-s1. No requirement moved or count changed.


## Controlled FF-0001 S2-A evidence — 2026-10-28

Owner explicitly authorized disabled-by-default Meta adapter and isolated tests only. S2-A implemented software boundary does not complete live-provider qualification;whole-feature lifecycle remains IN_PROGRESS,not ACCEPTED. Actual scope/evidence:execution/status.json facebookSsoS2A and PROGRESS.md#facebook-sso-s2a. Original discovery/S1 history and all existing MVP obligations retained.


<a id="admin-advertising-s1"></a>

## Controlled FF-0002 bounded local S1 implementation evidence — 2026-10-29

The earlier IDEA/preparation entry remains the historical wider-scope record. Owner attachment a0065c8f-08a9-44cb-a707-f4882e913892 explicitly grants CN-R1-ADMIN-ADVERTISING-001 local synthetic Rooms-owned S1, independent publisher, additive V30 and isolated content fixtures. This is the same unit F, not another backlog. Bounded local functionality and real-stack verification are recorded in [PROGRESS](../PROGRESS.md#admin-advertising-s1) and existing execution registries. Production media/publishing authority, uploads, commercial promotions and formal acceptance remain unapproved. Exact-source publication CI is separate; do not infer acceptance from implementation. Original requirement/roadmap counts are unchanged.
