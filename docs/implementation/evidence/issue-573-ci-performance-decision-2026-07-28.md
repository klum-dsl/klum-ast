# #573 CI performance decision record (2026-07-28)

Status: final decision evidence for #573. This record is the authoritative repository
consolidation for the explicit Gradle build-cache hypothesis; it does not delete or
replace the older uncommitted `CI-PERF-DEC` memo.

## Scope and invariant CI contract

The ordinary CI path is one Ubuntu/JDK 17 job and one
`./gradlew check --scan sonar --info` invocation. It preserves Groovy 3, 4, and 5
validation, aggregate JaCoCo evidence, Sonar analysis, and the documentation/Javadoc
verification reached through `check`. This record makes no claim that Gradle
parallelism is absent or ineffective; the observed wall times alone cannot disprove it.

The separate public-release verification intentionally uses `--no-build-cache` and is
not changed by this decision.

## Ordinary-CI baseline and diagnostic observations

The prior CI-PERF evidence records successful ordinary hosted Gradle times from
**6m 49s to 10m 48s**, with median **8m 53s**. Those are observational samples, not a
controlled cache comparison.

The latest ordinary diagnostic completed in **10m 46s** and recorded a Sonar package
cache hit of **279 MB in about 3s**, followed by Sonar analysis of about **45s**. Its
build scan is [bxwzqhzfo5w4g](https://gradle.com/s/bxwzqhzfo5w4g). Six Javadocs and
documentation verification were observed in the task graph, but that observation does
not establish that any of those tasks is exclusively responsible for the elapsed time.

## Locked local feasibility observation

At the same source revision, authorized local `clean check --no-build-cache --info`
replicates took **2m 11s** and **2m 03s** (median **2m 07s**). A first explicit-cache
run populated the local cache; the warm explicit-cache replicate took **36s** with
**52 `FROM-CACHE`** task outcomes. The full `check` graph retained G3/G4/G5,
lane-isolation verification, JaCoCo aggregation, Javadocs, and documentation rendering.

This is workstation-only feasibility evidence. It neither predicts a hosted reduction
nor demonstrates cache persistence, comparable runners, or Sonar equivalence in
GitHub Actions.

## Hosted same-SHA controlled experiment

At commit `3babb60309419c3c1640fbde475e3dee42978d8b`, the temporary manual-dispatch
workflow ran two unchanged baselines and two runs with only `--build-cache` added.
All four succeeded, retained the ordinary CI graph, produced JUnit and compatibility
lane/JaCoCo artifacts, and completed Sonar analysis.

| Cohort | Run | Gradle wall time | Task result |
| --- | --- | ---: | --- |
| Baseline | [#451](https://github.com/klum-dsl/klum-ast/actions/runs/30345894511) | 9m 42s | 104 actionable: 82 executed, 22 up-to-date |
| Baseline | [#452](https://github.com/klum-dsl/klum-ast/actions/runs/30346041165) | 6m 57s | 104 actionable: 82 executed, 22 up-to-date |
| Explicit cache | [#453](https://github.com/klum-dsl/klum-ast/actions/runs/30346076395) | 9m 32s | 104 actionable: 82 executed, 22 up-to-date |
| Explicit cache | [#454](https://github.com/klum-dsl/klum-ast/actions/runs/30346113013) | 9m 14s | 104 actionable: 82 executed, 22 up-to-date |

The observed baseline median is **8m 20s**; the explicit-cache median is **9m 23s**.
The explicit flag was **63s slower** in this sample. One `FROM-CACHE` outcome already
appeared in baseline and cache runs, so it is not evidence that the explicit flag
improved hosted reuse. The Gradle action reported its cache read-only on the
non-default experiment branch; the concurrent enabled runs could not populate a shared
subsequent run.

## Decision and bounded follow-up

Do **not** adopt permanent `--build-cache`; the controlled hosted sample has no
meaningful cache win. Do **not** split CI lanes: no scan or resource evidence shows
CPU contention, and the three Groovy lanes remain required compatibility evidence.
Do not remove G4/G5, weaken JaCoCo or Sonar, omit documentation/Javadocs, or change the
clean-cache public-release verification.

Allowed future work is evidence-led only:

- inspect build-scan timelines and diagnose slow fixtures;
- perform a low-impact compatibility trial of Gradle configuration cache;
- measure docs/Javadocs and Sonar separately, without claiming exclusive task cost.

The temporary experiment branch and workflow remain available solely as reproducible
hosted evidence. Their removal, branch deletion, or any hosted workflow change needs
separate cleanup authority.

## Relationship to prior memo

Keep the older uncommitted `CI-PERF-DEC` memo unchanged. This committed record
supersedes its explicit build-cache hypothesis and implementation plan only; it does
not silently delete, rewrite, or discard the earlier ordinary-CI observations.
