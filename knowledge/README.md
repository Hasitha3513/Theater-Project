# CineNest Knowledge Base

Task CN-KB-BANK-PAYMENT-001 · Updated 2026-10-14. Canonical location: this knowledge/ directory in the Git repository whose origin is https://github.com/Hasitha3513/Theater-Project.git. Outer historical copies/central KBs are not synchronized.

Verified baseline KB **1.1.0** at HEAD 79c4269, chart-spec **1.1.0**, design-token **1.0.0**. Result KB **1.2.0** and chart-spec **1.2.0** for material additive bank payment/reporting; tokens, CSS, original PNGs and existing chart series colors unchanged. Prior versions/ADRs/feature IDs retained; no no-op bump or duplicate sections.

Hybrid direction, PR-PKG-001/CN-A12 admin-editable packages, and **PR-BANK-001/CN-C10/CN-A13 bank deposit/upload/notified authorized independent review** are core APPROVED; bank provenance USER-CONFIRMED. Supporting implementation/review/matching/report designs RECOMMENDED, deadlines/fees/hardware/providers/policies OPEN_DECISION. Application implementation NOT_VERIFIED; source PDFs still missing. Preserve online gateway alongside manual bank path and sold terms; receipt claim ≠ verified funds ≠ confirmed booking ≠ access ready.

| Canonical artifact | Purpose |
|---|---|
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
| [Current bank-task validation](delivery/CINENEST-BANK-PAYMENT-VALIDATION.md) | Current baseline/files/checks and publication snapshot |
| [Prior package-task validation](delivery/CINENEST-PACKAGE-CONFIGURATION-VALIDATION.md) | Prior package baseline/checks/file inventory |
| [Prior feature-task report](CINENEST-VALIDATION-REPORT.md) | Historical checks/limits and publication snapshot |
| [Validator](validate_design_kb.py) | Dependency-free KB validation |

Run `python3 knowledge/validate_design_kb.py` from the Git root (repository/ in this workspace). It checks local files/links/JSON/tokens/images/catalog/rules/metrics/manifest; it does not implement or test services/hardware. Draft JSON is specification data, not runtime configuration. Future design-token change: approved ADR → token update → `--generate-css` → update docs/manifest checksums → validate. Do not import proposed technologies/policies as approved implementation.
