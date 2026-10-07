# Issue #838: Schema Style Guide evidence

Related: [#838](https://github.com/klum-dsl/klum-ast/issues/838), [PR #840](https://github.com/klum-dsl/klum-ast/pull/840).

## Delivery to Schema authors

The [Schema Style Guide](../user/Schema-Style-Guide.md) answers what makes an idiomatic, readable Schema and why. Its
cohesive Model/Deployment/Capacity/Image example connects an early DSL sketch with domain types, convenient conversion,
nearby constraints, a small derived query, and semantic documentation. The principles are design preferences; exact
compiler and annotation contracts remain in their detailed pages. Onboarding and sidebar links make the guide an entry point. The added
Validation/Javadoc/Testing backlinks were removed during audience review because their detailed pages already have
clear purposes and the guide links outward to them.

No runtime, API, compiler restriction, testing policy, or architecture decision changes. In particular, #837/#839's
actual-collision boundary is unchanged, and no leading-underscore ban is introduced. Domain-first, target-contract,
direct-schema, and Layer 3 choices remain independent of these readability principles.

## Provenance

- [Static Models](../user/Static-Models.md), [Gradle Onboarding](../user/Gradle-Onboarding.md#choose-the-model-shape-first),
  and [Layer 3](../user/Layer3.md) ground completed-state queries, downstream behavior, and consumer boundaries. The
  preference for obvious structure and meaningful names is readability advice, not a new architecture contract. The
  maintainer's review adds the design practice of sketching a Model DSL, ideally in a test, before implementing the
  Schema and iterating both together; it does not prescribe a modeling architecture.
- [Validation](../user/Validation.md#choose-a-validation-form),
  [Required.java](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Required.java), and
  [Validate.java](../../klum-ast-annotations/src/main/java/com/blackbuild/klum/ast/Validate.java) ground the choice of
  presence/truth rules, local predicates, and named cross-field methods. The example combines already supported forms.
- [Migration's exact collision rules](../user/Migration.md#generated-inner-name-collisions-41),
  [GeneratedInnerNameCollisionTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/GeneratedInnerNameCollisionTest.groovy),
  and the [#837 audit](evidence/issue-837-generated-inner-overlap.md) ground the narrow naming constraint. Public prose
  intentionally omits the implementation-name inventory.
- [Converters](../user/Converters.md#factory-method-converters) and `ConverterSpec#'convention named factories are
  automatically included'` ground discovery of a non-DSL value's `fromString` factory. The final Image class has final
  String properties, constructor checks, and a deliberately narrow `name:tag` parser. This demonstrates immediate
  value validation, distinct from the [completed-tree validation phase](../user/Validation.md#validation-of-nested-objects).
  Considering immutable client-facing value types and enforcing contracts before documenting them are maintainer design
  guidance; this example adds no container-image-format contract to KlumAST.
- [Testing Models and Schemas](../user/Testing-Models-and-Schemas.md) grounds public construction and domain-state tests.
  [Javadoc for models](../user/Javadoc.md) grounds property documentation projection and accessor precedence.

## Preserved input for future adopter guidance

The research also identified useful operational recommendations. They are retained here as candidates for separate
agent guidance, rather than as a checklist in human documentation:

| Candidate recommendation | Authority or rationale |
| --- | --- |
| Sketch representative Model DSL syntax early, preferably in a test; evolve Schema and syntax together. | Maintainer readability/design guidance; [public construction tests](../user/Testing-Models-and-Schemas.md#assert-a-completed-model) provide the feedback surface. |
| Consider immutable client-facing domain values with converters/factory methods rather than making clients parse Strings. Validate self-contained values at construction and tree-dependent rules during Model validation. | Maintainer design guidance plus [Converters](../user/Converters.md#factory-method-converters) and [Validation timing](../user/Validation.md#validation-of-nested-objects). |
| Choose `@Required` for default presence/truth, a field closure for a small local rule, and a method for cross-field, reused, or substantial rules. | [Validation](../user/Validation.md#choose-a-validation-form). `@Required` is not universal non-nullness: Groovy-false values fail, boxed Boolean checks non-nullness, primitive boolean is unsupported, and it cannot accompany `@Validate`. |
| Keep validation closures to one expression or an `assert`, handling null deliberately. | [Field validation](../user/Validation.md#on-fields) and the annotation contracts above. |
| Reuse the established test framework and start with one small Gradle test feedback loop. | [Gradle Onboarding](../user/Gradle-Onboarding.md#create-the-gradle-project) and [Testing Models and Schemas](../user/Testing-Models-and-Schemas.md). A new Schema-plugin project has matching Spock setup. |
| Use native Spock conditions; omit redundant `assert` in `then:`/`expect:`. | Existing examples in [Testing Models and Schemas](../user/Testing-Models-and-Schemas.md). Assertions inside validation methods serve a separate purpose. |
| Expect `KlumValidationException` and assert an explicit message's distinctive semantic fragment. Avoid complete diagnostic/path/order/power-assert rendering unless that diagnostic is itself under test. | [Validation-failure example](../user/Testing-Models-and-Schemas.md#assert-a-validation-failure); canonical exception import: `com.blackbuild.klum.ast.runtime.validation.KlumValidationException`. |
| Prefer descriptive helpers over generated-looking names; do not infer an underscore ban. | Readability preference plus [exact collision rules](../user/Migration.md#generated-inner-name-collisions-41). |
| Keep comments semantic: meaning, units, constraints, and non-obvious relationships. | Readability preference plus [documentation projection](../user/Javadoc.md). |
| Follow source/test roots, named packages and matching package directories; import types by simple name. | [Onboarding source roots](../user/Gradle-Onboarding.md#create-the-gradle-project); the remaining choices are ordinary Groovy/JVM readability conventions, not KlumAST compiler restrictions. |

The inspected installed `klumast-schema-adopter-bootstrap` skill is external and untracked. A future owner update may
reference the release-matched public guide for principles and examples, the detailed public pages for contracts, and
additional agent-specific policy for operational rules. The distinct tracked `start-klum-project` distribution remains
frozen to 4.0.1. Neither skill nor its authority records are changed here; selecting their next authority belongs to
separate work. This is KlumAST-specific guidance and requires no engineering-baseline change.

## Validation and delivery boundary

Only Markdown changes. The [documentation-only testing exemption](../agents/testing.md) applies: rendering,
internal-link/fragment crawling, and diff checks replace the Groovy compatibility lanes. The cohesive code blocks are
illustrations of existing behavior, not new compiled fixtures or DSL features.

Initial delivery at `d52fbf79` passed Java 17 `renderLocalDocumentation -PdocumentationVersion=4.1.0-tracer`, the internal
page/asset/fragment crawl (633 generated outputs), diff checks, and separate Standards/Spec reviews. That revision's CI
build, JUnit report, and SonarCloud checks passed with no new issues. The audience revision requires its own rendering
and review evidence before publication; final revision/check results are recorded in PR #840's updated description and
consolidated follow-up.

The audience revision's two Groovy blocks were extracted from the Markdown and exercised with the existing Groovy 3
compiler/runtime through a temporary verification task outside the repository. Construction, default values, the
derived query, the local range violation, the cross-field violation, and an empty required image all passed. No permanent
test fixture or build task was added.

The later DSL-first/value-object revision keeps that same human audience. Its actual Model/Schema/Image blocks are
verified together for converter discovery, typed/direct value assignment, immutability, immediate rejection of malformed
values, default/query behavior, and normal Schema validation. Current rendering, reviews, and CI evidence remain in the
PR description and consolidated follow-up; the earlier String-image example's checks are historical evidence only.

The dedicated branch starts at master `2f448ca7` (#839). Preserve the reviewed initial commit and add the audience
revision as a follow-up commit. Tracker relationship remains `Related: #838`; Hive acceptance reconciliation and any
external adopter-skill integration remain separate. No release-curation decision changes.
