# Architecture map for issue curation

This is a compact index for later issue analysis. Unless marked **target** or **hypothesis**, statements below are confirmed by the current source and tests on `master` at `93be14fd` (2026-07-13). Reopen source when an issue touches an unmapped seam or contradicts this map.

## Dependency shape

```text
klum-ast-annotations
    ^
    +-- klum-ast-runtime <--- klum-ast-jackson
    |          ^         <--- klum-ast-bean-validation
    |          |
    +------ klum-ast

klum-ast-gradle-plugin -- adds published coordinates/BOM; no project dependency
klum-ast-bom           -- constrains subprojects that apply `java-library`
```

`code-coverage-report` only aggregates JaCoCo output and has no architectural role.

| Module | Responsibility and seam | Direct architectural dependencies | Primary evidence |
|---|---|---|---|
| `klum-ast-annotations` | Public schema vocabulary: `@DSL`, field/key/owner/role, lifecycle, validation, converter, copy strategy, and Layer 3 annotations. Runtime retention lets the runtime interpret the schema; transformation/validator classes are named as strings so the annotation artifact does not depend on compiler implementation. | AnnoDocimal annotations and KlumCast annotations; Groovy is compile-only. | [`DSL.java`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/DSL.java), [`Field.java`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/Field.java), [`FieldType.java`](../../../klum-ast-annotations/src/main/java/com/blackbuild/groovy/configdsl/transform/FieldType.java), [`layer3/annotations`](../../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/util/layer3/annotations) |
| `klum-ast` | Compile-time implementation. Local AST transformations validate schemas, move construction state and mutators to generated Builders, generate factories/DSL methods, and emit internal materialization hooks. It is consumed for schema compilation, not normal model runtime. | `api`: annotations, runtime, AnnoDocimal global AST, KlumCast compiler. | [`DSLASTTransformation.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.java), [`DslAstHelper.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DslAstHelper.java), [`ConverterBuilder.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/ConverterBuilder.java), [`AlternativesClassBuilder.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AlternativesClassBuilder.java) |
| `klum-ast-runtime` | Runtime deep module behind generated methods: root factories, Builder state, phase driver, materialization, templates/copying, validation, breadcrumbs/model paths, owner/link/default phases, and structure traversal. Generated schema bytecode depends on it. | `api`: annotations. Phase actions and validators are extended through Java `ServiceLoader`. | [`KlumBuilder.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), [`FactoryHelper.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/FactoryHelper.java), [`PhaseDriver.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/process/PhaseDriver.java), [`StructureUtil.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/layer3/StructureUtil.java) |
| `klum-ast-jackson` | Jackson adapter. Auto-registers `KlumAstModule`, resolves effective Jackson properties, preallocates one owned Builder graph, replays configuration through the normal lifecycle, and keeps `LINK` persistence reference-only through Jackson identity or explicit property codecs. | `api`: runtime and Jackson Databind. | [`KlumAstModule.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumAstModule.java), [`KlumDeserializer.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumDeserializer.java), [`LinkIdentitySpec.groovy`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/LinkIdentitySpec.groovy), [`JacksonCustomizationSpec.groovy`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonCustomizationSpec.groovy) |
| `klum-ast-bean-validation` | Validation adapter. Registers `JSR380Validator` as an `InstanceValidator`; maps Jakarta violations and `Level` payloads into Klum validation issues. | `api`: runtime and Jakarta Validation; `implementation`: Hibernate Validator. | [`JSR380Validator.java`](../../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/JSR380Validator.java), [`Level.java`](../../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/Level.java), [`JSR380ValidatorTest.groovy`](../../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/JSR380ValidatorTest.groovy) |
| `klum-ast-gradle-plugin` | Build adapter with three plugin interfaces: `com.blackbuild.klum-ast-schema`, `com.blackbuild.klum-ast-model`, and `com.blackbuild.convention.groovy`. Schema projects receive compile-only `klum-ast`, API `klum-ast-runtime`, AnnoDocimal, sources/Javadoc, the BOM, and the explicit IDE source-mirror refresh lifecycle. Model projects expose schema dependencies and generate `META-INF/klum-model` descriptors. The convention plugin selects Groovy/Spock 3, 4, or 5. | Gradle interfaces plus AnnoDocimal plugin. Published Klum coordinates are added by name, not project dependencies. | [`AbstractKlumPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/AbstractKlumPlugin.java), [`KlumAstSchemaPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java), [`KlumAstModelPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstModelPlugin.java), [`GroovyDependenciesPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/convention/GroovyDependenciesPlugin.java) |
| `klum-ast-bom` | Version-alignment platform. Adds constraints for every subproject that applies `java-library`; it has no behavior. | Gradle platform constraints only. | [`build.gradle`](../../../klum-ast-bom/build.gradle) |

