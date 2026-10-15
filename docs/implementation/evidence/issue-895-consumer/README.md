# #895: lane-local lifecycle consumer prerequisites

The bounded follow-up removes **36 subprocess launches per Groovy lane** while retaining every existing consumer
scenario. Two focused repeats per revision and lane show mean suite-time improvements of **15.0% / 6.5% / 10.3%** for
Groovy 3 / 4 / 5. This is local fixture evidence, not a controlled hosted-build speedup claim. Issue #895 remains open.

## Method and revisions

Base: `11c5503f49a9e6bf6e00f5b1d043ccda36144a7b` (PR #902). The parent investigation was supplied as local commit
`f614c0ca56f576ab8a70411f48e604d32c7f7056`, file `docs/implementation/evidence/issue-895/README.md`.
Only this test and its measurement/evidence files change. Production source, public APIs, build logic, CI, release gates,
global caches and required verification breadth remain unchanged.

Isolated managed worktree, dedicated branch `codex/issue-895-consumer-prerequisites`. Baseline test content is exactly
commit `812816c5` (instrumentation only); optimized test content is exactly `953d4df1`. The baseline runs were captured
before committing instrumentation, and optimized runs before committing reuse. [measurements.json](measurements.json)
retains invocation HEAD **and the actual consumer source SHA-256**, verified against those committed snapshots. This is
a same-production-source/environment comparison with the intended test-fixture diff, not an identical-test-source SHA
experiment. No persistent baseline-mode switch or benchmark timing assertion was added to the normal tests.

Host: Apple M3 Max, 16 logical CPUs, 64 GiB RAM, macOS 27.0.1 (26A434), arm64. Java: Azul Zulu 17.0.3+7-LTS;
Gradle 8.14.4; Groovy 3.0.25 / 4.0.32 / 5.0.6 and matching Spock 2.4. Existing dependency/transform caches and daemon
were retained. Task-output build cache and configuration cache were disabled. An initial G3 bootstrap (74.044s suite)
is excluded from the table. Each selected Test task is forced to execute; production/prerequisite tasks can remain
up-to-date. First compatibility invocations and first optimized invocations also compile changed lane test sources, so
**JUnit suite duration** is the primary comparison; wrapper times are retained separately.

The shared desktop was not idle (sampled load averages about 6.7–14.3). This task launched one Gradle invocation at a time,
with `--no-parallel --max-workers=2`; it cannot exclude other users' work. All baseline runs preceded optimized runs.
Two samples per lane are descriptive, not a confidence interval; G4 has material variation and no hosted gain is inferred.

Reproduce on the corresponding revision, with Java 17 and Python 3:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
python3 docs/implementation/evidence/issue-895-consumer/measure.py \
  --label before --repeats 2 --output /tmp/consumer-before
# On the optimized revision, use --label after --output /tmp/consumer-after.
```

The script invokes, sequentially for `test`, `groovy4Tests`, and `groovy5Tests`:

```sh
./gradlew :klum-ast:<lane> --tests com.blackbuild.klum.ast.LifecycleParticipantConsumerTest \
  --init-script <temporary-timings-init-script> \
  --no-build-cache --no-configuration-cache --no-parallel --max-workers=2 --console=plain
```

Its temporary init script sets Test system property `klum.consumerTimings=true` and `outputs.upToDateWhen { false }`.
The opt-in hook reports each successful subprocess's Groovy version, category, epoch-millisecond start, monotonic elapsed
nanoseconds and source names. Durations include process setup, startup, output draining and waiting. `groovyc` is an
external JVM running the lane's compiler; `java` counts runtime consumers. The existing in-process ToolProvider negative
Java compilation is not a subprocess and remains unchanged. Full logs, per-process JSON and XML stay in the selected
local output directory. Committed evidence retains category counts/durations/start ranges, individual domain-compiler
events, case names and source identities; no raw build logs or private host identifiers are committed. The hook is silent
and inactive during ordinary verification.

## Exact focused results

All runs have zero failures, errors or skips. Original case names were compared as sets between revisions in every lane.
The optimized counts are 36 / 37 / 37: all original 35 / 36 / 36 cases plus one copy-isolation regression test.

| Groovy | Before suite repeats (s) | After suite repeats (s) | Before mean (s) | After mean (s) | Reduction |
| --- | --- | --- | ---: | ---: | ---: |
| 3 | 74.217, 72.604 | 62.251, 62.482 | 73.411 | 62.367 | 15.0% |
| 4 | 75.780, 72.746 | 73.106, 65.825 | 74.263 | 69.466 | 6.5% |
| 5 | 78.621, 74.955 | 70.655, 67.082 | 76.788 | 68.869 | 10.3% |

Counts below are identical in both repeats. Only unchanged domain compilation and packaging are removed.

| Lane | Groovy compiler before → after | javac before → after | JAR before → after | Runtime before → after | Total before → after |
| --- | ---: | ---: | ---: | ---: | ---: |
| G3 | 60 → 42 | 68 → 68 | 96 → 78 | 28 → 28 | 252 → 216 |
| G4 | 62 → 44 | 74 → 74 | 100 → 82 | 29 → 29 | 265 → 229 |
| G5 | 62 → 44 | 74 → 74 | 100 → 82 | 29 → 29 | 265 → 229 |

`CompositionDomain.groovy`, `TypeDomain.groovy` and `ContainerDomain.groovy` previously compiled 9 / 5 / 7 times per lane.
Their first baseline repeats consumed 11.425s / 11.482s / 12.113s in domain compiler processes for G3 / G4 / G5, before
packaging. Each now compiles and packages exactly once **in its own lane**. The measured suite means save 11.044s / 4.798s /
7.920s. Remaining process timing variation explains why removing the same work does not produce the same elapsed saving.

## Reuse boundary and preserved controls

A specification-local `@Shared @TempDir` owns a lazy map of three immutable domain artifacts. The key selects a fixed
resource; compiler JARs, lane runtime/compiler classpath, Java home and compiler options are constant within that
specification execution. There is no persistent/task cache and no unmodeled invalidation across executions. Shared state
is neither static nor reachable from another Groovy lane. Initialization is synchronized and records only successful
compilations; master sources and classes are deleted before any copy is returned.

Each scenario receives a physical `Files.copy` into its own existing TemporaryFolder. The private master archive is
never passed to a scenario compiler or runtime. The new #895 test replaces one copy and verifies an existing independent
copy and a later copy remain byte-identical and distinct files. Lazy construction avoids compiling unrelated domains
when selecting a single feature.

All scenario-specific annotation libraries, Java/Groovy author variations, phase/form rows, schemas, Java/static/dynamic
Groovy consumers and library replacements still compile in their own directories. All rejection assertions, generated
and binary contract checks, deleted-source/class runtime consumption, compiler-free runtime filtering and forbidden
JPMS escape-flag checks remain. The classpath and G4/G5 named-module fixtures are untouched and receive no shared artifact.
Groovy 3 remains classpath-only under ADR 0014; independently compiled G3/G4/G5 fixtures preserve ADR 0011.

No user-visible behavior changes, so user pages, migration navigation and CHANGES need no update. This is a local test
fixture implementation, not reusable agent policy; no engineering-baseline change is needed.

## Separate publication seam

`TemplateScopePublishedConsumerTest.publishFixture` uses `GradleRunner.withProjectDir(projectDirectory())`, where the
module supplies the active root checkout as `klumProjectDirectory`. Its temporary Maven repository isolates publication
destination, **not root compilation/JAR/descriptor outputs**. Concurrent outer work can therefore share those mutable
outputs with the nested publication build. This confirms the seam recorded by the parent investigation; it does not
establish a race frequency or a safe correction. The test remains unchanged because domain-artifact reuse does not depend
on it. Local full verification is serialized to bound overlap; ordinary CI topology remains unchanged. Isolating nested
publication belongs in a separate bounded follow-up under #895.

## Verification and delivery

Focused G3/G4/G5: two successful repeats per revision, every original case preserved, new isolation test passes.
Full root `check` and the three explicit annotation-propagation artifact lanes use the following command
(the code content is `953d4df1`; final qualification results belong to the PR/handoff):

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew check \
  :klum-ast:annotationPropagationArtifactsGroovy3 :klum-ast:annotationPropagationArtifactsGroovy4 \
  :klum-ast:annotationPropagationArtifactsGroovy5 \
  --no-build-cache --no-configuration-cache --no-parallel --max-workers=2 --console=plain
```

The initial broad run (776.70s wrapper) passed core G3/G4/G5 with 2040 / 2042 / 2042 cases, zero failures and 15 existing
skips in each lane, then failed 11 source-mirror plugin fixtures. The outer build captured Git-derived version `953d4df`,
but an evidence commit advanced HEAD to `98170b2` during execution; the nested publication therefore published a different
version from the consumer request. This was a validation-procedure error, not evidence of a consumer-reuse defect.
Do not commit or rewrite HEAD during repository tests: nested publication reads live Git version state. Final qualification
must rerun with HEAD frozen; the PR/handoff records that result rather than treating this attempt as successful.

Local Standards and Spec reviews found no implementation or scope defect. Standards identified baseline hostname metadata;
it was removed before publication and rechecked. Review children were read-only (root + two reviews, capacity 3), ran no
builds or remote mutations, and stopped after the results. Commit-history review retains three reasoning steps:
measurement loop, independently validated reuse/isolation, and sanitized evidence. No user-facing documentation changes.

Final-head hosted CI/Sonar is also required for the handoff. Hosted CI is correctness qualification; a controlled
identical-SHA hosted performance experiment remains separate follow-up work.
Tracker relationship: `Related: #895`; curation impact: none. No release gate, dependency, owner or published fact changes.
