# #456 VD-5 protected pending Pages-stage evidence

This is the sanitized, durable local record for the VD-5 implementation slice.
It records public contract facts and local validation scope only; it excludes
prompts, credentials, raw command output, host paths, and inferred telemetry.

## External contract evidence

- [Issue #456](https://github.com/klum-dsl/klum-ast/issues/456) requires immutable
  documentation/Javadoc snapshots and deliberate release integration.
- [ADR 0013](../../adr/0013-versioned-documentation-and-javadocs.md) assigns #456 the
  independently validated, unlisted pending Pages snapshot before #488 artifact
  publication, while #488 retains authorization, artifact publication, public proof,
  release/tag, and recovery ownership.
- [#488](https://github.com/klum-dsl/klum-ast/issues/488) supplies the protected
  release workflow that consumes this handoff; this slice does not add publication
  credentials or artifact publication behavior.

## VD-5 evidence boundary

- `preparePendingDocumentationStage` accepts only `candidate`/`final`, an exact
  matching version, a lowercase full SHA on `origin/master`, and the Nebula version
  recomputed for the requested release task. It renders only that checked-out SHA.
- The renderer has a distinct `pending` status with explicit non-public chrome. Its
  handoff is limited to `stage`, `version`, `sha`, immutable
  `pending/<version>/<sha>/` path, and source-manifest SHA-256.
- A final also requires `docs/branding/final-approval.json` at that source SHA. The
  approval names and hashes the exact branding manifest and is retained in the
  source manifest. Candidate branding is intentionally insufficient.
- The separately permissioned reusable workflow writes only the unlisted immutable
  pending path on `gh-pages`; it does not contain artifact credentials or invoke an
  artifact publication task. The release workflow requires and rechecks its handoff
  before the credential-bearing artifact step.
- If a valid identity reaches the Pages stage and then fails, the workflow writes a
  minimal immutable `pending-rejected/<version>/<sha>/rejection.json` record. It
  excludes logs, credentials, and telemetry. Invalid, off-master, or used-tag inputs
  fail before a Pages record is created.

## Local verification

`verifyVersionedDocumentationRenderer` covers pending chrome, candidate
stage/version/SHA validation, an off-master rejection, and static workflow checks
that the Pages stage is SHA-scoped, cannot publish artifacts, and is required before
the artifact workflow can proceed.
