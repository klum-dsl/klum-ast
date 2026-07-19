---
name: work-orchestrator
description: Maintain a compact, evidence-backed horizon of user-selectable repository work; create and reconcile user-visible tasks without confusing completed execution with delivered repository state.
---

# Work orchestrator

Use this skill to run a persistent coordination task for a repository release, milestone, or other bounded work horizon. This file is authoritative for the reusable protocol. Repository rules, release vocabulary, issue workflow, and publication authority remain local overlays.

## Inputs

The spawn template below accepts only these variables:

- `{{repository}}` — repository and, when useful, local checkout identity.
- `{{release_or_milestone}}` — the bounded horizon to coordinate.
- `{{callback_thread_id}}` — thread to receive task reports.
- `{{local_evidence_sources}}` — authoritative repository, tracker, and delivery sources to refresh.
- `{{scope_overlays}}` — local rules, release boundaries, and explicit exclusions.

Choose and explain the appropriate model and reasoning level from the work's cost and uncertainty; it is not a template parameter. Read the selected repository-local skills completely before relying on them.

## Operating protocol

### 1. Refresh evidence without mutation

On startup, a user return, a task report, or a periodic reconciliation, refresh the supplied evidence sources and relevant task threads. Prefer live tracker, PR, CI, and repository state to stale summaries. State the evidence revision or timestamp.

Refreshing is read-only: do not edit repository files, issues, labels, milestones, projects, pull requests, releases, or task state merely to build the overview. Do not create a task or start work automatically because an item is ready; wait for the user's selection or explicit authorization.

### 2. Select the smallest honest horizon

Show roughly five worthwhile, concrete items when that many exist; use fewer when the evidence does not support more and only extend to six or seven when necessary to make a dependency chain intelligible. Prefer implementable slices, decision sessions, or curation sessions to broad tracker issues. Do not invent issues, requirements, dependencies, or delivery claims to fill the horizon.

For every item, distinguish:

- **execution state** — `ready`, `not ready`, `running`, or `completed` for the task outcome;
- **delivery state** — live issue/PR/release condition, which can remain open after execution completes;
- **external condition** — a named, linked condition outside the assigned task, such as review, merge, credential, release approval, or a prerequisite owned elsewhere.

An item is `ready` only when it is actionable, has no substantive open prerequisite, and no user-visible task is already doing that work. A `not ready` item names every material open prerequisite. Never mark an issue or release delivered solely because a task reported completion.

### 3. Render dependencies for decisions

Use a compact Mermaid dependency graph when several labeled relationships make the horizon easier to understand; use a table alone for one independent item or when a graph would be decorative. Edges always run from prerequisite to dependent. Each graph node and table row carries a stable short label, issue or other owning reference, concise title, and written execution state.

Pair any status color with the written state and keep the mapping stable and accessible: ready, not-ready, running, and completed must remain distinguishable without color. Follow a graph with a small table containing label, linked work, execution state, reason/blocker, task or PR when present, delivery/external condition, and recommended next action. Make startable choices obvious.

### 4. Recommend and create user-visible tasks

Before creating a separate task, recommend a model and reasoning level with one sentence of rationale, following the local backlog policy. Apply the recommendation directly only when that policy permits it; otherwise obtain the required confirmation. Do not use a hidden sub-agent as a substitute for a user-visible backlog task.

Give the new task the stable short label, callback ID, a bounded outcome, local overlay references, and the required reporting protocol. Record its thread ID and mark the matching item `running`. The task must report:

- `RUNNING <label>: <brief current scope>` once substantive work begins;
- a concise update when a blocker, PR, external condition, or material scope change appears;
- `COMPLETED <label>: <outcome>; external condition: <link or none>` only when its assigned work is finished;
- `NOT READY <label>: <specific unmet condition>` when it cannot proceed.

If a task fails to report, inspect its thread before inferring status. Reconcile every report against live evidence and explain only material changes.

### 5. Triage cross-orchestrator impact

When creating a user-visible task and when its scope materially changes, discover and reconcile relevant active work orchestrators. Compare declared scope, repository ownership, and shared technology or policy boundaries. Choose exactly one outcome: **local only**, **cross-cutting implementation candidate**, **cross-cutting issue candidate**, or **shared policy/baseline candidate**.

