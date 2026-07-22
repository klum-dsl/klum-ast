# Versioned user documentation and Javadocs

Date: 2026-07-21

Amended: 2026-07-22 (static-HTML presentation and Pages rehearsal contract)

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

The current 4.x authoring tree moves from the misleading wiki/ name to docs/user/ in the first #456 implementation slice. Protected GitHub Pages is the canonical public destination at https://klum-dsl.github.io/klum-ast/. The mutable GitHub wiki is sunset as a content destination and retains only small, labelled deep-link migration stubs. It is not a second authoring tree.

The renderer consumes a checked-out revision, never implicit working-tree state. For 4.x it reads docs/user/; for historical releases it reads the tagged wiki/ tree. Its site manifest identifies that source root and tree, renderer revision and contract, generated files, Javadoc inputs, and output hashes. Every rendered page visibly identifies its exact version and status. Repository-source URLs are contributor-only and sparse; user navigation and durable external links use the canonical versioned Pages URLs.

### Rendered-page contract

Markdown remains the authored source format, but it is not a deployable Pages payload. A pinned repository-local JVM renderer produces deterministic static HTML before publication. The exact version tree contains the rendered HTML, renderer-owned navigation/status chrome and CSS, copied local images/assets, isolated Javadocs, and one `site-manifest.json` covering every exact-tree file except itself. It has no CDN, external font, theme framework, required JavaScript, or runtime Jekyll dependency. A root `.nojekyll` belongs to the global `gh-pages` ledger and is covered by the verified deployment commit rather than an individual version manifest.

Authored `Home.md` becomes `/<version>/index.html`; every other authored `path/Page.md` becomes `/<version>/path/Page/index.html`. Links use relative extensionless directory URLs, including between nested pages and the API landing. GitHub-wiki links are rewritten mechanically. Heading fragments follow GitHub-compatible lowercase punctuation/space and duplicate-suffix behavior so historical fragments remain useful. Authored raw HTML is escaped and unsafe URL schemes are sanitized. The current input contains no presentation HTML. v3.0.1 has generic type tokens such as `<Child>` in signature prose that CommonMark classifies as inline HTML, but no intended presentation markup; escaping preserves the visible notation safely.

Local assets are copied within the exact version tree and manifest-covered. Diagram support is an extension point, not part of the first renderer contract: a future pinned build-time Mermaid or PlantUML adapter may emit local SVG/PNG assets that receive the same link and manifest treatment. Until then, diagram-like fenced blocks remain ordinary readable code.

### URL and alias contract

| Purpose | URL | Mutability |
| --- | --- | --- |
| Exact user documentation | /<version>/ | immutable |
| Exact API landing | /<version>/api/ | immutable |
| Exact module API | /<version>/api/<module>/ | immutable |
| Current stable final | /stable/ | labelled alias |
| Current maintained line, for example 4.0 | /4.0/ | labelled alias |
| Exact public RC under evaluation | /preview/ | labelled alias |
| Archived-version discovery | /archive/ | labelled index |

The root landing page points to /stable/ and identifies the selected version. A manifest-driven selector distinguishes exact immutable versions, stable, maintained lines, and the one preview. /archive/ groups 2.x and 3.0.1 as Archived (legacy), but their exact content remains at the same /<version>/ shape. It retains a same-path deep link where possible; otherwise it goes to the target version root and says the page is unavailable there.

/preview/ may point only to one exact publicly resolvable RC. There is no public moving development tree. A failed, superseded, or not-yet-public candidate stays unlisted. Stable and line aliases advance only after #488's public artifact proof; preview advances only when its exact RC is public and eligible for that proof.

### Version-status chrome

The renderer adds status chrome to every rendered page; it is mechanical presentation, not a change to historical authored prose. Archived exact versions display an Archived (legacy) banner and link to /archive/. Every public RC snapshot contains a fixed warning that it is a prerelease, not stable, and a link to its version-status record.

An exact documentation tree and its manifest remain immutable. When #488 later proves and publishes a final from an RC, #456 appends the successor event to a site-wide version-status record rather than rewriting the RC tree. The page chrome may resolve that record to display “RC <rc> is superseded by <final>” with a link to the final; its fixed RC warning and status-record link remain the non-JavaScript fallback. The status record must not make pending, rejected, or otherwise unlisted paths discoverable.

### Versioned branding

Each 4.x exact documentation snapshot includes a branding manifest naming its Season identity, logo asset, accessible
alternative text, and asset digest. The renderer copies that logo into the exact version tree and records the manifest in
the site manifest; a versioned page must not rely on a mutable global logo URL. Site-wide layout, typography, and other
styling may evolve later without changing this versioned branding identity.

The protected final documentation stage rejects a final snapshot without a branding manifest approved by the branding
owner. A public RC may carry candidate branding, but the final brand must be settled before its pending snapshot deploys.
#456 validates and captures the manifest; it does not select the Season identity or logo.

