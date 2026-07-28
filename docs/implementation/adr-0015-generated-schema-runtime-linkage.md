# ADR 0015 implementation plan: generated-schema runtime linkage

This plan implements proposed [ADR 0015](../adr/0015-generated-schema-runtime-linkage.md)
for [#391](https://github.com/klum-dsl/klum-ast/issues/391). It is a prerequisite
for ADR 0014's JP-4 named-schema fixture; it does not reopen the five artifact
identities, introduce a third-party Builder SPI, or make Groovy 3 modular.

## Confirmed failure path

The runtime descriptor exports `runtime` and `runtime.validation` generally,
but confines `runtime.internal`, `.internal.process`, and `.internal.layer3`
to checked compiler/Jackson consumers. The transformation nevertheless emits
schema classes that use those packages. In particular, `Foo$Builder` extends
`InternalKlumBuilder`; generated constructors and private state name its token
and companion; `Foo.Template` names `BoundTemplateHandler`; and generated
cluster, breadcrumb, and omitted-projection code invokes internal helpers.

The compiler has access through its qualified exports, but its generated class
is defined by the Schema Developer's named module. That module cannot link the
internal references. The full source-backed list and exclusions are in
[the JP-ABI inventory](evidence/issue-391-jp-abi-generated-schema-runtime.md).

## Compatibility constraints

- The runtime module retains only its approved public API, validation API, and
  the new minimal generated-runtime package; all other internals remain
  non-exported or qualified only to the compiler/Jackson adapter.
- Generated `Foo_DSL` contracts remain the supported build-time interfaces;
  generated implementation names remain unsupported.
- `KlumBuilder<T>` remains zero-operation. The new generated Builder base is
  framework linkage only, not a client mutation API or Builder-phase SPI.
- `Foo.Template` must not retain a public internal-handler descriptor. A
  generated Template facade is compatible with ADR 0005's conditional
  `Foo_DSL.Template` seam.
- Groovy 3 stays classpath-only. Groovy 4 and 5 must each prove the real module
  boundary. No workaround flags, automatic-module fallback, descriptor rewrite,
  or source-mirror module input is allowed.
- All generated model semantics remain unchanged: one Construction session,
  Builder-first lifecycle, Materialization at phase 40, object-companion
  encapsulation, Template recipe serialization limits, and ordinary Jackson
  interoperability.

## Tracer bullets and commits

### JP-ABI-1 — Freeze the emitted-linkage inventory and bridge vocabulary

Add an automated class-file/AST inventory that records every generated
descriptor and direct call outside schema/JDK/Groovy types. Define
`runtime.generated` with only the roles approved by ADR 0015: generated Builder
base, opaque token/object state, static helpers, and Template facade. The
inventory must fail when generated output names a `runtime.internal...` type.

**Acceptance:** the inventory covers representative keyed/unkeyed DSL Objects,
inheritance, collection/cluster factories, Templates, and omitted projections;
it classifies each bridge member to a direct generated use. No bridge type has
a client-extension or generic mutation API.

**Commit boundary:** commit the inventory and empty/narrow bridge types with
their contract tests. Do not move generated emission in the same commit.

### JP-ABI-2 — Bridge Builder, model-state, and Template descriptors

Move the generated Builder superclass and required construction hooks behind
the generated Builder base. Retarget generated model constructors and private
state to the opaque generated token/object-state descriptors. Replace the
public `Foo.Template` internal-handler field with the generated Template
facade, keeping its internal implementation and existing Template behavior.

**Acceptance:** generated class descriptors contain no `InternalKlumBuilder`,
materialization-token, companion, or `BoundTemplateHandler` internal name;
the existing Builder-first, Template, copy-source, and Java/static-Groovy
generated-interface tests pass unchanged in behavior. `Foo_DSL.Template`, if
introduced, accurately mirrors the emitted facade.

**Commit boundary:** one vertical runtime/transform/test commit. Keep the old
internal implementation delegating to the bridge only where it does not leave
a generated reference behind.

### JP-ABI-3 — Bridge generated helper calls and remove residual leaks

Retarget breadcrumb collection/cluster operations, cluster query calls, and
omitted-projection fallback to the static generated helpers. Re-run the
inventory against representative bytecode and remove every residual emitted
`runtime.internal` reference. Keep `FactoryHelper` and `TemplateManager`
internal unless a new bytecode scan proves a direct generated use.

**Acceptance:** all generated direct calls resolve through `runtime`, schema,
or `runtime.generated` packages; runtime internal packages are neither general
exports nor schema-module requirements. Dynamic diagnostics remain unchanged.

**Commit boundary:** one generated-helper/runtime delegation commit with the
affected relationship, cluster, and projection tests.

### JP-ABI-4 — Prove the named Schema Developer boundary

Extend ADR 0014 JP-4 with a real Groovy 4/5 schema module and a Java consumer
module. Compile and run DSL/mutator, converter, and Layer 3 transformation
activation; root and Builder-producing factories; collection/cluster behavior;
Template creation; and the omitted-projection rejection path. Include a Java
consumer and an `@CompileStatic` Groovy consumer naming `Foo_DSL` interfaces.

The fixture's user-owned descriptor requires the public annotations/runtime
modules and `org.apache.groovy`, opens its DSL package to the runtime, and
requires the compiler only as specified by ADR 0014. It does not name an
internal or generated-runtime module because the bridge package belongs to the
existing runtime module.

**Acceptance:** Groovy 4 and 5 fixtures pass on the module path with no
workaround flags; `jar --describe-module` exposes the expected generated-runtime
package and no broad internal export. The equivalent Groovy 3 named-module
attempt fails with documented classpath-only remediation; classpath fixtures
remain green for Groovy 3, 4, and 5.

**Commit boundary:** fixture harness/minimal schema first; then client,
Template, and adapter assertions as focused commits if the harness remains
independently green.

### JP-ABI-5 — Complete release-facing verification and guidance

Regenerate the #468 public inventory and the JP-ABI scan from published
candidates. Update ADR 0014's implementation record, module descriptor
assertions, Schema-plugin remediation, user migration/module guidance,
Javadocs, and `CHANGES.md` only after the named fixture proves the boundary.

**Acceptance:** release checks reject a generated internal reference; the
documentation explains Groovy 4/5 named use, Groovy 3 classpath-only use, and
the fact that `Foo_DSL` mirrors are IDE metadata rather than module sources.

**Commit boundary:** release inventory/assertions, then user/documentation
closure once the executable evidence is present.

## Test map

| Concern | Existing seam | Required addition |
| --- | --- | --- |
| Generated public interfaces and Java/static-Groovy clients | `GeneratedDslSupportSpec` | Assert bridge-free public descriptors and generated Template facade. |
| Builder/materialization and companion state | `BuilderFirstSpec`, `KlumObjectSupportSpec`, lifecycle tests | Assert bridge-backed constructors/state preserve materialization behavior. |
| Template behavior and serialization boundary | `TemplatesSpec`, copy-source tests | Assert `Foo.Template` names only its facade and keeps recipe behavior. |
| Collection/cluster and omitted projections | relationship/cluster specs, `BuilderProjectionSpec` | Assert static bridge helpers preserve results and diagnostics. |
| JPMS packages and descriptors | `JpmsPackageBoundaryTest`, `JpmsGroovyModuleIdentityTest` | Reject emitted internal references and inspect the one new exported package. |
| Real consumer module | ADR 0014 JP-4 fixture | Run Groovy 4/5 named schemas and the Groovy 3 negative case. |

New executable tests carry `@Issue("391")`; a user-visible module example is
documentary only when JP-ABI-5 publishes it under `docs/user/`.

## Risks and open questions

| Risk or question | Control or decision point |
| --- | --- |
| The minimum bridge grows into a second general runtime API. | JP-ABI-1 admits only inventory-proven emitted symbols and keeps the package's generated-only documentation/review gate. |
| A private or synthetic descriptor is missed. | Scan complete class-file constant pools and descriptors, not only public methods. |
| `Foo_DSL.Template` changes a promised generated shape. | ADR 0005 expressly reserves it for Template-specific methods; JP-ABI-2 proves the real field/facade shape before freezing it. |
| Schema module requirements change unexpectedly. | JP-ABI-4 compiles a user-owned descriptor; plugin work reports the final directives but never writes them. |
| Groovy 3 appears to work through an accidental flag. | Retain the negative named-module fixture and inspect the launched command line. |

## Issue-to-slice mapping

| Issue/decision | Slices |
| --- | --- |
| #391 / ADR 0014 module boundary | JP-ABI-1 through JP-ABI-5; JP-ABI-4 is the JP-4 gate. |
| #394 / ADR 0005 generated support API | JP-ABI-2 Template facade and generated-interface mirror parity. |
| #431 / ADR 0004 Builder-producing protocol | JP-ABI-2 Builder bridge and JP-ABI-4 active-session fixture. |
| #468 public inventory | JP-ABI-1 and JP-ABI-5 descriptor review. |
