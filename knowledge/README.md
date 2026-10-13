# CineNest UI/UX Knowledge Base

Approved direction: one hybrid admin interface with a cinematic customer experience. Exact proposed tokens and policy-dependent behavior require missing-source reconciliation; the canonical empty repository has been cloned under repository/ and these assets form its initial knowledge-base publication. Publication results are recorded in Git history and the task completion report.

| Artifact | Purpose |
|---|---|
| [Design system](CINENEST-DESIGN-SYSTEM.md) | Shared visual/component/accessibility baseline |
| [Admin pages](CINENEST-ADMIN-PAGES.md) | Eleven module specifications |
| [Customer booking pages](CINENEST-CUSTOMER-BOOKING-PAGES.md) | Twelve customer flow/destination specifications |
| [Charts and metrics](CINENEST-CHARTS-AND-METRICS.md) | Eight metrics, formulas and contracts |
| [ADR-001](CINENEST-DESIGN-DECISION-ADR-001.md) | Approved hybrid decision and change governance |
| [Reference traceability](CINENEST-REFERENCE-TRACEABILITY.md) | Preserved evidence, inventory and conflicts |
| [Acceptance checklist](CINENEST-UX-ACCEPTANCE-CHECKLIST.md) | Documentation and future implementation gates |
| [Design tokens](tokens/cinenest-design-tokens.json) | Centralized proposed values |
| [Chart specs](tokens/cinenest-chart-specs.json) | Machine-readable metrics and proposed data contracts |
| [Theme CSS](styles/cinenest-theme.css) | Generated specification variables; not application implementation |
| [Validator](validate_design_kb.py) | Dependency-free structural consistency checks |
| [Validation report](CINENEST-VALIDATION-REPORT.md) | Current results, limitations and Git status |

Run `python3 knowledge/validate_design_kb.py` from the project root. After editing design tokens, run `python3 knowledge/validate_design_kb.py --generate-css`, then update related documentation and rerun validation. Generating CSS changes only the knowledge theme asset. Read traceability and unresolved policies before implementation. No framework, endpoint, chart library, provider, or policy is established merely by these documents.
