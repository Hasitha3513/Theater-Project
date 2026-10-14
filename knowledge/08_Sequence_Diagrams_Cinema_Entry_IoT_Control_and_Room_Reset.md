# CineNest Sequence Diagram 08: Cinema Entry, IoT Control and Room Reset

**Diagram:** 08 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show revision-bound access provisioning, guest check-in, physical entry, independent device and session-clock work, and operational evidence before room readiness is restored.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer and Guests | Arrive, complete the required original-ID check-in, and use a valid incoming-entry credential. |
| Authorized On-site Staff | Record check-in, request scoped controls, respond to incidents and establish actual cleaning/readiness. |
| Module-owned Workers | Execute committed access and session-clock work without making emergency exit depend on software. |
| Access Control | Owns credential intent, current-booking validation, installed outcome, expiry and revocation recovery. |
| Smart Lock / Lock Service | Implements supported, physically tested credential and incoming-entry behavior. |
| Device Operations / Room Controller and Devices | Separate authorized command intent, acknowledgement, telemetry and observed physical state. |
| Booking and Room APIs | Represent distinct owning-module interfaces for current reservation/session facts and operational readiness, not a shared cross-module repository. |

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
    actor G as Customer and Guests
    actor O as Authorized On-site Staff
    participant W as Module-owned Workers
    participant A as Access Control
    participant L as Smart Lock / Lock Service
    participant D as Device Operations
    participant H as Room Controller / Devices
    participant R as Booking and Room APIs
    Note over G,R: Proposed interaction contract. Check-in enforcement, lock compatibility and offline behavior require physical approval/tests.
    W->>A: Execute committed provisioning work
    A->>R: Read current booking revision, purchased window and eligibility
    R-->>A: Authoritative provisioning context
    break Booking cancelled, superseded or prerequisites absent
        A->>A: Skip stale work; reconcile latest desired access
    end
    A->>A: Commit request ID, desired window and attempt state
    A->>L: Provision supported room-bound credential and validity window
    L-->>A: Device-contract installation result
    alt Pending, unknown or failed provisioning
        A->>A: Persist true outcome; schedule bounded reconciliation
        A-)O: Raise access incident when response is needed (10)
        Note over A,L: Do not blindly create another credential after a timeout.
    else Installation confirmed
        A->>A: Persist readiness evidence and protected notification work
    end
    G->>O: Arrive and present required original IDs for all guests
    O->>A: Record authorized, attributable check-in outcome
    A->>R: Recheck booking, guest eligibility and room readiness
    R-->>A: Current entry conditions
    alt Eligibility, readiness, time or credential conditions not satisfied
        A-->>O: Deny incoming entry or wait; provide support
        O-->>G: Explain actual restriction; independent exit unaffected
    else Entry requirements satisfied
        A->>L: Apply approved physical check-in gate under supported contract
        L-->>A: Gate outcome confirmed
        G->>L: Enter valid PIN at the correct room
        L->>L: Enforce credential, gate and installed time boundaries
        alt Invalid PIN or current lock conditions fail
            L-->>G: No incoming unlock; support available
        else Credential accepted
            L-->>G: Permit incoming access
            L-)A: Authenticated entry event when connectivity allows
            A->>R: Record relevant entry against current session
            A->>D: Request approved initial cinema scene
            D->>D: Persist scoped command IDs and deadlines
            D->>H: Send supported AC, lighting and projector commands
            par Observed states and authorized controls
                loop While session active, within command deadlines
                    H-)D: Authenticated acknowledgements and telemetry
                    D->>D: Deduplicate; distinguish acknowledged and observed state
                    opt Staff requests another room command
                        O->>D: Request scoped room control
                        D->>D: Authorize (05), validate capability/safety, persist intent
                        alt Permitted and safe
                            D->>H: Send supported revision-bound command
                            H-->>D: Acknowledged, failed or unknown
                        else Denied or unsupported
                            D-->>O: No command sent; explain safe reason
                        end
                    end
                    opt Failure, stale telemetry or missing acknowledgement
                        D-)O: Incident escalation with actual state (10)
                    end
                end
            and Independent session-clock work
                W->>R: Read current session revision and purchased end
                R-->>W: Current timing, not stale pre-amendment timing
                opt Five-minute warning due and not already issued
                    W-)G: Warning through configured notification channel
                end
                Note over W,L: Installed credential expiry and emergency egress do not wait for command/notification completion.
                W->>A: Reconcile current session-end expiry or authorized revocation
                A->>L: Reconcile expired/revoked incoming credential
                L-->>A: Current evidence or unknown state
                A->>A: Persist outcome; unresolved access remains visible
                A->>R: Record session-end/access outcome through owner APIs
            end
        end
    end
    opt Session completed or terminated and room needs reset
        O->>O: Verify actual departure; perform cleaning and required checks
        O->>R: Submit room-readiness evidence under scoped authority
        R->>R: Check buffer, occupancy, access, maintenance and safety restrictions
        alt Readiness checks complete
            R->>R: Commit READY state without removing unrelated bookings
            R-->>O: Ready; availability still determined by inventory
        else Incomplete or unknown
            R-->>O: Keep unavailable; retain operational follow-up
        end
    end
    Note over G,L: Pre-provisioning cannot bypass check-in; otherwise credential enabling must wait. Emergency exit is always independent of PIN, payment and cloud.
    Note over D,R: A timer, exit event or command acknowledgement alone does not prove an empty, clean or safe room.
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

- The currently committed booking revision, purchased window and eligibility govern access work. Discard or reconcile stale work instead of restoring a superseded or cancelled credential.
- A failed or unknown installation remains unresolved. The subsequent successful entry branch applies only if the required credential conditions are eventually satisfied.
- The positive check-in-gate response is a prerequisite on the shown incoming-entry success path. Failure or uncertainty must remain restricted and enter support/recovery, not grant entry.
- A pre-provisioned credential cannot bypass all-guest eligibility. A real supported gate must exist; otherwise credential enabling must wait. No gating hardware or staff-less procedure is selected here.
- Protected PIN retrieval/delivery is abstracted by notification work. Use authorized channels and do not expose PINs in routine logs, reports or unrestricted evidence.
- The par fragment shows independent control/telemetry and session-clock responsibilities. Neither clock expiry nor emergency exit waits for a hung command or a failed notification.
- Warning and expiry work must check the latest session revision when acting. Only committed, eligible amendments may change the effective access window.
- Distinguish requested commands, provider acceptance, device acknowledgement and observed physical state. Unknown telemetry cannot be shown as OFF, empty, safe or healthy.
- Room readiness requires verified departure, cleaning/reset, the applicable buffer and clearance of relevant safety/access/maintenance restrictions. READY does not erase future reservations.
- Local expiry, outage recovery, power-loss behavior and independent emergency egress require physical evidence; these lifelines do not establish hardware certification.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [02. Bank Deposit, Receipt Review and Approval](02_Sequence_Diagrams_Bank_Deposit_Receipt_Review_and_Approval.md)
- [04. Identity Evidence and Eligibility Review](04_Sequence_Diagrams_Identity_Evidence_and_Eligibility_Review.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [09. Room Maintenance and Controlled Closure](09_Sequence_Diagrams_Room_Maintenance_and_Controlled_Closure.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
