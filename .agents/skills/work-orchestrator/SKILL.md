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

### 6. Run an explicitly bounded AFK window

AFK mode is an exception to the normal no-auto-start rule, not a second orchestrator. Enter it only when the user explicitly supplies all of the following: a fixed duration or deadline, a capacity mode, and a maximum model/reasoning level. Render the active window and its ceiling visibly. A request that omits capacity is **passive reconciliation-only**, not permission to launch work.

Capacity is a ceiling on concurrently active implementation tasks: `passive` = 0, `low` = 1, `normal` = 2, and `high` = 3. A repository overlay may lower that number for its own risk, but may never raise it. Use the least costly model and reasoning level that fits each admitted task, never exceeding the declared ceiling.

Before every launch, refresh the candidate's declared evidence sources and admit it only when the evidence is fresh, the work is already fully specified, and it belongs to the allowed task class: a bounded implementation or validation/documentation follow-up whose safe delivery boundary is a local commit. A candidate requiring a product, compatibility, architecture, classification, scope, or publication decision stays in the human queue. An empty admissible queue is valid and must remain idle.

Every admitted task is a fresh, user-visible worktree task with a stable short label, callback ID, bounded outcome, and the normal report protocol. It may create a local branch, edit, validate, review its local history, and commit. It must not use credentials or make any remote mutation: no push; PR or issue creation; comment, closure, label, milestone, or dependency change; merge, release, workflow dispatch; or equivalent remote action. Do not treat local commits as publication authorization.

An unexpected decision, exception, ambiguous evidence, failed admission check, or policy conflict is a safe stop: launch no replacement work and return the candidate with its evidence and question to the human queue. At the deadline, on an early return from AFK, or when capacity is reduced, stop new launches, let already admitted work reach its safe local boundary, and then provide the complete handoff: task reports, local branch/commit state, validation, unresolved human queue, and every external condition.

Use the existing orchestrator and, when the platform supports it, exactly one one-shot deadline heartbeat for the AFK window. Do not create another orchestrator, a recurring automation, or a self-renewing heartbeat. On exit, the normal no-auto-start rule immediately resumes. Cross-orchestrator impact triage remains required, but during AFK it may only route non-blocking information; it cannot dispatch remote, cross-repository, or additional work.

### 7. Reconcile lifecycle and titles

Keep a task's assigned execution scope separate from its repository delivery state. A completed task can therefore retain an external delivery condition; a task title must express the current actionable/archive state under the repository's authoritative task-title policy.

On a user report or orchestrator refresh, verify relevant issue, PR, and merge state and ask the task to update its title when that policy requires it. Tasks need not poll continuously. Do not archive or call work done while an actionable delivery step remains; use the repository's archive-safe status prefix only after its definition is satisfied.

### 8. Record post-release orchestration evidence

For a release or other bounded run, require every created worker to write an append-only, machine-readable evidence event stream and to include the local evidence-stream location in its callback report. This applies to both active and finished user-visible workers; the orchestrator backfills no inferred history. Use the exact schema and a minimal valid example in [the evidence reference](references/post-release-evidence.schema.json) and [example](references/post-release-evidence.example.json).

Record only events observed by the worker or orchestrator. Assign stable opaque IDs to the release, run, worker, task, event, artifact, decision, and operation; use links or artifact categories instead of copied content. Never record secrets, credentials, raw private prompts, full command output, personal assessments, or unredacted sensitive paths.

Use append-only semantics: add a new event; never edit or delete an already-recorded one. Correct factual errors with a `correction` event that identifies the superseded event and replacement values. Redact unsafe detail with a `redaction` event that identifies the event/field and reason, preserves the audit trail, and replaces the detail with a safe category or stable reference. A correction or redaction must not silently rewrite timing, authorization, or outcome history.

Each worker must emit `worker_started` and `worker_finished` events. Its finished summary must capture UTC start/end, actual model and reasoning, purpose, expected and actual output, and all observable worker data: visible token/quota pressure (or explicitly `unavailable` when the platform does not expose it); human intervention points and waiting duration; retries, abandoned approaches, and duplicated investigation; requested, authorized, denied, and deferred repository/remote operations; meaningful artifacts read/written; spawn rationale and why existing workers could not continue; cross-library decisions with owner and receiver acknowledgements; and other encountered problems. Capture an AFK decision record with exactly: Trigger; why this worker was needed; why existing workers could not handle it; allowed repository scope; allowed operations; operations requiring later authorization; expected stopping condition; result.

Treat “mental overload” only as optional, aggregate-safe operational telemetry: counts/timing of user-facing context switches and concurrent human-decision queues (for example, switches between unrelated grill sessions). Do not infer cognition, record personal traits, or capture prompt content.

The creating orchestrator must provide the release/run IDs, evidence location, callback requirement, and a worker’s permitted scope. It records orchestration-level launches, routing, authorization friction, and human-queue changes; checks callback reports for the final event location; and reconciles missing or invalid records as an explicit audit gap rather than guessing. Workers retain their normal authorization boundaries: evidence collection neither grants repository/remote operations nor changes AFK restrictions.

Use two layers of storage. Keep raw runtime event streams outside product repositories, partitioned by release and run, for example `<cross-repository-runtime-evidence>/<release-id>/<run-id>/`. At release close, aggregate only sanitized, human-reviewable findings and a machine-readable summary for the proposed engineering-baseline/release-governance location; do not create that runtime store, automate collection, or mutate a product repository merely to collect evidence. The aggregation calculates wall-clock and active/wait durations, observable quota efficiency, authorization/security friction, human task-switch/queue load, retries/duplication, and AFK-policy value, while preserving `unavailable` and audit-gap states rather than fabricating precision.

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
- [ ] An AFK window, when active, records an explicit deadline, capacity mode, and model/reasoning ceiling; admission evidence is fresh; every launched task is a local-only worktree task within the capacity cap; and no recurring automation or remote mutation was used.
- [ ] A release/run evidence stream exists outside the product repository for each created worker, has valid start/finish events or an explicit audit gap, and contains no prohibited sensitive content.
- [ ] The release-close aggregation produces only a sanitized report and machine-readable summary for the proposed engineering-baseline/release-governance location; its measurements preserve unavailable/unknown states.

## KlumAST overlay

For KlumAST, use root `AGENTS.md` as the authoritative task-title and delivery/archive policy; use `docs/agents/short-term-backlog.md` for model-selection and task-boundary rules; use `docs/agents/issue-tracker.md` and `docs/agents/pull-requests.md` for GitHub and publication authority. Apply `klum-review-release` for release reconciliation, `klum-curate-issues` for issue inventories, `klum-grill-issue` for unresolved maintainer decisions, and `klum-implement-issue` only for accepted implementation work. The current release plan, issue-culture artifacts, ADRs, local GitHub state, and task threads supply the release-specific evidence.

In an AFK window, tests, CI load, and local resource pressure may reduce concurrency below the generic cap. License-plugin conflicts are hard stops under `AGENTS.md`; do not adapt files or configuration to evade them. The only autonomous reconciliation is the local task and repository inventory. Uncertain classification and all GitHub changes stay in the human queue. These are overlays, not copies of the generic protocol.

## Baseline reflection

The generic evidence, horizon, rendering, lifecycle, callback, and AFK-window protocol is a candidate for later upstream extraction through [blackbuild/engineering-baseline#2](https://github.com/blackbuild/engineering-baseline/issues/2). KlumAST's overlay paths, resource limits, license-policy stops, and release authority remain repository-owned.
