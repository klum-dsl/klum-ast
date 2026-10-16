# #903 — Direct GitHub release verification implementation plan

Date: 2026-10-16. Status: accepted revised design; production implementation has not started.
Authority: canonical [#903](https://github.com/klum-dsl/klum-ast/issues/903) maintainer decision and
[ADR 0030](../adr/0030-exact-sha-ci-proof-reuse.md). Related: #903; keep open until full acceptance.

## Superseded design and evidence

The artifact-backed attestation/receipt/manifest/report-inventory/retention and Sonar-analysis-correlation plan is
superseded for disproportionate complexity. Preserve the [original investigation](issue-903-release-verification-contract.md)
and [Sonar feasibility report](issue-903-sonar-feasibility.md) unchanged as historical evidence/rejected alternatives.
Their earlier admission gates, missing exact-analysis-to-check join and proposed producer mapping are no longer active
requirements. No Sonar CE/analysis queries, separate integration, proof artifacts or custom retention system remain.

## Existing seams and minimum reviewed contract

[CI](../../.github/workflows/ci.yml) retains its one `build` job, full `check --scan sonar --info` invocation and explicit
Groovy 3/4/5 annotation-propagation invocation with existing scheduling limits. Required successful steps cover those
two invocations, `Publish Test Report` and `Archive compatibility-lane evidence`; failure-only class diagnostics are
excluded. Verify actual approved workflow/action/job/step identities during implementation and keep this small contract
reviewed with workflow changes. Preserve all original lanes, isolation, coverage, artifact and documentation checks;
no second task/report enumeration or workflow-blob admission registry.

[REL-1](../../.github/workflows/release.yml) needs read-only GitHub gates before pending documentation/approval and
immediately before publication. [Root build](../../build.gradle) and existing publishing conventions need successful
local prerequisite ancestry on actual protected operations. Keep these concerns separate through two slices only.

## REL-A — GitHub preflight and protected recheck

Implement a narrow read-only GitHub helper and workflow dependency edges. Use existing fixture/test harnesses with
synthetic API responses and no live calls or credentials in tests. Do not build a generic evidence/adapter framework.

- Validate repository numeric ID/full name/head repository, exact full source SHA, intended line/live ancestry,
  approved numeric CI workflow ID and exact path. Eligible runs are same-repository pushes for that exact line/SHA.
- Paginate all relevant run/job/check lists and choose newest eligible run by creation time/run ID before success
  filtering. Require successful completed latest attempt and attempt-specific required job/step outcomes.
- Require successful completed trusted SonarCloud App check for that exact SHA using an evidenced, reviewed numeric
  App ID. Check name alone is insufficient. Exact attempt/analysis correlation is explicitly unnecessary and is an
  accepted residual risk. Reject ambiguous relevant check matches instead of choosing a convenient green result.
- Before pending documentation/protected approval, retain only selected run/attempt/check identifiers as ordinary
  workflow outputs. After approval, immediately before publication, repeat essential checks and compare identities.
  Changed evidence requires new preflight/approval; no silent rebinding, older-green fallback or automatic dispatch.
- Use only contents/actions/checks read permissions. Never pass scanner or publication credentials to preflight.
  Missing, malformed, ambiguous, unavailable or incompletely paginated evidence fails closed. Bound transient retries.
- Preserve protected environments, version/tag checks, local version/documentation checks and pending handoff.
  Do not rerun/split the matrix, download proof artifacts or introduce receipts/manifests/inventories/retention policy.

Keep helper and workflow changes in small reasoned commits with passing tests. Update relevant release documentation
and neutral CHANGES.md tooling entry in this slice. Master-first; future maintenance admission is separately reviewed.

Focused acceptance:

| Case | Expected result |
| --- | --- |
| Exact repo/SHA/line, approved workflow, newest eligible successful latest attempt, required job/steps and trusted successful exact-SHA Sonar check | Preflight passes; unchanged post-approval recheck passes |
| Wrong/fork repo, SHA prefix/ancestor/merge ref, wrong line/workflow/path/event | Reject before pending documentation/approval/publication |
| Older green run with newest eligible failed/running run; pending/failed latest attempt; mixed-attempt jobs | Reject without fallback |
| Missing/skipped/neutral/cancelled/timed-out/failed required job or step; failed report/upload | Reject; failure-only diagnostics are not required passing steps |
| Missing/pending/failed or spoofed-App/wrong-SHA Sonar check; ambiguous matching checks | Reject; a trusted successful exact-SHA check suffices without CE/analysis correlation |
| API error/timeout/malformed fields, duplicates, pagination failure or later-page conflicting evidence | Fail closed after bounded retries; never weaken selection |
| Run/attempt/check identity changes after approval, branch ancestry changes, required result becomes unavailable/unsuccessful | Recheck rejects; fresh preflight/approval required |
| CI topology/commands/worker controls and full verification families | Unchanged; release dispatch selects no repeated matrix |

## REL-B — Gradle protected-operation dependencies

Inspect actual task types, destination bindings, signatories and shared signing behavior before wiring. Attach successful
local authorization/version/documentation dependencies to protected-key signing, real Sonatype staging initialization,
protected Maven uploads, staging close/release and production Plugin Portal actions. Preserve complete-product entry
checks at actual operations; avoid cycles. Plain Gradle dependencies suffice; no publishing framework or receipt guards.

Use established isolated TestKit fixtures and synthetic credentials; replace irreversible actions with markers.
Prove action containment as well as real graph ancestry. Shared signing may serve Maven Local, so guard actual protected
key/destination use while preserving nonprotected fixture behavior. Destination redirection and mixed graphs remain
protected regardless of local-looking names/properties. Synchronize release documentation and changelog for this slice.

Focused acceptance:

| Case | Expected result |
| --- | --- |
| Authorized complete-product fixture, valid version/documentation prerequisites | Protected markers execute after successful dependencies |
| Authorization/version/documentation failure individually | Zero protected sign/staging/upload/Portal markers |
| Direct low-level invocation, alternative aggregates, reordered arguments, parallel execution, `--continue` | No protected action bypass; verify graph ancestry and execution |
| Shared signing in local-only/protected-only/mixed graphs; protected destination/key behind local-looking request | Protected operations remain guarded; no property/name bypass |
| Maven Local, disposable TestKit/local publication, nonprotected fixture signing, compilation and ordinary verification | Work without GitHub proof or protected credentials |

## Validation, scope and overengineering review

All new Spock tests use `Test` suffix and `@Issue("903")`. Start with focused baseline tests, then affected module
coverage and required Groovy 3/4/5 lanes at final tip for executable changes. Run applicable workflow/YAML/helper,
documentation and diff checks. Fixture tests make no live calls and never dispatch releases. A dry-run proves graph
selection, not failure containment. Inspect final-head CI/Sonar and address findings before handoff.

Mandatory review: Actions/Checks already provide identities/results; protected environments provide approval and
credentials; plain Gradle dependencies provide successful local prerequisites. Confirm implementation does not duplicate
these authorities with custom attestations/receipts/manifests/report inventories/retention systems, Sonar CE/analysis
integration, generic adapters or publishing frameworks. Any extra slice needs a demonstrated dependency and review.

Preserve ADRs 0012/0013/0019 ordering and independent consumer/public proof. Master first, no 4.0.x backport;
4.1.x is a separate later decision. No environment/protection changes or maintenance branches are implicit.

This amendment is documentation only. Validate local Markdown links, unchanged historical reports, diff/scope and additive
commit history. Omit Groovy lanes under the documentation-only exception. Update existing draft PR #912 in place with
`Related: #903`; preserve reviewed commits, push the additive revision and inspect final-head CI/Sonar. No production
implementation, issue-state mutation, secrets access, release dispatch or child tasks. Retain `(PR:open)` until verified
merged and request Hive reconciliation; do not mark `(arch)` autonomously.
