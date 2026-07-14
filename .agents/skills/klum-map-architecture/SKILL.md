---
name: klum-map-architecture
description: Map the KlumAST repository architecture into the persistent issue-curation architecture map. Use for the initial architecture study, after a major architectural change, or when later issue work exposes stale or missing architecture knowledge.
---

# Map KlumAST Architecture

Build the compact architecture cache used by later issue-curation work.

## Establish the baseline

1. Work from the repository root and read `AGENTS.md`, `CONTEXT.md`, and the agent policies they route to.
2. Read `settings.gradle`, the root and module build files, `buildSrc/src/main/groovy/`, `.junie/guidelines.md`, `README.md`, `CHANGES.md`, `wiki/_Sidebar.md`, and `wiki/Builder-First-Migration.md`.
3. Read every file in `docs/adr/` and `docs/implementation/`. Treat accepted ADRs as authoritative and surface contradictions.

## Trace the architecture selectively

Map dependency and public-API boundaries for:

- `klum-ast-annotations`
- `klum-ast`
- `klum-ast-runtime`
- `klum-ast-jackson`
- `klum-ast-bean-validation`
- `klum-ast-gradle-plugin`
- `klum-ast-bom`

Treat `code-coverage-report` as build infrastructure, not an architectural module.

Inspect enough source to locate:

- AST transformation entry points, generated types, and generated API seams
- Builder lifecycle, Construction sessions, Materialization, Model companions, validation, Templates, factories, and structure traversal
- schema annotations and their public contracts
- Jackson, Bean Validation, and Gradle integration boundaries
- Groovy 3, 4, and 5 compatibility seams

Use tests as executable specifications: module `src/test/groovy/`, both relevant `src/testFixtures/groovy/` trees, and `klum-ast/src/test/scenarios/`. Record representative paths rather than cataloguing every test.

## Write the map

Create or refresh only `docs/implementation/issue-curation/architecture-map.md`. Preserve useful confirmed content already present.

Include:

- scope, revision/date, and source-of-truth rules
- module dependency map and public boundaries
- construction/lifecycle invariants
- generated-type and integration seams
- important source and executable-specification paths
- Groovy compatibility and verification notes
- authoritative ADR and implementation-note index
- explicitly labelled hypotheses or gaps

Keep confirmed facts separate from hypotheses. Cite repository paths for claims that later curators may need to verify. Finish only when every listed module and architectural concern is represented and no file outside the authorized map changed.
