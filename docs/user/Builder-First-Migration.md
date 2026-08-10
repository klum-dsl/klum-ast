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
| A manual configurator shadows a field in a factory map | Map keys intentionally call the same-named Builder method before considering storage. A non-void override can be mistaken for an ordinary helper. | A `void` `@Mutator` remains silent. A return of the field value or its Builder receives a warning; rename the helper if that is not intended. Use `setX` in the map for direct field assignment. An incompatible non-void return is a compilation error. |
| A statically checked Builder lifecycle method sees an ordinary collection or map value as `Object` | An earlier 4.0 release candidate emitted a raw Builder accessor for simple collection and map fields. | Recompile the Schema with the correction. Declared element and map value types are preserved, so a compensating local generic cast is no longer needed. |
| A polymorphic relationship closure cannot see members of the selected subtype under static compilation | A dynamic `ChildType` Class selector retains the declared base Builder delegate. | Pass the generated factory, for example `child(ConcreteChild.Create) { concreteProperty 'value' }`, to select the exact public `ConcreteChild_DSL.Builder<ConcreteChild>` delegate. |
| `instanceof SomeDslModel` is rejected in a Builder-phase callback | The relationship value is a Builder before materialization, not the completed DSL Object. The diagnostic names the inferred Builder type when available. | Do not use a completed-model type check in a mutator, mutating lifecycle method, or Builder-retargeted annotation closure. Move a completed-model invariant to `@Validate`; ordinary checks and operands known only as `Object` remain valid. |
| `Child.Create.With`, `One`, or `From` is rejected in Builder-phase code | That call starts an independent root lifecycle and returns a completed model, which cannot become owned composition in the active Builder graph. | Use `Child.Create.AsBuilder.With`, `One`, or `From`, then attach the returned Builder to an owned relationship. Root factories remain valid in `@Validate` and ordinary static source factory methods. |
| A public static method declared on a custom `Factory` is rejected | Public `Factory` methods become root operations on `Create`, which delegates to a Factory instance. | Remove `static`. Move model-level static converters out of `Factory`; they remain model methods. Non-public static Factory helpers remain valid. |
| A member beginning with `$klum$` is rejected | The namespace is reserved for generated implementation members. | Rename the source member. |
| A custom creator or converter is absent from `Foo_DSL` or its IDE mirror | Its model-producing path is opaque or precompiled, so KlumAST cannot safely adapt it to the active session. Source-visible recursive calls, including unqualified static calls to same-source converters, are projected. | Use the generated child method, return an explicit `KlumBuilder<Foo>`, or compile the producer source together with the schema. |

### Public Builder Contracts

Generated accessors always expose `Foo_DSL.Builder<Foo>`, never the hidden `Foo$Builder` implementation. This also
applies when two source files compile together and an `@Owner(root = true)` target has not yet been resolved. (See:
`GeneratedDslSupportSpec#'projects an unresolved cross-source owner Builder into the public namespace'`.)

