# Issue 544 — 4.0 user-documentation content audit

Tracking issue: [#544 — Finalize KlumAST 4.0 user-documentation content before the first RC](https://github.com/klum-dsl/klum-ast/issues/544)

This is the repository-local, sanitized content-audit record for the current 4.x authoring tree. It records the
reviewed source, settled corrections, and the limits deliberately left to their owners. It does not record credentials,
commands, raw GitHub responses, renderer output, or release telemetry.

## Audit basis and boundary

- Reviewed source: `docs/user/` and `CHANGES.md` history through 3.0.1, at base revision
  `85f08426b73025a8dcfa25234b96c53e31ae93b8` plus this issue branch's corrections.
- Documentary-test traceability source: [issue #491](https://github.com/klum-dsl/klum-ast/issues/491) and
  [the repository inventory](issue-491-documentary-test-audit.md).
- Onboarding consistency source: [#469](https://github.com/klum-dsl/klum-ast/issues/469).
- Layer 3 boundary: [#454](https://github.com/klum-dsl/klum-ast/issues/454) owns unsettled terminology, variants, and
  representative-contract decisions. This audit corrects only settled Builder-first wording.
- Renderer, hosting, version aliases, and publication boundary: [#456](https://github.com/klum-dsl/klum-ast/issues/456)
  and ADR 0013. They are not audit findings on documentation content.

## Changelog history comparison

The 3.0.1 release entry contains only the AnnoDocimal dependency update and introduces no separate user-facing DSL
contract. Earlier 3.0/2.x entries were compared with the current feature pages and historical migration sections:
construction/factories, defaults, copy strategies, lifecycle phases, validation, relationships, Templates, converters,
and Gradle setup each retain a current explanation or an intentionally historical migration note. No additional settled
4.0 content correction resulted from that comparison. Missing documentary links remain the bounded #491 roster, and a
version-matched manual Maven contract remains a maintainer decision rather than an inferred promise.

## Page inventory

Every current authoring page was reviewed for audience, 4.0 terminology, links, examples, and its documentary-test
relationship. “Inventory” means the #491 roster records the next traceability action; it does not imply that an
unannotated legacy regression test is a documentary test.

| Pages | Content result | Documentary / follow-up status |
| --- | --- | --- |
| `Home`, `Getting-Started`, `Gradle-Plugins`, `Usage` | Entry route is coherent: start with Gradle onboarding, then choose a modeling shape. `Usage` remains an advanced, manual-setup reference. | Onboarding pages are coordination surfaces; #469 owns executable adopter journeys. |
| `Basics`, `Alternatives-Syntax`, `Inheritance`, `Factory-Classes`, `Convenience-Factories`, `Converters` | Core Builder-first construction and relationship guidance is present. | `Basics` factory construction is linked to `FactoryConstructionTest`; the remaining headings are #491 inventory candidates. |
| `Model-Phases`, `Validation`, `Default-Values`, `Exception-Handling` | Lifecycle/materialization and validation wording is consistent with the 4.0 migration story. | `Default-Values` field default is linked to `DefaultValuesDocumentaryTest`; phase and validation examples remain #491 candidates. |
| `Templates`, `Copy-Strategies` | Template/copy semantics consistently distinguish recipes, ordinary completed objects, and Builders. The stale `TemplateSpec` reference is corrected to `BoundTemplatesSpec`. | #491 should select a readable Template happy path before adding a documentary link. |
| `Completed-Object-Support`, `Static-Models`, `Advanced-Techniques`, `Behind-the-Curtain`, `Javadoc` | Correct 4.0 Builder/completed-object and generated-support guidance, with implementation mechanics separated from ordinary authoring guidance. | #491 records unclear governing-issue ownership for completed-object and legacy-technique pages; maintainer triage is required before documentary conversion. |
| `Jackson-Integration` | Explicitly bounded asymmetric foreign-format integration; no round-trip claim. | #464 owns the executable Jackson/YAML documentary path; no change here. |
| `Domain-First-Modeling`, `Target-Contract-Modeling` | The two independent architectural choices and their executable fixtures agree with #469. | `SmartHomeJourneyDocumentaryTest` and `HelmTargetContractDocumentaryTest` are named in their pages. |
| `Layer3`, `Terms` | Retains only the settled API–Schema–Model pattern and the four roles. A stale RW-interface reference is corrected to generated Builder construction API. | The existing optional-link evidence remains `OptionalLinkRelationshipTest`; #454 owns broader terminology and examples. |
| `Migration`, `Builder-First-Migration`, `FAQ`, `Roadmap`, `_Sidebar`, `_Footer` | Migration routing remains current; the obsolete future-tense 2.x/3.x roadmap is replaced with the 4.0 RC content boundary. `_Footer` is intentionally empty. | Navigation and migration material do not need feature documentary tests. |

## Settled corrections made by this audit

1. Reframed the Home page around the 4.0 Builder-first user journey, removed stale GDSL/DSLD status claims, and linked
   to Gradle onboarding.
2. Replaced the obsolete future-tense historical roadmap with the actual 4.0 content boundary and its acceptance owners.
3. Replaced the Layer 3 page’s deprecated RW-interface wording with the settled generated Builder construction API
   terminology, without choosing any unresolved Layer 3 variant.
4. Repaired the Templates page’s stale test reference and clarified that #491 still owns selection of a documentary
   happy path.
5. Removed the legacy direct-constructor contrast from the scoped-template guidance: Builder-first documentation
   presents the supported construction APIs without implying a direct-constructor entry point.
6. Added first-use links for role/value terminology, modeling-route choices, Layer 3, and the Builder/template/copy
   lifecycle. A page now routes readers to a prerequisite or deeper concept when sidebar reading order has not already
   introduced it; repeated ordinary vocabulary remains unlinked.
7. Added the Home/README model-as-code value proposition and the `Why aC is not enough` guide. It distinguishes local
   model verification and unit tests from target integration tests, without claiming that configuration in Git alone
   proves deployment behavior.
8. Clarified inner validation classes as topic-named validation classes, documented their issue-target spelling, and
   added checked validation-output examples to the Validation page, Home, and README.
9. Expanded Terms with the Schema/Model analogy, lifecycle vocabulary, validation, Templates, and relationships, each
   linked to its detailed explanation rather than duplicated here.
10. Synchronized completed-object validation documentation with the accepted 4.0 API: `getResult()` is target-only and
    `getSubtreeResults()` includes the target plus its owned composition subtree. The ambiguous `getResults()` name is
    removed without a compatibility bridge ([#549](https://github.com/klum-dsl/klum-ast/issues/549)).
11. Removed obsolete pre-3.0 historical framing from active guidance, normalized the requested headings and identifier
    markup, and added a suppressed-issue example backed by `ValidationSpec`'s documentary feature.
12. Completed the remaining Advanced Features review: clarified Defaults, alternatives naming, converters, behavior-model
    hints, Jackson setup, FAQ IDE guidance, and the consolidated 2.0.0 fixes. Detailed Builder/Jackson mechanics now live
    in `Behind the Curtain`; `AlternativesSpec` provides the new #544 documentary example for `stripSuffix`.

## Maintainer feedback incorporated

This section records the content feedback received after the initial audit pass and how the branch incorporated it.

| Feedback | Incorporated outcome |
| --- | --- |
| Do not imply that direct constructor calls are a supported way to leave a scoped Template. | Removed the legacy direct-constructor contrast from `Templates`; Builder-first pages present supported generated construction APIs only. |
| Introduce cross-page concepts before readers need them, or link to their explanation when sidebar order does not do so. | Added focused first-use links for terminology, modeling routes, Layer 3, and Builder/template/copy concepts; ordinary repeated vocabulary remains unlinked. |
| Make the project overview explain KlumAST's value for self-validating, unit-testable models, reduced boilerplate, and GitOps/`*aC`. | Added a prominent Home/README value proposition and `Why aC is not enough`; fact-checked wording says local checks complement target integration tests and does not promise standalone `@Grab` support. |
| Make the new Home value proposition read as one coherent flow rather than two taped-together objective lists. | Unified the overview as automate construction → support authoring → validate/test the model → Builder-first mechanics → GitOps/`*aC` consequence; removed the duplicate legacy objectives. |
| Make validation classes, their topical grouping, and their emitted issue names visible; include example output. | Renamed the heading, added topic-grouping guidance and verified `#ValidationClass.method()` output, and surfaced a short output line in Home/README. |
| Define Schema and Model before roles, and add concise lifecycle vocabulary plus other missing user-facing terms. | Added the linked core-term overview: Schema, Model, lifecycle phase, lifecycle methods and closures, validation, Templates, and relationships. |
| Show a construction path beside the top-level validation output, especially for split-file Models. | Added an illustrative file-backed construction-path failure to Home/README and linked the detailed Exception Handling explanation. |
| Clarify that Schema contains class definitions while Model is Groovy script source that instantiates them; show non-inner validation output and consider documentary tests. | Terms now distinguishes Schema source, concrete Model configuration (normally script source), and the completed Model result. Validation now shows a field-rule result. `ValidationSpec` already asserts exact messages, but #491 has not selected a stable validation documentary happy path or governing issue, so no new unowned documentary test was added. |
| Introduce the `message` member before the field-validation output that uses it; keep `@Required` in its own later introduction. | Moved the explicit-message output example immediately after the `message` explanation. It uses `@Validate` deliberately to illustrate that member; the subsequent `@Required` section remains the preferred Groovy-truth alias guidance. |
| Give every current user page a page-level title, and make Usage lead with the supported Gradle plugin setup rather than manual Maven dependencies. | Normalized every `docs/user/` page to one top-level title and demoted former top-level sections. Usage now begins with the schema-plugin route, links the complete Gradle onboarding/plugin references, and places direct Gradle plus auxiliary Maven dependencies under a final Manual dependencies section. |
| Replace the stale promise of future Gradle/Maven tooling for multi-project Schema source dependencies; create an explicit post-4.0 owner if none exists. | Verified that #226 and #337 do not own this Schema-source workflow, created [#548](https://github.com/klum-dsl/klum-ast/issues/548), and replaced the promise with the current manual workaround plus a Gradle-only owner link. |
| Replace obsolete All-in-One GDSL/IDE warnings with the current source-mirror and AnnoDoc Support route, including a short setup example and the planned first-RC field test. | Usage now shows the Schema-plugin and source-mirror refresh steps, distinguishes completion mirrors from compiled Quick Documentation, and links #469 as the real-project post-RC onboarding evaluation owner. |
| Complete the Overview review: remove obsolete Gradle imports; make issue references absolute; align page titles; distinguish generic journeys from agentic workflow; clarify Basics relationships/examples and phase errors; and simplify Javadoc. | Removed the obsolete imports; made all `docs/user/` issue references absolute; aligned the sidebar and outgoing links with `Gradle onboarding` while the page title retains its 4.0-preview context; separated agentic workflow/field-test material; removed the unexplained `apply` sentence; labelled Schema/Model/assertion examples; documented `OPTIONAL_LINK`; linked phase errors to Exception Handling; and dropped the nonessential versioned-API paragraph. The observed renderer escaping remains #456-owned rather than a content change. |
| Synchronize the accepted `KlumObjectSupport.Validation` 4.0 names after #550 merged. | Updated completed-object, validation, and Builder-first migration guidance; the release notes; ADR 0006; and the JVM public inventory. `getResult()` means the target only, while `getSubtreeResults()` returns the target and owned subtree. `getResults()` is removed without a deprecated bridge ([#549](https://github.com/klum-dsl/klum-ast/issues/549)). |
| Refresh Advanced Features and Validation: remove obsolete pre-3.0 references; backtick identifiers; remove a stale Templates lead; normalize requested heading capitalization; clarify early validation; and show suppression with executable evidence. | Convenience Factories, Factory Classes, Templates, Basics, Default Values, and FAQ no longer use outdated pre-3.0 framing in active guidance. Convenience Factories consistently marks APIs and annotation members as code. Validation explains Groovy's default public methods, its early-validation boundary, suppressing later non-error issues, and nests JSR380 details correctly. `ValidationSpec` now has the #407-tagged documentary feature `suppresses a later non-error issue for one member`, linked to the new example. |
| Finish the Advanced Features review: make Defaults, Converters, Alternatives Syntax, Advanced Techniques, Jackson, FAQ, and the 2.0.0 history clearer; move deep implementation detail out of primary reading paths; and add documentary evidence where practical. | `Default Values` now distinguishes `@Default` from DSL `@AutoCreate`/`@AutoLink`, labels runnable examples, and nests `@DefaultValues` material. `Converters`, `Alternatives Syntax`, and `Advanced Techniques` use shorter authoring guidance, corrected identifiers, and direct API references; `Behind the Curtain` holds Builder projection, Jackson binding, and Builder-first mechanics. `AlternativesSpec` has the #544-tagged `stripSuffix` documentary feature and the #491 inventory links it. Jackson now leads with the Gradle-plugin/BOM route, nests setup/module headings, and links detailed binding mechanics. FAQ replaces obsolete GDSL/DSLD and volatile-deprecation guidance with source mirrors and AnnoDoc Support. `CHANGES.md` consolidates 2.0.0 RC fixes and removes the obsolete Jackson dependency update notice. |

## Deliberate deferrals and owners

| Gap or question | Owner / required next action |
| --- | --- |
| Broad Layer 3 terminology, variants, dependency examples, and the distinction between generic and Schema-specific clients | #454 maintainer grilling and accepted terminology decision. |
| Documentary examples for the remaining feature pages | #491: choose stable headings and governing issues, then add readable annotated tests and page links. Priority candidates are Templates and Convenience Factories. |
| Jackson/YAML documentary example evolution | #464; this audit preserves its documented scope. |
| Onboarding field-test outcome and portable-skill refinements | #469; the current preview labels remain intentional. |
| Version status, renderer, link rewriting, Pages, aliases, and publication | #456; no content audit change may alter those mechanics. |
| A user-facing, version-matched manual Maven setup | Maintainer decision. `Usage` retains the legacy advanced reference, while the supported onboarding path is the Gradle schema plugin. Do not infer a new Maven support contract from this audit. |
| A standalone `@Grab`/Grape example | The Builder-first migration guide deliberately says that setup remains to be documented. A read-only GitHub search on 2026-07-23 found no matching `@Grab`, Grape, or standalone-script issue. A maintainer must identify or create its owner before the route is recommended for release. |

## Content-freeze checklist for maintainer acceptance

- [ ] **#544:** Confirm this inventory's page review, changelog-history comparison through 3.0.1, twelve settled
  corrections, and 4.0 `CHANGES.md`/migration guidance describe the intended user contract for the first RC.
- [ ] **#454 and #491:** Accept the stated Layer 3 terminology boundary and documentary-test roster as intentional
  deferrals; do not treat this content audit as their implementation.
- [ ] **#464 and #469:** Confirm their Jackson/YAML and onboarding evidence remains accurately represented by the current
  documentation.
- [ ] **#456:** Verify the rendered documentation and link output through its separate renderer, versioning, Pages, and
  publication checks; this audit does not authorize those operations.
- [ ] **#544:** Record explicit maintainer content acceptance before the first public RC. A later correction follows the
  accepted RC-promotion exception policy.

## Local two-axis review evidence

| Axis | Purpose and window | Result |
| --- | --- | --- |
| Standards | Read-only standards review, 2026-07-22 23:48–23:51 CEST | One P1 finding: the scoped `Template.With` example asserted `c.roles` twice. Corrected to assert `d.roles` for the second template result. |
| Spec/content scope | Read-only #544 acceptance and scope review, 2026-07-22 23:55–23:59 CEST | One partial requirement: the audit had not explicitly recorded the changelog comparison through 3.0.1. The comparison and #544 acceptance gate are now recorded above. |
| Standards follow-up | Read-only factual/style review, 2026-07-23; run before the Spec follow-up | Three P2 findings: target projection wording overstated KlumAST's role, standalone `@Grab` wording made an unsupported positive claim, and the README opening was stale. Corrected in `16484d2b`. |
| Spec/content-scope follow-up | Read-only #544 scope review, 2026-07-23; run after the Standards follow-up | No findings. The value proposition and `*aC` guide remain docs-only, qualify local checks as complements to target integration tests, preserve #454/#456 boundaries, and retain `@Grab` as an unsupported route pending an owner. |
| Standards validation/Terms follow-up | Read-only factual/style review, 2026-07-23; run before the Spec follow-up | One P1 finding: validation-class discovery requires a public, non-static, non-abstract class. Corrected in `5e6b3e72`; the review confirmed the documented `#ValidationClass.method()` issue target. |
| Spec validation/Terms follow-up | Read-only #544 scope review, 2026-07-23; run after the Standards follow-up | One P1 finding: one explicit no-argument validation-class constructor is permitted; parameterized or multiple constructors are not. Corrected in `ed8aa2ec`. All requested validation, Terms, commit-splitting, feedback-record, and #454/#456 scope requirements otherwise passed. |
| Standards PR-feedback follow-up | Read-only factual/style review, 2026-07-23 11:55–11:57 CEST; run before the Spec follow-up | One P2 finding: “Model is Groovy source” conflicted with documented imported and Template-based configuration and blurred configuration with the completed Model. Corrected in `5a8c3b2c`. |
| Spec PR-feedback follow-up | Read-only #544 scope review, 2026-07-23 11:58–12:00 CEST; run after the Standards follow-up | No findings. The file-backed construction-path example, Model distinction, non-inner validation result, and #491 documentary-test boundary are accurate, linked, and docs-only. |
| Standards validation-order follow-up | Read-only factual/style review, 2026-07-23 12:01–12:03 CEST; run before the Spec follow-up | No findings. The explicit `message` example follows its explanation, `@Required` remains the distinct Groovy-truth alias guidance, and the output/formatting are accurate. |
| Spec validation-order follow-up | Read-only #544 scope review, 2026-07-23 12:04–12:05 CEST; run after the Standards follow-up | No findings. The feedback is fully addressed, retained output remains in scope, and #491/#454/#456 boundaries are unchanged. |
| Standards title/Usage follow-up | Read-only factual/style review, 2026-07-23 12:13–12:17 CEST; run before the Spec follow-up | Two P1 findings: Home still had a second H1, and manual Gradle must expose `klum-ast-runtime` as `api` for published Schema consumers. Corrected in `96c15007`. |
| Spec title/Usage follow-up | Read-only #544 scope review, 2026-07-23 12:18–12:21 CEST; run after the Standards follow-up | No findings. Every user content page now has one title; Usage leads with Gradle onboarding and bounds manual Gradle/Maven setup to the advanced, auxiliary route. |
| Standards Schema-source follow-up | Read-only factual/style review, 2026-07-23 12:28–12:29 CEST; run before the Spec follow-up | No findings. The manual joint-source workaround, Gradle-only #548 owner link, wording, and audit record are accurate. |
| Spec Schema-source follow-up | Read-only #544 scope review, 2026-07-23 12:30–12:31 CEST; run after the Standards follow-up | No findings. The stale promise is removed, #548 is not described as delivered, Maven is not promised, and the change remains within documentation scope. |
| Standards All-in-One follow-up | Read-only factual/style review, 2026-07-23 12:34–12:35 CEST; run before the Spec follow-up | No findings. The schema-plugin setup, source-mirror/Quick-Documentation distinction, #469 validation boundary, and wording are accurate. |
| Spec All-in-One follow-up | Read-only #544 scope review, 2026-07-23 12:36–12:37 CEST; run after the Standards follow-up | No findings. The obsolete GDSL claim is removed; current IntelliJ tooling is presented without an IDE-parity claim and real-project confirmation remains #469-owned. |
| Standards Overview follow-up | Read-only factual/style review of `60e9bc15..a32bda4f`, 2026-07-23 13:17–13:19 CEST; run before the Spec follow-up | No findings. Sidebar titles, absolute issue links, Gradle cleanup, journey separation, settled `OPTIONAL_LINK` wording, exception guidance, Javadoc simplification, and the feedback record are accurate. |
| Spec Overview follow-up | Read-only #544 scope review of `60e9bc15..a32bda4f`, 2026-07-23 13:19–13:19 CEST; run after the Standards follow-up | No findings. The completed Overview feedback is addressed without extending #544 or deciding #454 Layer 3 terminology or #456 renderer behavior. |
| Standards final-batch review | Read-only factual/style/Markdown review of the uncommitted final batch, 2026-07-23 17:27–17:29 CEST; edits frozen | One P1 observation: consolidating 2.0.0 fixes removes the RC grouping. Intentionally retained because the maintainer explicitly requested obsolete 2.0.0 RC fixes be merged; every substantive fix remains in the final 2.0.0 list. All other reviewed headings, links, examples, and the `stripSuffix` documentary metadata passed. |
| Spec/content-scope final-batch review | Read-only #544 acceptance and boundary review of the uncommitted final batch, 2026-07-23 17:29–17:30 CEST; run after the Standards review, edits frozen | No findings. The new `AlternativesSpec` documentary feature is bounded and correctly linked in the #491 inventory; #454 terminology and #456 renderer boundaries remain intact. The requested 2.0.0 consolidation retains substantive history, and `Behind the Curtain` is correctly separated from primary authoring guidance. |
