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

## Suppressed tests

Every test-suppressing annotation must explain why the test does not currently run. This includes `@Ignore`, `@IgnoreIf`, and `@PendingFeature`.

- Put a concise, actionable explanation directly in the annotation when one line is enough. State the unsupported contract or blocker, not merely "legacy", "obsolete", or "broken".
- For a conditional annotation, populate its `reason` member and explain the condition.
- If the explanation needs history, multiple constraints, or removal criteria, document it under `docs/testing/` and reference that document and heading from the annotation.
- Prefer deleting a test whose asserted behavior has been intentionally removed, or rewriting it as a rejection test, when it no longer records useful compatibility intent.
