# CineNest Activity Diagram 01: Customer Booking and Online Payment

**Diagram:** 01 of 10<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed workflow; implementation and execution tests not verified.<br>
**Artifact:** Standalone requested Markdown export; no project/repository files modified.

## Purpose

Follow a customer from room discovery to a versioned quote, controlled reservation hold, verified online payment, and guarded booking confirmation. Bank-payment requests continue in Activity 02.

Exported from one of the eight detailed interactive activity diagrams. The overview poster is a visual summary, not the detailed workflow contract.

## Participants and responsibilities

| Participant | Responsibility |
| --- | --- |
| Customer / Primary Booker | Selects a package and session, accepts terms, completes eligibility requirements, and uses an available payment method. |
| CineNest backend | Owns the quote, inventory hold, payment recording, booking guards, and durable follow-up work. |
| Payment gateway | Provides hosted checkout and authenticated server-side payment outcomes. |

## Editable activity diagram

```mermaid
---
config:
  theme: base
  themeVariables:
    fontFamily: Arial, sans-serif
    fontSize: 16px
    primaryColor: '#F0EEFF'
    primaryTextColor: '#111827'
    primaryBorderColor: '#5B4BFF'
    lineColor: '#64748B'
  flowchart:
    curve: linear
---
flowchart TB
    Start((Start)) --> Browse("Customer: browse rooms and packages<br/>Sign in before requesting a booking")
    Browse --> Select("Select room, package, start time,<br/>duration, guests and extras")
    Select --> Hold{"Request valid and<br/>room inventory available?"}
    Hold -->|No| Alternate("Explain conflict or invalid selection")
    Alternate --> Select
    Hold -->|Yes| Quote("Backend: create versioned quote<br/>and durable, bounded reservation hold")
    Quote --> Consent{"Customer accepts<br/>quote and applicable terms?"}
    Consent -->|No| Release("Release eligible hold<br/>No payment requested")
    Release --> NoBooking([End: no confirmed booking])
    Consent -->|Yes| Verify[["Complete required pre-confirmation<br/>eligibility checks: Activity 04"]]
    Verify --> Eligible{"Required eligibility<br/>checks satisfied?"}
    Eligible -->|No or pending| PendingID("Show rejection, correction or review status<br/>Hold survives only within its valid deadline")
    PendingID --> Pause([Pause: eligibility unresolved])
    Eligible -->|Yes| Method{"Payment method?"}
    Method -->|Bank deposit / transfer| Bank[["Continue with Activity 02"]]
    Method -->|Online gateway| Hosted("Create payment attempt for accepted quote<br/>Customer completes hosted checkout")
    Hosted --> Event("Receive server-side provider result")
    Event --> Valid{"Authenticated, correlated<br/>and not already processed?"}
    Valid -->|Invalid| Invalid("Reject event; preserve business state<br/>Record safe audit information")
    Invalid --> Ignored([End: event ignored])
    Valid -->|Already processed| Duplicate("Return recorded outcome<br/>Resume incomplete durable work if needed")
    Duplicate --> Recorded([End: no duplicate processing])
    Valid -->|New valid result| Outcome{"Payment outcome?"}
    Outcome -->|Pending or unknown| Await("Keep pending and reconcile provider state<br/>Do not request duplicate payment")
    Await -.->|Later authenticated update| Event
    Outcome -->|Confirmed failure| Failed("Record payment failure<br/>Release eligible hold and offer a new attempt")
    Failed --> NoBooking
    Outcome -->|Funds confirmed| Funds("Record verified payment once<br/>Preserve actual amount and currency")
    Funds --> Amount{"Matches accepted quote<br/>and has unallocated funds?"}
    Amount -->|No| Resolve("Retain funds; resolve amount,<br/>currency or duplicate-payment discrepancy")
    Amount -->|Yes| Confirm{"Transactional confirmation:<br/>allocation, inventory and eligibility valid?"}
    Confirm -->|No| Resolve
    Resolve --> NeedsResolution([End: funds recorded; resolution required])
    Confirm -->|Yes| Commit("Commit allocation and confirmed booking<br/>Preserve purchased package and policy snapshot")
    Commit --> Queue("Durably queue confirmation notice<br/>and access provisioning")
    Queue --> Done([End: booking confirmed; access tracked separately])
    Note["All protected steps use Activity 05 authorization.<br/>Browser redirects do not verify payment.<br/>Catalog edits do not reprice an accepted valid quote.<br/>A late successful payment never overrides another reservation."]
    Note -.- Confirm

    classDef terminal fill:#0B1526,color:#FFFFFF,stroke:#0B1526;
    classDef actor fill:#DBEAFE,color:#111827,stroke:#2878FF;
    classDef decision fill:#FFF7E6,color:#111827,stroke:#B45309;
    classDef success fill:#DCFCE7,color:#14532D,stroke:#15803D;
    classDef exception fill:#FEE2E2,color:#7F1D1D,stroke:#B91C1C;
    classDef pending fill:#FFF7E6,color:#111827,stroke:#B45309;
    classDef note fill:#F8FAFC,color:#475569,stroke:#94A3B8,stroke-dasharray:4 3;
    class Start,NoBooking,Pause,Ignored,Recorded,NeedsResolution terminal;
    class Browse,Select,Hosted actor;
    class Hold,Consent,Eligible,Method,Valid,Outcome,Amount,Confirm decision;
    class PendingID,Await pending;
    class Commit,Done success;
    class Invalid,Failed,Resolve exception;
    class Note note;
```

