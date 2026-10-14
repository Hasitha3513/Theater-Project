# CineNest Sequence Diagram 03: Package Editing, Publication and Deactivation

**Diagram:** 03 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed interaction design; implementation not verified.<br>
**Artifact:** Owner-requested standalone Markdown export; no project or repository files modified.

## Purpose

Show administrator edits to package content, duration and price, guarded draft/publication operations, scheduled activation, and deactivation that preserves prior purchases.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Venue Administrator / Admin Panel | Edit and preview package configuration within applicable permissions and affected-venue scope. |
| Packages and Pricing | Owns draft revisions, package validation, publication versions and customer-facing offer rules. |
| Venues and Rooms | Supplies authoritative venue constraints, capacity and operating-policy revisions. |
| PostgreSQL | Persists package records, revision guards, publication windows, audit and activation work. |
| Package Activation Worker | Executes already-authorized future activation under the applicable service scope and policy. |

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
    actor A as Venue Administrator
    participant UI as Admin Panel
    participant P as Packages and Pricing
    participant V as Venues and Rooms
    participant DB as PostgreSQL
    participant W as Package Activation Worker
    A->>UI: Open package and select intended venues
    UI->>P: Load package under scoped read permission
    P->>DB: Read current version and editable draft
    P-->>UI: Current revision and permitted actions
    A->>UI: Edit description, duration, price and inclusions
    UI->>P: Validate proposed configuration and expected revision
    P->>P: Enforce action and affected-venue authorization (05)
    P->>V: Read authoritative capacity and operating constraints
    V-->>P: Venue rules and constraint revisions
    P->>P: Check duration, pricing, extras and applicable policy
    alt Invalid fields or missing edit authority
        P-->>UI: Safe denial or field errors; no publication
    else Valid proposal
        P-->>UI: Customer-facing preview and old/new values
        A->>UI: Save draft, publish now or schedule publication
        UI->>P: Submit chosen action with expected revision
        alt Save draft
            P->>DB: Optimistic revision check and draft save
            DB-->>P: Saved or stale-edit conflict
            P-->>UI: Draft outcome; live offer unchanged
        else Publish or schedule
            P->>P: Recheck publish grant, approvals and effective dates
            P->>DB: Begin transaction; guard revisions and version-window overlap
            alt Publication denied, stale or inconsistent
                P->>DB: Roll back publication attempt
                P-->>UI: Explain conflict; retain draft for reconciliation
            else Publication guards pass
                P->>DB: Commit immutable version, audit and activation work
                P-->>UI: Published or scheduled, with effective time
                Note over P,W: Immediate versions activate at commit. Future versions use committed scheduled work.
                opt Future activation is due
                    W->>DB: Claim due activation intent
                    W->>P: Activate under scoped service authority
                    P->>V: Revalidate applicable current venue constraints
                    V-->>P: Current constraint outcome
                    P->>DB: Atomically recheck version, schedule and competing changes
                    alt Still eligible
                        P->>DB: Activate recorded version; queue view refresh
                    else No longer eligible
                        P->>DB: Keep inactive; record conflict and owner notification
                    end
                end
            end
        end
    end
    opt Separate later deactivation request
        A->>UI: Deactivate package for new sales
        UI->>P: Deactivate with scope, revision and effective time
        P->>P: Authorize and assess impact on issued quotes
        P->>DB: Guard revision; commit effective deactivation or return conflict
        P-->>UI: Actual deactivation/scheduling outcome
    end
    Note over A,W: Purchased booking snapshots and PIN windows remain unchanged. No booking is amended through catalog editing.
    Note over P,V: Minimum-duration policy and physical capacity are independent constraints, not editable package labels.
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

- Editable package information includes duration, prices and inclusions without a deployment for every commercial adjustment.
- A package cannot exceed room capacity or bypass the separately owned minimum-duration and operating policies. Included extras must not be billed again as optional add-ons.
- Draft-save, publication, activation and deactivation have their own revision and authorization checks. A successful preview is not permanent permission to publish.
- Publication must cover all affected venues under applicable grants; a permission at one venue cannot borrow another role's scope.
- Sales effective dates and permitted cinema-session dates are different concepts. Publish an unambiguous version window and show scheduled versus active honestly.
- Scheduled work uses scoped authority and the explicit policy for already-approved work. A later invalid activation is blocked and surfaced for resolution.
- Deactivation stops new sales according to its effective rule, not existing purchased bookings. Booking amendments use Sequence 06.
- Changes to shared configurations and visibility refreshes must not silently rewrite accepted quote or purchased booking snapshots.

## Implementation and review notes

Use this interaction view alongside its corresponding activity and use-case descriptions. Trace each branch to the owning module's acceptance criteria. Verify request ownership, scoped authorization, state/revision conflicts, duplicate handling and failure recovery as applicable.

Any depicted local transaction spanning module calls is a proposed contract that requires explicit transaction propagation and real-database tests. It does not merge the modules' data ownership. External gateway, bank, object-storage, OCR, messaging or hardware operations are not made atomic by a diagram and must not run inside an assumed all-encompassing database transaction.

Worker lifelines abstract separate module-owned jobs and delivery channels. Durable follow-up intent must survive process failure; its consumers must handle retries and stale revisions safely. Tests with simulated providers/devices are not physical or production acceptance evidence.

## Related sequence diagrams

- [01. Customer Booking and Online Payment](01_Sequence_Diagrams_Customer_Booking_and_Online_Payment.md)
- [05. Scoped RBAC and ABAC Authorization](05_Sequence_Diagrams_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_Sequence_Diagrams_Booking_Amendment_and_Session_Extension.md)
- [09. Room Maintenance and Controlled Closure](09_Sequence_Diagrams_Room_Maintenance_and_Controlled_Closure.md)

## Source and authority

This standalone export follows the corresponding **detailed interactive CineNest sequence diagram** in the conversation, informed by the previously supplied mind map, use-case diagrams, activity diagrams, and subsequent package, bank-payment and authorization clarifications. The generated overview poster combines/reorders some scenes; the detailed sequence numbering is retained consistently across these ten files.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries continue to govern requirements, approval status and implementation. This file does not replace or update them, select providers or policy values, authorize implementation, or certify application, financial, integration or physical safety behavior.

**Status:** Proposed interaction design; implementation and runtime acceptance are not verified by this export. Role bundles, deadlines, refund limits, revision/revocation policy, automated decisioning and hardware capabilities retain their actual approved/proposed/open status.
