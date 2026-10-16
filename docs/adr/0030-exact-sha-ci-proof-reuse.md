# Exact-SHA CI proof before protected publication

Date: 2026-10-16

Status: Accepted. Policy and design confirmed by maintainer delegation on 2026-10-16.

Implementation status: Design only. No workflow, build, protection, or release behavior changed.

Tracking issue: [#903](https://github.com/klum-dsl/klum-ast/issues/903) (remains open).

Implementation plan: [#903 proof-reuse implementation plan](../implementation/issue-903-ci-proof-reuse.md).

Evidence: [#903 investigation](../implementation/issue-903-release-verification-contract.md), retained verbatim
from local investigation commit `9ef9e975f6e8f6e652eea02c4800a4277aa6a924`. Its recommendation-awaiting-adoption
wording describes the investigation's historical state; this ADR records the subsequently accepted reuse direction.

Parent decisions: [ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0012](0012-shared-prerelease-channel-policy.md), [ADR 0013](0013-versioned-documentation-and-javadocs.md).
Related boundary: [ADR 0019](0019-published-showcase-consumer-verification.md), still Proposed in this checkout.

## Context

At base `96608b7c8c1657662f95e003b09d6e6d96cde777`, ordinary CI has one Ubuntu/JDK-17 `build` job.
It runs `check --scan sonar --info`, then the three annotation-propagation artifact tasks with
`--no-parallel --max-workers=2`. Protected publication depends only on root `:check`, which does not
select the subproject matrix. Investigation found 134 publication tasks versus 206 complete CI tasks;
these counts describe that revision, not a permanent acceptance constant. REL-1 has no exact-SHA CI lookup.
Some actual signing/staging/upload tasks also lack version and verification dependency ancestors.
Successful historical source CI exists; the finding is missing enforcement, not proof of an unqualified release.

## Decision

### Source proof and topology

Reuse trusted successful CI for the exact full source SHA; do not repeat the expensive matrix just because
publication is dispatched. Preserve the single CI job, both existing Gradle invocations and their scheduling
limits, Java 17, single Groovy-3 production product, independently compiled Groovy 3/4/5 test lanes,
isolation, artifact qualification, Javadocs/renderer, coverage, JUnit and Sonar. Lane splitting and performance
experiments remain separate under [#573](../implementation/evidence/issue-573-ci-performance-decision-2026-07-28.md).
No DSL/API usage example applies to this internal release boundary.

A read-only REL-1 preflight must finish before pending documentation or protected publication is eligible.
Require `contents: read`, `actions: read`, and `checks: read` only. It validates the requested stage/version,
full SHA, selected release line and ancestry, then selects and retains a source-proof identity. A green badge,
branch protection, environment approval, workflow title, or caller-provided JSON alone is insufficient.

### Trusted producer and attempt selection

The producer must be a same-repository `push` run of the configured numeric workflow ID and exact path
`.github/workflows/ci.yml`, on the selected release branch, with `head_sha` exactly equal to the input SHA.
Reject fork, PR/merge-ref, dispatch, tag and other-workflow proofs even if their trees or names match.
Validate repository ID/full name and head repository, workflow ID/path, event, branch and source SHA through
API metadata; bind the workflow file blob at that source to a reviewed proof-contract revision. A contract
revision explicitly lists required steps, task families, report producers and allowed action identities.
A workflow-file change needs reviewed contract admission; an unchanged display name cannot admit it.

Choose the newest eligible run by creation time with run ID as a deterministic tie-breaker, before filtering
on success. Require its current latest attempt to be completed successfully, its `build` job and every
required verification/report/upload step successful. Reject missing, skipped, cancelled, neutral, timed-out,
pending or failed outcomes; explicitly declared task-level NO-SOURCE cases are distinct from skipped steps.
Use attempt-specific job metadata and attempt-named immutable artifacts. Never combine jobs/checks/artifacts
from different attempts or fall back to an older green attempt/run when the newest is red or incomplete.
Paginate all run/job/check/artifact lists and reject ambiguous matches and API errors.

### Evidence and Sonar

A versioned producer manifest binds repository, source SHA, branch/event, workflow ID/path/blob, contract
revision, run ID, attempt, job and check IDs, toolchain, task/lane outcomes and an exhaustive report inventory.
Each report entry records relative path, producer task/lane, byte size and SHA-256. A trusted verifier reconciles
that inventory against the contract; self-declared completeness is not accepted. Retain baseline and G4/G5
JUnit, all three JaCoCo XML/HTML outputs, lane-isolation results, annotation-propagation G3/G4/G5 results,
and documentation/Javadoc verification summary. Expected empty test suites must be explicitly enumerated
by the contract, never inferred from missing XML. Retain diagnostics on failure but issue no passing proof.
The manifest's own artifact identity/digest is bound by the consumer receipt, avoiding a self-hash cycle.

Sonar scan upload success alone is insufficient. Require a completed successful quality-result check from
the approved Sonar GitHub App ID for this repository/project and SHA, and bind check ID, analysis/task ID,
revision and result to this producer attempt. The producer records the scanner task identity and the analysis
correlation; a same-SHA check from an unrelated analysis or a spoofed check name cannot qualify. Only G3
coverage feeds Sonar; G4/G5 remain retained compatibility evidence. If the available trusted integration
cannot expose a verifiable attempt-to-analysis binding without privileged credentials in the consumer,
implementation must stop for an explicit integration decision, not weaken correlation or grant release secrets.

The consumer receipt binds stage/version/source line/SHA to the selected run/attempt, producer manifest hash,
artifact IDs and service digests plus independently calculated file hashes, job/step/check identities and Sonar
analysis/result. Download only from the validated run's artifacts; reject expired/deleted/duplicate artifacts,
archive traversal, symlinks and hash/inventory discrepancies. Proof artifacts are data and never executed or
placed on a publication classpath. CI binaries are not published: protected publication builds/signs the exact
source with its authorized release version, retaining local version and documentation checks.

### Retention and freshness

Explicitly retain all source-proof payloads,
manifest and release receipts for 30 days, and accept source proof for at most 30 days from attempt completion.
The service's earlier expiry/deletion or any invalidation always wins; configuration is not evidence of availability.
Record completion/expiry timestamps and validate them at both gates. A shorter repository retention cap must
be resolved before admission. Persistent release records retain compact proof identities/hashes and the actual
expiry deadline under the release audit policy; these references do not make expired payloads reusable.
Do not introduce a new external evidence store in #903. Missing or expired proof requires fresh CI on the exact
SHA through a separately authorized path; no release dispatch, old success, ancestor proof or local bypass repairs it.

### Protected recheck and local operations

Both pending documentation and `publish` need the preflight. After environment approval and before any signing,
staging initialization, upload or Portal action, publication repeats read-only validation against the retained
receipt: exact checkout/cleanliness, live branch ancestry, trusted contract, latest eligible run/attempt, all
conclusions, Sonar correlation, artifact availability/hashes and freshness. It must reject superseding failed or
in-progress attempts/runs, deleted evidence or a changed receipt. A newer successful run also requires a new
preflight receipt; publication never silently rebinds an approved receipt. Keep remote version/tag availability
and pending-documentation handoff rechecks. Perform the proof recheck immediately before the publication command;
it is a point-in-time gate, not an atomic lock over subsequent GitHub changes. A new dispatch revalidates everything.

Actual Gradle signing, staging initialization, every Sonatype upload, close/release and Portal operation must
depend on successful local version/authorization, source-proof receipt validation, and root documentation checks.
Enforce the sole complete-product entry at operation tasks, including low-level upload tasks. A sibling aggregate
or ordering hint cannot satisfy this boundary. The local receipt is created by the verified protected workflow;
Gradle enforces its presence/identity and operation ordering, not an independent cryptographic authority over a
malicious local checkout. Protected environments remain the authority for credentials. Avoid cycles through
artifact generation; shared prerequisites run once. Direct or reordered requests and `--continue` must not bypass
failed dependencies. Do not transfer release credentials into CI or preflight.

### Branch admission and independent release gates

Admit `master` first after the corrected contract itself has passed trusted push CI. For a maintenance release,
require exactly `release/<major>.<minor>.x` matching the version and dispatch ref, source ancestry on that line,
and proof produced on that same line. `main`, generic `release/*`, feature-branch CI, an ancestor or master proof
for a maintenance SHA is not automatic admission. Backport producer, verifier and operation guards as one reviewed
maintenance reliability package; absence of the contract blocks release. Do not backport this package to
`release/4.0.x`. `release/4.1.x` is the first intended maintenance branch to receive the complete package if/when
needed, with producer, verifier and operation guards admitted together and proof produced on that exact line.
Do not create branches or change protection implicitly.

ADR 0012's independent RC/final authorization, immutable versions, burns, public propagation, manual annotated
tag/release record and REL-2 proof remain intact. ADR 0013's pending snapshot precedes artifact publication and its
public promotion follows public proof. CI reuse cannot replace candidate-coordinate or independent public-consumer
qualification. ADR 0019 candidate rebinding/release orchestration remains separately owned; historical Catwalk
4.0.1 success is not proof of a new candidate. This ADR does not decide or waive that deferred gate.

## Consequences and rejected alternatives

The release waits for complete source evidence without buying another matrix run. Evidence expiry, unavailable
APIs or a newer failed attempt block publication intentionally. Existing historical CI without the new manifest
cannot qualify automatically. Proof identifies source verification; it does not claim versioned release binaries
are byte-identical to CI outputs or make RC evidence interchangeable with final publication.

Reject ancestor/latest-branch or name-only checks, mixed attempts, successful scan upload alone, root `:check` as
a matrix substitute, silent rerun/fallback and workflow splitting as a fix for the missing dependency. A complete
credential-free matrix rerun could be safe but is outside the accepted reuse direction. No public/generated API,
DSL lifecycle, serialization, ownership or Groovy compatibility contract changes.

## Confirmed maintainer decisions

Maintainer delegation on 2026-10-16 confirms:

1. The 30-day source-proof payload/receipt retention and freshness window is approved. Earlier GitHub/service
   expiry or deletion wins; no external evidence store is introduced.
2. Exact Sonar project/App/attempt-to-analysis correlation must be established read-only during implementation.
   If existing metadata cannot prove it, stop for a maintainer integration decision. Do not weaken matching or
   grant extra credentials. Numeric identities remain implementation discovery, not guessed policy values.
3. Adopt master-first. Do not backport to `release/4.0.x`; `release/4.1.x` is the first intended maintenance
   branch for the complete package if/when needed. Producer, verifier and operation guards must travel together,
   and release proof must come from that exact line.

No policy decision remains open for this design. A Sonar integration decision is conditional on implementation
finding inadequate metadata. Exact-SHA reuse and current CI topology remain settled. ADR 0019 candidate
qualification remains a separate existing decision surface. Implementation has not started.
