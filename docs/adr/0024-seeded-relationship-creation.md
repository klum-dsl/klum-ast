# Seeded relationship creation

Date: 2026-09-30

Status: Proposed — fresh-copy operation confirmed; type-domain and keyed-Template boundaries need maintainer review

Implementation status: Design only; no runtime, compiler, or generated API implementation

Target: 4.1

Tracking issue: [#342 — Combine withTemplate and apply](https://github.com/klum-dsl/klum-ast/issues/342)

Implementation plan: [ADR 0024 implementation plan](../implementation/adr-0024-seeded-relationship-creation.md)

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0004](0004-asbuilder-composition-protocol.md), [ADR 0005](0005-generated-dsl-support-api.md),
[ADR 0015](0015-generated-schema-runtime-linkage.md), [ADR 0016](0016-template-creation-and-scoped-application.md),
and [ADR 0017](0017-published-template-test-support.md).

## Context and authority

Issue #342 originally suggested `bedroom(template) { ... }` and root factory conveniences, and asked about inferring a
concrete type from a recipe. Its 2026-09-30 maintainer comment identifies current Schelm relationship-creation demand and
retargets the issue to 4.1. Historical names and root overloads are not an accepted contract.

The design investigation initially considered bounded default scoping. During this task, the maintainer narrowed the
request to exactly one DSL setter form: one materialized Model, live Builder, or Template plus a refinement closure.
The seed is never assigned as the relationship value; it contributes configuration to a newly created Builder. This
later instruction is the authority for the proposal below. It supersedes this task's earlier scoped-default direction.

Existing `copyFrom` already distinguishes values-only completed Models, marked Template recipes, and live same-session
Builder snapshots. It does not choose a relationship target type or allocate its receiving Builder. Existing closure
creators reuse an occupied single child or map entry. Neither behavior alone gives the requested always-fresh operation.

Collection factories already expose #135's `withTemplates(Iterable) { ... }`: immediate expansion of one child per
Template. Generated `Foo_DSL.TemplateScope` owns `Foo.Template.With/WithAll`; the test-support `TemplateScope` owns a
bounded test lifetime. These are existing operations with distinct roles, not naming candidates for the new setter.

## Decision direction confirmed by the maintainer

### One operation with two arguments

Add the semantic form `relationship(seed) { refinement }` wherever an owned DSL relationship already has a generated
creator: a direct child setter or collection/map element adder, including its forwarding Collection/Cluster factory
entrypoint. It takes exactly one seed and a required closure; `{}` means copy without further refinement. It returns the
fresh public child Builder, as other construction-time relationship creators do.

Do not add seed-list, closure-free, named-map, explicit-type/key, root `Create.With(seed, ...)`, `WithTemplate`,
`fromTemplates`, `useTemplates`, or `installTemplates` variants as part of #342. A Template is an instance of a Model
class with marked identity, so it needs no separate public Template overload. Model and Builder source categories may
need separate JVM overloads, but those must represent this same two-argument language; the generated descriptor choice
is an acceptance gate, not permission to add a second operation.

Illustrative direct-Schema use; this syntax is proposed, not available today:

```groovy
import com.blackbuild.klum.ast.DSL

@DSL
class Room {
    String label
    int temperature
}

@DSL
class Flat {
    Room bedroom
    Room storage
    Room spare
}

def warm = Room.Create.Template.With(temperature: 20)
def existing = Room.Create.With(label: 'source', temperature: 15)

def flat = Flat.Create.With {
    def configured = bedroom(warm) { label 'Bedroom' }
    storage(existing) { label 'Storage' }
    spare(configured) { label 'Spare' }
}

assert flat.bedroom.temperature == 20
assert flat.storage.temperature == 15
assert flat.spare.temperature == 20
assert !flat.storage.is(existing)
assert !flat.spare.is(flat.bedroom)
```

The live Builder example uses a child already owned in the same active Construction session as its source. Copying it
does not move its ownership claim. This is meaningful for Layer 3 concrete named relationships, not just collection
factories. Generic completed-model clients still consume the Domain API and do not depend on Builders.

### Freshness, source identity, and lifecycle

Each invocation allocates a new ordinary child Builder in the recipient's Construction session, copies the seed under the
existing copy policies, refines the fresh Builder once, and attaches it through the relationship's normal ownership path.
The source is neither adopted nor mutated. Repeated invocations must never take the existing-child configuration branch.
For single values, normal setter replacement applies; for collections, normal append/set equality applies; for maps,
normal key derivation and duplicate replacement apply. Replacement must use existing bookkeeping for ownership and paths,
without permitting a displaced Builder to acquire a second owner. This requires explicit lifecycle coverage.

