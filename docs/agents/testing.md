# Testing

KlumAST supports three Groovy generations. Passing one generation is not evidence that the other two pass, even when their compiler behavior is currently similar.

## Test lanes

| Gradle task | Groovy generation | Use |
|---|---:|---|
| `test` | 3 | Baseline for focused tests and normal development iterations |
| `groovy4Tests` | 4 | Compatibility check when Groovy-version behavior may differ and at the end of a change |
| `groovy5Tests` | 5 | Compatibility check when Groovy-version behavior may differ and at the end of a change |

Start with the narrowest relevant Groovy 3 test, then run the affected module's Groovy 3 suite. Run Groovy 4 and 5 sparingly during development unless the change touches compiler APIs, AST behavior, Groovy syntax, dependency compatibility, or another known version seam. Before handing off a completed change, run both compatibility lanes; the root `check` task includes them for projects using the multi-Groovy convention.

Typical commands are:

```shell
./gradlew :klum-ast:test --tests com.blackbuild.groovy.configdsl.transform.SomeSpec
./gradlew test
./gradlew groovy4Tests groovy5Tests
./gradlew check
```

## Feature traceability and documentary tests

Every test added as part of user-visible feature work must carry Spock's `@Issue` annotation with the number of the
driving GitHub issue. Put the annotation on the specification when one issue owns all of its tests, or on individual
features when a specification covers several issues. Choose the specific implementation issue or its governing parent
case by case, according to which one best explains the behavior under test.

Every new user-visible DSL feature must also have one or more documentary tests. At least one should normally be a happy
path that demonstrates the feature's basic use as readable, executable DSL code. Prefer meaningful hypothetical domain
vocabulary over placeholders such as `Foo` and `Bar` when that makes the example easier to understand.

Mark each documentary test with Spock's `@Tag("documentary")`. Put the tag on an individual feature method unless the
entire specification is documentary, in which case a specification-level tag is sufficient. Use `@See` on the same
feature or specification to link to the relevant documentation element. `@See` is represented as an attachment in Spock
reports, so give it an absolute URL to the current documentation source and include a heading anchor when it is stable.
Until issue #456 settles the final documentation placement, a URL to the relevant file under `wiki/` is sufficient.

The normal annotation shape is:

```groovy
@Issue("123")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/wiki/Feature.md#basic-use")
def "demonstrates the basic feature syntax"() {
    // readable happy path
}
```

Do not require a feature-name prefix as a second documentary marker; the tag is the single machine-readable convention.
`@Narrative` and `@Title` apply only to specifications, so they may improve a wholly documentary specification but do not
replace its `@Tag("documentary")` marker.

Keep the feature issue, documentary test, and user documentation mutually traceable:

- The feature issue names the documentary test file and feature method, and the relevant documentation element. A wiki
  filename is sufficient while the final documentation placement remains open.
- The documentary test carries the driving `@Issue` number, the `documentary` tag, and an `@See` link to the relevant
  documentation file or section.
- The documentation demonstrates the feature with an abbreviated example that may omit imports and other setup, and
  refers to the documentary test file and feature method. Align the example with the test where practical so executable
  coverage guards the documented usage.

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
