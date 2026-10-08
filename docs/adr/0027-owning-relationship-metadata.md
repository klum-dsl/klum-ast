# Read owning Schema relationship metadata through state-specific Structure views

Date: 2026-10-08

Status: Accepted D1/D2/D3/D4/D6; D5 copied-container/alias policy remains gated

Implementation status: RM-1 and the approved bounded RM-2 subset implemented; D5 and RM-3/RM-4 acceptance remain pending

Target: 4.1 (D4 approved)

Tracking issue: [#856](https://github.com/klum-dsl/klum-ast/issues/856)

Implementation plan and refreshed investigation evidence:
[ADR 0027 implementation plan](../implementation/adr-0027-owning-relationship-metadata.md)

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0004](0004-asbuilder-composition-protocol.md), [ADR 0005](0005-generated-dsl-support-api.md),
[ADR 0006](0006-completed-object-support.md), [ADR 0010](0010-framework-public-interface-conventions.md),
[ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md), [ADR 0015](0015-generated-schema-runtime-linkage.md),
and [ADR 0020](0020-explicit-shared-model-builder-capabilities.md).

## Approval and current evidence

The maintainer approved D1's exact generic/equality/error contract in the RM-0 decision session, D4 placement in 4.1,
and D6's read-only lifetime revision. RM-1 was then separately authorized from current master. Its executable evidence
and bounded implementation are recorded in [the RM-1 report](../implementation/issue-856-rm1-evidence.md).
The retained RM-0 authority is local `6c7d2efab4b10bbc6ee92d3932c8731a6c46cd5a` on
`codex/issue-856-rm0-proof`, including its report and 33 characterization cases.
The maintainer approved D2 internal accepted-definition retention with recipient recapture and D3 same-version-only
serialization before bounded RM-2 implementation; see [the decision record](../implementation/issue-856-rm2-decisions.md).
D5 copied-container/alias repairs are explicitly excluded from that assignment and remain an acceptance gap.
The implemented subset and validation are recorded in [the RM-2 evidence](../implementation/issue-856-rm2-evidence.md). Initial documentary guidance describes the delivered seam; RM-4 still owns complete release acceptance.

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
at operation time. D1 and D6 approve the exact descriptor and per-request read lifetime for the bounded ordinary tracer.
The earlier unsealed/phase-before-40 restriction is superseded. This ADR replaces the local investigation's candidate
`KlumBuilderStructure.of(...)` spelling. Remaining graph/persistence policies and release gates stay explicit below.

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
fragments show the approved client contract; the RM-1 documentary tracer exercises both routes.

### Describe the declaration, separately from its location

Use these query signatures on both Structure views:

```java
Optional<KlumSchemaRelationship> getOwningRelationship();
<A extends Annotation> Optional<A> getOwningRelationshipAnnotation(Class<A> annotationType);
```

Provide `KlumSchemaRelationship` as a final framework-created immutable value with `getDeclaringClass(): Class<?>`,
`getName(): String`, and `<A extends Annotation> Optional<A> getAnnotation(Class<A>)`. D1 approves the Builder facade as
`KlumBuilderSupport<T>.of(KlumBuilder<T>)` returning `Structure<T>`; the completed facade keeps its existing generic shape.
Equality is declaring Class identity plus member name, never path or receiver identity. No public constructor is needed.

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
The approved D2 policy retains existing accepted definition-edge declarations internally, then recaptures recipient
declarations during application. It does not repair CopyHandler placements that bypass claims or decide conflicting aliases. Current `KlumObjectSupport.of` accepts ordinary completed Objects
and rejects marked Templates through `requireCompletedModel`; preserve that gate. Direct public Template inspection
would require a separate decision, not an accidental widening of the facade to read the new record.

Same-version ordinary/Template Java serialization must preserve declaration identity and relationship graph identity.
If an otherwise compatible stream lacks this record, return empty; do not claim it is necessarily a root. ADR 0004 makes
no arbitrary cross-version serialization guarantee. Changing companion state must audit computed serialVersionUIDs and
must not silently introduce a historical-stream promise. D3 explicitly chooses same-version serialization only. No
particular older stream is promised; any later historical support would require named versioned fixtures and a new maintainer decision. Jackson remains foreign-format import/export, not companion persistence:
new owned imports capture their new edges, reference imports preserve linked targets, and no wire metadata is added.