## Generated schema interface

`@DSL` runs the main transformation at canonicalization, a late model verifier at instruction selection, and the delegation transform. Required/Optional and Layer 3 annotations add their own local transforms or KlumCast checks.

Confirmed generated artifacts:

- the DSL Object implements a keyed/unkeyed/model marker and `Serializable`;
- a public static generated Builder currently named `$_RW`, inheriting the parent DSL Builder or `KlumBuilder<T>`;
- a generated `$_Factory` and public static `Create` field, with `KlumFactory.Keyed`, `Unkeyed`, or abstract/custom factory behavior;
- a public static `Template` handler, collection-local factory types, closure delegate metadata, converters, alternatives, copy methods, and lifecycle/mutator methods on the Builder;
- an internal model constructor guarded by `KlumBuilder.MaterializationToken`, plus generated allocation/relationship-assignment hooks;
- an artificial `$Template` implementation when an abstract DSL type needs a materializable recipe.

The accepted target client interface is the schema annotations plus generated `Foo_DSL` interfaces (`Factory`, `Builder`,
and nested Collection/Cluster factories), with `Foo.Create` typed to `Foo_DSL.Factory`. Clients may name but not implement
these interfaces. Current `$_RW`, `KlumRwObject`, `@DelegatesToRW`, and `KlumInstanceProxy` remain compatibility details
pending ADR 0005 implementation. Completed objects use `KlumObjectSupport`; direct `KlumModelProxy` access is not supported
by ADR 0006 and the Model companion is package-internal.

ADR 0005 also requires AnnoDocimal `Foo_DSL` source mirrors to be IDE-only metadata. DSL-G adds a cacheable
`createKlumDslSourceMirrors` task and registers its output only as an IDEA generated source root. Developers run the task
explicitly after schema changes; it compiles the real contract first. Mirror output does not enter a Java/Groovy SourceSet,
compiler input, source JAR, publication, classpath, or downstream build input.

## Lifecycle and state boundary

Phase actions are discovered from [`META-INF/services/...PhaseAction`](../../../klum-ast-runtime/src/main/resources/META-INF/services/com.blackbuild.klum.ast.process.PhaseAction) and globally ordered by phase number; actions registered at the same number retain registration/`ServiceLoader` order.

| Phase | State | Confirmed invariant |
|---:|---|---|
| `CREATE` 0 | Builder | Factory allocation/configuration occurs outside an executable phase action. A root factory owns one `PhaseDriver.withBuilderLifecycle` and returns a completed model. |
| `APPLY_LATER` 1 | Builder | Default deferred Builder closures. Dynamically registered numeric apply-later phases run before the next registered phase. |
| `EARLY_VALIDATE` 5 | Builder | Deprecation/`@Notify` issues are provisional Builder metadata. |
| `AUTO_CREATE` 10 | Builder | Missing annotated composition is created inside the active construction session. |
| `OWNER` 15 | Builder | Direct, transitive, and root owners plus roles are established between Builders. |
| `AUTO_LINK` 20 | Builder | Layer 3 links are resolved before defaults/post-tree. |
| `DEFAULT` 25 | Builder | Annotation, field, and lifecycle defaults are applied. |
| `POST_TREE` 30 | Builder | Final mutating lifecycle callbacks run over the Builder composition graph. |
| `INSTANTIATE` 40 | state switch | Two-pass materialization first allocates every unsealed Builder, then assigns relationship fields; this preserves cycles and self-links. The phase root becomes the completed DSL Object. |
| `VALIDATE` 50 | Model | `InstanceValidator`s run on completed models. Each validator class runs at most once per model companion. |
| `VERIFY` 80 | Model graph | Aggregated validation results throw according to `klum.validation.failOnLevel`; the phase can be skipped via `klum.validation.skipVerify`. |
| `COMPLETE` 100 | Model | Cleanup action traverses completed models; Builder construction state is not retained. |

