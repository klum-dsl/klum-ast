## Agent skills

### Task names

Each agent should rename its own Codex task when the purpose becomes materially more precise, using a concise title that describes the specific outcome. Use a status prefix when work reaches one of these handoff states:

- `(ready:commit)` — changes and validation are complete, but the commit remains.
- `(ready:PR)` — commits are ready, but pushing or creating the pull request remains.
- `(done)` — the requested outcome is complete with no repository step pending.
- `(done:PR)` — the completed work has been published as a pull request.
- `(done:merged)` — use only when the task explicitly includes shepherding the pull request through merge.
- `(blocked)` — progress requires external input or an external state change.

Active work needs no status prefix.

### Issue tracker

Issues and PRDs for this repo live in GitHub Issues (uses the `gh` CLI). External PRs are not treated as a request surface. See `docs/agents/issue-tracker.md`.

### Short-term backlog

Keep worthwhile side work visible without losing the current objective. Side work and agent-configuration changes normally
belong in separate user-visible tasks. See `docs/agents/short-term-backlog.md` for task, sidetrack, and baseline-reflection
rules.

### Triage labels

This repo uses the canonical triage label vocabulary. See `docs/agents/triage-labels.md` for the mapping.

### Feature triage

New feature issues should record a primary use case, need horizon, and workaround viability so priority and minimum scope
follow an actual user problem. An optional secondary angle must not silently broaden the feature. Missing classification is
not a triage blocker, already-triaged issues need no retroactive pass, and complexity-free mini-features may be treated as
quick wins. See `docs/agents/feature-triage.md`.

### Domain docs

This repository uses a single-context layout: one `CONTEXT.md` at the repo root and `docs/adr/` for ADRs. See `docs/agents/domain.md` for the consumption rules.

### Coding style

Import referenced Java and Groovy types and use their simple names. Fully qualified names in source are reserved for genuine
name conflicts or another documented technical necessity. See `docs/agents/coding-style.md`.

### Testing

Groovy 3 is the baseline test lane (`test`). Groovy 4 and Groovy 5 compatibility use `groovy4Tests` and `groovy5Tests`; run them when a version difference is expected and at the end of a change, rather than on every focused iteration. Every ignored, conditionally ignored, or pending test must state an actionable reason. See `docs/agents/testing.md`.
Every newly added test must carry its driving issue number in `@Issue`; a class-level annotation is sufficient while all
tests in that class originate from the same issue. Add or amend `@Issue` on an existing test only when a change to it is
significant. Every new user-visible DSL feature also needs a documentary test marked with `@Tag("documentary")` and linked
to its documentation through `@See`. Name new executable test classes with the `Test` suffix; use
`<Theme>DocumentaryTest` for dedicated documentary classes. Existing `*Spec` classes need not be renamed. See
`docs/agents/testing.md`.

### Feature discussion examples

During grilling and implementation, usually present a compact usage example in the conversation so the feature's syntax
and feel can be evaluated early. For DSL changes, use only Groovy and show the relevant Schema or Model syntax. For client
APIs, show Java first and Groovy second. Evolve the example into the documentary test and user documentation once the
feature direction is settled. See `docs/agents/testing.md`.

### Issue implementation commits

Implement issues on a new, dedicated issue branch using small, reasoned commits. Agents may create commits there without asking. Review and, when necessary, rewrite the local commit sequence before first publication or review; preserve reviewed commits and add review fixes as follow-up commits. See `docs/agents/commits.md`.

### Pull requests and releases

Workers may push a dedicated issue branch and open a draft pull request without additional permission when the change is
small, its intent is settled, and the required review, tests, and documentation are complete. Complex or unresolved work
still stops at `(ready:PR)`. See `docs/agents/pull-requests.md` for the publication boundary.

User-facing documentation is maintained in `wiki/`. Pull requests with user-visible changes must keep the relevant wiki pages, migration navigation, `CHANGES.md`, linked issues, and SonarCloud findings in sync. See `docs/agents/pull-requests.md`.
When reacting to pull-request review feedback, post one consolidated follow-up comment that states what was addressed, what
was intentionally left unchanged and why, and which validation supports the result.
