# Builder-owned lifecycle classes

Date: 2026-09-22

Status: Accepted

Target release: 4.1

Tracking issue: [#420 — Lifecycle classes](https://github.com/klum-dsl/klum-ast/issues/420)

Implementation plan: [ADR 0021 implementation plan](../implementation/adr-0021-builder-owned-lifecycle-classes.md)

Parent decisions:

- [ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md)
- [ADR 0005 — Generated DSL support API](0005-generated-dsl-support-api.md)
- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)
- [ADR 0020 — Explicit shared Model and Builder capabilities](0020-explicit-shared-model-builder-capabilities.md)

## Context

#420 predates Builder-first and proposed grouping lifecycle methods in classes
that might use the former RW state. #415 subsequently delivered only completed
Model `@Validate` inner classes. Under ADR 0003, all mutating lifecycle work
through `POST_TREE` operates on Builders; materialization at phase 40 creates
the completed Model, and validation runs only afterwards.

The existing validation-class mechanism cannot cross that state switch. It
instantiates a non-static inner class with its completed Model outer instance.
Mutating lifecycle methods are instead relocated to the generated Builder. A
generic Model-to-Builder delegate would make Model-only methods and relationship
types lie about their state, which ADR 0020 explicitly rejects.

A throwaway compiler probe established the narrow feasibility needed here: a
new non-static `InnerClassNode` owned by a Builder can receive the original
lifecycle class's method nodes before variable scope/type checking. Unqualified
field access and calls then bind to Builder fields, projected `@Builder.Query`
members, and `@Builder.Method` members. `InnerClassNode` has no mutable
outer-class setter, so rehoming always constructs a Builder-owned class rather
than modifying the source node in place.

## Decision

### Relocate one Builder-phase class, not each individual method

A direct, non-static inner class of a DSL Object, at any source visibility, may
be marked with one eligible pre-materialization lifecycle annotation. The
compiler relocates it into the hidden generated Builder before variable scope
and type checking, removes it from the completed Model, and creates a
Builder-internal runner for its phase. A `private` source class is therefore
valid and is preferred when the rules have no source-level consumer. The runner
constructs one lifecycle-class instance per visited Builder and invokes its
callback methods in source declaration order.

The initial implementation starts with `@PostTree`; subsequent slices may add
the other parameterless Builder-phase lifecycle annotations (`@PostCreate`,
`@PostApply`, `@AutoCreate`, `@AutoLink`, and `@Default`) under the same
contract. `EARLY_VALIDATE` is a built-in provisional-issue pass, not a
lifecycle-class callback. `@Owner` has a parameterized relationship contract
and is excluded. `@Validate` classes remain completed-Model validation classes
and are neither moved nor generalized.

For example, the intended source shape is:

```groovy
@DSL
class Deployment {
    String environment

    @Builder.Query
    String normalizedEnvironment() {
        environment?.trim()?.toLowerCase()
    }

    @Builder.Method
    void useEnvironment(String value) {
        environment = value
    }

    @PostTree
    class TreeRules {
        void normalizeEnvironment() {
            useEnvironment(normalizedEnvironment())
        }

        void verifyBuilderState() {
            assert environment == environment.toLowerCase()
        }

        private String diagnosticName() {
            "deployment:$environment"
        }
    }
}
```

Every public, non-static, parameterless method declared by the lifecycle class
is a callback. Private methods are helpers and are not invoked as callbacks.
The initial slice rejects explicit constructors and lifecycle-class inheritance;
that keeps callback lifetime, ordering, and outer-class projection local. An
inheritance feasibility tracer remains in #420's implementation lane and may
add an explicit supported hierarchy only after it proves source superclass
rewriting, Builder hierarchy alignment, callback order, and diagnostics.

### Preserve truthful Builder semantics

The relocated class's lexical outer is the generated Builder. Ordinary
unqualified field access therefore reaches Builder fields; relationship values
remain Builders; and source-visible `@Builder.Query`, `@Builder.Method`, and
the source-compatible deprecated `@Mutator` spelling work through their existing
Builder projections. An unannotated Model-only instance method has no Builder
counterpart and is a targeted compilation error. `this` remains the lifecycle-
class instance, while an explicit `Foo.this` has no Builder-safe meaning and is
rejected.

This compiler-only relocation is not a public generated API. The hidden class
and runner are not emitted on `Foo_DSL`, its AnnoDocimal mirror, or the completed
Model. `KlumBuilder<T>` remains its intentionally zero-operation public
capability; it does not become a generic lifecycle receiver.

### Maintain lifecycle, ownership, and persistence boundaries

Lifecycle classes run only while their Builder is active in its Construction
session. They may create and attach only fresh, same-session owned Builders;
completed Models remain aggregation `LINK`/`OPTIONAL_LINK` targets and are
never re-owned or rehydrated. They must complete before `INSTANTIATE`; they may
not retain a Builder in a Model, schedule Builder work at or after phase 40, or
capture a Builder in a Template recipe.

Any provisional validation issue raised before materialization follows the
existing Builder-to-Model-companion transfer. The lifecycle-class instance and
its Builder-only state are construction machinery and are not serialized.

## Consequences

- Schema authors can group related Builder-phase callbacks without exposing
  them as completed-Model methods or introducing one-method wrappers.
- The source class retains normal Builder-aware IDE completion because the
  compiler relocates its lexical outer before type resolution.
- Callback declaration order becomes an explicit contract and must be generated,
  rather than inferred from reflection's method order.
- The compiler gains focused diagnostics for Model-only calls, explicit Model
  outer references, invalid class placement, constructors, and unsupported
  inheritance.
- The feature changes annotation placement, AST movement, and generated source;
  it requires Java plus Groovy 3/4/5 compiler/consumer coverage. No new public
  Builder interface member or JPMS export follows from it.

## Rejected alternatives

**One explicit Builder argument per callback.** This is truthful but turns a
cohesive lifecycle class into needless boilerplate without adding information:
the Builder is already the relocated class's lexical outer.

**Instantiate the original class with a completed Model.** No completed Model
exists before materialization, and mutating it would violate Builder-first
immutability.

**Move only each callback method.** A method alone cannot rebind the inner
class's lexical outer, helper members, or callback-instance state.

**Delegate every Model method to Builders.** This repeats the rejected blanket
projection model and makes Model-typed relationships and results untruthful.

**Reuse validation-class reflection unchanged.** Validation classes are
completed-Model components. Builder lifecycle classes need generated callback
ordering and must not inherit validation's receiver or visibility assumptions.

**Support lifecycle-class inheritance in the first slice.** Rehoming a source
superclass changes both its lexical outer and the subclass's source superclass.
It needs an independent feasibility proof before becoming a compatibility
promise.
