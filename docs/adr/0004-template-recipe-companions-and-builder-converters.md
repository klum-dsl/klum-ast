# Template recipe companions and Builder-producing converters

Date: 2026-07-13

Status: Accepted

Parent decision: [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)

## Context

ADR 0003 makes Builders the sole owners of mutable construction state and limits the Model companion to completed-object technical state. Deferred `applyLater` actions satisfy that rule while a Builder is live, but a Template must persist those modifying actions after its defining Builder has been discarded. DSL Object converters pose a related lifecycle problem: a classic converter can call `Create.With` while another Builder lifecycle is active, even though that factory normally produces an independently completed model.

## Decision

### Template companions

Ordinary completed DSL Objects and Template recipes have distinct serializable companions behind a common internal companion abstraction:

- `KlumModelProxy` owns completed-model paths, metadata, validation state, and `InstanceValidator` memoization. It contains no deferred modifying code.
- `KlumTemplateProxy` identifies a DSL Object as a Template and owns immutable `TemplateRecipeState`, including dehydrated deferred Builder actions.

Live deferred actions remain on `KlumBuilder`. Materializing an ordinary model discards actions that have run. Materializing a Template snapshots its actions into `TemplateRecipeState`, which can clone and replay them into each fresh recipient Builder without exposing the stored closure collection.

Every unsealed Builder materialized as part of a Template composition graph receives a Template companion. A sealed wrapper around a pre-existing aggregation `LINK` target retains its ordinary Model companion. `TemplateManager.isTemplate(Object)` exposes Template identity without exposing companion implementation. A Template object cannot be assigned directly to any relationship, including a `LINK`; it must be consumed through a Template or copy recipe API that rehydrates it into fresh Builders. This provides the Template marking required by issue #343.

`applyLater` is valid only for a phase number strictly lower than `INSTANTIATE` (40). Scheduling phase 40 or later fails immediately for both ordinary and Template Builders. This is the materialization boundary invariant, not a general current/past-phase guard from issue #281.

### Builder-producing converters

The primary composition protocol for DSL Object converters is `Create.AsBuilder`. It returns `KlumBuilder<T>` at the stable runtime boundary, creates and configures a child in the active construction session, and never starts an independent `PhaseDriver` or materializes or validates a separate model. The generated factory override exposes the current concrete generated Builder as its covariant return and closure delegate for IDE support. That generated reference is obtained through the existing generated-type indirection so this decision does not determine the Builder name or nested/top-level placement deferred to issue #394.

`AsBuilder` is valid only in an active construction session. The returned Builder is associated with that session and cannot be adopted by another lifecycle. It applies active Templates and runs nested `PostCreate`, explicit configuration, and `PostApply` exactly once. No `AsStub` API is introduced without a concrete use case requiring an explicitly incomplete Builder.

Source converters should state a truthful Builder contract, for example:

```groovy
@Converter
static KlumBuilder<Bar> fromLong(long value) {
    Bar.Create.AsBuilder(birthday: new Date(value))
}
```

Generated relationship converter methods return the concrete child Builder. A source-retention compiler hint tells the DSL transformation to substitute the generated Builder type in generated factory methods, but it does not silently change a user method declared as returning a model into a different public JVM signature.

For source-visible classic converters whose return paths end in recognizable `Create.With` or `Create.One` calls, the transformation generates a hidden Builder-producing twin and routes DSL composition through it. The original method remains unchanged for direct callers and still returns an independently completed model. Opaque or precompiled model-returning converters cannot receive this adaptation safely and must migrate to the explicit Builder protocol or be used only for aggregation `LINK` fields.

An explicitly marked Template returned by a converter can be rehydrated at the converter boundary. Ordinary completed models cannot.

## Consequences

- Completed Model companions contain no modifying recipe code, while Template-only behavior has a local, serializable home.
- Template identity is graph-aware and can be rejected at ordinary relationship assignment boundaries.
- Composition converters join the owner's single Builder lifecycle and run lifecycle callbacks only once.
- Existing source-visible direct-factory converters can retain their public completed-model behavior through a generated Builder twin.
- Precompiled or opaque composition converters returning completed models are a documented compatibility break. Completed converter results remain valid for `LINK` aggregation only.

## Deferred compatibility option

If evidence later requires binary compatibility for opaque converters, an opt-in adapter can intercept their nested factories and divert creation into a marked, lifecycle-free Template recipe before an ordinary model lifecycle starts. This option is not part of the accepted converter protocol. It must never reinterpret an ordinary completed model as a Template after the fact.

## Rejected alternatives

Materializing an ordinary converter result and then copying it into another Builder is rejected. The nested factory fails before the converter returns under the current lifecycle boundary; permitting an isolated nested lifecycle would risk duplicate initializers and lifecycle side effects, discarded validation, changed identity and cycles, and would contradict ADR 0003's rule that ordinary completed models are never rehydrated.

A new public `KlumDraft<T>` protocol is also deferred. It offers stronger capability typing but adds public surface and construction-session machinery without improving the current `KlumBuilder<T>` seam enough to justify it.
