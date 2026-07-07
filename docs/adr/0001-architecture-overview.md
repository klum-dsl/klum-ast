# ADR 0001: Repository context and high-level architecture

Date: 2026-07-07

Status: Proposed

## Context

This repository contains the KlumAST project: a modular Java/Groovy library composed of multiple focused subprojects (annotations, runtime, jackson integration, gradle plugin, etc.). Prior to this ADR there was no `CONTEXT.md` at the repo root; domain vocabulary lived in the project wiki (`wiki/Terms.md`).

Agents and engineering skills expect a repository-level context file (`CONTEXT.md`) for single-context repos and `docs/adr/` for ADRs. To make the repository consumable by the agent workflows and to surface canonical terminology to contributors, we must pick an authoritative place for the project's context and record the choice.

## Decision

We will adopt a single-context layout for this repository.

- Add a root `CONTEXT.md` containing a short project summary, a high-level architecture overview (module boundaries) and a consolidated domain glossary (sourced from `wiki/Terms.md`).
- Place ADRs in `docs/adr/` and record this ADR as `docs/adr/0001-architecture-overview.md`.
- Agents and skills (triage, domain-modeling, improve-codebase-architecture) will read `CONTEXT.md` and `docs/adr/` to learn the project's vocabulary and past decisions.

Rationale:

- The project is relatively small and the module boundaries are already explicit in `settings.gradle`. A single global context reduces duplication and makes it easier for tools (and humans) to find the glossary.
- The wiki already contains useful domain notes; consolidating the most widely-used terms into `CONTEXT.md` gives machines a predictable place to read domain language from.

## Consequences

- Short term: `CONTEXT.md` is added to the root and agents can read canonical definitions. Contributors should reference `CONTEXT.md` when naming domain concepts in issues and PRs.
- Long term: If the repository later grows into a true multi-context monorepo, we will add `CONTEXT-MAP.md` and move to per-context `CONTEXT.md` files; update agents accordingly.

## Next steps

1. Maintain `CONTEXT.md` as the canonical glossary. Consider periodically syncing it with `wiki/Terms.md` or consolidating the wiki pages into `docs/`.
2. Add any missing ADRs for major architecture decisions (module responsibility, public API guarantees, migration strategy for major versions).
3. Optionally create tracer-bullet issues to codify implementation tasks derived from this decision.

