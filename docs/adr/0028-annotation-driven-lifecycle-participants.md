# Annotation-driven external Builder lifecycle participants

Date: 2026-10-09

Status: Proposed ADR; semantic decisions confirmed in maintainer discussion; names and qualification deferred.

Implementation status: Design only. Existing traversal characterization is available; feature implementation is not authorized.

Release: Conditional 4.1 candidate; #867 remains untargeted until qualification and release review.

Tracking issue: [#867](https://github.com/klum-dsl/klum-ast/issues/867).
Prerequisite: [#868](https://github.com/klum-dsl/klum-ast/issues/868).
Implementation plan: [ADR 0028 plan](../implementation/adr-0028-annotation-driven-lifecycle-participants.md).
Historical evidence: [initial feasibility brief](../implementation/issue-867-field-lifecycle-decision-brief.md).

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0005](0005-generated-dsl-support-api.md), [ADR 0008](0008-phase-registration.md),
[ADR 0010](0010-framework-public-interface-conventions.md),
[ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md),
[ADR 0020](0020-explicit-shared-model-builder-capabilities.md),
[ADR 0027](0027-owning-relationship-metadata.md).

## Context

The driving graph is Application → Domain → Fact: an annotation on Application.domain
configures the existing Domain Builder's Fact relationship using Application knowledge.
Domain does not know the Fact's source. Supplying the annotated relationship itself is a
second supported use case. Type placement allows a Schema to select its own reusable behavior.

Existing traversal visits the parent before reading and descending into children. Participants
must reuse the containing field's phase-processing slot, not a second whole-tree phase action
or reconstructed child ownership. Existing AutoLink methods remain the supported workaround.

## Decision

All API names in this document are provisional planning vocabulary.

### D1 — Placement and phase selection

Support domain annotations on direct DSL-typed fields. Scalars are outside scope.
Collections/maps of DSL values have an independent exploratory slice and a mandatory
support-or-reject decision before release; core implementation must not depend on that slice.

Mutating participants also support Schema type annotations if the domain author permits TYPE.
Creating participants are field-only.

Initial supported phases: AutoCreate, AutoLink, Default and PostTree. Select by the lifecycle
annotation Class (Groovy: phase = AutoLink; Java: phase = AutoLink.class), not an annotation
instance, phase enum or number. Validate against these four markers. No custom phase contract.

### D2 — Two composable capabilities

Use distinct creating and mutating meta-annotations and handler interfaces. One domain
annotation may contain both, in different or identical phases, and several mutations per phase.

Creating participants run only if the field is null. Return a Builder or null; null means
leave unset. Assign a non-null result through existing checked assignment. Handlers must
respect FieldType, ownership and session rules, including permissible sealed LINK wrappers.
No completed-Model return or context setter.

Mutations run only on a non-null target Builder, return void and configure through public
generated Builder contracts. They cannot replace the annotated field through context.
Creation runs first within the field visit; mutations see its result and skip if still null.
This supersedes the earlier alternative-branch-at-entry proposal.

### D3 — Dispatch and ordering

Dispatch arises from the actual containing Builder field in its existing phase visit, without
child owning-metadata, Owner backlinks or path reconstruction. Enumerate eligible fields using
existing lifecycle ordering mechanics; handle built-in or external participants at that slot.
Do not introduce alphabetical sorting.

At most one creation mechanism directly declared on a field may claim the same phase.
Competing direct creators, including built-in versus external, fail compilation.
Multiple mutations are allowed. Different-phase annotations coexist. Do not impose a blanket
built-in/meta-lifecycle conflict; diagnose competing same-phase creation roles.

AutoCreationPhase.doVisit keeps three distinct steps:

1. Fields, including creating then mutating participants.
2. ClusterFields, reacting to the resulting field state.
3. LifecycleMethods, including lifecycle Closure callbacks.

Cluster AutoCreate may coexist with a direct creator, filling fields still unset (including
null returns). It does not rerun field participants. Preserve other phases' built-in steps
and methods-before-Closures behavior, with exact insertion points qualified by tracers.
Child descent follows the containing visit, so parent-field work precedes child processing.

Type mutations execute first within the visited Builder, before fields, clusters, methods
and Closures. They therefore see earlier parent-field work. Follow Java @Inherited semantics:
subclass annotations of the same type override superclass annotations; unmarked annotations
and interface annotations do not propagate. Characterize repeatable-container semantics
rather than inventing inheritance rules. Type dispatch does not traverse sealed aggregation
targets that current traversal skips.

Multiple mutations in one phase execute in declaration order on their domain annotation,
conditional on a binary/Groovy tracer proving the representation. Retain upgrade regression
coverage. If disproved, reopen ordering then; do not depend on incidental reflection iteration.

### D4 — Context

Shared context supplies the actual domain annotation, containing Builder, incoming field name,
applicable declared DSL type and Klum FieldType, plus a field/type dispatch distinction.
Mutation context supplies the non-null target Builder; creation context has no target or setter.
Expose Builders through KlumBuilder<?> so handlers work across unrelated Schema hierarchies.

For field dispatch, metadata/typed annotation lookup come from the original Schema field.
For type dispatch, target is the visited Builder; containing Builder and field name come from
traversal and are null at root. Lookup queries the Schema type with inherited semantics,
not the incoming field declaration. Incoming relationship metadata is absent where traversal
has none; do not invent an owning-metadata requirement. Exact signatures remain tracer-gated.

Provide singular typed annotation lookup only. No reflective Field, annotation list or plural
lookup. This does not restrict multiple participant declarations on one domain annotation.

No extra path getters solely for dispatch. Phase code establishes/restores existing diagnostic
context, keeping retained object paths distinct from occurrence context. Context lifetime is
documented as invocation-only; no expiry/invalidation ceremony. Retention grants no new rights.

#868 supplies concrete Model-type discovery on KlumBuilderSupport for active and sealed
Builders. Field declared type may differ from the target's concrete Model type. #648's
factory-token predicates/narrowing remain the route to exact generated public Builder types.

### D5 — Typing and lifetime

Handler annotation parameter must resolve to exactly the domain annotation, including generic
inheritance. Reject raw, wildcard, unresolved and mismatched parameters. Compile-time source
validation has equivalent runtime checks for precompiled inputs.

Each invocation gets a fresh public concrete handler with a public no-arg constructor.
No instance caching or DI contract. Handler/context state is not serialized or retained in
Template recipes.

### D6 — Sealed policy

Mutating participant declarations choose default failure, explicit skip or explicit handle.
Names remain provisional. Never silently pass a sealed target to an unprepared handler.
Handle does not unseal it or relax existing mutation guards. Creation uses ordinary assignment
rules and needs no separate sealed policy.

### D7 — Closure members

Handlers choose which members to evaluate, their fixed delegate/result contract and sentinel.
Provide one narrow evaluation operation, exact signature tracer-gated: fresh Closure,
DELEGATE_ONLY, handler-selected delegate also passed as the single argument, expected-result
checking. No configurable owner/resolve/constructor/cache or automatic member execution API.

Annotation members represent Closure classes. Java Class literals and Groovy inline Closure
expressions require separate coverage. Generic Class bounds alone do not prove static result
or delegate checking. Preserve existing DELEGATE_FIRST lifecycle Closure behavior.

### D8 — Modules, errors and validation

Meta-annotations, handlers, contexts and the helper belong to runtime's existing public package.
Domain annotation libraries depend on runtime. Existing runtime → annotations direction permits
lifecycle annotation Class references without a cycle; keep KlumBuilder zero-operation,
generated Builder contracts unchanged and runtime internals unexported.

Configuration errors identify declaration/handler. Construction, invocation and Closure failures
preserve their cause and add participant annotation, handler, phase and field/type context.
Consumer domains own defaults, selection, ambiguity and constraints.

User docs must explain fail-fast errors versus validation participation and the existing supported
route for provisional issues to survive materialization and reach normal severity/reporting.
If no public route exists for external handlers, record the gap and obtain a bounded decision;
do not export companions or introduce a parallel collector.

## Consequences

Additive runtime extension surface and compiler checks; no ownership, session, materialization,
Templates/imports or serialization redesign. Existing shadowing rejection (#371, merged PR #821)
remains authoritative; inherited fields retain declaration metadata and actual containing Builder.

Groovy 3 remains classpath-only. Groovy 4/5 named consumers must work with ordinary documented
exports/opens, without access flags. No ScHelm types, algorithms, annotations or policy enter KlumAST.

## Rejected alternatives and deferred gates

Rejected: setter context, completed-Model return, reverse module edge, whole-tree equal-phase
action, public reflective Field/list/plural lookup, expiry guards, blanket annotation conflict,
alphabetical field ordering, scalar primary example and AutoLink-only scope.

Deferred: names/signatures, collection/map qualification, proven declaration order, Closure
static guarantees, phase-specific insertion details, inter-type annotation ordering and public
validation participation mechanics. Release placement remains conditional on qualification.
