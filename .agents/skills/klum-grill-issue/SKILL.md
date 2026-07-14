---
name: klum-grill-issue
description: Conduct an evidence-led maintainer interview for a KlumAST issue or issue cluster. Use when curation leaves product, compatibility, architecture, canonical-issue, or release-placement decisions unresolved.
---

# Grill a KlumAST Issue

Resolve maintainer decisions without asking for facts discoverable in the repository.

## Prepare the decision surface

1. Read `AGENTS.md`, `CONTEXT.md`, `docs/agents/domain.md`, and `.agents/skills/grilling/SKILL.md`.
2. Read the complete issues and comments plus their entries in the architecture map, issue index, duplicate map, release plan, and decision log.
3. Read linked ADRs, implementation notes, wiki pages, tests, and only enough source to establish current behavior.

Present a compact evidence brief before questioning:

- apparent request and original motivation
- confirmed behavior today
- overlap or conflict inside the cluster
- Builder-first, generated-API, and Groovy compatibility consequences
- decisions still owned by the maintainer

## Interview one branch at a time

Ask one focused question, include a recommended answer with its tradeoff, and wait. Walk dependencies in decision order. Establish:

- the user problem and whether it remains desirable
- required source and binary compatibility
- affected schema, generated API, runtime lifecycle, or integration contract
- whether an accepted 4.0 decision supersedes the request
- the smallest coherent behavior and acceptance boundary
- canonical issue, duplicates, and release target
- whether an ADR, implementation plan, or tracer bullet is required

Challenge inconsistent answers against repository evidence and ADRs. Update no GitHub issue and enact no implementation during the interview.

## Close the interview

Summarize the agreed decisions, rejected alternatives, remaining open questions, proposed issue dispositions, documentation impact, and release placement. Ask the maintainer to confirm the summary. Finish only when the problem, desired behavior, compatibility policy, canonical issue, and release decision are either confirmed or explicitly recorded as unresolved.
