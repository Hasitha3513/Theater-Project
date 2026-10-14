# CineNest Knowledge Base

Task CN-KB-FEATURES-TECH-001 · Updated 2026-10-14. Canonical location: this knowledge/ directory in the Git repository whose origin is https://github.com/Hasitha3513/Theater-Project.git. The outer workspace copy is historical and not maintained/synchronized by this task.

Baseline: HEAD 16710dc, existing design-token and chart-spec versions 1.0.0, no overall KB manifest/version/changelog. The first explicit KB manifest establishes **1.0.0** for the consolidated knowledge base; chart contracts advance from observed 1.0.0 to **1.1.0** for this additive/clarifying update. Design tokens remain **1.0.0**, byte-for-byte unchanged. No invented prior KB v1.1.0/archive and no downgrade. A no-op rerun must not increment these versions.

Hybrid visual direction is APPROVED. Detailed business behavior is DOCUMENTED_DRAFT; preferred technologies RECOMMENDED; provider/hardware/palette migration choices OPEN_DECISION. Feature implementation NOT_VERIFIED: no application code/manifests exist. Source PDFs remain missing; user notes are preserved, not represented as PDF quotations.

| Canonical artifact | Purpose |
|---|---|
| [Product/features](product/CINENEST-PRODUCT-SCOPE-AND-FEATURES.md) | Stable feature catalog and acceptance |
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
| [Charts/metrics](CINENEST-CHARTS-AND-METRICS.md) | Nine specifications and canonical metrics |
| [ADR-001](CINENEST-DESIGN-DECISION-ADR-001.md) | Historical approved hybrid direction |
| [ADR-002](CINENEST-DESIGN-DECISION-ADR-002.md) | Rule/metric clarification and palette conflict |
| [Reference traceability](CINENEST-REFERENCE-TRACEABILITY.md) | Preserved original evidence/history |
| [UX checklist](CINENEST-UX-ACCEPTANCE-CHECKLIST.md) | Documentation/future implementation acceptance |
| [Tokens](tokens/cinenest-design-tokens.json) / [theme](styles/cinenest-theme.css) | Unchanged proposed visual values |
| [Chart contracts](tokens/cinenest-chart-specs.json) | Proposed data/metric specs v1.1.0 |
| [Manifest](manifest.json) / [changelog](CHANGELOG.md) | Actual inventory and version history |
| [Validation report](CINENEST-VALIDATION-REPORT.md) | Actual checks/limits and publication snapshot |
| [Validator](validate_design_kb.py) | Dependency-free KB validation |

Run `python3 knowledge/validate_design_kb.py` from the Git root (repository/ in this workspace). It checks local files/links/JSON/tokens/images/catalog/rules/metrics/manifest; it does not implement or test services/hardware. Draft JSON is specification data, not runtime configuration. Future design-token change: approved ADR → token update → `--generate-css` → update docs/manifest checksums → validate. Do not import proposed technologies/policies as approved implementation.
