# #391 JP-ABI generated-schema runtime inventory

This local evidence inventory separates the stable, client-visible generated
schema contract from runtime symbols embedded in generated schema bytecode. It
does not decide the package move or module descriptor; it supplies the ABI
input for that decision.

## Public generated-schema contract

| Generated contract | Runtime ABI it names | Evidence and compatibility implication |
| --- | --- | --- |
| `Foo_DSL`, `Foo_DSL.Factory`, `Foo_DSL.Builder<SELF extends Foo>`, and the per-field collection/cluster factory interfaces | `KlumBuilder<SELF>` | `GeneratedDslSupport` creates the public namespace and links hidden implementations to it. A root Builder extends the zero-operation `KlumBuilder`; child Builders extend their parent public Builder while retaining the leaf `SELF` type. This is the stable generated-client type system, not the hidden Builder hierarchy. [Generator](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java), [runtime capability](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumBuilder.java). |
| `public static final Foo.Create` | Field: `Foo_DSL.Factory`; Factory method: `KlumFactory<T>` plus `BuilderFactory`, `KeyedBuilderFactory`, and `UnkeyedBuilderFactory` | The transform creates the hidden `KlumFactory` implementation then retargets `Create` to the public factory interface. `Foo_DSL.Factory.getAsBuilder()` is specialized to a public `KlumFactory.*BuilderFactory<Foo, Foo_DSL.Builder<Foo>>` descriptor (Groovy `AsBuilder`). These descriptors are required by generated Java and static-Groovy consumers. [Transform](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java), [factory API](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/KlumFactory.java). |
| Model marker interface and `applyLater(DefaultKlumPhase, Closure)` overload | `KlumModelObject`, `KlumKeyedModelObject`, `KlumUnkeyedModelObject`, `DefaultKlumPhase` | The transform implements exactly one model marker based on keyed/abstract state and emits the phase overload. These types are part of emitted model/Builder descriptors and must remain in the runtime's exported generated-hook surface. [Transform](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java). |

`GeneratedDslSupportSpec` compiles Java and `@CompileStatic` Groovy consumers
using `Foo_DSL`, `KlumFactory`, and `KlumBuilder`, and asserts that the tested
Builder/factory public signatures contain no `$_` implementation spelling. It
is executable evidence for those public contracts, not an exhaustive audit of
every generated schema member. [Test](../../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupportSpec.groovy).

## Runtime linkage embedded in emitted schema classes

The following symbols are not a third-party extension API, but generated class
files directly name or invoke them. A named schema module must be able to link
them; the compiler's access to them is not enough.

| Emitted use | Current runtime symbol(s) | Why it is ABI-relevant |
| --- | --- | --- |
| Hidden `Foo$Builder` superclass and generated mutable DSL methods | `runtime.generated.GeneratedKlumBuilder<SELF>` | The hidden Builder extends the generated-runtime bridge. Its emitted hooks retain the reviewed Builder lifecycle surface while `InternalKlumBuilder` stays an implementation type and `KlumBuilder<T>` remains zero-operation for clients. [Generated hierarchy](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java), [proxy generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/ProxyMethodBuilder.java), [bridge](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/generated/GeneratedKlumBuilder.java). |
| Protected synthetic model constructors; root-model state | `GeneratedMaterializationToken`, `GeneratedModelSupport`, and `GeneratedObjectState` in `runtime.generated` | The transform emits the opaque token in synthetic constructor descriptors, calls role-specific reserved model-support hooks, and declares a private synthetic `$state` field with the opaque state descriptor. Runtime-internal Model/Template companions retain the actual path, validation, identity, and recipe mechanics. [Model generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/DSLASTTransformation.java). |
| Public `Foo.Template` field (current implementation) and template methods | `runtime.internal.BoundTemplateHandler<T>` | `Foo.Template` is currently a public static field whose declared type is `BoundTemplateHandler<T>`. This is both a public descriptor leak and generated execution linkage, despite ADR 0005 reserving a future public `Foo_DSL.Template` contract. `TemplateManager` is an implementation dependency of that handler, not a direct emitted-schema reference. [Template generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/TemplateMethods.java), [handler](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/BoundTemplateHandler.java), [ADR 0005](../../adr/0005-generated-dsl-support-api.md). |
| Generated collection/cluster factory methods | `runtime.internal.process.BreadcrumbCollector` | Collection/cluster closure methods call `BreadcrumbCollector.withBreadcrumb(...)`. `FactoryHelper` is not listed: its `ProxyMethodBuilder` helper has no current generated call site, so it is runtime implementation rather than proven direct schema-bytecode linkage. [Collection generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/AlternativesClassBuilder.java), [cluster generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/layer3/ClusterFactoryBuilder.java), [proxy builder](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/ProxyMethodBuilder.java). |
| `@Cluster` model methods and omitted Builder-projection fallback | `runtime.internal.layer3.ClusterModel`, `runtime.internal.OmittedProjectionSupport` | Generated method bodies call `ClusterModel` query methods and synthetic `methodMissing` calls `OmittedProjectionSupport.handle`. [Cluster generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/layer3/ClusterTransformation.java), [fallback generation](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/OmittedProjectionCatalog.java). |

## JPMS decision constraint

The runtime descriptor exports `runtime`, `runtime.generated`, and
`runtime.validation` to all consumers. Its `runtime.internal` and `.internal.process` packages are
qualified only to the compiler and Jackson modules; `.internal.layer3` is
qualified only to the compiler module. [Runtime descriptor](../../../klum-ast-runtime/src/main/module-info/module-info.java).

Consequently, those qualified exports do not authorize generated bytecode
defined in an arbitrary named schema module. The approved
`runtime.generated` bridge carries migrated generated linkage without broadly
exporting the current `internal` packages or adding flags. The remaining direct
internal references—Template, breadcrumbs, Cluster, and omitted projection—must
migrate through their separately approved bridges before the Groovy 4/5
named-schema fixture can pass. The generated-linkage package is a tightly scoped
ABI for compiler-emitted code—not a general SPI or client extension surface.
[ADR 0014](../../adr/0014-groovy4-jpms-boundary.md), [#468 export handoff](../issue-468-jvm-public-inventory.md).

## Compatibility baseline

The public generated interfaces and their runtime descriptors are 4.x API
after the intentional 3.x-to-4.0 recompilation break. The internal-linkage
set is also binary-significant for independently compiled schemas once named
schemas are supported; it must receive an explicit owner, export disposition,
and generated Java/static-Groovy plus Groovy 4/5 named-module fixture coverage.
The AnnoDocimal `Foo_DSL` mirror is only IDE metadata and is not alternate ABI
evidence. [ADR 0005](../../adr/0005-generated-dsl-support-api.md), [ADR 0014](../../adr/0014-groovy4-jpms-boundary.md).
