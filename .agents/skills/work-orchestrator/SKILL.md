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

## Workspace convention

Keep one stable, user-owned IDE-main checkout for normal interactive development. It is the user's working
directory, not an agent workspace: agents must not change its branch, create commits there, or use `gh pr
checkout` there. In particular, `gh pr checkout` is never a shortcut for preparing a review in the IDE-main
checkout, because it changes the user's checked-out branch and working context.

Run each agent assignment in its own isolated worktree. Its branch, base revision, and intended lifecycle must be
known before editing. Do not treat an agent worktree as an IDE-main replacement, and do not mix assignments in one
worktree. Report the worktree path, branch, and final commit when handing an implementation or policy change back.

Treat a proposed handoff as speculative until the Hive admits it. A handoff is reviewable only when it names the
accepted contract or issue reference, the exact base revision, the bounded local-commit outcome, and the validation
that will establish it. The completion record then names the worktree, branch, final commit, validation, and rebase
status against that base. If the base moved, rebase and revalidate before handoff, or record the safe stop; never
silently substitute a moving branch name for commit-addressable evidence.

Prepare pull-request review in a separate detached worktree named `review-<PR>` (or an unambiguous equivalent), at
the precise PR head commit being reviewed. Keep the review checkout detached so it cannot accidentally advance or
replace a local branch. A review report must identify the PR, the detached worktree path, the reviewed head commit,
and the comparison base or range; branch names alone are not sufficient evidence of what was reviewed. Do not use
`gh pr checkout` in the IDE-main checkout for this purpose.

Treat worktrees as explicit lifecycle-managed resources. Keep an agent worktree until its handoff records its branch,
commit, validation, and any delivery condition; keep a review worktree until the review outcome is recorded. Before
cleanup, confirm the target path is the intended isolated worktree and that it has no needed uncommitted changes or
unreported review context. Remove only that explicit worktree, never the IDE-main checkout; prefer ordinary
`git worktree remove` and follow with `git worktree prune` only when stale administrative entries remain. Do not use
forced removal to discard work. A user may retain a completed worktree for investigation; cleanup is a safe,
intentional lifecycle step, not an automatic completion action.

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

Keep bounded research questions visible in the horizon as `research` candidates, separate from ready delivery work.
Each names its question, authoritative evidence sources, and the decision owner. Evidence-only research is not an
implementation admission unless its expected result is a safe, non-decisional local report or commit; research that
could decide product, compatibility, architecture, classification, scope, or publication remains in the human queue.

### 3. Render dependencies for decisions

Use a compact Mermaid dependency graph when several labeled relationships make the horizon easier to understand; use a table alone for one independent item or when a graph would be decorative. Edges always run from prerequisite to dependent. Each graph node and table row carries a stable short label, issue or other owning reference, concise title, and written execution state. When Mobile Mode is active, use compact vertical sections instead: do not render Mermaid or a wide table.

Pair any status color with the written state and keep the mapping stable and accessible: ready, not-ready, running, and completed must remain distinguishable without color. Follow a graph with a small table containing label, linked work, execution state, reason/blocker, task or PR when present, delivery/external condition, and recommended next action. Make startable choices obvious.

### 4. Recommend and create user-visible tasks

Before creating a separate task, recommend a model and reasoning level with one sentence of rationale, following the local backlog policy. Apply the recommendation directly only when that policy permits it; otherwise obtain the required confirmation. Do not use a hidden sub-agent as a substitute for a user-visible backlog task.

Give the new task the stable short label, callback ID, a bounded outcome, local overlay references, and the required reporting protocol. Record its thread ID and mark the matching item `running`. The task must report:

- `RUNNING <label>: <brief current scope>` once substantive work begins;
- a concise update when a blocker, PR, external condition, or material scope change appears;
- `COMPLETED <label>: <outcome>; external condition: <link or none>` only when its assigned work is finished;
- `NOT READY <label>: <specific unmet condition>` when it cannot proceed.

If a task fails to report, inspect its thread before inferring status. Reconcile every report against live evidence and explain only material changes.

