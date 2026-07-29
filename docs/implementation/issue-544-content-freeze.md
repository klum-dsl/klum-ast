# Issue 544 — 4.0 user-documentation content-freeze inventory

Tracking issue: [#544 — Finalize KlumAST 4.0 user-documentation content before the first RC](https://github.com/klum-dsl/klum-ast/issues/544)

Review basis: `origin/master` at `45f2167e` (2026-07-29), including the merged #491 inventory
([PR #610](https://github.com/klum-dsl/klum-ast/pull/610)) and the closed #391 JPMS/module work
([PR #614](https://github.com/klum-dsl/klum-ast/pull/614)). This is a content review of current `docs/user/`; #456
continues to own rendered form, versioning, hosting, aliases, and publication.

## Accepted content inventory

| Adopter path | Reviewed current pages | Result |
| --- | --- | --- |
| Entry and setup | `Home`, `Terms`, `Usage`, `Getting-Started`, `Gradle-Plugins`, `Javadoc`, `Roadmap`, `Why-aC-is-not-enough` | The 4.0 preview, Builder-first construction, Groovy/JPMS boundary, generated-mirror scope, and versioned-documentation status agree with current artifacts. The remaining plugin examples now use a matching 4.x version placeholder rather than a 3.0 version. |
| Core authoring and migration | `Basics`, `Static-Models`, `Model-Phases`, `Validation`, `Exception-Handling`, `Builder-First-Migration`, `Migration` | Builder-first materialization, validation, collection snapshots, transient-state exception, path terminology, and named-module migration guidance agree with the shipped 4.0 sources. `TRANSIENT` now explicitly distinguishes KlumAST's mutable/serialized model state from Java/Groovy `transient`. |
| Construction and advanced use | `Convenience-Factories`, `Factory-Classes`, `Templates`, `Copy-Strategies`, `Default-Values`, `Inheritance`, `Converters`, `Alternatives-Syntax`, `Advanced-Techniques`, `Behind-the-Curtain`, `Completed-Object-Support` | #491 supplies the accepted documentary mapping or a deliberate reference/API-boundary exception. `Advanced-Techniques` remains a concept page, not a manufactured tutorial; its `@ParameterAnnotation` source link now follows #391's final package. |
| Modeling shapes and integrations | `Domain-First-Modeling`, `Target-Contract-Modeling`, `Layer3`, `Jackson-Integration`, `FAQ` | Direct-schema versus Layer 3 and domain-first versus target-contract stay separate; the #469 journeys and #491 mappings remain consistent. Jackson remains asymmetric external-format integration, not persistence or source composition. |
| Navigation and release context | `_Sidebar`, `CHANGES.md`, `Migration`, `Builder-First-Migration` | Current navigation exposes entry, migration, and release notes. `CHANGES.md` and migration pages state the same Builder-first and JPMS contract. The renderer owns final rendered-link verification. |

## Evidence-backed corrections in this review

- Updated the `@ParameterAnnotation` source link to its final `com.blackbuild.klum.ast` package.
- Removed an unsupported dependency on a separate project from the static-model definition and clarified the supported adapter/decorator boundary.
- Clarified that `FieldType.TRANSIENT` remains mutable and is serialized by default; it is not the Java/Groovy `transient` modifier.
- Replaced 3.0 plugin-version literals in current 4.0 Gradle examples with the matching-version placeholder, while
  retaining version inheritance in the multi-module child-project example.
- Corrected the rendered named-module migration links to their generated heading anchor.

## Deliberate residual ownership

| Remaining substantive gap or acceptance | Existing owner | #544 disposition |
| --- | --- | --- |
| Confirm Layer 3 variants, examples, and broader terminology beyond the accepted API–Schema–Model baseline. | [#454](https://github.com/klum-dsl/klum-ast/issues/454) | Do not define it through this content review. |
| Publish/version/stage the reviewed source and verify the rendered exact-version site. | [#456](https://github.com/klum-dsl/klum-ast/issues/456) | Renderer and publication work remain separate. |
| Field-test the portable onboarding routes and refine them from adoption evidence. | [#469](https://github.com/klum-dsl/klum-ast/issues/469) | The current routes are consistent; this review does not extend them. |
| State and prove the supported Gradle-version range. | [#389](https://github.com/klum-dsl/klum-ast/issues/389) | No range is claimed here. |
| Reconcile the merged #491 documentary audit's tracker acceptance/closure. | [#491](https://github.com/klum-dsl/klum-ast/issues/491) | Its merged inventory is accepted evidence for #544; this review does not reopen the audit. |
| Accept this content freeze before the first public RC. | [#544](https://github.com/klum-dsl/klum-ast/issues/544) maintainer | Pending explicit maintainer acceptance. |

## Maintainer acceptance request

Please confirm that this inventory is accepted as the pre-RC 4.0 user-documentation content freeze. Subsequent changes
should be limited to the accepted RC-promotion correction policy or to the separately owned issues above.
