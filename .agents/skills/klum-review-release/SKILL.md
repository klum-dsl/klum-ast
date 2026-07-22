---
name: klum-review-release
description: Reconcile KlumAST 4.0 issue curation against the repository, merged work, CI, ADRs, migration guidance, and release notes. Use for periodic release-scope reviews, API-freeze readiness, blocker analysis, and safe deferral recommendations.
---

# Review the KlumAST Release

Produce the smallest coherent 4.0 scope consistent with accepted decisions and current implementation.

## Reconcile reality

Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/issue-tracker.md`, `docs/agents/feature-triage.md`, `docs/agents/pull-requests.md`, all curation files, open issues and milestones, recent merged PRs, current CI status, `CHANGES.md`, `README.md`, `docs/user/Builder-First-Migration.md`, and relevant ADRs and implementation notes.

Cross-check every curation claim against current GitHub and repository state. Identify:

- remaining 4.0 blockers and dependency order
- decisions required before generated/public API freeze
- compatibility, migration, and documentation gaps
- stale or contradicted curation entries
- completed, duplicate, obsolete, or superseded issues
- safe deferrals to 4.1, later 4.x, or 5.0
- recorded feature need horizons and workaround viability that materially affect priority
- risks in implementation order and release scope

Treat green CI as evidence of tested state, not proof that unresolved product or compatibility decisions are complete.
Treat feature use-case classification as priority evidence rather than an automatic ranking. Do not require a retroactive
classification pass over already-triaged issues; use the record when present or when an issue is revisited for another reason.

## Update analysis artifacts

During analysis, update only:

- `docs/implementation/issue-curation/release-4.0.md`
- related files under `docs/implementation/issue-curation/`

Record evidence, recommendation, owner/decision needed, and next action for each blocker. Keep accepted facts separate from proposed scope changes.

## Propose external changes

Present proposed changes to `docs/user/Roadmap.md`, `CHANGES.md`, milestones, labels, and GitHub issues separately. Apply them only after explicit maintainer confirmation and through the appropriate repo-specific skill.

Finish only when the issue index, release plan, repository state, and GitHub state reconcile; every 4.0 item has a disposition and evidence; and the recommended scope states its blockers, deferrals, and residual risks.
