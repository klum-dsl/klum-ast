# Descriptor task inputs across verification invocations

Related: [#895](https://github.com/klum-dsl/klum-ast/issues/895). This bounded follow-up starts at merged
[PR #905](https://github.com/klum-dsl/klum-ast/pull/905), base
`649bd52e3c85433e75b04cee0f0e114e0811f6ea`. The original investigation is retained at
`f614c0ca56f576ab8a70411f48e604d32c7f7056:docs/implementation/evidence/issue-895/README.md`.

## Finding and change

Four `compileModuleInfo` tasks execute again after the test-support published-consumer fixture uses TestKit to publish
from the active checkout: annotations, runtime, compiler, and test support. They do **not** ordinarily recompile on an
unchanged outer repeat. The nested build uses `build/tmp/test/work/.gradle-test-kit` as its Gradle user home; the outer
build uses the ordinary user home. Both write the same production outputs and task history. Previously the descriptor
convention put an absolute `--module-path` string into `options.compilerArgs`. Changing dependency-cache locations changed
that scalar input in both directions, even though the ordered dependency filenames and complete JAR bytes matched.
The sequential outer probe reported `Value of input property 'options.compilerArgs' has changed` for all four tasks.
The next artifact-qualification invocation thus recompiles these descriptors, while the adapter descriptors, which the
nested publication does not reach, can remain up-to-date.

More seriously, the old string tracked module **paths**, without declaring their **contents** as task inputs.
Replacing a dependency's `module-info.class` at the same path could reuse stale compilation, even after the required module
disappeared. A regression control demonstrates this with identical dependency class bytes and changed module metadata.

`ModulePathArgumentProvider` now supplies the same compiler arguments through `compilerArgumentProviders`. Its
`@Classpath` input fingerprints full module-path contents, including module descriptors, dependency implementation
classes and resources. It deliberately does not use ABI-only `@CompileClasspath` normalization for that path.
Its ordered `@Input` filenames preserve automatic module identity, which may depend on a JAR name. Cache-directory
relocation can reuse valid compilation; module bytes, filenames and ordinary compiler-option changes cannot silently reuse it.

## Input/output and ownership rationale

| Input or output | Owner and treatment |
| --- | --- |
| `src/main/module-info/**`, including package markers | Existing `JavaCompile.source` input, unchanged. |
| Compiled production packages | Existing `JavaCompile.classpath = sourceSets.main.output`; Gradle owns compile-classpath normalization and production task dependencies. |
| Resolved Groovy-4 module path | New nested `@Classpath` file input on the argument provider; same dependency configuration, exclusions and order as before. |
| Module-path filenames | New ordered scalar input for automatic module names; absolute cache roots are not semantic inputs. |
| JDK, release 17, debug/parameters and other compiler settings | Existing JavaCompile inputs, unchanged. |
| Descriptor classes and incremental compiler state | Existing JavaCompile output directory and local compilation state; no new shared output location or custom up-to-date predicate. |
| Production JARs | Existing JAR tasks still depend on descriptors and include only `module-info.class` from their output; main resources remain JAR inputs. |
| Publication metadata | Existing live Gradle `GenerateMavenPom` model and destination; generation remains unconditional. |
| Documentation | These seven tasks do not read documentation sources. Publication documentation URLs remain live POM metadata; renderer/Javadoc ownership is unchanged. |

The explicit `dependsOn(configurations.jpmsModulePath)` remains. Compiler `Test` prerequisites, POM file inputs,
all six production JAR dependencies, artifact qualification, binary-consumer deletion/replacement controls, classpath and
JPMS assertions, lane isolation, and the separate Groovy 3/4/5 test/fixture compilations are unchanged. Production descriptors
remain the same single Java-17/Groovy-4 descriptor compilation over the Groovy-3 production artifact, as before.
No test or fixture output is shared across lanes; [ADR 0011](../../../adr/0011-shared-multi-groovy-compatibility-contract.md)
continues to govern that boundary.

The three POM generators execute every time because Gradle 8.14.4's `GenerateMavenPom` explicitly carries
`@UntrackedTask`: Gradle cannot model the publication data structures. The local distribution's
`src/maven/org/gradle/api/publish/maven/tasks/GenerateMavenPom.java` confirms the annotation, internal POM model and
declared output file. Measured generation is only about 4–8 ms total for these three tasks. Adding an output declaration,
timestamp guard, or a partial publication-property fingerprint would not cover dependency constraints, scopes, version
mapping, XML customizations and all publication metadata. This slice leaves them untracked and does not introduce a
synthetic fixture POM or replace live publication inspection.

## Reproduction and measurements

On Java 17, first populate the real nested fixture cache:

```shell
./gradlew :klum-ast-test-support:test --tests com.blackbuild.klum.ast.testsupport.TemplateScopePublishedConsumerTest --no-build-cache --no-configuration-cache --no-parallel --max-workers=2
python3 docs/implementation/evidence/issue-895-task-inputs/measure.py --label baseline --output /tmp/descriptor-baseline
```

Repeat the harness against the changed implementation with a different label/output directory. Both measurements must
use the same frozen Git HEAD, branch/version, host and JDK; the implementation variants are separately hashed. Do not
commit, edit buildSrc or run another root/nested build during a cycle. Use existing dependency caches; do not delete them.

The loop selects exactly the four descriptor prerequisites plus the three POM generators. Each cycle runs an outer
invocation, the identical graph with the **existing real TestKit home**, an outer artifact-prerequisite invocation,
then an unchanged outer repeat. It does not execute artifact tests or publish: this isolates prerequisite costs from
consumer compilation and publication I/O. The real nested consumer test established the boundary; broad root/artifact
validation separately exercises the real graphs. `--info`, disabled task-output/configuration caches, serialized projects,
and two workers apply throughout. [measure.py](measure.py) retains raw local logs and fails on revision/implementation
drift; [trace.init.gradle](trace.init.gradle) observes task outcomes without changing their dependencies or up-to-date rules.

[measurements.json](measurements.json) records two baseline, two initial-after, and two fully warm after cycles, exact
targets/flags, raw-log digests, implementation hashes, ordered module filenames/content hashes, and complete output hashes.
All use Git HEAD `649bd52e3c85433e75b04cee0f0e114e0811f6ea` with separately hashed uncommitted buildSrc variants. Host:
shared Apple M3 Max/macOS arm64, Azul Zulu 17.0.3+7-LTS, Gradle 8.14.4. No hostname, credentials or serialized cache is retained.

| Boundary, two repeats | Before descriptor executions | After descriptor executions | Before descriptor time, total of four | Warm after descriptor time, total of four |
| --- | --- | --- | --- | --- |
| TestKit home | 4 / 4 | 0 / 0 | 0.2232 / 0.1752 s | 0.0085 / 0.0057 s |
| Following outer artifact prerequisites | 4 / 4 | 0 / 0 | 0.1515 / 0.1431 s | 0.0046 / 0.0044 s |
| Unchanged outer repeat | 0 / 0 | 0 / 0 | 0.0020 / 0.0018 s | 0.0032 / 0.0086 s |

POM executions remain 3 / 3 on every row. All seven output files have one byte-identical hash across every before/after
record. Ordered module-path fingerprints also match across locations and revisions. The fully warm TestKit wrapper
times are 1.180 / 0.943 s before and 0.861 / 1.125 s after; following outer wrapper times are 0.925 / 0.876 s before and
0.660 / 0.695 s after. These small ordered samples on a shared host demonstrate valid reuse, **not** a controlled full-build
or hosted speedup. First-after outer/TestKit runs compile changed buildSrc code and are retained separately; they are
not warm timing comparisons. An early exploratory outer probe briefly overlapped the real consumer fixture; none of
its timing enters this serialized loop.

## Invalidation controls and validation

Run the focused buildSrc seam independently; ordinary root builds compile buildSrc without running its tests:

```shell
./gradlew -p buildSrc test --tests com.blackbuild.klum.ast.compile.JpmsDescriptorInputsTest --no-build-cache --no-configuration-cache --no-parallel --max-workers=2
```

Five Spock tests marked `@Issue('895')` exercise the actual convention using hermetic Java modules:

- Unchanged and byte-identical same-name relocated module JARs reuse compilation; descriptor bytes match both the prior
  output and the module descriptor entry in the packaged production JAR. Deleting the descriptor repairs it byte-for-byte.
- Changing only dependency module metadata at the same path, with byte-identical API classes, reruns javac and rejects
  a missing required module. Restoring valid metadata permits compilation again.
- Dependency class and resource changes rerun compilation. Full module bytes are intentionally tracked conservatively.
- Renaming byte-identical automatic-module JARs reruns javac and rejects the missing original module identity.
- Compiler options, descriptor source and production class changes invalidate compilation. Own resource changes rerun
  resource processing and packaging, preserve the correct resource bytes, and need not recompile a descriptor.

All five pass. Temporarily restoring the original argument wiring makes three fail: relocation reuse, the same-path
metadata rejection (an unexpected build success), and content invalidation. The two unaffected tests remain green.
The implementation was automatically restored and all five passed again; failed fixture-setup iterations are not
reported as successful validation.

A separate live-publication control changed POM description and documentation URL through an init script: all three
generators executed, all three output hashes changed, and every POM contained the new values. Removing the init script
restored all three original byte hashes. No publication model or repository configuration change is retained.

Required broad validation commands for the frozen publication head are:

```shell
./gradlew check --no-build-cache --no-configuration-cache --no-parallel --max-workers=2
./gradlew :klum-ast:annotationPropagationArtifactsGroovy3 :klum-ast:annotationPropagationArtifactsGroovy4 :klum-ast:annotationPropagationArtifactsGroovy5 --no-build-cache --no-configuration-cache --no-parallel --max-workers=2
```

The pull-request validation record reports their actual results, head SHA, two-axis reviews, and final-head CI/SonarCloud
state. No user API, DSL syntax, migration or release-facing behavior changes, so no user documentation or changelog
entry is required. No renderer, configuration-cache, workflow, protected gate, release policy or CI topology change is
included. Tracker impact: `Related: #895`; the parent investigation remains open. Curation impact: none.
