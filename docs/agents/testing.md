# Testing

KlumAST supports three Groovy generations. Passing one generation is not evidence that the other two pass, even when their compiler behavior is currently similar.

The baseline and compatibility lanes share only their production artifact plus explicit `src/test/java` and
`src/test/resources` inputs. Groovy test sources and Groovy test fixtures are compiled separately in each lane.
`verifyTestLaneIsolation`, which is part of `check`, rejects mismatched Groovy/Spock dependencies, cross-lane compiled
test or resource output on source-set/Groovy compile/runtime classpaths, non-lane test classes, and non-lane JUnit result
directories. CI keeps the Groovy-4/5 JUnit and JaCoCo reports as compatibility-lane evidence; Sonar consumes only the
Groovy-3 baseline report so production classes are not counted once per lane.

## Test lanes

| Gradle task | Groovy generation | Use |
|---|---:|---|
| `test` | 3 | Baseline for focused tests and normal development iterations |
| `groovy4Tests` | 4 | Compatibility check when Groovy-version behavior may differ and at the end of a change |
| `groovy5Tests` | 5 | Compatibility check when Groovy-version behavior may differ and at the end of a change |

Start with the narrowest relevant Groovy 3 test, then run the affected module's Groovy 3 suite. Run Groovy 4 and 5 sparingly during development unless the change touches compiler APIs, AST behavior, Groovy syntax, dependency compatibility, or another known version seam. Before handing off a completed change, run both compatibility lanes; the root `check` task includes them for projects using the multi-Groovy convention.

A documentation-only pull request does not require the Groovy 3, 4, or 5 test lanes when its changes cannot affect
compilation, test execution, generated output, or runtime behavior. Run `git diff --check` plus any applicable Markdown,
link, rendering, or documentation-generation checks instead, and state the omission in the pull request. Treat changes to
build configuration, executable or compiled examples, generated-source inputs, and test fixtures as code changes rather
than documentation-only changes.

Typical commands are:

```shell
./gradlew :klum-ast:test --tests com.blackbuild.groovy.configdsl.transform.SomeTest
./gradlew test
./gradlew groovy4Tests groovy5Tests
./gradlew check
```

## Issue traceability

Every newly added test must carry Spock's `@Issue` annotation with the number of its driving GitHub issue. When a complete
specification or test class relates to one issue, put `@Issue` on the class; that remains sufficient for every test that
originates from the same issue. Annotate an individual test when it originates from a different issue, such as a later bug
fix or feature addition. Choose the specific implementation issue or its governing parent case by case, according to
which one best explains the behavior under test.

When implementation changes an existing test, add or amend its `@Issue` annotation only if the test change is significant.
A change is significant when it materially changes the tested behavior, scenario, or expected contract. Mechanical edits,
renames, formatting, or adjustments to shared setup do not require issue-annotation churn.

The driving issue is the normal historical link from a test. When an ADR governs the behavior, the governing issue must
reference it. Do not routinely repeat that ADR link on every test. Add another `@See` for the ADR only when the test
directly enforces an architectural decision or non-obvious invariant and the link materially helps a reader understand why
the asserted boundary is intentional. A documentary happy path should normally link to user documentation rather than an
ADR.

## Test class naming and organization

Name every new executable test class with the `Test` suffix. Use `<Subject><Concern>Test` when a concern distinguishes the
class from other tests for the same subject, or `<Subject>Test` when the subject is already narrow. Do not introduce new
`*Spec` names. Existing `*Spec` classes need not be renamed; they may be renamed when the change stays within the task's
scope and all test filters and references remain correct.

A production class or concept does not need a one-to-one test class. Split its tests into multiple classes when each
resulting class has a cohesive purpose and the split improves readability, navigation, or fixture clarity. Avoid splitting
so finely that related behavior or setup becomes harder to understand.

## Documentary tests

Every new user-visible DSL feature must also have one or more documentary tests. At least one should normally be a happy
path that demonstrates the feature's basic use as readable, executable DSL code. Prefer meaningful hypothetical domain
vocabulary over placeholders such as `Foo` and `Bar` when that makes the example easier to understand.

Mark each documentary test with Spock's `@Tag("documentary")`. Put the tag on an individual feature method unless the
entire specification is documentary, in which case a specification-level tag is sufficient. Use `@See` on the same
feature or specification to link to the relevant documentation element. `@See` is represented as an attachment in Spock
reports, so give it an absolute URL to the current documentation source and include a heading anchor when it is stable.
The current 4.x authoring source lives under `docs/user/`; use its repository URL until the exact rendered Pages URL exists.

