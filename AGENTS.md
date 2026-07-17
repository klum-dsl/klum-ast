## Agent skills

### Issue tracker

Issues and PRDs for this repo live in GitHub Issues (uses the `gh` CLI). External PRs are not treated as a request surface. See `docs/agents/issue-tracker.md`.

### Short-term backlog

Keep worthwhile side work visible without losing the current objective. Side work and agent-configuration changes normally
belong in separate user-visible tasks. See `docs/agents/short-term-backlog.md` for task, sidetrack, and baseline-reflection
rules.

### Triage labels

This repo uses the canonical triage label vocabulary. See `docs/agents/triage-labels.md` for the mapping.

### Domain docs

This repository uses a single-context layout: one `CONTEXT.md` at the repo root and `docs/adr/` for ADRs. See `docs/agents/domain.md` for the consumption rules.

### Coding style

Import referenced Java and Groovy types and use their simple names. Fully qualified names in source are reserved for genuine
name conflicts or another documented technical necessity. See `docs/agents/coding-style.md`.

### Testing

Groovy 3 is the baseline test lane (`test`). Groovy 4 and Groovy 5 compatibility use `groovy4Tests` and `groovy5Tests`; run them when a version difference is expected and at the end of a change, rather than on every focused iteration. Every ignored, conditionally ignored, or pending test must state an actionable reason. See `docs/agents/testing.md`.

### Issue implementation commits

Implement issues on a new, dedicated issue branch using small, reasoned commits. Agents may create commits there without asking. Review and, when necessary, rewrite the local commit sequence before first publication or review; preserve reviewed commits and add review fixes as follow-up commits. See `docs/agents/commits.md`.

### Pull requests and releases

User-facing documentation is maintained in `wiki/`. Pull requests with user-visible changes must keep the relevant wiki pages, migration navigation, `CHANGES.md`, linked issues, and SonarCloud findings in sync. See `docs/agents/pull-requests.md`.
When reacting to pull-request review feedback, post one consolidated follow-up comment that states what was addressed, what
was intentionally left unchanged and why, and which validation supports the result.
