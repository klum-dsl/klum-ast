# Short-term backlog and task boundaries

Conversations often surface worthwhile work outside the current objective. Keep that work visible without silently
expanding the active task.

## Prefer a separate user-visible task

When a side note or separate idea warrants follow-up and task creation is supported, create a separate user-visible task.
That task is the short-term backlog entry: it owns the follow-up questions and the formulation of the corresponding issue.
Remote issue creation still follows the repository's normal issue-tracker workflow and authorization.

Before creating a task, recommend an appropriate model and reasoning level and briefly state why they fit. When that
recommendation is lower than or equivalent to the current model and reasoning level, configure the new task with the
recommendation directly. Ask for confirmation only when the choice is unclear or the recommendation has unusually high
expected cost, such as Sol at very-high (`xhigh`) reasoning or `ultra`.

If a task starts with a model or reasoning level different from the recommendation, its initial instructions must require
it to ask for confirmation before substantive work begins. If the task-creation surface cannot set the recommendation,
include it and the confirmation requirement in the task context. Do not substitute a hidden sub-agent for the user-visible
backlog task.

Every spawned task must state its completion status clearly in its final response. When its work is complete, say that the
task is finished. When the task's work is complete but an external condition remains, use a conditional completion
statement that names and, when possible, links the condition, for example: "This task is finished, with final merge of PR
#123 pending." Do not declare completion while substantive work assigned to the task remains.

Agent-configuration work normally warrants its own task because it changes how later work is performed.

## Small sidetracks may stay in the current task

Keep a sidetrack in the current task when that is prudent for its size or cost. Clearly announce the sidetrack before
starting it, then explicitly announce the return to the main topic when it is complete. Do not let the sidetrack silently
replace or broaden the main objective.

When a separate task would be disproportionate or unavailable, a temporary ignored or otherwise uncommitted file may hold
the short-term backlog. Identify the file and its temporary status so it is not mistaken for durable project policy or a
committed deliverable.

## Reflect reusable agent policy upstream

When changing agent configuration, inspect the repository's `AGENTS.md`, `CONTEXT.md`, repository-local skills, and related
configuration to locate the authoritative project file. Keep operational instructions out of `CONTEXT.md` unless they
define project domain language or architecture.

Also check whether the change is reusable across Blackbuild/Klum repositories and should be reflected in
[`blackbuild/engineering-baseline`](https://github.com/blackbuild/engineering-baseline). Keep project-specific overlays
local and avoid duplicating the canonical rule across files or skills. Record exactly one auditable outcome: when upstream
work is needed, link its baseline issue or pull request; otherwise, record a brief rationale for keeping the change local.
Do not invent a baseline location or modify a separate checkout or remote repository without the required authorization.

The reusable worker draft-pull-request authorization and the broader reusable portion of this policy are tracked
upstream in
[`blackbuild/engineering-baseline#2`](https://github.com/blackbuild/engineering-baseline/issues/2). The KlumAST-specific
issue-tracker and authorization wording above remains local.

The reusable AFK-window protocol, commit-addressable handoff, and safe evidence-context contract are also proposals for
that same baseline issue. Its KlumAST overlay remains local: the exact burst/capacity limits, resource-sensitive
concurrency reduction, license-policy hard stops, and human ownership of uncertain classification and all GitHub changes.

When a KlumAST worker completes, apply the generic `work-orchestrator` completion-handoff protocol; this overlay does not
authorize automatic follow-up work or change the root `AGENTS.md` delivery/archive states. In particular, a worker that
reaches `(ready:PR)` provides the generic review/change brief in its final callback, then requests rather than self-applies
archive reconciliation. The Hive alone performs the explicit final check for root `AGENTS.md`'s `(arch)` state.

## KlumAST worktree overlay

Maintain a stable, user-owned IDE-main checkout for ordinary KlumAST development. Every agent assignment uses a
separate isolated worktree; a pull-request review uses a detached `review-<PR>` worktree at the reviewed PR head.
Never run `gh pr checkout` in the IDE-main checkout. Review handoffs must state the review worktree path, the PR, its
reviewed head commit, and the comparison base or range; implementation handoffs must state the isolated worktree path,
branch, and final commit. Apply the generic skill's explicit, non-forced cleanup rules, while retaining worktrees when
their delivery condition or investigation value remains active.

## AFK-ADV-DEC local overlay

The Hive is KlumAST's only dispatcher and authoritative record during an AFK burst. It may admit one non-renewing,
90-minute window with at most three active tasks overall, at most two in this repository, and at most one
heavy local process (for example, a full build or compatibility lane) at a time. The ceiling is GPT-5.6 Terra at
`high` reasoning; a lower-cost model may be selected when it fits. A new window needs new explicit authorization;
neither a heartbeat nor an early completion renews the burst.

Admission is fresh and candidate-specific: the Hive must have a fully specified contract, evidence source, base revision,
bounded local-commit output, and validation/rebase plan. It stops admission on the deadline, a capacity limit, unavailable
quota/capacity visibility, an unexpected decision or exception, ambiguous or stale evidence, a failed check, a license
policy conflict, or any request for remote state. It records the stop as `unavailable` or a safe evidence reference rather
than estimating hidden quota or process state.

The accepted permanent authority is local only: within its declared scope a worker may use an isolated worktree and branch,
edit, validate, review local history, and create a local commit. It never authorizes credentials, push, GitHub changes,
merge, release, workflow dispatch, or external evidence storage. The only children are declared two-axis local-review
children. Before each child starts, the Hive records its name, both review axes, capacity count, local scope, and audit
return; all other delegation is prohibited.

Keep research candidates visible in the horizon with their evidence sources and human decision owner. AFK research is
evidence-only only when the question and sources are fully specified, the result cannot decide product/compatibility/
architecture/classification/scope/publication, and its stopping boundary is a safe local report or commit. Otherwise it
waits in the human queue. At every AFK return, the Hive first produces the full refreshed overview and reconciled matrix;
it does not use the return as authority to dispatch another task.

If a KlumAST worker stops at a safe boundary under the applicable `re` policy, title it `(paused)` and state that it is
waiting for an explicit resume. Its callback preserves the safe branch/commit/validation boundary and the precise resume
precondition; a pause is not completion, cancellation, or cleanup authorization. When entering another authorized AFK
window, the Hive refreshes and considers eligible paused workers before new candidates. It may resume one only after the
normal fresh-admission and capacity checks still pass.

## Post-release orchestration evidence overlay

For KlumAST 4.0, every cross-orchestrator evidence stream must carry stable `release_id` `klum-ast-4.0` and a distinct
`run_id` (for example, the release-orchestrator run or AFK-window identifier). The reusable event contract, including its
privacy boundary and AFK decision record, lives in the authoritative
[`work-orchestrator` skill](../../.agents/skills/work-orchestrator/SKILL.md) and its linked schema; do not copy the schema
into this overlay.

Keep runtime records outside the KlumAST checkout, organized by release/run. A release-close result is only a proposed,
sanitized human-reviewable report plus machine-readable summary for the engineering-baseline/release-governance location.
This policy does not authorize creating that store, automating collection, or mutating this product repository, its
tracker, or its remotes to gather evidence. Preserve KlumAST's normal human ownership of classification, GitHub changes,
and publication authority. When a per-run store is explicitly authorized, use the reusable schema's safe append-only
policy context for mode, window, deadline, capacity, admission stop, and contract/base/rebase/validation references; do
not add prompts, secrets, credentials, raw command output, inferred quota, or inferred cognition.
