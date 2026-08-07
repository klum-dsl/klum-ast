# ADR 0012 public release-record finalization

Status: implemented plan for the 2026-08-07 amendment to [ADR 0012](../adr/0012-shared-prerelease-channel-policy.md)

Issues: [#488](https://github.com/klum-dsl/klum-ast/issues/488) and [#456](https://github.com/klum-dsl/klum-ast/issues/456)

## Confirmed behavior and conflict

`release.yml` publishes an exact candidate/final only after the #456 pending documentation handoff. One manual dispatch of `verify-public-release.yml` then provides credential-free Maven Central and Plugin Portal resolve-back evidence. The #672 workflow `promote-public-documentation.yml` validates that retained proof artifact and its matching pending ledger identity before it creates the public exact documentation tree and eligible aliases.

The prior `RELEASING.md` required the independent external-consumer check before manual tag/release creation. That left the consumer without the normal release surface it is expected to encounter and conflicted with ADR 0012's immutable tag requirement. The ADR amendment replaces that ordering: public proof, protected record/document finalization, then the independent consumer check.

## Decision and trust boundary

`verify-public-release.yml` is the sole normal manual post-publication entry point. It accepts only the exact stage/version/source SHA. Its credential-free proof job creates and retains the immutable public-proof run/attempt identity, then passes it internally to the existing proof-gated documentation-promotion workflow rather than duplicating or bypassing #672. The downstream protected `release-record` job rechecks the immutable pending manifest, public exact manifest, promotion record, proof identity, and stage-eligible aliases before it creates or verifies the annotated `v<version>` tag and matching GitHub release.

The finalizer uses a new protected `release-record` environment with human approval and `contents: write`; it has no artifact, signing, registry, or Pages-writer credentials. `release.yml` remains `contents: read`, so publication cannot create tags or release records. Source selection always checks out and verifies the supplied full SHA, even when workflow code on `master` has advanced.

Recovery is intentionally narrow. A transient retry of the same parent run may continue after documentation, tag, or release creation only when GitHub retains the completed `resolve` job and its artifact, and every already-existing record exactly binds the same stage/version/SHA/proof identity and manifest digests. Promotion validates that exact completed job attempt rather than waiting for the parent workflow to conclude, so a retained proof can drive a failed downstream retry. GitHub pins failed-job reusable workflows to their original revision; a caller or called-workflow correction therefore cannot be validated by that retry. After the correction merges, one new input-only Verify public release dispatch is permitted only if no public exact tree, promotion record, tag, or release exists; it creates a fresh proof identity for a new unrecorded promotion. If the parent finalizer itself needs that correction after the immutable public promotion completed, the exceptional `recover-public-release-record.yml` accepts only the exact failed parent run and proof attempt, revalidates its proof/promotion/deployment evidence, and then uses only the existing `release-record` approval to create the tag and release. It has no Pages or artifact credentials. A release without the tag, a lightweight tag, a tag targeting another commit, a wrong prerelease flag, a mismatched promotion, or a wrong alias fails closed. A newly dispatched or re-run proof can never repair an earlier partial path. Pending/rejected ledgers and the immutable-version burn rule remain unchanged.

## Thin implementation slices

1. Make #672 documentation promotion callable only from unified verification, and expose its verified identity/ledger output. Acceptance: static workflow checks prove the parent passes the completed proof identity internally and promotion still validates the exact proof artifact plus pending evidence.
2. Move the separately protected release-record finalizer downstream into unified verification. Acceptance: static workflow checks prove its job has no publication credentials, is `master`-only, validates exact immutable documentation evidence, creates an annotated tag at the selected SHA, and rejects mismatched tag/release states.
3. Amend ADR 0012, ADR 0013's integration record, `RELEASING.md`, and `CHANGES.md`. Acceptance: the documented sequence is public proof → finalization → external consumer, and the former later-tag rule is named as superseded.
4. Capture the read-only 4.0 RC backfill matrix. Acceptance: every public 4.0 RC is identified from remote evidence, missing or mismatched proof is a blocker requiring exact re-proof, and no remote tag, release, alias, or workflow dispatch is created.

## Commit boundaries and validation

- `Unify post-publication verification`: parent-owned proof identity, downstream workflow call/job, and focused static acceptance assertions.
- `Document unified public-release verification`: governing ADR/runbook/implementation and sanitized backfill evidence.

Run `./gradlew verifyVersionedDocumentationRenderer`, `git diff --check`, and a focused workflow review. The release-workflow changes do not alter Groovy runtime/build behavior, so documentation/workflow checks are proportionate; the root `check` remains an optional final confidence gate.

## RC.14 operator sequence

1. Wait for the protected publication to make the exact product publicly resolvable.
2. Dispatch **Verify public release** once from `master`, entering only exact stage/version/SHA; approve the documentation writer/deployment gates and then the separate `release-record` gate when reached.
3. Verify the unified run reports its credential-free proof, exact documentation promotion, annotated tag, and prerelease record before dispatching independent external-consumer validation.
4. Preserve the parent run and consumer evidence. A transient downstream failure retries only the same proof identity. A merged workflow correction before any public promotion/tag/release record requires a new input-only dispatch and its fresh internal proof identity; never copy a proof value or repair a used record in place. If only the parent finalizer required that correction after public promotion, use the exceptional recovery workflow with the retained parent run/attempt and the separate `release-record` approval.

## Risks and non-goals

This change does not configure GitHub environments, create a release, tag, alias, or backfill historical records. Maintainers must configure `release-record` with required human approval and no administrator bypass, keeping its contents-write scope separate from `release-candidate`, `final-release`, and `documentation-pages-writer`. Historical reconciliation is a separately approved operation, not a normal finalizer rerun.
