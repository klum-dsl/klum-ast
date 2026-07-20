# Architecture map for issue curation

This is a compact index for later issue analysis. Unless marked **target** or **hypothesis**, statements below are confirmed by the current source and tests on `master` at `8efa2005` (2026-07-20). Reopen source when an issue touches an unmapped seam or contradicts this map.

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
| `klum-ast` | Compile-time implementation. Local AST transformations validate schemas, move construction state and mutators to hidden generated Builders, generate factories/DSL methods and the public `Foo_DSL` support contract, and emit internal materialization hooks. It is consumed for schema compilation, not normal model runtime. | `api`: annotations, runtime, AnnoDocimal global AST, KlumCast compiler. | [`DSLASTTransformation.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.java), [`GeneratedDslSupport.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/GeneratedDslSupport.java), [`DslAstHelper.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/DslAstHelper.java), [`ConverterBuilder.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/ConverterBuilder.java), [`AlternativesClassBuilder.java`](../../../klum-ast/src/main/java/com/blackbuild/groovy/configdsl/transform/ast/AlternativesClassBuilder.java) |
| `klum-ast-runtime` | Runtime deep module behind generated methods: root factories, Builder state, phase driver, materialization, templates/copying, validation, construction/structural paths, owner/link/default phases, and structure traversal. Generated schema bytecode depends on it. `BreadcrumbCollector` remains generated/runtime implementation vocabulary. | `api`: annotations. Phase actions and validators are extended through Java `ServiceLoader`. | [`KlumBuilder.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/KlumBuilder.java), [`FactoryHelper.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/FactoryHelper.java), [`PhaseDriver.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/process/PhaseDriver.java), [`StructureUtil.java`](../../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/util/layer3/StructureUtil.java) |
| `klum-ast-jackson` | Jackson interoperability adapter. It auto-registers `KlumAstModule`, resolves effective foreign properties, preallocates one owned Builder graph for current root reads, and exports completed models through ordinary Jackson serialization with Template/LINK safeguards. ADR 0009's final `KlumJacksonImporter`/`KlumJacksonInput` seam provides exactly one parser/tree/Map input per explicit root, Template, active-session Builder, or apply-to-Builder operation; it is not a Klum wire format. | `api`: runtime and Jackson Databind. | [`KlumAstModule.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumAstModule.java), [`KlumDeserializer.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumDeserializer.java), [`KlumJacksonImporter.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonImporter.java), [`KlumJacksonInput.java`](../../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonInput.java), [`KlumJacksonImporterSpec.groovy`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/KlumJacksonImporterSpec.groovy), [`JacksonYamlInteroperabilityDocumentaryTest.groovy`](../../../klum-ast-jackson/src/test/groovy/com/blackbuild/klum/ast/jackson/JacksonYamlInteroperabilityDocumentaryTest.groovy) |
| `klum-ast-bean-validation` | Validation adapter. Registers `JSR380Validator` as an `InstanceValidator`; maps Jakarta violations and `Level` payloads into Klum validation issues. | `api`: runtime and Jakarta Validation; `implementation`: Hibernate Validator. | [`JSR380Validator.java`](../../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/JSR380Validator.java), [`Level.java`](../../../klum-ast-bean-validation/src/main/java/com/blackbuild/klum/ast/validation/bean/Level.java), [`JSR380ValidatorTest.groovy`](../../../klum-ast-bean-validation/src/test/groovy/com/blackbuild/klum/ast/validation/bean/JSR380ValidatorTest.groovy) |
| `klum-ast-gradle-plugin` | Build adapter with three plugin interfaces: `com.blackbuild.klum-ast-schema`, `com.blackbuild.klum-ast-model`, and `com.blackbuild.convention.groovy`. Schema projects receive compile-only `klum-ast`, API `klum-ast-runtime`, AnnoDocimal, sources/Javadoc, the BOM, and the explicit IDE source-mirror refresh lifecycle. Model projects expose schema dependencies and generate `META-INF/klum-model` descriptors. The convention plugin selects Groovy/Spock 3, 4, or 5. | Gradle interfaces plus AnnoDocimal plugin. Published Klum coordinates are added by name, not project dependencies. | [`AbstractKlumPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/AbstractKlumPlugin.java), [`KlumAstSchemaPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.java), [`KlumAstModelPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/KlumAstModelPlugin.java), [`GroovyDependenciesPlugin.java`](../../../klum-ast-gradle-plugin/src/main/java/com/blackbuild/klum/ast/gradle/convention/GroovyDependenciesPlugin.java) |
| `klum-ast-bom` | Version-alignment platform. Adds constraints for every subproject that applies `java-library`; it has no behavior. | Gradle platform constraints only. | [`build.gradle`](../../../klum-ast-bom/build.gradle) |

## Generated schema interface

`@DSL` runs the main transformation at canonicalization, a late model verifier at instruction selection, and the delegation transform. Required/Optional and Layer 3 annotations add their own local transforms or KlumCast checks.

Confirmed generated artifacts:

- the DSL Object implements a keyed/unkeyed/model marker and `Serializable`;
- a top-level public `Foo_DSL` namespace with `Factory`, self-typed `Builder<SELF extends Foo>`, and nested Collection/Cluster factory interfaces; `Foo.Create` is typed to `Foo_DSL.Factory`;
- a hidden generated `Foo$Builder` implementation that mirrors the inherited Builder hierarchy and implements `Foo_DSL.Builder`; `KlumBuilder<T>` is its zero-operation public capability rather than a mutable implementation base;
- a hidden generated factory implementation and public static `Create` field, with keyed, unkeyed, or abstract/custom factory behavior;
- a public static `Template` handler, collection-local factory types, closure delegate metadata, converters, alternatives, copy methods, and lifecycle/mutator methods on the Builder;
- an internal model constructor guarded by `InternalKlumBuilder.MaterializationToken`, plus generated allocation/relationship-assignment hooks;
- an artificial `$Template` implementation when an abstract DSL type needs a materializable recipe.

The supported client interface is the schema annotations plus generated `Foo_DSL` interfaces (`Factory`, `Builder`, and
nested Collection/Cluster factories). Clients may name but not construct, implement, or subclass these interfaces. `$_RW`
and `KlumRwObject` are removed; `@DelegatesToBuilder` is canonical and `@DelegatesToRW` is its deprecated source alias.
`KlumInstanceProxy` is a deprecated Builder-only compatibility adapter. Completed objects use `KlumObjectSupport`; direct
`KlumModelProxy` access is not supported and the Model companion is package-internal.

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
- Builder relationship storage contains Builders. A pre-existing completed DSL Object is wrapped by a sealed Builder and accepted only for `FieldType.LINK` or `OPTIONAL_LINK`; owned composition must be created in the owner's session. Nested root factories are rejected. `@LinkTo` selects `OPTIONAL_LINK` unless explicitly overridden: a fresh same-session Builder is claimed as composition, while an already claimed Builder or a completed model is aggregation. A fresh Builder remains invalid for aggregation-only `LINK`.
- Materialization publishes independent read-only snapshots for `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`; comparators are retained. `EnumSet` is defensive. Unsupported concrete/custom collection declarations fail schema compilation. Simple Values are retained, not deep-copied.
- Generated models retain a sealed internal `KlumObjectCompanion`. The package-internal Model companion stores ordinary
  completed-model paths, metadata, validation results, and validator memoization without deferred actions; `KlumTemplateProxy` stores graph-wide
  Template identity and immutable serializable `TemplateRecipeState`. Neither retains the creating Builder.
- `KlumObjectSupport.Validation` reads stored target/subtree results and verifies them without executing validators or
  mutating lifecycle issue state. Generic Builder/Model metadata access is internal lifecycle linkage, not an extension seam.
- Templates copy values/composition and replay cloned recipe actions into fresh Builders. Every owned Template node is
  marked, pre-existing ordinary `LINK` targets retain identity, and no Template may be a relationship value. Normal
  Jackson export rejects Templates; `KlumJacksonImporter.readTemplate` imports an explicit value-only Template without
  running its lifecycle.
- `StructureUtil.visit` is identity-cycle-safe and follows composition only: declared fields are traversed, while owner and `LINK` fields are skipped. Builder phase traversal also skips sealed aggregation wrappers. Paths use GPath-like field, index, and map-key segments. Per-entry relationship state keeps `OPTIONAL_LINK` collections/maps free to mix owned and aggregation entries without traversal leakage (#474).
- Current Jackson root deserialization binds resolved public configuration properties between `PostCreate` and `PostApply`
  in one Builder lifecycle. The complete owned graph and its identities are prepared before binding so backward and forward
  `LINK` references resolve without creating ownership; polymorphic owned values remain Builders in that session.
  `KlumJacksonImporter` provides the root, value-only Template, in-session Builder, and apply-to-Builder modes with immutable
  parser/tree/Map `KlumJacksonInput` adapters and import-source construction-path context. Each operation binds one input;
  source-neutral ordered composition remains #304 work.

## Extension seams

- Phase extensions implement `PhaseAction`; pre-materialization traversal must extend `BuilderVisitingPhaseAction`, post-materialization traversal must extend `ModelVisitingPhaseAction`. Legacy `VisitingPhaseAction` fails with migration guidance.
- ADR 0008's later-4.x target wraps phase actions in state-typed registrations with stable IDs and equal-phase dependencies;
  the current runtime still loads actions directly.
- Validation extensions implement `InstanceValidator` and register via `ServiceLoader`. Core annotation validators and optional Bean Validation share the same result and memoization path.
- Jackson uses Jackson's `Module` service provider interface and only replaces deserializers for DSL types. The delivered
  public importer is data-format-neutral, snapshots caller-owned mapper/reader configuration, and exposes no
  `ConstructionSession` or source-specific convenience overloads. The 4.0 artifact remains a Jackson 2 adapter; a future
  Jackson 3 adapter must compile separately against the same neutral lifecycle engine.
- Generated factories may inherit a schema-supplied `Factory`; root factory methods still return completed models.

## Test map

- Transformation/generated interface: [`TransformSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TransformSpec.groovy), [`GeneratedDslSupportSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ast/GeneratedDslSupportSpec.groovy), [`BuilderProjectionSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderProjectionSpec.groovy), [`BuilderFirstSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/BuilderFirstSpec.groovy), [`ConverterSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ConverterSpec.groovy).
- Lifecycle/templates/validation: [`LifecycleSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/LifecycleSpec.groovy), [`TemplatesSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/TemplatesSpec.groovy), [`ValidationSpec.groovy`](../../../klum-ast/src/test/groovy/com/blackbuild/groovy/configdsl/transform/ValidationSpec.groovy), plus owner/default/auto-create/auto-link specs nearby.
- Runtime algorithms without the real transform: [`klum-ast-runtime/src/test`](../../../klum-ast-runtime/src/test/groovy) with [`AbstractRuntimeTest.groovy`](../../../klum-ast-runtime/src/testFixtures/groovy/com/blackbuild/klum/ast/util/AbstractRuntimeTest.groovy). End-to-end dynamic schema compilation uses [`AbstractDSLSpec.groovy`](../../../klum-ast/src/testFixtures/groovy/com/blackbuild/groovy/configdsl/transform/AbstractDSLSpec.groovy); file/compilation-order cases use the other fixtures in that directory.
- Integration boundaries: Jackson `JsonExportSpec`, `ConfigurationReplaySpec`, `ConstructionOverrideSpec`,
  `LinkIdentitySpec`, `JacksonCustomizationSpec`, `KlumJacksonImporterSpec`, and `JacksonYamlInteroperabilityDocumentaryTest`, Bean Validation `JSR380ValidatorTest`, and Gradle TestKit/unit scenarios under
  [`klum-ast-gradle-plugin/src/test`](../../../klum-ast-gradle-plugin/src/test).
