# Issue 890 / ADR 0029 AP-2 evidence

Date: 2026-10-14. Scope: AP-2 contribution normalization, legality and diagnostics only.
Base: `944925e8` (merged AP-1/AP-1b). Branch: `codex/issue-890-ap2`.

## Ownership and dependency route

The existing neutral KlumCast contributor remains the only production entry point. KlumAST returns a
complete default-only batch or no batch on propagation errors; it never mutates the declaration after
handoff. KlumCast owns constraint evaluation, validation state, binding/path diagnostics and rendering.
No VALIDATED metadata is copied, set or cleared. Required/Optional keep their existing local registrations;
their only change is carrying the input annotation's source position onto the lowered Validate annotation.

KlumCast annotations/SPI/compiler remain aligned at public `0.5.0-rc.2`, resolved by the ordinary
Central-only build. No dependency, repository, publishing, release-default or generated API change.

## Acceptance matrix

| Contract | Executable evidence |
| --- | --- |
| Repeated requests and explicit `PropagateAnnotation.List` | `AnnotationPropagationContributionTest`: Groovy source, compiled Groovy and separately javac-compiled Java 17 libraries; both request spellings |
| Genuine applied containers, explicit/default values, retained originals | Same matrix: physical container and domain values retained; selected defaults unchanged; unrelated arrays inert |
| Explicit plus propagated, two requests/domains, repeated applied domains | Same matrix: deterministic duplicate failure, even with repeatable selected Label; ordinary explicit repeats/containers succeed |
| Both contribution identities | `AnnotationPropagationRulesTest`: occurrence paths and container indexes identify repeated requests/domains separately |
| Type/target/default legality and one-hop guard | Source/Groovy/Java matrix: wrong target, required member, self-reference and cycle; rules/diagnostic tests add DSL and non-annotation selections before local casts |
| Default-only members | Both request spellings retain configured domain values and selected defaults; existing Required level/message non-forwarding control remains |
| KlumCast direct-control constraints | `AnnotationPropagationDiagnosticTest`: selected field-type/member constraints, introduced peer conflict, existing peer conflict and independently invalid original domain match direct binding/path and count, with effective origin/context |
| Applied source position | Rules test catches source-default containers pointing at annotation-library definitions; diagnostics now use the applied container |
| Downstream projection/lowering | Instruction-selection compiler observation: Required/Optional Validate nodes, Query Model/Builder twins and Cluster getter retain applied annotation positions |
| Collector interactions | Source and compiled Groovy collectors cannot be selected; early domain rewriting retains ordinary Groovy misplaced-marker failure, without merging/precedence |

The documentary matrix is
`AnnotationPropagationContributionTest#'repeated and explicit request containers retain domains and selected defaults (#kind, #policy)'`,
linked to [repeatable requests and containers](../user/Annotation-Propagation.md#repeatable-requests-and-containers).
Existing AP-1 documentary AutoCreate/DefaultValues coverage remains the primary consumer example.

## Rewrite limitation

Groovy's AnnotationCollector can replace an applied collector domain before the neutral contributor runs,
so no original domain remains to propagate or diagnose by name. Combining collector and propagation
metadata on a field-use domain produces the ordinary misplaced PropagateAnnotation target error in source
and compiled-library controls. Selecting a collector is explicitly rejected. No alias merging, collector
mode precedence, extra scheduler or late rescan is introduced. Independent third-party rewriting and
annotations introduced after handoff remain outside the ordering contract.

## Validation and delivery

Local qualification uses Temurin Java `17.0.17` with ordinary Central resolution:

```shell
./gradlew :klum-ast:test --tests '*AnnotationPropagation*Test' \
  :klum-ast:groovy4Tests --tests '*AnnotationPropagation*Test' \
  :klum-ast:groovy5Tests --tests '*AnnotationPropagation*Test' --max-workers=2
./gradlew check --no-parallel --max-workers=2
```

Both commands pass. Focused propagation results are 92 / 93 / 93 cases on Groovy 3 / 4 / 5,
respectively, with zero failures or skips (278 total). All 61 new AP-2 cases pass on every lane.
The full root run passes in 14m 22s; the compiler module reports:

| Lane | Cases | Failures/errors | Existing skips |
| --- | ---: | ---: | ---: |
| Groovy 3 | 1993 | 0 | 15 |
| Groovy 4 | 1995 | 0 | 15 |
| Groovy 5 | 1995 | 0 | 15 |

Root checks include auxiliary modules, Gradle plugin tests, lane isolation, license checks and the
versioned documentation renderer. No new tests are ignored or pending. `git diff --check` and changed
Markdown relative-link checks pass.

An earlier parallel root run encountered runtime-jar replacement while workers were reading it,
including invalid ZIP local-header and missing-class errors. The isolated Groovy 4 subset (10 cases)
and the complete serialized root rerun pass with fresh workers; no product changes were made for those
failures. The superseded overlapping/unfiltered run is not qualification evidence.

Standards review against `944925e8...ea775d4e`: one P3 stale ADR statement about missing source positions,
corrected in the final evidence commit; no other documented violations or material smells. Specification
review on the same fixed diff: no actionable findings or AP-2 acceptance gaps. The five implementation/
documentation commits were reviewed for scope, order and reasoning before publication; the final commit
records validation and the ADR correction. Reviewed implementation commits are preserved; later
SonarCloud fixes are additive.

Delivery audit: GitHub CLI authorized; Git push authorized by dedicated-branch dry run. No connected
GitHub App channel was used. Final-head CI and SonarCloud results belong to the draft PR checks and PR
validation record; no claim here substitutes for checking the exact pushed revision.

CI run `38614581432` passes on `be11d9af`. Its first SonarCloud analysis reports 100% new-code coverage
and 0% duplication, but finds seven test-only observations: one GString map key, four unbraced branches,
and two explicit repeatable-container spelling suggestions. The additive follow-up converts the key to
String, braces the branches, and keeps the two explicit Java containers with declaration-local
`java:S1710` suppressions explaining why those exact source inputs must remain. No production code or
accepted behavior changes. The same 278-case Java 17 / Groovy 3/4/5 focused matrix passes after the
follow-up (54s), with no failures/skips. Final-head remote verification is recorded on the PR.

Tracker discrepancy: GitHub reports issue #890 as closed, with its investigation-era body and no comments.
This implementation follows the accepted ADR/plan and explicit AP-2 assignment. Issue state, labels,
milestone and release/curation placement are unchanged. PR relationship is `Related: #890`.
AP-3/AP-4/AP-5, consumer admission and final release/issue reconciliation remain separate work.
Curation and reusable agent-policy impact: none; no policy files changed.
