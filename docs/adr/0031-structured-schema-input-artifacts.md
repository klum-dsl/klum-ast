# Structured-input interpretation and Gradle-driven JSON Schema generation

Date: 2026-10-20. Architecture revised: 2026-10-23 (reflective generator decision). Delivery status reconciled: 2026-10-23.

Status: **Accepted direction; compiler admission seam and restricted WIRE-1 runtime merged; opt-in reflective Gradle projection implemented; real ScHelm acceptance pending**.
Merged #937/#938 completed `WIRE-PLAN`. The maintainer-approved [WIRE-RUNTIME v1 plan](../implementation/adr-0031-wire-runtime-v1.md)
is implemented by WIRE-1, including the two `KlumStructuredInput` entry points, ordered
preflight/dispatch and shared Construction callbacks. [Runtime evidence](../implementation/evidence/issue-932-wire-1-runtime.md)
records the pre-publication qualification checkpoint; [PR #940](https://github.com/klum-dsl/klum-ast/pull/940) is now merged.
Broader #932 remains open. [SCHEMA-0](../implementation/adr-0031-schema-0.md) reconciles the independent #529 delivery
plan against that merged baseline and merged SCHEMA-T (#942). The maintainer revision below supersedes the
carrier-first generator approach of #941/#942. SCHEMA-R and the subsequent WIRE migration are complete as recorded below.
The older SCHEMA-1 branch remains paused; the independently assigned SCHEMA-GRADLE delivery below adds the first Gradle path. No release is assigned.

Interpreter owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
[Implementation plan](../implementation/adr-0031-structured-schema-input-artifacts.md).
Need horizon: identified ScHelm demand, provisional next 4.x integration line; no milestone assigned.

Parent decisions: [Builder-first construction](0003-builder-first-materialization.md),
[AsBuilder composition](0004-asbuilder-composition-protocol.md),
[public generated contracts](0005-generated-dsl-support-api.md),
[Jackson interoperability](0009-jackson-interoperability.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md),
[named-map dispatch](0022-conservative-named-map-safety.md).


## SCHEMA-GRADLE delivery — 2026-10-23

The separately assigned [SCHEMA-GRADLE slice](../implementation/evidence/issue-529-schema-gradle.md) adds opt-in `generateKlumJsonSchema` to the existing
Schema plugin. `klumSchema.jsonSchema.rootTypes` selects fully qualified DSL Model binary names; declared classpath and
ordinary output files are tracked for up-to-date, build-cache and configuration-cache behavior. A private plugin
projector uses `CompiledBuilderSchema` as its sole metadata seam in a closeable loader with the platform loader as parent.
The runtime link is compile-only for plugin implementation; the inspected runtime comes from the consumer's explicit
classpath. No new runtime dependency, public generator API, CLI, carrier, registry or publication contract is added.
Real ScHelm root/output acceptance remains consumer-owned and pending; #529 remains open, with #932 independent.
The older standalone SCHEMA-1 branch remains paused and untouched. Earlier checkpoints below retain their dated context.

## Final compiled-contract checkpoint — 2026-10-23

[WIRE-REPLACE #947](https://github.com/klum-dsl/klum-ast/pull/947) selected the internal, package-private
`CompiledBuilderSchema`: reflected public Builder/Factory signatures and types, exact-contract selective annotation
bytes, and minimal generated field/selection/exclusion and child/fixed-key facts. [WIRE-REMOVE evidence](../implementation/evidence/issue-529-wire-remove.md)
records removal of companion emission, serialization, readers, binary resources and transitional comparison machinery.
There is no production carrier dependency, fallback or compiled-carrier compatibility promise: the carrier was internal
and unreleased. Required missing enrichment retains recompile/package guidance. Fixed expected contracts now guard the
final facade; useful binary/no-execution, inheritance, documentation, runtime and JPMS regressions remain.

SCHEMA-R #945 and WIRE-SCHEMA-PROBE #946 are complete historical checkpoints. The next separately assigned #529 work is
reflective structural projection inside the Schema Gradle plugin, followed by its isolated classpath and output/cache
qualification. No generator starts here. The historical SCHEMA-1 branch stays paused and untouched; #932 runtime language
and lifecycle semantics remain independent. Earlier carrier descriptions and probe instructions below record migration
history, not active architecture or outstanding assignments.

## Problem and ownership

Model Writers need structured input capable of invoking Schema-authored construction operations, including operations
that configure several properties. Schema Developers also need JSON Schema describing that language for authoring tools.
A field-binding schema alone cannot establish the semantics of custom Builder methods, argument forms or nested scopes.

Two related but independent capabilities address these needs:

| Capability | Owns | Boundary |
| --- | --- | --- |
| Structured Input Interpreter (#932) | Restricted operation admission, naming, arguments, nested dispatch, ordering, diagnostics and Construction-session integration over a parsed Map/List/scalar tree | Optional; parsers caller-owned, no YAML/Jackson dependency in core; not a second Model construction engine |
| JSON Schema Generator (#529) | Optional Gradle-driven Draft 2020-12 generation describing the admitted language from compiled contracts | Does not execute input or own language semantics; no public descriptor protocol |

The two capabilities share an accepted structural subset and may reuse minimal enrichment metadata. Neither defines the
other or requires the other's implementation. In particular, #529 does not depend on WIRE runtime implementation or its
private plan reader. #932 owns runtime admission/execution; #529 owns contract extraction and JSON Schema projection.
Changes to their overlapping names/shapes require explicit reconciliation, not automatic adoption of one consumer's internals.
Managed Jackson import remains independently supported foreign-format interoperability under ADR 0009. It is neither
replaced by the interpreter nor the sole grammar for structured input. Any new opt-in operation language leaves existing
Jackson public signatures, modes, mapper behavior and diagnostics intact; this ADR does not redefine Jackson's wire contract.

## Accepted principles and first-tracer boundary

### Accepted architectural principles

The accepted language direction is a concise, descriptor-directed domain tree rather than a generic Builder call record.
Compiler-owned admission, deterministic external naming, existing Builder-first construction and a shared structural
contract for runtime and generator are architectural principles, not tracer conveniences. Schema-authored custom Builder
methods are first-class language operations; they are not exceptional escape hatches or inferred storage properties.

### Deliberately limited `WIRE-PLAN` grammar

`WIRE-PLAN` deliberately proves only scalar/enum and ordinary declared collection/map properties, concrete nested owned
objects, keyed domain maps whose outer keys are instance keys, and explicitly classified Schema-authored `@Builder.Method`
operations with exactly one payload. It uses identity fallback with explicit rename/exclusion. A declared Map or List
remains one payload and is never globally spread into arguments. An admitted operation may still change several Builder
properties; its input describes the declared payload, not inferred Model effects.

Generated setters/adders may share a property representation where justified; custom operations must not be flattened into
invented storage properties. Public visibility, NamedParam eligibility or an arbitrary method-name pattern alone is not
admission. Unknown/ambiguous roles require diagnostics.

### Intended extensions requiring separate decisions

The following remain intended language extensions but require separate syntax, admission and projection decisions after
the tracer: multi-argument `@Builder.Method` operations; custom converter and factory operations; camelCase and kebab-case
naming strategies; unkeyed relationship collections; `CollectionFactory` scopes and element creators; and additional
argument representations where justified. Zero-argument operations, ordered/repeated-call carriers, reset/clear conventions
and polymorphic aliases remain deferred questions. None is permanently rejected merely because `WIRE-PLAN` omits it, and
this ADR selects no deferred syntax.

Accepted guardrails:

- Generated dynamic factory overloads with leading generic Map values-bag and/or first/second-position runtime Class/Factory
  selectors are not serializable operations. Ordinary Map<K,V>-valued properties remain eligible with a qualified key/value
  representation; do not confuse data maps with control arguments or every custom factory with a generated selector.
- Filter untargeted closures and framework lifecycle/session/helper/copy controls. Do not blanket-exclude Schema-authored
  Builder methods as helpers. Existing broad named-map calls, including copyFrom, retain their behavior unchanged.
- Nested owned values use Builders and the existing Construction session, not prebuilt Models. LINK/AutoLink resolution
  belongs to ordinary lifecycle work; no generic reference registry or external-context injection framework is introduced.
  Schema-specific factories must supply dependencies needed by PostCreate/early PostApply before those callbacks run.
  The interpreter configures an already-prepared Builder; it neither injects dependencies, delays phases nor reorders
  callbacks. Context acquisition/provider selection remains external; AutoLink/LinkTo keep their normal lifecycle.
- Runtime traverses supplied Maps recursively in their own iteration order and preserves List element order. Insertion-
  ordered Maps preserve caller-selected order; unordered Maps expose implementation-defined order. Use order-sensitive
  custom Builder operations deliberately. There is no sorting, scheduler, ordering configuration, commutativity claim or
  portable YAML/JSON source-order guarantee. Domain objects do not gain a repeated-call or generic call-record syntax.
- Complete preflight validates and prepares the entire supplied tree in supplied traversal order without invoking user
  code or mutating an active Builder. Execution follows that prepared order; ordinary side effects have no rollback
  promise. Deterministic contract ordering and inherited-name lookup validate names without reordering execution. JSON
  Schema may sort metadata for reproducible artifacts independently. No serialized admission plan is emitted.

## Naming and coexistence

Resolve each admitted external name exactly once for both capabilities. Precedence is `@JsonIgnore(true)`, then a literal
nonempty `@JsonProperty`, then one selected identity/camel/kebab fallback. Reject inherited, transformed-name and overload-
group collisions deterministically. Ordinary data-map keys and keyed-domain-map instance keys remain literal data and never
become operation names or aliases. No current ScHelm need requires Jackson annotations on factory arguments.

This narrow use of Jackson annotations does not imply aliases, mixins/views, codecs, coercion, polymorphism or arbitrary
mapper naming-strategy behavior. Existing importer behavior must not be changed to force parity. All four managed modes
remain independently supported. Diagnostics for the new route include source identity, input path, external domain name,
selected public contract, expected versus actual shape/type and preserved cause, while ADR 0009 diagnostics stay unchanged.

## Generator architecture and fidelity

### Revised decision — reflection first

JSON Schema generation is classpath-oriented reflective tooling, normally invoked from the Schema Gradle plugin.
Gradle supplies an explicit root public generated contract type and an isolated, resolved Schema/API/runtime/annotation
classpath. Inspect `Foo_DSL.Builder` / `Foo_DSL.Factory` and reachable public contracts by reflection: methods, parameter
and return types, generic signatures, inheritance, retained annotations and embedded `@AnnoDoc` documentation are primary
inputs. Public visibility alone is not input admission. Keep a private extraction/projection seam that distinguishes
reflected callable shape from compiler-proven input role and produces conservative Draft 2020-12.

Do not load user Schema implementation classes as discovery/inspection targets, instantiate them, read `Foo.Create`, or
invoke Schema methods, constructors, factories, defaults, converters, lifecycle code or annotation closures. Contract
loading uses initialization disabled. Reflection may cause the JVM to resolve referenced Model/API types from signatures
or annotation class literals; that is linkage, never permission to inspect hidden implementations or initialize/execute
those types. The required probe must expose that resolution boundary and demonstrate silent initialization traps. A
promise that no referenced Model class is ever resolved is not established by the earlier classfile-only tracer.

```text
Gradle: root public contract + isolated resolved classpath
                         |
                         v
Reflection: public Builder/Factory signatures, annotations, @AnnoDoc
            + only irreducible compiler enrichment
                         |
                         v
Private extraction / structural projection -> Draft 2020-12 output files
```

The generator has no filesystem-discovery contract. Resolving named types/resources on the supplied classpath is an
implementation detail, not an API for scanning directories/JARs for roots, subtypes, providers or source files. There is
no standalone executable or CLI requirement. A later CLI, if justified, remains classpath-oriented with explicit roots.
Sources, IDE mirrors, Model field inspection and raw classfile scanning are not the primary discovery mechanism.

### Minimize compiler enrichment

Reflection already supplies most WIRE carrier data: identities, signatures, inheritance, types and container shape.
Existing generated annotations supply authored-operation markers, generated-role hints and documentation. Missing
source-field provenance, property/population grouping or normalized admission facts must be demonstrated individually;
first prefer a small runtime-retained generated annotation on the relevant public declaration. A private companion is
allowed only for a named fact that cannot be represented truthfully and economically there, such as a declaration with
no surviving public annotation target. No companion-only requirement is currently proven for #529.

The completed [SCHEMA-R inventory/probe](../implementation/adr-0031-schema-0.md#next-required-slice--schema-r-reflection-versus-enrichment)
classified **every** then-current WIRE datum as directly reflected, existing annotation, small generated annotation, or genuinely
necessary private companion. Its measured inheritance, attribution and missing-metadata results justified the small generated annotations delivered
by WIRE-REPLACE. No companion-only fact was found. WIRE-REMOVE retires the duplicated plan after migration of every
production consumer and the useful regression corpus.

`@AnnoDoc` is runtime-retained embedded bytecode documentation, readable through reflection on public declarations.
Ordinary reflection cannot recover raw source Javadoc; that does not make this existing documentation carrier unavailable.
Read exact declaration annotations, preserve inherited attribution, and report missing/ambiguous documentation honestly.
Do not parse prose to infer admission, required/default values or arbitrary validation. No source JAR, Javadoc network
lookup, compiler AST or IDE-mirror fallback is required.

### Placement and compatibility consequences

First assess a **private implementation inside `klum-ast-gradle-plugin` depending on `klum-ast-runtime`**, with a small
internal extraction/projection seam. The existing plugin has no production runtime dependency today; adding/resolving it
must be qualified for plugin-versus-Schema classloader isolation and dependency alignment. A new standalone public module,
published generator API or reusable executable is not presumed necessary. No module or dependency is added by this design
revision. Generator/rendering dependencies stay out of `klum-ast-runtime`; runtime interpretation remains independently
optional. Depending on the runtime artifact for contract/annotation types does not mean invoking its interpreter.

Resolve all Schema/API, Groovy, Klum runtime and annotation dependencies from declared Gradle inputs in an isolated worker
or equivalent loader. Fail on missing/incompatible linkage with root/type/dependency context; do not fall back to daemon
or ambient application classes. Use exact type identity within one loader, close it after use, and retain no loaded
Schema classes in configuration-cache state. Qualification must cover Groovy 3/4/5 and separately compiled inherited
contracts. Named-module access must be proved for public contracts without opening hidden implementations or adding flags.

Generated annotations are generated ABI even when framework-owned: retention, targets, identity, defaults and inheritance
rules need compatibility tests. Additive enrichment must preserve existing Builder/Factory signatures, client linking,
construction, lifecycle, ownership and serialization. Missing required enrichment must produce explicit upgrade/recompile
or unsupported-shape guidance; optional prose absence need not fail generation. This ADR adds no compatibility shim,
legacy WIRE decoder or public descriptor version protocol. The unreleased carrier has been removed; required public enrichment has explicit recompile/package guidance.

Gradle inputs include explicit root contract names, projection options/tool implementation and the complete resolved
classpath including runtime annotations/docs. Contract resolution occurs at task execution, never configuration. Ordinary
output files must be deterministic, declared, stale-safe and qualified for relevant dependency/documentation changes,
root deletion, relocation, up-to-date/build-cache and configuration-cache reuse. No source/mirror input or directory-
discovery contract is added. Consumers own packaging, copying, publishing and editor association; no classifier, registry,
publication variant or automatic embedding is prescribed.

Structural validity does not imply successful interpretation, operation execution, binding, lifecycle, materialization
or completed validation. Do not turn completed `@Required` into raw-input `required`, evaluate defaults, infer caller LINK
context or translate arbitrary Groovy validation. Float/double and wrappers retain the accepted `type: number` structural
approximation; Java conversion/overflow/rounding checks remain runtime concerns. Straightforward integral bounds and
mathematical BigInteger/BigDecimal semantics remain. Reusable `$defs`/`$ref`, fully qualified generated Builder identities,
literal data/instance keys and conservative unsupported-shape diagnostics remain required. At least one real ScHelm root
and output still needs consumer-owned acceptance before production completion, independently of interpreter delivery.

### Superseded approach and retained evidence

The earlier carrier-first direct projection, classfile/JAR/directory-reader input API and standalone-tooling/module
assumptions in SCHEMA-0 are superseded for future #529 implementation. SCHEMA-T (#942) remains valid historical evidence:
it emitted real Draft 2020-12, reused definitions, passed representative validator checks and executed no Schema code.
It did **not** test reflective classpath loading or show that every duplicated carrier datum was irreducible. Likewise,
#938 compared then-existing annotations with a carrier; it did not rule out small generated annotations. Those limits,
plus the maintainer's explicit 2026-10-23 decision, justify reopening the generator architecture. The old instruction to
proceed from successful SCHEMA-T directly to production without revisiting enrichment is superseded.

No production implementation resumes automatically. SCHEMA-1 remains paused and its branch is untouched. The completed reflection/enrichment migration justifies the next separately assigned projection and Gradle-delivery work.

## Polymorphism and independent concerns

ScHelm needs scalar abstract NamingStrategy and Map<String, Environment>. Generated factories describe calls, not a closed
subtype set or serialized aliases. Abstract input targets remain explicit unsupported negatives until a qualified mapping
is decided. Eventually require closed Schema-owned aliases, reuse literal Field.alternatives where applicable, and reject
unknown/ambiguous aliases. Instance keys are data, not discriminators. No subtype/classpath/factory inference, class-name
wire selection, overlapping-shape inference or annotation-closure execution. Alias carrier/tag syntax remains open.

#930 examples, #929 Jackson 3, #481 deployment, #528 Spring and #304/#310/#349 composition/copy stay related and independently
releasable. ScHelm provider/environment/root/package selection, context injection, OPTIONAL_LINK fallback, profiles and
scaffold rendering remain out of the interpreter's generic scope. No universal deserializer, synthetic closures, reference
registry, public descriptor/codec/registry, publication system, annotation DSL or lifecycle framework is approved.

## Evidence and implementation gate

The shared feasibility probe is complete in [PR #933](https://github.com/klum-dsl/klum-ast/pull/933) and its
[evidence report](../implementation/evidence/issue-932-structured-input-probe.md). It proves existing dispatch/session reuse,
all four managed Jackson modes, active-Builder loading, completed LINK attachment, compiled cross-module discovery and
no-execution Draft 2020-12 projection. The shared compiler-owned admission tracer is now complete in
[PR #937](https://github.com/klum-dsl/klum-ast/pull/937) and
[PR #938](https://github.com/klum-dsl/klum-ast/pull/938). The original package-private carrier preserved normalized roles,
source identity and rejection facts missing from retained annotations. The linked evidence qualifies separate-artifact
inheritance, no-execution extraction, JPMS, Groovy 3/4/5, generated linkage and the supported Gradle output lifecycle.
The approved [runtime plan](../implementation/adr-0031-wire-runtime-v1.md) now has its first production consumer in
WIRE-1: parser-free root and active-Builder interpretation through admitted public contracts. Its bounded numeric and
container policies, keyMapping exclusion and unreleased private fixed-child-key carrier refinement are recorded in that
plan and the linked evidence. The remaining broader #932 work and #529 JSON Schema generation remain independent
from this first runtime slice and can share the acceptance corpus. #932 owns runtime admission/execution and
Construction-session integration; #529 owns reflective extraction, static projection and Gradle integration. The generator does not depend on the
interpreter and may describe only its currently representable accepted subset, while unsupported shapes are diagnosed
honestly.

## Historical WIRE-1 refinements — behavior retained, carrier retired

These refinements record the original migration decisions. The behavioral restrictions remain; transport-format
instructions and carrier identities below describe the removed implementation only.

### WIRE-1 numeric admission clarification

The maintainer-approved numeric scalar targets are exactly `byte`, `short`, `int`, `long`, `float`, `double`, their boxed
JDK types, `BigInteger` and `BigDecimal`. Every other `Number` type is rejected during compiler admission, including
inside supported List/Map payloads, with the unsupported type and source field or Builder operation identified.
The private carrier is unreleased: retain WIRE-PLAN/B unchanged and add no legacy-carrier fallback, compatibility
branch, custom constructor/converter policy, versioning or public API for this correction.
[The stop reproduction and decision](../implementation/evidence/issue-932-wire-1-numeric-stop.md) record why this correction precedes runtime implementation.

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

[Actual generated field/operation characterization](../implementation/evidence/issue-932-wire-1-collection-contracts.md) records all supported categories and the authored Iterable boundary.

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

## Genuine open maintainer decisions

None blocks this documentation revision or the bounded SCHEMA-R probe. After that probe, return only concrete unresolved
facts: any demonstrated need for a companion rather than an annotation, or a demonstrated reason the private plugin
implementation cannot meet isolation/compatibility needs. Those are conditional evidence-driven decisions, not permission
to invent a metadata framework, standalone module or CLI. Task/package naming and routine implementation details are not
maintainer architecture questions. Deferred language extensions remain separate #932 work.

## Explicit key-member extension — #954

Date: 2026-10-24. Status: **Accepted; Stage A merged in #961; Stage B projection and documentation implemented, delivery qualification recorded below**.
This section extends the historical v1 checkpoint above with the bounded accepted contract for
[#954](https://github.com/klum-dsl/klum-ast/issues/954); its
[evidence, examples, diagnostics and implementation stages](../implementation/issue-954-explicit-key-members.md)
are the accepted implementation contract. [Stage B acceptance evidence](../implementation/evidence/issue-954-stage-b.md)
records projection, external validation and delivery qualification. #952 retains broader grammar; #953 retains Factory/source convenience.

### Context and accepted decision

The original v1 excludes actual key fields from object payloads. Root identity comes from a separate argument, Map-child identity
from the outer literal key, and direct-child Schema key providers execute through the ordinary construction seam.
At the original design baseline, the complete-preflight/Consumer split already permitted identity extraction before
allocation. The missing facts were key rename/ignore admission and an explicit classification of direct-child Schema
key authority; enrichment then retained only the native key name. Stage A supplied those facts without introducing a
second construction engine.

Add `KlumStructuredInput.readRoot(Class<T>, Object parsed, String sourceName)` for self-contained root documents.
A keyed root requires its actual Schema `@Key` member under the established external name. Keep the existing
four-argument overload's explicit-rootKey authority and null validation; inner identity remains rejected there even
when equal. Keep `applyToBuilder` keyless for its already allocated target. No generated Factory/Builder callable changes
are proposed: `Create.FromMap(Map)` already has independent copy/type-hint semantics and must not be repurposed.

Concrete direct owned children without a Schema key provider require their key member when supplied, at any depth.
Fixed, derived and Cluster-key children continue their existing construction and reject inner keys. Keyed Map values
continue to derive identity exclusively from their outer literal String; inner identity remains rejected. These
positional rules also apply to children configured through the existing root and active-Builder entry points.

Key naming uses ignore, literal nonempty rename, then identity fallback on the compiler-selected declaration, including
inherited keys and separately compiled parents. No synthetic generic `key`, aliases or coercion are added. Values must
be Strings; empty Strings remain valid. Missing, ignored, ambiguous, wrong-shaped or dual-source keys fail with source,
pointer, external name and declaring-contract diagnostics. A parser's discarded duplicate tokens cannot be recovered
from the supplied Map. Ordinary unannotated properties named `key` retain their meaning.

Preflight snapshots the key separately from ordered operations, validates the whole input without execution, and then
passes it into existing root/direct-child Consumer construction. It never mutates the input Map, sets an allocated
Builder's identity, evaluates a Schema provider or creates an independent child root. Existing context acquisition,
initializers, Templates, lifecycle, ownership, reuse/key-mismatch checks, error chains and no-rollback behavior remain.
Only precise non-executing key-name/admission and key-source enrichment is added to the shared private compiled facade;
missing required facts report recompile/package guidance.

JSON Schema needs position-sensitive definitions: self-contained keyed roots/direct children have the real String
property in `required`; externally keyed bodies reject it. Cache by Builder plus key position, retain closed objects,
and reserve recursive definitions before descent. The definition suffix remains private; retain fully qualified Builder
identities and Model-named output files without a general context framework or configurable key-position policy.
Avoid extending closed definitions through conflicting `allOf` composition.

Compiler-rejected operations remain absent with `x-klum-unsupported`. Admitted operations whose structural input cannot
be represented faithfully fail generation with root, declaring/relationship source, public contract, key position and
reason. In particular, an admitted direct relationship with an ignored or colliding required key fails generation even
when optional; runtime rejection of its key is not evidence that the relationship was excluded from admitted grammar.
A categorically unavailable interpreter position may be omitted only with explicit shared admission evidence, a
diagnostic and interpreter/projection parity coverage. This extension introduces no such categorical exclusion for those
direct relationships. Do not use arbitrary input or an impossible required property as a fallback. Ignored/colliding
required root keys also fail generation; retain previous successful outputs rather than emit partial schemas.

This does not infer raw requirements from completed `@Required` or promise construction/lifecycle success. Default root
artifacts describe self-contained documents and need not validate explicit-rootKey or active-Builder payloads;
no external-rootKey generation option is added in this extension.

### Consequences and alternatives

This proposal intentionally tightens supplied direct caller-key children that previously could reach construction with
null identity, and changes the generated keyed-root artifact from a keyless payload to a self-contained document schema.
Both are explicit migration effects requiring acceptance with this design. Existing method signatures, root authority,
ordinary DSL construction, keyed Maps and managed Jackson stay intact. New generated metadata requires matched
compiler/runtime/plugin qualification on Groovy 3/4/5 and binary inheritance; additive annotation defaults alone do not
establish backward compatibility with the strict annotation-byte reader.

Rejected alternatives are a universal `key` field, post-allocation rebinding, precedence/equality rules for two key
sources, null-based inference in the existing root overload, changing `Create.FromMap`, a single schema shape everywhere,
and preflight execution or overload inference for derived keys. Parser/file/URL support, keyMapping, Templates,
polymorphism, repeated/multi-argument operations and scheduling remain outside this extension. Managed Jackson retains
ADR 0009's independent naming, coercion, context and import-mode contracts.

The linked plan has two cohesive implementation stages: A combines compiler enrichment with its first runtime consumer,
root/direct key interpretation and compatibility tests; B adds position-sensitive projection, fail-closed diagnostics,
Gradle/cross-module qualification and documentation. Stage B depends on Stage A's executable shared facts; no additional
stage, permanent intermediate API or architectural probe is justified. These stages do not mandate separate PRs/issues.
Stage A is delivered by [PR #961](https://github.com/klum-dsl/klum-ast/pull/961). Stage B consumes those facts without
changing the runtime key design; its [acceptance record](../implementation/evidence/issue-954-stage-b.md) maps the
position-specific projection and user-facing documentation to executable coverage. Broader #529 ScHelm qualification
and #932 language work remain independently owned.


## Opt-in raw-input assertions — #959 D2

Date: 2026-10-24. Status: **Accepted semantic profile; production implementation not scheduled**.
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[D1 metadata authority](../implementation/issue-959-schema-projection-feasibility.md#accepted-d1-metadata-authority-and-missing-artifacts),
[D2 contract, diagnostics and evidence](../implementation/evidence/issue-959-d2-raw-input-profile.md).

### Decision

Accept D2 option **B**: a separately selected, off-by-default JSON Schema authoring profile may restrict supplied raw
structured-input values using direct Jakarta `@Min`/`@Max` on ordinary integral properties. Start with byte/short/int/long,
boxed counterparts and BigInteger; one direct Min and/or Max, empty groups and payload, and no relevant group
redefinition/sequence. Intersect inclusive bounds with existing type and machine limits exactly. Preserve original
source association, effective inherited/renamed property names, exclusions and nested owned-property placement.
Do not infer a destination-property constraint for an authored operation or converter input.

The profile constrains what the Model Writer supplies. Possible later replacement or repair during construction does
not relax it. The maintainer regards such repairs as hypothetical boundary cases, not an expected use case or a
compatibility requirement. There is no callback inspection, effective-value prediction or repair exception mode.
JSON Schema validity is not materialization success and is not final Model/Bean Validation equivalence.

Keep current omission and null admission unchanged. Never infer raw-input `required` from `@NotNull`, `@Required`,
bounds or defaults; preserve existing construction-key requirements. Explicit null stays subject to structural
admission. Zero, false and empty String remain supplied values. #962 retains nullable final-validation ownership.

D1 remains authoritative: required inert source-associated facts come only from generated public Builder contracts.
Enhanced generation fails closed on missing/malformed/unverifiable required metadata with source and recompile/package
guidance; there is no Model-byte fallback. Complete unsupported declarations retain labelled runtime descriptions,
appended to existing prose. Contradictory projected bounds fail generation with source-aware diagnostics. Preserve
previous successful outputs on generation failure. The linked D2 contract defines the remaining activation and diagnostic
expectations, including explicit artifact identification and the distinction between schema errors and runtime issues.

### Consequences and alternatives

The existing structural generator remains unchanged until explicitly enhanced. Selecting ordinary schema generation
alone does not activate bounds; consumers choose the artifact their editors/validators use. No runtime JSON Schema
validation, lifecycle change, Bean adapter change, serialization change, new public/generated API or SPI is introduced
by this decision. Exact configuration, metadata encoding and compiler/generator implementation need a separately
assigned P1 plan and Groovy 3/4/5 qualification. The characterization fixtures passed in all three lanes.

Descriptions-only A was considered but not selected as the overall profile; descriptions remain appropriate for
unsupported complete declarations. Broader C is deferred, including groups/payload extensions, composed/repeatable rules,
relationship closures, defaults projection, callbacks, decimals/Size/Pattern and quantity grammar. D2 does not accept
D3–D6, schedule production work or assign a release/milestone.
