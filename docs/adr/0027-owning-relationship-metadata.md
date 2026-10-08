# Read owning Schema relationship metadata through state-specific Structure views

Date: 2026-10-08

Status: Proposed; entrypoint direction frozen by the maintainer, implementation not approved

Implementation status: Planning only; no runtime/API changes

Target: Untargeted

Tracking issue: [#856](https://github.com/klum-dsl/klum-ast/issues/856)

Implementation plan and refreshed investigation evidence:
[ADR 0027 implementation plan](../implementation/adr-0027-owning-relationship-metadata.md)

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0004](0004-asbuilder-composition-protocol.md), [ADR 0005](0005-generated-dsl-support-api.md),
[ADR 0006](0006-completed-object-support.md), [ADR 0010](0010-framework-public-interface-conventions.md),
[ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md), [ADR 0015](0015-generated-schema-runtime-linkage.md),
and [ADR 0020](0020-explicit-shared-model-builder-capabilities.md).

## Context and authority

An inherited relationship can need an annotation on its concrete owning Schema field to choose a provider binding.
The motivating ScHelm use case has direct composition, an inherited base-typed Facts relationship, declared provider
fields, and exact completed-target identity. The consumer wants to read its binding in AUTO_LINK and inspect source/default
annotations on the owning fields of completed provider candidates. Selection remains consuming Schema policy.

The #856 investigation at local commit `1715eab6` recommends structural metadata instead of extending LinkTo selection.
It demonstrates a viable fallback: owning-field DefaultValues transport into protected Builder-only state followed by an
inherited Default callback. That callback is later than AUTO_LINK and carries mechanical binding state. It also shows
that a completed child can have a model path and no explicit Owner value. Neither a path nor an Owner backreference is a
reliable substitute for the actual owning Schema declaration.

The maintainer accepted the investigation direction and froze these entrypoints for this planning task:

```java
KlumBuilderSupport.of(builder).getStructure();
KlumObjectSupport.of(model).getStructure();
```

Both Structure views expose a shared immutable `KlumSchemaRelationship`; absence-oriented relationship and annotation
queries return Optional. Builder ownership requests recheck the active session and a phase strictly after OWNER(15)
at operation time. These are planning constraints, not authorization to implement. This ADR replaces the local
investigation's candidate `KlumBuilderStructure.of(...)` spelling. Remaining proposals and approval gates are explicit
below and in the plan; no capability described here is currently shipped.

## Decision direction

### Separate state entrypoints and group capabilities

Introduce `KlumBuilderSupport` in the existing public `com.blackbuild.klum.ast.runtime` namespace. Its `of` accepts
the public `KlumBuilder<T>` marker and returns support whose only capability now is `getStructure()`. Use a private
constructor, as for `KlumObjectSupport`. Future capabilities belong behind this facade only when separately justified;
no validation, mutation, generic extension registry, or completed-Model extraction is part of #856.

Extend the existing `KlumObjectSupport.Structure<T>` with the same metadata queries. Preserve its completed-object gate
and existing Owner/path/traversal behavior. The Builder Structure view initially needs only the relationship/annotation
queries; it does not promise the completed view's entire navigation/traversal API. Any later Builder owner navigation,
hierarchy, or owner-dependent path method must use the same operation-time guard.

Do not add operations to the zero-operation `KlumBuilder` marker or generated `Foo_DSL.Builder` interfaces. Do not expose
`InternalKlumBuilder`, raw reflection Field, companions, Construction sessions, or generic metadata bags. These remain
implementation details. A consumer uses the supported facades in Java first:

```java
import java.util.Optional;
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship;

// builder is the active callback receiver after OWNER; facts is already completed.
Optional<KlumSchemaRelationship> relationship =
    KlumBuilderSupport.of(builder).getStructure().getOwningRelationship();
Optional<Binding> binding =
    KlumBuilderSupport.of(builder).getStructure().getOwningRelationshipAnnotation(Binding.class);
Optional<Source> source =
    KlumObjectSupport.of(facts).getStructure().getOwningRelationshipAnnotation(Source.class);
```

The corresponding Groovy calls keep Optional results:

```groovy
def binding = KlumBuilderSupport.of(builder).structure.getOwningRelationshipAnnotation(Binding)
def source = KlumObjectSupport.of(facts).structure.getOwningRelationshipAnnotation(Source)
```

`Binding` and `Source` are consumer-owned, runtime-retained field annotations, not new KlumAST annotations. These
fragments show the proposed client contract, not executable examples of a delivered feature.

### Describe the declaration, separately from its location

Propose these query signatures on both Structure views:

```java
Optional<KlumSchemaRelationship> getOwningRelationship();
<A extends Annotation> Optional<A> getOwningRelationshipAnnotation(Class<A> annotationType);
```

