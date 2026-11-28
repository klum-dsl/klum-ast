# #1127 — Qualification policy evidence and review record

Date: 2026-11-28. **D1–D6 and O1 accepted; documentation-only design delivery.**
Decision: [ADR 0035](../../adr/0035-proportionate-pr-qualification.md).
Plan: [implementation sequence](../issue-1127-qualification-policy-proposal.md).

## Authority, scope and overlap assessment

Maintainer decisions relayed through KlumAST Hive (chat `019fc7e7-9f4d-7ad3-8056-f8aefcb23aec`) settled D1–D6 and O1.
The final instruction authorizes consolidation, local commits, final two-axis review, push and a **draft** PR with
`Related: #1127`. It does not authorize workflow/settings/protection changes, CI behavior, prechecks, Vortex, implementation
slices, issue closure or merge. The research began as an uncommitted report while decisions were open; those decisions
are now accepted and the publication boundary is explicitly authorized.

Accepted D6 selects narrow **non-gating advisory** changed-source complexity/nesting, semantic-literal and brace feedback,
without Sonar-equivalence claims. SonarLint remains complementary. Global hooks are deferred; an explicit-file Vortex
probe requires entitlement, source-transfer/user-token policy, actual Groovy TEST-rule parity and completeness evidence.
No probe, login, hook, source upload or precheck was executed by this assignment.

Isolated worktree `/private/tmp/klum-1127-policy`, branch `codex/1127-qualification-policy`, pinned base
`7a74f22977a69303f41a289489a5bc9d56244c02`. Initial IDE-main inspection was its unrelated branch at
`9c52a77dbffe62a802a60c4bb237d0e418587087`; this worker made no source edits or branch changes there.

Pre-PR refresh found master `dea80156d212ebe2231e5f4a48290832df650be1`, advancing through #1133. Its two changes are
ADR 0032's RawData extension and `docs/implementation/issue-1132-bounded-raw-data.md`. This delivery adds ADR 0035, the
qualification plan, this evidence and the Sonar report; there is **no file overlap or demonstrated conceptual interaction**
with RawData's generated/runtime/input contract. No generated/runtime/test seam is changed. Master still has no ADR 0035.
Retain the pinned base/independent branch under accepted O1; no freshness-only rebase or unrelated merge is needed.
An open-PR refresh returned #1139, the release/4.2.x JPMS-helper upmerge; it touches none of these policy/ADR paths.
Its generated/runtime repair is independent from this inert design delivery, which changes no test or runtime seam.
No concurrent owner of the four design paths was identified in that bounded refresh.
Refresh live integration before publication; record the resulting tree and any changed overlap rather than assuming it.

## Research sources

