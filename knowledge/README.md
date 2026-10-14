# CineNest Knowledge Base Index

Canonical Git KB remains `knowledge/`. Read [PROJECT](PROJECT.md) for scope/features/rules/acceptance, [ARCHITECTURE](ARCHITECTURE.md) for technology/modules/data/API/security/IoT/decisions, [DESIGN](DESIGN.md) for hybrid UI/tokens/pages/charts, [PROGRESS](PROGRESS.md) for instructions/plan/status/approval/evidence/handoff. Follow [root instructions](../AGENTS.md). Update these documents in place; new knowledge files require explicit owner approval. Future prompts remain in conversation unless a file is requested.

KB1.5.0 consolidates KB1.4.0 at75cd23f. Tokens1.0.0/chart1.2.0 unchanged. Stack proposal awaiting owner approval, no implementation authorized. Superseded prose files are labelled frozen historical snapshots retained for owner deletion review; they are not parallel canonical authorities. The [manifest](manifest.json) stores exact migration mappings/checksums, not another prose report. No files deleted. Existing source requests/registries/images/theme/evidence retained; missing PDFs remain missing, not fabricated.

Structured data stays in existing product/decisions/execution/sources registries and tokens/styles assets. Historical evidence JSON remains immutable; current verification/status recorded in PROGRESS and status.json. One backlog/coverage model, no duplicate sources of truth.

Safe checks from Git root: `python3 knowledge/validate_design_kb.py`; `python3 knowledge/execution/validate_execution.py`; `git diff --check`. Validators inspect canonical migrated content and source preservation, not only retained legacy files. Application/provider/hardware/physical tests remain NOT RUN.
