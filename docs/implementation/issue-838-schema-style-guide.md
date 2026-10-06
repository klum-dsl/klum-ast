# Issue #838: Schema Style Guide authority

Related: [#838](https://github.com/klum-dsl/klum-ast/issues/838).

## Recommendation evidence

The user-facing [Schema Style Guide](../user/Schema-Style-Guide.md) collects conventions without changing behavior.
Its framework statements and documented practices come from:

- [GeneratedInnerNameCollisionTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/GeneratedInnerNameCollisionTest.groovy)
  and the [#837 audit](evidence/issue-837-generated-inner-overlap.md): deterministic implementation names, exact
  collision diagnostics, and legal unrelated underscore-prefixed types. The broader naming preference is explicitly
  a recommendation, not a new reservation policy.
- [Gradle Onboarding](../user/Gradle-Onboarding.md): Schema/test source roots, matching Spock setup, small feedback loop,
  and independent architecture choices. Named packages, matching directories, file organization, and simple imported
  names are labeled readability conventions.
- [Validation](../user/Validation.md#choose-a-validation-form),
  [Required.java](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Required.java), and
  [Validate.java](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Validate.java): presence/closure/method
  choices, Groovy-truth and Boolean boundaries, validation targets, and relationship constraints. The small Deployment
  example is abbreviated from the existing validation guide; this change adds no DSL feature or executable input.
- [Testing Models and Schemas](../user/Testing-Models-and-Schemas.md): completed-state assertions, canonical validation
  exception, and semantic message fragments. Its existing Spock examples use native conditions.
- [Javadoc for models](../user/Javadoc.md): property/accessor projection and method documentation. Comments that explain
  domain meaning rather than repeat spelling are a readability preference.

## Installed bootstrap skill boundary

The installed `klumast-schema-adopter-bootstrap` skill and its `references/rc23-authority.md` and
`references/rc23-validation-error-contract.md` were inspected for this issue. Neither that skill nor its authority
records are tracked in this repository. The tracked `agent-skills/start-klum-project` is a different distribution,
frozen to public 4.0.1; replacing its authority with current 4.1 prose would break its version selection.

The intended style-authority reference is
[`docs/user/Schema-Style-Guide.md`](../user/Schema-Style-Guide.md), published with the matching documentation release.
For a future frozen adopter record, select that page at the same immutable release tag/commit as its imports, plugin,
Builder, and validation sources. Link to the page rather than copying its recommendations. Current source location:
[Schema Style Guide on master](https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Schema-Style-Guide.md).
That moving source link is for maintainers; it must not replace a frozen record's release-matched authority.

Updating the external installed skill and selecting its next authority record remain separate owner work. RC.23 and
4.0.1 do not contain this new page; neither existing frozen record is changed by this documentation slice. The scope is
KlumAST-specific user guidance, so no engineering-baseline change is needed.

## Delivery scope

This change adds current user documentation, discoverability links, and the 4.1 changelog entry. It changes no runtime,
API, build configuration, executable example, or test fixture. The documentation-only exemption in
[testing policy](../agents/testing.md) applies; use rendering, internal-link/site crawling, and diff checks.
No migration guidance or release-curation decision changes. Tracker relationship: `Related: #838`, because the external
skill integration still requires separate ownership and the Hive owns final acceptance reconciliation.

## Local validation

- `renderLocalDocumentation -PdocumentationVersion=4.1.0-tracer` passed with Java 17: the immutable-revision renderer,
  module Javadoc generation, and `verifyLocalDocumentationSite` internal page/asset/fragment crawl completed. The
  manifest includes the new Schema page among 633 generated outputs. Existing Javadoc and Gradle warnings remain.
- `git diff 2f448ca7...HEAD --check` passed. The dedicated branch starts at current master `2f448ca7`, the merge of
  #837's PR #839.
- Separate Standards and Spec reviews found no findings. Commit-history review retains one focused documentation
  commit; no code/test change requires a new documentary test or Groovy 3/4/5 lane.
- Delivery authorization audit: authenticated GitHub CLI repository access is authorized; Git branch transport is
  authorized for this assigned push. No GitHub App delivery channel is used.
