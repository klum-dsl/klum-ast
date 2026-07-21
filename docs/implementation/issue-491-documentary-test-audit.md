# Issue 491 — Documentary-test audit inventory

Tracking issue: [#491 — Audit DSL documentation for documentary test coverage and traceability](https://github.com/klum-dsl/klum-ast/issues/491)

This is the repository-side inventory for the deliberate documentary-test audit. It records the evidence available on
`master` before this slice and avoids reclassifying legacy regression tests as user documentation merely because they
exercise the same implementation. #456 owns the eventual versioned documentation URL and hosting policy; until then,
source links use the current `docs/user/` path on `master`.

## Current documentary evidence

Before this slice, the executable suite contained one `@Tag("documentary")` marker:

| Documentation element | Responsible issue | Executable evidence | Audit result |
| --- | --- | --- | --- |
| [`Layer3.md` — Automatic creation and linking](../user/Layer3.md#automatic-creation-and-linking) | #474 | `OptionalLinkRelationshipTest.optional relationships retain local composition and aggregation identity for single List and Map entries` | Already aligned with `@Issue`, `@Tag("documentary")`, and `@See`. #454 owns the wider Layer 3 terminology rewrite. |
| [`Basics.md` — Factory construction](../user/Basics.md#factory-construction) | #76 (closed: move creator methods into a creator class) | `FactoryTest` exercised the generated factory but did not provide a readable linked documentary example. | Aligned by this slice with `FactoryConstructionTest.builds a completed deployment configuration with Create.With`. |
| [`Default-Values.md` — Other fields (`field`)](../user/Default-Values.md#other-fields-field) | #318 (closed: make `@Default` a lifecycle method) | `DefaultValuesSpec` covered field defaults but did not provide a readable linked documentary example. | Aligned by this slice with `DefaultValuesDocumentaryTest.defaults a release identifier from its configured name`. |

## In-scope user-visible DSL inventory

The following maps the current feature-oriented wiki corpus to the best local historical issue evidence. `Not yet
aligned` means the documentation and executable behavior exist, but this audit has not yet established a readable,
annotated documentary path. It is a queue for a later #491 slice, not a new behavioral contract.

| Documentation element | Responsible issue evidence | Executable coverage evidence | Status / next audit action |
| --- | --- | --- | --- |
| `Basics.md` — DSL object, fields, keys, owners, relationships, field types | Historical transformation issues in `TransformSpec` (including #21, #22, #35, #54, #56, #58, #80, #121, #126–#128, #172, #249–#250) | `TransformSpec`, `OwnerReferencesSpec`, `RWClassSpec` | Factory construction aligned here; split the remaining broad page by stable heading before adding documentary examples. |
| `Alternatives-Syntax.md` | #77, #270 | `AlternativesSpec` | Not yet aligned. |
| `Convenience-Factories.md` | #114, #195, #198 | `ConvenienceFactoriesSpec` | Not yet aligned. |
| `Converters.md` | #148, #198, #243, #300, #319 | `ConverterSpec` | Not yet aligned. |
| `Copy-Strategies.md` | #36, #309, #348, #374, #400 | `CopyHandlerTest`, `CopyHandlerRuntimeTest`, `OverwriteStrategyTest` | Not yet aligned. |
| `Default-Values.md` — Other fields (`field`) | #318 | `DefaultValuesSpec`, `DefaultValuesDocumentaryTest` | Aligned with `DefaultValuesDocumentaryTest.defaults a release identifier from its configured name`. The Layer 3 annotation variants remain separate follow-ups under #361 and #370. |
| `Inheritance.md` | #130, #138 | `InheritanceSpec` | Not yet aligned. |
| `Templates.md` | #82, #322, #368, #376 | `TemplatesSpec`, `BoundTemplatesSpec` | Not yet aligned; replace the stale `TemplateSpec` reference when this page is selected. |
| `Validation.md` | #25, #125, #145, #221, #223, #276, #381, #395, #406–#407, #409, #415 | `ValidationSpec` | Not yet aligned. |
| `Model-Phases.md` | #64, #138, #376 | `LifecycleSpec`, phase tests | Not yet aligned. |
| `Layer3.md` | #454 owns terminology/documentation placement; #474 owns the existing optional-link example | `OptionalLinkRelationshipTest` | One narrow section aligned; defer broader example changes until #454's required terminology grilling. |
| `Completed-Object-Support.md` | ADR 0006 implementation record; issue mapping needs confirmation | `KlumObjectSupport` tests | Contract-to-issue mapping is unclear; require human triage before adding a documentary test. |
| `Builder-First-Migration.md` | Builder-first migration issues, including #474 | `BuilderFirstSpec`, `OptionalLinkRelationshipTest` | Not yet aligned; preserve migration wording and avoid overlapping JSON-4 documentation. |
| `Factory-Classes.md`, `Static-Models.md`, `Advanced-Techniques.md`, `Javadoc.md` | Local legacy source does not establish one unambiguous owning issue | Focused tests exist but are not linked to these pages | Queue human triage for the governing issue before documentary conversion. |
| `Terms.md`, `Exception-Handling.md`, `FAQ.md` | Reference material, not one discrete feature contract | Focused behavior tests exist where applicable | Excluded from the first feature-example roster; select only after a maintainer identifies a stable, user-facing happy path. |

## Explicit exclusions and coordination boundaries

- `Jackson-Integration.md` and all Jackson/YAML executable coverage are owned by concurrent JSON-4 under #464. This
  audit records that mapping only; it does not edit those files, examples, or tests.
- `Usage.md`, `Home.md`, `Gradle-Plugins.md`, release/migration navigation, and adopter journeys are documentation or
  onboarding coordination surfaces. #456 owns versioned placement, while #469 owns task-oriented onboarding. They are
  not substitutes for feature-level documentary tests.
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
