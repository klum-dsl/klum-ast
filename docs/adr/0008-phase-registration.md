# State-typed declarative phase registration

Date: 2026-07-14

Status: Accepted

Tracking issue: [#305 — Custom Phase annotation](https://github.com/klum-dsl/klum-ast/issues/305)

Implementation status: Planned for later 4.x. The runtime currently loads `PhaseAction` implementations directly through
`ServiceLoader` and orders them only by phase number. See the
[implementation plan](../implementation/adr-0008-phase-registration.md).

Related decision: [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)

Supersedes: the phase-registration portion of [ADR 0002](0002-phase-contracts-and-builder-model.md). It does not revive ADR
0002's superseded Builder/Model migration strategy or weaken ADR 0003's state typing.

## Context

Third-party lifecycle actions can be discovered with `ServiceLoader`, but they cannot declare stable identities or refine
ordering between actions at the same numeric phase. ADR 0002 proposed a broad phase DSL and schema annotation before the
Builder/Model state boundary was settled. The remaining need is narrower: a deterministic plugin registration SPI that
preserves numeric phases and the explicit Builder-versus-Model action types.

## Decision

Introduce the Java SPI:

```java
interface PhaseRegistration<A extends PhaseAction> {
    String getId();
    KlumPhase getPhase();
    Set<String> getRunsAfter();
    Set<String> getRunsBefore();
    A createAction();
}

interface BuilderPhaseRegistration
        extends PhaseRegistration<BuilderVisitingPhaseAction> {}

interface ModelPhaseRegistration
        extends PhaseRegistration<ModelVisitingPhaseAction> {}
```

Registrations are discovered through `ServiceLoader`. IDs are stable and globally unique; built-in actions publish
constants. Each `PhaseDriver` receives a fresh action instance from `createAction()`.

Numeric phase order remains authoritative and remains the coordinate used by `applyLater`. `runsAfter` and `runsBefore`
refine ordering only among registrations with the same phase number. A cross-number declaration may document/assert the
already implied order but may not contradict it.

Before construction begins, registration validation rejects duplicate IDs, missing references, cycles, numeric-order
contradictions, action/registration phase mismatches, Builder registrations at or after `INSTANTIATE`, and Model
registrations at or before `INSTANTIATE`. Equal-phase registrations without a dependency are ordered deterministically by
ID.

The built-in `INSTANTIATE` action is framework-owned and cannot be replaced. Existing direct
`ServiceLoader<PhaseAction>` providers remain through a deprecated adapter using the action class name as ID and no
dependencies.

A schema-level `@CustomPhase` annotation is deferred until a concrete schema-authored use case proves it is needed. This
ADR defines the plugin SPI only.

## Consequences

- Plugins gain deterministic, validated equal-phase ordering without changing phase numbers.
- State-typed Builder and completed-model actions remain mandatory.
- Invalid plugin graphs fail before any object construction side effect.
- Existing providers receive a bounded migration path.
- Annotation processing and AST changes are avoided until their semantics are justified.

## Rejected alternatives

A free-form phase DSL that can reorder numeric phases is rejected because it would destabilize lifecycle and `applyLater`
semantics. Loading mutable action singletons is rejected because drivers must not share run state. A single untyped visitor
registration is rejected because it weakens ADR 0003's materialization boundary. Implementing `@CustomPhase` now is
rejected as premature surface area.
