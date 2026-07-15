---
name: klum-plan-design
description: Convert a confirmed cross-cutting KlumAST issue cluster into an ADR decision and an implementation plan with tracer-bullet slices. Use when work spans modules, changes generated or public contracts, extends Builder-first architecture, or needs executable acceptance coverage before implementation.
---

# Plan a KlumAST Design

Turn confirmed issue intent into durable decisions and independently verifiable implementation slices.

## Establish authority

Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/domain.md`, `docs/agents/commits.md`, the architecture map, canonical issues, every relevant ADR and implementation note, `wiki/Builder-First-Migration.md`, affected source, and executable specifications.

Classify the work:

- **A — implement an accepted ADR:** update or add `docs/implementation/<theme>.md`.
- **B — clarify or extend an ADR:** update that ADR or draft a successor, plus the implementation document.
- **C — make a new architectural decision:** draft the next numbered `docs/adr/NNNN-<decision>.md`, plus the implementation document.

Do not reopen accepted decisions without naming the conflicting evidence. Keep user guidance in `wiki/`, not `docs/`.

## Record the decision

Match the existing ADR style: title, date, status, implementation status when useful, context, decision, consequences, rejected alternatives, parent decisions, and issue links. State public/generated API, lifecycle, serialization, ownership, and Groovy compatibility consequences explicitly where applicable.

## Plan tracer bullets

The implementation document must contain:

- confirmed current behavior and failure paths
- affected modules and important source/generated seams
- compatibility and migration constraints
- thin vertical implementation slices in dependency order
- reasoned commit boundaries within each slice, following `docs/agents/commits.md`
- executable acceptance coverage for each slice
- documentation and release-note work
- risks, open questions, and issue-to-slice mapping

Prefer existing Spock seams, shared fixtures, and `klum-ast/src/test/scenarios/`. Use reasoned `@PendingFeature` tests only for an agreed contract whose implementation is deliberately deferred, following `docs/agents/testing.md`.

Finish only when the decision source is unambiguous, every confirmed requirement maps to a slice and acceptance check, and no speculative requirement is presented as decided.
