# AsBuilder composition and Builder-producing factory projections

Date: 2026-07-13

Status: Accepted

Tracking issue: [#431 — Implement ADR 0004: add AsBuilder composition protocol](https://github.com/klum-dsl/klum-ast/issues/431)

Implementation status: Planned. PR #429 implemented ADR 0003 but intentionally left nested factory composition,
Template companion separation, and the materialization scheduling boundary incomplete. The accepted tracer bullets and
executable coverage are recorded in the [implementation context](../implementation/adr-0004-asbuilder-composition.md).

Parent decisions:

- [ADR 0003 — Builder-first materialization of DSL Objects](0003-builder-first-materialization.md)
- [ADR 0005 — Generated DSL support namespace and public Builder capabilities](0005-generated-dsl-support-api.md)

## Context

ADR 0003 requires every owned DSL Object to be built in one Builder lifecycle and materialized with its complete
composition graph. Root factories truthfully return completed DSL Objects. Calling one during another lifecycle would
either start nested materialization or return a completed object that cannot become owned composition.

Established collection factories, converters, direct script inputs, and custom factories nevertheless need to create
owned children. Templates additionally need to retain a replayable recipe after their defining Builder is gone. The
framework therefore needs a truthful active-session Builder protocol, a distinct Template companion, and a strict boundary
between deferred Builder actions and materialization.

## Decision

### Separate root creation from active-session composition

`Create.With`, `Create.One`, and `Create.From` own a complete Construction session and return completed DSL Objects.
`Create.AsBuilder` instead creates an unsealed child Builder in the currently active session. It is invalid without an
active session, never starts a `PhaseDriver`, and never materializes or validates independently.

The runtime return type is the narrow `KlumBuilder<T>` capability. Generated factories covariantly return the concrete
`Foo_DSL.Builder` interface from ADR 0005 and use that type as the configuration delegate. `AsBuilder` applies active
Templates and runs `PostCreate`, explicit configuration, and `PostApply` exactly once; graph phases, materialization, and
validation remain owned by the outer session.

Builders carry an internal opaque `ConstructionSession` identity token. One token exists per root lifecycle, all owned
Builders capture it, and ownership/adoption checks compare it by identity. The token exposes no current phase or current
object and becomes invalid after the lifecycle. It is deliberately narrower than `PhaseDriver.Context`.

### Project adaptable factory methods into Builder-producing methods

Collection and Cluster factories call Builder-producing implementations and return the created Builder. Factory methods
returning Collections or Maps receive projections returning concrete containers of Builders. The outer container type,
iteration order, comparator, duplicate behavior, and map keys are preserved. After validating the session, the whole batch
is attached to the owning relationship and the same result container is returned; no separate owning-sink abstraction is
introduced.

Framework-owned inputs are adaptable when they are recipes or data: configuration closures, `FromMap`,
`DelegatingScript`, and text, File, or URL sources compiled as delegating scripts. A regular or precompiled Script whose
`run()` returns a completed model remains an opaque materializing program.

For source-visible model-returning converters and custom factory methods, the transformation generates a hidden twin named
`$klum$asBuilder$<originalName>`. It preserves parameters and overloads but projects the return recursively from
`KlumBuilder<Foo>` to `Foo_DSL.Builder`, including Collection and Map value types. The twin is public at the JVM level where
cross-package generated callers require it, synthetic, reserved under `$klum$`, and unsupported client API. AST metadata
links the original `MethodNode` to its twin; generated and recursive callers bind directly and never rediscover it by name.
Precompiled methods without that metadata remain opaque.

Concrete projection types are inferred from declared generics. No compiler-hint annotation is added. Raw, wildcard, or
otherwise unresolved Builder element types produce a targeted compilation diagnostic instead of a guessed projection.

An unadaptable method is absent from the public `Foo_DSL` interfaces, source stubs, generated documentation, and static IDE
surface. The hidden implementation retains a catalog of omitted signatures and reasons. A dynamic call matching that
catalog throws a targeted `KlumModelException`; an unknown name remains an ordinary `MissingMethodException`.

Projection documentation describes the active-session behavior rather than copying root-factory text verbatim: it creates
an unsealed Builder, attaches it to the relationship, returns it for configuration, and does not independently materialize
or validate. Applicable parameter and throws documentation is preserved, the return text is truthful, map-key behavior is
noted, and the root method is linked with `@see`. Hidden twins carry no public documentation.

### Give Templates a distinct companion and recipe state

Generated DSL Objects hold a private companion field typed as the sealed internal `KlumObjectCompanion`, implemented by
final `KlumModelProxy` and `KlumTemplateProxy`. The common contract contains only `getObject()`, `getBreadcrumbPath()`, and
`getModelPath()`. It may require JVM-public visibility for generated classes in arbitrary packages, but is internal and not
a supported client interface.

`KlumModelProxy` owns completed-model validation and internal metadata and contains no deferred modifying code.
`KlumTemplateProxy` marks Template identity and owns immutable, serializable `TemplateRecipeState`. Live deferred actions
remain on a Builder; Template materialization dehydrates pending actions into recipe state. Recipe state exposes only the
internal `replayInto(KlumBuilder<?> recipient)` operation, which clones and schedules actions without exposing stored Maps
or Closures.

Every owned node created in Template mode receives a Template companion. A pre-existing `LINK` target retains its ordinary
Model companion. `TemplateManager.isTemplate` tests only this persistent identity. Templates cannot be relationship values,
including `LINK`; a Template/copy operation must rehydrate them into fresh Builders.

Java serialization preserves Template identity and immutable recipe state. The captured closure graph is checked when the
Template is materialized; Builders, sessions, scopes, and mutable recipe collections are never serialized. Ordinary models
serialize no deferred actions. No arbitrary cross-version Java-serialization guarantee is made, and Jackson/value
serializers reject Templates rather than silently discarding recipes.

Template identity is narrow, but the internal copy-source protocol is broader:

- an ordinary completed model contributes values only because its deferred actions already ran;
- a marked Template contributes values and replays its recipe;
- an unsealed Builder from the same session contributes current values plus an ephemeral dehydrated snapshot of pending
  actions without being converted into or marked as a Template;
- a sealed or cross-session Builder is rejected as a live source;
- Maps and other data sources remain value-only.

Future `@Mixin` work may define a separate value-only operation. It does not change Template identity.

### Enforce applyLater before materialization

The lowest common scheduling primitive rejects every phase number greater than or equal to `INSTANTIATE` (40), regardless
of overload, Template replay, or creation path. It throws `KlumModelException` immediately. The diagnostic identifies the
phase and says to use a phase below 40 or a `ModelVisitingPhaseAction` for completed-model work, for example:

```text
Cannot schedule applyLater for phase 'instantiate' (40): deferred Builder actions must run before materialization at phase 40. Use a phase below 40, or a ModelVisitingPhaseAction for completed-model work.
```

Existing overloads remain, but there is no clamp, drop, warning mode, or deferred failure. This is the materialization
boundary, not the general past/current-phase guard tracked by #281.

## Consequences

- Nested composition joins one Construction session and lifecycle callbacks execute once.
- Root factories retain completed-model signatures and behavior.
- Public generated APIs advertise only adaptable composition methods.
- Source converters retain direct-call behavior while generated twins provide truthful Builder results.
- Template recipes have persistent identity without contaminating ordinary completed models.
- Existing use of completed models as value-only copy sources remains valid.
- Opaque nested materializing programs and late `applyLater` scheduling are explicit compatibility breaks.

## Rejected alternatives

Globally allowing nested materialization is rejected because it breaks lifecycle ownership, identity, cycles, Templates,
and graph-wide validation. Contextually changing a model-returning JVM method to return a Builder is rejected because its
signature becomes untruthful. Materializing and silently adopting or rehydrating an ordinary result is rejected because
ordinary models are not recipes. Using `PhaseDriver.Context` as the session capability exposes excess authority. A new
owning-sink result API and a separate compiler-hint annotation add surface without improving the inferred contract.
