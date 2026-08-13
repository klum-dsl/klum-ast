# Issue 546 — adopter-exercise toolkit

This is the reusable control pack for [#546](https://github.com/klum-dsl/klum-ast/issues/546).
It supports bounded discovery exercises against the final-coordinate KlumAST 4.0 RC,
`4.0.0-rc.20`. It is not exercise evidence, a release artifact, user-documentation
acceptance, or a publication procedure. The RC and its exact public coordinates remain
the release claim governed by [ADR 0012](../adr/0012-shared-prerelease-channel-policy.md),
`RELEASING.md`, and [#512](https://github.com/klum-dsl/klum-ast/issues/512).

Use one copy of the templates below for every exercise. Keep the completed record outside
the implementer's local repository. A mission may point to published documentation, the
selected installed skill revision, and the published RC; it must not turn an exercise into
an alternate release-validation fixture. Freeze the documentation/skills revision and
allowed-information policy before each round; do not reconstruct them from memory later.

## Exercise contract

An exercise has three roles:

| Role | Responsibility |
| --- | --- |
| Domain expert | Writes the mission and retains the shared invented-facts/assumptions set. Answers a fact only after the implementer asks for it. |
| Implementer | Builds a compact schema/model and local tests from the mission and permitted adopter materials. Records KlumAST-specific uncertainty, trials, and outside discoveries. |
| Evaluator | Assesses fulfilment and the evidence without changing the implementer's result. A maintainer classifies follow-ups. |

The Domain expert may be a human or agent who knows the target domain but not KlumAST
internals. The Implementer has an ordinary local Git worktree and may make small commits
and explicit exploratory reverts. The Evaluator may be the Domain expert or a separate
role. The maintainer remains the final classifier; no finding automatically changes the
product or release decision.

### Comparable serial rounds

Within a round, give each competing model or agent the same exact full prompt, retained as
a canonical artifact with an exact revision or content hash. Retain one shared
invented-facts set. Disclose a fact only when that implementer asks for it, then record
the supplied answer. A later round may use a changed competitor prompt with the same
facts, but it must identify that change.

Run competitors serially. Do not overlap their build or test activity. Record the start,
wait, resume, and finish ordering in the events log so elapsed time, build-cache effects,
and machine contention cannot be mistaken for an implementation difference.

## External exercise control plane

The control plane is outside every implementer repository. It is an append-only mailbox,
not a confidentiality boundary: processes running under the same macOS user can still
inspect each other's files. When facts must be technically isolated, use separate
sandboxes or macOS accounts and a mediator that transfers only the mailbox files.

Use a runner-owned directory such as:

```text
<exercise-root>/
  private-domain/                 # Domain expert only; assumptions/facts source
    assumptions-and-facts.md      # retained facts; never copied to a competitor
  control-plane/
    events.ndjson                  # append-only ordering record
    requests/Q-001.md              # immutable implementer question
    answers/Q-001.md               # immutable answer actually supplied
  implementer-records/<competitor>/ # copies of the public templates below
  evaluation/
```

`private-domain/` is not copied into an implementer's record. The shared control plane
contains only the exact question and answer that crossed the boundary. Separate numbered
files avoid locking or concurrent-append ambiguity. Allocate question identifiers in
order (`Q-001`, `Q-002`, ...); an implementer writes one request, records `waiting`, and
stops until the matching answer exists. The Domain expert or neutral runner writes the
answer, appends the resume event, and only then resumes that implementer.

For every state transition, append one line to `events.ndjson`. `order` is the comparison
authority; `actor` identifies who made the transition. A safe ISO-8601 timestamp is
optional and must not substitute for the ordering number:

```json
{"order":1,"event":"round-started","actor":"runner","round":"R-01","competitor":"A","prompt_sha256":"<sha256>"}
{"order":2,"event":"question-requested","actor":"implementer","round":"R-01","competitor":"A","question_id":"Q-001"}
{"order":3,"event":"implementer-waiting","actor":"implementer","round":"R-01","competitor":"A","question_id":"Q-001"}
{"order":4,"event":"answer-supplied","actor":"domain-expert","round":"R-01","competitor":"A","question_id":"Q-001"}
{"order":5,"event":"implementer-resumed","actor":"runner","round":"R-01","competitor":"A","question_id":"Q-001"}
{"order":6,"event":"competitor-finished","actor":"runner","round":"R-01","competitor":"A"}
```

Do not put prompts, credentials, raw command output, personal data, or unrelated local
paths in the event log.

### Control-plane request and answer

Use this exact shape for each immutable pair. The answer contains only what was actually
provided; it must not reveal unasked facts from the private assumptions set.

```markdown
<!-- control-plane/requests/Q-001.md -->
# Q-001

- Round: R-01
- Competitor: <identifier>
- Asked after event: <order>
- Kind: domain-fact | KlumAST-material | exercise-rule | clarification

## Question

<The implementer's unedited question>
```

```markdown
<!-- control-plane/answers/Q-001.md -->
# Q-001

- Round: R-01
- Competitor: <identifier>
- Supplied after event: <order>
- Answered by: Domain expert | neutral runner

## Answer supplied

<Only the answer supplied to this implementer>
```

## Mission-brief template

```markdown
# <exercise title>

## Mission

<Describe the domain outcome, its boundary, and the expected demonstrable result.>

## Starting point

- Exercise ID / round: <E-### / R-##>
- Competitor identifier: <identifier>
- Public KlumAST RC coordinates: `4.0.0-rc.20`
- KlumAST source/tag identity, if supplied: <identity>
- Selected documentation revision/URLs: <exact published revision and URLs>
- Installed skills and revisions: <name, exact revision, and source>
- Agent/model/tooling configuration: <recorded configuration>
- Exact full competitor prompt artifact, revision, and SHA-256: <location, revision, hash>
- Shared invented-facts set identity (Domain expert only): <identifier/hash>
- Run type: fresh | controlled repeat

## Required outcome and acceptance cues

- <A small domain behavior the completed model must demonstrate.>
- <A local test or other observable confirmation.>
- <A stated limit that the implementer must not cross.>

## Allowed materials and restrictions

- Allowed-information policy revision: <identifier/hash>
- Published RC documentation and selected skills: allowed
- Local source/history, issue trackers, JAR inspection, or public internet: <allowed or prohibited per source>
- Local Git experimentation and explicit reverts: allowed
- Publication, release actions, product changes, and remote tracker edits: prohibited

## Questions and assumptions

Ask the Domain expert through the external control plane. Do not infer a missing domain
fact silently: record it in the assumptions log and, when necessary, ask Q-###. The
Domain expert may disclose a retained fact only in the matching answer file.

## Handoff expected from the Implementer

- Local repository/branch and commit history reference
- Validation results
- Assumptions, questions, trials, reverts, and external discoveries
- A short self-assessment of what was documented versus discovered
```

## Domain-expert private assumptions and facts template

Keep this file only in `private-domain/`. It is the retained shared facts set for a round,
not an implementer handoff. The Domain expert may invent a fact to complete the domain,
but only disclose a retained fact through the matching immutable `answers/Q-###.md` file
after the implementer has asked. Do not copy an undisclosed row into a mission brief,
implementer record, events log, or evaluator packet.

```markdown
# Private assumptions and facts — <exercise ID / round>

- Shared invented-facts set identity: <identifier/hash>
- Mission-brief revision: <identifier/hash>
- Prepared by: <Domain expert>

| ID | State | Retained assumption or invented fact | Origin | Eligible question kind | Disclosed in Q-### | Notes / supersedes |
| --- | --- | --- | --- | --- | --- | --- |
| D-001 | retained | <fact needed to complete the domain> | domain expert invention \| accepted brief constraint | domain-fact \| clarification | — | <D-### or —> |
```

`State` is `retained`, `disclosed`, `superseded`, or `withdrawn`. Retain the original row
and add a new row for a correction. A fact becomes `disclosed` only when the answer file
records the fact actually supplied; a question that is not answered with that fact leaves
the row retained.

## Implementer-record templates

Copy these files into `implementer-records/<competitor>/`. Each log is append-only during a
run; record corrections as new rows referencing the earlier row rather than rewriting it.

### `assumptions.md`

```markdown
# Assumptions — <exercise ID / competitor>

| ID | Status | Assumption or invented fact | Source | Related Q-### | Effect on model/trial | Later correction |
| --- | --- | --- | --- | --- | --- | --- |
| A-001 | proposed | <fact or assumption> | mission \| answer \| implementer invention | <Q-### or —> | <effect> | <row ID or —> |
```

Use `proposed`, `confirmed`, `superseded`, or `rejected` for Status. Mark an implementer
invention explicitly; it is distinct from a fact supplied by the Domain expert.

### `questions.md` — KlumAST question log

```markdown
# KlumAST questions and uncertainty — <exercise ID / competitor>

| ID | State | Category | Question or uncertainty | Evidence consulted | Control-plane Q-### | Resolution | Impact |
| --- | --- | --- | --- | --- | --- | --- | --- |
| K-001 | open | documentation \| API \| generated DSL \| workflow | <question> | <published material only, or an external discovery> | <Q-### or —> | <answer/decision> | <trial or finding ID> |
```

`State` is `open`, `waiting`, `resolved`, `blocked`, or `not-a-KlumAST-question`. Record
domain questions in the control-plane request/answer pair and link them here only when they
materially affected a KlumAST decision.

### `trials.md` — trial and discovery log

```markdown
# Trials and external discoveries — <exercise ID / competitor>

| ID | Result | Intent | Attempt and outcome | Validation | Commit/revert | Knowledge source | Candidate finding |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-001 | passed \| failed \| abandoned | <what was tested> | <concise approach and outcome> | <test/task or observation> | <commit/revert or —> | published materials \| allowed external lookup | <F-### or —> |
```

For an allowed external lookup, state the fact that could not be learned from the published
adopter materials, not the full command output. Preserve useful exploration with a small
commit or an explicitly commented revert; do not rewrite history merely to hide a failed
trial.

## Runner guide

1. Create an exercise root outside the implementer repository and prepare the directory
   layout above. Create the private facts log, mission brief, canonical exact shared full
   prompt, and allowed-information policy before starting the first competitor.
2. Freeze `4.0.0-rc.20`, the documentation URLs/revision, installed skills/revisions,
   model/tool configuration, full-prompt artifact/revision/SHA-256, shared-facts-set
   identity, and run type in the mission brief. For a controlled repeat, retain the same
   facts and record every prompt or restriction change.
3. Give each competitor in one round the same exact full shared prompt. Supply the mission
   brief alongside it, changing only per-competitor record metadata such as the competitor
   identifier; do not treat that metadata as part of the shared prompt. Run one competitor
   at a time; do not start another competitor's build or test activity until the current
   one has finished or is explicitly stopped. Append `competitor-finished` or
   `competitor-stopped` before beginning the next competitor.
4. When an implementer asks a question, allocate the next `Q-###`, write the immutable
   request, append `question-requested` and `implementer-waiting`, and stop that
   implementer. The Domain expert or neutral runner writes only the answer actually
   supplied, appends `answer-supplied` and `implementer-resumed`, then resumes it.
5. Collect the implementer's append-only assumptions, KlumAST-question, and trial logs
   with its local commits, validations, and explicit reverts. Keep domain facts,
   KlumAST-specific uncertainty, and externally discovered facts separate.
6. Have the evaluator complete the checklist and findings matrix. The maintainer, not the
   runner, classifies proposed product, documentation, skill, release, or
   Showcase/Catwalk follow-up. Do not make an automatic product or release change.

The mailbox is a comparability channel only. It is not a confidentiality boundary for
processes running as the same macOS user. Use separate sandboxes or macOS accounts plus a
mediator when technical fact isolation is required.

## Evaluator checklist

Record `yes`, `no`, `partial`, or `not applicable` for every item, with evidence links.

| Area | Check | Result | Evidence / note |
| --- | --- | --- | --- |
| Setup | Exact `4.0.0-rc.20` coordinates, documentation/skills revision, allowed-information policy, prompt revision, competitor configuration, and permitted sources were frozen before the run. |  |  |
| Comparability | All competitors in this round received the same exact full-prompt artifact/revision/hash and shared-facts identity; any later-round variation is recorded. |  |  |
| Serial execution | Events show no overlapping competitor build/test activity, an ordered request/wait/answer/resume for every question, and a finish or stop before the next competitor starts. |  |  |
| Mission | The implemented schema/model meets the stated domain outcome and its stated limits. |  |  |
| Model quality | The result uses the applicable canonical roles and domain shape rather than an accidental workaround. |  |  |
| Assumptions | Inventions, supplied facts, assumptions, and later corrections are distinguishable. |  |  |
| Interaction | Questions demonstrate useful modelling interaction; domain clarification is not misclassified as a KlumAST defect. |  |  |
| Adopter materials | The record separates knowledge available in selected published materials from permitted external/history/JAR discoveries. |  |  |
| Ergonomics | The generated DSL/API and workflow were assessable from the implementer's actual trials. |  |  |
| Evidence | Local commits, validation, trials, and reverts are enough to understand the result without recreating the run. |  |  |
| Boundaries | No release, artifact publication, remote tracker mutation, or automatic product change occurred. |  |  |
| Potential follow-up | Showcase/Catwalk suitability is evaluated separately from product defects and is optional. |  |  |

## Findings matrix template

The maintainer, not the exercise runner, classifies the final disposition. One row represents
one actionable observation; retain an observation whose disposition is an accepted trade-off
instead of silently deleting it.

| ID | Observation | Evidence | Scope | Documented or discovered | Candidate owner | Recommended disposition | Priority / rationale | Maintainer decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-001 | <concise finding> | <trial/question/evaluator links> | documentation \| skill \| API \| generated DSL \| workflow | documented \| discovered externally \| unclear | <issue/team or —> | next RC \| final-release correction \| later issue \| accepted trade-off \| Showcase/Catwalk candidate | <why> | pending |

Classify a documentation or skill gap separately from an ordinary domain-modeling question.
A recommendation for the next RC or final-release correction is a proposed release input, not
evidence that the current RC fails. A substantive product, dependency, or release-configuration
change follows ADR 0012's next immutable RC rule; this toolkit does not authorize that change.

## First-run readiness check

Before the first real exercise, the runner confirms that an exact public KlumAST RC exists,
the mission identifies its published materials, the external control-plane directory is ready,
and the chosen roles can preserve serial ordering. The first completed run must add its own
evidence outside this template; do not backfill fictional values here.
