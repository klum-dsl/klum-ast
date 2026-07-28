# Generated-schema runtime linkage boundary

Date: 2026-07-28

Status: Proposed

Tracking issue: [#391 — Define Java modules and finalize public packages for 4.0](https://github.com/klum-dsl/klum-ast/issues/391)

Implementation plan: [ADR 0015 implementation plan](../implementation/adr-0015-generated-schema-runtime-linkage.md)

Parent decisions: [ADR 0003 — Builder-first materialization](0003-builder-first-materialization.md),
[ADR 0004 — AsBuilder composition](0004-asbuilder-composition-protocol.md),
[ADR 0005 — generated DSL support API](0005-generated-dsl-support-api.md), and
[ADR 0014 — Groovy 4/5 JPMS boundary](0014-groovy4-jpms-boundary.md).

## Context

ADR 0014 correctly confines ordinary runtime implementation packages and gives
only the compiler and Jackson adapter their checked qualified exports. The
generated-schema ABI inventory now identifies a conflicting fact: some emitted
classes are defined in the Schema Developer's module, not in either of those
KlumAST modules. They directly name or invoke types in
`com.blackbuild.klum.ast.runtime.internal...`.

The principal links are the generated `Foo$Builder` superclass and construction
hooks (`InternalKlumBuilder` and its materialization token), the generated
model companion descriptor, the current public `Foo.Template` handler,
breadcrumb collection/cluster calls, cluster queries, and omitted-projection
fallback. A qualified export to the compiler cannot authorize the JVM linkage
of an arbitrary named schema module. The exact inventory is recorded in
[the #391 JP-ABI evidence](../implementation/evidence/issue-391-jp-abi-generated-schema-runtime.md).

Leaving this unresolved would make the JP-4 named-schema fixture fail, or
would pressure the project to use broad internal exports or consumer JVM flags.
Both outcomes contradict ADR 0014's positive export list and portable Groovy
4/5 module-path contract.

## Decision

### Add one generated-runtime ABI package

The runtime artifact owns a new generally exported package:

```text
com.blackbuild.klum.ast.runtime.generated
```

It is the only runtime package whose purpose is compiler-emitted linkage in a
Schema Developer's module. It is a stable 4.x binary contract after the
intentional 3.x-to-4.0 recompilation break, but is neither a general client API
nor a third-party extension SPI. Its types and members use generated-only
Javadoc and reserved `$klum$` operation names where a generated class must call
an operation directly.

The package contains only these narrow roles:

- a public abstract generated Builder base implementing `KlumBuilder<M>`, with
  the protected/public construction hooks presently emitted against
  `InternalKlumBuilder`;
- an opaque public materialization token and generated object-state descriptor
  required by protected generated constructors and private generated fields;
- static generated helpers for breadcrumb, cluster, and omitted-projection
  operations currently emitted against internal helpers; and
- a public generated Template facade used by `Foo.Template` and implemented by
  an internal handler. `Foo_DSL.Template` becomes the schema-specific public
  type when Template operations must be named by generated/client code.

The exact member set is frozen from the JP-ABI inventory during JP-ABI-1. Each
member must have a generated class-file call site or descriptor; convenience
operations, raw Builder state, lifecycle driver access, reflection, traversal,
and general mutation APIs do not enter this package.

### Retarget emitted bytecode, keep mechanics internal

The compiler generates only public annotations, schema types, existing runtime
API types, and `runtime.generated` types in descriptors and direct calls. It
does not emit a reference to `runtime.internal`, `.internal.process`, or
`.internal.layer3`.

`InternalKlumBuilder`, companions, Template recipes/handlers, collection and
cluster mechanics, and all lifecycle implementation remain internal. They may
implement or be delegated to by the generated-runtime ABI but are not exported
to schema modules. The compiler/Jackson qualified exports in ADR 0014 remain
limited to their own checked implementation linkage.

The schema module continues to require the existing runtime module and open
its DSL packages to it. No schema adds a `requires` clause for an internal
KlumAST package, and the schema plugin never writes a module descriptor.

### Freeze the public generated contracts honestly

`Foo_DSL.Factory`, `Foo_DSL.Builder`, and their collection/cluster factory
interfaces remain the supported build-time interfaces from ADR 0005. Clients
may name but not implement or subclass them. `KlumBuilder<T>` remains the
zero-operation public capability; the generated Builder base is not a mutable
client Builder API.

The current public `Foo.Template` field must no longer expose an internal
handler descriptor. It is retargeted to the generated Template facade, which
is the condition ADR 0005 anticipated for a `Foo_DSL.Template` contract.
Neither this facade nor the generated-runtime package changes root-vs-Builder
construction, Construction-session ownership, Materialization, Template
serialization, or Jackson behavior.

### Prove it in real named schemas

The named-schema fixture required by ADR 0014 JP-4 becomes the acceptance
authority. It compiles and runs a Groovy 4 and Groovy 5 schema module with a
root, relationship/collection or cluster operation, Template, and omitted
projection path; then exercises Java and `@CompileStatic` Groovy consumers.
The fixture must link without `--add-exports`, `--add-opens`, `--add-reads`,
or patched modules. Groovy 3 remains classpath-only and retains its explicit
negative module-path guidance.

## Consequences

- Named schemas use one explicit, minimal framework ABI rather than a hidden
  dependency on the compiler's qualified exports.
- The runtime module gains a small additional export whose compatibility is
  reviewed as generated bytecode ABI; its scope stays distinct from client and
  extension APIs.
- Existing classpath schemas recompile for 4.0 and retain behavior, while
  Groovy 4/5 named schemas gain a portable linkage boundary.
- The compiler must retain an automated inventory that rejects emitted
  `runtime.internal` references before a release candidate.
- Current user migration guidance remains valid; named-module documentation is
  added only after the JP-4 fixture passes.

## Rejected alternatives

**Export all runtime internals.** This would freeze lifecycle, reflection,
companions, and mechanics as public API, directly reversing ADR 0014's
positive export boundary.

**Add each schema module to qualified runtime exports.** Schema module names
are user-owned and unbounded; a published descriptor cannot enumerate them.

**Use consumer JVM flags.** `--add-exports`, `--add-opens`, `--add-reads`, or
patched modules make a local launch work but are not portable Schema Developer
contracts and are already rejected by ADR 0014.

**Treat hidden generated classes as exempt from JPMS.** Access checks apply to
the defining schema module even when the class or member is synthetic or not a
supported client API.

**Keep `Foo.Template` typed to the internal handler.** Its public field
descriptor already leaks an internal name; preserving it would make that leak
the de facto 4.x schema ABI.
