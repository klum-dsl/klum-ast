# AP-1 default-only propagation — public RC.2 qualification

Date: 2026-10-14. Related: [#890](https://github.com/klum-dsl/klum-ast/issues/890).
Authority: [ADR 0029](../adr/0029-default-only-annotation-propagation.md) and its
[plan](adr-0029-default-only-annotation-propagation.md).
Base: `81b61ad3c3098d57824301de4fdeb7ca499df031`.

## Current dependency route and admission

All three `com.blackbuild.klum.cast` coordinates resolve normally from Central at exact `0.5.0-rc.2`.
There is no Maven Local repository or development version in the current build or opt-in probe.
RC.2 supersedes dev.8 and RC.1; all earlier results are historical and supply no current qualification.
The [release](https://github.com/klum-dsl/klum-cast/releases/tag/v0.5.0-rc.2) identifies source
`4c43059933d15edfb57579e4374bb9817ee9a68c`.
[Public resolve-back](https://github.com/klum-dsl/klum-cast/actions/runs/38586084323) is upstream evidence;
the following qualification is the independent KlumAST result.

| Artifact | SHA-256 of resolved jar |
| --- | --- |
| klum-cast-annotations | `ac0908461574e0023db5ae39902bb5c7a237f26c9f2c2ab57e0a64c117aa3515` |
| klum-cast-spi | `7eecfcf861f4995909fc28ff9bbbd9d022eb7ddf155c2acdf5c13e4d98bbd074` |
| klum-cast-compile | `7789b1104ff72152b904c1987265fa390ba6e25f3e51e93c387b08436ca8dd87` |

The ordinary Gradle no-op provider/control compiles on Groovy 3/4/5. The production contributor is also
exercised through actual Gradle-compiled Schemas in `AnnotationPropagationGradleTest`, not just test-created
GroovyClassLoaders. Both paths cross the AST-transform/source-loader boundary that failed with RC.1.

## Implemented behavior and coverage

The JDK-only retained, repeatable marker and internal compiler contributor ship together. The contributor
returns a complete batch through the supported neutral seam and never attaches annotations or touches
validation state. Its separate eligibility, normalization and selection checks reject non-annotation/DSL
selections, illegal targets, missing defaults, nested requests and duplicates. Originals and source/origin
provenance remain available. No configured member values, runtime annotation instance, validation engine,
local-transform calls or global scheduling workaround are propagated.

| Test class | Evidence |
| --- | --- |
| AnnotationPropagationTest | Required/Optional direct controls and normal conversion; AutoCreate with retained DefaultValues, supplied-child precedence and documentary example; Query/Cluster Builder/Factory signature parity and source mirrors; separately compiled foreign class/field/method local transform; KlumCast origin/context and one diagnostic; Java request container/Groovy repeated requests and binary parent inheritance |
| AnnotationPropagationRulesTest | Nested/self requests, target/type/default-member errors, explicit/multiple/repeated/genuine-container duplicates, repeatable selected types and direct repeats, no member forwarding, inert ordinary/interface/parameter sites, same-source defaults, defaulted genuine containers and explicit container identity, SOURCE/CLASS/RUNTIME bytecode retention, class/field/method target defaults |
| AnnotationPropagationGradleTest | Separately javac-compiled policies and Gradle-compiled Schema exercise Required/AutoCreate/Query/Cluster with the production service and normal local discovery |
| AnnotationPropagationArtifactTest | Actual jar descriptor with no new exports; separate Java policy, Groovy Schema and typed Java Builder/Factory consumer artifacts; classpath Groovy 3/4/5, named Groovy 4/5 compiler/library/Schema/consumer modules, runtime-only consumers without compiler/SPI engine or access-bypass flags |

Java 17, Groovy 3.0.25 / 4.0.32 / 5.0.6. Focused sentinel/rules/Gradle cases: 29 per lane;
artifact cases: 2 on Groovy 3, 3 each on Groovy 4/5, for totals of 31 / 32 / 32. All pass with no skipped tests.
Standards and specification reviews have no remaining findings after container/probe corrections.
New specifications carry `@Issue('890')`; the DefaultValues example is documentary with an absolute
`@See` link to [user guidance](../user/Annotation-Propagation.md#default-only-composition).

## Validation and delivery status

Focused production and artifact/module matrices pass. Full root `check` passes after the maintainer's
approved alignment of the two existing version/offline-Grab fixtures to public RC.2. The initial four
stale-fixture failures are corrected without weakening version/module or package-ownership assertions.
Standards/specification reviews pass, including the focused container/default fixes and small helper
extractions. The final source focused matrix and license task pass again after those extractions.
The final organized publication candidate also passes full root `check` after the helper extractions
and commit-history review. No source changes follow that run; only this qualification record is finalized.

| Full compiler lane | Cases | Failures / errors | Existing skips |
| --- | ---: | --- | ---: |
| Groovy 3 | 1,932 | 0 / 0 | 15 |
| Groovy 4 | 1,934 | 0 / 0 | 15 |
| Groovy 5 | 1,934 | 0 / 0 | 15 |

All 95 new propagation cases pass without skips. Root checks include affected and auxiliary module
suites, lane isolation, license and documentation verification. `git diff --check` and changed Markdown
relative-link checks pass. The three commits separate public dependency adoption, the working guarded
feature with tests/user docs, and qualification/ADR state. No development init script is in this history.
CLI repository-write and Git transport dry-run authorization are both verified; no GitHub App is used.
Tracker impact: related issue #890; no curation files change. Hive retains consumer/release reconciliation
and canonical issue/documentary cross-link upkeep; this slice does not select issue-closing syntax.

Reproduction of production qualification uses normal resolution, with no init script:

```shell
./gradlew :klum-ast:test :klum-ast:groovy4Tests :klum-ast:groovy5Tests \
  --tests '*AnnotationPropagation*Test' --max-workers=2
./gradlew check --max-workers=2
```

The historical no-op injection route has been removed because the shipped production provider now
owns the service resource. The [discovery record](evidence/issue-890/ap1-gradle-service/README.md) points
to ordinary Gradle-compiled Schemas and actual artifact consumers as the maintained regression coverage.

Remaining admission limits: independent IntelliJ/editor recognition and actual ScHelm integration were
not performed. No universal ordering guarantee for third-party rewriting is added. AP-5 consumer/release
reconciliation remains Hive-owned. Issue #890 stays open; any eventual PR uses `Related: #890` and is draft.
