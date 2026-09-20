# Explicit shared Model and Builder capabilities

Date: 2026-09-20

Status: Proposed

Target release: 4.1

Implementation status: Not started; the implementation plan defines five independently verifiable behavior slices plus
final contract reconciliation.

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

Add four orthogonal annotations to the public schema vocabulary:

- `@BuilderQuery` marks a side-effect-free instance method that remains on the completed Model and is also generated on
  the public `Foo_DSL.Builder` contract. Its result must contain no DSL Object or Builder type.
- `@BuilderInput` marks one Model-typed parameter whose Builder-side signature uses the corresponding generated Builder
  type. Unmarked Model-typed parameters retain completed-Model semantics, including `LINK` use cases.
- `@BuilderResult` marks a method result whose Builder-side signature and execution produce an owned, unsealed Builder in
  the active Construction session. It may be used on an ordinary shared helper or on a Builder-only `@Mutator`.
- `@BuilderState` marks a schema-authored interface implemented by a Model. Every eligible method in that interface is an
  explicit state-query projection; KlumAST generates a distinct Builder-side companion interface with projected parameter
  and result types.

`@BuilderQuery` and `@BuilderResult` target methods, `@BuilderInput` targets parameters, and `@BuilderState` targets
interfaces. They use the annotation module's normal runtime retention so KlumCast and generated-contract inspection see
one consistent declaration, but runtime construction does not discover or interpret them. Method/parameter annotations are
valid only on methods declared by a DSL Object; `@BuilderState` is valid only on an interface implemented by at least one
DSL Object. Annotating an untransformed external helper or custom Factory method is a targeted compilation error.

The presence of `@BuilderQuery`, `@BuilderInput`, or `@BuilderResult` selects an otherwise ordinary source-visible method
for Builder-side projection. An ordinary non-static method remains on the Model and gains a generated Builder twin; a
`@Mutator` remains Builder-only. Source-visible static converter/helper methods continue to use ADR 0004's hidden linked
twin mechanism rather than becoming static members of `Foo_DSL.Builder`. `@BuilderState` selects the complete eligible
method set of its interface and produces the paired companion contract in one declaration.

The annotations describe independent facts instead of introducing a broad `@Method(MethodType)` classification. Existing
lifecycle and `@Mutator` annotations retain their established meaning, and future method categories do not acquire Builder
visibility merely by being added to an enum.

For example:

```groovy
@BuilderState
interface RegistryState {
    String toUrl()
    Registry selectedRegistry()
    boolean selects(Registry candidate)
}

@DSL
class Registry implements RegistryState {
    String host
    Registry selected

    String toUrl() {
        "https://$host"
    }

    Registry selectedRegistry() {
        selected
    }

    boolean selects(Registry candidate) {
        selected.is(candidate)
    }

    @BuilderResult
    static Registry normalized(@BuilderInput Registry source) {
        Registry.Create.With(host: source.host.toLowerCase())
    }
}
```

`Registry` implements the completed-state `RegistryState`. Its generated Builder implements the distinct
`RegistryState_DSL.BuilderState` companion, whose `selectedRegistry()` result and `selects(...)` parameter are
`Registry_DSL.Builder<Registry>` rather than `Registry`. The completed helper signature stays
`Registry normalized(Registry)`. In Builder-phase source compiled with the Schema, its selected twin is equivalent to
`Registry_DSL.Builder<Registry> normalized(Registry_DSL.Builder<Registry>)` and uses the active-session Builder-producing
path.

### Project schema-authored state interfaces as paired contracts

`@BuilderState` is the bulk opt-in for a coherent read-only state interface. The Model implements the authored interface;
the Builder does not. For an annotated interface `S`, KlumAST generates `S_DSL.BuilderState` and makes each applicable
`Foo_DSL.Builder` implement that companion. This is a pair of state-specific interfaces, not one interface shared across
the Materialization boundary.

Every public instance method declared or inherited by `S` participates. It has `@BuilderQuery` purity semantics, while
its complete parameter and result signature is recursively state-projected. Concrete DSL Object types become exact public
Builders, annotated state-interface types become their generated `BuilderState` companions, and supported Collection/Map
shapes preserve their outer type and keys. Non-DSL types remain unchanged. Static/private interface methods do not form
instance state and are rejected rather than silently omitted.