The Hive is the sole dispatcher and authoritative worker record for this horizon. It alone records admissions,
capacity, and completion handoffs. A worker may retain permanent authority only for the explicitly scoped local
operations in its assignment; it cannot dispatch ordinary work. The only permitted child work is explicitly declared
two-axis local review: before it starts, the Hive names the child, the two review axes, its scope, and its capacity
count; its start, result, validation, and stop condition are audited in the callback/evidence record. Undeclared,
non-review, remote, or cross-repository children are prohibited.

When a worker reaches `(ready:PR)`, its final callback must also contain a concise **review/change brief**. This is a review aid, not a file-by-file changelog. Include two to five outcome bullets; key files grouped as implementation, tests, and user-facing documentation (state `none` for an absent category); grouped minor or auxiliary changes; meaningful validation; and the review focus or compatibility risk (or explicit `none`). For simple work, the brief may be copied into the draft pull-request body. For complex work, it is the executive summary while the pull request retains the fuller rationale and evidence.

When Mobile Mode is active, make that brief a skimmable, vertical review brief rather than a link to the app diff or a prose wall. State the changed behavior, key file groups, validation, risks, and the exact desktop-review focus. Mobile reading may establish context or a short confirmation, but a full desktop diff review and every normal safety and release gate remain required.

### 4a. Audit remote-delivery authorization by channel

Before an authorized remote mutation, verify the required capability on each intended delivery channel. Authentication and authorization do not transfer between channels: for GitHub, a connected GitHub App installation and the authenticated `gh` CLI session are separate authorities, even when they target the same repository. A success or denial on one must not be inferred for the other.

Record only the safe channel/category and outcome (`authorized`, `denied`, or `unavailable`), not tokens, scopes, credential material, or raw error output. If one channel is denied, report the safe reason and use another already-authorized channel only when the user has authorized the same remote action; otherwise stop at the delivery boundary. A fallback channel does not expand the action's scope or grant permission for a different mutation.

### 5. Triage cross-orchestrator impact

When creating a user-visible task and when its scope materially changes, discover and reconcile relevant active work orchestrators. Compare declared scope, repository ownership, and shared technology or policy boundaries. Choose exactly one outcome: **local only**, **cross-cutting implementation candidate**, **cross-cutting issue candidate**, or **shared policy/baseline candidate**.

For any candidate, send each relevant orchestrator one non-blocking message containing source task/label/repository, affected concern and evidence, recommended outcome, likely owner, and suggested next action. A receiving orchestrator verifies local relevance, deduplicates, records the candidate in its horizon or policy notes where appropriate, and reports only material changes.

This is automatic triage, not authorization or work dispatch: never auto-start a user-visible task, create a GitHub issue or pull request, modify a repository, or make a feedback loop. Preserve normal explicit GitHub-write authority and the user's implementation selection. Route only at creation or a material scope change; do not re-broadcast an already-routed candidate unless its evidence or scope materially changes.

Cross-Hive routing applies only to Hives that the local overlay classifies as peers. Treat an uncertain candidate as non-peer until the maintainer explicitly confirms it; do not broaden routine broadcasts merely because another task calls itself a Hive. A future persistent Baseline/Policy Hive owns refinement of the shared classification rule after it exists.

### 6. Run an explicitly bounded AFK window

AFK mode is an exception to the normal no-auto-start rule, not a second orchestrator. Enter it only when the user explicitly supplies all of the following: a fixed duration or deadline, a capacity mode, and a maximum model/reasoning level. Render the active window and its ceiling visibly. An AFK window is one-shot and non-renewing: a later window requires a new explicit authorization. A request that omits capacity is **passive reconciliation-only**, not permission to launch work.

Capacity is a ceiling on concurrently active implementation tasks: `passive` = 0, `low` = 1, `normal` = 2, and `high` = 3. A repository overlay may lower that number for its own risk, but may never raise it. Use the least costly model and reasoning level that fits each admitted task, never exceeding the declared ceiling.