- `klum-ast/src/test/scenarios` currently has no committed executable scenario, only its README; `ScenariosTest` conditionally skips when no scenario directory or `.link` fixture exists.

## Groovy 3/4/5 compatibility

Groovy 3 is the compile and focused-test baseline. [`klum-ast.multigroovy-conventions.gradle`](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle) recompiles the same test sources and AST test fixtures against Groovy/Spock 4 and 5; `check` depends on both lanes. Coordinate group changes (`org.codehaus.groovy` for 3, `org.apache.groovy` for 4/5) are isolated in the version catalog and Gradle convention plugin. [ADR 0011](../../adr/0011-shared-multi-groovy-compatibility-contract.md) makes this the shared Java-17 contract: a compatibility lane must not receive test or fixture output compiled under another Groovy/Spock generation. #496 owns the current `sourceSets.test.output` isolation correction and reporting evidence; it does not change the single Groovy-3-compiled production artifact.

Compiler-version seams are concentrated in `klum-ast`: [`Groovy3To4MigrationHelper.java`](../../../klum-ast/src/main/java/com/blackbuild/klum/common/Groovy3To4MigrationHelper.java) copies renamed AST helpers, and [`AstReflectionBridge.java`](../../../klum-ast/src/main/java/com/blackbuild/klum/ast/util/reflect/AstReflectionBridge.java) preserves reflected parameter names for generated overrides. Any issue changing AST nodes, generated signatures, static type checking, or Groovy compiler APIs requires all three lanes. Jackson, Bean Validation, runtime, and AST modules use the multi-Groovy convention; the annotations module and Gradle plugin have narrower/unit and scenario-specific coverage.

