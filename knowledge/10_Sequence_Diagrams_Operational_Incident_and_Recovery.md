# CineNest Sequence Diagram 10: Operational Incident and Recovery

**Diagram:** 10 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show authorized incident intake, immediate safety response where needed, module-owned recovery with bounded retries, and evidence-based technical closure without hiding outstanding financial or room-readiness obligations.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Staff / Device Signal | Supplies a permitted report or authenticated operational event; the label abstracts several possible sources. |
| Operations Incident Use Case | Correlates incidents, assesses impact, assigns responders and records actual recovery state. |
| Authorization | Evaluates protected recovery actions and current matching scope. |
| PostgreSQL | Retains incident context, restrictions, command intent and residual obligations. |
| Authorized / Qualified Responder | Performs approved local safety response and necessary qualified verification. |
| Owning Business Module | Owns the affected state and recovery action, reached through its published application interface. |
| Device / External Provider | Returns supported observations and command outcomes; no capability is assumed merely from its lifeline. |

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
    actor S as Customer / Staff / Device Signal
    participant O as Operations Incident Use Case
    participant A as Authorization
    participant DB as PostgreSQL
    actor R as Authorized / Qualified Responder
    participant M as Owning Business Module
    participant X as Device / External Provider
    Note over S,R: Independent emergency reporting and physical exit do not wait for application authorization or logging.
    S->>O: Report issue or submit authenticated operational event
    O->>O: Validate intake source, target scope and safe payload
    alt Unauthorized protected operation or invalid event
        O->>DB: Record safe security information when appropriate
        O-->>S: Reject protected processing; disclose no sensitive details
    else Permitted incident intake
        O->>DB: Create or correlate incident; retain actual timestamps and resource references
        O->>O: Assess impact from evidence; keep uncertain states explicit
        O->>DB: Commit assignment and durable responder notification
        O-)R: Notify incident owner through configured channel
        alt Immediate safety risk suspected
            R->>R: Invoke approved local response and independent safe exit
            R->>O: Record safety findings when system access is available
            O->>M: Apply permitted room restriction and preserve affected bookings
            M->>DB: Record restriction and customer-resolution obligations
            break Safety response incomplete or technical recovery not yet permitted
                O->>DB: Keep incident open and room restricted
                O-->>S: Appropriate safety/support status
            end
        else No immediate safety risk established
            O-->>R: Current evidence, prior attempts and known unknowns
        end
        R->>O: Propose supported recovery for current resource revision
        O->>A: Evaluate requested recovery action and matching scope (05)
        A-->>O: Scoped permit or denial
        alt Recovery authority or safe capability missing
            O->>DB: Escalate with owner and follow-up deadline
            O-->>R: No privileged action issued; incident remains open
        else Recovery may proceed
            O->>M: Investigate through resource owner's application API
            M->>DB: Read current state, recorded commands and prior outcomes
            opt External status can be queried safely
                M->>X: Reconcile previous request under supported contract
                X-->>M: Trustworthy current result or unknown
            end
            loop Only while another safe recovery attempt is authorized
                R->>O: Confirm current permitted recovery step
                O->>M: Revalidate authority, revision and safety before action
                M->>DB: Persist bounded command/work intent and idempotency reference
                alt Supported remote recovery
                    M->>X: Execute outside DB transaction
                    X-->>M: Acknowledgement, failure or uncertain outcome
                    M->>DB: Persist actual command result and reconciliation work
                else Approved local fallback is necessary and tested
                    O-->>R: Apply authorized on-site procedure
                    R->>R: Perform qualified local recovery
                end
                R->>O: Report observations and required physical checks
                O->>M: Request current service/readiness evidence
                M-->>O: Verified outcome or unresolved state
                Note over O,R: Stop attempts on success, unsafe conditions or the configured retry/deadline limit.
            end
            alt Service or required safety checks remain unresolved
                O->>DB: Retain open incident, room restrictions and escalation ownership
                O-->>S: Actual unresolved status and support path
            else Restoration is independently supported by evidence
                O->>DB: Commit restoration evidence and residual follow-up work
                O->>M: Request permitted readiness and business-consequence handling
                Note over O,M: Module-specific work: booking amendments (06), refunds (07), access/readiness (08/09). No direct writes into another owner's records.
                M-->>O: Actual follow-up statuses; remaining obligations retained
                O->>DB: Close technical incident only when closure criteria pass
                O-)S: Notify permitted recipients of actual recovery outcome
            end
        end
    end
    Note over S,X: A total network outage is not repaired by a cloud unlock. Unknown telemetry is not proof of health or vacancy.
    Note over O,M: Technical incident closure does not prove refund completion, credential readiness or room availability.
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

- Validation of an application incident event protects privileged processing and private data; it does not prevent independent emergency reporting or physical exit.
- Safety response starts independently of log persistence, a network connection or a payment/authorization result. Record evidence when access is available.
- The operations incident use case is an owning-module workflow, not a universal workflow service with direct write access to every table.
- Do not blindly repeat earlier unlocks, payments or refunds. Reconcile previous attempts, current resource revision and external evidence before safe recovery.
- Recovery actions require applicable authority, current business-state guards and actual device/provider capability; an administrator label is not a safety override.
- Bound attempts by the approved retry/deadline policy, stopping on success or unsafe conditions. No specific timer or numerical limit is selected by this export.
- Use only tested local fallback during a total connectivity outage. Cloud remote control is not an offline guarantee.
- Technical closure requires credible restoration evidence. Room availability still needs readiness/inventory checks, and unpaid refund obligations remain independently visible.
- Notify only appropriate human recipients. The combined source lifeline does not imply that financial or customer evidence is sent to device reporters.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [09. Room Maintenance and Controlled Closure](09_Sequence_Diagrams_Room_Maintenance_and_Controlled_Closure.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
