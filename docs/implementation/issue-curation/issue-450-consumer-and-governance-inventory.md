# Issue 450: KlumAST consumer and repository-governance inventory

Date: 2026-07-16

Tracking issue: [#450 — Audit klum-cast and AnnoDocimal integration opportunities before 4.0](https://github.com/klum-dsl/klum-ast/issues/450)

Related investigations: [#455 — Revisit and align the multi-Groovy build approach across Klum libraries](https://github.com/klum-dsl/klum-ast/issues/455), [#456 — Introduce versioned user documentation and Javadocs](https://github.com/klum-dsl/klum-ast/issues/456)

## Purpose and evidence boundary

This is the factual KlumAST-side input to later library-native sessions. It inventories current consumer contracts,
workarounds, tests, and repository governance. It does not decide whether a seam belongs upstream, classify a proposed
change for the 4.0 release, select a multi-Groovy design, or select a documentation platform.

The inventory was checked against repository base `927436a9` on `codex/issue-450-consumer-inventory`, the three issues
above, [`CONTEXT.md`](../../../CONTEXT.md), the [curation architecture map](architecture-map.md), [ADR 0004](../../adr/0004-asbuilder-composition-protocol.md), [ADR 0005](../../adr/0005-generated-dsl-support-api.md), its [implementation plan](../adr-0005-generated-dsl-support-api.md), dependency declarations, source, tests, CI/release configuration, the wiki, and the repository's agent guidance. Upstream facts are linked to tagged upstream source or upstream issues.

The words **confirmed**, **current limitation**, and **open question** are used deliberately. A current limitation records
what KlumAST does not presently prove or support; it is not an upstream-change recommendation.

## KlumCast consumer inventory

### Resolved artifacts and Gradle roles

KlumAST pins KlumCast **0.3.1** and declares both published artifacts in its version catalog
([`settings.gradle`](../../../settings.gradle#L38-L40)).

| KlumAST module/configuration | Direct dependency | Confirmed consumer role |
|---|---|---|
| `klum-ast-annotations` / `api` | `com.blackbuild.klum.cast:klum-cast-annotations:0.3.1` | Public schema annotations carry KlumCast validation markers and built-in check annotations. Because this is a `java-library` `api` dependency, it is exposed to consumers of the KlumAST annotation artifact ([build file](../../../klum-ast-annotations/build.gradle#L8-L13)). |
| `klum-ast` / `api` | `com.blackbuild.klum.cast:klum-cast-compile:0.3.1` | Supplies the compiler/global-AST side during schema compilation. The module explicitly describes all of its dependencies as client compile-time dependencies and publishes this one as `api` ([build file](../../../klum-ast/build.gradle#L14-L23)). |
| resolved `klum-ast` compile classpath | `klum-cast-compile` plus transitive `klum-cast-annotations` | Gradle selects Java 11 `apiElements` variants for both artifacts when the KlumAST project requests JVM 17. The annotations artifact is also present through the `klum-ast-annotations` project. |

The tagged upstream README describes the intended split as runtime annotations plus a schema-compile-only compiler/global
AST artifact, and documents custom checks as subclasses of `KlumCastCheck`
([KlumCast 0.3.1 README](https://github.com/klum-dsl/klum-cast/blob/v0.3.1/README.md)). KlumAST's `api` declaration
therefore reflects that `klum-ast` is itself the schema-compilation artifact; it is broader than a repository-internal
implementation dependency.

No direct KlumCast dependency is declared by `klum-ast-runtime`, `klum-ast-gradle-plugin`, `klum-ast-jackson`,
`klum-ast-bean-validation`, or the BOM. `klum-ast-runtime` nevertheless receives the annotations artifact transitively
through its `api` dependency on `klum-ast-annotations` ([runtime build file](../../../klum-ast-runtime/build.gradle#L22-L25)).

### Annotation-validation surface

KlumAST uses KlumCast as a declarative validation vocabulary in the public annotation module. `@KlumCastValidated` marks
the following KlumAST annotations; adjacent KlumCast check annotations declare their target/member constraints.

| KlumAST annotation group | KlumCast seam in current source |
|---|---|
| Core schema | `@DSL` uses a named custom validator; `@Field` combines `@NumberOfParameters`, local `@NeedsDSLClass`, and a named custom validator; `@Key` uses `@NotTogetherWith`; `@Owner` combines parameter-count and mutually-exclusive-member checks; `@Role` combines parameter-count, type, and parameter-type checks ([`DSL`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/DSL.java#L367-L376), [`Field`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Field.java#L53-L61), [`Key`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Key.java#L102-L107), [`Owner`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Owner.java#L121-L129), [`Role`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Role.java#L67-L75)). |
| Validation and lifecycle | `@Validate` combines parameter-count and two custom validators; `@Required` and `@Optional` use `@NotTogetherWith`; `@PostCreate`, `@PostApply`, `@PostTree`, and `@Mutator` acquire method rules through the local `@WriteAccess` meta-annotation ([`Validate`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Validate.java#L130-L137), [`Required`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Required.java#L42-L47), [`Optional`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Optional.java#L37-L42), [`WriteAccess`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/WriteAccess.java#L34-L40)). |
| Converters/defaults | `@Converter` uses return-type and static-method checks. `@Default` combines the local DSL-class check with mutually constrained members and target constraints ([`Converter`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Converter.java#L45-L52), [`Default`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Default.java#L87-L96)). |
| Copy strategy | `@Overwrite` and its nested `@Single`, `@Collection`, `@Map`, and `@Missing` annotations are validated. They combine the local DSL-class check, member/type checks, and custom validators for single and map strategies ([`Overwrite`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/copy/Overwrite.java#L39-L85)). |
| Layer 3 annotations | `@AutoCreate`, `@AutoLink`, `@Cluster`, `@DefaultValues`, `@LinkTo`, and `@Notify` are marked for KlumCast validation and combine write-access, type/generics, member-relation, or custom checks as applicable ([`AutoCreate`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/AutoCreate.java#L54-L60), [`AutoLink`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/AutoLink.java#L39-L45), [`Cluster`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/Cluster.java#L109-L126), [`DefaultValues`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/DefaultValues.java#L37-L42), [`LinkTo`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/LinkTo.java#L100-L111), [`Notify`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/Notify.java#L40-L47)). |

`@NeedsDSLClass` is KlumAST's own composed validation annotation: it is itself `@KlumCastValidated` and delegates to
KlumCast's `@ClassNeedsAnnotation(DSL.class)`
([source](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/cast/NeedsDSLClass.java#L34-L40)).
`@WriteAccess` is another KlumAST meta-annotation; it names a custom validator only for method targets
([source](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/WriteAccess.java#L34-L40)).

### Custom compiler checks

The annotation artifact names custom check implementations as strings, keeping it free of a project dependency on the
compiler module. This boundary is recorded in the [architecture map](architecture-map.md#dependency-shape) and is visible
in the annotation sources. Eight current bindings point at eight KlumAST classes:

| Annotation binding | KlumAST check implementation and direct KlumCast API use |
|---|---|
| `@DSL` | `CheckDslAnnotation extends KlumCastCheck<Annotation>` ([annotation](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/DSL.java#L373-L375), [check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/validation/CheckDslAnnotation.java#L24-L39)). |
| `@Field` | `FieldAstValidator extends KlumCastCheck<Annotation>` ([annotation](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Field.java#L56-L60), [check](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/FieldAstValidator.java#L24-L40)). |
| `@WriteAccess` | `WriteAccessMethodCheck extends KlumCastCheck<WriteAccess>` ([annotation](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/WriteAccess.java#L34-L38), [check](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/mutators/WriteAccessMethodCheck.java#L24-L36)). |
| `@Validate` (two bindings) | `CheckForPrimitiveBoolean` and `ValidateAnnotationCheck`, both subclasses of `KlumCastCheck`; the latter also throws KlumCast `ValidationException` ([annotation](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Validate.java#L132-L136), [boolean check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/validation/CheckForPrimitiveBoolean.java#L24-L36), [placement check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/validation/ValidateAnnotationCheck.java#L24-L47)). |
| `@Overwrite.Single` and `@Overwrite.Map` | `OverwriteSingleCheck` and `OverwriteMapCheck` extend `KlumCastCheck` ([bindings](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/copy/Overwrite.java#L51-L76), [single check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/validation/OverwriteSingleCheck.java#L24-L42), [map check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/validation/OverwriteMapCheck.java#L24-L42)). |
| `@DefaultValues` | `DefaultValuesCheck extends KlumCastCheck<Annotation>` and throws KlumCast `ValidationException` for mismatched `value`/`valueTarget` contracts ([annotation](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations/DefaultValues.java#L37-L42), [check](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/util/layer3/DefaultValuesCheck.java#L24-L48)). |

These classes compile directly against `com.blackbuild.klum.cast.checks.impl.KlumCastCheck` and, in two cases,
`checks.impl.ValidationException`. The package name is factually an `impl` package even though it is the documented custom
check contract consumed by KlumAST.

The string binding also carries two concrete compiler assumptions. KlumCast 0.3.1 rejects validation when the annotation
`ClassNode` is not already resolved/compiled, and it resolves a string-valued `@KlumCastValidator` with
`Class.forName(name, true, AstSupport.getTargetClassLoader(target))` before reflectively constructing the check
([`ValidationHandler`](https://github.com/klum-dsl/klum-cast/blob/v0.3.1/klum-cast-compile/src/main/java/com/blackbuild/klum/cast/validation/ValidationHandler.java#L67-L73),
[`ValidationHandler`](https://github.com/klum-dsl/klum-cast/blob/v0.3.1/klum-cast-compile/src/main/java/com/blackbuild/klum/cast/validation/ValidationHandler.java#L122-L139)).
KlumAST therefore assumes its public annotations are already compiled and the named check classes are visible from the
schema target's Groovy classloader. KlumCast also supports a class-valued validator member; KlumAST uses strings to preserve
the annotation/compiler artifact separation described above.

Two KlumAST source comments explicitly mark validation ownership as unfinished. `FieldAstValidator` contains a
`TODO move logic to klumCast` above its field-versus-method and `defaultImpl` checks
([source](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/FieldAstValidator.java#L44-L52)).
`LinkHelper` contains `TODO: Move to AutoLink Phase, convert static annotation checks to klumCast`; the current runtime
path still reports target-type, selector-type, ambiguous-link, and candidate-count failures while resolving `@LinkTo`
([source](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/layer3/LinkHelper.java#L49-L70),
[resolution checks](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/layer3/LinkHelper.java#L80-L150)).
These are concrete KlumAST-side ownership/brittleness markers, not decisions that either body of logic belongs in a new
KlumCast API.

### Artifact and JPMS facts

Local inspection of the resolved 0.3.1 JARs establishes the following without selecting a module redesign:

- Gradle metadata exposes Java 11 variants for both artifacts.
- Neither JAR has an explicit module descriptor. `jar --describe-module` derives automatic modules
  `klum.cast.annotations` and `klum.cast.compile`.
- Both JARs contain `com.blackbuild.klum.cast.checks.impl`: the annotations JAR contains `KlumCastCheck`,
  `ValidationException`, and `AnnotationHelper`, while the compiler JAR contains the concrete built-in check implementations.
  The two automatic modules therefore contain a split package.
- The compiler JAR declares the Groovy global AST transformation provider
  `com.blackbuild.klum.cast.validation.KlumCastTransformation` in
  `META-INF/services/org.codehaus.groovy.transform.ASTTransformation`; `jar --describe-module` reports the corresponding
  `provides` entry.

The split package and automatic names are current module-path limitations to feed the later KlumCast-native session and
[#391](https://github.com/klum-dsl/klum-ast/issues/391); they are not a decision about artifact layout.

### Validation evidence in KlumAST tests

KlumAST has no test source that imports a KlumCast type directly. Instead, the integration is exercised by compiling
schemas that use KlumAST annotations and asserting resulting diagnostics. Representative direct evidence includes:

- core `@DSL`, `@Field`, and key/type/member rejections in
  [`TransformSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TransformSpec.groovy#L309-L326) and
  [`FixedKeySpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/FixedKeySpec.groovy#L114-L154);
- owner parameter-count and conflicting annotation checks in
  [`OwnerReferencesSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/OwnerReferencesSpec.groovy#L722-L775);
- lifecycle signature checks in
  [`LifecycleSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/LifecycleSpec.groovy#L55-L87);
- `@Validate` signature, placement, constructor, and primitive-boolean checks in
  [`ValidationSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L572-L608),
  [`ValidationSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L765-L886), and
  [`ValidationSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L1146-L1164);
- `@Default` member exclusivity and `@DefaultValues` meta-annotation consistency in
  [`DefaultValuesSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/DefaultValuesSpec.groovy#L211-L242) and
  [`DefaultValuesSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/DefaultValuesSpec.groovy#L688-L746).

Those tests run through the repository's Groovy 3 baseline and Groovy 4/5 compatibility suites when their module uses the
multi-Groovy convention; they do not isolate KlumCast diagnostics or artifact loading from the complete KlumAST transform.

Coverage of the eight custom check bindings is uneven:

| Custom check seam | Current direct evidence or explicit gap |
|---|---|
| `CheckDslAnnotation` | `DefaultImplTest` proves a valid class-level `@DSL(defaultImpl=...)` path, but no focused rejection test for this check's subtype constraint was located ([test](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/DefaultImplTest.groovy#L151-L177)). |
| `FieldAstValidator` | `DefaultImplTest` covers valid field, Collection, Map, and virtual-setter defaults; `TransformSpec` and `FixedKeySpec` cover rejected `members`, `key`, and `keyMapping` placements ([default implementations](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/DefaultImplTest.groovy#L31-L149), [`members`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TransformSpec.groovy#L1691-L1703), [fixed-key rejections](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/FixedKeySpec.groovy#L114-L155)). The individual invalid `defaultImpl` branches are not pinned by focused negative tests. |
| `WriteAccessMethodCheck` | `LifecycleSpec` rejects private and parameterized lifecycle methods ([test](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/LifecycleSpec.groovy#L55-L87)). |
| `CheckForPrimitiveBoolean` and `ValidateAnnotationCheck` | `ValidationSpec` rejects primitive-boolean fields, parameterized methods, illegal members/placements, and invalid validation inner classes ([method checks](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L572-L608), [placement/constructor checks](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L765-L886), [boolean check](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy#L1146-L1164)). |
| `OverwriteSingleCheck` and `OverwriteMapCheck` | `OverwriteStrategyTest` exercises valid Single and Map strategy behavior, including DSL-object merge paths; no focused negative tests were located for the custom checks' invalid Collection/Map, `MERGE`, or `MERGE_VALUES` type constraints ([test](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/OverwriteStrategyTest.groovy#L58-L354)). |
| `DefaultValuesCheck` | `DefaultValuesSpec` covers missing and valid `value`/`valueTarget` combinations ([test](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/DefaultValuesSpec.groovy#L688-L746)). |

### Current KlumCast-side limitations and questions

Confirmed limitations:

- KlumAST exposes both KlumCast artifacts through `api` edges rather than an internal/compile-only Gradle edge.
- KlumAST custom checks compile against classes in a package named `checks.impl`; the public annotation artifact names the
  KlumAST implementations by fully qualified string.
- `FieldAstValidator` and `LinkHelper` retain explicit TODOs about moving or converting validation logic to KlumCast; the
  latter still performs related checks during runtime link resolution.
- Those string bindings assume already-compiled KlumAST annotations and target-classloader visibility of the compiler
  checks; package moves or classloader isolation can invalidate the names even when Java source still compiles.
- The resolved automatic modules have a split package and no stable explicit module metadata.
- Test evidence is integration-level and indirect; there is no small KlumCast-only consumer fixture in this repository.
- Neither #450 nor the repository links an existing KlumCast upstream issue for these facts. Historical KlumAST
  [#312](https://github.com/klum-dsl/klum-ast/issues/312) records the switch to the KlumCast validation direction but does
  not specify an upstream contract change.

Open questions for the library-native session:

- Which classes and packages does KlumCast 0.3.1 intentionally support for third-party custom checks, diagnostics, and
  Groovy 3/4/5 compiler integration?
- Which of the automatic-module, split-package, global-AST service, and Java 11 variant facts are intentional published
  contracts versus current packaging?
- Which KlumAST `api` edges are required for schema consumers, and which are only consequences of the current artifact
  graph?
- What isolated compatibility/diagnostic evidence should complement KlumAST's indirect Groovy 3/4/5 tests?

## AnnoDocimal consumer inventory

### Resolved artifacts and Gradle roles

KlumAST pins AnnoDocimal **0.7.1** and catalogs four direct coordinates
([`settings.gradle`](../../../settings.gradle#L66-L70)). Gradle resolution adds a fifth artifact,
`anno-docimal-ast`, transitively.

| KlumAST module/configuration | Direct dependency | Confirmed consumer role |
|---|---|---|
| `klum-ast-annotations` / `api` | `anno-docimal-annotations` | Exposes the annotation artifact to schema/compiler/runtime consumers, matching upstream's recommended runtime availability for generated `@AnnoDoc`. No handwritten source in this module imports an AnnoDocimal type, but generated classes carry the annotation and runtime code reads it ([build file](../../../klum-ast-annotations/build.gradle#L8-L13), [`EarlyValidationPhase`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/validation/EarlyValidationPhase.java#L89-L99)). |
| `klum-ast` / `api` | `anno-docimal-global-ast` | Brings the global AST integration and transitively resolves `anno-docimal-ast` plus `anno-docimal-annotations`; KlumAST compiler source directly consumes extractor and formatter APIs ([build file](../../../klum-ast/build.gradle#L14-L23)). Gradle selects JVM 17 `apiElements` variants. |
| `klum-ast` / `sharedTests` | `anno-docimal-gradle-plugin` | Makes the plugin/generator surface available to all three test-suite implementations through the shared test configuration ([build file](../../../klum-ast/build.gradle#L21-L23), [multi-Groovy convention](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L10-L16)). |
| `klum-ast-runtime` / `annotationProcessor` | `anno-docimal-apt` | Runs the Java annotation processor; Gradle resolves JVM 17 `anno-docimal-apt` and its `anno-docimal-annotations` runtime dependency ([build file](../../../klum-ast-runtime/build.gradle#L22-L25)). |
| `klum-ast-gradle-plugin` / `api` | `anno-docimal-gradle-plugin` | The published KlumAST plugin compiles against and applies `AnnoDocimalPlugin`, so its dependency is part of the plugin's API/runtime graph ([build file](../../../klum-ast-gradle-plugin/build.gradle#L43-L47), [schema plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java#L45-L49)). Gradle resolves the plugin's `shadowRuntimeElements` variant. |

The tagged upstream README describes the annotations, Java APT, Groovy global AST, AST extractor/formatter/generator, and
Gradle plugin modules, and warns that helper names and APIs were still in flux in 0.7.1
([AnnoDocimal 0.7.1 README](https://github.com/blackbuild/anno-docimal/blob/v0.7.1/README.md)). That warning is relevant
to the number of concrete helper classes consumed below, but is not itself evidence that any one helper must change.

### Direct API surface

| AnnoDocimal API | KlumAST usage |
|---|---|
| `@InlineJavadocs` | Marks `FactoryHelper`, `KlumFactory`, and `TemplateManager` so their Java source documentation is available for later AST extraction ([`FactoryHelper`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/FactoryHelper.java#L52-L57), [`KlumFactory`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumFactory.java#L39-L46), [`TemplateManager`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/TemplateManager.java#L36-L44)). |
| `@AnnoDoc` | Read reflectively at runtime to derive a deprecation validation message from an `@deprecated` tag; also used directly by documentation/mirror tests ([`EarlyValidationPhase`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/validation/EarlyValidationPhase.java#L79-L100)). |
| `ASTExtractor` | Reads raw documentation and structured `DocText` from classes, fields, methods, and templates. It is used by `AbstractMethodBuilder`, `ProxyMethodBuilder`, `DSLASTTransformation`, `BuilderMethodProjection`, and local `DocUtil` ([`AbstractMethodBuilder`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AbstractMethodBuilder.java#L218-L228), [`DocUtil`](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/doc/DocUtil.java#L54-L75)). |
| `AnnoDocUtil` | Writes documentation metadata to generated methods, Builders, factories, fields, and the generated `Foo_DSL` namespace/interfaces ([`AbstractMethodBuilder`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AbstractMethodBuilder.java#L204-L212), [`DSLASTTransformation`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.java#L295-L300), [`GeneratedDslSupport`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/GeneratedDslSupport.java#L80-L94)). |
| `DocBuilder`, `JavadocDocBuilder`, `DocText` | Builds documentation, remaps or removes overload parameter tags, preserves named tags, and supports Builder-projection rewriting ([`AbstractMethodBuilder`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AbstractMethodBuilder.java#L59-L68), [`ProxyMethodBuilder`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/ProxyMethodBuilder.java#L174-L209), [`BuilderMethodProjection`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/BuilderMethodProjection.java#L168-L195)). |
| `JavaDocUtil` | Creates `@see` links from source converter/producer methods in generated documentation ([`ConverterBuilder`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/ConverterBuilder.java#L248-L255), [`BuilderMethodProjection`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/BuilderMethodProjection.java#L183-L195)). |
| `AnnoDocGenerator` | Generates Java source mirrors from compiled top-level `Foo_DSL.class` files in the Gradle plugin task and AST tests ([mirror task](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/CreateKlumDslSourceMirrors.java#L66-L80), [namespace test](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ast/GeneratedDslSupportSpec.groovy#L186-L204)). |
| `AnnoDocimalPlugin` | Applied by class by `KlumAstSchemaPlugin`; normal source/Javadoc variants come from that combined plugin setup ([schema plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java#L45-L60), [plugin test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumAstSchemaPluginTest.groovy#L47-L72)). |

The KlumAST wiki describes how documentation is copied from runtime/schema methods, templated with `@template` tags, and
filtered for omitted overload parameters ([`wiki/Javadoc.md`](../../../wiki/Javadoc.md#L1-L67)). Projection-specific code
replaces root materialization wording with active-session Builder wording while preserving applicable parameter and throws
tags and adding a source `@see` link; hidden synthetic twins intentionally carry no public AnnoDoc
([ADR 0004](../../adr/0004-asbuilder-composition-protocol.md#L69-L76)).

### IDE-only `Foo_DSL` source mirrors

ADR 0005 defines mirrors as IDE metadata for the real AST-generated `Foo_DSL` bytecode, not as a second compilable API
([ADR](../../adr/0005-generated-dsl-support-api.md#L49-L60)). The current KlumAST-specific lifecycle is:

1. `KlumAstSchemaPlugin` applies AnnoDocimal and IDEA, registers `createKlumDslSourceMirrors`, consumes the main classes
   directories, and writes to `build/generated/sources/klum-dsl-ide/main`
   ([source](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java#L45-L73)).
2. The cacheable local task declares the class directories as `@Classpath`, deletes its output, selects top-level
   `**/*_DSL.class` while excluding nested `$` classes, and invokes `AnnoDocGenerator`
   ([source](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/CreateKlumDslSourceMirrors.java#L41-L80)).
3. The output directory is added only to IDEA source/generated-source directory sets. It is not added to the Java or Groovy
   source sets, and ordinary Javadoc excludes `**/*_DSL.java`
   ([schema plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java#L75-L80), [plugin test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumAstSchemaPluginTest.groovy#L74-L90)).
4. Developers explicitly run the refresh task after schema changes. A clean IDEA model registers the directory without
   compiling, while refresh runs compilation once before mirror generation
   ([implementation plan](../adr-0005-generated-dsl-support-api.md#L54-L75), [integration test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L53-L78)).

The integration fixture proves build-cache restoration, deterministic output, stale-file deletion, AnnoDoc-only input
invalidation, absence from compilation/testing/Javadoc/publication/downstream inputs, and absence from published archives
([integration test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L79-L140), [archive checks](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L152-L171)).

### Concrete workarounds and upstream links

- **Local source-mirror task:** KlumAST duplicates the class-to-source task action instead of configuring AnnoDocimal's
  `CreateClassStubs`. Upstream [AnnoDocimal #35](https://github.com/blackbuild/anno-docimal/issues/35) records the missing
  arbitrary filtered input, declared filter inputs, stale-output cleanup, full classpath/content sensitivity, and reusable
  task contract. The KlumAST task is explicitly described as temporary in the
  [ADR implementation plan](../adr-0005-generated-dsl-support-api.md#L80-L86).
- **Configuration cache:** AnnoDocimal 0.7.1 installs a `GroovyCompile.doFirst` action that captures a Gradle `Project`.
  Under the current Gradle 8.14.4 wrapper, the TestKit probe must use
  `--configuration-cache-problems=warn` and asserts the serialization problem rather than strict cache storage
  ([test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L87-L96), [wrapper](../../../gradle/wrapper/gradle-wrapper.properties#L1-L5)). This is also tracked by AnnoDocimal #35.
- **Documentation-sensitive inputs:** The local task uses `@Classpath`, not ABI-only compile classpath sensitivity, because
  AnnoDoc values and parameter metadata affect generated source. AnnoDoc-only invalidation is executable evidence
  ([task](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/CreateKlumDslSourceMirrors.java#L49-L57), [test](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L125-L140), [upstream comment](https://github.com/blackbuild/anno-docimal/issues/35#issuecomment-4970974560)).
- **JPMS:** [AnnoDocimal #36](https://github.com/blackbuild/anno-docimal/issues/36) records 0.7.1 module identity and the
  global-AST cross-artifact service-provider packaging facts discovered for KlumAST #391. #450 links that issue as an
  input; this inventory does not repeat its proposed solution.

### AnnoDocimal-focused test evidence

| Test surface | Contract pinned by KlumAST |
|---|---|
| [`AnnoDocTest`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ast/AnnoDocTest.groovy#L105-L249) | Generated Builder/factory/converter documentation, copied schema docs, templated text, return/parameter tags, and overload behavior. |
| [`GeneratedDslSupportSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ast/GeneratedDslSupportSpec.groovy#L186-L204) | A compiled namespace produces a mirror containing the outer namespace, nested Factory/Builder/Collection/Cluster interfaces, and nested AnnoDoc. |
| [`BuilderProjectionSpec`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderProjectionSpec.groovy#L123-L177) | Projected Builder documentation preserves applicable source tags, changes return semantics, adds a source link, appears in the mirror, and remains absent from hidden twins. |
| [`CreateKlumDslSourceMirrorsTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/CreateKlumDslSourceMirrorsTest.groovy#L36-L63) | The local task removes stale output and generates only a direct top-level `_DSL` mirror. |
| [`KlumDslSourceMirrorsIntegrationTest`](../../../klum-ast-gradle-plugin/src/test/groovy/com/blackbuild/klum/ast/gradle/KlumDslSourceMirrorsIntegrationTest.groovy#L53-L140) | Gradle/IDE isolation, ordering, cache behavior, configuration-cache limitation, archive/downstream exclusions, and documentation-only invalidation. |

### Current AnnoDocimal-side limitations and questions

Confirmed limitations:

- Strict configuration-cache storage is not supported end to end for schema compilation with AnnoDocimal 0.7.1.
- The reusable upstream stub task does not yet provide the filtered, stale-cleaning, documentation-sensitive contract, so
  KlumAST owns a parallel task implementation.
- Mirror discovery is coupled to the generated binary spelling `**/*_DSL.class`, top-level/nested class layout, and
  `AnnoDocGenerator`'s Java source projection.
- The plugin is an `api` dependency of the published KlumAST Gradle plugin, and the compiler directly consumes six named
  extractor/formatter helper types whose upstream 0.7.1 README calls in flux.
- `klum-ast-annotations` exposes the annotation artifact to all schema/runtime consumers so generated `@AnnoDoc` remains
  available at runtime; whether every downstream surface needs that transitive edge remains a library-native boundary
  question.
- The integration tests prove Groovy-version behavior through KlumAST's full compatibility lanes, not an isolated
  AnnoDocimal fixture.

Open questions for the library-native session:

- Which extractor, formatter, generator, annotation, and Gradle task types are supported library contracts in 0.7.1 and in
  the intended successor release?
- Which Java/Groovy source-fidelity, overload/tag-remapping, nested-interface, and documentation-projection cases are owned
  by AnnoDocimal versus KlumAST's projection policy?
- Which configuration-cache, build-cache, filtered-task, module-path, and Groovy 3/4/5 facts can the library-native tests
  prove independently?
- Which current `api` edges are necessary to a Klum schema/plugin consumer, and which are only present to supply build-time
  generation?

## KlumAST repository-governance baseline

Later work in either library should compare against this established KlumAST baseline without assuming that another
repository already has or should copy it verbatim.

| Area | Confirmed KlumAST practice |
|---|---|
| Agent routing | Root [`AGENTS.md`](../../../AGENTS.md) routes issue tracking, triage labels, domain docs, coding style, testing, commits, and PR/release documentation to `docs/agents/` ([routing](../../../AGENTS.md#L1-L32)). |
| Additional contributor guide | [`.junie/guidelines.md`](../../../.junie/guidelines.md) also records Java 17, `check` plus explicit Groovy lanes, wiki/Javadoc expectations, Nebula `candidate`/`final`, and `CHANGES.md`/migration updates ([build/test](../../../.junie/guidelines.md#L17-L44), [docs/release](../../../.junie/guidelines.md#L139-L161)). For agent work, the more specific policies routed by root `AGENTS.md` remain the controlling repository instructions. |
| Research workflow | The repository `research` skill delegates primary-source reading to a background agent, requires claims to follow first-party sources, and writes one cited Markdown artifact in the repository's established location ([skill](../../../.agents/skills/research/SKILL.md#L1-L14)). This inventory follows that single-artifact boundary. |
| Issue workflow skills | KlumAST's repository-specific skills separate read-only evidence curation, maintainer interviews, ADR/tracer-bullet planning, approved implementation, confirmed issue normalization, and release reconciliation. Design conflicts route back to grilling/planning; normalization requires confirmed intent before GitHub mutation; release review treats green CI as tested-state evidence rather than proof that product decisions are complete ([curation](../../../.agents/skills/klum-curate-issues/SKILL.md#L6-L41), [grilling](../../../.agents/skills/klum-grill-issue/SKILL.md#L6-L40), [planning](../../../.agents/skills/klum-plan-design/SKILL.md#L6-L41), [implementation](../../../.agents/skills/klum-implement-issue/SKILL.md#L6-L50), [normalization](../../../.agents/skills/klum-normalize-issues/SKILL.md#L6-L46), [release review](../../../.agents/skills/klum-review-release/SKILL.md#L6-L39)). |
| Issue tracker | GitHub Issues, operated through `gh`, are the issue/PRD tracker. External PRs are not a request surface ([issue-tracker guidance](../../agents/issue-tracker.md#L1-L28)). |
| Triage vocabulary | Canonical roles map directly to `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` ([label map](../../agents/triage-labels.md#L1-L13)). |
| Domain decisions | The repository is one bounded context with root `CONTEXT.md` and accepted decisions in `docs/adr/`; agents read both and surface conflicts rather than overriding ADRs ([domain guidance](../../agents/domain.md#L5-L32), [context](../../../CONTEXT.md#L9-L24)). |
| Coding style | Handwritten Java/Groovy imports referenced types and uses simple names; fully qualified names require a genuine conflict or documented technical/generation constraint ([coding style](../../agents/coding-style.md#L1-L20)). |
| Test policy | Groovy 3 `test` is the focused baseline; `groovy4Tests` and `groovy5Tests` are compatibility/end-of-change lanes. Suppressed tests require actionable reasons ([testing guidance](../../agents/testing.md#L1-L30)). |
| Issue branches and pre-review history | Issue implementation uses a dedicated branch and small reasoned commits. Before first publication, local history is reviewed and may be rewritten for focus/order/message quality, followed by validation of the final tip ([commit guidance](../../agents/commits.md#L5-L28)). |
| Reviewed history and responses | Once human or automated review has happened, reviewed commits are frozen and fixes are additive. Review feedback receives one consolidated response covering addressed and intentionally unchanged items plus validation; thread resolution/review submission requires separate authorization ([commit guidance](../../agents/commits.md#L30-L37), [PR guidance](../../agents/pull-requests.md#L20-L32)). |
| Release-facing documentation | User-visible changes update the relevant `wiki/` pages, migration navigation, `CHANGES.md`, issue links, and SonarCloud findings. New/renamed wiki pages update `_Sidebar.md` ([PR guidance](../../agents/pull-requests.md#L34-L40), [`AGENTS.md`](../../../AGENTS.md#L28-L32)). |
| CI quality gate | One Ubuntu/JDK 17 job runs `./gradlew check --scan sonar --info`, publishes JUnit XML matching the baseline `test` result path, and archives compiled test classes on failure ([workflow](../../../.github/workflows/ci.yml#L15-L56)). PR guidance requires inspecting all checks including SonarCloud ([guidance](../../agents/pull-requests.md#L11-L18)). |
| Publication/release | Subprojects use Maven publication and signing conventions with sources/Javadoc JARs for Java-library modules. The root uses Nebula Release, Nexus publishing, Gradle Plugin Publish, and git-publish for the wiki; composite builds are rejected by `releaseCheck` and `candidate`/`final` finalize by pushing the wiki ([base convention](../../../buildSrc/src/main/groovy/klum-ast.base-conventions.gradle#L1-L51), [Java convention](../../../buildSrc/src/main/groovy/klum-ast.java-conventions.gradle#L10-L25), [root release](../../../build.gradle#L10-L71)). |

## KlumAST-side build/release baseline for issue 455

This section is one factual column for the later three-repository comparison. It makes no choice between a shared plugin,
aligned local conventions, or another approach.

### Confirmed configuration

- The wrapper is Gradle **8.14.4** and Java toolchains target **17**
  ([wrapper](../../../gradle/wrapper/gradle-wrapper.properties#L1-L5), [Java convention](../../../buildSrc/src/main/groovy/klum-ast.java-conventions.gradle#L10-L15)).
- The catalog pins Groovy **3.0.25**, **4.0.32**, and **5.0.6** with matching Spock **2.4** variants. Groovy 3 uses
  `org.codehaus.groovy`; 4/5 use `org.apache.groovy`, including their BOMs
  ([catalog](../../../settings.gradle#L23-L57)).
- The internal `klum-ast.multigroovy-conventions` plugin defines one shared test-dependency bucket, baseline `test`, and
  `groovy4Tests`/`groovy5Tests` `JvmTestSuite`s. Compatibility suites reuse `src/test/groovy`, select matching
  Groovy/Spock/BOMs, and make `check` depend on both suites
  ([convention](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L1-L63)).
- Test-fixture source directories are explicitly added to compatible `GroovyCompile` test tasks because tests compile with
  different Spock versions. `klum-ast-runtime` has a second analogous source addition for its own fixtures
  ([multi-Groovy convention](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L54-L58), [runtime build](../../../klum-ast-runtime/build.gradle#L41-L45)).
- The published consumer convention plugin independently maps configurable Groovy versions to group, Groovy/BOM, and
  Spock dependencies; its known versions match the catalog and its group selection follows the Groovy 3 versus 4/5
  boundary ([extension](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/convention/GroovyDependenciesExtension.java#L97-L140), [plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/convention/GroovyDependenciesPlugin.java#L69-L104)).
- JaCoCo 0.8.13 is configured per module and aggregated for `klum-ast`, Jackson, Bean Validation, and the Gradle plugin;
  Sonar consumes the aggregate XML paths ([JaCoCo convention](../../../buildSrc/src/main/groovy/klum-ast.jacoco-conventions.gradle#L1-L13), [aggregation](../../../code-coverage-report/build.gradle#L12-L35), [Sonar root](../../../build.gradle#L34-L41)).
- CI is one job rather than a GitHub Actions Groovy-version matrix; the Gradle task graph supplies the compatibility lanes
  ([workflow](../../../.github/workflows/ci.yml#L15-L49), [multi-Groovy convention](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L60-L63)).
- Java-library publications share POM metadata/signing and produce sources/Javadoc JARs; the BOM constrains every subproject
  applying `java-library`; the Gradle plugin also publishes through Plugin Publish
  ([base convention](../../../buildSrc/src/main/groovy/klum-ast.base-conventions.gradle#L7-L51), [BOM](../../../klum-ast-bom/build.gradle#L1-L23), [Gradle plugin build](../../../klum-ast-gradle-plugin/build.gradle#L13-L47)).

### Current limitations/questions for the issue 455 session

- `code-coverage-report` names `groovy3Tests` and `groovy4Tests` aggregate suites and no Groovy 5 aggregate, while the
  multi-Groovy convention names the baseline suite `test`, then `groovy4Tests` and `groovy5Tests`. This is a configuration
  fact to verify, not evidence here that a particular report task fails
  ([aggregation](../../../code-coverage-report/build.gradle#L19-L30), [suite definitions](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L18-L51)).
- The GitHub JUnit reporter glob includes only `**/build/test-results/test/TEST-*.xml`, not the two compatibility-suite
  result directories ([workflow](../../../.github/workflows/ci.yml#L44-L49)).
- Configuration cache is neither enabled as a general CI lane nor currently usable strictly through the AnnoDocimal schema
  compiler hook; build-cache behavior is specifically proven for the mirror task.
- Focused-development speed, task execution cost, local/CI cache reuse, publication parity, and accidental drift across
  the three repositories remain measurement questions owned by #455.
- The later session must compare this one-column baseline with klum-cast and AnnoDocimal before calling any convention
  genuinely shared or library-specific.

## KlumAST-side documentation baseline for issue 456

### Confirmed current state

- Repository policy names `wiki/` as canonical user documentation; migration guides live there and navigation is maintained
  in [`wiki/_Sidebar.md`](../../../wiki/_Sidebar.md#L1-L31)
  ([PR guidance](../../agents/pull-requests.md#L34-L40)).
- The current wiki already documents the unreleased Builder-first 4.0 contract, including `Foo_DSL` and IDE-only
  AnnoDocimal mirrors ([migration page](../../../wiki/Builder-First-Migration.md#L110-L122)). `README.md` warns that 4.0 is
  in development and links directly to that wiki ([README](../../../README.md#L10-L21)).
- Root git-publish copies `wiki/` to the single wiki `master` branch, replaces `@version@` tokens with the current build
  version, copies `CHANGES.md` as `Changelog.md`, and pushes after Nebula `candidate` and `final`
  ([root build](../../../build.gradle#L43-L71)).
- Java-library modules produce Javadoc and sources JARs. Applying the Klum schema plugin also enables source/Javadoc
  variants, while `_DSL.java` IDE mirrors are deliberately excluded from ordinary Javadoc
  ([Java convention](../../../buildSrc/src/main/groovy/klum-ast.java-conventions.gradle#L10-L16), [schema plugin](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java#L57-L80)).
- Generated API documentation policy and examples live in `wiki/Javadoc.md`; public completed-object documentation can
  instead designate class Javadoc as its source of truth, as `wiki/Completed-Object-Support.md` does
  ([Javadoc page](../../../wiki/Javadoc.md#L1-L67), [completed-object page](../../../wiki/Completed-Object-Support.md#L24-L34)).
- `CHANGES.md` has one unreleased 4.0 section plus historical release sections and links to the migration/wiki material
  ([changelog](../../../CHANGES.md#L1-L18)).

### Current limitations/questions for the issue 456 session

- The repository config contains one mutable wiki publication target. It defines no stable/development/historical URL
  scheme, version selector, immutable documentation snapshot, preview publication, rollback, or retention workflow.
- The build can produce Javadoc JARs, but CI's `check --scan sonar` invocation does not explicitly run `javadoc` or
  `javadocJar` and has no documentation-site/Javadoc hosting job
  ([workflow](../../../.github/workflows/ci.yml#L38-L56), [Java convention](../../../buildSrc/src/main/groovy/klum-ast.java-conventions.gradle#L10-L16)).
- The README warning distinguishes the unreleased 4.0 line, but ordinary wiki pages do not establish a per-page stable
  versus development identity.
- `wiki/Javadoc.md` still says #394 will decide the generated Builder type spelling/location
  ([lines 54-56](../../../wiki/Javadoc.md#L54-L56)), while ADR 0005 has accepted `Foo_DSL` and the implementation plan
  records DSL-1/DSL-G as implemented ([ADR](../../adr/0005-generated-dsl-support-api.md#L26-L60), [plan](../adr-0005-generated-dsl-support-api.md#L27-L38)). This is concrete current documentation drift.
- Recoverable 3.x user documentation, canonical source/hosting, versioned multi-module Javadocs, aliases, search/deep-link
  behavior, and release/preview/rollback automation remain questions for #456. This inventory does not select a platform.

## Reproducible artifact checks

The artifact-level statements above were reproduced from this repository with the pinned dependency graph:

```shell
./gradlew :klum-ast:dependencyInsight --dependency klum-cast --configuration compileClasspath
./gradlew :klum-ast:dependencyInsight --dependency anno-docimal --configuration compileClasspath
./gradlew :klum-ast-runtime:dependencyInsight --dependency anno-docimal --configuration annotationProcessor
./gradlew :klum-ast-gradle-plugin:dependencyInsight --dependency anno-docimal --configuration compileClasspath
jar --describe-module --file <resolved-klum-cast-annotations-0.3.1.jar>
jar --describe-module --file <resolved-klum-cast-compile-0.3.1.jar>
jar tf <each-resolved-klum-cast-0.3.1.jar>
```

The dependency reports selected KlumCast Java 11 API variants, AnnoDocimal JVM 17 variants, the
`global-ast -> ast -> annotations` and `apt -> annotations` edges, and the Gradle plugin's shadowed runtime variant. The JAR
inspection supplied the automatic-module, split-package, and AST service-provider facts.

## Concise handoffs

### KlumCast library-native task

Start from the two `api` dependency edges, the declarative annotation/check table, the eight named custom checks, the two
validation-ownership TODOs, and the coverage gaps above. Reproduce the Java 11 variant, automatic-module, shared
`checks.impl` package, and global
AST service-provider facts in the KlumCast repository. Establish from that repository which packages, diagnostics, custom
check hooks, and Groovy 3/4/5 behavior are supported. Return facts and candidate ownership/classification to #450; do not
assume that KlumAST's current `api` exposure or string binding is required. No KlumCast upstream issue has yet been linked.

### AnnoDocimal library-native task

Start from all five resolved artifacts, the direct helper/API table, the Java APT path, Gradle plugin application, and the
compiled-bytecode-to-IDE-mirror lifecycle. Reproduce #35's filtered/deterministic task and configuration-cache facts, #36's
module-path facts, nested `Foo_DSL` source fidelity, overload/tag projection, and Groovy 3/4/5 behavior in AnnoDocimal's own
build. Distinguish AnnoDocimal projection fidelity from KlumAST-specific Builder wording and IDEA-only model wiring. Return
facts and candidate ownership/classification to #450; do not redesign the library from this consumer inventory.

### Cross-repository build and documentation tasks

For #455, use the build/release section as the KlumAST column and measure/inspect the two library-native columns before
selecting an approach. For #456, use the documentation section as the current source/publication baseline and resolve its
open questions without treating AnnoDocimal IDE mirrors as published Javadocs or user documentation.
