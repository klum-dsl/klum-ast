# Gradle-plugin test configuration-cache input correction

Related: [#895](https://github.com/klum-dsl/klum-ast/issues/895). Base:
`133d3a7ba67b3cbbe05d2ee5d65dcd68b6dbaeb0` (merged PR #907). Read the baseline evidence at
`f614c0ca56f576ab8a70411f48e604d32c7f7056`, merged PRs #905/#906/#907, ADR 0011, and the owning root,
plugin and buildSrc conventions before changing the input wiring. This is an internal build-correctness slice;
configuration time does not explain the parent issue's CI wall time.

## Original failure and selected owner

`check --dry-run --configuration-cache --configuration-cache-problems=fail` reproduces **46 problems / 16 unique**.
The serialization trace for `:klum-ast-gradle-plugin:test` ends at its unnamed input property `$1`: the explicit
`inputs.files(project(":klum-ast").tasks.named("jar"))` retains a `Jar` task. A focused **executing** plugin test command
reproduces exactly that one strict storage problem; the entry is discarded on both attempts.

The three ranked causes were the explicit task input, filtered GDSL fixture classpaths, or indirect plugin callbacks.
The trace identifies the explicit input. Replacing just that notation removes the failure; no other hypothesis needs a
build change. This is the smallest safe owner correction: one line enables the full plugin verification graph. The
larger lane-isolation owner requires a separate treatment of source sets, task models and dependency resolution.

Use `tasks.named("jar", Jar).flatMap { it.archiveFile }` for the existing input. Gradle can serialize the archive
provider while retaining its producer dependency. This follows Gradle 8.14.4's
[task-access and model-type requirements](https://docs.gradle.org/8.14.4/userguide/configuration_cache.html#config_cache:requirements:task_access).
No task is reclassified as cacheable and no task output is reused without its existing inputs.

## Reproduce the real seam

From an isolated checkout, with Java 17 and unchanged HEAD:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
python3 docs/implementation/evidence/issue-895-configuration-cache/verify.py
```

Run `verify.py --expect-broken` with the original input line restored to reproduce the negative control. The retained
before/after controls use the same base HEAD, Git-derived version, dependency caches, wrapper, host and final harness.
They differ only in the owning build script's recorded SHA-256. The corrected probe ran first, followed by the original
negative control; the correction was then restored. No commit was made during an invocation.

The harness uses the ordinary project cache. A new empty init-script path selects a fresh configuration entry for each
help/core/plugin pair without deleting existing entries or replacing task history. Plugin pairs include `cleanTest`, so
**both storage and reuse actually execute the 14 focused archive tests**, rather than only testing a dry run or skipping
the task as up-to-date. Help and the original focused core test remain independent controls. It also checks the remaining
strict full-check problem count and the Jar capture's presence/absence. Raw logs/cache snapshots stay outside source
control; sanitized commands, timings,
log hashes, implementation hashes, problem inventory and graph paths are in [measurements.json](measurements.json).
`<probe>` denotes the fresh temporary directory created by the script.

The additional archive probe configures a harmless resource from a temporary file into the real compiler Jar. It asserts
that original Task file notation and the archive provider resolve to the same file set, and that the plugin Test retains
that archive as an input. An unchanged repeat is up-to-date; changing resource contents rebuilds the Jar and executes the
plugin tests **while reusing configuration**. No source file is mutated for this probe. The next ordinary invocation
rebuilds the Jar without the temporary resource. Full-check and focused graph task-path sets are identical before/after,
including the compiler Jar prerequisite.

A synthetic TestKit test of copied input wiring would duplicate the implementation. This opt-in harness exercises the
actual root build script instead; it is targeted regression evidence, not a new protected CI gate or permanent cache setting.

## Observed results

Shared macOS arm64 workstation; Azul Zulu Java 17.0.3+7-LTS; Gradle 8.14.4. Dependencies and existing outputs retained,
Gradle build cache disabled, serialized with two workers. Timings are descriptive single pairs, not a controlled speedup
estimate. In particular, the corrected focused-core storage run re-executed tests, while the later original control reused
warm outputs; their first-run timings are not comparable.

| Real graph | Original | Corrected |
| --- | --- | --- |
| Help | Strict store/reuse | Strict store/reuse |
| Focused core test | Strict store/reuse | Strict store/reuse |
| Focused plugin test, forced execution both times | Strict discard/discard; 3.318s / 2.306s | Strict store/reuse; 2.209s / 2.039s |
| Full check dry run | 46 problems / 16 unique; discarded | 45 problems / 15 unique; discarded |
| Archive-content control | Not applicable | Store 2.318s; unchanged reuse 0.501s, Test up-to-date; changed reuse 2.042s, Test executed |

This establishes a usable opt-in plugin test loop and complete archive input tracking, not a hosted full-build acceleration.
Early harness drafts had three initialization/introspection failures before reaching the archive-content control (task
registration order, buildSrc scope and Gradle's project-state lock). A temporary `--project-cache-dir` draft also discarded
execution history on replay. Those drafts are excluded from the retained comparison; the final harness uses the ordinary
project cache and checks reachability from the real executed graph.

## Remaining owner inventory and preserved contracts

The remaining **45 storage problems / 15 unique** are exactly three unsupported model kinds in each of five
`verifyTestLaneIsolation` tasks: SourceSet, GroovyCompile and Test, with three occurrences per kind/task. Owners:
`:klum-ast`, `:klum-ast-runtime`, `:klum-ast-jackson`, `:klum-ast-bean-validation`, `:klum-ast-test-support`.
The plugin Test Jar capture is absent. Full-check strict cache execution remains unsupported; a dry run cannot inventory
additional task-action problems behind this storage barrier (including renderer live Project access).

One provider substitution preserves task input bytes, the Jar producer, full task-graph reachability, GDSL classpath
selection, generated metadata, all three Groovy qualification lanes, lane-isolation checks, coverage, documentation
rendering, publication fixtures and artifact qualification. It changes no DSL/API, current user docs, migration or
changelog entry; no release gate, workflow, dependency, plugin version or curation state changes. No global cache property,
warning-mode acceptance, task exclusion or dependency weakening is introduced. Ordinary `check` and the explicit artifact
lanes remain required for final qualification.

The final handoff/PR records the frozen validation head, all-plugin strict store/reuse, normal root check and artifact
results, Standards/Spec reviews and current CI/Sonar status. Tracker impact: **Related: #895**, curation impact **none**;
parent investigation remains open. Hive reconciliation is required for archive safety.
