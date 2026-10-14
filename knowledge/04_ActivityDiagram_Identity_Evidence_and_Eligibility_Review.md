# CineNest Activity Diagram 04: Identity Evidence and Eligibility Review

**Diagram:** 04 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed workflow; implementation and execution tests not verified.<br>
**Artifact:** Standalone requested Markdown export; no project/repository files modified.

## Purpose

Separate safe evidence submission, OCR processing, optional policy-approved automated pre-decision, manual review, and the attributable digital eligibility outcome.

Exported from one of the eight detailed interactive activity diagrams. The overview poster is a visual summary, not the detailed workflow contract.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer | Uploads permitted evidence for their own authorized case and supplies corrections. |
| CineNest verification module | Protects evidence, coordinates processing, and applies the current eligibility policy. |
| OCR provider | Extracts necessary text and confidence/uncertainty information; does not establish authenticity. |
| Verification officer | Reviews permitted evidence for an authorized purpose and records the case outcome. |

## Editable activity diagram

```mermaid
---
config:
  theme: base
  themeVariables:
    fontFamily: Arial, sans-serif
    fontSize: 16px
    primaryColor: '#F0EEFF'
    primaryTextColor: '#111827'
    primaryBorderColor: '#5B4BFF'
    lineColor: '#64748B'
  flowchart:
    curve: linear
---
flowchart TB
    Start((Start)) --> Upload("Customer: submit permitted identity evidence<br/>for an authorized verification case")
    Upload --> Safe{"Case ownership, consent and<br/>safe-upload checks pass?"}
    Safe -->|No| Correct("Reject unsafe or invalid submission<br/>Explain permitted correction")
    Correct --> Upload
    Safe -->|Yes| Store("Store evidence privately with restricted access<br/>Record submission and queue processing")
    Store --> OCR("OCR service: extract necessary fields<br/>Record uncertainty and provider outcome")
    OCR --> Returned{"Usable extraction returned?"}
    Returned -->|No| Recovery{"Safe retry still available<br/>under the bounded policy?"}
    Recovery -->|Yes| Retry("Keep processing pending<br/>Retry with backoff")
    Retry -.->|Retry due| OCR
    Recovery -->|No| Queue("Route case to authorized manual review")
    Returned -->|Yes| Check("Backend: validate fields and document structure<br/>Evaluate applicable age / eligibility policy")
    Check --> Automatic{"Reliable result and approved policy<br/>permit an automated pre-decision?"}
    Automatic -->|No| Queue
    Automatic -->|Yes| Result{"Pre-confirmation<br/>eligibility satisfied?"}
    Result -->|Yes| Eligible("Record digital eligibility outcome<br/>with source and policy version")
    Result -->|No| Ineligible("Record reasoned ineligible outcome<br/>Provide permitted review or correction route")
    Queue --> Officer("Officer: check case scope and evidence purpose<br/>before reading documents: Activity 05")
    Officer --> Grant{"Authorized reviewer available?"}
    Grant -->|No| Pending([Pause: pending authorized review])
    Grant -->|Yes| Review("Review evidence and checks<br/>Record attributable decision")
    Review --> Manual{"Review outcome?"}
    Manual -->|More evidence needed| More("Request correction<br/>Retain submission history and real deadlines")
    More -.->|New submission| Upload
    Manual -->|Eligible| Eligible
    Manual -->|Ineligible| Ineligible
    Eligible --> Notify("Notify customer through permitted channel<br/>Expose only their own result")
    Ineligible --> Notify
    Notify --> Done([End: digital decision recorded])
    Note["OCR is text extraction, not proof of authenticity or liveness.<br/>Pre-confirmation eligibility is distinct from all-guest check-in.<br/>Original physical-ID checks remain required before entry.<br/>Provider outages must not produce automatic approval."]
    Note -.- Eligible

    classDef terminal fill:#0B1526,color:#FFFFFF,stroke:#0B1526;
    classDef actor fill:#DBEAFE,color:#111827,stroke:#2878FF;
    classDef decision fill:#FFF7E6,color:#111827,stroke:#B45309;
    classDef success fill:#DCFCE7,color:#14532D,stroke:#15803D;
    classDef exception fill:#FEE2E2,color:#7F1D1D,stroke:#B91C1C;
    classDef pending fill:#FFF7E6,color:#111827,stroke:#B45309;
    classDef note fill:#F8FAFC,color:#475569,stroke:#94A3B8,stroke-dasharray:4 3;
    class Start,Pending,Done terminal;
    class Upload,OCR,Officer,Review actor;
    class Safe,Returned,Recovery,Automatic,Result,Grant,Manual decision;
    class Correct,Ineligible exception;
    class Queue,Retry,More pending;
    class Eligible success;
    class Note note;
```

