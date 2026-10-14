# CineNest Component Diagram 02: Payments Module - Hexagonal Ports and Adapters

**View:** 02 of 02<br>
**Export date:** 2026-10-14<br>
**Status:** Proposed module decomposition; actual source packages, port names, adapters and runtime verification are not established by this export.<br>
**Artifact:** Owner-requested standalone Markdown export. No project repository or canonical knowledge document is modified.

## Purpose and boundary

Expand the Payments component from the corrected platform diagram. Show how inbound requests reach the application through a provided interface, how application services apply domain rules through required ports, and how technology-specific adapters implement those ports.

The Payments module is **inside the CineNest modular monolith**, not a new microservice. Other modules are external to this module's boundary but can remain inside the same deployed backend. Database access is owner-specific, and external providers remain outside the local transaction boundary.

## Editable component diagram

```mermaid
---
config:
  theme: base
  themeVariables:
    fontFamily: Arial, sans-serif
    fontSize: 15px
    primaryColor: '#F0EEFF'
    primaryTextColor: '#111827'
    primaryBorderColor: '#5B4BFF'
    lineColor: '#64748B'
    clusterBkg: '#F8FAFC'
    clusterBorder: '#94A3B8'
  flowchart:
    curve: linear
    nodeSpacing: 30
    rankSpacing: 75
    htmlLabels: true
---
flowchart LR
    subgraph PAYMENTS["Payments module | One logical component inside the modular-monolith backend"]
        direction LR
        subgraph INBOUND["Driving adapters | Translate inbound requests"]
            direction TB
            HTTP["«component» Payment HTTP Adapter<br/>Customer payment requests<br/>Authorized bank-review and refund actions"]
            CALLBACK["«component» Gateway Callback Adapter<br/>Authenticate provider evidence<br/>Correlate and normalize outcomes"]
            JOB["«component» Payment Work Handler<br/>Scoped reconciliation and recovery<br/>Committed work, not a universal administrator"]
        end

        subgraph CORE["Payments core | Owns application interfaces and required port contracts"]
            direction TB
            API(["«provided interface» PaymentUseCases<br/>Submit claim, record verified credit<br/>Allocate funds, request refund and query status"])
            APP["«component» Payment Application Services<br/>Coordinate authorization and domain operations<br/>Define transaction boundaries and recovery intent"]
            DOMAIN["«component» Payment Domain<br/>Payment requests and verified credits<br/>Allocation balances and refund obligations<br/>Duplicate-safe monetary invariants"]
            AUTH(["«required interface» AuthorizationPort<br/>Current principal, scope and mandatory restrictions"])
            REPO(["«required interface» PaymentRepositoryPort<br/>Versioned records and guarded updates"])
            EVID(["«required interface» ReceiptStorePort<br/>Restricted evidence and submission versions"])
            GWP(["«required interface» PaymentGatewayPort<br/>Checkout, query outcome and supported refunds"])
            WORK(["«required interface» DurableWorkPort<br/>Committed events, audit and recovery intent"])
            APP -.->|provides / implements| API
            APP -->|uses| DOMAIN
            APP -->|uses| AUTH
            APP -->|uses| REPO
            APP -->|uses| EVID
            APP -->|uses| GWP
            APP -->|uses| WORK
        end

        subgraph OUTBOUND["Driven adapters | Implement core-owned contracts"]
            direction TB
            AA["«component» Authorization Adapter<br/>Uses published identity / policy API"]
            PA["«component» PostgreSQL Payment Adapter<br/>Payment-owned records and concurrency checks"]
            SA["«component» Private Receipt Adapter<br/>Restricted object access and safe evidence handling"]
            GA["«component» Gateway HTTP / SDK Adapter<br/>Provider-specific protocol and result mapping"]
            WA["«component» Transactional Work Adapter<br/>Persists follow-up intent with the business commit"]
        end
        HTTP -->|calls| API
        CALLBACK -->|calls| API
        JOB -->|calls| API
        AA -.->|implements| AUTH
        PA -.->|implements| REPO
        SA -.->|implements| EVID
        GA -.->|implements| GWP
        WA -.->|implements| WORK
    end

    ID["Identity and Authorization Module<br/>Published scoped-policy API"]
    DB[("PostgreSQL<br/>Payment-owned records and assigned work tables")]
    STORE[("Private Object Storage<br/>Restricted payment receipt evidence")]
    PROVIDER["Contracted Payment Gateway<br/>External system"]
    AA -->|in-process public interface| ID
    PA -->|SQL| DB
    WA -->|transactional persistence| DB
    SA -->|restricted object operations| STORE
    GA -->|provider protocol| PROVIDER
    PROVIDER -->|authenticated callback input| CALLBACK

    TEST["«test adapter» Controlled Gateway Simulator<br/>Isolated test profiles only<br/>Not proof of actual funds or live-provider support"]
    TEST -.->|implements same port in tests| GWP

    NOTE["DEPENDENCY RULE<br/>Driving adapters call the core-owned use-case interface.<br/>Application services provide that interface and use required ports.<br/>Driven adapters implement required ports; concrete adapters are injected at composition time.<br/>Business rules do not import HTTP controllers, provider SDKs or concrete storage adapters."]
    NOTE -.- DOMAIN

    classDef inbound fill:#DBEAFE,color:#0B1526,stroke:#2878FF,stroke-width:1.5px;
    classDef component fill:#F0EEFF,color:#111827,stroke:#5B4BFF,stroke-width:1.5px;
    classDef domain fill:#0B1526,color:#FFFFFF,stroke:#5B4BFF,stroke-width:2px;
    classDef port fill:#FFFFFF,color:#4C1D95,stroke:#5B4BFF,stroke-width:2px;
    classDef adapter fill:#FEF3C7,color:#78350F,stroke:#B45309;
    classDef external fill:#F1F5F9,color:#334155,stroke:#64748B;
    classDef testing fill:#DCFCE7,color:#14532D,stroke:#15803D,stroke-dasharray:4 3;
    classDef note fill:#F8FAFC,color:#475569,stroke:#94A3B8,stroke-dasharray:4 3;
    class HTTP,CALLBACK,JOB inbound;
    class APP component;
    class DOMAIN domain;
    class API,AUTH,REPO,EVID,GWP,WORK port;
    class AA,PA,SA,GA,WA adapter;
    class ID,DB,STORE,PROVIDER external;
    class TEST testing;
    class NOTE note;
```

