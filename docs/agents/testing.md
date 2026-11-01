# Testing

KlumAST supports three Groovy generations. Passing one generation is not evidence that the other two pass, even when their compiler behavior is currently similar.

The baseline and compatibility lanes share only their production artifact plus explicit `src/test/java` and
`src/test/resources` inputs. Groovy test sources and Groovy test fixtures are compiled separately in each lane.
`verifyTestLaneIsolation`, which is part of `check`, rejects mismatched Groovy/Spock dependencies, cross-lane compiled
test or resource output on source-set/Groovy compile/runtime classpaths, non-lane test classes, and non-lane JUnit result
directories. CI keeps the Groovy-4/5 JUnit and JaCoCo reports as compatibility-lane evidence; Sonar consumes only the
Groovy-3 baseline report so production classes are not counted once per lane.

## Local validation before push

Choose checks by the highest applicable risk below, not by the number of files. Start test-first where behavior changes:
run the narrowest relevant Groovy 3 test, then broaden to the affected module suite and other affected seams. Select
checks that exercise the actual change and its consumers; if their scope cannot establish safety, use the full-check tier.
Run applicable compilation, static, documentation and `git diff --check` checks in every tier. Record the commands,
results, scope, and any omissions or failures in the handoff and PR; focused success is not a full-build or Sonar result.

| Change and risk | Minimum local evidence before push |
|---|---|
| Documentation only, unable to affect compilation, tests, generated output or runtime | `git diff --check` and applicable Markdown, link, rendering or documentation-generation checks. Groovy lanes are unnecessary; state their omission in the PR. Build configuration, executable or compiled examples, generated-source inputs and test fixtures are code changes. |
| Localized runtime, test or documentation repair | Focused Groovy 3 tests for the changed seam, the affected module's Groovy 3 suite, and applicable checks above. Include affected consumer or regression tests where needed. Run Groovy 4/5 when the change touches a version-sensitive seam; a localized SonarCloud finding alone does not require root `check`. |
| Compiler, AST, generated public contract or compatibility surface | Focused compiler and consumer regressions, affected module suites and both `groovy4Tests` and `groovy5Tests` for affected multi-Groovy modules. Add binary compatibility and JPMS descriptor/module-path tests when the binary or module contract can change; run Gradle plugin/TestKit tests when generated contracts or plugin behavior can affect plugin consumers. Escalate to root `check` for shared or cross-module contracts, or when focused evidence is insufficient. |
| Gradle/build/CI topology, publication or release, cross-module/shared contract, or uncertain safety | Run full local root `./gradlew check`, plus any targeted integration, plugin, binary/JPMS, publication or release checks needed for the change. `check` is not by itself proof of an external publication or release. |

For example, a localized SonarCloud repair in `klum-ast-runtime` runs its relevant focused tests and
`:klum-ast-runtime:test`, plus applicable compilation, static and documentation checks; it does not repeat root `check`
solely because Sonar reported the finding. A change to a generated Builder contract in `klum-ast` runs compiler and
consumer regressions, affected module suites and Groovy 4/5 lanes, with binary/JPMS and plugin consumer tests where
those surfaces are affected. A shared contract spanning modules also requires root `check`.

After a final rebase, rerun the checks invalidated by integration and any targeted regressions needed for the combined
change; retain earlier results only where their inputs and assumptions remain valid. Follow the reviewed-history rules in
[commits.md](commits.md) before rebasing. GitHub PR CI is the authoritative complete-build qualification: inspect all
required checks, including SonarCloud, on the final PR revision. Local evidence never substitutes for pending or failed CI.

## Test lanes

| Gradle task | Groovy generation | Use |
|---|---:|---|
| `test` | 3 | Baseline for focused tests and affected module suites |
| `groovy4Tests` | 4 | Compatibility lane when required by the risk matrix |
| `groovy5Tests` | 5 | Compatibility lane when required by the risk matrix |

Root `check` includes both compatibility lanes for projects using the multi-Groovy convention. Typical commands are:

```shell
./gradlew :klum-ast:test --tests com.blackbuild.groovy.configdsl.transform.SomeTest
./gradlew :klum-ast:test
./gradlew :klum-ast:groovy4Tests :klum-ast:groovy5Tests
./gradlew check
```

## Spock assertion clarity

Use Spock's `expect:` only for a rather simple, one-line statement whose method names or operators make its meaning
self-explanatory.

## Compiler-test success controls

In a Spock compiler test, do not use a bare `expect:` block that contains only a side-effecting `createClass(...)` call.
That form does not make the compile-success intent evident. Express successful compilation explicitly, normally as:

```groovy
when:
createClass(...)
then:
notThrown(MultipleCompilationErrorsException)
```

An equally explicit success assertion is acceptable when it makes the tested compiler contract materially clearer.

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
- Place that reference immediately before the corresponding example in the form
  `(See: \`ThemeDocumentaryTest#'demonstrates the feature'\`.)`, following the `Templates.md` convention. Keep an
  example's logical setup, invocation, and assertion in one Groovy block; use `given:`, `when:`, and `then:` labels when
  they help the reader distinguish those parts rather than splitting them across separate blocks.

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
