# ADR 0012 public release-record finalization

Status: implemented plan for the 2026-08-07 amendment to [ADR 0012](../adr/0012-shared-prerelease-channel-policy.md)

Issues: [#488](https://github.com/klum-dsl/klum-ast/issues/488) and [#456](https://github.com/klum-dsl/klum-ast/issues/456)

## Confirmed behavior and conflict

`release.yml` publishes an exact candidate/final only after the #456 pending documentation handoff. `verify-public-release.yml` then provides credential-free Maven Central and Plugin Portal resolve-back evidence. The #672 workflow `promote-public-documentation.yml` already validates that proof artifact and its matching pending ledger identity before it creates the public exact documentation tree and eligible aliases.

The prior `RELEASING.md` required the independent external-consumer check before manual tag/release creation. That left the consumer without the normal release surface it is expected to encounter and conflicted with ADR 0012's immutable tag requirement. The ADR amendment replaces that ordering: public proof, protected record/document finalization, then the independent consumer check.

## Decision and trust boundary

`finalize-publicly-proven-release-record.yml` is the sole protected manual release-record path. It accepts only the exact stage/version/source SHA and the immutable public-proof run/attempt. It calls the existing proof-gated documentation promotion workflow rather than duplicating or bypassing #672. The finalizer then rechecks the immutable pending manifest, public exact manifest, promotion record, proof identity, and stage-eligible aliases before it creates or verifies the annotated `v<version>` tag and matching GitHub release.

The finalizer uses a new protected `release-record` environment with human approval and `contents: write`; it has no artifact, signing, registry, or Pages-writer credentials. `release.yml` remains `contents: read`, so publication cannot create tags or release records. Source selection always checks out and verifies the supplied full SHA, even when workflow code on `master` has advanced.

Recovery is intentionally narrow. A rerun may continue after documentation, tag, or release creation only when each already-existing record exactly binds the same stage/version/SHA/proof identity and manifest digests. A release without the tag, a lightweight tag, a tag targeting another commit, a wrong prerelease flag, a mismatched promotion, or a wrong alias fails closed. Pending/rejected ledgers and the immutable-version burn rule remain unchanged.

## Thin implementation slices

1. Make #672 documentation promotion reusable while retaining explicit manual dispatch, and expose its verified identity/ledger output. Acceptance: static workflow checks prove the finalizer invokes the existing workflow and promotion still validates the exact proof artifact plus pending evidence.
2. Add the separately protected release-record finalizer. Acceptance: static workflow checks prove it has no publication credentials, is `master`-only, validates exact immutable documentation evidence, creates an annotated tag at the selected SHA, and rejects mismatched tag/release states.
3. Amend ADR 0012, ADR 0013's integration record, `RELEASING.md`, and `CHANGES.md`. Acceptance: the documented sequence is public proof → finalization → external consumer, and the former later-tag rule is named as superseded.
4. Capture the read-only 4.0 RC backfill matrix. Acceptance: every public 4.0 RC is identified from remote evidence, missing or mismatched proof is a blocker requiring exact re-proof, and no remote tag, release, alias, or workflow dispatch is created.

## Commit boundaries and validation

- `Reuse proof-gated promotion for release-record finalization`: workflow-call seam plus focused static acceptance assertions.
- `Document public release-record finalization`: governing ADR/runbook/implementation and sanitized backfill evidence.

Run `./gradlew verifyVersionedDocumentationRenderer`, `git diff --check`, and a focused workflow review. The release-workflow changes do not alter Groovy runtime/build behavior, so documentation/workflow checks are proportionate; the root `check` remains an optional final confidence gate.

## RC.14 operator sequence

1. Wait for the protected publication and its credential-free public proof to succeed for the exact approved identity.
2. Dispatch **Finalize publicly proven release record** from `master` with the exact stage/version/SHA and public-proof run/attempt; approve both the documentation writer/deployment gates and the separate `release-record` gate.
3. Verify the finalizer reports the exact documentation promotion, annotated tag, and prerelease record before dispatching independent external-consumer validation.
4. Preserve the finalizer and consumer evidence. A failure at any stage is an incident; do not retry a used version in place or move stable aliases for a candidate.

## Risks and non-goals

This change does not configure GitHub environments, create a release, tag, alias, or backfill historical records. Maintainers must configure `release-record` with required human approval and no administrator bypass, keeping its contents-write scope separate from `release-candidate`, `final-release`, and `documentation-pages-writer`. Historical reconciliation is a separately approved operation, not a normal finalizer rerun.