### Separate mutation eligibility from metadata authority

The source audit at `513cfcdd` provides no ownership-authority reason for the earlier numeric upper bound or blanket sealed
receiver rejection. InternalKlumBuilder.allocateModel sets completedModel and sealed; it neither clears the composition
claim nor completes the Construction session. Materialization collects the graph, allocates all Models, then assigns
relationships. InstantiatePhase replaces the phase root only after both passes. PhaseDriver retains the registered
Builders and their active session membership through later actions, including COMPLETE; its outer lifecycle finally
calls leave/completeConstructionSession on success or exception. CleanupPhase does not clear Builder ownership.

Mutation preflight is a separate check: assertMutable rejects sealed or closed-session Builders. BuilderVisitingPhaseAction
also forbids traversal actions at/after 40 and skips sealed receivers. These are mutable traversal rules, not evidence
that a retained receiver's declaration becomes unreliable. Later ModelVisitingPhaseAction callbacks receive Models,
not Builders; a Builder can nevertheless remain an active-session metadata receiver when retained by a closure or
extension. This proposal adds no late Builder callback, traversal, creation, or mutation capability.

At phase 40 an action ordered before InstantiatePhase still has a Builder graph. During allocation, individual Models
and their companions become available internally, but the relationship graph is incomplete until the second pass ends.
A facade over an already allocated ordinary Model can pass the current completed-companion gate; that gate alone does
not establish graph completion. After InstantiatePhase returns, the completed Model root is available to later actions
at that number and to phases above 40. Neither partial allocation nor the availability of a Model invalidates declaration
metadata retained on its Builder. Clients should normally use Model support in later Model callbacks and after the root
factory returns. No Model extraction operation is added to Builder support.

FactoryHelper.wrapCompletedModel uses createBuilder, which attaches the wrapper to the current session, then sealTo.
That wrapper has no composition claim for the importing LINK edge. Its authoritative declaration, when present, belongs
to the completed target's stored original owning field. It must never be inferred from wrapper storage, the importing
relationship, Owner members, or a traversal alias. Existing completed target support is already usable before the
receiver's AUTO_LINK; querying through a wrapper is a read-only adaptation, not new ownership.

### Accepted read-only lifetime revision — D6

Allow each Builder metadata request when the genuine receiver belongs to the current thread's active
Construction session, the current numeric phase is strictly after OWNER(15), and the declaration source is authoritative.
There is no numeric upper bound and no unsealed requirement. For an ordinary construction Builder, read the current
accepted declaration record, preserving its identity through normal allocation/sealing; for a completed LINK wrapper,
read the target's retained declaration internally. Valid root/missing-annotation/historical-record absence returns empty;
an unresolved explicit record or conflicting ownership remains an error, subject to D5. Mutation preflight must not be
used to authorize these reads. D6 replaces the earlier `15 < phase < 40`/unsealed proposal; RM-1 implements this read lifetime.

Construction/no phase, APPLY_LATER(1), AUTO_CREATE(10), OWNER(15), and custom phases at or below 15 still reject, including
roots and absent annotations. Custom phase 16, AUTO_LINK, DEFAULT, POST_TREE, actions at 40 before/after materialization,
custom phases above 40, VALIDATE, VERIFY and COMPLETE can read only while the same session is active and the declaration
source is authoritative. Same-number phase-40 acceptance must test actual action ordering, not assume graph completion.
An internally allocated but partially assigned Model does not require or justify a Builder metadata rejection.

After session completion or abort, on another thread or in another session, the live Builder view rejects even though
its fields may still be present. After success, use KlumObjectSupport on the returned ordinary Model. An aborted build
does not promise a publishable completed Model, even if some objects were allocated. Template-definition Builders have
no valid Construction session and still reject; public Template inspection remains excluded. Acquiring a view early
or reading it successfully never grants permanent access: every query rechecks session, phase, and declaration authority.
Retained immutable descriptors are detached Schema metadata and remain readable after the live view expires.

Use `KlumModelException` for invalid live Builder state and `KlumSchemaException` for an unresolvable retained Schema
declaration, as approved under D1. Diagnostics identify the operation, receiver Schema type, current phase or
absence of an active session, and required state; include only safely available path context. For premature access:

```text
getOwningRelationshipAnnotation requires the current active Construction session and a phase after OWNER(15); current phase: OWNER(15); receiver: Consumer
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

The approved public API is additive in the exported runtime package; private companion state changes require explicit
qualification. Generated Factory/Builder signatures and IDE source mirrors stay unchanged. Existing direct-Owner queries,
paths, composition traversal, immutable Model data, lifecycle ordering, and LINK identity must retain their contracts.
The old ADR 0010 paragraph describing a dynamic `KlumBuilder.link` capability is historical and inconsistent with the
current zero-operation marker; it does not authorize adding an operation in this task.

Require Java 17, dynamic/static Groovy 3/4/5 classpath, separately compiled inherited Schema/annotation consumers, and
Groovy 4/5 JPMS named modules without expanding internal exports or requiring new broad opens. Existing schema-module
openings needed for construction remain the baseline. Runtime metadata lookup must not need private-field value access.
The annotations artifact gains no dependency on the runtime. Documentation and release notes describe the feature only
when implementation is delivered. RM-1 leaves #856 open for its later 4.1 qualification gates.

## Rejected alternatives

- Extend LinkTo AUTO, OWNER_PATH, or selector now: changes established behavior and introduces unapproved selection policy.
- Add TYPED/LinkBinding/defaultFor vocabulary: consumer policy is not yet a general KlumAST requirement.
- Keep DefaultValues transport as the primary seam: viable workaround, but too late for AUTO_LINK and duplicates metadata.
- Infer the field from Owner values, Role, or a path: fails absent/multiple Owners, containers, inheritance, and aliases.
- Return a raw Field or companion: exposes access/mutation mechanics and freezes internal state.
- Add operations to KlumBuilder or generated Builders: widens every generated contract and invites schema-name collisions.
- Let KlumObjectSupport accept Builders: weakens the completed-state boundary of ADR 0006.
- Implement every Structure traversal/navigation twin now: exceeds the metadata use case and creates more lifetime contracts.

## Maintainer decision dispositions

The entrypoints, shared immutable relationship type, Optional absence, per-operation checks and separation from consumer
selection are accepted. The first implementation milestone is the ScHelm vertical tracer in RM-1, immediately after
bounded RM-0 ownership/representation proof. It joins completed provider metadata and inherited AUTO_LINK Builder reads
through these public facades, then preserves the selected target's identity through the existing typed relationship
operation. Template/copy/persistence and full binary/module qualification are later gates, not prerequisites to exercising
that core path. No slice is release-ready until all approved eventual acceptance passes.

Decision dispositions (later slices still require explicit authorization):

1. **D1 — approved.** Exact generic shape, Class/member equality, Optional absence and error categories are frozen by
   RM-0; RM-1 has separate implementation authorization. See its report for the exact signatures.
2. **D2 — approved.** Retain declarations from accepted Template-definition claims internally and recapture recipient
   claims during application/copy. Preserve public Template rejection. Copied-container/alias repairs remain excluded;
   direct Template inspection requires its own decision.
3. **D3 — approved, same-version only.** Qualify ordinary/Template serialization with compatible available Schema
   definitions and empty lookup for otherwise readable absent metadata. No historical streams or Schema evolution are
   promised; audit changed companion UIDs without pinning old values.
4. **D4 — approved for 4.1.** RM-0, RM-1, and bounded RM-2 are authorized; parent #856 remains open for later gates and Hive reconciliation.
5. **D5 — settle copied-container ownership ambiguity.** RM-0 must characterize CopyHandler's direct list/map insertion
   and repeated recipe identity across distinct owned fields. Approve how an unclaimed copied node gains an authoritative
   declaration and how genuinely conflicting declarations fail or are represented, while preserving LINK/OPTIONAL_LINK
   semantics. Repeated positions within one field share its declaration; they do not require a unique path. No traversal
   order tie-breaker or unrelated ownership repair is approved by this plan.
6. **D6 — approved and implemented by RM-1.** Active same-session plus phase-after-OWNER checks include normal sealing
   and completed LINK wrappers. Every request rechecks state; completion/abort and foreign contexts reject.
