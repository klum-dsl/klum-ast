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
  `pending/<version>/<sha>/` path, and static-site-manifest SHA-256.
- A final also requires `docs/branding/final-approval.json` at that source SHA. The
  approval names and hashes the exact branding manifest and is retained in the
  site manifest. Candidate branding is intentionally insufficient.
- Authored Markdown is converted by pinned repository-local `commonmark-java` components into
  deterministic static HTML. Extensionless relative URLs, GitHub-compatible heading fragments,
  local CSS/images, six Javadoc trees, and every exact-tree output digest are captured in
  `site-manifest.json`; authored raw HTML is escaped and unsafe URL schemes are sanitized.
- CommonMark AST inspection verifies current `docs/user/` contains no authored presentation-HTML
  nodes. Tagged v3.0.1 contains only generic type tokens such as `<Child>` that CommonMark
  classifies as inline HTML; they are prose/code notation, not presentation markup, and are safely
  escaped. XML dependency examples remain fenced/indented code. Historical documentation is
  secondary, but the same escaping rule applies to every imported tag.
- The separately permissioned reusable workflow writes only the unlisted immutable
  pending path on `gh-pages`, reads its pushed commit back, uploads that exact ledger, and deploys
  it through the official protected Pages actions. It does not contain artifact credentials or
  invoke an artifact publication task. The release workflow requires and rechecks the site
  manifest, gh-pages commit, Pages run identity, and page URL before the credential-bearing step.
- The workflow fails closed unless `DOCUMENTATION_PAGES_READY=true` and root `.nojekyll` prove the
  disposable empty-site rehearsal has been removed and the permanent ledger/protection is ready.
  No rehearsal or Pages deployment was performed by this implementation work.
- If a valid identity reaches the Pages stage and then fails, the workflow writes a
  minimal immutable `pending-rejected/<version>/<sha>/rejection.json` record. It
  excludes logs, credentials, and telemetry. Invalid, off-master, or used-tag inputs
  fail before a Pages record is created.

## Local verification

`verifyVersionedDocumentationRenderer` covers pending chrome, candidate stage/version/SHA
validation, an off-master rejection, static HTML/link/asset/anchor fixtures, a JDK HTTP crawl under
`/klum-ast/`, and static workflow checks that the Pages stage is SHA-scoped, readiness-gated,
read-back verified, unable to publish artifacts, and required before artifact publication.