| Source | Copied contribution | Constraint |
| --- | --- | --- |
| Ordinary completed DSL Model | Current values; no deferred action replay | Explicit seed role authorizes a fresh copy, never implicit adoption |
| Marked materialized Template | Values plus immutable recipe replay | Template identity remains on the source, not on an ordinary recipient |
| Live Builder | Current values plus a detached snapshot of still-pending actions | Unsealed, same active Construction session as recipient; source need not be unclaimed |

Reject sealed, cross-session, or inactive-session Builder sources. A caller can use the completed Model for a values-only
copy instead. Do not mark a live Builder as a Template or serialize its ephemeral action snapshot. Ordinary Model
mutation remains unavailable. Simple Values retain existing copy semantics; this operation does not promise a deep clone
of arbitrary user objects. Keys, Owners, Roles, ignored/transient state, composition graphs, and aggregation edges retain
`CopyHandler` rules rather than a second clone implementation.

The lifecycle order follows existing nested creation: source initializers; currently active Template defaults in hierarchy
order; `PostCreate`; explicit seed copy; refinement closure; `PostApply`; outer graph phases; materialization; validation.
Recipes schedule into the recipient's lifecycle and cannot schedule at or after phase 40. Template recipe closures must
not capture Builders; live-source snapshots retain the existing ephemeral capture rules without new serialization checks. The closure has the public child Builder as a `DELEGATE_ONLY` delegate and receives no seed
argument. It runs once, not once per copied descendant.

During Template definition, materialized Model/Template seeds use the existing Template-mode child construction path,
with its lifecycle omissions and marked graph. Live Builder seeds remain subject to the existing active-session source
rule; do not silently make Template-definition Builders a new accepted live-source category.

### Composition and existing scopes

Generate the form for composition and `OPTIONAL_LINK` relationships. Every seeded `OPTIONAL_LINK` invocation takes the
owned-composition path even when its source is a completed Model that could be an aggregation value in another overload.
Do not generate it for aggregation-only `LINK`: a fresh Builder is not an existing link target. Existing one-argument
relationship setters retain their current link/adoption behavior.

Seed application opens no ambient Template frame and registers no default. `Foo.Template.With/WithAll` and test-support
`TemplateScope` defaults apply to fresh creation exactly as today, before explicit seed copying; seed values and explicit
refinement follow existing copy/overwrite policy. An active Template that is also passed as a seed follows the same
sequence as today's `child { copyFrom activeTemplate }`; no identity-based deduplication or new precedence is implied.

Multiple and nested seeded calls therefore have no registration stack to clean up. Existing Template scopes keep their
thread-local restoration and creation-time Builder snapshots for later phases. Failure must not alter those scopes.
This is compatible with both published scoped APIs precisely because it is an explicit copy input, not a second scope
or registry. #304 remains the separate ordered root-layer coordinator; this child operation introduces no competing
`Compose` or heterogeneous layer protocol.

## Proposed type selection — requires confirmation

Resolve the source's Model type using framework metadata: completed Models use their DSL type, live Builders use their
Model type, and a synthetic abstract Template uses its original abstract DSL type, never its artificial implementation.
Do not select a generated synthetic Template implementation for an ordinary recipient.

The recommended policy follows the historical use case while keeping Schema constraints authoritative:

| Relationship and seed | Recommended recipient |
| --- | --- |
| Seed is a concrete subtype assignable to declared relationship type | That concrete seed type |
| Seed is the relationship type or its superclass; relationship/default implementation is concrete | Relationship's existing concrete/default implementation |
| Synthetic abstract Template for an ancestor | Relationship's concrete/default implementation; copied base configuration only |
| Abstract relationship and abstract seed, without a concrete/default implementation | Error with guidance to use the ordinary explicit-type creator plus `copyFrom` |
| Unrelated types, sibling types incompatible with the concrete relationship, or non-DSL input | Error before refinement or attachment |

Honor field-level default implementation as well as the DSL type's default implementation. Shared field names alone do
not establish compatible types. Do not extend this operation to Maps, arbitrary POJOs, Classes, or Factory tokens; those
already have other input semantics.

