# Structured-input interpretation and Gradle-driven JSON Schema generation

Date: 2026-10-20. Architecture revised: 2026-10-22. Delivery status reconciled: 2026-10-23.

Status: **Accepted direction; compiler admission seam and restricted WIRE-1 runtime merged; generator not implemented**.
Merged #937/#938 completed `WIRE-PLAN`. The maintainer-approved [WIRE-RUNTIME v1 plan](../implementation/adr-0031-wire-runtime-v1.md)
is implemented by WIRE-1, including the two `KlumStructuredInput` entry points, private classfile reader, ordered
preflight/dispatch and shared Construction callbacks. [Runtime evidence](../implementation/evidence/issue-932-wire-1-runtime.md)
records the pre-publication qualification checkpoint; [PR #940](https://github.com/klum-dsl/klum-ast/pull/940) is now merged.
Broader #932 remains open. [SCHEMA-0](../implementation/adr-0031-schema-0.md) reconciles the independent #529 delivery
plan against that merged baseline; it authorizes no generator or Gradle plugin implementation and assigns no release.

Interpreter/language owner: [#932](https://github.com/klum-dsl/klum-ast/issues/932).
Generator owner: [#529](https://github.com/klum-dsl/klum-ast/issues/529).
[Implementation plan](../implementation/adr-0031-structured-schema-input-artifacts.md).
Need horizon: identified ScHelm demand, provisional next 4.x integration line; no milestone assigned.

Parent decisions: [Builder-first construction](0003-builder-first-materialization.md),
[AsBuilder composition](0004-asbuilder-composition-protocol.md),
[public generated contracts](0005-generated-dsl-support-api.md),
[Jackson interoperability](0009-jackson-interoperability.md),
[multi-Groovy compatibility](0011-shared-multi-groovy-compatibility-contract.md),
[named-map dispatch](0022-conservative-named-map-safety.md).

## Problem and ownership

Model Writers need structured input capable of invoking Schema-authored construction operations, including operations
that configure several properties. Schema Developers also need JSON Schema describing that language for authoring tools.
A field-binding schema alone cannot establish the semantics of custom Builder methods, argument forms or nested scopes.

Two related but independent capabilities address these needs:

| Capability | Owns | Boundary |
| --- | --- | --- |
| Structured Input Interpreter (#932) | Restricted operation admission, naming, arguments, nested dispatch, ordering, diagnostics and Construction-session integration over a parsed Map/List/scalar tree | Optional; parsers caller-owned, no YAML/Jackson dependency in core; not a second Model construction engine |
| JSON Schema Generator (#529) | Optional Gradle-driven Draft 2020-12 generation describing the admitted language from compiled contracts | Does not execute input or own language semantics; no public descriptor protocol |

They share the admission/naming contract proven by one probe, not a mandatory runtime dependency or public metadata SPI.
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
  promise. Deterministic carrier ordering and inherited-name lookup validate names without reordering execution. JSON
  Schema may sort metadata for reproducible artifacts independently. WIRE-PLAN/B normalization/serialization is unchanged.

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

The representation comparison below records the original selection criteria. Merged WIRE-PLAN selected the private
carrier for normalized facts missing from annotations; SCHEMA-0 consumes the current WIRE-1 refinement rather than
repeating that selection. Public contracts still verify the selected operations; retained documentation may supplement
them without redefining admission. The independent [SCHEMA-0 plan](../implementation/adr-0031-schema-0.md) records the
direct projection and the maintainer-simplified binary-only tracer. SCHEMA-0/T uses one synthetic separately compiled
API -> Schema fixture to prove extraction without Schema execution, actual metaschema/instance-validated Draft 2020-12
with reusable definitions, and representative WIRE-1 structural alignment. Comprehensive inheritance, JPMS, numeric,
diagnostic, determinism and output-lifecycle qualification remains production acceptance, not initial feasibility.

```text
Compiled Schema + public Foo_DSL.Builder/Factory contracts + proven admission/naming
                                |
                                v
                  Internal operation/property normalization
                                |
                                v
                       JSON Schema Draft 2020-12
```

The first production generator uses an optional Gradle plugin accepting explicit roots and compiled dependencies and
exposes ordinary JSON Schema task output files, with incremental/up-to-date and configuration-cache behavior. The test-only
feasibility tracer needs no production plugin. Prefer fully qualified generated class identities
for `$defs`; prove deterministic references, escaping and collision handling across modules. Existing retained Schema
annotations and generated public Builder/Factory methods are the first candidate binary source. `WIRE-PLAN` compares their
retained facts with one package-private generated carrier and selects the carrier only if annotations cannot preserve a
required operation role, external name, payload shape or source identity across separately compiled artifacts. The bounded
comparison covers metadata completeness, binary retention, no-execution extraction, runtime lookup, JPMS access, generated
ABI compatibility and implementation/maintenance complexity; it does not introduce a third architecture or descriptor
framework. Source/mirror reconstruction is not primary discovery. No Schema constructor, factory, initializer/default,
converter, lifecycle method or annotation closure executes during extraction. Runtime interpretation intentionally executes
admitted Schema code.

If retained annotations suffice, prefer them. If a carrier is necessary, record the concrete missing annotation capability
and emit the smallest supportable binary contract. Such a carrier is not a public Schema Developer API, but published Schema
artifacts still make its identity and encoding generated ABI: separately compiled Schema/API artifacts from supported older
4.x lines must remain readable by supported newer KlumAST tooling. Package privacy does not remove that compatibility duty;
proposed [ADR 0015](0015-generated-schema-runtime-linkage.md) and
[#896](https://github.com/klum-dsl/klum-ast/issues/896) provide the directional context without becoming parent decisions.
This decision adds no version negotiation, compatibility shim, runtime-floor enforcement or public descriptor version
protocol.

Consumers own packaging, distribution, final location and use. No Maven classifier, embedding requirement, deployment
lookup, published Gradle variant, automatic publication wiring or universal registry is prescribed. Consumers may embed,
copy, publish alongside other artifacts, or use the output for editor/validation tooling. A future published artifact or
variant requires demonstrated consumer need. JSON Schema is the only initial generator artifact;
normalization stays private, without serialized generic descriptors, codecs, migrations or public readers. Plugin/task/
extension names and internal packaging remain provisional; tooling dependencies do not enter core/runtime.

Structural validity does not imply successful interpretation, binding, materialization or completed validation. Required
may be satisfied by defaults/lifecycle and is not raw-input required. Do not evaluate default closures, translate arbitrary
Groovy validation or imply that schema references encode LINK identities. Advanced defaults/constraints may be omitted
honestly. [Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation) distinguishes presence assertions
from default annotations; no merge/patch, execution-order or round-trip promise follows from schema generation.

Maintainer-confirmed numeric fidelity: Java float/double and their wrappers project to JSON Schema `type: number` as a
structural approximation. Numeric schema validity does not guarantee Java conversion or successful Model construction;
WIRE-1 retains overflow, non-finite and conversion checks. Generation does not reproduce every rounding or
representability boundary, add a custom numeric vocabulary/decimal conversion framework, or require exhaustive range
encoding. This accepted approximation is supported projection and must not block first useful JSON Schema. Integral
targets retain exact representable bounds where straightforward; BigInteger/BigDecimal retain mathematical numeric
semantics without conversion through binary double.

Real ScHelm integration is deferred from feasibility. Before declaring the first production generator complete, qualify
at least one real ScHelm Schema root and generated output through a consumer-owned acceptance process. The Hive may
coordinate that later with the ScHelm maintainer; this planning PR selects no consumer packaging convention or repository
change. A successful SCHEMA-0/T leads to a decision whether to implement the generator, not another metadata design phase.

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
[PR #938](https://github.com/klum-dsl/klum-ast/pull/938). The selected package-private carrier preserves normalized roles,
source identity and rejection facts missing from retained annotations. The linked evidence qualifies separate-artifact
inheritance, no-execution extraction, JPMS, Groovy 3/4/5, generated linkage and the supported Gradle output lifecycle.
The approved [runtime plan](../implementation/adr-0031-wire-runtime-v1.md) now has its first production consumer in
WIRE-1: parser-free root and active-Builder interpretation through admitted public contracts. Its bounded numeric and
container policies, keyMapping exclusion and unreleased private fixed-child-key carrier refinement are recorded in that
plan and the linked evidence. The remaining broader #932 work and #529 JSON Schema generation remain independent
from this first runtime slice and can share the acceptance corpus. #932 owns language semantics and
runtime interpretation; #529 owns static projection and optional Gradle integration. The generator does not depend on the
interpreter and may describe only its currently representable accepted subset, while unsupported shapes are diagnosed
honestly.

## WIRE-1 numeric admission clarification

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
