# Lane-isolation configuration-cache correction

Related: [#895](https://github.com/klum-dsl/klum-ast/issues/895). Base:
`44b23410ffa39f82f533d2261123d727cc4a2a14` (merged #908). The investigation read the baseline evidence commit
`f614c0ca56f576ab8a70411f48e604d32c7f7056`, merged #905–#908, ADR 0011, and the owning build conventions.
This is an internal build-logic slice, with no user API, documentation, changelog, migration, publication, workflow,
dependency/plugin version, global cache setting or release-policy change.

## Cause and correction

The strict serialization trace is:
`DefaultTask.actions → ClosureTaskAction.closure → doLast.testLanes → groovy.lang.Reference.value → lane map`.
That map retains three SourceSets and task providers that retain three GroovyCompile and three Test models. Each of
five module verifiers contributes nine problems / three unique kinds: **45 problems / 15 unique** in the full graph.
The live action additionally reads the version catalog, dependency-resolution model and project layout. Merely changing
the map's task providers would leave the other live model reads in the action.

`VerifyTestLaneIsolationTask` now owns the same assertions. Its nested lane inputs declare expected Groovy/Spock versions,
resolved module coordinate identities, classpath/output path membership, and expected/actual result locations. Lazy
providers project the models into strings and sets after consuming-project configuration, including late source-set
classpath replacement and output overrides. Dependency coordinates come from the resolution-result root provider and
include transitively resolved components and platforms. No task action resolves dependencies or accesses a Project,
SourceSet, compiler or Test model. This follows Gradle 8.14.4's
[model-type requirements](https://docs.gradle.org/8.14.4/userguide/configuration_cache.html#config_cache:requirements:disallowed_types).

Paths are values, rather than byte fingerprints: these checks compare path membership, not compiler inputs' contents.
The existing compiler/Test tasks still track source, resource and dependency bytes. Explicit dependencies and ordering on
all three Test tasks remain unchanged. The verifier still has no outputs and executes every time; it checks actual JUnit
directory existence at execution time, including on configuration-cache reuse. It is deliberately not an output-cacheable
verification task. No test compilation/output is shared across Groovy generations.

## Regression evidence

`buildSrc/.../testing/TestLaneIsolationTest.groovy` applies the real convention in a disposable project, reusing the root
settings' pinned catalog/plugin versions. Its real Spock test compiles and runs independently in G3/G4/G5. Ten cases cover:

- strict store/reuse, lane-specific compiled classes and JUnit XML, and recompilation of changed Groovy source in all lanes
  while configuration is reused;
- source-set classpath replacement after verifier configuration begins, compiler-classpath leakage, runtime leakage of
  compiled classes and relocated resources, unexpected test classes, wrong/missing result directories, and mismatched
  Groovy/Spock coordinates;
- each rejection starts from a healthy cached configuration, invalidates when the configuration property changes, and
  retains its diagnostic on both strict storage and reuse. The missing-results fixture deletes results after the real test
  finishes; it does not suppress the test.

All ten new cases and five existing descriptor-input cases pass without failures/errors/skips. Early development attempts
exposed a JUnit report provider lacking producer metadata, Groovy's dynamic dispatch of a private static helper, and
fixture probes failing before the intended assertion (XML-disabled directories, incompatible Spock engine and undeclared
resource-output dependency). Those attempts are excluded from the retained before/after comparison; each was corrected.

## Reproduce the owning root seam

From a frozen isolated checkout with Java 17:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
python3 docs/implementation/evidence/issue-895-lane-cache/verify.py
# Includes all five owning modules / all fifteen real Test tasks:
python3 docs/implementation/evidence/issue-895-lane-cache/verify.py --expanded
./gradlew -p buildSrc test --no-configuration-cache --no-build-cache --no-parallel --max-workers=2 --console=plain
```

For the negative control, restore only the convention script from the base, run `verify.py --expect-broken`, then restore
the corrected script. The retained pair uses the same HEAD, branch/version, source, host, dependency caches and harness;
only the convention changes. The new task class and test scaffolding exist in both controls, but the original action does
not use them. Source SHA-256 values identify each wiring. No commits occur during invocations. A harmless init script reads a unique probe property to select a fresh entry without deleting configuration caches
or replacing ordinary execution history; an empty init-script path alone can reuse a retained entry and is insufficient. `cleanTest`,
`cleanGroovy4Tests` and `cleanGroovy5Tests` force all three bean-validation Test tasks to execute on both attempts.
A harness draft expecting a fresh entry from a new empty init-script path instead found a valid older entry; that
attempt is excluded from the retained comparison. Raw logs stay outside the repository; [measurements.json](measurements.json) retains sanitized commands, log hashes,
counts, traces, task paths and JUnit summaries. `<probe>` denotes each temporary output directory.

Shared macOS arm64 workstation; Azul Zulu Java 17.0.3+7-LTS; Gradle 8.14.4. Dependency caches retained, output build cache
disabled, no parallel project execution, two workers. Single ordered before/after pairs are functional evidence, not a
controlled hosted or full-build performance claim.

| Probe | Original | Corrected |
| --- | --- | --- |
| Help | Strict store/reuse, 1.032 / 0.504s | Strict store/reuse, 1.069 / 0.484s |
| Three forced bean-validation lanes | Strict discard/discard, 7.603 / 7.319s, nine problems each | Strict store/reuse, 5.697 / 5.441s |
| Full `check --dry-run` | Strict discard/discard, 45 / 15 each | Strict store/reuse, 0 / 0, 0.763 / 0.534s |

Before/after focused and full repository task-path sets match exactly (excluding buildSrc configuration work, which is
absent on configuration reuse). All 14 retained JAR/JPMS descriptor byte hashes match before/after, including earlier version-named JARs left in build outputs; the comparison is of available files, not a new claim that every retained file was rebuilt. An ordinary pre-change bean-validation verifier control also
passes (32 actionable tasks, one executed / 31 up-to-date). A separate original strict invocation first executed all 32
actionable tasks, then failed storage with the same nine captures; its unchanged repeat also discarded its entry.

## Qualification boundary

A zero-problem dry-run inventory proves full-graph serialization, not every task action's compatibility. Renderer
acceptance still reads live Project state at execution time; full strict root execution needs separate qualification and
any remaining owner belongs outside this lane-isolation correction. The final PR/handoff records the expanded executing
store/reuse result, final frozen-head ordinary root `check` and artifact qualification, both review axes, and final-head
CI/Sonar. Ordinary root verification and independent G3/G4/G5 artifact qualification remain required.

Tracker impact: **Related: #895**; #895 remains open. Curation/release-gate impact: **none**. The worker requests Hive
reconciliation; it does not self-archive or merge.
