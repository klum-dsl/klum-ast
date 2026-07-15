# Builder-first construction migration

KlumAST 4.0 changes how model creation works internally: it configures Builders first and then materializes one completed,
structurally immutable model graph. Most schemas and model scripts that already use generated factories should continue to
work without changes:

```groovy
def config = Config.Create.With {
    project("api") {
        url "https://example.invalid/api"
    }
}
```

Do not rewrite working schemas preemptively. Compile the schema, run a representative model, and follow the targeted
diagnostics if KlumAST finds a construct that crosses the new Builder lifecycle boundary.

## Migration checklist

### 1. Compile the schema

Run the build or compilation task that normally compiles your `@DSL` classes. Fix ordinary compilation errors first, then
use this guide for Builder-first diagnostics:

| Problem | What it means | What to do |
| --- | --- | --- |
| A DSL Object is constructed directly | Completed model instances are now created by the generated lifecycle. | Replace constructors and direct allocation with `Foo.Create.With`, `One`, `FromMap`, or another generated root factory. |
| A client-facing signature refers to `$_RW`, `KlumRwObject`, or an RW delegate | Those types are generated implementation details. | Use the generated `Foo_DSL.Builder` interface and `@DelegatesToBuilder`, or let the generated relationship method supply the delegate type. |
| A model collection declaration is rejected | Completed collections are read-only snapshots and require a supported declaration. | Declare `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, or `EnumSet`; remove unsupported concrete/custom declarations. |
| A `KlumBuilder` result is raw, wildcarded, or unresolved | KlumAST cannot determine which public Builder interface to expose. | Declare the concrete model type, for example `KlumBuilder<Child>` or `List<KlumBuilder<Child>>`. |
| A member beginning with `$klum$` is rejected | The namespace is reserved for generated implementation members. | Rename the source member. |
| A custom creator or converter is absent from `Foo_DSL` or its IDE mirror | Its model-producing path is opaque or precompiled, so KlumAST cannot safely adapt it to the active session. | Use the generated child method, return an explicit `KlumBuilder<Foo>`, or compile the producer source together with the schema. |

### 2. Compile and run a representative model

Compile and execute at least one real root configuration. A unit test that calls `Config.Create.With` is usually the
simplest repeatable migration check; an existing root script is equally suitable. A project-less script can also obtain
KlumAST with `@Grab`, but the complete standalone-script setup will be documented separately.

| Runtime failure | What to do |
| --- | --- |
| An independent factory cannot start while construction is active | A nested `Child.Create.With` would start a second lifecycle, which is forbidden. Call the generated child method on the parent Builder. Framework extensions can use `Child.Create.AsBuilder` and attach the result in the same session. |
| A completed DSL Object cannot be adopted as composition | Build a fresh child through the owning Builder. Pass an existing completed object only to a `FieldType.LINK` relationship. |
| `Create.AsBuilder` reports no active session, a different session, or a completed session | Use it only inside the active root construction and attach the returned Builder before that construction finishes. |
| An omitted Builder-producing projection is reported | Replace the call with the generated relationship method, return an explicit `KlumBuilder<Foo>`, or make the recognizable factory path source-visible to schema compilation. |
| `Create.AsBuilder.From` rejects a regular `Script` | Use a `DelegatingScript` as the nested configuration recipe, or run the materializing Script as a root with `Create.From`. |
| A generated `apply` method is missing on a completed model | Move the changes into the original `Create.With` callback, a Template, or another factory input. |
| Completed-model proxy access fails | Stop calling `KlumInstanceProxy.getProxyFor(model)`; use supported completed-object utilities as they become available. |

### 3. Run the full model test suite

Pay particular attention to lifecycle callbacks, validation, ownership and breadcrumb paths, sorted collection comparators,
Templates, serialization, and Jackson inputs. These areas intentionally distinguish between the construction-time Builder
graph and the completed model graph.

## Detailed migration rules

- Replace direct construction with generated factories. Completed DSL Objects are not client-constructed.
- Move post-construction `model.apply { ... }` calls into the original `Create.With { ... }` callback, a Template, or another
  factory input. Completed models expose no generated `apply` method.
- Stop calling `KlumInstanceProxy.getProxyFor(model)`. The deprecated compatibility adapter accepts Builders only. Completed
  model technical state belongs to the Model companion and supported public utilities.
- Treat `KlumRwObject` as a temporary deprecated generated-layout marker only. Builders no longer expose the former
  `getDSLInstance()` or `getRwInstance()` identity aliases. [ADR 0005](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0005-generated-dsl-support-api.md)
  removes RW vocabulary in favor of the generated `Foo_DSL.Builder` interface and `@DelegatesToBuilder`; implementation is
  tracked by [issue #394](https://github.com/klum-dsl/klum-ast/issues/394).
- Build all owned children through the parent Builder lifecycle. Do not call `Child.Create.With` directly from inside a
  parent construction callback: that would start a second lifecycle, which is forbidden. Use the generated child method on
  the parent Builder.
- Pass an existing completed DSL Object only to a `FieldType.LINK` relationship. Completed objects cannot become newly owned
  composition. Use a Template when an existing object is intended as a reusable recipe; applying it rehydrates fresh
  Builders.

## How Builder-first construction works

The closure receivers, child values, mutators, and lifecycle callbacks through `POST_TREE` are Builders. `INSTANTIATE`
(phase 40) materializes the whole composition graph, including cyclic relationships. `VALIDATE` and later phases receive
completed DSL Objects. The authoritative design is
[issue #416](https://github.com/klum-dsl/klum-ast/issues/416) and
[ADR 0003](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0003-builder-first-materialization.md).

Standalone root factories still return completed models. `Create.AsBuilder` is valid only during an active root Construction
session; using its result after that lifecycle, or adopting it into another root lifecycle, is rejected. Its `From` operation
accepts `DelegatingScript` recipes but deliberately rejects ordinary Scripts that materialize a completed model.

A source-visible custom factory or converter that returns a DSL Object through a recognizable factory path is projected to
an active-session Builder-producing path automatically. This also applies recursively to Collection and Map values and to
Cluster/alternative delegates. The generated relationship API returns concrete `Foo_DSL.Builder` types and, for batches,
the producer's same outer container with its order, comparator, duplicate behavior, subtype, and map keys intact. Direct
calls to the original factory still return completed root models.

The generated `$klum$asBuilder$...` twins are public synthetic JVM implementation details so generated callers in another
package can link to them; they are not present in `Foo_DSL`, IDE mirrors, or generated public documentation. Opaque source
paths, precompiled model-returning producers without a Builder contract, and regular materializing Scripts are omitted from
the generated relationship API. A matching dynamic call reports a targeted `KlumModelException`; an unrelated unknown name
remains an ordinary `MissingMethodException`. Migrate an opaque producer to an explicit `KlumBuilder<Foo>` contract or move
its source into the schema compilation. The protocol is recorded in
[ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md).

## Schema and lifecycle changes

- Source field initializers and `FieldType.BUILDER` fields exist and run on Builders only.
- Non-relationship, non-transient model fields are final. `FieldType.TRANSIENT` and Java `transient` fields remain mutable.
- Model collections are independent read-only snapshots. Supported declarations are `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`. Other concrete and custom collection declarations fail schema compilation.
- Sorted snapshots retain their comparator. `EnumSet` getters return defensive copies. Simple Values are retained rather than deep-copied.
- Pre-materialization custom phases extend `BuilderVisitingPhaseAction`; post-materialization phases extend `ModelVisitingPhaseAction`. The legacy untyped `VisitingPhaseAction` is deprecated.
- Provisional validation issues raised during Builder phases transfer to the completed model. Each `InstanceValidator` executes at most once per completed model.

The supported generated API is the top-level `Foo_DSL` namespace with nested `Factory`, `Builder`, and
relationship-factory interfaces. The generated `$_RW` classes remain internal implementations and are not a client API.
AnnoDocimal sources are IDE-only mirrors: the Gradle plugin exposes them for completion without compiling or packaging them
as a second definition of the AST-generated interfaces.

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
