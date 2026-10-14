# CineNest Sequence Diagram 07: Cancellation and Refund Execution

**Diagram:** 07 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Separate booking cancellation from refund authority and actual repayment, including reusable refund work for amendments or excess-funds resolution.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Authorized Staff | Request a policy-permitted cancellation and view financial status. |
| Booking | Owns cancellation, reservation revision checks, eligible inventory release and access-revocation intent. |
| Payments | Owns refundable balance, refund obligations, approvals, attempts and confirmed outcomes. |
| PostgreSQL / Module-owned Workers | Store durable state and resume cancellation, notification or refund work safely. |
| Finance Reviewer | Provides required scoped approval and independently verified manual bank-refund evidence. |
| Payment Gateway | Executes supported gateway refunds and supplies trustworthy status evidence. |
| Bank Portal | Represents a controlled manual transfer, not an assumed automatic CineNest bank API. |

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
    participant B as Booking
    participant P as Payments
    participant DB as PostgreSQL
    participant W as Module-owned Workers
    actor F as Finance Reviewer
    participant G as Payment Gateway
    participant Bank as Bank Portal
    opt Cancellation is the originating request
        C->>B: Request cancellation for owned/scoped booking
        B->>B: Authorize and evaluate accepted cancellation policy
        B->>DB: Read booking revision and purchased terms
        B->>P: Calculate remaining refund entitlement under accepted policy
        P-->>B: Entitlement and financial constraints
        B-->>C: Present cancellation and financial consequences
        alt Cancellation not permitted or required acceptance absent
            B-->>C: No change; explain permitted review/support path
        else Cancellation authorized and accepted
            B->>DB: Begin transaction; recheck booking revision and eligibility
            B->>P: Record any valid refund obligation in same transaction
            P->>DB: Persist obligation without marking it paid out
            alt Commit guards pass
                B->>DB: Commit cancellation, eligible inventory release and durable follow-ups
                B-->>C: Cancelled; refund and access revocation tracked separately
                Note over B,W: Queue incoming-credential revocation (08). Do not declare an occupied room empty.
            else Stale state or invariant failure
                B->>DB: Roll back cancellation and new obligation
                B-->>C: Conflict; current booking preserved
            end
        end
    end
    opt Outstanding refund obligation exists
        Note over P,DB: This branch also serves amendment or excess-funds resolution without cancelling their bookings.
        W->>DB: Claim committed refund work
        W->>P: Prepare outstanding refund
        P->>DB: Read original payments, prior refunds and remaining balance
        P-->>F: Request required scoped approval and destination verification
        F->>P: Submit approved action and independent approval evidence if required
        P->>P: Recheck permission, limits, policy and current refundable amount (05)
        alt Required authority missing or amount invalid
            P->>DB: Keep obligation pending with reason
        else Refund may proceed
            P->>DB: Atomically reserve refundable balance and persist attempt ID
            alt Original payment is gateway-funded
                P->>G: Request supported refund using recorded idempotency reference
                G-->>P: Accepted, completed, failed or unknown outcome
            else Original payment is bank-funded
                P-->>F: Recorded refund attempt and verified instructions
                F->>Bank: Execute controlled transfer outside CineNest
                Bank-->>F: Outgoing transaction evidence
                F->>P: Record independently verified outgoing outcome
                Note over F,Bank: Manual bank operation; no automatic banking API assumed.
            end
            alt Reliable evidence confirms completed refund
                P->>DB: Commit completed refund, balance change and notification job
                W-)C: Notify actual refund completion through configured channel
            else Pending or uncertain outcome
                P->>DB: Keep existing attempt pending; queue reconciliation
                loop Bounded reconciliation of the SAME refund attempt
                    W->>P: Reconcile provider status or authorized bank evidence
                    P->>DB: Persist verified status and follow-up work atomically
                end
                Note over P,Bank: Timeout is not permission to send another payment.
            else Confirmed failure
                P->>DB: Record failure; retain obligation for approved retry/escalation
            end
        end
    end
    C->>P: Query own permitted refund status
    P-->>C: Outstanding, approved, pending, failed or completed with evidence
    Note over B,P: Cancellation does not wait for external refund settlement. Financial approval and repayment completion remain distinct.
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

- Cancellation eligibility and refund entitlement use accepted booking terms and current evidence. An admin button cannot decide unapproved financial policy.
- The cancellation transaction and any newly created refund obligation succeed or roll back together. External repayment runs later.
- Release only eligible inventory. A cancelled active booking or uncertain access-revocation result does not prove the room is vacant or safe to rebook.
- Refund approval, amount reservation, initiated transfer, provider acceptance and completed repayment are different states.
- Reserve the remaining refundable balance before issuing a refund attempt. Concurrent approvals and retries must not repay the same funds twice.
- Unknown results require reconciliation of the same attempt. The loop stops on a reliable terminal outcome, exhausted retry policy or escalation; it is not an instruction to send another transfer.
- The refund branch can be invoked after an amendment or excess-funds resolution; it must not label those originating bookings cancelled.
- For mixed-method payment, track each original transaction and its remaining refundable balance. The displayed method branches represent a single supported refund attempt at a time.
- No bank API, stored card token, approved bank-refund destination policy or arbitrary financial limit is established by this diagram.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
