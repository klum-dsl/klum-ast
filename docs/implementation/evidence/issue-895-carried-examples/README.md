# Carried-examples producer reuse

Related: [#895](https://github.com/klum-dsl/klum-ast/issues/895). The eight invalid carried-examples rows now share a pristine producer repository within the specification. Each row receives physical copies, with the absolute initialization-trap path specialized in its copied classes before the row mutates an artifact. Positive controls initialize both root classes in two independent scenarios; the fixture also requires all six known trap-bearing classes to be specialized. Diagnostics, suppression, output preservation and source-free consumption remain covered by the original rows.

## Local observations

On macOS 27.0.1 arm64, Gradle 8.14.4 and Temurin 25.0.4.1, using existing dependency caches and `--no-parallel --max-workers=2`, the following command was run with `/usr/bin/time -p`:

```shell
./gradlew :klum-ast-gradle-plugin:test --tests '*KlumJsonSchemaGenerationTest.invalid carried examples*' --no-parallel --max-workers=2 --rerun-tasks
```

The pre-change run at `ee0e8ad03910d44aee03bd3bdbd8e5f8ca704e6b` took 61.47 seconds. Two post-review runs at `b82d96eb6` took 23.28 and 23.41 seconds. All passed. The pre-change run started from a fresh worktree and included colder setup; the post-change runs used warm project and daemon state. This is not a controlled estimate of speedup or a full-build comparison. The command forces prerequisite tasks as well as the selected rows, and the shared host is not an isolated benchmark environment.

The final code revision passed the focused trap controls and eight rows, and `./gradlew check --no-parallel --max-workers=2` passed in 24m44s, including the full plugin suite and Groovy 3, 4, and 5 compatibility lanes. No local SonarCloud upload was performed.
