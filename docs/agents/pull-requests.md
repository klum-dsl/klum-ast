# Pull requests and release-facing documentation

Use this checklist for any pull request that changes public behavior, compatibility, or generated APIs.

## Pull request scope and issue links

- Use closing keywords only for issues whose accepted behavior is fully delivered by the pull request.
- Reference related issues that remain intentionally deferred, and say what the pull request does not implement.
- Keep the pull request summary, compatibility breaks, and test evidence current as commits are added.

## Quality checks

- Review changed source against `docs/agents/coding-style.md`; unnecessary fully qualified names should be fixed unless a
  documented exception applies.
- Inspect all required CI checks, including SonarCloud, against the current pull request revision.
- Fix reliability and security findings before handoff. Address maintainability findings or retain them only with a localized suppression and a concrete reason.
- Do not use broad suppressions to hide unrelated findings. A suppression is documentation of an intentional exception and must explain the invariant or compatibility constraint.
- Follow `docs/agents/testing.md`: use Groovy 3 for focused iterations and run the Groovy 4 and 5 compatibility lanes before final handoff.

## Review feedback follow-up

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
- Put migration guides in `wiki/`. A substantial migration may use a dedicated page, but it must be linked from `wiki/Migration.md`.
- Whenever a wiki page is added, renamed, or removed, update `wiki/_Sidebar.md` so the page remains discoverable.
- Keep provisional policies explicitly labelled and linked to the issue that will finalize them.