Additional invariants:

- Source initializers execute once when a Builder is allocated. `FieldType.BUILDER` state is omitted from the model. Non-transient, non-relationship model fields become final.
- Builder relationship storage contains Builders. A pre-existing completed DSL Object is wrapped by a sealed Builder and accepted only for `FieldType.LINK`; owned composition must be created in the owner's session. Nested root factories are rejected.
- Materialization publishes independent read-only snapshots for `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`; comparators are retained. `EnumSet` is defensive. Unsupported concrete/custom collection declarations fail schema compilation. Simple Values are retained, not deep-copied.
- Generated models retain a sealed internal `KlumObjectCompanion`. The package-internal Model companion stores ordinary
  completed-model paths, metadata, validation results, and validator memoization without deferred actions; `KlumTemplateProxy` stores graph-wide
  Template identity and immutable serializable `TemplateRecipeState`. Neither retains the creating Builder.
- `KlumObjectSupport.Validation` reads stored target/subtree results and verifies them without executing validators or
  mutating lifecycle issue state. Generic Builder/Model metadata access is internal lifecycle linkage, not an extension seam.
- Templates copy values/composition and replay cloned recipe actions into fresh Builders. Every owned Template node is
  marked, pre-existing ordinary `LINK` targets retain identity, and no Template may be a relationship or JSON value.
- `StructureUtil.visit` is identity-cycle-safe and follows composition only: declared fields are traversed, while owner and `LINK` fields are skipped. Builder phase traversal also skips sealed aggregation wrappers. Paths use GPath-like field, index, and map-key segments.
- Jackson deserialization replays resolved public configuration properties between `PostCreate` and `PostApply` in one
  Builder lifecycle. The complete owned graph and its identities are prepared before binding so backward and forward
  `LINK` references resolve without creating ownership; polymorphic owned values remain Builders in that session.

## Extension seams

- Phase extensions implement `PhaseAction`; pre-materialization traversal must extend `BuilderVisitingPhaseAction`, post-materialization traversal must extend `ModelVisitingPhaseAction`. Legacy `VisitingPhaseAction` fails with migration guidance.
- ADR 0008's later-4.x target wraps phase actions in state-typed registrations with stable IDs and equal-phase dependencies;
  the current runtime still loads actions directly.
- Validation extensions implement `InstanceValidator` and register via `ServiceLoader`. Core annotation validators and optional Bean Validation share the same result and memoization path.
- Jackson uses Jackson's `Module` service provider interface and only replaces deserializers for DSL types.
- Generated factories may inherit a schema-supplied `Factory`; root factory methods still return completed models.

## Test map

