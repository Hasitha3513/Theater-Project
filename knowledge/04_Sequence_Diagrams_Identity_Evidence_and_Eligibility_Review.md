# CineNest Sequence Diagram 04: Identity Evidence and Eligibility Review

**Diagram:** 04 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show protected identity-evidence submission, independent processing work, conditional OCR-assisted decisioning, authorized manual review, and publication of the customer's actual digital eligibility status.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Customer Portal | Submit permitted evidence for an owned case and view authorized status or correction instructions. |
| Verification | Owns case state, restricted evidence access, processing rules and attributable decisions. |
| Private Evidence Storage | Stores protected evidence versions and provides scoped access. |
| PostgreSQL | Persists cases, processing/review work, decisions and audit references. |
| Verification Worker | Runs committed processing or review-notification work with bounded recovery. |
| OCR Provider | Extracts necessary document fields; does not itself prove identity, authenticity or liveness. |
| Verification Officer | Reviews permitted cases under a legitimate purpose and records a valid, current decision. |

## Editable sequence diagram

```mermaid
---
config:
  theme: base
  themeVariables:
    fontFamily: Arial, sans-serif
    actorBkg: '#F0EEFF'
    actorBorder: '#5B4BFF'
    actorTextColor: '#111827'
    signalColor: '#334155'
    signalTextColor: '#111827'
    noteBkgColor: '#FFF7E6'
    noteBorderColor: '#B45309'
  sequence:
    wrap: true
    useMaxWidth: true
    actorMargin: 25
    messageMargin: 24
---
sequenceDiagram
    autonumber
    actor C as Customer
    participant UI as Customer Portal
    participant V as Verification
    participant S as Private Evidence Storage
    participant DB as PostgreSQL
    participant W as Verification Worker
    participant OCR as OCR Provider
    actor R as Verification Officer
    C->>UI: Submit permitted identity evidence
    UI->>V: Upload for owned case with required consent
    V->>V: Enforce ownership, safe-upload checks and allowed format
    break Unsafe upload or unauthorized case
        V-->>UI: Safe rejection or correction instructions
    end
    V->>S: Store protected, scanned evidence version
    S-->>V: Restricted object reference
    V->>DB: Commit submission, audit and processing job
    V-->>UI: Evidence received; processing pending
    W->>DB: Claim committed case-processing work
    W->>V: Process current submission under service scope
    V->>S: Read permitted evidence
    S-->>V: Required document data
    V->>OCR: Extract necessary fields outside DB transaction
    OCR-->>V: Fields and confidence, error or timeout
    alt Provider error or unusable extraction
        V->>DB: Persist pending/retry state and failure evidence
        loop Only while bounded retry policy permits
            W->>V: Resume the same processing job
            V->>OCR: Retry permitted extraction
            OCR-->>V: Current result
            V->>DB: Record result; stop retries on usable outcome
        end
        Note over V,R: Still uncertain after retry limits means manual review or escalation, never automatic approval.
    else Usable extraction
        V->>DB: Persist extracted fields and validation outcome
    end
    V->>V: Evaluate deterministic checks and approved decision policy
    alt Reliable outcome and automated pre-decision explicitly approved
        V->>DB: Commit digital eligibility outcome and policy/evidence references
    else Manual review required
        V->>DB: Commit review assignment/queue and notification work
        W-)R: Notify authorized reviewer via configured channel
        R->>V: Open case for a legitimate review purpose
        V->>V: Evaluate evidence permission and matching scope (Sequence 05)
        alt No applicable reviewer authority
            V-->>R: Deny evidence access; retain queued case
        else Evidence access allowed
            V->>S: Obtain restricted safe preview
            S-->>V: Safe evidence view
            V->>DB: Audit sensitive evidence access
            V-->>R: Case, extraction results and permitted evidence
            R->>V: Submit eligible, ineligible or more-information decision
            V->>V: Recheck current authority, case revision and decision rules
            alt Stale case or invalid decision authority
                V-->>R: Reject stale decision; reload/review required
            else Valid reviewer decision
                V->>DB: Commit attributable decision or correction request
                opt More information required
                    V-->>UI: Request correction without extending the booking hold
                    C->>UI: Submit a new evidence version through the same checks
                    Note over UI,V: Preserve old submissions and decisions; re-enter this sequence.
                end
            end
        end
    end
    V->>DB: Persist outcome notification work when state changes
    C->>UI: View own verification status
    UI->>V: Authorized outcome query
    V-->>UI: Actual pending, correction, eligible or ineligible state
    Note over C,R: OCR is text extraction, not proof of document authenticity. Digital eligibility is separate from all-guest original-ID check-in (08).
    Note over S,DB: Storage and database commits are not one distributed transaction; orphan-object cleanup and recovery must be defined.
```

## Reading this sequence diagram

Time proceeds down the diagram. A lifeline names a person, interface, logical module, store, worker or external participant; separate lifelines do **not** mandate separate deployed microservices.

| Notation | Meaning in this export |
| --- | --- |
| `->>` | Request, call or action message. A self-message describes local processing. |
| `-->>` | Return, response or reported outcome; the message label determines whether it is successful, pending or failed. |
| `-)` | Asynchronous event or notification. Delivery, acknowledgement and business completion remain distinct. |
| `alt` / `else` | Alternative branches selected by their labeled conditions. |
| `opt` | Behavior that occurs only when its condition applies. |
| `loop` | Repeated work under the stated exit/retry conditions, not an unbounded busy loop. |
| `par` / `and` | Independently progressing interactions. Used in Sequence 08 for device handling and session-clock work. |
| `break` | The enclosing interaction stops on the stated condition. |
| `Note over` | An explanatory constraint, not a message or an implemented test. |

The `autonumber` labels identify messages; they are not canonical requirement or task IDs. A call named `API` can be an in-process module interface. Arrows to PostgreSQL abbreviate access through each module's own persistence adapter; they do not grant cross-module repository/table access.

Render the fenced `mermaid` block in a Mermaid-enabled Markdown viewer. If a viewer lacks Mermaid frontmatter support, remove only the initial `---` configuration section **inside the code fence**, retaining the `sequenceDiagram` content. No external images or fonts are required by these Markdown exports.

## Key safeguards and recovery

- Evidence submission, safe storage, extraction and eligibility decisions have separate outcomes. File rejection is not an automatic eligibility denial.
- Automated digital decisioning is conditional on an approved policy and a sufficiently reliable outcome. It must not be enabled merely because the diagram contains an automated branch.
- OCR failure uses bounded retries and then manual review or escalation; no reviewer available means pending review, not approval.
- All sensitive evidence reads require explicit purpose and applicable scope and are audited. Broad customer-management privileges are not evidence-access authority.
- A reviewer must decide the current case/submission revision under current authority. Retain earlier submissions and decisions when the customer corrects evidence.
- Review and correction pauses do not extend booking holds automatically. The actual hold and payment deadlines remain visible.
- Notification work belongs in the same durable change boundary as the decision or in an equivalent reliable publication mechanism; it must not be lost between an update and an in-memory enqueue.
- Every attendee still requires the approved on-site original-ID/entry process. This sequence does not certify a staff-less enforcement method.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