Propose `KlumSchemaRelationship` as a final framework-created immutable value with `getDeclaringClass(): Class<?>`,
`getName(): String`, and `<A extends Annotation> Optional<A> getAnnotation(Class<A>)`. Exact descriptors, including
the Builder facade/helper generic declarations and descriptor equality, remain approval gate D1. The recommended equality
is declaring Class identity plus member name, never path or receiver identity. No public constructor is needed.

The declaration is the field on the owning Schema Model type, including the original declaring class for an inherited
field. It is never a field inferred from the child's type or a generated Builder storage field. List/collection elements
and map values report their containing field's declaration; index/key belongs to structural or traversal location.
Do not add cardinality, candidate enumeration, annotation scanning, repeatable/meta-annotation expansion, or effective
generic target typing here. Requested runtime annotations use direct field lookup consistent with `Field.getAnnotation`;
missing annotations and valid objects without retained owning declarations return Optional.empty(). Null query arguments
are programmer errors, not absence. An existing declaration record that cannot resolve is an actionable failure.

The actual composition claim is authoritative independently of zero, one, transitive, converted, or several explicit
Owner values. LINK aliases cannot become ownership or replace metadata. OPTIONAL_LINK is evaluated per entry: only the
owned entry supplies a new declaration; aggregation keeps the target's original declaration. Repeating a target in a
container does not create a distinct declaration per position or guarantee a unique path.

### Retain identity through materialization without retaining construction state

Capture the exact owning Schema declaring type/member from the accepted composition claim or a qualified owned-copy
placement. Current CopyHandler list/map insertion can bypass claim normalization, so claim capture alone is not complete.
Qualify these paths before selecting the capture mechanism; do not invent an owner from first traversal order or blindly
normalize aggregation/copy inputs in a way that changes their semantics. Transfer that identity through
the private Builder ModelState/companion creation seam before completed validators run. Retain a small internal
serializable record, reconstructing the public view from the declaration. Do not serialize Optional, reflection Field,
annotation proxies, or the public facade as companion state. Do not retain a Builder, session, owner object pointer,
callback, or traversal path to answer the query. A retained immutable descriptor has no session dependence and may
be inspected after its creating Builder expires.

The captured declaration must correspond to the final accepted owning edge, including permitted claim transfer,
owned-copy placement, and late attachment. Traversal/Owner callbacks may corroborate it but are not the only capture path: Template definition and
value-only Template import materialize without ordinary OWNER lifecycle. Internal retention does not require the public
Builder guard to be bypassed. Completed queries never reconstruct a guessed declaration by parsing Role or saved paths.

Completed Objects support queries anywhere, including VALIDATE and outside a lifecycle. External linked Objects retain
their original declaration, even when the original owner is not serialized with them. An external root remains without
an owning declaration. Rehydrated Template/copy nodes describe the recipient graph's actual composition fields;
source graph metadata is not an instruction to adopt an old owner. A standalone copied root has no owning relationship.
The proposed Template policy is to retain the definition graph's own declarations internally, then recapture recipient
declarations during application (approval gate D2). Current `KlumObjectSupport.of` accepts ordinary completed Objects
and rejects marked Templates through `requireCompletedModel`; preserve that gate. Direct public Template inspection
would require a separate decision, not an accidental widening of the facade to read the new record.

Same-version ordinary/Template Java serialization must preserve declaration identity and relationship graph identity.
If an otherwise compatible stream lacks this record, return empty; do not claim it is necessarily a root. ADR 0004 makes
no arbitrary cross-version serialization guarantee. Changing companion state must audit computed serialVersionUIDs and
must not silently introduce a historical-stream promise. A promise to load particular older versions requires an explicit
versioned fixture and maintainer decision (D3). Jackson remains foreign-format import/export, not companion persistence:
new owned imports capture their new edges, reference imports preserve linked targets, and no wire metadata is added.

### Guard each live Builder ownership request

Each request first checks that the receiver is a supported, unsealed Builder in the current active Construction session
on this thread. It then checks the current numeric phase is strictly greater than OWNER(15) and strictly less than
INSTANTIATE(40). Only then may it read composition ownership or return an empty result. A numeric comparison is necessary
for custom phases. AUTO_LINK(20) is the first standard eligible phase; custom phase 16 is also eligible.

Construction/no executable phase, APPLY_LATER(1), AUTO_CREATE(10), OWNER(15), and every custom phase at or below 15 reject,
even for a root, an absent annotation, or a known claim. INSTANTIATE and later reject regardless of session liveness or
whether a particular Builder has already been allocated. Sealed wrappers around completed LINK targets reject as Builder
receivers; the completed target is queried through `KlumObjectSupport`. Template-definition Builders with no Construction
session reject. A view created early is allowed to be retained, but acquisition never grants lifetime authority: every
query rechecks. Views used after completion, abort, across sessions, or from another thread reject. Repeated queries during
phase changes cannot reuse a cached eligibility result or skip the guard because metadata was read before.