Superclass seeds are the substantive open choice: #135 currently requires each Template to be an instance of the declared
collection element type, whereas #342's original single-child example contemplated a base recipe for a concrete child.
Accepting a base seed is useful for Cluster projections but needs an explicit runtime domain check and a truthful static
signature. A broad `Object` parameter makes the base case possible but weakens type checking; a declared-child Model
parameter excludes it. Do not silently invent a framework-wide Model/Builder union type to resolve this.

Proposed Layer 3 syntax if base seeds are accepted:

```groovy
@DSL
abstract class HeatedRoom { int temperature }

@DSL
class Bedroom extends HeatedRoom { String label }

@DSL
class Flat {
    Bedroom bedroom
    // Other concrete room fields can be exposed through the existing @Cluster API.
}

def heated = HeatedRoom.Create.Template.With(temperature: 20)
def flat = Flat.Create.With {
    bedroom(heated) { label 'Bedroom' }
}
```

This creates `Bedroom`, not an abstract `HeatedRoom` or synthetic Template Model. A real Layer 3 documentary acceptance
must include distinct abstract Domain API types and a meaningful `@Cluster` projection, as in Catwalk's smart-home
journey; the reduced example above isolates only the type-selection question.

## Key and failure boundaries

Keys must be resolved before Builder allocation. Proposed minimal policy: retain an existing relationship key provider,
otherwise use an available seed key when the selected recipient is keyed; map `keyMapping` remains the normal attachment
rule. Never copy an Owner/Role from the seed. Marked Templates are unkeyed, so `(template, closure)` cannot supply a new
arbitrary key to a keyed child. The refinement closure cannot repair that missing construction identity.

Confirm whether accepting this limitation is sufficient for #342. If arbitrary keyed Template creation is required,
stop: it would require a separately approved input convention beyond the chosen two-argument form. Do not add a key or
map overload by inference. The established explicit-key creator with `copyFrom` remains the workaround.

Validate recipient mutability, non-null seed/closure, source category/session, type compatibility, and required key before
user code or attachment. Use `KlumModelException` with relationship, source kind/type, and actionable guidance for domain
errors. A null seed is rejected even though bare `copyFrom(null)` currently does nothing. Static unsupported calls may
fail compilation normally; dynamic valid-shape calls require the same runtime checks. A copy/refinement failure leaves
that newly created child unattached and any existing relationship value intact. There is no promise to roll back arbitrary
user closure side effects or the entire Construction session when a caller catches an exception.

## Compatibility and consequences

The confirmed operation adds construction-time capability without changing completed Model or Template serialization,
root Factory descriptors, TemplateScope state, or existing `copyFrom` behavior. Seeds retain their existing serialization
contract, and completed results retain neither the source Builder nor a new scope/recipe wrapper.

Do not reinterpret existing one-argument setter calls or existing Map, Class, generated Factory-token, converter, and
closure creators. Before acceptance, inventory two-argument signatures and custom Schema methods: an additive overload
must not silently redirect a formerly valid converter/custom call. A real collision needs a maintainer compatibility
choice or targeted Schema-compilation diagnostic. Generated public return/delegate types may advertise only the declared
relationship Builder (not a guaranteed runtime subtype); exact subtype completion is available through existing explicit
Factory-token creation plus `copyFrom`. Java, static Groovy 3/4/5, source mirrors, generated Javadocs, and GDSL must agree.

## Alternatives rejected by the current direction

- Bounded `withTemplates(...) { ... }` defaults: existing scoped APIs already cover that purpose; #135 also owns the
  collection name with different semantics. The maintainer now requests an explicit one-seed child operation.
- In-place `useTemplates`/`installTemplates`: introduces registry lifetime/cleanup rather than a fresh copy input.
- Assigning, linking, or adopting the seed: directly contradicts the required freshness contract.
- Calling the ordinary occupied-child creator then `copyFrom`: can mutate/reuse a child and does not guarantee freshness.
- Additional root/list/Map/type/key convenience families: broaden the single chosen language and revive historical scope.
- Duplicating recipe capture/copy machinery: creates a second source protocol and risks incompatible lifecycle semantics.

## Acceptance and stopping boundary

The fresh-copy operation and one-seed-plus-closure language are confirmed. Type-domain/static-descriptor choice and the
keyed-Template limitation remain proposed. This ADR must not be marked Accepted or implemented until those choices and
signature collision evidence are reconciled. The accompanying plan maps all confirmed requirements and the proposed
branches to small executable slices. Issue #342 stays open; this design document makes no release delivery claim.