## Authoritative decisions and non-facts

- [ADR 0001](../../adr/0001-architecture-overview.md) establishes the single-context documentation layout; it is **Proposed**.
- [ADR 0002](../../adr/0002-phase-contracts-and-builder-model.md) is historical and **Superseded**; do not infer its opt-in rollout or phase-registration DSL exists.
- [ADR 0003](../../adr/0003-builder-first-materialization.md) is **Accepted** and describes the implemented Builder-first/materialization boundary.
- [ADR 0004](../../adr/0004-asbuilder-composition-protocol.md) is **Accepted and implemented** through the closed AB-1,
  AB-2, AB-3, and compatibility-closure work under #431: active-session composition, generated Builder projections,
  Template recipe companions, copy-source behavior, and the materialization scheduler boundary are indexed in
  [`adr-0004-asbuilder-composition.md`](../adr-0004-asbuilder-composition.md).
- [ADR 0005](../../adr/0005-generated-dsl-support-api.md) accepts and implements the `Foo_DSL`/Builder contract:
  DSL-1 public namespaces, DSL-2 capability/RW migration, DSL-G's IDE-only mirror lifecycle, and DSL-3 distribution/
  documentation closure are delivered under closed #394.
- [ADR 0006](../../adr/0006-completed-object-support.md) accepts `KlumObjectSupport`. OS-1 construction-path/composition support,
  OS-2 stored-validation support/companion lockdown, and OS-3's `getConstructionPath()` compatibility closure are implemented.
