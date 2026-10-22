# WIRE-RUNTIME — proposed first #932 production slice

Date: 2026-10-22. Status: **implementation proposal for review; production work not authorized**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Language owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Independent static projection: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Baseline: `3d13ffa2d` (merged [#938](https://github.com/klum-dsl/klum-ast/pull/938)), after
[#937](https://github.com/klum-dsl/klum-ast/pull/937) and [#933](https://github.com/klum-dsl/klum-ast/pull/933).

## Smallest useful delivery

Propose one opt-in, parser-free runtime route for the existing WIRE-PLAN grammar: scalar/enum and ordinary declared
List/Map property values, concrete owned objects, keyed concrete owned maps, and admitted exactly-one-payload
`@Builder.Method` operations. An existing active Builder is the primary integration seam. A thin root convenience uses
the same implementation inside ordinary root construction. Both belong to one delivery slice: root-only delivery would
miss the demonstrated ApplicationHolder/completed-Environment use case; an internal reader alone would not deliver #932.

This revision delivers the plan and a fixed-call test of existing internals only. The proposed class/method spellings
below are review vocabulary, not an API added to this branch. Acceptance of this document must precede a separately
assigned production implementation. No milestone or issue-completion claim follows from planning approval.

### Caller surface proposal

Use one small facade in the existing runtime artifact, with no public descriptor, reader, input-wrapper hierarchy,
registry, parser or session token. Candidate Java signatures (not source declarations in this revision):

```java
// Proposed static methods on KlumStructuredInput; names remain subject to plan review.
<T> T readRoot(Class<T> concreteType, String rootKey, Object parsed, String sourceName);
<B extends KlumBuilder<?>> B applyToBuilder(B builder, Object parsed, String sourceName);
```

`parsed` is caller-owned Map/List/scalar data, never text, a parser or Jackson node. A domain root must be a Map with
String keys; root List/scalar/null rejects with expected-domain-object diagnostics. List/scalar nodes are admitted only
as the payload specified by an entry. Require a nonempty opaque `sourceName`; it is diagnostic text, never a resource to
open. `rootKey` is null for an unkeyed root and an explicit String for a keyed root, outside serialized input. The
carrier-excluded key member is never reintroduced as a domain property. The runtime must not choose a subtype/default
implementation from an abstract root. The caller selects an exact concrete Schema.

Candidate Java usage, followed by Groovy (illustrative only; no callable facade exists yet):

```java
Application result = KlumStructuredInput.readRoot(Application.class, null, parsed, "application.yaml");
Application_DSL.Builder<Application> same =
        KlumStructuredInput.applyToBuilder(activeApplication, parsed, "application.yaml");
```

```groovy
ApplicationHolder.Create.With {
    def app = Application.Create.AsBuilder().One()
    KlumStructuredInput.applyToBuilder(app, parsed, 'application.yaml')
    app.environment(suppliedEnvironment)  // ordinary completed LINK supplied by caller
    application(app)
}
```

A Schema-authored operation still looks like ordinary domain input, rather than an invocation record:

```groovy
@DSL class Application {
    String host
    int port
    @Builder.Method void endpoint(Map<String, String> settings) {
        host = settings.host
        port = Integer.parseInt(settings.port)
    }
}
// Caller-parsed input: [endpoint: [host: 'api', port: '8080']]
// The whole settings map is one payload; it is not named-argument binding.
```

There is no v1 `readTemplate`, `readBuilder`, generated `Create` overload, generic Factory call or auto-selected mode.
Callers allocate an exact child through ordinary `Create.AsBuilder()` or a Schema-specific factory, then apply input.
Template definition/sealed LINK wrappers/completed Models are rejected as targets. Repeated API calls remain ordinary
separate applications, with no merge/layer/repetition language promise.

## Reuse and the necessary internal extension

| Existing mechanism | Reuse | Why direct delegation/extension is insufficient |
| --- | --- | --- |
| `InternalKlumBuilder.applyNamedParameters` | Existing `InvokerHelper` behavior for a selected admitted property/operation | Private helper accepts broader names, follows input iteration, and can spread a List; invoking it would bypass admission and nested external-name translation. Leave it unchanged. |
| Generated public Builder/Factory contracts | Verify recorded descriptors and concrete receiver scope; dispatch only an admitted group | Public visibility/NamedParam catalogs do not establish admission. Plural relationship methods are factory scopes, not universal Map setters. |
| `createSingleChild`, `addNewDslElementToMap` | Target resolution, existing-child reuse, key checks, breadcrumbs, templates, ownership and attachment | Their configuration parameter is currently Map/Closure. Add an internal Consumer configuration variant sharing the existing implementation, without changing generated public signatures. |
| `FactoryHelper.prepareNestedBuilder` / private `prepareBuilder` | Existing Consumer allocation/configuration between PostCreate and PostApply | Already suitable for fresh children. Do not allocate empty children and configure afterward to simulate this ordering. |
| `FactoryHelper.doCreate` / `PhaseDriver.withBuilderLifecycle` | Root ownership, preparation, graph phases and cleanup | Factor a narrow internal Consumer root entry around existing preparation; do not copy its lifecycle code or expose PhaseDriver. |
| Jackson `KlumDeserializer` / `KlumJacksonImporter` | Preserve as independent characterization; reuse construction mechanics conceptually | Field binding, Jackson codecs and replay are not operation dispatch. Extending the importer changes ADR 0009 semantics and duplicates runtime admission. No shared dispatch framework is justified. |

Source seams:
[`InternalKlumBuilder`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/InternalKlumBuilder.java),
[`FactoryHelper`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/FactoryHelper.java),
[`PhaseDriver`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/PhaseDriver.java),
[`ConstructionSession`](../../klum-ast-runtime/src/main/java/com/blackbuild/klum/ast/runtime/internal/process/ConstructionSession.java),
[`GeneratedDslSupport`](../../klum-ast/src/main/java/com/blackbuild/klum/ast/compiler/internal/ast/GeneratedDslSupport.java),
[`KlumJacksonImporter`](../../klum-ast-jackson/src/main/java/com/blackbuild/klum/ast/jackson/KlumJacksonImporter.java).

The Consumer variants must converge with existing creator bodies, preserving existing direct-child resolution and
existing-map-entry configuration. They must not become an alternative attachment engine. An already present child gets
ordinary apply semantics, not a second PostCreate. Fixed concrete relationship selection uses the carrier's owning
property/source and nested Model/Builder identity. Its generated descriptor group verifies that this is the declared
relationship; dynamic Class/Factory selector overloads are never externally selectable.

Every domain Map changes scope to its target's public Builder plan. A keyed outer Map keeps its keys as literal child
keys; each value changes to the child plan. Never infer singular member names, enter a CollectionFactory via Closure,
interpret instance keys as operations, invoke a child root Factory, or construct completed owned Models first. Factories
used internally are only fixed-target Builder allocation; authored/custom Factory operations are outside v1.

## Internal carrier reader and packaging

Put the opt-in facade and implementation in `klum-ast-runtime`; the reader and dispatcher remain in its non-exported
internal packages. There is no new module or qualified export. This uses existing session/creator internals and existing
Schema-to-runtime JPMS openings. A separate optional runtime artifact would require a new internal friendship/opening or
public bridge for no parser/dependency isolation benefit. #529's optional tooling module remains separate and does not
depend on the interpreter. This packaging recommendation does not create a public metadata-access route for #529.

Read only `<exact Model binary name>_DSL$WirePlan.class` from that Model's defining module/classloader, using the Groovy
runtime's existing shaded ASM classfile support to obtain the private DATA ConstantValue. Decode the existing
WIRE-PLAN/B records and recursive payload encoding strictly; do not initialize or instantiate the carrier or Schema.
Confirm model/Builder identities and descriptors against the selected generated public contract. Traverse only that
contract's explicit generated Builder parents, following each exact sibling carrier; do not scan the classpath or pick
an arbitrary classloader resource by a global name. For named modules, qualify byte access as well as the previously
proved private-field access: reflective access proof alone is not proof of the new reader implementation.

Compose direct and inherited facts once. Collapse identical diamond facts by source identity, reject conflicting
sources/external names deterministically, and preserve source declaration plus public contract in diagnostics. A rejection
record need not reject unrelated admitted entries: unsupported Schema members can coexist with supported input. A
supplied name without an admitted entry rejects; collision diagnostics retain every implicated declaration. Existing
rejection records are source-oriented, so do not invent an external alias for every rejected member by parsing prose.

Missing/in-memory-only carriers reject with recompile/package guidance, never fall back to reflection-based admission,
source mirrors or Jackson. Malformed, unsupported-header or inconsistent records reject before dispatch. Use only
successfully compiled and packaged Schema artifacts; a carrier alone is not evidence that compilation succeeded.
Retain the existing single-constant size limit and generated ABI obligation; no new encoding/version negotiation,
chunking, registry, runtime-floor policy or compatibility shim. Qualification must include #938-produced artifacts on
the new reader. Start without a global cache; classloader-aware caching is optional only if measured lookup cost warrants it.

## Payload preparation, admission and order

Preflight the complete supplied tree before the first admitted operation: reject unknown names, wrong shapes, forbidden
nested key members, null at any level, unsupported targets and inconsistent contracts. Validate domain and data-map keys
as Strings. Detect cyclic caller containers by identity on the current traversal stack; a repeated acyclic shared value
can be read twice. Do not mutate input; snapshot containers for preparation so later dispatch cannot observe its own
input mutation. This is local preparation, not a serialized invocation plan or new descriptor protocol.

Use the carrier payload kind/type only. Scalars are String, boolean, supported integral/decimal numbers and enums.
Enum text must match an exact declared constant; convert only during intentional runtime preparation. Numeric conversion
must be exact for integral targets (range/fraction checks); floating targets reject non-finite/overflow values. No numeric
or boolean strings, compact Duration syntax, arbitrary Object binding, constructors, codecs or converter discovery.
Recognize the standard numeric types represented by the existing corpus; if the compiler's broad Number-subclass
classification admits an unimplementable type, stop with that concrete mismatch rather than silently widening conversion.
List/Map payloads recursively use their declared item/value nodes. Carrier LIST facts can describe supported declared
collection interfaces; qualify conversion through existing generated collection contracts (including sets/sorted forms),
not assignment of an unchecked List to an arbitrary declared collection. Any unsupported admitted container is a stop,
not permission to narrow the shared grammar silently. Preserve List element order and literal data-map keys.

For a property or authored operation, dispatch the admitted native name with an explicit single-element `Object[]` around
the prepared value. A Map/List is one argument. Existing Groovy method/converter machinery still selects within the
verified generated group; the interpreter does not rank arbitrary reflective overloads. The only custom operations are
carrier OPERATION entries with exactly one supported payload on the public Builder. Ignore, rename and identity fallback
have already been decided by the compiler; do not reinterpret Jackson annotations at runtime.

Use one deterministic traversal for execution: canonical external-name order within each domain scope, depth-first child
configuration, and literal String-key order for keyed child creation. Preserve data-container contents and List order.
This is a proposed fixed framework order, not source ordering, a caller-selectable sequence or repetition syntax. The
existing carrier already sorts direct entries; inherited merge must also canonicalize. Reversed parsed-Map insertion
order must produce identical observed operation traces. Side-effecting operations can still interact: document this
fixed order, never claim commutativity, infer Model effects, or invent conflict detection between a property and an
operation that changes it. If a real adopter requires a different sequencing policy, stop for a separate decision.

## Lifecycle, ownership and error boundary

| Route | Proposed behavior |
| --- | --- |
| `readRoot` | Reject an active root session before allocation. Preflight; then ordinary root preparation applies Templates, PostCreate, prepared input, PostApply; outer phases/materialization/validation run once. No nested root. |
| `applyToBuilder` | Require an active, same-thread, same-session, unsealed generated Builder before preflight. Apply one prepared input and one ordinary PostApply; never replay PostCreate, seal or complete it. Return the identical precise B. |
| Fresh owned child | Existing creator with Consumer runs normal preparation once, configures before PostApply, attaches through existing ownership checks; completes with the parent. |
| Existing owned child/key | Existing creator reuses it, checks type/key, applies input and PostApply; no new root, PostCreate or ownership claim. |

These counts refer to this route's work. `AsBuilder().One()` already runs create/apply, and subsequent `applyToBuilder`
runs another apply; calling it inside `Create.With` can likewise precede the enclosing apply. Do not promise exactly one
PostApply for an entire orchestration with explicit additional applications. Root dispatch must call configuration-only
internals rather than public `applyToBuilder` and accidentally add another PostApply. Jackson's measured readBuilder and
applyToBuilder create/apply replay remains unchanged, including its different callback counts.

Caller-provided completed LINK context attaches through normal Builder calls before enclosing configuration returns.
Defaults/Owner/AutoLink/PostTree/materialization/validation remain existing phases. Early create/apply callbacks cannot
assume context attached afterward; use an existing context-aware Schema factory/attach-before-apply arrangement if needed.
No serialized LINK/reference lookup, context injection, session API or phase scheduling is added.

For local input/dispatch failures, use KlumModelException with source identity, escaped JSON Pointer (`~` to `~0`, `/` to
`~1`, list indices), external name, selected public contract, expected shape/type, actual shape/type, and original cause.
Unknown names report the target contract and no selected operation; do not fabricate a successful selection. Keep the
external pointer separate from the existing immutable construction path. Do not append source/path metadata to the
Schema, carrier or completed Model, or claim retained provenance/lineage or parser line/column information.

Preflight errors occur before this input invokes admitted operations; existing active Builder state can of course
predate the call. Once Schema code executes, no rollback is promised. An operation can mutate then throw; preserve its
cause and abort propagation. Root failures escape through existing PhaseDriver cleanup; later validation failures retain
existing Klum validation types/details. Add source/root context around root completion only where it preserves those
types; do not attribute lifecycle failures to the last input leaf. Active-Builder later completion belongs to the caller
and cannot receive a fabricated retained input pointer. Do not catch JVM Errors, wrap every framework exception, or allow
partial failure to be reported as successful application.

## Evidence available now and its limits

[Shared evidence](evidence/issue-932-structured-input-probe.md) records named-map/Jackson differences, all four managed
modes, the Holder/LINK route, one-payload Map/List calls, deterministic naming facts and no-execution carrier extraction.
The implementation source shows that root Consumer preparation is private and nested Consumer preparation already exists.
The planned creator Consumer factoring, strict runtime decoding, numeric adaptation and facade are not implemented here.

The new [StructuredInputRuntimeBoundaryProbeTest](../../klum-ast/src/test/groovy/com/blackbuild/klum/ast/runtime/internal/StructuredInputRuntimeBoundaryProbeTest.groovy)
contains fixed calls, not an interpreter or fixture-name admission engine. It proves existing Consumer child preparation,
explicit one-argument List/Map dispatch, owner/key identity, pre-completion Builder state, normal final phases and
cause/session cleanup on failure. Its Groovy closures are authored test configuration/SAM fixtures, not synthesized
interpreter closures. It deliberately attaches through current operations and the existing internal map-adder; it does
not claim to have qualified reused-child Consumer creators, root Consumer access, the reader or full diagnostics.

The first run tried to pass a Builder-valued Map to the generated plural `entries` operation and failed: that contract
exposes a factory scope/other overloads, not a general Map setter. The corrected bounded probe uses the existing keyed
attachment primitive. This is evidence for using compiler relationship roles plus existing creators, not extending named
maps or guessing singular method names.

## One production slice, reasoned commit boundaries

Keep WIRE-RUNTIME as the work label. These are commits within one proposed delivery, not independently published APIs.

1. **Consume the existing carrier internally.** Add strict classfile reader/composition with corpus assertions, missing/
   corrupt-carrier negatives, inherited collisions and a #938 binary dependency fixture. No facade yet. Acceptance:
   runtime facts equal the compiler/#529 proof facts; accepted/rejected reading initializes no Schema/enum/factory code;
   runtime-module/classloader and named-module Groovy 4/5 access needs no new flag/export. Stop on metadata insufficiency.
2. **Share configuration with ordinary creators.** Factor internal Consumer variants for root preparation, direct children,
   keyed maps and existing-child apply, retaining current Map/Closure paths as adapters. Acceptance: paired legacy/new
   callback traces, Template/default/key/owner behavior, preconfigured/reused child identity, completed-LINK identity,
   session/thread/sealing rejection, nested-root rejection, failure cleanup and all four Jackson modes unchanged.
3. **Deliver the restricted route and documentation together.** Add proposed facade, preflight/payload preparation,
   admitted dispatch and diagnostics, using the previous commits. Acceptance: every grammar production and negative below;
   Java/static/dynamic Groovy consumers; Groovy 3/4/5; documentary test and release documentation. No partial API shipment
   between commits 1 and 3. Review the local sequence before first publication.

Acceptance inventory, shared with #529 where structurally meaningful:

| Requirement | Executable acceptance for production work |
| --- | --- |
| Scalar/property/operation | String/boolean/numeric boundaries/enum; ordinary nested List/Map; renamed and inherited one-payload operations change several properties; List/Map never spread; standard collection target conversion |
| Concrete nesting and keyed maps | Child uses its plan; outer keys `a/b~c`, empty/punctuated strings stay literal under existing key rules; duplicate/conflicting child key member rejects; direct keyed child uses only existing declared key derivation |
| Admission and names | Ignore/rename/identity; inherited/overload collisions report every source; unrelated admitted entries remain usable; unknown/helper/copy/query/lifecycle and zero/multi-arg controls reject before any admitted operation |
| No broadened grammar | Null/reset, abstract/polymorphic targets, dynamic selector, unkeyed relationship collections, CollectionFactory/custom factory/converter operations, references and arbitrary caller objects reject |
| Session and completion | Both routes; Holder + completed Environment; fresh/reused child; one root late lifecycle; no premature sealing; wrong thread/stale/sealed/Template targets reject; no nested root |
| Diagnostics/failures | Escaped nested/keyed/list paths; source/contract/expected/actual/cause; late lifecycle keeps existing failure types and no invented leaf pointer; operation mutation then throw; fresh root succeeds after failed root |
| Source-order independence | Opposite Map insertion orders, inherited/local names and keyed-child maps have identical canonical operation traces; List payload order stays intact |
| Metadata/module compatibility | Packaged #938 artifacts, separately compiled inherited Schema/API, missing/malformed carrier, generated linkage inventory, fresh-JVM no-execution and actual runtime-module named-schema byte reading |
| Preserved behavior | Existing named-map suites and `StructuredInput*ProbeTest`/`KlumJacksonImporterSpec` retain all four independent modes; no mapper, callback-order or diagnostic parity rewrite |

Use existing Spock/compiler fixtures and `klum-ast/src/test/scenarios/` where appropriate. Every new test carries
`@Issue("932")`; a first public feature example adds `@Tag("documentary")` and `@See` for its matching user page.
Production delivery updates `docs/user/Structured-Input.md`, `_Sidebar.md`, applicable Builder-first/import migration
navigation, `CHANGES.md` and the #932 acceptance links; it states numeric, canonical-order, null, callback and error
limits. No release/user documentation claims this feature exists in the planning PR. #529 stays independently open;
its corpus follows the same grammar without depending on runtime execution.

## Stop conditions and review boundary

Stop before implementation if plan review requires different root/active callback semantics, caller-controlled order,
an optional-module bridge or a different public surface. The concrete alternatives are the recommended runtime facade
above versus active-Builder-only delivery using ordinary roots; neither requires a new lifecycle API. A separate optional
artifact would need a fresh module/access decision and is not silently substituted.

During the proposed implementation, stop if exact contract/role resolution needs a public descriptor/reader/SPI, registry,
reflection-based discovery, synthetic closures, generated internal linkage or a changed carrier encoding; if named-module
classfile access fails; if concrete keyed direct targets need an unspecified external key encoding; if a supported numeric/
collection contract cannot consume the declared payload without new conversion policy; or if existing creator semantics
cannot survive Consumer factoring. Present the failing fixture and narrow alternatives before widening the slice.

No parser dependency, Jackson behavior change, generic reflection resolver, lifecycle/session API, ordering/repetition
syntax, polymorphism, collection factory, zero/multi-argument call, reset/clear, reference mechanism or source-order promise
belongs to this slice. Any need for these ends the slice's current authorization. #932 and #529 stay open; a planning PR
uses Related links, remains draft and is not merged by this worker.
