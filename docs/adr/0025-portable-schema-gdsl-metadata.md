# Portable Schema-owned IntelliJ GDSL metadata

Date: 2026-10-01

Status: Accepted delivery contract; proposed predicate representation and API spellings require confirmation

Implementation status: Planning only. No plugin API, metadata variant, or new IntelliJ discovery lifecycle is implemented.

Tracking issue: [#805 — Restore IDE completion for DelegatingScripts](https://github.com/klum-dsl/klum-ast/issues/805)

Implementation plan: [ADR 0025 implementation plan](../implementation/adr-0025-portable-schema-gdsl-metadata.md)

Parent decisions:

- [ADR 0005 — Generated DSL support namespace](0005-generated-dsl-support-api.md)
- [ADR 0011 — Shared multi-Groovy compatibility contract](0011-shared-multi-groovy-compatibility-contract.md)

Related: [ADR 0019 — Published consumer verification](0019-published-showcase-consumer-verification.md),
[ADR 0022 — Named-map safety](0022-conservative-named-map-safety.md).

## Context and authority

A bare `DelegatingScript` does not statically identify the Builder selected by its receiving runtime factory.
The interim [PR #809](https://github.com/klum-dsl/klum-ast/pull/809) recipe associates a deliberate filename suffix
with an existing public `Environment_DSL.Builder` in IntelliJ. Placing that contributor in ordinary resources also
ships it in a normal Schema or Model JAR. The existing root materialization task handles only framework GDSL;
the Model plugin has no equivalent IDE integration.

The maintainer confirmed portable-first delivery: reusable mappings belong to the Schema, published metadata is
opt-in, and the first delivery includes a separate Model-only build consuming a binary Schema. Explicit declarations,
editor-only resolution/materialization, a custom metadata capability, and an explicit classifier fallback are required.
Mappings are Schema-versioned and project-wide; conflicting versions, duplicate payloads, and overlapping predicates
must be rejected. Native IntelliJ discovery in source and binary topologies is a release gate.

The input comparison is the local report `docs/implementation/evidence/issue-805-local-versus-portable-gdsl-plan.md`
at `9f137a748018ac36c759acc0f87c31052774fdc6` (not in this branch's ancestry). It left overlap prevention to authors
and deferred generated declarations. **The confirmed rejection requirement supersedes that part of the comparison.**
Arbitrary executable GDSL cannot provide a decidable predicate-overlap contract simply by being namespaced or hashed.
The plan therefore proposes a bounded declaration format; that refinement has not yet been approved.

## Decision

### Portable-first ownership and opt-in

The Schema Developer owns the reusable editor mapping and versions it with the normal Schema GAV. The Schema plugin
exposes an explicit metadata opt-in; the Model plugin exposes explicit consumption opt-in and metadata dependencies.
There is no scanning of all Schema dependencies to discover metadata. The first complete delivery supports both
in-build Schema-to-Model consumption and a separate binary-only Model build, without applying the Schema plugin there.
A producer-only archive is an intermediate implementation step, not a deliverable release of #805.

Metadata has no independent release version. A mapping change, including removal, requires a new Schema version and
Schema release notes. Released classifier bytes are immutable. The KlumAST BOM still versions KlumAST, not a user's
Schema; a classifier introduces no extra BOM module. Model artifacts do not republish or propagate editor metadata.

### Separate published variant and lossy-consumer fallback

Use a manually defined consumable metadata configuration on the Schema's Java component, with a custom metadata
capability distinct from the normal library capability. It has no production dependencies or constraints and no
fake Java feature/source set. The proposed attributes and exact names are listed in the implementation plan.

Gradle Module Metadata carries the additional variant and its capability. Maven carries one explicitly enabled
`gdsl` classifier JAR alongside the Schema's normal artifact, with the same GAV. A POM cannot describe this selection
contract. Lossy/POM-only consumers must explicitly request the exact `gdsl` classifier; there is no automatic fallback
to the ordinary JAR and no silent success when requested metadata is missing. Non-Gradle consumers resolve, extract,
register, and activate this editor content manually; native Maven IDE integration is not promised.

Only enabled Schema metadata publication adds that archive/variant. Normal Schema/Model JARs, sources/Javadocs,
`apiElements`/`runtimeElements`, and POM dependency graphs remain free of the new metadata. Existing framework GDSL
already shipped in `klum-ast-runtime` is unchanged.

### Editor-only resolver and one materialization owner

Metadata dependencies live in dedicated scopes/resolvers. They never extend from or into ordinary compile, runtime,
test, resource, or JPMS inputs. Validate metadata origin/version against the actual selected normal Schema before
materialization, including the union of imported projects affected by the active project-wide mappings.
The resolver is lazy: ordinary compilation, tests, JAR creation, and IDE model construction do not resolve it.
Enabled Schema publication builds its metadata archive; it does not run editor materialization.

Reuse root `materializeKlumDslGdsl` and `build/generated/klum-dsl-ide/gdsl`. One cacheable sync owns framework and
Schema metadata. Each participating Schema/Model module registers that shared root as IDEA generated resource content,
outside all Gradle SourceSets. Source mirrors remain local Schema-owned IDE projections; external Schemas use real
compiled public Builder contracts. An included build never writes into another build's root output.

Validate the complete input union before changing output. Repeated references to the same component/artifact across
Models are idempotent; distinct duplicate payload registrations, duplicate archive entries, different payloads for one
origin, conflicting Schema versions, and overlapping rules are errors. Never use copy order, an override, or longest
suffix as priority. Removal, upgrade to empty metadata, and disablement remove obsolete output on an executed refresh.
Do not use `@SkipWhenEmpty`. A failed refresh leaves prior bytes as stale output, not as a valid new result.

### Project-wide predicates: proposed enforceable v1

**Proposal, requiring confirmation before implementation:** support a finite catalog of case-sensitive literal
filename suffixes ending in `.groovy`, each paired with one public `*_DSL.Builder` qualified name and a stable
Schema-local mapping ID. Generate the script-scoped GDSL from these declarations and publish the catalog with it.
Gradle never executes supplied GDSL. Arbitrary predicates, raw contributor files, regular expressions, per-directory
scopes, Model-local overrides, and transitive discovery are outside this first format.

Two suffix predicates overlap exactly when either suffix ends with the other; reject that pair even if their targets
are equal. Normalize/validate declarations before this check and reject duplicate IDs/entries. Verify generated
payloads against the declared catalog so an arbitrary contributor cannot merely claim a narrower predicate.
The consumer validates every catalog in the root union, including the producer's local source-authoring contribution.
Coordinates separate physical paths; they never restrict a contributor to one IDE module. Disabling one Model's opt-in
does not guarantee that project-wide rules still supplied by another module cease to affect its scripts.

The mapping supplies an editor hint, not a runtime type declaration. It must resolve the real public Builder; a missing
class contributes no invented operations. Calling the recipe through another runtime factory remains an authoring
error; this design introduces no runtime suffix check. Runtime truth is proved with the actual receiving factory and
root/owned-child recipe paths. A need for enforceable runtime typing belongs to #269.

### Migration and native release gate

Remove prior #809 manually copied resources before enabling the new transport: move the mapping to the approved
declaration, remove its resource copy, rebuild/publish a new Schema version, then refresh/reimport IDEA. Reject known
legacy copies in the participating normal inputs/artifacts during refresh; document the detection boundary for
unrelated arbitrary GDSL. A raw contributor with an unrecognized predicate is not admitted through a waiver that
would defeat overlap rejection. The implementation plan makes that migration boundary executable.

Do not release this facility until fresh native IntelliJ Gradle-import evidence establishes discovery, activation,
completion, and method resolution for source mirrors and a separate binary Schema consumer, including removal and
negative controls. `.iml` checks, GroovyShell stubs, and native PSI contributor-logic tests complement this gate but
cannot replace the import test. If root `build/` exclusion defeats registration, propose a supported editor-only
registration fix; adding metadata to production resources is not an acceptable remedy.

## Consequences

- The new public surface is Gradle configuration and a versioned editor payload format. Exact new spellings and the
  bounded predicate format remain proposals; existing plugin IDs, extensions, and refresh task names remain stable.
- No AST annotation, generated Script superclass, Builder interface implementation, compiler semantics, serialization,
  lifecycle phase, ownership rule, runtime ABI, or JPMS requirement changes. GDSL consumes the real generated contract.
- Groovy 3/4/5 fixtures must compile and execute the same recipe behavior independently. The Gradle plugin's Spock suite
  remains Groovy 3; its external consumer fixtures select each supported generation explicitly.
- Native IntelliJ evidence is version-specific. Eclipse DSLD (#14), VS Code (#808), typed script APIs (#269), and
  AnnoDocimal Quick Documentation remain distinct; none is implicitly delivered by this archive.
- Implementers must stop before freezing the new API/format until the refinement is confirmed. The accepted portable
  delivery, isolation, and rejection requirements do not need to be reopened to make that choice.

## Rejected alternatives

**Local-first or archive-only first release.** Neither delivers the confirmed binary Schema-to-Model use case.

**Ordinary resource packaging or putting metadata on compile/runtime classpaths.** This reproduces #809's interim
transport and violates the selected editor-only contract.

**Java feature variants or an empty metadata SourceSet.** These introduce irrelevant compile/resource/library
lifecycles. A custom outgoing variant expresses the actual non-runtime contract.

**Automatic dependency scanning, silent classifier retry, or Model-local priority.** These conceal opt-in, missing
metadata, version drift, and project-wide ambiguity.

**Raw arbitrary GDSL with only filename collision checks or author-declared predicate claims.** Physical names and
hashes do not prove semantic disjointness or that executable predicates match their declaration. The proposed bounded
format is the smallest identified way to make the confirmed overlap gate enforceable.

**Generate a typed Script API or implement Builder on a script.** That broadens public/runtime contracts under #269
and conflicts with ADR 0005's Builder implementation boundary; it is unnecessary for the selected editor transport.