- [ADR 0007](../../adr/0007-jackson-configuration-replay.md) is **Superseded**; its JSON-1 property binding and JSON-2
  identity/customization mechanics remain implemented history, but not a persistence contract.
- [ADR 0008](../../adr/0008-phase-registration.md) accepts a later-4.x registration SPI; it is not implemented.
- [ADR 0009](../../adr/0009-jackson-interoperability.md) accepts asymmetric foreign-format import and ordinary POJO
  export with no Klum wire metadata. Closed #463 delivers the importer/interface and closed #464 delivers the asymmetric
  YAML compatibility/documentation closure. Parent #428 remains open in GitHub, so its remaining parent-level release
  reconciliation must not be inferred from either child issue's closure.
- The [#450 integration audit](issue-450-integration-audit.md) confirms that current KlumCast 0.3.1 and AnnoDocimal 0.7.1
  behavior remains unchanged. Target dependency contracts are tracked by #459 (KlumCast 0.4 artifacts/modules), #460
  (desirable durable check-SPI migration), and #461 (blocking AnnoDocimal 1.0 adoption); none is a current capability.

Confirmed deferred gaps, not current capabilities:

- Regular opaque scripts returning completed models remain top-level-only; they are deliberately omitted from generated
  Builder projections. Declarative phase registration (#305) is decided and deferred to later 4.x.
- Generated `Foo_DSL` (#394) and the Jackson JSON-1/#439, JSON-2/#440, JSON-3/#463, and JSON-4/#464 seams are current
  capabilities. Completed-object support (#390) has all confirmed OS slices: OS-1 construction-path/structure, OS-2
  stored validation/proxy lockdown, and OS-3's sole `getConstructionPath()` public facade getter.
- #474's delivered relationship boundary is current behavior: `@LinkTo` selects `OPTIONAL_LINK`; explicit `LINK` is
  aggregation-only; composition-only fields reject completed/claimed values; generated Java/Groovy relationship shapes
  and non-destructive dynamic Auto-Link coverage are part of the 4.0 public seam.

**Analyst hypothesis:** issue coupling is highest where generated composition projections (`AlternativesClassBuilder`/`ConverterBuilder`) call model-returning factories (`KlumFactory`/`FactoryHelper`) and then cross `InternalKlumBuilder.normalizeRelationshipValue`. Verify that path for each factory/converter issue; do not assume all converter or script inputs share it.
