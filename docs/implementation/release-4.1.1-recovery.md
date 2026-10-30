# 4.1.1 pre-publication recovery exception

Date: 2026-10-30

Related: #522 (maintenance lines), #488 (release process).

## Failure and retained evidence

[REL-1 run 41062565238](https://github.com/klum-dsl/klum-ast/actions/runs/41062565238) attempted final `4.1.1` from
`release/4.1.x` at `076705063475572abf2852c7e6f0b2632238464b`. Its ordinary push CI, trusted SonarCloud, and REL-1
preflight gates passed. Rendering and site verification completed, but `preparePendingDocumentationStage` rejected
the source because its Gradle ancestry guard hardcoded `origin/master` instead of the allowed maintenance line.
The artifact publication and both public availability jobs were skipped.

The immutable rejection remains at
[`pending-rejected/4.1.1/076705063475572abf2852c7e6f0b2632238464b/rejection.json`](https://github.com/klum-dsl/klum-ast/blob/gh-pages/pending-rejected/4.1.1/076705063475572abf2852c7e6f0b2632238464b/rejection.json).
This recovery must not alter that record or retry the rejected source identity.

## Explicit maintainer decision

The maintainer explicitly approved fixing and validating the release tooling and preparing a fresh final `4.1.1`
attempt from the corrected release-branch SHA, retaining the rejected identity. This is a one-time exception to
`RELEASING.md`'s next-version rule for rejected pending documentation and the substantive release-configuration
requalification rule in ADR 0012. The earlier authorization to release directly without an RC remains in effect.

The changed validation claim is that final `4.1.1` now includes a correction to its pre-publication source-line gate;
the successful checks of the rejected source do not qualify that correction. No product artifact was published by
the failed run, so this exception does not replace or repair published coordinates, tags, or release records.

## Compensating evidence and execution limits

- Exercise the real Gradle identity validator with a maintenance-only commit absent from master, for both candidate
  and final versions; retain master acceptance and rejection controls for wrong lines, missing refs, unrelated commits,
  and dirty checkouts. Check the workflow-to-Gradle branch handoff as well.
- Run the applicable local build and documentation checks, then require ordinary push CI and trusted SonarCloud for
  the exact corrected `release/4.1.x` commit. Do not reuse the rejected source's CI or upload SonarCloud locally.
- Dispatch a fresh REL-1 run with `stage=final`, `version=4.1.1`, and that corrected full SHA from `release/4.1.x`.
  Keep remote tag/release-identity absence checks and every protected approval in force. Its pending snapshot has a
  new source-SHA path; the original rejection remains immutable and unlisted.
- Stop at a required protected approval or any new release-gate failure. Do not overwrite retained evidence, create an
  RC, upmerge master, close #989, or dispatch REL-2 in this recovery task. Hive owns post-delivery reconciliation.

The exception applies only to this pre-publication tooling failure. General immutability and next-version rules remain
unchanged, and this record alone does not claim that public delivery succeeded.

## Local qualification

On Java 17, the focused identity-gate suite reproduced the maintenance-only ancestry failure before the correction
and passed all 14 cases afterwards. The full root `check` passed, including Groovy 3/4/5, documentation-renderer,
workflow-contract, and build checks. The separate buildSrc test suite also passed. Local verification did not invoke
SonarCloud or any publication task; fresh ordinary CI and protected REL-1 gates remain required for this source commit.
