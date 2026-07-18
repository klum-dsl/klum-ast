---
name: klum-grill-issue
description: Conduct an evidence-led maintainer interview for a KlumAST issue or issue cluster. Use when curation leaves product, compatibility, architecture, canonical-issue, or release-placement decisions unresolved.
---

# Grill a KlumAST Issue

Resolve maintainer decisions without asking for facts discoverable in the repository.

## Prepare the decision surface

1. Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/domain.md`, `docs/agents/feature-triage.md`, and `.agents/skills/grilling/SKILL.md`.
2. Read the complete issues and comments plus their entries in the architecture map, issue index, duplicate map, release plan, and decision log.
3. Read linked ADRs, implementation notes, wiki pages, tests, and only enough source to establish current behavior.

Present a compact evidence brief before questioning:

- apparent request and original motivation
- for an enhancement, the recorded primary use case, need horizon, workaround viability, and secondary angle
- confirmed behavior today
- overlap or conflict inside the cluster
- Builder-first, generated-API, and Groovy compatibility consequences
- decisions still owned by the maintainer

## Interview one branch at a time

Ask one focused question, include a recommended answer with its tradeoff, and wait. Walk dependencies in decision order. Establish:

- the user problem and whether it remains desirable
- for an enhancement, the primary use case, need horizon, workaround viability, and whether any secondary angle belongs in scope
- required source and binary compatibility
- affected schema, generated API, runtime lifecycle, or integration contract
- whether an accepted 4.0 decision supersedes the request
- the smallest coherent behavior and acceptance boundary
- canonical issue, duplicates, and release target
- whether an ADR, implementation plan, or tracer bullet is required

Challenge inconsistent answers against repository evidence and ADRs. Update no GitHub issue and enact no implementation during the interview.

Infer feature-triage fields from evidence before asking. Confirm uncertain judgments when they affect priority or scope, but
do not make missing classification a hard blocker. If this is a complexity-free mini-feature, record the quick-win rationale
instead of forcing the full classification.

## Close the interview

Summarize the agreed decisions, rejected alternatives, remaining open questions, proposed issue dispositions, documentation impact, and release placement. Ask the maintainer to confirm the summary. Finish only when the problem, desired behavior, compatibility policy, canonical issue, and release decision are either confirmed or explicitly recorded as unresolved.
