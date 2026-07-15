---
name: implement
description: "Implement a piece of work based on a PRD or set of issues."
disable-model-invocation: true
---

Implement the work described by the user in the PRD or issues.

Read the repository's agent instructions, `docs/agents/coding-style.md`, and `docs/agents/commits.md`. Before the first commit, create a new branch dedicated to the issue from the agreed base, or confirm the current branch was newly created for this issue.

Use /tdd where possible, at pre-agreed seams. Commit each completed reasoning slice according to `docs/agents/commits.md`; a TDD commit contains the failing test and the change that makes it green.

Run typechecking regularly, single test files regularly, and the full test suite once at the end.

Keep required documentation consistent with the final behavior. It may be a separate final commit.

Once done, use /code-review against the issue branch base, commit any fixes, then review and improve the complete commit sequence as required by `docs/agents/commits.md`. Re-run the final verification after any history rewrite.