Use `KlumModelException` for invalid live Builder state and `KlumSchemaException` for an unresolvable retained Schema
declaration, subject to D1 descriptor review. Diagnostics identify the operation, receiver Schema type, current phase or
absence of an active session, and required state; include only safely available path context. For premature access:

```text
getOwningRelationshipAnnotation requires an active Builder phase after OWNER(15) and before INSTANTIATE(40); current phase: OWNER(15); receiver: Consumer
```

Do not read guarded ownership to enrich a rejection diagnostic. Session/state rejection precedes absence and declaration
resolution. Descriptor annotation reads inspect immutable Schema metadata and need no Builder/phase check.

### Leave target-selection policy with consumers

An inherited consuming AutoLink callback can read its owning Binding, inspect completed provider candidates' Source/default
annotations, select a completed Model by its own policy, and call the existing typed generated relationship method.
Explicit binding > unique candidate > requested-type default > ambiguity is the downstream investigation direction,
not a KlumAST selection algorithm or precedence contract. No LinkTo/LinkSource member, AUTO behavior, selector expression
language, OWNER_PATH reinterpretation, generic link method, or domain-specific annotation is introduced.

The dynamic completed Cluster reader and direct subtype-Builder setter limitation remain separate query/typing findings.
ADR 0020 forbids indiscriminate Model getter forwarding and DSL-bearing Builder.Query results. Proposed #180/ADR 0026
generic specialization is independently gated; #856 neither depends on nor implements it. #853 completed declarative
targets and #850/#855 sealed mutation hardening stay separately scoped. Published KlumCast supplies sufficient FieldNode
declaration context; no cross-project request is a demonstrated prerequisite.

## Consequences and acceptance boundary

The proposed public API is additive in the exported runtime package; private companion state changes require explicit
qualification. Generated Factory/Builder signatures and IDE source mirrors stay unchanged. Existing direct-Owner queries,
paths, composition traversal, immutable Model data, lifecycle ordering, and LINK identity must retain their contracts.
The old ADR 0010 paragraph describing a dynamic `KlumBuilder.link` capability is historical and inconsistent with the
current zero-operation marker; it does not authorize adding an operation in this task.

Require Java 17, dynamic/static Groovy 3/4/5 classpath, separately compiled inherited Schema/annotation consumers, and
Groovy 4/5 JPMS named modules without expanding internal exports or requiring new broad opens. Existing schema-module
openings needed for construction remain the baseline. Runtime metadata lookup must not need private-field value access.
The annotations artifact gains no dependency on the runtime. Documentation and release notes describe the feature only
when implementation is delivered. This ADR/plan PR leaves #856 open and untargeted.

## Rejected alternatives

- Extend LinkTo AUTO, OWNER_PATH, or selector now: changes established behavior and introduces unapproved selection policy.
- Add TYPED/LinkBinding/defaultFor vocabulary: consumer policy is not yet a general KlumAST requirement.
- Keep DefaultValues transport as the primary seam: viable workaround, but too late for AUTO_LINK and duplicates metadata.
- Infer the field from Owner values, Role, or a path: fails absent/multiple Owners, containers, inheritance, and aliases.
- Return a raw Field or companion: exposes access/mutation mechanics and freezes internal state.
- Add operations to KlumBuilder or generated Builders: widens every generated contract and invites schema-name collisions.
- Let KlumObjectSupport accept Builders: weakens the completed-state boundary of ADR 0006.
- Implement every Structure traversal/navigation twin now: exceeds the metadata use case and creates more lifetime contracts.

## Maintainer decisions still required

The entrypoints, shared immutable relationship type, Optional absence, per-operation guard, and selection deferral are
frozen for planning. The following decisions remain; no implementation slice starts by inference from this PR:

1. **D1 — approve the full metadata contract and implementation start.** Confirm exact query/descriptor signatures,
   generic shape, equality and failure categories, metadata-only initial Builder Structure, and rejection of sealed and
   Template-definition Builders under the proposed lifetime rules.
2. **D2 — approve Template retention semantics.** Confirm internal definition-edge retention and application/copy
   recipient-edge recapture while preserving public Template rejection. If direct Template inspection is required,
   decide its separate support/gating contract before expanding this plan.
3. **D3 — choose historical serialization scope.** Retain ADR 0004's same-version contract and empty lookup for a compatible
   absent record, or name specific old versions whose stream loading must be supported and proved.
4. **D4 — authorize scheduling/release placement.** Decide whether/when to start the dependent slices and whether metadata
   delivery is a child issue of #856. No evidence currently warrants a release milestone or closing the parent issue.
5. **D5 — settle copied-container ownership ambiguity.** RM-0 must characterize CopyHandler's direct list/map insertion
   and repeated recipe identity across distinct owned fields. Approve how an unclaimed copied node gains an authoritative
   declaration and how genuinely conflicting declarations fail or are represented, while preserving LINK/OPTIONAL_LINK
   semantics. Repeated positions within one field share its declaration; they do not require a unique path. No traversal
   order tie-breaker or unrelated ownership repair is approved by this plan.
