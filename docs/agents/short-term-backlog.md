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

The reusable AFK-window protocol is also a candidate for that same baseline issue. Its KlumAST overlay remains local:
resource-sensitive concurrency reduction, license-policy hard stops, and human ownership of uncertain classification and
all GitHub changes.

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
and publication authority.
