# CineNest repository instructions

This Git root is the Theater Project / CineNest repository. Canonical KB: `knowledge/`.

For **every CineNest request**, use the Project Execution Agent workflow:
1. Read [charter](docs/agents/CINENEST-PROJECT-EXECUTION-AGENT.md), [current status](knowledge/execution/CINENEST-EXECUTION-STATUS.md), [handoff](knowledge/execution/CINENEST-SESSION-HANDOFF.md), applicable nested instructions, decisions and relevant knowledge before recommendations or implementation.
2. Reconcile actual branch/HEAD/upstream/worktree and source changes; preserve unrelated changes. Map work to requirements and a bounded change set in the [canonical plan](knowledge/execution/CINENEST-EXECUTION-PLAN.md).
3. Execute only a READY authorized change set; routine local edits/checks within scope need no repeated permission. Guidance/read-only requests remain read-only. If no READY approved item exists, report the exact approval/dependency rather than inventing authority or skipping to unrelated work.
4. Verify and review actual completed work. Update [evidence and coverage](knowledge/execution/CINENEST-REQUIREMENTS-TRACEABILITY.md), status and handoff before reporting completion; for read-only guidance report findings without writing these records unless authorized. Do not bypass this workflow because the user did not repeat it.

The agent charter does not approve a stack, every proposed roadmap item, a business rule, provider, hardware, cost or live operation. Preserve hybrid visuals, editable-package requirement and bank-payment requirement; supporting choices keep their actual status. Receipt is not verified funds, verified payment is not booking/access readiness; no unsafe egress/device assumptions.

No production access/deployments, destructive data operations, real payments/refunds/bank changes, secret rotation, real notifications or physical commands without explicit applicable authorization. A simulator does not establish physical safety. Never install an Agents SDK/orchestration service just to assume this role.

Run documented safe checks after inspecting them: `python3 knowledge/validate_design_kb.py` and `python3 knowledge/execution/validate_execution.py`. The design validator's `--generate-css` option writes CSS; use only for an authorized token change. No application build/tests exist yet.

Publication: user's explicit automatic commit/push instruction in this CineNest chat authorizes task-scoped Git publication after checks/review; it does not authorize deployment/live operations or policies. Reverify actual remote/branch, respect stronger branch rules, stage only owned files, never force push/rewrite history; report denied/failed operations accurately. Keep source/evidence free of secrets.

Default: one change set per session, stop at required approval or completed checkpoint. One writer updates shared plan/status; no atomic task claim or native agent registration is implied. No background execution promised.
