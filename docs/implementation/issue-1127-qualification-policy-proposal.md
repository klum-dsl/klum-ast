# #1127 — PR qualification implementation plan

Date: 2026-11-28. Governing decision: [ADR 0035](../adr/0035-proportionate-pr-qualification.md), **accepted D1–D6 and O1**.
Implementation status: **Not started; separately authorized slices required.**
Related: [#1127](https://github.com/klum-dsl/klum-ast/issues/1127). This documentation-only delivery records the settled
policy and a reviewable implementation sequence. It changes no workflow, settings, protections, CI behavior, precheck,
Vortex integration or production code. The filename is retained so existing evidence/handoff locators remain valid.

## Observed baseline and failure path

Pinned research/branch base: `7a74f22977a69303f41a289489a5bc9d56244c02`. At this revision:

- [PR qualification](../../.github/workflows/pr-qualification.yml) uses complete-diff positive admission for architecture
  prose; other/unknown inputs and merge groups run full producers. One PR-controlled workflow invokes base tooling;
  this is not independent workflow trust. Confirmation rereads base/head/merge identities and can reject passing steps.
- [Push CI](../../.github/workflows/ci.yml) retains `check --scan sonar --info`, all three annotation-artifact tasks,
  JUnit, compatibility evidence and landing checks. [Release verification](../../.github/scripts/verify-release-ci.cjs)
  admits exact-SHA push CI under ADR 0030; no reduced PR/tree/queue evidence is eligible release proof.
- Root [Sonar wiring](../../build.gradle) explicitly depends on all aggregate JaCoCo tasks. The
  [coverage convention](../../buildSrc/src/main/groovy/klum-ast.jacoco-conventions.gradle) imports the G3 report;
  [aggregation](../../code-coverage-report/build.gradle) keeps G4/G5 separately. Filters, skip flags or `-x test`
  cannot establish a truthful, narrower producer contract.
- #1136's run `46131848718` checked out synthetic merge `507047ab…`, whose whole tree equals integration `27ab4704…`.
  All required product steps succeeded, but overall freshness confirmation failed. Final head `c766c151…` adds an evidence
  page and its run was cancelled. Neither result is relabelled; the later delta and any admission remain separate.

The [evidence record](evidence/issue-1127-qualification-policy-evidence.md) contains full identities, replayable patch/tree
comparisons, artifact hashes, dated durations and limitations. Five eligible historical full jobs total 311m07s; #1136's
observed interrupted duplicate cost 3m27s. These are opportunity observations, not realized savings or replacement costs.

## Boundaries and affected seams

| Seam | Future responsibility and invariant |
| --- | --- |
| `pr-qualification.cjs` and fixtures | Complete diff/topology selection, exact checkout versus event identities, genuine outcomes, conservative fallback. No worker label can lower the minimum. |
| PR workflow / trusted producer | Independently controlled deterministic minimum, scope-bearing results and unconditional failure evaluation. Base tooling inside a PR-controlled workflow or Actions App identity alone is insufficient. |
| Gradle/root/coverage conventions | Preserve complete fresh G3 baseline, normal coverage and full-source hosted Sonar. Narrow only additional G4/G5, JPMS, artifact-consumer/plugin checks with demonstrated consumer closure. |
| Compiler/runtime/generated contracts and fixtures | Determine concept overlap and downstream tests; shared metadata, generated linkage and test harnesses can interact without path overlap. Unknown scope remains full/Hive. |
| Push CI / release verifier | Every master push remains full in first rollout. Exact release-head full CI and independent protected/publication/consumer gates remain unchanged. |
| Agent/testing/PR policy documents | Reconcile accepted overlap and evidence-admission rules without changing reviewed-history, required-context or issue-lifecycle authority. |
| Local feedback | Narrow advisory changed-source findings only; initially non-gating. SonarLint complementary; global hooks deferred. No claimed Sonar equivalence. |

No public/generated API, Model/Builder lifecycle, ownership, serialization, metadata protocol, dependencies or Groovy
compatibility surface changes in this design. Preserve the single production artifact and independently compiled lanes.
User pages, migration navigation and CHANGES need no update for inert policy documents; future user-visible behavior
would require its owning documentation. No DSL/client usage example or new executable test applies here.

## Selection and evidence design

A small worker manifest names repository/PR, exact base/head/actual checkout and tree, complete changed paths, affected
concepts/consumers, requested tasks/filters/lanes, coverage scope and evidence run/attempt locators. It is a proposal,
not a second release-proof registry. Deterministic policy independently reads complete Git/topology facts and computes
minimum checks; Hive handles disagreements, unmodelled seams, exceptions and D1 admission. Retain native GitHub evidence.

Unknown/incomplete history, renames/modes/binaries/submodules, graph ownership gaps, selection-tool changes or uncertain
shared contracts must not accidentally admit a weaker lane. Review deletion/suppression of tests and coverage-config
changes explicitly. Bind evidence to actual checkout, not only a PR event's `headSha`; enumerate latest applicable
attempts before considering success. The exact D1 checklist and no-combining-attempts rule live in ADR 0035.

Proposed coverage implementation safeguards, subordinate to accepted D3:

- Isolate fresh baseline and focused outputs; retain source/base, test filters/counts, toolchain and matching class/exec
  identities. Filtered execution must not overwrite evidence later described as full-suite coverage.
- Report changed executable lines and branches, covered/missed counts and unmapped lines. Missing classes/reports,
  stale inputs, mismatches or unexplained empty denominators fail eligibility; they are not silently excluded.
- Keep focused counters diagnostic with omitted tests/modules visible. Never upload them as the canonical complete
  report, reuse stale module XML, narrow Sonar source scope/exclusions or double-count production classes across lanes.
- Validate complete normal G3 coverage imports and actual hosted metrics as well as gate success. G4/G5 remain independent
  compatibility evidence. Bytecode identity is not source freshness, test adequacy or proof of a whole-project percentage.

The concrete consumer map, trusted execution mechanism and coverage wiring require implementation design/qualification.
They are not unresolved policy choices and cannot be activated by this PR.

## Status names and branch-protection migration

Current contexts are `PR qualification`, push `build`, `JUnit Test Report`, and authoritative `SonarCloud Code Analysis`
from App 12526 (`sonarqubecloud`). Separate `SonarCloud` security integration is not that gate. The live settings read in
the evidence report was incomplete; do not derive effective protection solely from workflow YAML.

| Proposed result | Truthful success meaning | Protection consequence |
| --- | --- | --- |
| `KlumAST PR gate` | Independent minimum recomputation plus accepted evidence for the current declared route | Stable always-evaluated required gate only after trusted producer is qualified; initial manual equivalence attestation cannot impersonate it. |
| `KlumAST prose checks` | Actual admitted head/integration or separately isolated prose-delta checks passed | Not build, JUnit, Sonar or product proof. |
| `KlumAST affected checks` | Complete G3 baseline/normal coverage/hosted Sonar plus required additional topology checks passed | Scope visible; never named full verification. |
| `KlumAST equivalence` | Real independent tree/context/delta admission check, citing earlier complete proof | Future automation only; initial Hive/maintainer checklist. Never a copied build/Sonar success. |
| `KlumAST full verification` | Every current full producer passed on the tested PR/queue revision | Cannot replace exact-SHA push release proof. |
| Existing `CI/build` and authentic Sonar | Complete exact-head push verification | Preserve approved workflow ID/path/event and reviewed release job/step/check contract. |

These names are a design sketch, not checks created or passed. Inventory effective classic protection/rulesets, contexts,
expected Apps/workflows and freshness before migration. Qualify a trusted replacement before changing required names or
source rules; old requirements remain binding until a separately authorized migration. If an exception is prohibited,
obtain genuine required checks or stop. No bypass, synthetic green status, broad path skip or conditional skipped-success.
Use unconditional result evaluation for selected dependencies; a skipped workflow can leave required contexts pending.
[Required-check semantics](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).

A merge queue is a separate future option. It needs actual group-SHA `merge_group` results and supported Sonar identity,
reviewed settings/concurrency/merge method, and failure handling; the current full fallback is not end-to-end qualification.
Queue batching does not necessarily batch builds. [Queue semantics](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-a-merge-queue),
[event identities](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows).

## Implementation sequence and acceptance

All slices relate to #1127 and need separate execution authorization. Keep each reasoning step, its meaningful tests and
matching documentation together; commit the qualification contract before wiring that depends on it.

| Slice / decisions | Reasoned commit boundaries | Executable acceptance before activation |
| --- | --- | --- |
| S1 policy/overlap and manual admission — O1, D1, D2 | Reconcile operative agent/testing/PR guidance and record shape; add bounded evidence-check fixtures with the checklist behavior. | Conflicting/composable/disjoint-concept examples; actual checkout versus stale event; equal patches/unequal trees; equal tree/changed context; later prose delta; failed product versus policy-only hold; no attempt mixing or manufactured result. |
| S2 independent conservative selection — D2 | Reviewed topology facts and deterministic classifier with tests; separately qualify its trusted execution/gate producer. | Complete rename/mode/binary/deletion/submodule enumeration, shallow/missing/truncated input, unknown consumers, self-edited tooling, worker disagreement and required-result failure/skip propagation. Worker input cannot reduce minimum. |
| S3 first affected topology and coverage — D3 | Establish one bounded consumer closure and fresh normal G3 producers with tests; then wire only eligible additional checks. Reconcile #573/ADR 0030 full-producer constraints explicitly. | Complete baseline including plugin/module tests; G4/G5/JPMS/artifact consumers when affected; missing/stale/mismatched coverage, changed base, empty selection and suppressed/deleted tests cannot inflate quality. Inspect real hosted source/coverage results through naturally authorized qualification, not a research upload. |
| S4 capstone operation — D4, D5 | Record related sets/last full SHA and reasoned cadence variations; qualify enforcement/reporting while master pushes stay full. | Normal three-merge/24h/end-of-set trigger, recorded exception, owner absence, master movement and failed latest attempt. A cadence exception cannot waive release exact-head full verification or supply missing product evidence. |
| S5 advisory feedback — D6 | Small changed-source parser-backed diagnostics and known-bad/fixed controls; keep initially non-gating. No probe/install in this slice. | Complexity/nesting, semantic literals and appropriate braces; guard clauses/incidental test strings; syntax/unsupported scope and justified exceptions. No regex-as-Groovy-grammar or Sonar-parity claim. |
| S6 measured follow-ups — D5, D6 | Measure natural qualified routes; separately propose queue/push omission or an explicit-file Vortex experiment. | Selection/coverage/capstone qualified first. Omission requires accepted premerge proof, qualified complete context and no capstone due under recorded cadence. Vortex requires entitlement, source-transfer/user-token policy and actual Groovy TEST-rule/completeness evidence; global hooks remain deferred. |

Cheap local feedback should precede publication, but advisory findings are not merge gates. The existing
[Sonar research](evidence/issue-1127-local-sonar-research.md) supplies the IDE/Scanner/agent distinction, fail-open hook
limits and historical-pair probe. SonarLint remains complementary; Vortex is server-side source transfer with no coverage
producer. No global hooks, credentials, scans or source upload follow from design acceptance.

## Capstone and measurement operations

The accepted normal cadence is three reduced/equivalence merges, 24 hours since the first, or end-of-related-set,
whichever first, with a recorded reasoned exception permitted. Proposed set records name last exact-head full push,
intended PRs, risk/consumer closure and next verification checkpoint. Precise freeze/fallback/recording mechanics are
implementation details, not an absolute cadence rule. Hive owns triggering; do not silently reset cadence via grouping.

A successful capstone requires the full latest-attempt producer at its exact final head, including independent G3/G4/G5
lanes/isolation/coverage, annotation artifacts, docs/Javadocs, real JUnit/uploads and authentic Sonar. A moved head or
pending/failed/unavailable result is not that final-head capstone. Release remains unconditional and separate.

Every master push stays full in the first rollout. Use native run/job/step time, queue delay and billing where exposed;
compare natural admitted routes to dated historical controls, retaining cancellations and fallback counts separately.
Do not dispatch sampling builds, extrapolate a local batch into hosted speedup, add overlapping task intervals, or infer
cache/critical-path attribution from inaccessible scans. Future omission is measured and separately authorized.

## Design delivery and ensuing decisions

D1–D6 and O1 are settled; no further policy interview is required for this design PR. Next decisions are implementation
admission and ordering, the trusted producer/protection mechanism, one demonstrably bounded topology and its concrete
coverage wiring, and capstone exception/enforcement mechanics. The future omission/queue/Vortex work remains separate.

This PR is `Related: #1127`, not an issue-closure candidate. It establishes design documentation only: no enforcement,
hosted savings, new analyzer/precheck, workflow/settings mutation or implementation slice. Preserve historical evidence
and process/audit disclosures; report current head, real PR/CI state and external conditions to Hive for reconciliation.
