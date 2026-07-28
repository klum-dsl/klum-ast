# #573 CI build-cache decision memo and experiment plan

Date: 2026-07-28
Evaluated revision: `6311e1dbf1dc358ddabd9069843c9bdb518d589b` (`origin/master` when this branch was created)
Scope: measurement and decision preparation only; no hosted-workflow, cache, coverage, or release-path change.

## Primary evidence observed

| Source | Observation |
| --- | --- |
| [#573](https://github.com/klum-dsl/klum-ast/issues/573) (read 2026-07-28) | It records successful hosted Gradle wall times of 6:49–10:48 (median 8:53), latest 10:46, and a roughly 45-second Sonar phase with a cache hit. It requires two comparable baseline reruns and two explicit-cache reruns on one SHA, preserving all quality evidence. |
| [CI workflow](../../../.github/workflows/ci.yml#L18-L60) | One Ubuntu/JDK-17 job invokes `./gradlew check --scan sonar --info`; `setup-gradle` is installed and only the Sonar package cache is explicitly declared. It publishes all JUnit XML and archives Groovy-4/5 JUnit plus JaCoCo artifacts. |
| [build wiring](../../../build.gradle#L45-L51), [renderer/Javadocs wiring](../../../build.gradle#L206-L214), and [coverage aggregation](../../../code-coverage-report/build.gradle#L19-L34) | `sonar` skips compilation, depends on JaCoCo reports, and `check` requires the versioned-documentation renderer, which requires representative module Javadocs. JaCoCo creates baseline, Groovy-4, and Groovy-5 reports. |
| [multi-Groovy convention](../../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle#L61-L147) and [testing policy](../../agents/testing.md#L3-L25) | `check` includes Groovy 3/4/5 execution and lane-isolation verification. The three lanes are distinct release evidence; baseline coverage is intentionally not duplicated for Sonar. |
| [release verification workflow](../../../.github/workflows/verify-public-release.yml#L29-L43) | Public-consumer verification intentionally uses clean dependencies and `--no-build-cache`; it is separate release confidence and must not be changed for this experiment. |
| [ADR 0011](../../adr/0011-shared-multi-groovy-compatibility-contract.md#L54-L67) and [4.0 release gate](../issue-curation/release-4.0.md#L177-L187) | Build-cache reuse is expected to be measured, but configuration-cache support is independent. Full three-lane, aggregate, documentation/Javadocs, and Sonar confidence remain release gates. |

`docs/implementation/evidence/ci-perf-dec-2026-07-28.md` was named by #573 but is not present at the evaluated SHA or in this worktree. Its timing figures above are therefore **issue-reported observations**, not independently re-verified by this note.

## Same-SHA hosted experiment

Use the exact commit above, runner image, JDK 17, and existing CI command/task graph. Make the only intentional difference explicit task-output cache enablement:

| Cohort | Runs | Command delta | Collect |
| --- | ---: | --- | --- |
| Baseline | 2 | current `./gradlew check --scan sonar --info` | workflow/job wall time, Gradle elapsed time, scan task timeline/outcomes, cache diagnostics, JUnit/artifact publication, Sonar result |
| Explicit cache | 2 | same command plus `--build-cache` | the identical fields, plus task `FROM-CACHE`/up-to-date/executed counts and cache restore/store diagnostics |

Do not interleave cohorts with a source or dependency change. Record runner image, Gradle version, action versions, cache key/hit state, retry status, and known service/rate-limit anomalies. Compare distributions and task outcomes, not a single fastest run; report a gain only if repeated cache-enabled runs show a material reduction without missing/changed required evidence.

## Decision boundaries

- **Observed:** #573's reported range and the current one-job command/artifact wiring.
- **Inference to test:** explicit `--build-cache` may improve task-output reuse beyond the existing action defaults; the current source does not prove it either way.
- **Not in scope:** removing G3/G4/G5, JaCoCo, Sonar, docs/Javadocs, or the clean-cache public-release verification; splitting lanes requires scan evidence of CPU/resource contention, not elapsed time alone.

## Local preflight observation (not hosted-CI evidence)

Two authorized local baselines at the evaluated revision used
`./gradlew clean check --no-build-cache --info`. They exited 0 in **2m 11s** and
**2m 3s**, each with 112 actionable tasks (81 executed, 31 up-to-date). The clean task
ensured fresh project outputs; `check` completed Groovy 3/4/5, lane-isolation
verification, JaCoCo aggregation, Javadocs, and documentation rendering. Sonar was
deliberately not run because this local-only measurement has no credentials and is not
an equivalent hosted scan.

This is an observed workstation result only. Its much shorter wall time neither
explains the hosted 6:49–10:48 range nor demonstrates a cache benefit. A second
`--no-build-cache` replicate and two separately locked `--build-cache` replicates are
the bounded local preflight; hosted evidence remains decisive.

The first `--build-cache` run then completed in **2m 0s** (also 81 executed and 31
up-to-date). It enabled the local Gradle User Home cache at
`~/.gradle/caches/build-cache-1` and stored entries for compile, test, and JaCoCo
tasks, but had no top-level `FROM-CACHE` outcomes: it is cache population, not a
comparison result. Lane-isolation tasks were correctly reported non-cacheable because
they have actions but no declared outputs.

The second cache-enabled run completed in **36s**: 29 tasks executed, 52 restored
`FROM-CACHE`, and 31 were up-to-date. Compared with the two no-cache runs (median
**2m 07s**), that is a local same-SHA reduction of **91s (72%)** while retaining all
three lanes, JaCoCo, Javadocs, and documentation verification. This establishes that
the current task graph has substantial reusable output; it does **not** establish the
same gain, cache persistence, or Sonar equivalence on GitHub-hosted runners.

No build configuration change or special harness is needed: Gradle's explicit
`--no-build-cache` and `--build-cache` flags are the minimal reversible switch. Every
local invocation must retain `clean check --info`, the same commit and environment, and
the Hive heavy-Gradle lock; the command output records task outcomes and elapsed time.

## Candidate safe implementation and authority still required

If all four hosted runs support a material, evidence-preserving gain, the smallest candidate change is to add `--build-cache` to the existing CI Gradle invocation only. It must retain the single `check --scan sonar --info` graph and all reporting/artifact steps. Authorization is still required to alter the workflow, push it, and dispatch/obtain the four hosted runs; none is granted by this note.

For a truly same-SHA hosted experiment, that authorization should cover one temporary
branch workflow with a manual `workflow_dispatch` cache-mode input. Dispatch it twice
with the current command and twice with only `--build-cache` added, all at the same
commit. Retain the scan URLs/logs, runner image, Gradle-action cache diagnostics,
G3/G4/G5 XML, JaCoCo artifacts, and Sonar result for every run. Only after that evidence
is green and the repeat-run reduction is material should a follow-up replace the
temporary switch with the one-line unconditional `--build-cache` invocation.

Retain the uncommitted `CI-PERF-DEC` memo until its raw hosted-run identifiers, timings, and candidate ranking are copied or reconciled into a committed decision record. This plan does not supersede evidence that is not available in the evaluated worktree.

## Hosted experiment result (2026-07-28)

The temporary manual-dispatch workflow at commit `3babb60309419c3c1640fbde475e3dee42978d8b`
ran the unchanged CI graph twice and that same graph with only `--build-cache` added
twice. All four runs succeeded, published the JUnit report and compatibility-lane
artifact, and reported successful Sonar analysis. Each reported **104 actionable
tasks: 82 executed, 22 up-to-date**. The observed Gradle wall times were:

| Cohort | Run | Gradle wall time | Result |
| --- | --- | ---: | --- |
| Baseline | [#451](https://github.com/klum-dsl/klum-ast/actions/runs/30345894511) | 9m 42s | success |
| Baseline | [#452](https://github.com/klum-dsl/klum-ast/actions/runs/30346041165) | 6m 57s | success |
| `--build-cache` | [#453](https://github.com/klum-dsl/klum-ast/actions/runs/30346076395) | 9m 32s | success |
| `--build-cache` | [#454](https://github.com/klum-dsl/klum-ast/actions/runs/30346113013) | 9m 14s | success |

The baseline median is **8m 20s** and the explicit-cache median is **9m 23s**;
the explicit flag was therefore **63s slower** in this sample, not a win. The logs do
show one existing `FROM-CACHE` task in every cohort, including the baseline, so it is
not evidence created by the explicit flag. `setup-gradle` also reported its cache as
read-only on the experiment branch, so neither concurrent cache-mode run could
persist outputs for the other. Sonar analysis remained successful (44.671s baseline
#452; 60.513s and 62.023s in the explicit-cache runs); its variation is not a
task-output-cache signal.

**Decision:** do not add unconditional `--build-cache`, do not split lanes, and do not
alter coverage, JaCoCo, Sonar, documentation/Javadocs, or release verification. The
safe implementation plan is now **no permanent CI change**; retain this temporary
manual-dispatch branch only as reproducible evidence until the Hive decides its normal
branch cleanup. `CI-PERF-DEC` should be retained, then superseded only by a committed
decision record that reconciles its historic timings with the four raw run IDs above.