## Diagram notation

This is an **activity-style control-flow diagram expressed with Mermaid flowchart notation**, not a strict UML/XMI model. It preserves the discussed workflow decisions without claiming executable behavior.

- The start circle begins this activity; nodes labeled **End** or **Pause** identify outcomes. A pause does not mean the booking, money, or incident has disappeared.
- Rounded actions describe work. Diamonds are decisions with labeled guards/outcomes. Double-border boxes reference another activity.
- Solid arrows show control flow. Dashed arrows with labels represent later events or retries; unlabelled dashed note connectors do not control execution.
- Blue actions identify people or external interactions where labeled; pale violet is ordinary system work, amber is a decision or pending state, green is a verified successful outcome, and red is an exception. Text remains authoritative over color.
- Responsibility is written in node labels and the participant table. These are not fixed swimlane columns. Sequential drawing does not make remote operations one atomic transaction, and no parallel fork/join is implied unless explicitly shown.

Render the fenced `mermaid` block in a Mermaid-capable Markdown viewer. The surrounding text remains readable without rendering. The viewer must support Mermaid frontmatter configuration; remove only the `---` configuration section inside the block when using a renderer that does not support it.

## Main activity outline

1. Browse published rooms and packages; establish an authenticated session before protected actions.
2. Validate the selected session and atomically create a bounded inventory hold with the authoritative quote.
3. Obtain required consent and pre-confirmation eligibility outcomes. Pending verification must not extend a hold indefinitely.
4. Choose an eligible payment method. Bank transfer continues in Activity 02; the online route uses the accepted quote.
5. Authenticate and correlate the server-side payment outcome, and process it idempotently.
6. Retain actual funds, then confirm only when payment allocation, inventory, and eligibility pass at commit.
7. Queue notifications and access provisioning durably; expose their outcomes independently.

## Guards and interpretation rules

- The ordering shown is a proposed activity view; approved eligibility/hold timing in the canonical documents remains authoritative.
- Customer identity, receipt, and booking access are scoped server-side. Public browsing does not grant protected privileges.
- A duplicate event with incomplete downstream work must resume that work safely, not fabricate a completed booking.
- Timeouts and missing callbacks require reconciliation. A payment page closing is not proof of failure.
- Prices, duration, inclusions, and policy snapshots come from the accepted quote; later catalog edits do not silently alter them.
- Database-enforced inventory includes the applicable cleaning buffer and maintenance blocks. Redis is not an assumed prerequisite.
- Remote payment and lock actions are outside a local database transaction. Actual external funds remain traceable if confirmation fails.

## Alternative outcomes and recovery

| Condition | Intended handling |
| --- | --- |
| Unavailable room / invalid selection | Offer eligible alternatives without creating a confirmed reservation. |
| Eligibility pending / rejected | Display the actual outcome and enforce the current hold deadline. |
| Pending / unknown provider result | Reconcile the original attempt; do not invite duplicate payment automatically. |
| Late funds or amount mismatch | Retain the payment and enter resolution without displacing another reservation. |
| Notification or access failure | Preserve the confirmed financial/booking state and show the failed or pending external work. |

## Review before implementation

Treat this diagram as an analysis/design view. Trace every guard and recovery route to the corresponding canonical requirement and planned acceptance check. Test authorization, state changes, duplicate processing, concurrency, and recovery as applicable. For physical/device outcomes, distinguish a simulation from actual hardware evidence. No application, payment-provider, or hardware acceptance test is claimed by this export.

## Related activity diagrams

- [02. Bank Deposit, Receipt Review and Approval](02_ActivityDiagram_Bank_Deposit_Receipt_Review_and_Approval.md)
- [04. Identity Evidence and Eligibility Review](04_ActivityDiagram_Identity_Evidence_and_Eligibility_Review.md)
- [05. Scoped RBAC and ABAC Authorization](05_ActivityDiagram_Scoped_RBAC_and_ABAC_Authorization.md)
- [06. Booking Amendment and Session Extension](06_ActivityDiagram_Booking_Amendment_and_Session_Extension.md)
- [07. Booking Cancellation and Refund Execution](07_ActivityDiagram_Booking_Cancellation_and_Refund_Execution.md)
- [08. Cinema Entry, IoT Control and Room Reset](08_ActivityDiagram_Cinema_Entry_IoT_Control_and_Room_Reset.md)

## Source and authority

- Use-case export 01: Customer Booking and Account
- Use-case export 03: Payments, Bank Review and Refunds
- Detailed interactive activity 1 of 8

These files are standalone exports, not updates to the repository. The current four canonical knowledge documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and their authoritative registries continue to govern requirements, approvals, and implementation state.

Diagram labels, actor bundles, pending policies, timer values, provider/hardware choices, and retry behavior retain their actual canonical status. This export neither approves unresolved policies nor certifies application code, external integrations, bank reconciliation, device behavior, or physical safety. All protected operations use the applicable scoped authorization; business invariants remain separate.
