# Proportionate PR qualification and independently reviewed integration evidence

Date: 2026-11-28

Status: **Accepted policy: D1–D6 and O1.**
Implementation status: **Design only; no workflow, settings, protection, precheck, Vortex or CI behavior changes.**

Authority: maintainer decisions relayed through KlumAST Hive on 2026-11-28. Related:
[#1127](https://github.com/klum-dsl/klum-ast/issues/1127). The
[implementation plan](../implementation/issue-1127-qualification-policy-proposal.md) separates future slices and
activation prerequisites; the [evidence record](../implementation/evidence/issue-1127-qualification-policy-evidence.md)
retains observed results, limitations and historical process/audit disclosures.

## Context and retained decisions

PR #1136 exposed a qualification-identity failure after successful product steps: its actual synthetic merge tree
equalled the later integration tree, but freshness confirmation rejected it. Its final evidence-only delta and cancelled
run are separate facts; no failed/cancelled result becomes green. PR #1135 and earlier performance evidence also show
substantial repeated verification cost, without establishing realized savings for a replacement route.

This ADR selects a bounded policy response. It qualifies the blanket freshness/rebase requirements in the
[D1 prose runbook](../implementation/issue-1127-docs-qualification.md), [testing policy](../agents/testing.md),
[PR policy](../agents/pull-requests.md), and [orchestrator overlay](../../.agents/skills/work-orchestrator/SKILL.md).
Their operational documents and executable enforcement are not changed by this design delivery; implementation must
reconcile them before activating new routes. Existing required GitHub contexts remain binding throughout.

[ADR 0030](0030-exact-sha-ci-proof-reuse.md) remains the release authority: exact source-SHA push proof, approved workflow,
latest eligible run/attempt, successful full producers and authentic Sonar, plus protected/publication/consumer gates.
PR, merge-queue, ancestor or equivalent-tree proof cannot replace it. The single Groovy-3 production artifact and
independent compatibility lanes from [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md) remain intact.
[#573's performance decision](../implementation/evidence/issue-573-ci-performance-decision-2026-07-28.md) does not establish
cache or lane-splitting benefits; future wiring must reconcile its full-producer constraints explicitly.

## Accepted decisions

### O1 — Assess overlap before PR creation; preserve independent commits where sound

Every parallel worker must name its base SHA, changed paths, affected generated/runtime/test seams and known active-PR
interaction before creating a PR. Assess concepts and consumers as well as files: disjoint paths can share a contract;
matching paths can contain composable edits. Refresh after relevant neighboring work changes.

Without demonstrated overlap, retain independent commits despite master movement. Composable file overlap requires
Hive-coordinated stack/rebase timing. Shared-contract/generated/runtime seam uncertainty goes to Hive **before PR
creation**; an actual conflict requires resolution. A clean merge alone proves no semantic independence.

Preserve reviewed history under [commits.md](../agents/commits.md): integrate additively where appropriate, or obtain the
maintainer's explicit rewrite instruction. Record commit-addressable base/integration evidence instead of silently
substituting a moving branch name. Changed inputs require evidence reassessment, not automatic rebase or acceptance.

### D1 — Narrow exact-tree equivalence; initial Hive/maintainer attestation

An independently reviewed proof may retire **only a policy-hold/cancellation** when successful prior verification tested
the exact resulting integration tree, later deltas are separately qualified, no failed/incomplete product result is
overridden, and required GitHub contexts remain truthful. Initial admission uses this Hive/maintainer checklist:

1. Identify the actual checked-out SHA/tree, full run/latest applicable attempt, successful product/artifact/report
   steps and authentic Sonar evidence. Actions PR `headSha` alone is not checkout proof. Selected product proof must be
   complete and successful. Record overall run conclusions separately; a policy-confirmation failure stays failed.
2. Independently recompute the live pinned base/head integration and compare the **whole Git tree**, including modes,
   binaries, fixtures and configuration. No untested resolution may remain. Patch equality supports unchanged-patch
   reasoning but cannot substitute for whole-tree equality or semantic assessment.
3. Review toolchain/dependency resolutions, external inputs, workflow context, affected test topology and approved
   policy/profile assumptions. Assess commit/parent/branch/tag/dirty-tree-derived versions/resources separately. Unknown
   or material changed context requires fresh verification; tree equality is not binary reproducibility.
4. Enumerate and qualify each later delta under its own applicable lane. A prose-only delta needs complete-delta checks
   and preserved product inputs; it cannot turn the entire code PR into a prose PR.
5. Enumerate relevant newer attempts before choosing evidence. Actual product failure blocks reuse. Pending, cancelled,
   timed-out, missing or ambiguous evidence blocks by default. Hive/maintainer may retire only an independently isolated
   obsolete policy hold: an interrupted duplicate keeps unfinished steps incomplete, supplies no positive product proof,
   and cannot have its outcomes replaced by earlier steps or combined across attempts. The earlier complete exact-tree
   proof is admitted as its own record, without rewriting either run.
6. Record reviewer, Hive/maintainer attestor, exact revisions, evidence locators, hold/cancellation and reason. Recheck
   identities immediately before merge and the actual resulting tree after merge. A changed result invalidates reuse.
   If required contexts prohibit this route, obtain genuine required checks or stop; no status fabrication or bypass.

Worker self-certification, two unrelated green PRs, ancestry, patch IDs, a clean merge or disjoint files are insufficient.
For #1136, the tested synthetic merge and integration share a tree; its later evidence page requires separate delta
qualification. Its recorded failed/cancelled conclusions remain unchanged. This ADR does not create a per-run admission.

### D2 — Worker proposal, independent deterministic minimum, Hive exceptions

Workers propose a proportionate validation manifest. Deterministic policy independently recomputes the conservative
minimum from complete diff/topology facts. Hive/maintainer resolves exceptions, disagreements, unmodelled seams and
equivalence admission. A label, manifest or worker-selected task list is not authority to reduce the minimum.

The implementation must establish complete merge-base enumeration, reviewed consumer/topology closure, exact identities
and actual outcomes. Unknown/incomplete scope selects full qualification or Hive resolution. Selection-tool/workflow/
coverage changes cannot lower their own rigor. A PR-controlled workflow checking out base tooling or using the Actions
App does not authenticate independent enforcement; trusted execution and check-source migration need separate design.

### D3 — Retain complete baseline coverage; narrow only additional checks

Every executable PR initially retains a **complete fresh Groovy 3 baseline with normal coverage and hosted Sonar source
analysis**. Only additional Groovy 4/5, JPMS, artifact-consumer and plugin checks may narrow to demonstrated affected
topology. Baseline plugin/module tasks cannot become optional by relabelling them additional checks.

Focused coverage remains **diagnostic**, not a substitute for baseline coverage until later evidence supports a further
accepted extension. Preserve normal source scope, coverage denominator/exclusions/new-code base, and independent G4/G5
evidence. Missing/stale reports, bytecode mismatch or unexecuted baseline tests cannot silently improve a quality signal.
D1 remains a separate bounded proof-reuse exception; it cannot shrink the scope of the successful proof being reused.

### D4 — Normal capstone cadence with recorded reasoned exceptions

Hive normally triggers full **exact-head** capstone verification after **three reduced/equivalence merges, 24 hours, or
end-of-related-set, whichever comes first**. This is a guideline, not an absolute count/time/end-of-set rule. A recorded
reasoned exception may vary cadence. Enforcement/exception-recording mechanics remain implementation design.

A capstone proves full integration qualification and refreshed coverage/quality at its verified head; it does not
retroactively supply checks on each intermediate PR. An exception changes timing, not test results or the meaning of a
successful capstone. **Release full exact-head verification remains unconditional and independent**, even with a recent
capstone, equivalent tree or recorded cadence variation.

### D5 — Preserve every master-push full CI during the first rollout

Every master push retains full CI during the first implementation rollout. Conditional post-merge/full-push omission is
a **separate measured follow-up**, only after trusted selection, the coverage contract and capstone enforcement qualify.
Future design requires accepted premerge qualification, no incomplete/unqualified context and no capstone due under the
recorded cadence. These are necessary future constraints, not active omission behavior or a sufficient selector.
Neither D1 nor this ADR activates push omission or a merge queue.

### D6 — Narrow advisory local feedback; defer global Sonar hooks

Adopt narrow changed-source precheck design for recurring complexity/nesting, semantic-literal and brace findings.
Prechecks are **non-gating initially**, advisory, and must not claim Sonar equivalence or impose a broad style regime.
SonarLint/SonarQube for IDE remains complementary; local silence cannot certify hosted analysis or coverage.

Defer global Sonar hooks. Any explicit-file Vortex probe requires entitlement, source-transfer/user-token policy,
actual Groovy TEST-rule parity and completeness evidence. The [primary-source research](../implementation/evidence/issue-1127-local-sonar-research.md)
distinguishes local IDE analysis, server-side Vortex and ordinary CI scanners. No installation, probe, upload or local
precheck implementation is authorized by this design delivery.

## Check/protection and activation boundary

Keep check scopes truthful: prose is not full product proof, scoped tests are not a full suite, and equivalence is an
independent evidence-admission result rather than a new build/JUnit/Sonar success. The implementation plan models names
and protection migration. No new check is created here; no skipped-success, broad `paths-ignore` or required-context
removal may substitute for selected executed checks. All current protection requirements still apply.

Policy decisions are settled. Trusted producer mechanism, topology map, wiring, capstone recording and activation are
future implementation decisions requiring a separately authorized assignment. Master-push and release full producers
remain unchanged during the first rollout. Do not weaken release proof to admit a reduced PR route.

## Consequences and alternatives

Preserving independent commits and reusing a tested whole integration tree can avoid duplicate work, at the cost of
explicit context/identity review. D3 conservatively retains expensive baseline coverage, so savings may be smaller than
focused-test timings suggest. No realized savings are claimed; use naturally occurring hosted measurements.

Retain strict freshness/full qualification as fallback. Reject worker-only selection, patch-only equivalence, partial
canonical coverage uploads, synthetic passed checks and blanket style gating. A merge queue remains a separate option
for testing latest-base integration; it can add builds and needs qualified group-SHA/Sonar/protection behavior.
Mandatory exact-head release gates are not an alternative to capstones and cannot be replaced by them.

No public/generated API, serialization, Model/Builder lifecycle, ownership, metadata protocol, module dependency or
Groovy compatibility contract changes. No user-facing DSL/API example applies. Issue #1127 remains open; design delivery
does not establish implementation, hosted savings, policy enforcement or archive safety.