Read: root AGENTS/CONTEXT, agent domain/commits/testing/coding-style/PR/tracker/backlog rules, complete work-orchestrator
skill/overlay, [ADR 0030](../../adr/0030-exact-sha-ci-proof-reuse.md),
[D1 runbook](../issue-1127-docs-qualification.md), [U3 record](../issue-1129-u3-evidence.md),
CI/PR workflows, `pr-qualification.cjs`, coverage conventions and aggregation, Sonar dependencies, release verifier,
[#573 decision](issue-573-ci-performance-decision-2026-07-28.md) and
[#903 historical Sonar investigation](../issue-903-sonar-feasibility.md). The latter's strict correlation no-go was
superseded by ADR 0030; it is not a current release blocker.

Read-only GitHub refresh used #1127 issue/body/comments, #1135/#1136 PR identities/check rollups, selected run/job/step
metadata, the historical synthetic Git commit and effective master rules. Complete raw selected responses remain in
`/private/tmp/1127-policy-live/`; no credentials, scopes or logs were added to the report. Existing full U3 log and
qualification artifact were read from their retained paths. GitHub CLI/API read channel: authorized; initial restricted
network read was unavailable and the approved read succeeded. No GitHub write channel was needed during that research phase; design-PR delivery is separately authorized below.

## Commit and tree evidence independently rederived

| Role | Full identity |
| --- | --- |
| Original common base B | `71dedcf980486a1de08a258b9baa827ef4b9b941` |
| Qualified U3 head H | `5038e7229cbd866a2429d0035410fd06f708edd5` |
| Master after #1135 M | `3b2f5efc01277dba8cbc99d6c86f88883d3b40b6` |
| Tested GitHub synthetic merge S | `507047ab1a871a0a7772a1faae9889758204c9cb` |
| Additive integration C | `27ab4704c13c423992f033af9e388133a6da0907` |
| Final PR head F | `c766c151fad71c7613ccdbfc814238cae3e3ded3` |
| Actual #1136 merge R / report base | `7a74f22977a69303f41a289489a5bc9d56244c02` |
| S = C tree | `9a186bda257c1678be3d489e6e448efde8f39afe` |
| F = R tree | `4c06319b0f5dfb322851b3b9e0b638c91aec30ed` |

The [GitHub Git-commit response](https://api.github.com/repos/klum-dsl/klum-ast/git/commits/507047ab1a871a0a7772a1faae9889758204c9cb)
again gives S's parents as M,H and the table's tree. Local `git merge-tree --write-tree M H` independently returns that
same tree with success. First restricted attempt could not write temporary Git objects; approved execution succeeded.
Local tree comparisons confirm C, F and R as above. S is not assumed locally available just because the API exposes it.

Full-index binary patches independently recomputed from Git objects:

| Comparison | Equality / SHA-256 |
| --- | --- |
| B..H versus M..C | Byte-identical, 721317 bytes, `376047c9842fb8c9e6fb97a506cf3a602d4d9ef1b8edd6cd1cd3e0af33b54c04` |
| B..M versus H..C | Byte-identical, 94986 bytes, `f24c57d123d5d090e893227874660fe692c822b774b00d236fb3ec3ec7962c60` |
| C..F | Only `docs/implementation/issue-1129-u3-evidence.md` modified |
| F..R | Empty |

U3 changes 27 paths; incoming master changes 15; their path intersection is empty. The prior
`/private/tmp/klum-1129-u3-equivalence/assessment.md` inventories every master path and possible interaction. In particular,
#1135 changes buildSrc, renderer helpers, workflow/check contracts and plugin workflow tests: this is more than unrelated
prose. Crucially the full combined tree was tested, so the argument need not compose two isolated green product trees.
Patch equality alone would not establish this.

The retained log `/private/tmp/klum-1129-u3-ci-46131848718-full.log`, checkout around line 365, records S. The retained
artifact `/private/tmp/klum-1129-u3-run-46131848718-measurement/pr-qualification.json` records `runSha=S`,
`eventBase=B`, `lane=full`, and reason `Live base tip differs from PR merge evidence`. Live master was M. This establishes
a checkout/metadata freshness mismatch; it does not imply that every stale event can be safely accepted.

The U3 record's 48 identity/hash rows were parsed and all rehashed against
`/private/tmp/klum-1129-u3-qualified-27ab/`: **48 verified, zero missing/mismatched**. This reads preserved bytes and does
not rebuild artifacts or rerun consumers. The prior 57-schema / 348 accepted / 636 rejected oracle result is inherited
from U3, not rerun here. Git-derived versioned JAR names themselves show why source-tree equality cannot promise binary
identity across commits/events.

Reproduction uses ordinary `git rev-parse '<SHA>^{tree}'`, `git diff --binary --full-index <A> <B> --`,
`git diff --name-status C F`, `git diff F R`, and `git merge-tree --write-tree M H`, substituting the full SHA table.
Compare patch bytes with SHA-256, not whitespace-normalizing patch IDs. Inspect actual checkout evidence, current live
parents and successful product steps separately. [Git merge-tree semantics](https://git-scm.com/docs/git-merge-tree).

## Dated hosted observations and bounded opportunity

Job timestamps were reread through GitHub on November 28; these are elapsed runner-job durations, not billed minutes,
queue time, causal benchmarks or savings. Full run conclusion is distinct from successful steps.

| Sample | Observed job duration and result | Meaning |
| --- | --- | --- |
| [#1135 final PR run 46124594110](https://github.com/klum-dsl/klum-ast/actions/runs/46124594110) | 51m09s, success | Full build step 48m43s; genuine full qualification. Initial implementation is not eligible for its own new prose shortcut. |
| [#1135 master run 46128730487](https://github.com/klum-dsl/klum-ast/actions/runs/46128730487) | 77m07s, success | Exact M push full verification. No claim that its tree equals the earlier PR checkout. |
| [#1136 run 46131848718](https://github.com/klum-dsl/klum-ast/actions/runs/46131848718) | 61m55s, failure | Build 59m32s; annotation artifacts, JUnit, compatibility, landing and trusted Sonar steps succeed; final confirmation fails. |
| [#1136 final run 46137230355](https://github.com/klum-dsl/klum-ast/actions/runs/46137230355) | 3m27s, cancelled | Began repeating work, then held/cancelled. No completed product or final-head Sonar result. |

Live PR metadata verifies #1135 merged at 09:04:21Z and #1136 at 11:05:25Z on November 28, with full merge/head identities
above. #1136 final-head rollup shows only the cancelled PR qualification. Merge delivery is observed; a successful
final-head check is not inferred. This report does not reconstruct the maintainer's acceptance authorization from merge
state or advocate retroactively changing results.

**Avoided-work estimate:** for the exact-tree portion of #1136, the accepted narrow protocol could have avoided
requesting another full product run, since 61m55s of actual combined-tree qualification already existed. The only
observed repeated final-run expenditure is 3m27s; another complete run's duration and replacement guard cost are unknown.
Do not count 61m55s as realized savings, assume the cancelled run would match it, or claim that every #1135 push minute
was avoidable. Local integration checks were 28s and prose-delta checks 9s in the prior record; neither is a hosted
replacement cost measured by this investigation.

For #1135, local head `07a8648f…` and actual merge M both have tree
`c9f48d55be0de428ee3a49418cdfdfbf85aff9d1`. Its 77m07s post-merge job is therefore another potential duplicate-work
candidate, subject to verifying the PR's actual checkout and all commit/event-sensitive inputs. Those joins were not
rederived here, so the entire duration is **not** counted as proven avoidable work or added to a savings total.

D1's bounded 45-PR sample admitted 7 architecture cases; five successful full jobs total **311m07s**, median **58m10s**;
two cancellations are separate. These remain historical opportunity evidence, not measured hosted savings. The older
local-feedback baseline verified #1124's 60m42s completed job and roughly 58 minutes before scanner startup; it did not
independently establish the issue's claimed three builds / roughly 200 minutes. That gap is preserved.

The existing scan [twohazcnq7n3g](https://gradle.com/s/twohazcnq7n3g) was not made readable by this task. The prior
investigation explicitly reserves detailed cache/scan retrieval for the maintainer. Therefore no critical-path or cache
attribution is invented. #573's four same-SHA hosted cache controls found no improvement and do not authorize permanent
cache flags, matrix splitting or removal of compatibility/coverage/documentation work.

## Protection, tooling and research limitations

Effective `rules/branches/master` read returned deletion, non-fast-forward and PR rules from repository ruleset 7156445;
no required-status or merge-queue entry appeared in that response. Classic protection read was unavailable. This is a
bounded observation, **not a complete proof of all effective settings, bypass actors or future enforcement**. No setting
was changed. A later implementation must inventory and review the actual required-check source/freshness contract.

The implementation plan cites dated official GitHub semantics; its `KlumAST …` names are proposed. None was created.
Current `PR qualification` is a single PR-controlled workflow and base-tooling reuse is not independent workflow trust.
Release proof deliberately rejects equivalent trees and non-push runs under ADR 0030.

[Sonar primary-source refresh](issue-1127-local-sonar-research.md) updates scanner documentation to 8.4.0.16527 and keeps
agent-facing CLI 1.13.1.18209 distinct. Actual Groovy TEST-rule parity, Desktop hook operation, entitlement and Vortex
context remain untested. No login, hook install, test execution, scan or source upload was performed.

## Validation and independent review history

The report phase used disposable alternate Git-tree snapshots without commits, a separate local manifest and the
maintained `verifyProseDocumentation` checker. Those checks validated source Markdown/links, not hosted prose admission.
Original snapshot `41789fb9d1792f4bff25b6416c12cc1109bfd788` passed (three files, 21 local links, 40 external links
syntax-only; escaped CommonMark render, 14s). Later snapshots and safe summaries remain in
`/private/tmp/1127-policy-validation/`, including `final-check-summary.json`, `accepted-decisions-check-summary.json`,
`d3-check-summary.json` and `d4-d5-check-summary.json`. Initial cache-lock/temporary-object restrictions were resolved
through approved local execution. No product tests, scan or source upload were performed.

The final committed documentation candidate uses the standard command with exact refs:

```shell
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  node .github/scripts/pr-qualification.cjs local origin/master HEAD
git diff --check 7a74f22977a69303f41a289489a5bc9d56244c02 HEAD
```

Groovy/product lanes are omitted under the inert-documentation exception. Delivery validation and the two-axis review
receipt below identify the exact comparison base/candidate; they do not turn policy acceptance into enforcement.

## Standards

`standards_review` reviewed the original proposal and successive accepted-decision amendments. Content findings were
zero; one consistency clarification was addressed. **One historical execution-policy violation remains disclosed:** the
research skill led to a non-review child (`official_local_sonar_research`) despite the work-orchestrator overlay limiting
worker children to declared two-axis review. Disclosure does not retrospectively authorize it. The child performed only
primary-source research and wrote the companion report; no code/configuration or GitHub mutation. Hive must reconcile
this process violation before archival. Final review of the consolidated committed delivery is separate from those
historical checks.

Final whole-diff Standards review of `9788c6878192e4427a7c43db9ec3cbe75762382d` against pinned base
`7a74f22977a69303f41a289489a5bc9d56244c02`: **zero actionable content findings or applicable Fowler smells**. The
historical process violation remains a separate reconciliation condition, not a cleared content finding.

## Specification

`specification_review` found one original P2 ambiguity about newer-attempt precedence, then one amendment P2 ambiguity
about incomplete selected proof versus an interrupted duplicate. Both were addressed and independently rechecked with
zero outstanding findings. The rule requires complete successful selected proof, retains failed/cancelled overall
conclusions, leaves unfinished duplicate steps incomplete, and never replaces/combines outcomes across attempts.
D3, D4 and D5 amendment rechecks each had zero new findings. Final consolidated delivery receives a fresh whole-diff
review rather than silently extending those results to a new commit.

Final whole-diff Specification review of the same commit/base: **zero actionable findings** across all eight original
inputs and accepted D1–D6/O1. D6 is initially non-gating; D4 is a guideline with recorded reasoned exceptions; D5 preserves
every master-push full CI in the first rollout. Release verification and the no-implementation boundary remain intact.

## Design publication receipt

The complete four-document candidate `9788c6878192e4427a7c43db9ec3cbe75762382d` passed the standard committed-head
prose command against fetched master `dea80156d212ebe2231e5f4a48290832df650be1`: **four files, 30 local links, zero
incoming links/fragments, 33 external links syntax-only, escaped CommonMark render**, 8s. Whole-diff whitespace checks
passed. Precommit snapshot `1d5c40a766b15b539ce00184a658a3686f3225ad` had the same result, 10s.
These are local committed-source checks, not hosted/integration qualification or Sonar analysis.

GitHub CLI repository-write capability and Git transport dedicated-branch dry-run were checked independently:
**authorized** on each channel. No GitHub App capability was inferred or used. The dry-run changed no remote branch.
Local merge-tree computation against fetched master completed cleanly; no freshness-only rebase was performed.
This receipt adds validation/review/overlap facts only; final-head source checks and publication state are reported with
the draft PR/Hive handoff. No passed hosted check, enforcement, implementation or issue delivery is inferred.

## Audit gap and delivery boundary

No release/run IDs, authorized persistent runtime-evidence root or retention/backup ownership were supplied. No default
root, inferred runtime/token/quota/model telemetry or fabricated event history was created. Safe task evidence under
`/private/tmp/1127-policy-live/` and `/private/tmp/1127-policy-validation/` is **not a complete orchestration event stream**.
Existing step timings and reviewer results are observed, not inferred. These gaps and the historical process violation
remain visible for Hive reconciliation; document publication does not repair them.

Tracker impact: **Related: #1127; no closure or milestone/curation change.** Policy choices are settled; future admission,
trusted producer/protection design, topology/coverage wiring and capstone mechanics need implementation assignments.
Publish only the authorized draft design PR; report actual final head/PR/CI state to Hive. No self-archive or claim of
implementation, enforced policy, hosted savings, successful final-head CI or release delivery follows from this record.
