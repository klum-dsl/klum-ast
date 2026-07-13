# Builder-first construction migration

Issue #416 and [ADR 0003](adr/0003-builder-first-materialization.md) change DSL Object creation from “allocate a model, then mutate it” to “configure Builders, then materialize an immutable model graph.” Existing factory-oriented DSL scripts remain the supported construction style:

```groovy
def config = Config.Create.With {
    project("api") {
        url "https://example.invalid/api"
    }
}
```

The closure receivers, child values, mutators, and lifecycle callbacks through `POST_TREE` are Builders. `INSTANTIATE` (phase 40) materializes the whole composition graph, including cyclic `LINK` relationships. `VALIDATE` and later phases receive completed DSL Objects.

## Required client migrations

- Replace direct construction with generated factories. A DSL Object may not declare client constructors.
- Move post-construction `model.apply { ... }` calls into the original `Create.With { ... }` callback, a template, or another factory input. Completed models expose no `apply` method.
- Stop calling `KlumInstanceProxy.getProxyFor(model)`. The compatibility adapter accepts Builders only; completed-object technical state belongs to `KlumModelProxy` and normal public utilities.
- Build all owned children through the parent Builder lifecycle. Do not call `Child.Create.With` from inside a parent construction callback or from a nested converter/factory: that starts a second lifecycle and cannot produce composition owned by the first. Use the generated child method on the parent Builder instead.
- Pass an existing completed DSL Object only to a `FieldType.LINK` relationship. Completed objects cannot become newly owned composition. Use a template when an existing object is intended as a reusable recipe; applying it rehydrates fresh Builders.

Standalone root factories still return completed models. A custom factory or converter that creates a completed model remains suitable only where that model is the root result or an aggregation `LINK` target.

## Schema and lifecycle changes

- Source field initializers and `FieldType.BUILDER` fields exist and run on Builders only.
- Non-relationship, non-transient model fields are final. `FieldType.TRANSIENT` (and Java `transient`) fields remain mutable.
- Model collections are independent read-only snapshots. Supported declarations are `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`. Concrete and custom collection declarations fail schema compilation.
- Sorted snapshots retain their comparator. `EnumSet` getters return defensive copies. Simple Values are retained rather than deep-copied.
- Pre-materialization custom phases extend `BuilderVisitingPhaseAction`; post-materialization phases extend `ModelVisitingPhaseAction`.
- Provisional validation issues raised during Builder phases transfer to the completed model. Each `InstanceValidator` executes at most once per completed model.

The current generated Builder spelling and layout are implementation details. Do not add a public dependency on them: issue #394 will decide the final generated type name and whether it is nested or top-level.

## Templates, serialization, and Jackson

Templates remain client-facing DSL Object recipes. Every application copies the recipe into a fresh Builder graph, so ownership and lifecycle callbacks belong to the recipient construction.

The completed model companion is serialized with the model and retains breadcrumb, model-path, and validation state. Builder-only state is not serialized.

Jackson restores serialized fields into Builders, then runs the normal lifecycle, graph materialization, and validation. This policy is explicitly provisional: lifecycle-derived values may be recomputed, and issue #428 will decide the long-term persisted-versus-recomputed behavior.
