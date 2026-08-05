# 4.0.0 (unreleased)

This is a breaking release. See the [Builder-first construction migration](docs/user/Builder-First-Migration.md) for required client and extension changes.

## Documentation infrastructure

- Current 4.x user documentation is authored in `docs/user/` and rendered locally from an explicit Git revision into an immutable exact-version static HTML tree. The renderer captures a deterministic site manifest and versioned Season/logo input, while the former mutable wiki publisher fails closed ([#456](https://github.com/klum-dsl/klum-ast/issues/456)).
- Exact 4.x documentation renders six isolated module-Javadoc trees below `/&lt;version&gt;/api/` for `klum-ast`, runtime, annotations, Jackson, Bean Validation, and the Gradle plugin. The BOM and IDE-only source mirrors are not public API inputs ([#456](https://github.com/klum-dsl/klum-ast/issues/456)).
- Protected release orchestration now requires #456's separately permissioned, immutable, unlisted pending documentation/Javadoc stage on `gh-pages` before artifact publication. It validates the exact stage/version/master SHA and returns only a manifest-bound evidence handoff; it neither publishes artifacts nor advances aliases ([#456](https://github.com/klum-dsl/klum-ast/issues/456)).

## Dependency compatibility

- Migrated to released [AnnoDocimal 1.0.0-rc.7](https://github.com/blackbuild/anno-docimal/releases/tag/v1.0.0-rc.7)
  (`11324cfea7d8b8ea27d13c6f2ffaeb370f3ef466`). KlumAST now uses its supported documentation-authoring and source-projection
  APIs. The schema plugin retains its IDEA-only mirror policy while using the configuration-cache-safe projection task;
  property documentation is projected verbatim to generated Model and Builder accessors unless an accessor supplies its
  own documentation ([#461](https://github.com/klum-dsl/klum-ast/issues/461)). Final AnnoDocimal 1.0 remains a KlumAST
  final-release prerequisite; this change validates the immutable RC train only.
- Upgraded to the immutable [KlumCast 0.4.0-rc.2](https://github.com/klum-dsl/klum-cast/releases/tag/v0.4.0-rc.2) artifact set: `klum-cast-annotations`, `klum-cast-spi`, and `klum-cast-compile`. The artifacts have stable automatic module names (`com.blackbuild.klum.cast.annotations`, `.spi`, and `.compiler`) and no split KlumCast packages. Recompile schemas and custom checks for 4.0.
- Migrated KlumAST's eight name-bound compiler checks to KlumCast's stateless `Check` SPI. Their expected violations now emit source-positioned structured diagnostics; diagnostic codes are the check implementation names, while unexpected failures remain technical errors with their causes. Invalid `@Overwrite.Single(MERGE)` strategies on non-DSL fields are rejected during compilation ([#460](https://github.com/klum-dsl/klum-ast/issues/460)).

## Java modules

- Named schema modules are supported with Groovy 4 and 5; Groovy 3 remains supported on the ordinary classpath. The Schema plugin validates the user-owned descriptor as part of `check` and Maven publication, reporting copyable missing directives without editing it. A named schema requires the documented `org.apache.groovy` dependency and a qualified `opens` directive to KlumAST runtime, Jackson when used, and Hibernate Validator when Bean Validation is used. No JVM module-path workaround flags are required ([#391](https://github.com/klum-dsl/klum-ast/issues/391)).
- Fixed Groovy 4/5 named-schema materialization for owned direct, collection, and keyed relationships without changing the approved module descriptors or requiring consumer flags ([#622](https://github.com/klum-dsl/klum-ast/issues/622)).

## Validation

- `KlumValidationException` is now solely `com.blackbuild.klum.ast.runtime.validation.KlumValidationException`; update
  imports and caught types. The former runtime-package class has been removed as an intentional 4.0 source and binary
  compatibility break ([#657](https://github.com/klum-dsl/klum-ast/issues/657)).
- Added the supported `KlumSchemaSupport`/`KlumValidationReporter` facade for custom lifecycle diagnostics, including
  explicit target reporting, suppression, and configured fail-level access. The preliminary `Validator` and
  `ValidatorBase` types are removed with direct 4.0 migration guidance; #406 remains the separate compile-time
  placement rule ([#626](https://github.com/klum-dsl/klum-ast/issues/626)).

## Builder-first construction

- Fixed polymorphic `@Owner` fields on types with `defaultImpl` to retain their declared public Builder type for ownership
  matching, while ordinary relationship creation continues to select the default implementation ([#666](https://github.com/klum-dsl/klum-ast/issues/666)).
- Uninitialized `SortedSet`/`NavigableSet` and `SortedMap`/`NavigableMap` fields now receive their
  natural-order sorted defaults before Builder-first materialization. Their completed views remain
  sorted and immutable, while explicit `TreeSet`/`TreeMap` comparators continue to be preserved
  ([#664](https://github.com/klum-dsl/klum-ast/issues/664)).
- Factory maps continue to prefer an explicit same-named Builder mutator over direct field storage. KlumAST now warns
  when an exact single-argument `@Mutator` override returns the field value or compatible Builder, points direct
  assignment to `setX`, and rejects other non-void return types as likely helper-method collisions. `void` overrides and
  no-field map-method fallback remain valid ([#661](https://github.com/klum-dsl/klum-ast/issues/661)).
- Statically checked Builder-phase code now identifies source-visible `Child.Create.With`, `One`, and `From` root
  factories, explains that they return a completed model, and directs nested composition to `Child.Create.AsBuilder.*`
  attached to an owned relationship. Completed-model validation, ordinary static source factories, non-DSL `Create`, and
  valid Builder composition remain valid ([#656](https://github.com/klum-dsl/klum-ast/issues/656)).
- Statically checked Builder-phase code now rejects `instanceof SomeDslModel` when a relationship value is known to be a
  Builder before materialization. The diagnostic reports the inferred Builder type and points to the Builder-first
  migration guide; completed-model validation, ordinary non-model checks, and operands known only as `Object` remain
  valid ([#654](https://github.com/klum-dsl/klum-ast/issues/654)).
- Added a deliberately incomplete, best-effort 3.x-to-4.0 migration starter script for known annotation,
  `KlumModelException`, and copy-annotation import moves plus current-target validation-reporter edits. It runs from a
  schema module in a clean disposable Git worktree and requires diff review, normal compilation, and the Builder-first
  migration checklist; it is not an automatic migration tool ([#652](https://github.com/klum-dsl/klum-ast/issues/652)).
- Added the portable `build-domain-first-schema` skill, task-oriented domain-first guidance, and an executable Layer 3 smart-home journey. The fixture separates generic API, fixed floorplan Schema, registered Model script, and API-only `client-demo`; it covers Cluster projection, provider-polymorphic Builder calls, `@DefaultValues` labels, and a bounded field-test artifact ([#471](https://github.com/klum-dsl/klum-ast/issues/471)).

- Added a task-oriented Gradle onboarding preview, portable `start-klum-project`, `author-klum-model`, and `feature-advisor` Agent Skills, plus an executable minimal fixture. `feature-advisor` also assesses whether KlumAST or its skill distribution needs an update ([#470](https://github.com/klum-dsl/klum-ast/issues/470)).
- Added the portable `build-target-contract-schema` skill and an executable direct-schema Helm journey. It renders two validated service models as human-readable values files with semantic golden-contract evidence, makes the Layer 3 decision explicit, and keeps resource-backed defaults and ordered configuration composition with #79 and #304 ([#472](https://github.com/klum-dsl/klum-ast/issues/472)).

- Replaced mutable generated RW objects with generated Builders inheriting from `KlumBuilder`, while preserving DSL inheritance. Builders own field initializers, relationship state, mutators, and lifecycle work through `POST_TREE` ([#416](https://github.com/klum-dsl/klum-ast/issues/416), [#266](https://github.com/klum-dsl/klum-ast/issues/266)).
- Fixed generated Builder fields and accessors to retain declared simple collection and map generic types for lifecycle static checking, including inherited fields, while relationship collections retain their generated Builder element types ([#646](https://github.com/klum-dsl/klum-ast/issues/646)).
- Added the `INSTANTIATE` phase at ordinal 40. It materializes the complete graph before validation, including cycles and self-links, then runs validation against completed DSL Objects.
- Completed DSL Objects no longer expose generated `apply` or construction-path members directly. Non-transient simple fields are final; `FieldType.TRANSIENT` remains mutable ([#323](https://github.com/klum-dsl/klum-ast/issues/323)).
- Relationship fields hold Builders during construction. `OPTIONAL_LINK` preserves Layer 3 `@LinkTo` overrides per single, List, and Map entry: a fresh same-session Builder is owned composition, while an already claimed Builder or completed model is aggregation. `LINK` remains aggregation-only and rejects fresh Builders; `KlumBuilder.link(fieldName, target)` provides non-destructive custom Auto-Link fallback ([#474](https://github.com/klum-dsl/klum-ast/issues/474)).
- Completed collections are independent read-only snapshots. Supported declarations are `List`, `Set`, `SortedSet`/`NavigableSet`, `Map`, `SortedMap`/`NavigableMap`, and `EnumSet`; unsupported concrete or custom declarations now fail schema compilation.
- Split construction and completed-model state between `KlumBuilder` and `KlumModelProxy`. `KlumInstanceProxy` is now a deprecated Builder-only compatibility adapter, and `VisitingPhaseAction` is replaced by state-specific Builder and Model variants.
- Replaced the legacy `$_RW`/`KlumRwObject` Builder implementation contract with generated, self-typed
  `Foo_DSL.Builder<SELF extends Foo> extends KlumBuilder<SELF>` capabilities. Inherited Builder interfaces retain their
  parent interface and thread the same leaf `SELF`; factories expose the concrete `Foo_DSL.Builder<Foo>`. Runtime operations
  are internal; use generated Builder/factory
  interfaces and `@DelegatesToBuilder`. `@DelegatesToRW` remains a deprecated source alias
  ([#394](https://github.com/klum-dsl/klum-ast/issues/394)).
- Added `createKlumDslSourceMirrors` to the schema Gradle plugin. Run it after schema changes to compile the real
  `Foo_DSL` interfaces and refresh their AnnoDocimal IDE source mirrors without compiling, packaging, publishing, or
  propagating the mirrors themselves ([#434](https://github.com/klum-dsl/klum-ast/issues/434)).
- Added `generateKlumDslSourceMirrors` as the root Gradle entry point for multi-project builds. It lazily refreshes each
  participating Schema project's IDE-only mirror task, including Layer 3 `api`/`schema` layouts, without generating or
  exposing a root payload ([#559](https://github.com/klum-dsl/klum-ast/issues/559)).
- Generated completed-model and Builder getters now carry field-derived AnnoDoc documentation, including deprecation
  reasons ([#383](https://github.com/klum-dsl/klum-ast/issues/383)).
- Provisional Builder validation issues transfer to the completed-model companion, and each `InstanceValidator` is memoized once per completed model.
- Added active-session `Create.AsBuilder.With`, `One`, `FromMap`, and `From(DelegatingScript)` operations. They create an
  unsealed owned Builder in the current root Construction session, apply active Templates, and run `PostCreate`, explicit
  configuration, and `PostApply` once without starting a nested lifecycle. Calls outside the session, across root sessions,
  or after lifecycle completion fail with migration guidance; ordinary materializing Scripts remain root-only
  ([#436](https://github.com/klum-dsl/klum-ast/issues/436)).
- Added statically precise polymorphic relationship selection with `child(ConcreteChild.Create) { ... }`. Generated
  factories expose the exact public `ConcreteChild_DSL.Builder<ConcreteChild>` through a public provider contract, while
  single, collection, map, named-parameter, and keyed child creation stays in the current parent session. The dynamic
  `child(ConcreteChild) { ... }` Class selector remains supported and unchanged
  ([#620](https://github.com/klum-dsl/klum-ast/issues/620)).
- Restored Builder-first collection, map, Cluster, direct `DelegatingScript`, converter, alternative, and custom-factory
  composition. Source-visible model-producing methods receive synthetic `$klum$asBuilder$...` twins linked through AST
  metadata; generated public contracts expose concrete `Foo_DSL.Builder` results while direct root factory behavior remains
  completed-model-oriented. Multi-result projections retain their original container, iteration order, comparator,
  duplicates, and map keys. Opaque or precompiled model producers are omitted from generated APIs and matching dynamic calls
  fail with targeted migration guidance ([#437](https://github.com/klum-dsl/klum-ast/issues/437),
  [ADR 0004](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0004-asbuilder-composition-protocol.md)).
- Fixed Builder-producing projection for source-visible recursive unqualified static factory and converter calls, including
  overload selection. The generated relationship APIs and IDE mirrors now expose the matching public Builder overloads for
  single, List, and Map relationships without changing direct root-factory behavior ([#642](https://github.com/klum-dsl/klum-ast/issues/642)).
- Added the Java-first `KlumObjectSupport.of(completedObject)` facade for a completed DSL Object root or subtree. Its
  construction-path getter and composition-only `Structure` helper expose paths, direct ownership, relative paths, and
  cycle-safe typed traversal. Its `Validation` helper exposes `getResult()` for the target and `getSubtreeResults()` for
  the target plus owned subtree, and verifies stored results without rerunning validators. Subtree result lists include
  every stored result, including results without issues; the deprecated
  `Validator.getValidationResultsFromStructure` and `verifyStructure` adapters now inherit that broader list contract.
  The Model companion and generic metadata access are now internal ([#435](https://github.com/klum-dsl/klum-ast/issues/435),
  [#390](https://github.com/klum-dsl/klum-ast/issues/390),
  [ADR 0006](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0006-completed-object-support.md)). Its sole
  public construction-string getter is `getConstructionPath()`; no `getBreadcrumbPath()` facade alias remains
  ([#390](https://github.com/klum-dsl/klum-ast/issues/390), [#549](https://github.com/klum-dsl/klum-ast/issues/549)).
- Split the generated internal companion into sealed Model and Template variants. Ordinary models retain no deferred
  actions; every owned Template node carries persistent recipe identity and paths, while pre-existing ordinary `LINK`
  targets retain their identity. Direct Template relationship assignment, including `LINK`, is rejected with rehydration
  guidance ([#438](https://github.com/klum-dsl/klum-ast/issues/438)).
- Defined copy-source behavior: ordinary completed models and Maps are value-only; marked Templates add immutable recipe
  replay; same-session unsealed Builders add an ephemeral dehydrated snapshot of pending actions without identity
  conversion. Sealed and cross-session Builders are rejected.
- Generated public Builder contracts now expose the same-model active-Builder `copyFrom` overload, so Builder lifecycle
  merges are statically checked without exposing internal Builder implementations ([#644](https://github.com/klum-dsl/klum-ast/issues/644)).
- `applyLater`/`scheduleApplyLater` now reject every phase at or after `INSTANTIATE` (40) immediately and direct
  completed-model work to `ModelVisitingPhaseAction`.

## Templates, serialization, and Jackson

- Templates remain DSL Object recipes and rehydrate into fresh Builder graphs on every application. Template `applyLater`
  recipes are stored in immutable serializable recipe state, cloned on replay, and validated when the Template
  materializes. Captured values must be serializable and captured Builders are rejected. Java serialization preserves
  graph-wide Template identity without serializing Builders, Construction sessions, scopes, or mutable recipe collections.
- Completed-model companion state is serializable. Technical metadata rejects non-serializable values immediately.
- Defined Jackson as asymmetric external-format interoperability rather than Klum persistence: managed import binds foreign
  data through Builders, completed models export through ordinary Jackson APIs, and KlumAST adds no wire-format or producer
  metadata. External version fields and migration adapters remain Schema-owned
  ([#447](https://github.com/klum-dsl/klum-ast/issues/447),
  [ADR 0009](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0009-jackson-interoperability.md)).
- Jackson serialization rejects marked Templates, including nested values, so JSON cannot silently discard recipe actions.
- Jackson import now binds resolved public configuration properties into root and owned child Builders between `PostCreate` and
  `PostApply`, then runs one normal lifecycle, materialization, validation, and verification pipeline. Missing input keeps
  initializer/default behavior; present values, `null`, and containers replace authoritatively. Resolved `@JsonProperty`,
  `@JsonAlias`, naming strategies, mixins, access/ignore rules, and unknown-property policy are honored without ambient
  Templates or copy/overwrite semantics ([#439](https://github.com/klum-dsl/klum-ast/issues/439),
  [#251](https://github.com/klum-dsl/klum-ast/issues/251),
  [ADR 0009](https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0009-jackson-interoperability.md)).
  Explicit type-level custom deserializers remain the opt-out.
- Added `KlumJacksonImporter` and `KlumJacksonInput` as the explicit Jackson 2 import seam. Caller-owned mapper/reader
  configuration is captured without mutation; root, value-only Template, active-session Builder, and existing-Builder
  application modes each consume one parser/tree/Map input ([#463](https://github.com/klum-dsl/klum-ast/issues/463)).
- Added executable asymmetric YAML interoperability evidence: one foreign input is imported through one Builder lifecycle
  and ordinary Jackson export emits an intentionally enriched, separately owned projection. Import/export fixtures cover
  foreign aliases, composition, polymorphism, null and unknown-field policy, `LINK`, Templates, diagnostics, custom
  serializers, and the absence of Klum wire metadata; no round-trip or Jackson-owned layering contract is introduced
  ([#464](https://github.com/klum-dsl/klum-ast/issues/464)).
- Jackson `LINK` import is reference-only. Explicit Jackson identities with `alwaysAsId`, custom property codecs, and
  custom `ObjectIdResolver`s preserve completed targets and same-session Builder identity across backward and forward
  references, including Collection and Map `LINK`s. Inline input fails with focused mapping errors. Export requires an
  explicit projection and may use identity, omission, scalar/custom structure, or a deliberate inline serializer; Owner and
  Role remain framework-managed ([#440](https://github.com/klum-dsl/klum-ast/issues/440)).
- Jackson views, inclusion, formats, Simple Value codecs, mixins, and polymorphic owned DSL subtypes work without replacing
  Klum construction. `@JsonCreator`, direct model mutators, foreign Jackson Builders, completed-model owned deserializers,
  and managed/back references cannot take over Builder allocation. The former public `KlumValueInstantiator` and
  `SettableKlumBeanProperty` extension classes remain removed.

# 3.0.1
- New annodocimal version, ignores irrelevant inner class entries in class files

# 3.0.0

- Support for Groovy 2 has been dropped, support for Groovy 5 has been added
- Minimum Java version is now 17
- Only include base groovy module instead of groovy-all in gradle plugins
- Gradle plugins now support versions as strings ("5", "5.0" or "5.0.5") or int (5) instead of the GroovyVersion enum, which is deprecated. 

# 2.2.0
- Breaking change: Dropped manualValidation() support
- Minor Breaking change: `toString()` methods are not created anymore. If needed, they can still be generated using the default Groovy `@ToString` annotation. 
- Validation improvements (see [Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation) [#395](https://github.com/klum-dsl/klum-ast/issues/395))
  - Validation-phase is split into validation and verify phases.
  - Provided new (preliminary) methods to explicitly create validation issues. This allows validation issues to be created in earlier phases, as well as multiple issues in a single validation/lifecycle method.
  - further validation issues can explicitly be suppressed for specific fields and specific maximum levels
  - New annotation `@Optional` as alias for `@Validate(Validate.Ignore)`
  - VerifyPhase can now be skipped using system property `klum.validation.skipVerify`
  - Results of a complete structure can be retrieved using `Validator.getValidationResultsFromStructure(Object)` or verified later using `Validator.verifyStructure(Object)`
  - Deprecation checks now run in the new early validation phase and check only for manually set values, not values created by later phases
  - new annotation `@Notify` to raise issues if a field is set or unset in the early validation phase.
  - new module: klum-ast-bean-validation to provide Bean Validation integration (JSR380). (see [Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation#Validation-levels-and-JSR380) [#395](https://github.com/klum-dsl/klum-ast/issues/395))
  - Validation methods can be collected into inner classes annotated with `@Validate` (see [Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation#on-in-inner-classes) and [#415](https://github.com/klum-dsl/klum-ast/issues/415))
- Added a bom with all module versions (`com.blackbuild.klum.ast:klum-ast-bom`) for easier dependency management. Note that this BOM is already applied by the gradle plugins

## Bugfixes
- `StructureUtil.getPathOfFieldContaining()` and therefore `@Role` fields ignored fields where the value was actually a subclass of the field type.
- CopyFrom and templating failed on primitive values. See [#400](https://github.com/klum-dsl/klum-ast/issues/400)
- modelPath / Validation path was off if using various complex inner factories or AutoCreates

# 2.1.4/2.1.5
- Prevent ConcurrentModificationExceptions when calling `applyLater` from a lifecycle method

# 2.1.3
- `StructureUtil.visit()` and `StructureUtil.deepFind()` should ignore `Owner` and `Link` fields (see [#396](https://github.com/klum-dsl/klum-ast/issues/396))
- internal: `StructureUtil.deepFind()` should internally use a visitor instead of duplicating logic (see [#397](https://github.com/klum-dsl/klum-ast/issues/397)) 

# 2.1.2
- Validate problems should include the structure path along with the breadcrumb path

# 2.1.1
- KlumValidationException should omit empty KlumValidationResult instances.

# 2.1.0
No new features, but all deprecated features from 2.0.0 have been removed. All dropped methods are correctly declared in 2.0.0,
along with their migration paths.

## Dropped methods and features (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)):
- The `@Validation` annotation. Use `@Validate` on class level instead.
- Creator methods on the model class have been dropped
  - `X.create*()` -> `X.Create.*()`
- The generated `validate()` method. Use `Validator.validate()` instead. This means that creating own `validate()` methods is legal again.
- Template-specific methods are now pooled in a new `BoundTemplateHandler` class, which is accessible for as static Field Template.
  - `X.withTemplate()` -> `X.Template.With()`
  - `X.withTemplates()` -> `X.Template.WithAll()`
  - `X.Create.TemplateFrom()` -> `X.Template.CreateFrom()`
  - `X.Create.AsTemplate()` -> `X.Template.Create()`
  - `withTemplate()` and `withTemplates()` are now deprecated, use the new methods instead.

# 2.0.0
## New Features
- New Field Type: BUILDER: Getters are protected or private in model, but dsl methods are public
- Compatibility with Groovy 3 and 4. KlumAST is currently still built with Groovy 2.4 (for compatibility with Jenkins). Tests are run with Groovy 3 and 4 as well.
- Replace basic jackson transformation with a dedicated (beta) JacksonModule (see [Jackson Integration](https://github.com/klum-dsl/klum-ast/wiki/Migration))).
- First steps for Layer3 models. (see [Layer3](https://github.com/klum-dsl/klum-ast/wiki/Layer3))    
- Split model creation into distinct phases (see [#156](https://github.com/klum-dsl/klum-ast/issues/156), [#155](https://github.com/klum-dsl/klum-ast/issues/155),[#187](https://github.com/klum-dsl/klum-ast/issues/187) and [Model Phases](https://github.com/klum-dsl/klum-ast/wiki/Model-Phases))
- New Phases:
  - PostTree: is run after the model is completely realized ([#280](https://github.com/klum-dsl/klum-ast/issues/280)
  - AutoCreate: automatic creation of null fields ([#275](https://github.com/klum-dsl/klum-ast/issues/275)
  - Owner: Sets owners and calls owner methods ([#284](https://github.com/klum-dsl/klum-ast/issues/284))
  - AutoLink: Links fields to other fields in the model ([#275](https://github.com/klum-dsl/klum-ast/issues/275)
  - Defaults: sets default values ([#196](https://github.com/klum-dsl/klum-ast/issues/196)
  - Validate: Validation of the model
- In addition to lifecycle methods, fields of type `Closure` can now be used to define model provided (instead of schema provided) lifecycle methods. These closures will be executed in their respective Lifecycle phases.
- default implementation: by providing the attribute `defaultImpl` on either `@DSL` or `@Field`, one can allow the creation of non-polymorphic field methods even for interfaces and abstract types. (see [Default Implementations](https://github.com/klum-dsl/klum-ast/wiki/Basics#default-implementations))
- Creator methods have been moved to a separate creator class (see [#76](https://github.com/klum-dsl/klum-ast/issues/76)), creator methods on the model class have been deprecated (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)). 
- Custom creator classes can be provided (see [Factory Classes](https://github.com/klum-dsl/klum-ast/wiki/Factory-Classes))
- Methods of creator classes (including custom creators) are included in collection factories (see [#300](https://github.com/klum-dsl/klum-ast/issues/300) and [Factory Classes](https://github.com/klum-dsl/klum-ast/wiki/Factory-Classes#Creator-methods-and-collection-factories))
- Creator class also supports creating templates from scripts (files or URLS) (see [Templates](https://github.com/klum-dsl/klum-ast/wiki/Templates) and [#322](https://github.com/klum-dsl/klum-ast/issues/322))
- Switch annotation validation to [KlumCast](https://github.com/klum-dsl/klum-cast) Framework (see [#312](https://github.com/klum-dsl/klum-ast/issues/312)))
- Generate Documentation for almost all generated methods via [AnnoDocimal](https://github.com/blackbuild/anno-docimal) (see [#197](https://github.com/klum-dsl/klum-ast/issues/197)))
- [Gradle Plugin](https://github.com/klum-dsl/klum-ast/wiki/Gradle-Plugins) for easier project setup
- Various owner improvements:
  - Owner targets now can be transitive, i.e. be filled with the value of an ancestor of the specified type (instead of the direct owner) (see [Transitive Owners](https://github.com/klum-dsl/klum-ast/wiki/Basics#transitive-owners) and [#49](https://github.com/klum-dsl/klum-ast/issues/49))
  - Ower fields can be filled with the actual root of the model. This works even if no explicit owner field is present (see [Root Owner](https://github.com/klum-dsl/klum-ast/wiki/Basics#root-owners))
  - Owner objects can be converted before handing them to owner fields or methods (see [Owner Converters](https://github.com/klum-dsl/klum-ast/wiki/Basics#owner-converters) and [#189](https://github.com/klum-dsl/klum-ast/issues/189))
  - New `@Role` annotation to infer the name of the owner field containing an object (see [Role fields](https://github.com/klum-dsl/klum-ast/wiki/Layer3#role-fields) and [#86](https://github.com/klum-dsl/klum-ast/issues/86))
- Overwrite strategies for `copyFrom` and templates (see [Copy Strategies](https://github.com/klum-dsl/klum-ast/wiki/Copy-Strategies), [#309](https://github.com/klum-dsl/klum-ast/issues/309), [#348](https://github.com/klum-dsl/klum-ast/issues/348))
- Multiple calls to a single object closure now configure the same object instead of completely overriding the previous field, the same for map entries using the same key. (see [#325](https://github.com/klum-dsl/klum-ast/issues/325)). While this is a more natural behaviour, it might break existing code in some corner cases, see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)).
- Breadcrumbs: each Method or closures encountered while creating model is used to setup a breadcrumb path. This path is used in exceptions to identify the location of the problem in the scripts, which is especially handy when the model is split over various scripts. (see [Exception Handling](https://github.com/klum-dsl/klum-ast/wiki/Exception-Handling) and [#264](https://github.com/klum-dsl/klum-ast/issues/264))
- Rework exception handling as a whole, this includes a new hierarchy of exceptions (see [Exception Handling](https://github.com/klum-dsl/klum-ast/wiki/Exception-Handling)) which contain relevant information about the phase in which the exception occured as well as the object which caused the exception. This is especially useful for validation exceptions. (see [#149](https://github.com/klum-dsl/klum-ast/issues/149) and [#288](https://github.com/klum-dsl/klum-ast/issues/288))
- Validations are now all executed, even if exceptions are encountered. All violations are aggregated into a single `KlumValidationException` which is thrown at the end of the phase (see [#146](https://github.com/klum-dsl/klum-ast/issues/146))
- New `FromMap` factory to allow a "poor man's deserialization" (see [Convenience Factories](https://github.com/klum-dsl/klum-ast/wiki/Convenience-Factories#Map) and [#359](https://github.com/klum-dsl/klum-ast/issues/359)) 
- DefaultValues annotations provide an annotation based way to set default values in Layer3 scenarios (see [Default Values](https://github.com/klum-dsl/klum-ast/wiki/Default-Values#DefaultValues-annotation) and [#361](https://github.com/klum-dsl/klum-ast/issues/361))
- `@Cluster`-Fields create now a factory closure for that field, containing only the cluster members (see [#365](https://github.com/klum-dsl/klum-ast/issues/365))
- `applyLater` methods for all objects that can be used to hook closures to be applied in later phases (see [Model Phases](https://github.com/klum-dsl/klum-ast/wiki/Model-Phases))
- Validations have additional levels (WARNING, DEPRECATION and INFO) that can be set for each individual validation. Non error validations to lead to failure in the model (see [#145](https://github.com/klum-dsl/klum-ast/issues/145) and [Validation](https://github.com/klum-dsl/klum-ast/wiki/Validation))

## Improvements
- Creator classes also support methods creating multiple instances at once (see [#319](https://github.com/klum-dsl/klum-ast/issues/319))
- CopyFrom now creates deep clones (see [#36](https://github.com/klum-dsl/klum-ast/issues/36))
- `boolean` fields are never validated (makes no sense), `Boolean` fields are evaluated against not null, not against Groovy Truth (i.e. the field must have an explicit value assigned) (see [#223](https://github.com/klum-dsl/klum-ast/issues/223))
- Provide `@Required` as an alternative to an empty `@Validate` annotation (see [#221](https://github.com/klum-dsl/klum-ast/issues/221))
- `EnumSet` fields are now supported. Note that for enum sets a copy of the underlying set is returned as opposed to a readonly instance. (see [#249](https://github.com/klum-dsl/klum-ast/issues/249))
- Converter methods are now honored for Alternatives methods as well. (see [#270](https://github.com/klum-dsl/klum-ast/issues/270))
- `@Validate` now can be placed on classes. This effectively replaces `@Validate(option=Validation.Option.VALIDATE_UNMARKED)`, which is internally converted to the new format (see [#276](https://github.com/klum-dsl/klum-ast/issues/276)). The `@Validation` annotation is deprecated.
- Sanity check: Key Fields must not have `@Owner` or `@Field` annotations.
- Selector members for `@LinkTo` annotations allows to determine the link source from the provider based on the value of another field (see [#302](https://github.com/klum-dsl/klum-ast/issues/302))
- @LinkTo now correctly handles empty collections/maps as target
- Allow a custom key-provider function for `createFrom(URL)` and `createFrom(File)` 
- `@Cluster` can also be placed on fields, which will be converted into a getter method (see [#366](https://github.com/klum-dsl/klum-ast/issues/366))
- `@Cluster` can be combined with `@AutoCreate` to auto create all members of the annotated cluster (see [#363](https://github.com/klum-dsl/klum-ast/issues/363))
- Templates that where active during an object's creation are now stored in the proxy of that object and will be applied to any object created by that object in later phases (usually AutoCreate) (see [#377](https://github.com/klum-dsl/klum-ast/issues/377))
- `@DefaultValues` has a `valueTarget` member that is used to remap the `value` member of the targeted annotation to a specific field (see [#370](https://github.com/klum-dsl/klum-ast/issues/370))
- new Layer3 `@DefaultApply` annotation that can be used for complex, schema-controlled default values (see [#370](https://github.com/klum-dsl/klum-ast/issues/370) and [Default Values](https://github.com/klum-dsl/klum-ast/wiki/Default-Values#DefaultValues-annotation))

## Deprecations (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration)):
- The `@Validation` annotation is deprecated. Use `@Validate` on class level instead.
- creator methods on the model class have been deprecated.
- The generated `validate()` method is now deprecated, use `KlumInstanceProxy.validate()` instead. This means that creating own `validate()` methods is legal again.
- Template-specific methods are now pooled in a new `BoundTemplateHandler` class, which is accessible for as static Field Template.
  - `X.withTemplate()` -> `X.Template.With()`
  - `X.withTemplates()` -> `X.Template.WithAll()`
  - `X.Create.TemplateFrom()` -> `X.Template.CreateFrom()`
  - `X.Create.AsTemplate()` -> `X.Template.Create()`
  - `withTemplate()` and `withTemplates()` are now deprecated, use the new methods instead.

## Breaking changes (see [Migration](https://github.com/klum-dsl/klum-ast/wiki/Migration))
- it is a compile error to place the `@Validate` annotation on a boolean field.
- KlumAST is split into different modules, klum-ast-compile is compile-time only,
  klum-ast-runtime is needed for runtime as well. This completes
  the changes started in 1.2.0
- In order for the serialization in jackson to work, the new klum-ast-jackson module needs to be included in the project (see [Jackson Integration](https://github.com/klum-dsl/klum-ast/wiki/Jackson-Integration))
- The naming of virtual fields is changed, now the virtual field
  is identical to the method name (previously, the first element of the camel
  cased method name was removed).
- methods named `doValidate` are no longer considered Validate methods by default.
- Static Type Checking for Configuration Scripts does not (yet) work under Groovy 3
- Previously, only public methods were checked for illegal write access. This has been changed to include all visibilities. Protected methods that are conceptually write access methods must now also be annotated with @Mutator, otherwise a compile error is thrown.
- Owner fields are now set in a later phase, meaning that they are not yet set when apply closures are resolved. This logic must be moved to a later phase (postTree), for example using lifecycle closures.
- Default values are no longer a modification of the getter but rather explicitly set during the 'default' phase. This might result in subtle differences in the behavior, especially when using a non-template as template / target for
 `copyFrom`. Make sure to create template instances with `Create.Template` if you want to use them as templates.
- `withTemplates(Map, Closure)` now only accepts anonymous templates, i.e. the signature changed from `withTemplates(Map<Class, Object>, Closure)` to `withTemplates(Map<Class, Map<String, Object>, Closure)`. Calls using concrete templates now must use `withTemplates(List<Object>, Closure)` instead.

## Fixes
- `Required.value()` is correctly translated to `Validate.message()` (see [#373](https://github.com/klum-dsl/klum-ast/issues/373)).
- `CopyHandler` ignores the `IGNORED` field type (see [#374](https://github.com/klum-dsl/klum-ast/issues/374)).
- Root objects of the wrong type are ignored, allowing partial Models for testing.
- Unqualified links in `KlumFactory` Javadoc no longer cause Javadoc failures.
- The Gradle model plugin uses the script class rather than the model class in its model descriptor.
- `ClusterModel` annotation filtering retrieves the actual `Field` object.
- Script names are determined correctly when a filename contains multiple dots (see [#328](https://github.com/klum-dsl/klum-ast/issues/328)).
- Generated RW classes are public, so static type checking works when owner and child are in different packages.
- The AnnoDocimal inner-enum final-modifier fix is included.
- `@Overrides` is not copied to RW delegation methods (see [#340](https://github.com/klum-dsl/klum-ast/issues/340)).
- Polymorphic virtual setters work correctly (see [#250](https://github.com/klum-dsl/klum-ast/issues/250)).
- Converter methods honor default values (see [#268](https://github.com/klum-dsl/klum-ast/issues/268)).
- Nested generic types work correctly (see [#248](https://github.com/klum-dsl/klum-ast/issues/248)).
- Converter methods work for maps of DSL Objects (see [#242](https://github.com/klum-dsl/klum-ast/issues/242)).
- Generated classes with generic field types and generic factories such as `List.of` are valid (see [#243](https://github.com/klum-dsl/klum-ast/issues/243)).
- A default delegate may be a getter rather than a field (see [#244](https://github.com/klum-dsl/klum-ast/issues/244)).
- `apply` accepts a Map-only call (see [#241](https://github.com/klum-dsl/klum-ast/issues/241)).
- Key fields work correctly in hierarchies (see [#238](https://github.com/klum-dsl/klum-ast/issues/238)).
- Creator-method visibility is correct (see [#232](https://github.com/klum-dsl/klum-ast/issues/232)).
