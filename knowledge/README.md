# CineNest Knowledge Base

Task CN-AGENT-SETUP-001 · Updated 2026-10-14. Canonical Git root is repository/ in the desktop parent workspace; KB remains knowledge/. Every CineNest request follows [root instructions](../AGENTS.md), [charter](../docs/agents/CINENEST-PROJECT-EXECUTION-AGENT.md), [status](execution/CINENEST-EXECUTION-STATUS.md) and [handoff](execution/CINENEST-SESSION-HANDOFF.md). Read-only requests remain read-only; no native agent registration/background service implied.

Verified baseline KB **1.2.0** at a48cc6e; resulting KB **1.3.0** adds material execution workflow/coverage records only. Chart **1.2.0**, token **1.0.0**, existing business/design/JSON/CSS/image definitions unchanged except narrow current metadata/count reconciliation. Prior IDs/ADRs/history retained; no no-op increment. Baseline 44 tracked assets source-reviewed, original PDFs remain missing.

Approved product decisions: hybrid direction, PR-PKG-001/CN-A12 editable packages without per-edit release, PR-BANK-001/CN-C10/CN-A13 bank deposit/receipt/notified independent authorized review. Setup workflow explicitly user-approved S10; future code/backlog plan **NOT_APPROVED**. All product implementation NOT_VERIFIED. Proposed technology/deadlines/fees/policies/hardware/cloud remain unresolved; documentation verification never equals product acceptance.

Only current setup was READY/authorized; no application scaffold. Next single action: **authorize CN-DEC-001 bounded initial-stack decision brief**; final choice remains separate approval before implementation. [Coverage/omissions](execution/CINENEST-REQUIREMENTS-TRACEABILITY.md) retains all approved/draft features and launch gates, no invented completion percentage.

| Canonical artifact | Purpose |
|---|---|
| [Execution plan](execution/CINENEST-EXECUTION-PLAN.md) | Canonical proposed bounded change-set backlog and readiness gates |
| [Execution coverage](execution/CINENEST-REQUIREMENTS-TRACEABILITY.md) | Source ledger, requirements/test plans, omissions and evidence classes |
| [Execution status/handoff](execution/CINENEST-EXECUTION-STATUS.md) / [handoff](execution/CINENEST-SESSION-HANDOFF.md) | Mandatory current controls and one safe next action |
| [Setup evidence](execution/evidence/CN-AGENT-SETUP-001.md) | Actual preflight/checks/reviews/publication verification |
| [Product/features](product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md) | Stable feature catalog and acceptance |
| [Bank deposit/manual payment](product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) | Canonical confirmed feature, separate review/funds/booking/access states |
| [Package management](product/CINENEST-PACKAGE-MANAGEMENT.md) | Canonical user-confirmed admin editability and recommended safeguards |
| [Business rules/lifecycles](product/CINENEST-BUSINESS-RULES-AND-LIFECYCLES.md) | Draft defaults, concurrency and seven state machines |
| [Stack/decisions](architecture/CINENEST-TECHNOLOGY-STACK-AND-DECISIONS.md) | Evidence vs recommendation/options |
| [System architecture](architecture/CINENEST-SYSTEM-ARCHITECTURE.md) | Proposed modular-monolith boundaries |
| [Data/API contracts](architecture/CINENEST-DATA-AND-API-CONTRACTS.md) | Conceptual entities and durable integration |
| [IoT/access/failures](architecture/CINENEST-IOT-ACCESS-AND-FAILURE-MODES.md) | Hardware gates and safe behavior |
| [Security/privacy/compliance](security/CINENEST-SECURITY-PRIVACY-COMPLIANCE.md) | Review requirements, no legal certification |
| [Delivery/acceptance](delivery/CINENEST-IMPLEMENTATION-PHASES-AND-ACCEPTANCE.md) | Twelve phases and continuous gates |
| [Open decisions/traceability](decisions/CINENEST-OPEN-DECISIONS-AND-TRACEABILITY.md) | Sources, owners, evidence and blockers |
| [Design system](CINENEST-DESIGN-SYSTEM.md) | Preserved active tokens and shared components |
| [Admin pages](CINENEST-ADMIN-PAGES.md) | Eleven modules |
| [Customer pages](CINENEST-CUSTOMER-BOOKING-PAGES.md) | Twelve customer flows/destinations |
| [Interactions/responsive/a11y](CINENEST-INTERACTIONS-RESPONSIVE-A11Y.md) | Shared interaction and future test gates |
| [Charts/metrics](CINENEST-CHARTS-AND-METRICS.md) | Ten chart/panel specifications including bank clocks and balances |
| [ADR-001](CINENEST-DESIGN-DECISION-ADR-001.md) | Historical approved hybrid direction |
| [ADR-002](CINENEST-DESIGN-DECISION-ADR-002.md) | Rule/metric clarification and palette conflict |
| [ADR-003](CINENEST-DESIGN-DECISION-ADR-003.md) | Approved core package capability; supporting recommendations |
| [ADR-004](CINENEST-DESIGN-DECISION-ADR-004.md) | Bank feature and dual-source financial/confirmation semantics |
| [Reference traceability](CINENEST-REFERENCE-TRACEABILITY.md) | Preserved original evidence/history |
| [UX checklist](CINENEST-UX-ACCEPTANCE-CHECKLIST.md) | Documentation/future implementation acceptance |
| [Tokens](tokens/cinenest-design-tokens.json) / [theme](styles/cinenest-theme.css) | Unchanged proposed visual values |
| [Chart contracts](tokens/cinenest-chart-specs.json) | Proposed data/metric specs v1.2.0 |
| [Manifest](manifest.json) / [changelog](CHANGELOG.md) | Actual inventory and version history |
| [Prior bank-task validation](delivery/CINENEST-BANK-PAYMENT-VALIDATION.md) | Historical bank-task baseline/files/checks |
| [Prior package-task validation](delivery/CINENEST-PACKAGE-CONFIGURATION-VALIDATION.md) | Prior package baseline/checks/file inventory |
| [Prior feature-task report](CINENEST-VALIDATION-REPORT.md) | Historical checks/limits and publication snapshot |
| [Validator](validate_design_kb.py) | Dependency-free KB validation |

Run `python3 knowledge/validate_design_kb.py` from the Git root (repository/ in this workspace). It checks local files/links/JSON/tokens/images/catalog/rules/metrics/manifest; it does not implement or test services/hardware. Draft JSON is specification data, not runtime configuration. Future design-token change: approved ADR → token update → `--generate-css` → update docs/manifest checksums → validate. Do not import proposed technologies/policies as approved implementation.

Read-only execution validation: `python3 knowledge/execution/validate_execution.py` from Git root. Do not bootstrap app code on an unselected stack. Root instructions/charter supplement this KB manifest via explicit externalInstructionFiles; one canonical backlog, no competing KB tree.
