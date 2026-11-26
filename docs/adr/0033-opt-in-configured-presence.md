# Whether to add native configured presence

Date: 2026-11-25. Assessment revised: 2026-11-26.

Status: Proposed. **The maintainer has not decided to implement native configured presence.** The accepted
supported-mutation boundary below is conditional on pursuing it, not approval to deliver it. No API, tracer,
implementation or release target is authorized by this ADR.

Tracking issue: [#1111](https://github.com/klum-dsl/klum-ast/issues/1111).
Decision/tracer plan: [ADR 0033 plan](../implementation/adr-0033-configured-presence.md).
The earlier seven-slice delivery roadmap is replaced by an early consumer decision and conditional follow-on work.

Parent boundaries: [Builder-first](0003-builder-first-materialization.md),
[Template/copy](0004-asbuilder-composition-protocol.md), [completed support](0006-completed-object-support.md),
[multi-Groovy](0011-shared-multi-groovy-compatibility-contract.md), [JPMS](0014-groovy4-jpms-boundary.md),
[generated linkage](0015-generated-schema-runtime-linkage.md),
[Builder capabilities](0020-explicit-shared-model-builder-capabilities.md) and
[Struct](0032-builder-oriented-structured-input.md). Their existing compatibility/lifecycle contracts remain;
ADR 0015 retains its own Proposed status.

## Context and recommendation

Schelm generates target Schemas with payload slots, presence markers, custom Builder operations and list-holder objects.
Its serializer needs omission, explicit null and explicit values to remain distinct after completion and snapshot copy.
The workaround works; removing all of it is not necessarily worth a framework-wide compiler/runtime capability.

**Recommend option B only as a candidate for one explicitly authorized, bounded consumer tracer. Do not commit to A.**
Keep D as the working production baseline until the tracer demonstrates a worthwhile reduction in consumer machinery.
Independently consider C1's container-copy repair even if native presence is rejected. A maintainer may reasonably choose
C/D immediately instead of funding another tracer. This recommendation does not select B for implementation.

The [retained evidence](../implementation/adr-0033-configured-presence.md#evidence-and-current-source) proves feasible
storage and a bounded compiler-produced mutation contract, not sufficient consumer benefit or a cheap production feature.
The three reports do not prove the simpler mechanism proposed here. No experimental code is promoted.

## Accepted conditional boundary and terms

The [maintainer decision](https://github.com/klum-dsl/klum-ast/issues/1111#issuecomment-7169948681) permits authoritative
presence only for an opt-in documented compiler-generated/explicitly supported Builder mutation surface, with complete
producer-hierarchy participation and positive coverage for each original declaring field. Missing/old/partial/mismatched
facts, detected unsupported shapes, unknown fields and unavailable transfer state fail closed as unavailable.

Reflection, metaprogramming, raw List/Map aliases/views and other bypasses remain unsupported contract violations.
Detection of every violation or retroactive downgrade is not promised. No transparent wrappers, security boundary or
general escape analysis follows from this contract. Existing arbitrary/raw mutation does not become presence-aware.

Configured presence means known ABSENT, NULL or VALUE under the selected configuration rules. Unavailable is no answer,
not a fourth state or implicit ABSENT. Ordinary effective getter values can come from initializers/defaults; nullness,
GroovyTruth, emptiness and before/after equality cannot establish configuration. Declaring-field identity stays internal.
Reads are neutral. Presence is neither a second retained payload nor provenance/event history.

## Options and opportunity cost

Relative effort is an engineering judgment from the inspected seams, not a measured schedule. A means the former broad
native proposal within the accepted mutation boundary, not impossible transparent tracking of every Groovy write.

| Dimension | A — broad native contract | B — restricted native candidate | C — independent improvements | D — Schelm-managed presence |
| --- | --- | --- | --- | --- |
| Consumer benefit | Potentially remove slots, markers, maintenance setters and presence-only list holders across many construction routes. | Aim to remove those for generated target Schemas using ordinary construction and snapshot copy; prove the saving early. | C1 can remove JsonTree copy envelopes; C2/C3 might reduce container-specific plumbing, not presence itself. | Existing demonstrated semantics remain available without waiting for KlumAST. |
| Remaining Schelm code | Wire metadata, validation, number handling, JSON-tree freezing, YAML backend; unsupported-route adapters may remain. | Same domain code, plus workaround for excluded Template/lifecycle/merge/import routes and immutable trees unless C1 lands. | Slots, markers, custom state operations and serializer state lookup remain; list holders remain unless separately demonstrated removable. | All present machinery remains; it can be simplified in Schelm independently. |
| Compiler/runtime complexity | High: direct-write forms, origins/deferred work, all transfers, Template retention and compatibility. | Still material: closed generated protocol, empty-operation hooks, defaults, per-field validity and snapshot transfer. Reject/withhold other shapes. | No native presence metadata; bounded payload-copy or container-operation changes, each with its own contract. | No new KlumAST machinery; generator/support library carry the logic. |
| Defaults/lifecycle/Template/copy impact | Requires explicit policy across all these interactions. | Opted-in explicit values suppress implicit defaults; restrict callbacks/deferred work, Template and combined copies rather than define every case. | C1 changes copy allocation only; C2 is a distinct nullable-copy policy decision; C3 changes explicit operations. | KlumAST behavior unchanged; Schelm chooses snapshot policy and keeps defaults outside this workaround. |
| API/compatibility | Reader, opt-in and mutations plus broader retained-state and generated-linkage obligations. | One enum/direct Optional reader and selected authoring operations; existing ABI obligations remain, no new historical-stream promise. | Only the selected independent behavior/API; C1 may need no public API. | Consumer APIs evolve under Schelm's control. |
| Maintenance/regression risk | Highest: every generator, Groovy version and transfer/lifecycle extension can invalidate coverage. | Lower scope, still ongoing proof of the entire advertised protocol per covered field. Restrictions must stay understandable. | Localized copy/container regressions; avoid accidental changes to custom collections, comparators or identity. | Presence and payload stay coordinated by consumer code; generation drift and duplicated machinery remain consumer risks. |
| Relative effort | Very high, sustained multi-module qualification. | Medium-to-high, not a small nullable-field change; tracer must expose its actual cost. | C1 small-to-medium bounded candidate; C2/C3 medium if ownership/null APIs expand. | Minimal framework effort; ongoing consumer maintenance already incurred. |
| Reversibility | Easy before publication; difficult after public APIs/serialized behavior become dependencies. | Tracer disposable; published native API still creates a lasting obligation even with narrow scope. | Independently selectable; released behavior needs compatibility care. | Most reversible framework choice; later adoption remains possible. |

C1 is specifically supported by current code: CopyHandler creates nested Map/List copies with a no-arg constructor of the
runtime implementation class. Schelm's TargetValues wraps immutable subtrees in JsonTree to avoid that path. A bounded
repair for recognized standard containers, preserving ordering/comparators and donor isolation without instantiating
private JDK classes, is worth independent consideration. It could remove the envelopes; it does not remove JSON-tree
validation/freezing or prove general custom-container/deep-copy support. Scope it separately, without creating an issue here.

C2 is clarification/repair of ALWAYS_REPLACE for null containers. It could benefit ordinary nullable snapshot copies,
but changing today's null-donor skip is a behavior decision, not an automatic bug-fix authorization. C3 is an explicit
whole-container replace/empty operation usable by consumer-managed markers; it may reduce custom setter plumbing, but
cannot alone retain presence or remove copy-driven list holders. C2/C3 need a concrete independent use case and ownership
review; neither justifies a generic extension SPI. C1 is the strongest independent candidate; do not bundle all three.

## Proposed smaller native contract

All choices here are recommendations, not accepted decisions. Prefer class-wide explicit opt-in and inspection of ordinary
completed Models only. A class-wide opt-in is a request, not a guarantee that every field is covered. Special fields and
unsupported declarations are unavailable or rejected; an unqualified hierarchy never yields partial synthetic ABSENT.

Prefer this direct client API, with no public inspector object, descriptor, result hierarchy or diagnostic taxonomy:

```java
// Proposed API; ConfiguredPresence has ABSENT, NULL and VALUE only.
Optional<ConfiguredPresence> state = KlumObjectSupport.of(model).getConfiguredPresence("replicas");
// Optional.empty() means unavailable: the serializer must reject/defer, never treat it as ABSENT.
```

```groovy
// Same proposed contract in Groovy; do not test Optional or payload using GroovyTruth.
def state = KlumObjectSupport.of(model).getConfiguredPresence('replicas')
```

A name resolves to the original declaring field internally; unknown/ambiguous names return empty. Recommend initially
withholding shadowed fields rather than adding public field tokens. Distinguishing unavailable reasons is not necessary
for correct serialization; existing compilation diagnostics can explain known unsupported declarations. Add a richer
result only after a concrete consumer demonstrates why Optional is insufficient. Builder and Template readers are deferred.

B would cover ordinary generated scalar/owned-object and supported List/Map operations, including empty blocks/bulk calls,
explicit reset/null/empty and ordinary ALWAYS_REPLACE snapshots. It would initially exclude customized/overridden mutation
families, direct stores in custom Builder methods, arbitrary initializer helpers, mutating lifecycle/participants/custom
phases, applyLater, Template replay, combined-copy policies and unqualified import routes. Keep custom Builder methods that
only call supported generated operations. Detectable excluded Schema shapes must reject or withhold coverage before any
ABSENT claim; known runtime transfers/scheduling outside the subset invalidate affected availability. Foreign bypasses
remain contract violations, not a demand for whole-program detection. The exact support/reject policy is still a decision.

To a Model Writer: use the documented generated methods; an empty operation counts as configuration even if it adds no
members. Custom helpers must use those methods. Do not edit a raw List/Map, its iterator/view, or storage through reflection.
Direct custom stores remain legal on untracked Schemas but unsupported by this candidate. Getter/inspection reads do nothing.
Struct support, if selected, must hook its actual admitted execution seams, including empty population; public setter
coverage does not automatically cover Struct's internal child paths. Empty population retains current contents; it is not
replacement. Native presence must not silently change general FromStruct null admission.

Prefer explicit NULL/zero/false/empty suppressing implicit defaults on opted-in fields, with ABSENT remaining eligible at
the normal lifecycle step. Initial values/container allocation do not establish configuration. Reset clears stale logical
payload and ownership, establishes ABSENT, and allows subsequent reconfiguration; it does not rerun phases. Do not retain a
second configured payload to repair later implicit overwrites. If arbitrary lifecycle rewriting is essential, either define
its semantics separately or choose A/D; do not quietly add origin-history machinery to B. Validate the selected boundary
against real target Schemas: the pinned generator records source defaults but does not materialize them today.

## Smallest sufficient mechanism to evaluate

Essential facts are (1) a recognized compiler contract on every actual producer layer, (2) positive declaring-field coverage
of that entire supported protocol, (3) object-specific validity/state, and (4) coherent successful transitions and transfers.
A compiler version, opt-in marker, field-name list or freshly allocated empty map alone cannot prove ABSENT.

Propose a single closed field-level protocol version plus covered declaring fields. The compiler grants a field only when
all operations admitted by that version are covered; an override/unsupported generator shape withholds it. Internal
call-site checks and generation tests still prove every promised operation. Per-operation runtime manifests are needed
only if independently selectable operation families become a real requirement. Narrowing avoids that requirement.

Retain one object-specific validity/state record, not duplicate class descriptors, producer snapshots and diagnostic origin
maps. For a live same-build object, qualified birth facts and current loaded-class hierarchy can supply correspondence.
To make dropping retained producer snapshots honest, recommend B initially returning unavailable after Java deserialization,
including same-build round trips, instead of reconstructing facts from current classes. Preserve ordinary Model serialization
and existing ABI contracts; implementation must verify that this loss of presence is safely detectable. If presence must
survive streams, reintroduce only the minimum contract/layout correspondence evidence under a separate decision. The old
proof's successful serialized snapshots do not prove that this simpler live-only design works.

Use existing Builder-to-companion completion, private declaring-field keys and narrow runtime.generated linkage. No Model
retains a Builder/session. Field validity must survive a copy as unavailable when donor evidence is missing; a newly allocated
receiver cannot launder it. Reject or withhold detectable unsupported shapes instead of instrumenting every direct-write form.
No alias wrappers, general escape analysis, public coverage registry, configured-payload log or historical compatibility
framework is required. B still needs an authored/implicit boundary for supported defaults and initialization; narrowing cannot
remove that correctness obligation. If these facts cannot be represented simply, that is a stop/go finding, not permission
to recreate the earlier metadata subsystem.

## Copy follows payload decisions, with explicit limits

CopyHandler already decides whether to replace, retain or combine. Attach presence to that decision rather than running
an independent policy engine. The proposed minimal rules are:

| Payload outcome | Proposed presence rule |
| --- | --- |
| Replace successfully from a qualified donor field | Transfer donor configured state and validity, using receiver coverage. Known ABSENT under a complete snapshot replaces prior configuration too. |
| Retain without accepting donor configuration | Keep receiver presence; a generic setter hook must not reclassify it. |
| Combine contributions | Neither marker automatically wins. Define whether an accepted configuration event establishes parent VALUE and which contributors are required; unavailable necessary evidence yields unavailable. B can withhold combined paths initially. |
| Explicit reset | Clear stale payload/ownership and establish ABSENT under the selected reset contract. |
| Transfer evidence or receiver coverage insufficient | Unavailable, never infer from the selected getter payload. |

The simplifying rules have real exceptions. List/Map null donors currently return before policy dispatch; preserving
receiver state there is truthful about the skipped operation but does not implement a NULL snapshot. B must either perform
the selected null replacement safely or leave that requested case unavailable. Null receiver containers also need safe
storage for later member operations. Failed operations must not pair a new marker with a failed store; partial bulk/copy
work retains coherent earlier effects, without a new transaction guarantee.

A single owned-object MERGE can keep the parent payload identity while changing child fields: treating it as plain retention
would miss an explicitly configured empty child block. Likewise ADD_MISSING can accept configuration without inserting an
entry, and append/upsert may be VALUE even when equality leaves contents unchanged. Thus outcome means the semantic copy
branch/contribution, not before/after equality. Parent presence and recursive child transfer are separate. Do not infer
field presence from a merge's final size. A broad implementation needs a small contribution rule for these cases; the
restricted candidate can decline them. Map/data donors lack retained native history; do not fabricate it. The related
immutable nested-container copy problem remains C1, not part of presence-state transfer.

## Consequences and unresolved decision

B could remove Schelm's slots/markers and presence-only list holders for its core path, with a much smaller client API.
That benefit is still conditional. Published opt-in/reader/mutation APIs and field-level compiler guarantees incur ongoing
Groovy/generator regression work; restricting them reduces scope, not the seriousness of that obligation. Untracked defaults,
copy, nullability, ownership, Template behavior and public APIs remain unchanged. B can preserve existing payload execution on
unqualified routes while returning unavailable, but frequent unavailability may make the feature useless to the serializer.

Maintainers must choose A/B/C/D or no further work; if funding B's tracer, choose its exact exclusions, API/opt-in spelling,
reset/null/empty/default policy, nullable snapshot treatment, live-only serialization limit and stop/go owner. Those are
proposals, not an accepted migration contract. No new historical ABI/stream commitment or release placement is made.
The [early decision criteria](../implementation/adr-0033-configured-presence.md#early-stopgo-decision) allow proceeding,
narrowing or abandoning native presence. Nothing in the plan presumes later implementation.