Before every launch, refresh the candidate's declared evidence sources and admit it only when the evidence is fresh, the work is already fully specified, and it belongs to the allowed task class: a bounded implementation or validation/documentation follow-up whose safe delivery boundary is a local commit. Evidence-only research is admissible only under the conditions stated in the horizon protocol. A candidate requiring a product, compatibility, architecture, classification, scope, or publication decision stays in the human queue. An empty admissible queue is valid and must remain idle. If quota/capacity data is not exposed, record it as `unavailable`; do not estimate it or admit additional work from an inference.

Every admitted task is a fresh, user-visible worktree task with a stable short label, callback ID, bounded outcome, and the normal report protocol. It may create a local branch, edit, validate, review its local history, and commit. It must not use credentials or make any remote mutation: no push; PR or issue creation; comment, closure, label, milestone, or dependency change; merge, release, workflow dispatch; or equivalent remote action. Do not treat local commits as publication authorization.

An unexpected decision, exception, ambiguous evidence, failed admission check, or policy conflict is a safe stop: launch no replacement work and return the candidate with its evidence and question to the human queue. At the deadline, on an early return from AFK, or when capacity is reduced, stop new launches, let already admitted work reach its safe local boundary, and then provide the complete handoff: task reports, local branch/commit state, validation, unresolved human queue, and every external condition. The Hive returns the full refreshed overview and reconciled decision matrix before it presents or considers further dispatch.

Use the existing orchestrator and, when the platform supports it, exactly one one-shot deadline heartbeat for the AFK window. Do not create another orchestrator, a recurring automation, or a self-renewing heartbeat. On exit, the normal no-auto-start rule immediately resumes. Cross-orchestrator impact triage remains required, but during AFK it may only route non-blocking information; it cannot dispatch remote, cross-repository, or additional work.

### 6a. Use explicit, time-bounded Mobile Mode

Mobile Mode is an interaction and scheduling context, not an authorization mode and not a device-state detector. Enter it only on explicit maintainer request with an expiry; never infer it from remote access, a device, activity, or availability. It is independent from AFK Mode and may be active at the same time without changing AFK admission, capacity, repository, remote-mutation, or release rules.

While active, prefer bounded tasks needing short confirmations, no long maintainer answer, and contained diffs. A task remains mobile-friendly when its full diff review deliberately waits for desktop use. Deprioritize broad design grillings, large or manual comparisons, and work expected to require IDE-heavy review. On entry, a worker with a still-pending user interaction, such as a PR or issue action, repeats its request when that would help: outcome/status, the single needed action, and any desktop-review caveat in the same compact format. Present the outcome first in compact vertical sections; omit Mermaid, logs, and raw long diffs unless requested; and ask for at most one material decision at a time.

Record only observed safe Mobile Mode context: whether it is active, its explicit expiry, and an optional desktop-review follow-up. Do not record or infer device, access path, location, availability, prompts, credentials, or personal telemetry. On expiry, return immediately to the ordinary interaction policy; no heartbeat or later contact renews it.

### 7. Reconcile lifecycle and titles

Keep a task's assigned execution scope separate from its repository delivery state. A completed task can therefore retain an external delivery condition; a task title must express the current actionable/archive state under the repository's authoritative task-title policy.

On a user report or orchestrator refresh, verify relevant issue, PR, and merge state and ask the task to update its title when that policy requires it. Tasks need not poll continuously. Do not archive or call work done while an actionable delivery step remains.

Where the repository has an archive-safe terminal state, workers must request Hive reconciliation; they must not self-archive or set that terminal state as part of normal completion. The Hive makes an explicit final reconciliation before applying it: assigned execution and delivery are complete; the audit/evidence record is complete or explicitly has no unfilled required fields; no local commit, tracker or PR update, external delivery, decision, human action, or task-scope condition remains. A completed or merged-delivery title alone is not sufficient. Retain the useful completed/delivery state in the final callback and, where practical, in the archive title or evidence record.

When a worker stops at a safe boundary under the applicable `re` policy, it is neither completed nor blocked: use the
repository's `(paused)` title state and say explicitly that it is **waiting for an explicit resume**. Its callback records
the safe boundary, branch/commit, validation, unresolved condition, and the resume precondition. Do not clean up,
reassign, or infer resumption from the passage of time. On entry to a later authorized AFK window, the Hive refreshes the
paused worker's evidence and considers eligible paused workers before new candidates. A resume still requires fresh
admission, capacity, and the explicit authorization required by the local overlay.