For any candidate, send each relevant orchestrator one non-blocking message containing source task/label/repository, affected concern and evidence, recommended outcome, likely owner, and suggested next action. A receiving orchestrator verifies local relevance, deduplicates, records the candidate in its horizon or policy notes where appropriate, and reports only material changes.

This is automatic triage, not authorization or work dispatch: never auto-start a user-visible task, create a GitHub issue or pull request, modify a repository, or make a feedback loop. Preserve normal explicit GitHub-write authority and the user's implementation selection. Route only at creation or a material scope change; do not re-broadcast an already-routed candidate unless its evidence or scope materially changes.

### 6. Reconcile lifecycle and titles

Keep a task's assigned execution scope separate from its repository delivery state. A completed task can therefore retain an external delivery condition; a task title must express the current actionable/archive state under the repository's authoritative task-title policy.

On a user report or orchestrator refresh, verify relevant issue, PR, and merge state and ask the task to update its title when that policy requires it. Tasks need not poll continuously. Do not archive or call work done while an actionable delivery step remains; use the repository's archive-safe status prefix only after its definition is satisfied.

## Spawn template

```text
You are the user-visible work orchestrator for {{repository}}.

Scope: maintain the smallest evidence-backed, user-selectable horizon for {{release_or_milestone}}.
Callback thread: {{callback_thread_id}}.

Read and refresh: {{local_evidence_sources}}.
Apply these local overlays and exclusions: {{scope_overlays}}.

Follow the repository-local work-orchestrator skill. Refresh without mutation; keep execution state, delivery state, and external conditions separate. Render a compact graph only when dependencies need it, followed by a concise decision table. Recommend model/reasoning before creating user-visible work, wait for explicit selection or authorization, record each created task, and reconcile callback reports with live evidence. At task creation or material scope change, perform the skill's non-blocking cross-orchestrator impact triage exactly once per unchanged candidate.
```

## Human-verifiable audit checklist

Before handing off an overview or an orchestrator task, verify:

- [ ] Evidence names current repository, tracker, PR/CI, and task-thread sources, with a revision/time indicator.
- [ ] Every dependency is readable from prerequisite to dependent; every `not ready` item names its open prerequisite.
- [ ] Every `ready` item is actually startable, and the display does not invent work to meet a target count.
- [ ] Each task title matches the repository's current lifecycle/archive policy; active, delivery-pending, and archive-safe work are distinguishable.
- [ ] A reported task outcome is not mistaken for issue, PR, merge, or release delivery; live delivery state is shown separately.
- [ ] Every external condition is explicit and linked when a durable link exists.
- [ ] Created tasks have a stable label, callback ID, thread record, and required status-reporting language; missing reports were checked rather than guessed.
- [ ] At task creation and material scope changes, relevant active orchestrators were compared for repository, scope, and shared-boundary impact; exactly one triage outcome was selected.
- [ ] Cross-cutting candidates were routed once with the required evidence and ownership fields, then deduplicated by receivers without triggering work, GitHub writes, repository changes, or feedback loops.
- [ ] Refreshing and rendering made no repository, tracker, project, PR, release, or task-state mutation beyond a user-authorized task lifecycle action.

## KlumAST overlay

For KlumAST, use root `AGENTS.md` as the authoritative task-title and delivery/archive policy; use `docs/agents/short-term-backlog.md` for model-selection and task-boundary rules; use `docs/agents/issue-tracker.md` and `docs/agents/pull-requests.md` for GitHub and publication authority. Apply `klum-review-release` for release reconciliation, `klum-curate-issues` for issue inventories, `klum-grill-issue` for unresolved maintainer decisions, and `klum-implement-issue` only for accepted implementation work. The current release plan, issue-culture artifacts, ADRs, and local GitHub state supply the release-specific evidence. These are overlays, not copies of this generic protocol.

## Baseline reflection

The generic evidence, horizon, rendering, lifecycle, and callback protocol is a candidate for later upstream extraction through [blackbuild/engineering-baseline#2](https://github.com/blackbuild/engineering-baseline/issues/2); it remains local now because the reusable baseline has not yet been validated across repositories and KlumAST's overlay paths and release authority must stay repository-owned.
