# CineNest Sequence Diagram 09: Room Maintenance and Controlled Closure

**Diagram:** 09 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show conflict-aware planned maintenance, durable room-inventory blocking, qualified work, and authorized reopening that preserves other bookings and restrictions.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Venue / Operations Staff and Admin Panel | Request and manage permitted maintenance windows and current room scope. |
| Venues and Rooms | Owns maintenance requests, work outcomes and operational readiness rules. |
| Booking Inventory | Owns blocking intervals and reservation conflicts, including protected holds and cleaning intervals. |
| PostgreSQL | Enforces shared transactional protection without transferring table ownership between modules. |
| Module-owned Workers | Execute committed scheduled work and staff/customer notification intent. |
| Qualified Maintenance Staff | Perform the approved work and supply actual inspection and reset evidence. |

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
    actor O as Venue / Operations Staff
    participant UI as Admin Panel
    participant R as Venues and Rooms
    participant B as Booking Inventory
    participant DB as PostgreSQL
    participant W as Module-owned Workers
    actor T as Qualified Maintenance Staff
    O->>UI: Request maintenance interval, resources and reason
    UI->>R: Submit scoped maintenance request with room revision
    R->>R: Authorize and validate request (Sequence 05)
    break Unauthorized or invalid request
        R-->>UI: Reject; no maintenance block created
    end
    R->>B: Inspect affected holds, bookings, cleaning and blocks
    B->>DB: Query authoritative inventory
    DB-->>B: Current conflicts and revisions
    B-->>R: Conflict summary
    alt Existing reservations or protected holds affected
        R->>DB: Save pending maintenance request and impact summary
        R-->>UI: Present affected bookings and required resolution
        O->>B: Request separately authorized amendments/cancellations (06/07)
        B-->>O: Actual resolution outcome; financial obligations preserved
        O->>R: Resume maintenance request after approved resolution
    else No initial conflict
        R-->>UI: Preview eligible maintenance window
    end
    R->>DB: Begin local transaction; check scope and room revision
    R->>B: Create maintenance inventory block in same transaction
    B->>DB: Recheck all intervals and enforce conflict protection
    alt Conflict remains or concurrent state changed
        R->>DB: Roll back activation
        R-->>UI: Keep request pending; do not erase existing bookings
    else Maintenance can be scheduled
        R->>DB: Commit block, schedule, audit and notification work
        R-->>UI: Scheduled maintenance with exact interval
        W->>DB: Claim committed notices and due work
        W-)T: Notify assigned personnel through configured channel
        Note over W,T: At the approved window, physical readiness is checked before work.
        T->>R: Record actual vacancy and safe-to-work checks
        R->>R: Validate operator scope and required operational evidence
        alt Vacancy or safety unknown
            R->>DB: Retain restriction and record incident/follow-up
            R-->>T: Work cannot proceed; use incident procedure (10)
        else Work is permitted
            T->>T: Perform maintenance and required inspections
            T->>R: Submit findings, completion and cleaning/reset evidence
            R->>DB: Record attributable work outcome
            alt Work incomplete or inspection fails
                R->>B: Retain operational restriction and flag affected future sessions
                B->>DB: Preserve customer records and record resolution needs
                R-->>UI: Not ready; assign follow-up
            else Completion evidence passes
                O->>R: Request authorized reopening or block completion
                R->>R: Check current readiness, timing and other restrictions
                R->>DB: Begin release transaction with current revisions
                R->>B: Release only this eligible maintenance block
                B->>DB: Guard scope and inventory; retain all unrelated intervals
                alt Reopening guards satisfied
                    R->>DB: Commit closed maintenance and readiness state
                    R-->>UI: Ready; availability remains inventory-driven
                else Another restriction or revision conflict
                    R->>DB: Roll back reopening
                    R-->>UI: Still unavailable; explain outstanding condition
                end
            end
        end
    end
    Note over O,T: Planned maintenance never silently cancels purchased bookings. Emergency restrictions use Sequence 10.
    Note over R,DB: A scheduled end time does not certify completion. Early release or interval extension requires separate authorization and conflict checks.
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

- This is the planned-maintenance route. Immediate safety restrictions use the approved incident response rather than waiting for routine rescheduling.
- Booking owns conflict checks for holds, paid bookings, cleaning and maintenance inventory. Room administration calls that API instead of maintaining a second availability calendar.
- Previewing a conflict-free window does not reserve it. Repeat revision and interval checks in the transaction that commits the maintenance block.
- Resolve purchased-booking conflicts through authorized amendment/cancellation and financial workflows, preserving necessary customer agreement and all received money.
- A scheduled maintenance block and an unresolved safety/readiness restriction have different purposes. Do not fabricate a non-overlap violation by force-inserting conflicting inventory or deleting existing reservations.
- Do not start maintenance until actual vacancy and safe-to-work evidence are established. Elapsed time or a task checkbox is not physical assurance.
- Incomplete work keeps the relevant restriction visible and triggers follow-up for affected future bookings; it does not mark a room available on schedule alone.
- Release only the authorized block after current readiness and timing checks. Early reopening or extending a window requires its own authorization and inventory validation.
- Notification failures do not undo an existing block. A ready room can still be unavailable because another valid booking or restriction exists.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [03. Package Editing, Publication and Deactivation](03_Sequence_Diagrams_Package_Editing_Publication_and_Deactivation.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [07. Cancellation and Refund Execution](07_Sequence_Diagrams_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_Sequence_Diagrams_Cinema_Entry_IoT_Control_and_Room_Reset.md)
- [10. Operational Incident and Recovery](10_Sequence_Diagrams_Operational_Incident_and_Recovery.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
