# AsBuilder composition and Builder-producing factory projections

Date: 2026-07-13

Status: Accepted

Tracking issue: [#431 — Implement ADR 0004: add AsBuilder composition protocol](https://github.com/klum-dsl/klum-ast/issues/431)

Implementation status: Deferred from PR #429. That PR implements ADR 0003 but intentionally leaves DSL Object converters,
collection-local factory projections, and other nested factory-oriented composition incompatible with the Builder-first
model. The desired contracts remain executable as reasoned `@PendingFeature` tests. See the
[implementation context](../implementation/adr-0004-asbuilder-composition.md) for confirmed failure paths, implementation
slices, and acceptance coverage.

Parent decision: [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)

## Context

ADR 0003 requires every owned DSL Object to be built in one Builder lifecycle and materialized with its complete
composition graph. A root factory such as `Create.With`, `Create.One`, or `Create.From` truthfully returns a completed DSL
Object. Calling such a materializing factory while another Builder lifecycle is active cannot create owned composition:
the child would either start an independent lifecycle or return a completed object that can only be used as an aggregation
`LINK` target.

Several established DSL conveniences currently hide that conflict. Generated collection factories project methods from
the element's `Create` factory into closures such as `bars { From(script); WithAge(age) }`; direct collection methods also
accept scripts; DSL Object converters can call `Create.With` internally. Before Builder-first materialization these nested
calls joined the outer phase driver and their results became owned children. PR #429 instead rejects them, including
previously supported behavior from issues #198, #270, #300, and #319.

Child creation during an active lifecycle is nevertheless essential. The required boundary is therefore not “no creation
inside a lifecycle,” but “no nested materialization inside a lifecycle.” Composition needs a truthful factory protocol that
produces an unsealed child Builder in the active construction session.

## Decision

### Root creation and active-session composition are separate protocols

Standalone `Create.With`, `Create.One`, and `Create.From` calls continue to run a complete root lifecycle and return a
completed DSL Object. They remain invalid as nested materializing calls during another Builder lifecycle.

`Create.AsBuilder` is the primary composition protocol. At the stable runtime boundary it returns `KlumBuilder<T>`, creates
and configures a child in the active construction session, and never starts an independent `PhaseDriver`, materializes, or
validates a separate model. It is valid only while a construction session is active. A returned Builder belongs to that
session and cannot be adopted by another lifecycle.

The generated factory override returns the current concrete generated Builder covariantly and uses that type as the
configuration closure delegate for IDE support. Generated code obtains the type through the existing generated-type
indirection, so this ADR does not decide the Builder's final name or nested/top-level placement deferred to issue #394.

`AsBuilder` applies active Templates and runs `PostCreate`, explicit configuration, and `PostApply` exactly once. The outer
lifecycle subsequently runs graph-wide phases, materialization, and validation. No `AsStub` protocol is introduced without
a concrete use case for an intentionally incomplete Builder.

### Collection factories are Builder-producing projections

A collection-local method has composition semantics even when it retains the familiar name of a root factory method. A
generated projection such as `bars { From(script) }` therefore calls the corresponding Builder-producing implementation,
adds the returned unsealed Builder to the owning Builder, and returns the concrete child Builder. It must not delegate to
`Bar.Create.From` and then attempt to adopt the completed result.

The same rule applies to generated direct script methods such as `bars(scriptClass)`. Factory methods returning multiple
elements receive Builder-producing twins that return Collections or Maps of unsealed Builders; map keys and key-mapping
semantics are preserved before the outer graph is materialized.

Framework-owned inputs that are already recipes or data can be applied directly to a child Builder. This includes
`With`/`One` configuration, `FromMap`, `DelegatingScript`, and text, File, or URL sources compiled as delegating scripts. A
regular or precompiled `Script` whose `run()` returns a completed DSL Object is an opaque materializing program, not a
Builder recipe. It remains top-level-only unless it adopts an explicit Builder-producing contract. Collection projections
must reject that case with migration guidance rather than reinterpret the completed result.

### Converters and custom factory methods need truthful Builder variants

Source converters should state a truthful Builder contract when practical:

```groovy
@Converter
static KlumBuilder<Bar> fromLong(long value) {
    Bar.Create.AsBuilder(birthday: new Date(value))
}
```

Generated relationship converter methods return the concrete child Builder. A source-retention compiler hint may tell the
DSL transformation to substitute the generated Builder type in generated methods, but it does not silently change a user
method declared as returning a model into a different public JVM signature.

For source-visible classic converters and custom factory methods whose return paths end in recognizable `Create.With`,
`Create.One`, `Create.From`, or another adaptable source-visible factory call, the transformation generates a hidden
Builder-producing twin and routes composition projections through it. The original method remains unchanged for direct
callers and continues to return an independently completed model. Recursive calls between adaptable factory methods use
their Builder-producing twins, including methods that return Collections or Maps of DSL Objects.

Opaque or precompiled model-returning methods cannot be adapted safely. They are not projected as usable composition
methods and must migrate to the explicit Builder protocol or be used only to produce a root result or aggregation `LINK`
target. An explicitly marked Template returned by a converter may instead be rehydrated at the converter boundary;
ordinary completed models cannot.

### Consequences for Template recipes

Most modifying code exists only on a live Builder. Templates are the intentional exception because they must preserve a
Builder-producing recipe after the defining construction session and Builder have been discarded. Ordinary completed DSL
Objects and Template recipes therefore have distinct serializable companions behind a common internal abstraction:

- `KlumModelProxy` owns completed-model paths, metadata, validation state, and `InstanceValidator` memoization. It contains
  no deferred modifying code.
- `KlumTemplateProxy` identifies a DSL Object as a Template and owns immutable `TemplateRecipeState`, including dehydrated
  deferred Builder actions.

Live deferred actions remain on `KlumBuilder`. Materializing an ordinary model discards actions that have run. Materializing
a Template snapshots its actions into `TemplateRecipeState`, which can clone and replay them into each fresh recipient
Builder without exposing the stored closure collection.

Every unsealed Builder materialized as part of a Template composition graph receives a Template companion. A sealed wrapper
around a pre-existing aggregation `LINK` target retains its ordinary Model companion. `TemplateManager.isTemplate(Object)`
exposes Template identity without exposing companion implementation. A Template object cannot be assigned directly to any
relationship, including a `LINK`; it must be consumed through a Template or copy recipe API that rehydrates it into fresh
Builders. This provides the Template marking required by issue #343.

`applyLater` is valid only for a phase number strictly lower than `INSTANTIATE` (40). Scheduling phase 40 or later fails
immediately for both ordinary and Template Builders. This is the materialization-boundary invariant, not a general
current/past-phase guard from issue #281.

## Consequences

- Composition-oriented factory calls join the owner's construction session and run lifecycle callbacks exactly once.
- Root factory methods retain their completed-model signatures and behavior.
- Collection-local APIs stop advertising opaque model-returning methods that cannot work as composition.
- Existing source-visible factory and converter APIs can retain direct-call behavior through hidden Builder-producing twins.
- Precompiled or opaque composition factories returning completed models are a documented compatibility break.
- Completed Model companions contain no modifying recipe code; Template-only recipe behavior has a local serializable home.
- Template identity is graph-aware and can be rejected at ordinary relationship assignment boundaries.

## Deferred compatibility option

If evidence later requires binary compatibility for opaque converters, an opt-in adapter may intercept their nested
factories and divert creation into a marked, lifecycle-free Template recipe before an ordinary model lifecycle starts. This
is not part of the accepted protocol and must never reinterpret an ordinary completed model as a Template after the fact.

## Rejected alternatives

Globally allowing nested materialization is rejected. It would create an independently completed graph, risk duplicate
initializers and lifecycle side effects, discard or duplicate validation, change identity and cycle behavior, and violate
ADR 0003's composition boundary.

Making an existing `T Create.With(...)` method return a Builder only when a lifecycle happens to be active is rejected. The
declared JVM return type would become untruthful and statically compiled Groovy or Java callers could fail with casts even
if dynamic Groovy happened to accept the value.

Materializing an ordinary result and copying it into another Builder is rejected. Ordinary completed models are aggregation
targets, not recipes, and must never be silently rehydrated.

A new public `KlumDraft<T>` protocol remains deferred. It offers stronger capability typing but adds public surface and
construction-session machinery without improving the current `KlumBuilder<T>` seam enough to justify it.
