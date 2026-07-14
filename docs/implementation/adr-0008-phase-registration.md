# ADR 0008 implementation plan: phase registration SPI

This later-4.x plan implements [ADR 0008](../adr/0008-phase-registration.md) for canonical issue
[#305](https://github.com/klum-dsl/klum-ast/issues/305).

## Current behavior and failure

`PhaseDriver` directly loads `ServiceLoader<PhaseAction>` and stores actions by numeric phase. Providers have no stable ID,
dependency declaration, duplicate/cycle validation, or fresh-instance factory contract. Builder and Model visiting action
constructors enforce their sides of `INSTANTIATE`, but registration cannot be validated as one graph before construction.

## Affected seams

- `klum-ast-runtime`: registration interfaces, discovery, validation/topological ordering, legacy adapter.
- service descriptors in runtime/plugins: migrate built-ins to registration providers and stable ID constants.
- plugin documentation/tests: Java provider example and deterministic failure cases.
- no schema AST/annotation work in this plan.

## Tracer-bullet slices

### [PH-1 — One state-typed registered plugin action](https://github.com/klum-dsl/klum-ast/issues/441)

Register one Builder action and one Model action through their typed registration interfaces, allocate fresh actions per
driver, and preserve existing numeric ordering around `INSTANTIATE`. Adapt one legacy direct provider by class-name ID.
Issue #441 also carries the PH-2 validation-graph foundation so discovery and deterministic ordering land coherently.

### PH-2 — Deterministic dependency graph

Add equal-phase before/after topological ordering and pre-construction validation for duplicates, missing references,
cycles, cross-number contradictions, phase/action mismatch, and the materialization boundary. Use ID ordering for otherwise
unconstrained peers and make built-in `INSTANTIATE` non-replaceable.

### PH-3 — Plugin migration contract

Migrate built-in descriptors, deprecate direct `PhaseAction` loading, publish a Java plugin example and diagnostics, and run
runtime/ServiceLoader integration coverage. Reassess a schema `@CustomPhase` only as a separate future decision.

## Compatibility

Existing direct providers continue through the no-dependency adapter during 4.x. Their class names become provisional IDs,
so plugins that require dependencies must migrate to registrations. Numeric phase numbers do not change.

## Acceptance map

| ADR contract | Slice |
|---|---|
| typed registration and fresh action per driver | PH-1 |
| legacy adapter | PH-1 |
| deterministic ordering and complete validation | PH-2 |
| built-in protection, migration, documentation | PH-3 |

## Risks

`ServiceLoader` order is unspecified, so all determinism must come after complete discovery. Validation must finish before
any construction or action side effect. Cross-module providers need integration tests using real service descriptors.
