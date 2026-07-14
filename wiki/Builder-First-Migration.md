# Builder-first construction migration

KlumAST 4.0 changes DSL Object creation from “allocate a model, then mutate it” to “configure Builders, then materialize a structurally immutable completed model graph.” The authoritative design is [issue #416](https://github.com/klum-dsl/klum-ast/issues/416) and [ADR 0003](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0003-builder-first-materialization.md).

Existing factory-oriented DSL scripts remain the supported construction style:

```groovy
def config = Config.Create.With {
    project("api") {
        url "https://example.invalid/api"
    }
}
```

The closure receivers, child values, mutators, and lifecycle callbacks through `POST_TREE` are Builders. `INSTANTIATE` (phase 40) materializes the whole composition graph, including cyclic relationships. `VALIDATE` and later phases receive completed DSL Objects.

## Required client migrations

- Replace direct construction with generated factories. Completed DSL Objects are not client-constructed.
- Move post-construction `model.apply { ... }` calls into the original `Create.With { ... }` callback, a Template, or another factory input. Completed models expose no generated `apply` method.
- Stop calling `KlumInstanceProxy.getProxyFor(model)`. The deprecated compatibility adapter accepts Builders only. Completed-object technical state belongs to the Model companion and supported public utilities.
- Treat `KlumRwObject` as a temporary deprecated generated-layout marker only. Builders no longer expose the former
  `getDSLInstance()` or `getRwInstance()` identity aliases. [ADR 0005](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0005-generated-dsl-support-api.md)
  removes RW vocabulary in favor of the generated `Foo_DSL.Builder` interface and `@DelegatesToBuilder`; implementation is
  tracked by [issue #394](https://github.com/klum-dsl/klum-ast/issues/394).
- Build all owned children through the parent Builder lifecycle. Do not call `Child.Create.With` from inside a parent construction callback or from a nested converter: that starts a second lifecycle. Use the generated child method on the parent Builder instead.
- Pass an existing completed DSL Object only to a `FieldType.LINK` relationship. Completed objects cannot become newly owned composition. Use a Template when an existing object is intended as a reusable recipe; applying it rehydrates fresh Builders.

Standalone root factories still return completed models. A custom factory or converter that creates a completed model remains suitable only where that model is the root result or an aggregation `LINK` target. The `Create.AsBuilder` composition protocol and Builder-producing collection projections are recorded in [ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md) and tracked by [issue #431](https://github.com/klum-dsl/klum-ast/issues/431). Until that follow-up is available, collection-local creator methods, direct `DelegatingScript` collection creation, and owned DSL Object converters that call a root factory fail with nested-lifecycle guidance; use generated child closure methods instead.

## Schema and lifecycle changes

- Source field initializers and `FieldType.BUILDER` fields exist and run on Builders only.
- Non-relationship, non-transient model fields are final. `FieldType.TRANSIENT` and Java `transient` fields remain mutable.
- Model collections are independent read-only snapshots. Supported declarations are `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`. Other concrete and custom collection declarations fail schema compilation.
- Sorted snapshots retain their comparator. `EnumSet` getters return defensive copies. Simple Values are retained rather than deep-copied.
- Pre-materialization custom phases extend `BuilderVisitingPhaseAction`; post-materialization phases extend `ModelVisitingPhaseAction`. The legacy untyped `VisitingPhaseAction` is deprecated.
- Provisional validation issues raised during Builder phases transfer to the completed model. Each `InstanceValidator` executes at most once per completed model.

The current generated Builder spelling and layout remain implementation details. The accepted 4.0 public target is the
top-level `Foo_DSL` namespace with nested `Factory`, `Builder`, and relationship-factory interfaces. Until #394 implements
ADR 0005, do not add a dependency on either current `$_RW` spelling or the future interfaces. The planned AnnoDocimal
sources are IDE-only mirrors: the Gradle plugin must expose them for completion without compiling or packaging them as a
second definition of the AST-generated interfaces.

## Templates, serialization, and Jackson

Templates remain client-facing DSL Object recipes. Every application copies the recipe into a fresh Builder graph, so ownership and lifecycle callbacks belong to the recipient construction.

A Template `applyLater` recipe must address the fresh Builder through its closure delegate. Capturing a Builder in a local variable or holder is rejected because a completed recipe must not retain construction state. The complete graph of other captured recipe values must be serializable so the detached recipe remains serializable.

The current completed Model companion is serialized with the model; Builder-only state is not. [ADR 0006](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0006-completed-object-support.md)
accepts `KlumObjectSupport.of(object)` as the future Java-first path/structure/validation facade and makes
`KlumModelProxy` plus raw metadata internal. Until #390 implements that boundary, do not build new integrations on direct
proxy or metadata access.

The current Jackson implementation restores raw serialized state into Builders. [ADR 0007](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0007-jackson-configuration-replay.md)
accepts configuration replay as the 4.0 target: persist public Builder inputs, bind resolved Jackson properties, and run
one normal lifecycle so derived values are recomputed. #428/#251 track implementation; existing JSON remains beta and may
change.