The Model must provide each abstract method through its source implementation, inherited Model implementation, or generated
property accessor. A source-visible default interface method is projected with the same purity checks. A precompiled
annotated interface is usable only when it was compiled with the matching KlumAST contract and carries its generated
companion; KlumAST does not reconstruct an opaque default body from bytecode.

Multiple `@BuilderState` interfaces compose: the generated Model Builder implements each companion, and companion
inheritance mirrors annotated source-interface inheritance. Incompatible inherited projections or two methods that erase to
the same Builder descriptor fail Schema compilation with both source declarations named.

The companion is deliberately narrower than `Foo_DSL.Builder`: it describes observable construction state but provides no
factory, mutation, lifecycle, session, or materialization capability. Generic helpers may depend on the companion when they
truly operate on Builder state, without depending on a complete schema-specific Builder contract.

### Project types only at annotated positions

At an annotated input or result position, a concrete DSL Object type `M` projects to `M_DSL.Builder<M>`. An abstract DSL
Object type projects to its public Builder contract with the existing bounded subtype form. Supported Collection and Map
shapes preserve their declared outer type and map keys while projecting DSL Object elements or values, following ADR
0004's established container rules.

Non-DSL types and unmarked positions are unchanged. A `LINK`-returning helper therefore continues to return a completed
Model unless `@BuilderResult` explicitly declares owned-Builder semantics. `@BuilderResult` never converts a completed
Model into composition: every successful Builder path must already produce an unsealed Builder in the current Construction
session, and normal attachment/claim checks remain authoritative.

A DSL Object result projected through `@BuilderState` is different: it exposes the Builder currently stored in that state
position and makes no ownership or mutability promise. It may be an owned unsealed Builder or a sealed wrapper for a
completed `LINK`. Only `@BuilderResult` denotes a newly produced owned Builder result.

Raw `KlumBuilder`, wildcard Builder elements, unresolved DSL-bearing generic placeholders, unsupported nested containers,
and projected overloads that collapse to the same JVM signature are compilation errors. KlumAST reports the annotated
position and the unsupported type instead of guessing. Non-DSL generics are preserved.

### Treat query purity as a checked contract

`@BuilderQuery` is permitted only on a non-void method that remains valid on a completed Model. Its implementation may read
fields present in both states, call other projected queries, and use ordinary non-DSL values. It may not assign DSL fields,
invoke known mutators or lifecycle methods, use construction-only `FieldType.BUILDER` state, start or attach construction,
or return a DSL Object/Builder-bearing value. `@BuilderState` methods use the same purity rules but may declare DSL-bearing
parameters/results because the paired companion gives those positions distinct truthful Builder-state types.

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
projected signatures and Builder-state documentation. `@BuilderState` additionally produces the paired
`S_DSL.BuilderState` companion and mirror. The original Model method/interface documentation retains completed-state
semantics. Hidden static twins remain synthetic generated linkage and are not client entrypoints.

Do not make the original `@BuilderState` interface common to Model and Builder. Annotated DSL Object parameters and results
have different truthful signatures in the two states. The generated companion is the narrow observable-state interface;
`Foo_DSL.Builder` remains the complete schema-specific construction contract and `KlumBuilder<T>` remains zero-operation.

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
- A schema-authored `@BuilderState` interface groups a coherent state view and produces a separately typed Builder
  companion without making Builders implement the completed-Model interface.
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
that the compiler already knows while contaminating parameters, results, and container element types. State-specific Model
and generated companion interfaces retain more information with a smaller caller interface. A private implementation view
may still be introduced later if repeated internal inspection proves a real seam; it is not generated or supported surface.

**Make one interface common to Model and Builder.** Even a schema-authored state interface may contain DSL Object
parameters/results whose truthful types differ across Materialization. `@BuilderState` therefore generates a paired
Builder-side companion instead of making the Builder implement the completed-state interface.

**Use one extensible `@Method(MethodType)` annotation.** It overlaps `@Mutator` and lifecycle annotations, makes unrelated
future enum members part of one compatibility surface, and hides whether an input, result, or whole query is projected.

**Infer every Model-typed parameter or result.** A Model result may intentionally be an aggregation `LINK`; automatic
projection would turn completed-object semantics into ownership by accident.

**Rewrite `instanceof Model` in Builder code.** Ambient rewriting hides the state distinction. The typed factory predicate
and cast make the intended Model-or-Builder test and the exact narrowing explicit.
