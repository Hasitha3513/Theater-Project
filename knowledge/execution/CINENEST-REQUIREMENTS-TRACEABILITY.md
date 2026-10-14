# CineNest Requirements Traceability and Source Coverage

CN-AGENT-SETUP-001 · 2026-10-14. Canonical [source ledger](source-ledger.json), [requirements](requirements.json), [test plan](test-plan.json) and [omissions](omissions.json); [backlog](change-sets.json) is the only work model. Existing [open-decision register](../decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md) remains the only business/architecture decision model.

## Status semantics and evidence limits
Existing Decision APPROVED maps USER-CONFIRMED; DOCUMENTED_DRAFT, RECOMMENDED and OPEN_DECISION retain existing meanings. Work uses NOT_STARTED, READY, IN_PROGRESS, BLOCKED, IMPLEMENTED_UNVERIFIED, VERIFIED, ACCEPTED. Source review and checked documentation do not set feature implementation VERIFIED. Existing feature Implementation remains NOT_VERIFIED. Product acceptance NOT_ACCEPTED until actual owner evidence. Publication LOCAL/COMMITTED/PUSHED/MERGED/DEPLOYED requires corresponding Git/operation proof; no merge/deployment inferred.

Core product approval denominator is three decisions A/B/C: hybrid visual direction (PR-UI-001 tracking alias), PR-PKG-001 and PR-BANK-001; catalog feature IDs are linked coverage records, not three more independent approvals. Workflow setup approval is separate. Every approved requirement has mapped units and explicit blockers; none silently deferred/dropped. All 27 existing features remain in denominator; optional engagement retained without invented defer approval. Supplementary support/NFR IDs are coverage aliases from existing sources, DOCUMENTED_DRAFT, not new product approvals.

## Source-review coverage
All 44 baseline tracked assets at a48cc6e reviewed: text/JSON/CSS/validator in bounded reads (parent + independent source_review), three PNGs visually inspected. Ledger has exact baseline hashes, logical line ranges/reviewer and change freshness policy. Missing PDFs S2/S3/S4 are NOT_READ_MISSING, not counted as reviewed; normalized S7 image original filename equivalence unverified. S10 attached setup instruction fully read and preserved. Edited indexes/control metadata are rechecked at closeout; unchanged normative sources need no arbitrary daily reread.

## Requirement/task/test matrix
Every row includes source/approval, priority/scope/criteria/module/UI/API ownership, change sets/dependencies/decisions, security/safety impact, test IDs, evidence/current status and unresolved questions in JSON. Code/test paths remain empty rather than invented; tests listed here are plans, NOT RUN. Setup checks will get actual evidence at closeout. Existing PK-AC/BP-AC and metric/BR IDs preserved.

