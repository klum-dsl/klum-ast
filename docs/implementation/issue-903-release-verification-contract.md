# #903 — Authoritative pre-publication verification contract

Date: 2026-10-16. Investigation complete; recommendation awaits maintainer adoption.
Related: [#903](https://github.com/klum-dsl/klum-ast/issues/903), which remains open.

## Decision recommendation

**B — a bounded workflow/build correction is needed.** Source CI already supplies the full source
verification graph, but REL-1 neither selects that graph nor requires its successful completion for
the requested source SHA. The inspected live branch rules do not supply a required-status substitute.
Separation of source verification, protected publication, public propagation, and consumer qualification
is sensible; treating that separation as an already enforceable full-matrix gate is unsupported.

Recommend retaining source CI as the authoritative matrix proof, adding a fail-closed exact-SHA proof
check before pending documentation and protected publication, and enforcing local version/documentation
checks at the actual signing and remote-operation tasks. This avoids repeating the expensive matrix
solely because a release was dispatched. It does not waive any matrix, artifact, coverage, Sonar, or
consumer requirement. No workflow/build correction was implemented by this investigation.

## Source and environment

- Isolated worktree: `/Users/stephan/.codex/worktrees/08c2/klum-ast`; dedicated local branch
  `codex/issue-903-release-gate`. Clean start at freshly fetched `origin/master`,
  `96608b7c8c1657662f95e003b09d6e6d96cde777`; IDE-main was not modified.
- Local probes: macOS, Zulu JDK 17 selected through `/usr/libexec/java_home -v 17`, wrapper Gradle
  8.14.4; shared dependency/wrapper caches retained, fresh worktree outputs initially absent.
  Sandbox prevented the first wrapper lock access; approved execution succeeded. No publication
  credentials or authorization were supplied. Every publication-related invocation used `--dry-run`.
- Read [AGENTS.md](../../AGENTS.md), [CONTEXT.md](../../CONTEXT.md), [release procedure](../../RELEASING.md),
  [testing](../agents/testing.md), [commits](../agents/commits.md), and
  [publication policy](../agents/pull-requests.md). Baseline evidence was read with
  `git show f614c0ca56f576ab8a70411f48e604d32c7f7056:docs/implementation/evidence/issue-895/README.md`;
  its measured source was `11c5503f49a9e6bf6e00f5b1d043ccda36144a7b`. That local evidence commit
  is not in this branch's authoring tree. Current CI/release workflow files and publication wiring
  retain the baseline distinction; intervening root changes declare renderer inputs for cache replay.
- GitHub reads used `gh` and were authorized; no GitHub mutation, dispatch, release, push, PR, merge,
  secrets access, or environment change occurred. #904 was not used or reopened. No child workers.

## Authority and trust boundaries

| Boundary | Current evidence and authority | Limit |
| --- | --- | --- |
| Source verification | [CI](../../.github/workflows/ci.yml): JDK 17, `./gradlew check --scan sonar --info`, then explicit `:klum-ast:annotationPropagationArtifactsGroovy3/4/5`; JUnit report and compatibility artifact upload. [ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md) requires all three generations before signing/publication. | A workflow trigger is not a dependency of REL-1. Successful Sonar submission alone is not the asynchronous Sonar quality result. |
| Pending documentation | [Reusable pending workflow](../../.github/workflows/publish-pending-documentation.yml): exact stage/version/SHA, clean allowed line, renderer/Javadocs/crawl, protected writer/deployment, immutable manifest/read-back and handoff. | Documentation proof does not execute or attest the source test matrix. |
| Protected publication | [REL-1](../../.github/workflows/release.yml): stage/version shape, remote identity absence, exact SHA ancestry, environment approval, credentials, pending handoff, `publishCompleteKlumAstProduct`. | No CI run/check lookup or source-verification `needs` edge. Gradle selects root `:check`, not every project's check. |
| Public propagation | After publication, REL-1 resolves three Plugin Portal markers once, then the Maven product from fresh homes, at most 12 attempts ten minutes apart. | Intentional post-publication availability proof; cannot prevent a source-verification omission before signing. Preserve cache isolation and polling. |
| Public release proof/promotion | [REL-2](../../.github/workflows/verify-public-release.yml): annotated tag/release identity, fresh-cache combined product resolution, immutable proof, protected documentation promotion. [RELEASING.md](../../RELEASING.md#public-resolve-back-evidence) additionally requires equivalent maintainer consumer evidence from another machine/network. | Resolver fixtures do not compile a real Schema, project its source mirrors, run Schema Javadocs, or test its model. Human external-consumer evidence was not independently verified here. |
| Executable consumer qualification | [ADR 0019](../adr/0019-published-showcase-consumer-verification.md), still marked Proposed, separates retained public pins from complete isolated candidate-coordinate proof before protected approval. | Current Catwalk public baseline is delivered; candidate rebinding and release orchestration are deferred. It cannot attest a new candidate merely by passing against 4.0.1. |

The eight current Maven product modules include `klum-ast-test-support`; RELEASING.md's opening
seven-coordinate inventory is stale relative to the current publication graph. Both current Maven
resolver builds unconditionally request test support. Historical 4.0.1 did not ship that artifact;
current-source historical revalidation therefore needs the separately recorded release-line manifest
repair, not fabricated old coordinates. This is an existing ADR 0019/SC-1a prerequisite, not permission
to broaden #903 into consumer implementation.

## Live settings and actual runs

Read-only API snapshot on 2026-10-16:

- [Effective master rules](https://api.github.com/repos/klum-dsl/klum-ast/rules/branches/master) contain
  only deletion/non-fast-forward rules from active ruleset `7156445` (`protected main`, default branch).
  [Branch summary](https://api.github.com/repos/klum-dsl/klum-ast/branches/master) reports
  `protected=true`, but classic protection disabled and required-status enforcement off with empty checks.
  The classic `/protection` read was unavailable; no absent protection is inferred from that response alone.
- [Effective release/4.0.x rules](https://api.github.com/repos/klum-dsl/klum-ast/rules/branches/release%2F4.0.x)
  are empty; its branch summary reports `protected=false`, required-status checks off. Thus the release
  procedure's claim of normal maintenance branch protection is not established by current configuration.
- `release-candidate`, `final-release`, and `documentation-pages-writer` have required reviewer `pauxus`,
  allow self-review (`prevent_self_review=false`), forbid administrator bypass (`can_admins_bypass=false`),
  and admit `master` / `release/*` through custom branch policies. No automated CI protection rule was
  returned. These filters apply to the workflow ref, not an arbitrary input SHA; REL-1 separately checks
  ancestry. [GitHub's environment contract](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
  explains this distinction. `documentation-pages` also requires reviewer approval; no settings changed.
- `gh-pages` ruleset `19824173` restricts creation/update/deletion/non-fast-forward changes to the Pages
  App bypass. This is a documentation-ledger boundary, not a source-matrix gate.

There is positive verification evidence, separate from enforcement:

- Current base [CI run 38777368869](https://github.com/klum-dsl/klum-ast/actions/runs/38777368869), push
  to master at the exact base SHA, completed successfully. Build job `116568387058` logs all five
  multi-Groovy modules' compatibility/isolation tasks, all three coverage aggregations, renderer, Sonar,
  and the three explicit annotation-propagation tasks. Its step metadata confirms both Gradle invocations
  and compatibility upload succeeded; the SHA's `SonarCloud Code Analysis` check also reports success.
  Compatibility artifact `11996482573` is unexpired, digest
  `sha256:6a6a0d3670f05d4ed8139c08601cf83cef7ef7d8921b4b56595074cd363f11de`.
- Latest protected release [35453068192](https://github.com/klum-dsl/klum-ast/actions/runs/35453068192)
  successfully published final 4.0.1 from `release/4.0.x`, source
  `4d85ec2ed7e0a737d71b421af2c0cf597f6830e4`. That exact source had successful push
  [CI 35351442539](https://github.com/klum-dsl/klum-ast/actions/runs/35351442539) before publication,
  plus successful Sonar/JUnit checks. This is not evidence that an unqualified release was published.
- [REL-2 35454839078](https://github.com/klum-dsl/klum-ast/actions/runs/35454839078) successfully resolved
  the product and promoted documentation. Downloaded artifact `public-release-proof-35454839078.1`
  binds `final`, `4.0.1`, the same `4d85ec2...` SHA, and `publicly-resolved`. Its event head is
  `e3cba0fb...` on master: event/workflow head must not be confused with the input release SHA.
  Its proof/consumer artifacts and REL-1 Portal/Maven evidence remain unexpired at this snapshot.
- Catwalk was inspected at `97a787ecc2696569327d42f6cf5d59999c5503ac`:
  [baseline](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/direct-schema/baselines/4.0.json),
  [retained proof](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/validations/direct-schema/evidence/2026-09-19-public-4.0.1.md),
  and [CI](https://github.com/klum-dsl/klum-catwalk/blob/97a787ecc2696569327d42f6cf5d59999c5503ac/.github/workflows/ci.yml).
  The clean public 4.0.1 proof includes projection/Javadocs/model test, with JDK 25.0.4 recorded locally;
  current Catwalk CI selects JDK 17. The evidence tasks reject non-4.0.1 versions and the manifest
  explicitly defers candidate rebinding to SC-2 and disclaims being a release/workflow gate.
  Layer 3 currently selects one Groovy/Spock pair, not a three-generation consumer matrix.

Unknown: whether humans deliberately intended exact-SHA CI reuse, unpublished approval practices,
future candidate-manifest admission, and any out-of-band policy. None can supply a machine-enforced
dependency retrospectively. No unavailable fact prevents identifying the wiring gap; maintainer
adoption is required before implementing a release-policy correction.

## Reproduced task graph and ordering

All probes passed at the base SHA. Common suffix:

```text
--dry-run --no-configuration-cache --no-build-cache --console=plain --no-parallel --max-workers=2
```

| Invocation before common suffix | Selected root-build tasks | Matrix / extra gate selection |
| --- | ---: | --- |
| `./gradlew publishCompleteKlumAstProduct` | 134 | Root `:check`, renderer, seven module Javadocs; zero subproject test/compatibility/isolation/coverage/annotation-propagation tasks. |
| `./gradlew check` | 202 | All subproject checks, seven `test` nodes, ten G4/G5 test nodes, five isolation nodes, three coverage reports; no explicit annotation-propagation nodes. |
| `./gradlew check sonar :klum-ast:annotationPropagationArtifactsGroovy3 :klum-ast:annotationPropagationArtifactsGroovy4 :klum-ast:annotationPropagationArtifactsGroovy5` | 206 | Same check graph plus Sonar and three artifact qualification nodes; consolidates task selection for inspection only, not a proposed CI invocation change. |

The exact multi-Groovy owners are `klum-ast`, `klum-ast-runtime`, `klum-ast-jackson`,
`klum-ast-bean-validation`, and `klum-ast-test-support`: each selects `test`, `groovy4Tests`,
`groovy5Tests`, and `verifyTestLaneIsolation`. Annotations and Gradle plugin also select `test`;
a selected task may be `NO-SOURCE` at execution. Coverage nodes are
`:code-coverage-report:{testCodeCoverageReport,groovy4CodeCoverageReport,groovy5CodeCoverageReport}`.
The explicit artifact tasks are deliberately excluded from ordinary test lanes in
[klum-ast/build.gradle](../../klum-ast/build.gradle). Thus simply adding all subproject checks
to publication would still miss part of CI's shipping proof.

[Root wiring](../../build.gradle), [multi-Groovy convention](../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle),
and [coverage wiring](../../code-coverage-report/build.gradle) explain the selection. Gradle's
8.14.4 distribution documentation confirms that a bare task selector traverses projects, while a
dependency on `tasks.named('check')` refers to that project's task. A sibling dependency does not
make publication depend on successful check completion; `mustRunAfter` alone does not imply a
success dependency either ([task semantics](https://docs.gradle.org/8.14.4/userguide/controlling_task_execution.html)).

Dependency-ancestry inspection exposed a further ordering defect:

| Actual operation | Root `:check` ancestor? | `:verifyReleaseVersion` ancestor? |
| --- | --- | --- |
| `:initializeSonatypeStagingRepository` | no | no |
| `:klum-ast:signMavenJavaPublication` | no | no |
| `:klum-ast:publishMavenJavaPublicationToSonatypeRepository` | no | no |
| `:closeSonatypeStagingRepository` | no | yes |
| `:klum-ast-gradle-plugin:publishPlugins` | no | yes |

Ordinary publication dry-run orders `:check`, initialization, core signing, core Maven upload,
then `:verifyReleaseVersion`. The guard is attached to `publishToSonatype` aggregates rather than
every lower-level upload/signing task. An alternate dry-run explicitly requesting initialization
and core signing before the complete-product aggregate orders those two before `:check`, and core
upload still before version verification. It used synthetic `-Prelease.version=4.1.0-rc.903
-Prelease.stage=candidate`, without authorization; actions remained skipped. This proves selection
and order, not a performed remote operation. Exact invocation and ancestry inspection are retained
temporarily in `/private/tmp/klum903-evidence/ordering-dry-run.log` and `inspect-gates.init.gradle`.
Reproduce with `./gradlew :initializeSonatypeStagingRepository :klum-ast:signMavenJavaPublication
publishCompleteKlumAstProduct -Prelease.version=4.1.0-rc.903 -Prelease.stage=candidate`, followed by
the common dry-run suffix. The optional init script recursively follows
`task.taskDependencies.getDependencies(task)` at `taskGraph.whenReady` to inspect ancestry;
it does not alter tasks or dependencies. BuildSrc bootstrap tasks may execute during a dry-run;
the table counts only root-build tasks printed as `SKIPPED`.
Workflow shell identity checks mitigate the normal protected path; Gradle's advertised before-any-
publication guard and sibling root check do not independently guarantee that boundary.

## CI topology alternatives

Follow-up analysis requested on 2026-10-16; no workflow change or hosted experiment. The topology
and the release-proof dependency are separate decisions: **all three alternatives can preserve
the authoritative gate, but none repairs REL-1 merely by running tests somewhere.** Every option
must retain the complete task/evidence contract above and the exact-SHA release preflight below.
The [#573 decision](evidence/issue-573-ci-performance-decision-2026-07-28.md) rejected lane splitting
without measured justification; this analysis does not supersede it or claim a performance win.

| Alternative | Setup, cache and scheduling | Gate preservation / assessment |
| --- | --- | --- |
| **1. One Gradle invocation, repository parallelism** | One checkout/JDK/Gradle setup, configuration and production graph; Gradle deduplicates shared task nodes. Existing `org.gradle.parallel=true` already enables project parallelism. Combining the current two invocations can remove a second configuration pass, but changes artifact-step resource scheduling. | Simplest proof identity and report collection. Graph-equivalent in dry-run; first option to measure if consolidation is authorized. Retain one Sonar submission, all reports and explicit artifact tasks. Does not itself enforce release reuse. |
| **2. Parallel jobs in one workflow** | G3, G4, G5, documentation and artifact work can use separate runners; each repeats queue/startup, checkout, JDK/Gradle setup, dependency/cache restore, buildSrc/configuration and potentially production compilation. Sharing a producer artifact adds a dependency/handoff; it is not free parallelism. | Can preserve the gate with a tested lane/coverage/isolation seam and a final all-inputs aggregate. Current commands do not provide independent lane-only jobs without duplicated execution. Consider only after same-SHA hosted cost/resource evidence and maintainer admission. |
| **3. Distinct workflows** | Keeping source CI separate from protected release preserves a useful privilege boundary. Splitting source lanes into independent workflows has the same repeated setup as jobs, plus cross-run discovery, retries, retention and coordination. | Existing CI/REL separation is viable once exact-SHA CI proof is enforced. Autonomous per-lane workflows need an explicit all-workflows join; a completion trigger is insufficient. Highest coordination cost; no current evidence justifies this decomposition. |

**Option 1 prerequisites and limits.** Preserve the full `check sonar` graph plus all three
`annotationPropagationArtifactsGroovyN` tasks. The existing artifact invocation explicitly uses
`--no-parallel --max-workers=2`; a single invocation cannot apply that global worker cap to only
one subset. Preserve its intended resource bound through validated task scheduling/resource
controls, or measure an explicitly approved whole-invocation cap. Artifact tasks must still execute
before a successful source gate, even if their CLI position changes. Project parallelism does not
promise simultaneous G3/G4/G5 tasks within the large `klum-ast` project. Nested compiler/TestKit
processes, fork limits and runner memory still bound useful concurrency. One invocation keeps local
lane paths and JaCoCo inputs together and needs no cross-job output transport. Keep current artifact
and JUnit evidence, and verify the asynchronous Sonar quality result independently of scan upload.

**Option 2 prerequisites and costs.** Define an exhaustive job-to-task mapping: baseline tests
also include Gradle-plugin/annotation projects; G4/G5 cover all five multi-Groovy owners; documentation,
license/other `check` tasks, coverage, and explicit annotation-propagation proof cannot fall between
jobs. Each generation recompiles its own Groovy tests/fixtures. To preserve one qualified production
artifact set, model an explicit producer/consumer artifact boundary or prove equivalent production
bytes and dependencies across rebuilt jobs; never share Groovy-compiled test outputs between lanes.
This requires acceptance of the artifact strategy under ADR 0011, not just a YAML matrix.

The current verifier depends on every lane and requires all local JUnit directories. A separate
aggregation runner cannot discharge it by creating empty directories or downloading XML alone.
Introduce tested producer-bound lane metadata plus equivalent cross-lane verification, or demonstrate
a complete valid output handoff that avoids rerunning producers. Likewise, current `sonar` depends
on **all three** JaCoCo reports: adding it to a G3-only job pulls G4/G5 back into that job. A report
consumer seam must accept verified reports/class/source inputs without reselecting tests. Produce
each lane's coverage once; retain G4/G5 reports while feeding only G3 production coverage to one
Sonar analysis. If generating reports centrally, bind execution data to the exact matching class
bytes; XML reports alone do not establish class identity. Give JUnit/check and artifact uploads
distinct job/lane/run-attempt identities and retain failure evidence without artifact-name collisions.

Every runner can restore dependency caches, but a concurrent job cannot assume another job's eventual
cache save is already present. Gradle User Home caches are neither an artifact handoff nor proof of
matching test outputs. Measure restore/upload size, eviction and total runner-minutes alongside wall
time. Default-branch cache writes versus maintenance/PR read-only behavior must be observed for the
selected action/configuration; the [Gradle action documentation](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)
describes these defaults. Do not add overlapping Gradle-home cache owners, equate the separate Sonar
package cache with task-output reuse, or enable a permanent cache flag on the strength of this analysis.

**Aggregation/protection for option 2.** Use one stable final source-verification job with `needs`
covering every required producer/coverage/docs/report job. Run its result evaluation even when an
upstream job fails or is skipped, and explicitly fail unless every required result and manifest is
successful and complete. Reporting success, an absent job, `continue-on-error`, or a skipped final
job cannot qualify a release. Prefer completing other matrix lanes after one fails for diagnosis;
if fail-fast cancels them, the aggregate must reject the incomplete proof. These are implications of
GitHub's [job and matrix dependency semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idneeds),
not current repository wiring. Bind the aggregate to the repository, source SHA, workflow revision,
run/attempt, expected job set, report/artifact digests and trusted Sonar result. PR branch rules can
require that stable aggregate and trusted check producer, but REL-1 must independently verify its
exact source SHA. Required-status rules can accept skipped/neutral results under GitHub's
[protection semantics](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches#require-status-checks-before-merging);
the release verifier must demand actual successful complete proof. Current rules do not enforce it.

**Option 3 coordination contract.** Separate autonomous source workflows have no shared `needs` DAG.
Require explicit trusted workflow identities and collect all successful same-source proofs, with a
defined retry/cohort policy and no evidence from different SHAs, forks or stale failed attempts.
The join must publish a stable source-bound aggregate or REL-1 must validate the entire proof set.
Calling reusable workflows as jobs under one parent instead retains option 2's aggregation model.
GitHub's [`workflow_run` contract](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run)
triggers on any listed workflow, regardless of success, and uses default-branch event SHA/ref;
it is not an AND barrier. Use the validated triggering source identity, not that event head, and
re-query every required workflow. A coordinator must remain read-only and must not execute untrusted
downloaded artifacts with elevated credentials; `workflow_run` can otherwise receive secrets/write
authority unavailable upstream. Keep workflow-specific concurrency identities and reconcile cancellations
and expiry. Splitting workflows does not authorize more secrets, publication triggers or release dispatch.

**Additional task-selection evidence.** At report-only commit `fd1f40ee7331e46282c46f5a5aa79b5a67b80930`
(build/workflow source unchanged from base), three additional dry-runs passed with the earlier common
suffix. `test sonar` selected 136 tasks, including G4/G5 tests in `klum-ast`, runtime, Jackson and bean
validation and all three coverage reports; it omitted isolation, renderer and explicit artifact tasks.
`verifyTestLaneIsolation` selected 130 tasks, including all five owners' G3/G4/G5 tests.
The complete 206-task CI invocation with `--parallel` replacing `--no-parallel` selected **exactly the
same task set**. These probes establish selection, not concurrent execution, resource safety or elapsed-time
benefit. Logs in `/private/tmp/klum903-evidence/` have SHA-256:
`g3-with-sonar-dry-run.log` = `1167733765db3dbeaac81568a8c1c283de693e292ce6d235e09842f6c48d9663`;
`isolation-only-dry-run.log` = `a223631675bde68e50a551397c70533bb956e5d48450d06e58a46acb0dadc56a`;
`combined-parallel-dry-run.log` = `532061f1aa8769ad81119b8bfb3b3f63697e0dcc559d9f204c8b1042f3a8a9de`.

Recommendation remains **B**: establish release-proof enforcement first; retain current CI topology
while doing so. Consolidation, job splitting or workflow decomposition is a separately authorized
performance/design experiment requiring complete graph/evidence equivalence, failure/cancellation/skip
controls, same-SHA cold/warm hosted cohorts, measured setup/cache/reporting overhead and total cost.
No topology can replace candidate-consumer qualification, public propagation or protected authorization.

## Bounded implementation plan and acceptance

1. **Adopt the source-proof contract.** Maintainer chooses exact-SHA CI reuse (recommended) or
   rerunning the complete verification graph in a credential-free release job. Never accept ancestor,
   PR-merge, latest-branch, different-version consumer, or similarly named check evidence as exact
   source proof. Keep [ADR 0011](../adr/0011-shared-multi-groovy-compatibility-contract.md)'s before-signing
   requirement and make RELEASING/testing wording distinguish the selector from root `:check`.
2. **Enforce reusable proof before side effects.** Add a read-only REL-1 source-verification preflight
   with `actions: read` / `checks: read`, exact allowed-line SHA validation, and a trusted same-repository
   `ci.yml` push run on the selected source line. Require completed successful latest eligible run/attempt,
   successful build and explicit artifact step, expected lane/coverage artifact, JUnit, and trusted Sonar
   quality result. Bind workflow identity/revision, contract revision, source SHA, run/attempt, job/check
   identities, artifact ID/digest and conclusion in a retained manifest. Pending documentation and
   protected publication must both require this job; publication rechecks the bound attempt and digest.
   API errors, missing/expired evidence, pending/failed/cancelled/skipped gates fail closed; no automatic
   publication or silent weaker fallback. Missing proof can be supplied by a separately authorized CI run.
3. **Enforce the actual local operation boundary.** Put version/authorization and retained root
   documentation checks in the dependency ancestry of staging initialization, each applicable Sign,
   every Sonatype upload, staging close/release, and Portal publication. Preserve the sole complete-product
   entry guard at actual upload operations, not just aggregates; avoid cycles through artifact generation.
   Do not add all source tests a second time if step 2 is adopted. Shared dependency nodes execute once.
4. **Prove failure containment without publishing.** Fixture/API tests must reject wrong SHA/repository/
   workflow/event/branch, stale failed attempts, spoofed Sonar identity, missing explicit artifact lane,
   expired/mismatched artifact, API denial, and incomplete proof. Positive fixture consumes the current
   successful CI identity without rerunning its matrix. Graph assertions must cover all publication/signing
   nodes, not only root membership or log order. Stub irreversible task actions and force verifier failure:
   no sign, staging, upload, Portal, or pending-Pages action may execute, including reordered CLI requests,
   parallel scheduling, and `--continue`. An unauthorized direct upload must fail before its operation.
   Preserve original full CI task selection and reports; run ordinary Groovy 3/4/5 validation for build/workflow
   changes and renderer/workflow checks. No real release dispatch is needed for this acceptance proof.

This is a local design, not authorization to change protection/settings or establish a new consumer
gate. The workflow/build enforcement is bounded; exact proof reuse, attempt/retention policy, trusted
Sonar identity, maintenance-line admission, and ADR 0019 candidate qualification still need maintainer
decisions. If CI reuse is rejected, a separate credential-free full-graph release job is safe but
repeats expensive work; merely changing root `check` membership is insufficient.

## Validation and handoff

- All four initial and three topology dry-run probes succeeded. Publication graph/source checks confirmed the #895 observation
  still holds. Local executed `./gradlew :check --no-configuration-cache --no-build-cache --console=plain
  --no-parallel --max-workers=2` passed in 10s: 34 actionable tasks, 26 executed, 8 up-to-date,
  including Javadocs and renderer verification. It is explicitly **not** full-matrix evidence.
- Source Groovy lanes were not rerun locally for this report-only change; exact-base hosted CI and
  task selection were inspected instead, consistent with the documentation-only testing exception.
  `git diff --check` and local Markdown reference checks passed before commit.
- Temporary raw evidence lives in `/private/tmp/klum903-evidence/`; durable findings, commands,
  selected node sets, identities and digests are recorded here. Raw-log SHA-256:
  publication `32360f4e5d7e4b1f32efc95f883c394aac269d4a6bb1baf3fd25faa8a47bc53c`;
  check `7e5ff074e609ef621a83697aba660c37401bb93d7ca402ad8c2cae58d9b1960f`;
  CI contract `f773cf84addc25d00d56f8ee01058dda1b5e5750c6c3f173dcf86c290bc413b2`;
  ordering `bf78f9211f39bd2431213849678719b3778f2d470d62b0b14cb05e0a0a12f463`.
- Compatibility/API/runtime behavior is unchanged. Future implementation must preserve the single
  Java-17/Groovy-3 production product, all CI lanes/artifact checks/coverage/Sonar, protected boundaries,
  immutable release rules, fresh-cache propagation and public consumer proof. #895 speed goals do not
  reduce these gates. No maintainer release authorization or out-of-band consumer approval was assumed.
- Delivery is local evidence-report commits only. Tracker impact: no updates; #903 remains open,
  #484/ADR 0019 retain consumer ownership, #904 remains unused. No PR relationship selected beyond
  neutral `Related: #903`. Hive reconciliation requested; this worker does not mark itself archive-safe.
