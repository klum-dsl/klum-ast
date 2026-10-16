# #903 — Exact-SHA CI-proof reuse implementation plan

Date: 2026-10-16. Status: accepted policy/design package; implementation has not started.

Authority: [ADR 0030](../adr/0030-exact-sha-ci-proof-reuse.md), accepted exact-SHA reuse direction from
maintainer delegation, [investigation](issue-903-release-verification-contract.md).
Related: [#903](https://github.com/klum-dsl/klum-ast/issues/903); keep open through implementation acceptance.
This package changes documentation only and makes no claim that any proposed acceptance test already passes.

## Current behavior and seams

The investigation was performed at `96608b7c8c1657662f95e003b09d6e6d96cde777`, also this worktree's base.
Its report is copied verbatim from investigation commit `9ef9e975f6e8f6e652eea02c4800a4277aa6a924` so the
plan remains reviewable independently of the other worktree. Live run/protection observations in that report
are dated snapshots, not current checks by this planning task.

| Seam | Current behavior/failure | Planned responsibility |
| --- | --- | --- |
| [ci.yml](../../.github/workflows/ci.yml) | Single `build` job; matrix, Sonar submission, explicit artifact tasks; compatibility artifact only, no complete source-proof manifest | Keep topology/invocations; add exhaustive reports, attempt-qualified manifest/artifacts and trusted Sonar correlation |
| [release.yml](../../.github/workflows/release.yml) | No CI lookup for input commit; pending documentation can start before matrix proof | Read-only preflight dependency for both pending documentation and publication, then protected recheck |
| [build.gradle](../../build.gradle) | Root `:check` is a sibling of publication; lower-level Sign/upload/initialization lack guards | Shared successful prerequisite for each actual irreversible operation, sole complete-product entry |
| [multi-Groovy convention](../../buildSrc/src/main/groovy/klum-ast.multigroovy-conventions.gradle) | Five owners independently compile G3/G4/G5 and verify isolation | Unchanged source qualification, explicit result inventory |
| [coverage](../../code-coverage-report/build.gradle) | Three aggregations; Sonar consumes G3 baseline | Retain all outputs without feeding duplicate production coverage to Sonar |
| [pending Pages](../../.github/workflows/publish-pending-documentation.yml) | Protected deployment and immutable source/version handoff | Remains separate; cannot become eligible when source preflight fails |
| [REL-2](../../.github/workflows/verify-public-release.yml) / [recovery](../../.github/workflows/recover-public-release-record.yml) | Public resolve proof, promotion and exceptional record recovery | Unchanged; never treat CI receipt as public-product proof |

Source task families at this base: `test`, `groovy4Tests`, `groovy5Tests`, `verifyTestLaneIsolation` for
klum-ast/runtime/jackson/bean-validation/test-support; baseline `test` also for annotations/Gradle plugin;
three coverage reports and three explicit `annotationPropagationArtifactsGroovyN` tasks; all other `check`
verification including license, root Javadocs and renderer. Derive an exhaustive contract from the admitted
revision's graph rather than baking 206 into a test. Assert family/module membership and task dependencies.
An intentional NO-SOURCE baseline is recorded explicitly, not mistaken for missing evidence.

Suggested narrow helper seams are a repository-local producer/validator under `release/verification/` and
buildSrc release guard wiring. These directories/helpers are proposed, not existing files. Keep API transport,
pure proof validation and Gradle task wiring separable for fixture tests. The verifier must execute from the
reviewed dispatch/release workflow revision, never from a downloaded artifact or an unadmitted input ref.
Bind that verifier/policy revision in the receipt. Admit the release workflow and proof-contract revisions
explicitly before using their changed semantics on master or a maintenance line.

## Proof handoff shape

Illustrative receipt fields (final schema follows the slice-1 feasibility admission gate):

```text
schemaVersion, contractRevision, verifierRevision
release: {stage, version, sha, sourceBranch}
producer: {repositoryId, repository, workflowId, path, workflowBlob,
           event, headBranch, headSha, runId, attempt, completedAt}
verification: {jobId, requiredStepResults, checkIds, taskOutcomes, toolchain}
sonar: {appId, projectKey, scannerTaskId, analysisId, checkId, revision, qualityResult}
artifacts: [{id, name, serviceDigest, manifestSha256, expiresAt,
             files: [{path, producerTask, lane, size, sha256}]}]
validatedAt, freshnessDeadline
```

The producer payload does not claim its own service digest; the consumer obtains that from the API and
records it in the receipt. Strict types, full lowercase SHAs, positive numeric IDs, schema/contract versions,
unique normalized paths and allowed inventories are validated. Unknown contract/schema versions fail.
Receipt release identity includes stage/version even though reusable CI source proof itself is version-neutral.
Preflight outputs only receipt identity/hash and selected run/attempt, not an executable payload. Publication
retrieves the exact receipt, validates its digest and repeats the API/data checks in ADR 0030.

Example: release input SHA S, newest trusted push run R attempt 2 on the selected line, successful complete
reports and Sonar analysis A yields receipt `(S,R,2,A)`. After approval, a rerun attempt 3 causes the protected
recheck to reject that receipt even if attempt 2 was green. A new preflight must bind the current valid attempt.
The slice-1 probe must prove which metadata the producer and protected preflight can each access read-only
with existing credentials; do not assume the Actions token can query Sonar. Correlation metadata must be retained
by CI; release must not inherit `SONAR_TOKEN` or any publication credential into preflight.

## Slices and reasoned commit boundaries

All slices map to #903. Policy choices were confirmed by maintainer delegation on 2026-10-16.
Implement on a new dedicated issue branch under the accepted contract.
Keep production changes with their passing tests; never commit a deliberately failing guard fixture by itself.

### 1. Establish SonarCloud feasibility, then define the proof contract

First commit: a bounded read-only feasibility record using actual GitHub Actions/SonarCloud metadata. No producer
manifest, receipt validator, workflow change or Gradle guard starts before this admission gate passes. Inventory:

- Trusted GitHub workflow/repository/run/attempt/job/scanner-step/check fields and their relationships.
- Sonar task/analysis/project/revision and completed quality-gate fields, including the exact GitHub App identity.
- The authoritative join from successful scanner execution in the selected attempt to its analysis and quality
  gate. Test whether a same-SHA successful check can belong to another workflow run or attempt.
- Reruns, failed attempts, multiple analyses of one SHA and asynchronous quality completion, using actual records.
- Read-only access separately available to producer and protected preflight with existing credentials; no release
  secrets, new privileges or privileged credentials added to unprotected CI.

Record field paths, sanitized examples, provenance, counterexamples and access limitations. Names, timestamps,
branch labels, successful uploads and same-SHA green checks are not authoritative joins. Missing/ambiguous binding,
insufficient read-only access or unavailable counterexample records leave admission unproven: stop for a maintainer
integration-contract decision before substantial implementation, without heuristic fallback. The probe is future
slice-1 work, not executed by this documentation amendment.

Only after admission, commit strict schema, pure validator, exhaustive inventory and focused fixture tests;
add the read-only API adapter with pagination/error coverage as a separate reasoned commit. Reuse the existing
fixture/test harness with sanitized response shapes; fixture tests make no live calls or credential demands.
Acceptance: P1 and N1–N7/N10. Explicit NO-SOURCE cases, baseline-only Sonar input and G4/G5 retention remain required.

### 2. Emit complete CI evidence within the existing job

Commit: producer collection, manifest generation, attempt-qualified immutable uploads, failure diagnostics,
correlation and retention settings plus producer fixtures/workflow contract assertions. Preserve existing task
commands, worker controls, reporting behavior and one `build` job. Write passing proof only after every required
verification/report/upload input succeeds; the run conclusion remains an independent consumer check. If Sonar
quality completes after CI, consumer waits only through bounded read-only polling and fails on timeout.
Do not mark scan submission as quality success to avoid waiting.

Acceptance: P1/P2 and N5–N7. Enumerate exact expected XML/report paths from contract, including baseline JUnit,
three coverage outputs and artifact qualification. Ensure report uploader failure makes proof unusable; failure
uploads may retain partial diagnostics without authorizing publication. Stable required-check names remain intact.
Check no cross-lane compiled test/fixture sharing is introduced and all original source verification families remain.

### 3. Gate REL-1 and recheck after protected approval

Commit: preflight, minimal read permissions, receipt retention/outputs and DAG edges for both pending documentation
and publication, with mock API and workflow-DAG acceptance. Follow with protected recheck in the same slice before
calling any signing/staging/publication command. Tests assert receipt identity and both gates, not only step names.
Preflight is an AND dependency alongside existing version/identity checks; it must not replace them.

Acceptance: P3 and N1–N10. Validate live ancestry from the selected branch; proof selection searches all paginated
same-SHA eligible runs, chooses newest before checking success, and uses attempt-specific metadata. Preserve
concurrency, environment approval, remote identity checks and pending handoff. No automatic CI or release dispatch.
Keep credentials scoped to protected publication; API logs redact tokens and contain only selected non-secret fields.

### 4. Enforce the protected operation boundary without disrupting development

Before wiring, inspect actual Gradle/plugin task types, publication/repository destinations, signing key use and
shared dependencies. Current source uses Nexus publish plugin 1.3.0 and Plugin Publish 1.2.1; the base convention
calls `sign it` for each publication and includes Maven Local in its signing-required predicate. Local and protected
paths share `:klum-ast:signMavenJavaPublication`, confirmed by offline JDK-17 dry-runs of
`:klum-ast:publishToMavenLocal` and `publishCompleteKlumAstProduct` with task-type/dependency inspection.
A third dry-run of `:klum-ast:jar :klum-ast:generatePomFileForMavenJavaPublication` selected no publication Sign,
upload or staging action. All three used `--dry-run --offline --no-configuration-cache --no-build-cache`;
root task actions were skipped (buildSrc bootstrap executed). These establish graph structure, not action safety.
Do not attach release prerequisites to every Sign task blindly.
Retain fixture-specific signing behavior without making ordinary preparation require a GitHub receipt.

Class A is real Sonatype initialization/upload/close/release, protected-key signing and production Portal publication.
Class B is Maven Local, disposable TestKit/isolated qualification repositories and local consumer publication without
protected destinations/keys. Class C is nonpublishing compilation, JAR/module/POM/sources/Javadoc preparation and
verification. REL-1 owns proof/credential admission; Gradle owns actual operation/destination binding and dependency
ancestry. Reclassify redirected destinations and mixed graphs correctly: local labels/properties cannot bypass A.

Commit destination-aware operation guards and successful prerequisites with focused graph/execution tests. Prefer
existing Gradle dependency/publication models, preserving the sole protected complete-product entry and version/
documentation checks; no separate publishing hierarchy unless evidence shows the model insufficient. Avoid matrix
reexecution and artifact cycles. Assert ancestry on every actual A operation, not just aggregate membership/order.

Acceptance: P4/P5 and N11–N14. Use existing isolated TestKit/fixtures, replacing irreversible actions with markers and
synthetic credentials; no real network or protected key. Fail each prerequisite under direct, reordered, alternative
aggregate, parallel and `--continue` requests and prove zero protected-operation markers. Cover shared Sign tasks in
local-only, protected-only and mixed graphs, including direct invocation and attempted protected-key use. Prove B/C
still work without receipts, and redirecting B to a protected destination requires A guards. Confirm production graph
bindings as well as fixture behavior; dry-runs alone do not prove action containment.

### 5. Admit branches and synchronize release documentation

Commit: `RELEASING.md`, testing/release implementation notes and neutral `CHANGES.md` release-tooling entry;
record confirmed retention, Sonar policy identity and admitted branch contracts. Link ADR 0030 from the appropriate
release navigation. ADRs 0012/0013 need only links/clarifying integration wording if required; preserve their ordering.
No Schema/Model migration or Builder-first user page changes are required because APIs/runtime behavior do not change.

Acceptance: P2/P3 for master first. Do not backport to `release/4.0.x`. If/when needed, `release/4.1.x` is the
first intended maintenance branch: admit producer, verifier and operation guards together, pass its own
exact-line contract and require release proof from that same line. Do not
manufacture historical test-support coordinates. Track the historical-product inventory repair and Catwalk candidate
rebinding through existing ADR 0019 ownership, without implementing them in #903. Record issue/test/doc relationships
when GitHub mutation is later authorized. Do not close #903 until all negative containment checks and branch admission
required for the implemented scope have evidence.

## Executable acceptance matrix

Implement fixture/API tests in the selected repository test harness; new Spock classes use `Test` suffix and
`@Issue("903")`. This table specifies future tests, not results of this planning task.

| ID | Input/control | Required outcome |
| --- | --- | --- |
| P1 | Probe establishes authoritative run/attempt-to-analysis/quality join and read-only access; complete admitted proof | Admit slice 1 before downstream work; later produce one receipt reconciled to API/contract |
| P2 | Original CI graph and report producers on admitted source | Same verification families, one production product/job; all G3/G4/G5/report evidence; no matrix weakening |
| P3 | Valid preflight, approved release, unchanged live receipt/proof | Pending documentation and publication become eligible in order; protected recheck succeeds before operation |
| P4 | Valid protected fixture prerequisites and complete entry task | Stub protected operations execute only after guards; source matrix does not rerun |
| P5 | Maven Local, disposable TestKit/qualification repository publication, local consumer artifacts and nonpublishing preparation without receipt | B/C remain usable with existing fixture signing behavior; no protected destination/key reachable |
| N1 | Wrong SHA, prefix, ancestor, equivalent tree, PR merge SHA or dispatch event head instead of input | Reject before pending Pages/signing/staging |
| N2 | Foreign/fork repository, wrong workflow numeric ID/path/blob/contract, lookalike workflow name | Reject untrusted producer |
| N3 | Wrong source branch, main, mismatched maintenance version, master proof for maintenance, removed ancestry | Reject line admission even with same SHA |
| N4 | Older green run with newest eligible red/running run; rerun pending/failed; mixed attempts | Reject; no successful-only filtering or fallback |
| N5 | Missing/failed/skipped/cancelled/timed-out build, artifact step, report upload, isolation or lane result | Reject incomplete proof; NO-SOURCE only where contract explicitly allows |
| N6 | Missing baseline/G4/G5 JUnit or coverage, missing artifact lane, malformed/duplicate inventory | Reject; partial diagnostics never form passing proof |
| N7 | Same SHA/different attempt or unrelated analysis; successful upload with pending/failed gate; missing/ambiguous join; insufficient read-only identity access | Slice-1 admission stops if binding unproven; later verifier rejects or bounded-waits then rejects; no upload/name/SHA heuristic |
| N8 | Missing/expired/deleted artifact, digest/file mismatch, archive traversal/symlink, expired freshness | Reject before side effects; no executable artifact consumption |
| N9 | After approval: artifact deleted, rerun/new run, failed Sonar, branch force change, receipt tampering | Protected recheck rejects; no silent rebinding |
| N10 | API 403/404/429/5xx, timeout, malformed JSON, duplicate identities or later-page conflicting run/artifact | Fail closed; bounded retries do not become bypasses; paginate completely |
| N11 | Version, authorization, receipt or documentation prerequisite fails individually | Zero protected sign/staging/upload/Portal markers; ancestry established for every A operation |
| N12 | Direct protected signing/staging/upload, protected tasks before aggregate, alternative aggregate, parallel failed authorization and `--continue` after failed prerequisite | No A operation escapes prerequisite or complete-entry guard |
| N13 | Passing root check alone, forged local boolean/property or unsupported receipt schema | Cannot authorize protected workflow publication; local guard rejects invalid/missing receipt |
| N14 | Local-looking publication redirected to protected destination/key; shared Sign task requested locally, protected or in mixed graph | A remains fail-closed including direct signing; no name/property exemption; legitimate nonprotected signing/preparation remains usable |

For N13, distinguish trusted workflow/API enforcement from local build defense: anyone able to rewrite the build can
rewrite its checks. No local JSON file confers environment approval or publisher credentials. Tests must prove the
protected workflow never reaches publication with a forged receipt, as well as local structural validation.

## Validation and delivery requirements

During implementation run focused proof/graph tests, affected-module tests, then required Groovy 3/4/5 lanes and
workflow/documentation checks at final tip. Validate YAML, lint shell/helpers as appropriate, and review the workflow
DAG and task ancestry. A dry-run alone proves selection, not failure containment. Tests use stubs; do not dispatch a
real release to prove a negative. Preserve clean-cache Portal/Maven/REL-2 behavior and independent consumer proof.
Hosted producer acceptance is separately authorized normal CI, never an implicit publication rehearsal. Reconcile
Sonar findings and review feedback before delivery. No speedup is claimed.

This planning package: compare retained investigation byte-for-byte, check local Markdown targets, `git diff --check`,
review changed-file scope and commit history, then commit locally. Groovy lanes are omitted under the documentation-only
exception in [testing guidance](../agents/testing.md); no executable/configuration inputs changed.

## Risks and confirmed decisions

Sonar feasibility is the first implementation admission gate. Open technical evidence: actual API joins/access and
rerun behavior, plus destination binding/shared-signing semantics in the configured Gradle plugins. These need
implementation evidence, not another architecture choice; inability to prove the Sonar join requires maintainer
integration adjudication before substantial work. Service retention can also outlast proof availability.
Latest-attempt policy intentionally makes reruns invalidate approval-bound proof. API snapshots cannot provide an atomic
lock over later service changes. The protected recheck narrows that race without claiming to eliminate it. Versioned
release binaries still require authorized local assembly/signing and public resolve-back.

Maintainer delegation on 2026-10-16 confirms ADR 0030's policy choices:

1. Retain source-proof payloads/receipts for 30 days and accept source proof for at most 30 days from attempt
   completion. Earlier GitHub/service expiry or deletion wins; no external evidence store.
2. Establish exact Sonar project/App/attempt-to-analysis correlation read-only during implementation. If existing
   metadata cannot prove it, stop for a maintainer integration decision; do not weaken matching or grant extra
   credentials. Actual numeric identities and metadata capability still require evidence during implementation.
3. Master-first; no `release/4.0.x` backport. `release/4.1.x` is the first intended maintenance branch if/when
   needed, receiving producer/verifier/operation guards together and requiring proof from that exact line.

No design policy choice remains open. Sonar integration escalation is conditional on inadequate metadata found
in implementation. Exact-SHA reuse and single-job topology remain settled. This decision record does not authorize
this worker to edit workflows/builds/settings, access secrets, publish or dispatch. No child worker is required.

Assigned delivery is the local ADR/plan/evidence package. Implementation, GitHub updates, push/PR, branch backport and
release execution are excluded. Hive reconciliation is requested on completion; the worker does not mark itself `(arch)`.
