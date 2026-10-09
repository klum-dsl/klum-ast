# Annotation-driven external Builder lifecycle participants

Date: 2026-10-09

Status: Architecture accepted by maintainer; core/optional refinement confirmed. Names/signatures and qualification remain provisional.

Implementation status: [LP-1 direct-field AutoLink qualification tracer](../implementation/issue-867-lp1-evidence.md) implemented under a subsequent explicit maintainer delegation. LP-2–LP-8 remain unqualified; names/signatures remain provisional.

Release: Conditional 4.1 candidate; #867 remains untargeted until qualification and release review.

Tracking issue: [#867](https://github.com/klum-dsl/klum-ast/issues/867).
Prerequisite: [#868](https://github.com/klum-dsl/klum-ast/issues/868).
Implementation plan: [ADR 0028 plan](../implementation/adr-0028-annotation-driven-lifecycle-participants.md).
Historical evidence: [initial feasibility brief](../implementation/issue-867-field-lifecycle-decision-brief.md).
Current refinement: [evidence](../implementation/evidence/issue-867-core-optional-refinement.md).

Parent decisions: [ADR 0003](0003-builder-first-materialization.md),
[ADR 0005](0005-generated-dsl-support-api.md), [ADR 0008](0008-phase-registration.md),
[ADR 0010](0010-framework-public-interface-conventions.md),
[ADR 0011](0011-shared-multi-groovy-compatibility-contract.md),
[ADR 0014](0014-groovy4-jpms-boundary.md),
[ADR 0020](0020-explicit-shared-model-builder-capabilities.md),
[ADR 0027](0027-owning-relationship-metadata.md).

## Context

The driving graph is Application → Domain → Facts: an annotation on Application.domain
configures the existing Domain Builder's Facts relationship using Application knowledge.
Domain does not know the Facts' source. Supplying the annotated relationship itself is a
second supported use case. Type placement allows a Schema to select its own reusable behavior.

Existing traversal visits the parent before reading and descending into children. Participants
must reuse the containing field's phase-processing slot, not a second whole-tree phase action
or reconstructed child ownership. Existing AutoLink methods remain the supported workaround.

## Decision

All API names in this document are provisional planning vocabulary.

### Core acceptance versus optional capabilities

One cohesive feature with incremental slices. Field mutation is the primary ScHelm requirement:
Application → @Binding Domain → Facts, where application knowledge configures an existing
Domain Builder's nested Facts relationship. Domain stays independent of provider selection.

Mandatory core: direct DSL relationship fields; separate creator/mutator interfaces and
meta-annotations; creator-before-mutator; all four existing Builder phase visitors; public
Builder typing; sealed FAIL/SKIP; existing validation support; Java/Groovy 3/4/5 and applicable
JPMS; existing ownership/session/materialization/Template/import compatibility.

Desirable but independently deferrable: type mutation (LP-5), annotation Closure evaluation
(LP-6), sealed HANDLE (LP-4 extension), Collection/Map support (LP-7). Attempt straightforward
compatible implementations. Substantial complexity permits evidence-backed deferral without
blocking the core. Optional does not mean automatically deferred. Do not publish partial APIs
for deferred capabilities. Container support is optional, but a pre-release support-or-reject
decision with diagnostics is mandatory. LP-1 alone does not qualify the whole feature.
Separate issues/PRs per slice are unnecessary unless the Hive selects independent delivery.

### D1 — Placement and phase selection

Support domain annotations on direct DSL-typed fields. Scalars are outside scope.
Collections/maps of DSL values have an independent exploratory slice and a mandatory
support-or-reject decision before release; core implementation must not depend on that slice.

LP-5 may qualify mutating participants on Schema types if the domain author permits TYPE.
It is independently deferrable, not a mandatory core or 4.1 gate. No type-level public contract
before qualification. Creating participants are field-only.

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

If LP-5 ships, type mutations execute first within the visited Builder, before fields, clusters, methods
and Closures. They therefore see earlier parent-field work. Follow Java @Inherited semantics:
subclass annotations of the same type override superclass annotations; unmarked annotations
and interface annotations do not propagate. Characterize repeatable-container semantics
rather than inventing inheritance rules. Type dispatch does not traverse sealed aggregation
targets that current traversal skips.

Creation-before-mutation is unconditional. Other ordering dimensions are distinct:

1. Within one domain annotation: deterministic execution is desired, but declaration order
   is not a mandatory public guarantee. Probe actual compiled Java/Groovy annotations,
   repeatable meta-annotations, separately compiled libraries/Schemas and Groovy 3/4/5.
   Reliably recovered declaration order becomes documented and regression-tested; otherwise
   execution order is explicitly unspecified.
   [LP-2 ordering probes](../implementation/issue-867-lp2-evidence.md#exact-ordering-conclusion)
   establish declaration order for repeated mutators and value-array order for one explicit container.
   Mixing a singular marker and explicit container retains unspecified relative order.
2. Between different domain annotations on a field: no deterministic order is established.
   Do not infer it from reflection or the within-annotation result.
3. Type versus field: if LP-5 ships, type mutations precede fields in their own visit;
   parent-field dispatch still precedes the child visit. This does not establish order
   between different type annotations.

Where unspecified, handlers must not rely on order for correctness. No priorities,
alphabetical sorting, new ordering SPI or elaborate machinery to manufacture a guarantee.
An unspecified mutation order does not block core release.

### D4 — Context

Shared context supplies the actual domain annotation, containing Builder, incoming field name,
applicable declared DSL type and Klum FieldType, with a field/type dispatch distinction only if LP-5 is qualified.
Mutation context supplies the non-null target Builder; creation context has no target or setter.
Expose Builders through KlumBuilder<?> so handlers work across unrelated Schema hierarchies.

For field dispatch, metadata/typed annotation lookup come from the original Schema field.
For optional type dispatch, target is the visited Builder; containing Builder and field name come from
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

Core mutating policies are FAIL (default: reject invocation) and SKIP (omit invocation).
Never silently pass a sealed target to an unprepared handler. Creating participants use
ordinary checked assignment/ownership/session rules, without separate sealed policy.

HANDLE is optional for read-only inspection or validation of completed LINK targets.
Probe a small extension of existing dispatch; retain if straightforward. Defer if it requires
mutation interception, Builder lifecycle changes or a general read-only participant framework.
No HANDLE API if deferred; never unseal or grant mutation privileges. Names remain provisional.

### D7 — Closure members

Handlers choose which members to evaluate, their fixed delegate/result contract and sentinel.
LP-6 proposes one independently deferrable evaluation operation, exact signature tracer-gated: fresh Closure,
DELEGATE_ONLY, handler-selected delegate also passed as the single argument, expected-result
checking. No configurable owner/resolve/constructor/cache or automatic member execution API.
No partial helper API if LP-6 is deferred.

Annotation members represent Closure classes. Probe early using existing Validate annotation
Closure transformation/strong typing and IntelliJ owning-class inference as reuse baseline.
This is not an entirely new compiler problem. Investigate Model/generated-Builder mapping and
handler-selected Builder/context delegate typing; inline Groovy and Java-authored Closure
classes; DELEGATE_ONLY; delegate as one argument; expected result checking; static compilation;
Groovy 3/4/5. Existing Validate IDE ergonomics suffice; perfect inference everywhere is not required.

Reuse modest existing machinery if compatible; otherwise document limitations and defer LP-6
without blocking core. Generic Class bounds alone are not proof. Preserve existing
DELEGATE_FIRST behavior; avoid a generic Closure framework or unrelated compiler infrastructure.

### D8 — Modules, errors and validation

Meta-annotations, handlers, contexts and any qualified optional helper belong to runtime's existing public package.
Domain annotation libraries depend on runtime. Existing runtime → annotations direction permits
lifecycle annotation Class references without a cycle; keep KlumBuilder zero-operation,
generated Builder contracts unchanged and runtime internals unexported.

Configuration errors identify declaration/handler. Construction, invocation and Closure failures
preserve their cause and add participant annotation, handler, phase and field/type context.
Consumer domains own defaults, selection, ambiguity and constraints.

Validation participation uses existing support and severity/reporting; it is not an unresolved
architecture or public-API gate. The maintainer calls this KlumValidationSupport; this PR baseline
exposes KlumSchemaSupport.getKlumValidation() and klumValidationForObject(target), returning
KlumValidationReporter. Use existing concrete names; no rename or new facade is proposed.

Current-object reporting requires framework-managed instance/member context. Explicit targets
have no default member; errorAt/issueAt selects the location. For nested Facts, explicitly
target the Domain Builder. Existing issue transfer through materialization, fail levels and
suppression remain unchanged. LP-8 provides concise user guidance and one small executable
participant example. No collector, companion access, runtime API or validation infrastructure.

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

Probe-dependent contracts: actual mutation ordering, precise phase field slots, Closure
mapping/helper signature and HANDLE feasibility. Optional deferrals: LP-5, LP-6, HANDLE,
LP-7 support; document evidence and omit unqualified public APIs.

Remaining maintainer input follows probes: final names/signatures, optional deferral
recommendations, Collection/Map support-or-reject, conditional 4.1 placement after core evidence.
No new validation architecture decision is required.

Original authorization: planning only. Subsequent explicit maintainer delegation authorized LP-1 only,
recorded in the linked execution evidence. Architecture acceptance does not authorize additional slices,
issue retargeting or release placement. Existing characterization tests are preserved.