After publication, a logo replacement is an explicit exception rather than a silent mutation. The original branding asset
and manifest digest remain preserved. A separately authorized correction record names the old/new digest, reason, scope,
and approval; shared chrome may use that record to display the replacement. It must not rewrite the exact documentation
payload, alter the Season identity, or advance aliases.

### Historical documentation and API reference

For every 2.x final and 3.0.1, preserve the tagged wiki/ content exactly apart from mechanical rendering and link rewriting. Do not silently correct historical prose or examples; labelled errata are separate. The historical wiki/ location does not keep that name as the 4.x authoring convention. Generate historical API reference from released Javadoc artifacts where available and label an unavailable artifact rather than substituting current source.

Every final patch and public RC from 4.0 onward publishes matching immutable user documentation plus isolated Javadocs for klum-ast, klum-ast-runtime, klum-ast-annotations, klum-ast-jackson, klum-ast-bean-validation, and klum-ast-gradle-plugin. klum-ast-bom is dependency-management only and has no Javadoc site. There is no merged namespace and no AnnoDocimal IDE-only source mirror presented as published API.

### Tracer and protected release integration

Before any RC, #456 delivers a credential-free documentation tracer. It renders the v3.0.1 tag and a fixed 4.0 commit, generates six API outputs, selectors, deep-link and wiki-stub fixtures, and a site manifest. It neither publishes output nor changes aliases, and it must never call a candidate an RC.

The later #456 protected Pages workflow independently validates the same stage, exact version, and master SHA accepted by the release path. Before #488 starts artifact publication it commits an immutable, unlisted pending documentation snapshot and manifest for that exact input to the protected `gh-pages` ledger, reads the commit back, uploads that exact ledger through the official Pages artifact path, and completes a protected Pages deployment. Deployment is not public-release proof and does not advance aliases. Only #488's post-proof signal may expose stable, line, or preview aliases. Failed paths remain rejected and unlisted; a used public version is burned under ADR 0012 rather than overwritten or repaired in place.

Because Pages was not configured when this contract was accepted, one disposable pre-contract rehearsal may publish the generated static site for manual evaluation, then unpublish Pages and delete the entire experimental branch. That rehearsal must happen before creating and protecting the canonical orphan `gh-pages` ledger. It does not use a release-like `pending/<version>/<sha>/` path and creates no immutable release evidence. The protected workflow remains fail-closed while the repository variable `DOCUMENTATION_PAGES_READY` is not `true`; maintainers set it only after the rehearsal is removed and the fresh `.nojekyll` ledger, Pages source, environments, and protection rules are verified.

### Ownership boundaries

#456 owns the renderer, site manifest, site contract, historical ingestion, Javadoc layout, migration stubs, tracer, protected documentation stage, and alias implementation. #488 owns protected authorization, credentials, Maven and plugin publication, public proof, recovery record, and release-operation authority. #456 consumes #488's proof result; it neither publishes artifacts nor creates a release/tag or claims a public RC.

AnnoDocimal #71 and KlumCast #47 are independent repository-local counterparts. They are linked for deduplication and may adopt compatible evidence, but neither changes this KlumAST URL, workflow, or ownership contract.

## Consequences

- A reader can identify both the version and whether content is immutable, stable/line navigation, an exact RC preview, or an archived legacy snapshot—even from a direct deep link.
- The release gate has reproducible documentation evidence before artifact publication, while aliases remain tied to #488 proof.
- Historical content remains auditable rather than being silently upgraded to current terminology or generated API shapes.
- The physical source move, renderer source selection, and removal/rejection of the mutable gitPublish path are one first implementation slice, not a later cleanup. Migration stubs preserve existing public wiki links without preserving wiki/ as a live authoring root.
- A released Season identity remains recognizable even when the surrounding site styling evolves; any logo correction is
  visible and auditable rather than a retrospective rewrite.
- The public payload is reviewable without GitHub: Gradle can render it, a JDK HTTP server serves it under the real
  `/klum-ast/` base path, and a crawler checks internal pages, assets, directory indexes, and fragments. The one-time
  platform rehearsal covers the remaining GitHub Pages presentation/configuration risk.
- Changing the presentation later requires a new renderer contract for future exact versions; already published exact
  trees remain byte-identical. Pre-1.0 tags are outside the initial retention set and are unaffected unless explicitly
  imported by later work.

## Rejected alternatives

**One mutable wiki with an unreleased banner.** A banner cannot make an old deep link accurate for a released line.

**A public moving development site.** It would be easy to mistake as a release contract and contradicts ADR 0012.

**Merged module Javadocs or IDE mirrors.** Both lose module ownership and misrepresent generated completion metadata as supported API.

**Alias advancement on Pages deployment.** Deployment does not prove every Maven coordinate and plugin marker is publicly usable; #488 proof is the required gate.

**Deploying Markdown and relying on implicit Jekyll.** It does not prove the bytes, layout, deep links, or renderer version that readers receive and makes local verification diverge from Pages.

**A permanent rehearsal namespace.** A release-like pending path is single-use evidence. The empty-site rehearsal is deliberately disposable and precedes the immutable ledger instead of weakening that rule.