## Reading the dependency direction

This is a **UML-style component/ports-and-adapters view expressed in Mermaid flowchart notation**, not a strict UML/XMI artifact or a runtime sequence. Interface names are proposed descriptive contracts, not a requirement for one Java interface per method.

| Relationship | Meaning |
| --- | --- |
| Driving adapter → `PaymentUseCases` | The entry adapter depends on/calls the published application interface. |
| Application Services ⇢ `PaymentUseCases`, labeled `provides / implements` | Application services supply the implementation of the provided interface. |
| Application Services → required port | The application depends on a core-owned abstraction, not a concrete SDK, database or file-store adapter. |
| Driven adapter ⇢ required port, labeled `implements` | A technology-specific adapter implements the port contract. The source dependency points toward that contract. |
| Application Services → Domain | Application orchestration invokes business rules and entities/value objects. |
| Adapter → external system or database | The adapter uses the actual provider protocol or infrastructure. |
| Gateway → Callback Adapter | A distinct inbound integration; authenticate evidence before admitting it to the use-case boundary. |
| Test Adapter ⇢ Gateway Port | A controlled test implementation can replace the provider adapter in isolated tests. |
| Unlabeled dashed note connector | An explanatory rule only, not a dependency. |

Concrete adapters are wired at the composition/configuration boundary. Runtime calls through an interface can execute its configured adapter without making the core import that concrete adapter. Dashed arrows here mean implementation/provision, **not** asynchronous delivery. For event routing see Diagram 01; for time order use the sequence-diagram exports.

## Components and responsibilities

