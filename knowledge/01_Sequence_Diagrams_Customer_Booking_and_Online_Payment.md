# CineNest Sequence Diagram 01: Customer Booking and Online Payment

**Diagram:** 01 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show the time-ordered interactions for a customer quotation, provisional reservation, hosted online checkout, verified payment recording, and conditional booking confirmation.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer | Chooses the session, accepts the quotation and terms, and completes the selected payment method. |
| Customer Portal | Displays backend-issued information and submits authenticated customer requests. |
| Booking Use Cases | Coordinates the authoritative quotation, reservation inventory, eligibility checks, and purchased booking snapshot. |
| Payments | Owns payment attempts, provider-evidence processing, actual received funds, and allocation. |
| Module-owned Workers | Resume committed durable work under the appropriate scoped service identity. |
| PostgreSQL | Stores module-owned records and enforces transactional constraints. |
| Payment Gateway | Provides hosted checkout and authenticated server-side payment results. |

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
    box rgb(240, 238, 255) CineNest backend modules
        participant B as Booking Use Cases
        participant P as Payments
        participant W as Module-owned Workers
    end
    participant DB as PostgreSQL
    participant G as Payment Gateway
    Note over B,W: Logical modules in one backend. Protected calls use Sequence 05.
    C->>UI: Browse and select room, package, time, guests and extras
    UI->>B: Request quote and provisional reservation
    B->>B: Authorize; obtain pricing and venue rules through owner APIs
    B->>DB: Atomically create eligible bounded hold and quote reference
    alt Invalid selection or inventory conflict
        DB-->>B: Hold not created
        B-->>UI: Explain conflict and valid alternatives
    else Hold created
        DB-->>B: Hold ID, revision and expiry
        B-->>UI: Versioned quotation and terms
        C->>UI: Accept quote and applicable terms
        UI->>B: Record acceptance and request checkout
        B->>B: Check pre-confirmation eligibility via Verification API (04)
        alt Eligibility or quote/hold prerequisites not satisfied
            B-->>UI: Correction, review or expired-hold status; no checkout
        else Online checkout permitted
            UI->>P: Initiate online payment for accepted quote
            P->>DB: Commit attempt and idempotency reference
            P->>G: Create hosted checkout outside DB transaction
            G-->>P: Checkout URL or pending/failed creation outcome
            P-->>UI: Display actual checkout state
            opt Hosted checkout created
                C->>G: Complete payment
                G-->>UI: Browser redirect, not payment authority
                G-)P: Server-side payment result
                P->>P: Authenticate and correlate result
                alt Invalid provider evidence
                    P->>DB: Record safe rejection; no financial mutation
                else Authenticated result
                    P->>DB: Idempotently ingest result and actual payment status
                    alt Duplicate event
                        P-->>G: Acknowledge existing record
                        Note over P,W: Existing durable confirmation work remains retryable.
                    else Pending, unknown or confirmed failure
                        P->>DB: Record status; queue reconciliation or eligible hold release
                        Note over P,DB: Missing callbacks or timeouts do not prove payment failure.
                    else Funds confirmed
                        P->>DB: T1 commit verified funds and durable confirmation work
                        P-->>G: Acknowledge after durable recording
                        W->>DB: Claim committed confirmation work
                        W->>B: Confirm funded request using scoped service authority
                        B->>DB: T2 begin; recheck quote, eligibility, revision and inventory
                        B->>P: Validate and allocate required funds within T2
                        P->>DB: Guard amount, currency and unused allocation
                        alt All guards pass
                            B->>DB: Commit booking snapshot, allocation and follow-up jobs
                            B-->>W: Confirmed booking
                            W->>DB: Complete confirmation work; follow-ups remain durable
                            Note over B,W: Notification and access provisioning are separate jobs. See 08.
                        else Guard or concurrent reservation conflict
                            B->>DB: Roll back T2; record resolution in a new transaction
                            Note over B,DB: T1 funds survive. Never displace another reservation.
                        end
                    end
                end
            end
        end
    end
    C->>UI: View own booking and payment status
    UI->>B: Authorized current-state query
    B-->>UI: Booking, payment and access states shown separately
    Note over C,G: Bank payment uses Sequence 02. Exact providers and timing policies remain subject to approval.
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

- A displayed calendar is not an inventory lock. The hold and subsequent confirmation require authoritative, concurrency-safe inventory checks, including cleaning and maintenance.
- The browser redirect does not verify payment. Authenticate the provider result, correlate it to the accepted quote, and guard duplicate processing.
- T1 records genuine funds before T2 attempts booking confirmation. A failed T2 must not erase money already received. The shown local transaction propagation is a proposed implementation contract, not verified code.
- A duplicate callback can acknowledge the existing durable record while unfinished follow-up work remains retryable. It must not allocate funds again.
- Pending/unknown checkout creation or payment outcomes require reconciliation of the original attempt. Do not assume a timeout or closed browser means payment failed.
- An expired hold or a mismatched amount/currency leads to resolution, not automatic displacement of another reservation or silent repricing.
- The final status query applies only to an existing authorized request; invalid requests must not reveal another customer's records.
- Required pre-confirmation eligibility is separate from the all-guest original-ID check at the venue.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [04. Identity Evidence and Eligibility Review](04_Sequence_Diagrams_Identity_Evidence_and_Eligibility_Review.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
