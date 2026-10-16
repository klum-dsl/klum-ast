# Exact-SHA CI verification before protected publication

Date: 2026-10-16

Status: Accepted, revised by the canonical maintainer decision in [#903](https://github.com/klum-dsl/klum-ast/issues/903).
Implementation status: REL-A implemented locally pending review/delivery; REL-B remains pending.
See the [REL-A evidence record](../implementation/issue-903-rel-a-evidence.md). No release or protection state was changed.

Plan: [#903 implementation plan](../implementation/issue-903-ci-proof-reuse.md).
Historical evidence: [original investigation](../implementation/issue-903-release-verification-contract.md)
and [Sonar feasibility report](../implementation/issue-903-sonar-feasibility.md), retained unchanged.

## Context and supersession

At investigated baseline `96608b7c8c1657662f95e003b09d6e6d96cde777`, ordinary CI has one Ubuntu/JDK-17
`build` job. It runs `check --scan sonar --info`, then the three annotation-propagation artifact tasks with
`--no-parallel --max-workers=2`. Protected publication selects root `:check`, which omits portions of the
subproject matrix, and REL-1 does not verify exact-SHA CI. Successful historical CI exists; this finding
concerns missing enforcement and is not evidence of an unqualified release.

The previous artifact-backed attestation, custom receipt/manifest, exhaustive report inventory, retention system,
and Sonar CE/task/analysis correlation design is **superseded for disproportionate complexity**. The historical
reports describe the investigation and earlier requirements, not current implementation obligations. In particular,
the Sonar feasibility no-go concerns the rejected attempt-to-analysis join and does not block this decision.

## Decision

### Minimum sufficient GitHub gate

Use existing GitHub Actions, Checks and protected environments as the authoritative systems. A read-only REL-1
preflight must pass before pending documentation or protected approval/publication becomes eligible. Use only
`contents: read`, `actions: read` and `checks: read`; no Sonar or publication credentials in that gate.

Verify repository numeric ID/full name and head repository, the exact full source SHA, intended source branch
and live ancestry, and the approved numeric CI workflow ID with exact `.github/workflows/ci.yml` path. Eligible
source runs are same-repository `push` runs on that exact line and SHA. Reject fork/PR/merge-ref/tag/dispatch or
other-workflow runs, SHA prefixes, ancestor proofs and equivalent trees. Workflow title alone is insufficient.

Paginate all relevant GitHub run/job/check lists. Select the newest eligible run by creation time and then run ID
before filtering on success. Require that run's current latest attempt to be completed successfully. Inspect
attempt-specific jobs and a small stable reviewed contract of required job/step identities and successful results.
Do not combine attempts, fall back to older green runs/attempts, or accept missing/skipped/neutral/cancelled/
timed-out/pending/failed required jobs or steps. The failure-only diagnostic upload is not a required passing step.

The reviewed contract covers the existing `build` job's complete Gradle verification invocation, explicit annotation
artifact qualification, JUnit report and compatibility-evidence upload. Keep action identities and step identities
reviewable against the checked-in workflow. Names alone do not establish workflow trust. A changed required job/step
contract needs review before admission; do not introduce a workflow-blob attestation registry or enumerate every
Gradle task/report in a second manifest. Normal CI success remains the evidence that its reviewed verification ran.

Require a completed successful SonarCloud GitHub App check for the exact full SHA from the approved numeric App
identity. Establish approved repository/workflow/App IDs from actual GitHub metadata and review them explicitly;
never guess IDs or accept check display names as identity. Reject missing, ambiguous, pending or unsuccessful
required check evidence. A successful trusted exact-SHA check is sufficient: correlation to the selected CI attempt
or a particular Sonar analysis is an **explicitly accepted residual risk**. Do not query Sonar CE/analysis APIs,
create a Sonar adapter, or infer correlation from timestamps, branch dashboards or check names.

API errors, malformed responses, missing fields, conflicting/duplicate identities, incomplete pagination and
unavailable evidence close the gate. Bounded transient retries are allowed; exhausting them cannot cause a weaker
fallback. No automatic CI/release dispatch repairs missing evidence.

### Recheck and unchanged CI topology

Repeat essential GitHub checks after protected-environment approval, immediately before any protected publication
operation. Revalidate exact clean checkout/source identity, live intended-line ancestry, approved workflow, newest
eligible run/latest attempt, required job/step outcomes and trusted exact-SHA Sonar check. Compare the selected
run/attempt/check identities with the successful preflight outputs; changed evidence requires a fresh preflight
and approval flow, including a newer successful run. Plain workflow outputs suffice for these identifiers; no
custom receipt, artifact attestation, manifest, report inventory, retention/freshness subsystem or external store.

Preserve the current single CI job, both Gradle invocations, worker limits, Java 17, single Groovy-3 production
product, independently compiled Groovy 3/4/5 lanes, isolation, all three coverage outputs, artifact qualification,
JUnit, Javadocs/renderer and Sonar. Do not rerun or split the matrix for release dispatch. Performance work remains
separate under [#573](../implementation/evidence/issue-573-ci-performance-decision-2026-07-28.md).

Protected environments remain the credential/approval authority. Keep local version/documentation and remote
version/tag availability checks plus the pending-documentation handoff. Rechecking is a point-in-time safeguard,
not an atomic lock over later GitHub changes. No separate 30-day proof policy remains; unavailable GitHub evidence
fails the gate regardless of age. No DSL/API usage example applies to this internal release boundary.

### Gradle protected-operation dependencies

Use plain Gradle successful dependency ancestry at actual protected operations: protected-key signing, Sonatype
staging initialization, Maven uploads to protected destinations, staging close/release and production Plugin Portal
publication. Require local authorization, version and documentation prerequisites before each action. Preserve the
complete-product entry boundary. REL-1 owns GitHub verification and protected credential admission; Gradle owns
local operation prerequisites. A local boolean or file cannot replace GitHub/environment authority.

Direct low-level invocation, alternative aggregates, reordered requests, parallel execution and `--continue` must
not bypass these dependencies. Classify by actual destination and key use: redirected local-looking publication
and mixed local/protected graphs cannot exempt protected operations. Maven Local, disposable TestKit/local fixture
publication, nonprotected fixture signing, compilation, JAR/POM/Javadoc generation and ordinary verification remain
usable without GitHub proof. Shared signing tasks require inspection of actual key/destination use rather than a
blanket guard on every Sign task. Prove failure containment with stubbed operations; dry-runs alone are insufficient.

### Release placement and independent gates

Master first. No `release/4.0.x` backport. A future `release/4.1.x` line requires a separate reviewed decision for
the complete correction. Maintenance admission must require exact version/dispatch line and proof from that line;
master proof does not admit a maintenance SHA. Do not create maintenance branches or change protection implicitly.

[ADR 0012](0012-shared-prerelease-channel-policy.md) RC/final authorization, immutable versions, burns, public
resolve-back and REL-2 remain intact. [ADR 0013](0013-versioned-documentation-and-javadocs.md) pending documentation
precedes publication and promotion follows public proof. [ADR 0019](0019-published-showcase-consumer-verification.md)
independent consumer qualification retains separate ownership. Exact-SHA CI cannot replace candidate/public proof.

## Mandatory overengineering review

Actions already owns repository/workflow/run/attempt identity and execution results. Checks already identifies the
Sonar App and source SHA. Protected environments already own approval and credential admission. These systems supply
the minimum sufficient authority directly; reproducing them in artifact attestations, custom receipts, manifests,
report inventories, retention stores or a separate Sonar integration adds implementation and review burden without
an accepted need. The deliberate same-SHA Sonar residual risk removes the unavailable analysis-correlation join.

Gradle already provides successful dependency execution and failure propagation. Plain dependencies at actual
protected operations express the local boundary without a release framework, publishing hierarchy or task-name
bypass. Keep the GitHub helper narrow and separately fixture-testable; no generic evidence/adapter framework.
Implement only REL-A and REL-B unless a demonstrated dependency requires another slice and maintainer review.