For same-project IntelliJ source completion, refresh the IDEA-only `Foo_DSL` mirrors through the Schema plugin and reload
the Gradle project. The refresh materializes one root-owned GDSL resource directory and registers it with every Schema
module; the packaged contributor then supplies source-level static `Foo.Create` as `Foo_DSL.Factory` and `Foo.Template`
as `Foo_DSL.Template`, without exposing hidden implementation classes. The capitalized members use the version-sensitive
internal GDSL bridge described in [Gradle Onboarding](Gradle-Onboarding.md#intellij-and-generated-dsl-support); it does
not add bytecode or independent read-only semantics. This is IDE metadata only: neither the GDSL root nor mirrors become
compiler, package, or downstream inputs.

```groovy
// Child.groovy
@DSL
class Child {
    @Owner(root = true) Root root
}

// Root.groovy
@DSL
class Root {}
```

In generated signatures and IDE source mirrors, `Child_DSL.Builder` uses `Root_DSL.Builder<Root>` for `root`.

### Builder-phase Type Checks

Relationships are Builders until the graph materializes. A completed-model `instanceof` check therefore belongs in a
completed-model validation callback rather than in construction-time code. (See:
`BuilderFirstMigrationDocumentaryTest#'keeps completed-model type checks in validation'`.)

```groovy
@Validate
void validateCompletedService() {
    assert service instanceof Service
}
```

### Builder-phase Factories

`Create.With`, `Create.One`, and `Create.From` are root factories: they return a completed model and own a complete
Construction session. In a mutator, mutating lifecycle method, or Builder-retargeted annotation closure, create the
owned child in the active session instead:

```groovy
@Mutator
void supplySource() {
    source = ProductSource.Create.AsBuilder.With(name: 'default source')
}
```

The returned Builder must be attached to an owned relationship. Do not use `Create.AsBuilder` for an independent root
model; use the ordinary root factory outside Builder-phase code instead.

### Factory Methods Exposed Through `Create`

Public methods declared on a custom `Factory` are exposed through `Create`, so they must be instance methods. Keep
`Factory` itself `static`; this rule applies only to its public methods. Private, protected, and package-private static
helpers remain internal to the Factory. Model-level static converters remain outside the Factory contract.

(See: `BuilderFirstMigrationDocumentaryTest#'exposes an instance Factory method through Create'`.)

```groovy
@DSL
class ServicePlan {
    String name

    static class Factory extends KlumFactory.Unkeyed<ServicePlan> {
        protected Factory() { super(ServicePlan) }

        ServicePlan standard(String name) {
            Create.With(name: name)
        }
    }
}

assert ServicePlan.Create.standard('catalog').name == 'catalog'
```

### Map Configurator Overrides

Factory maps preserve method-first configuration. When a Builder has both a writable `outboxUrl` field and an explicit
`outboxUrl(String)` mutator, `Create.With(outboxUrl: value)` calls the mutator. This makes intentional overrides work
consistently across `Create.With`, `Create.AsBuilder.With`, Templates, and automatic creation.

```groovy
@DSL
class Mailbox {
    String outboxUrl

    @Mutator
    String outboxUrl(String value) {
        // Normalize, validate, or coordinate related Builder state.
        value
    }
}

Mailbox.Create.With(outboxUrl: 'https://example.invalid')
Mailbox.Create.With(setOutboxUrl: 'https://example.invalid') // explicit direct field assignment
```

KlumAST warns when this exact one-argument `@Mutator` override returns the field value or its Builder, because map
configuration will choose the method. A `void` mutator is unambiguous and remains silent. A non-void return unrelated
to the field or its Builder is rejected at the mutator declaration; rename it or make it setter-like. Methods without a
same-named writable field retain their existing map-method fallback without a diagnostic.

### 2. Compile and Run a Representative Model

Compile and execute at least one real root configuration. A unit test that calls `Config.Create.With` is usually the
simplest repeatable migration check; an existing root script is equally suitable. A project-less script can also obtain
KlumAST with `@Grab`, but the complete standalone-script setup will be documented separately.

Build owned children through the generated method on that root Builder, so the entire configuration shares one lifecycle.
(See: `BuilderFirstMigrationDocumentaryTest#'builds a representative deployment through one Builder lifecycle'`.)

```groovy
def deployment = Deployment.Create.With {
    environment 'production'
    service {
        image 'catalog:1.0'
    }
}

assert deployment.service.image == 'catalog:1.0'
```

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

The generated `Foo_DSL.Builder<Foo>` interface now types `copyFrom` for an active Builder of the same model. In a
`@Default`, `@AutoCreate`, or `Create.AsBuilder.With` callback, use that public Builder type instead of suppressing
static checking. Runtime still rejects sealed and cross-session Builder sources.

### 3. Run the Full Model Test Suite

Pay particular attention to lifecycle callbacks, validation, ownership and construction paths, sorted collection comparators,
Templates, serialization, and Jackson inputs. These areas intentionally distinguish between the construction-time Builder
graph and the completed model graph.

## Optional Mechanical Starting Point

The [**best-effort convenience script**](assets/migrate-3x-to-4x-builder-first.sh) performs only a small set of mechanical
3.x-to-4.0 edits. It is not a
KlumAST migration tool: it does not promise a compiling project, understand application semantics, or replace the
checklist below. Run it only from the **schema module directory**—the Gradle subproject containing the Schema's
`src/main` and `src/test` sources—not from a multi-module project's top-level directory. That module must be in a
**clean, disposable Git worktree**. Review its diff immediately, then compile, run a representative model, and fix the
remaining compiler errors with this guide.

Use this order: update the schema module to the target KlumAST version, run the script, inspect the diff, then commit it
as a deliberate migration starting point or revert it. Continue with the [Template migration guidance](Migration.md#template-creation-and-scoped-application), this checklist, and compilation. The
canonical creation/application example is executable in
[`TemplatesDocumentaryTest#'applies one scoped template to multiple service configurations'`](../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TemplatesDocumentaryTest.groovy).

It rewrites public schema-annotation imports, changes the deprecated `@DelegatesToRW` spelling to
`@DelegatesToBuilder`, migrates Layer 3 annotation imports such as `@AutoCreate`, copy annotations such as `@Overwrite`,
and `KlumModelException`/`KlumValidationException` imports. It also converts only current-target Groovy `Validator`
reporting/suppression calls and direct type-qualified Template creation calls:

| Established spelling | Mechanical starting point |
| --- | --- |
| `Foo.Create.Template(...)` | `Foo.Create.Template.With(...)` |
| `Foo.Create.TemplateFrom(source)` | `Foo.Create.Template.From(source)` |
| `Foo.Template.Create(...)` | `Foo.Create.Template.With(...)` |
| `Foo.Template.CreateFrom(source)` | `Foo.Create.Template.From(source)` |

The Template rewrites are intentionally narrow. The script leaves `Foo.Template.With` and `WithAll` scoped application,
variables such as `type.Template.Create`, dynamic/custom calls such as `holder.Service.Template.Create`, method
references, comments, and quoted/script content unchanged. It recognizes imported direct model names (and direct nested
model names), not fully qualified types or expression/property chains. It preserves the argument text of a recognized
direct call, but does not promise that the result compiles.

It intentionally does **not** rewrite internal packages, `ValidatorBase`, validation result readers, explicit-target
`Validator` calls, fully qualified type references, Builders, factories, relationships, lifecycle structure, or any other
semantic migration.

Do not broaden this script by guessing package destinations or rewriting unresolved compiler errors. The compilation and
model checks are the handoff from mechanical edits to the actual migration.

## Builder Lifecycle Field Types

Builder lifecycle code preserves ordinary declared collection and map element types. A statically checked `@Default` or
`@AutoCreate` method can use the inferred type directly; no source-side cast is required:

(See: `ModelPhasesDocumentaryTest#'preserves simple collection types in Builder lifecycle code'`.)

```groovy
@DSL
class CustomerRoster {
    List<String> customers = ['central:primary']
    String primaryCustomer

    @Default
    @CompileStatic
    void selectPrimaryCustomer() {
        customers.each { String customer ->
            primaryCustomer = customer.split(':', 2)[0]
        }
    }
}
```

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
  the parent Builder. For polymorphic relationships, `child(ConcreteChild)` remains the dynamic Class-selection form;
  `child(ConcreteChild.Create)` provides an exact static Builder delegate. Passing `Create` here is contextual selection in
  the current session, not a root factory invocation.
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
[Behind the Curtain#builder-first-materialization](Behind-the-Curtain.md#builder-first-materialization).

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
Use `KlumObjectSupport.getValidation()` directly; the preliminary `Validator` result readers are removed in 4.0 rather
than retained as deprecated adapters.

Jackson import now binds externally owned data to public Builder configuration through resolved property metadata. Missing input preserves source
initializers and later defaults; present values, `null`, and containers replace current Builder state authoritatively between
`PostCreate` and `PostApply`. Derived output can be exposed as Jackson read-only output and is recomputed by the single
normal lifecycle rather than rebound.

Rename migrated JSON with `@JsonAlias` while keeping the new `@JsonProperty` name canonical. Configured naming strategies,
mixins, ignore/access rules, and unknown-property policy are resolved by Jackson. Ambient Templates, `@Overwrite`, and
`copyFrom` no longer affect JSON input. See [Jackson Integration](Jackson-Integration.md) and
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
