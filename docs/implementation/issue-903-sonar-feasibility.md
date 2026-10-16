# #903 — Read-only Sonar attempt-correlation feasibility

Date: 2026-10-16. Outcome: **NO-GO for strict ADR 0030 admission with current observed public metadata.**
Related: [#903](https://github.com/klum-dsl/klum-ast/issues/903),
[ADR 0030](../adr/0030-exact-sha-ci-proof-reuse.md), [accepted plan](issue-903-ci-proof-reuse.md).

This is a completed bounded investigation, not an assertion that correlation is impossible under every integration.
Hive must decide the integration contract before proof schema/validator implementation resumes. No production,
workflow, protection or release behavior changed; no credentials, API mutations, dispatch, push or PR were used.

## Method and source

Isolated branch `codex/issue-903-proof-validator`, base `fb793bb5521284d66c0de48122873e27f8e4ac6b`
(current fetched origin/master when this investigation began). Root checkout untouched. All live requests used
Python standard-library HTTPS GET with a probe User-Agent and **no Authorization header**, cookies or credential
lookup. Initial sandbox DNS was unavailable; approved network execution succeeded. Only selected public identity,
status and API-description fields are retained here; no raw logs or credentials were downloaded or stored.
The report uses these endpoint paths as reproducible provenance; values are a dated snapshot.

## Established identities and joins

GitHub base URL: `https://api.github.com/repos/klum-dsl/klum-ast/`.

| Read endpoint / field | Observed value or relationship |
| --- | --- |
| Repository root: `id`, `full_name`, `private` | `40505913`, `klum-dsl/klum-ast`, false |
| `actions/workflows/ci.yml`: `id`, `path` | `23237467`, `.github/workflows/ci.yml` |
| `contents/.github/workflows/ci.yml?ref=fb793bb5521284d66c0de48122873e27f8e4ac6b`: `sha` | Workflow blob `9b59de0ce752da9d909323f560b61af0106fde96` |
| `actions/workflows/23237467/runs?head_sha=fb793bb5521284d66c0de48122873e27f8e4ac6b&event=push&branch=master&per_page=100` | Exactly one returned run, total 1, no next-page Link: `38897125107`, latest `run_attempt=1`, completed/success. Repository and head repository both ID `40505913` / full name `klum-dsl/klum-ast`. Exact full head SHA, workflow ID, event and branch agree. Selection did not filter on success. |
| `actions/runs/38897125107/attempts/1/jobs?per_page=100` | Total 2 records: build job `116960153248`, JUnit check record `116970410835`; both explicitly run `38897125107`, attempt 1, exact head SHA. Single actual build topology remains unchanged. |
| Build `steps` | Step 6 `Build with Gradle`, completed/success, 11:57:03–12:22:44 UTC. Artifact qualification step 7 and compatibility upload step 9 completed/success. Failure-only archive skipped as expected. Step metadata has no scanner task/analysis ID. |
| Run `check_suite_id` | Actions suite `105411624667` |
| `commits/<full SHA>/check-runs?per_page=100` | Total 3 records: build, JUnit and Sonar. Sonar check `116969935456`, exact head SHA, completed/success, App ID `12526`, slug `sonarqubecloud`, owner SonarSource. Sonar suite `105411621062` differs from Actions suite. |
| Sonar check correlation fields | `external_id` empty; `details_url` is `https://sonarcloud.io/dashboard?id=klum-dsl_klum-ast&branch=master`; output links also target mutable branch pages. No run ID, attempt, scanner task ID or analysis ID in inspected check payload. |
| `https://api.github.com/apps/sonarqubecloud` | Independently returns App ID `12526` and external URL `https://sonarcloud.io`. This is observed identity, **not maintainer approval** of an allowlist. |
| `actions/runs/38897125107/artifacts?per_page=100` | Total 1: compatibility artifact `12045642466`, unexpired, digest `sha256:3039cafc39a40f84df0eb2b695f5c5fcf02ec369182927a27eb7770cbf4cf357`. API binds run/repository/SHA/branch, but no attempt field; current name is not attempt-qualified. No dedicated Sonar correlation artifact. Bytes not downloaded. |

Sonar base URL: `https://sonarcloud.io/`.

| Read endpoint / field | Observed value or relationship |
| --- | --- |
| `api/components/show?component=klum-dsl_klum-ast` | Public project `klum-dsl_klum-ast`, organization `klum-dsl`; matches configured `build.gradle` properties. |
| `api/project_analyses/search?project=klum-dsl_klum-ast&branch=master&ps=100` | Analysis `305a60ba-abd9-4159-ba72-76b29af31f1c`, revision exactly `fb793bb5521284d66c0de48122873e27f8e4ac6b`, dated 12:21:38 UTC. Fields include key, revision, date, version/events; no Actions run/attempt or GitHub check ID. |
| `api/qualitygates/project_status?analysisId=305a60ba-abd9-4159-ba72-76b29af31f1c` | Analysis-specific result `projectStatus.status=OK`. This proves this analysis passed, not which CI attempt submitted it. |
| `api/ce/component?component=klum-dsl_klum-ast` (also queried with `branch=master`) | Mutable current task `AaFErYPCLlIa2KDqBWOp`, analysis `be1a8acf-3ac7-4167-be88-26e9113d71f2`, SUCCESS, PR 911, submitted 12:26:24 UTC. Even with the branch parameter, response describes this PR task rather than the selected master analysis. No branch filtering assumption is valid here. |
| `api/ce/task?id=AaFErYPCLlIa2KDqBWOp` | Anonymous HTTP 404 despite task identity returned by component endpoint. This is unavailable task detail, not proof the task never existed. |
| `api/ce/activity?component=klum-dsl_klum-ast&ps=5` | Anonymous HTTP 401. |
| `api/webservices/list` | Task details require Execute Analysis permission; component-scoped activity requires project administration. Analysis search requires Browse; quality status allows Browse or administration. No additional permission was requested. |
| GitHub `actions/jobs/116960153248/logs` | Anonymous HTTP 403; scanner log correlation unavailable through this channel. No authenticated retry or secret lookup. |

## Bounded counterexamples and limits

The latest 100 CI workflow runs (all events, total 945, additional pages exist) contained no rerun attempts.
One sampled cancelled PR run was `38035115764`, attempt 1, SHA
`0f3dba6ecbf3c4a5ac37b6f56eb84336a0b72e16`. Failed/latest-rerun invalidation is therefore **not demonstrated**
against live records. The latest 100 master analyses (total 388, additional pages exist) had no duplicate revision.
Multiple same-SHA analyses and asynchronous pending-to-completed quality transitions were **not exercised**.
These bounded samples are not exhaustive historical searches and absence in them proves no uniqueness invariant.
Exact selected-SHA runs, selected-attempt jobs, checks and artifact lists fit their reported totals in one page.
A future adapter must paginate every relevant list and reject ambiguity/errors, independently of this probe's bounds.

There is an actual counterexample to using project-current task identity: a newer PR task is returned while the
selected master analysis and CI attempt are different. A same-SHA green check plus a same-SHA green analysis still
has no exposed authoritative association to the selected attempt. Timestamps, version strings, check names and
branch dashboard links cannot repair the missing join. No permission denial is interpreted as missing evidence
being acceptable.

Anonymous access demonstrates the listed public metadata can be read without Sonar or publication secrets.
It does **not** exercise the actual producer or protected preflight GITHUB_TOKEN contexts, network availability,
rate limits, or artifact-download permission. Producer already references SONAR_TOKEN for scanning; this probe
neither reads it nor establishes its task-query capability. Existing credentials' capabilities remain unproven.

## Minimum additional correlation and adapter boundary

A separate read-only adapter is needed to reconcile service response shapes, pagination, errors and immutable
identities, but an adapter alone cannot manufacture absent provenance. Before admission, establish all of:

1. The trusted selected CI attempt retains scanner-generated `report-task.txt` correlation (actual location must
   be verified for this Gradle scanner), including server URL, exact project key and CE task ID. Bind it to repository,
   source SHA, workflow ID/path/blob, run ID, latest attempt, build job and scanner step through an attempt-qualified
   immutable artifact and manifest. Whitelist correlation fields; do not upload tokens or arbitrary scanner context.
2. A trusted producer with existing permitted access resolves that **exact** CE task to SUCCESS, analysis ID and
   project identity, then binds the analysis's exact revision and analysis-specific completed quality result.
   Preserve authenticated service provenance in the trusted producer's evidence; copying caller-supplied IDs alone
   is insufficient. Polling must be bounded and failure/pending/unknown results cannot issue passing evidence.
3. Establish a service-verifiable association between that analysis and the approved Sonar App's exact GitHub check
   ID. The inspected check response exposes no such association. Retaining an arbitrary same-SHA check ID does not
   solve this. Investigate supported service metadata or have Hive explicitly decide a revised integration contract;
   this report does not propose relaxing ADR 0030 silently.
4. Demonstrate protected read-only consumer reconciliation without Execute Analysis/admin credentials, SONAR_TOKEN,
   release secrets or new privileges. It must validate the trusted attempt artifact and public analysis-specific
   revision/result plus the approved App/check association. If the producer can supply the missing trusted mapping,
   document precisely which assertions the consumer verifies and which it trusts from the reviewed producer.

No numeric ID is guessed. Repository/workflow/App IDs above are observations; policy approval, scanner task identity
for the selected attempt, trusted task-to-analysis mapping, exact analysis-to-check join, and real consumer access
are not established. Do not implement proof validation or producer wiring until Hive adjudicates these gaps.

## Validation and delivery

Documentation-only evidence. Reviewed against ADR 0030 and the accepted plan: no inference from successful upload,
names, timestamps or matching SHA was promoted to authoritative attempt correlation. No code, build inputs,
workflows, settings or user DSL changed; Groovy 3/4/5 lanes are omitted under the documentation-only exception.
Local Markdown target validation and `git diff --check` are the applicable checks. Tracker impact: none; #903 remains
open. Local report commit only; no push or PR. Requested investigation is complete; Hive reconciliation requested.
