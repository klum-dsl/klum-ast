# Builder-First Construction Migration

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

## Migration Checklist

### 1. Compile the Schema

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

### 2. Compile and Run a Representative Model

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
| A Template is rejected as a relationship value | Do not assign a marked Template, including to `LINK`. Rehydrate it through `Template.With`, `copyFrom`, or another Template/copy API. |
| `copyFrom` rejects a sealed or cross-session Builder | Use a completed model for a value-only copy, a marked Template for value-plus-recipe replay, or an unsealed `Create.AsBuilder` result from the same active Construction session. |
| `applyLater` rejects phase 40 or later | Schedule Builder mutation below `INSTANTIATE`, or move completed-model work into a `ModelVisitingPhaseAction`. |
| Jackson rejects a marked Template | Materialize a fresh ordinary model through a Template/copy API and serialize that model. JSON cannot preserve Template recipe actions. |
| Jackson rejects a `LINK` value or inline object | For import, configure identity/reference handling, a converter, or lifecycle resolution; inline input never becomes owned composition. For export, choose an explicit id, omission, scalar, custom, or deliberate inline projection. |
| A generated `apply` method is missing on a completed model | Move the changes into the original `Create.With` callback, a Template, or another factory input. |
| Completed-model proxy access fails | Stop calling `KlumInstanceProxy.getProxyFor(model)`; use `KlumObjectSupport.of(model)` and its supported completed-object utilities. Use `getConstructionPath()` for the Builder/factory invocation path and `getModelPath()` for the object's structural location. |

### 3. Run the Full Model Test Suite

Pay particular attention to lifecycle callbacks, validation, ownership and construction paths, sorted collection comparators,
Templates, serialization, and Jackson inputs. These areas intentionally distinguish between the construction-time Builder
graph and the completed model graph.

## Detailed Migration Rules

- Replace direct construction with generated factories. Completed DSL Objects are not client-constructed.
- Move post-construction `model.apply { ... }` calls into the original `Create.With { ... }` callback, a Template, or another
  factory input. Completed models expose no generated `apply` method.
- Stop calling `KlumInstanceProxy.getProxyFor(model)`. The deprecated compatibility adapter accepts Builders only. Completed
  model technical state belongs to the Model companion and supported public utilities.
- `KlumRwObject` and `$_RW` are removed in 4.0. Name generated `Foo_DSL.Builder<Foo>` interfaces and use
  `@DelegatesToBuilder`; an inherited `Child_DSL.Builder<Child>` remains a `Parent_DSL.Builder<Child>` and has the one
  `KlumBuilder<Child>` capability required by Builder-producing APIs. The legacy `@DelegatesToRW` annotation remains a
  deprecated source alias. Builders do not expose
  the former `getDSLInstance()` or `getRwInstance()` identity aliases.
- Build all owned children through the parent Builder lifecycle. Do not call `Child.Create.With` directly from inside a
  parent construction callback: that would start a second lifecycle, which is forbidden. Use the generated child method on
  the parent Builder.
- Pass an existing completed DSL Object only to a `FieldType.LINK` relationship. Completed objects cannot become newly owned
  composition. Use a Template when an existing object is intended as a reusable recipe; applying it rehydrates fresh
  Builders.
- Treat copy sources by identity: ordinary models and Maps are value-only, marked Templates add immutable recipe replay,
  and only same-session unsealed Builders add an ephemeral snapshot of pending actions. Never use a sealed or cross-session
  Builder as a live recipe.
- Keep deferred Builder actions below phase 40. `applyLater` and `scheduleApplyLater` now fail immediately at
  `INSTANTIATE` or later, including during Template replay.

## Implementation Details

The migration checklist above is sufficient for ordinary Schema and Model work. For the Builder/materialization boundary,
generated producer projection, lifecycle state, and the public-versus-synthetic generated API, see
[[Behind the Curtain#builder-first-materialization]].

## Templates, Serialization, and Jackson

Templates remain client-facing DSL Object recipes. Every owned node created in Template mode has persistent Template
identity, while pre-existing ordinary `LINK` targets retain their identity. Every application copies the recipe into a
fresh ordinary Builder graph, so ownership, paths, lifecycle callbacks, and validation belong to the recipient construction.

A Template `applyLater` recipe must address the fresh Builder through its closure delegate. Capturing a Builder in a local
variable or holder is rejected because a completed recipe must not retain construction state. The complete graph of other
captured recipe values must be serializable so the detached recipe remains serializable. Java serialization preserves the
Template companion and immutable recipe state, but never Builders, Construction sessions, scopes, or mutable recipe
collections. Ordinary completed models retain no deferred actions.

The internal generated `$proxy` field uses a sealed common Model/Template companion solely for cross-package generated
linkage. It is not client API. Use `KlumObjectSupport.of(object)` for supported completed-object paths, structure, and
stored validation; do not build integrations on companion classes or raw metadata. The supported construction-string
getter is `getConstructionPath()`; see [#390](https://github.com/klum-dsl/klum-ast/issues/390) and
[ADR 0006](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0006-completed-object-support.md).

`getValidation().getSubtreeResults()` returns the target's result and every stored result in its owned subtree, including
clean results without issues.
This is broader than the old `Validator.getValidationResultsFromStructure` and `verifyStructure` list contract; their
deprecated adapters now return the facade's complete stored-result list.

Jackson import now binds externally owned data to public Builder configuration through resolved property metadata. Missing input preserves source
initializers and later defaults; present values, `null`, and containers replace current Builder state authoritatively between
`PostCreate` and `PostApply`. Derived output can be exposed as Jackson read-only output and is recomputed by the single
normal lifecycle rather than rebound.

Rename migrated JSON with `@JsonAlias` while keeping the new `@JsonProperty` name canonical. Configured naming strategies,
mixins, ignore/access rules, and unknown-property policy are resolved by Jackson. Ambient Templates, `@Overwrite`, and
`copyFrom` no longer affect JSON input. See [[Jackson Integration]] and
[ADR 0009](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0009-jackson-interoperability.md).

`LINK` import requires an explicit reference schema, property conversion, or lifecycle resolution. Put
`@JsonIdentityInfo` on the target type and `@JsonIdentityReference(alwaysAsId = true)` on each `LINK` for standard Jackson
identity handling. Backward and forward ids resolve against completed targets or Builders allocated in the same import
session. Inline objects are not accepted as `LINK` input. Output must choose its own explicit representation and may use a
custom deliberate inline serializer when the external format requires it.

Marked Templates are rejected as JSON values so recipe actions cannot be silently lost; rehydrate a fresh ordinary model
before serialization. Jackson views, formats, inclusion, Simple Value codecs, mixins, and polymorphic owned types remain
supported, but creator, model-setter, foreign Jackson Builder, owned completed-model deserializer, and managed/back-reference
annotations cannot replace the Klum Builder lifecycle.

Do not treat JSON/YAML output as a Klum persistence or round-trip format. Completed models serialize through ordinary
Jackson APIs, KlumAST adds no wire metadata, and external version properties remain Schema-controlled data.
Each importer operation accepts one input and owns one lifecycle; YAML documents and exported projections never establish
Jackson-owned composition or cross-input overwrite semantics.
