# WIRE-RUNTIME — proposed first #932 production slice

Date: 2026-10-22. Delivery status reconciled: 2026-10-23. Status: **approved restricted WIRE-1 slice merged in PR #940**.
Decision: [ADR 0031](../adr/0031-structured-schema-input-artifacts.md).
Language owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Independent static projection: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
Baseline: `3d13ffa2d` (merged [#938](https://github.com/klum-dsl/klum-ast/pull/938)), after
[#937](https://github.com/klum-dsl/klum-ast/pull/937) and [#933](https://github.com/klum-dsl/klum-ast/pull/933).

The planning text and initial-probe validation below retain their historical revision vocabulary. The separately
assigned WIRE-1 work now implements the two proposed facade methods, private carrier reading, shared Consumer creator
paths, complete ordered preflight and exact execution. Current implementation evidence, including the resolved key-derivation decision, is tracked in
[WIRE-1 runtime qualification](evidence/issue-932-wire-1-runtime.md); user behavior is documented in
[Structured Input](../user/Structured-Input.md). This does not authorize a parser, generator, additional grammar or merge.
The evidence's unpublished handoff is historical: [PR #940](https://github.com/klum-dsl/klum-ast/pull/940) is now merged.
[SCHEMA-0](adr-0031-schema-0.md) reconciles the independent generator plan without changing this runtime contract.

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

Candidate Java usage, followed by Groovy (illustrative only; no callable facade exists yet). The root example assumes
self-contained early construction; externally supplied early dependencies require an already-prepared Builder:

```java
Application result = KlumStructuredInput.readRoot(Application.class, null, parsed, "application.yaml");
Application_DSL.Builder<Application> same =
        KlumStructuredInput.applyToBuilder(activeApplication, parsed, "application.yaml");
```

The Holder example below supplies LINK context for later lifecycle phases only. It is valid only when PostCreate and
any early PostApply do not need that dependency; it is not a context-aware factory example.

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
| `InternalKlumBuilder.applyNamedParameters` | Existing Groovy dispatch machinery after exact admitted-signature selection | Private helper accepts broader names, follows input iteration, and can spread a List; invoking it would bypass admission and nested external-name translation. Leave it unchanged. |
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
input mutation. Build the prepared representation by visiting supplied entries in their own iteration order recursively,
including keyed-map entries, and retain List element order. Complete preflight neither invokes user code nor mutates an
active Builder: no factory/constructor, initializer, callback, operation, converter, enum initialization or dynamic
MetaClass selection runs here. Validate names, shape, types and contract descriptors through metadata only. This is local
preparation, not a serialized invocation plan or new descriptor protocol. A failure anywhere prevents all execution for
this input, including entries that preceded the failure during preflight.

Use the carrier payload kind/type only. Scalars are String, boolean, supported integral/decimal numbers and enums.
Enum text must match an exact declared constant discovered without initialization (for example, enum field declarations
in class bytes). Materialize enum values only during execution; `getEnumConstants`/`Enum.valueOf` must not run in preflight.
Numeric conversion
must be exact for integral targets (range/fraction checks); floating targets reject non-finite/overflow values. No numeric
or boolean strings, compact Duration syntax, arbitrary Object binding, constructors, codecs or converter discovery.
Recognize the standard numeric types represented by the existing corpus; if the compiler's broad Number-subclass
classification admits an unimplementable type, stop with that concrete mismatch rather than silently widening conversion.
List/Map payloads recursively use their declared item/value nodes. Carrier LIST facts for fields describe supported
Schema collection declarations, including sets/sorted forms, whose Builder-managed containers are populated through the
compiler-selected generated element path. Do not construct a whole container argument or assign an unchecked List to
those declarations. Authored operations retain ordinary List/Map arguments and reject whole-container targets requiring
construction or guessing. Any unsupported admitted container is a stop, not permission to narrow the shared grammar
silently. Preserve List element order and literal data-map keys.

For a property or authored operation, keep an explicit single-element `Object[]` around the prepared payload. A Map/List
is one argument. Native-name-only `InvokerHelper.invokeMethod` is insufficient: `choose(int)` and `choose(Integer)` can
have different external names or exclusions, yet boxed input selects the wrapper overload regardless of admission.
For an OPERATION, take the exact declared parameter signature from its carrier descriptor and resolve it through Groovy's
existing `MetaClass.pickMethod` during execution, using the declared signature rather than the runtime payload's class.
Preflight checks contract descriptors without dynamic dispatch. Verify the selected method against that exact public
contract signature before invoking the same selected `MetaMethod`; never perform a second native-name lookup. The bounded
primitive/wrapper fixture proves that this preserves the primitive call even when an Integer overload is ignored.
For a scalar PROPERTY, use its declared payload type with the same existing Groovy selection and verify membership in
the compiler-recorded property group. For a collection/map PROPERTY, visit the prepared elements/entries in supplied order
and invoke its compiler-selected generated element/keyed population descriptor using the declared item/key/value types.
The declared field container type is not a whole-container invocation argument. Stop if bridges/projection or a generated collection/converter group cannot satisfy
this check; do not rank arbitrary reflective candidates or invoke outside the group. Full property-group qualification
remains a production acceptance gate. The only custom operations are carrier OPERATION entries with exactly one supported
payload on the public Builder. Ignore, rename and identity fallback have already been decided by the compiler; do not
reinterpret Jackson annotations at runtime.

Execute the prepared traversal in the caller-supplied order: visit each Map in its own iteration order recursively,
including nested domain scopes and keyed-child creation, preserve List payload element order, and invoke admitted Builder
operations in that order. Insertion-ordered Maps preserve the caller's selected order. Unordered Maps expose their
implementation-defined iteration order; the runtime must not impose sorting to make it reproducible. There is no
scheduler, ordering configuration, commutativity claim or portable YAML/JSON source-order guarantee. Model Writers should
use order-sensitive custom `@Builder.Method` operations deliberately: reversing entries may change both traces and Model
values. No inferred effect/conflict analysis or repeated-call syntax follows from this contract.

Carrier metadata retains its deterministic ordering. Carrier lookup and inherited-plan composition resolve and validate
names only; they must not drive iteration or reorder the prepared traversal. Walk the supplied entries and look each one
up in the resolved admission map, rather than filtering/iterating carrier entries to construct execution order. JSON Schema
may sort properties for reproducible artifacts without affecting interpreter behavior. Do not change WIRE-PLAN/B
serialization or normalization. Preflight builds the ordered representation completely; execution follows it without
re-reading or reordering the input. Once execution starts, ordinary side effects remain and no rollback is promised.

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

Construction dependencies belong to the Schema/caller. Schema-specific factories must prepare dependencies needed by
PostCreate or early PostApply before those callbacks run; the interpreter configures the resulting already-prepared,
active unsealed Builder. `AsBuilder().One()` has already run create/apply: attaching a dependency afterward cannot satisfy
those earlier callbacks. No generic context-aware factory API is proposed here, and no unqualified factory example is
claimed. If the Schema has no existing construction path meeting that constraint, integration waits for an appropriate
Schema-specific path rather than making the interpreter inject dependencies, delay phases or reorder callbacks.

Caller-provided completed LINK context needed only by later phases can attach through normal Builder calls before
enclosing configuration returns, as in the qualified Holder example. Defaults/Owner/AutoLink/LinkTo/PostTree/materialization/
validation retain their normal lifecycle. The interpreter does not change callback timing or responsibility for context.
No serialized LINK/reference lookup, context injection, session API or phase scheduling is added. The proposed
`readRoot`/`applyToBuilder` signatures stay unchanged; callers with externally prepared early dependencies use the latter.

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
contains bounded fixture-only experiments, not a production reader or interpreter. It proves existing Consumer child preparation,
explicit one-argument List/Map dispatch, owner/key identity, pre-completion Builder state, normal final phases and
cause/session cleanup on failure. A third fixture proves that native-name dispatch can invoke an ignored primitive/boxed
overload, while exact carrier-signature selection through existing Groovy MetaClass preserves the admitted operation.
The companion admission test verifies the distinct renamed descriptors and ignored-overload rejection in the carrier
plan. One compact order-sensitive fixture then reads its actual carrier names, composes the explicitly selected parent
carrier, and separates complete preparation from execution for three fixed operation signatures and two concrete
relationships. Reversed LinkedHashMaps change traces and values; nested Maps, keyed children and List payloads retain
supplied order. Unknown entries first, last, nested or in a later keyed child prevent all execution and leave the active
Builder unchanged. This bounded experiment is not full production preflight, decoding or inheritance qualification.
Its Groovy closures are authored test configuration/SAM fixtures, not synthesized interpreter closures. It deliberately attaches through current operations and the existing internal map-adder; it does
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
3. **Deliver the restricted route and documentation together.** Add proposed facade, complete code-free preflight/payload
   preparation retaining supplied traversal order, admitted dispatch and diagnostics, using the previous commits. Acceptance: every grammar production and negative below;
   Java/static/dynamic Groovy consumers; Groovy 3/4/5; documentary test and release documentation. No partial API shipment
   between commits 1 and 3. Review the local sequence before first publication.

Acceptance inventory, shared with #529 where structurally meaningful:

| Requirement | Executable acceptance for production work |
| --- | --- |
| Scalar/property/operation | String/boolean/numeric boundaries/enum; ordinary nested List/Map; renamed and inherited one-payload operations change several properties; List/Map never spread; renamed/ignored primitive-wrapper overloads cannot escape the selected descriptor; standard collection target conversion |
| Concrete nesting and keyed maps | Child uses its plan; outer keys `a/b~c`, empty/punctuated strings stay literal under existing key rules; duplicate/conflicting child key member rejects; direct keyed child uses only existing declared key derivation |
| Admission and names | Ignore/rename/identity; inherited/overload collisions report every source; unrelated admitted entries remain usable; unknown/helper/copy/query/lifecycle and zero/multi-arg controls reject before any admitted operation |
| No broadened grammar | Null/reset, abstract/polymorphic targets, dynamic selector, unkeyed relationship collections, CollectionFactory/custom factory/converter operations, references and arbitrary caller objects reject |
| Session and completion | Both routes; Holder + late-phase completed Environment; Schema-specific construction supplies dependencies before early callbacks; interpreter configures an already-prepared Builder; fresh/reused child; normal AutoLink/LinkTo; one root late lifecycle; no premature sealing; wrong thread/stale/sealed/Template targets reject; no nested root |
| Diagnostics/failures | Escaped nested/keyed/list paths; source/contract/expected/actual/cause; late lifecycle keeps existing failure types and no invented leaf pointer; operation mutation then throw; fresh root succeeds after failed root |
| Supplied traversal order | Reversed LinkedHashMaps reverse operation traces and order-sensitive results; nested maps preserve nested operation order; keyed-child creation follows entry order; List payload element order is retained; carrier lookup/inheritance never reorders prepared entries |
| Complete preflight | Unknown/invalid entries at the start, end or in nested/keyed input prevent all execution and leave an active Builder unchanged; preparation invokes no user code and retains supplied traversal order |
| Metadata/module compatibility | Packaged #938 artifacts, separately compiled inherited Schema/API, missing/malformed carrier, generated linkage inventory, fresh-JVM no-execution and actual runtime-module named-schema byte reading |
| Preserved behavior | Existing named-map suites and `StructuredInput*ProbeTest`/`KlumJacksonImporterSpec` retain all four independent modes; no mapper, callback-order or diagnostic parity rewrite |

Use existing Spock/compiler fixtures and `klum-ast/src/test/scenarios/` where appropriate. Every new test carries
`@Issue("932")`; a first public feature example adds `@Tag("documentary")` and `@See` for its matching user page.
Production delivery updates `docs/user/Structured-Input.md`, `_Sidebar.md`, applicable Builder-first/import migration
navigation, `CHANGES.md` and the #932 acceptance links; it states numeric, supplied-traversal-order, null, callback/context and error
limits. No release/user documentation claims this feature exists in the planning PR. #529 stays independently open;
its corpus follows the same grammar without depending on runtime execution.

## Stop conditions and review boundary

Stop before implementation if plan review requires different root/active callback semantics, additional ordering configuration,
an optional-module bridge or a different public surface. The concrete alternatives are the recommended runtime facade
above versus active-Builder-only delivery using ordinary roots; neither requires a new lifecycle API. A separate optional
artifact would need a fresh module/access decision and is not silently substituted.

During the proposed implementation, stop if exact contract/role resolution or property-group selection needs a public descriptor/reader/SPI, registry,
reflection-based discovery, synthetic closures, generated internal linkage or a changed carrier encoding; if named-module
classfile access fails; if concrete keyed direct targets need an unspecified external key encoding; if a supported numeric/
collection contract cannot consume the declared payload without new conversion policy; or if existing creator semantics
cannot survive Consumer factoring. Present the failing fixture and narrow alternatives before widening the slice.

No parser dependency, Jackson behavior change, generic reflection resolver, lifecycle/session API, ordering/repetition
syntax, polymorphism, collection factory, zero/multi-argument call, reset/clear, reference mechanism or source-order promise
belongs to this slice. Any need for these ends the slice's current authorization. #932 and #529 stay open; a planning PR
uses Related links, remains draft and is not merged by this worker.

## Validation of the initial planning revision

- Final `StructuredInputAdmissionPlanTest` and `StructuredInputRuntimeBoundaryProbeTest` run on `test`, `groovy4Tests`
  and `groovy5Tests`: **18 cases per lane, zero failures/errors/skips**. The added runtime fixtures use fixed calls only.
- Compiler and Jackson full Groovy 3/4/5 suites were exercised. All unchanged cases passed; the added Groovy 5 overload
  fixture exposed an annotation-loading setup error, corrected before the final focused run above. This does not claim
  that a production reader, property-group adapter or interpreter has been implemented or qualified.
- Jackson module suites: **121 / 119 / 119 cases**, zero failures. Both modules' `license` and
  `verifyTestLaneIsolation` tasks pass. Markdown fences/local file links and `git diff --check` pass.
- Independent Standards review: zero findings. Specification review's descriptor-selection gap was addressed with the
  exact-signature proposal and paired fixtures, then accepted on re-review subject to the passing checks above.

The draft planning PR keeps Related links to #932/#529. Public behavior, generated contracts and Jackson production
sources are unchanged. Remote CI/SonarCloud must be inspected on the published revision; local success is not remote CI
or implementation approval.

## Maintainer traversal/context refinement

The maintainer refinement replaces the earlier canonical execution-order proposal with supplied Map iteration recursively
and retained List element order. Deterministic carrier lookup/inheritance and JSON Schema artifact ordering remain separate;
WIRE-PLAN/B normalization/serialization is untouched. Complete preflight prepares that same traversal without user-code
execution or Builder mutation. Dependencies needed by early callbacks remain Schema-specific construction responsibilities;
normal AutoLink/LinkTo and the proposed entry signatures are unchanged.

`StructuredInputRuntimeBoundaryProbeTest#'prepared caller traversal survives inherited carrier lookup and complete preflight precedes execution'`
uses one closed, order-sensitive fixture. Its actual fixture/parent carrier lookup cannot reorder preparation. It covers
reversed LinkedHashMaps, nested operation order, keyed-child creation order, retained List order and first/last/deep
preflight errors preventing all execution. These are bounded planning experiments, not a production adapter or a test
framework extension.

Validation of this follow-up: the affected runtime-boundary specification passes **4 cases per Groovy 3/4/5 lane**, with
zero failures/errors/skips. Module license and test-lane isolation tasks, changed Markdown local links/fences and
`git diff --check` pass. Local Standards/Specification review found no remaining discrepancy with the maintainer's six
refinements. No planning blocker remains; the existing production reader/creator/property-group qualification gates
still apply to a separately authorized implementation. #932/#529 remain open and no milestone or production behavior changes.

## WIRE-1 numeric admission clarification

The maintainer-approved numeric scalar targets are exactly `byte`, `short`, `int`, `long`, `float`, `double`, their boxed
JDK types, `BigInteger` and `BigDecimal`. Every other `Number` type is rejected during compiler admission, including
inside supported List/Map payloads, with the unsupported type and source field or Builder operation identified.
The private carrier is unreleased: retain WIRE-PLAN/B unchanged and add no legacy-carrier fallback, compatibility
branch, custom constructor/converter policy, versioning or public API for this correction.
[The stop reproduction and decision](evidence/issue-932-wire-1-numeric-stop.md) record why this correction precedes runtime implementation.

## WIRE-1 collection population clarification

Supported field declarations and authored-operation arguments are separate admission decisions. Keep the existing finite
DSL field container set: List, Set, SortedSet, NavigableSet, EnumSet, Map, SortedMap and NavigableMap. External collection
field data is a List of admitted element payloads; external map field data is a Map of literal String keys to admitted
value payloads. The interpreter adds those prepared elements/entries in caller-supplied order through compiler-selected
generated element/keyed population operations. Normal Builder mechanics allocate and manage the actual declared
container, including set deduplication and sorted storage. A supported NavigableSet field is not a request to construct a
NavigableSet argument or invoke a replacement setter.

The compiler must select and record the generated population method using its owning-field provenance, including explicit
Field.members names. A same-named authored override in a broad plural descriptor group is not population authority.
Existing carrier name/descriptor/source/role slots encode this selection; no carrier-format change or runtime guessing is
required. Keyed owned maps retain the existing fixed-target nested Builder Consumer creator and attachment path.
Generated keyed-add calls may use key and value internally; no authored multi-argument wire operation is introduced.

Authored operations keep the admitted exactly-one-payload scalar/enum/ordinary List/Map grammar. Reject whole-container
targets requiring Set/NavigableSet, broad Collection, concrete/custom Collection/Map implementations or construction
heuristics. Nested ordinary data payloads must likewise require no guessed container. Authored Iterable methods are not
currently admitted and stay rejected; generated Iterable population of a field does not extend that grammar.
The opaque List/Map reproduction is retained as rejection coverage. No constructor/converter policy, replacement-container
policy, legacy handling, public API, parser/Jackson change or CollectionFactory syntax follows from this clarification.

[Actual generated field/operation characterization](evidence/issue-932-wire-1-collection-contracts.md) records all supported categories and the authored Iterable boundary.

## WIRE-1 literal owned-map key clarification

V1 compiler admission excludes keyed owned-map declarations carrying `@Field.keyMapping`. Literal structured instance
keys conflict with keyMapping-derived keys: ordinary attachment evaluates the mapping even when an explicit key is
supplied, and distinct input keys may then collapse. Record the owning source field and this reason as a rejection;
unrelated admitted fields remain available. Owned maps without keyMapping retain their existing literal String-key path.

This restriction changes structured admission only. Ordinary Builder DSL keyMapping, generated contracts, WIRE-PLAN/B
encoding and attachment/reuse semantics stay unchanged. It introduces no structured-key projection, collision/reuse rule,
preflight closure execution, runtime-only rejection, legacy handling or public API.

## WIRE-1 unreleased fixed direct-child key refinement

The maintainer approved one private carrier-format refinement before release: append a twelfth `entry` token,
`fixedChildKey`, after the existing recursive payload token. `-` means absent; otherwise the token is the existing
canonical Base64-encoded nonempty String literal. The WIRE-PLAN/B header, carrier class/DATA identity and preceding
fields stay unchanged. A non-null fixed key is valid only for a keyed concrete `OWNED_OBJECT` entry.

The compiler uses its existing fixedKeys Cluster membership decision to persist the native field-name key. Runtime
passes that literal directly to the existing Consumer creator; it does not inspect Cluster membership, infer from
public overloads, substitute the external alias, synthesize `@Field` metadata, replay application or change attachment.
An unrelated/nonselected field retains an absent fact. Explicit `@Field.key` continues through ordinary creator logic.
Direct and separately compiled inherited facts, malformed-role diagnostics and actual named-module byte reading are
qualified. This adds no public API or generated public contract.

This is an explicitly approved refinement of an unreleased private ABI, not a compatibility-free precedent. The reader
requires the refined record shape; earlier eleven-token entries, including the untouched #938 binary fixture, reject
with recompile/package guidance. There is no legacy decoder, version negotiation or inference fallback. The original
fixture bytes and provenance remain intact. The historical reader acceptance result predates this approved change.
