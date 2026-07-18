---
name: klum-curate-issues
description: Inventory, investigate, cluster, and provisionally classify KlumAST GitHub issues for release planning. Use for global issue sweeps, duplicate discovery, 4.0 scope analysis, or refreshing the persistent curation indexes without mutating GitHub issues.
---

# Curate KlumAST Issues

Build an evidence-backed issue inventory before interviewing the maintainer or changing GitHub.

## Load durable context

1. Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/issue-tracker.md`, `docs/agents/triage-labels.md`, `docs/agents/feature-triage.md`, and `docs/agents/domain.md`.
2. Read `README.md`, `CHANGES.md`, `wiki/Builder-First-Migration.md`, relevant ADRs and implementation notes, and `docs/implementation/issue-curation/architecture-map.md` if present.
3. Treat `wiki/Roadmap.md` as historical evidence unless current decisions confirm it.

## Inventory and cluster

Use the repository's configured GitHub workflow to read every open issue, including comments, labels, milestone, dates, and linked work. Do not edit, label, close, or retarget issues in this skill.

For each issue record:

- intended problem or capability
- for a newly triaged enhancement, primary use case, need horizon, workaround viability, and optional secondary angle
- affected module, domain concepts, generated/public API, and compatibility surface
- bug, enhancement, refactoring, documentation, or design decision
- current relevance and evidence of implementation, supersession, or rejection
- related issues, PRs, tests, ADRs, wiki pages, and release notes
- relationship to Builder-first 4.0 and Groovy 3/4/5 compatibility
- provisional disposition: `4.0 must`, `4.0 nice`, `4.1`, `later 4.x`, `5.0`, `completed`, `obsolete`, `duplicate`, or `maintainer decision`

Use the feature-triage classification as priority evidence and derive minimum scope from the primary use case. Do not block
classification when evidence is unknown, revisit already-triaged enhancements solely to fill the fields, or let a secondary
angle silently broaden the request. When an existing enhancement is being reconsidered for another reason, add the record
from available evidence. A complexity-free mini-feature may instead carry a concise quick-win rationale.

Cluster by underlying domain problem before deep source exploration. Inspect one code path per cluster where possible and record where redundancy and prior-rejection checks were performed.

## Maintain the curation cache

Create or refresh only:

- `docs/implementation/issue-curation/issue-index.md`
- `docs/implementation/issue-curation/duplicates.md`
- `docs/implementation/issue-curation/release-4.0.md`
- `docs/implementation/issue-curation/decision-log.md`

Distinguish evidence, inference, and unresolved maintainer decisions. Preserve confirmed earlier decisions unless new evidence explicitly contradicts them. Finish only when every open issue appears exactly once in the index, every cluster is cross-linked, and uncertain decisions have targeted interview questions.
