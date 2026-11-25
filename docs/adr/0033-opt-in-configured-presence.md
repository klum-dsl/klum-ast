# Opt-in configured presence over a supported Builder mutation surface

Date: 2026-11-25

Status: Proposed design record of the accepted supported-mutation boundary. Public vocabulary and semantic choices
listed below remain proposed or open; this ADR does not authorize production implementation.

Implementation status: Not implemented. Three retained local disposable proofs establish bounded feasibility, not
production qualification. No release placement is selected.

Tracking issue: [#1111 — native configured presence](https://github.com/klum-dsl/klum-ast/issues/1111).
Plan: [ADR 0033 implementation plan](../implementation/adr-0033-configured-presence.md).

Parent decisions: [Builder-first materialization](0003-builder-first-materialization.md),
[AsBuilder and Template recipes](0004-asbuilder-composition-protocol.md),
[generated public contracts](0005-generated-dsl-support-api.md),
[completed-object support](0006-completed-object-support.md),
[framework public interfaces](0010-framework-public-interface-conventions.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md),
[JPMS](0014-groovy4-jpms-boundary.md), [generated linkage](0015-generated-schema-runtime-linkage.md),
[Template entrypoints](0016-template-creation-and-scoped-application.md),
[explicit Builder capabilities](0020-explicit-shared-model-builder-capabilities.md),
[structured contracts](0031-structured-schema-input-artifacts.md), and
[Builder-oriented structured input](0032-builder-oriented-structured-input.md).
ADR 0015 retains its own Proposed status; this design follows its implemented generated-linkage boundary without
changing that status or extending its historical compatibility promise by implication.

## Context and authority

Schelm's generated target Schemas need to distinguish an omitted field, explicit null, and an explicitly configured
value after Model completion and copying. Its current workaround maintains slots, state markers, custom Builder
operations and list-holder objects on KlumAST 4.2.1. False, zero and empty containers must survive the distinction.
Schelm owns external names, requiredness/nullability rules, Kubernetes/CRD facts, YAML output and original-schema
validation. KlumAST supplies a source-neutral field capability, not a serializer or a Kubernetes abstraction.

The complete issue and all six Hive/maintainer comments were read on 2026-11-25. The latest
[maintainer decision](https://github.com/klum-dsl/klum-ast/issues/1111#issuecomment-7169948681)
accepts the bounded supported-mutation contract. It supersedes the earlier proofs' requests to decide that boundary;
it does not settle their remaining semantic/API questions. The issue body's desired consumer semantics are acceptance
work to reconcile, not authority to silently choose every native default, copy or lifecycle rule.

The [plan's evidence register](../implementation/adr-0033-configured-presence.md#retained-local-evidence) records the exact
three local commits, report paths, reproduction access and limits. Those branches must remain local and disposable.
Their experimental production edits, sentinel, String descriptor and inspection helper must not be merged, rebased,
published or copied into implementation. Production work starts independently from master.

## Terms

| Term | Meaning in this design |
| --- | --- |
| Logical field | One retained Schema field, identified by its original declaring Schema and field identity, rather than an external wire name or a coincidentally named getter. |
| Configured presence | Retained supported-configuration state: `ABSENT`, `NULL`, or `VALUE`. It is independent of effective getter values. |
| `ABSENT` | Known absence of configuration under the chosen semantic policy, including an accepted explicit reset. It is a positive claim requiring complete coverage. |
| `NULL` | Known explicit null configuration where the selected operation and nullability policy admit it. It does not mean a null element inside a VALUE container. |
| `VALUE` | Known non-null configuration, including false, zero, empty String, explicitly empty object or container. Exact operation/default/lifecycle rules require the choices below. |
| Effective value | What ordinary getters expose after initializers, defaults, automatic creation, lifecycle and materialization. It is neither a presence detector nor a configured-payload history. |
| Producer capability | Compiler-produced facts advertising the recognized mutation contract and exact field/operation coverage of one Schema layer. A compiler version alone is insufficient. |
| Retained coverage | Object-specific evidence that the advertised contract covered construction and subsequent supported transfers of a particular field. A capable class may contain an object with unavailable field state. |
| Unavailable | No authoritative configured-state answer. It is not a fourth configured state, null, ABSENT, or a validation failure. |
| Origin | Internal classification needed to distinguish supported authored operations from implicit work. It is not public provenance, lineage or an event log. |

## Accepted decision

### Bound authority to an opt-in mutation contract

Native configured presence, if delivered, is opt-in. Authoritative answers apply only to the documented compiler-generated
and explicitly supported Builder mutation surface, with the complete actual producer hierarchy qualified and positive
coverage for each declaring field. Participation is required from every Schema producer layer, including abstract bases;
a tracked leaf cannot certify an untracked base, and a tracked base cannot certify an untracked descendant.

Reflection, metaprogramming, raw List/Map aliases and views, arbitrary external writes and other bypasses outside that
surface are unsupported contract violations. KlumAST does not promise to detect every bypass or retrospectively return
unavailable after it. Existing arbitrary/raw mutation is not made presence-aware by opting in. This is an authoring and
consumer correctness contract, not a security boundary or protection from hostile code/bytecode.

Missing, old, partial or mismatched producer/coverage facts, unknown fields, detected unsupported shapes and unavailable
transfer state fail closed as unavailable. Runtime age, an opt-in annotation, an empty state map, a field-name list,
setter success or ordinary getter values cannot independently establish coverage. No transparent wrapper/view tracking
will be pursued under this decision.

### Preserve the Builder/Model boundary

Construction-time mutation remains on active, same-thread, unsealed ordinary Builders, with the existing separate
Template-definition protocol. Presence does not add completed-Model mutation or expose companions, raw metadata,
sessions, hidden Builders, or a reconstruction API. Completion must retain the facts without retaining the Builder.
Reads must not configure fields, create authoritative facts, run defaults, lifecycle or validation.

Configured presence does not by itself preserve a historical configured payload. In particular, the proofs retained
VALUE for explicit zero while existing defaults changed the effective payload to seven. A consumer cannot infer that
the getter still returns the originally authored value. This mismatch must be settled by the default/lifecycle gate
before claiming Schelm acceptance.

### Supported mutation contract in author-facing language

The eventual user guidance must say, in substance:

> Configure tracked fields through the documented generated Builder operations or supported Schema-authored Builder
> methods. Reading a field does not configure it. A raw collection obtained from a Builder is not a tracked mutation
> handle: changing it, an iterator, a subList or a Map view does not participate in presence tracking. Custom helpers must
> use supported operations or compiler-covered direct writes. Do not use reflection or metaprogramming to maintain
> tracked state. Presence answers assume these rules were followed; KlumAST cannot discover every violation.

The following is a **production qualification target**, not an assertion that every row is implemented or that every
source form will be supported. The producer may advertise only operations that pass the plan's acceptance checks.

| Surface | Required boundary/qualification |
| --- | --- |
| Generated scalar and owned-child operations | Record after successful normalization/store/attachment, including explicit empty child blocks and re-entry into an existing child. Same-value writes count; equality comparison cannot detect them. |
| Generated collection/Map member, bulk and CollectionFactory operations | Record successful explicit operation even when it adds nothing. Preserve existing append/upsert/population behavior; an empty bulk call does not silently become a clearing setter. Custom overrides, alternatives, converters and projections require their own exact coverage. |
| `@Builder.Method` and moved helpers | Calling a custom method alone proves no field changed. Generated operations inside it participate; direct writes participate only for compiler-qualified forms, including inherited declaring-field identity. Nested closures/helper calls and static/dynamic Groovy need qualification. |
| Direct field/property/attribute assignment | Tested moved-method forms can be instrumented after success. Arbitrary writes from foreign configuration code, unknown receivers, index/view writes and unqualified compound/prefix/postfix shapes are not automatically supported. Detectable unsupported forms must reject or conservatively withhold affected coverage; unknown-target escapes withhold the whole affected hierarchy. |
| Struct | Instrument admitted execution/population seams, including explicit empty containers and child bodies. Missing input does not configure a field. Keep ordered partial effects, source diagnostics and current null admission; do not add a reset/null grammar by implication. |
| Initializers, defaults, AutoCreate, lifecycle, deferred actions | Generated setters alone do not establish authored origin. Preserve an internal origin boundary, including nested initialization and deferred scheduling/replay; the semantic classification remains a maintainer choice. |
| Copy/import/Template | These have distinct source and transfer semantics, not a blanket authored-setter classification. Preserve unavailable state when required transfer evidence is absent. Only qualified paths may advertise support. |

A failed assignment must not update presence before its RHS, coercion and store succeed. Earlier successful writes in a
later-failing block/bulk/copy may remain; this adds no method-wide, document-wide or graph-wide rollback guarantee.

## Recommended private architecture, subject to design review

Use one small internal presence module owning immutable producer descriptions, resolved declaring-field keys, retained
field validity/state and transition/transfer checks. Compiler generation records coverage from actual generated and
instrumented paths, rather than assigning a broad token solely from field type. Runtime hooks pass successful operation
and origin facts; a single transition boundary coordinates them with storage. Keep the encoding private and versioned.

Validate the actual complete Schema hierarchy, declaration/superclass identities, recognized contract/operation format,
and object-retained matching facts. Within a valid hierarchy, missing field coverage withholds that field alone; a missing
producer layer prevents all field claims. Known local hazards can withhold a field; unknown-target escapes withhold the
whole hierarchy. Unsupported special fields, shadowing, generics and non-Schema ancestry require a qualification or an
explicit unavailable/rejection rule, never a guessed identity. `FieldType.BUILDER` has no retained completed logical
field and cannot receive a completed-field answer.

Retain ordinary facts privately in Model companion state. Give Templates an independent immutable presence snapshot and
origin-bearing recipe actions, not references to live Builder metadata or parallel mutable action/origin lists. Replaying
into a fresh recipient uses that recipient's producer capability and the transferred object's field availability; a fresh
runtime/recipient cannot turn unknown donor history into known ABSENT. Sealed LINK wrappers inspect their completed
target's original facts rather than empty wrapper storage. Ownership, alias/cycle identity and one lifecycle remain as
defined by the parent ADRs.

Generated calls/descriptors must use narrow `runtime.generated` bridges under ADR 0015. Public inspection belongs in
ordinary exported runtime API; compiler implementation and companions remain internal. The coverage protocol is distinct
from `GeneratedSchema` compiler provenance, `GeneratedInput` Struct admission and runtime-floor metadata. Reuse objective
facts where correct, but none of these markers alone grants presence coverage. Do not add a general metadata SPI.

## Smallest recommended public consumer contract — not selected spelling

Recommend a read-only helper on `KlumObjectSupport` accepting a logical Schema field name, resolving it to the declaring
field, and returning an immutable result with mutually exclusive `Known(ABSENT | NULL | VALUE)` and `Unavailable` cases.
A serializer must branch on availability explicitly; no default-to-ABSENT or getter fallback. A known state can never be
null. Prefer no payload, mutable descriptor, write API, lineage or capability registry in this first consumer surface.
Unknown/ambiguous names produce unavailable. External/wire-name mapping stays with the consumer.

Illustrative Java first, then Groovy; **all new names below are proposals**, not compilable delivered examples:

```java
var result = KlumObjectSupport.of(model).getConfiguredPresence().inspect("replicas");
// Handle Known(state) and Unavailable explicitly; serialize the ordinary value only under the chosen policy.
```

```groovy
def result = KlumObjectSupport.of(model).configuredPresence.inspect('replicas')
// The same result contract; do not test result or model.replicas with GroovyTruth.
```

Alternatives requiring maintainer selection:

- `Optional<State>` is smaller and distinguishes unavailable from known ABSENT, but loses diagnostic reasons and makes
  accidental fallback easy. A reason-bearing result costs more API while making unsupported producers/transfers legible.
- A helper on the established facade keeps completion semantics discoverable; a dedicated facade has fewer changes to
  `KlumObjectSupport` but adds another entrypoint. Generated per-field methods add binary/API volume and naming collisions.
- Name lookup is sufficient for the concrete Schelm serializer. A public declaring-field token handles shadowing and
  enumeration more explicitly but adds a descriptor contract. Recommend withholding ambiguous fields initially rather
  than exposing reflection fields or wire names. No enumeration API is required for the first consumer.
- Completed Models are the minimum required reader. Builder inspection would need its own active-session/sealed-wrapper
  policy; Template public inspection needs a decision between recipe facts and ordinary Model semantics. Neither is
  silently included by choosing the completed-object facade.

## Remaining maintainer choices

The accepted boundary does not choose the following. The plan names gates before any affected implementation slice.

| Choice | Alternatives and consequence |
| --- | --- |
| Opt-in annotation and vocabulary | Class-wide opt-in is simple; selected fields reduce scope but still require whole-hierarchy participation. Decide inheritance/abstract producer rules and generated/user namespaces; no proof sentinel becomes API. |
| Inspection and explicit reset/null/empty APIs | Select result/facade spelling and mutation operations, nullability admission, collision rules and field identity. Reset must differ from null and VALUE(empty), remove stale logical payload and permit later configuration; exact reset storage/ownership mechanics remain to design. |
| NULL/zero/empty versus defaults | Presence-aware default eligibility would preserve explicit values; retaining effective-value defaults would require an additional configured-payload contract or narrower consumer promise. Choose explicitly; the latter alone does not satisfy #1111's requested default suppression. |
| Initializer/AutoCreate/lifecycle semantics | Distinguish implicit effective data from configuration. Decide callbacks, participants, custom phases, nested calls, deferred scheduling and later writes to previously configured fields. Choose reset eligibility at the normal default phase, with no read-triggered default execution or phase rerun implied. |
| Copy policies | The proven ALWAYS_REPLACE availability behavior is evidence; decide null-container replacement/population and state/payload behavior for every other strategy. A configured-values overlay differs from a complete snapshot. No general MERGE/ADD/missing-only policy is selected. |
| ABI and Java serialized streams | Inventory additive generated linkage and existing directional contracts, then select historical producer/runtime and stream promises. Same-runtime proof is not old-stream or mixed-version compatibility. Unknown retained facts stay unavailable if an older stream is accepted. |
| Release target | Untargeted. Earlier provisional horizon comments are not placement authority. Delivery and consumer qualification must precede a release claim. |

## Compatibility, serialization and consequences

- Non-opted-in Schemas retain existing behavior, nullability, defaults, copy policies and public construction APIs. Their
  presence is unavailable. Changes in effective default/null/copy behavior must be opt-in or carry an explicitly reviewed
  migration contract; native presence must not silently redefine general `FromStruct` null admission.
- Same-version Model and Template Java serialization must preserve both positive and unavailable facts without retaining
  Builders/sessions. New private fields can affect default serial UIDs; select format/UID/read policy before implementation.
  No new historical-stream promise is made. Presence is not a Jackson/YAML persistence format or Struct round trip.
- One production artifact must serve separately compiled Java and dynamic/static Groovy 3/4/5 consumers. Qualify AST
  expression results, evaluation count, inherited writes, closure lowering and origin restoration in every lane. Java 17
  remains the baseline; add Java 21 consumer evidence for the motivating use case. Groovy 3 remains classpath-only;
  Groovy 4/5 named modules need ordinary exports/opens, without access flags or internal descriptors.
- Coverage costs storage, instrumentation and qualification. Opt-in alone does not make a complex Schema supportable;
  conservative unavailable answers may require Schema changes or recompilation. Consumers must handle unavailable.
- Presence/payload copy choices must be coordinated per logical field. Donor immutability and existing ownership rules
  remain. The immutable nested-container/scalar-envelope copy limitation is separate; this ADR promises no generic deep copy.
- No public user guidance or changelog claims delivery from this design. Implementation must add documentary tests,
  user guidance, migration navigation and release notes once the API and behavior are accepted and executable.

## Rejected alternatives

- Getter nullness, GroovyTruth, emptiness, before/after equality or setter-only interception: these miss authored same-value
  writes, explicit empty operations and implicit-origin differences; they cannot establish authoritative ABSENT.
- Transparent collection wrappers/views: raw/shared/stale aliases bypass or misattribute tracking; wrappers also change
  identity and require a separate ownership redesign. The accepted contract excludes this direction.
- Universal automatic bypass detection: the third proof changed live storage by foreign reflection while false ABSENT
  survived copy/serialization. A descriptor certifies a compiler contract, not arbitrary foreign behavior.
- Runtime version, provenance marker, opt-in marker or a newly allocated empty map as coverage: none proves every producer
  layer, declaring field and transfer participated. Unsupported/old objects cannot be upgraded by inspection.
- Public companion/metadata access or publishing the prototype descriptor: this freezes experimental internals and
  contradicts completed-object support and generated-linkage boundaries.
- Unconditionally transferring donor markers after arbitrary copy policies: a retained receiver payload with unrelated
  donor state is false. NULL-container snapshot limitations cannot be repaired by metadata alone.
- Schelm-specific slots, Kubernetes rules or a core serializer: these obscure the native source-neutral capability and
  belong to the consumer. Ordered composition remains #304 and is not a prerequisite invented by this ADR.