### 8. Present next work after worker completion

Treat a **completed worker** as a worker that reports `COMPLETED` after finishing its assigned bounded execution scope. It is not an issue, pull request, or release delivery claim: an open PR, review, merge, credential, or release condition remains an external delivery condition. A `NOT READY` report, an unstarted task, and a worker whose bounded scope is still active are not completed workers.

Treat a **substantive worker** as a worker that has started a bounded execution outcome and reported `RUNNING` or later state; do not count an unstarted, administrative, or placeholder task. Treat an item as **available** only when it remains `ready` under this skill: its work is actionable, it has no material open prerequisite, and no user-visible task is already doing it. Do not relabel delivery-pending, waiting, or blocked work as available.

On every received worker execution-completion report, reconcile enough current task-thread, repository, tracker, PR, CI, and dependency evidence to present the concise next available choices. Keep the completed worker's execution outcome, its live delivery state, and every external condition separate. Present the smallest useful set of genuinely available choices, normally two to four; name blocked dependencies separately instead of presenting their dependents as startable. In Mobile Mode, use the compact vertical presentation and present at most one material decision at a time. This is a handoff for user selection, not authorization to start another task.

When the report ends the last substantive worker, perform a mandatory **full refresh** before the handoff. A full refresh reads the live relevant issue, PR, dependency, task-thread, and repository state; renders the compact dependency graph (or Mobile Mode's compact vertical equivalent); and reconciles the decision matrix. It remains read-only and must not create or start a task, mutate a repository or remote, or infer delivery from a completed worker.

### 9. Record post-release orchestration evidence

For a release or other bounded run, require every created worker to write an append-only, machine-readable evidence event stream and to include the local evidence-stream location in its callback report. This applies to both active and finished user-visible workers; the orchestrator backfills no inferred history. Use the exact schema and minimal valid examples for a [worker finish](references/post-release-evidence.example.json) and [completion handoff](references/post-release-evidence.completion-handoff.example.json).

Record only events observed by the worker or orchestrator. Assign stable opaque IDs to the release, run, worker, task, event, artifact, decision, and operation; use safe summaries, links, or artifact categories instead of copied content. The schema's optional policy context records only safe mode, window, deadline, capacity, admission-stop, Mobile Mode active/until context, optional desktop-review follow-up, and contract/rebase/validation references. Never auto-collect or store raw prompts, secrets, credentials, full command output, device/access state, or inferred telemetry, including inferred token/quota pressure, active/wait time, cognition, or mental state. Record only observed safe summaries or references; use `unavailable` or an `audit_gap` event when the platform does not expose the data. Apply this boundary equally to completion-handoff refresh events.

Use append-only semantics: add a new event; never edit or delete an already-recorded one. Correct factual errors with a `correction` event that identifies the superseded event and replacement values. Redact unsafe detail with a `redaction` event that identifies the event/field and reason, preserves the audit trail, and replaces the detail with a safe category or stable reference. A correction or redaction must not silently rewrite timing, authorization, or outcome history.

Each worker must emit `worker_started` and `worker_finished` events. Its finished summary must capture UTC start/end, actual model and reasoning, purpose, expected and actual output, and all observable worker data: visible token/quota pressure (or explicitly `unavailable` when the platform does not expose it); human intervention points and waiting duration; retries, abandoned approaches, and duplicated investigation; requested, authorized, denied, and deferred repository/remote operations; meaningful artifacts read/written; spawn rationale and why existing workers could not continue; cross-library decisions with owner and receiver acknowledgements; and other encountered problems. Capture an AFK decision record with exactly: Trigger; why this worker was needed; why existing workers could not handle it; allowed repository scope; allowed operations; operations requiring later authorization; expected stopping condition; result.

Treat “mental overload” only as optional, aggregate-safe operational telemetry: counts/timing of user-facing context switches and concurrent human-decision queues (for example, switches between unrelated grill sessions). Do not infer cognition, record personal traits, or capture prompt content.

The creating orchestrator must provide the release/run IDs, evidence location, callback requirement, and a worker’s permitted scope. It records orchestration-level launches, routing, authorization friction, human-queue changes, and completion-handoff refreshes; checks callback reports for the final event location; and reconciles missing or invalid records as an explicit audit gap rather than guessing. Authorization-friction records identify only the channel/category and safe authorized/denied/unavailable outcome; they never copy credentials, scopes, or raw errors, and never infer that one delivery channel's authority applies to another. A completion-handoff event records only the completed worker reference, refresh level, whether it was the last substantive worker, and the sanitized available choices and blocked dependencies actually presented. Do not duplicate worker runtime data or infer unavailable timing or telemetry. Workers retain their normal authorization boundaries: evidence collection neither grants repository/remote operations nor changes AFK restrictions.

Use two layers of storage. The default runtime-evidence root, as guidance only, is `~/.codex/orchestration-evidence/<release-id>/<run-id>/`, containing append-only `events.jsonl` and a small `manifest.json`. The manifest contains only schema, release, and run identifiers plus safe artifact references. Never auto-create this root or either file: require per-run user authorization before creating or persisting it, and record retention and backup ownership when that authorization is made. At release close, aggregate only sanitized, human-reviewable findings and a machine-readable summary for the proposed engineering-baseline/release-governance location; do not automate collection or mutate a product repository merely to collect evidence. The aggregation calculates wall-clock and active/wait durations, observable quota efficiency, authorization/security friction, human task-switch/queue load, retries/duplication, and AFK-policy value, while preserving `unavailable` and audit-gap states rather than fabricating precision.

## Spawn template

```text
You are the user-visible work orchestrator for {{repository}}.

Scope: maintain the smallest evidence-backed, user-selectable horizon for {{release_or_milestone}}.
Callback thread: {{callback_thread_id}}.

Read and refresh: {{local_evidence_sources}}.
Apply these local overlays and exclusions: {{scope_overlays}}.

Follow the repository-local work-orchestrator skill. Refresh without mutation; keep execution state, delivery state, and external conditions separate. Render a compact graph only when dependencies need it, followed by a concise decision table; in Mobile Mode use compact vertical sections with no Mermaid or wide tables. Recommend model/reasoning before creating user-visible work, wait for explicit selection or authorization, record each created task, and reconcile callback reports with live evidence. At task creation or material scope change, perform the skill's non-blocking cross-orchestrator impact triage exactly once per unchanged candidate.

When a worker reaches `(ready:PR)`, require its final callback to include a concise review/change brief: two to five outcome bullets; key files grouped as implementation, tests, and user-facing documentation (or `none`); grouped auxiliary changes; meaningful validation; and review focus or compatibility risk (or `none`). In Mobile Mode it must also state behavior, risks, and the exact desktop-review focus in short labeled sections; it must not point only to an app diff. Workers request, but never self-apply, an archive-safe terminal status; the Hive applies it only after explicit final reconciliation under the repository overlay.
```

## Human-verifiable audit checklist

Before handing off an overview or an orchestrator task, verify:

- [ ] Evidence names current repository, tracker, PR/CI, and task-thread sources, with a revision/time indicator.
- [ ] Every dependency is readable from prerequisite to dependent; every `not ready` item names its open prerequisite.
- [ ] Every `ready` item is actually startable, and the display does not invent work to meet a target count.
- [ ] Each task title matches the repository's current lifecycle/archive policy; active, delivery-pending, and archive-safe work are distinguishable.
- [ ] A reported task outcome is not mistaken for issue, PR, merge, or release delivery; live delivery state is shown separately.
- [ ] Every `(ready:PR)` worker final callback includes a review/change brief with two to five outcomes, key files grouped as implementation/tests/user documentation (or `none`), grouped auxiliary changes, meaningful validation, and review focus or an explicit `none` risk.
- [ ] Before every authorized remote delivery action, the required authority was checked separately for each intended channel; a GitHub App and `gh` CLI session were not treated as interchangeable, and the audit records only safe channel/outcome facts.
- [ ] Mobile Mode, when active, was explicitly requested with a recorded expiry, did not infer device/access state or change AFK/release authority, used compact vertical output without Mermaid/logs/raw long diffs unless requested, and presented at most one material decision.
- [ ] On Mobile Mode entry, every worker with a still-pending PR, issue, or other user interaction repeated the request when useful in the compact mobile format, including the single needed action and any desktop-review caveat.
- [ ] Every Mobile Mode `(ready:PR)` brief states behavior, key file groups, validation, risks, and the precise desktop-review focus; desktop diff review and all normal safety/release gates remain outstanding where applicable.
- [ ] Any archive-safe terminal title was explicitly reconciled by the Hive: execution and delivery are complete; audit/evidence is complete or has no unfilled required fields; no local, remote, external-delivery, decision, human-action, or task-scope condition remains; and useful completed/delivery history is retained.
- [ ] Every `COMPLETED` worker report produced a read-only next-work handoff with only genuinely available choices and separately named blocked dependencies; the last substantive worker also received the mandatory full refresh, compact graph, and reconciled matrix without auto-starting work.
- [ ] Every external condition is explicit and linked when a durable link exists.
- [ ] Created tasks have a stable label, callback ID, thread record, and required status-reporting language; missing reports were checked rather than guessed.
- [ ] At task creation and material scope changes, relevant active orchestrators were compared for repository, scope, and shared-boundary impact; exactly one triage outcome was selected.
- [ ] Cross-cutting candidates were routed once with the required evidence and ownership fields, then deduplicated by receivers without triggering work, GitHub writes, repository changes, or feedback loops.
- [ ] Refreshing and rendering made no repository, tracker, project, PR, release, or task-state mutation beyond a user-authorized task lifecycle action.
- [ ] An AFK window, when active, records an explicit deadline, capacity mode, and model/reasoning ceiling; admission evidence is fresh; every launched task is a local-only worktree task within the capacity cap; and no recurring automation or remote mutation was used.
- [ ] Every speculative handoff is commit-addressable and records contract, base, worktree/branch, validation, and rebase status; the Hive returned a full overview before any further dispatch.
- [ ] Every local-review child was declared by name with two review axes, counted against capacity, and audited; all other child dispatch is absent.
- [ ] A safe `re`-policy stop uses the repository's `(paused)` title, says it is waiting for explicit resume, and is considered before new AFK candidates after a fresh admission check.
- [ ] A release/run evidence stream exists outside the product repository for each created worker, has valid start/finish events or an explicit audit gap, and contains no prohibited sensitive content.
- [ ] The release-close aggregation produces only a sanitized report and machine-readable summary for the proposed engineering-baseline/release-governance location; its measurements preserve unavailable/unknown states.

## KlumAST overlay

For KlumAST, use root `AGENTS.md` as the authoritative task-title and delivery/archive policy; use `docs/agents/short-term-backlog.md` for model-selection and task-boundary rules; use `docs/agents/issue-tracker.md` and `docs/agents/pull-requests.md` for GitHub and publication authority. Apply `klum-review-release` for release reconciliation, `klum-curate-issues` for issue inventories, `klum-grill-issue` for unresolved maintainer decisions, and `klum-implement-issue` only for accepted implementation work. The current release plan, issue-culture artifacts, ADRs, local GitHub state, and task threads supply the release-specific evidence.

In an AFK window, tests, CI load, and local resource pressure may reduce concurrency below the generic cap. License-plugin conflicts are hard stops under `AGENTS.md`; do not adapt files or configuration to evade them. The only autonomous reconciliation is the local task and repository inventory. Uncertain classification and all GitHub changes stay in the human queue. These are overlays, not copies of the generic protocol.

## Baseline reflection

The generic evidence, horizon, rendering, lifecycle, callback, AFK-window, and Mobile Mode protocol is a candidate for later upstream extraction through [blackbuild/engineering-baseline#2](https://github.com/blackbuild/engineering-baseline/issues/2). KlumAST's overlay paths, resource limits, peer-Hive classification, license-policy stops, and release authority remain repository-owned.
