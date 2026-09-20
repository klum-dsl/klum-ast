# Explicit shared Model and Builder capabilities

Date: 2026-09-20

Status: Proposed

Target release: 4.1

Implementation status: Not started; the implementation plan defines four independently verifiable behavior slices, a
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

### Make every shared operation opt in

Add three orthogonal annotations to the public schema vocabulary:

- `@BuilderQuery` marks a side-effect-free instance method that remains on the completed Model and is also generated on
  the public `Foo_DSL.Builder` contract. Its result must contain no DSL Object or Builder type.
- `@BuilderInput` marks one Model-typed parameter whose Builder-side signature uses the corresponding generated Builder
  type. Unmarked Model-typed parameters retain completed-Model semantics, including `LINK` use cases.
- `@BuilderResult` marks a method result whose Builder-side signature and execution produce an owned, unsealed Builder in
  the active Construction session. It may be used on an ordinary shared helper or on a Builder-only `@Mutator`.

`@BuilderQuery` and `@BuilderResult` target methods, while `@BuilderInput` targets parameters. They use the annotation
module's normal runtime retention so KlumCast and generated-contract inspection see one consistent declaration, but
runtime construction does not discover or interpret them. The annotations are valid only on methods declared by a DSL
Object. Annotating an untransformed external helper or custom Factory method is a targeted compilation error.

The presence of `@BuilderQuery`, `@BuilderInput`, or `@BuilderResult` selects an otherwise ordinary source-visible method
for Builder-side projection. An ordinary non-static method remains on the Model and gains a generated Builder twin; a
`@Mutator` remains Builder-only. Source-visible static converter/helper methods continue to use ADR 0004's hidden linked
twin mechanism rather than becoming static members of `Foo_DSL.Builder`.

The annotations describe independent facts instead of introducing a broad `@Method(MethodType)` classification. Existing
lifecycle and `@Mutator` annotations retain their established meaning, and future method categories do not acquire Builder
visibility merely by being added to an enum.

For example:

```groovy
@DSL
class Registry {
    String host

    @BuilderQuery
    String toUrl() {
        "https://$host"
    }

    @BuilderResult
    static Registry normalized(@BuilderInput Registry source) {
        Registry.Create.With(host: source.host.toLowerCase())
    }
}
```

The completed helper signature stays `Registry normalized(Registry)`. In Builder-phase source compiled with the Schema,
its selected twin is equivalent to
`Registry_DSL.Builder<Registry> normalized(Registry_DSL.Builder<Registry>)` and uses the active-session Builder-producing
path. `toUrl()` remains available on the completed Model and is explicitly projected onto the generated Builder.

### Defer bulk state-interface projection until the first slices provide evidence

A fourth interface-level annotation could provide a bulk opt-in for coherent read-only state, but this ADR does not adopt
one yet. Its value and truthful public shape depend on evidence from the narrower query, input, result, and narrowing
slices. Deciding it now would freeze substantially more generated surface, inheritance behavior, and precompiled-contract
rules than the initial use cases require.

After the first four behavior slices are executable, ADR 0020 must be revisited and record one of three outcomes:

- implement a bulk state-interface projection in the current lane because repeated annotations or generic state consumers
  demonstrate enough leverage;
- waive it because the explicit per-method and per-position annotations remain sufficient; or
- move it to a later related issue when the need is credible but the contract is not required for the current lane.

If that review chooses an interface-level feature, one authored interface must not be shared unchanged by Model and
Builder when DSL Object parameter or result types differ. A generated paired companion remains a candidate, not an
accepted contract. This ADR neither reserves the `BuilderState` name nor requires a particular companion shape.

### Project types only at annotated positions

At an annotated input or result position, a concrete DSL Object type `M` projects to `M_DSL.Builder<M>`. An abstract DSL
Object type projects to its public Builder contract with the existing bounded subtype form. Supported Collection and Map
shapes preserve their declared outer type and map keys while projecting DSL Object elements or values, following ADR
0004's established container rules.

Non-DSL types and unmarked positions are unchanged. A `LINK`-returning helper therefore continues to return a completed
Model unless `@BuilderResult` explicitly declares owned-Builder semantics. `@BuilderResult` never converts a completed
Model into composition: every successful Builder path must already produce an unsealed Builder in the current Construction
session, and normal attachment/claim checks remain authoritative.

Raw `KlumBuilder`, wildcard Builder elements, unresolved DSL-bearing generic placeholders, unsupported nested containers,
and projected overloads that collapse to the same JVM signature are compilation errors. KlumAST reports the annotated
position and the unsupported type instead of guessing. Non-DSL generics are preserved.

### Treat query purity as a checked contract

`@BuilderQuery` is permitted only on a non-void method that remains valid on a completed Model. Its implementation may read
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
- An annotation at each projected method, parameter, or result makes Builder exposure reviewable in source.
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

**Use one extensible `@Method(MethodType)` annotation.** It overlaps `@Mutator` and lifecycle annotations, makes unrelated
future enum members part of one compatibility surface, and hides whether an input, result, or whole query is projected.

**Infer every Model-typed parameter or result.** A Model result may intentionally be an aggregation `LINK`; automatic
projection would turn completed-object semantics into ownership by accident.

**Rewrite `instanceof Model` in Builder code.** Ambient rewriting hides the state distinction. The typed factory predicate
and cast make the intended Model-or-Builder test and the exact narrowing explicit.
