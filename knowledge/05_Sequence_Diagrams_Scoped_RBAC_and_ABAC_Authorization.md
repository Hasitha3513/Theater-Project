# CineNest Sequence Diagram 05: Scoped RBAC and ABAC Authorization

**Diagram:** 05 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show the backend interactions that authenticate a caller, resolve trusted authorization attributes, combine action and scope within an applicable grant, enforce contextual restrictions, and leave business validity with the owning module.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Staff / Service | Requests a protected action as an authenticated human or constrained integration identity. |
| Protected Entry Point | Receives the request and coordinates identity and module access without treating client-supplied attributes as authority. |
| Identity and Authorization | Evaluates current identity state, matching scoped grants, mandatory restrictions and applicable policy freshness. |
| Owning Business Module | Supplies minimal trusted resource attributes and enforces business invariants and scoped data access. |
| PostgreSQL | Stores module-owned identity/grant and resource records, with appropriate transaction and revision controls. |
| Audit Capability | Records attributable, purpose-appropriate decisions without leaking credentials or full private evidence. |

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
    actor X as Customer / Staff / Service
    participant E as Protected Entry Point
    participant A as Identity and Authorization
    participant O as Owning Business Module
    participant DB as PostgreSQL
    participant L as Audit Capability
    X->>E: Request action on a resource or scoped collection
    E->>A: Authenticate current session or integration identity
    A->>DB: Read identity status and relevant session/grant revision
    DB-->>A: Authoritative identity state
    alt Unauthenticated, suspended or invalid identity
        A-->>E: Deny
        E->>L: Record redacted denial where required
        E-->>X: Safe authentication/access error
    else Eligible authenticated principal
        E->>O: Resolve minimal authorization attributes through owner API
        O->>DB: Read trusted owner, venue, sensitivity, amount or case attributes
        DB-->>O: Minimal attributes, revision or missing resource
        O-->>E: Authorization facts, not unrestricted resource contents
        E->>A: Evaluate action, principal, resource and context
        A->>DB: Read applicable scoped grants and current policy
        DB-->>A: Grants, limits and policy/revocation version
        A->>A: Match permission AND target scope within an applicable grant
        A->>A: Evaluate ownership, purpose, limits, approvals and mandatory denials
        A->>A: Enforce required freshness and deny missing mandatory information
        alt No applicable permit
            A->>L: Record scoped denial without sensitive payloads
            A-->>E: Deny with safe reason code
            E-->>X: Deny without exposing another user's resource
        else Applicable scoped permit
            A-->>E: Permit with scope, restrictions and policy references
            E->>O: Execute use case with authenticated context and permitted scope
            opt Sensitive mutation or privilege-changing operation
                O->>A: Revalidate required current authority at commit boundary
                A-->>O: Current decision / changed authorization revision
            end
            O->>DB: Guard business invariants and current resource revisions
            alt Authorization changed or business guards fail
                O->>DB: Abort mutation; preserve business state
                O->>L: Record denied/conflicting operation as appropriate
                O-->>E: Rejected or retry/review required
            else All required checks pass
                alt Read, list, export or download
                    O->>DB: Query only authorized rows and permitted fields
                    DB-->>O: Scoped result
                else Valid mutation
                    O->>DB: Commit guarded changes and durable audit/follow-up intent
                    DB-->>O: Committed result
                end
                O-->>E: Authorized result
            end
            E-->>X: Return actual use-case outcome
        end
    end
    Note over A,O: A finance grant at Venue A plus operations at Venue B never grants finance authority at B.
    Note over O,DB: Authorization does not replace payment, booking or safety invariants. Revocation consistency at commit needs an explicit policy and tests.
    Note over X,L: Covers protected operations, not public browsing or independent emergency egress. Exact role bundles, limits and exceptions remain governed by their approval status.
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

- A protected action needs an applicable grant containing both the action permission and the matching resource scope. Do not stitch unrelated role grants into broader authority.
- Mandatory denials, account suspension and applicable self-approval restrictions cannot be bypassed by trying an alternative grant.
- Ownership, venue, amount, sensitivity, case purpose and policy attributes come from trusted server-side records. Client values are selectors, not authorization evidence.
- Resolve only the minimal facts needed for authorization without returning protected details. Collection operations derive a permitted query scope instead of loading and exposing all records.
- Apply checks to details, lists, counts, exports, private downloads, live subscriptions, scheduled work and integration actions, not just admin buttons.
- A permit is not a permanent authorization token. Define the freshness, revocation, revalidation and concurrency behavior for sensitive changes before implementation.
- The owning module still validates payment, reservation, capacity and lifecycle rules after authorization. A valid role does not make an invalid business operation acceptable.
- The audit lifeline is a logical capability, not an instruction to deploy a separate audit service or share unrestricted repositories.
- Role bundles, financial limits, independent-review rules and revocation timing retain their actual proposed/open status. Public browsing and independent emergency egress are outside this protected-operation flow.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [03. Package Editing, Publication and Deactivation](03_Sequence_Diagrams_Package_Editing_Publication_and_Deactivation.md)
- [04. Identity Evidence and Eligibility Review](04_Sequence_Diagrams_Identity_Evidence_and_Eligibility_Review.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [09. Room Maintenance and Controlled Closure](09_Sequence_Diagrams_Room_Maintenance_and_Controlled_Closure.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
