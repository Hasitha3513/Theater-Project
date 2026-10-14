# CineNest Sequence Diagram 06: Booking Amendment and Session Extension

**Diagram:** 06 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show the interaction sequence for a controlled booking amendment, retaining the original reservation while replacement inventory and any additional payment are validated.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Authorized Staff and Portal / Admin Panel | Request and accept permitted changes while seeing actual financial and access consequences. |
| Booking | Owns the original booking, replacement hold, accepted amendment and atomic inventory replacement. |
| Packages and Pricing | Provides the amendment quote under the applicable policy without silently substituting a newer catalog price. |
| Payments | Verifies additional funds and owns allocation or refund-obligation changes. |
| PostgreSQL | Protects booking revisions, inventory, funds and durable follow-up intent. |
| Module-owned Workers / Access Control | Synchronize credentials only for current committed booking revisions and track external outcomes separately. |

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
    actor C as Customer / Authorized Staff
    participant UI as Portal / Admin Panel
    participant B as Booking
    participant Q as Packages and Pricing
    participant P as Payments
    participant DB as PostgreSQL
    participant W as Module-owned Workers
    participant A as Access Control
    C->>UI: Request room, time, duration or package amendment
    UI->>B: Submit request with booking ID and expected revision
    B->>B: Authorize ownership/scope and accepted amendment policy (05)
    break Request not authorized or amendment not permitted
        B-->>UI: Deny; original booking unchanged
    end
    B->>DB: Read purchased snapshot and current booking revision
    DB-->>B: Existing terms, funds and reserved intervals
    B->>Q: Quote proposed amendment under applicable accepted policy
    Q-->>B: Itemized change, acceptance terms and financial difference
    B->>DB: Atomically create bounded replacement hold
    Note over B,DB: Hold is linked to its original booking; it blocks others without self-conflicting. Original reservation is retained.
    alt Replacement unavailable or invalid
        DB-->>B: No replacement hold
        B-->>UI: Explain conflict; preserve original booking
    else Replacement hold created
        B-->>UI: Amendment preview, amount difference and deadline
        C->>UI: Accept or decline changed terms
        alt Customer declines
            UI->>B: Abandon amendment
            B->>DB: Release replacement hold only
            B-->>UI: Original booking unchanged
        else Amendment accepted
            UI->>B: Record versioned amendment acceptance
            B->>DB: Persist accepted amendment request
            opt Additional amount is due
                B->>P: Initiate payment linked to amendment, not a new booking
                Note over C,P: Use online or bank verification subflow from 01/02; do not confirm a second reservation.
                P->>DB: Record actual verified additional funds independently
                P-->>B: Verified funds available, pending, failed or unknown
            end
            alt Required additional payment not yet verified
                B-->>UI: Amendment not applied; original remains active
                Note over B,DB: Replacement deadline still applies. Do not grant an unverified PIN/time extension.
            else Financial prerequisites satisfied
                B->>DB: Begin commit transaction; lock current booking and replacement hold
                B->>B: Recheck authority, revision, acceptance and inventory
                B->>P: Apply financial adjustment through owner API in same transaction
                P->>DB: Guard allocation or record valid refund obligation
                alt All guards succeed
                    B->>DB: Atomically replace inventory; persist amendment and durable follow-ups
                    DB-->>B: Committed new booking revision
                    B-->>UI: Revised purchased booking; external work pending
                    W->>DB: Claim committed access-synchronization work
                    W->>A: Synchronize credentials for committed revision
                    A->>B: Read latest authoritative access window
                    B-->>A: Current revision and eligibility
                    alt Work references current eligible revision
                        A->>A: Provision/update through safe Sequence 08 recovery
                    else Stale or revoked revision
                        A->>A: Reject stale work; reconcile latest desired access
                    end
                    Note over P,W: Refund obligations continue in Sequence 07. Notice, refund and access outcomes are separate.
                else Concurrent change or invariant failure
                    B->>DB: Roll back amendment and same-transaction adjustments
                    B->>DB: Record resolution; release failed replacement hold as permitted
                    B-->>UI: Original booking preserved; extra money remains traceable
                end
            end
        end
    end
    Note over C,A: Catalog editing does not amend sold bookings. External payment and lock calls are outside the commit transaction.
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

- Quote the amendment from the purchased snapshot and applicable accepted policy. This export does not decide unapproved repricing or amendment eligibility rules.
- The original reservation remains intact until successful commit. The replacement hold understands its originating booking identity so it does not self-conflict while still protecting inventory from others.
- Additional payment uses the verification portion of Sequence 01 or 02 and is bound to an amendment. It must not accidentally create a second cinema booking.
- Pending, unknown or failed extra payment is not an approved extension. The original reservation and actual replacement-hold deadline remain in force.
- At commit, recheck authorization, the original booking revision, accepted terms, replacement inventory, payment allocation and any refund obligation.
- A failed amendment cannot erase extra money already received. Reconcile or refund it through an authorized workflow.
- Only the latest committed booking revision may drive access synchronization. Delayed jobs/callbacks must not restore an old credential window.
- A refund obligation can arise without cancellation. Refund processing, access synchronization and notices have their own outcomes and recovery.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [03. Package Editing, Publication and Deactivation](03_Sequence_Diagrams_Package_Editing_Publication_and_Deactivation.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [09. Room Maintenance and Controlled Closure](09_Sequence_Diagrams_Room_Maintenance_and_Controlled_Closure.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
