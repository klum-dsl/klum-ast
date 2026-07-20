# Shared prerelease-channel policy

Date: 2026-07-19

Status: Accepted

Related work: [#488 — Audit, document, and improve the release and publishing process](https://github.com/klum-dsl/klum-ast/issues/488),
[KlumCast #38 — Codify release and prerelease procedures](https://github.com/klum-dsl/klum-cast/issues/38), and
[AnnoDocimal #44](https://github.com/blackbuild/anno-docimal/issues/44),
[#45](https://github.com/blackbuild/anno-docimal/issues/45), and
[#47](https://github.com/blackbuild/anno-docimal/issues/47).

## Context

KlumAST 4.0 needs validation from clean, external consumers after KlumCast 0.4 and AnnoDocimal 1.0 are available.
The three repositories publish different artifact sets and retain local release machinery, but an incompatible vocabulary
for prereleases would make a claimed consumer validation result ambiguous. The earlier 4.0 release-plan record made the
same policy locally; this ADR is the durable shared baseline.

## Decision

### Channel roles

| Channel | Shared role |
| --- | --- |
| Snapshot, development snapshot, immutable snapshot | Developer integration only; never the named remote consumer-validation result. |
| Central/Nexus staging | Signing and repository-acceptance gate; not an external-consumer channel. |
| Release candidate (RC) | The sole supported public prerelease channel. Every published product coordinate and applicable plugin marker is publicly resolvable, immutable, and validated by a clean external consumer. |
| Final | The formal release, published and independently re-verified. |
| Alpha / beta | Unsupported unless a later, explicit cross-repository decision defines their purpose and recovery rules. |

An RC validates its source commit and release configuration; it does not prove that differently versioned final artifacts
are byte-identical. RCs are short-lived validation artifacts, normally only a small number per release. There is no hard
cap: an unusually complex release such as KlumAST 4.0 may need more. They are not a long-term production release channel.

### Version, promotion, and recovery

Nebula Release's monotonic candidate identity is the shared model: `X.Y.Z-rc.N` uses an immutable tag and a strictly
increasing `N`. RCs are never relabelled, replaced, or promoted. A final `X.Y.Z` is a distinct publication from the exact
commit accepted for the last RC. A substantive source or release-configuration change requires the next RC; a
documentation-only change may proceed without another RC. Any other exception requires an explicit recorded decision
stating the changed validation claim and compensating evidence.

Publication must make the complete product available as atomically as each registry permits; no partial artifact set or
plugin-marker set is a successful candidate. Used artifact versions are burned. A published or tagged RC is never
recovered: a rejected or partial RC remains immutable, is recorded as failed or superseded, and the next attempt uses
`rc.N+1`. A final's remote tag may exceptionally be deleted only by an explicit human decision when no artifact was ever
published. Local runbooks must state the registry-specific staging, abort, and retry mechanics without weakening these
rules.

### Authorization and validation

Every public RC and final release requires explicit human authorization through a protected release path. Ordinary CI
performs unprivileged validation. After authorization it may automate release steps, but signing and publishing credentials
exist only in protected environments and must never be exposed to pull-request/fork workflows or logs. Final authorization
is separate from RC authorization.

External consumers pin the exact published version, use clean caches and no composite build, `mavenLocal`, local
repository, or unpublished included build, and resolve each repository's complete public product from its real consumer
repositories. KlumCast additionally proves its Groovy 3/4/5, classpath, and JPMS consumer contracts; AnnoDocimal proves
its six artifacts and two plugin markers; KlumAST proves all Maven artifacts and its three declared plugin markers.

## Ownership

This ADR defines only the shared vocabulary and safety boundary. It does not standardize Gradle commands, credentials,
release branches, documentation hosting, or registry implementation.

- KlumAST #488 owns its `RELEASING.md`, protected publication path, consumer fixtures, and recovery procedure.
- KlumCast #38 owns `docs/agents/releases.md`, the three-artifact procedure, and its public consumer evidence.
- AnnoDocimal #44, #45, and #47 own publication smoke tests, the rehearsed runbook, and its 1.0 release gate.

Each local record links here rather than reproducing this policy.
