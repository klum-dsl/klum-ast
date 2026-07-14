# ADR 0005 implementation plan: generated DSL support API

This plan implements [ADR 0005](../adr/0005-generated-dsl-support-api.md) for canonical issue
[#394](https://github.com/klum-dsl/klum-ast/issues/394).

## Current behavior and failure

`DSLASTTransformation.createRWClass()` generates inner `Foo.$_RW` classes that extend concrete `KlumBuilder` and directly
declare deprecated `KlumRwObject`. Root and relationship factories are other inner generated classes. AnnoDoc exists in
class metadata, but IntelliJ same-project completion consumes generated sources and cannot receive a separately generated
source stub for an inner class of existing `Foo`. `@DelegatesToRW` and its transformation keep RW vocabulary public.

`KlumAstSchemaPlugin` currently applies `AnnoDocimalPlugin` and enables normal source/Javadoc variants, but it registers no
Klum-specific IDE-mirror task or IDE-only generated source root. A mirror `Foo_DSL` source cannot be placed in a normal
`SourceSet`: the AST transformation already emits the real interface with that binary name, so compiling the mirror would
create a duplicate type and could make IDE metadata change production output.

## Affected seams

- `klum-ast-annotations`: introduce `@DelegatesToBuilder`, deprecate the legacy alias.
- `klum-ast`: generate `Foo_DSL` interfaces/stubs; type generated fields and delegates; link hidden implementations.
- `klum-ast-runtime`: split narrow `KlumBuilder<T>` capability from the support implementation; remove `KlumRwObject`.
- Gradle plugin/AnnoDocimal task: generate IDE mirrors and contribute them to the IDE model without registering compiler,
  archive, classpath, or downstream build inputs.
- IDE/docs: replace RW and GDSL assumptions with the generated-source contract.

## Tracer-bullet slices

### [DSL-1 — One complete generated namespace](https://github.com/klum-dsl/klum-ast/issues/433)

For a representative DSL Object with a root factory, collection relationship, and Cluster factory, generate
`Foo_DSL.Factory`, `Foo_DSL.Builder`, `CollectionFactory_<field>`, and `ClusterFactory_<field>` bytecode interfaces plus
matching IDE source mirrors. Type `Foo.Create` and generated implementations against the real interfaces. Verify Java and
statically compiled Groovy call the full factory-to-child path against bytecode while the uncompiled mirrors provide the
same completion surface. This is the prerequisite for ADR 0004 AB-2.

### DSL-2 — Capability narrowing and RW migration

Convert `KlumBuilder<T>` to the supported narrow interface, move implementation behavior behind an internal base, remove
`KlumRwObject`/direct generated markers, add canonical `@DelegatesToBuilder`, and normalize deprecated
`@DelegatesToRW`. Compile-fail when both annotations occur. Migrate generated code and tests to Builder vocabulary while
retaining only the promised source alias.

### DSL-3 — Generated-source distribution and documentation

Deliver the selected Gradle/IDE integration from DSL-G, verify a same-project schema and Java consumer fixture, document
which interfaces clients may name but not implement, update migration navigation and `CHANGES.md`, and run Groovy 3/4/5
plus Gradle plugin scenarios. The generated mirror directory must remain absent from Java/Groovy compilation, source JARs,
published variants, and downstream task inputs.

### [DSL-G — Prove an IDE-only Gradle source-mirror lifecycle](https://github.com/klum-dsl/klum-ast/issues/434)

Prototype and choose how the schema plugin generates AnnoDocimal `Foo_DSL` mirrors and exposes them during Gradle IDE
import without adding them to a production `SourceSet`. The mechanism may use a dedicated task plus IDE model wiring or
another Gradle-supported metadata seam; it must not rely on a manually run build. Record the chosen task graph/model seam
in this plan or a successor ADR before DSL-3 implements it.

The prototype must prove:

- a clean IDE import sees the mirror source and documentation;
- `compileGroovy`/`compileJava` never receive the mirror directory and produce the real interface only once;
- `classes`, tests, source JARs, publication variants, and downstream projects do not consume the mirror;
- repeated builds, configuration cache/build cache where supported, and deletion/regeneration are deterministic;
- no compile → mirror → compile task cycle is introduced.

## Compatibility

The `@DelegatesToRW` source alias is the only retained RW compatibility. Explicit references to `$_RW`, implementations of
generated interfaces, and `KlumRwObject` are unsupported 4.0 breaks. Generated implementation spelling remains internal.

## Acceptance map

| ADR contract | Slice |
|---|---|
| `Foo_DSL` namespace and all visible factory interfaces | DSL-1 |
| truthful Java/Groovy signatures and hidden implementation linkage | DSL-1 |
| narrow `KlumBuilder`, remove RW marker, annotation migration | DSL-2 |
| IntelliJ generated-source completion and AnnoDoc | DSL-3 |
| IDE-only Gradle generation without compilation or packaging | DSL-G, DSL-3 |
| wiki/migration/CHANGES and all Groovy lanes | DSL-3 |

## Risks

Groovy AST owner/nesting rules, inherited DSL Objects, default parameters, and generic bridge signatures can diverge across
Groovy versions. Stubs must be tested against compiled bytecode to prevent two public truths. No client implementation or
subclassing contract may leak from Java interface visibility.

The Gradle integration is the principal unresolved adoption risk. Standard generated-source registration usually makes a
directory a compile input, which is forbidden here. Generating mirrors from compiled bytecode may also create an IDE-sync
or task-cycle problem. DSL-G must resolve that mechanism before ADR 0005 can be considered implementable end to end.
