# Issue 491 — Documentary-test audit inventory

Tracking issue: [#491 — Audit DSL documentation for documentary test coverage and traceability](https://github.com/klum-dsl/klum-ast/issues/491)

This is the repository-side inventory for the deliberate documentary-test audit. It records the evidence available on
`master` and avoids reclassifying legacy regression tests as user documentation merely because they exercise the same
implementation. The current 4.0 documentation root is `docs/user/`; #456 owns its eventual versioned URL and hosting
policy. Each record maps a user-visible documentation element or feature to readable executable evidence, or records a
concrete exception or follow-up. It does not require a separate documentary test for every individual code fence.

## Existing documentary evidence

The following aligned examples are already present in the executable suite:

| Documentation element | Responsible issue | Executable evidence | Audit result |
| --- | --- | --- | --- |
| [`Layer3.md` — Automatic creation and linking](../user/Layer3.md#automatic-creation-and-linking) | #474 | `OptionalLinkRelationshipTest.optional relationships retain local composition and aggregation identity for single List and Map entries` | Already aligned with `@Issue`, `@Tag("documentary")`, and `@See`. #454 owns the wider Layer 3 terminology rewrite. |
| [`Basics.md` — Factory construction](../user/Basics.md#factory-construction) | #76 (closed: move creator methods into a creator class) | `FactoryTest` exercised the generated factory but did not provide a readable linked documentary example. | Aligned by this slice with `FactoryConstructionTest.builds a completed deployment configuration with Create.With`. |
| [`Default-Values.md` — Other fields (`field`)](../user/Default-Values.md#other-fields-field) | #318 (closed: make `@Default` a lifecycle method) | `DefaultValuesSpec` covered field defaults but did not provide a readable linked documentary example. | Aligned by this slice with `DefaultValuesDocumentaryTest.defaults a release identifier from its configured name`. |
| [`Templates.md` — Creating templates](../user/Templates.md#creating-templates) | #491 audit selection; #322 for `CreateFrom` | `TemplatesSpec`, `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.creates an unkeyed reusable template without lifecycle callbacks` and `creates a template from a DelegatingScript file`. |
| [`Templates.md` — `copyFrom()`](../user/Templates.md#copyfrom) | #491 audit selection | `TemplatesSpec`, `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.copies a template into one completed service configuration`. |
| [`Templates.md` — Template.With() and anonymous templates](../user/Templates.md#templatewith) | #376 (closed: Closure Templates) | `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.applies one scoped template to multiple service configurations` and `applies named values through an anonymous scoped template`. |
| [`Templates.md` — Collection factories](../user/Templates.md#templates-for-collection-factories) | #82 (closed: collection factory closure contract) | `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.applies one collection-factory template to every created server`. |
| [`Templates.md` — Template.WithAll()](../user/Templates.md#templatewithall) | #376 (closed: Closure Templates) | `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.applies templates for multiple configuration types in one scope`. |
| [`Templates.md` — Abstract classes](../user/Templates.md#templates-for-abstract-classes) | #491 audit selection | `TemplatesSpec`, `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.creates a template implementation for an abstract configuration type`. |
| [`Templates.md` — Order of precedence](../user/Templates.md#order-of-precedence) | #491 audit selection | `TemplatesSpec`, `BoundTemplatesSpec` | Aligned with `TemplatesDocumentaryTest.lets child templates and explicit configuration override parent defaults`. |
| [`Templates.md` — `applyLater`](../user/Templates.md#applylater-and-templates) | #376 (closed: Closure Templates) | `BoundTemplatesSpec`, `ApplyLaterBoundarySpec` | Aligned with `TemplatesDocumentaryTest.replays a template applyLater recipe for each completed configuration`; rejection and capture-boundary cases remain focused regression coverage. |
| [`Convenience-Factories.md` — Script classes](../user/Convenience-Factories.md#script-classes) | #491 audit selection | `ConvenienceFactoriesSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.loads a completed deployment from a script class`. |
| [`Convenience-Factories.md` — Delegating Scripts](../user/Convenience-Factories.md#delegating-scripts) | #491 audit selection | `ConvenienceFactoriesSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.uses a DelegatingScript class as keyed configuration content`. |
| [`Convenience-Factories.md` — Script and delegating script for collections and maps](../user/Convenience-Factories.md#script-and-delegating-script-for-collections-and-maps) | #198 | `ConvenienceFactoriesSpec`, `AsBuilderSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.applies DelegatingScript recipes to list and map relationship factories`; materializing regular Script rejection remains boundary coverage. |
| [`Convenience-Factories.md` — Text](../user/Convenience-Factories.md#text) | #114 | `ConvenienceFactoriesSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.loads keyed configuration from text`. The optional class-loader overload is an invocation variation, not a separate tutorial path. |
| [`Convenience-Factories.md` — File or URL](../user/Convenience-Factories.md#file-or-url) | #491 audit selection | `ConvenienceFactoriesSpec`, `ScriptTest` | Aligned with `ConvenienceFactoriesDocumentaryTest.derives a keyed configuration name from a file or URL`. The custom key-provider overload remains focused behavior coverage in `ScriptTest`. |
| [`Convenience-Factories.md` — Classpath](../user/Convenience-Factories.md#classpath) | #491 audit selection | `ConvenienceFactoriesSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.discovers a deployment entry point from its classpath marker`; abstract and DelegatingScript entry-point variants remain focused coverage. |
| [`Convenience-Factories.md` — Map](../user/Convenience-Factories.md#map) | #359 | `FactoryTest`, `AsBuilderSpec` | Aligned with `ConvenienceFactoriesDocumentaryTest.adapts external map keys in a custom factory`. `Create.AsBuilder.FromMap` remains an active-session extension boundary rather than a standalone tutorial. |
| [`Converters.md` — Field-Based Converters](../user/Converters.md#field-based-converters) | #148 | `ConverterSpec` | Aligned with `ConvertersDocumentaryTest.converts timestamp input for a simple field and map entry`. The zero-argument and multi-argument overloads, DSL-object source-visibility rule, and generated-method reflection remain focused behavior or integration boundaries. |
| [`Converters.md` — Factory Method Converters](../user/Converters.md#factory-method-converters) | #148 | `ConverterSpec` | Aligned with `ConvertersDocumentaryTest.uses a named factory method as an owned DSL relationship creator`, which demonstrates both root and owning-Model invocation. Prefix variations, ambiguous signatures, keyed/list/map variants, generic-placeholder behavior (#243), and DelegatingScript collection input (#198) remain focused coverage. |
| [`Converters.md` — Factory Classes](../user/Converters.md#factory-classes) | #148 | `ConverterSpec` | Aligned with `ConvertersDocumentaryTest.uses a converter factory class for convention and annotation-based inputs`. Field-local registration, subtype discovery, and collection-factory propagation (#300, #319) remain focused extension coverage. |
| [`Converters.md` — Customization](../user/Converters.md#customization) | #148 | `ConverterSpec` | Aligned with `ConvertersDocumentaryTest.uses an opt-in URI constructor as a converter`. Method include/exclude filters and prefix changes remain API-reference and focused discovery coverage. |
| [`Validation.md` — Suppress Further Issues](../user/Validation.md#suppress-further-issues) | #407 | `ValidationSpec.suppresses a later non-error issue for one member` | Partially aligned: the feature already carries `@Issue("407")`, `@Tag("documentary")`, and an `@See` to this heading. The other Validation examples remain queued for a later #491 selection. |
| [`Inheritance.md` — Choosing a derived implementation](../user/Inheritance.md#choosing-a-derived-implementation) | #491 audit selection | `InheritanceSpec` | Aligned with `InheritanceDocumentaryTest.configures a derived project through an unkeyed field`. |
| [`Inheritance.md` — Keyed inheritance](../user/Inheritance.md#keyed-inheritance) | #130 | `InheritanceSpec` | Aligned with `InheritanceDocumentaryTest.configures a keyed derived project through an inherited key`. |
| [`Inheritance.md` — Key hierarchy constraints](../user/Inheritance.md#key-hierarchy-constraints) | #130 | `InheritanceSpec` | Intentional exception: this rejection boundary is not a tutorial path. `InheritanceSpec` retains the valid abstract-parent case and invalid keyed-child declarations as focused coverage. Final/abstract typed-factory availability is likewise a focused `InheritanceSpec` API boundary; #138's two-children-before-parent compilation regression remains there rather than becoming a user-facing example. |

## In-scope user-visible DSL inventory

The following maps the current feature-oriented user-documentation corpus to the best local historical issue evidence. `Not yet
aligned` means the documentation and executable behavior exist, but this audit has not yet established a readable,
annotated documentary path. It is a queue for a later #491 slice, not a new behavioral contract.

| Documentation element | Responsible issue evidence | Executable coverage evidence | Status / next audit action |
| --- | --- | --- | --- |
| `Basics.md` — DSL object, fields, keys, owners, relationships, field types | Historical transformation issues in `TransformSpec` (including #21, #22, #35, #54, #56, #58, #80, #121, #126–#128, #172, #249–#250) | `TransformSpec`, `OwnerReferencesSpec`, `RWClassSpec` | Factory construction aligned here; split the remaining broad page by stable heading before adding documentary examples. |
| `Alternatives-Syntax.md` — Strip Common Suffixes | #77, #270, #544 | `AlternativesSpec` | Aligned with `AlternativesSpec.uses stripped suffixes for alternative method names`. Other alternatives sections remain candidates for later selection. |
| `Convenience-Factories.md` — script classes, DelegatingScript recipes, collection/map factories, text, file/URL, classpath, and maps | #114, #198, #359; #491 audit selection for historical seams without one governing issue | `ConvenienceFactoriesSpec`, `ScriptTest`, `FactoryTest`, `AsBuilderSpec`, `ConvenienceFactoriesDocumentaryTest` | Aligned as one dedicated documentary cohort. Class-loader and custom key-provider overloads, abstract entry points, active-session `Create.AsBuilder.FromMap`, and materializing Script rejection remain focused behavior or boundary coverage rather than tutorial paths. |
| `Converters.md` — Field-Based Converters, Factory Method Converters, Factory Classes, and Customization | #148; #198, #243, #300, #319 for focused boundary coverage | `ConverterSpec`, `ConvertersDocumentaryTest` | Aligned as one dedicated documentary cohort. `includeConstructors` has a readable opt-in tutorial; method filters, overload, visibility, generic, ambiguity, collection-factory, and script boundaries remain focused coverage rather than separate tutorials. |
| `Copy-Strategies.md` — Single Object, Collections, Maps, and Nested Annotations | #309; #581 for packaged overwrite-strategy validation | `CopyHandlerTest`, `OverwriteStrategyTest`, `CopyStrategiesDocumentaryTest` | Aligned as one dedicated documentary cohort with `merges a nested service configuration from a template`, `adds template roles to a service configuration`, `merges environment map values from a template`, and `applies the packaged Helm copy policy to a deployment`. |
| `Copy-Strategies.md` — Copy source protocol, strategy lookup, Single Object `INHERIT`/`REPLACE`/`ALWAYS_REPLACE`/`SET_IF_NULL`/`MERGE`, Collections `INHERIT`/`ADD`/`REPLACE`/`SET_IF_EMPTY`/`ALWAYS_REPLACE` and its `null` setter example, Maps `INHERIT`/`FULL_REPLACE`/`ALWAYS_REPLACE`/`SET_IF_EMPTY`/`MERGE_KEYS`/`MERGE_VALUES`/`ADD_MISSING`, and Missing field handling | #36, #309, #348, #374, #400 | `CopyHandlerTest`, `CopyHandlerRuntimeTest`, `OverwriteStrategyTest` | Deliberate exception: these source-session, precedence, compatibility, and exhaustive API-reference rules retain focused coverage rather than individual tutorial methods. |
| `Default-Values.md` — Other fields (`field`) | #318 | `DefaultValuesSpec`, `DefaultValuesDocumentaryTest` | Aligned with `DefaultValuesDocumentaryTest.defaults a release identifier from its configured name`. The Layer 3 annotation variants remain separate follow-ups under #361 and #370. |
| `Inheritance.md` — Choosing a derived implementation and Keyed inheritance | #491 audit selection; #130 for keyed inheritance | `InheritanceSpec`, `InheritanceDocumentaryTest` | Aligned as one dedicated documentary cohort. Final/abstract typed-factory availability, illegal key-hierarchy declarations, and #138's two-children-before-parent compilation regression remain focused boundary coverage rather than tutorial paths. |
| `Templates.md` — Templates cohort: creation, copying, scoped/anonymous/collection templates, WithAll, abstract classes, precedence, and `applyLater` | #491 audit selection; #82, #322, #376 | `TemplatesSpec`, `BoundTemplatesSpec`, `ApplyLaterBoundarySpec`, `TemplatesDocumentaryTest` | Aligned as one dedicated documentary cohort. Internal identity, relationship rejection, serialization, and capture-rejection boundaries remain focused regression coverage rather than tutorial paths. |
| `Validation.md` | #25, #125, #145, #221, #223, #276, #381, #395, #406–#407, #409, #415 | `ValidationSpec` | Partially aligned for **Suppress Further Issues** through the #407-tagged documentary feature above. The other Validation examples remain queued. |
| `Model-Phases.md` | #64, #138, #376 | `LifecycleSpec`, phase tests | Not yet aligned. |
| `Layer3.md` | #454 owns terminology/documentation placement; #474 owns the existing optional-link example | `OptionalLinkRelationshipTest` | One narrow section aligned; defer broader example changes until #454's required terminology grilling. |
| `Completed-Object-Support.md` | ADR 0006 implementation record; issue mapping needs confirmation | `KlumObjectSupport` tests | Contract-to-issue mapping is unclear; require human triage before adding a documentary test. |
| `Builder-First-Migration.md` | Builder-first migration issues, including #474 | `BuilderFirstSpec`, `OptionalLinkRelationshipTest` | Not yet aligned; preserve migration wording and avoid overlapping JSON-4 documentation. |
| `Factory-Classes.md`, `Static-Models.md`, `Advanced-Techniques.md`, `Behind-the-Curtain.md`, `Javadoc.md` | Local legacy source does not establish one unambiguous owning issue | Focused tests exist but are not linked to these pages | Legacy-technique/ownership queue: human triage must identify the governing issue before documentary conversion. `Behind-the-Curtain.md` is already covered by this queue; no separate clarification is needed. |
| `Terms.md`, `Exception-Handling.md`, `FAQ.md`, `Why-aC-is-not-enough.md` | Reference material, not one discrete feature contract | Focused behavior tests exist where applicable | Reference-material classification: no feature documentary test is required merely because the page is validated or contains examples. Select only after a maintainer identifies a stable, user-facing happy path. |
| `Domain-First-Modeling.md` — Smart-home journey | #469 onboarding; its fixture feature is driven by #471 | `SmartHomeJourneyDocumentaryTest.loads a floorplan-specific Model and lets a generic client inspect every window` | Explicit #469 mapping/exemption: the page already names the readable journey test, which carries `@Tag("documentary")` and an `@See` to its heading. Do not add a separate #491 feature-documentary test. |
| `Target-Contract-Modeling.md` — Executable Helm journey | #469 onboarding; its fixture feature is driven by #472 | `HelmTargetContractDocumentaryTest.generates human-readable #release.name Helm values that conform to the golden contract` | Explicit #469 mapping/exemption: the page already names the readable journey test, which carries `@Tag("documentary")` and an `@See` to its heading. Do not add a separate #491 feature-documentary test. |

## Explicit exclusions and coordination boundaries

- `Jackson-Integration.md` and all Jackson/YAML executable coverage are owned by concurrent JSON-4 under #464. This
  audit records that mapping only; it does not edit those files, examples, or tests.
- `Usage.md`, `Home.md`, `Gradle-Plugins.md`, release/migration navigation, and adopter journeys are documentation or
  onboarding coordination surfaces. #456 owns versioned placement, while #469 owns task-oriented onboarding. They are
  not substitutes for feature-level documentary tests.
- The #469 domain-first and target-contract journeys are explicit exceptions to that general coordination rule: their
  named documentary tests are the readable executable evidence for those journey pages, without turning every journey
  step or code fence into a separate #491 feature test.
- `Why-aC-is-not-enough.md` is reference material about the testing boundary. Its classification does not assert that
  the page lacks validation; it only says that the page itself is not a discrete feature contract requiring a
  feature-documentary test.
- `Converters.md` — **Customization** has one stable `includeConstructors` tutorial. `includeMethods`,
  `excludeMethods`, and `excludeDefaultPrefixes` remain API-reference selection controls with focused
  discovery/selection coverage in `ConverterSpec`, not separate documentary examples.
- `Roadmap.md`, `Migration.md`, `_Sidebar.md`, and `_Footer.md` are release, migration, or navigation material rather
  than user-visible DSL feature elements. They do not require a feature documentary test of their own.
- #454's Layer 3 terminology rewrite remains a product/documentation-placement dependency. The existing #474 example is
  retained, but this audit does not broaden it.

## Follow-up queue for human action

No GitHub issues were created by this local-only slice. Before a subsequent #491 implementation slice, a maintainer
should choose the next stable documentation heading and either confirm its governing historical issue or create a
focused follow-up for an unclear contract. The Templates and Convenience Factories cohorts are complete; the Layer 3
annotation variants in `Default-Values.md` remain bounded by #361/#370, while `Completed-Object-Support.md` and the
legacy technique pages need ownership confirmation first.
