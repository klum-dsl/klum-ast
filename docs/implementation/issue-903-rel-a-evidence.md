# #903 REL-A — exact-SHA GitHub release gate

Date: 2026-10-16. Status: implementation complete; draft PR publication authorized; review/CI pending. Related: #903.
Base: `255c05c70adf190cd78a375dd2c63a9eaaf38222` (fetched `origin/master`).
Branch: `codex/903-rel-a-ci-preflight`; isolated worktree, root checkout untouched.
Authority: [ADR 0030](../adr/0030-exact-sha-ci-proof-reuse.md) and the
[accepted two-slice plan](issue-903-ci-proof-reuse.md). REL-B is not implemented here.

## Delivered boundary

The narrow [GitHub helper](../../.github/scripts/verify-release-ci.cjs) reads repository, commit,
live branch ancestry, approved workflow, exact-SHA push runs, attempt-specific jobs and exact-SHA
checks. It selects newest run by creation time/run ID before success filtering; requires successful
latest attempt, one required build job and the four reviewed required steps; and requires exactly
one relevant successful check from the numeric SonarCloud App. It verifies the latest attempt again
after reading jobs/checks, rejecting a rerun that begins during the read.

All run/job/check lists follow validated same-endpoint pagination links and reconcile stable totals,
unique numeric identities and complete pages. The GitHub filtered-run search ceiling is 1,000;
responses exceeding that ceiling fail closed. Other lists have a bounded 100-page limit. A count-only
check-suite query rejects references outside the check-runs endpoint's 1,000-suite window. API errors,
malformed fields, incomplete/ambiguous evidence or exhausted bounded retries reject the gate.

[REL-1](../../.github/workflows/release.yml) makes successful preflight a dependency of pending
Pages and protected publication. After approval, the final step before Gradle publication rechecks
the exact clean checkout and repeats the GitHub verification, comparing run/attempt/check IDs.
The protected job retains the helper from its workflow revision before checking out the requested
source SHA. Release credentials are step-scoped and are not supplied to the helper. Existing
remote version checks, local version/documentation prerequisites, pending handoff, separate RC/final
environments, public availability proof, REL-2 and independent consumer qualification are retained.

CI remains byte-identical to the base: one build job, both original Gradle invocations, worker limits,
Java 17, independently compiled Groovy lanes, coverage, uploads, documentation and Sonar. Ordinary
job outputs carry three identifiers only and confer no additional authority. No attestations,
receipts, proof manifests, report inventories, retention store, workflow-policy engine, Sonar APIs
or new Sonar integration were added. Same-SHA Sonar success does not claim attempt/analysis
correlation; the accepted residual risk and point-in-time race limitation remain explicit.

## Reviewed identity provenance

Read-only GitHub metadata inspected on 2026-10-16; no release dispatch, secrets read, or remote mutation:

| Identity | Observed value | Source |
| --- | --- | --- |
| Repository | `40505913`, `klum-dsl/klum-ast` | [repository API](https://api.github.com/repos/klum-dsl/klum-ast) |
| CI workflow | `23237467`, `.github/workflows/ci.yml`, active | [workflow API](https://api.github.com/repos/klum-dsl/klum-ast/actions/workflows/ci.yml) |
| SonarCloud App | `12526`, `sonarqubecloud`, owner `SonarSource` | [exact-SHA checks](https://api.github.com/repos/klum-dsl/klum-ast/commits/fb793bb5521284d66c0de48122873e27f8e4ac6b/check-runs) |
| Successful reference run | `38897125107`, attempt `1`, exact SHA `fb793bb5521284d66c0de48122873e27f8e4ac6b` | [run API](https://api.github.com/repos/klum-dsl/klum-ast/actions/runs/38897125107) |
| Required step identities | build job; steps 6–9: Build with Gradle, Qualify annotation propagation artifacts, Publish Test Report, Archive compatibility-lane evidence | [attempt-specific jobs](https://api.github.com/repos/klum-dsl/klum-ast/actions/runs/38897125107/attempts/1/jobs) |

These concrete identities are proposed for admission through normal review of this branch; observation
alone is not maintainer approval. Required names/numbers are checked only after repository, numeric
workflow and path verification. Workflow contract tests guard the commands, actions and single-job
shape; changes require review together with the helper and runbook. No workflow-blob registry exists.
GitHub's [workflow-run API](https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow),
[attempt-job API](https://docs.github.com/en/rest/actions/workflow-jobs#list-jobs-for-a-workflow-run-attempt)
and [check-run API](https://docs.github.com/en/rest/checks/runs#list-check-runs-for-a-git-reference)
define the underlying result/pagination interfaces. `filter=all` prevents the Checks API's default
latest-only filtering from hiding conflicting Sonar records.

## Test-first acceptance and review

`ReleaseCiPreflightTest` and `ReleaseCiWorkflowTest`, both `@Issue('903')`, run under the existing
Gradle-plugin Spock test task and therefore ordinary CI. Node is the hosted Ubuntu runner's existing
runtime; production uses only built-in Node APIs. The [official Ubuntu image inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
listed Node 22.23.3 when checked on 2026-10-16; local qualification uses that generation as well as
Node 26. SnakeYAML is a test-only dependency for parsing the actual workflow graph. All API responses are synthetic fixtures; tests make no network calls and
receive only a synthetic token. CLI tests preload a fixture fetch boundary and execute the actual
helper and extracted workflow shell with harmless protected-operation markers.

Observed red-to-green cycles: missing helper; multi-page traversal; workflow dependency/immediate
recheck wiring; run status/latest-attempt changes during result reads; and malformed timestamp/
contradictory-pagination rejection. Positive controls prove
complete preflight, unchanged recheck and protected marker reachability. Negative cases include
wrong/fork repository, full-SHA/ref/line/workflow/event mismatches, newer failed or incomplete runs,
failed latest attempts, mixed-attempt jobs, missing/ambiguous jobs and steps, all unsuccessful
required outcomes, missing/failed/untrusted/ambiguous/wrong-SHA Sonar, malformed/duplicate/missing
API fields, bounded errors/timeouts, later-page failures and changed post-approval identities,
status or ancestry. Failed preflight makes Pages/publication ineligible; failed real CLI/recheck
shell writes no successful outputs and reaches no protected marker.

Validation completed with Java 17:

- Focused `:klum-ast-gradle-plugin:test --tests '*ReleaseCi*Test'`: 123 passing cases on local Node 26.
- Root `check --no-parallel --max-workers=2`: passed in 17m 14s, including the complete Groovy 3/4/5
  suites, lane isolation, coverage, license, documentation renderer and full Gradle-plugin suite.
- Focused helper/workflow suite forced with hosted-generation Node 22.23.3 on `PATH`: 123 passing cases.
  The official temporary runtime archive was checked against Node's published SHA-256 list.
- Separate `annotationPropagationArtifactsGroovy3/4/5 --no-parallel --max-workers=2`: passed in
  1m 2s. All three existing artifact qualification lanes completed successfully.
- Pinned actionlint 1.7.7 (including shellcheck), Node syntax checks, local Markdown target checks and
  `git diff --check`: passed. CI, reusable pending documentation, REL-2, public promotion and both
  historical investigation files remain byte-identical to the base.

Standards/spec review was performed locally without child workers, as requested. Standards review
found no remaining documented violations: new test classes use `Test` and class-level `@Issue('903')`,
imports use simple names, license headers are retained, and build changes only integrate test inputs
and a test-only YAML parser. Spec review found no remaining REL-A omissions or extra runtime authority;
malformed timestamp/pagination findings were repaired with red-to-green fixtures. The overengineering
review confirms the GitHub and environment authorities are consumed directly, without duplicated proof
storage or new publication abstractions. No public DSL/API or production dependency contract changes.
Commit-history review retains three ordered reasoning steps: helper/tests (`dc30d39b`), workflow/recheck
and API stabilization, then synchronized runbook/changelog/implementation evidence. No reviewed history
was rewritten and no unrelated changes are included.

Final-head remote CI/Sonar must be inspected on the published PR revision; local validation does not claim remote results. No real release or environment/protection
state is exercised by this work. Live protected-environment approval and the deployed Actions-token
context were not exercised through release dispatch; that prohibition is preserved.

## Delivery boundary

Tracker impact: `Related: #903`; the issue remains open for REL-B and final acceptance. Hive must
reconcile release-gate curation; global curation indexes are unchanged here. The initial handoff stopped
at `(ready:PR)`. The subsequent explicit maintainer request authorizes branch push and draft PR creation.
Concrete identity admission, runtime, DAG/credential scope and final-head CI/Sonar remain review items;
publication of a draft does not authorize merge or release. Delivery-channel audit on 2026-10-16:
Git SSH branch publication `authorized`; gh CLI repository capability `authorized`. No issue update,
release dispatch or environment/protection change is included.
Hive owns later reconciliation and archive safety; this worker does not archive itself.

## Pagination reliability review

Review baseline: PR #915 head `4fedd102618c66c8ec8034109edb1b1ee2152cfe`, 2026-10-16.
Scope: pagination helper, its existing fixture seam and necessary documentation only. The unchanged
release-tooling changelog entry was relocated to avoid an overlapping insertion from merged #913;
no compiler changes were imported or edited. Accepted
repository/source/workflow/attempt/Sonar selection, workflow DAG, credentials, permissions,
verification commands, topology and REL-B are unchanged. Review is local; no child workers.

The [workflow-run endpoint](https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow)
documents a 1,000-result search window when using filters including `head_sha`, `event` and `branch`.
`total_count` describes that filtered search within the selected workflow. A bounded read-only
KlumAST probe returned 951 unfiltered CI workflow runs, one result for the exact eligible reference SHA/
master/push filter, and zero when changing its event to workflow_dispatch. The reference run was
`38897125107`; no success filter was used. Consequently the original 1,000 guard is justified and
retained; it does not reject a release merely because the repository has over 1,000 historical runs.
A final bounded probe of public VS Code workflow `1472838` with `event=pull_request` advertised
`total_count=2500` on page one. Requests beyond the search window (page 1001 at one per page,
page 11 at 100 per page, and the advertised last page 2500 at one per page) returned HTTP 200
with empty results and `total_count=0`. A large reported count is therefore not evidence that
all advertised pages are accessible. The existing cap is retained with a specific diagnostic;
the fixture asserts rejection before any continuation request.

The [attempt jobs endpoint](https://docs.github.com/en/rest/actions/workflow-jobs#list-jobs-for-a-workflow-run-attempt)
documents pages of at most 100 records with no analogous search ceiling. The
[reference checks endpoint](https://docs.github.com/en/rest/checks/runs#list-check-runs-for-a-git-reference)
also permits 100-record pages, but searches only the 1,000 most recent check suites. This is a
suite limit, not a 1,000-check limit. The original non-workflow threshold of 10,000 is derived from
100 pages times 100 records, not a GitHub service cap. It adds no rejection within the existing
page budget. Fully accessible results requiring 101 pages are intentionally outside that bounded
budget; the reader fails rather than weakening completeness. Keep that generous existing budget
for this one-job CI contract, rather than inventing an unbounded history reader.

[GitHub pagination guidance](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api)
requires following continuation links; `last` may be absent. Actual responses for KlumAST runs,
jobs and checks used `/repositories/40505913/...` in Link headers even when requested through
`/repos/klum-dsl/klum-ast/...`. The former helper rejected those legitimate second pages. A
reproducing fixture failed before the two-line correction. The helper now normalizes only that
known numeric repository alias before its existing origin/path/query validation. It continues to
construct subsequent requests from the original approved endpoint and rejects foreign IDs/routes.

A second focused correction probes only `total_count` from the existing
[check-suite endpoint](https://docs.github.com/en/rest/checks/suites#list-check-suites-for-a-git-reference)
using `per_page=1`. Larger-than-1,000 references now fail explicitly because the existing reference
check-runs endpoint cannot prove completeness. Missing, malformed or unavailable count evidence
also fails. This is not a suite inventory or a new check selection rule; it adds one read-only
request per verification through the existing client, with the same timeout/retry bounds. The
reference SHA probe returned four suites. No suite-by-suite fallback, new client, credentials or
pagination framework was added.

All original 123 cases remain. The 23 additional pagination cases cover the real numeric alias and
wrong-ID rejection, exactly 1,000 workflow results, 1,001 and 10,000 jobs/checks, the suite service
window, missing/malformed later-page links, conflicting totals and duplicate identities across
pages, and rejection before page 101. Existing later-page newer-failed-run coverage is retained.
The existing fixture builder was extended to create these pages; no parallel suite was duplicated.

Validation: 146 focused Node-backed Spock cases passed on Node 22. The full Java-17 repository
`check --no-parallel --max-workers=2` passed in 12m26s, including Groovy 3/4/5, isolation, coverage,
documentation/license checks and the 309-case affected Gradle-plugin suite (zero failures/errors).
Remote CI/Sonar are inspected on the published follow-up head and reported in the consolidated
PR review response; the previous head passed both. Reviewed history is preserved in one additive
pagination follow-up. Local standards/spec review found no remaining findings within this scope.
Actionlint (including shellcheck), Node syntax and diff checks passed. Remaining limits are the
filtered-run search window, reference-suite window and deliberate local page budget; all remain
fail-closed, with no older-success fallback. Checks deleted by GitHub are not recovered by a custom
history or retention system; the accepted gate continues to verify GitHub's available current state.
