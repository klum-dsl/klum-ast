## Agent skills

### Issue tracker

Issues and PRDs for this repo live in GitHub Issues (uses the `gh` CLI). External PRs are not treated as a request surface. See `docs/agents/issue-tracker.md`.

### Triage labels

This repo uses the canonical triage label vocabulary. See `docs/agents/triage-labels.md` for the mapping.

### Domain docs

This repository uses a single-context layout: one `CONTEXT.md` at the repo root and `docs/adr/` for ADRs. See `docs/agents/domain.md` for the consumption rules.

### Testing

Groovy 3 is the baseline test lane (`test`). Groovy 4 and Groovy 5 compatibility use `groovy4Tests` and `groovy5Tests`; run them when a version difference is expected and at the end of a change, rather than on every focused iteration. Every ignored, conditionally ignored, or pending test must state an actionable reason. See `docs/agents/testing.md`.

### Pull requests and releases

User-facing documentation is maintained in `wiki/`. Pull requests with user-visible changes must keep the relevant wiki pages, migration navigation, `CHANGES.md`, linked issues, and SonarCloud findings in sync. See `docs/agents/pull-requests.md`.
