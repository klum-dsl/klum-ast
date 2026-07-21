# Versioned user documentation and Javadocs

Date: 2026-07-21

Status: Accepted

Tracking issue: [#456 — Introduce versioned user documentation and Javadocs](https://github.com/klum-dsl/klum-ast/issues/456)

Implementation plan: [ADR 0013 implementation plan](../implementation/adr-0013-versioned-documentation-and-javadocs.md)

Related owners: [#488](https://github.com/klum-dsl/klum-ast/issues/488), [#469](https://github.com/klum-dsl/klum-ast/issues/469), [AnnoDocimal #71](https://github.com/blackbuild/anno-docimal/issues/71), and [KlumCast #47](https://github.com/klum-dsl/klum-cast/issues/47).

Parent decision: [ADR 0012 — shared prerelease-channel policy](0012-shared-prerelease-channel-policy.md)

## Context

The mutable GitHub wiki mixes released and unreleased Builder-first guidance, so an ordinary deep link can present a 4.0 contract to a 3.0.1 user. The current gitPublish task copies that mutable tree after Nebula release tasks, while Java-library modules produce only Javadoc JARs. Neither is a versioned public documentation contract.

KlumAST needs documentation and API reference that identify the exact release line, preserve historical material, and obey ADR 0012's immutable RC/final and explicit-authorization model. A development snapshot, Nexus staging repository, or mutable wiki must never look like a public prerelease.

## Decision

### Canonical source and destination

wiki/ remains repository authoring input. Protected GitHub Pages is the canonical public destination at https://klum-dsl.github.io/klum-ast/. The mutable GitHub wiki is sunset as a content destination and retains only small, labelled deep-link migration stubs.

The renderer consumes a checked-out revision, never implicit working-tree state. Its source manifest identifies the input revision and wiki tree, renderer revision, generated files, Javadoc inputs, and output hashes. Every rendered page visibly identifies its exact version and status.

### URL and alias contract

| Purpose | URL | Mutability |
| --- | --- | --- |
| Exact user documentation | /<version>/ | immutable |
| Exact API landing | /<version>/api/ | immutable |
| Exact module API | /<version>/api/<module>/ | immutable |
| Current stable final | /stable/ | labelled alias |
| Current maintained line, for example 4.0 | /4.0/ | labelled alias |
| Exact public RC under evaluation | /preview/ | labelled alias |

The root landing page points to /stable/ and identifies the selected version. A manifest-driven selector distinguishes exact immutable versions, stable, maintained lines, and the one preview. It retains a same-path deep link where possible; otherwise it goes to the target version root and says the page is unavailable there.

/preview/ may point only to one exact publicly resolvable RC. There is no public moving development tree. A failed, superseded, or not-yet-public candidate stays unlisted. Stable and line aliases advance only after #488's public artifact proof; preview advances only when its exact RC is public and eligible for that proof.

### Historical documentation and API reference

For every 2.x final and 3.0.1, preserve tagged wiki/ content exactly apart from mechanical rendering and link rewriting. Do not silently correct historical prose or examples; labelled errata are separate. Generate historical API reference from released Javadoc artifacts where available and label an unavailable artifact rather than substituting current source.

Every final patch and public RC from 4.0 onward publishes matching immutable user documentation plus isolated Javadocs for klum-ast, klum-ast-runtime, klum-ast-annotations, klum-ast-jackson, klum-ast-bean-validation, and klum-ast-gradle-plugin. klum-ast-bom is dependency-management only and has no Javadoc site. There is no merged namespace and no AnnoDocimal IDE-only source mirror presented as published API.

### Tracer and protected release integration

Before any RC, #456 delivers a credential-free documentation tracer. It renders the v3.0.1 tag and a fixed 4.0 commit, generates six API outputs, selectors, deep-link and wiki-stub fixtures, and a source manifest. It neither publishes output nor changes aliases, and it must never call a candidate an RC.

The later #456 protected Pages workflow independently validates the same stage, exact version, and master SHA accepted by the release path. Before #488 starts artifact publication it deploys an immutable, unlisted pending documentation snapshot and manifest for that exact input. Deployment is not public-release proof and does not advance aliases. Only #488's post-proof signal may expose stable, line, or preview aliases. Failed paths remain rejected and unlisted; a used public version is burned under ADR 0012 rather than overwritten or repaired in place.

### Ownership boundaries

#456 owns the renderer, source manifest, site contract, historical ingestion, Javadoc layout, migration stubs, tracer, protected documentation stage, and alias implementation. #488 owns protected authorization, credentials, Maven and plugin publication, public proof, recovery record, and release-operation authority. #456 consumes #488's proof result; it neither publishes artifacts nor creates a release/tag or claims a public RC.

AnnoDocimal #71 and KlumCast #47 are independent repository-local counterparts. They are linked for deduplication and may adopt compatible evidence, but neither changes this KlumAST URL, workflow, or ownership contract.

## Consequences

- A reader can identify both the version and whether content is immutable, stable/line navigation, or an exact RC preview.
- The release gate has reproducible documentation evidence before artifact publication, while aliases remain tied to #488 proof.
- Historical content remains auditable rather than being silently upgraded to current terminology or generated API shapes.
- The mutable wiki gitPublish path is removed only by implementation slices after migration stubs make existing links intelligible.

## Rejected alternatives

**One mutable wiki with an unreleased banner.** A banner cannot make an old deep link accurate for a released line.

**A public moving development site.** It would be easy to mistake as a release contract and contradicts ADR 0012.

**Merged module Javadocs or IDE mirrors.** Both lose module ownership and misrepresent generated completion metadata as supported API.

**Alias advancement on Pages deployment.** Deployment does not prove every Maven coordinate and plugin marker is publicly usable; #488 proof is the required gate.
