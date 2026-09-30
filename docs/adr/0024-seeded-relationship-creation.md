# Seeded relationship creation

Date: 2026-09-30

Status: Proposed — fresh-copy operation and seed descriptors confirmed; keyed-Template boundary needs maintainer review

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
class with marked identity, so it needs no separate public Template overload. Exactly two JVM overloads represent this
same two-argument language: one Model seed and one Builder seed, both based on the relationship's highest DSL superclass
as specified below. They are two source-state signatures for one operation, not separate DSL forms.

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

The lifecycle order follows existing nested creation: recipient initializers; currently active Template defaults in hierarchy
order; `PostCreate`; explicit seed copy; refinement closure; `PostApply`; outer graph phases; materialization; validation.
Recipes schedule into the recipient's lifecycle and cannot schedule at or after phase 40. Template recipe closures must
not capture Builders; live-source snapshots retain the existing ephemeral capture rules without new serialization checks.
The closure has the public child Builder as a `DELEGATE_ONLY` delegate and receives no seed argument. It runs once, not
once per copied descendant.

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

## Confirmed seed domain and descriptors

The maintainer selected one common domain for both seed states: the relationship's **highest DSL superclass**, or the
relationship type itself when it has no DSL ancestor. For a collection/map element, apply this rule to the element Model
type. Derive this one class from the relationship's declared Model hierarchy, not from the runtime seed subtype or a
field's default implementation. There is no per-ancestor overload enumeration.

For a `Bedroom` relationship whose highest DSL superclass is `HeatedRoom`, generate exactly these seed signatures on the
owning public Builder and applicable forwarding factory contracts:

```java
Bedroom_DSL.Builder<Bedroom> bedroom(HeatedRoom seed, Closure<?> refinement);
Bedroom_DSL.Builder<Bedroom> bedroom(HeatedRoom_DSL.Builder<? extends HeatedRoom> seed,
                                   Closure<?> refinement);
```

Both closures carry `@DelegatesTo(Bedroom_DSL.Builder)` with `Closure.DELEGATE_ONLY`. The Builder source wildcard accepts
Builders for `HeatedRoom` and its descendants through the existing self-typed Builder inheritance contract; the return
and delegate remain the declared recipient's public Builder. The erased parameter descriptors are `(HeatedRoom, Closure)`
and `(HeatedRoom_DSL.Builder, Closure)`, and both return the public `Bedroom_DSL.Builder`. A relationship with no DSL
ancestor uses its own Model and corresponding public Builder in those two seed positions.

This admits ancestor seeds, `Bedroom` seeds/subtypes, and sibling seeds sharing `HeatedRoom`. An unrelated domain is
rejected statically rather than admitted through an `Object`/`KlumModelObject` fallback. Dynamic invocation must enforce
the same domain and existing live-Builder eligibility. A marked Template enters through the Model signature; synthetic
Template implementations must be normalized to their original Model type for recipient selection. Do not introduce a
marker/union source API, generic `KlumBuilder` fallthrough, or additional signatures for intermediate DSL ancestors.

Source admissibility and recipient selection are distinct. A sibling is an accepted seed, not a replacement for the
Schema's declared child type:

| Relationship and seed | Recipient selection |
| --- | --- |
| Seed is a concrete subtype assignable to declared relationship type | That concrete seed type |
| Seed is an ancestor or sibling within the selected highest-DSL domain; relationship/default implementation is concrete | Relationship's existing concrete/default implementation |
| Synthetic abstract Template within the selected domain | Relationship's concrete/default implementation; never the synthetic implementation |
| Abstract relationship and seed with no compatible concrete/default implementation | Error with guidance to use the ordinary explicit-type creator plus `copyFrom` |
| Seed is outside the selected highest-DSL domain or is non-DSL input | Unsupported seed call; static rejection or dynamic domain diagnostic before refinement/attachment |

Honor field-level default implementation as well as the DSL type's default implementation when selecting a concrete
recipient. Shared field names do not admit unrelated domains. Sibling copying preserves existing `copyFrom`/`@Overwrite`
policy: an accepted sibling seed can still fail a normal copy check when it contributes fields absent from the recipient;
`CopyHandler` currently defaults missing fields to `FAIL`. This descriptor decision does not silently discard sibling-only
fields or change missing-field policy. A sibling with compatible configuration demonstrates the admitted domain.

Choosing a different domain ancestor, or skipping a technical/non-domain DSL ancestor, is a future interface/domain-modeling
concern explicitly outside #342. The highest DSL class is authoritative here even when its name or intended role seems
technical; the generator must not guess which ancestor is a better domain boundary.

Illustrative syntax for the confirmed base-seed domain:

```groovy
@DSL
abstract class HeatedRoom { int temperature }

@DSL
class Bedroom extends HeatedRoom { String label }

@DSL
class StorageRoom extends HeatedRoom { }

@DSL
class Flat {
    Bedroom bedroom
    // Other concrete room fields can be exposed through the existing @Cluster API.
}

def heated = HeatedRoom.Create.Template.With(temperature: 20)
def storage = StorageRoom.Create.With(temperature: 12)
def flat = Flat.Create.With {
    bedroom(heated) { label 'Bedroom' }
    bedroom(storage) { label 'Copied storage defaults' }
}
```

Each call creates a fresh `Bedroom`, not an abstract `HeatedRoom`, sibling `StorageRoom`, or synthetic Template Model.
The second call replaces the first child through the normal setter attachment path. A real Layer 3 documentary acceptance
must include distinct abstract Domain API types and a meaningful `@Cluster` projection, as in Catwalk's smart-home
journey; the reduced example above isolates the source-domain and recipient-selection contract.

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

The fresh-copy operation, one-seed-plus-closure language, and exactly two highest-DSL-domain source descriptors are
confirmed. The keyed-Template limitation remains the product decision gate. This ADR must not be marked Accepted or
implemented until that choice and signature collision evidence are reconciled. The accompanying plan maps all confirmed
requirements and the proposed branches to small executable slices. Issue #342 stays open; this design document makes no release delivery claim.
