# Architecture prose PR qualification

Related: [#1127](https://github.com/klum-dsl/klum-ast/issues/1127), partial delivery in
[PR #1135](https://github.com/klum-dsl/klum-ast/pull/1135). Refined 2026-11-28.
Prose qualification supplies no product, generated-site, Javadoc, JUnit, Sonar or release proof.

## Accepted trust and qualification policy

The maintainer selected **B: explicit maintainer/Hive qualification policy** on 2026-11-28. This replaces the initial
D1 proposal's strict-protection activation prerequisite. No settings change or new credential is required.

| Alternative | Practical guarantee | Burden and activation | Expected benefit |
| --- | --- | --- | --- |
| A: strict required check | GitHub enforces the configured context and up-to-date rule; this does not authenticate the invoking workflow definition | Requires a maintainer ruleset change; live rules/classic-protection interpretation adds API calls and failure modes. Current settings make the shortcut inactive | None until settings are changed |
| B: explicit repository policy, **selected** | Complete positive admission, genuine selected checks and revision checks during validation; maintainer/Hive verifies current evidence immediately before merge | Remove live protection inspection; ordinary maintainer review/merge authority remains. Base/head movement after validation requires human requalification | Architecture-only PRs can avoid full PR builds after this implementation is accepted |
| C: local tooling only | Exact committed-source documentation checks; existing full PR CI unchanged | Smallest delivery if automatic routing proves disproportionate; no automated savings | Better local feedback, no hosted build reduction |

GitHub-enforced merge authorization, repository qualification policy and correct classification are separate facts.
Immediately before accepting a prose-qualified PR, the maintainer/Hive must compare its final head, live base tip and
merge identity with the qualification evidence. After either branch moves, update/rebase the PR and obtain fresh
qualification; an old green check does not authorize merging. Existing separately required GitHub checks still apply.
Prose cannot satisfy required build/JUnit/Sonar contexts. No code changes branch protection or bypasses GitHub rules.

The classifier and checker use committed base tooling, limiting accidental coupling to PR tool changes. The invoking
workflow is nevertheless PR-controlled. An Actions App-bound check attests to its producing App, not a particular trusted
workflow definition. This implementation does **not** defend against deliberately malicious workflow changes; those
require maintainer review under the existing trust model. It guards accidental stale/incomplete qualification. No
trusted-workflow infrastructure, attestation registry or general security framework is introduced.

## Admission boundary and revision checks

Only additions/modifications of regular mode `100644`, UTF-8, non-LFS prose in these flat locations qualify:

```text
CONTEXT.md
docs/adr/*.md
docs/implementation/*.md
docs/implementation/evidence/*.md
```

The flat architecture/evidence directories are not product or generated-site inputs: root renderer live-contract inputs
are `docs/user`, README, build/workflow/release machinery, and its source tree is `docs/user` for current versions.
See [root wiring](../../build.gradle), [renderer](../../buildSrc/src/main/groovy/com/blackbuild/klum/ast/docs/VersionedDocumentationRenderer.groovy)
and [renderer acceptance](../../buildSrc/src/main/groovy/com/blackbuild/klum/ast/docs/VerifyVersionedDocumentationRendererTask.groovy).
References to ADRs in executable tests are documentary `@See` links, not reads of their content.
Future code that consumes admitted prose as executable/generated input must revisit this boundary.

README, CHANGES, user documentation/navigation/images, agent instructions, workflows, Gradle, source and resources
retain full qualification. Nested evidence/probe/fixture trees remain excluded: they contain executable and fixture
inputs and the inspected workload establishes no need to admit them. Do not rename or relocate a document just to obtain
admission. A future real need can justify a separately reviewed boundary change; this is not general Markdown admission.

Copies, renames, deletions, mode/type changes, binary content, empty diffs and unknown input select full. The classifier
uses the complete merge-base-to-head raw NUL-delimited Git diff with rename/copy detection, not the last commit or a
bounded API file list. It requires full history, one merge base, complete objects, and checks modes and blob contents.
Git failure/warning, incomplete/malformed output and budget exhaustion select full.
Budgets: 500 records, 1 MiB raw diff, 256 KiB per old/new blob, 1 MiB changed blobs in total, 4 MiB Git output and
30 seconds per Git process. Completed change enumeration survives eligibility rejection for fallback auditing.

Hosted prose admission additionally requires an active same-repository PR into `master`, an admitted activity and exact
full SHAs. The tested synthetic merge must have exactly the pinned base/head parents and retain admitted head content.
Live PR metadata must agree with the live base ref. The base-tooling checkout must match that pinned base. Missing or
stale base tooling selects full, including the initial implementation PR. Full and prose confirmation both bind the
tested run SHA to the pinned merge and re-read head/base/merge before success. This protects the validation interval;
post-validation movement is handled by the maintainer/Hive pre-merge rule above, not automatic enforcement.

`opened`, `synchronize`, `reopened` and `edited` all recompute the full change set. Base retargets, merge groups, forks,
non-master targets and unsupported inputs run full. There are no PR branch/path filters or skipped full jobs presented
as product success. A classifier infrastructure failure runs actual full work or qualification fails.

## Documentation checks and standard local command

After committing an architecture-documentation snapshot, workers use:

```shell
node .github/scripts/pr-qualification.cjs local origin/master HEAD
```

Optional HEAD defaults to `HEAD`; BASE is explicit and refs resolve to exact commits. This command needs no GitHub token.
It shares the complete-diff classifier, writes a disposable manifest and invokes the existing `verifyProseDocumentation`
task. It validates committed objects, not uncommitted edits, and makes no integration/hosted claim. Outside the allowlist,
use the applicable documentation/product checks. JDK/Gradle prerequisites are the repository's existing ones.

The hosted task reads its classifier manifest and checks both exact head and merge trees:

- Whole merge-base-to-head `git diff --check`.
- CommonMark 0.28.0/GFM tables, one H1 and a nonempty escaped HTML render per changed page.
- Parsed inline/reference/image local targets, heading fragments, bounded `#Lx-Ly` source ranges, escapes and symlinks.
- Incoming local links from tracked Markdown to changed pages, including removed or renumbered headings.

Heading extraction, Unicode handling and duplicate-ID assignment reuse the existing renderer helper. Source links follow
[GitHub's source-anchor spacing rules](https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#section-links):
`Selected YAML interface — #1114` has source ID `selected-yaml-interface--1114`. The generated site keeps its existing
normalized ID `selected-yaml-interface-1114`. The first replay exposed this mismatch; a regression protects both modes.
There is one CommonMark renderer, not a second site renderer.

External HTTP(S)/mailto links are syntax-only. Code blocks/spans stay inert and authored HTML is escaped. These are bounded
source/render/link checks, not semantic truth, browser/visual acceptance, remote availability, executable-example proof
or whole-repository link health. User docs still require their actual renderer/site/Javadoc checks.
Checker limits: 1 MiB per Markdown document, 20,000 tracked paths, 4 MiB Git output per process, 64 MiB reads per revision,
and 30-second Git timeouts. Unsupported or incomplete validation fails. Failed docs are never ignored or relabelled full.
The uncached task treats the Git object store as internal, avoiding whole-working-tree snapshots and accidental
dependencies on generated product outputs. A combined focused-test/prose invocation exposed and controls this boundary.
Unit tests run on full/code PRs; prose PRs execute their real documentation checks without retesting unchanged tooling.

## Authentic Sonar and full-workflow synchronization

Historical check-run reads on 2026-11-28 distinguish:

| Check | App | Meaning |
| --- | --- | --- |
| `SonarCloud Code Analysis` | `12526`, `sonarqubecloud` | Authoritative quality gate; e.g. #1133 output title is `Quality Gate passed` |
| `SonarCloud` | `57789`, `github-advanced-security` | Separate security integration; e.g. #1133 says no new alerts, not the product quality gate |

The old filter already excluded App 57789; the two different checks alone were not its ambiguity bug. The actual weakness
was rejecting repeated authoritative records and allowing an earlier same-head success to satisfy a later attempt.
[Actual #1133 check response](https://api.github.com/repos/klum-dsl/klum-ast/commits/8edd460f8a5badb2fb5604c03fd89fa7c9e73648/check-runs?filter=all)
contains quality-gate check `140813075641` and security check `140813078583`.
[#1116](https://api.github.com/repos/klum-dsl/klum-ast/commits/2dadac474e17e5c90fcce21320461eb9f4fdd7a7/check-runs?filter=all)
similarly contains quality-gate `140083952339` and security `140083959655`.

The bounded rerun search found existing [run 44555781925, attempt 2](https://github.com/klum-dsl/klum-ast/actions/runs/44555781925/attempts/2).
Its head is `35faf977d2f43dce4450613d30ebc49f11fd48a9`; attempt 1 failed before Sonar, while attempt 2 produced quality check
`135753911381` starting at 17:35:14Z after attempt start 16:31:35Z on November 19. The response has repeated build/JUnit
records but only one authoritative Sonar record. We do not claim an observed duplicate-Sonar rerun; controlled regressions
cover repeated quality-gate records, including older success plus newer pending/failure and later success after failure.

PR verification reuses the existing release helper's bounded, complete GitHub pagination and API client. The release
verifier's unique-record selection cannot handle the new repeat policy and is left unchanged, preserving release behavior.
The small PR-specific selector admits only exact-head App 12526 plus exact quality-gate name, selecting newest `started_at`
**before** considering success. Queued/undated evidence blocks old success; a tied newest timestamp passes only when all
possible newest records succeeded. The selected start must be at/after the current Actions attempt's `run_started_at`.
The run ID/attempt/path/event/head are checked. Missing, stale, malformed, incomplete, pending or failed evidence cannot
qualify. A bounded wait of 40 reads at 15-second intervals permits the genuine asynchronous gate to finish. No second
scanner upload or general check orchestrator is added. Another analysis of the same head during this attempt's interval
is an accepted residual risk; this is not a Sonar analysis-to-run identity join.
The [Checks API](https://docs.github.com/en/rest/checks/runs#list-check-runs-for-a-git-reference) documents all/latest filtering
and endpoint limits; completed-at order alone must not hide a newer pending result.

Both full paths retain `check --scan sonar --info`, Groovy 3/4/5 compatibility/coverage through root check, all three
annotation-artifact tasks and worker limits, real JUnit reporting, compatibility uploads and landing-route checks.
The existing `ReleaseCiWorkflowTest` now compares the actual named producers' commands/actions/parameters/environment in
both workflows and their full-lane conditions, plus reporting failure semantics. It does not demand identical workflows
or introduce a reusable workflow abstraction. Existing push step numbering and protected release-proof selection remain
unchanged. PR/prose/dispatch/merge-group runs cannot become release push proof.

## Bounded workload and cost assessment

Sample: the latest 45 master-targeted PRs returned on 2026-11-28, #1039–#1135, created November 6–28, including open/closed
and merged PRs. Fourteen have entirely Markdown change sets; API changed-file totals matched their complete path lists.
Git merge-base raw-diff/content checks independently admitted all seven architecture cases under the revised boundary.
These are workload eligibility counts, not claims that historical protection/event identities would admit a run today.

| PR | Initial paths eligible | Revised paths/content eligible | Boundary | Existing full job |
| --- | --- | --- | --- | --- |
| [#1133](https://github.com/klum-dsl/klum-ast/pull/1133) | no | yes | Architecture prose | [58m10s · success](https://github.com/klum-dsl/klum-ast/actions/runs/46086458900) |
| [#1130](https://github.com/klum-dsl/klum-ast/pull/1130) | no | yes | Architecture prose | [51m57s · cancelled](https://github.com/klum-dsl/klum-ast/actions/runs/46052870605) |
| [#1126](https://github.com/klum-dsl/klum-ast/pull/1126) | no | yes | Architecture prose | [13m01s · cancelled](https://github.com/klum-dsl/klum-ast/actions/runs/45979290602) |
| [#1116](https://github.com/klum-dsl/klum-ast/pull/1116) | yes | yes | Architecture prose | [50m28s · success](https://github.com/klum-dsl/klum-ast/actions/runs/45863305494) |
| [#1112](https://github.com/klum-dsl/klum-ast/pull/1112) | yes | yes | Architecture prose | [78m24s · success](https://github.com/klum-dsl/klum-ast/actions/runs/45859240226) |
| [#1109](https://github.com/klum-dsl/klum-ast/pull/1109) | no | no | README/CHANGES/rendered user docs | [76m48s · success](https://github.com/klum-dsl/klum-ast/actions/runs/45165025534) |
| [#1089](https://github.com/klum-dsl/klum-ast/pull/1089) | no | no | README/CHANGES/rendered user docs | [77m08s · success](https://github.com/klum-dsl/klum-ast/actions/runs/43943047233) |
| [#1084](https://github.com/klum-dsl/klum-ast/pull/1084) | no | no | README/CHANGES/rendered user docs | [52m47s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42992116795) |
| [#1080](https://github.com/klum-dsl/klum-ast/pull/1080) | no | no | README/CHANGES/rendered user docs | [44m57s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42869215356) |
| [#1062](https://github.com/klum-dsl/klum-ast/pull/1062) | no | no | README/CHANGES/rendered user docs | [73m32s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42505861683) |
| [#1061](https://github.com/klum-dsl/klum-ast/pull/1061) | no | no | README/CHANGES/rendered user docs | [1m24s · failure](https://github.com/klum-dsl/klum-ast/actions/runs/42505327967) |
| [#1056](https://github.com/klum-dsl/klum-ast/pull/1056) | no | yes | Architecture prose | [68m59s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42467712577) |
| [#1054](https://github.com/klum-dsl/klum-ast/pull/1054) | no | no | README/CHANGES/rendered user docs | [70m05s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42461342643) |
| [#1047](https://github.com/klum-dsl/klum-ast/pull/1047) | no | yes | Architecture prose | [55m06s · success](https://github.com/klum-dsl/klum-ast/actions/runs/42370856626) |

Initial coverage was 2/14 Markdown PRs (14.3%, 2/45 overall). Revised coverage is 7/14 (50%, 7/45 overall = 15.6%),
including all seven architecture-only cases inspected. #1126 was excluded by the ordinary `issue-1114-yaml-integration.md`
plan and flat evidence page; #1130 and #1133 by their ordinary `issue-*` plan names. No executable/generated/release
consumer of these prose paths was found. The seven retained exclusions all affect current user-site or release inputs.

After correcting source anchors, the maintained prose task passed all seven cases at both exact historical head and
merge trees (14 revision checks in one warm local Gradle invocation: 1m07s). #1112/#1056's final merge parents differ from
the API's recorded base, so their final merged trees are checker inputs, not reconstructed pre-merge admission proof.
No historical file was renamed, relocated or edited to obtain those results.

The five successful eligible jobs span 50m28s–78m24s (median 58m10s), totalling 311m07s; the two cancelled jobs used
another 64m58s but are not successful qualification controls. These historical minutes are opportunity evidence, **not
measured savings**: the sample is small/nonrandom, cold hosted prose setup is unmeasured, cache/runner effects vary, and
full postmerge push builds continue. The 1m07s local batch is not a hosted speedup ratio.

Each hosted job retains classification version, full paths/status/modes, reasons, exact revisions, run/attempt, outcomes
and timings in its summary and `pr-qualification-measurement` JSON artifact. Use native job/step timestamps for setup,
validation and reporting cost; the JSON elapsed field begins at classification and excludes earlier checkout and final
upload. Use billed runner usage where available; queue time is separate. No external telemetry, usage poller or secrets.
Compare the first five naturally admitted prose heads with five naturally full controls after acceptance; label historical
controls separately and record fallback counts over the first ten runs. Do not trigger hosted sampling builds.

B now offers useful coverage with the classifier/checker already present and removes live protection interpretation.
Keep routing only if natural hosted evidence confirms worthwhile savings and correct admission. Unexpected complexity,
false admission or excessive cost warrants reverting PR routing (C) while retaining the useful local checker. Disable
routing by a reviewed code change returning full, not synthetic green statuses or a protection-settings trick.

## Validation and acceptance

Focused commands:

```shell
node --test .github/scripts/pr-qualification.test.cjs
./gradlew -p buildSrc test --tests '*ProseDocumentationTest' --no-daemon --offline
./gradlew :klum-ast-gradle-plugin:test --tests '*ReleaseCiWorkflowTest' --tests '*ReleaseCiPreflightTest' --no-daemon --offline
bash .github/scripts/test-write-documentation-landing.sh
git diff --check
```

This refinement uses focused routing, checker, existing release-CI and workflow-contract tests, plus historical read/replay
controls. No expensive local root check/Groovy compatibility sweep, Sonar upload or hosted sampling build is required.
Final-head hosted full qualification, authentic Sonar and independent review remain the publication/acceptance evidence;
local controls do not fabricate those results. The policy choice is settled; maintainer PR acceptance remains required.
No runtime/DSL/user-navigation change, release change, issue closure/milestone change or repository-settings change.
This allowlist and renderer/release boundary are KlumAST-specific; no engineering-baseline change is included.