## Diagram notation

This is an **activity-style control-flow diagram expressed with Mermaid flowchart notation**, not a strict UML/XMI model. It preserves the discussed workflow decisions without claiming executable behavior.

- The start circle begins this activity; nodes labeled **End** or **Pause** identify outcomes. A pause does not mean the booking, money, or incident has disappeared.
- Rounded actions describe work. Diamonds are decisions with labeled guards/outcomes. Double-border boxes reference another activity.
- Solid arrows show control flow. Dashed arrows with labels represent later events or retries; unlabelled dashed note connectors do not control execution.
- Blue actions identify people or external interactions where labeled; pale violet is ordinary system work, amber is a decision or pending state, green is a verified successful outcome, and red is an exception. Text remains authoritative over color.
- Responsibility is written in node labels and the participant table. These are not fixed swimlane columns. Sequential drawing does not make remote operations one atomic transaction, and no parallel fork/join is implied unless explicitly shown.

Render the fenced `mermaid` block in a Mermaid-capable Markdown viewer. The surrounding text remains readable without rendering. The viewer must support Mermaid frontmatter configuration; remove only the `---` configuration section inside the block when using a renderer that does not support it.

## Main activity outline

1. Check submission ownership, applicable consent, permitted document type, and safe-upload controls.
2. Store necessary evidence privately and create durable processing work.
3. Run OCR as text extraction and evaluate deterministic structural/eligibility checks.
4. Use bounded retries for outages. If processing remains uncertain, refer to manual review rather than approving by default.
5. Allow an automated digital pre-decision only under an explicitly approved policy; otherwise require an authorized reviewer.
6. Record the decision, source/evidence, policy version, and reviewer where applicable.
7. Expose the customer's permitted outcome and correction route. All-guest on-site checks remain separate.

## Guards and interpretation rules

- An automated path in this diagram is conditional design, not approval to activate automated identity decisioning.
- A safe file rejection differs from a denied eligibility decision. Preserve the distinction in customer status.
- Use necessary data only, restricted retrieval, safe previews, and the approved retention/deletion policy.
- An unreadable or malicious file must not reach unrestricted reviewer access. A normal customer-admin role does not grant evidence access.
- No officer available means pending review, not a successful decision.
- A review/correction pause does not guarantee the original booking hold survives.
- On-site originals and guest checks remain mandatory according to the approved entry policy; the physical enforcement method requires its own approval.

## Alternative outcomes and recovery

| Condition | Intended handling |
| --- | --- |
| OCR outage | Bounded retries, then controlled manual-review/incident handling. |
| Uncertain or inconsistent fields | Correction or manual review, not authenticity claimed from OCR. |
| Out-of-scope reviewer | Deny access and retain the case for an authorized reviewer. |
| Customer wants to correct an outcome | Retain prior decisions and follow the approved re-review process. |
| Notification failure | Keep the recorded decision and retry delivery without changing eligibility. |

## Review before implementation

Treat this diagram as an analysis/design view. Trace every guard and recovery route to the corresponding canonical requirement and planned acceptance check. Test authorization, state changes, duplicate processing, concurrency, and recovery as applicable. For physical/device outcomes, distinguish a simulation from actual hardware evidence. No application, payment-provider, or hardware acceptance test is claimed by this export.

## Related activity diagrams

- [01. Customer Booking and Online Payment](01_ActivityDiagram_Customer_Booking_and_Online_Payment.md)
- [05. Scoped RBAC and ABAC Authorization](05_ActivityDiagram_Scoped_RBAC_and_ABAC_Authorization.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_ActivityDiagram_Cinema_Entry_IoT_Control_and_Room_Reset.md)

## Source and authority

- Use-case export 04: Identity Verification and Authorization
- Detailed interactive activity 4 of 8

These files are standalone exports, not updates to the repository. The current four canonical knowledge documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and their authoritative registries continue to govern requirements, approvals, and implementation state.

Diagram labels, actor bundles, pending policies, timer values, provider/hardware choices, and retry behavior retain their actual canonical status. This export neither approves unresolved policies nor certifies application code, external integrations, bank reconciliation, device behavior, or physical safety. All protected operations use the applicable scoped authorization; business invariants remain separate.
