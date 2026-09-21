# Explicit shared Model and Builder capabilities

Date: 2026-09-20

Amended: 2026-09-21 (canonical nested `Builder` vocabulary and `@Mutator` migration)

Status: Proposed

Target release: 4.1

Implementation status: Not started; the implementation plan defines five independently verifiable behavior slices, a
post-slice decision checkpoint, and final contract reconciliation.

Tracking issue: [#689 — Design explicit shared Model and Builder capabilities](https://github.com/klum-dsl/klum-ast/issues/689)

Implementation plan: [ADR 0020 implementation plan](../implementation/adr-0020-explicit-shared-model-builder-capabilities.md)

Parent decisions:

- [ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md)
- [ADR 0004 — AsBuilder composition and Builder-producing factory projections](0004-asbuilder-composition-protocol.md)
- [ADR 0005 — generated DSL support API](0005-generated-dsl-support-api.md)
- [ADR 0010 — framework public-interface conventions](0010-framework-public-interface-conventions.md)
- [ADR 0011 — shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)

Related issues: [#648 — Builder-aware type predicates and narrowing](https://github.com/klum-dsl/klum-ast/issues/648),
[#650 — explicit Builder parameters and results](https://github.com/klum-dsl/klum-ast/issues/650), and
[#651 — selected pure Model queries on Builders](https://github.com/klum-dsl/klum-ast/issues/651).

## Context

Builder-first construction deliberately gives a DSL Object two different states. A generated Builder owns mutable
construction state until Materialization; the completed Model owns the immutable public domain view afterward. Ordinary
Model methods therefore cannot be made available on Builders by delegation without lying about receiver state, relationship
types, ownership, or return values.

Some domain operations are nevertheless meaningful in both states. Examples include a URL derived from scalar fields, a
subtype-sensitive read during a lifecycle callback, and a source-visible helper whose Model form consumes or returns
completed DSL Objects while its Builder form consumes or returns exact generated Builders. The current workarounds duplicate
logic, split Model and Builder helpers, or lose static checking. `KlumBuilder<T>` cannot fill this role because it is
intentionally a zero-operation capability rather than a schema-access API.

ADR 0004 already projects source-visible Builder-producing factories and converters where KlumAST can prove an
active-session path. That projection is about owned-child creation, not permission to copy every Model method onto a
Builder. The dead `DelegateFromRwToModel` lineage in #208 and #503 is evidence against restoring the old blanket behavior.

## Decision

### Classify methods separately from signature facets

Add one final, non-instantiable public vocabulary namespace class, `com.blackbuild.klum.ast.Builder`, with four public
nested annotations:

- `@Builder.Query` marks a side-effect-free instance method that remains on the completed Model and is also generated on
  the public `Foo_DSL.Builder` contract. Its result must contain no DSL Object or Builder type.
- `@Builder.Method` marks a Builder-only method. It is moved to the generated Builder and is absent from the completed
  Model, matching the established semantics of `@Mutator`.
- `@Builder.Input` marks one Model-typed parameter whose Builder-side signature uses the corresponding generated Builder
  type. Unmarked Model-typed parameters retain completed-Model semantics, including `LINK` use cases.
- `@Builder.Result` marks a method result whose Builder-side signature and execution produce an owned, unsealed Builder in
  the active Construction session.

`Builder` is only an enclosing namespace for Builder-specific schema vocabulary: it is not an annotation and is unrelated
to each schema's generated `Foo_DSL.Builder` interface. `Query` and `Method` are the two explicit and mutually exclusive
method categories. `Input` and `Result` are orthogonal signature
facets: they describe individual parameter or result projection and do not turn an ordinary instance method into a
Builder operation by themselves. An unannotated instance method therefore remains a Model method. Applying both
`@Builder.Query` and `@Builder.Method`, or applying a signature facet to an instance method with neither category, is a
targeted compilation error.

`@Builder.Query`, `@Builder.Method`, and `@Builder.Result` target methods, while `@Builder.Input` targets parameters. They
use the annotation module's normal runtime retention so KlumCast and generated-contract inspection see one consistent
declaration, but runtime construction does not discover or interpret them. The annotations are valid only on methods
declared by a DSL Object. Annotating an untransformed external helper or custom Factory method is a targeted compilation
error.

An ordinary `@Builder.Query` method remains on the Model and gains a generated Builder twin. A `@Builder.Method` is
Builder-only. Source-visible static converter/helper methods continue to use ADR 0004's hidden linked-twin mechanism
rather than becoming static members of `Foo_DSL.Builder`; `@Builder.Input` and `@Builder.Result` can describe their linked
Builder signatures without reclassifying the source method as an instance `Query` or `Method`.

`@Builder.Method` is the canonical successor to `@Mutator`. `@Mutator` remains source-compatible for the 4.1 migration
window, retains exactly the same Builder-only behavior, and is deprecated in source and generated documentation in favor
of `@Builder.Method`. The compiler must not require an immediate source rewrite, silently change a legacy method's
visibility, or allow both annotations on one declaration. New examples and generated guidance use only
`@Builder.Method`.

The nested vocabulary does not revive the rejected `@Method(MethodType)` design. `Builder.Method` is a zero-argument
marker for one fixed Builder-only category, not an annotation whose enum value selects among an open-ended set of method
kinds. `Builder.Query` remains a separate marker with different retention-on-Model and purity rules, while `Builder.Input`
and `Builder.Result` name signature positions rather than enum flags. Adding a future category therefore requires a new,
reviewable annotation and does not silently broaden `Builder.Method`.

For example:

```groovy
import com.blackbuild.klum.ast.Builder

@DSL
class Registry {
    String host

    @Builder.Query
    String toUrl() {
        "https://$host"
    }

    @Builder.Result
    static Registry normalized(@Builder.Input Registry source) {
        Registry.Create.With(host: source.host.toLowerCase())
    }

    @Builder.Method
    void normalizeHost() {
        host = host.toLowerCase()
    }
}
```

The completed helper signature stays `Registry normalized(Registry)`. In Builder-phase source compiled with the Schema,
its selected twin is equivalent to
`Registry_DSL.Builder<Registry> normalized(Registry_DSL.Builder<Registry>)` and uses the active-session Builder-producing
path. `toUrl()` remains available on the completed Model and is explicitly projected onto the generated Builder.
`normalizeHost()` exists only on the Builder. Existing source may continue to spell the last annotation `@Mutator`, but
new source uses `@Builder.Method`.

### Defer bulk state-interface projection until the first slices provide evidence

A further interface-level annotation could provide a bulk opt-in for coherent shared behavior, but this ADR does not adopt
one yet. Its value depends on evidence from the narrower query, input, result, and narrowing slices.

The preferred soft candidate is a selector interface, analogous to the contract interfaces used by
`@OwnerProvidedDefaults`. The Model implements the authored interface, and that interface identifies the Model methods
which receive the same special handling as individually annotated methods. KlumAST classifies each selected method and its
parameter/result positions according to the explicit projection rules. The generated Builder gains those projected
methods, but does not implement the selector interface, and KlumAST generates no companion interface. The interface is a
declaration map, not a shared Model/Builder type.

After the five behavior slices are executable, ADR 0020 must be revisited and record one of three outcomes:

- implement selector-interface grouping in the current lane because repeated annotations demonstrate enough leverage;
- waive it because the explicit per-method and per-position annotations remain sufficient; or
- move it to a later related issue when the need is credible but the contract is not required for the current lane.

If that review finds a separately typeable Builder-state contract valuable, it may consider a generated paired companion
as a stronger alternative. That alternative needs independent evidence because it adds a public type hierarchy without
improving method selection. One authored interface must never be shared unchanged by Model and Builder when DSL Object
parameter or result types differ. This ADR reserves neither the `BuilderState` name nor any companion shape.

### Project types only at annotated positions

At a `@Builder.Input` or `@Builder.Result` position, a concrete DSL Object type `M` projects to
`M_DSL.Builder<M>`. An abstract DSL
Object type projects to its public Builder contract with the existing bounded subtype form. Supported Collection and Map
shapes preserve their declared outer type and map keys while projecting DSL Object elements or values, following ADR
0004's established container rules.

Non-DSL types and unmarked positions are unchanged. A `LINK`-returning helper therefore continues to return a completed
Model unless `@Builder.Result` explicitly declares owned-Builder semantics. `@Builder.Result` never converts a completed
Model into composition: every successful Builder path must already produce an unsealed Builder in the current Construction
session, and normal attachment/claim checks remain authoritative.

Raw `KlumBuilder`, wildcard Builder elements, unresolved DSL-bearing generic placeholders, unsupported nested containers,
and projected overloads that collapse to the same JVM signature are compilation errors. KlumAST reports the annotated
position and the unsupported type instead of guessing. Non-DSL generics are preserved.

### Treat query purity as a checked contract

`@Builder.Query` is permitted only on a non-void method that remains valid on a completed Model. Its implementation may read
fields present in both states, call other projected queries, and use ordinary non-DSL values. It may not assign DSL fields,
invoke known mutators or lifecycle methods, use construction-only `FieldType.BUILDER` state, start or attach construction,
or return a DSL Object/Builder-bearing value.

The compiler enforces these locally visible restrictions. The annotation is also a Schema Developer assertion that calls
into foreign non-DSL code are observational; KlumAST does not attempt whole-program purity analysis. An `instanceof M`
test against a Builder remains invalid and is not silently rewritten.

### Put subtype identity and narrowing on the typed factory token

Extend the generated factory capability `KlumFactory.BuilderFactoryProvider<T, B>` with:

```java
boolean isModelOrBuilder(Object value);
boolean isBuilder(Object value);
B asBuilder(Object value);
```

`isModelOrBuilder` tests the selected Model type against either a completed DSL Object or a Builder's declared Model type.
`isBuilder` is the narrower predicate for Builder values. `asBuilder` returns the same Builder identity as the exact public
`B` type and throws a targeted `KlumModelException` for a completed Model, a mismatched Builder, or a non-DSL value. It
does not create, unseal, adopt, or materialize anything.

This makes subtype-sensitive Builder code explicit and statically narrowable without exposing Builder implementation
classes:

```groovy
if (SpecialRegistry.Create.isBuilder(registry)) {
    def special = SpecialRegistry.Create.asBuilder(registry)
    assert special.toUrl().startsWith('https://')
}
```

Identity testing remains deterministic outside an active Construction session. The returned Builder's existing sealed,
session, and ownership guards still control every subsequent construction operation.

### Keep generated and completed contracts separate

Public non-static projected methods appear on `Foo_DSL.Builder`, and therefore in its AnnoDocimal IDE mirror, with exact
projected signatures and Builder-state documentation. The original Model method documentation retains completed-state
semantics. Hidden static twins remain synthetic generated linkage and are not client entrypoints. `Foo_DSL.Builder` remains
the complete schema-specific construction contract and `KlumBuilder<T>` remains zero-operation.

Source compiled by an older compiler has no shared-capability contract. A precompiled DSL type produced by an implementing
compiler is usable through its emitted `Foo_DSL` API and linked twins; an older or otherwise opaque precompiled helper is
not retroactively projected from annotations or bytecode. Calls that require a missing projection fail with a targeted
source-visibility/recompilation diagnostic.

### Preserve lifecycle, serialization, and compatibility boundaries

Shared capabilities do not change Materialization, Construction-session ownership, Template identity, Builder sealing,
Model serialization, validation timing, or Jackson behavior. Generated Builder methods and static twins are construction
code and are never serialized with completed Models. Model methods continue to run against completed state when invoked on
a Model.

Every compiler/API slice requires Groovy 3, 4, and 5 source coverage. Generated public signatures require Java and
`@CompileStatic` Groovy consumers plus AnnoDocimal mirror parity. User-visible behavior requires documentary coverage and
4.1 migration guidance.

## Consequences

- Schema Developers can share selected domain logic without recovering legacy Model-to-Builder delegation.
- The `Builder` namespace makes method categories and projected signature positions discoverable without conflating them.
- An annotation at each projected method, parameter, or result makes Builder exposure reviewable in source; ordinary
  unannotated methods remain Model-only.
- Existing `@Mutator` source remains valid while new code converges on the canonical `@Builder.Method` spelling.
- Bulk state-interface projection remains conditional until implementation evidence shows whether it earns a public
  interface in the current lane.
- Exact generated Builder types flow through public contracts while `KlumBuilder<T>` stays narrow.
- Completed `LINK` results remain distinguishable from owned Builder results.
- Static source projection retains ADR 0004's same-compilation/source-visibility boundary.
- The compiler gains explicit ambiguity, purity, generic, and precompiled diagnostics instead of dynamic fallback.
- Generated API additions are 4.1-compatible additions but still require the full multi-Groovy and IDE-mirror matrix.

## Rejected alternatives

**Restore blanket Model-method delegation.** This repeats the behavior rejected by #208/#503, exposes operations without
intent, and makes Model-typed relationship signatures untruthful during construction.

**Make `KlumBuilder<T>` a schema-access API.** A generic Builder cannot name schema-specific members or return exact child
Builder types. Adding reflective accessors would expose implementation state and weaken ADR 0005.

**Introduce a public `ModelOrBuilder<T>` wrapper.** The wrapper would erase the exact generated Builder interface; adding a
second Builder type parameter would merely restate the paired Model/Builder types at every call site. A binary wrapper also
cannot truthfully capture the construction distinctions that matter after narrowing: unsealed versus sealed, current versus
inactive session, and unclaimed versus composition-claimed ownership. It would force callers to branch on lifecycle state
that the compiler already knows while contaminating parameters, results, and container element types. Exact Model and
generated Builder contracts retain more information with a smaller caller interface. A private implementation view may
still be introduced later if repeated internal inspection proves a real seam; it is not generated or supported surface.

**Make one interface common to Model and Builder.** Even a schema-authored state interface may contain DSL Object
parameters/results whose truthful types differ across Materialization. If bulk interface projection is later justified,
its review must preserve distinct truthful Model and Builder contracts rather than force one shared type.

**Use one extensible `@Method(MethodType)` annotation.** It overlaps lifecycle annotations, makes unrelated future enum
members part of one compatibility surface, and hides whether an input, result, or whole query is projected. The selected
`@Builder.Method` spelling does not weaken this rejection: it is one fixed Builder-only marker inside a vocabulary
namespace, has no `MethodType` member, and cannot classify a query or signature position.

**Infer every Model-typed parameter or result.** A Model result may intentionally be an aggregation `LINK`; automatic
projection would turn completed-object semantics into ownership by accident.

**Rewrite `instanceof Model` in Builder code.** Ambient rewriting hides the state distinction. The typed factory predicate
and cast make the intended Model-or-Builder test and the exact narrowing explicit.
