# CineNest Sequence Diagram 02: Bank Deposit, Receipt Review and Approval

**Diagram:** 02 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show receipt submission, independent review of bank credit, single recording/allocation of funds, and confirmation only when the requested reservation still passes its guards.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Customer Portal | Request payment instructions, submit private receipt evidence, and view actual review and booking states. |
| Finance Reviewer | Independently checks the business receiving account and records a decision within approved authority. |
| Payments | Owns payment requests, evidence references, review outcomes, bank-credit identity, and allocations. |
| Booking | Owns eligible holds, reservation guards, confirmation, and purchased snapshots. |
| Private Receipt Storage | Retains protected evidence, submission versions, and safe reviewer access. |
| PostgreSQL / Module-owned Workers | Persist reviewed state and execute committed notification and follow-up work. |
| Bank Portal / Records | Represents customer banking and the reviewer's independent manual inspection; it is not a claimed direct CineNest integration. |

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
    actor F as Finance Reviewer
    participant P as Payments
    participant B as Booking
    participant S as Private Receipt Storage
    participant DB as PostgreSQL
    participant W as Module-owned Workers
    participant Bank as Bank Portal / Records
    Note over P,W: Internal modules, stores and workers; not separate business microservices.
    Note over F,Bank: Bank interaction is manual. No direct bank API is assumed.
    C->>UI: Choose bank deposit for accepted package quotation
    UI->>P: Request bank-payment instructions
    P->>B: Validate method eligibility and bounded reservation hold
    B->>DB: Check and persist eligible hold with actual deadline
    B-->>P: Hold outcome
    break Bank method or reservation prerequisites not satisfied
        P-->>UI: Explain alternatives; do not invite payment
    end
    P->>DB: Save request, quote and receiving-account instruction versions
    P-->>UI: Amount, reference, bank details and payment/review deadlines
    C->>Bank: Deposit cash or transfer against instructions
    Bank-->>C: Customer payment evidence
    C->>UI: Upload receipt and declared payment details
    UI->>P: Submit evidence for owned payment request
    P->>P: Check ownership and safe-upload validation
    break Unsafe file or unauthorized submission
        P-->>UI: Reject upload; preserve request and actual deadlines
    end
    P->>S: Store restricted receipt and submission version
    S-->>P: Private evidence reference
    P->>DB: Commit claim, history and durable review notification
    P-->>UI: Under review; not paid or confirmed
    W->>DB: Claim committed reviewer-notification work
    W-)F: Notify reviewer through configured channel
    F->>P: Open claim under scoped authority (Sequence 05)
    P->>S: Retrieve permitted safe preview
    S-->>P: Evidence preview
    P-->>F: Claim, quote, bank account and hold status
    F->>Bank: Independently inspect receiving-account credit
    Bank-->>F: Actual credit evidence or no match
    alt Evidence insufficient or credit not yet visible
        F->>P: Request correction or mark awaiting bank credit
        P->>DB: Record review outcome and escalation work
        P-->>UI: Actual pending/correction status; deadlines unchanged
    else Claim cannot be substantiated
        F->>P: Reject claim with attributable reason
        P->>DB: Retain history and possible late-credit reconciliation
        P-->>UI: Claim rejected; no payment verified
    else Actual credit independently verified
        F->>P: Verify credit with bank reference, amount and currency
        P->>P: Recheck reviewer scope, restrictions and policy
        P->>DB: T1 record verified credit once and durable confirmation work
        P->>DB: Lock request and inspect existing allocations
        alt Same request already fully processed
            P-->>F: Return existing result; resume incomplete jobs safely
        else New allocation may be attempted
            P->>DB: T2 begin; lock available funds and request revision
            P->>B: Validate reservation and eligibility inside T2
            B->>DB: Guard room interval, hold revision and accepted quote
            B-->>P: Eligible or resolution required
            alt Correct currency, sufficient unused funds and valid booking
                P->>DB: Allocate only amount due and record approval
                P->>B: Persist confirmed booking within T2
                B->>DB: Write booking snapshot and confirmation follow-up work
                P->>DB: Commit T2; preserve any unallocated excess
                P-->>F: Verified payment and confirmed booking
                W->>DB: Claim committed notification/access work
                Note over B,W: Follow-up uses Sequence 08; access readiness is separate.
            else Amount, scope, quote or inventory guard fails
                P->>DB: Roll back T2; persist resolution in a new transaction
                P-->>F: Funds recorded; booking or money requires resolution
                Note over P,DB: Under/overpayment, late credit and double payment remain traceable.
            end
        end
    end
    C->>UI: View own payment and booking status
    UI->>P: Authorized current-state query through owner APIs
    P-->>UI: Review, funds, allocation, booking and access states
    Note over C,Bank: Repeated uploads never extend holds indefinitely. Received money does not guarantee room availability.
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

- A receipt upload is a customer claim. Approve only after independent bank-side verification under the appropriate scope and restrictions; OCR is not approval authority.
- Review, payment credit, allocation, booking, and access remain separate lifecycles. A rejected receipt must not erase funds that arrive later.
- Payment, submission, review, quote, and reservation-hold deadlines are distinct. Exact values and convenience-fee treatment remain open unless approved in the canonical records.
- Repeated submissions do not renew a hold indefinitely. Late money remains traceable even when the request is no longer payable or the original room is unavailable.
- Use actual bank transaction identity and concurrency-safe allocation. Receipt hashes or customer-entered references alone cannot establish unique funds.
- The initial allocation lookup is not sufficient by itself; T2 must repeat the applicable uniqueness and balance checks while holding the relevant transactional protection.
- T1 survives a failed confirmation attempt. Only the amount due is allocated; excess or mixed-method duplicate payment enters controlled resolution.
- Receipt object storage and the DB are not one atomic transaction. Define safe retries, orphan cleanup, and recovery rather than claiming distributed atomicity.
- The diagram abstracts the notification delivery channel. Failed delivery does not remove the durable review item, and reviewer access still requires authorization.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
