# ADR-004: User-confirmed Bank Deposit and Manual Payment

CN-KB-BANK-PAYMENT-001 · 2026-10-14. Source S9, provenance USER-CONFIRMED; core Decision APPROVED, Implementation NOT_VERIFIED. Supporting workflow/controls/contract/report choices RECOMMENDED, method policies OPEN_DECISION. ADR-001–003 unchanged; existing package requirement approved and retained.

## Core decision
Add customer deposit/transfer to business account, receipt submission, administrator review notification and independently verified authorized payment approval alongside existing hosted online gateway option. [Canonical specification](product/CINENEST-BANK-DEPOSIT-AND-MANUAL-PAYMENT.md) owns PR-BANK-001/CN-C10/CN-A13. No real receiving account or connectivity is established by documentation.

## Conflicts and explicit resolution
- Receipt uploaded ≠ independent receiving-bank credit verification; verified funds ≠ allocation/sufficient payment ≠ guarded booking confirmation ≠ acknowledged access.
- Existing gateway notification/attempt/capture language now scoped to gateway. Bank reconciliation emits internal evidence-source facts, never forged gateway callbacks/card capture. Pre-confirmation inventory/identity/age/consent/hours/capacity remain required; original-ID checks still entry requirement.
- Gateway short checkout holds cannot silently govern manual review. Method-specific durable bounded holds/timeliness/service hours/deadlines remain OD-23/24/29; 24-hour lead is not selected.
- Initial online fee does not approve bank fee; OD-25 unresolved. Partial/excess/late/duplicate funds recorded independently, no wallet/installment/shared-deposit product inferred; no bank customer card token/damage re-billing assumption.
- Valid package/quote/instruction snapshots preserved. Received funds survive unavailable booking; safe restoration/customer-agreed alternative/approved refund, not displacement/forfeit or latest catalog reprice.
- Chart financial semantics extended from gateway captures to explicit bank credits with credited/verified/allocated/refund clocks. Receipts and allocation never double-count collections; excess/unmatched actual cash not automatic booking/recognized revenue. Existing chart series colors unchanged; chart contract content version advances intentionally. Token/CSS/image bytes preserved.

## Supporting recommendations and acceptance
Independent manual matching, controlled statement entry identity, append-only verified funds/allocations/adjustments, concurrent/idempotent guarded booking, durable alerts/access and evidence-based refunds are recommended unimplemented design. Bank feed/OCR approval/current provider support not asserted; original PDFs missing and no legal endorsement.

BP-AC01–13 require future implementation evidence. OD-23–29 cover unresolved fee/timing/matching/privacy/account/refund/staffing decisions. Material additive KB 1.1.0→1.2.0, chart contract 1.1.0→1.2.0; tokens remain 1.0.0. No no-op increment/duplicate IDs. Application/runtime/money/account/device/deployment unchanged.