| Component | Intended responsibility | Must not do |
| --- | --- | --- |
| Payment HTTP Adapter | Translate customer/admin requests; establish authenticated request context; call the owning payment use case | Treat user-supplied roles, amount, venue or receipt as trusted approval evidence; implement payment rules solely in a controller |
| Gateway Callback Adapter | Authenticate, correlate and normalize supported provider messages | Accept a browser success redirect as payment verification; invent bank callbacks for manual approvals |
| Payment Work Handler | Resume committed payment reconciliation and recovery under a scoped service identity | Act as a universal admin, bypass monetary rules or silently duplicate prior effects |
| Payment Application Services | Coordinate authorization, domain operations, local transactions, idempotency and durable recovery intent | Assume an external provider or object write is included atomically in a database transaction |
| Payment Domain | Model requests, verified credits, allocation balances, refund obligations and monetary invariants | Import HTTP controllers, gateway SDK types, concrete repositories or object-storage clients |
| Authorization Adapter | Obtain applicable policy/grant decisions through a published module interface | Inject another module's internal identity repository or combine unrelated permission/scope grants |
| PostgreSQL Payment Adapter | Map core persistence contracts to payment-owned records and enforce supported concurrency checks | Modify Booking tables directly or offer unrestricted access to every operational table |
| Private Receipt Adapter | Store/read restricted evidence and versioned receipt references | Make evidence publicly accessible or treat upload success as bank credit |
| Gateway HTTP / SDK Adapter | Translate checkout/status/refund contracts to a selected provider and normalize results | Hide pending/unknown outcomes behind a generic success value |
| Transactional Work Adapter | Persist assigned publication/audit/recovery intent within the relevant local commit | Claim delivery is complete merely because work was enqueued |
| Controlled Gateway Simulator | Provide deterministic test results and failure cases through the same port | Be enabled as a production fallback or be used as evidence that real funds were received |

## Provided and required interface contracts

| Contract | Kind | Conceptual capability |
| --- | --- | --- |
| `PaymentUseCases` | Provided interface | Submit a bank claim, ingest an authenticated gateway result, record bank-verified funds, inspect allocation, apply a guarded allocation, request a refund and query permitted status |
| `AuthorizationPort` | Required interface | Evaluate principal eligibility, matching scoped grants, mandatory restrictions, approval evidence and relevant freshness |
| `PaymentRepositoryPort` | Required interface | Read/write owned requests, verified credits, allocations and refund records with revisions and transaction-aware guards |
| `ReceiptStorePort` | Required interface | Store a safe submission version and retrieve evidence only under explicit request/role/purpose scope |
| `PaymentGatewayPort` | Required interface | Initiate hosted checkout, query an existing payment/refund attempt, and request a supported refund with stable correlation/idempotency references |
| `DurableWorkPort` | Required interface | Record committed follow-up intent for reconciliation, audit, booking coordination and notifications |

Split contracts when the actual responsibilities require it; do not introduce a generic catch-all gateway, repository or policy engine. The named ports summarize meaningful boundaries rather than mandate boilerplate classes for every function. Final package locations and exact framework integration must follow the approved implementation plan.

## Online and bank-payment entry paths

**Online gateway:** A customer request enters through the HTTP adapter. The application records a payment attempt and invokes the selected gateway through `PaymentGatewayPort`. Authenticated callback evidence enters through the callback adapter. The application records actual verified status idempotently and queues any committed follow-up work. A timeout or missing callback produces a reconciliation question, not proof of failure.

**Bank deposit or transfer:** The customer pays outside CineNest and uploads evidence through the HTTP adapter. An authorized reviewer independently matches the receiving-account credit, then invokes the permitted verification use case. A clear image, customer-entered reference or OCR result cannot prove funds arrived. There is deliberately **no bank API adapter** in this module view.

Receipt submission, bank matching, verified credit, allocation, booking confirmation, credential readiness and refund completion are separate facts/lifecycles. No entry path collapses them into a universal `approved` state.

## Coordination with Booking and other modules

Other modules consume Payments through its published application/query contracts. They never inject its concrete repository or modify its tables. Payments can publish a committed funds-verification fact; a Booking-owned handler can then coordinate the guarded reservation and allocation use cases.

The local transaction coordinating allocation and reservation must preserve each module's ownership. Its orchestration and transaction propagation need an explicit approved design and real-database concurrency tests; this diagram is not evidence that such atomicity has been implemented.

The verified incoming funds record must survive a failed reservation attempt. When inventory, quote, eligibility or authorization checks fail, retain funds and route to reconciliation, an agreed alternative or an authorized refund. For events that cross module boundaries, retain stable correlation, event version and owner scope without raw receipts, bank credentials or access PINs.