| Requirement | Decision | Change sets | Work | Test plan | Acceptance |
|---|---|---|---|---|---|
| CN-AGENT-WORKFLOW-001 | APPROVED | CN-AGENT-SETUP-001, CN-DEC-001 | VERIFIED (documentation) | T-CN-AGENT-WORKFLOW-001 / PASS DOC CHECKS | NOT ACCEPTED (no product acceptance) |
| PR-UI-001 | APPROVED | CN-UI-001, CN-DEC-001 | BLOCKED | T-PR-UI-001 / NOT RUN | NOT ACCEPTED |
| PR-PKG-001 | APPROVED | CN-PKG-001, CN-PKG-002, CN-QUOTE-001, CN-AMEND-001, CN-DEC-001 | BLOCKED | T-PR-PKG-001 / NOT RUN | NOT ACCEPTED |
| PR-BANK-001 | APPROVED | CN-PAY-BANK-001, CN-PAY-BANK-002, CN-REFUND-001, CN-NOTIFY-001, CN-DEC-001 | BLOCKED | T-PR-BANK-001 / NOT RUN | NOT ACCEPTED |
| CN-C01 | DOCUMENTED_DRAFT | CN-ROOM-001 | BLOCKED | T-CN-C01 / NOT RUN | NOT ACCEPTED |
| CN-C02 | DOCUMENTED_DRAFT | CN-AUTH-001 | BLOCKED | T-CN-C02 / NOT RUN | NOT ACCEPTED |
| CN-C03 | DOCUMENTED_DRAFT | CN-BOOK-001 | BLOCKED | T-CN-C03 / NOT RUN | NOT ACCEPTED |
| CN-C04 | DOCUMENTED_DRAFT | CN-QUOTE-001 | BLOCKED | T-CN-C04 / NOT RUN | NOT ACCEPTED |
| CN-C05 | DOCUMENTED_DRAFT | CN-VERIFY-001 | BLOCKED | T-CN-C05 / NOT RUN | NOT ACCEPTED |
| CN-C06 | DOCUMENTED_DRAFT | CN-PAY-GW-001 | BLOCKED | T-CN-C06 / NOT RUN | NOT ACCEPTED |
| CN-C07 | DOCUMENTED_DRAFT | CN-ACCESS-001, CN-NOTIFY-001 | BLOCKED | T-CN-C07 / NOT RUN | NOT ACCEPTED |
| CN-C08 | DOCUMENTED_DRAFT | CN-CUSTOMER-001, CN-AMEND-001 | BLOCKED | T-CN-C08 / NOT RUN | NOT ACCEPTED |
| CN-C09 | OPEN_DECISION | CN-ENG-001 | BLOCKED | T-CN-C09 / NOT RUN | NOT ACCEPTED |
| CN-A01 | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-A01 / NOT RUN | NOT ACCEPTED |
| CN-A02 | DOCUMENTED_DRAFT | CN-BOOK-001, CN-AMEND-001 | BLOCKED | T-CN-A02 / NOT RUN | NOT ACCEPTED |
| CN-A03 | DOCUMENTED_DRAFT | CN-ROOM-001 | BLOCKED | T-CN-A03 / NOT RUN | NOT ACCEPTED |
| CN-A04 | DOCUMENTED_DRAFT | CN-UI-001, CN-ROOM-001 | BLOCKED | T-CN-A04 / NOT RUN | NOT ACCEPTED |
| CN-A05 | DOCUMENTED_DRAFT | CN-IOT-001, CN-OPS-001 | BLOCKED | T-CN-A05 / NOT RUN | NOT ACCEPTED |
| CN-A06 | DOCUMENTED_DRAFT | CN-AUTH-001, CN-VERIFY-001, CN-ADMIN-001 | BLOCKED | T-CN-A06 / NOT RUN | NOT ACCEPTED |
| CN-A07 | DOCUMENTED_DRAFT | CN-PAY-GW-001, CN-REFUND-001 | BLOCKED | T-CN-A07 / NOT RUN | NOT ACCEPTED |
| CN-A08 | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-A08 / NOT RUN | NOT ACCEPTED |
| CN-A09 | OPEN_DECISION | CN-ENG-001 | BLOCKED | T-CN-A09 / NOT RUN | NOT ACCEPTED |
| CN-A10 | DOCUMENTED_DRAFT | CN-ADMIN-001 | BLOCKED | T-CN-A10 / NOT RUN | NOT ACCEPTED |
| CN-A11 | DOCUMENTED_DRAFT | CN-ADMIN-001 | BLOCKED | T-CN-A11 / NOT RUN | NOT ACCEPTED |
| CN-O01 | DOCUMENTED_DRAFT | CN-ACCESS-001 | BLOCKED | T-CN-O01 / NOT RUN | NOT ACCEPTED |
| CN-O02 | DOCUMENTED_DRAFT | CN-IOT-001 | BLOCKED | T-CN-O02 / NOT RUN | NOT ACCEPTED |
| CN-O03 | DOCUMENTED_DRAFT | CN-OPS-001 | BLOCKED | T-CN-O03 / NOT RUN | NOT ACCEPTED |
| CN-O04 | DOCUMENTED_DRAFT | CN-IOT-001, CN-OPS-001 | BLOCKED | T-CN-O04 / NOT RUN | NOT ACCEPTED |
| CN-A12 | APPROVED | CN-PKG-001, CN-PKG-002 | BLOCKED | T-CN-A12 / NOT RUN | NOT ACCEPTED |
| CN-C10 | APPROVED | CN-PAY-BANK-001, CN-PAY-BANK-002 | BLOCKED | T-CN-C10 / NOT RUN | NOT ACCEPTED |
| CN-A13 | APPROVED | CN-PAY-BANK-001, CN-PAY-BANK-002, CN-NOTIFY-001 | BLOCKED | T-CN-A13 / NOT RUN | NOT ACCEPTED |
| CN-NFR-01 | DOCUMENTED_DRAFT | CN-UI-001, CN-ACCEPT-001 | BLOCKED | T-CN-NFR-01 / NOT RUN | NOT ACCEPTED |
| CN-NFR-02 | DOCUMENTED_DRAFT | CN-GATE-001, CN-AUTH-001, CN-VERIFY-001, CN-ADMIN-001, CN-ACCEPT-001 | BLOCKED | T-CN-NFR-02 / NOT RUN | NOT ACCEPTED |
| CN-NFR-03 | DOCUMENTED_DRAFT | CN-FND-001, CN-REPORT-001, CN-ACCEPT-001 | BLOCKED | T-CN-NFR-03 / NOT RUN | NOT ACCEPTED |
| CN-NFR-04 | DOCUMENTED_DRAFT | CN-ACCEPT-001 | BLOCKED | T-CN-NFR-04 / NOT RUN | NOT ACCEPTED |
| CN-NFR-05 | DOCUMENTED_DRAFT | CN-GATE-001, CN-OPS-001, CN-ACCEPT-001, CN-LAUNCH-001 | BLOCKED | T-CN-NFR-05 / NOT RUN | NOT ACCEPTED |
| CN-NFR-06 | DOCUMENTED_DRAFT | CN-ACCEPT-001, CN-LAUNCH-001 | BLOCKED | T-CN-NFR-06 / NOT RUN | NOT ACCEPTED |
| CN-SUP-01 | DOCUMENTED_DRAFT | CN-CUSTOMER-001, CN-NOTIFY-001, CN-OPS-001 | BLOCKED | T-CN-SUP-01 / NOT RUN | NOT ACCEPTED |
| BR-01 | DOCUMENTED_DRAFT | CN-BOOK-001 | BLOCKED | T-BR-01 / NOT RUN | NOT ACCEPTED |
| BR-02 | DOCUMENTED_DRAFT | CN-PKG-002, CN-BOOK-001 | BLOCKED | T-BR-02 / NOT RUN | NOT ACCEPTED |
| BR-03 | DOCUMENTED_DRAFT | CN-QUOTE-001 | BLOCKED | T-BR-03 / NOT RUN | NOT ACCEPTED |
| BR-04 | DOCUMENTED_DRAFT | CN-QUOTE-001 | BLOCKED | T-BR-04 / NOT RUN | NOT ACCEPTED |
| BR-05 | DOCUMENTED_DRAFT | CN-QUOTE-001 | BLOCKED | T-BR-05 / NOT RUN | NOT ACCEPTED |
| BR-06 | DOCUMENTED_DRAFT | CN-BOOK-001, CN-OPS-001 | BLOCKED | T-BR-06 / NOT RUN | NOT ACCEPTED |
| BR-07 | DOCUMENTED_DRAFT | CN-BOOK-001 | BLOCKED | T-BR-07 / NOT RUN | NOT ACCEPTED |
| BR-08 | DOCUMENTED_DRAFT | CN-VERIFY-001 | BLOCKED | T-BR-08 / NOT RUN | NOT ACCEPTED |
| BR-09 | DOCUMENTED_DRAFT | CN-ACCESS-001 | BLOCKED | T-BR-09 / NOT RUN | NOT ACCEPTED |
| BR-10 | DOCUMENTED_DRAFT | CN-QUOTE-001, CN-AMEND-001 | BLOCKED | T-BR-10 / NOT RUN | NOT ACCEPTED |
| PK-AC01 | RECOMMENDED | CN-PKG-001 | BLOCKED | T-PK-AC01 / NOT RUN | NOT ACCEPTED |
| PK-AC02 | RECOMMENDED | CN-PKG-001 | BLOCKED | T-PK-AC02 / NOT RUN | NOT ACCEPTED |
| PK-AC03 | RECOMMENDED | CN-PKG-001 | BLOCKED | T-PK-AC03 / NOT RUN | NOT ACCEPTED |
| PK-AC04 | RECOMMENDED | CN-PKG-002 | BLOCKED | T-PK-AC04 / NOT RUN | NOT ACCEPTED |
| PK-AC05 | RECOMMENDED | CN-PKG-002 | BLOCKED | T-PK-AC05 / NOT RUN | NOT ACCEPTED |
| PK-AC06 | RECOMMENDED | CN-PKG-002 | BLOCKED | T-PK-AC06 / NOT RUN | NOT ACCEPTED |
| PK-AC07 | RECOMMENDED | CN-QUOTE-001 | BLOCKED | T-PK-AC07 / NOT RUN | NOT ACCEPTED |
| PK-AC08 | RECOMMENDED | CN-PAY-GW-001 | BLOCKED | T-PK-AC08 / NOT RUN | NOT ACCEPTED |
| PK-AC09 | RECOMMENDED | CN-PKG-002 | BLOCKED | T-PK-AC09 / NOT RUN | NOT ACCEPTED |
| PK-AC10 | RECOMMENDED | CN-QUOTE-001 | BLOCKED | T-PK-AC10 / NOT RUN | NOT ACCEPTED |
| PK-AC11 | RECOMMENDED | CN-PKG-002 | BLOCKED | T-PK-AC11 / NOT RUN | NOT ACCEPTED |
| PK-AC12 | RECOMMENDED | CN-ACCESS-001 | BLOCKED | T-PK-AC12 / NOT RUN | NOT ACCEPTED |
| PK-AC13 | RECOMMENDED | CN-QUOTE-001 | BLOCKED | T-PK-AC13 / NOT RUN | NOT ACCEPTED |
| BP-AC01 | RECOMMENDED | CN-PAY-BANK-002 | BLOCKED | T-BP-AC01 / NOT RUN | NOT ACCEPTED |
| BP-AC02 | RECOMMENDED | CN-PAY-BANK-002 | BLOCKED | T-BP-AC02 / NOT RUN | NOT ACCEPTED |
| BP-AC03 | RECOMMENDED | CN-PAY-BANK-002 | BLOCKED | T-BP-AC03 / NOT RUN | NOT ACCEPTED |
| BP-AC04 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC04 / NOT RUN | NOT ACCEPTED |
| BP-AC05 | RECOMMENDED | CN-PAY-BANK-002 | BLOCKED | T-BP-AC05 / NOT RUN | NOT ACCEPTED |
| BP-AC06 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC06 / NOT RUN | NOT ACCEPTED |
| BP-AC07 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC07 / NOT RUN | NOT ACCEPTED |
| BP-AC08 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC08 / NOT RUN | NOT ACCEPTED |
| BP-AC09 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC09 / NOT RUN | NOT ACCEPTED |
| BP-AC10 | RECOMMENDED | CN-PAY-BANK-001 | BLOCKED | T-BP-AC10 / NOT RUN | NOT ACCEPTED |
| BP-AC11 | RECOMMENDED | CN-ACCESS-001 | BLOCKED | T-BP-AC11 / NOT RUN | NOT ACCEPTED |
| BP-AC12 | RECOMMENDED | CN-REFUND-001 | BLOCKED | T-BP-AC12 / NOT RUN | NOT ACCEPTED |
| BP-AC13 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BP-AC13 / NOT RUN | NOT ACCEPTED |
| CN-M-REVENUE-BOOKINGS | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-REVENUE-BOOKINGS / NOT RUN | NOT ACCEPTED |
| CN-M-ROOM-OCCUPANCY | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-ROOM-OCCUPANCY / NOT RUN | NOT ACCEPTED |
| CN-M-REVENUE-TREND | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-REVENUE-TREND / NOT RUN | NOT ACCEPTED |
| CN-M-POPULAR-TIME-SLOTS | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-POPULAR-TIME-SLOTS / NOT RUN | NOT ACCEPTED |
| CN-M-ROOM-UTILIZATION | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-ROOM-UTILIZATION / NOT RUN | NOT ACCEPTED |
| CN-M-BOOKING-STATISTICS | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-BOOKING-STATISTICS / NOT RUN | NOT ACCEPTED |
| CN-M-FINANCIAL-KPIS | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-FINANCIAL-KPIS / NOT RUN | NOT ACCEPTED |
| CN-M-SYSTEM-IOT-STATUS | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-SYSTEM-IOT-STATUS / NOT RUN | NOT ACCEPTED |
| CN-M-PAYMENT-ADDON-BREAKDOWN | DOCUMENTED_DRAFT | CN-REPORT-001 | BLOCKED | T-CN-M-PAYMENT-ADDON-BREAKDOWN / NOT RUN | NOT ACCEPTED |
| CN-M-BANK-PAYMENT-REVIEW-FINANCES | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-CN-M-BANK-PAYMENT-REVIEW-FINANCES / NOT RUN | NOT ACCEPTED |
| BN-M01 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M01 / NOT RUN | NOT ACCEPTED |
| BN-M02 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M02 / NOT RUN | NOT ACCEPTED |
| BN-M03 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M03 / NOT RUN | NOT ACCEPTED |
| BN-M04 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M04 / NOT RUN | NOT ACCEPTED |
| BN-M05 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M05 / NOT RUN | NOT ACCEPTED |
| BN-M06 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M06 / NOT RUN | NOT ACCEPTED |
| BN-M07 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M07 / NOT RUN | NOT ACCEPTED |
| BN-M08 | RECOMMENDED | CN-REPORT-001 | BLOCKED | T-BN-M08 / NOT RUN | NOT ACCEPTED |

