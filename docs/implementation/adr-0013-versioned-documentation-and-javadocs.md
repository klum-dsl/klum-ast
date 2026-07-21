# ADR 0013 implementation plan: versioned user documentation and Javadocs

This plan implements [ADR 0013](../adr/0013-versioned-documentation-and-javadocs.md) for [#456](https://github.com/klum-dsl/klum-ast/issues/456). It is a cross-cutting implementation candidate: the decision is settled, but the renderer and protected Pages path are not implemented. It does not publish Pages, mutate GitHub, implement a release, or absorb #488 artifact/public-proof ownership.

## Confirmed current behavior and failure path

The root gitPublish configuration copies wiki/ to the mutable klum-ast.wiki master branch, token-replaces @version@, and copies CHANGES.md as Changelog.md. candidate and final still have that mutable path available, while the protected product task deliberately excludes documentation. Java-library modules produce Javadoc JARs but no hosted multi-module API tree. wiki/ contains unreleased 4.0 Builder-first content and cannot be accurate documentation for 3.0.1 users.

The failure is semantic and operational: a URL does not identify its contract version, a staged/local build could look released, and an alias could move before all public artifacts are proven. The implementation removes these ambiguities without rewriting tagged historical prose.

## Requirement-to-delivery map

| Confirmed requirement | Delivery and acceptance evidence | Owner |
| --- | --- | --- |
| Canonical hosting and version switching | Current authoring moves to docs/user/ while Pages renderer publishes immutable exact user/API trees plus labelled aliases; selector/deep-link tests exercise the contract. | #456 |
| Stable, historical, and prerelease identity | Page chrome states exact version/status; selector labels aliases; no development alias exists. | #456 |
| Accurate 2.x and 3.0.1 history | Tag-driven comparison permits only rendering/link changes; errata are separate; released Javadoc artifacts are used when present. | #456 |
| Module-specific 4.x API | Six isolated Javadoc trees and version API landing; BOM absent; IDE mirrors excluded. | #456 |
| Reproducible pre-RC proof | Credential-free tracer renders v3.0.1 and fixed SHA 963d12dbf28ebeaf9a47e52c56465f8f27b97592, six API trees, selectors/deep links/wiki stubs, and source manifest without deployment or alias changes. | #456 |
| Immutable protected snapshot | Protected documentation stage validates release stage/version/master SHA and deploys only an unlisted pending immutable tree plus manifest before artifacts. | #456, invoked by #488 |
| Deliberate aliases and recovery | Promotion requires #488 public-proof result; rejected paths stay unlisted; no version/path is overwritten. | #456 consumes #488 proof |
| README, migration, release-note, and wiki migration | Labelled wiki stubs and link inventory precede mutable-destination removal; canonical Pages URLs replace release links. | #456 and feature owners |
| Clear authority | RELEASING.md and workflow tests separate #456 documentation stage from #488 authorization, credentials, artifacts, proof, release/tag, and recovery. | #456 / #488 |

## Affected seams and constraints

- Authoring/rendering: move current 4.x content from wiki/ to docs/user/, then update CHANGES.md, README/migration links, navigation metadata, renderer/site fixture, and contributor guidance. The renderer reads docs/user/ for 4.x and tagged wiki/ only for historical versions; it renders a selected Git revision, not ambient repository state.
- Build/API inputs: Javadoc tasks/Javadoc JARs for the six named modules. BOM has no Java API; AnnoDocimal mirrors remain IDE-only and outside inputs.
- Release: root version/stage checks, protected release workflow, separate protected Pages deploy. The documentation stage validates independently and runs before #488 artifacts, but receives no artifact-publishing authority or credentials.
- Compatibility: v3.0.1 and 2.x retain tagged prose. 4.x uses Builder-first terminology. A version switch never implies a page/API exists in another version.
- Validation: renderer/manifest fixtures, Javadoc generation, URL/link checks, protected-workflow static/task tests, and git diff --check. Groovy 3/4/5 lanes are unnecessary unless a slice changes build/test behavior.

## Dependency-ordered tracer and implementation slices

### VD-1 — Pin the site contract and deterministic renderer

**Work.** In the same change, move the current 4.x authoring tree from wiki/ to docs/user/, update its relative links/navigation and the contributor instructions, and add a local renderer with explicit revision, version, status, and output-directory inputs. The renderer selects docs/user/ for 4.x and does not treat wiki/ as a second current source. Replace or make the old gitPublish path fail closed so it cannot publish the prior mutable tree. Implement ADR 0013 layout, page chrome, selector, immutable-tree checks, and a machine-readable source manifest containing source-root/revision/tree hash, renderer revision, version/status, Javadoc inputs/checksums, output hashes, and no secrets.

**Acceptance.** Repeated render of one revision yields identical output/manifest. The 4.x fixture proves docs/user/ is the only current authoring root, while a historical fixture remains able to select tagged wiki/. Reject a dirty or unresolved input, preview not pointing to an RC, missing expected source root, duplicate path, and a development alias. Verify root, exact version, API landing, module API, alias labels, missing same-path fallback, and that the old mutable publisher cannot run.

**Commit boundary.** The source move, link/navigation rewrites, contributor-guidance correction, renderer source selection, and fail-closed old-publisher change stay together so no branch has two current authoring roots; renderer/manifest fixtures follow in the same vertical slice.

### VD-2 — Preserve historical input and migrate the mutable wiki

**Work.** Enumerate every 2.x final tag and v3.0.1; render their wiki/ trees mechanically and compare normalized source content. Import historical Javadocs from released artifacts where available and label absence otherwise. Generate minimal GitHub-wiki landing/deep-link stubs from a checked link inventory; each is labelled migration content and links to stable or exact canonical content without pretending to redirect HTTP. Historical rendering must not recreate wiki/ as a current 4.x source.

**Acceptance.** Fixture hashes prove no prose/example change beyond mechanical rendering/link rewriting. Known legacy slugs resolve to labelled stub or mapped page. Missing historical Javadocs remain unavailable; no 4.x API tree can satisfy an old version request.

**Commit boundary.** Historical ingestion/comparison tooling, then stub inventory and migration guidance.

### VD-3 — Generate isolated module Javadocs

**Work.** Generate six module outputs from the selected 4.x revision below /<version>/api/<module>/ and create /<version>/api/ landing. Use an explicit allowlist that excludes BOM and mirrors. Generated public Builder/model Javadocs may only come from real module inputs, never by compiling or packaging a mirror.

**Acceptance.** A representative public type is reachable in every output; bases are distinct and unmerged. BOM and mirror sources are absent. A failed Javadoc task fails the exact-version render rather than yielding partial labelled success.

**Commit boundary.** Module inputs/task wiring/assertions, then API navigation/policy.

### VD-4 — Deliver the credential-free documentation tracer

**Work.** Add one unprivileged task/workflow path that obtains v3.0.1 and fixed SHA 963d12dbf28ebeaf9a47e52c56465f8f27b97592, runs VD-1–VD-3, and retains outputs/manifests as ordinary build evidence. It uses no GitHub, Pages, signing, registry, or alias credentials and never names the fixed SHA an RC.

**Acceptance.** A clean checkout runs with no secrets; asserts both identities, six 4.0 API outputs, selector/deep-link/stub cases, and manifests. Static inspection proves no publish/deploy/alias task is in the graph. A changed input revision deliberately changes manifest.

**Commit boundary.** Tracer plus fixtures in one green commit; CI retention/reporting may be a second.

### VD-5 — Add the protected pending-documentation stage

**Work.** Add protected Pages task/workflow accepting exact candidate/final stage, version, and full SHA; recompute Nebula version, prove SHA is on master, and reject mismatch, used path/tag, or malformed stage. Render only that revision and deploy immutable unlisted pending tree plus manifest before #488 artifacts. Hand the verified values to #488 orchestration without copying artifact credentials.

**Acceptance.** Tests reject mismatched stage/version/SHA, off-master SHA, existing immutable path, malformed identity, missing manifest, and artifact publication before pending-doc success. Pending output cannot be confused with preview/stable.

**Commit boundary.** Shared validated-input/manifest handoff; Pages deploy with negative ordering tests; release guide update. Do not mix with #488 artifact publication.

### VD-6 — Promote aliases only after public proof and complete migration

**Work.** Define #488 proof-result handoff containing exact version/SHA and successful public artifact evidence. Only then update stable, maintained-line, eligible preview aliases, and selector index. Preserve rejected/superseded paths unlisted. Complete canonical-link migration in README, migration navigation, release guidance, and CHANGES.md; wiki migration stubs remain only for legacy deep links, not as authoring guidance.

**Acceptance.** Alias tests reject absent/failed/mismatched proof and non-public/non-RC preview. Successful proof advances only allowed aliases and never exact content. Recovery keeps failed paths unlisted and requires a new RC/version. Link inventory shows no release-facing document names either the mutable wiki or repository source paths as canonical user documentation.

**Commit boundary.** Proof/alias state machine with negative tests, then migration/release documentation. The old publisher was already removed or made fail-closed in VD-1.

## Release integration and ownership sequence

```text
VD-1 renderer ─┬─> VD-2 historical/stubs
               ├─> VD-3 module Javadocs ─> VD-4 credential-free tracer
               └──────────────────────────> VD-5 pending Pages stage

VD-4 + VD-5 ─> #488 protected artifact publication ─> #488 public proof ─> VD-6 aliases
```

For a real RC/final, #456 validates the same values and produces only unlisted pending documentation. #488 owns authorization, signing/registry and plugin publication, public resolve-back, external proof, tag/release record, and incident recovery. The documentation stage receives a proof result, not a licence to infer one. A documentation-only correction after an accepted RC follows ADR 0012; source, release-configuration, or workflow change needs the next RC.

## Risks and non-goals

- Pages deployment semantics, retention limits, and workflow permissions are VD-5 checks; they do not change immutable-path/alias gates.
- A historical tag may lack a Javadoc artifact or renderable construct. The only response is labelled absence or errata, never snapshot rewrite or current-source substitution.
- Search indexes exact visible versions but must not discover unlisted pending/rejected paths.
- #456 neither designs an AnnoDocimal/KlumCast shared site nor adds IDE mirrors to public APIs. AnnoDocimal #71 and KlumCast #47 are links for deduplication only.
- No moving development site, documentation release branch, merged API namespace, release artifact publication, tag, GitHub release, or credential handling is part of this plan.

## Documentation and issue ownership

ADR 0013 is the decision source. This plan owns the delivery sequence. RELEASING.md records the #456/#488 handoff; feature owners migrate user pages when content ships. #469/#470–#472 use delivered versioned documentation as authority but do not implement hosting. This planning record changes no released user contract, so it adds no CHANGES.md entry.