- Transformation/generated interface: [`TransformSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TransformSpec.groovy), [`RWClassSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/RWClassSpec.groovy), [`BuilderFirstSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderFirstSpec.groovy), [`ConverterSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ConverterSpec.groovy).
- Lifecycle/templates/validation: [`LifecycleSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/LifecycleSpec.groovy), [`TemplatesSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TemplatesSpec.groovy), [`ValidationSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy), plus owner/default/auto-create/auto-link specs nearby.
- Runtime algorithms without the real transform: [`klum-ast-runtime/src/test`](../../../klum-ast-runtime/src/test/groovy) with [`AbstractRuntimeTest.groovy`](../../../klum-ast-runtime/src/testFixtures/groovy/com/blackbuild/klum/ast/util/AbstractRuntimeTest.groovy). End-to-end dynamic schema compilation uses [`AbstractDSLSpec.groovy`](../../../klum-ast/src/testFixtures/groovy/com/blackbuild/groovy/configdsl/transform/AbstractDSLSpec.groovy); file/compilation-order cases use the other fixtures in that directory.
- Integration boundaries: Jackson `JsonExportSpec`, `ConfigurationReplaySpec`, `LinkIdentitySpec`, and
  `JacksonCustomizationSpec`, Bean Validation `JSR380ValidatorTest`, and Gradle TestKit/unit scenarios under
  [`klum-ast-gradle-plugin/src/test`](../../../klum-ast-gradle-plugin/src/test).
- `klum-ast/src/test/scenarios` currently has no committed executable scenario, only its README; `ScenariosTest` conditionally skips when no scenario directory or `.link` fixture exists.

## Groovy 3/4/5 compatibility

Groovy 3 is the compile and focused-test baseline. [`klum-ast.multigroovy-conventions.gradle`](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle) recompiles the same test sources and AST test fixtures against Groovy/Spock 4 and 5; `check` depends on both lanes. Coordinate group changes (`org.codehaus.groovy` for 3, `org.apache.groovy` for 4/5) are isolated in the version catalog and Gradle convention plugin.

Compiler-version seams are concentrated in `klum-ast`: [`Groovy3To4MigrationHelper.java`](../../../klum-ast/src/main/java/com/blackbuild/klum/common/Groovy3To4MigrationHelper.java) copies renamed AST helpers, and [`AstReflectionBridge.java`](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/util/reflect/AstReflectionBridge.java) preserves reflected parameter names for generated overrides. Any issue changing AST nodes, generated signatures, static type checking, or Groovy compiler APIs requires all three lanes. Jackson, Bean Validation, runtime, and AST modules use the multi-Groovy convention; the annotations module and Gradle plugin have narrower/unit and scenario-specific coverage.

## Authoritative decisions and non-facts

- [ADR 0001](../../adr/0001-architecture-overview.md) establishes the single-context documentation layout; it is **Proposed**.
- [ADR 0002](../../adr/0002-phase-contracts-and-builder-model.md) is historical and **Superseded**; do not infer its opt-in rollout or phase-registration DSL exists.
- [ADR 0003](../../adr/0003-builder-first-materialization.md) is **Accepted** and describes the implemented Builder-first/materialization boundary.
- [ADR 0004](../../adr/0004-asbuilder-composition-protocol.md) is **Accepted target behavior but not implemented**. The confirmed failure paths and pending tests are indexed in [`adr-0004-asbuilder-composition.md`](../adr-0004-asbuilder-composition.md).
- [ADR 0005](../../adr/0005-generated-dsl-support-api.md) accepts `Foo_DSL` and Builder vocabulary; only DSL-G's Gradle
  mirror lifecycle is implemented.
- [ADR 0006](../../adr/0006-completed-object-support.md) accepts `KlumObjectSupport`. OS-1 provenance/composition support
  and OS-2 stored-validation support/companion lockdown are implemented; OS-3 compatibility closure remains planned.
- [ADR 0007](../../adr/0007-jackson-configuration-replay.md) accepts configuration replay. JSON-1 property replay and
  JSON-2 identity-safe `LINK`/customization behavior are implemented; JSON-3 retains final migration closure.
- [ADR 0008](../../adr/0008-phase-registration.md) accepts a later-4.x registration SPI; it is not implemented.
- The [#450 integration audit](issue-450-integration-audit.md) confirms that current KlumCast 0.3.1 and AnnoDocimal 0.7.1
  behavior remains unchanged. Target dependency contracts are tracked by #459 (KlumCast 0.4 artifacts/modules), #460
  (desirable durable check-SPI migration), and #461 (blocking AnnoDocimal 1.0 adoption); none is a current capability.

Confirmed deferred gaps, not current capabilities:

- ADR 0004 AB-1/AB-2/AB-3 are implemented. Regular opaque scripts returning completed models remain top-level-only, and
  #431 retains final compatibility closure.
- Generated `Foo_DSL` AST layout (#394) remains decided but not implemented. Jackson JSON-1/#439 and JSON-2/#440 are
  implemented; #428 retains final compatibility closure.
  Completed-object support (#390) has its OS-1 provenance/structure and OS-2 validation/proxy-lockdown slices implemented;
  OS-3 compatibility closure remains. The DSL-G Gradle mirror lifecycle is implemented independently. Declarative phase
  registration (#305) is decided and deferred to later 4.x.

**Analyst hypothesis:** issue coupling is highest where generated composition projections (`AlternativesClassBuilder`/`ConverterBuilder`) call model-returning factories (`KlumFactory`/`FactoryHelper`) and then cross `KlumBuilder.normalizeRelationshipValue`. Verify that path for each factory/converter issue; do not assume all converter or script inputs share it.