## Omission check at selection and closeout
1. Approved unmapped requirements: validator checks all approved IDs have known task links/blocked state. No application criterion claimed executed; tests/evidence empty until actual run.
2. Task without approved scope: all implementation backlog items authorityAvailable=false / BLOCKED; CN-DEC-001 only has S11 brief authority; their linked draft requirements do not authorize code. Setup S10 and decision brief S11 have separate bounded documentation authority.
3. UI control without backend/auth: all current pages are specs, all corresponding code/tests absent. Planned slices include scopes/error paths; omission stays visible.
4. Failure path/operational/migration/launch gate: future task risk/test/migration/DoD fields required; critical policies linked to OD IDs, physical/staff/provider/recovery acceptance missing. Test planning is not completion.
5. Claimed result without evidence: setup evidence links exact commands/reviews, product results not claimed. Whole-project completion percentage deliberately omitted. Source review counts, verified documentation and actual software/physical/business acceptance remain separate.


Setup closeout: workflow document/discovery checks passed with actual evidence; all other planned tests remain NOT RUN. Source ranges/hashes establish declared inventory consistency and reviewers recorded actual reads, not a proof of future model auto-loading or implemented product.

## CN-DEC-001 decision evidence

[Brief](../decisions/CN-DEC-001-INITIAL-STACK-BRIEF.md) maps all three approved core requirements and workflow coverage; [evidence](evidence/CN-DEC-001.md) records official research and independent read-only review. Stable 92 requirement/test-plan and 27 change-set records retained; symmetric core/workflow task links corrected to include the already-listed decision task. Product test statuses stay NOT RUN and all product implementation NOT_VERIFIED. Brief preparation is VERIFIED documentation; technology approval AWAITING_OWNER_APPROVAL and implementation authorization NOT_GRANTED. Historical setup ledger remains its dated baseline, not a claim current edited files retain old hashes.
