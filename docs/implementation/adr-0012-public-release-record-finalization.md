# ADR 0012 public release-record ordering

Status: implemented plan for the 2026-08-07 amendment to [ADR 0012](../adr/0012-shared-prerelease-channel-policy.md)

Issues: [#488](https://github.com/klum-dsl/klum-ast/issues/488) and [#456](https://github.com/klum-dsl/klum-ast/issues/456)

## Decision and trust boundary

`release.yml` publishes an exact candidate/final only after the #456 pending documentation handoff. Once the protected
publication job exits, two unprivileged jobs prove availability: `release/plugin-consumer` resolves the three markers
from the Plugin Portal only, then `release/maven-consumer` polls Maven Central for the full Maven product every ten
minutes for at most two hours. Neither job has a protected environment or publishing, signing, Pages, or repository-write
authority.

After those checks succeed, the maintainer explicitly creates the annotated `v<version>` tag at the authorized SHA and a
matching non-draft GitHub release (`prerelease` for a candidate). This is intentionally not an environment gate inside a
running workflow: approvals remain approvals, and the separately visible release-record action cannot be forgotten while
a job is waiting.

`verify-public-release.yml` is the normal manual post-publication entry point. It accepts only stage, version, and SHA;
it first validates the existing annotated tag and exact release record, rejecting lightweight tags, wrong targets, drafts,
and wrong prerelease flags. Its credential-free clean-cache proof retains an immutable run/attempt identity and passes it
internally to the proof-gated #456 documentation promotion. The Pages writer remains the only protected documentation
credential boundary and receives no publishing or release-record credentials.

The exceptional `recover-public-release-record.yml` is read-only. It accepts a failed historical verification run only
when its proof, promotion, deployment, and immutable ledger evidence match exactly, then validates the manually created
annotated tag and release. It has no release-record environment and no repository-write authority.

## RC.14 recovery

For the prior finalizer failure, first create the exact annotated `v4.0.0-rc.14` tag at
`9bfd5100233149c83d30ef6027528f4fd60a76ec` and the matching non-draft prerelease. Then dispatch **Recover incomplete
public release record** with `candidate`, `4.0.0-rc.14`, that SHA, verification run `31175000628`, and proof attempt `1`.
The workflow verifies the retained successful proof and already-promoted documentation but does not mutate GitHub state.

## Validation

Run `./gradlew verifyVersionedDocumentationRenderer`, YAML parsing, shell syntax checks for workflow `run` blocks, and
`git diff --check`. The release-workflow changes do not alter Groovy runtime/build behavior, so documentation/workflow
checks are proportionate; the root `check` remains an optional final confidence gate.
