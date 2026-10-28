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

Design disposition, 2026-10-28: the [contract/projection separation](#proposed-contract-and-projection-separation--959)
and its [staged plan](../implementation/issue-959-input-projection-architecture.md) distinguish current guarantees from
accepted P1/P2 architecture and the related, unaccepted P3 custom-projection proposal.
[S1](../implementation/issue-959-schema-s1-contract.md) is implemented and passes repository qualification; S2 remains
unassigned. Size stays paused. S1 evidence records the maintainer-directed draft publication with local Sonar omitted;
PR CI/Sonar remains authoritative.

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


## Raw-input assertions — #959 D2

Date: 2026-10-24. Status: **Accepted semantic profile; production implementation not scheduled**.
Related: [#959](https://github.com/klum-dsl/klum-ast/issues/959),
[D1 metadata authority](../implementation/issue-959-schema-projection-feasibility.md#accepted-d1-metadata-authority-and-missing-artifacts),
[D2 contract, diagnostics and evidence](../implementation/evidence/issue-959-d2-raw-input-profile.md).

### Decision

D2 originally accepted option **B**, a separately selected off-by-default authoring profile. The later accepted
always-on clarification below supersedes that selection: generated JSON Schema projects supported declarations on supplied raw
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
Generation fails closed on missing/malformed/unverifiable required metadata with source and recompile/package
guidance; there is no Model-byte fallback. Complete unsupported declarations retain labelled runtime descriptions,
appended to existing prose. Contradictory projected bounds remain valid schema assertions. Preserve
previous successful outputs on generation failure. The linked D2 contract defines the remaining activation and diagnostic
expectations, including explicit artifact identification and the distinction between schema errors and runtime issues.

### Consequences and alternatives

The original structural-only activation policy is historical and superseded: ordinary schema generation now projects
the supported numeric subset automatically; consumers choose whether to use that artifact in editors/validators. No runtime JSON Schema
validation, lifecycle change, Bean adapter change, serialization change, new public/generated API or SPI is introduced
by this decision. Exact configuration, metadata encoding and compiler/generator implementation need a separately
assigned P1 plan and Groovy 3/4/5 qualification. The characterization fixtures passed in all three lanes.

Descriptions-only A was considered but not selected as the overall profile; descriptions remain appropriate for
unsupported complete declarations. Broader C is deferred, including groups/payload extensions, composed/repeatable rules,
relationship closures, defaults projection, callbacks, decimals/Size/Pattern and quantity grammar. D2 does not accept
D3–D6, schedule production work or assign a release/milestone.


## Minimal integral production profile — #959 P1

Date: 2026-10-25. Status: **Approved bounded implementation; broader #959 mappings remain deferred**.
The separately assigned P1 implements the accepted D1/D2 contracts above. The historical D2 decision did not schedule
implementation; the later assignment authorizes this slice only. See the
[P1 implementation and evidence](../implementation/issue-959-p1-integral-profile.md).

- **Historical activation, superseded:** `integralConstraints` was a false-by-default Boolean. PR #972 removes it; supported numeric projection is unconditional.
  Existing root names, filenames, output ownership and isolated loader semantics remain unchanged.
- Each declaring public Builder's `GeneratedInput.Schema` carries the supported shared metadata protocol and an explicit
  `groupRedefined` fact. Each existing source-associated `GeneratedInput.Field` carries an explicit constraints array,
  including an empty array. Its inert nested facts identify the original annotation, authored description and, for
  direct Min/Max, its compiler-folded Java-long value and empty-groups/payload eligibility. Actual Bean annotations
  remain at their original sites; they are never retargeted onto Builder methods as executable constraints.
- The exact public-contract byte reader supplies these facts without annotation proxies, Model resource reads or a
  Bean provider. All required declaring/effective/inherited/owned contracts must carry complete supported numeric facts.
  Both consumers reject missing/incompatible facts under the shared protocol with source/artifact and recompilation
  guidance. Numeric assertions and runtime validation retain their existing separate semantics.
- Only admitted ordinary integral field properties gain inclusive bounds, intersected with structural machine limits.
  Selection considers the effective hierarchy's group-redefinition facts. Unsupported complete field declarations
  become deterministic runtime descriptions of explicitly authored attributes; there is no generic metadata/default
  evaluator, composed/repeatable interpretation or public contributor SPI. Source identity remains on the property.

This is internal generated linkage, not a general descriptor protocol. No default projection, requiredness inference,
null-admission change, runtime validator behavior, operation/converter-argument mapping or broader D3–D6 decision follows.


## P2 characterization checkpoint — #959

Date: 2026-10-25. Status: **Design evidence only; production profile remains bounded P1**.
The separately assigned [P2 characterization](../implementation/evidence/issue-959-p2-candidates.md) at merged #966
identifies three independently assignable candidates: exact DecimalMin/DecimalMax on BigDecimal/integral ordinary
properties, Size on ordinary exact List/Map inputs, and annotation-only deprecation at effective admitted member uses.
Each requires compiler-owned source-associated typed/completeness facts through the existing public Builder route;
current description text is not an assertion protocol. Decimal bounds must preserve precision, inclusivity and integer/
machine intersections. Container size must preserve raw cardinality and attach to the container use. Deprecated must
reflect the Schema source rather than generated compatibility aliases, without shared child-definition leakage.

String UTF-16 size, Set deduplication, arbitrary Java regex, flags and full-match/search differences block general hard
projection. A tiny flag-free ASCII pattern grammar is technically feasible but remains descriptive/deferred. Keyed owned
Map cardinality is a separate extension candidate, not part of the first ordinary-container assignment.

D1/D2, null/omission/requiredness, #962 separation and the no-user-code-execution boundary remain unchanged. This is no
new mapping or activation/public API decision; future work needs a separate narrow assignment and explicit activation/
metadata compatibility qualification. No runtime, lifecycle, ownership, serialization or generated ABI changes are made.
The retained probes qualify Groovy 3/4/5 inputs and binary producers, not a new JPMS or consumer acceptance promise.
No release/milestone or broader D3–D6 decision follows; #959 remains open.


## Default advisory deprecation — #959 narrow follow-up

Date: 2026-10-25. Status: **Approved bounded implementation; broader #959 remains open**.
The separately assigned follow-up selects default Draft 2020-12 `deprecated: true` for effective admitted members whose
Schema source declaration carries `java.lang.Deprecated`. It is independent of numeric assertions;
there is no Gradle switch. This supersedes the earlier candidate's future-profile wording for deprecation only.

The compiler emits explicit source-deprecation Booleans on existing public `GeneratedInput.Field`/`Key` facts and a
minimal `GeneratedInput.Operation` fact on the exact public projection of a Schema-authored Builder operation. The
private reader requires explicit provenance under the shared protocol and never derives it from generated methods or
Model bytes. Missing source-deprecation facts reject the contract. Advisory output and numeric assertions retain
independent semantics; their metadata has one completeness boundary.

Apply metadata to the effective property/operation-payload or relationship-use position, and to construction keys
only where the key member is present. Preserve inheritance, naming/exclusion/collision handling, documentation, shared
child definitions and nondeprecated sibling uses. Framework compatibility aliases and class-level deprecation supply
no member deprecation. No requiredness/null-admission change, rejection, runtime validation, lifecycle/default/converter
execution, removal/replacement/migration policy or `since`/`forRemoval` interpretation follows.

[Implementation and qualification](../implementation/issue-959-deprecation-projection.md) records this slice.

## D7 checkpoint — canonical shaping vocabulary deferred

Date: 2026-10-25. Status: **Deliberately deferred; no D7 implementation contract accepted**.
A small Klum-owned, dependency-free structured-input/schema-shaping vocabulary is a promising direction for shared
interpreter and JSON Schema facts. Jackson and Bean Validation may enrich or bridge those facts; they do not define
the contract and are not required dependencies of that vocabulary. ADR 0009's independent Jackson behavior is unchanged.

No public annotations, naming profiles, package mechanism or examples semantics are accepted by this checkpoint.
The [detailed D7 evidence brief](../implementation/issue-959-d7-naming-profiles.md) retains alternatives, characterization
and the maintainer decisions needed before later implementation. **D7 does not block #959 DecimalMin/DecimalMax**:
the active bounded decimal-bounds slice can proceed under its own assignment and existing D1/D2 boundaries.
No runtime behavior, generated output, release or milestone commitment follows from this checkpoint.

## D7 vocabulary direction — #959

Date: 2026-10-26. Status: **Accepted bounded design direction; enum contract and implementation remain open**.
This extends the earlier deferral with the maintainer's selected authoring shape, recorded in the
[D7 decision brief](../implementation/issue-959-d7-vocabulary-decision.md#accepted-maintainer-decision--2026-10-26).

- Select **B: a small family of focused Klum annotations**, separating language shaping from advisory projection.
  Allow compatible optional Jackson annotation inputs. Preserve the existing narrow `JsonProperty`/`JsonIgnore`
  support; equivalent facts may coexist and conflicts diagnose rather than depend on annotation order. Broader
  `JsonNaming` support needs its own bounded decision. Jackson/Bean Validation remain optional adapters, not the
  canonical contract or required dependencies. ADR 0009's independent binding behavior remains intact.
- **Defer examples**, favoring reconsideration when AnnoDocimal can retain structured Javadoc tags in separate
  annotations. Existing parsed tag access is distinct from that transport. Scalar examples authored as simple
  strings remain a possible fallback, with no accepted syntax, scalar conversion, placement or profile semantics.
- Select the singular field-level annotation name **`ConstraintProjection`**, with an enum-valued policy instead
  of the earlier suppression marker. **The enum type name, constant names, final value set and default remain
  undecided.** Normal projection, text-only descriptions and omission of constraint-derived output are the
  candidate behaviors discussed; `ProjectionType` and `PROJECT`/`TEXT_ONLY`/`SKIP` are provisional spellings only.
  Policy concerns supported effective field contributions, including approved propagated origins; it does not
  enable arbitrary composed mappings. Ordinary prose, standard deprecation, structural types/machine bounds,
  naming/admission, construction-key/null/omission semantics and runtime validation remain independent.

Keep compiler-folded source facts on generated public Builder declarations as the preferred transport seam; no
concrete fact format, annotation package, retention/override/completeness rule or naming-scope precedence is
selected here. The brief retains the remaining decisions and bounded probe needs. This supersedes its original
examples-first and single-marker recommendations, not the stable always-on faithful numeric baseline delivered
by #972. No production implementation, installed public API, issue/milestone change or release commitment follows.

## Exact direct decimal authoring bounds — #959 narrow follow-up

Date: 2026-10-25. Status: **Approved bounded implementation; D7 remains deferred**.
The DecimalMin/DecimalMax slice now projects supported declarations automatically. The unreleased global numeric
flags are removed without aliases or another selector. Ordinary direct BigDecimal and the existing integral subset
retain the same single-direct, empty-groups/payload and hierarchy eligibility rules; all original numeric-type and
unsupported-context exclusions remain.

Each declaring public Builder carries the supported shared metadata protocol; original field facts carry compiler-folded exact
String `decimalValue` and explicit effective Boolean `inclusive` for direct decimal annotations. Original source and
selection stay on existing facts. Generation always requires complete supported versions and facts on reachable declaring/inherited/owned contracts, with fail-closed
recompile/package diagnostics. No Model-byte fallback, source requirement, annotation-proxy execution or text parsing
supplies missing facts. This is matched internal compiler/runtime/plugin linkage, with no mixed-version guarantee for
an older strict reader consuming newer metadata.

Parse with BigDecimal(String), emit exact valid JSON numeric tokens, and use numeric Draft 2020-12
exclusiveMinimum/exclusiveMaximum. Preserve each independent assertion, including contradictory bounds, using `allOf`
where keywords would collide. Do not prove an admissible integer or reject an empty numeric domain. Preserve raw null/omission/key
requiredness and runtime/interpreter/Jackson/default/Template/lifecycle behavior. This remains an input authoring
policy, with no general Bean Validation equivalence. Precision-capable downstream numeric parsing/validation is required.

See [implementation and qualification](../implementation/issue-959-decimal-bounds.md). Size/Pattern/defaults,
quantity grammar, broader D3-D6 and D7 naming/profile/example vocabulary remain deferred; #962 remains separate.


## Numeric projection responsibility clarification — #959 / PR #972

Date: 2026-10-25. Status: **Accepted maintainer correction, superseding numeric rejection in D2/P1/P2**.

Supported numeric constraints are projected faithfully, including mutually contradictory bounds. JSON Schema generation does not perform numeric-domain satisfiability analysis. A valid generated schema may describe a property for which no supplied value satisfies all constraints.

This explicitly supersedes D2/P1's contradiction-rejection policy delivered in merged PR #966, P2's proposed integer
feasibility requirement, and the earlier PR #972 endpoint/adjacency/ceil-floor refinements. Historical experiments and
qualification records remain evidence of their revisions, not current acceptance criteria. The correction reconciles both annotation families through the shared path without extending their mapping scope.
The subsequent always-on decision below supersedes the global activation flags as well.

Preserve structural numeric types/machine limits and every independently declared restriction. Unique keywords can
coexist directly; colliding numeric keywords use conjunctive `allOf` fragments. Do not select or normalize endpoints.
Exact BigDecimal parsing and serialization keep sparse exponents without integer conversion or exponent arithmetic.
Malformed or out-of-range decimal encodings, missing/incompatible compiler facts and unsupported structural grammar
still fail before output replacement. Optional properties stay omittable; supplied values face all projected assertions.

The compiler owns declaration legality, runtime/Bean Validation owns completed-Model validation, and JSON Schema
consumers validate supplied documents. Enabling generation adds no numeric declaration-validity requirement.
See [current implementation evidence](../implementation/issue-959-decimal-bounds.md#always-on-faithful-numeric-projection).


## Always-on supported numeric projection — #959 / PR #972

Date: 2026-10-25. Status: **Accepted maintainer decision; D7 opt-out remains proposal-only**.

Whenever `generateKlumJsonSchema` runs, project supported direct Min/Max and DecimalMin/DecimalMax automatically.
The unreleased Gradle `integralConstraints` and `decimalConstraints` properties are removed completely, without
compatibility aliases, a replacement global flag or a profile selector. Users configure `rootTypes` and existing binary
inputs/output placement only. Existing numeric types, eligibility, exact values, source association, machine bounds
and independent conjunction remain authoritative. Representable contradictions generate valid Draft 2020-12 schemas;
no numeric satisfiability analysis occurs. Omission/null/keys and runtime construction/validation remain unchanged.

Require the shared supported metadata protocol and complete numeric facts on root, declaring/inherited and participating
owned public Builder contracts. Both interpreter and generator reject missing, malformed or incompatible required facts
with Schema/Builder, artifact and recompilation guidance; no Model-byte fallback or authored-code execution is allowed.
Numeric assertions retain the always-on behavior above; input values and runtime validation are unchanged.

This explicitly supersedes global opt-in requirements in D1/D2/P1/P2 and the earlier PR #972 assignment, including
merged #966's activation/rejection assumptions. Historical evidence remains attached to its original revisions.

D7's field-level projection-control direction is maintainer input, not an accepted annotation API. The
[field opt-out proposal](../implementation/issue-959-d7-naming-profiles.md#field-level-hard-constraint-projection-opt-out--proposal-only)
requires a separate vocabulary/semantics decision and, after approval, a separate implementation PR. It does not block
this independently mergeable numeric baseline. No annotation, suppression, policy object, setter validation, callback,
SPI or general filtering infrastructure is implemented here. Other D3–D7 questions and release placement stay deferred.


## D7 field constraint projection slice — #959

Date: 2026-10-26. Status: **Implemented narrow slice; other D7 topics remain deferred**.
Authority: the accepted first D7 implementation assignment following merged PR #973. This supersedes only the
projection enum/transport questions in the earlier vocabulary checkpoint, not the historical evidence or broader D7 deferrals.

Select `com.blackbuild.klum.ast.ConstraintProjection`, a documented FIELD-only, RUNTIME-retained annotation, with
nested enum `Policy { PROJECT, TEXT_ONLY, OMIT }` and default `PROJECT`. Nesting keeps the single-purpose vocabulary
local without a generic policy framework. Three values suffice: faithful supported assertions with existing fallback
constraint descriptions; all captured constraint descriptions without assertions; or no constraint-derived output,
including source attribution. Absence and an empty annotation both retain #972's default behavior. There is no global flag.

Policy follows the exact effective declaring field identity after approved propagation. Inherited/renamed sources keep
their declaring policy; an independently accepted replacement declaration selects its own/default policy under existing
hierarchy/collision/exclusion rules. A field policy does not recurse into referenced owned definitions. Ordinary
prose/Javadoc, standard deprecation, structure/machine bounds, naming/admission, construction keys, omission/null and
runtime validation remain independent. Captured unsupported constraints are still descriptive except under `OMIT`.

Transport is one explicit compiler-folded enum fact `GeneratedInput.Field.constraintProjection` plus
the shared `GeneratedInput.Schema.version=1` on declaring public Builder contracts. The enum is read from declaration
bytes as inert descriptor/name facts, not through authored annotation proxies. All required facts share that protocol boundary.
Generation always checks policy completeness across root/declaring/inherited/reachable owned contracts, including when
output is omitted; missing/unknown/incompatible policy and malformed supported numeric facts fail atomically with
artifact/source and recompile guidance. Runtime structural reading accepts the fact without using it for admission.
This is matched internal linkage, not a promise that old strict readers accept newer facts.

The supported constraint subset is unchanged: single eligible direct Jakarta Min/Max on integral scalars and
DecimalMin/Max on integral/BigDecimal scalars; empty groups/payload and group context restrictions remain. Approved
one-hop default-only propagation is already effective before capture (for example NotNull descriptions); annotations
with required values cannot be propagated. Arbitrary composed/repeatable hard mappings are not introduced.

The next independent [external naming/exclusion brief](../implementation/issue-959-d7-external-naming-decision.md)
records evidence and maintainer choices; the later type-wide naming checkpoint below records the accepted direction.

Naming profiles/scope/precedence, broader JsonNaming, examples/Javadoc-tag machine data pending separate AnnoDocimal
structured-tag retention, selective filtering, defaults, setter-time validation, provider SPI and the other D3–D7 mappings
remain open. Jackson/Bean Validation stay optional adapters. No WIRE carrier, registry/callback, Schema execution,
Model-byte inspection or runtime/interpreter/Gradle task surface is added.

See [slice evidence and qualification](../implementation/issue-959-d7-constraint-projection.md) and
[user contract](../user/Json-Schema.md#field-constraint-projection).


## D7 type-wide naming direction — #959

Date: 2026-10-26. Status: **Design-only authoring contract confirmed; bounded binary probes precede implementation.**
The maintainer identifies another current project besides Schelm that preconverts kebab-case input Map keys to
camelCase and finds this brittle. Type-wide naming profiles with per-entry literal overrides are therefore an immediate
use case. This supersedes the external-naming brief's earlier profile deferral and root-only recommendation; it does
not make Schelm's observed camelCase a different requirement or authorize implementation.

- An explicit profile binds descendants. Unannotated subtypes inherit it; the same explicit profile may be repeated;
  a different explicit profile is rejected. Explicit `IDENTITY` is binding too.
- Unannotated ancestry leaves the choice open. A child may introduce a profile over its local and inherited eligible
  entries; this does not change the ancestor's standalone contract. Literal names/exclusions retain authority.
- Composed owned values resolve names from their own type/hierarchy, independently of their owner. This refers to
  relationships between ordinary separate types, not lexical nested class declarations; no nested-class support
  guarantee is added.
- Kebab boundaries are accepted: `serviceName -> service-name`, `URLValue -> url-value`, `XML2Parser -> xml2-parser`,
  `version2URL -> version2-url`, keeping digits attached to the preceding word. Non-ASCII characters and existing
  separators remain unchanged; apply only ASCII boundaries/lowercasing. `already_snake` stays unchanged;
  `ÅngströmURL -> Ångström-url`. Literal overrides remain available.
- Profile-induced collisions reject the conflicting type with a diagnostic identifying both declarations, before input
  execution or schema publication. Do not silently remove colliding entries; a literal override can resolve the conflict.
- The first slice covers structured input and JSON Schema only. The current project does not require the new profile
  through ObjectMapper/managed Jackson import; existing Jackson naming stays independent.
- Include focused Klum name/exclusion annotations in this slice, retaining compatible `JsonProperty`/`JsonIgnore`
  support so naming can be authored entirely with Klum annotations. Accepted names are `InputNaming` with nested
  `Profile.IDENTITY`/`KEBAB_CASE`, `InputName('literal')` and marker `InputIgnore`, all in `com.blackbuild.klum.ast`.
  Contracts are confirmed: `RUNTIME` retention; type-only `InputNaming` with a required explicit profile value;
  field/admitted-method `InputName` with a required nonempty literal and marker `InputIgnore`, including selected key
  fields. Equivalent Klum/Jackson declarations coalesce; different explicit literal names fail with both origins,
  including on an excluded entry. Exclusion wins over renaming, including mixed annotations; `JsonIgnore(false)` is neutral and cannot undo exclusion. No canonical or
  annotation-order winner selection.
- Package-info remains deferred. Exact source-free metadata compatibility requires bounded probes before implementation;
  the author-facing behavior does not require a root selector or mapper strategy.

The [updated decision brief](../implementation/issue-959-d7-external-naming-decision.md) distinguishes accepted direction
from remaining recommendations and binary probes. Both interpreter and generator must use one shared grammar, without
ambient mapper/classpath/reflection-order policy or user-code execution. Existing independent Jackson import, #972/#974
projection behavior and deferred examples remain intact. No production annotation, runtime/plugin option, metadata
format, implementation assignment, issue/milestone change or release commitment is accepted here.


## D7 type-profile implementation slice — #959

Date: 2026-10-26. The implementation assignment following merged #975 deliberately splits the accepted authoring family:
**deliver `InputNaming` profiles now; defer `InputName`/`InputIgnore` to the subsequent literal-vocabulary slice.** This
supersedes only the earlier combined delivery scope, not its accepted later vocabulary or bridge-conflict decisions.

`com.blackbuild.klum.ast.InputNaming` has runtime retention, TYPE target and a required nested `Profile` value with exactly
`IDENTITY` and `KEBAB_CASE`. Both consumers resolve one effective profile from public Builder type facts before name
collision pruning. Explicit ancestor choices bind descendants, equal repetition coalesces, conflicting choices reject with
source/artifact provenance, and a first profiled descendant governs inherited/local native candidates. The standalone
unannotated ancestor retains identity behavior. Owned types resolve independently. The bounded ASCII algorithm above
applies only to native eligible member tokens, never literals or data/instance keys; malformed UTF-16 native metadata fails.

The shared `GeneratedInput.Schema.version=1`, explicitly emitted `namingExplicit` and inert `namingProfile` String retain
declared choice versus absence. Provenance is the exact declaring public Builder/type/artifact. Complete naming facts
are required throughout every participating hierarchy, including unprofiled/identity types. Older or incomplete contracts
are rejected with recompilation guidance. Numeric, projection, literal and naming facts share one protocol boundary;
no Model bytes, companion, annotation proxies, authored-code execution or registry is introduced.

Nonidentity member collisions reject before input execution/schema publication with both declarations. Construction-key
collisions retain position-specific handling. Complete no-profile and identity behavior remains unchanged, including
existing parent collision pruning. Native candidates remain available under a first child profile so identity collisions
can be reconsidered without resurrecting structurally excluded sources. The existing Jackson-only literal bridge is
preserved exactly: ignore true/absent value, then nonempty literal `JsonProperty`, then profile/native fallback; false ignore
is neutral and empty rename falls back. No broader Jackson strategy or managed-import profile adapter is implemented.

See [probe/implementation evidence](../implementation/issue-959-d7-input-naming-profiles.md),
[structured-input contract](../user/Structured-Input.md#type-naming-profiles) and
[JSON Schema contract](../user/Json-Schema.md#type-naming-profiles). Package/root/relationship selectors, examples transport,
constraint policy changes and the remaining D7 work stay deferred. Related: #959; issue state and release placement stay unchanged.


## D7 literal name and exclusion implementation slice — #959

Date: 2026-10-27. Status: **Implemented bounded slice following merged PR #976**.
Authority: the accepted [external naming decision](../implementation/issue-959-d7-external-naming-decision.md) and the
literal-vocabulary assignment. This supersedes only the earlier profile slice's `InputName`/`InputIgnore` deferral.

`InputName` and marker `InputIgnore` in `com.blackbuild.klum.ast` have RUNTIME retention and FIELD/METHOD targets.
The required exact nonempty String names source fields, selected keys or already admitted authored Builder methods.
Eligibility/exclusion precedes compatible literals, then inherited/local profiles and identity. Equal Klum/Jackson names
coalesce; contradictory names fail with both origins before exclusion, independent of annotation order. `InputIgnore` or
true/default `JsonIgnore` excludes; false ignore is neutral. Compatible rename plus exclusion is valid. Literal overrides
can separate profile collisions; unresolved nonidentity collisions retain source-aware failure and identity behavior stays
unchanged. Construction-key positions and external identity obligations remain intact.

Existing field/key slots retain effective rename/exclusion. Authored-operation facts now fold the same choices. Explicit
name-origin facts distinguish native, Jackson/Klum literals and Jackson/Klum exclusion; the shared protocol
requires complete facts on declaring public Builder contracts. No raw Model bytes, annotation-proxy execution or Schema
execution participates. Unknown, malformed or missing required facts fail with source/artifact recompile guidance;
older or incomplete contracts are rejected. Numeric, projection and naming semantics remain unchanged.

The [implementation evidence](../implementation/issue-959-d7-literal-input-names.md) records packaged G3/G4/G5 qualification.
There are no package/root/global profiles, broader JsonNaming, mapper adapters, examples, defaults, relationship selectors,
runtime overloads or metadata SPI. Ordinary Groovy configuration, Jackson import and runtime validation remain independent.


## Unified structured-input metadata protocol — PR #977

Date: 2026-10-27. Status: **Accepted maintainer simplification for unreleased 4.2 functionality**.

Structured input and JSON Schema generation require Schemas compiled with a matching supported metadata protocol.
Older or incomplete Builder contracts are rejected; recompile the participating Schema artifacts.

One explicitly emitted `GeneratedInput.Schema.version=1` replaces all feature-specific version markers and supersedes
prior metadata-compatibility statements in this ADR. Both consumers require exactly the supported version and complete,
valid current facts on every participating contract. This includes inherited and owned contracts across artifacts.
Missing/older/future/malformed versions, absent facts and contradictions fail with Schema/Builder, artifact where available
and recompilation guidance. No identity-only acceptance, old operation-annotation reconstruction, conditional profile
completeness, optional older provenance or unknown key-authority state remains. Current key-member facts for suppressed
public getters and separate field/operation names on a shared accessor remain necessary current compiler facts.

The exact public Builder byte reader is the common gate. The interpreter reuses its per-read plan cache to validate the admitted ownership closure before execution, including
omitted children and recursive contracts; the generator already visits that closure. Excluded targets and unrelated
classpath classes are not inspected. There is no version negotiation, registry, adapter, migration framework
or new authoring API. Supported naming, key positions, admission, numeric assertions, projection policy, runtime validation,
initialization safety and output atomicity are preserved. See
[implementation and qualification](../implementation/issue-959-unified-metadata-protocol.md).


## D7 documentary string examples — #959

Date: 2026-10-27. Status: **Accepted simplification for PR #978**.
Authority: the maintainer's [PR #978](https://github.com/klum-dsl/klum-ast/pull/978) simplification decision. This supersedes
the earlier parsed-example decision and the examples/AnnoDocimal deferral for the explicit annotation route.

`@InputExamples` provides documentary strings. They are emitted as JSON Schema string examples, without parsing or
validation against the property type or runtime constraints. FIELD/METHOD targeting, runtime retention and the required
`String[] value` are retained. Empty arrays mean no examples; values, duplicates and order are preserved. Non-JSON text,
quotes, backslashes, newlines and Unicode use the existing JSON Schema serializer. There is no typed-value parser,
content grammar, evaluator, example provider or validation callback.

Separate `GeneratedInput.Field`, `Key` and `Operation` arrays carry the original declaration's strings, including the
suppressed-key getter fallback. Inheritance, ownership, naming/literal/exclusion, constraint projection and deprecation
select and describe the same sources. Placement is the exact field, member-key or authored-operation payload, or parent
relationship use. Relationship notes never leak into shared child definitions; keyless definitions omit key notes.
The byte reader verifies required string-array shape without source/Model inspection or annotation-proxy execution.

The compiler uses Groovy's existing AST-only constant folding, with a small local-field-reference normalization before
scope resolution. The overload that can reflectively read a static field is not used. Nonconstant values fail before
ordinary annotation compilation could initialize a supplying class. There is no custom recursive constant evaluator.
JVM/compiler annotation limits apply. One private aggregate-text guard prevents repeated classfile constants from
amplifying into excessive output; there is no public per-string/count/depth limit contract.

Examples change no runtime input admission, defaults, requiredness, constraints, conversion, templates or lifecycle.
The unified `GeneratedInput.Schema.version=1` is unchanged; no feature version or compatibility mechanism is added.
No infrastructure is retained for future typed examples. Pattern projection is separate.
[Implementation evidence](../implementation/issue-959-d7-examples.md) records the reduction and qualification;
[user documentation](../user/Json-Schema.md#advisory-input-examples) gives the authoring contract.


## Conservative Pattern projection — #959

Date: 2026-10-27. Status: **Accepted bounded implementation slice; rebased onto merged D7 examples for delivery**.
Authority: [maintainer decision](https://github.com/klum-dsl/klum-ast/issues/959#issuecomment-6442779190).
This supersedes only the Pattern deferral for direct singleton Jakarta Pattern on eligible ordinary String fields.

Adopt the evidence-defined ASCII-BOUNDED-1 finite literal/class/quantifier grammar and limits, with optional paired
outer anchors. Conjoin the anchored assertion with the typed printable-ASCII guard. Java full matching and ECMA/Draft
2020-12 supplied-String predicates must agree; no weakened hard translation is allowed. Unsupported complete declarations
remain deterministic runtime-constraint descriptions. Flags, groups/payload, hierarchy group changes, composition/repeats,
non-direct origins, keys, operations, container elements and non-String positions remain outside hard eligibility.

Compiler-frozen regexp String and Boolean flag emptiness extend existing constraint facts. Both consumers validate them
before policy or execution, including suppressed facts and omitted owned closure. The public Builder is the sole byte
source; no Model fallback, provider/user-code execution, interpreter validation or new SPI/flag is introduced. Existing
PROJECT/TEXT_ONLY/OMIT, source attribution, inherited names/exclusion, null/omission and independent conjunction remain.

Per the same maintainer decision, retain `GeneratedInput.Schema.version=1` during 4.2 development. Formal release protocol
cutover belongs to actual release preparation. Current tools require matching, completely recompiled participating
artifacts; unchanged marker is not a mixed/historical toolchain promise. No feature-specific version is added.

See [implementation/qualification](../implementation/issue-959-pattern-projection.md) and
[user contract](../user/Json-Schema.md#conservative-pattern-projection). Related: #959, without tracker-state or release-placement change.


### Pattern origin proof within the existing compiler handoff

The existing KlumCast-owned contributor additionally returns a transient internal SOURCE annotation for an eligible
original field only when its source annotations include direct Jakarta Pattern or a Groovy AnnotationCollector use.
Its private metadata retains immutable original annotation identities before collector expansion. This is compiler-fact
capture for the accepted Pattern slice, separate from default-only annotation propagation in ADR 0029 D2; no propagation
request, copied Bean constraint, user annotation change or emitted behavior is inferred. Other no-request declarations
remain untouched. The inspected declaration and annotation members are not mutated by the contributor: KlumCast owns
attachment through the existing complete-batch handoff. Capture is complete before that handoff and the contributor
performs no subsequent mutation. The transient marker may follow normal field-to-accessor copying but is never emitted
to class files or public Builder contracts and is not an authored annotation or validation rule.

This preserves ADR 0014's local-transform activation and exact module services. No independently registered global
transform, generic preprocessing API, new SPI, source parsing or runtime inspection is introduced. Later Pattern capture
requires identity in the original snapshot; copied/collector-supplied origins remain whole descriptions. The binary
G3/G4/G5 collector regression and existing propagation/JPMS qualification protect both the new origin fact and unchanged
activation boundaries.


## Proposed contract and projection separation — #959

Date: 2026-10-28. Status: **P1/P2 accepted; P1/S1 implemented under the separate assignment below; P2/S2 unassigned; P3 unaccepted.**
Authority: the INPUT-PROJECTION-ARCH design assignment and subsequent maintainer acceptance of P1/P2, informed by
[#959](https://github.com/klum-dsl/klum-ast/issues/959) and the completed Size probe. The
[plan and evidence inventory](../implementation/issue-959-input-projection-architecture.md) pin the inspected revisions.
This is a focused extension of this ADR, not a successor to its language or no-execution decisions.

### Facts, direction and decision ownership

**Design-checkpoint facts at the pinned pre-S1 base:** `CompiledBuilderSchema` combined exact public-contract byte decoding, protocol checks,
reflected signatures, effective naming/admission, immutable-looking result data and constraint-profile extraction.
`JsonSchemaProjection` already lives in the Gradle plugin; its numeric/Pattern mapping and rendering are separate from
runtime interpretation, but its mapping decisions are still embedded in the renderer. The compiler captures inert field
facts; operations have no production parameter-constraint facts. The unified protocol, field projection policies,
documentary string examples, always-on numeric mappings and bounded Pattern mapping remain the accepted baseline.

**Accepted decisions:** P1 and P2 below. Acceptance alone did not assign implementation. The subsequent S1 assignment
and qualification are recorded below; S2 remains unassigned.
**Related open design question:** P3 stays here because custom projection can affect the separation of inert contract
facts, mapping contributions and executable handlers. Its interface, packaging and execution policy are not accepted.
**Separate investigations:** former P4 (parameter projection/editor assistance) is tracked in
[#981](https://github.com/klum-dsl/klum-ast/issues/981); former P5 (import guidance/Jackson compatibility) in
[#982](https://github.com/klum-dsl/klum-ast/issues/982). Neither is a decision or implementation gate in this ADR revision.
Size implementation and new mappings remain deferred; no release placement changes.

### P1 — Extract behavior before choosing a record

Keep one deep internal contract-reading interface used by the interpreter and generator. Inside it, separate:

| Responsibility | Proposed home and interface |
| --- | --- |
| Compiler constraint-fact capture | Existing compiler `StructuredInputConstraintMetadata`; source identity, eligible typed values and explicit completeness, never evaluation. |
| Source-free contract reader | Runtime internal `BuilderContractReader`; exact public Builder/Factory signatures and selected declaration bytes to validated facts, including artifact identity and protocol errors. |
| Effective input resolver | Runtime internal `InputContractResolver`; validated candidates/hierarchy to selected entries, naming, exclusions, key positions and rejection reasons. Preserve native candidates until effective naming is resolved. |
| Normalized immutable contract | Runtime internal `BuilderInputContract`; data only, including declaration identity, payload/role, selected public call, documentation/examples and constraint facts. |
| Built-in constraint projection | Plugin internal `BuiltinConstraintCatalog`; qualified fact plus effective input position to assertions or a reasoned unsupported result. |
| JSON Schema assembly/rendering | Existing plugin projector; structure, conjunction, description/example placement, `$defs`, deterministic serialization and generation diagnostics. |

These are responsibility names, not a mandate for six new public types or interfaces. Start with package-private classes
in the existing runtime internal package and the plugin's existing private tooling package. The plugin currently loads
its same-package projector beside runtime types in one isolated unnamed module; moving types to subpackages would change
package access. Do not add public exports, a split named-module arrangement, or a new artifact just to reorganize files.
Keep the platform-parent loader and existing JPMS qualification. A later package move needs its own demonstrated benefit.

The reader validates required facts once per read session; the resolver must not silently repair missing facts. Both
consumers must validate the admitted ownership closure, including omitted children and suppressed output, before input
execution or schema replacement. Share validated declarations rather than independently re-reading constraint profiles.
Cycles use an identity-indexed graph with a per-session cache, never recursive record equality or a global classloader cache.

Only after extraction assess `BuilderInputContract` as a Java record. A record is suitable if its complete value is the
interface: defensively copied, deterministically ordered collections; no loading, resolving, lazy mutable cache or JSON
behavior. Referenced `Class`/`Method` handles, if retained for dispatch, stay local to that loader/session and are never
invoked by projection, serialized, or retained in Gradle configuration state. Records are only shallowly immutable;
nested lists/maps need copies too. Keep a final class if controlled construction/access or identity semantics fit better.
Converting the current behavior-heavy class directly to a record would leave the architectural problem intact.

Javadocs must explain the allowed inspection targets and initialization prohibition, completeness/error contract,
declaring source versus effective external name, field versus operation role, key position, owned versus use-site facts,
collection ownership/order, and cache lifetime. In particular, replace the current numeric-only `constraintProfile`
description, document `plain` as eligibility rather than validity, and explain that a carried annotation description is
not original source text. The first slice preserves existing diagnostics and generated bytes.

### P2 — Keep built-in mappings in a closed internal catalog

Use a small immutable catalog for the currently supported Jakarta Min/Max, DecimalMin/Max and Pattern mappings. Select
by annotation identity and qualified role/raw type/origin/group facts, with one implementation per coherent mapping
family. Keep byte-protocol validation in the reader and source-fact extraction in the compiler; neither calls this catalog.
The catalog is not ServiceLoader discovery, a user registry, a provider interface or a new runtime dependency. Its
leverage is locality for mapping eligibility and unsupported reasons; the renderer retains combination and placement.

Preserve exact numeric literals/machine fences, independent conjunction (including contradictory bounds), Pattern's
ASCII-BOUNDED-1 proof, PROJECT/TEXT_ONLY/OMIT, structural null/omission/key rules and descriptive unsupported declarations.
No satisfiability engine, new default evaluation or mapping is part of the extraction. Size remains paused pending its
own raw-cardinality, malformed/contradictory-declaration and metadata decisions.

### P3 — Custom projection is a separate trust decision

Recommend **built-ins only for initial delivery**. If later commissioned, use a distinct Klum meta-annotation on a custom
constraint annotation, provisionally `@InputConstraintProjector(SemVerProjection)`. Its association is class-valued;
prefer a bounded class type when a narrow handler contract is approved. This is distinct from the existing field-level
`@ConstraintProjection` output policy. Annotation declaration, handler signature and packaging remain proposed.

A class literal gives compiler/IDE linkage and refactoring support; a handwritten binary-name String shifts failures to
generation and is unnecessarily fragile. It does not authorize loading or execution. The compiler would freeze the
association as inert type identity and typed annotation arguments/defaults on the public Builder, without initializing
the handler. Reading a stored JVM descriptor is compatible with class-valued authoring. Do not inspect the Model or parse
the human description to reconstruct missing metadata. The current protocol does not yet transport these custom facts.

The eventual narrow handler would receive only a copied inert annotation descriptor, qualified raw-input shape and
source/use location. It may return a constraint fragment for that same input value, a stable description amendment, and
one advisory documentary String example. It receives no Model/Builder, input instance, executable validator, mapper,
Gradle Project, default supplier or lifecycle context. It is separate from `ConstraintValidator`; neither `initialize`
nor `isValid` is a projection callback. The plan bounds fragment vocabulary, combination and suppression.

**Trust boundary:** initializing or calling a handler executes user code in the build. A separate classloader or Gradle
worker is not a security sandbox. Restricting the interface cannot prevent malicious code from filesystem/network access,
reflection or initializing other classes. Thus the strong no-user-code/no-Model-execution guarantee remains for built-in
generation only; custom mode could promise that the framework does not supply or invoke Models, not that arbitrary
handlers cannot do so. A declarative association is not consent to execute a dependency's code.

The smallest later policy is an explicit build-owned allowlist of exact associated handlers and a declared handler
classpath, default empty; no scanning or transitive auto-activation. Unknown/unselected custom rules remain labelled
descriptions. Selected missing/incompatible handlers, exceptions or invalid output fail generation before output
replacement. Validate all metadata first. Treat selected handlers as trusted build code; do not claim a sandbox.
Disable output caching/up-to-date reuse for custom mode initially unless all inputs and deterministic behavior are
separately qualified; built-in task behavior is unchanged. Do not execute handlers in runtime preflight or the IDE.

### Consequences and rejected alternatives

Initial extraction changes no public/generated ABI, protocol version, language, runtime validation, lifecycle, ownership,
serialization, optional dependency or release behavior. Groovy 3/4/5 and binary/JPMS regressions remain required for later
code changes. Custom association metadata would be a separate generated-linkage change with explicit
recompilation and compatibility decisions, respecting the current development protocol/release-cutover policy.

Reject a record-first cosmetic rewrite, a generic public contract/validation SPI, a growing mapping switch in the
reader, parsing rendered annotations, executing constraint validators for schema generation and automatic custom discovery.
They either retain mixed responsibilities, lose metadata authority or cross the trust boundary silently. This design
acceptance alone authorizes no production work; S1's subsequent assignment and qualification are recorded below. The
maintainer separately authorized draft documentation publication and the P4/P5 issue split.


## Accepted source-free contract separation — #959 / S1

Date: 2026-10-28. The maintainer accepts the architecture proposal's P1/P2 separation and assigns **S1 only**.
The accepted proposal is pinned at `b49579d3faeab174a558d9b228695f1410be329a`; this implementation starts independently
from master `d232c81f88d513c8c65d9c67b1939dc717b00067` and does not depend on the proposal branch.

P1 separates the package-private exact public declaration reader (`BuilderContractReader`), effective input resolver
(`InputContractResolver`) and immutable normalized result (`CompiledBuilderSchema`). The reader validates required
facts once per exact Class identity within one consumer session. Both consumers reuse those validated facts, including
constraint profiles, rather than rereading projection metadata. The result retains identity semantics as a final class,
with copied nested collections and stable owned-type references; it contains no loading, resolution, rendering or lazy cache.

Source identity remains independent of effective external names. Native candidates survive until hierarchy naming
resolution; field/operation facts, key authority, relationship uses and shared child definitions retain their existing
roles. Every admitted owned contract, including omitted children and suppressed output, remains validated before input
execution or schema replacement. Existing cycle reservations and per-read plans remain consumer-owned. No Model bytes,
annotation proxies, initialization, defaults, lifecycle, validators or other user code participate.

S1 preserves generated ABI/protocol bytes, admission/rejection language, diagnostics, runtime interpretation, JSON Schema
output, Gradle behavior and package/loader/JPMS boundaries. Constraint mappings and projection policy are unchanged.
P2's closed built-in catalog is accepted for a separate S2 assignment and is **not implemented here**. Size remains paused;
custom projectors, parameter facts/#971, IDE tooling and Jackson sunset remain unaccepted and outside this assignment.
See [S1 implementation and qualification](../implementation/issue-959-schema-s1-contract.md). Related: #959;
no issue closure, milestone or release-placement change follows.
