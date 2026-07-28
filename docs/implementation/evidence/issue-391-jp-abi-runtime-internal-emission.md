# #391 JP-ABI emitted `runtime.internal` inventory

Base: `24d11b2d2f3ab2c7a01ff5d45718281d6f1111c7` (`origin/master`, 2026-07-28).

JP-ABI-4 base: `146a9e196bcbc25178fc0ac61a5b1cb2bf92485a`
(`origin/master`, merged PR #589, 2026-07-28). This local-only slice completed
its required final validation on this merged base before delivery review.

JP-ABI-5 Cluster rebase base: `aa2668ac9fc704111ec1b03c82414f1152c3d339`
(`origin/master`, merged PR #591, 2026-07-28). This local-only slice rebased
exactly onto that refreshed merged master state and repeats final validation
before delivery review.

JP-ABI-6 omitted-projection base: `7617170107533bc937e9ae784424e9a882b55bb3`
(`codex/jp-abi-5-generated-clusters`, local, 2026-07-28). This local-only
slice started exactly from the completed JP-ABI-5 bridge.

JP-ABI-6 final rebase base: `398f2f933266e491b66524e5cac132646b813eda`
(`origin/master`, merged PR #593, 2026-07-28). The local branch rebased once
exactly onto this merged master state before final validation and delivery review.

This is a local decision record, not an ADR amendment. It inventories framework
types that the DSL transformation places in a Schema Developer's generated
class files. A type is listed only when the generated class has a class,
field, method, superclass, signature, or executable instruction reference to
it; a compiler import or Javadoc string alone is not sufficient.

## Evidence method and limitation

The bytecode evidence uses the existing transformed classes under
`klum-ast/build/test-classes/3.0/GeneratedDslSupportSpec/...`, inspected with
`javap -p -verbose`. The representative `Base`, `Foo`, and `Child` model,
Builder, factory, collection, and Cluster artifacts prove the references shown
below. They were generated at `6311e1db`; `git diff --name-only
6311e1db..24d11b2d -- klum-ast/src/main/java klum-ast-runtime/src/main/java`
is empty, so the relevant compiler/runtime production source is unchanged at
this base.

JP-ABI-1 through JP-ABI-5 migrate the Builder, model-state, Materialization,
Template, breadcrumb, and Cluster entries below to the generated-runtime
bridge. JP-ABI-6 migrates the final omitted-projection entry. Its class-file
owner scanner records the generated bridge owner and rejects the internal
owner; the restored ordinary named-schema acceptance gate parses every emitted
class file's constant pool (including owner and descriptor constants) on the
Groovy 4 and 5 module paths.
This record does not claim a current module-path execution result.

## Remaining direct emitted linkage

None. JP-ABI-6 removes the final inventory entry; the synthetic `methodMissing`
fallback now names the reviewed generated bridge while retaining its existing
unsupported-projection diagnostic. This clears the direct emitted-internal-linkage
prerequisite only. JP-1b retains ownership of restoring and proving its broader
named-schema end-to-end acceptance fixture.

## Migrated generated-runtime linkage

| Previous internal linkage | Current generated-runtime bridge | Scope and retained boundary |
| --- | --- | --- |
| `runtime.internal.InternalKlumBuilder<M>` | `runtime.generated.GeneratedKlumBuilder<M>` | JP-ABI-2 moved the hidden generated Builder superclass and generated mutable hooks. `InternalKlumBuilder` remains the runtime implementation; `KlumBuilder<T>` remains the zero-operation client capability. |
| `InternalKlumBuilder.MaterializationToken` | `runtime.generated.GeneratedMaterializationToken` | JP-ABI-3 retargets synthetic model-constructor descriptors to an opaque, unforgeable token. |
| `InternalKlumBuilder.$requireMaterializationToken`, `$snapshotField`, `$createCompanion`; `runtime.internal.KlumObjectCompanion` | `runtime.generated.GeneratedModelSupport`; `runtime.generated.GeneratedObjectState` | JP-ABI-3 moves emitted model-constructor calls behind role-specific reserved support hooks and changes the private synthetic root field from `$proxy` to `$state`. Internal Model/Template companions retain paths, validation state, identity, and recipe mechanics. |
| `runtime.internal.BoundTemplateHandler<T>` | `runtime.generated.GeneratedTemplateSupport<T>` | JP-ABI-1 retargets the public model-package Template adapter. Template recipe mechanics remain internal. |
| `runtime.internal.BreadCrumbVerbInterceptor`; `runtime.internal.process.BreadcrumbCollector` | `runtime.generated.GeneratedBreadcrumbs` | JP-ABI-4 retargets generated factory/Builder verb registration and collection/Cluster scoped Closure calls. The interceptor and collector retain all construction-path mechanics internally. |
| `runtime.internal.layer3.ClusterModel` | `runtime.generated.GeneratedClusters` | JP-ABI-5 retargets generated `@Cluster` query accessors. Layer 3 reflection and Builder-aware query mechanics remain internal. |
| `runtime.internal.OmittedProjectionSupport` | `runtime.generated.GeneratedOmittedProjectionSupport` | JP-ABI-6 retargets synthetic Builder `methodMissing` fallback diagnostics. Catalog parsing and matching remain internal; the unsupported-projection diagnostic contract is unchanged. |

## Not emitted by the current transform

The following are intentionally excluded from the direct-linkage set:

- `FactoryHelper` and `TemplateManager`: `ProxyMethodBuilder` has helper
  factories for them, but current emission has no call site. `TemplateManager`
  is an implementation dependency of `BoundTemplateHandler`, not a direct
  generated reference.
- `KlumInstanceProxy`, `KlumModelProxy`, `KlumTemplateProxy`, and
  `ConstructionSession`: the compiler uses some names or internal contracts,
  but this source/bytecode audit found no generated descriptor or instruction
  that directly names those types. Do not export them as a precaution.
- `Foo_DSL`, `KlumBuilder`, `KlumFactory`, model markers, and
  `DefaultKlumPhase`: these are existing public generated/runtime ABI, not
  `runtime.internal` leakage. `Foo_DSL` remains in the package of its model
  class.

## JPMS consequence

`com.blackbuild.klum.ast.runtime` exports only `runtime` and
`runtime.validation` generally. Its `internal` and `internal.process`
packages are qualified to compiler/Jackson; `internal.layer3` is qualified
only to compiler. The generated class, however, is defined in an arbitrary
Schema Developer module. The compiler's qualified access therefore cannot
authorize that schema class to link any table entry.

`opens` is not a substitute for type access, and a qualified export cannot
enumerate arbitrary user-owned schema module names. Broadly exporting the
existing internal packages would freeze far more lifecycle, reflection, and
companion API than this inventory needs.

## Maintainer choices

**Confirmed maintainer direction (2026-07-28): Option A.** The shared bridge
is shipped once in the runtime artifact; `Foo_DSL`, `Foo$Builder`, and every
other model-specific generated class remain in the Schema Developer's package.

**Confirmed ABI shape — generated Builder base (2026-07-28):** introduce
`runtime.generated.GeneratedKlumBuilder<M>` as the hidden generated
`Foo$Builder` superclass. It implements the zero-operation `KlumBuilder<M>`
capability and owns only generated construction hooks. It is not a supported
mutable client API; `Foo_DSL.Builder` remains the model-package configuration
surface. Generated-only hooks use the reserved `$klum$` vocabulary.

**Confirmed ABI shape — model state (2026-07-28):** generated model
constructors name an opaque, unforgeable
`runtime.generated.GeneratedMaterializationToken`, and root models store their
opaque state in a private synthetic `runtime.generated.GeneratedObjectState`
field. The field is renamed from the historical `$proxy` to `$state` as part
of this 4.0 recompilation break. Neither bridge type exposes client operations;
the current internal Model and Template companions retain the actual paths,
validation, identity, and recipe mechanics.

**Confirmed ABI shape — templates (2026-07-28):** `Foo_DSL.Template` remains
the model-package public contract, backed by a hidden adapter and the public
generated-runtime bridge. `Template.With(template, body)` is a generic
scoped-body operation and invokes its body unchanged; it must not claim a
Builder delegate. The template-configuration overloads (`Create(Closure)` and
`Create(Map, Closure)`) carry
`@DelegatesTo(value = Foo_DSL.Builder.class, strategy = Closure.DELEGATE_ONLY)`,
so static type checking exposes only the public Builder surface.

**Confirmed ABI shape — bridge granularity (2026-07-28):** use small,
role-specific bridge owners rather than a catch-all `GeneratedSchemaSupport`
facade: `GeneratedKlumBuilder<M>` for Builder lifecycle,
`GeneratedModelSupport` for token/state construction hooks,
`GeneratedTemplateSupport` for Template adapter operations,
`GeneratedBreadcrumbs` for registration and scoped breadcrumbs,
`GeneratedClusters` for Cluster access, and
`GeneratedOmittedProjectionSupport` for the omitted-property fallback. Their
members are public only for generated-bytecode linkage and remain
inventory-gated; generated-only operations use the reserved `$klum$` naming
where applicable.

**Confirmed compatibility policy (2026-07-28):** a Schema Developer adopts a
Klum version in the schema project and recompiles that schema; its published
schema artifact brings the corresponding runtime transitively to clients.
Consequently `runtime.generated` is public for module-safe generated linkage,
not a promise that bytecode generated by one Klum release runs unchanged
against a different runtime release. The compatibility guarantee is source
compatibility of schema projects on upgrade (except documented major-version
migrations); the 4.0 migration already requires recompilation. Internal
delegates and hidden model-package implementation classes remain replaceable.

**Confirmed generated-only boundary (2026-07-28):** every
`runtime.generated` type is documented as generated-code linkage rather than
supported client API. Opaque types and reserved `$klum$` names make
non-domain operations conspicuous. There is no mechanical ban on handwritten
use, because generated and handwritten JVM bytecode are indistinguishable;
such use is unsupported. The tracer enforces the producer boundary by
allowing generated descriptors and instructions to name only the reviewed
bridge owners, never `runtime.internal...`.

**Confirmed Template projection (2026-07-28):** `Foo_DSL.Template` projects
the full existing Template capability rather than narrowing it: `With`,
`WithAll`, every `Create` overload, and every file/URL `CreateFrom` overload
(with or without `ClassLoader`). A hidden model-package adapter delegates each
operation to `GeneratedTemplateSupport`. Only the `Create` overloads with a
configuration closure carry the public `Foo_DSL.Builder` `@DelegatesTo`
annotation; scoped `With`/`WithAll` bodies retain their generic, unchanged
closure semantics.

**Confirmed JPMS posture (2026-07-28):** the runtime module adds exactly one
unqualified export, `com.blackbuild.klum.ast.runtime.generated`. It adds no
`opens`, qualified schema-module exports, consumer descriptor edits, or
`--add-exports`, `--add-opens`, or `--add-reads` launch flags. Existing
qualified exports of internal packages remain unchanged.

**Confirmed tracer lanes (2026-07-28):** Groovy 4 and Groovy 5 each compile
and run an independently built named schema and consumer without flags or
descriptor rewrites, exercising Builder, Template, Cluster, breadcrumbs, and
omitted projection. Groovy 3 retains ordinary classpath coverage and asserts
the documented named-module limitation with its classpath-only remediation.
Every lane uses the class-file emitted-owner scanner to reject
`runtime.internal...` references.

### A — recommended: one generated-linkage bridge package

Add an exported `com.blackbuild.klum.ast.runtime.generated` package inside the
existing runtime module. It contains only inventory-backed bridge types: a
generated Builder base, opaque materialization/object-state descriptors,
static construction-path/Cluster/omitted-projection helpers, and a Template
facade. Generated classes name this package and existing public schema/runtime
types only; it delegates to the retained internals.

This lets Groovy 4/5 schemas link directly to their adopted, matching runtime
release without making that boundary a client mutation API or a third-party
SPI. The schema remains the version driver and is recompiled on a Klum
upgrade. The design preserves `Foo_DSL` and its Builder interfaces alongside
each model class.

### B — same policy, different packaging: use the existing runtime root

Place the same minimal bridge types in `com.blackbuild.klum.ast.runtime` rather
than a focused subpackage. This avoids an additional export but mixes
generated-only linkage with client entrypoints such as `KlumObjectSupport` and
`KlumBuilder`. It is viable only if every bridge member remains explicitly
generated-only and inventory-gated.

### C — rejected: export existing internal packages or ask schemas for flags

This either freezes broad accidental APIs or relies on non-portable launch
configuration. It conflicts with #391's positive export decision and fails the
user-owned-module requirement.

### D — rejected: preserve no direct schema/runtime link

Moving generated classes into a framework-owned module or artifact would alter
the Schema Developer's package/module ownership and the fixed artifact set.
Reflection/erased `Object` workarounds would weaken the Builder-first and
Materialization boundaries without eliminating the required runtime authority.

## Tracer acceptance plan

1. **ABI scanner:** transform keyed/unkeyed, inherited, collection/Cluster,
   Template, and omitted-projection schemas. Parse their class files, not raw
   strings; fail on a class, field, method, signature, or instruction owner in
   `runtime.internal...`. Keep a reviewed allowlist for public runtime,
   annotations, Groovy, JDK, and schema-owned names.
2. **Bridge preservation:** retain one root Construction session; verify
   pre-INSTANTIATE Builder mutation, Materialization at phase 40, completed
   object state/path access, Template recipe behavior, and omitted-projection
   diagnostics. The test must prove `KlumBuilder<T>` remains zero-operation and
   generated interfaces remain model-package types.
3. **Descriptor hygiene:** assert `Foo.Template` no longer exposes an internal
   type and that generated public interfaces contain no hidden implementation
   type. Assert module descriptors expose only the chosen bridge package; all
   existing internal exports stay qualified.
4. **Named Schema Developer fixture:** compile and run a Groovy 4 and Groovy 5
   named schema and consumer with no `--add-exports`, `--add-opens`,
   `--add-reads`, patch module, or descriptor rewrite. Exercise root/Builder
   factories, collection/Cluster, Template, and the omitted-projection path.
5. **Groovy 3 boundary:** retain ordinary classpath evidence in all three lanes
   and prove the Groovy 3 named-module attempt reports the documented
   classpath-only remediation.