Notifications, booking confirmation and access provisioning are not implemented by a payment-provider adapter. They are independently owned use cases and durable follow-up handlers as shown in the platform view.

## Transaction, persistence and failure rules

- Persist stable payment/claim/attempt references before external effects where required; retries and callbacks must resolve to their existing records.
- Keep verified receipt-of-funds evidence separate from a transaction that may fail while allocating funds to a booking. Do not roll back the fact that money arrived merely because the room became unavailable.
- Enforce sufficient available balance, unique bank/provider transaction identity, permitted currency and exactly the amount due at allocation time. A preliminary read alone does not provide concurrency protection.
- Preserve underpayment and excess funds, including real double payments through different methods. Excess is not automatically booking revenue.
- A local database transaction cannot make the provider, object store, bank portal or lock participate atomically. Perform remote calls outside an assumed long-running business transaction and persist their actual outcomes.
- Object storage and database persistence need defined retry, cleanup and recovery behavior for partial results.
- Record refund intent/approval, initiated repayment, unknown status and verified completed repayment separately. Reconcile the original attempt before repeating a transfer after a timeout.
- Retryable jobs need scoped identities, current relevant revisions and bounded policies. “Job sent” is not evidence of business completion.
- A gateway or local bank interface may have different capabilities. Contract implementations must expose unsupported or uncertain outcomes rather than fabricate equivalence.

## Scoped RBAC + ABAC and privacy

A protected operation needs a current eligible principal and an applicable grant containing both the requested permission and resource scope. Mandatory denials, relevant ownership, legitimate evidence purpose and approved financial constraints still apply. A finance grant at Venue A plus an operations grant at Venue B is not finance authority at both venues.

Application services coordinate the permission check and their domain/persistence guards at the required consistency boundary. They must reject invalid allocations or refunds even for an otherwise permitted administrator. The domain remains responsible for money and lifecycle invariants rather than relying on a generic authorization engine to make invalid business state acceptable.

Review evidence through restricted, safe previews. Audit sensitive access and decisions without putting raw receipts, PINs, session tokens or banking secrets in ordinary logs. Exact limits, independent-review rules and revocation timing remain governed by their actual approved/proposed/open status in the canonical records.

## Test seams and review targets

These are proposed verification targets, not test results:

| Level | Suggested evidence |
| --- | --- |
| Domain unit tests | Allocation balance, under/overpayment, refund entitlement and valid state transitions without provider/database access |
| Application tests | Permission denial, idempotency, payment claim versus verified credit, failure handling and durable follow-up intent using controlled adapters |
| Real PostgreSQL tests | Simultaneous approvals/allocations, unique transaction identity, rollback behavior and durability of earlier verified funds |
| Provider contract tests | Authenticated callbacks, correlation, pending/unknown outcomes and safe refund reconciliation in a supported test environment |
| Storage tests | Unauthorized reads, unsafe file handling, versioned evidence, and object/DB partial-failure recovery |
| Architecture checks | Core does not import concrete web/SDK/storage adapters; other modules use public contracts; source dependencies remain acyclic |

No simulator output substitutes for bank-credit verification, production settlement, actual notification delivery or physical device acceptance.

## Related view, rendering and authority

[01. Platform Components and Integration Boundaries](01_Component%20Architecture%20Diagram_Platform_Components_and_Integration_Boundaries.md) places Payments alongside Booking, Packages, Verification, Access, Device Operations and supporting components.

Render the `mermaid` block in a compatible Markdown viewer. When a viewer does not support YAML frontmatter, remove only the initial `---` configuration section **inside that block**, retaining `flowchart LR` and the diagram. Text, tables and the companion link remain readable without Mermaid. No external image or font files are required.

This export follows the detailed hexagonal component view and the Payments inset in `CineNest_Component_Diagram_Corrected.png`, with explicit callback direction and interface semantics. It does not revive erroneous Kafka, TimescaleDB, active Redis, native-mobile, generic shared-repository or unrelated frontend labels from the earlier generated posters.

The repository's four canonical documents (`PROJECT.md`, `ARCHITECTURE.md`, `DESIGN.md`, `PROGRESS.md`) and authoritative registries retain decision and implementation authority. These requested exports neither create repository knowledge documents nor approve a technology stack, financial policy, integration provider or physical capability.
