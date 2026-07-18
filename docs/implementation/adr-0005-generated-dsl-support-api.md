# ADR 0005 implementation plan: generated DSL support API

This plan implements [ADR 0005](../adr/0005-generated-dsl-support-api.md) for canonical issue
[#394](https://github.com/klum-dsl/klum-ast/issues/394).

## Current behavior and failure

Before DSL-2, `DSLASTTransformation.createRWClass()` generated inner `Foo.$_RW` classes that extended concrete
`KlumBuilder` and directly declared deprecated `KlumRwObject`. DSL-2 replaces that spelling with the hidden
`Foo$Builder` implementation, makes public `KlumBuilder<T>` zero-operation, and has generated
`Foo_DSL.Builder` extend it. Runtime behavior lives in `InternalKlumBuilder`; `@DelegatesToBuilder` is canonical and
the deprecated `@DelegatesToRW` source alias normalizes to the same generated metadata.

`KlumAstSchemaPlugin` applies `AnnoDocimalPlugin` and enables normal source/Javadoc variants. DSL-G now adds the dedicated
`createKlumDslSourceMirrors` task and IntelliJ-only model wiring described below. A mirror `Foo_DSL` source cannot be placed
in a normal `SourceSet`: the AST transformation emits the real interface with that binary name, so compiling the mirror
would create a duplicate type and could make IDE metadata change production output.

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

**Implemented:** issue #433 provides the generated namespace and linked public Builder/factory types. ADR 0004 AB-2
(#437) now uses that contract for concrete Builder-producing projection signatures and composition-specific AnnoDoc.

### DSL-2 — Capability narrowing and RW migration

Convert `KlumBuilder<T>` to the supported narrow interface, move implementation behavior behind an internal base, remove
`KlumRwObject`/direct generated markers, add canonical `@DelegatesToBuilder`, and normalize deprecated
`@DelegatesToRW`. Compile-fail when both annotations occur. Migrate generated code and tests to Builder vocabulary while
retaining only the promised source alias.

**Confirmed Builder capability refinement (2026-07-18):** `KlumBuilder<T>` is a zero-operation public construction
capability, not a general-purpose mutable Builder API. Every generated `Foo_DSL.Builder` extends
`KlumBuilder<Foo>` (with its declared model generics preserved), and `KlumFactory.BuilderFactory<T, B>` consistently
uses `B extends KlumBuilder<T>`. The generated `Foo_DSL` interfaces remain the only public surface for
schema-specific configuration. Runtime state, lifecycle, reflection, materialization, path, copy, and collection
internals move behind an internal support base. The later dynamic `KlumBuilder.link(fieldName, target)` capability is
owned by #474 and must not broaden DSL-2.

This shares the intentional 4.0 recompilation boundary already required by package migration. `$_RW` and
`KlumRwObject` have no retained source or binary compatibility; `@DelegatesToRW` is the sole deprecated source alias.
After the 4.0 API is delivered, the `KlumBuilder<T>` bound, generated `Foo_DSL` interfaces, and Java
`Create.getAsBuilder()` / static-Groovy `Create.AsBuilder` shapes are 4.x source and binary contracts. Hidden generated
implementations remain non-contractual.

The exact JSON-3 descriptors in #463 depend on this slice: an interface `Foo_DSL.Builder` cannot satisfy
`B extends KlumBuilder<T>` while `KlumBuilder<T>` is a class. DSL-2 now delivers that prerequisite; #463 can prove the
exact Java 17 and static-Groovy consumer shapes at the Jackson 2.14.2 and 2.21.x endpoints.

**Implemented:** `KlumBuilder<T>` is the zero-operation capability, generated Builder interfaces extend it with their
model generic type, and `InternalKlumBuilder` owns runtime behavior. The generated implementation spelling is
`Foo$Builder`; `KlumRwObject` and `$_RW` are removed. `@DelegatesToBuilder` is canonical, `@DelegatesToRW` remains the
deprecated source alias, and combining them is rejected. JSON-3 remains separate importer work.

### DSL-3 — Generated-source distribution and documentation

Exercise the DSL-G Gradle/IDE integration against the actual DSL-1 namespace, verify a same-project schema and Java
consumer fixture, document which interfaces clients may name but not implement, update migration navigation and
`CHANGES.md`, and run Groovy 3/4/5 plus Gradle plugin scenarios. The generated mirror directory must remain absent from
Java/Groovy compilation, source JARs, published variants, and downstream task inputs.

### [DSL-G — IDE-only Gradle source-mirror lifecycle](https://github.com/klum-dsl/klum-ast/issues/434)

DSL-G selects a compiled-contract-to-IDE-mirror lifecycle:

1. `compileGroovy`/`compileJava` produce the real AST-generated `Foo_DSL.class` contract once, without any mirror source on
   their source path or classpath.
2. The cacheable `createKlumDslSourceMirrors` task consumes the main classes directories, selects only top-level
   `**/*_DSL.class` files, and invokes AnnoDocimal's `AnnoDocGenerator` into
   `build/generated/sources/klum-dsl-ide/main`. It clears its output before generation so removed namespaces cannot leave
   stale mirrors.
3. `KlumAstSchemaPlugin` applies Gradle's built-in `idea` plugin and adds that output directory only to the IDEA module's
   source and generated-source directory sets. Developers run `createKlumDslSourceMirrors` explicitly after schema
   changes. A clean import registers the generated root without compiling the schema; the next explicit refresh populates
   it through the compiler-to-mirror path.
4. The output is deliberately absent from every Gradle `SourceSet`. No compile, classes, test, archive, publication,
   configuration, classpath, or downstream edge points to the mirror task or directory. The ordinary AnnoDocimal Javadoc
   source tree excludes `**/*_DSL.java`, so the generated namespace is not consumed as Javadoc input there either.

The resulting graph is one-way: compile → IDE mirror. There is no mirror → compile edge and therefore no
compile → mirror → compile cycle. A multi-project TestKit fixture pins the clean IDEA metadata, explicit refresh behavior,
AnnoDoc preservation, task ordering, archive/publication/classpath exclusions, downstream isolation, stable output hash,
stale-output cleanup, and build-cache restoration after deleting the mirror directory.

**Implemented:** issue #434 provides this mirror lifecycle; #437 verifies that projected Builder methods appear in the
mirror while synthetic hidden twins and omitted opaque methods do not.

Gradle's configuration cache is not currently an end-to-end supported lane for schema compilation: AnnoDocimal 0.7.1
adds a `GroovyCompile.doFirst` action that captures the Gradle `Project`, which Gradle 8.14.4 reports as configuration-cache
serialization problems. The TestKit fixture records that limitation with `--configuration-cache-problems=warn`. The new
mirror task itself uses declared inputs/outputs and injected filesystem operations; build-cache reuse is proven. Re-test
strict configuration-cache storage when [AnnoDocimal #35](https://github.com/blackbuild/anno-docimal/issues/35) removes the
captured-project compiler action and makes its filtered stub task reusable; Klum can then replace the temporary local task
implementation without changing this lifecycle.

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
| IDE mirrors excluded from compilation and packaging | DSL-G, DSL-3 |
| wiki/migration/CHANGES and all Groovy lanes | DSL-3 |

## Risks

Groovy AST owner/nesting rules, inherited DSL Objects, default parameters, and generic bridge signatures can diverge across
Groovy versions. Stubs must be tested against compiled bytecode to prevent two public truths. No client implementation or
subclassing contract may leak from Java interface visibility.

The Gradle integration is no longer an unresolved adoption risk: DSL-G selects an IDEA-only generated root plus an
explicit, one-way compile → mirror refresh task. The remaining delivery risk is validating that DSL-1's real `Foo_DSL`
bytecode carries the complete nested AnnoDoc surface expected by the mirror generator. Configuration-cache support also
remains gated on AnnoDocimal removing its captured-project `GroovyCompile` action.