Documentary tests may live beside the focused behavioral tests or in a separate thematic class. Prefer a dedicated
`<Theme>DocumentaryTest` when several examples form a coherent reading path or review entry point, share comprehensible
domain setup, or span multiple driving issues within one theme. Keep an isolated documentary happy path with its focused
behavioral tests when extraction would duplicate fixtures or fragment ownership. Use `DocumentaryTest`, not
`DocumentationTest`, so the name identifies an executable example rather than suggesting a test of documentation tooling.

In a dedicated documentary class, put `@Tag("documentary")` on the class in addition to using the documentary class name.
If its features originate from different issues, put `@Issue` on each feature method. Put `@See` on each feature when the
documentation targets differ; a class-level `@See` is sufficient only when one documentation target genuinely covers the
whole class.

The normal annotation shape is:

```groovy
@Issue("123")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Feature.md#basic-use")
def "demonstrates the basic feature syntax"() {
    // readable happy path
}
```

A thematic class spanning issues normally looks like:

```groovy
@Tag("documentary")
class TemplatesDocumentaryTest extends AbstractDSLSpec {

    @Issue("123")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#applying-templates")
    def "applies a named template"() {
        // first readable example
    }

    @Issue("124")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Templates.md#combining-templates")
    def "combines templates in declaration order"() {
        // related example from another issue
    }
}
```

Do not require documentary prefixes in feature-method names. `@Tag("documentary")` is the machine-readable marker, while
the `DocumentaryTest` suffix identifies a dedicated documentary class. `@Narrative` and `@Title` apply only to
specifications, so they may improve a wholly documentary specification but do not replace its `documentary` tag.

Keep the feature issue, documentary test, and user documentation mutually traceable:

- The feature issue names the documentary test file and feature method, and the relevant documentation element under
  `docs/user/` until an exact rendered URL exists.
- The documentary test carries the driving `@Issue` number, the `documentary` tag, and an `@See` link to the relevant
  documentation file or section.
- The documentation demonstrates the feature with an abbreviated example that may omit imports and other setup, and
  refers to the documentary test file and feature method. Align the example with the test where practical so executable
  coverage guards the documented usage.

When the outcome itself clarifies the contract, the documentation may also show an abbreviated `then:` assertion from the
documentary test. For a more complex resulting graph, a concise logical representation of the completed model may be
clearer than executable assertions. This is optional: omit it when the configured model is already self-explanatory, but
consider it for behavior such as automatic creation, default values, or owner-provided defaults where the resulting state
is the important part of the example.

This policy applies prospectively. Do not expand unrelated feature work by retrofitting the existing suite; the existing
documentation and test audit is tracked in [issue #491](https://github.com/klum-dsl/klum-ast/issues/491). This policy is
KlumAST-specific because it connects the project's DSL behavior, Spock suite, GitHub issues, and user documentation.

## Feature discussion examples

During grilling and implementation of a user-visible feature, normally present a compact usage example in the dialogue
before the design is considered settled. Use it as a syntax probe: it should be small enough that a reviewer can quickly
get a feel for the feature, question awkward calls or names, and compare alternatives. Once accepted, use it as the seed
for the documentary test and its abbreviated documentation example.

- When the feature changes the DSL, show only Groovy. Show Schema code, Model code, or both according to the surface being
  designed; omit unrelated setup.
- When the feature changes a client API, show Java first as the primary contract and Groovy second as the convenience
  view.
- For a feature spanning both DSL and client surfaces, apply the corresponding language rule to each example.
- An internal-only change or a discussion without a meaningful usage surface may omit the example, but state that briefly
  instead of silently skipping it.

## Suppressed tests

Every test-suppressing annotation must explain why the test does not currently run. This includes `@Ignore`, `@IgnoreIf`, and `@PendingFeature`.

- Put a concise, actionable explanation directly in the annotation when one line is enough. State the unsupported contract or blocker, not merely "legacy", "obsolete", or "broken".
- For a conditional annotation, populate its `reason` member and explain the condition.
- If the explanation needs history, multiple constraints, or removal criteria, document it under `docs/testing/` and reference that document and heading from the annotation.
- Prefer deleting a test whose asserted behavior has been intentionally removed, or rewriting it as a rejection test, when it no longer records useful compatibility intent.
