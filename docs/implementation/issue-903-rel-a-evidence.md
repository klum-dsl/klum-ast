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
responses exceeding that ceiling fail closed. Other lists have a bounded 100-page limit. API errors,
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
