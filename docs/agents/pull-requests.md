# Pull requests and release-facing documentation

Use this checklist for any pull request that changes public behavior, compatibility, or generated APIs.

## Worker draft-pull-request authorization

A worker implementing an assigned change on a dedicated issue branch may push that branch and open a draft pull request
without asking for additional permission when all of the following are true:

- The change is small and localized, and the implementation follows an established repository pattern.
- The issue or other accepted specification settles the intended behavior; no product or maintainer decision remains.
- The work does not require an unresolved cross-cutting architecture, compatibility, public or generated API, migration,
  or release-policy decision. A localized user-visible fix may still qualify when its expected behavior is already settled.
- Local review, commit-history review, applicable tests, and required documentation are complete, and any validation gap
  is stated accurately in the draft pull request.
- The branch and pull request contain only the assigned work and preserved pre-existing changes are excluded.

If any condition is uncertain or unmet, stop at `(ready:PR)` and ask the maintainer to decide the publication boundary.
This authorization covers pushing the dedicated branch and creating a draft pull request only. It does not authorize
marking the pull request ready for review, merging it, or expanding the assigned scope.

## Pull request scope and issue links

- Use closing keywords only for issues whose accepted behavior is fully delivered by the pull request.
- Treat GitHub closing keywords as mechanical syntax, not prose. In pull-request titles and bodies, never place `close`,
  `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves`, or `resolved` before an issue reference unless the
  pull request is intended to close that issue automatically. Negation, quotation, code formatting, and explanatory
  context do not make the pattern safe. For non-closing relationships, use neutral wording such as `Related: #123`,
  `Issue #123 remains open`, or `This pull request leaves the issue state unchanged`.
- Reference related issues that remain intentionally deferred, and say what the pull request does not implement.
- Keep the pull request summary, compatibility breaks, and test evidence current as commits are added.

## Quality checks

- Review changed source against `docs/agents/coding-style.md`; unnecessary fully qualified names should be fixed unless a
  documented exception applies.
- Inspect all required CI checks, including SonarCloud, against the current pull request revision.
- Fix reliability and security findings before handoff. Address maintainability findings or retain them only with a localized suppression and a concrete reason.
- Do not use broad suppressions to hide unrelated findings. A suppression is documentation of an intentional exception and must explain the invariant or compatibility constraint.
- Follow `docs/agents/testing.md`: use Groovy 3 for focused iterations and run the Groovy 4 and 5 compatibility lanes
  before final handoff. Documentation-only pull requests that cannot affect compilation, tests, generated output, or
  runtime behavior may omit the Groovy lanes when the applicable documentation and diff checks are reported instead.
- Verify that every new test carries its driving `@Issue` number, using a class-level annotation while one issue owns the
  complete specification. Add or amend `@Issue` on changed existing tests only for significant behavioral changes. A new
  user-visible DSL feature must also have a documentary test marked with `@Tag("documentary")` and linked to its
  documentation through `@See`. The feature issue, test, and documentation must reference one another as defined in
  `docs/agents/testing.md`.
- Verify that new executable test classes use the `Test` suffix and that dedicated documentary classes use
  `<Theme>DocumentaryTest`. Existing `*Spec` classes need not be renamed unless a scoped rename improves the change.

## Review feedback follow-up

- Preserve the commits that formed the reviewed revision. Address feedback with additive follow-up commits according to
  `docs/agents/commits.md`; do not amend or otherwise rewrite pre-review commits unless the maintainer explicitly asks.
- A request to address pull-request review feedback also authorizes one consolidated follow-up comment on that pull
  request after the resulting changes have been pushed. Do not wait for a separate request to post it.
- Account for every review observation: identify addressed items and the relevant change or commit; identify intentionally
  unchanged items and explain the scope, correctness, or design reason.
- Distinguish positive or informational observations from requested changes, and record why they require no action.
- Include the focused tests, compatibility lanes, CI checks, and static-analysis results that support the final state. Report
  pending or failing checks accurately rather than implying success.
- Keep the follow-up consolidated to avoid comment noise. This authorization does not include resolving review threads,
  dismissing reviews, or submitting a review unless the user explicitly requests those actions.

## Changelog and migration documentation

- Add user-visible features, fixes, deprecations, and compatibility breaks to the next release section in `CHANGES.md`.
- The canonical user documentation lives in `wiki/`, not under `docs/`. Update every affected feature page as part of the behavioral change.
- Demonstrate new user-visible DSL features with concise examples aligned with their documentary tests, and identify the
  corresponding documentary test file and feature method on the affected wiki page.
- Put migration guides in `wiki/`. A substantial migration may use a dedicated page, but it must be linked from `wiki/Migration.md`.
- Whenever a wiki page is added, renamed, or removed, update `wiki/_Sidebar.md` so the page remains discoverable.
- Keep provisional policies explicitly labelled and linked to the issue that will finalize them.
