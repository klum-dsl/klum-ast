# Structured-input interpretation and Gradle-driven JSON Schema generation

Date: 2026-10-20. Architecture revised: 2026-10-21.

Status: **Accepted direction; implementation not started**. The merged feasibility probe established the reuse and
compiled-contract seams. Runtime and generator implementation remain gated by the shared admission-contract tracer in
the linked plan.

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
  Factories are a workaround for required construction context; context acquisition/provider selection remains external.
- Domain-object members are not a call sequence; parsed Map iteration or YAML source order is not a language guarantee.

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

```text
Compiled Schema + public Foo_DSL.Builder/Factory contracts + proven admission/naming
                                |
                                v
                  Internal operation/property normalization
                                |
                                v
                       JSON Schema Draft 2020-12
```

An optional Gradle plugin accepts explicit roots and compiled dependencies, generating reproducible JSON Schema as ordinary
task outputs with incremental/up-to-date and configuration-cache behavior. Prefer fully qualified generated class identities
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
lookup, publication framework or universal registry is prescribed. JSON Schema is the only initial generator artifact;
normalization stays private, without serialized generic descriptors, codecs, migrations or public readers. Plugin/task/
extension names and internal packaging remain provisional; tooling dependencies do not enter core/runtime.

Structural validity does not imply successful interpretation, binding, materialization or completed validation. Required
may be satisfied by defaults/lifecycle and is not raw-input required. Do not evaluate default closures, translate arbitrary
Groovy validation or imply that schema references encode LINK identities. Advanced defaults/constraints may be omitted
honestly. [Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation) distinguishes presence assertions
from default annotations; no merge/patch, execution-order or round-trip promise follows from schema generation.

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
no-execution Draft 2020-12 projection. The next bounded step is the implementation plan's `WIRE-PLAN` compiler-owned
admission-plan tracer. It must pass its binary-representation comparison, cross-Groovy, no-execution and dual-consumer gates
before either production consumer starts. Once the common contract is accepted and available, #932 runtime interpretation
and #529 JSON Schema generation may proceed independently from the same acceptance corpus. #932 owns language semantics and
runtime interpretation; #529 owns static projection and optional Gradle integration. The generator does not depend on the
interpreter and may describe only its currently representable accepted subset, while unsupported shapes are diagnosed
honestly.
