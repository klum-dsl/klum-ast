---
name: klum-implement-issue
description: Implement one approved KlumAST issue or tracer-bullet slice with module-appropriate Spock coverage, Groovy 3/4/5 compatibility checks, and synchronized release-facing documentation. Use after issue intent and architectural decisions are settled.
---

# Implement a KlumAST Issue

Deliver one approved behavior slice without silently making new architecture decisions.

## Load the contract

Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/testing.md`, `docs/agents/commits.md`, `docs/agents/pull-requests.md`, the canonical issue, architecture map, linked ADRs, implementation plan, and wiki contract. Confirm the requested slice and its acceptance criteria before editing.

Stop and report a decision conflict when the requested behavior contradicts an ADR, Builder-first lifecycle invariant, generated/public contract, or confirmed issue. Route unresolved design back to `$klum-grill-issue` or `$klum-plan-design`.

Create a new branch dedicated to the issue from the agreed base, or confirm the current branch was newly created for this issue. Commit completed reasoning steps without asking, following `docs/agents/commits.md`; every TDD commit must complete its red-to-green cycle.

## Implement at the correct seam

Respect module boundaries and existing conventions. Put coverage where the behavior lives:

- ordinary Spock specifications in `<module>/src/test/groovy/`
- reusable harnesses in existing `src/testFixtures/groovy/` trees
- compilation or lifecycle scenarios in `klum-ast/src/test/scenarios/`
- Gradle behavior in `klum-ast-gradle-plugin/src/test/groovy/`

Keep the change focused. Preserve Java 17, license headers, generated-code conventions, structural immutability, Construction-session ownership, and Materialization/validation ordering unless the accepted contract explicitly changes them.

## Verify progressively

Follow `docs/agents/testing.md`: start with the narrowest Groovy 3 test, then the affected module suite. Run Groovy 4 and 5 compatibility lanes when touching compiler APIs, AST behavior, Groovy syntax, dependencies, or before final handoff. Run root `check` for repository-wide or release-critical changes.

## Synchronize durable artifacts

Update only what the behavior requires:

- formal decision in `docs/adr/`
- implementation reasoning in `docs/implementation/`
- user behavior or migration in `wiki/`, including `_Sidebar.md` when navigation changes
- implemented release change in `CHANGES.md`
- canonical issue/PR links according to `docs/agents/pull-requests.md`

Run the applicable code review against the issue branch base, commit its fixes, then review and improve the commit sequence under `docs/agents/commits.md`. Re-run the required checks after rewriting history.

Finish with changed behavior, affected modules, tests and results, documentation changes, compatibility implications, commit-history review, and remaining follow-ups. Finish only when every acceptance criterion has evidence, the final documentation matches the branch state unless explicitly excluded, and the worktree contains no unrelated edits.
