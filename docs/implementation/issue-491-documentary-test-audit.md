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
| [`Validation.md` — Suppress Further Issues](../user/Validation.md#suppress-further-issues) | #407 | `ValidationSpec.suppresses a later non-error issue for one member` | Partially aligned: the feature already carries `@Issue("407")`, `@Tag("documentary")`, and an `@See` to this heading. The other Validation examples remain queued for a later #491 selection. |

## In-scope user-visible DSL inventory

The following maps the current feature-oriented user-documentation corpus to the best local historical issue evidence. `Not yet
aligned` means the documentation and executable behavior exist, but this audit has not yet established a readable,
annotated documentary path. It is a queue for a later #491 slice, not a new behavioral contract.

| Documentation element | Responsible issue evidence | Executable coverage evidence | Status / next audit action |
| --- | --- | --- | --- |
| `Basics.md` — DSL object, fields, keys, owners, relationships, field types | Historical transformation issues in `TransformSpec` (including #21, #22, #35, #54, #56, #58, #80, #121, #126–#128, #172, #249–#250) | `TransformSpec`, `OwnerReferencesSpec`, `RWClassSpec` | Factory construction aligned here; split the remaining broad page by stable heading before adding documentary examples. |
| `Alternatives-Syntax.md` — Strip Common Suffixes | #77, #270, #544 | `AlternativesSpec` | Aligned with `AlternativesSpec.uses stripped suffixes for alternative method names`. Other alternatives sections remain candidates for later selection. |
| `Convenience-Factories.md` | #114, #195, #198 | `ConvenienceFactoriesSpec` | Not yet aligned. |
| `Converters.md` | #148, #198, #243, #300, #319 | `ConverterSpec` | Not yet aligned. |
| `Copy-Strategies.md` | #36, #309, #348, #374, #400 | `CopyHandlerTest`, `CopyHandlerRuntimeTest`, `OverwriteStrategyTest` | Not yet aligned. |
| `Default-Values.md` — Other fields (`field`) | #318 | `DefaultValuesSpec`, `DefaultValuesDocumentaryTest` | Aligned with `DefaultValuesDocumentaryTest.defaults a release identifier from its configured name`. The Layer 3 annotation variants remain separate follow-ups under #361 and #370. |
| `Inheritance.md` | #130, #138 | `InheritanceSpec` | Not yet aligned. |
| `Templates.md` | #82, #322, #368, #376 | `TemplatesSpec`, `BoundTemplatesSpec` | Not yet aligned; replace the stale `TemplateSpec` reference when this page is selected. |
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
- `Roadmap.md`, `Migration.md`, `_Sidebar.md`, and `_Footer.md` are release, migration, or navigation material rather
  than user-visible DSL feature elements. They do not require a feature documentary test of their own.
- #454's Layer 3 terminology rewrite remains a product/documentation-placement dependency. The existing #474 example is
  retained, but this audit does not broaden it.

## Follow-up queue for human action

No GitHub issues were created by this local-only slice. Before a subsequent #491 implementation slice, a maintainer
should choose the next stable documentation heading and either confirm its governing historical issue or create a
focused follow-up for an unclear contract. The clearest candidates are `Templates.md` and
`Convenience-Factories.md`; the Layer 3 annotation variants in `Default-Values.md` remain bounded by #361/#370, while
`Completed-Object-Support.md` and the legacy technique pages need ownership confirmation first.
